/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.darwin;

import static com.oracle.svm.core.posix.headers.darwin.CoreFoundation.CFRunLoopAddTimer;
import static com.oracle.svm.core.posix.headers.darwin.CoreFoundation.CFRunLoopGetCurrent;
import static com.oracle.svm.core.posix.headers.darwin.CoreFoundation.CFRunLoopRunInMode;
import static com.oracle.svm.core.posix.headers.darwin.CoreFoundation.CFRunLoopTimerCreate;
import static com.oracle.svm.core.posix.headers.darwin.CoreFoundation.CFRelease;
import static com.oracle.svm.core.posix.headers.darwin.CoreFoundation.kCFAllocatorDefault;
import static com.oracle.svm.core.posix.headers.darwin.CoreFoundation.kCFRunLoopDefaultMode;
import static com.oracle.svm.core.posix.headers.darwin.CoreFoundation.kCFRunLoopRunFinished;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.DarwinMainCFRunLoopSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.posix.headers.darwin.CoreFoundation.CFRunLoopTimerCallBack;
import com.oracle.svm.core.posix.headers.darwin.CoreFoundation.CFRunLoopTimerRef;
import com.oracle.svm.guest.staging.c.function.CEntryPointOptions;
import com.oracle.svm.guest.staging.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.guest.staging.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import org.graalvm.nativeimage.ImageSingletons;

/**
 * Parks the process main thread in a CFRunLoop, matching OpenJDK libjli /
 * {@code sdk/.../launcher.cc} {@code ParkEventLoop}.
 *
 * @see <a href="https://github.com/oracle/graal/issues/13994">oracle/graal#13994</a>
 */
@Platforms(Platform.DARWIN.class)
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public final class DarwinMainCFRunLoopSupportImpl implements DarwinMainCFRunLoopSupport {

    /**
     * Far-future fire date so the timer exists only as a run-loop source (libjli pattern).
     */
    private static final double FAR_FUTURE = 1.0e20;

    private static final CEntryPointLiteral<CFRunLoopTimerCallBack> DUMMY_TIMER_CALLBACK = CEntryPointLiteral.create(DarwinMainCFRunLoopSupportImpl.class, "dummyTimer",
                    CFRunLoopTimerRef.class, VoidPointer.class);

    @Override
    @Uninterruptible(reason = "Called while the launcher thread is detached from the isolate.")
    public void parkEventLoop() {
        /*
         * See OpenJDK java_md_macosx.m ParkEventLoop and Graal launcher.cc ParkEventLoop: the run
         * loop needs at least one source; 1e20 is far into the future.
         */
        CFRunLoopTimerRef timer = CFRunLoopTimerCreate(kCFAllocatorDefault(), FAR_FUTURE, 0.0, 0, 0, DUMMY_TIMER_CALLBACK.getFunctionPointer(), WordFactory.nullPointer());
        CFRunLoopAddTimer(CFRunLoopGetCurrent(), timer, kCFRunLoopDefaultMode());
        CFRelease(timer);

        int result;
        do {
            result = CFRunLoopRunInMode(kCFRunLoopDefaultMode(), FAR_FUTURE, false);
        } while (result != kCFRunLoopRunFinished());
    }

    @SuppressWarnings("unused")
    @Uninterruptible(reason = "Called from CFRunLoop.")
    @CEntryPoint(include = DarwinParkMainInCFRunLoopEnabled.class)
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
    static void dummyTimer(CFRunLoopTimerRef timer, VoidPointer info) {
        /* Intentionally empty — timer exists only to keep the run loop alive. */
    }

    private static final class DarwinParkMainInCFRunLoopEnabled implements java.util.function.BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return SubstrateOptions.DarwinParkMainInCFRunLoop.getValue();
        }
    }
}

@Platforms(Platform.DARWIN.class)
@AutomaticallyRegisteredFeature
class DarwinMainCFRunLoopFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.DarwinParkMainInCFRunLoop.getValue();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (!Platform.includedIn(Platform.DARWIN.class)) {
            throw VMError.shouldNotReachHere("-H:+DarwinParkMainInCFRunLoop is only supported on Darwin");
        }
        ImageSingletons.add(DarwinMainCFRunLoopSupport.class, new DarwinMainCFRunLoopSupportImpl());
    }
}
