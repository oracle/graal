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
package com.oracle.svm.core.jni.functions;

import org.graalvm.compiler.serviceprovider.IsolateUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.c.function.CEntryPointSetup;
import com.oracle.svm.core.c.function.CEntryPointSetup.LeaveDetachThreadEpilogue;
import com.oracle.svm.core.c.function.CEntryPointSetup.LeaveTearDownIsolateEpilogue;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.jni.JNIJavaVMList;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.JNIThreadLocalEnvironment;
import com.oracle.svm.core.jni.JNIThreadOwnedMonitors;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.JNIJavaVMEnterAttachThreadEnsureJavaThreadPrologue;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.JNIJavaVMEnterAttachThreadManualJavaThreadPrologue;
import com.oracle.svm.core.jni.functions.JNIInvocationInterface.Support.JNIGetEnvPrologue;
import com.oracle.svm.core.jni.headers.JNIEnvironmentPointer;
import com.oracle.svm.core.jni.headers.JNIErrors;
import com.oracle.svm.core.jni.headers.JNIJavaVM;
import com.oracle.svm.core.jni.headers.JNIJavaVMAttachArgs;
import com.oracle.svm.core.jni.headers.JNIJavaVMInitArgs;
import com.oracle.svm.core.jni.headers.JNIJavaVMOption;
import com.oracle.svm.core.jni.headers.JNIJavaVMPointer;
import com.oracle.svm.core.jni.headers.JNIVersion;
import com.oracle.svm.core.log.FunctionPointerLogHandler;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.util.Utf8;

/**
 * Implementation of the JNI invocation API for interacting with a Java VM without having an
 * existing context and without linking against the Java VM library beforehand.
 *
 * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/invocation.html">
 *      Java Native Interface Specification: The Invocation API</a>
 */
public final class JNIInvocationInterface {

    // Checkstyle: stop

    public static class Exports {
        /*
         * jint JNI_GetCreatedJavaVMs(JavaVM **vmBuf, jsize bufLen, jsize *nVMs);
         */

