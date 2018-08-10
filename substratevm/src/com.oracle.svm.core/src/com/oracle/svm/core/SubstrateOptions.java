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

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;

public class SubstrateOptions {

    private static ValueUpdateHandler optimizeValueUpdateHandler;

    @Option(help = "Show available options based on comma-separated option-types (allowed categories: User, Expert, Debug).")//
    public static final OptionKey<String> PrintFlags = new OptionKey<>(null);

    @Option(help = "Control native-image code optimizations: 0 - no optimizations, 1 - basic optimizations.", type = OptionType.User)//
    public static final HostedOptionKey<Integer> Optimize = new HostedOptionKey<Integer>(1) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Integer oldValue, Integer newValue) {
            SubstrateOptions.IncludeNodeSourcePositions.update(values, newValue < 1);
            SubstrateOptions.AOTInline.update(values, newValue > 0);
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

    @Option(help = "Track NodeSourcePositions during runtime-compilation")//
    public static final HostedOptionKey<Boolean> IncludeNodeSourcePositions = new HostedOptionKey<>(false);

    @Option(help = "Search path for C libraries passed to the linker (list of comma-separated directories)")//
    public static final HostedOptionKey<String> CLibraryPath = new HostedOptionKey<>(
                    Paths.get("clibraries/" + OS.getCurrent().asPackageName() + "-" + SubstrateUtil.getArchitectureName()).toAbsolutePath().toString());

    @Option(help = "Path passed to the linker as the -rpath (list of comma-separated directories)")//
    public static final HostedOptionKey<String> LinkerRPath = new HostedOptionKey<>("");

    @APIOption(name = "-ea", customHelp = "enable assertions in the generated image")//
    @APIOption(name = "-da", kind = APIOption.APIOptionKind.Negated, customHelp = "disable assertions in the generated image")//
    @Option(help = "Enable or disable Java assert statements at run time", type = OptionType.User)//
    public static final HostedOptionKey<Boolean> RuntimeAssertions = new HostedOptionKey<>(false);

    @Option(help = "Directory of the image file to be generated", type = OptionType.User)//
    public static final HostedOptionKey<String> Path = new HostedOptionKey<>(Paths.get(".").toAbsolutePath().normalize().resolve("svmbuild").toString());

    @Fold
    public static FoldedPredicate getRuntimeAssertionsFilter() {
        return makeFilter(RuntimeAssertionsFilter.getValue());
    }

    @Option(help = "Print summary GC information after each collection")//
    public static final RuntimeOptionKey<Boolean> PrintGC = new RuntimeOptionKey<>(false);

    @Option(help = "Print summary GC information after main completion")//
    public static final RuntimeOptionKey<Boolean> PrintGCSummary = new RuntimeOptionKey<>(false);

    @Option(help = "Print a time stamp at each collection, if +PrintGC or +VerboseGC.")//
    public static final RuntimeOptionKey<Boolean> PrintGCTimeStamps = new RuntimeOptionKey<>(false);

    @Option(help = "Print more information about the heap before and after each collection")//
    public static final RuntimeOptionKey<Boolean> VerboseGC = new RuntimeOptionKey<>(false);

    @Option(help = "Verify naming conventions during image construction.")//
    public static final HostedOptionKey<Boolean> VerifyNamingConventions = new HostedOptionKey<>(false);

    @Option(help = "Enable support for threads and and thread-local variables (disable for single-threaded implementation)")//
    public static final HostedOptionKey<Boolean> MultiThreaded = new HostedOptionKey<>(true);

    @Option(help = "Use only a writable native image heap.")//
    public static final HostedOptionKey<Boolean> UseOnlyWritableBootImageHeap = new HostedOptionKey<>(false);

    @Option(help = "Use heap base register. ")//
    public static final HostedOptionKey<Boolean> UseHeapBaseRegister = new HostedOptionKey<>(false);

    @Option(help = "Use linear pointer compression (requires the use of heap base register).")//
    public static final HostedOptionKey<Boolean> UseLinearPointerCompression = new HostedOptionKey<>(true);

    @Option(help = "Support multiple isolates (disable for legacy mode with a single isolate). ")//
    public static final HostedOptionKey<Boolean> SpawnIsolates = new HostedOptionKey<Boolean>(false) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            if (newValue) {
                UseHeapBaseRegister.update(values, true);
            }
        }
    };

    @Option(help = "Trace VMOperation execution.")//
    public static final RuntimeOptionKey<Boolean> TraceVMOperations = new RuntimeOptionKey<>(false);

    @Option(help = "Prefix that is added to the names of entry point methods.")//
    public static final HostedOptionKey<String> EntryPointNamePrefix = new HostedOptionKey<>("");

    @Option(help = "Prefix that is added to the names of API functions.")//
    public static final HostedOptionKey<String> APIFunctionPrefix = new HostedOptionKey<>("graal_");

    @Option(help = "List of comma separated protocols to enable.")//
    public static final HostedOptionKey<String> EnableURLProtocols = new HostedOptionKey<>("");

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
                    "Memory about to be written with the value of new objects is prefetched up to this distance starting from the address of the last allocated object. Each Java thread has its own allocation point.")//
    public static final HostedOptionKey<Integer> AllocatePrefetchDistance = new HostedOptionKey<>(256);

    @Option(help = "Sets the step size (in bytes) for sequential prefetch instructions.")//
    public static final HostedOptionKey<Integer> AllocatePrefetchStepSize = new HostedOptionKey<>(16);

    @Option(help = "Define the maximum number of stores for which the loop that zeroes out objects is unrolled.")//
    public static final HostedOptionKey<Integer> MaxUnrolledObjectZeroingStores = new HostedOptionKey<>(8);

    @Option(help = "Provide method names for stack traces.")//
    public static final HostedOptionKey<Boolean> StackTrace = new HostedOptionKey<>(true);

    @Option(help = "Use runtime-option parsing in JavaMainWrapper")//
    public static final HostedOptionKey<Boolean> ParseRuntimeOptions = new HostedOptionKey<>(true);

    @Option(help = "Only use Java assert statements for classes that are matching the comma-separated list of package prefixes.")//
    public static final HostedOptionKey<String> RuntimeAssertionsFilter = new HostedOptionKey<>(null);

    @Option(help = "Perform method inlining in the AOT compiled native image")//
    public static final HostedOptionKey<Boolean> AOTInline = new HostedOptionKey<>(true);

    @Option(help = "Maximum number of nodes in a method so that it is considered trivial.")//
    public static final HostedOptionKey<Integer> MaxNodesInTrivialMethod = new HostedOptionKey<>(20);

    @Option(help = "Maximum number of invokes in a method so that it is considered trivial (for testing only).")//
    public static final HostedOptionKey<Integer> MaxInvokesInTrivialMethod = new HostedOptionKey<>(1);

    @Option(help = "Maximum number of nodes in a method so that it is considered trivial, if it does not have any invokes.")//
    public static final HostedOptionKey<Integer> MaxNodesInTrivialLeafMethod = new HostedOptionKey<>(40);

    public static FoldedPredicate makeFilter(String definedFilter) {
        if (definedFilter != null) {
            List<String> wildCardList = Arrays.asList(definedFilter.split(","));
            if (!wildCardList.contains("")) {
                return new FoldedPredicate((String javaName) -> {
                    for (String wildCard : wildCardList) {
                        if (javaName.startsWith(wildCard)) {
                            return true;
                        }
                    }
                    return false;
                });
            }
        }
        return new FoldedPredicate((String javaName) -> true);
    }

    public static class FoldedPredicate implements Predicate<String> {

        @Platforms(Platform.HOSTED_ONLY.class)//
        private final Predicate<String> wrapped;

        @Platforms(Platform.HOSTED_ONLY.class)
        public FoldedPredicate(Predicate<String> wrapped) {
            this.wrapped = wrapped;
        }

        @Fold
        @Override
        public boolean test(String t) {
            return wrapped.test(t);
        }
    }
}
