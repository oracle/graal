/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.libgraal.truffle;

import org.graalvm.jniutils.JNI.JClass;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;

import static org.graalvm.jniutils.JNIUtil.NewGlobalRef;

import org.graalvm.jniutils.JNIUtil;

import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id;

final class TruffleFromLibGraalCalls extends FromLibGraalCalls<Id> {

    private static final String ENTRY_POINT_CLASS_NAME = "com.oracle.truffle.runtime.hotspot.libgraal.TruffleFromLibGraalEntryPoints";
    private static final String CLASS_ENTRY_POINT_CLASS_NAME = "Class<" + ENTRY_POINT_CLASS_NAME + ">";

    TruffleFromLibGraalCalls(JNIEnv env, JClass runtimeClass) {
        super(Id.class, resolvePeer(env, runtimeClass));
    }

    private static JClass resolvePeer(JNIEnv env, JClass runtimeClass) {
        JObject classLoader = JNIUtil.getClassLoader(env, runtimeClass);
        JClass clazz = JNIUtil.findClass(env, classLoader, ENTRY_POINT_CLASS_NAME);
        return NewGlobalRef(env, clazz, CLASS_ENTRY_POINT_CLASS_NAME);
    }

}
