/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

//Checkstyle: allow reflection

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.util.Counter;

import jdk.vm.ci.code.Architecture;

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

        final MethodHandle javaMainHandle;
        final String javaMainClassName;

        public String[] mainArgs;

        @Platforms(Platform.HOSTED_ONLY.class)
        public JavaMainSupport(Method javaMainMethod) throws IllegalAccessException {
            this.javaMainHandle = MethodHandles.lookup().unreflect(javaMainMethod);
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
                String[] unmodifiedArgs = SubstrateUtil.getArgs(args.getArgc(), args.getArgv());
                List<String> inputArgs = new ArrayList<>(Arrays.asList(unmodifiedArgs));

                if (mainArgs != null) {
                    inputArgs.removeAll(Arrays.asList(mainArgs));
                }
                return Collections.unmodifiableList(inputArgs);
            }
            return Collections.emptyList();
        }
    }

    /**
     * Used by JavaMainWrapper and any user supplied main entry point (from
     * {@link org.graalvm.nativeimage.hosted.Feature.AfterRegistrationAccess}).
     */
    @AlwaysInline(value = "Single callee from the main entry point.")
    public static int runCore() {
        Architecture imageArchitecture = ImageSingletons.lookup(SubstrateTargetDescription.class).arch;
        CPUFeatureAccess cpuFeatureAccess = ImageSingletons.lookup(CPUFeatureAccess.class);
        cpuFeatureAccess.verifyHostSupportsArchitecture(imageArchitecture);

        int exitCode;
        try {
            if (SubstrateOptions.ParseRuntimeOptions.getValue()) {
                /*
                 * When options are not parsed yet, it is also too early to run the startup hooks
                 * because they often depend on option values. The user is expected to manually run
                 * the startup hooks after setting all option values.
                 */
                RuntimeSupport.getRuntimeSupport().executeStartupHooks();
            }

            /*
             * Invoke the application's main method. Invoking the main method via a method handle
             * preserves exceptions, while invoking the main method via reflection would wrap
             * exceptions in a InvocationTargetException.
             */
            JavaMainSupport mainSupport = ImageSingletons.lookup(JavaMainSupport.class);
            mainSupport.javaMainHandle.invokeExact(mainSupport.mainArgs);

            /* The application terminated normally. */
            exitCode = 0;

        } catch (Throwable ex) {
            JavaThreads.dispatchUncaughtException(Thread.currentThread(), ex);

            /*
             * The application terminated with exception. Note that the exit code is set to 1 even
             * if an uncaught exception handler is registered. This behavior is the same on the Java
             * HotSpot VM.
             */
            exitCode = 1;

        } finally {
            /*
             * Shutdown sequence: First wait for all non-daemon threads to exit.
             */
            JavaThreads.singleton().joinAllNonDaemons();
            /*
             * Run shutdown hooks (both our own hooks and application-registered hooks. Note that
             * this can start new non-daemon threads. We are not responsible to wait until they have
             * exited.
             */
            RuntimeSupport.getRuntimeSupport().shutdown();

            Counter.logValues();
        }
        return exitCode;
    }

    @CEntryPoint
    @CEntryPointOptions(prologue = EnterCreateIsolateWithCArgumentsPrologue.class, include = CEntryPointOptions.NotIncludedAutomatically.class)
    @SuppressWarnings("unused")
    public static int run(int argc, CCharPointerPointer argv) {
        return runCore();
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
            MemoryUtil.copyConjointMemoryAtomic(WordFactory.pointer(arg0Pointer.rawValue()), WordFactory.pointer(firstArgPos.rawValue()), newArgLength);
            // Zero-out the remaining space
            MemoryUtil.fillToMemoryAtomic((Pointer) WordFactory.unsigned(firstArgPos.rawValue()).add(newArgLength), origLength.subtract(newArgLength), (byte) 0);
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

    private static class EnterCreateIsolateWithCArgumentsPrologue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString(
                        "Failed to create the main Isolate.");

        @SuppressWarnings("unused")
        public static void enter(int paramArgc, CCharPointerPointer paramArgv) {
            CEntryPointCreateIsolateParameters args = MAIN_ISOLATE_PARAMETERS.get();
            args.setVersion(3);
            args.setArgc(paramArgc);
            args.setArgv(paramArgv);

            int code = CEntryPointActions.enterCreateIsolate(args);
            if (code != 0) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }
}
