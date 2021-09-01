/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.options.OptionType.Debug;
import static org.graalvm.compiler.options.OptionType.Expert;
import static org.graalvm.compiler.options.OptionType.User;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

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

import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.APIOptionGroup;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.UserError;
import org.graalvm.nativeimage.ImageSingletons;

public class SubstrateOptions {

    @Option(help = "When true, compiler graphs are parsed only once before static analysis. When false, compiler graphs are parsed for static analysis and again for AOT compilation.")//
    public static final HostedOptionKey<Boolean> ParseOnce = new HostedOptionKey<>(true);

    public static boolean parseOnce() {
        /*
         * Parsing all graphs before static analysis is work-in-progress, and not yet working for
         * graphs parsed for deoptimization entry points and JIT compilation.
         */
        return ParseOnce.getValue() && !DeoptimizationSupport.enabled();
    }

    @Option(help = "Module containing the class that contains the main entry point. Optional if --shared is used.", type = OptionType.User)//
    public static final HostedOptionKey<String> Module = new HostedOptionKey<>("");

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

    @APIOption(name = "target")//
    @Option(help = "Selects native-image compilation target (in <OS>-<architecture> format). Defaults to host's OS-architecture pair.")//
    public static final HostedOptionKey<String> TargetPlatform = new HostedOptionKey<>("");

