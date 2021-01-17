/*
 * paperweight is a Gradle plugin for the PaperMC project.
 *
 * Copyright (c) 2020 Kyle Wood (DemonWav)
 *                    Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation;
 * version 2.1 only, no later versions.
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

import io.papermc.paperweight.util.Constants.DEOBF_NAMESPACE
import io.papermc.paperweight.util.Constants.OBF_NAMESPACE
import io.papermc.paperweight.util.Constants.SPIGOT_NAMESPACE
import io.papermc.paperweight.util.MappingFormats
import io.papermc.paperweight.util.MethodRef
import io.papermc.paperweight.util.emptyMergeResult
import io.papermc.paperweight.util.file
import io.papermc.paperweight.util.orNull
import io.papermc.paperweight.util.readOverrides
import io.papermc.paperweight.util.path
import org.cadixdev.lorenz.MappingSet
import org.cadixdev.lorenz.merge.MappingSetMerger
import org.cadixdev.lorenz.merge.MappingSetMergerHandler
import org.cadixdev.lorenz.merge.MergeConfig
import org.cadixdev.lorenz.merge.MergeContext
import org.cadixdev.lorenz.merge.MergeResult
import org.cadixdev.lorenz.model.ClassMapping
import org.cadixdev.lorenz.model.FieldMapping
import org.cadixdev.lorenz.model.InnerClassMapping
import org.cadixdev.lorenz.model.MethodMapping
import org.cadixdev.lorenz.model.TopLevelClassMapping
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.traverse.DepthFirstIterator

abstract class GenerateSpigotMappings : DefaultTask() {

    @get:InputFile
    abstract val classMappings: RegularFileProperty
    @get:InputFile
    abstract val memberMappings: RegularFileProperty
    @get:InputFile
    abstract val packageMappings: RegularFileProperty

    @get:InputFile
    abstract val loggerFields: RegularFileProperty
    @get:InputFile
    abstract val paramIndexes: RegularFileProperty
    @get:InputFile
    abstract val syntheticMethods: RegularFileProperty
    @get:InputFile
    abstract val methodOverrides: RegularFileProperty

    @get:InputFile
    abstract val sourceMappings: RegularFileProperty

    @get:OutputFile
    abstract val outputMappings: RegularFileProperty

    @TaskAction
    fun run() {
        val classMappingSet = MappingFormats.CSRG.createReader(classMappings.path).use { it.read() }
        val memberMappingSet = MappingFormats.CSRG.createReader(memberMappings.path).use { it.read() }
        val mergedMappingSet = MappingSetMerger.create(classMappingSet, memberMappingSet).merge()

        for (line in loggerFields.file.readLines(Charsets.UTF_8)) {
            val (className, fieldName) = line.split(' ')
            val classMapping = mergedMappingSet.getClassMapping(className).orElse(null) ?: continue
            classMapping.getOrCreateFieldMapping(fieldName, "Lorg/apache/logging/log4j/Logger;").deobfuscatedName = "LOGGER"
        }

        // Get the new package name
        val newPackage = packageMappings.asFile.get().readLines()[0].split(Regex("\\s+"))[1]

        val sourceMappings = MappingFormats.TINY.read(sourceMappings.path, OBF_NAMESPACE, DEOBF_NAMESPACE)

        /*
        val synths = newSynths()
        syntheticMethods.file.useLines { lines ->
            for (line in lines) {
                val (className, desc, synthName, baseName) = line.split(" ")
                synths.computeIfAbsent(className) { hashMapOf() }
                    .computeIfAbsent(desc) { hashMapOf() }[baseName] = synthName
            }
        }
         */

        val overrides = readOverrides(methodOverrides)

        val notchToSpigotSet = MappingSetMerger.create(
            mergedMappingSet,
            sourceMappings,
            MergeConfig.builder()
                .withMergeHandler(SpigotMappingsMergerHandler(newPackage, overrides))
                .build()
        ).merge()

        val adjustedSourceMappings = adjustParamIndexes(sourceMappings)
        val cleanedSourceMappings = removeLambdaMappings(adjustedSourceMappings)
        val spigotToNamedSet = notchToSpigotSet.reverse().merge(cleanedSourceMappings)

        MappingFormats.TINY.write(spigotToNamedSet, outputMappings.path, SPIGOT_NAMESPACE, DEOBF_NAMESPACE)
    }

    private fun adjustParamIndexes(mappings: MappingSet): MappingSet {
        val indexes = hashMapOf<String, HashMap<String, HashMap<Int, Int>>>()

        paramIndexes.file.useLines { lines ->
            lines.forEach { line ->
                val parts = line.split(" ")
                val (className, methodName, descriptor) = parts

                val paramMap = indexes.computeIfAbsent(className) { hashMapOf() }
                    .computeIfAbsent(methodName + descriptor) { hashMapOf() }

                for (i in 3 until parts.size step 2) {
                    paramMap[parts[i].toInt()] = parts[i + 1].toInt()
                }
            }
        }

        val result = MappingSet.create()
        for (old in mappings.topLevelClassMappings) {
            val new = result.createTopLevelClassMapping(old.obfuscatedName, old.deobfuscatedName)
            copyClassParam(old, new, indexes)
        }

        return result
    }

    private fun copyClassParam(
        from: ClassMapping<*, *>,
        to: ClassMapping<*, *>,
        params: Map<String, Map<String, Map<Int, Int>>>
    ) {
        for (mapping in from.fieldMappings) {
            mapping.copy(to)
        }
        for (mapping in from.innerClassMappings) {
            val newMapping = to.createInnerClassMapping(mapping.obfuscatedName, mapping.deobfuscatedName)
            copyClassParam(mapping, newMapping, params)
        }

        val classMap = params[from.fullObfuscatedName]
        for (mapping in from.methodMappings) {
            val newMapping = to.createMethodMapping(mapping.signature, mapping.deobfuscatedName)

            val paramMappings = mapping.parameterMappings
            if (paramMappings.isEmpty() || classMap == null) {
                continue
            }

            val methodMap = classMap[mapping.signature.toJvmsIdentifier()] ?: continue
            for (paramMapping in paramMappings) {
                val i = methodMap[paramMapping.index] ?: continue
                newMapping.createParameterMapping(i, paramMapping.deobfuscatedName)
            }
        }
    }

    private fun removeLambdaMappings(mappings: MappingSet): MappingSet {
        val result = MappingSet.create()

        for (classMapping in mappings.topLevelClassMappings) {
            val newClassMapping = result.createTopLevelClassMapping(
                classMapping.obfuscatedName,
                classMapping.deobfuscatedName
            )
            removeLambdaMappings(classMapping, newClassMapping)
        }

        return result
    }
    private fun removeLambdaMappings(old: ClassMapping<*, *>, new: ClassMapping<*, *>) {
        for (inner in old.innerClassMappings) {
            val newInner = new.createInnerClassMapping(inner.obfuscatedName, inner.deobfuscatedName)
            removeLambdaMappings(inner, newInner)
        }

        for (field in old.fieldMappings) {
            new.createFieldMapping(field.signature, field.deobfuscatedName)
        }

        for (method in old.methodMappings) {
            if (method.deobfuscatedName.startsWith("lambda$")) {
                continue
            }
            val newMethod = new.createMethodMapping(method.signature, method.deobfuscatedName)
            for (param in method.parameterMappings) {
                newMethod.createParameterMapping(param.index, param.deobfuscatedName)
            }
        }
    }
}

