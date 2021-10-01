/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.syntax

import com.intellij.lang.LighterASTNode
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.buildChildSourceElement
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.getContainingClassSymbol
import org.jetbrains.kotlin.fir.analysis.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.analysis.diagnostics.findChildByType
import org.jetbrains.kotlin.fir.analysis.diagnostics.reportOn
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.effectiveVisibility
import org.jetbrains.kotlin.fir.declarations.utils.effectiveVisibilityOrNull
import org.jetbrains.kotlin.fir.diagnostics.ConeJumpOutsideLoopError
import org.jetbrains.kotlin.fir.diagnostics.ConeNotAFunctionLabelError
import org.jetbrains.kotlin.fir.diagnostics.ConeNotALoopLabelError
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.FirThisReference
import org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitorVoid
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.psi.KtLabelReferenceExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.utils.addIfNotNull

object FirRedundantLabelChecker : FirDeclarationSyntaxChecker<FirDeclaration, PsiElement>() {
    override fun checkLightTree(
        element: FirDeclaration,
        source: FirLightSourceElement,
        context: CheckerContext,
        reporter: DiagnosticReporter
    ) {
        // Local declarations are already checked when the containing declaration is checked.
        if (!isRootLabelContainer(element)) return

        val allTraversalRoots = mutableSetOf<FirSourceElement>()

        // First collect all labels in the declaration
        element.accept(object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) {
                element.acceptChildren(this)
            }

            override fun visitBlock(block: FirBlock) {
                markTraversalRoot(block)
                super.visitBlock(block)
            }

            override fun visitProperty(property: FirProperty) {
                markTraversalRoot(property)
                super.visitProperty(property)
            }

            override fun visitSimpleFunction(simpleFunction: FirSimpleFunction) {
                markTraversalRoot(simpleFunction)
                super.visitFunction(simpleFunction)
            }

            override fun visitPropertyAccessor(propertyAccessor: FirPropertyAccessor) {
                markTraversalRoot(propertyAccessor)
                super.visitPropertyAccessor(propertyAccessor)
            }

            override fun visitConstructor(constructor: FirConstructor) {
                markTraversalRoot(constructor)
                super.visitConstructor(constructor)
            }

            private fun markTraversalRoot(elem: FirElement) {
                val elemSource = elem.source
                if (elemSource?.kind is FirRealSourceElementKind) {
                    allTraversalRoots.add(elemSource)
                }
            }
        })

        val unusedLabels = mutableSetOf<FirSourceElement>()

        for (root in allTraversalRoots) {
            root.treeStructure.collectLabels(unusedLabels, root as FirLightSourceElement, allTraversalRoots)
        }

        // Remove any labels that are referenced
        element.visitReferencedLabels { firLabel -> firLabel.source?.let { unusedLabels -= it } }

        for (unusedLabel in unusedLabels) {
            reporter.reportOn(unusedLabel, FirErrors.REDUNDANT_LABEL_WARNING, context)
        }
    }

    private fun FlyweightCapableTreeStructure<LighterASTNode>.collectLabels(
        unusedLabels: MutableSet<FirSourceElement>,
        source: FirLightSourceElement,
        allTraversalRoots: Set<FirSourceElement>,
        isChildNode: Boolean = false,
    ) {
        if (isChildNode && source in allTraversalRoots) return // Prevent double traversal
        val node = source.lighterASTNode
        if (node.tokenType == KtNodeTypes.LABELED_EXPRESSION) {
            val labelQualifier = findChildByType(node, KtNodeTypes.LABEL_QUALIFIER)
            if (labelQualifier != null) {
                findChildByType(labelQualifier, KtNodeTypes.LABEL)?.let {
                    unusedLabels.add(source.buildChildSourceElement(it))
                }
            }
        }
        val childrenRef = Ref.create<Array<LighterASTNode?>>(null)
        getChildren(node, childrenRef)
        for (child in childrenRef.get() ?: return) {
            if (child == null) continue
            collectLabels(unusedLabels, source.buildChildSourceElement(child), allTraversalRoots, true)
        }
    }

    override fun checkPsi(
        element: FirDeclaration,
        source: FirPsiSourceElement,
        psi: PsiElement,
        context: CheckerContext,
        reporter: DiagnosticReporter
    ) {
        // Local declarations are already checked when the containing declaration is checked.
        if (!isRootLabelContainer(element)) return
        val unusedLabels = mutableSetOf<FirSourceElement>()

        // First collect all labels in the declaration
        source.psi.accept(object : KtTreeVisitorVoid() {
            override fun visitLabeledExpression(expression: KtLabeledExpression) {
                unusedLabels.addIfNotNull((expression.getTargetLabel() as? KtLabelReferenceExpression)?.toFirPsiSourceElement())
                super.visitLabeledExpression(expression)
            }
        })

        // Remove any labels that are referenced
        element.visitReferencedLabels { firLabel -> (firLabel.source)?.let { unusedLabels -= it } }

        for (unusedLabel in unusedLabels) {
            reporter.reportOn(unusedLabel, FirErrors.REDUNDANT_LABEL_WARNING, context)
        }
    }

    private fun isRootLabelContainer(element: FirDeclaration): Boolean {
        if (element.source?.kind is FirFakeSourceElementKind) return false
        if (element is FirCallableDeclaration && element.effectiveVisibilityOrNull == EffectiveVisibility.Local) return false
        return when (element) {
            is FirAnonymousFunction -> false
            is FirFunction -> true
            is FirProperty -> true
            // Consider class initializer of non local class as root label container.
            is FirAnonymousInitializer -> {
                val parentVisibility =
                    (element.getContainingClassSymbol(element.moduleData.session) as? FirRegularClassSymbol)?.effectiveVisibility
                parentVisibility != null && parentVisibility != EffectiveVisibility.Local
            }
            else -> false
        }
    }

    private fun FirElement.visitReferencedLabels(action: (FirLabel) -> Unit) {
        accept(object : FirDefaultVisitorVoid() {
            override fun visitElement(element: FirElement) {
                element.acceptChildren(this)
            }

            override fun visitReturnExpression(returnExpression: FirReturnExpression) {
                if (returnExpression.isLabeled) {
                    when (val targetFunction = returnExpression.target.labeledElement) {
                        is FirAnonymousFunction -> targetFunction.label?.let(action)
                        is FirErrorFunction -> (targetFunction.diagnostic as? ConeNotAFunctionLabelError)?.let { action(it.label) }
                        else -> {}
                    }
                }
                super.visitReturnExpression(returnExpression)
            }

            override fun visitContinueExpression(continueExpression: FirContinueExpression) {
                visitLoopJumpImpl(continueExpression)
            }

            override fun visitBreakExpression(breakExpression: FirBreakExpression) {
                visitLoopJumpImpl(breakExpression)
            }

            private fun visitLoopJumpImpl(loopJump: FirLoopJump) {
                if (!loopJump.isLabeled) return
                val target = loopJump.target
                when (val labeledElement = target.labeledElement) {
                    is FirErrorLoop -> when (val diagnostic = labeledElement.diagnostic) {
                        is ConeJumpOutsideLoopError -> diagnostic.label?.let(action)
                        is ConeNotALoopLabelError -> diagnostic.label?.let(action)
                        else -> {}
                    }
                    else -> labeledElement.label?.let(action)
                }
            }

            override fun visitThisReference(thisReference: FirThisReference) {
                val referencedAnonymousFunctionSymbol = thisReference.boundSymbol as? FirAnonymousFunctionSymbol ?: return
                val firLabel = referencedAnonymousFunctionSymbol.label ?: return
                if (thisReference.labelName == firLabel.name) {
                    action(firLabel)
                }
            }
        })
    }
}