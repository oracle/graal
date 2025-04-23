/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.IsolateListenerSupport;
import com.oracle.svm.core.IsolateListenerSupportFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.JfrExecutionSamplerSupported;
import com.oracle.svm.core.jfr.JfrFeature;
import com.oracle.svm.core.jfr.sampler.JfrExecutionSampler;
import com.oracle.svm.core.posix.linux.LinuxSubstrateSigprofHandler;
import com.oracle.svm.core.sampler.SubstrateSigprofHandler;
import com.oracle.svm.core.thread.ThreadListenerSupport;
import com.oracle.svm.core.thread.ThreadListenerSupportFeature;
import com.oracle.svm.core.util.VMError;

/**
 * This feature is part of POSIX-based signal handling (see {@link PosixSubstrateSigprofHandler} as
 * well) and is present in the configuration if the required features are there. We can't override
 * the {@link #isInConfiguration} method because the checks depend on singletons, which are only
 * present in the {@link #afterRegistration} phase. The checks that we perform later also depend on
 * whether JFR support is present (see {@link JfrFeature}), since the JFR sampling is the main
 * consumer of POSIX-based signal handling.
 */
@AutomaticallyRegisteredFeature
public class PosixSubstrateSigprofHandlerFeature implements InternalFeature {

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(ThreadListenerSupportFeature.class, IsolateListenerSupportFeature.class, JfrFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (JfrExecutionSamplerSupported.isSupported() && isSignalHandlerBasedExecutionSamplerEnabled() && shouldUseAsyncSampler()) {
            SubstrateSigprofHandler sampler = makeNewSigprofHandler();
            ImageSingletons.add(JfrExecutionSampler.class, sampler);
            ImageSingletons.add(SubstrateSigprofHandler.class, sampler);

            ThreadListenerSupport.get().register(sampler);
            IsolateListenerSupport.singleton().register(sampler);
        }
    }

    protected boolean shouldUseAsyncSampler() {
        return true;
    }

    /**
     * <p>
     * There are two ways to handle SIGPROF signals. The first is using a global handler (see
     * {@link PosixSubstrateGlobalSigprofHandler}), where only one thread is responsible for
     * handling the signal each time the timer expires, resulting in a single sample. The second
     * method is using a per-thread handler (see {@link LinuxSubstrateSigprofHandler}), where each
     * thread must handle the signal after the timer expires. Note that per-thread signal handling
     * is supported only on Linux.
     * </p>
     * <p>
     * For JFR, we should use a global handler instead of a per-thread handler to adhere to the
     * sampling frequency specified in .jfc (JFR's configuration).
     * </p>
     */
    private static SubstrateSigprofHandler makeNewSigprofHandler() {
        if (Platform.includedIn(Platform.DARWIN.class) || HasJfrSupport.get()) {
            return new PosixSubstrateGlobalSigprofHandler();
        } else if (Platform.includedIn(Platform.LINUX.class)) {
            return new LinuxSubstrateSigprofHandler();
        } else {
            throw VMError.shouldNotReachHere("The JFR-based sampler is not supported on this platform.");
        }
    }
}