/*
typealias Synths = Map<String, Map<String, Map<String, String>>>
fun newSynths() = hashMapOf<String, MutableMap<String, MutableMap<String, String>>>()
 */

class SpigotMappingsMergerHandler(
    private val newPackage: String,
    private val methodOverrides: SimpleDirectedGraph<MethodRef, *>
) : MappingSetMergerHandler {

    override fun mergeTopLevelClassMappings(
        left: TopLevelClassMapping,
        right: TopLevelClassMapping,
        target: MappingSet,
        context: MergeContext
    ): MergeResult<TopLevelClassMapping?> {
        throw IllegalStateException("Unexpectedly merged class: ${left.fullObfuscatedName}")
    }

    override fun mergeDuplicateTopLevelClassMappings(
        left: TopLevelClassMapping,
        right: TopLevelClassMapping,
        rightContinuation: TopLevelClassMapping?,
        target: MappingSet,
        context: MergeContext
    ): MergeResult<TopLevelClassMapping?> {
        // If both are provided, keep spigot
        return MergeResult(
            target.createTopLevelClassMapping(left.obfuscatedName, prependPackage(left.deobfuscatedName)),
            right
        )
    }

    override fun addLeftTopLevelClassMapping(
        left: TopLevelClassMapping,
        target: MappingSet,
        context: MergeContext
    ): MergeResult<TopLevelClassMapping?> {
        throw IllegalStateException(
            "Unexpected added class from Spigot: ${left.fullObfuscatedName} - ${left.fullDeobfuscatedName}"
        )
    }

    override fun addRightTopLevelClassMapping(
        right: TopLevelClassMapping,
        target: MappingSet,
        context: MergeContext
    ): MergeResult<TopLevelClassMapping?> {
        // This is a mapping Spigot is totally missing, but Spigot maps all classes without a package to
        // /net/minecraft regardless if there are mappings for the classes or not
        return MergeResult(
            target.createTopLevelClassMapping(right.obfuscatedName, prependPackage(right.obfuscatedName)),
            right
        )
    }

    override fun mergeInnerClassMappings(
        left: InnerClassMapping,
        right: InnerClassMapping,
        target: ClassMapping<*, *>,
        context: MergeContext
    ): MergeResult<InnerClassMapping?> {
        throw IllegalStateException("Unexpectedly merged class: ${left.fullObfuscatedName}")
    }
    override fun mergeDuplicateInnerClassMappings(
        left: InnerClassMapping,
        right: InnerClassMapping,
        rightContinuation: InnerClassMapping?,
        target: ClassMapping<*, *>,
        context: MergeContext
    ): MergeResult<InnerClassMapping?> {
        return MergeResult(
            target.createInnerClassMapping(left.obfuscatedName, left.deobfuscatedName),
            right
        )
    }

    override fun addLeftInnerClassMapping(
        left: InnerClassMapping,
        target: ClassMapping<*, *>,
        context: MergeContext
    ): MergeResult<InnerClassMapping> {
        throw IllegalStateException(
            "Unexpected added class from Spigot: ${left.fullObfuscatedName} - ${left.fullDeobfuscatedName}"
        )
    }
    override fun addRightInnerClassMapping(
        right: InnerClassMapping,
        target: ClassMapping<*, *>,
        context: MergeContext
    ): MergeResult<InnerClassMapping?> {
        // We want to get all of the members from mojmap, but not the mojmap names
        return MergeResult(target.createInnerClassMapping(right.obfuscatedName, right.obfuscatedName), right)
    }

    override fun mergeFieldMappings(
        left: FieldMapping,
        strictRight: FieldMapping?,
        looseRight: FieldMapping?,
        target: ClassMapping<*, *>,
        context: MergeContext
    ): FieldMapping {
        throw IllegalStateException("Unexpectedly merged field: ${left.fullObfuscatedName}")
    }
    override fun mergeDuplicateFieldMappings(
        left: FieldMapping,
        strictRightDuplicate: FieldMapping?,
        looseRightDuplicate: FieldMapping?,
        strictRightContinuation: FieldMapping?,
        looseRightContinuation: FieldMapping?,
        target: ClassMapping<*, *>,
        context: MergeContext
    ): FieldMapping {
        // Spigot mappings don't include signature, so see if we can pull the signature out of the mojmap
        // Regardless, still keep Spigot's name
        val right = strictRightDuplicate ?: looseRightDuplicate ?: strictRightContinuation ?: looseRightContinuation ?: left
        return target.createFieldMapping(right.signature, left.deobfuscatedName)
    }

    override fun mergeMethodMappings(
        left: MethodMapping,
        standardRight: MethodMapping?,
        wiggledRight: MethodMapping?,
        target: ClassMapping<*, *>,
        context: MergeContext
    ): MergeResult<MethodMapping?> {
        throw IllegalStateException("Unexpectedly merged method: $left")
    }
    override fun mergeDuplicateMethodMappings(
        left: MethodMapping,
        standardRightDuplicate: MethodMapping?,
        wiggledRightDuplicate: MethodMapping?,
        standardRightContinuation: MethodMapping?,
        wiggledRightContinuation: MethodMapping?,
        target: ClassMapping<*, *>,
        context: MergeContext
    ): MergeResult<MethodMapping?> {
        val ref = MethodRef.from(left.parent.obfuscatedName, left.signature)
        val newMapping = if (methodOverrides.containsVertex(ref)) {
            val methodRef = DepthFirstIterator(methodOverrides, ref).asSequence().firstOrNull() ?: ref
            target.getOrCreateMethodMapping(methodRef.methodName, methodRef.methodDesc).also {
                it.deobfuscatedName = left.deobfuscatedName
            }
        } else {
            target.getOrCreateMethodMapping(left.signature).also {
                it.deobfuscatedName = left.deobfuscatedName
            }
        }

        return MergeResult(newMapping)
        /*
        val newMapping = target.getOrCreateMethodMapping(left.signature).also {
            it.deobfuscatedName = left.deobfuscatedName
        }

        // Check if Spigot calls this mapping something else
        val synthMethods = synths[left.parent.fullObfuscatedName]?.get(left.obfuscatedDescriptor)
        val newName = synthMethods?.get(left.obfuscatedName)
        return if (newName != null) {
            // Spigot does call this mapping something else, we need to find it first
            val newLeftMapping = left.parentClass.getMethodMapping(MethodSignature(newName, left.descriptor)).orNull
            val newMapping = if (newLeftMapping != null) {
                // We found it, use it for the left side instead
                target.getOrCreateMethodMapping(newLeftMapping.signature).also {
                    it.deobfuscatedName = left.deobfuscatedName
                }
            } else {
                // Can't find the name spigot uses, check the hierarchy

                val methodRef = MethodRef(newMapping.parent.obfuscatedName, newMapping.obfuscatedName, newMapping.descriptor.toString())

                target.getOrCreateMethodMapping(left.signature).also {
                    it.deobfuscatedName = newName
                }
            }

            val methodRef = MethodRef(newMapping.parent.obfuscatedName, newMapping.obfuscatedName, newMapping.descriptor.toString())
            if (methodOverrides.containsVertex(methodRef)) {
                for ((superClass, superName, superDesc) in BreadthFirstIterator(methodOverrides, methodRef)) {

                }
            }
            MergeResult(newMapping)
        } else {
            // normal mapping
            val newMapping = target.getOrCreateMethodMapping(left.signature).also {
                it.deobfuscatedName = left.deobfuscatedName
            }
            return MergeResult(newMapping)
        }
         */
    }
    override fun addLeftMethodMapping(
        left: MethodMapping,
        target: ClassMapping<*, *>,
        context: MergeContext
    ): MergeResult<MethodMapping?> {
        val ref = MethodRef.from(left.parent.obfuscatedName, left.signature)
        val newMapping = if (methodOverrides.containsVertex(ref)) {
            val methodRef = DepthFirstIterator(methodOverrides, ref).asSequence().firstOrNull() ?: ref
            target.getOrCreateMethodMapping(methodRef.methodName, methodRef.methodDesc).also {
                it.deobfuscatedName = left.deobfuscatedName
            }
        } else {
            target.getOrCreateMethodMapping(left.signature).also {
                it.deobfuscatedName = left.deobfuscatedName
            }
        }

        return MergeResult(newMapping)

        /*
        // Check if Spigot maps this from a synthetic method name
        var obfName: String? = null
        val synthMethods = synths[left.parent.fullObfuscatedName]?.get(left.obfuscatedDescriptor)
        if (synthMethods != null) {
            // This is a reverse lookup
            for ((base, synth) in synthMethods) {
                if (left.obfuscatedName == synth) {
                    obfName = base
                    break
                }
            }
        }

        if (obfName == null) {
            // This mapping doesn't actually exist, drop it
            return emptyMergeResult()
        }

        val newMapping = target.getOrCreateMethodMapping(obfName, left.descriptor)
        newMapping.deobfuscatedName = left.deobfuscatedName
        return MergeResult(newMapping)
         */
    }

    override fun addLeftFieldMapping(left: FieldMapping, target: ClassMapping<*, *>, context: MergeContext): FieldMapping? {
        // We don't want mappings Spigot thinks exist but don't
        return null
    }

    // Disable non-spigot mappings
    override fun addRightFieldMapping(
        right: FieldMapping,
        target: ClassMapping<*, *>,
        context: MergeContext
    ): FieldMapping? {
        return null
    }

    override fun addRightMethodMapping(
        right: MethodMapping,
        target: ClassMapping<*, *>,
        context: MergeContext
    ): MergeResult<MethodMapping?> {
        val ref = MethodRef.from(right.parent.obfuscatedName, right.signature)
        if (methodOverrides.containsVertex(ref)) {
            val methodRef = DepthFirstIterator(methodOverrides, ref).asSequence().firstOrNull() ?: ref
            val originalMapping = context.left.getClassMapping(methodRef.className).orNull
                ?.getMethodMapping(methodRef.methodName, methodRef.methodDesc)?.orNull ?: return emptyMergeResult()
            val newMapping = target.getOrCreateMethodMapping(right.signature).also {
                it.deobfuscatedName = originalMapping.deobfuscatedName
            }
            return MergeResult(newMapping)
        } else {
            return emptyMergeResult()
        }

        /*
        // Check if spigot changes this method automatically
        val synthMethods = synths[right.parentClass.fullObfuscatedName]?.get(right.obfuscatedDescriptor)
        val newName = synthMethods?.get(right.obfuscatedName) ?: return emptyMergeResult()

        val newClassMapping = context.left.getClassMapping(right.parentClass.fullObfuscatedName).orNull
        val newMethodMapping = newClassMapping?.getMethodMapping(MethodSignature(newName, right.descriptor))?.orNull
        val newMapping = target.getOrCreateMethodMapping(right.signature)
        if (newMethodMapping != null) {
            newMapping.deobfuscatedName = newMethodMapping.deobfuscatedName
        } else {
            newMapping.deobfuscatedName = newName
        }
        return MergeResult(newMapping)
         */
    }

    private fun prependPackage(name: String): String {
        return if (name.contains('/')) {
            name
        } else {
            newPackage + name
        }
    }
}
