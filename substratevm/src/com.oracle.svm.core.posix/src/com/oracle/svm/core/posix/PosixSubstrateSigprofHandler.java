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
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.IsolateListenerSupport;
import com.oracle.svm.core.IsolateListenerSupportFeature;
import com.oracle.svm.core.RegisterDumper;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.jfr.JfrExecutionSamplerSupported;
import com.oracle.svm.core.jfr.JfrFeature;
import com.oracle.svm.core.jfr.sampler.JfrExecutionSampler;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.sampler.SubstrateSigprofHandler;
import com.oracle.svm.core.thread.ThreadListenerSupport;
import com.oracle.svm.core.thread.ThreadListenerSupportFeature;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.UserError;

import jdk.graal.compiler.options.Option;

public final class PosixSubstrateSigprofHandler extends SubstrateSigprofHandler {
    private static final CEntryPointLiteral<Signal.AdvancedSignalDispatcher> advancedSignalDispatcher = CEntryPointLiteral.create(PosixSubstrateSigprofHandler.class,
                    "dispatch", int.class, Signal.siginfo_t.class, Signal.ucontext_t.class);

    @Platforms(Platform.HOSTED_ONLY.class)
    public PosixSubstrateSigprofHandler() {
    }

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
            tryUninterruptibleStackWalk(ip, sp);
        }
    }

    private static void registerSigprofSignal(Signal.AdvancedSignalDispatcher dispatcher) {
        PosixUtils.installSignalHandler(Signal.SignalEnum.SIGPROF, dispatcher, Signal.SA_RESTART());
    }

    @Override
    protected void updateInterval() {
        updateInterval(TimeUtils.millisToMicros(newIntervalMillis));
    }

    public static void updateInterval(long us) {
        Time.itimerval newValue = UnsafeStackValue.get(Time.itimerval.class);
        newValue.it_value().set_tv_sec(us / TimeUtils.microsPerSecond);
        newValue.it_value().set_tv_usec(us % TimeUtils.microsPerSecond);
        newValue.it_interval().set_tv_sec(us / TimeUtils.microsPerSecond);
        newValue.it_interval().set_tv_usec(us % TimeUtils.microsPerSecond);

        int status = Time.NoTransitions.setitimer(Time.TimerTypeEnum.ITIMER_PROF, newValue, WordFactory.nullPointer());
        PosixUtils.checkStatusIs0(status, "setitimer(which, newValue, oldValue): wrong arguments.");
    }

    @Override
    protected void installSignalHandler() {
        registerSigprofSignal(advancedSignalDispatcher.getFunctionPointer());
        updateInterval();
    }

    @Override
    protected void uninstallSignalHandler() {
        /*
         * Only disable the sampling but do not replace the signal handler with the default one
         * because a signal might be pending for some thread (the default signal handler would print
         * "Profiling timer expired" to the output).
         */
        updateInterval(0);
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
            SubstrateSigprofHandler sampler = new PosixSubstrateSigprofHandler();
            ImageSingletons.add(JfrExecutionSampler.class, sampler);
            ImageSingletons.add(SubstrateSigprofHandler.class, sampler);

            ThreadListenerSupport.get().register(sampler);
            IsolateListenerSupport.singleton().register(sampler);
        }
    }
}
