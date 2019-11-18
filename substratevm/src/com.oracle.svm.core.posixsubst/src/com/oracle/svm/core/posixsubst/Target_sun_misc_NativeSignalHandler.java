/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posixsubst;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.DeprecatedPlatform;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.posix.headers.Signal;

/** Translated from: jdk/src/share/native/sun/misc/NativeSignalHandler.c?v=Java_1.8.0_40_b10. */
@TargetClass(className = "sun.misc.NativeSignalHandler", onlyWith = JDK8OrEarlier.class)
@Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class})
final class Target_sun_misc_NativeSignalHandler {

    /**
     * This method gets called from the runnable created in the dispatch(int) method of
     * {@link sun.misc.Signal}. It is running in a Java thread, but the handler is a C function. So
     * I transition to native before making the call, and transition back to Java after the call.
     *
     * This looks really dangerous: Taking a long parameter and calling through it. If the only way
     * to get a NativeSignalHandler is from previously-registered native signal handler (see
     * {@link sun.misc.Signal#handle(sun.misc.Signal, sun.misc.SignalHandler)} then maybe this is
     * not quite as dangerous as it first seems.
     */
    // 033 typedef void (*sig_handler_t)(jint, void *, void *);
    // 034
    // 035 JNIEXPORT void JNICALL
    // 036 Java_sun_misc_NativeSignalHandler_handle0(JNIEnv *env, jclass cls, jint sig, jlong f) {
    @Substitute
    static void handle0(int sig, long f) {
        // 038 /* We've lost the siginfo and context */
        // 039 (*(sig_handler_t)jlong_to_ptr(f))(sig, NULL, NULL);
        final Signal.AdvancedSignalDispatcher handler = WordFactory.pointer(f);
        handler.dispatch(sig, WordFactory.nullPointer(), WordFactory.nullPointer());
    }
}
