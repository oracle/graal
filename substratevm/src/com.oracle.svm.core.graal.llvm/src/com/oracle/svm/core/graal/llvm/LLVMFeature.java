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

import static com.oracle.svm.core.SubstrateOptions.CompilerBackend;
import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.llvm.LLVMUtils;
import org.graalvm.compiler.core.llvm.LLVMUtils.TargetSpecific;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.LoadExceptionObjectNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.TargetGraphBuilderPlugins;
import org.graalvm.compiler.replacements.llvm.LLVMGraphBuilderPlugins;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateBackendFactory;
import com.oracle.svm.core.graal.code.SubstrateLoweringProviderFactory;
import com.oracle.svm.core.graal.code.SubstrateSuitesCreatorProvider;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.nodes.ExceptionStateNode;
import com.oracle.svm.core.graal.nodes.ReadExceptionObjectNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.snippets.ExceptionUnwind;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.c.util.FileUtils;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.image.NativeImageCodeCacheFactory;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.meta.HostedMethod;

@AutomaticFeature
@Platforms({InternalPlatform.LINUX_JNI_AND_SUBSTITUTIONS.class, InternalPlatform.DARWIN_JNI_AND_SUBSTITUTIONS.class})
public class LLVMFeature implements Feature, GraalFeature {

    private static HostedMethod personalityStub;
    public static HostedMethod retrieveExceptionMethod;

    private static final int MIN_LLVM_VERSION = 8;
    private static final int MIN_LLVM_OPTIMIZATIONS_VERSION = 9;
    private static final int llvmVersion = checkLLVMVersion();

    public static final int SPECIAL_REGISTER_COUNT;
    public static final int THREAD_POINTER_INDEX;
    public static final int HEAP_BASE_INDEX;

    static {
        int firstArgumentOffset = 0;
        THREAD_POINTER_INDEX = (SubstrateOptions.MultiThreaded.getValue()) ? firstArgumentOffset++ : -1;
        HEAP_BASE_INDEX = (SubstrateOptions.SpawnIsolates.getValue()) ? firstArgumentOffset++ : -1;
        SPECIAL_REGISTER_COUNT = firstArgumentOffset;
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        if (!CompilerBackend.getValue().equals("llvm")) {
            for (HostedOptionKey<?> llvmOption : LLVMOptions.allOptions) {
                if (llvmOption.hasBeenSet()) {
                    throw UserError.abort("Flag " + llvmOption.getName() + " can only be used together with -H:CompilerBackend=llvm");
                }
            }
        }
        return CompilerBackend.getValue().equals("llvm");
    }

    public static HostedMethod getPersonalityStub() {
        return personalityStub;
    }

    @Fold
    public static boolean useExplicitSelects() {
        if (!Platform.includedIn(Platform.AMD64.class)) {
            return false;
        }
        if (llvmVersion == -1) {
            return !LLVMOptions.BitcodeOptimizations.getValue();
        } else {
            return llvmVersion < MIN_LLVM_OPTIMIZATIONS_VERSION;
        }
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
                LLVMPersonalityFunction.raiseException();
            }
        });

        ImageSingletons.add(TargetGraphBuilderPlugins.class, new LLVMGraphBuilderPlugins());
        ImageSingletons.add(SubstrateSuitesCreatorProvider.class, new SubstrateSuitesCreatorProvider());
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        FeatureImpl.BeforeAnalysisAccessImpl accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        try {
            accessImpl.registerAsCompiled(LLVMPersonalityFunction.class.getMethod("retrieveException"));
        } catch (NoSuchMethodException e) {
            throw shouldNotReachHere();
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        FeatureImpl.BeforeCompilationAccessImpl accessImpl = (FeatureImpl.BeforeCompilationAccessImpl) access;
        personalityStub = accessImpl.getUniverse().lookup(LLVMPersonalityFunction.getPersonalityStub());
        try {
            retrieveExceptionMethod = accessImpl.getMetaAccess().lookupJavaMethod(LLVMPersonalityFunction.class.getMethod("retrieveException"));
        } catch (NoSuchMethodException e) {
            throw shouldNotReachHere();
        }
    }

    @Override
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        lowerings.put(LoadExceptionObjectNode.class, new LLVMLoadExceptionObjectLowering());
    }

    private static class LLVMLoadExceptionObjectLowering implements NodeLoweringProvider<LoadExceptionObjectNode> {

        @Override
        public void lower(LoadExceptionObjectNode node, LoweringTool tool) {
            FrameState exceptionState = node.stateAfter();
            assert exceptionState != null;

            StructuredGraph graph = node.graph();
            FixedWithNextNode readRegNode = graph.add(new ReadExceptionObjectNode(StampFactory.objectNonNull()));
            graph.replaceFixedWithFixed(node, readRegNode);

            /*
             * When libunwind has found an exception handler, it jumps directly to it from native
             * code. We therefore need the CFunctionEpilogueNode to restore the Java state before we
             * handle the exception.
             */
            CFunctionEpilogueNode cFunctionEpilogueNode = new CFunctionEpilogueNode(StatusSupport.STATUS_IN_NATIVE);
            graph.add(cFunctionEpilogueNode);
            graph.addAfterFixed(readRegNode, cFunctionEpilogueNode);
            cFunctionEpilogueNode.lower(tool);

            graph.addAfterFixed(readRegNode, graph.add(new ExceptionStateNode(exceptionState)));
        }
    }

    private static int checkLLVMVersion() {
        if (!CompilerBackend.getValue().equals("llvm") || LLVMOptions.CustomLLC.hasBeenSet()) {
            return -1;
        }

        String versionString = getLLVMVersion();
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

    private static String getLLVMVersion() {
        int status;
        String output = null;
        try (OutputStream os = new ByteArrayOutputStream()) {
            List<String> cmd = new ArrayList<>();
            cmd.add(LLVMUtils.getLLVMBinDir().resolve("llvm-config").toString());
            cmd.add("--version");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            FileUtils.drainInputStream(p.getInputStream(), os);

            status = p.waitFor();
            output = os.toString().trim();
        } catch (IOException | InterruptedException e) {
            status = -1;
        }

        if (status != 0) {
            throw UserError.abort("Using the LLVM backend requires LLVM to be installed on your machine.");
        }

        return output;
    }
}

