/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import static com.oracle.svm.core.option.RuntimeOptionParser.DEFAULT_OPTION_PREFIX;
import static com.oracle.svm.core.option.RuntimeOptionParser.GRAAL_OPTION_PREFIX;
import static com.oracle.svm.core.option.SubstrateOptionsParser.BooleanOptionFormat.NAME_VALUE;
import static com.oracle.svm.core.option.SubstrateOptionsParser.BooleanOptionFormat.PLUS_MINUS;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.allocationprofile.AllocationSite;
import com.oracle.svm.core.amd64.AMD64CPUFeatureAccess;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointSetup.EnterCreateIsolatePrologue;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jdk.RuntimeFeature;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionParser;
import com.oracle.svm.core.option.XOptions;
import com.oracle.svm.core.properties.RuntimePropertyParser;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.util.Counter;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.TargetDescription;

public class JavaMainWrapper {

    /* C runtime argument count and argument vector */
    private static int argc;
    private static CCharPointerPointer argv;
    /* Remember original argument vector length */

    static {
        /* WordFactory.boxFactory is initialized by the static initializer of Word. */
        Word.ensureInitialized();
    }
    private static UnsignedWord argvLength = WordFactory.zero();

    public static class JavaMainSupport {

        public static void executeStartupHooks() {
            RuntimeSupport runtimeSupport = RuntimeSupport.getRuntimeSupport();
            executeHooks(runtimeSupport.getStartupHooks(), runtimeSupport::removeStartupHook);
        }

        public static void executeShutdownHooks() {
            RuntimeSupport runtimeSupport = RuntimeSupport.getRuntimeSupport();
            executeHooks(runtimeSupport.getShutdownHooks(), runtimeSupport::removeShutdownHook);
        }

        private static void executeHooks(List<Runnable> hooks, Consumer<Runnable> deleteHookAction) {
            List<Throwable> hookExceptions = new ArrayList<>();

            for (Runnable hook : hooks) {
                deleteHookAction.accept(hook);
                try {
                    hook.run();
                } catch (Throwable ex) {
                    hookExceptions.add(ex);
                }
            }

            // report all hook exceptions, but do not re-throw
            if (hookExceptions.size() > 0) {
                for (Throwable ex : hookExceptions) {
                    ex.printStackTrace(Log.logStream());
                }
            }
        }

        private final MethodHandle javaMainHandle;

        @Platforms(Platform.HOSTED_ONLY.class)
        public JavaMainSupport(MethodHandle javaMainHandle) {
            this.javaMainHandle = javaMainHandle;
        }

        @Fold
        public MethodHandle getJavaMainHandle() {
            assert javaMainHandle != null;
            return javaMainHandle;
        }
    }

    /** A shutdown hook to print the PrintGCSummary output. */
    public static class PrintGCSummaryShutdownHook implements Runnable {
        @Override
        public void run() {
            Heap.getHeap().getGC().printGCSummary();
        }
    }

    private static final Thread preallocatedThread;
    static {
        preallocatedThread = new Thread("main");
        preallocatedThread.setDaemon(false);
    }

