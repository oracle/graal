/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.function.Function;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import com.oracle.objectfile.io.AssemblyBuffer;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.UniqueShortNameProvider;
import com.oracle.svm.core.UniqueShortNameProviderDefaultImpl;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.image.sources.SourceManager;
import com.oracle.svm.hosted.util.DiagnosticUtils;

@AutomaticallyRegisteredFeature
@SuppressWarnings("unused")
class NativeImageDebugInfoFeature implements InternalFeature {
    private NativeImageBFDNameProvider bfdNameProvider;

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
                // configure a BFD mangler to provide unique short names for method and field
                // symbols
                FeatureImpl.AfterRegistrationAccessImpl accessImpl = (FeatureImpl.AfterRegistrationAccessImpl) access;
                // the Graal system loader will not duplicate JDK builtin loader classes
                ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
                // the Graal app loader and image loader and their parent loader will not duplicate
                // classes
                ClassLoader appLoader = accessImpl.getApplicationClassLoader();
                ClassLoader imageLoader = accessImpl.getImageClassLoader().getClassLoader();
                ClassLoader imageLoaderParent = imageLoader.getParent();
                // the app and image loader should both have the same parent
                assert imageLoaderParent == appLoader.getParent();
                // ensure the mangle ignores prefix generation for Graal loaders
                List<ClassLoader> ignored = List.of(systemLoader, imageLoaderParent, appLoader, imageLoader);
                bfdNameProvider = new NativeImageBFDNameProvider(ignored);
                ImageSingletons.add(UniqueShortNameProvider.class, bfdNameProvider);
            }
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        /*
         * Make the name provider aware of the native libs
         */
        if (bfdNameProvider != null) {
            var accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
            bfdNameProvider.setNativeLibs(accessImpl.getNativeLibraries());
        }
    }

    @Override
    @SuppressWarnings("try")
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        Timer timer = TimerCollection.singleton().get(TimerCollection.Registry.DEBUG_INFO);
        try (Timer.StopTimer t = timer.start()) {
            ImageSingletons.add(SourceManager.class, new SourceManager());
            var accessImpl = (FeatureImpl.BeforeImageWriteAccessImpl) access;
            var image = accessImpl.getImage();
            var debugContext = new DebugContext.Builder(HostedOptionValues.singleton(), new GraalDebugHandlersFactory(GraalAccess.getOriginalSnippetReflection())).build();
            RuntimeConfiguration runtimeConfiguration = ((FeatureImpl.BeforeImageWriteAccessImpl) access).getRuntimeConfiguration();
            DebugInfoProvider provider = new NativeImageDebugInfoProvider(debugContext, image.getCodeCache(), image.getHeap(), image.getNativeLibs(), accessImpl.getHostedMetaAccess(),
                            runtimeConfiguration);
            var objectFile = image.getObjectFile();
            objectFile.installDebugInfo(provider);

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

                var imageClassLoader = accessImpl.getImageClassLoader();
                objectFile.newUserDefinedSection(".debug.svm.imagebuild.classpath", makeSectionImpl.apply(DiagnosticUtils.getClassPath(imageClassLoader)));
                objectFile.newUserDefinedSection(".debug.svm.imagebuild.modulepath", makeSectionImpl.apply(DiagnosticUtils.getModulePath(imageClassLoader)));
                objectFile.newUserDefinedSection(".debug.svm.imagebuild.arguments", makeSectionImpl.apply(DiagnosticUtils.getBuilderArguments(imageClassLoader)));
                objectFile.newUserDefinedSection(".debug.svm.imagebuild.java.properties", makeSectionImpl.apply(DiagnosticUtils.getBuilderProperties()));
            }
        }
        ProgressReporter.singleton().setDebugInfoTimer(timer);
    }
}
