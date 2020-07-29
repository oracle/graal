/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.GraalOptions.TrackNodeSourcePosition;
import static org.graalvm.compiler.core.common.SpectrePHTMitigations.None;
import static org.graalvm.compiler.core.common.SpectrePHTMitigations.Options.SpectrePHTBarriers;
import static org.graalvm.compiler.options.OptionType.Expert;
import static org.graalvm.compiler.options.OptionType.User;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionStability;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.jdk.JavaNetSubstitutions;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.option.XOptions;
import com.oracle.svm.core.util.UserError;

public class SubstrateOptions {

    @Option(help = "Class containing the default entry point method. Optional if --shared is used.", type = OptionType.User)//
    public static final HostedOptionKey<String> Class = new HostedOptionKey<>("");

    @Option(help = "Name of the main entry point method. Optional if --shared is used.")//
    public static final HostedOptionKey<String> Method = new HostedOptionKey<>("main");

    @Option(help = "Name of the output file to be generated", type = OptionType.User)//
    public static final HostedOptionKey<String> Name = new HostedOptionKey<>("");

    @APIOption(name = "shared")//
    @Option(help = "Build shared library")//
    public static final HostedOptionKey<Boolean> SharedLibrary = new HostedOptionKey<>(false);

    @APIOption(name = "static")//
    @Option(help = "Build statically linked executable (requires static libc and zlib)")//
    public static final HostedOptionKey<Boolean> StaticExecutable = new HostedOptionKey<>(false);

