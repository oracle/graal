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

import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_1;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_2;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_4;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_6;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_8;

import java.io.CharConversionException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.graalvm.compiler.serviceprovider.IsolateUtil;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.core.c.function.CEntryPointSetup;
import com.oracle.svm.core.c.function.CEntryPointSetup.LeaveDetachThreadEpilogue;
import com.oracle.svm.core.c.function.CEntryPointSetup.LeaveTearDownIsolateEpilogue;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.FunctionPointerLogHandler;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.option.RuntimeOptionParser;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.util.Utf8;
import com.oracle.svm.jni.JNIJavaVMList;
import com.oracle.svm.jni.JNIObjectHandles;
import com.oracle.svm.jni.JNIThreadLocalEnvironment;
import com.oracle.svm.jni.JNIThreadOwnedMonitors;
import com.oracle.svm.jni.functions.JNIFunctions.Support.JNIJavaVMEnterAttachThreadEnsureJavaThreadPrologue;
import com.oracle.svm.jni.functions.JNIFunctions.Support.JNIJavaVMEnterAttachThreadManualJavaThreadPrologue;
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
final class JNIInvocationInterface {

    // Checkstyle: stop

    static class Exports {
        /*
         * jint JNI_GetCreatedJavaVMs(JavaVM **vmBuf, jsize bufLen, jsize *nVMs);
         */

        @CEntryPoint(name = "JNI_GetCreatedJavaVMs")
        @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, publishAs = Publish.SymbolOnly, include = CEntryPointOptions.NotIncludedAutomatically.class)
        @Uninterruptible(reason = "No Java context.")
        static int JNI_GetCreatedJavaVMs(JNIJavaVMPointer vmBuf, int bufLen, CIntPointer nVMs) {
            JNIJavaVMList.gather(vmBuf, bufLen, nVMs);
            return JNIErrors.JNI_OK();
        }

        /*
         * jint JNI_CreateJavaVM(JavaVM **p_vm, void **p_env, void *vm_args);
         */

        static class JNICreateJavaVMPrologue {
            @SuppressWarnings("unused")
            static void enter(JNIJavaVMPointer vmBuf, JNIEnvironmentPointer penv, JNIJavaVMInitArgs vmArgs) {
                if (!SubstrateOptions.SpawnIsolates.getValue()) {
                    int error = CEntryPointActions.enterIsolate((Isolate) CEntryPointSetup.SINGLE_ISOLATE_SENTINEL);
                    if (error != CEntryPointErrors.UNINITIALIZED_ISOLATE) {
                        if (error == CEntryPointErrors.NO_ERROR) {
                            CEntryPointActions.leave();
                        }
                        CEntryPointActions.bailoutInPrologue(JNIErrors.JNI_EEXIST());
                    }
                }
                int error = CEntryPointActions.enterCreateIsolate(WordFactory.nullPointer());
                if (error == CEntryPointErrors.NO_ERROR) {
                    // success
                } else if (error == CEntryPointErrors.UNSPECIFIED) {
                    CEntryPointActions.bailoutInPrologue(JNIErrors.JNI_ERR());
                } else if (error == CEntryPointErrors.MAP_HEAP_FAILED || error == CEntryPointErrors.RESERVE_ADDRESS_SPACE_FAILED || error == CEntryPointErrors.INSUFFICIENT_ADDRESS_SPACE) {
                    CEntryPointActions.bailoutInPrologue(JNIErrors.JNI_ENOMEM());
                } else { // return a (non-JNI) error that is more helpful for diagnosis
                    error = -1000000000 - error;
                    if (error == JNIErrors.JNI_OK() || error >= -100) {
                        error = JNIErrors.JNI_ERR(); // non-negative or potential actual JNI error
                    }
                    CEntryPointActions.bailoutInPrologue(error);
                }
            }
        }

