/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.libgraal.truffle;

import static jdk.graal.compiler.hotspot.libgraal.truffle.BuildTime.getHostMethodHandleOrFail;

import java.lang.invoke.MethodHandle;

/**
 * Methods to call Native image specific API.
 */
final class NativeImageHostCalls {

    private static final Handles HANDLES = new Handles();

    private NativeImageHostCalls() {
    }

    // libgraal to native-image upcalls

    static void initializeHost(long runtimeClass) {
        try {
            HANDLES.initializeHost.invoke(runtimeClass);
        } catch (Throwable t) {
            throw HSIndirectHandle.handleException(t);
        }
    }

    static Object createLocalHandleForLocalReference(long jniLocalHandle) {
        try {
            return HANDLES.createLocalHandleForLocalReference.invoke(jniLocalHandle);
        } catch (Throwable t) {
            throw HSIndirectHandle.handleException(t);
        }
    }

    static Object createLocalHandleForWeakGlobalReference(long jniLocalHandle) {
        try {
            return HANDLES.createLocalHandleForWeakGlobalReference.invoke(jniLocalHandle);
        } catch (Throwable t) {
            throw HSIndirectHandle.handleException(t);
        }
    }

    static Object createGlobalHandle(Object hsHandle, boolean allowGlobalDuplicates) {
        try {
            return HANDLES.createGlobalHandle.invoke(hsHandle, allowGlobalDuplicates);
        } catch (Throwable t) {
            throw HSIndirectHandle.handleException(t);
        }
    }

    static boolean isSameObject(Object o1, Object o2) {
        try {
            return (boolean) HANDLES.isSameObject.invoke(o1, o2);
        } catch (Throwable t) {
            throw HSIndirectHandle.handleException(t);
        }
    }

    static long getObjectClass(Object o) {
        try {
            return (long) HANDLES.getObjectClass.invoke(o);
        } catch (Throwable t) {
            throw HSIndirectHandle.handleException(t);
        }
    }

    static Object createTruffleCompilerOptionDescriptor(String name, int type, boolean deprecated, String help, String deprecationMessage) {
        try {
            return HANDLES.createTruffleCompilerOptionDescriptor.invoke(name, type, deprecated, help, deprecationMessage);
        } catch (Throwable t) {
            throw HSIndirectHandle.handleException(t);
        }
    }

    static void onCompilationSuccess(Object hsHandle, int compilationTier, boolean lastTier) {
        try {
            HANDLES.onCompilationSuccess.invoke(hsHandle, compilationTier, lastTier);
        } catch (Throwable t) {
            throw HSIndirectHandle.handleException(t);
        }
    }

    private static final class Handles {
        final MethodHandle initializeHost = getHostMethodHandleOrFail("initializeHost");
        final MethodHandle createLocalHandleForLocalReference = getHostMethodHandleOrFail("createLocalHandleForLocalReference");
        final MethodHandle createLocalHandleForWeakGlobalReference = getHostMethodHandleOrFail("createLocalHandleForWeakGlobalReference");
        final MethodHandle createGlobalHandle = getHostMethodHandleOrFail("createGlobalHandle");
        final MethodHandle isSameObject = getHostMethodHandleOrFail("isSameObject");
        final MethodHandle getObjectClass = getHostMethodHandleOrFail("getObjectClass");
        final MethodHandle createTruffleCompilerOptionDescriptor = getHostMethodHandleOrFail("createTruffleCompilerOptionDescriptor");
        final MethodHandle onCompilationSuccess = getHostMethodHandleOrFail("onCompilationSuccess");
    }
}
