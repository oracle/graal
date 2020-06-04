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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.nativeimage.impl.VMRuntimeSupport;

public final class RuntimeSupport implements VMRuntimeSupport {

    /** Hooks that run before calling Java {@code main} or in {@link VMRuntime#initialize()}. */
    private AtomicReference<Runnable[]> startupHooks = new AtomicReference<>();

    /**
     * Hooks that run after the Java {@code main} method or when calling {@link Runtime#exit} (or
     * {@link System#exit}).
     */
    private AtomicReference<Runnable[]> shutdownHooks = new AtomicReference<>();

    /** Hooks that run during isolate initialization. */
    private AtomicReference<Runnable[]> initializationHooks = new AtomicReference<>();

    /** Hooks that run during isolate tear-down. */
    private AtomicReference<Runnable[]> tearDownHooks = new AtomicReference<>();

    private RuntimeSupport() {
    }

    public static void initializeRuntimeSupport() {
        assert ImageSingletons.contains(RuntimeSupport.class) == false : "Initializing RuntimeSupport again.";
        ImageSingletons.add(RuntimeSupport.class, new RuntimeSupport());
    }

    @Fold
    public static RuntimeSupport getRuntimeSupport() {
        return ImageSingletons.lookup(RuntimeSupport.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void addStartupHook(Runnable hook) {
        addHook(startupHooks, hook);
    }

    @Override
    public void executeStartupHooks() {
        executeHooks(startupHooks);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void addShutdownHook(Runnable hook) {
        addHook(shutdownHooks, hook);
    }

    static void executeShutdownHooks() {
        executeHooks(getRuntimeSupport().shutdownHooks);
    }

    public void addInitializationHook(Runnable initHook) {
        addHook(initializationHooks, initHook);
    }

    /** Runs isolate initialization hooks. Although public, this method should not be public API. */
    public static void executeInitializationHooks() {
        executeHooks(getRuntimeSupport().initializationHooks);
    }

    public void addTearDownHook(Runnable tearDownHook) {
        addHook(tearDownHooks, tearDownHook);
    }

    /** Runs isolate tear-down hooks. Although public, this method should not be public API. */
    public static void executeTearDownHooks() {
        executeHooks(getRuntimeSupport().tearDownHooks);
    }

    private static void addHook(AtomicReference<Runnable[]> hooksReference, Runnable newHook) {
        Objects.requireNonNull(newHook);

        Runnable[] existingHooks;
        Runnable[] newHooks;
        do {
            existingHooks = hooksReference.get();
            if (existingHooks != null) {
                newHooks = Arrays.copyOf(existingHooks, existingHooks.length + 1);
                newHooks[newHooks.length - 1] = newHook;
            } else {
                newHooks = new Runnable[]{newHook};
            }
        } while (!hooksReference.compareAndSet(existingHooks, newHooks));
    }

    private static void executeHooks(AtomicReference<Runnable[]> hooksReference) {
        Runnable[] hooks = hooksReference.getAndSet(null);
        if (hooks != null) {
            for (Runnable hook : hooks) {
                hook.run();
            }
        }
    }

    @Override
    public void shutdown() {
        Target_java_lang_Shutdown.shutdown();
    }

}
