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
package com.oracle.truffle.espresso.libjavavm;

import java.io.PrintStream;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.word.WordFactory;

import com.oracle.truffle.espresso.libjavavm.jniapi.JNIEnvironmentPointer;
import com.oracle.truffle.espresso.libjavavm.jniapi.JNIErrors;
import com.oracle.truffle.espresso.libjavavm.jniapi.JNIFunctionPointerTypes.GetEnvFunctionPointer;
import com.oracle.truffle.espresso.libjavavm.jniapi.JNIJavaVM;
import com.oracle.truffle.espresso.libjavavm.jniapi.JNIJavaVMInitArgs;
import com.oracle.truffle.espresso.libjavavm.jniapi.JNIJavaVMPointer;
import com.oracle.truffle.espresso.libjavavm.jniapi.JNIVersion;

public class LibEspresso {
    private static final PrintStream STDERR = System.err;

    @CEntryPoint(name = "Espresso_CreateJavaVM")
    static int createJavaVM(@SuppressWarnings("unused") IsolateThread thread, JNIJavaVMPointer javaVMPointer, JNIEnvironmentPointer penv, JNIJavaVMInitArgs args) {
        if (args.getVersion() < JNIVersion.JNI_VERSION_1_2() || args.getVersion() > JNIVersion.JNI_VERSION_10) {
            return JNIErrors.JNI_EVERSION();
        }
        // TODO use Launcher infra to parse graalvm specific options
        Context.Builder builder = Context.newBuilder().allowAllAccess(true);

        // These option need to be set before calling `Arguments.setupContext()` so that cmd line
        // args can override the default behavior.

        // Since Espresso has a verifier, the Static Object Model does not need to perform shape
        // checks and can use unsafe casts.
        builder.option("engine.RelaxStaticObjectSafetyChecks", "true");

        int result = Arguments.setupContext(builder, args);
        if (result != JNIErrors.JNI_OK()) {
            return result;
        }
        VMRuntime.initialize();
        // Use the nuclear option for System.exit
        builder.useSystemExit(true);
        builder.option("java.ExitHost", "true");
        builder.option("java.EnableSignals", "true");
        builder.option("java.ExposeNativeJavaVM", "true");
        Context context = builder.build();
        context.enter();
        Value bindings = context.getBindings("java");
        Value java = bindings.getMember("<JavaVM>");
        if (!java.isNativePointer()) {
            STDERR.println("<JavaVM> is not available in the java bindings");
            return JNIErrors.JNI_ERR();
        }
        JNIJavaVM espressoJavaVM = WordFactory.pointer(java.asNativePointer());
        bindings.removeMember("<JavaVM>");
        ObjectHandle contextHandle = ObjectHandles.getGlobal().create(context);
        espressoJavaVM.getFunctions().setContext(contextHandle);

        GetEnvFunctionPointer getEnv = espressoJavaVM.getFunctions().getGetEnv();
        result = getEnv.invoke(espressoJavaVM, penv, JNIVersion.JNI_VERSION_1_2());
        if (result != JNIErrors.JNI_OK()) {
            return result;
        }
        javaVMPointer.write(espressoJavaVM);
        return JNIErrors.JNI_OK();
    }

    @CEntryPoint(name = "Espresso_EnterContext")
    static int enterContext(@SuppressWarnings("unused") IsolateThread thread, JNIJavaVM javaVM) {
        ObjectHandle contextHandle = javaVM.getFunctions().getContext();
        Context context = ObjectHandles.getGlobal().get(contextHandle);
        if (context == null) {
            STDERR.println("Cannot enter context: no context found");
            return JNIErrors.JNI_ERR();
        }
        context.enter();
        return JNIErrors.JNI_OK();
    }

    @CEntryPoint(name = "Espresso_LeaveContext")
    static int leaveContext(@SuppressWarnings("unused") IsolateThread thread, JNIJavaVM javaVM) {
        ObjectHandle contextHandle = javaVM.getFunctions().getContext();
        Context context = ObjectHandles.getGlobal().get(contextHandle);
        if (context == null) {
            STDERR.println("Cannot leave context: no context found");
            return JNIErrors.JNI_ERR();
        }
        context.leave();
        return JNIErrors.JNI_OK();
    }

    @CEntryPoint(name = "Espresso_ReleaseContext")
    static int releaseContext(@SuppressWarnings("unused") IsolateThread thread, JNIJavaVM javaVM) {
        ObjectHandle contextHandle = javaVM.getFunctions().getContext();
        ObjectHandles.getGlobal().destroy(contextHandle);
        return JNIErrors.JNI_OK();
    }

    @CEntryPoint(name = "Espresso_CloseContext")
    static int closeContext(@SuppressWarnings("unused") IsolateThread thread, JNIJavaVM javaVM) {
        ObjectHandle contextHandle = javaVM.getFunctions().getContext();
        Context context = ObjectHandles.getGlobal().get(contextHandle);
        ObjectHandles.getGlobal().destroy(contextHandle);
        context.leave();
        context.close();
        return JNIErrors.JNI_OK();
    }

    @CEntryPoint(name = "Espresso_Shutdown")
    static int shutdown(@SuppressWarnings("unused") IsolateThread thread) {
        VMRuntime.shutdown();
        return JNIErrors.JNI_OK();
    }
}
