/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.tools.projectWizard.plugins.templates

import org.jetbrains.kotlin.tools.projectWizard.core.Context
import org.jetbrains.kotlin.tools.projectWizard.core.entity.PipelineTask
import org.jetbrains.kotlin.tools.projectWizard.templates.NativeConsoleApplicationTemplate

class NativeConsoleApplicationTemplatePlugin(context: Context) : TemplatePlugin(context) {
    override val path = PATH

    override val pipelineTasks: List<PipelineTask> = super.pipelineTasks +
            listOf(
                addTemplate,
            )

    companion object {
        private const val PATH = "template.nativeConsoleApplicationTemplate"

        val addTemplate by addTemplateTask(PATH, NativeConsoleApplicationTemplate())
    }
}