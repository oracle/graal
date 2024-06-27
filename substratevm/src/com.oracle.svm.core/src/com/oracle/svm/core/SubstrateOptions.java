/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.option.RuntimeOptionKey.RuntimeOptionKeyFlag.Immutable;
import static com.oracle.svm.core.option.RuntimeOptionKey.RuntimeOptionKeyFlag.RelevantForCompilationIsolates;
import static org.graalvm.compiler.core.common.SpectrePHTMitigations.None;
import static org.graalvm.compiler.core.common.SpectrePHTMitigations.Options.SpectrePHTBarriers;
import static org.graalvm.compiler.options.OptionType.Expert;
import static org.graalvm.compiler.options.OptionType.User;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
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
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.c.libc.MuslLibC;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.heap.ReferenceHandler;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.APIOptionGroup;
import com.oracle.svm.core.option.BundleMember;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.thread.VMOperationControl;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.misc.Unsafe;

public class SubstrateOptions {

    @Option(help = "When true, compiler graphs are parsed only once before static analysis. When false, compiler graphs are parsed for static analysis and again for AOT compilation.")//
    public static final HostedOptionKey<Boolean> ParseOnce = new HostedOptionKey<>(true);
    @Option(help = "When true, each compiler graph version (DeoptTarget, AOT, JIT) needed for runtime compilation will be separately analyzed during static analysis." +
                    "When false, only one version of the compiler graph (AOT) will be used in static analysis, and then three new versions will be parsed for compilation.")//
    public static final HostedOptionKey<Boolean> ParseOnceJIT = new HostedOptionKey<>(true);
    @Option(help = "Preserve the local variable information for every Java source line to allow line-by-line stepping in the debugger. Allow the lookup of Java-level method information, e.g., in stack traces.")//
    public static final HostedOptionKey<Boolean> SourceLevelDebug = new HostedOptionKey<>(false);
    @Option(help = "Constrain debug info generation to the comma-separated list of package prefixes given to this option.")//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> SourceLevelDebugFilter = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

    @Option(help = "Image Build ID is a 128-bit UUID string generated randomly, once per bundle or digest of input args when bundles are not used.")//
    public static final HostedOptionKey<String> ImageBuildID = new HostedOptionKey<>("");

    public static boolean parseOnce() {
        /*
         * Parsing all graphs before static analysis is work-in-progress and for JIT compilation is
         * only enabled when ParseOnceJIT is set.
         */
        return ParseOnce.getValue() && (ParseOnceJIT.getValue() || !DeoptimizationSupport.enabled());
    }

    @Option(help = "Module containing the class that contains the main entry point. Optional if --shared is used.", type = OptionType.User)//
    public static final HostedOptionKey<String> Module = new HostedOptionKey<>("");

    @Option(help = "Class containing the default entry point method. Optional if --shared is used.", type = OptionType.User, stability = OptionStability.STABLE)//
    public static final HostedOptionKey<String> Class = new HostedOptionKey<>("");

    @Option(help = "Name of the main entry point method. Optional if --shared is used.")//
    public static final HostedOptionKey<String> Method = new HostedOptionKey<>("main");

    @APIOption(name = "-o", valueSeparator = APIOption.WHITESPACE_SEPARATOR)//
    @Option(help = "Name of the output file to be generated", type = OptionType.User)//
    public static final HostedOptionKey<String> Name = new HostedOptionKey<>("");

    @APIOption(name = "shared")//
    @Option(help = "Build shared library")//
    public static final HostedOptionKey<Boolean> SharedLibrary = new HostedOptionKey<>(false);

    @APIOption(name = "static")//
    @Option(help = "Build statically linked executable (requires static libc and zlib)")//
    public static final HostedOptionKey<Boolean> StaticExecutable = new HostedOptionKey<>(false, key -> {
        if (!LibCBase.targetLibCIs(MuslLibC.class)) {
            throw UserError.abort(
                            "Building static executable images is only supported with musl libc. Remove the '--static' option or add the '--libc=musl' option.");
        }
    });

    @APIOption(name = "libc")//
    @Option(help = "Selects the libc implementation to use. Available implementations: glibc, musl, bionic")//
    public static final HostedOptionKey<String> UseLibC = new HostedOptionKey<>(null) {
        @Override
        public String getValueOrDefault(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
            if (!values.containsKey(this)) {
                return Platform.includedIn(Platform.ANDROID.class)
                                ? "bionic"
                                : System.getProperty("substratevm.HostLibC", "glibc");
            }
            return (String) values.get(this);
        }

        @Override
        public String getValue(OptionValues values) {
            assert checkDescriptorExists();
            return getValueOrDefault(values.getMap());
        }
    };

