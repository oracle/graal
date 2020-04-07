/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm;

import java.nio.file.Path;
import java.util.Map;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.java.LoadExceptionObjectNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.TargetGraphBuilderPlugins;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateBackendFactory;
import com.oracle.svm.core.graal.code.SubstrateLoweringProviderFactory;
import com.oracle.svm.core.graal.code.SubstrateSuitesCreatorProvider;
import com.oracle.svm.core.graal.llvm.lowering.LLVMLoadExceptionObjectLowering;
import com.oracle.svm.core.graal.llvm.lowering.SubstrateLLVMLoweringProvider;
import com.oracle.svm.core.graal.llvm.replacements.LLVMGraphBuilderPlugins;
import com.oracle.svm.core.graal.llvm.runtime.LLVMExceptionUnwind;
import com.oracle.svm.core.graal.llvm.util.LLVMOptions;
import com.oracle.svm.core.graal.llvm.util.LLVMToolchain;
import com.oracle.svm.core.graal.llvm.util.LLVMToolchain.RunFailureException;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.snippets.ExceptionUnwind;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.image.NativeImageCodeCacheFactory;
import com.oracle.svm.hosted.image.NativeImageHeap;

/*
 * This feature enables the LLVM backend of Native Image. It does so by registering the backend,
 * lowerings, code cache and exception handling mechanism required to emit LLVM bitcode
 * from Graal graphs, and compile this bitcode into machine code.
 */
@AutomaticFeature
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
public class LLVMFeature implements Feature, GraalFeature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        if (!SubstrateOptions.useLLVMBackend()) {
            for (HostedOptionKey<?> llvmOption : LLVMOptions.allOptions) {
                if (llvmOption.hasBeenSet()) {
                    throw UserError.abort("Flag " + llvmOption.getName() + " can only be used together with -H:CompilerBackend=llvm");
                }
            }
        }
        return SubstrateOptions.useLLVMBackend();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(SubstrateBackendFactory.class, new SubstrateBackendFactory() {
            @Override
            public SubstrateBackend newBackend(Providers newProviders) {
                return new SubstrateLLVMBackend(newProviders);
            }
        });

        ImageSingletons.add(SubstrateLoweringProviderFactory.class, SubstrateLLVMLoweringProvider::new);

        ImageSingletons.add(NativeImageCodeCacheFactory.class, new NativeImageCodeCacheFactory() {
            @Override
            public NativeImageCodeCache newCodeCache(CompileQueue compileQueue, NativeImageHeap heap, Platform platform, Path tempDir) {
                return new LLVMNativeImageCodeCache(compileQueue.getCompilations(), heap, platform, tempDir);
            }
        });

        ImageSingletons.add(ExceptionUnwind.class, new ExceptionUnwind() {
            @Override
            protected void customUnwindException(Pointer callerSP) {
                LLVMExceptionUnwind.raiseException();
            }
        });

        ImageSingletons.add(TargetGraphBuilderPlugins.class, new LLVMGraphBuilderPlugins());

        ImageSingletons.add(SubstrateSuitesCreatorProvider.class, new SubstrateSuitesCreatorProvider());
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        FeatureImpl.BeforeAnalysisAccessImpl accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        accessImpl.registerAsCompiled((AnalysisMethod) LLVMExceptionUnwind.getRetrieveExceptionMethod(accessImpl.getMetaAccess()));
    }

    @Override
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        lowerings.put(LoadExceptionObjectNode.class, new LLVMLoadExceptionObjectLowering());
    }

    static class LLVMVersionChecker {
        private static final int MIN_LLVM_VERSION = 8;
        private static final int MIN_LLVM_OPTIMIZATIONS_VERSION = 9;
        private static final int llvmVersion = getLLVMVersion();

        static boolean useExplicitSelects() {
            if (!Platform.includedIn(Platform.AMD64.class)) {
                return false;
            }
            return llvmVersion < MIN_LLVM_OPTIMIZATIONS_VERSION;
        }

        private static int getLLVMVersion() {
            String versionString;
            try {
                versionString = LLVMToolchain.runLLVMCommand("llvm-config", null, "--version");
            } catch (RunFailureException e) {
                throw UserError.abort("Using the LLVM backend requires LLVM to be installed on your machine.");
            }
            String[] splitVersion = versionString.split("\\.");
            assert splitVersion.length == 3;
            int version = Integer.parseInt(splitVersion[0]);

            if (version < MIN_LLVM_VERSION) {
                throw UserError.abort("Unsupported LLVM version: " + version + ". Supported versions are LLVM " + MIN_LLVM_VERSION + " and above");
            } else if (LLVMOptions.BitcodeOptimizations.getValue() && version < MIN_LLVM_OPTIMIZATIONS_VERSION) {
                throw UserError.abort("Unsupported LLVM version to enable bitcode optimizations: " + version + ". Supported versions are LLVM " + MIN_LLVM_OPTIMIZATIONS_VERSION + ".0.0 and above");
            }

            return version;
        }
    }
}
