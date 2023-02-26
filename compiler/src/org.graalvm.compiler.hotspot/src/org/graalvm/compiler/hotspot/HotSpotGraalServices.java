/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import static org.graalvm.compiler.debug.GraalError.shouldNotReachHere;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Objects;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.services.Services;

/**
 * Interface to HotSpot specific functionality that abstracts over which JDK version Graal is
 * running on.
 */
public class HotSpotGraalServices {

    // NOTE: The use of reflection to access JVMCI API is to support
    // compiling on JDKs with varying versions of JVMCI.

    private static final Method runtimeExitHotSpot;
    private static final Method scopeOpenLocalScope;
    private static final Method scopeEnterGlobalScope;

    private static final Constructor<? extends HotSpotSpeculationLog> hotSpotSpeculationLogConstructor;

    static {
        Method enterGlobalScope = null;
        Method openLocalScope = null;
        Method exitHotSpot = null;
        Constructor<? extends HotSpotSpeculationLog> hslConstructor = null;
        boolean firstFound = false;
        try {
            Class<?> scopeClass = Class.forName("jdk.vm.ci.hotspot.HotSpotObjectConstantScope");
            enterGlobalScope = scopeClass.getDeclaredMethod("enterGlobalScope");
            firstFound = true;
            openLocalScope = scopeClass.getDeclaredMethod("openLocalScope", Object.class);
            exitHotSpot = HotSpotJVMCIRuntime.class.getDeclaredMethod("exitHotSpot", Integer.TYPE);
            hslConstructor = HotSpotSpeculationLog.class.getDeclaredConstructor(Long.TYPE);
        } catch (Exception e) {
            // If the very first method is unavailable assume nothing is available. Otherwise only
            // some are missing so complain about it.
            if (firstFound) {
                throw new InternalError("some JVMCI features are unavailable", e);
            }
        }
        runtimeExitHotSpot = exitHotSpot;
        scopeEnterGlobalScope = enterGlobalScope;
        scopeOpenLocalScope = openLocalScope;
        hotSpotSpeculationLogConstructor = hslConstructor;
    }

    /**
     * Enters the global context. This is useful to escape a local context for execution that will
     * create foreign object references that need to outlive the local context.
     *
     * Foreign object references encapsulated by {@link JavaConstant}s created in the global context
     * are only subject to reclamation once the {@link JavaConstant} wrapper dies.
     *
     * @return {@code null} if the current runtime does not support remote object references or if
     *         this thread is currently in the global context
     */
    public static CompilationContext enterGlobalCompilationContext() {
        if (scopeEnterGlobalScope != null) {
            try {
                AutoCloseable impl = (AutoCloseable) scopeEnterGlobalScope.invoke(null);
                return impl == null ? null : new CompilationContext(impl);
            } catch (Throwable throwable) {
                throw new InternalError(throwable);
            }
        } else {
            return null;
        }
    }

    /**
     * Opens a local context that upon closing, will release foreign object references encapsulated
     * by {@link JavaConstant}s created in the context.
     *
     * @param description an non-null object whose {@link Object#toString()} value describes the
     *            context being opened
     * @return {@code null} if the current runtime does not support remote object references
     */
    public static CompilationContext openLocalCompilationContext(Object description) {
        if (scopeOpenLocalScope != null) {
            try {
                AutoCloseable impl = (AutoCloseable) scopeOpenLocalScope.invoke(null, Objects.requireNonNull(description));
                return impl == null ? null : new CompilationContext(impl);
            } catch (Throwable throwable) {
                throw new InternalError(throwable);
            }
        } else {
            return null;
        }
    }

    /**
     * Exits Graal's runtime. This calls {@link System#exit(int)} in HotSpot's runtime if possible
     * otherwise calls {@link System#exit(int)} in the current runtime.
     *
     * This exists so that the HotSpot VM can be exited from within libgraal.
     */
    public static void exit(int status, HotSpotJVMCIRuntime runtime) {
        if (Services.IS_IN_NATIVE_IMAGE) {
            try {
                runtimeExitHotSpot.invoke(runtime, status);
            } catch (Throwable throwable) {
                throw new InternalError(throwable);
            }
        } else {
            System.exit(status);
        }
    }

    public static SpeculationLog newHotSpotSpeculationLog(long cachedFailedSpeculationsAddress) {
        if (hotSpotSpeculationLogConstructor != null) {
            try {
                return hotSpotSpeculationLogConstructor.newInstance(cachedFailedSpeculationsAddress);
            } catch (Throwable e) {
                throw new InternalError(e);
            }
        } else {
            throw shouldNotReachHere();
        }
    }
}