    @APIOption(name = "target")//
    @Option(help = "Selects native-image compilation target (in <OS>-<architecture> format). Defaults to host's OS-architecture pair.")//
    public static final HostedOptionKey<String> TargetPlatform = new HostedOptionKey<>("") {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, String oldValue, String newValue) {
            String updatedNewValue;
            // both darwin and macos refer to Platform.MacOS
            if (newValue.equals("macos")) {
                updatedNewValue = "darwin";
            } else {
                updatedNewValue = newValue;
            }
            super.onValueUpdate(values, oldValue, updatedNewValue);

        }
    };

    @Option(help = "Builds a statically linked executable with libc dynamically linked", type = Expert, stability = OptionStability.EXPERIMENTAL)//
    public static final HostedOptionKey<Boolean> StaticExecutableWithDynamicLibC = new HostedOptionKey<>(false);

    @Option(help = "Builds image with libstdc++ statically linked into the image (if needed)", type = Expert, stability = OptionStability.EXPERIMENTAL)//
    public static final HostedOptionKey<Boolean> StaticLibStdCpp = new HostedOptionKey<>(false);

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
    public static final String KEEP_ALIVE_PREFIX = "-keepalive";
    private static ValueUpdateHandler<OptimizationLevel> optimizeValueUpdateHandler;

    @Fold
    public static boolean getSourceLevelDebug() {
        return SourceLevelDebug.getValue();
    }

    @Fold
    public static Predicate<String> getSourceLevelDebugFilter() {
        return makeFilter(SourceLevelDebugFilter.getValue().values());
    }

    @Fold
    public static UUID getImageBuildID() {
        return UUID.fromString(SubstrateOptions.ImageBuildID.getValue());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static Predicate<String> makeFilter(List<String> definedFilters) {
        if (definedFilters.isEmpty()) {
            return javaName -> true;
        }
        return javaName -> {
            for (String wildCard : definedFilters) {
                if (javaName.startsWith(wildCard)) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * The currently supported optimization levels. See the option description of {@link #Optimize}
     * for a description of the levels.
     */
    public enum OptimizationLevel {
        O0("No optimizations", "0"),
        O1("Basic optimizations", "1"),
        O2("Advanced optimizations", "2"),
        O3("All optimizations for best performance", "3"),
        BUILD_TIME("Optimize for fastest build time", "b");

        private final String description;
        private final String optionSwitch;

        OptimizationLevel(String description, String optionSwitch) {
            this.description = description;
            this.optionSwitch = optionSwitch;
        }

        public String getDescription() {
            return description;
        }

        public String getOptionSwitch() {
            return optionSwitch;
        }

        /**
         * Determine if this level is one of the given ones.
         */
        public boolean isOneOf(OptimizationLevel... levels) {
            if (levels != null) {
                for (OptimizationLevel level : levels) {
                    if (level.equals(this)) {
                        return true;
                    }
                }
            }
            return false;
        }

    }

    @APIOption(name = "-O", valueSeparator = APIOption.NO_SEPARATOR)//
    @Option(help = "Control code optimizations: b - optimize for fastest build time, " +
                    "0 - no optimizations, 1 - basic optimizations, 2 - advanced optimizations, 3 - all optimizations for best performance.", type = OptionType.User)//
    public static final HostedOptionKey<String> Optimize = new HostedOptionKey<>("2") {

        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, String oldValue, String newValue) {
            OptimizationLevel newLevel = parseOptimizationLevel(newValue);

            // `-g -O0` is recommended for a better debugging experience
            GraalOptions.TrackNodeSourcePosition.update(values, newLevel == OptimizationLevel.O0);
            SubstrateOptions.IncludeNodeSourcePositions.update(values, newLevel == OptimizationLevel.O0);
            SubstrateOptions.SourceLevelDebug.update(values, newLevel == OptimizationLevel.O0);
            SubstrateOptions.AOTTrivialInline.update(values, newLevel != OptimizationLevel.O0);
            if (optimizeValueUpdateHandler != null) {
                optimizeValueUpdateHandler.onValueUpdate(values, newLevel);
            }

        }
    };

    /**
     * Only allows 'b' or positive numeric optimization levels, throws a user error otherwise.
     */
    private static OptimizationLevel parseOptimizationLevel(String value) {
        if (value.equals("b")) {
            return OptimizationLevel.BUILD_TIME;
        }

        int intLevel;
        try {
            intLevel = Integer.parseInt(value);
        } catch (NumberFormatException nfe) {
            intLevel = -1;
        }

        if (intLevel == 0) {
            return OptimizationLevel.O0;
        } else if (intLevel == 1) {
            return OptimizationLevel.O1;
        } else if (intLevel == 2) {
            return OptimizationLevel.O2;
        } else if (intLevel > 2) {
            // We allow all positive numbers, and treat that as our current highest supported level.
            return OptimizationLevel.O3;
        } else {
            throw UserError.abort("Invalid value '%s' provided for option Optimize (expected 'b' or numeric value >= 0)", value);
        }
    }

    @Fold
    public static OptimizationLevel optimizationLevel() {
        return parseOptimizationLevel(Optimize.getValue());
    }

    public static boolean useEconomyCompilerConfig(OptionValues options) {
        return "b".equals(Optimize.getValue(options));
    }

    @Fold
    public static boolean useEconomyCompilerConfig() {
        return useEconomyCompilerConfig(HostedOptionValues.singleton());
    }

    @Fold
    public static boolean isMaximumOptimizationLevel() {
        return optimizationLevel() == OptimizationLevel.O3;
    }

    public interface ValueUpdateHandler<T> {
        void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, T newValue);
    }

    public static void setOptimizeValueUpdateHandler(ValueUpdateHandler<OptimizationLevel> updateHandler) {
        SubstrateOptions.optimizeValueUpdateHandler = updateHandler;
    }

    @Option(help = "Track NodeSourcePositions during runtime-compilation")//
    public static final HostedOptionKey<Boolean> IncludeNodeSourcePositions = new HostedOptionKey<>(false);

    @Option(help = "Search path for C libraries passed to the linker (list of comma-separated directories)", stability = OptionStability.STABLE)//
    @BundleMember(role = BundleMember.Role.Input)//
    public static final HostedOptionKey<LocatableMultiOptionValue.Paths> CLibraryPath = new HostedOptionKey<>(LocatableMultiOptionValue.Paths.buildWithCommaDelimiter());

    @Option(help = "Path passed to the linker as the -rpath (list of comma-separated directories)")//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> LinkerRPath = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

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
    public static final HostedOptionKey<Boolean> UseSerialGC = new HostedOptionKey<>(true) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            if (newValue) {
                SubstrateOptions.UseEpsilonGC.update(values, false);
            }
        }
    };

    @APIOption(name = "epsilon", group = GCGroup.class, customHelp = "Epsilon garbage collector")//
    @Option(help = "Use a no-op GC")//
    public static final HostedOptionKey<Boolean> UseEpsilonGC = new HostedOptionKey<>(false) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            if (newValue) {
                SubstrateOptions.UseSerialGC.update(values, false);
            }
        }
    };
    @Option(help = "Physical memory size (in bytes). By default, the value is queried from the OS/container during VM startup.", type = OptionType.Expert)//
    public static final RuntimeOptionKey<Long> MaxRAM = new RuntimeOptionKey<>(0L, Immutable);

    @Option(help = "The size of each thread stack at run-time, in bytes.", type = OptionType.User)//
    public static final RuntimeOptionKey<Long> StackSize = new RuntimeOptionKey<>(0L);

    @Option(help = "The size of each internal thread stack, in bytes.", type = OptionType.Expert)//
    public static final HostedOptionKey<Long> InternalThreadStackSize = new HostedOptionKey<>(2L * 1024 * 1024);

    /**
     * Cached value of {@link ConcealedOptions#MaxJavaStackTraceDepth}. Also used as default value.
     */
    private static int maxJavaStackTraceDepth = 1024;

    /**
     * Cached accessor for {@link ConcealedOptions#MaxJavaStackTraceDepth}.
     */
    public static int maxJavaStackTraceDepth() {
        return maxJavaStackTraceDepth;
    }

    public static void updateMaxJavaStackTraceDepth(EconomicMap<OptionKey<?>, Object> runtimeValues, int newValue) {
        ConcealedOptions.MaxJavaStackTraceDepth.update(runtimeValues, newValue);
    }

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
    public static final HostedOptionKey<Boolean> SpawnIsolates = new HostedOptionKey<>(true);

    @Option(help = "At CEntryPoints check that the passed IsolateThread is valid.") //
    public static final HostedOptionKey<Boolean> CheckIsolateThreadAtEntry = new HostedOptionKey<>(false);

    @Option(help = "Trace VMOperation execution.")//
    public static final HostedOptionKey<Boolean> TraceVMOperations = new HostedOptionKey<>(false);

    @APIOption(name = "trace-class-initialization")//
    @Option(help = "Comma-separated list of fully-qualified class names that class initialization is traced for.")//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> TraceClassInitialization = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

    @APIOption(name = "trace-object-instantiation")//
    @Option(help = "Comma-separated list of fully-qualified class names that object instantiation is traced for.")//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> TraceObjectInstantiation = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

    @Option(help = "Trace all native tool invocations as part of image building", type = User)//
    public static final HostedOptionKey<Boolean> TraceNativeToolUsage = new HostedOptionKey<>(false);

    @Option(help = "Prefix that is added to the names of entry point methods.")//
    public static final HostedOptionKey<String> EntryPointNamePrefix = new HostedOptionKey<>("");

    @Option(help = "Prefix that is added to the names of API functions.", stability = OptionStability.STABLE)//
    public static final HostedOptionKey<String> APIFunctionPrefix = new HostedOptionKey<>("graal_");

    @APIOption(name = "enable-http", fixedValue = "http", customHelp = "enable http support in the generated image")//
    @APIOption(name = "enable-https", fixedValue = "https", customHelp = "enable https support in the generated image")//
    @APIOption(name = "enable-url-protocols")//
    @Option(help = "List of comma separated URL protocols to enable.")//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> EnableURLProtocols = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

    @Option(help = "List of comma separated URL protocols that must never be included.")//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> DisableURLProtocols = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

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

    @Option(help = "JNI functions will return more specific error codes.", type = OptionType.User)//
    public static final HostedOptionKey<Boolean> JNIEnhancedErrorCodes = new HostedOptionKey<>(false);

    @Option(help = "Alignment of AOT and JIT compiled code in bytes.")//
    public static final HostedOptionKey<Integer> CodeAlignment = new HostedOptionKey<>(16);

    public static final String BUILD_ARTIFACTS_FILE_NAME = "build-artifacts.json";
    @Option(help = "Create a " + BUILD_ARTIFACTS_FILE_NAME + " file in the build directory. The output conforms to the JSON schema located at: " +
                    "docs/reference-manual/native-image/assets/build-artifacts-schema-v0.9.0.json", type = OptionType.User)//
    public static final HostedOptionKey<Boolean> GenerateBuildArtifactsFile = new HostedOptionKey<>(false);

    /*
     * Build output options.
     */

    @APIOption(name = "color")//
    @Option(help = "Color build output ('always', 'never', or 'auto')", type = OptionType.User)//
    public static final HostedOptionKey<String> Color = new HostedOptionKey<>("auto");

    public static final boolean hasColorsEnabled(OptionValues values) {
        if (Color.hasBeenSet(values)) {
            String value = Color.getValue(values);
            return switch (value) {
                case "always" -> true;
                case "auto" -> {
                    /* Fail only when assertions are enabled. */
                    assert false : "'auto' value should have been resolved in the driver";
                    yield false;
                }
                case "never" -> false;
                default -> throw UserError.abort("Unsupported value '%s' for '--color' option. Only 'always', 'never', and 'auto' are accepted.", value);
            };
        }
        return false;
    }

    @APIOption(name = "silent")//
    @Option(help = "Silence build output", type = OptionType.User)//
    public static final HostedOptionKey<Boolean> BuildOutputSilent = new HostedOptionKey<>(false);

    @Option(help = "Prefix build output with '<pid>:<image name>'", type = OptionType.User)//
    public static final HostedOptionKey<Boolean> BuildOutputPrefix = new HostedOptionKey<>(false);

    @Option(help = "Color build output (enabled by default if colors are supported by terminal)", type = OptionType.User, deprecated = true, deprecationMessage = "Please use '--color' instead.")//
    public static final HostedOptionKey<Boolean> BuildOutputColorful = new HostedOptionKey<>(false);

    @Option(help = "Show links in build output (defaults to the value of BuildOutputColorful)", type = OptionType.User)//
    public static final HostedOptionKey<Boolean> BuildOutputLinks = new HostedOptionKey<>(false);

    @Option(help = "Report progress in build output (default is adaptive)", type = OptionType.User)//
    public static final HostedOptionKey<Boolean> BuildOutputProgress = new HostedOptionKey<>(false);

    @Option(help = "Show code and heap breakdowns as part of the build output", type = OptionType.User)//
    public static final HostedOptionKey<Boolean> BuildOutputBreakdowns = new HostedOptionKey<>(true);

    @Option(help = "Show recommendations as part of the build output", type = OptionType.User)//
    public static final HostedOptionKey<Boolean> BuildOutputRecommendations = new HostedOptionKey<>(true);

    @Option(help = "Print GC warnings as part of build output", type = OptionType.User)//
    public static final HostedOptionKey<Boolean> BuildOutputGCWarnings = new HostedOptionKey<>(true);

    @BundleMember(role = BundleMember.Role.Output)//
    @Option(help = "Print build output statistics as JSON to the specified file. " +
                    "The output conforms to the JSON schema located at: " +
                    "docs/reference-manual/native-image/assets/build-output-schema-v0.9.2.json", type = OptionType.User)//
    public static final HostedOptionKey<LocatableMultiOptionValue.Paths> BuildOutputJSONFile = new HostedOptionKey<>(LocatableMultiOptionValue.Paths.build());

    /*
     * Object and array allocation options.
     */
    @Option(help = "Number of cache lines to load after the array allocation using prefetch instructions.")//
    public static final HostedOptionKey<Integer> AllocatePrefetchLines = new HostedOptionKey<>(4);

    @Option(help = "Number of cache lines to load after the object address using prefetch instructions.")//
    public static final HostedOptionKey<Integer> AllocateInstancePrefetchLines = new HostedOptionKey<>(1);

    @Option(help = "Generated code style for prefetch instructions: for 0 or less no prefetch instructions are generated and for 1 or more prefetch instructions are introduced after each allocation.")//
    public static final HostedOptionKey<Integer> AllocatePrefetchStyle = new HostedOptionKey<>(1);

    @Option(help = "Sets the prefetch instruction to prefetch ahead of the allocation pointer. Possible values are from 0 to 3. The actual instructions behind the values depend on the platform.")//
    public static final HostedOptionKey<Integer> AllocatePrefetchInstr = new HostedOptionKey<>(0);

    @Option(help = "Sets the size (in bytes) of the prefetch distance for object allocation. " +
                    "Memory about to be written with the value of new objects is prefetched up to this distance starting from the address of the last allocated object. " +
                    "Each Java thread has its own allocation point.")//
    public static final HostedOptionKey<Integer> AllocatePrefetchDistance = new HostedOptionKey<>(192);

    @Option(help = "Sets the step size (in bytes) for sequential prefetch instructions.")//
    public static final HostedOptionKey<Integer> AllocatePrefetchStepSize = new HostedOptionKey<>(64);

    @Option(help = "How many bytes to pad fields and classes marked @Contended with.") //
    public static final HostedOptionKey<Integer> ContendedPaddingWidth = new HostedOptionKey<>(128);

    @Option(help = "Add additional header bytes to each object, for diagnostic purposes.", type = OptionType.Debug) //
    public static final HostedOptionKey<Integer> AdditionalHeaderBytes = new HostedOptionKey<>(0, SubstrateOptions::validateAdditionalHeaderBytes);

    private static void validateAdditionalHeaderBytes(HostedOptionKey<Integer> optionKey) {
        int value = optionKey.getValue();
        if (value < 0 || value % 4 != 0) {
            throw UserError.abort("The option '%s' must be 0 or a multiple of 4.", optionKey.getName());
        }
    }

    /*
     * Isolate tear down options.
     */

    @Option(help = "The number of nanoseconds before and between which tearing down an isolate gives a warning message.  0 implies no warning.")//
    public static final RuntimeOptionKey<Long> TearDownWarningNanos = new RuntimeOptionKey<>(0L, RelevantForCompilationIsolates);

    @Option(help = "The number of nanoseconds before tearing down an isolate gives a failure message.  0 implies no message.")//
    public static final RuntimeOptionKey<Long> TearDownFailureNanos = new RuntimeOptionKey<>(0L, RelevantForCompilationIsolates);

    public static long getTearDownWarningNanos() {
        return TearDownWarningNanos.getValue();
    }

    public static long getTearDownFailureNanos() {
        return TearDownFailureNanos.getValue();
    }

    @Option(help = "Define the maximum number of stores for which the loop that zeroes out objects is unrolled.")//
    public static final HostedOptionKey<Integer> MaxUnrolledObjectZeroingStores = new HostedOptionKey<>(8);

    @Option(help = "Deprecated, has no effect.", deprecated = true)//
    static final HostedOptionKey<Boolean> StackTrace = new HostedOptionKey<>(true);

    @Option(help = "Parse and consume standard options and system properties from the command line arguments when the VM is created.")//
    public static final HostedOptionKey<Boolean> ParseRuntimeOptions = new HostedOptionKey<>(true);

    @Option(help = "Enable wildcard expansion in command line arguments on Windows.")//
    public static final HostedOptionKey<Boolean> EnableWildcardExpansion = new HostedOptionKey<>(true);

    @Option(help = "Deprecated", deprecated = true)//
    static final HostedOptionKey<Boolean> AOTInline = new HostedOptionKey<>(true);

    @Option(help = "Perform trivial method inlining in the AOT compiled native image")//
    public static final HostedOptionKey<Boolean> AOTTrivialInline = new HostedOptionKey<>(true);

    @Option(help = "file:doc-files/NeverInlineHelp.txt", type = OptionType.Debug)//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> NeverInline = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());

    @Option(help = "Maximum number of nodes in a method so that it is considered trivial.")//
    public static final HostedOptionKey<Integer> MaxNodesInTrivialMethod = new HostedOptionKey<>(20);

    @Option(help = "Maximum number of invokes in a method so that it is considered trivial (for testing only).")//
    public static final HostedOptionKey<Integer> MaxInvokesInTrivialMethod = new HostedOptionKey<>(1);

    @Option(help = "Maximum number of nodes in a method so that it is considered trivial, if it does not have any invokes.")//
    public static final HostedOptionKey<Integer> MaxNodesInTrivialLeafMethod = new HostedOptionKey<>(40);

    @Option(help = "The maximum number of nodes in a graph allowed after trivial inlining.")//
    public static final HostedOptionKey<Integer> MaxNodesAfterTrivialInlining = new HostedOptionKey<>(Integer.MAX_VALUE);

    @Option(help = "Saves stack base pointer on the stack on method entry.")//
    public static final HostedOptionKey<Boolean> PreserveFramePointer = new HostedOptionKey<>(false);

    @Option(help = "Use callee saved registers to reduce spilling for low-frequency calls to stubs (if callee saved registers are supported by the architecture)")//
    public static final HostedOptionKey<Boolean> UseCalleeSavedRegisters = new HostedOptionKey<>(true);

    @Option(help = "Report error if <typename>[:<UsageKind>{,<UsageKind>}] is discovered during analysis (valid values for UsageKind: InHeap, Allocated, Reachable).", type = OptionType.Debug)//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> ReportAnalysisForbiddenType = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());

    @Option(help = "Backend used by the compiler", type = OptionType.User)//
    public static final HostedOptionKey<String> CompilerBackend = new HostedOptionKey<>("lir") {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, String oldValue, String newValue) {
            if ("llvm".equals(newValue)) {
                boolean isLLVMBackendMissing;
                if (ModuleSupport.modulePathBuild) {
                    isLLVMBackendMissing = ModuleLayer.boot().findModule("org.graalvm.nativeimage.llvm").isEmpty();
                } else {
                    isLLVMBackendMissing = ReflectionUtil.lookupClass(true, "com.oracle.svm.core.graal.llvm.LLVMFeature") == null;
                }
                if (isLLVMBackendMissing) {
                    throw UserError.abort(
                                    "The LLVM backend for GraalVM Native Image is missing and needs to be build from source. " +
                                                    "For instructions, please see https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/LLVMBackend.md.");
                }

                /* See GR-14405, https://github.com/oracle/graal/issues/1056 */
                GraalOptions.EmitStringSubstitutions.update(values, false);
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

    @Fold
    public static boolean useLIRBackend() {
        return "lir".equals(CompilerBackend.getValue());
    }

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
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> CCompilerOption = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());

    @Option(help = "Use strict checks when performing query code compilation.", type = OptionType.User)//
    public static final HostedOptionKey<Boolean> StrictQueryCodeCompilation = new HostedOptionKey<>(true);

    @APIOption(name = "native-image-info")//
    @Option(help = "Show native-toolchain information and image-build settings", type = User)//
    public static final HostedOptionKey<Boolean> DumpTargetInfo = new HostedOptionKey<>(false);

    @Option(help = "Check if native-toolchain is known to work with native-image", type = Expert)//
    public static final HostedOptionKey<Boolean> CheckToolchain = new HostedOptionKey<>(true);

    @APIOption(name = "install-exit-handlers")//
    @Option(help = "Provide java.lang.Terminator exit handlers", type = User)//
    public static final HostedOptionKey<Boolean> InstallExitHandlers = new HostedOptionKey<>(false);

    @Option(help = "When set to true, the image generator verifies that the image heap does not contain a home directory as a substring", type = User, stability = OptionStability.STABLE)//
    public static final HostedOptionKey<Boolean> DetectUserDirectoriesInImageHeap = new HostedOptionKey<>(false);

    @Option(help = "Determines if a null region is present between the heap base and the image heap.", type = Expert)//
    public static final HostedOptionKey<Boolean> UseNullRegion = new HostedOptionKey<>(true);

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

    @Option(help = "Determines if VM internal threads (e.g., a dedicated VM operation or reference handling thread) are allowed in this image.", type = OptionType.Expert) //
    public static final HostedOptionKey<Boolean> AllowVMInternalThreads = new HostedOptionKey<>(true);

    @Option(help = "Determines if debugging-specific helper methods are embedded into the image. Those methods can be called directly from the debugger to obtain or print additional information.", type = OptionType.Debug) //
    public static final HostedOptionKey<Boolean> IncludeDebugHelperMethods = new HostedOptionKey<>(false);

    @APIOption(name = "-g", fixedValue = "2", customHelp = "generate debugging information")//
    @Option(help = "Insert debug info into the generated native image or library")//
    public static final HostedOptionKey<Integer> GenerateDebugInfo = new HostedOptionKey<>(0, SubstrateOptions::validateGenerateDebugInfo) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Integer oldValue, Integer newValue) {
            if (!OS.DARWIN.isCurrent()) {
                /*
                 * Keep the symbol table, as it may be used by debugging or profiling tools (e.g.,
                 * perf). On Windows, the symbol table is included in the pdb-file, while on Linux,
                 * it is part of the .debug file.
                 */
                DeleteLocalSymbols.update(values, newValue == 0);
            }
        }
    };

    private static void validateGenerateDebugInfo(HostedOptionKey<Integer> optionKey) {
        if (OS.getCurrent() == OS.DARWIN && optionKey.hasBeenSet() && optionKey.getValue() > 0) {
            LogUtils.warning("Using %s is not supported on macOS", SubstrateOptionsParser.commandArgument(optionKey, optionKey.getValue().toString()));
        }
    }

    public static boolean useDebugInfoGeneration() {
        return useLIRBackend() && GenerateDebugInfo.getValue() > 0;
    }

    @Option(help = "Directory under which to create source file cache for Application or GraalVM classes")//
    static final HostedOptionKey<String> DebugInfoSourceCacheRoot = new HostedOptionKey<>("sources");

    @Option(help = "Temporary option to disable checking of image builder module dependencies or increasing its verbosity", type = OptionType.Debug)//
    public static final HostedOptionKey<Integer> CheckBootModuleDependencies = new HostedOptionKey<>(ModuleSupport.modulePathBuild ? 1 : 0);

    public static Path getDebugInfoSourceCacheRoot() {
        try {
            return Paths.get(Path.getValue()).resolve(DebugInfoSourceCacheRoot.getValue());
        } catch (InvalidPathException ipe) {
            throw UserError.abort("Invalid path provided for option DebugInfoSourceCacheRoot %s", DebugInfoSourceCacheRoot.getValue());
        }
    }

    @Option(help = "Use a separate file for debug info.")//
    public static final HostedOptionKey<Boolean> StripDebugInfo = new HostedOptionKey<>(OS.getCurrent() != OS.DARWIN, SubstrateOptions::validateStripDebugInfo);

    private static void validateStripDebugInfo(HostedOptionKey<Boolean> optionKey) {
        if (OS.getCurrent() == OS.DARWIN && optionKey.hasBeenSet() && optionKey.getValue()) {
            throw UserError.abort("Using %s is not supported on macOS", SubstrateOptionsParser.commandArgument(SubstrateOptions.StripDebugInfo, "+"));
        }
        if (OS.getCurrent() == OS.WINDOWS && optionKey.hasBeenSet() && !optionKey.getValue()) {
            throw UserError.abort("Using %s is not supported on Windows: debug info is always generated in a separate file", SubstrateOptionsParser.commandArgument(optionKey, "-"));
        }
    }

    @Option(help = "Omit generation of DebugLineInfo originating from inlined methods") //
    public static final HostedOptionKey<Boolean> OmitInlinedMethodDebugLineInfo = new HostedOptionKey<>(false);

    @Option(help = "Specify maximum inlining depth to consider when building DebugCodeInfo") //
    public static final HostedOptionKey<Integer> DebugCodeInfoMaxDepth = new HostedOptionKey<>(Integer.MAX_VALUE);

    @Option(help = "Do not use SourceMappings for generating DebugCodeInfo (i.e. only use Infopoints)") //
    public static final HostedOptionKey<Boolean> DebugCodeInfoUseSourceMappings = new HostedOptionKey<>(false);

    @Option(help = "Emit debuginfo debug.svm.imagebuild.* sections with detailed image-build options.")//
    public static final HostedOptionKey<Boolean> UseImagebuildDebugSections = new HostedOptionKey<>(true);

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

    @Option(help = "Options that are passed to each compilation isolate. Individual arguments are separated by spaces. Arguments that contain spaces need to be enclosed by single quotes.") //
    public static final RuntimeOptionKey<String> CompilationIsolateOptions = new RuntimeOptionKey<>(null);

    @Option(help = "Size of the reserved address space of each compilation isolate (0: default for new isolates).") //
    public static final RuntimeOptionKey<Long> CompilationIsolateAddressSpaceSize = new RuntimeOptionKey<>(0L);

    @Fold
    public static boolean useRememberedSet() {
        return !SubstrateOptions.UseEpsilonGC.getValue() && ConcealedOptions.UseRememberedSet.getValue();
    }

    /** Query these options only through an appropriate method. */
    public static class ConcealedOptions {

        @Option(help = "Support runtime compilation in separate isolates (enable at runtime with option CompileInIsolates).") //
        public static final HostedOptionKey<Boolean> SupportCompileInIsolates = new HostedOptionKey<>(null) {
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
        public static final RuntimeOptionKey<Boolean> CompileInIsolates = new RuntimeOptionKey<>(true, RelevantForCompilationIsolates);

        @Option(help = "Determines if a remembered sets is used, which is necessary for collecting the young and old generation independently.", type = OptionType.Expert) //
        public static final HostedOptionKey<Boolean> UseRememberedSet = new HostedOptionKey<>(true);

        /** Use {@link VMOperationControl#useDedicatedVMOperationThread()} instead. */
        @Option(help = "Determines if VM operations should be executed in a dedicated thread.", type = OptionType.Expert)//
        public static final HostedOptionKey<Boolean> UseDedicatedVMOperationThread = new HostedOptionKey<>(false);

        /** Use {@link ReferenceHandler#isExecutedManually()} instead. */
        @Option(help = "Determines if the reference handling is executed automatically or manually.", type = OptionType.Expert) //
        public static final RuntimeOptionKey<Boolean> AutomaticReferenceHandling = new RuntimeOptionKey<>(true, Immutable);

        /** Use {@link com.oracle.svm.core.jvmstat.PerfManager#usePerfData()} instead. */
        @Option(help = "Flag to disable jvmstat instrumentation for performance testing.")//
        public static final RuntimeOptionKey<Boolean> UsePerfData = new RuntimeOptionKey<>(true, Immutable);

        /** Use {@link SubstrateOptions#maxJavaStackTraceDepth()} instead. */
        @Option(help = "The maximum number of lines in the stack trace for Java exceptions (0 means all)", type = OptionType.User)//
        public static final RuntimeOptionKey<Integer> MaxJavaStackTraceDepth = new RuntimeOptionKey<>(maxJavaStackTraceDepth) {
            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Integer oldValue, Integer newValue) {
                super.onValueUpdate(values, oldValue, newValue);
                maxJavaStackTraceDepth = newValue;
            }
        };
    }

    @Option(help = "Overwrites the available number of processors provided by the OS. Any value <= 0 means using the processor count from the OS.")//
    public static final RuntimeOptionKey<Integer> ActiveProcessorCount = new RuntimeOptionKey<>(-1, Immutable, RelevantForCompilationIsolates);

    @Option(help = "For internal purposes only. Disables type id result verification even when running with assertions enabled.", stability = OptionStability.EXPERIMENTAL, type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> DisableTypeIdResultVerification = new HostedOptionKey<>(true);

    @Option(help = "Enables signal handling", stability = OptionStability.EXPERIMENTAL, type = Expert)//
    public static final RuntimeOptionKey<Boolean> EnableSignalHandling = new RuntimeOptionKey<>(null, Immutable) {
        @Override
        public Boolean getValueOrDefault(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
            if (values.containsKey(this)) {
                return (Boolean) values.get(this);
            }
            return ImageInfo.isExecutable();
        }

        @Override
        public Boolean getValue(OptionValues values) {
            return getValueOrDefault(values.getMap());
        }
    };

    @Option(help = "Dump heap to file (see HeapDumpPath) the first time the image throws java.lang.OutOfMemoryError because it ran out of Java heap.")//
    public static final RuntimeOptionKey<Boolean> HeapDumpOnOutOfMemoryError = new RuntimeOptionKey<>(false);

    @Option(help = "Path of the file or directory in which heap dumps are created. An empty value means a default file " +
                    "name will be used. An existing directory means the dump will be placed in the directory and have " +
                    "the default file name.") //
    public static final RuntimeOptionKey<String> HeapDumpPath = new RuntimeOptionKey<>("", Immutable);

    @Option(help = "A prefix that is used for heap dump filenames if no heap dump filename was specified explicitly.")//
    public static final HostedOptionKey<String> HeapDumpDefaultFilenamePrefix = new HostedOptionKey<>("svm-heapdump-");

    @Option(help = "Create a heap dump and exit.")//
    public static final RuntimeOptionKey<Boolean> DumpHeapAndExit = new RuntimeOptionKey<>(false, Immutable);

    @Option(help = "Enable Java Flight Recorder.")//
    public static final RuntimeOptionKey<Boolean> FlightRecorder = new RuntimeOptionKey<>(false, Immutable);

    @Option(help = "Start flight recording with options.")//
    public static final RuntimeOptionKey<String> StartFlightRecording = new RuntimeOptionKey<>("", Immutable);

    @Option(help = "file:doc-files/FlightRecorderLoggingHelp.txt")//
    public static final RuntimeOptionKey<String> FlightRecorderLogging = new RuntimeOptionKey<>("all=warning", Immutable);

    public static String reportsPath() {
        Path reportsPath = ImageSingletons.lookup(ReportingSupport.class).reportsPath;
        if (reportsPath.isAbsolute()) {
            return reportsPath.toString();
        }
        return Paths.get(Path.getValue()).resolve(reportsPath).toString();
    }

    public static class ReportingSupport {
        Path reportsPath;

        public ReportingSupport(Path reportingPath) {
            this.reportsPath = reportingPath;
        }
    }

    @Option(help = "Define PageSize of a machine that runs the image. The default = 0 (== same as host machine page size)")//
    protected static final HostedOptionKey<Integer> PageSize = new HostedOptionKey<>(0);

    @Fold
    public static int getPageSize() {
        int value = PageSize.getValue();
        if (value == 0) {
            return Unsafe.getUnsafe().pageSize();
        }
        assert value > 0;
        return value;
    }

    @Option(help = "Specifies how many details are printed for certain diagnostic thunks, e.g.: 'DumpThreads:1,DumpRegisters:2'. " +
                    "A value of 1 will result in the maximum amount of information, higher values will print less information. " +
                    "By default, the most detailed output is enabled for all diagnostic thunks. Wildcards (*) are supported in the name of the diagnostic thunk.", type = Expert)//
    public static final RuntimeOptionKey<String> DiagnosticDetails = new RuntimeOptionKey<>("", RelevantForCompilationIsolates) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, String oldValue, String newValue) {
            super.onValueUpdate(values, oldValue, newValue);
            SubstrateDiagnostics.updateInitialInvocationCounts(newValue);
        }
    };

    @Option(help = "Only print diagnostic output that is async signal safe.", type = OptionType.Expert)//
    public static final HostedOptionKey<Boolean> AsyncSignalSafeDiagnostics = new HostedOptionKey<>(false);

    @Option(help = "Specifies the number of entries that diagnostic buffers have.", type = OptionType.Debug)//
    public static final HostedOptionKey<Integer> DiagnosticBufferSize = new HostedOptionKey<>(30);

    @Option(help = "Determines if implicit exceptions are fatal if they don't have a stack trace.", type = OptionType.Debug)//
    public static final RuntimeOptionKey<Boolean> ImplicitExceptionWithoutStacktraceIsFatal = new RuntimeOptionKey<>(false);

    @SuppressWarnings("unused")//
    @APIOption(name = "configure-reflection-metadata")//
    @Option(help = "Enable runtime instantiation of reflection objects for non-invoked methods.", type = OptionType.Expert, deprecated = true)//
    public static final HostedOptionKey<Boolean> ConfigureReflectionMetadata = new HostedOptionKey<>(true);

    @Option(help = "Include a list of methods included in the image for runtime inspection.", type = OptionType.Expert)//
    public static final HostedOptionKey<Boolean> IncludeMethodData = new HostedOptionKey<>(true);

    @Option(help = "Verify type states computed by the static analysis at run time. This is useful when diagnosing problems in the static analysis, but reduces peak performance significantly.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> VerifyTypes = new HostedOptionKey<>(false);

    @Option(help = "Run reachability handlers concurrently during analysis.", type = Expert)//
    public static final HostedOptionKey<Boolean> RunReachabilityHandlersConcurrently = new HostedOptionKey<>(true);

    @Option(help = "Force many trampolines to be needed for inter-method calls. Normally trampolines are only used when a method destination is outside the range of a pc-relative branch instruction.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> UseDirectCallTrampolinesALot = new HostedOptionKey<>(false);

    @Option(help = "Initializes and runs main entry point in a new native thread.", type = Expert)//
    public static final HostedOptionKey<Boolean> RunMainInNewThread = new HostedOptionKey<>(false) {
        @Override
        public Boolean getValue(OptionValues values) {
            return getValueOrDefault(values.getMap());
        }

        @Override
        public Boolean getValueOrDefault(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
            if (!values.containsKey(this) && Platform.includedIn(Platform.LINUX.class) && LibCBase.targetLibCIs(MuslLibC.class)) {
                return true;
            }
            return (Boolean) values.get(this, this.getDefaultValue());
        }
    };

    @Option(help = "Instead of abort, only warn if image builder classes are found on the image class-path.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> AllowDeprecatedBuilderClassesOnImageClasspath = new HostedOptionKey<>(false);

    @Option(help = "file:doc-files/MissingRegistrationHelp.txt")//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> ThrowMissingRegistrationErrors = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());

    @Option(help = "file:doc-files/MissingRegistrationPathsHelp.txt")//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> ThrowMissingRegistrationErrorsPaths = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());

    public enum ReportingMode {
        Warn,
        Throw,
        ExitTest,
        Exit
    }

    @Option(help = {"Select the mode in which the missing reflection registrations will be reported.",
                    "Possible values are:",
                    "\"Throw\" (default): Throw a MissingReflectionRegistrationError;",
                    "\"Exit\": Call System.exit() to avoid accidentally catching the error;",
                    "\"Warn\": Print a message to stdout, including a stack trace to see what caused the issue."})//
    public static final HostedOptionKey<ReportingMode> MissingRegistrationReportingMode = new HostedOptionKey<>(
                    ReportingMode.Throw);

    @Option(help = "Instead of warning, throw IOExceptions for link-at-build-time resources at build time")//
    public static final HostedOptionKey<Boolean> ThrowLinkAtBuildTimeIOExceptions = new HostedOptionKey<>(false);

    @Option(help = "Allows the addresses of pinned objects to be passed to other code.", type = OptionType.Expert) //
    public static final HostedOptionKey<Boolean> PinnedObjectAddressing = new HostedOptionKey<>(true);

    @Option(help = "Emit indirect branch target marker instructions.", type = OptionType.Expert) //
    public static final HostedOptionKey<Boolean> IndirectBranchTargetMarker = new HostedOptionKey<>(false);

    @Option(help = "Enable and disable normal processing of flags relating to experimental options.", type = OptionType.Expert, stability = OptionStability.EXPERIMENTAL) //
    public static final HostedOptionKey<Boolean> UnlockExperimentalVMOptions = new HostedOptionKey<>(false);

    @Option(help = "Force using legacy method handle intrinsics.", type = Expert) //
    public static final HostedOptionKey<Boolean> UseOldMethodHandleIntrinsics = new HostedOptionKey<>(false);
}
