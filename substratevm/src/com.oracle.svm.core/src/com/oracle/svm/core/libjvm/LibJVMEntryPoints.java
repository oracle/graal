/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.libjvm;

import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.JNIEnvEnterPrologue;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.ReturnNullHandle;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;

import jdk.graal.compiler.word.Word;

final class LibJVMEntryPoints {

    /**
     * This entrypoint implements
     * {@code *jclass JVM_FindClassFromBootLoader(JNIEnv *env, const char *name) }.
     *
     * It allows for finding classes from the VM's bootstrap class loader directly, without causing
     * unnecessary searching of the classpath for the required classes. It is used for shared
     * library images that provide the functionality of {@code libjvm.so}.
     */
    @CEntryPoint(name = "JVM_FindClassFromBootLoader", exceptionHandler = ReturnNullHandleHandler.class, publishAs = Publish.SymbolOnly, include = LibJVMMainMethodWrappers.Enabled.class)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle findClassFromBootLoader(@SuppressWarnings("unused") JNIEnvironment env, CCharPointer name) {
        if (!ClassForNameSupport.respectClassLoader()) {
            return Word.nullPointer();
        }

        try {
            var clazzSymbol = SymbolsSupport.getTypes().fromClassGetName(CTypeConversion.toJavaString(name));
            var bootRegistry = ClassRegistries.singleton().getRegistry(null);
            var clazz = bootRegistry.loadClass(clazzSymbol);
            return JNIObjectHandles.createLocal(clazz);
        } catch (ClassNotFoundException e) {
            return JNIObjectHandles.nullHandle();
        }
    }

    private static final class ReturnNullHandleHandler implements CEntryPoint.ExceptionHandler {
        @Uninterruptible(reason = "exception handler")
        static JNIObjectHandle handle(@SuppressWarnings("unused") Throwable t) {
            return JNIObjectHandles.nullHandle();
        }
    }
}