    @Option(help = "Builds a statically linked executable with libc dynamically linked", type = Expert, stability = OptionStability.EXPERIMENTAL)//
    public static final HostedOptionKey<Boolean> StaticExecutableWithDynamicLibC = new HostedOptionKey<Boolean>(false) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            StaticExecutable.update(values, true);
            super.onValueUpdate(values, oldValue, newValue);
        }
    };

    public static final int ForceFallback = 10;
    public static final int Automatic = 5;
    public static final int NoFallback = 0;

    public static final String OptionNameForceFallback = "force-fallback";
    public static final String OptionNameAutoFallback = "auto-fallback";
    public static final String OptionNameNoFallback = "no-fallback";

    @APIOption(name = OptionNameForceFallback, fixedValue = "" + ForceFallback, customHelp = "force building of fallback image") //
    @APIOption(name = OptionNameAutoFallback, fixedValue = "" + Automatic, customHelp = "build stand-alone image if possible") //
    @APIOption(name = OptionNameNoFallback, fixedValue = "" + NoFallback, customHelp = "build stand-alone image or report failure") //
    @Option(help = "Define when fallback-image generation should be used.")//
    public static final HostedOptionKey<Integer> FallbackThreshold = new HostedOptionKey<>(Automatic);

    public static final String IMAGE_CLASSPATH_PREFIX = "-imagecp";
    public static final String WATCHPID_PREFIX = "-watchpid";
    private static ValueUpdateHandler optimizeValueUpdateHandler;
    private static ValueUpdateHandler debugInfoValueUpdateHandler = SubstrateOptions::defaultDebugInfoValueUpdateHandler;

    @Option(help = "Show available options based on comma-separated option-types (allowed categories: User, Expert, Debug).")//
    public static final OptionKey<String> PrintFlags = new OptionKey<>(null);

    @Option(help = "Print extra help, if available, based on comma-separated option names. Pass * to show all options that contain extra help.")//
    public static final OptionKey<String> PrintFlagsWithExtraHelp = new OptionKey<>(null);

    @Option(help = "Control native-image code optimizations: 0 - no optimizations, 1 - basic optimizations, 2 - aggressive optimizations.", type = OptionType.User)//
    public static final HostedOptionKey<Integer> Optimize = new HostedOptionKey<Integer>(2) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Integer oldValue, Integer newValue) {
            SubstrateOptions.IncludeNodeSourcePositions.update(values, newValue < 1);
            SubstrateOptions.AOTInline.update(values, newValue > 0);
            SubstrateOptions.AOTTrivialInline.update(values, newValue > 0);
            if (optimizeValueUpdateHandler != null) {
                optimizeValueUpdateHandler.onValueUpdate(values, oldValue, newValue);
            }
        }
    };

    public interface ValueUpdateHandler {
        void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Integer oldValue, Integer newValue);
    }

    public static void setOptimizeValueUpdateHandler(ValueUpdateHandler updateHandler) {
        SubstrateOptions.optimizeValueUpdateHandler = updateHandler;
    }

    public static void setDebugInfoValueUpdateHandler(ValueUpdateHandler updateHandler) {
        SubstrateOptions.debugInfoValueUpdateHandler = updateHandler;
    }

    @Option(help = "Track NodeSourcePositions during runtime-compilation")//
    public static final HostedOptionKey<Boolean> IncludeNodeSourcePositions = new HostedOptionKey<>(false);

    @Option(help = "Search path for C libraries passed to the linker (list of comma-separated directories)")//
    public static final HostedOptionKey<String[]> CLibraryPath = new HostedOptionKey<>(null);

    @Option(help = "Path passed to the linker as the -rpath (list of comma-separated directories)")//
    public static final HostedOptionKey<String[]> LinkerRPath = new HostedOptionKey<>(null);

    @Option(help = "Directory of the image file to be generated", type = OptionType.User)//
    public static final HostedOptionKey<String> Path = new HostedOptionKey<>(null);

    @APIOption(name = "-ea", customHelp = "enable assertions in the generated image")//
    @APIOption(name = "-da", kind = APIOption.APIOptionKind.Negated, customHelp = "disable assertions in the generated image")//
    @Option(help = "Enable or disable Java assert statements at run time", type = OptionType.User)//
    public static final HostedOptionKey<Boolean> RuntimeAssertions = new HostedOptionKey<>(false);

    public static boolean getRuntimeAssertionsForClass(String name) {
        return RuntimeAssertions.getValue() && getRuntimeAssertionsFilter().test(name);
    }

    @Fold
    static Predicate<String> getRuntimeAssertionsFilter() {
        return makeFilter(RuntimeAssertionsFilter.getValue());
    }

    @Option(help = "Use a card remembered set heap for GC")//
    public static final HostedOptionKey<Boolean> UseCardRememberedSetHeap = new HostedOptionKey<>(true);

    @Option(help = "Print summary GC information after each collection")//
    public static final RuntimeOptionKey<Boolean> PrintGC = new RuntimeOptionKey<>(false);

    @Option(help = "Print more information about the heap before and after each collection")//
    public static final RuntimeOptionKey<Boolean> VerboseGC = new RuntimeOptionKey<>(false);

    @Option(help = "Verify the heap before and after each collection.")//
    public static final HostedOptionKey<Boolean> VerifyHeap = new HostedOptionKey<>(false);

    @Option(help = "The minimum heap size at run-time, in bytes.", type = OptionType.User)//
    public static final RuntimeOptionKey<Long> MinHeapSize = new RuntimeOptionKey<Long>(0L) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Long oldValue, Long newValue) {
            if (!SubstrateUtil.HOSTED) {
                XOptions.getXms().setValue(newValue);
            }
        }
    };

    @Option(help = "The maximum heap size at run-time, in bytes.", type = OptionType.User)//
    public static final RuntimeOptionKey<Long> MaxHeapSize = new RuntimeOptionKey<Long>(0L) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Long oldValue, Long newValue) {
            if (!SubstrateUtil.HOSTED) {
                XOptions.getXmx().setValue(newValue);
            }
        }
    };

    @Option(help = "The maximum size of the young generation at run-time, in bytes", type = OptionType.User)//
    public static final RuntimeOptionKey<Long> MaxNewSize = new RuntimeOptionKey<Long>(0L) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Long oldValue, Long newValue) {
            if (!SubstrateUtil.HOSTED) {
                XOptions.getXmn().setValue(newValue);
            }
        }
    };

    @Option(help = "The size of each thread stack at run-time, in bytes.", type = OptionType.User)//
    public static final RuntimeOptionKey<Long> StackSize = new RuntimeOptionKey<Long>(0L) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Long oldValue, Long newValue) {
            if (!SubstrateUtil.HOSTED) {
                XOptions.getXss().setValue(newValue);
            }
        }
    };

    /* Same option name and specification as the Java HotSpot VM. */
    @Option(help = "Maximum total size of NIO direct-buffer allocations")//
    public static final RuntimeOptionKey<Long> MaxDirectMemorySize = new RuntimeOptionKey<>(0L);

    @Option(help = "Verify naming conventions during image construction.")//
    public static final HostedOptionKey<Boolean> VerifyNamingConventions = new HostedOptionKey<>(false);

    @Option(help = "Enable support for threads and and thread-local variables (disable for single-threaded implementation)")//
    public static final HostedOptionKey<Boolean> MultiThreaded = new HostedOptionKey<>(true);

    @Option(help = "Use only a writable native image heap (requires ld.gold linker)")//
    public static final HostedOptionKey<Boolean> ForceNoROSectionRelocations = new HostedOptionKey<>(false);

    @Option(help = "Support multiple isolates.") //
    public static final HostedOptionKey<Boolean> SpawnIsolates = new HostedOptionKey<Boolean>(null) {
        @Override
        public Boolean getValueOrDefault(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
            if (!values.containsKey(this)) {
                /*
                 * With the LLVM backend, isolate support has a significant performance cost, so we
                 * disable it unless it is explicitly enabled.
                 */
                return !useLLVMBackend();
            }
            return (Boolean) values.get(this);
        }

        @Override
        public Boolean getValue(OptionValues values) {
            assert checkDescriptorExists();
            return getValueOrDefault(values.getMap());
        }
    };

    @Option(help = "Trace VMOperation execution.")//
    public static final HostedOptionKey<Boolean> TraceVMOperations = new HostedOptionKey<>(false);

    @Option(help = "Instrument code to trace and report class initialization.")//
    public static final HostedOptionKey<Boolean> TraceClassInitialization = new HostedOptionKey<>(false);

    @Option(help = "Prefix that is added to the names of entry point methods.")//
    public static final HostedOptionKey<String> EntryPointNamePrefix = new HostedOptionKey<>("");

    @Option(help = "Prefix that is added to the names of API functions.")//
    public static final HostedOptionKey<String> APIFunctionPrefix = new HostedOptionKey<>("graal_");

    @APIOption(name = "enable-http", fixedValue = "http", customHelp = "enable http support in the generated image")//
    @APIOption(name = "enable-https", fixedValue = "https", customHelp = "enable https support in the generated image")//
    @APIOption(name = "enable-url-protocols")//
    @Option(help = "List of comma separated URL protocols to enable.")//
    public static final HostedOptionKey<String[]> EnableURLProtocols = new HostedOptionKey<String[]>(null) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, String[] oldValue, String[] newValue) {
            for (String protocol : OptionUtils.flatten(",", newValue)) {
                if (protocol.equals(JavaNetSubstitutions.HTTPS_PROTOCOL)) {
                    EnableAllSecurityServices.update(values, true);
                }
            }
        }
    };

    @APIOption(name = "enable-all-security-services")//
    @Option(help = "Add all security service classes to the generated image.")//
    public static final HostedOptionKey<Boolean> EnableAllSecurityServices = new HostedOptionKey<Boolean>(false) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            if (newValue) {
                /*
                 * Some providers like SunEC and SunSASL are implemented in native libs. These
                 * providers are added to the image when EnableAllSecurityServices is set. If they
                 * are actually used at runtime the user must provide and load the native libs.
                 */
                JNI.update(values, true);
            }
        }
    };

    @Option(help = "Enable Java Native Interface (JNI) support.")//
    public static final HostedOptionKey<Boolean> JNI = new HostedOptionKey<>(true);

    @Option(help = "Report information about known JNI elements when lookup fails", type = OptionType.User)//
    public static final HostedOptionKey<Boolean> JNIVerboseLookupErrors = new HostedOptionKey<>(false);

    @Option(help = "Export Invocation API symbols.", type = OptionType.User)//
    public static final HostedOptionKey<Boolean> JNIExportSymbols = new HostedOptionKey<>(true);

    /*
     * Object and array allocation options.
     */
    @Option(help = "Number of cache lines to load after the array allocation using prefetch instructions generated in JIT compiled code.")//
    public static final HostedOptionKey<Integer> AllocatePrefetchLines = new HostedOptionKey<>(3);

    @Option(help = "Number of cache lines to load after the object address using prefetch instructions generated in JIT compiled code.")//
    public static final HostedOptionKey<Integer> AllocateInstancePrefetchLines = new HostedOptionKey<>(1);

    @Option(help = "Generated code style for prefetch instructions: for 0 or less no prefetch instructions are generated and for 1 or more prefetch instructions are introduced after each allocation.")//
    public static final HostedOptionKey<Integer> AllocatePrefetchStyle = new HostedOptionKey<>(1);

    @Option(help = "Sets the prefetch instruction to prefetch ahead of the allocation pointer. Possible values are from 0 to 3. The actual instructions behind the values depend on the platform.")//
    public static final HostedOptionKey<Integer> AllocatePrefetchInstr = new HostedOptionKey<>(0);

    /*
     * Isolate tear down options.
     */

    @Option(help = "The number of nanoseconds before and between which tearing down an isolate gives a warning message.  0 implies no warning.")//
    public static final RuntimeOptionKey<Long> TearDownWarningNanos = new RuntimeOptionKey<>(0L);

    @Option(help = "The number of nanoseconds before tearing down an isolate gives a failure message.  0 implies no message.")//
    public static final RuntimeOptionKey<Long> TearDownFailureNanos = new RuntimeOptionKey<>(0L);

    public static final long getTearDownWarningNanos() {
        return TearDownWarningNanos.getValue().longValue();
    }

    public static final long getTearDownFailureNanos() {
        return TearDownFailureNanos.getValue().longValue();
    }

    /*
     * The default value is derived by taking the common value from HotSpot configs.
     */
    @Option(help = "Sets the size (in bytes) of the prefetch distance for object allocation. " +
                    "Memory about to be written with the value of new objects is prefetched up to this distance starting from the address of the last allocated object. " +
                    "Each Java thread has its own allocation point.")//
    public static final HostedOptionKey<Integer> AllocatePrefetchDistance = new HostedOptionKey<>(256);

    @Option(help = "Sets the step size (in bytes) for sequential prefetch instructions.")//
    public static final HostedOptionKey<Integer> AllocatePrefetchStepSize = new HostedOptionKey<>(16);

    @Option(help = "Define the maximum number of stores for which the loop that zeroes out objects is unrolled.")//
    public static final HostedOptionKey<Integer> MaxUnrolledObjectZeroingStores = new HostedOptionKey<>(8);

    @Option(help = "Provide method names for stack traces.")//
    public static final HostedOptionKey<Boolean> StackTrace = new HostedOptionKey<>(true);

    @Option(help = "Parse and consume standard options and system properties from the command line arguments when the VM is created.")//
    public static final HostedOptionKey<Boolean> ParseRuntimeOptions = new HostedOptionKey<>(true);

    @Option(help = "Only use Java assert statements for classes that are matching the comma-separated list of package prefixes.")//
    public static final HostedOptionKey<String[]> RuntimeAssertionsFilter = new HostedOptionKey<>(null);

    @Option(help = "Perform method inlining in the AOT compiled native image")//
    public static final HostedOptionKey<Boolean> AOTInline = new HostedOptionKey<>(true);

    @Option(help = "Perform trivial method inlining in the AOT compiled native image")//
    public static final HostedOptionKey<Boolean> AOTTrivialInline = new HostedOptionKey<>(true);

    @Option(help = "file:doc-files/NeverInlineHelp.txt", type = OptionType.Debug)//
    public static final HostedOptionKey<String[]> NeverInline = new HostedOptionKey<>(null);

    @Option(help = "Maximum number of nodes in a method so that it is considered trivial.")//
    public static final HostedOptionKey<Integer> MaxNodesInTrivialMethod = new HostedOptionKey<>(20);

    @Option(help = "Maximum number of invokes in a method so that it is considered trivial (for testing only).")//
    public static final HostedOptionKey<Integer> MaxInvokesInTrivialMethod = new HostedOptionKey<>(1);

    @Option(help = "Maximum number of nodes in a method so that it is considered trivial, if it does not have any invokes.")//
    public static final HostedOptionKey<Integer> MaxNodesInTrivialLeafMethod = new HostedOptionKey<>(40);

    @Option(help = "Saves stack base pointer on the stack on method entry.")//
    public static final HostedOptionKey<Boolean> PreserveFramePointer = new HostedOptionKey<>(false);

    @Option(help = "Use callee saved registers to reduce spilling for low-frequency calls to stubs (if callee saved registers are supported by the architecture)")//
    public static final HostedOptionKey<Boolean> UseCalleeSavedRegisters = new HostedOptionKey<>(true);

    @Option(help = "Report error if <typename>[:<UsageKind>{,<UsageKind>}] is discovered during analysis (valid values for UsageKind: InHeap, Allocated, InTypeCheck).", type = OptionType.Debug)//
    public static final HostedOptionKey<String[]> ReportAnalysisForbiddenType = new HostedOptionKey<>(new String[0]);

    @Option(help = "Backend used by the compiler", type = OptionType.User)//
    public static final HostedOptionKey<String> CompilerBackend = new HostedOptionKey<String>("lir") {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, String oldValue, String newValue) {
            if ("llvm".equals(newValue)) {
                if (JavaVersionUtil.JAVA_SPEC > 8) {
                    EmitStringEncodingSubstitutions.update(values, false);
                }
                /*
                 * The code information is filled before linking, which means that stripping dead
                 * functions makes it incoherent with the executable.
                 */
                RemoveUnusedSymbols.update(values, false);
                /*
                 * The LLVM backend doesn't support speculative execution attack mitigation
                 */
                SpectrePHTBarriers.update(values, None);
            }
        }
    };

    @Fold
    public static boolean useLLVMBackend() {
        return "llvm".equals(CompilerBackend.getValue());
    }

    @Option(help = "Emit substitutions for UTF16 and latin1 compression", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> EmitStringEncodingSubstitutions = new HostedOptionKey<>(true);

    @Option(help = "Determines if VM operations should be executed in a dedicated thread.", type = OptionType.Expert)//
    public static final HostedOptionKey<Boolean> UseDedicatedVMOperationThread = new HostedOptionKey<>(false);

    @Platforms(Platform.HOSTED_ONLY.class)
    public static Predicate<String> makeFilter(String[] definedFilters) {
        if (definedFilters != null) {
            List<String> wildCardList = OptionUtils.flatten(",", definedFilters);
            return javaName -> {
                for (String wildCard : wildCardList) {
                    if (javaName.startsWith(wildCard)) {
                        return true;
                    }
                }
                return false;
            };
        }
        return javaName -> true;
    }

    @Option(help = "Use linker option to prevent unreferenced symbols in image.")//
    public static final HostedOptionKey<Boolean> RemoveUnusedSymbols = new HostedOptionKey<>(false);
    @Option(help = "Use linker option to remove all local symbols from image.")//
    public static final HostedOptionKey<Boolean> DeleteLocalSymbols = new HostedOptionKey<>(true);

    @Option(help = "Fold SecurityManager getter.", stability = OptionStability.EXPERIMENTAL, type = OptionType.Expert) //
    public static final HostedOptionKey<Boolean> FoldSecurityManagerGetter = new HostedOptionKey<>(true);

    @APIOption(name = "native-compiler-path")//
    @Option(help = "Provide custom path to C compiler used for query code compilation and linking.", type = OptionType.User)//
    public static final HostedOptionKey<String> CCompilerPath = new HostedOptionKey<>(null);
    @APIOption(name = "native-compiler-options")//
    @Option(help = "Provide custom C compiler option used for query code compilation.", type = OptionType.User)//
    public static final HostedOptionKey<String[]> CCompilerOption = new HostedOptionKey<>(new String[0]);

    @APIOption(name = "native-image-info")//
    @Option(help = "Show native-toolchain information and image-build settings", type = User)//
    public static final HostedOptionKey<Boolean> DumpTargetInfo = new HostedOptionKey<>(false);

    @Option(help = "Check if native-toolchain is known to work with native-image", type = Expert)//
    public static final HostedOptionKey<Boolean> CheckToolchain = new HostedOptionKey<>(true);

    @APIOption(name = "install-exit-handlers")//
    @Option(help = "Provide java.lang.Terminator exit handlers for executable images", type = User)//
    public static final HostedOptionKey<Boolean> InstallExitHandlers = new HostedOptionKey<>(false);

    @Option(help = "When set to true, the image generator verifies that the image heap does not contain a home directory as a substring", type = User)//
    public static final HostedOptionKey<Boolean> DetectUserDirectoriesInImageHeap = new HostedOptionKey<>(false);

    @Option(help = "The interval in minutes between watchdog checks (0 disables the watchdog)", type = OptionType.Expert)//
    public static final HostedOptionKey<Integer> DeadlockWatchdogInterval = new HostedOptionKey<>(10);
    @Option(help = "Exit the image builder VM after printing call stacks", type = OptionType.Expert)//
    public static final HostedOptionKey<Boolean> DeadlockWatchdogExitOnTimeout = new HostedOptionKey<>(true);

    /**
     * The alignment for AOT and JIT compiled methods. The value is constant folded during image
     * generation, i.e., cannot be changed at run time, so that it can be used in uninterruptible
     * code.
     */
    @Fold
    public static int codeAlignment() {
        return GraalOptions.LoopHeaderAlignment.getValue(HostedOptionValues.singleton());
    }

    @Option(help = "Populate reference queues in a separate thread rather than after a garbage collection.", type = OptionType.Expert) //
    public static final HostedOptionKey<Boolean> UseReferenceHandlerThread = new HostedOptionKey<>(false);

    @APIOption(name = "-g", fixedValue = "2", customHelp = "generate debugging information")//
    @Option(help = "Insert debug info into the generated native image or library")//
    public static final HostedOptionKey<Integer> GenerateDebugInfo = new HostedOptionKey<Integer>(0) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Integer oldValue, Integer newValue) {
            debugInfoValueUpdateHandler.onValueUpdate(values, oldValue, newValue);
        }
    };

    private static void defaultDebugInfoValueUpdateHandler(EconomicMap<OptionKey<?>, Object> values, @SuppressWarnings("unused") Integer oldValue, Integer newValue) {
        // force update of TrackNodeSourcePosition
        if (newValue > 0 && !Boolean.TRUE.equals(values.get(TrackNodeSourcePosition))) {
            TrackNodeSourcePosition.update(values, true);
        }
    }

    @Option(help = "Search path for source files for Application or GraalVM classes (list of comma-separated directories or jar files)")//
    public static final HostedOptionKey<String[]> DebugInfoSourceSearchPath = new HostedOptionKey<String[]>(null) {
    };
    @Option(help = "Directory under which to create source file cache for Application or GraalVM classes")//
    public static final HostedOptionKey<String> DebugInfoSourceCacheRoot = new HostedOptionKey<>("sources");

    public static Path getDebugInfoSourceCacheRoot() {
        try {
            Path sourceRoot = Paths.get(DebugInfoSourceCacheRoot.getValue());
            return sourceRoot;
        } catch (InvalidPathException ipe) {
            throw UserError.abort("Invalid path provided for option DebugInfoSourceCacheRoot " + DebugInfoSourceCacheRoot.getValue());
        }
    }

    /** Command line option to disable image build server. */
    public static final String NO_SERVER = "--no-server";

}