        @CEntryPoint(name = "JNI_GetCreatedJavaVMs", include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
        @Uninterruptible(reason = "No Java context.")
        static int JNI_GetCreatedJavaVMs(JNIJavaVMPointer vmBuf, int bufLen, CIntPointer nVMs) {
            JNIJavaVMList.gather(vmBuf, bufLen, nVMs);
            return JNIErrors.JNI_OK();
        }

        /*
         * jint JNI_CreateJavaVM(JavaVM **p_vm, void **p_env, void *vm_args);
         */

        static class JNICreateJavaVMPrologue implements CEntryPointOptions.Prologue {
            @Uninterruptible(reason = "prologue")
            static int enter(JNIJavaVMPointer vmBuf, JNIEnvironmentPointer penv, JNIJavaVMInitArgs vmArgs) {
                if (!SubstrateOptions.SpawnIsolates.getValue()) {
                    int error = CEntryPointActions.enterByIsolate((Isolate) CEntryPointSetup.SINGLE_ISOLATE_SENTINEL);
                    if (error == CEntryPointErrors.NO_ERROR) {
                        CEntryPointActions.leave();
                        return JNIErrors.JNI_EEXIST();
                    } else if (error != CEntryPointErrors.UNINITIALIZED_ISOLATE) {
                        return JNIErrors.JNI_EEXIST();
                    }
                }

                boolean hasSpecialVmOptions = false;
                CEntryPointCreateIsolateParameters params = WordFactory.nullPointer();
                if (vmArgs.isNonNull()) {
                    int vmArgc = vmArgs.getNOptions();
                    if (vmArgc > 0) {
                        UnsignedWord size = SizeOf.unsigned(CCharPointerPointer.class).multiply(vmArgc + 1);
                        CCharPointerPointer argv = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(size);
                        if (argv.isNull()) {
                            return JNIErrors.JNI_ENOMEM();
                        }

                        // The first argument is reserved for the name of the binary. We use null
                        // when we are called via JNI.
                        int argc = 0;
                        argv.addressOf(argc).write(WordFactory.nullPointer());
                        argc++;

                        Pointer p = (Pointer) vmArgs.getOptions();
                        for (int i = 0; i < vmArgc; i++) {
                            JNIJavaVMOption option = (JNIJavaVMOption) p.add(i * SizeOf.get(JNIJavaVMOption.class));
                            CCharPointer optionString = option.getOptionString();
                            if (optionString.isNonNull()) {
                                // Filter all special VM options as those must be parsed differently
                                // after the isolate creation.
                                if (Support.isSpecialVMOption(optionString)) {
                                    hasSpecialVmOptions = true;
                                } else {
                                    argv.addressOf(argc).write(optionString);
                                    argc++;
                                }
                            }
                        }

                        params = StackValue.get(CEntryPointCreateIsolateParameters.class);
                        UnmanagedMemoryUtil.fill((Pointer) params, SizeOf.unsigned(CEntryPointCreateIsolateParameters.class), (byte) 0);
                        params.setVersion(4);
                        params.setArgc(argc);
                        params.setArgv(argv);
                        params.setIgnoreUnrecognizedArguments(vmArgs.getIgnoreUnrecognized());
                        params.setExitWhenArgumentParsingFails(false);
                    }
                }

                int code = CEntryPointActions.enterCreateIsolate(params);
                if (params.isNonNull()) {
                    ImageSingletons.lookup(UnmanagedMemorySupport.class).free(params.getArgv());
                    params = WordFactory.nullPointer();
                }

                if (code == CEntryPointErrors.NO_ERROR) {
                    // The isolate was created successfully, so we can finish the initialization.
                    return Support.finishInitialization(vmBuf, penv, vmArgs, hasSpecialVmOptions);
                } else if (code == CEntryPointErrors.UNSPECIFIED || code == CEntryPointErrors.ARGUMENT_PARSING_FAILED) {
                    return JNIErrors.JNI_ERR();
                } else if (code == CEntryPointErrors.MAP_HEAP_FAILED || code == CEntryPointErrors.RESERVE_ADDRESS_SPACE_FAILED || code == CEntryPointErrors.INSUFFICIENT_ADDRESS_SPACE) {
                    return JNIErrors.JNI_ENOMEM();
                } else { // return a (non-JNI) error that is more helpful for diagnosis
                    code = -1000000000 - code;
                    if (code == JNIErrors.JNI_OK() || code >= -100) {
                        code = JNIErrors.JNI_ERR(); // non-negative or potential actual JNI error
                    }
                    return code;
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
         | _fatal_log    | extraInfo is a pointer to a "void(const char *buf, size_t count)" function.       |
         |               | Formatted low level log messages are sent to this function.                       |
         |               | This log function is used for logging fatal crash data.                           |
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
        @CEntryPoint(name = "JNI_CreateJavaVM", include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = JNICreateJavaVMPrologue.class)
        static int JNI_CreateJavaVM(@SuppressWarnings("unused") JNIJavaVMPointer vmBuf, @SuppressWarnings("unused") JNIEnvironmentPointer penv, @SuppressWarnings("unused") JNIJavaVMInitArgs vmArgs) {
            return JNIErrors.JNI_OK();
        }

        /*
         * jint JNI_GetDefaultJavaVMInitArgs(void *vm_args);
         */
        @CEntryPoint(name = "JNI_GetDefaultJavaVMInitArgs", include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
        @Uninterruptible(reason = "No Java context")
        static int JNI_GetDefaultJavaVMInitArgs(JNIJavaVMInitArgs vmArgs) {
            int version = vmArgs.getVersion();
            if (JNIVersion.isSupported(vmArgs.getVersion()) && version != JNIVersion.JNI_VERSION_1_1()) {
                return JNIErrors.JNI_OK();
            }
            if (version == JNIVersion.JNI_VERSION_1_1()) {
                vmArgs.setVersion(JNIVersion.JNI_VERSION_1_2());
            }
            return JNIErrors.JNI_ERR();
        }

    }

    /*
     * jint AttachCurrentThread(JavaVM *vm, void **p_env, void *thr_args);
     */
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, exceptionHandler = JNIFunctions.Support.JNIExceptionHandlerDetachAndReturnJniErr.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIJavaVMEnterAttachThreadManualJavaThreadPrologue.class, epilogue = NoEpilogue.class)
    @Uninterruptible(reason = "Permits omitting an epilogue so we can detach in the exception handler.", calleeMustBe = false)
    static int AttachCurrentThread(JNIJavaVM vm, JNIEnvironmentPointer penv, JNIJavaVMAttachArgs args) {
        Support.attachCurrentThread(vm, penv, args, false);
        CEntryPointActions.leave();
        return JNIErrors.JNI_OK();
    }

    /*
     * jint AttachCurrentThreadAsDaemon(JavaVM *vm, void **p_env, void *thr_args);
     */
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, exceptionHandler = JNIFunctions.Support.JNIExceptionHandlerDetachAndReturnJniErr.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIJavaVMEnterAttachThreadManualJavaThreadPrologue.class, epilogue = NoEpilogue.class)
    @Uninterruptible(reason = "Permits omitting an epilogue so we can detach in the exception handler.", calleeMustBe = false)
    static int AttachCurrentThreadAsDaemon(JNIJavaVM vm, JNIEnvironmentPointer penv, JNIJavaVMAttachArgs args) {
        Support.attachCurrentThread(vm, penv, args, true);
        CEntryPointActions.leave();
        return JNIErrors.JNI_OK();
    }

    /*
     * jint DetachCurrentThread(JavaVM *vm);
     */
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIJavaVMEnterAttachThreadEnsureJavaThreadPrologue.class, epilogue = LeaveDetachThreadEpilogue.class)
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
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIJavaVMEnterAttachThreadEnsureJavaThreadPrologue.class, epilogue = LeaveTearDownIsolateEpilogue.class)
    @SuppressWarnings("unused")
    static int DestroyJavaVM(JNIJavaVM vm) {
        PlatformThreads.singleton().joinAllNonDaemons();
        return JNIErrors.JNI_OK();
    }

