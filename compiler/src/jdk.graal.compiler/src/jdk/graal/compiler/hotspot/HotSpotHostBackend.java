/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import static jdk.graal.compiler.core.common.NativeImageSupport.inBuildtimeCode;
import static jdk.graal.compiler.core.common.NativeImageSupport.inRuntimeCode;
import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.LEAF_NO_VZERO;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.NO_LOCATIONS;
import static jdk.vm.ci.code.CodeUtil.K;
import static jdk.vm.ci.code.CodeUtil.getCallingConvention;
import static jdk.vm.ci.common.InitTimer.timer;

import java.util.Collections;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.core.gen.LIRGenerationProvider;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugDumpHandlersFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotLoweringProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.replaycomp.ReplayCompilationSupport;
import jdk.graal.compiler.hotspot.stubs.Stub;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.framemap.FrameMapBuilder;
import jdk.graal.compiler.lir.framemap.ReferenceMapBuilder;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.truffle.hotspot.HotSpotTruffleCompilerImpl;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.runtime.JVMCICompiler;

/**
 * Common functionality of HotSpot host backends.
 */
public abstract class HotSpotHostBackend extends HotSpotBackend implements LIRGenerationProvider {

    /**
     * Descriptor for {@code SharedRuntime::deopt_blob()->unpack()}.
     */
    public static final HotSpotForeignCallDescriptor DEOPT_BLOB_UNPACK = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NO_SIDE_EFFECT, NO_LOCATIONS, "deopt_blob()->unpack()", void.class);

    /**
     * Descriptor for {@code SharedRuntime::deopt_blob()->unpack_with_exception_in_tls()}.
     */
    public static final HotSpotForeignCallDescriptor DEOPT_BLOB_UNPACK_WITH_EXCEPTION_IN_TLS = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NO_SIDE_EFFECT, NO_LOCATIONS,
                    "deopt_blob()->unpack_with_exception_in_tls()", void.class);

    /**
     * Descriptor for {@code SharedRuntime::deopt_blob()->uncommon_trap()}.
     */
    public static final HotSpotForeignCallDescriptor DEOPT_BLOB_UNCOMMON_TRAP = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NO_SIDE_EFFECT, NO_LOCATIONS, "deopt_blob()->uncommon_trap()",
                    void.class);

    public static final HotSpotForeignCallDescriptor ENABLE_STACK_RESERVED_ZONE = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NO_SIDE_EFFECT, NO_LOCATIONS, "enableStackReservedZoneEntry",
                    void.class, Word.class);

    public static final HotSpotForeignCallDescriptor THROW_DELAYED_STACKOVERFLOW_ERROR = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NO_SIDE_EFFECT, NO_LOCATIONS, "throwDelayedStackoverflowError",
                    void.class);

    /**
     * Descriptor for {@code SharedRuntime::polling_page_return_handler_blob()->entry_point()}.
     */
    public static final HotSpotForeignCallDescriptor POLLING_PAGE_RETURN_HANDLER = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NO_SIDE_EFFECT, NO_LOCATIONS,
                    "polling_page_return_handler_blob()", void.class);

    protected final GraalHotSpotVMConfig config;

    public HotSpotHostBackend(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        super(runtime, providers);
        this.config = config;
    }

    @Override
    @SuppressWarnings("try")
    public void completeInitialization(HotSpotJVMCIRuntime jvmciRuntime, OptionValues options) {
        final HotSpotProviders providers = getProviders();
        HotSpotHostForeignCallsProvider foreignCalls = providers.getForeignCalls();
        final HotSpotLoweringProvider lowerer = (HotSpotLoweringProvider) providers.getLowerer();

        try (InitTimer st = timer("foreignCalls.initialize"); DebugCloseable c = ReplayCompilationSupport.enterSnippetContext(providers)) {
            foreignCalls.initialize(providers, options);
        }
        try (InitTimer st = timer("lowerer.initialize"); DebugCloseable c = ReplayCompilationSupport.enterSnippetContext(providers)) {
            Iterable<DebugDumpHandlersFactory> factories = Collections.singletonList(new GraalDebugHandlersFactory(providers.getSnippetReflection()));
            lowerer.initialize(options, factories, providers, config);
        }
        providers.getReplacements().closeSnippetRegistration();
        providers.getReplacements().getGraphBuilderPlugins().getInvocationPlugins().maybePrintIntrinsics(options);
        maybeEncodeSnippets(options, jvmciRuntime);
    }

    /**
     * {@code true} when snippet encoding is in progress on jargraal.
     * <p>
     * The purpose of tracking whether snippets are being encoded is to allow the backend
     * initialization code to trigger the initialization of Truffle backends and break from the
     * recursion.
     */
    @LibGraalSupport.HostedOnly//
    private static boolean snippetEncodingInProgress;

    /**
     * Encodes snippets on jargraal if they are not already encoded (or being encoded by a caller).
     *
     * @param options option values
     * @param jvmciRuntime the JVMCI runtime
     */
    @SuppressWarnings("try")
    private void maybeEncodeSnippets(OptionValues options, HotSpotJVMCIRuntime jvmciRuntime) {
        if (!inRuntimeCode() && !inBuildtimeCode() && !HotSpotReplacementsImpl.snippetsAreEncoded() && !snippetEncodingInProgress) {
            try (InitTimer st = timer("encodeSnippets")) {
                GraalError.guarantee(getProviders().getReplayCompilationSupport() == null, "encode snippets without replay support");
                snippetEncodingInProgress = true;
                /*
                 * Initialize Truffle backends to register their snippets. We must perform this
                 * initialization since those backends could be initialized at any time in the
                 * future. The backends must register the snippets to our encoder so we share it
                 * with them.
                 */
                HotSpotReplacementsImpl replacements = (HotSpotReplacementsImpl) getProviders().getReplacements();
                replacements.shareSnippetEncoder();
                HotSpotGraalRuntimeProvider truffleGraalRuntime = getRuntime();
                if (HotSpotTruffleCompilerImpl.Options.TruffleCompilerConfiguration.hasBeenSet(options)) {
                    CompilerConfigurationFactory compilerConfigurationFactory = CompilerConfigurationFactory.selectFactory(
                                    HotSpotTruffleCompilerImpl.Options.TruffleCompilerConfiguration.getValue(options), options, jvmciRuntime);
                    truffleGraalRuntime = new HotSpotGraalRuntime("Truffle", jvmciRuntime, compilerConfigurationFactory, options, null);
                }
                HotSpotTruffleCompilerImpl.ensureBackendsInitialized(options, truffleGraalRuntime);
                replacements.encode(options);
            } finally {
                snippetEncodingInProgress = false;
            }
        }
    }

    protected CallingConvention makeCallingConvention(StructuredGraph graph, Stub stub) {
        if (stub != null) {
            return stub.getLinkage().getIncomingCallingConvention();
        }

        CallingConvention cc = getCallingConvention(getCodeCache(), HotSpotCallingConventionType.JavaCallee, graph.method(), this);
        if (graph.getEntryBCI() != JVMCICompiler.INVOCATION_ENTRY_BCI) {
            // for OSR, only a pointer is passed to the method.
            JavaType[] parameterTypes = new JavaType[]{getMetaAccess().lookupJavaType(long.class)};
            CallingConvention tmp = getCodeCache().getRegisterConfig().getCallingConvention(HotSpotCallingConventionType.JavaCallee, getMetaAccess().lookupJavaType(void.class), parameterTypes, this);
            cc = new CallingConvention(cc.getStackSize(), cc.getReturn(), tmp.getArgument(0));
        }
        return cc;
    }

    public void emitStackOverflowCheck(CompilationResultBuilder crb) {
        /*
         * Each code entry causes one stack bang n pages down the stack where n is configurable by
         * StackShadowPages. The setting depends on the maximum depth of VM call stack or native
         * before going back into java code, since only java code can raise a stack overflow
         * exception using the stack banging mechanism. The VM and native code does not detect stack
         * overflow. The code in JavaCalls::call() checks that there is at least n pages available,
         * so all entry code needs to do is bang once for the end of this shadow zone. The entry
         * code may need to bang additional pages if the framesize is greater than a page.
         */

        int pageSize = config.vmPageSize;
        int bangEnd = NumUtil.roundUp(config.stackShadowPages * 4 * K, pageSize);

        // This is how far the previous frame's stack banging extended.
        int bangEndSafe = bangEnd;

        int frameSize = Math.max(crb.frameMap.frameSize(), crb.compilationResult.getMaxInterpreterFrameSize());
        if (frameSize > pageSize) {
            bangEnd += frameSize;
        }

        int bangOffset = bangEndSafe;
        if (bangOffset <= bangEnd) {
            crb.blockComment("[stack overflow check]");
        }
        while (bangOffset <= bangEnd) {
            // Need at least one stack bang at end of shadow zone.
            bangStackWithOffset(crb, bangOffset);
            bangOffset += pageSize;
        }
    }

    protected abstract void bangStackWithOffset(CompilationResultBuilder crb, int bangOffset);

    @Override
    public ReferenceMapBuilder newReferenceMapBuilder(int totalFrameSize) {
        int uncompressedReferenceSize = getTarget().arch.getPlatformKind(JavaKind.Object).getSizeInBytes();
        return new HotSpotReferenceMapBuilder(totalFrameSize, config.maxOopMapStackOffset, uncompressedReferenceSize);
    }

    @Override
    public LIRGenerationResult newLIRGenerationResult(CompilationIdentifier compilationId, LIR lir, RegisterAllocationConfig registerAllocationConfig, StructuredGraph graph, Object stub) {
        return new HotSpotLIRGenerationResult(compilationId, lir, newFrameMapBuilder(registerAllocationConfig.getRegisterConfig(), (Stub) stub), registerAllocationConfig,
                        makeCallingConvention(graph, (Stub) stub), (Stub) stub, config.requiresReservedStackCheck(graph.getMethods()));
    }

    protected abstract FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig, Stub stub);
}