@AutomaticFeature
@Platforms(Platform.AMD64.class)
class LLVMAMD64TargetSpecificFeature implements Feature {
    private static final int AMD64_RSP_IDX = 7;
    private static final int AMD64_RBP_IDX = 6;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return CompilerBackend.getValue().equals("llvm");
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(TargetSpecific.class, new TargetSpecific() {
            @Override
            public String getRegisterInlineAsm(String register) {
                return "movq %" + register + ", $0";
            }

            @Override
            public String getJumpInlineAsm() {
                return "jmpq *$0";
            }

            @Override
            public String getLLVMArchName() {
                return "x86-64";
            }

            /*
             * The return address is pushed to the stack just before each call, but is not part of
             * the stack frame of the callee. It is therefore not accounted for in either call
             * frame.
             */
            @Override
            public int getCallFrameSeparation() {
                return FrameAccess.returnAddressSize();
            }

            /*
             * The frame pointer is stored as the first element on the stack, just below the return
             * address.
             */
            @Override
            public int getFramePointerOffset() {
                return -FrameAccess.wordSize();
            }

            @Override
            public int getStackPointerDwarfRegNum() {
                return AMD64_RSP_IDX;
            }

            @Override
            public int getFramePointerDwarfRegNum() {
                return AMD64_RBP_IDX;
            }

            @Override
            public List<String> getLLCAdditionalOptions() {
                return Collections.singletonList("-no-x86-call-frame-opt");
            }
        });
    }
}

@AutomaticFeature
@Platforms(Platform.AARCH64.class)
class LLVMAArch64TargetSpecificFeature implements Feature {
    private static final int AARCH64_FP_IDX = 29;
    private static final int AARCH64_SP_IDX = 31;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return CompilerBackend.getValue().equals("llvm");
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(TargetSpecific.class, new TargetSpecific() {
            @Override
            public String getRegisterInlineAsm(String register) {
                return "MOV $0, " + getLLVMRegisterName(register);
            }

            @Override
            public String getJumpInlineAsm() {
                return "BR $0";
            }

            @Override
            public String getLLVMArchName() {
                return "aarch64";
            }

            /*
             * The return address is not saved on the stack on ARM, so the stack frames have no
             * space inbetween them.
             */
            @Override
            public int getCallFrameSeparation() {
                return 0;
            }

            /*
             * The frame pointer is stored below the saved value for the link register.
             */
            @Override
            public int getFramePointerOffset() {
                return -2 * FrameAccess.wordSize();
            }

            @Override
            public int getStackPointerDwarfRegNum() {
                return AARCH64_SP_IDX;
            }

            @Override
            public int getFramePointerDwarfRegNum() {
                return AARCH64_FP_IDX;
            }

            @Override
            public List<String> getLLCAdditionalOptions() {
                return Collections.singletonList("--frame-pointer=all");
            }

            @Override
            public String getLLVMRegisterName(String register) {
                return register.replace("r", "x");
            }
        });
    }
}
