/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import static jdk.vm.ci.common.InitTimer.timer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import jdk.graal.compiler.core.Instrumentation;
import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.core.common.util.PhasePlan;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.options.EnumOptionKey;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.tiers.CompilerConfiguration;
import jdk.graal.compiler.serviceprovider.GlobalAtomicLong;
import jdk.graal.compiler.serviceprovider.GraalServices;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.services.Services;

/**
 * A factory that creates the {@link CompilerConfiguration} the compiler will use. Each factory must
 * have a unique {@link #name} and {@link #autoSelectionPriority}. The latter imposes a total
 * ordering between factories for the purpose of auto-selecting the factory to use.
 */
public abstract class CompilerConfigurationFactory implements Comparable<CompilerConfigurationFactory> {

    public enum ShowConfigurationLevel {
        none,
        info,
        verbose
    }

    public static class Options {
        // @formatter:off
        @Option(help = "Names the compiler configuration to use. " +
                       "If omitted, uses the compiler configuration with the greatest auto-selection priority. " +
                       "To see available configurations, use the value 'help'.", type = OptionType.Expert, stability = OptionStability.STABLE)
        public static final OptionKey<String> CompilerConfiguration = new OptionKey<>(null);
        @Option(help = "Writes the configuration of the selected compiler to the VM log.", type = OptionType.User, stability = OptionStability.STABLE)
        public static final OptionKey<ShowConfigurationLevel> ShowConfiguration = new EnumOptionKey<>(ShowConfigurationLevel.none);
        // @formatter:on
    }

    /**
     * The name of this factory. This must be unique across all factory instances and is used when
     * selecting a factory based on the value of {@link Options#CompilerConfiguration}.
     */
    private final String name;

    /**
     * A description of this configuration used for {@link ShowConfigurationLevel#info}.
     */
    private final String info;

    /**
     * The priority of this factory. This must be unique across all factory instances and is used
     * when selecting a factory when {@link Options#CompilerConfiguration} is omitted
     */
    private final int autoSelectionPriority;

    /**
     * Creates a compiler configuration factory.
     *
     * @param name the name by which users can explicitly select the configuration via the
     *            {@link Options#CompilerConfiguration} option
     * @param info a higher level description of the configuration used for
     *            {@link ShowConfigurationLevel#info}
     * @param autoSelectionPriority
     */
    protected CompilerConfigurationFactory(String name, String info, int autoSelectionPriority) {
        this.name = name;
        this.info = info;
        this.autoSelectionPriority = autoSelectionPriority;
    }

    public abstract CompilerConfiguration createCompilerConfiguration();

    public abstract Instrumentation createInstrumentation(OptionValues options);

    /**
     * Collect the set of available {@linkplain HotSpotBackendFactory backends} for this compiler
     * configuration.
     */
    public BackendMap createBackendMap() {
        // default to backend with the same name as the compiler configuration
        return new DefaultBackendMap(name);
    }

    /**
     * Returns a name that should uniquely identify this compiler configuration.
     */
    public final String getName() {
        return name;
    }

    public interface BackendMap {
        HotSpotBackendFactory getBackendFactory(Architecture arch);
    }

    public static class DefaultBackendMap implements BackendMap {

        private final EconomicMap<Class<? extends Architecture>, HotSpotBackendFactory> backends = EconomicMap.create();

        @SuppressWarnings("try")
        public DefaultBackendMap(String backendName) {
            try (InitTimer t = timer("HotSpotBackendFactory.register")) {
                for (HotSpotBackendFactory backend : GraalServices.load(HotSpotBackendFactory.class)) {
                    if (backend.getName().equals(backendName)) {
                        Class<? extends Architecture> arch = backend.getArchitecture();
                        if (arch != null) {
                            HotSpotBackendFactory oldEntry = backends.put(arch, backend);
                            assert oldEntry == null || oldEntry == backend : "duplicate Graal backend";
                        }
                    }
                }
            }
        }

        @Override
        public final HotSpotBackendFactory getBackendFactory(Architecture arch) {
            return backends.get(arch.getClass());
        }
    }

    @Override
    public int compareTo(CompilerConfigurationFactory o) {
        if (autoSelectionPriority > o.autoSelectionPriority) {
            return -1;
        }
        if (autoSelectionPriority < o.autoSelectionPriority) {
            return 1;
        }
        assert this == o : "distinct compiler configurations cannot have the same auto selection priority";
        return 0;
    }

    /**
     * Asserts uniqueness of {@link #name} and {@link #autoSelectionPriority} for {@code factory} in
     * {@code factories}.
     */
    private static boolean checkUnique(CompilerConfigurationFactory factory, List<CompilerConfigurationFactory> factories) {
        for (CompilerConfigurationFactory other : factories) {
            if (other != factory && factory.autoSelectionPriority == other.autoSelectionPriority) {
                assert !other.name.equals(factory.name) : factory.getClass().getName() + " cannot have the same selector as " + other.getClass().getName() + ": " + factory.name;
                assert other.autoSelectionPriority != factory.autoSelectionPriority : factory.getClass().getName() + " cannot have the same auto-selection priority as " +
                                other.getClass().getName() +
                                ": " + factory.autoSelectionPriority;
            }
        }
        return true;
    }

