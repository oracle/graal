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
package com.oracle.truffle.espresso.libjvm;

import static com.oracle.svm.core.c.function.CEntryPointOptions.Publish.SymbolOnly;
import static com.oracle.truffle.espresso.libjvm.nativeapi.JNIVersion.JNI_VERSION_1_1;
import static com.oracle.truffle.espresso.libjvm.nativeapi.JNIVersion.JNI_VERSION_1_2;

import com.oracle.truffle.espresso.libjvm.nativeapi.JNIFunctionPointerTypes;
import com.oracle.truffle.espresso.libjvm.nativeapi.JNIFunctionPointerTypes.GetEnvFunctionPointer;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointSetup.LeaveDetachThreadEpilogue;
import com.oracle.svm.core.c.function.CEntryPointSetup.LeaveTearDownIsolateEpilogue;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.truffle.espresso.libjvm.nativeapi.JNIEnvironmentPointer;
import com.oracle.truffle.espresso.libjvm.nativeapi.JNIErrors;
import com.oracle.truffle.espresso.libjvm.nativeapi.JNIFunctionPointerTypes.AttachCurrentThreadFunctionPointer;
import com.oracle.truffle.espresso.libjvm.nativeapi.JNIFunctionPointerTypes.DestroyJavaVMFunctionPointer;
import com.oracle.truffle.espresso.libjvm.nativeapi.JNIFunctionPointerTypes.DetachCurrentThreadFunctionPointer;
import com.oracle.truffle.espresso.libjvm.nativeapi.JNIInvokeInterface;
import com.oracle.truffle.espresso.libjvm.nativeapi.JNIJavaVM;
import com.oracle.truffle.espresso.libjvm.nativeapi.JNIJavaVMAttachArgs;
import com.oracle.truffle.espresso.libjvm.nativeapi.JNIJavaVMInitArgs;
import com.oracle.truffle.espresso.libjvm.nativeapi.JNIJavaVMOption;
import com.oracle.truffle.espresso.libjvm.nativeapi.JNIJavaVMPointer;
import com.oracle.truffle.espresso.libjvm.nativeapi.JNIVersion;

public class JNIInvocationInterface {
    // Checkstyle: stop method name check