    /*
     * jint GetEnv(JavaVM *vm, void **env, jint version);
     */
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIGetEnvPrologue.class)
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
        private static final CGlobalData<CCharPointer> JAVA_VM_ID_OPTION = CGlobalDataFactory.createCString("_javavm_id");

        static class JNIGetEnvPrologue implements CEntryPointOptions.Prologue {
            @Uninterruptible(reason = "prologue")
            static int enter(JNIJavaVM vm, WordPointer env, int version) {
                if (vm.isNull() || env.isNull()) {
                    return JNIErrors.JNI_ERR();
                }
                if (!JNIVersion.isSupported(version)) {
                    env.write(WordFactory.nullPointer());
                    return JNIErrors.JNI_EVERSION();
                }
                if (!CEntryPointActions.isCurrentThreadAttachedTo(vm.getFunctions().getIsolate())) {
                    env.write(WordFactory.nullPointer());
                    return JNIErrors.JNI_EDETACHED();
                }
                if (CEntryPointActions.enterByIsolate(vm.getFunctions().getIsolate()) != 0) {
                    return JNIErrors.JNI_ERR();
                }
                return JNIErrors.JNI_OK();
            }
        }

        static void attachCurrentThread(JNIJavaVM vm, JNIEnvironmentPointer penv, JNIJavaVMAttachArgs args, boolean asDaemon) {
            if (penv.isNull() || vm.notEqual(JNIFunctionTables.singleton().getGlobalJavaVM())) {
                throw ImplicitExceptions.CACHED_ILLEGAL_ARGUMENT_EXCEPTION;
            }

            penv.write(JNIThreadLocalEnvironment.getAddress());
            ThreadGroup group = null;
            String name = null;
            if (args.isNonNull() && args.getVersion() != JNIVersion.JNI_VERSION_1_1()) {
                group = JNIObjectHandles.getObject(args.getGroup());
                name = Utf8.utf8ToString(args.getName());
            }

            /*
             * Ignore if a Thread object has already been assigned: "If the thread has already been
             * attached via either AttachCurrentThread or AttachCurrentThreadAsDaemon, this routine
             * simply sets the value pointed to by penv to the JNIEnv of the current thread. In this
             * case neither AttachCurrentThread nor this routine have any effect on the daemon
             * status of the thread."
             */
            PlatformThreads.ensureCurrentAssigned(name, group, asDaemon);
        }

