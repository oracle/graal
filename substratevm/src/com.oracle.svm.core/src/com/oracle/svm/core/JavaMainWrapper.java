/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.c.function.CEntryPointSetup;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.jni.JNIJavaVMList;
import com.oracle.svm.core.jni.functions.JNIFunctionTables;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.ThreadingSupportImpl;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.thread.VMThreads.OSThreadHandle;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.word.Word;

@InternalVMMethod
public class JavaMainWrapper {
    /*
     * Parameters used to create the main isolate, including C runtime argument count and argument
     * vector
     */
    public static final CGlobalData<CEntryPointCreateIsolateParameters> MAIN_ISOLATE_PARAMETERS = CGlobalDataFactory.createBytes(() -> SizeOf.get(CEntryPointCreateIsolateParameters.class));

    static {
        /* WordFactory.boxFactory is initialized by the static initializer of Word. */
        Word.ensureInitialized();
    }
    private static UnsignedWord argvLength = WordFactory.zero();

    public static class JavaMainSupport {

        private final MethodHandle javaMainHandle;
        private final MethodHandle javaMainClassCtorHandle;
        final String javaMainClassName;

        public String[] mainArgs;

        private final boolean mainWithoutArgs;
        private final boolean mainNonstatic;

        @Platforms(Platform.HOSTED_ONLY.class)
        public JavaMainSupport(Method javaMainMethod) throws IllegalAccessException {
            if (instanceMainMethodSupported()) {
                javaMainMethod.setAccessible(true);
                int mods = javaMainMethod.getModifiers();
                this.mainNonstatic = !Modifier.isStatic(mods);
                this.mainWithoutArgs = javaMainMethod.getParameterCount() == 0;
                MethodHandle mainHandle = MethodHandles.lookup().unreflect(javaMainMethod);
                MethodHandle ctorHandle = null;
                if (mainNonstatic) {
                    // Instance main
                    try {
                        Constructor<?> ctor = ReflectionUtil.lookupConstructor(javaMainMethod.getDeclaringClass());
                        ctorHandle = MethodHandles.lookup().unreflectConstructor(ctor);
                    } catch (ReflectionUtil.ReflectionUtilError ex) {
                        throw UserError.abort(ex, "No non-private zero argument constructor found in class %s", ClassUtil.getUnqualifiedName(javaMainMethod.getDeclaringClass()));
                    }
                }
                this.javaMainHandle = mainHandle;
                this.javaMainClassCtorHandle = ctorHandle;
            } else {
                this.mainNonstatic = false;
                this.mainWithoutArgs = false;
                this.javaMainHandle = MethodHandles.lookup().unreflect(javaMainMethod);
                this.javaMainClassCtorHandle = null;
            }
            this.javaMainClassName = javaMainMethod.getDeclaringClass().getName();
        }

        public String getJavaCommand() {
            if (mainArgs != null) {
                StringBuilder commandLine = new StringBuilder(javaMainClassName);

                for (String arg : mainArgs) {
                    commandLine.append(' ');
                    commandLine.append(arg);
                }
                return commandLine.toString();
            }
            return null;
        }

        public List<String> getInputArguments() {
            CEntryPointCreateIsolateParameters args = MAIN_ISOLATE_PARAMETERS.get();
            if (args.getArgv().isNonNull() && args.getArgc() > 0) {
                String[] unmodifiedArgs = SubstrateUtil.convertCToJavaArgs(args.getArgc(), args.getArgv());
                List<String> inputArgs = new ArrayList<>(Arrays.asList(unmodifiedArgs));

                if (mainArgs != null) {
                    inputArgs.removeAll(Arrays.asList(mainArgs));
                }
                return Collections.unmodifiableList(inputArgs);
            }
            return Collections.emptyList();
        }

    }

    public static void invokeMain(String[] args) throws Throwable {
        JavaMainSupport javaMainSupport = ImageSingletons.lookup(JavaMainSupport.class);
        if (javaMainSupport.mainNonstatic) {
            Object instance = javaMainSupport.javaMainClassCtorHandle.invoke();
            if (javaMainSupport.mainWithoutArgs) {
                javaMainSupport.javaMainHandle.invoke(instance);
            } else {
                javaMainSupport.javaMainHandle.invoke(instance, args);
            }
        } else {
            if (javaMainSupport.mainWithoutArgs) {
                javaMainSupport.javaMainHandle.invokeExact();
            } else {
                javaMainSupport.javaMainHandle.invokeExact(args);
            }
        }
    }