    @CEntryPoint
    @CEntryPointOptions(prologue = EnterCreateIsolatePrologue.class, include = CEntryPointOptions.NotIncludedAutomatically.class)
    public static int run(int paramArgc, CCharPointerPointer paramArgv) throws Exception {
        JavaThreads.singleton().assignJavaThread(preallocatedThread);

        JavaMainWrapper.argc = paramArgc;
        JavaMainWrapper.argv = paramArgv;
        Architecture imageArchitecture = ImageSingletons.lookup(TargetDescription.class).arch;
        AMD64CPUFeatureAccess.verifyHostSupportsArchitecture(imageArchitecture);
        String[] args = SubstrateUtil.getArgs(paramArgc, paramArgv);
        if (SubstrateOptions.ParseRuntimeOptions.getValue()) {
            args = RuntimeOptionParser.singleton().parse(args, DEFAULT_OPTION_PREFIX, PLUS_MINUS);
            args = RuntimeOptionParser.singleton().parse(args, GRAAL_OPTION_PREFIX, NAME_VALUE);
            args = XOptions.singleton().parse(args);
            args = RuntimePropertyParser.parse(args);
        }
        try {
            final RuntimeSupport rs = RuntimeSupport.getRuntimeSupport();
            if (AllocationSite.Options.AllocationProfiling.getValue()) {
                rs.addShutdownHook(new AllocationSite.AllocationProfilingShutdownHook());
            }
            if (SubstrateOptions.PrintGCSummary.getValue()) {
                rs.addShutdownHook(new PrintGCSummaryShutdownHook());
            }
            try {
                JavaMainSupport.executeStartupHooks();
                ImageSingletons.lookup(JavaMainSupport.class).getJavaMainHandle().invokeExact(args);
            } finally { // always execute the shutdown hooks
                JavaMainSupport.executeShutdownHooks();
            }
        } catch (Throwable ex) {
            SnippetRuntime.reportUnhandledExceptionJava(ex);
        }

        JavaThreads.singleton().joinAllNonDaemons();
        Counter.logValues();

        return 0;
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
    public static long getCRuntimeArgumentBlockLength() {
        VMError.guarantee(argv.notEqual(WordFactory.zero()) && argc > 0, "Requires JavaMainWrapper.run(int, CCharPointerPointer) entry point!");

        CCharPointer firstArgPos = argv.read(0);
        if (argvLength.equal(WordFactory.zero())) {
            // Get char* to last program argument
            CCharPointer lastArgPos = argv.read(argc - 1);
            // Determine the length of the last program argument
            UnsignedWord lastArgLength = SubstrateUtil.strlen(lastArgPos);
            // Determine maximum C string length that can be stored in the program argument part
            argvLength = WordFactory.unsigned(lastArgPos.rawValue()).add(lastArgLength).subtract(WordFactory.unsigned(firstArgPos.rawValue()));
        }
        return argvLength.rawValue();
    }

    public static boolean setCRuntimeArgument0(String arg0) {
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

            CCharPointer firstArgPos = argv.read(0);
            // Copy the new arg0 to the original argv[0] position
            MemoryUtil.copyConjointMemoryAtomic(WordFactory.pointer(arg0Pointer.rawValue()), WordFactory.pointer(firstArgPos.rawValue()), newArgLength);
            // Zero-out the remaining space
            MemoryUtil.fillToMemoryAtomic((Pointer) WordFactory.unsigned(firstArgPos.rawValue()).add(newArgLength), origLength.subtract(newArgLength), (byte) 0);
        }

        // Let caller know if truncation happened
        return arg0truncation;
    }

    @AutomaticFeature
    public static class ExposeCRuntimeArgumentBlockFeature implements Feature {
        @Override
        public List<Class<? extends Feature>> getRequiredFeatures() {
            return Arrays.asList(RuntimeFeature.class);
        }

        @Override
        public void afterRegistration(AfterRegistrationAccess access) {
            RuntimeSupport rs = RuntimeSupport.getRuntimeSupport();
            rs.addCommandPlugin(new GetCRuntimeArgumentBlockLengthCommand());
            rs.addCommandPlugin(new SetCRuntimeArgument0Command());
        }
    }

    private static class GetCRuntimeArgumentBlockLengthCommand implements CompilerCommandPlugin {
        @Override
        public String name() {
            return "com.oracle.svm.core.JavaMainWrapper.getCRuntimeArgumentBlockLength()long";
        }

        @Override
        public Object apply(Object[] args) {
            return getCRuntimeArgumentBlockLength();
        }
    }

    private static class SetCRuntimeArgument0Command implements CompilerCommandPlugin {
        @Override
        public String name() {
            return "com.oracle.svm.core.JavaMainWrapper.setCRuntimeArgument0(String)boolean";
        }

        @Override
        public Object apply(Object[] args) {
            return setCRuntimeArgument0((String) args[0]);
        }
    }
}
