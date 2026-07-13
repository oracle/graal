/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import static com.oracle.graal.pointsto.ObjectScanner.OtherReason;
import static com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import static jdk.graal.compiler.options.OptionStability.EXPERIMENTAL;

import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.code.PendingExceptionStateSupport;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.thread.ThreadListenerSupport;
import com.oracle.svm.core.threadlocal.VMThreadLocalOffsetProvider;
import com.oracle.svm.hosted.FeatureImpl.AfterAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.util.OriginalClassProvider;

import jdk.graal.compiler.core.phases.HighTier;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.BytecodeHandlerConfig;
import jdk.graal.compiler.phases.util.BytecodeInterpreterAnnotations;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Shared hosted support for bytecode-handler outlining and tail-call threading.
 * <p>
 * This feature owns the SubstrateVM backend state used by clients such as Truffle and the Crema
 * interpreter: handler stub registration, invoke outlining, tail-call handler tables, rescanning,
 * and immutability registration.
 */
@Platforms(Platform.HOSTED_ONLY.class)
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = DisallowLayered.class)
public final class BytecodeHandlerFeature implements InternalFeature {

    public static class Options {
        @Option(help = "Enable tail call threading on bytecode interpreter handlers.", stability = EXPERIMENTAL) //
        public static final HostedOptionKey<Boolean> BytecodeInterpreterTailCallThreading = new HostedOptionKey<>(true);

        @Option(help = "Fill and rewrite bytecode-handler pending-state slots with debug sentinels. Defaults to enabled when assertions are enabled.", //
                        type = OptionType.Debug, stability = EXPERIMENTAL) //
        public static final HostedOptionKey<Boolean> BytecodeHandlerSlotSentinel = new HostedOptionKey<>(SubstrateUtil.assertionsEnabled());
    }

    private final SubstrateBytecodeHandlerStubHelper stubHelper = new SubstrateBytecodeHandlerStubHelper();

    /**
     * Contains mappings of handler/interpreter methods paired with their
     * {@link BytecodeHandlerConfig} to stub wrappers. Interpreter root methods map to default stubs
     * while handler methods map to specialized stubs.
     */
    private final EconomicMap<BytecodeHandlerStubKey, ResolvedJavaMethod> registeredBytecodeHandlers = EconomicMap.create();
    private final ScanReason scanReason = new OtherReason("Manual rescan triggered from " + BytecodeHandlerFeature.class);

    private EconomicSet<ResolvedJavaMethod> bytecodeHandlers;
    private final AtomicInteger maxHandlerAbiArity = new AtomicInteger();

    public static BytecodeHandlerFeature singleton() {
        return ImageSingletons.lookup(BytecodeHandlerFeature.class);
    }

    public static boolean isTailCallThreadingEnabled() {
        return Options.BytecodeInterpreterTailCallThreading.getValue();
    }

    @Override
    public void onRegistration(OnRegistrationAccess access) {
        ImageSingletons.add(BytecodeHandlerFeature.class, this);
    }

    public boolean isBytecodeHandler(ResolvedJavaMethod method) {
        if (bytecodeHandlers == null) {
            throw new IllegalStateException("Bytecode handlers not yet initialized");
        }
        return bytecodeHandlers.contains(method);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (!ImageSingletons.contains(PendingExceptionStateSupport.class)) {
            ImageSingletons.add(PendingExceptionStateSupport.class, new PendingExceptionStateSupport());
        }
        ThreadListenerSupport.get().register(PendingExceptionStateSupport.singleton());
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        BeforeAnalysisAccessImpl accessImpl = (BeforeAnalysisAccessImpl) access;
        BytecodeInterpreterAnnotations.registerCompilerDirectives(accessImpl.getMetaAccess(), OriginalClassProvider::getOriginalType);
    }

    @Override
    public void registerGraphBuilderPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        plugins.appendNodePlugin(new BytecodeHandlerInvokePlugin(registeredBytecodeHandlers, stubHelper, isTailCallThreadingEnabled(),
                        arity -> maxHandlerAbiArity.updateAndGet(current -> Math.max(current, arity))));
    }

    @Override
    public void registerGraalPhases(Providers providers, Suites suites, boolean hosted, boolean fallback) {
        if (hosted && suites.getHighTier() instanceof HighTier) {
            suites.getHighTier().prependPhase(new SubstrateOutlineBytecodeHandlerPhase(registeredBytecodeHandlers, stubHelper));
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        bytecodeHandlers = EconomicSet.create();
        if (Options.BytecodeInterpreterTailCallThreading.getValue()) {
            AfterAnalysisAccessImpl accessImpl = (AfterAnalysisAccessImpl) access;
            stubHelper.initializeBytecodeHandlers(registeredBytecodeHandlers);
            for (MethodPointer[] handlers : stubHelper.getAllBytecodeHandlers()) {
                accessImpl.rescanObject(handlers, scanReason);
            }
            bytecodeHandlers.addAll(registeredBytecodeHandlers.getValues());
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        if (ImageSingletons.contains(VMThreadLocalOffsetProvider.class)) {
            PendingExceptionStateSupport.singleton().setMaxSlots(maxHandlerAbiArity.get());
            PendingExceptionStateSupport.singleton().setUseSlotSentinel(Options.BytecodeHandlerSlotSentinel.getValue());
            PendingExceptionStateSupport.singleton().initializeThreadLocalOffset();
        }
        if (Options.BytecodeInterpreterTailCallThreading.getValue()) {
            for (MethodPointer[] handlers : stubHelper.getAllBytecodeHandlers()) {
                access.registerAsImmutable(handlers);
            }
        }
    }
}
