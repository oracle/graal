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
package com.oracle.svm.graal.hotspot.guestgraal;

import com.oracle.truffle.compiler.TruffleCompilerOptionDescriptor;
import jdk.graal.compiler.debug.GraalError;
import org.graalvm.jniutils.HSObject;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Map;

import static java.lang.invoke.MethodType.methodType;

public final class TruffleNativeHostMethods {

    private TruffleNativeHostMethods() {
    }

    public static Object createHandleForLocalReference(long jniLocalRef) {
        JNIMethodScope scope = JNIMethodScope.scopeOrNull();
        if (scope == null) {
            return null;
        }
        return new HSObject(scope, WordFactory.pointer(jniLocalRef));
    }

    public static Object createHandleForWeakGlobalReference(long jniWeakGlobalRef) {
        JNIMethodScope scope = JNIMethodScope.scope();
        JObject localRef = JNIUtil.NewLocalRef(scope.getEnv(), WordFactory.pointer(jniWeakGlobalRef));
        return localRef.isNull() ? null : new HSObject(scope, localRef);
    }

    public static boolean isSameObject(Object o1, Object o2) {
        return JNIUtil.IsSameObject(JNIMethodScope.env(), ((HSObject) o1).getHandle(), ((HSObject) o2).getHandle());
    }

    public static long getObjectClass(Object o) {
        return JNIUtil.GetObjectClass(JNIMethodScope.env(), ((HSObject) o).getHandle()).rawValue();
    }

    public static Object createTruffleCompilerOptionDescriptor(String name, int type, boolean deprecated, String help, String deprecationMessage) {
        return new TruffleCompilerOptionDescriptor(name, TruffleCompilerOptionDescriptor.Type.values()[type], deprecated, help, deprecationMessage);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static Map<String, MethodHandle> getUpCallHandles() {
        MethodHandles.Lookup lkp = MethodHandles.lookup();
        try {
            return Map.of("RunTime#createHandleForLocalReference", lkp.findStatic(TruffleNativeHostMethods.class, "createHandleForLocalReference", methodType(Object.class, long.class)),
                            "RunTime#createHandleForWeakGlobalReference", lkp.findStatic(TruffleNativeHostMethods.class, "createHandleForWeakGlobalReference", methodType(Object.class, long.class)),
                            "RunTime#isSameObject", lkp.findStatic(TruffleNativeHostMethods.class, "isSameObject", methodType(boolean.class, Object.class, Object.class)),
                            "RunTime#getObjectClass", lkp.findStatic(TruffleNativeHostMethods.class, "getObjectClass", methodType(long.class, Object.class)),
                            "RunTime#createTruffleCompilerOptionDescriptor", lkp.findStatic(TruffleNativeHostMethods.class, "createTruffleCompilerOptionDescriptor",
                                            methodType(Object.class, String.class, int.class, boolean.class, String.class, String.class)));
        } catch (Throwable e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }
}
