/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.functions;

import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_1;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_2;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_4;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_6;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_8;

import java.util.ArrayList;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointContext;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.MonitorSupport;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.core.c.function.CEntryPointSetup.EnterCreateIsolatePrologue;
import com.oracle.svm.core.c.function.CEntryPointSetup.LeaveDetachThreadEpilogue;
import com.oracle.svm.core.option.RuntimeOptionParser;
import com.oracle.svm.core.properties.RuntimePropertyParser;
import com.oracle.svm.jni.JNIThreadLocalEnvironment;
import com.oracle.svm.jni.JNIThreadOwnedMonitors;
import com.oracle.svm.jni.functions.JNIFunctions.Support.JNIJavaVMEnterAttachThreadPrologue;
import com.oracle.svm.jni.functions.JNIInvocationInterface.Support.JNIGetEnvPrologue;
import com.oracle.svm.jni.hosted.JNIFeature;
import com.oracle.svm.jni.nativeapi.JNIEnvironmentPointer;
import com.oracle.svm.jni.nativeapi.JNIErrors;
import com.oracle.svm.jni.nativeapi.JNIJavaVM;
import com.oracle.svm.jni.nativeapi.JNIJavaVMInitArgs;
import com.oracle.svm.jni.nativeapi.JNIJavaVMOption;
import com.oracle.svm.jni.nativeapi.JNIJavaVMPointer;

/**
 * Implementation of the JNI invocation API for interacting with a Java VM without having an
 * existing context and without linking against the Java VM library beforehand.
 *
 * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/invocation.html">
 *      Java Native Interface Specification: The Invocation API</a>
 */
@SuppressWarnings("unused")
final class JNIInvocationInterface {

    // Checkstyle: stop

    static class Exports {
        private static boolean explicitlyInitialized = false;

        // TODO: refactor these for the changed CEntryPoint entry action CreateIsolate.

        /*
         * jint JNI_GetCreatedJavaVMs(JavaVM **vmBuf, jsize bufLen, jsize *nVMs);
         */
        @CEntryPoint(name = "JNI_GetCreatedJavaVMs")
        @CEntryPointOptions(prologue = EnterCreateIsolatePrologue.class, publishAs = Publish.SymbolOnly, include = CEntryPointOptions.NotIncludedAutomatically.class)
        static int JNI_GetCreatedJavaVMs(JNIJavaVMPointer vmBuf, int bufLen, CIntPointer nVMs) {
            /*
             * NOTE: just by entering this method, we eagerly attach the calling thread. JNI does
             * not intend for this to happen, but we currently need it to safely execute the
             * following code. However, calling AttachCurrentThread() later is not an error.
             */
            int count = 0;
            if (explicitlyInitialized || !JNIFeature.Options.JNICreateJavaVM.getValue()) {
                count = 1;
            }
            if (count > 0 && bufLen > 0) {
                vmBuf.write(JNIFunctionTables.singleton().getGlobalJavaVM());
            }
            if (nVMs.isNonNull()) {
                nVMs.write(count);
            }
            return JNIErrors.JNI_OK();
        }

