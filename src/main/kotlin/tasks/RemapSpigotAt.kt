/*
 * paperweight is a Gradle plugin for the PaperMC project. It uses
 * some code and systems originally from ForgeGradle.
 *
 * Copyright (C) 2020 Kyle Wood
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package io.papermc.paperweight.tasks

import io.papermc.paperweight.util.defaultOutput
import io.papermc.paperweight.util.file
import org.cadixdev.at.AccessChange
import org.cadixdev.at.AccessTransform
import org.cadixdev.at.AccessTransformSet
import org.cadixdev.at.ModifierChange
import org.cadixdev.at.io.AccessTransformFormats
import org.cadixdev.bombe.type.MethodDescriptor
import org.cadixdev.bombe.type.signature.MethodSignature
import org.cadixdev.lorenz.io.MappingFormats
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.util.jar.JarFile

abstract class RemapSpigotAt : BaseTask() {

    @get:InputFile
    abstract val inputJar: RegularFileProperty
    @get:InputFile
    abstract val spigotAt: RegularFileProperty
    @get:InputFile
    abstract val mapping: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    override fun init() {
        outputFile.convention(defaultOutput("at"))
    }

    @TaskAction
    fun run() {
        val outputAt = AccessTransformSet.create()

        spigotAt.file.useLines { lines ->
            JarFile(inputJar.file).use { jarFile ->
                for (line in lines) {
                    if (line.isBlank() || line.startsWith('#')) {
                        continue
                    }

                    val (access, desc) = line.split(' ')

                    if (desc.contains('(')) {
                        // method
                        val index = desc.indexOf('(')
                        val methodDesc = desc.substring(index)
                        val classAndMethodName = desc.substring(0, index)
                        val slashIndex = classAndMethodName.lastIndexOf('/')
                        val className = classAndMethodName.substring(0, slashIndex)
                        val methodName = classAndMethodName.substring(slashIndex + 1)

                        outputAt.getOrCreateClass(className).replaceMethod(
                            MethodSignature(methodName, MethodDescriptor.of(methodDesc)),
                            parseAccess(access)
                        )
                    } else {
                        // either field or class
                        if (jarFile.getJarEntry("$desc.class") == null) {
                            // field
                            val index = desc.lastIndexOf('/')
                            val className = desc.substring(0, index)
                            val fieldName = desc.substring(index + 1)
                            outputAt.getOrCreateClass(className).replaceField(fieldName, parseAccess(access))
                        } else {
                            // class
                            outputAt.getOrCreateClass(desc).replace(parseAccess(access))
                        }
                    }
                }
            }
        }

        val mappings = MappingFormats.TSRG.createReader(mapping.file.toPath()).use { it.read() }
        val remappedAt = outputAt.remap(mappings)

        AccessTransformFormats.FML.write(outputFile.file.toPath(), remappedAt)
    }

    private fun parseAccess(text: String): AccessTransform {
        val index = text.indexOfAny(charArrayOf('+', '-'))
        return if (index == -1) {
            // only access
            AccessTransform.of(parseAccessChange(text))
        } else {
            val accessChange = parseAccessChange(text.substring(0, index))
            val modifierChange = parseModifierChange(text[index])
            AccessTransform.of(accessChange, modifierChange)
        }
    }

    private fun parseAccessChange(text: String): AccessChange {
        return when (text) {
            "public" -> AccessChange.PUBLIC
            "private" -> AccessChange.PRIVATE
            "protected" -> AccessChange.PROTECTED
            "default" -> AccessChange.PACKAGE_PRIVATE
            else -> AccessChange.NONE
        }
    }

    private fun parseModifierChange(c: Char): ModifierChange {
        return when (c) {
            '+' -> ModifierChange.ADD
            '-' -> ModifierChange.REMOVE
            else -> ModifierChange.NONE
        }
    }
}