        /**
         * This method supports the non-standard option strings detailed in the table below.
         *
         * <pre>
         | optionString  |                         meaning                                                   |
         |===============|===================================================================================|
         | _log          | extraInfo is a pointer to a "void(const char *buf, size_t count)" function.       |
         |               | Formatted low level log messages are sent to this function.                       |
         |               | If present, then _flush_log is also required to be present.                       |
         |---------------|-----------------------------------------------------------------------------------|
         | _flush_log    | extraInfo is a pointer to a "void()" function.                                    |
         |               | This function is called when the low level log stream should be flushed.          |
         |               | If present, then _log is also required to be present.                             |
         |---------------|-----------------------------------------------------------------------------------|
         | _fatal        | extraInfo is a pointer to a "void()" function.                                    |
         |               | This function is called when a non-recoverable, fatal error occurs.               |
         |---------------|-----------------------------------------------------------------------------------|
         * </pre>
         *
         * @see LogHandler
         * @see "https://docs.oracle.com/en/java/javase/14/docs/specs/jni/invocation.html#jni_createjavavm"
         */
        @CEntryPoint(name = "JNI_CreateJavaVM")
        @CEntryPointOptions(prologue = JNICreateJavaVMPrologue.class, publishAs = Publish.SymbolOnly, include = CEntryPointOptions.NotIncludedAutomatically.class)
        static int JNI_CreateJavaVM(JNIJavaVMPointer vmBuf, JNIEnvironmentPointer penv, JNIJavaVMInitArgs vmArgs) {
            // NOTE: could check version, extra options (-verbose etc.), hooks etc.
            WordPointer javavmIdPointer = WordFactory.nullPointer();
            if (vmArgs.isNonNull()) {
                Pointer p = (Pointer) vmArgs.getOptions();
                int count = vmArgs.getNOptions();
                ArrayList<String> options = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    JNIJavaVMOption option = (JNIJavaVMOption) p.add(i * SizeOf.get(JNIJavaVMOption.class));
                    CCharPointer str = option.getOptionString();
                    if (str.isNonNull()) {
                        String optionString = CTypeConversion.toJavaString(option.getOptionString());
                        if (!FunctionPointerLogHandler.parseVMOption(optionString, option.getExtraInfo())) {
                            if (optionString.equals("_javavm_id")) {
                                javavmIdPointer = option.getExtraInfo();
                            } else {
                                options.add(optionString);
                            }
                        }
                    }
                }
                FunctionPointerLogHandler.afterParsingVMOptions();
                RuntimeOptionParser.parseAndConsumeAllOptions(options.toArray(new String[0]));
            }
            JNIJavaVM javavm = JNIFunctionTables.singleton().getGlobalJavaVM();
            JNIJavaVMList.addJavaVM(javavm);
            if (javavmIdPointer.isNonNull()) {
                long javavmId = IsolateUtil.getIsolateID();
                javavmIdPointer.write(WordFactory.pointer(javavmId));
            }
            RuntimeSupport.getRuntimeSupport().addTearDownHook(new Runnable() {
                @Override
                public void run() {
                    JNIJavaVMList.removeJavaVM(javavm);
                }
            });
            vmBuf.write(javavm);
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
    @CEntryPointOptions(prologue = JNIJavaVMEnterAttachThreadManualJavaThreadPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int AttachCurrentThread(JNIJavaVM vm, JNIEnvironmentPointer penv, JNIJavaVMAttachArgs args) {
        return Support.attachCurrentThread(vm, penv, args, false);
    }

    /*
     * jint AttachCurrentThreadAsDaemon(JavaVM *vm, void **p_env, void *thr_args);
     */
    @CEntryPoint
    @CEntryPointOptions(prologue = JNIJavaVMEnterAttachThreadManualJavaThreadPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int AttachCurrentThreadAsDaemon(JNIJavaVM vm, JNIEnvironmentPointer penv, JNIJavaVMAttachArgs args) {
        return Support.attachCurrentThread(vm, penv, args, true);
    }

    /*
     * jint DetachCurrentThread(JavaVM *vm);
     */
    @CEntryPoint
    @CEntryPointOptions(prologue = JNIJavaVMEnterAttachThreadEnsureJavaThreadPrologue.class, epilogue = LeaveDetachThreadEpilogue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
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
    @CEntryPointOptions(prologue = JNIJavaVMEnterAttachThreadEnsureJavaThreadPrologue.class, epilogue = LeaveTearDownIsolateEpilogue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    @SuppressWarnings("unused")
    static int DestroyJavaVM(JNIJavaVM vm) {
        JavaThreads.singleton().joinAllNonDaemons();
        return JNIErrors.JNI_OK();
    }

    /*
     * jint GetEnv(JavaVM *vm, void **env, jint version);
     */
    @CEntryPoint
    @CEntryPointOptions(prologue = JNIGetEnvPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    @SuppressWarnings("unused")
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
                penv.write(JNIThreadLocalEnvironment.getAddress());
                ThreadGroup group = null;
                String name = null;
                if (args.isNonNull() && args.getVersion() != JNIVersion.JNI_VERSION_1_1()) {
                    group = JNIObjectHandles.getObject(args.getGroup());
                    if (args.getName().isNonNull()) {
                        ByteBuffer buffer = CTypeConversion.asByteBuffer(args.getName(), Integer.MAX_VALUE);
                        try {
                            name = Utf8.utf8ToString(true, buffer);
                        } catch (CharConversionException ignore) {
                        }
                    }
                }

                /*
                 * Ignore if a Thread object has already been assigned: "If the thread has already
                 * been attached via either AttachCurrentThread or AttachCurrentThreadAsDaemon, this
                 * routine simply sets the value pointed to by penv to the JNIEnv of the current
                 * thread. In this case neither AttachCurrentThread nor this routine have any effect
                 * on the daemon status of the thread."
                 */
                JavaThreads.ensureJavaThread(name, group, asDaemon);

                return JNIErrors.JNI_OK();
            }
            return JNIErrors.JNI_ERR();
        }

        static void releaseCurrentThreadOwnedMonitors() {
            JNIThreadOwnedMonitors.forEach((obj, depth) -> {
                for (int i = 0; i < depth; i++) {
                    MonitorSupport.singleton().monitorExit(obj);
                }
                assert !Thread.holdsLock(obj);
            });
        }

        public static JNIJavaVM getGlobalJavaVM() {
            return JNIFunctionTables.singleton().getGlobalJavaVM();
        }
    }
}
