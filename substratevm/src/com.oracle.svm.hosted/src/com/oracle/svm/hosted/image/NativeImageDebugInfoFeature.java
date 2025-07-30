/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.hosted.image;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.PointerBase;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.debugentry.ForeignStructTypeEntry;
import com.oracle.objectfile.debugentry.PointerToTypeEntry;
import com.oracle.objectfile.debugentry.PrimitiveTypeEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import com.oracle.objectfile.io.AssemblyBuffer;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.UniqueShortNameProvider;
import com.oracle.svm.core.UniqueShortNameProviderDefaultImpl;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.code.CodeInfoDecoder;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.debug.BFDNameProvider;
import com.oracle.svm.core.debug.GdbJitInterface;
import com.oracle.svm.core.debug.SubstrateDebugTypeEntrySupport;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.util.DiagnosticUtils;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.code.Architecture;

@AutomaticallyRegisteredFeature
@SuppressWarnings("unused")
class NativeImageDebugInfoFeature implements InternalFeature {

    public NativeLibraries nativeLibs;

    /*
     * A set of classes used for run-time debug info generation that may not be instantiated at
     * run-time, but still reachable through the SubstrateDebugTypeEntrySupport singleton.
     */
    public static final Set<Class<?>> foreignTypeEntryClasses = Set.of(PrimitiveTypeEntry.class, PointerToTypeEntry.class, ForeignStructTypeEntry.class);

    /*
     * A set of fields accessed during run-time debug info generation that are not seen as written
     * during analysis, but still reachable through the SubstrateDebugTypeEntrySupport singleton.
     */
    public static final Set<Field> foreignTypeEntryFields = Set.of(
                    ReflectionUtil.lookupField(TypeEntry.class, "typeName"),
                    ReflectionUtil.lookupField(TypeEntry.class, "typeSignature"),
                    ReflectionUtil.lookupField(ForeignStructTypeEntry.class, "typedefName"));

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.useDebugInfoGeneration();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        /*
         * Ensure that the Linux debug unique short name provider is registered when generating
         * debug info for Linux.
         */
        if (!UniqueShortNameProviderDefaultImpl.UseDefault.useDefaultProvider()) {
            if (!ImageSingletons.contains(UniqueShortNameProvider.class)) {
                /*
                 * Configure a BFD mangler to provide unique short names for methods, fields and
                 * classloaders.
                 */
                FeatureImpl.AfterRegistrationAccessImpl accessImpl = (FeatureImpl.AfterRegistrationAccessImpl) access;

                /*
                 * Ensure the mangle ignores prefix generation for Graal loaders.
                 *
                 * The Graal system loader will not duplicate JDK builtin loader classes.
                 *
                 * The Graal app loader and image loader and their parent loader will not duplicate
                 * classes. The app and image loader should both have the same parent.
                 */
                ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
                ClassLoader appLoader = accessImpl.getApplicationClassLoader();
                ClassLoader imageLoader = accessImpl.getImageClassLoader().getClassLoader();
                ClassLoader imageLoaderParent = imageLoader.getParent();
                assert imageLoaderParent == appLoader.getParent();
                List<ClassLoader> ignored = List.of(systemLoader, imageLoaderParent, appLoader, imageLoader);

                BFDNameProvider bfdNameProvider = new BFDNameProvider(ignored);
                ImageSingletons.add(UniqueShortNameProvider.class, bfdNameProvider);
            }
        }

        /*
         * Foreign Type entries are produced upfront. This way we can also use them for run-time
         * debug info.
         */
        SubstrateDebugTypeEntrySupport typeEntrySupport = new SubstrateDebugTypeEntrySupport();
        ImageSingletons.add(SubstrateDebugTypeEntrySupport.class, typeEntrySupport);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        /*
         * Ensure ClassLoader.nameAndId is available at runtime for type lookup from GDB.
         */
        access.registerAsAccessed(ReflectionUtil.lookupField(ClassLoader.class, "nameAndId"));

        var accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        nativeLibs = accessImpl.getNativeLibraries();
        /*
         * Make sure classes and fields for foreign type entries stored in the
         * SubstrateDebugTypeEntrySupport are available during run-time debug info generation.
         */
        for (Class<?> foreignTypeEntryClass : foreignTypeEntryClasses) {
            accessImpl.registerAsInHeap(foreignTypeEntryClass);
        }
        for (Field foreignTypeEntryField : foreignTypeEntryFields) {
            access.registerFieldValueTransformer(foreignTypeEntryField, new FieldValueTransformerWithAvailability() {
                @Override
                public Object transform(Object receiver, Object originalValue) {
                    return originalValue;
                }

                @Override
                public boolean isAvailable() {
                    return BuildPhaseProvider.isAnalysisFinished();
                }
            });
        }