    /**
     * Determines whether instance main methodes are enabled. See JDK-8306112: Implementation of JEP
     * 445: Unnamed Classes and Instance Main Methods (Preview).
     */
    public static boolean instanceMainMethodSupported() {
        var previewFeature = ReflectionUtil.lookupClass(true, "jdk.internal.misc.PreviewFeatures");
        try {
            return previewFeature != null && (Boolean) previewFeature.getDeclaredMethod("isEnabled").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    @Uninterruptible(reason = "The caller initialized the thread state, so the callees do not need to be uninterruptible.", calleeMustBe = false)
    private static int runCore() {
        return runCore0();
    }

    /**
     * Used by JavaMainWrapper and any user supplied main entry point (from
     * {@link org.graalvm.nativeimage.hosted.Feature.AfterRegistrationAccess}).
     */
    private static int runCore0() {
        try {
            if (SubstrateOptions.ParseRuntimeOptions.getValue()) {
                /*
                 * When options are not parsed yet, it is also too early to run the startup hooks
                 * because they often depend on option values. The user is expected to manually run
                 * the startup hooks after setting all option values.
                 */
                VMRuntime.initialize();
            }

            if (SubstrateOptions.DumpHeapAndExit.getValue()) {
                return VMInspectionOptions.dumpImageHeap() ? 0 : 1;
            }

            // Ensure that native code using JNI_GetCreatedJavaVMs finds this isolate.
            JNIJavaVMList.addJavaVM(JNIFunctionTables.singleton().getGlobalJavaVM());

            /*
             * Invoke the application's main method. Invoking the main method via a method handle
             * preserves exceptions, while invoking the main method via reflection would wrap
             * exceptions in a InvocationTargetException.
             */
            JavaMainSupport mainSupport = ImageSingletons.lookup(JavaMainSupport.class);
            invokeMain(mainSupport.mainArgs);

            return 0;
        } catch (Throwable ex) {
            JavaThreads.dispatchUncaughtException(Thread.currentThread(), ex);

            /*
             * The application terminated with exception. Note that the exit code is set to 1 even
             * if an uncaught exception handler is registered. This behavior is the same on the Java
             * HotSpot VM.
             */
            return 1;
        }
    }

    @Uninterruptible(reason = "The caller initialized the thread state, so the callees do not need to be uninterruptible.", calleeMustBe = false)
    private static void runShutdown() {
        ThreadingSupportImpl.pauseRecurringCallback("Recurring callbacks can't be executed during shutdown.");
        runShutdown0();
    }

    private static void runShutdown0() {
        try {
            PlatformThreads.ensureCurrentAssigned("DestroyJavaVM", null, false);
        } catch (Throwable e) {
            Log.log().string("PlatformThreads.ensureCurrentAssigned() failed during shutdown: ").exception(e).newline();
            return;
        }

        /* Wait for all non-daemon threads to exit. */
        PlatformThreads.singleton().joinAllNonDaemons();

        try {
            /*
             * Run shutdown hooks (both our own hooks and application-registered hooks) and teardown
             * hooks. Note that this can start new non-daemon threads. We are not responsible to
             * wait until they have exited.
             */
            RuntimeSupport.getRuntimeSupport().shutdown();
            RuntimeSupport.executeTearDownHooks();
        } catch (Throwable e) {
            Log.log().string("Exception occurred while executing shutdown hooks: ").exception(e).newline();
        }
    }

    @Uninterruptible(reason = "Thread state not set up yet.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
    public static int run(int argc, CCharPointerPointer argv) {
        if (SubstrateOptions.RunMainInNewThread.getValue()) {
            return doRunInNewThread(argc, argv);
        } else {
            return doRun(argc, argv);
        }
    }

    @Uninterruptible(reason = "Thread state not setup yet.")
    private static int doRun(int argc, CCharPointerPointer argv) {
        try {
            CPUFeatureAccess cpuFeatureAccess = ImageSingletons.lookup(CPUFeatureAccess.class);
            cpuFeatureAccess.verifyHostSupportsArchitectureEarlyOrExit();

            // Create the isolate and attach the current C thread as the main Java thread.
            EnterCreateIsolateWithCArgumentsPrologue.enter(argc, argv);
            assert !VMThreads.wasStartedByCurrentIsolate(CurrentIsolate.getCurrentThread()) : "re-attach would cause issues otherwise";

            Isolate isolate = CurrentIsolate.getIsolate();
            int exitCode = runCore();
            CEntryPointSetup.LeaveDetachThreadEpilogue.leave();

            // Re-attach the same C thread as another Java thread.
            EnterAttachThreadForShutdown.enter(isolate);
            runShutdown();
            CEntryPointSetup.LeaveDetachThreadEpilogue.leave();

            return exitCode;
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static final CGlobalData<CCharPointer> START_THREAD_UNMANAGED_ERROR_MESSAGE = CGlobalDataFactory
                    .createCString("Running main entry point in a new platform thread failed. Platform thread failed to start.");
    private static final CGlobalData<CCharPointer> JOIN_THREAD_UNMANAGED_ERROR_MESSAGE = CGlobalDataFactory.createCString("Thread that the main entry point was running on failed to join.");

    @Uninterruptible(reason = "Thread state not setup yet.")
    private static int doRunInNewThread(int argc, CCharPointerPointer argv) {
        MAIN_ISOLATE_PARAMETERS.get().setArgc(argc);
        MAIN_ISOLATE_PARAMETERS.get().setArgv(argv);
        long stackSize = SubstrateOptions.StackSize.getHostedValue();
        OSThreadHandle osThreadHandle = PlatformThreads.singleton().startThreadUnmanaged(RUN_MAIN_ROUTINE.get(), WordFactory.nullPointer(), (int) stackSize);
        if (osThreadHandle.isNull()) {
            CEntryPointActions.failFatally(1, START_THREAD_UNMANAGED_ERROR_MESSAGE.get());
            return 1;
        }
        try {
            WordPointer threadExitStatus = StackValue.get(WordPointer.class);
            boolean joined = PlatformThreads.singleton().joinThreadUnmanaged(osThreadHandle, threadExitStatus);
            if (!joined) {
                CEntryPointActions.failFatally(1, JOIN_THREAD_UNMANAGED_ERROR_MESSAGE.get());
                return 1;
            }
            return (int) threadExitStatus.read().rawValue();
        } finally {
            PlatformThreads.singleton().closeOSThreadHandle(osThreadHandle);
        }
    }

    private static final CGlobalData<CFunctionPointer> RUN_MAIN_ROUTINE = CGlobalDataFactory.forSymbol("__svm_JavaMainWrapper_runMainRoutine");

    private static class RunMainInNewThreadBooleanSupplier implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            if (!ImageSingletons.contains(JavaMainSupport.class)) {
                return false;
            }
            return SubstrateOptions.RunMainInNewThread.getValue();
        }
    }

    @SuppressWarnings("unused")
    @Uninterruptible(reason = "Thread state not setup yet.")
    @CEntryPoint(name = "__svm_JavaMainWrapper_runMainRoutine", include = RunMainInNewThreadBooleanSupplier.class)
    @CEntryPointOptions(prologue = CEntryPointOptions.NoPrologue.class, epilogue = CEntryPointOptions.NoEpilogue.class)
    static WordBase runMainRoutine(PointerBase data) {
        int exitStatus = doRun(MAIN_ISOLATE_PARAMETERS.get().getArgc(), MAIN_ISOLATE_PARAMETERS.get().getArgv());
        return WordFactory.signed(exitStatus);
    }

    private static boolean isArgumentBlockSupported() {
        if (!Platform.includedIn(Platform.LINUX.class) && !Platform.includedIn(Platform.DARWIN.class)) {
            return false;
        }
        CEntryPointCreateIsolateParameters args = MAIN_ISOLATE_PARAMETERS.get();
        if (args.getArgv().isNull() || args.getArgc() <= 0) {
            return false;
        }
        return true;
    }

    /**
     * Argv is an array of C strings (i.e. array of pointers to characters). Each entry points to a
     * different C string corresponding to a program argument. The program argument strings
     * themselves are located in a contiguous block of memory followed by the environment variables
     * key-value strings:
     *
     * <pre>
     * &lt;argument_0&gt;\n&lt;argument_1&gt;\n...&lt;argument_n&gt;\n
     * &lt;env_key_value_1&gt;\n&lt;env_key_value_2&gt;\n...&lt;env_key_value_n&gt;\n
     * </pre>
     *
     * @return maximum length of C chars that can be stored in the program argument part of the
     *         contiguous memory block without writing into the environment variables part.
     */
    public static int getCRuntimeArgumentBlockLength() {
        if (!isArgumentBlockSupported()) {
            return -1;
        }

        CEntryPointCreateIsolateParameters args = MAIN_ISOLATE_PARAMETERS.get();
        CCharPointer firstArgPos = args.getArgv().read(0);
        if (argvLength.equal(WordFactory.zero())) {
            // Get char* to last program argument
            CCharPointer lastArgPos = args.getArgv().read(args.getArgc() - 1);
            // Determine the length of the last program argument
            UnsignedWord lastArgLength = SubstrateUtil.strlen(lastArgPos);
            // Determine maximum C string length that can be stored in the program argument part
            argvLength = WordFactory.unsigned(lastArgPos.rawValue()).add(lastArgLength).subtract(WordFactory.unsigned(firstArgPos.rawValue()));
        }
        return Math.toIntExact(argvLength.rawValue());
    }

    public static boolean setCRuntimeArgument0(String arg0) {
        if (!isArgumentBlockSupported()) {
            throw new UnsupportedOperationException("Argument vector support not available");
        }
        boolean arg0truncation = false;

        try (CCharPointerHolder arg0Pin = CTypeConversion.toCString(arg0)) {
            CCharPointer arg0Pointer = arg0Pin.get();
            UnsignedWord arg0Length = SubstrateUtil.strlen(arg0Pointer);

            UnsignedWord origLength = WordFactory.unsigned(getCRuntimeArgumentBlockLength());
            UnsignedWord newArgLength = origLength;
            if (arg0Length.add(1).belowThan(origLength)) {
                newArgLength = arg0Length.add(1);
            }
            arg0truncation = arg0Length.aboveThan(origLength);

            CCharPointer firstArgPos = MAIN_ISOLATE_PARAMETERS.get().getArgv().read(0);
            // Copy the new arg0 to the original argv[0] position
            UnmanagedMemoryUtil.copy((Pointer) arg0Pointer, (Pointer) firstArgPos, newArgLength);
            // Zero-out the remaining space
            UnmanagedMemoryUtil.fill(((Pointer) firstArgPos).add(newArgLength), origLength.subtract(newArgLength), (byte) 0);
        }

        // Let caller know if truncation happened
        return arg0truncation;
    }

    public static String getCRuntimeArgument0() {
        if (!isArgumentBlockSupported()) {
            throw new UnsupportedOperationException("Argument vector support not available");
        }
        return CTypeConversion.toJavaString(MAIN_ISOLATE_PARAMETERS.get().getArgv().read(0));
    }

    private static class EnterCreateIsolateWithCArgumentsPrologue implements CEntryPointOptions.Prologue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString(
                        "Failed to create the main Isolate.");

        @SuppressWarnings("unused")
        @Uninterruptible(reason = "prologue")
        public static void enter(int paramArgc, CCharPointerPointer paramArgv) {
            CEntryPointCreateIsolateParameters args = MAIN_ISOLATE_PARAMETERS.get();
            args.setVersion(4);
            args.setArgc(paramArgc);
            args.setArgv(paramArgv);
            args.setIgnoreUnrecognizedArguments(false);
            args.setExitWhenArgumentParsingFails(true);

            int code = CEntryPointActions.enterCreateIsolate(args);
            if (code != CEntryPointErrors.NO_ERROR) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }

    public static class EnterAttachThreadForShutdown implements CEntryPointOptions.Prologue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString(
                        "Failed to re-attach the main thread for shutting down the main isolate.");

        @Uninterruptible(reason = "prologue")
        static void enter(Isolate isolate) {
            int code = CEntryPointActions.enterAttachThread(isolate, false);
            if (code != CEntryPointErrors.NO_ERROR) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }
}
