/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import static org.graalvm.jniutils.JNIUtil.PopLocalFrame;
import static org.graalvm.jniutils.JNIUtil.PushLocalFrame;

/**
 * A {@link JNIMethodScope} subclass which pushes a JNI locals frame when there is no Java frame
 * anchor on the stack. A Java frame anchor means there is an active Java-to-native stub (see
 * {@code SharedRuntime::generate_native_wrapper}) that will clear all JNI references in the current
 * JNI locals frame after the native call returns. A call directly into libgraal from the VM does
 * not use such a stub so without explicitly allocating a new JNI locals frame, the JNI references
 * created by libgraal will never be freed (i.e., a memory leak).
 */
final class LibGraalJNIMethodScope extends JNIMethodScope {

    private static volatile int lastJavaPCOffset = -1;

    private LibGraalJNIMethodScope(String scopeName, JNIEnv env) {
        super(scopeName, env);
        PushLocalFrame(env, 64);
    }

    @Override
    public void close() {
        setObjectResult(PopLocalFrame(getEnv(), getObjectResult()));
        super.close();
    }

    /**
     * Creates a new {@link JNIMethodScope} and pushes a JNI locals frame when the scope is a top
     * level scope and there is no Java frame anchor on the stack.
     *
     * @see LibGraalJNIMethodScope
     */
    static JNIMethodScope open(String scopeName, JNIEnv env) {
        return scopeOrNull() == null && getJavaFrameAnchor().isNull() ? new LibGraalJNIMethodScope(scopeName, env) : new JNIMethodScope(scopeName, env);
    }

    private static PointerBase getJavaFrameAnchor() {
        CLongPointer currentThreadLastJavaPCOffset = (CLongPointer) WordFactory.unsigned(HotSpotJVMCIRuntime.runtime().getCurrentJavaThread()).add(getLastJavaPCOffset());
        return WordFactory.pointer(currentThreadLastJavaPCOffset.read());
    }

    private static int getLastJavaPCOffset() {
        int res = lastJavaPCOffset;
        if (res == -1) {
            HotSpotVMConfigAccess configAccess = new HotSpotVMConfigAccess(HotSpotJVMCIRuntime.runtime().getConfigStore());
            int anchor = configAccess.getFieldOffset("JavaThread::_anchor", Integer.class, "JavaFrameAnchor");
            int lastJavaPc = configAccess.getFieldOffset("JavaFrameAnchor::_last_Java_pc", Integer.class, "address");
            res = anchor + lastJavaPc;
            lastJavaPCOffset = res;
        }
        return res;
    }
}
