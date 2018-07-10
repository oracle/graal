/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.functions;

import static com.oracle.svm.core.option.RuntimeOptionParser.DEFAULT_OPTION_PREFIX;
import static com.oracle.svm.core.option.SubstrateOptionsParser.BooleanOptionFormat.PLUS_MINUS;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_1;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_2;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_4;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_6;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_8;

import java.io.CharConversionException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MonitorSupport;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.core.c.function.CEntryPointSetup.LeaveDetachThreadEpilogue;
import com.oracle.svm.core.c.function.CEntryPointSetup.LeaveTearDownIsolateEpilogue;
import com.oracle.svm.core.option.RuntimeOptionParser;
import com.oracle.svm.core.properties.RuntimePropertyParser;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.util.Utf8;
import com.oracle.svm.jni.JNIObjectHandles;
import com.oracle.svm.jni.JNIThreadLocalEnvironment;
import com.oracle.svm.jni.JNIThreadOwnedMonitors;
import com.oracle.svm.jni.functions.JNIFunctions.Support.JNIJavaVMEnterAttachThreadPrologue;
import com.oracle.svm.jni.functions.JNIInvocationInterface.Support.JNIGetEnvPrologue;
import com.oracle.svm.jni.nativeapi.JNIEnvironmentPointer;
import com.oracle.svm.jni.nativeapi.JNIErrors;
import com.oracle.svm.jni.nativeapi.JNIJavaVM;
import com.oracle.svm.jni.nativeapi.JNIJavaVMAttachArgs;
import com.oracle.svm.jni.nativeapi.JNIJavaVMInitArgs;
import com.oracle.svm.jni.nativeapi.JNIJavaVMOption;
import com.oracle.svm.jni.nativeapi.JNIJavaVMPointer;
import com.oracle.svm.jni.nativeapi.JNIVersion;

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
        /*
         * jint JNI_GetCreatedJavaVMs(JavaVM **vmBuf, jsize bufLen, jsize *nVMs);
         */

        @CEntryPoint(name = "JNI_GetCreatedJavaVMs")
        @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, publishAs = Publish.SymbolOnly, include = CEntryPointOptions.NotIncludedAutomatically.class)
        @Uninterruptible(reason = "No Java context", calleeMustBe = false)
        static int JNI_GetCreatedJavaVMs(JNIJavaVMPointer vmBuf, int bufLen, CIntPointer nVMs) {
            /*
             * TODO: still less than ideal because it requires us to briefly attach to the isolate
             * to get the global Java VM pointer. Revisit when isolates are fully supported.
             */
            boolean didAttach = false;
            if (CEntryPointActions.enterIsolate(WordFactory.nullPointer()) != 0) {
                // Either there is no isolate, or the current thread is not attached to it
                if (CEntryPointActions.enterAttachThread(WordFactory.nullPointer()) != 0) {
                    // Could not attach: there is no isolate (or there was some problem)
                    nVMs.write(0);
                    return JNIErrors.JNI_OK();
                }
                didAttach = true;
            }
            // We have successfully entered the isolate at this point
            JNIJavaVM jvm = Support.getGlobalJavaVM();
            vmBuf.write(jvm);
            nVMs.write(1);
            if (didAttach) {
                CEntryPointActions.leaveDetachThread();
            } else {
                CEntryPointActions.leave();
            }
            return JNIErrors.JNI_OK();
        }

        /*
         * jint JNI_CreateJavaVM(JavaVM **p_vm, void **p_env, void *vm_args);
         */

        static class JNICreateJavaVMPrologue {
            static void enter(JNIJavaVMPointer vmBuf, JNIEnvironmentPointer penv, JNIJavaVMInitArgs vmArgs) {
                if (CEntryPointActions.enterIsolate(WordFactory.nullPointer()) == 0) {
                    CEntryPointActions.leave();
                    CEntryPointActions.bailoutInPrologue(JNIErrors.JNI_EEXIST()); // isolate exists
                }
                if (CEntryPointActions.enterCreateIsolate(WordFactory.nullPointer()) != 0) {
                    CEntryPointActions.bailoutInPrologue(JNIErrors.JNI_ERR());
                }
            }
        }

        @CEntryPoint(name = "JNI_CreateJavaVM")
        @CEntryPointOptions(prologue = JNICreateJavaVMPrologue.class, publishAs = Publish.SymbolOnly, include = CEntryPointOptions.NotIncludedAutomatically.class)
        static int JNI_CreateJavaVM(JNIJavaVMPointer vmBuf, JNIEnvironmentPointer penv, JNIJavaVMInitArgs vmArgs) {
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
                optionArray = RuntimeOptionParser.singleton().parse(optionArray, DEFAULT_OPTION_PREFIX, PLUS_MINUS, true);
                RuntimePropertyParser.parse(optionArray);
            }
            vmBuf.write(JNIFunctionTables.singleton().getGlobalJavaVM());
            if (!JNIThreadLocalEnvironment.isInitialized()) {
                JNIThreadLocalEnvironment.initialize();
            }
            penv.write(JNIThreadLocalEnvironment.getAddress());
            return JNIErrors.JNI_OK();
        }

        /*
         * jint JNI_GetDefaultJavaVMInitArgs(void *vm_args);
         */
        @CEntryPoint(name = "JNI_GetDefaultJavaVMInitArgs")
        @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, publishAs = Publish.SymbolOnly, include = CEntryPointOptions.NotIncludedAutomatically.class)
        @Uninterruptible(reason = "No Java context")
        static int JNI_GetDefaultJavaVMInitArgs(JNIJavaVMInitArgs vmArgs) {
            int version = vmArgs.getVersion();
            if (version == JNI_VERSION_1_8() || version == JNI_VERSION_1_6() || version == JNI_VERSION_1_4() || version == JNI_VERSION_1_2()) {
                return JNIErrors.JNI_OK();
            }
            if (version == JNI_VERSION_1_1()) {
                vmArgs.setVersion(JNI_VERSION_1_2());
            }
            return JNIErrors.JNI_ERR();
        }
    }

    /*
     * jint AttachCurrentThread(JavaVM *vm, void **p_env, void *thr_args);
     */
    @CEntryPoint
    @CEntryPointOptions(prologue = JNIJavaVMEnterAttachThreadPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int AttachCurrentThread(JNIJavaVM vm, JNIEnvironmentPointer penv, JNIJavaVMAttachArgs args) {
        return Support.attachCurrentThread(vm, penv, args, false);
    }

    /*
     * jint AttachCurrentThreadAsDaemon(JavaVM *vm, void **p_env, void *thr_args);
     */
    @CEntryPoint
    @CEntryPointOptions(prologue = JNIJavaVMEnterAttachThreadPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int AttachCurrentThreadAsDaemon(JNIJavaVM vm, JNIEnvironmentPointer penv, JNIJavaVMAttachArgs args) {
        return Support.attachCurrentThread(vm, penv, args, true);
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
     * jint DestroyJavaVM(JavaVM *vm);
     */
    @CEntryPoint
    @CEntryPointOptions(prologue = JNIJavaVMEnterAttachThreadPrologue.class, epilogue = LeaveTearDownIsolateEpilogue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int DestroyJavaVM(JNIJavaVM vm) {
        JavaThreads.singleton().joinAllNonDaemons();
        return JNIErrors.JNI_OK();
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
                    env.write(WordFactory.nullPointer());
                    CEntryPointActions.bailoutInPrologue(JNIErrors.JNI_EVERSION());
                }
                if (!CEntryPointActions.isCurrentThreadAttachedTo(vm.getFunctions().getIsolate())) {
                    env.write(WordFactory.nullPointer());
                    CEntryPointActions.bailoutInPrologue(JNIErrors.JNI_EDETACHED());
                }
                if (CEntryPointActions.enterIsolate(vm.getFunctions().getIsolate()) != 0) {
                    CEntryPointActions.bailoutInPrologue(JNIErrors.JNI_ERR());
                }
            }
        }

        static int attachCurrentThread(JNIJavaVM vm, JNIEnvironmentPointer penv, JNIJavaVMAttachArgs args, boolean asDaemon) {
            if (vm.equal(JNIFunctionTables.singleton().getGlobalJavaVM())) {
                if (!JNIThreadLocalEnvironment.isInitialized()) {
                    JNIThreadLocalEnvironment.initialize();
                }
                penv.write(JNIThreadLocalEnvironment.getAddress());
                ThreadGroup group = null;
                String name = null;
                if (args.isNonNull() && args.getVersion() != JNIVersion.JNI_VERSION_1_1()) {
                    group = JNIObjectHandles.getObject(args.getGroup());
                    if (args.getName().isNonNull()) {
                        ByteBuffer buffer = SubstrateUtil.wrapAsByteBuffer(args.getName(), Integer.MAX_VALUE);
                        try {
                            name = Utf8.utf8ToString(true, buffer);
                        } catch (CharConversionException ignore) {
                        }
                    }
                }
                JavaThreads.singleton().assignJavaThread(name, group, asDaemon);
                /*
                 * Ignore if a Thread object has already been assigned: "If the thread has already
                 * been attached via either AttachCurrentThread or AttachCurrentThreadAsDaemon, this
                 * routine simply sets the value pointed to by penv to the JNIEnv of the current
                 * thread. In this case neither AttachCurrentThread nor this routine have any effect
                 * on the daemon status of the thread."
                 */
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

        public static JNIJavaVM getGlobalJavaVM() {
            return JNIFunctionTables.singleton().getGlobalJavaVM();
        }
    }
}
