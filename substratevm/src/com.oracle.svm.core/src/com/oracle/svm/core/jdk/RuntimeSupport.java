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
package com.oracle.svm.core.jdk;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.nativeimage.impl.VMRuntimeSupport;

import com.oracle.svm.core.IsolateArgumentParser;
import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.heap.HeapSizeVerifier;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

@AutomaticallyRegisteredImageSingleton({VMRuntimeSupport.class, RuntimeSupport.class})
public final class RuntimeSupport implements VMRuntimeSupport {
    @FunctionalInterface
    public interface Hook {
        void execute(boolean isFirstIsolate);
    }

    private final AtomicReference<InitializationState> initializationState = new AtomicReference<>(InitializationState.Uninitialized);

    /** Hooks that run before calling Java {@code main} or in {@link VMRuntime#initialize()}. */
    private final AtomicReference<Hook[]> startupHooks = new AtomicReference<>();

    /**
     * Hooks that run after the Java {@code main} method or when calling {@link Runtime#exit} (or
     * {@link System#exit}).
     *
     * Note that it is possible for shutdownHooks to be called even if the {@link #startupHooks}
     * have not executed.
     */
    private final AtomicReference<Hook[]> shutdownHooks = new AtomicReference<>();

    /** Hooks that run during isolate initialization. */
    private final AtomicReference<Hook[]> initializationHooks = new AtomicReference<>();

    /**
     * Hooks that run during isolate tear-down. Note it is possible for these hooks to run even if
     * the {@link #initializationHooks} have not executed.
     */
    private final AtomicReference<Hook[]> tearDownHooks = new AtomicReference<>();

    @Platforms(Platform.HOSTED_ONLY.class)
    RuntimeSupport() {
    }

    @Fold
    public static RuntimeSupport getRuntimeSupport() {
        return ImageSingletons.lookup(RuntimeSupport.class);
    }

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
            IsolateArgumentParser.verifyOptionValues();
            HeapSizeVerifier.verifyHeapOptions();

            executeHooks(startupHooks);
            VMError.guarantee(initializationState.compareAndSet(InitializationState.InProgress, InitializationState.Done), "Only one thread can call the initialization");
        } else if (initializationState.get() != InitializationState.Done) {
            throw VMError.shouldNotReachHere("Only one thread can call the initialization");
        }
    }

    /**
     * Adds a hook which will execute during the shutdown process. Note it is possible for the
     * {@link #shutdownHooks} to be called without the {@link #startupHooks} executing first.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public void addShutdownHook(Hook hook) {
        addHook(shutdownHooks, hook);
    }

    static void executeShutdownHooks() {
        executeHooks(getRuntimeSupport().shutdownHooks);
    }

    /**
     * Initialization hooks are executed during isolate initialization, before runtime options are
     * parsed. The executed code should therefore not try to access any runtime options. If it is
     * necessary to access a runtime option, then its value must be accessed via
     * {@link com.oracle.svm.core.IsolateArgumentParser}.
     */
    public void addInitializationHook(Hook initHook) {
        addHook(initializationHooks, initHook);
    }

    /** Runs isolate initialization hooks. Although public, this method should not be public API. */
    public static void executeInitializationHooks() {
        executeHooks(getRuntimeSupport().initializationHooks);
    }

    /**
     * Adds a hook which will execute during isolate tear-down. Note it is possible for the
     * {@link #tearDownHooks} to be called without the {@link #initializationHooks} executing first.
     */
    public void addTearDownHook(Hook tearDownHook) {
        addHook(tearDownHooks, tearDownHook);
    }

    /** Runs isolate tear-down hooks. Although public, this method should not be public API. */
    public static void executeTearDownHooks() {
        executeHooks(getRuntimeSupport().tearDownHooks);
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
            boolean firstIsolate = Isolates.isCurrentFirst();
            for (Hook hook : hooks) {
                hook.execute(firstIsolate);
            }
        }
    }

    @Override
    public void shutdown() {
        Target_java_lang_Shutdown.shutdown();
    }

    private enum InitializationState {
        Uninitialized,
        InProgress,
        Done
    }
}