    @Option(help = "Builds a statically linked executable with libc dynamically linked", type = Expert, stability = OptionStability.EXPERIMENTAL)//
    public static final HostedOptionKey<Boolean> StaticExecutableWithDynamicLibC = new HostedOptionKey<Boolean>(false) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            StaticExecutable.update(values, true);
            super.onValueUpdate(values, oldValue, newValue);
        }
    };

    @Option(help = "Build with Loom JDK") //
    public static final HostedOptionKey<Boolean> UseLoom = new HostedOptionKey<>(false);

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
    public static final String IMAGE_MODULEPATH_PREFIX = "-imagemp";
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
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> CLibraryPath = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());

    @Option(help = "Path passed to the linker as the -rpath (list of comma-separated directories)")//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> LinkerRPath = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());

    @Option(help = "Directory of the image file to be generated", type = OptionType.User)//
    public static final HostedOptionKey<String> Path = new HostedOptionKey<>(null);

    public static final class GCGroup implements APIOptionGroup {
        @Override
        public String name() {
            return "gc";
        }

        @Override
        public String helpText() {
            return "Select native-image garbage collector implementation.";
        }
    }

    @APIOption(name = "serial", group = GCGroup.class, customHelp = "Serial garbage collector")//
    @Option(help = "Use a serial GC")//
    public static final HostedOptionKey<Boolean> UseSerialGC = new HostedOptionKey<>(true);

    @APIOption(name = "epsilon", group = GCGroup.class, customHelp = "Epsilon garbage collector")//
    @Option(help = "Use a no-op GC")//
    public static final HostedOptionKey<Boolean> UseEpsilonGC = new HostedOptionKey<>(false);

    @Option(help = "The size of each thread stack at run-time, in bytes.", type = OptionType.User)//
    public static final RuntimeOptionKey<Long> StackSize = new RuntimeOptionKey<>(0L);

    @Option(help = "The size of each internal thread stack, in bytes.", type = OptionType.Expert)//
    public static final HostedOptionKey<Long> InternalThreadStackSize = new HostedOptionKey<>(2L * 1024 * 1024);

    @Option(help = "The maximum number of lines in the stack trace for Java exceptions (0 means all)", type = OptionType.User)//
    public static final RuntimeOptionKey<Integer> MaxJavaStackTraceDepth = new RuntimeOptionKey<>(1024);

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

    @APIOption(name = "trace-class-initialization")//
    @Option(help = "Comma-separated list of fully-qualified class names that class initialization is traced for.")//
    public static final HostedOptionKey<String> TraceClassInitialization = new HostedOptionKey<>("");

    @APIOption(name = "trace-object-instantiation")//
    @Option(help = "Comma-separated list of fully-qualified class names that object instantiation is traced for.")//
    public static final HostedOptionKey<String> TraceObjectInstantiation = new HostedOptionKey<>("");

    @Option(help = "Trace all native tool invocations as part of image building", type = User)//
    public static final HostedOptionKey<Boolean> TraceNativeToolUsage = new HostedOptionKey<>(false);

    @Option(help = "Prefix that is added to the names of entry point methods.")//
    public static final HostedOptionKey<String> EntryPointNamePrefix = new HostedOptionKey<>("");

    @Option(help = "Prefix that is added to the names of API functions.")//
    public static final HostedOptionKey<String> APIFunctionPrefix = new HostedOptionKey<>("graal_");

    @APIOption(name = "enable-http", fixedValue = "http", customHelp = "enable http support in the generated image")//
    @APIOption(name = "enable-https", fixedValue = "https", customHelp = "enable https support in the generated image")//
    @APIOption(name = "enable-url-protocols")//
    @Option(help = "List of comma separated URL protocols to enable.")//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> EnableURLProtocols = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());

    @SuppressWarnings("unused") //
    @APIOption(name = "enable-all-security-services")//
    @Option(help = "Add all security service classes to the generated image.", deprecated = true)//
    public static final HostedOptionKey<Boolean> EnableAllSecurityServices = new HostedOptionKey<>(false);

    @Option(help = "Enable Java Native Interface (JNI) support.")//
    public static final HostedOptionKey<Boolean> JNI = new HostedOptionKey<>(true);

    @Option(help = "Report information about known JNI elements when lookup fails", type = OptionType.User)//
    public static final HostedOptionKey<Boolean> JNIVerboseLookupErrors = new HostedOptionKey<>(false);

    @Option(help = "Export Invocation API symbols.", type = OptionType.User)//
    public static final HostedOptionKey<Boolean> JNIExportSymbols = new HostedOptionKey<>(true);

    @Option(help = "Alignment of AOT and JIT compiled code in bytes.")//
    public static final HostedOptionKey<Integer> CodeAlignment = new HostedOptionKey<>(16);

    /*
     * Object and array allocation options.
     */
    @Option(help = "Number of cache lines to load after the array allocation using prefetch instructions.")//
    public static final HostedOptionKey<Integer> AllocatePrefetchLines = new HostedOptionKey<>(3);

    @Option(help = "Number of cache lines to load after the object address using prefetch instructions.")//
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

    @Option(help = "Enable wildcard expansion in command line arguments on Windows.")//
    public static final HostedOptionKey<Boolean> EnableWildcardExpansion = new HostedOptionKey<>(true);

    @Option(help = "Perform method inlining in the AOT compiled native image")//
    public static final HostedOptionKey<Boolean> AOTInline = new HostedOptionKey<>(true);

    @Option(help = "Perform trivial method inlining in the AOT compiled native image")//
    public static final HostedOptionKey<Boolean> AOTTrivialInline = new HostedOptionKey<>(true);

    @Option(help = "file:doc-files/NeverInlineHelp.txt", type = OptionType.Debug)//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> NeverInline = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());

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

    @Option(help = "Report error if <typename>[:<UsageKind>{,<UsageKind>}] is discovered during analysis (valid values for UsageKind: InHeap, Allocated, Reachable).", type = OptionType.Debug)//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> ReportAnalysisForbiddenType = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());

    @Option(help = "Backend used by the compiler", type = OptionType.User)//
    public static final HostedOptionKey<String> CompilerBackend = new HostedOptionKey<String>("lir") {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, String oldValue, String newValue) {
            if ("llvm".equals(newValue)) {
                if (JavaVersionUtil.JAVA_SPEC >= 9) {
                    /* See GR-14405, https://github.com/oracle/graal/issues/1056 */
                    GraalOptions.EmitStringSubstitutions.update(values, false);
                }
                /*
                 * The code information is filled before linking, which means that stripping dead
                 * functions makes it incoherent with the executable.
                 */
                RemoveUnusedSymbols.update(values, false);
                InternalSymbolsAreGlobal.update(values, true);
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

    @Option(help = "Determines if VM operations should be executed in a dedicated thread.", type = OptionType.Expert)//
    public static final HostedOptionKey<Boolean> UseDedicatedVMOperationThread = new HostedOptionKey<>(false);

    /*
     * RemoveUnusedSymbols is not enabled on Darwin by default, because the linker sometimes
     * segfaults when the -dead_strip option is used.
     */
    @Option(help = "Use linker option to prevent unreferenced symbols in image.")//
    public static final HostedOptionKey<Boolean> RemoveUnusedSymbols = new HostedOptionKey<>(OS.getCurrent() != OS.DARWIN);
    @Option(help = "Use linker option to remove all local symbols from image.")//
    public static final HostedOptionKey<Boolean> DeleteLocalSymbols = new HostedOptionKey<>(true);
    @Option(help = "Compatibility option to make symbols used for the image heap global. " +
                    "Using global symbols is problematic for shared libraries because the loader implicitly changes the value when the symbol is already defined in the executable loading the library. " +
                    "Setting this option to true preserves the broken behavior of old Native Image versions.")//
    public static final HostedOptionKey<Boolean> InternalSymbolsAreGlobal = new HostedOptionKey<>(false);

    @Option(help = "Common prefix used by method symbols in image.")//
    public static final HostedOptionKey<String> ImageSymbolsPrefix = new HostedOptionKey<>("");

    @Option(help = "Fold SecurityManager getter.", stability = OptionStability.EXPERIMENTAL, type = OptionType.Expert) //
    public static final HostedOptionKey<Boolean> FoldSecurityManagerGetter = new HostedOptionKey<>(true);

    @APIOption(name = "native-compiler-path")//
    @Option(help = "Provide custom path to C compiler used for query code compilation and linking.", type = OptionType.User)//
    public static final HostedOptionKey<String> CCompilerPath = new HostedOptionKey<>(null);
    @APIOption(name = "native-compiler-options")//
    @Option(help = "Provide custom C compiler option used for query code compilation.", type = OptionType.User)//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> CCompilerOption = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());

    @Option(help = "Use strict checks when performing query code compilation.", type = OptionType.User)//
    public static final HostedOptionKey<Boolean> StrictQueryCodeCompilation = new HostedOptionKey<>(true);

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
        return CodeAlignment.getValue();
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

    public static void defaultDebugInfoValueUpdateHandler(EconomicMap<OptionKey<?>, Object> values, @SuppressWarnings("unused") Integer oldValue, Integer newValue) {
        /* Force update of TrackNodeSourcePosition */
        TrackNodeSourcePosition.update(values, newValue > 0);
        if (OS.WINDOWS.isCurrent()) {
            /* Keep symbols on Windows. The symbol table is part of the pdb-file. */
            DeleteLocalSymbols.update(values, newValue == 0);
        }
    }

    @Option(help = "Search path for source files for Application or GraalVM classes (list of comma-separated directories or jar files)")//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> DebugInfoSourceSearchPath = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());

    @Option(help = "Directory under which to create source file cache for Application or GraalVM classes")//
    public static final HostedOptionKey<String> DebugInfoSourceCacheRoot = new HostedOptionKey<>("sources");

    public static Path getDebugInfoSourceCacheRoot() {
        try {
            return Paths.get(Path.getValue()).resolve(DebugInfoSourceCacheRoot.getValue());
        } catch (InvalidPathException ipe) {
            throw UserError.abort("Invalid path provided for option DebugInfoSourceCacheRoot %s", DebugInfoSourceCacheRoot.getValue());
        }
    }

    @Option(help = "Omit generation of DebugLineInfo originating from inlined methods") //
    public static final HostedOptionKey<Boolean> OmitInlinedMethodDebugLineInfo = new HostedOptionKey<>(true);

    @Fold
    public static boolean supportCompileInIsolates() {
        UserError.guarantee(!ConcealedOptions.SupportCompileInIsolates.getValue() || SpawnIsolates.getValue(),
                        "Option %s must be enabled to support isolated compilations through option %s",
                        SpawnIsolates.getName(),
                        ConcealedOptions.SupportCompileInIsolates.getName());
        return ConcealedOptions.SupportCompileInIsolates.getValue();
    }

    public static boolean shouldCompileInIsolates() {
        /*
         * If SupportCompileInIsolates is unset, CompileInIsolates becomes unreachable because this
         * expression is folded, and cannot be used at runtime.
         */
        return supportCompileInIsolates() && ConcealedOptions.CompileInIsolates.getValue();
    }

    @Option(help = "Size of the reserved address space of each compilation isolate (0: default for new isolates).") //
    public static final RuntimeOptionKey<Long> CompilationIsolateAddressSpaceSize = new RuntimeOptionKey<>(0L);

    @Fold
    public static boolean useRememberedSet() {
        return !SubstrateOptions.UseEpsilonGC.getValue() && ConcealedOptions.UseRememberedSet.getValue();
    }

    /** Query these options only through an appropriate method. */
    public static class ConcealedOptions {

        @Option(help = "Support runtime compilation in separate isolates (enable at runtime with option CompileInIsolates).") //
        public static final HostedOptionKey<Boolean> SupportCompileInIsolates = new HostedOptionKey<Boolean>(null) {
            @Override
            public Boolean getValueOrDefault(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
                if (!values.containsKey(this)) {
                    return SpawnIsolates.getValueOrDefault(values);
                }
                return super.getValueOrDefault(values);
            }

            @Override
            public Boolean getValue(OptionValues values) {
                return getValueOrDefault(values.getMap());
            }
        };

        @Option(help = "Activate runtime compilation in separate isolates (enable support during image build with option SupportCompileInIsolates).") //
        public static final RuntimeOptionKey<Boolean> CompileInIsolates = new RuntimeOptionKey<>(true);

        @Option(help = "Determines if a remembered sets is used, which is necessary for collecting the young and old generation independently.", type = OptionType.Expert) //
        public static final HostedOptionKey<Boolean> UseRememberedSet = new HostedOptionKey<>(true);
    }

    @Option(help = "Overwrites the available number of processors provided by the OS. Any value <= 0 means using the processor count from the OS.")//
    public static final RuntimeOptionKey<Integer> ActiveProcessorCount = new RuntimeOptionKey<>(-1);

    @Option(help = "For internal purposes only. Disables type id result verification even when running with assertions enabled.", stability = OptionStability.EXPERIMENTAL, type = Debug)//
    public static final HostedOptionKey<Boolean> DisableTypeIdResultVerification = new HostedOptionKey<>(true);

    public static boolean areMethodHandlesSupported() {
        return JavaVersionUtil.JAVA_SPEC >= 11;
    }

    @Option(help = "Enables the signal API (sun.misc.Signal or jdk.internal.misc.Signal). Defaults to false for shared library and true for executables", stability = OptionStability.EXPERIMENTAL, type = Expert)//
    public static final HostedOptionKey<Boolean> EnableSignalAPI = new HostedOptionKey<Boolean>(null) {
        @Override
        public Boolean getValueOrDefault(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
            if (values.containsKey(this)) {
                return (Boolean) values.get(this);
            }
            return !SharedLibrary.getValueOrDefault(values);
        }

        @Override
        public Boolean getValue(OptionValues values) {
            return getValueOrDefault(values.getMap());
        }
    };

    @Option(help = "Enable Java Flight Recorder.")//
    public static final RuntimeOptionKey<Boolean> FlightRecorder = new RuntimeOptionKey<>(false);

    @Option(help = "Start flight recording with options.")//
    public static final RuntimeOptionKey<String> StartFlightRecording = new RuntimeOptionKey<>("");

    @Option(help = "file:doc-files/FlightRecorderLoggingHelp.txt")//
    public static final RuntimeOptionKey<String> FlightRecorderLogging = new RuntimeOptionKey<>("all=warning");

    public static String reportsPath() {
        return Paths.get(Paths.get(Path.getValue()).toString(), ImageSingletons.lookup(ReportingSupport.class).reportsPath).toAbsolutePath().toString();
    }

    public static class ReportingSupport {
        String reportsPath;

        public ReportingSupport(String reportingPath) {
            this.reportsPath = reportingPath;
        }
    }
}
