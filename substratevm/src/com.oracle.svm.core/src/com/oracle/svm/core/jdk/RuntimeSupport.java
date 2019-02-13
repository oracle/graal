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
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.CompilerCommandPlugin;
import com.oracle.svm.core.util.VMError;
import org.graalvm.nativeimage.impl.VMRuntimeSupport;

public final class RuntimeSupport implements VMRuntimeSupport {

    /** A list of startup hooks. */
    private AtomicReference<Runnable[]> startupHooks;

    /** A list of shutdown hooks. */
    private AtomicReference<Runnable[]> shutdownHooks;

    /** A list of tear down hooks. */
    private AtomicReference<Runnable[]> tearDownHooks;

    /** A list of CompilerCommandPlugins. */
    private static final Comparator<CompilerCommandPlugin> PluginComparator = Comparator.comparing(CompilerCommandPlugin::name);
    private CopyOnWriteArrayList<CompilerCommandPlugin> commandPlugins;
    @Platforms(Platform.HOSTED_ONLY.class)//
    private boolean commandPluginsSorted;

    /** A constructor for the singleton instance. */
    private RuntimeSupport() {
        super();
        startupHooks = new AtomicReference<>();
        shutdownHooks = new AtomicReference<>();
        tearDownHooks = new AtomicReference<>();
        commandPlugins = new CopyOnWriteArrayList<>();
        commandPluginsSorted = false;
    }

    /** Construct and register the singleton instance, if necessary. */
    public static void initializeRuntimeSupport() {
        assert ImageSingletons.contains(RuntimeSupport.class) == false : "Initializing RuntimeSupport again.";
        ImageSingletons.add(RuntimeSupport.class, new RuntimeSupport());
    }

    /** Get the singleton instance. */
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

    /**
     * Called only internally as part of the JDK shutdown process, use {@link #shutdown()} to
     * trigger the whoke JDK shutdown process.
     */
    static void executeShutdownHooks() {
        executeHooks(getRuntimeSupport().shutdownHooks);
    }

    /**
     * Adds a tear down hook that is executed before the isolate torn down.
     *
     * @param tearDownHook hook to executed on isolate tear down.
     */
    public void addTearDownHook(Runnable tearDownHook) {
        addHook(tearDownHooks, tearDownHook);
    }

    /**
     * Called only internally as part of the isolate tear down process. These hooks clean up all
     * running threads to allow proper isolate tear down.
     *
     * Although public, this method should not go to the public API.
     */
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

    @Platforms(Platform.HOSTED_ONLY.class)
    public void addCommandPlugin(CompilerCommandPlugin plugin) {
        assert !commandPluginsSorted;
        if (commandPlugins.stream().anyMatch(p -> p.name().equals(plugin.name()))) {
            throw new IllegalArgumentException("CompilerCommandPlugin previously registered");
        }

        commandPlugins.add(plugin);
    }

    Object runCommand(String cmd, Object[] args) {
        CompilerCommandPlugin key = new CompilerCommandPlugin() {

            @Override
            public String name() {
                return cmd;
            }

            @Override
            public Object apply(Object[] ignore) {
                throw VMError.shouldNotReachHere();
            }
        };
        int index = Collections.binarySearch(commandPlugins, key, PluginComparator);
        if (index >= 0) {
            return commandPlugins.get(index).apply(args);
        }
        throw new IllegalArgumentException("Could not find SVM command with the name " + cmd);
    }

    @Override
    public void shutdown() {
        Target_java_lang_Shutdown.shutdown();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    void sortCommandPlugins() {
        commandPlugins.sort(PluginComparator);
        assert !commandPluginsSorted;
        commandPluginsSorted = true;
    }
}
