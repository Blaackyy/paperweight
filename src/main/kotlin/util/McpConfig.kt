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

package io.papermc.paperweight.util

typealias FunctionMap = Map<String, McpJvmCommand>
val FunctionMap.decompile: McpJvmCommand
    get() = getValue("decompile")
val FunctionMap.mcinject: McpJvmCommand
    get() = getValue("mcinject")
val FunctionMap.rename: McpJvmCommand
    get() = getValue("rename")

data class McpConfig(
    val spec: Int,
    val version: String,
    val data: McpConfigData,
    val steps: Map<String, List<Map<String, String>>>,
    val functions: Map<String, McpJvmCommand>
)

data class McpConfigData(
    val access: String,
    val constructors: String,
    val exceptions: String,
    val mappings: String,
    val inject: String,
    val statics: String,
    val patches: McpConfigDataPatches
)

data class McpConfigDataPatches(
    val client: String,
    val joined: String,
    val server: String
)

data class McpJvmCommand(
    val version: String,
    val args: List<String>,
    val repo: String,
    val jvmargs: List<String>?
)