        static void releaseCurrentThreadOwnedMonitors() {
            JNIThreadOwnedMonitors.forEach((obj, depth) -> {
                for (int i = 0; i < depth; i++) {
                    MonitorSupport.singleton().monitorExit(obj);
                }
                assert !Thread.holdsLock(obj);
            });
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        static boolean isSpecialVMOption(CCharPointer str) {
            // NOTE: could check version, extra options (-verbose etc.), hooks etc.
            return FunctionPointerLogHandler.isJniVMOption(str) || isJavaVmId(str);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        static boolean isJavaVmId(CCharPointer str) {
            return LibC.strcmp(str, JAVA_VM_ID_OPTION.get()) == 0;
        }

        @Uninterruptible(reason = "Called after creating the isolate, so there is no need to be uninterruptible.", calleeMustBe = false)
        static int finishInitialization(JNIJavaVMPointer vmBuf, JNIEnvironmentPointer penv, JNIJavaVMInitArgs vmArgs, boolean hasSpecialVmOptions) {
            return finishInitialization0(vmBuf, penv, vmArgs, hasSpecialVmOptions);
        }

        private static int finishInitialization0(JNIJavaVMPointer vmBuf, JNIEnvironmentPointer penv, JNIJavaVMInitArgs vmArgs, boolean hasSpecialVmOptions) {
            WordPointer javaVmIdPointer = WordFactory.nullPointer();
            if (hasSpecialVmOptions) {
                javaVmIdPointer = parseVMOptions(vmArgs);
            }

            JNIJavaVM javaVm = JNIFunctionTables.singleton().getGlobalJavaVM();
            JNIJavaVMList.addJavaVM(javaVm);
            if (javaVmIdPointer.isNonNull()) {
                long javaVmId = IsolateUtil.getIsolateID();
                javaVmIdPointer.write(WordFactory.pointer(javaVmId));
            }
            RuntimeSupport.getRuntimeSupport().addTearDownHook(isFirstIsolate -> JNIJavaVMList.removeJavaVM(javaVm));
            vmBuf.write(javaVm);
            penv.write(JNIThreadLocalEnvironment.getAddress());
            return JNIErrors.JNI_OK();
        }

        static WordPointer parseVMOptions(JNIJavaVMInitArgs vmArgs) {
            WordPointer javaVmIdPointer = WordFactory.nullPointer();
            Pointer p = (Pointer) vmArgs.getOptions();
            int vmArgc = vmArgs.getNOptions();
            for (int i = 0; i < vmArgc; i++) {
                JNIJavaVMOption option = (JNIJavaVMOption) p.add(i * SizeOf.get(JNIJavaVMOption.class));
                CCharPointer optionString = option.getOptionString();
                if (isJavaVmId(optionString)) {
                    javaVmIdPointer = option.getExtraInfo();
                } else {
                    FunctionPointerLogHandler.parseJniVMOption(optionString, option.getExtraInfo());
                }
            }
            FunctionPointerLogHandler.afterParsingJniVMOptions();
            return javaVmIdPointer;
        }
    }
}
