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
package com.oracle.svm.graal.hotspot.libgraal;

import org.graalvm.jniutils.HSObject;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;

import com.oracle.truffle.compiler.TruffleCompilerOptionDescriptor;

import jdk.graal.compiler.word.Word;

/**
 * Entry points for native-image specific methods called by guest Graal using method handles.
 */
public final class NativeImageHostEntryPoints {

    private NativeImageHostEntryPoints() {
    }

    public static void initializeHost(long runtimeClass) {
        TruffleFromLibGraalStartPoints.initializeJNI(Word.pointer(runtimeClass));
    }

    public static Object createLocalHandleForLocalReference(long jniLocalRef) {
        JNIMethodScope scope = JNIMethodScope.scopeOrNull();
        if (scope == null) {
            return null;
        }
        return new HSObject(scope, Word.pointer(jniLocalRef));
    }

    public static Object createLocalHandleForWeakGlobalReference(long jniWeakGlobalRef) {
        JNIMethodScope scope = JNIMethodScope.scope();
        JObject localRef = JNIUtil.NewLocalRef(scope.getEnv(), Word.pointer(jniWeakGlobalRef));
        return localRef.isNull() ? null : new HSObject(scope, localRef);
    }

    public static Object createGlobalHandle(Object hsHandle, boolean allowGlobalDuplicates) {
        if (hsHandle == null) {
            return null;
        }
        return new HSObject(JNIMethodScope.env(), ((HSObject) hsHandle).getHandle(), allowGlobalDuplicates, false);
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

    public static void onCompilationSuccess(Object hsHandle, int tier, boolean lastTier) {
        TruffleFromLibGraalStartPoints.onCompilationSuccess(hsHandle, tier, lastTier);
    }
}