    /**
     * @return sorted list of {@link CompilerConfigurationFactory}s
     */
    @SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE", justification = "false positive on dead store to `candidates`")
    private static List<CompilerConfigurationFactory> getAllCandidates() {
        List<CompilerConfigurationFactory> candidates = new ArrayList<>();
        for (CompilerConfigurationFactory candidate : GraalServices.load(CompilerConfigurationFactory.class)) {
            assert checkUnique(candidate, candidates);
            candidates.add(candidate);
        }
        Collections.sort(candidates);
        return candidates;
    }

    // Ensures ShowConfiguration output is printed once per VM process.
    private static final GlobalAtomicLong shownConfiguration = new GlobalAtomicLong(0L);

    /**
     * Selects and instantiates a {@link CompilerConfigurationFactory}. The selection algorithm is
     * as follows: if {@code name} is non-null, then select the factory with the same name else if
     * {@code Options.CompilerConfiguration.getValue()} is non-null then select the factory whose
     * name matches the value else select the factory with the highest
     * {@link #autoSelectionPriority} value.
     *
     * @param name the name of the compiler configuration to select (optional)
     */
    @SuppressWarnings("try")
    public static CompilerConfigurationFactory selectFactory(String name, OptionValues options, HotSpotJVMCIRuntime runtime) {
        CompilerConfigurationFactory factory = null;
        try (InitTimer t = timer("CompilerConfigurationFactory.selectFactory")) {
            String value = name == null ? Options.CompilerConfiguration.getValue(options) : name;
            if ("help".equals(value)) {
                System.out.println("The available compiler configurations are:");
                for (CompilerConfigurationFactory candidate : getAllCandidates()) {
                    System.out.println("    " + candidate.name + " priority " + candidate.autoSelectionPriority);
                }
                HotSpotGraalServices.exit(0, runtime);
            } else if (value != null) {
                for (CompilerConfigurationFactory candidate : getAllCandidates()) {
                    if (candidate.name.equals(value)) {
                        factory = candidate;
                        break;
                    }
                }
                if (factory == null) {
                    throw new GraalError("Compiler configuration '%s' not found. Available configurations are: %s", value,
                                    getAllCandidates().stream().map(c -> c.name).collect(Collectors.joining(", ")));
                }
            } else {
                List<CompilerConfigurationFactory> candidates = getAllCandidates();
                if (candidates.isEmpty()) {
                    throw new GraalError("No %s providers found", CompilerConfigurationFactory.class.getName());
                }
                factory = candidates.get(0);
            }
        }
        assert factory != null;

        ShowConfigurationLevel level = Options.ShowConfiguration.getValue(options);
        if (level != ShowConfigurationLevel.none && shownConfiguration.compareAndSet(0L, 1L)) {
            switch (level) {
                case info: {
                    printConfigInfo(factory);
                    break;
                }
                case verbose: {
                    printConfigVerbose(factory);
                    CompilerConfiguration config = factory.createCompilerConfiguration();
                    printPlan("High tier:", () -> config.createHighTier(options));
                    printPlan("Mid tier:", () -> config.createMidTier(options));
                    printPlan("Low tier:", () -> config.createLowTier(options, runtime.getHostJVMCIBackend().getTarget().arch));
                    printPlan("Pre regalloc stage:", () -> config.createPreAllocationOptimizationStage(options));
                    printPlan("Regalloc stage:", () -> config.createAllocationStage(options));
                    printPlan("Post regalloc stage:", () -> config.createPostAllocationOptimizationStage(options));
                    break;
                }
            }
        }
        return factory;
    }

    /**
     * Gets an object whose {@link #toString()} value describes where this configuration factory was
     * loaded from.
     */
    private Object getLoadedFromLocation(boolean verbose) {
        if (Services.IS_IN_NATIVE_IMAGE) {
            if (nativeImageLocationQualifier != null) {
                return "a " + nativeImageLocationQualifier + " Native Image shared library";
            }
            return "a Native Image shared library";
        }
        return verbose ? getClass().getResource(getClass().getSimpleName() + ".class") : "class files";
    }

    private static String nativeImageLocationQualifier;

    /**
     * Records a qualifier for the libgraal library (e.g., "PGO optimized").
     */
    public static void setNativeImageLocationQualifier(String s) {
        GraalError.guarantee(nativeImageLocationQualifier == null, "Native image location qualifier is already set to %s", nativeImageLocationQualifier);
        nativeImageLocationQualifier = s;
    }

    private static void printConfigInfo(CompilerConfigurationFactory factory) {
        Object location = factory.getLoadedFromLocation(false);
        TTY.printf("Using \"%s\" loaded from %s%n", factory.info, location);
    }

    private static void printConfigVerbose(CompilerConfigurationFactory factory) {
        Object location = factory.getLoadedFromLocation(true);
        TTY.printf("Using compiler configuration '%s' (\"%s\") provided by %s loaded from %s%n", factory.name, factory.info, factory.getClass().getName(), location);
    }

    private static <T> void printPlan(String label, Supplier<PhasePlan<T>> plan) {
        try {
            TTY.printf("%s%n%s", label, new PhasePlan.Printer().toString(plan.get()));
        } catch (Throwable t) {
            t.printStackTrace(TTY.out);
        }
    }
}
