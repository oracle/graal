/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler;

import java.lang.reflect.Method;
import java.util.Objects;

import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;

/**
 * Represents the compiler environment for Truffle compilers created in this process/isolate. There
 * is exactly one compiler environment at a time so this object is stored as singleton.
 *
 * Only use {@link #get() singleton} access if you do not have access to a
 * {@link TruffleCompilerConfiguration} instance. Otherwise make sure you access relative to that
 * instead.
 */
// GR-44222 refactor the code to be less dependent on the singleton
public abstract class TruffleCompilerEnvironment {

    private static TruffleCompilerEnvironment current;
    private static final Object RUNTIME = initRuntime();

    private final TruffleCompilerRuntime runtime;
    private final KnownTruffleTypes types;

    protected TruffleCompilerEnvironment(TruffleCompilerRuntime runtime, KnownTruffleTypes types) {
        this.runtime = runtime;
        this.types = types;
    }

    public static boolean isInitialized() {
        return current != null || RUNTIME != null;
    }

    public KnownTruffleTypes types() {
        return this.types;
    }

    public TruffleCompilerRuntime runtime() {
        return this.runtime;
    }

    public static TruffleCompilerEnvironment get() {
        TruffleCompilerEnvironment env = getIfInitialized();
        Objects.requireNonNull(env);
        return env;
    }

    public static TruffleCompilerEnvironment getIfInitialized() {
        TruffleCompilerEnvironment env = current;
        if (env != null) {
            return env;
        }
        Object runtime = RUNTIME;
        if (runtime != null) {
            env = initEnvironment(runtime);
        }
        return env;
    }

    public static synchronized void initialize(TruffleCompilerEnvironment environment) {
        current = environment;
    }

    /*
     * This method may call reflectively call back into truffle to eagerly initialize it if it is
     * available.
     */
    private static Object initRuntime() {
        try {
            Class<?> truffleClass = Class.forName("com.oracle.truffle.api.Truffle");
            Method getRuntime = truffleClass.getMethod("getRuntime");
            Object runtime = getRuntime.invoke(null);
            if (runtime instanceof TruffleCompilerRuntime) {
                return runtime;
            } else {
                return null;
            }
        } catch (Error e) {
            throw e;
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static synchronized TruffleCompilerEnvironment initEnvironment(Object runtime) {
        if (current != null) {
            return current;
        }
        try {
            Method m = runtime.getClass().getMethod("createCompilerEnvironment");
            m.setAccessible(true);
            TruffleCompilerEnvironment env = (TruffleCompilerEnvironment) m.invoke(runtime);
            current = env;
            return env;
        } catch (Error e) {
            throw e;
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

}
