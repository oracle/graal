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

package com.oracle.svm.core.posix;

import static com.oracle.svm.core.posix.PosixSubstrateSigprofHandler.isSignalHandlerBasedExecutionSamplerEnabled;
import static com.oracle.svm.core.posix.PosixSubstrateSigprofHandler.Options.SignalHandlerBasedExecutionSampler;

import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.IsolateListenerSupport;
import com.oracle.svm.core.IsolateListenerSupportFeature;
import com.oracle.svm.core.RegisterDumper;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.jfr.JfrExecutionSamplerSupported;
import com.oracle.svm.core.jfr.JfrFeature;
import com.oracle.svm.core.jfr.sampler.JfrExecutionSampler;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.posix.darwin.DarwinSubstrateSigprofHandler;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.linux.LinuxSubstrateSigprofHandler;
import com.oracle.svm.core.sampler.SubstrateSigprofHandler;
import com.oracle.svm.core.thread.ThreadListenerSupport;
import com.oracle.svm.core.thread.ThreadListenerSupportFeature;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.options.Option;

/**
 * <p>
 * This class serves as the core for POSIX-based SIGPROF signal handlers.
 * </p>
 *
 * <p>
 * POSIX supports two types of timers: the global timer and per-thread timer. Both timers can
 * interrupt threads that are blocked. This may result in situations where the VM operation changes
 * unexpectedly while a thread executes signal handler code:
 * <ul>
 * <li>Thread A requests a safepoint.
 * <li>Thread B is blocked because of the safepoint but the VM did not start executing the VM
 * operation yet (i.e., there is no VM operation in progress).
 * <li>Thread B receives a SIGPROF signal and starts executing the signal handler.
 * <li>The VM reaches a safepoint and thread A starts executing the VM operation.
 * <li>Thread B continues executing the signal handler while the VM operation is now suddenly in
 * progress.
 * </ul>
 * </p>
 */
public abstract class PosixSubstrateSigprofHandler extends SubstrateSigprofHandler {
    private static final CEntryPointLiteral<Signal.AdvancedSignalDispatcher> advancedSignalDispatcher = CEntryPointLiteral.create(PosixSubstrateSigprofHandler.class,
                    "dispatch", int.class, Signal.siginfo_t.class, Signal.ucontext_t.class);

    @SuppressWarnings("unused")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = CEntryPointOptions.NoPrologue.class, epilogue = CEntryPointOptions.NoEpilogue.class)
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in sigprof signal handler.")
    @Uninterruptible(reason = "Signal handler may only execute uninterruptible code.")
    private static void dispatch(@SuppressWarnings("unused") int signalNumber, @SuppressWarnings("unused") Signal.siginfo_t sigInfo, Signal.ucontext_t uContext) {
        /* We need to keep the code in this method to a minimum to avoid races. */
        if (tryEnterIsolate()) {
            CodePointer ip = (CodePointer) RegisterDumper.singleton().getIP(uContext);
            Pointer sp = (Pointer) RegisterDumper.singleton().getSP(uContext);
            tryUninterruptibleStackWalk(ip, sp, true);
        }
    }

    @Override
    protected void installSignalHandler() {
        PosixUtils.installSignalHandler(Signal.SignalEnum.SIGPROF, advancedSignalDispatcher.getFunctionPointer(), Signal.SA_RESTART());
    }

    static boolean isSignalHandlerBasedExecutionSamplerEnabled() {
        if (SignalHandlerBasedExecutionSampler.hasBeenSet()) {
            return SignalHandlerBasedExecutionSampler.getValue();
        } else {
            return isPlatformSupported();
        }
    }

    private static boolean isPlatformSupported() {
        return (Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class)) && SubstrateOptions.EnableSignalHandling.getValue();
    }

    private static void validateSamplerOption(HostedOptionKey<Boolean> isSamplerEnabled) {
        if (isSamplerEnabled.getValue()) {
            UserError.guarantee(isPlatformSupported(),
                            "The %s cannot be used to profile on this platform.",
                            SubstrateOptionsParser.commandArgument(isSamplerEnabled, "+"));
        }
    }

    static class Options {
        @Option(help = "Determines if JFR uses a signal handler for execution sampling.")//
        public static final HostedOptionKey<Boolean> SignalHandlerBasedExecutionSampler = new HostedOptionKey<>(null, PosixSubstrateSigprofHandler::validateSamplerOption);
    }
}

@AutomaticallyRegisteredFeature
class PosixSubstrateSigProfHandlerFeature implements InternalFeature {
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(ThreadListenerSupportFeature.class, IsolateListenerSupportFeature.class, JfrFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (JfrExecutionSamplerSupported.isSupported() && isSignalHandlerBasedExecutionSamplerEnabled()) {
            SubstrateSigprofHandler sampler = makeNewSigprofHandler();
            ImageSingletons.add(JfrExecutionSampler.class, sampler);
            ImageSingletons.add(SubstrateSigprofHandler.class, sampler);

            ThreadListenerSupport.get().register(sampler);
            IsolateListenerSupport.singleton().register(sampler);
        }
    }

    private static SubstrateSigprofHandler makeNewSigprofHandler() {
        if (Platform.includedIn(Platform.LINUX.class)) {
            return new LinuxSubstrateSigprofHandler();
        } else if (Platform.includedIn(Platform.DARWIN.class)) {
            return new DarwinSubstrateSigprofHandler();
        } else {
            throw VMError.shouldNotReachHere("The JFR-based sampler is not supported on this platform.");
        }
    }
}