        /*
         * Provide some global symbol for the gdb-debughelpers script.
         */
        CompressEncoding compressEncoding = ImageSingletons.lookup(CompressEncoding.class);
        CGlobalData<PointerBase> compressionShift = CGlobalDataFactory.createWord(Word.signed(compressEncoding.getShift()), "__svm_compression_shift");
        CGlobalData<PointerBase> useHeapBase = CGlobalDataFactory.createWord(Word.unsigned(compressEncoding.hasBase() ? 1 : 0), "__svm_use_heap_base");
        CGlobalData<PointerBase> reservedHubBitsMask = CGlobalDataFactory.createWord(Word.unsigned(Heap.getHeap().getObjectHeader().getReservedHubBitsMask()), "__svm_reserved_bits_mask");
        CGlobalData<PointerBase> objectAlignment = CGlobalDataFactory.createWord(Word.unsigned(ConfigurationValues.getObjectLayout().getAlignment()), "__svm_object_alignment");
        CGlobalData<PointerBase> frameSizeStatusMask = CGlobalDataFactory.createWord(Word.unsigned(CodeInfoDecoder.FRAME_SIZE_STATUS_MASK), "__svm_frame_size_status_mask");
        CGlobalData<PointerBase> heapBaseRegnum = CGlobalDataFactory.createWord(Word.unsigned(ReservedRegisters.singleton().getHeapBaseRegister().number), "__svm_heap_base_regnum");
        CGlobalDataFeature.singleton().registerWithGlobalHiddenSymbol(compressionShift);
        CGlobalDataFeature.singleton().registerWithGlobalHiddenSymbol(useHeapBase);
        CGlobalDataFeature.singleton().registerWithGlobalHiddenSymbol(reservedHubBitsMask);
        CGlobalDataFeature.singleton().registerWithGlobalHiddenSymbol(objectAlignment);
        CGlobalDataFeature.singleton().registerWithGlobalHiddenSymbol(frameSizeStatusMask);
        CGlobalDataFeature.singleton().registerWithGlobalHiddenSymbol(heapBaseRegnum);

        /*
         * Create a global symbol for the jit debug descriptor with proper initial values for the
         * GDB JIT compilation interface.
         */
        if (SubstrateOptions.RuntimeDebugInfo.getValue()) {
            Architecture arch = ConfigurationValues.getTarget().arch;
            ByteBuffer buffer = ByteBuffer.allocate(SizeOf.get(GdbJitInterface.JITDescriptor.class)).order(arch.getByteOrder());

            /*
             * Set version to 1. Must be 1 otherwise GDB does not register breakpoints for the GDB
             * JIT Compilation interface.
             */
            buffer.putInt(1);

            /* Set action flag to JIT_NOACTION (0). */
            buffer.putInt(GdbJitInterface.JITActions.JIT_NOACTION.ordinal());

            /*
             * Set relevant entry to nullptr. This is the pointer to the debug info entry that is
             * affected by the GDB JIT interface action.
             */
            buffer.putLong(0);

            /*
             * Set first entry to nullptr. This is the pointer to the last debug info entry notified
             * to the GDB JIT interface We will prepend new entries here.
             */
            buffer.putLong(0);

            CGlobalDataFeature.singleton().registerWithGlobalSymbol(CGlobalDataFactory.createBytes(buffer::array, "__jit_debug_descriptor"));
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        var accessImpl = (FeatureImpl.AfterAnalysisAccessImpl) access;
        var metaAccess = accessImpl.getMetaAccess();

        /*
         * Traverse all types and process native types into type entries. These type entries can
         * also be reused for run-time debug info generation.
         */
        for (AnalysisType t : accessImpl.getUniverse().getTypes()) {
            if (nativeLibs.isWordBase(t)) {
                TypeEntry typeEntry = NativeImageDebugInfoProvider.processElementInfo(nativeLibs, metaAccess, t);
                /*
                 * We will always create a type entry here. If no element info is found, or we can't
                 * find the pointed to type we create a pointer to void/primitive type.
                 */
                SubstrateDebugTypeEntrySupport.singleton().addTypeEntry(typeEntry);
            }
        }
        // the map is complete now -> trim it for storing it more efficiently
        SubstrateDebugTypeEntrySupport.singleton().trim();
    }

