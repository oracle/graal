/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#include <jni.h>

#define EXCEPTION_CHECK_VOID(env)                                                                                                                    \
    if ((*env)->ExceptionCheck(env)) {                                                                                                               \
        return;                                                                                                                                      \
    }

// Library entry points

JNIEXPORT void JNICALL Java_com_oracle_truffle_runtime_ModulesSupport_addExports0(JNIEnv *jniEnv, jclass clz, jobject m1, jobject pn, jobject m2) {
    jclass modulesClass = (*jniEnv)->FindClass(jniEnv, "jdk/internal/module/Modules");
    EXCEPTION_CHECK_VOID(jniEnv)
    jmethodID addExports =
        (*jniEnv)->GetStaticMethodID(jniEnv, modulesClass, "addExports", "(Ljava/lang/Module;Ljava/lang/String;Ljava/lang/Module;)V");
    EXCEPTION_CHECK_VOID(jniEnv)
    jvalue args[3];
    args[0].l = m1;
    args[1].l = pn;
    args[2].l = m2;
    (*jniEnv)->CallStaticVoidMethodA(jniEnv, modulesClass, addExports, args);
}