        /*
         * jint JNI_CreateJavaVM(JavaVM **p_vm, void **p_env, void *vm_args);
         */
        @CEntryPoint(name = "JNI_CreateJavaVM")
        @CEntryPointOptions(prologue = EnterCreateIsolatePrologue.class, publishAs = Publish.SymbolOnly, include = CEntryPointOptions.NotIncludedAutomatically.class)
        static int JNI_CreateJavaVM(JNIJavaVMPointer vmBuf, JNIEnvironmentPointer penv, JNIJavaVMInitArgs vmArgs) {
            if (explicitlyInitialized || !JNIFeature.Options.JNICreateJavaVM.getValue()) {
                return JNIErrors.JNI_EEXIST();
            }
            if (vmBuf.isNull() || penv.isNull()) {
                return JNIErrors.JNI_ERR();
            }
            // NOTE: could check version, extra options (-verbose etc.), hooks etc.
            if (vmArgs.isNonNull()) {
                Pointer p = (Pointer) vmArgs.getOptions();
                int count = vmArgs.getNOptions();
                ArrayList<String> options = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    JNIJavaVMOption option = (JNIJavaVMOption) p.add(i * SizeOf.get(JNIJavaVMOption.class));
                    CCharPointer str = option.getOptionString();
                    if (str.isNonNull()) {
                        options.add(CTypeConversion.toJavaString(option.getOptionString()));
                    }
                }
                String[] optionArray = options.toArray(new String[0]);
                optionArray = RuntimeOptionParser.singleton().parse(optionArray, RuntimeOptionParser.DEFAULT_OPTION_PREFIX);
                RuntimePropertyParser.parse(optionArray);
            }
            vmBuf.write(JNIFunctionTables.singleton().getGlobalJavaVM());
            if (!JNIThreadLocalEnvironment.isInitialized()) {
                JNIThreadLocalEnvironment.initialize();
            }
            penv.write(JNIThreadLocalEnvironment.getAddress());
            explicitlyInitialized = true;
            return JNIErrors.JNI_OK();
        }
    }

    /*
     * jint AttachCurrentThread(JavaVM *vm, void **p_env, void *thr_args);
     */
    @CEntryPoint
    @CEntryPointOptions(prologue = JNIJavaVMEnterAttachThreadPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int AttachCurrentThread(JNIJavaVM vm, JNIEnvironmentPointer penv, WordPointer targs) {
        return Support.attachCurrentThread(vm, penv, false);
    }

    /*
     * jint AttachCurrentThreadAsDaemon(JavaVM *vm, void **p_env, void *thr_args);
     */
    @CEntryPoint
    @CEntryPointOptions(prologue = JNIJavaVMEnterAttachThreadPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int AttachCurrentThreadAsDaemon(JNIJavaVM vm, JNIEnvironmentPointer penv, WordPointer targs) {
        return Support.attachCurrentThread(vm, penv, true);
    }

    /*
     * jint DetachCurrentThread(JavaVM *vm);
     */
    @CEntryPoint
    @CEntryPointOptions(prologue = JNIJavaVMEnterAttachThreadPrologue.class, epilogue = LeaveDetachThreadEpilogue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int DetachCurrentThread(JNIJavaVM vm) {
        int result = JNIErrors.JNI_OK();
        if (!vm.equal(JNIFunctionTables.singleton().getGlobalJavaVM())) {
            result = JNIErrors.JNI_ERR();
        }
        // JNI specification requires releasing all owned monitors
        Support.releaseCurrentThreadOwnedMonitors();
        return result;
    }

    /*
     * jint GetEnv(JavaVM *vm, void **env, jint version);
     */
    @CEntryPoint
    @CEntryPointOptions(prologue = JNIGetEnvPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int GetEnv(JNIJavaVM vm, WordPointer env, int version) {
        env.write(JNIThreadLocalEnvironment.getAddress());
        return JNIErrors.JNI_OK();
    }

    // Checkstyle: resume

    /**
     * Helper code for JNI invocation API functions. This is an inner class because the outer
     * methods must match JNI invocation API functions.
     */
    static class Support {
        // This inner class exists because all outer methods must match API functions

        static class JNIGetEnvPrologue {
            static void enter(JNIJavaVM vm, WordPointer env, int version) {
                if (vm.isNull() || env.isNull()) {
                    CEntryPointActions.bailoutInPrologue(JNIErrors.JNI_ERR());
                }
                if (version != JNI_VERSION_1_8() && version != JNI_VERSION_1_6() && version != JNI_VERSION_1_4() && version != JNI_VERSION_1_2() && version != JNI_VERSION_1_1()) {
                    env.write(Word.nullPointer());
                    CEntryPointActions.bailoutInPrologue(JNIErrors.JNI_EVERSION());
                }
                if (!CEntryPointContext.isCurrentThreadAttachedTo((Isolate) vm)) {
                    env.write(Word.nullPointer());
                    CEntryPointActions.bailoutInPrologue(JNIErrors.JNI_EDETACHED());
                }
                if (CEntryPointActions.enterIsolate((Isolate) vm) != 0) {
                    CEntryPointActions.bailoutInPrologue(JNIErrors.JNI_ERR());
                }
            }
        }

        static int attachCurrentThread(JNIJavaVM vm, JNIEnvironmentPointer penv, boolean daemon) {
            if (vm.equal(JNIFunctionTables.singleton().getGlobalJavaVM())) {
                if (!JNIThreadLocalEnvironment.isInitialized()) {
                    JNIThreadLocalEnvironment.initialize();
                }
                penv.write(JNIThreadLocalEnvironment.getAddress());
                // FIXME: setting daemon status after a thread has been attached is not supported
                // right now.
                return JNIErrors.JNI_OK();
            }
            return JNIErrors.JNI_ERR();
        }

        static void releaseCurrentThreadOwnedMonitors() {
            JNIThreadOwnedMonitors.forEach((obj, depth) -> {
                for (int i = 0; i < depth; i++) {
                    MonitorSupport.monitorExit(obj);
                }
                assert !Thread.holdsLock(obj);
            });
        }
    }
}