    @Override
    @SuppressWarnings("try")
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        Timer timer = TimerCollection.singleton().get(TimerCollection.Registry.DEBUG_INFO);
        try (Timer.StopTimer t = timer.start()) {
            var accessImpl = (FeatureImpl.BeforeImageWriteAccessImpl) access;
            var image = accessImpl.getImage();
            var debugContext = new DebugContext.Builder(HostedOptionValues.singleton(), new GraalDebugHandlersFactory(GraalAccess.getOriginalSnippetReflection())).build();
            RuntimeConfiguration runtimeConfiguration = ((FeatureImpl.BeforeImageWriteAccessImpl) access).getRuntimeConfiguration();
            DebugInfoProvider provider = new NativeImageDebugInfoProvider(debugContext, image.getCodeCache(), image.getHeap(), image.getNativeLibs(), accessImpl.getHostedMetaAccess(),
                            runtimeConfiguration);
            var objectFile = image.getObjectFile();

            int debugInfoGenerationThreadCount = SubstrateOptions.DebugInfoGenerationThreadCount.getValue();
            if (debugInfoGenerationThreadCount > 0) {
                try (ForkJoinPool threadPool = new ForkJoinPool(debugInfoGenerationThreadCount)) {
                    threadPool.submit(() -> objectFile.installDebugInfo(provider)).join();
                }
            } else {
                objectFile.installDebugInfo(provider);
            }

            if (Platform.includedIn(Platform.LINUX.class) && SubstrateOptions.UseImagebuildDebugSections.getValue()) {
                /*-
                 * Provide imagebuild infos as special debug.svm.imagebuild.* sections
                 * The contents of these sections can be dumped with:
                 * readelf -p .<sectionName> <debuginfo file>
                 * e.g. readelf -p .debug.svm.imagebuild.arguments helloworld
                 */
                Function<List<String>, BasicProgbitsSectionImpl> makeSectionImpl = customInfo -> {
                    var content = AssemblyBuffer.createOutputAssembler(objectFile.getByteOrder());
                    for (String elem : customInfo) {
                        content.writeString(elem);
                    }
                    return new BasicProgbitsSectionImpl(content.getBlob()) {
                        @Override
                        public boolean isLoadable() {
                            return false;
                        }
                    };
                };

                /*
                 * Create a section that triggers GDB to read debugging assistance information from
                 * gdb-debughelpers.py in the current working directory.
                 */
                Supplier<BasicProgbitsSectionImpl> makeGDBSectionImpl = () -> {
                    var content = AssemblyBuffer.createOutputAssembler(objectFile.getByteOrder());
                    // 1 -> python file
                    content.writeByte((byte) 1);
                    content.writeString("./gdb-debughelpers.py");
                    return new BasicProgbitsSectionImpl(content.getBlob()) {
                        @Override
                        public boolean isLoadable() {
                            return false;
                        }
                    };
                };

                var imageClassLoader = accessImpl.getImageClassLoader();
                objectFile.newUserDefinedSection(".debug.svm.imagebuild.classpath", makeSectionImpl.apply(DiagnosticUtils.getClassPath(imageClassLoader)));
                objectFile.newUserDefinedSection(".debug.svm.imagebuild.modulepath", makeSectionImpl.apply(DiagnosticUtils.getModulePath(imageClassLoader)));
                objectFile.newUserDefinedSection(".debug.svm.imagebuild.arguments", makeSectionImpl.apply(DiagnosticUtils.getBuilderArguments(imageClassLoader)));
                objectFile.newUserDefinedSection(".debug.svm.imagebuild.java.properties", makeSectionImpl.apply(DiagnosticUtils.getBuilderProperties()));

                Path svmDebugHelper = Path.of(System.getProperty("java.home"), "lib", "svm", "debug", "gdb-debughelpers.py");
                if (Files.exists(svmDebugHelper)) {
                    objectFile.newUserDefinedSection(".debug_gdb_scripts", makeGDBSectionImpl.get());
                }
            }
        }
        ProgressReporter.singleton().setDebugInfoTimer(timer);
    }
}
