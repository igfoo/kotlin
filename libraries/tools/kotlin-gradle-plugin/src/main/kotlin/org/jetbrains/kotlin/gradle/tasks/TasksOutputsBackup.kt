/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.tasks

import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Provider

internal class TaskOutputsBackup(
    val outputs: FileCollection,
    val localState: FileCollection,
    val snapshotsDir: Provider<Directory>,
    val fileSystemOperations: FileSystemOperations
) {
    fun createSnapshot() {
        if (!outputs.isEmpty) {
            fileSystemOperations.sync { spec ->
                spec.from(outputs)
                spec.into(snapshotsDir.map { it.dir(OUTPUTS_DIR_NAME) })
            }
        }

        if (!localState.isEmpty) {
            fileSystemOperations.sync { spec ->
                spec.from(localState)
                spec.into(snapshotsDir.map { it.dir(LOCAL_STATE_DIR_NAME) })
            }
        }
    }

    fun restoreOutputs() {
        fileSystemOperations.delete { it.delete(outputs, localState) }

        fileSystemOperations.sync { spec ->
            spec.from(snapshotsDir.map { it.dir(OUTPUTS_DIR_NAME) })
            spec.into(outputs.asPath)
        }
        fileSystemOperations.sync { spec ->
            spec.from(snapshotsDir.map { it.dir(LOCAL_STATE_DIR_NAME) })
            spec.into(localState.asPath)
        }
    }

    fun deleteSnapshot() {
        fileSystemOperations.delete { it.delete(snapshotsDir) }
    }

    internal companion object {
        const val OUTPUTS_DIR_NAME = "outputs"
        const val LOCAL_STATE_DIR_NAME = "localState"
    }
}
