/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging.jdk;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.VMRuntimeSupport;

import com.oracle.svm.guest.staging.GuestStagingDependencyBridge;
import com.oracle.svm.guest.staging.SubstrateGuestOptions;
import com.oracle.svm.guest.staging.option.RuntimeOptionValidationSupport;
import com.oracle.svm.shared.meta.GuestFold;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

/**
 * Manages VM-level lifecycle hooks for an isolate. These hooks are internal runtime callbacks and
 * are distinct from Java-level shutdown hooks registered with {@link Runtime#addShutdownHook}.
 * <p>
 * The VM-level hooks are executed in the following order:
 * <ol>
 * <li>Initialization hooks (always executed)</li>
 * <li>Startup hooks (only executed if {@link #initialize} is called)</li>
 * <li>Teardown hooks (always executed on isolate teardown, except for abnormal termination)</li>
 * </ol>
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public final class RuntimeSupport implements VMRuntimeSupport {

    @FunctionalInterface
    public interface Hook {
        void execute(boolean isFirstIsolate);
    }

    private final AtomicReference<InitializationState> initializationState = new AtomicReference<>(InitializationState.Uninitialized);
    private final AtomicReference<Hook[]> initializationHooks = new AtomicReference<>();
    private final AtomicReference<Hook[]> startupHooks = new AtomicReference<>();
    private final AtomicReference<Hook[]> tearDownHooks = new AtomicReference<>();

    @Platforms(Platform.HOSTED_ONLY.class)
    public RuntimeSupport() {
    }

    @GuestFold
    public static RuntimeSupport getRuntimeSupport() {
        return ImageSingletons.lookup(RuntimeSupport.class);
    }

    /**
     * Adds a VM-level startup hook that executes <b>after</b> the isolate is fully
     * {@link #initialize initialized}. Argument parsing is finished at that point, so it is safe to
     * access options and environment variables in such hooks. However, execution of VM-level
     * startup hooks may be skipped if {@link SubstrateGuestOptions#InitializeVM} is disabled and
     * {@link #initialize} is not called manually.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public void addStartupHook(Hook hook) {
        addHook(startupHooks, hook);
    }

    public boolean isUninitialized() {
        return initializationState.get() == InitializationState.Uninitialized;
    }

    @Override
    public void initialize() {
        boolean shouldInitialize = initializationState.compareAndSet(InitializationState.Uninitialized, InitializationState.InProgress);
        if (shouldInitialize) {
            RuntimeOptionValidationSupport.singleton().validate();

            GuestStagingDependencyBridge.singleton().verifyIsolateArgumentOptionValues();
            GuestStagingDependencyBridge.singleton().verifyHeapOptions();

            executeHooks(startupHooks);
            VMError.guarantee(initializationState.compareAndSet(InitializationState.InProgress, InitializationState.Done), "Only one thread can call the initialization");
        } else if (initializationState.get() != InitializationState.Done) {
            throw VMError.shouldNotReachHere("Only one thread can call the initialization");
        }
    }

    /**
     * Adds a VM-level initialization hook. Initialization hooks are executed during isolate
     * initialization, before runtime options are parsed. The executed code should therefore not
     * try to access any runtime options. If it is necessary to access a runtime option, then its
     * value must be parsed early and accessed via
     * {@code com.oracle.svm.core.IsolateArgumentParser}.
     */
    public void addInitializationHook(Hook initHook) {
        addHook(initializationHooks, initHook);
    }

    public static void executeInitializationHooks() {
        executeHooks(getRuntimeSupport().initializationHooks);
    }

    /**
     * Adds a VM-level teardown hook that executes during isolate teardown. Teardown hooks may run
     * even if initialization hooks or startup hooks did not run.
     */
    public void addTearDownHook(Hook tearDownHook) {
        addHook(tearDownHooks, tearDownHook);
    }

    public static void executeTearDownHooks() {
        Hook[] hooks = getRuntimeSupport().tearDownHooks.getAndSet(null);
        if (hooks != null) {
            boolean firstIsolate = GuestStagingDependencyBridge.singleton().isCurrentFirstIsolate();
            for (Hook hook : hooks) {
                try {
                    hook.execute(firstIsolate);
                } catch (Throwable ignored) {
                    /* We can't do much during teardown. */
                }
            }
        }

        /*
         * Execute the LogManager shutdown hook after all other hooks. This is a workaround for
         * GR-39429, as stderr might be closed afterwards.
         */
        GuestStagingDependencyBridge.singleton().runLogManagerShutdownHook();
    }

    private static void addHook(AtomicReference<Hook[]> hooksReference, Hook newHook) {
        Objects.requireNonNull(newHook);

        Hook[] existingHooks;
        Hook[] newHooks;
        do {
            existingHooks = hooksReference.get();
            if (existingHooks != null) {
                newHooks = Arrays.copyOf(existingHooks, existingHooks.length + 1);
                newHooks[newHooks.length - 1] = newHook;
            } else {
                newHooks = new Hook[]{newHook};
            }
        } while (!hooksReference.compareAndSet(existingHooks, newHooks));
    }

    private static void executeHooks(AtomicReference<Hook[]> hooksReference) {
        Hook[] hooks = hooksReference.getAndSet(null);
        if (hooks != null) {
            boolean firstIsolate = GuestStagingDependencyBridge.singleton().isCurrentFirstIsolate();
            for (Hook hook : hooks) {
                hook.execute(firstIsolate);
            }
        }
    }

    /**
     * Runs Java-level shutdown hooks that were registered via the JDK API (unlike the VM-internal
     * hooks registered in this class). This method is invoked even when {@link #initialize} is not
     * (e.g., when option {@link SubstrateGuestOptions#InitializeVM} is disabled).
     */
    @Override
    public void shutdown() {
        GuestStagingDependencyBridge.singleton().runJavaShutdownHooks();
    }

    private enum InitializationState {
        Uninitialized,
        InProgress,
        Done
    }
}