    static class JNICreateJavaVMPrologue {
        @SuppressWarnings("unused")
        static void enter(JNIJavaVMPointer vmBuf, JNIEnvironmentPointer penv, JNIJavaVMInitArgs vmArgs) {
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

    public static final CEntryPointLiteral<CFunctionPointer> CREATE_JAVA_VM_SYMBOL = CEntryPointLiteral.create(JNIInvocationInterface.class, "JNI_CreateJavaVM", JNIJavaVMPointer.class, JNIEnvironmentPointer.class,
                    JNIJavaVMInitArgs.class);

    @CEntryPoint(name = "JNI_CreateJavaVM")
    @CEntryPointOptions(prologue = JNICreateJavaVMPrologue.class, publishAs = SymbolOnly)
    static int JNI_CreateJavaVM(JNIJavaVMPointer javaVMPointer, JNIEnvironmentPointer penv, JNIJavaVMInitArgs args) {
        System.err.println("JNI_CreateJavaVM");
        System.err.printf(" version: %s (%d)%n", JNIVersion.versionString(args.getVersion()), args.getVersion());
        System.err.println(" ignoreUnrecognized:" + args.getIgnoreUnrecognized());
        System.err.println(" noptions:" + args.getNOptions());
        Pointer p = (Pointer) args.getOptions();
        int count = args.getNOptions();
        for (int i = 0; i < count; i++) {
            JNIJavaVMOption option = (JNIJavaVMOption) p.add(i * SizeOf.get(JNIJavaVMOption.class));
            CCharPointer str = option.getOptionString();
            if (str.isNonNull()) {
                String optionString = CTypeConversion.toJavaString(option.getOptionString());
                System.err.printf(" * %s: %016x%n", optionString, option.getExtraInfo().rawValue());
            } else {
                System.err.println(" * NULL");
            }
        }
        if (!LibJVM.isSupportedJniVersion(args.getVersion())) {
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
            System.err.println("Could not retrieve the JavaVM");
            return JNIErrors.JNI_ERR();
        }
        JNIJavaVM espressoJavaVM = WordFactory.pointer(java.asNativePointer());
        JNIJavaVM javaVM = UnmanagedMemory.calloc(SizeOf.unsigned(JNIJavaVM.class));
        JNIInvokeInterface invokeInterface = UnmanagedMemory.calloc(SizeOf.unsigned(JNIInvokeInterface.class));
        javaVM.setFunctions(invokeInterface);
        invokeInterface.setEspressoJavaVM(espressoJavaVM);
        invokeInterface.setIsolate(CurrentIsolate.getIsolate());

        GetEnvFunctionPointer getEnv = espressoJavaVM.getFunctions().getGetEnv();
        invokeInterface.setGetEnv(getEnv); // directly forward to Espresso
        invokeInterface.setDestroyJavaVM(DESTROY_JAVA_VM.getFunctionPointer());
        invokeInterface.setAttachCurrentThread(ATTACH_CURRENT_THREAD.getFunctionPointer());
        invokeInterface.setAttachCurrentThreadAsDaemon(ATTACH_CURRENT_THREAD_AS_DAEMON.getFunctionPointer());
        invokeInterface.setDetachCurrentThread(DETACH_CURRENT_THREAD.getFunctionPointer());

        javaVMPointer.write(javaVM);
        getEnv.invoke(espressoJavaVM, penv, JNIVersion.JNI_VERSION_1_2());
        return JNIErrors.JNI_OK();
    }

    @CEntryPoint(name = "JNI_GetCreatedJavaVMs")
    @CEntryPointOptions(prologue = CEntryPointOptions.NoPrologue.class, epilogue = CEntryPointOptions.NoEpilogue.class, publishAs = CEntryPointOptions.Publish.SymbolOnly)
    @Uninterruptible(reason = "No Java context.")
    static int JNI_GetCreatedJavaVMs(JNIJavaVMPointer vmBuf, int bufLen, CIntPointer nVMs) {
        JNIJavaVMList.gather(vmBuf, bufLen, nVMs);
        return JNIErrors.JNI_OK();
    }

    @CEntryPoint(name = "JNI_GetDefaultJavaVMInitArgs")
    @CEntryPointOptions(prologue = CEntryPointOptions.NoPrologue.class, epilogue = CEntryPointOptions.NoEpilogue.class, publishAs = CEntryPointOptions.Publish.SymbolOnly)
    @Uninterruptible(reason = "No Java context")
    static int JNI_GetDefaultJavaVMInitArgs(JNIJavaVMInitArgs vmArgs) {
        int version = vmArgs.getVersion();
        if (!LibJVM.isSupportedJniVersion(version)) {
            return JNIErrors.JNI_ERR();
        }
        if (version == JNI_VERSION_1_1()) {
            vmArgs.setVersion(JNI_VERSION_1_2());
        }
        return JNIErrors.JNI_OK();
    }

    static final CEntryPointLiteral<AttachCurrentThreadFunctionPointer> ATTACH_CURRENT_THREAD = CEntryPointLiteral.create(JNIInvocationInterface.class, "AttachCurrentThread", JNIJavaVM.class,
                    JNIEnvironmentPointer.class, JNIJavaVMAttachArgs.class);

    @CEntryPoint
    @CEntryPointOptions(prologue = Support.JNIJavaVMEnterAttachThreadManualJavaThreadPrologue.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    static int AttachCurrentThread(JNIJavaVM vm, JNIEnvironmentPointer penv, JNIJavaVMAttachArgs args) {
        return vm.getFunctions().getEspressoJavaVM().getFunctions().getAttachCurrentThread().invoke(vm, penv, args);
    }

    static final CEntryPointLiteral<AttachCurrentThreadFunctionPointer> ATTACH_CURRENT_THREAD_AS_DAEMON = CEntryPointLiteral.create(JNIInvocationInterface.class, "AttachCurrentThreadAsDaemon",
                    JNIJavaVM.class,
                    JNIEnvironmentPointer.class, JNIJavaVMAttachArgs.class);

    @CEntryPoint
    @CEntryPointOptions(prologue = Support.JNIJavaVMEnterAttachThreadManualJavaThreadPrologue.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    static int AttachCurrentThreadAsDaemon(JNIJavaVM vm, JNIEnvironmentPointer penv, JNIJavaVMAttachArgs args) {
        return vm.getFunctions().getEspressoJavaVM().getFunctions().getAttachCurrentThreadAsDaemon().invoke(vm, penv, args);
    }

    static final CEntryPointLiteral<DetachCurrentThreadFunctionPointer> DETACH_CURRENT_THREAD = CEntryPointLiteral.create(JNIInvocationInterface.class, "DetachCurrentThread", JNIJavaVM.class);

    @CEntryPoint
    @CEntryPointOptions(prologue = Support.JNIJavaVMEnterAttachThreadEnsureJavaThreadPrologue.class, epilogue = LeaveDetachThreadEpilogue.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    static int DetachCurrentThread(JNIJavaVM vm) {
        return vm.getFunctions().getEspressoJavaVM().getFunctions().getDetachCurrentThread().invoke(vm);
    }

    static final CEntryPointLiteral<DestroyJavaVMFunctionPointer> DESTROY_JAVA_VM = CEntryPointLiteral.create(JNIInvocationInterface.class, "DestroyJavaVM", JNIJavaVM.class);

    @CEntryPoint
    @CEntryPointOptions(prologue = Support.JNIJavaVMEnterAttachThreadEnsureJavaThreadPrologue.class, epilogue = LeaveTearDownIsolateEpilogue.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    @SuppressWarnings("unused")
    static int DestroyJavaVM(JNIJavaVM vm) {
        // XXX Do we really need to join the host threads??
        JavaThreads.singleton().joinAllNonDaemons();
        return vm.getFunctions().getEspressoJavaVM().getFunctions().getDestroyJavaVM().invoke(vm);
    }

    // Checkstyle: resume

    static class Support {
        // This inner class exists because all outer methods must match API functions

        static class JNIJavaVMEnterAttachThreadManualJavaThreadPrologue {
            static void enter(JNIJavaVM vm) {
                if (CEntryPointActions.enterAttachThread(vm.getFunctions().getIsolate(), false) != CEntryPointErrors.NO_ERROR) {
                    CEntryPointActions.bailoutInPrologue(JNIErrors.JNI_ERR());
                }
            }
        }

        static class JNIJavaVMEnterAttachThreadEnsureJavaThreadPrologue {
            static void enter(JNIJavaVM vm) {
                if (CEntryPointActions.enterAttachThread(vm.getFunctions().getIsolate(), true) != CEntryPointErrors.NO_ERROR) {
                    CEntryPointActions.bailoutInPrologue(JNIErrors.JNI_ERR());
                }
            }
        }
    }
}
