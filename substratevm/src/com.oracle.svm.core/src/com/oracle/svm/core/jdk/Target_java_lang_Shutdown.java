/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jfr.events.ShutdownEvent;
import com.oracle.svm.guest.staging.jdk.RuntimeSupport;
import com.oracle.svm.shared.util.VMError;

@TargetClass(className = "java.lang.Shutdown")
public final class Target_java_lang_Shutdown {
    /**
     * Re-initialize the map of registered hooks, because any hooks registered during native image
     * construction can not survive into the running image.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias)//
    static Runnable[] hooks = new Runnable[Util_java_lang_Shutdown.MAX_SYSTEM_HOOKS];

    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClassName = "java.lang.Shutdown$Lock")//
    static Object lock;

    @Substitute
    public static void beforeHalt() {
        ShutdownEvent.emit("Shutdown requested from Java", true);
    }

    /**
     * Invoked by the JNI DestroyJavaVM procedure when the last non-daemon thread has finished.
     * Unlike the exit method, this method only calls {@link #runHooks()} and does not actually
     * halt the VM.
     */
    @Alias
    static native void shutdown();

    @Substitute
    @TargetElement
    @SuppressWarnings("unused")
    private static void logRuntimeExit(int status) {
        // Disable exit logging (GR-45418/JDK-8301627)
    }

    /** Runs Java-level shutdown hooks. Does not run any isolate teardown hooks. */
    @Alias
    static native void runHooks();

    @Alias
    static native void halt(int status);

    @Substitute
    static void exit(int status) {
        logRuntimeExit(status);

        synchronized (Target_java_lang_Shutdown.class) {
            beforeHalt();
            runHooks();
            /* halt() stops the process abnormally, so we need to run the teardown hooks explicitly. */
            RuntimeSupport.executeTearDownHooks();
            halt(status);
        }
    }
}

/** Utility methods for Target_java_lang_Shutdown. */
final class Util_java_lang_Shutdown {

    /**
     * Value *copied* from {@code java.lang.Shutdown.MAX_SYSTEM_HOOKS} so that the value can be used
     * during image generation (@Alias values are only visible at run time). The JDK currently uses
     * slots 0, 1, and 2.
     */
    static final int MAX_SYSTEM_HOOKS = 10;

    private static Runnable logManagerShutdownHook;

    static void runLogManagerShutdownHook() {
        try {
            Runnable hook;
            synchronized (Target_java_lang_Shutdown.lock) {
                hook = logManagerShutdownHook;
                logManagerShutdownHook = null;
            }
            if (hook != null) {
                hook.run();
            }
        } catch (Throwable ignored) {
            /* Ignore exceptions in shutdown hooks, matching the JDK behavior. */
        }
    }

    public static void registerLogManagerShutdownHook(Runnable hook) {
        synchronized (Target_java_lang_Shutdown.lock) {
            VMError.guarantee(logManagerShutdownHook == null, "LogManager shutdown hook must not be registered twice");
            logManagerShutdownHook = hook;
        }
    }
}
