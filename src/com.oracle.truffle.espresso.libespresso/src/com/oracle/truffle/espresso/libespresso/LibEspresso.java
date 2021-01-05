/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.libespresso;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.word.WordFactory;

import com.oracle.truffle.espresso.libespresso.jniapi.JNIEnvironmentPointer;
import com.oracle.truffle.espresso.libespresso.jniapi.JNIErrors;
import com.oracle.truffle.espresso.libespresso.jniapi.JNIFunctionPointerTypes.GetEnvFunctionPointer;
import com.oracle.truffle.espresso.libespresso.jniapi.JNIJavaVM;
import com.oracle.truffle.espresso.libespresso.jniapi.JNIJavaVMInitArgs;
import com.oracle.truffle.espresso.libespresso.jniapi.JNIJavaVMPointer;
import com.oracle.truffle.espresso.libespresso.jniapi.JNIVersion;

import java.io.PrintStream;

public class LibEspresso {

    public static final CEntryPointLiteral<CFunctionPointer> CREATE_JAVA_VM_SYMBOL = CEntryPointLiteral.create(LibEspresso.class, "Espresso_CreateJavaVM", JNIJavaVMPointer.class,
                    JNIEnvironmentPointer.class, JNIJavaVMInitArgs.class);
    public static final PrintStream STDERR = System.err;

    @CEntryPoint(name = "Espresso_CreateJavaVM")
    static int createJavaVM(IsolateThread thread, JNIJavaVMPointer javaVMPointer, JNIEnvironmentPointer penv, JNIJavaVMInitArgs args) {
        if (args.getVersion() < JNIVersion.JNI_VERSION_1_2() || args.getVersion() > JNIVersion.JNI_VERSION_10()) {
            return JNIErrors.JNI_EVERSION();
        }
        // TODO use Launcher infra to parse graalvm specific options
        Context.Builder builder = Context.newBuilder().allowAllAccess(true);
        int result = Arguments.setupContext(builder, args);
        if (result != JNIErrors.JNI_OK()) {
            return result;
        }
        Context context = builder.build();
        context.enter();
        Value java = context.getBindings("java").getMember("<JavaVM>");
        if (!java.isNativePointer()) {
            STDERR.println("<JavaVM> is not available in the java bindings");
            return JNIErrors.JNI_ERR();
        }
        JNIJavaVM espressoJavaVM = WordFactory.pointer(java.asNativePointer());

        GetEnvFunctionPointer getEnv = espressoJavaVM.getFunctions().getGetEnv();
        result = getEnv.invoke(espressoJavaVM, penv, JNIVersion.JNI_VERSION_1_2());
        if (result != JNIErrors.JNI_OK()) {
            return result;
        }
        javaVMPointer.write(espressoJavaVM);
        return JNIErrors.JNI_OK();
    }
}
