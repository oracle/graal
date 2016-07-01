/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import static jdk.vm.ci.common.InitTimer.timer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.stream.Collectors;

import com.oracle.graal.debug.GraalError;
import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionType;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.phases.tiers.CompilerConfiguration;
import com.oracle.graal.serviceprovider.GraalServices;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.common.InitTimer;

/**
 * A factory that creates the {@link CompilerConfiguration} the Graal compiler will use. Each
 * factory must have a unique {@link #name} and {@link #autoSelectionPriority}. The latter imposes a
 * total ordering between factories for the purpose of auto-selecting the factory to use.
 */
public abstract class CompilerConfigurationFactory implements Comparable<CompilerConfigurationFactory> {

    static class Options {
        // @formatter:off
        @Option(help = "Names the Graal compiler configuration to use. If ommitted, the compiler configuration " +
                       "with the highest auto-selection priority is used. To see the set of available configurations, " +
                       "supply the value 'help' to this option.", type = OptionType.Expert)
        public static final OptionValue<String> CompilerConfiguration = new OptionValue<>(null);
        // @formatter:on
    }

    /**
     * The name of this factory. This must be unique across all factory instances and is used when
     * selecting a factory based on the value of {@link Options#CompilerConfiguration}.
     */
    protected final String name;

    /**
     * The priority of this factory. This must be unique across all factory instances and is used
     * when selecting a factory when {@link Options#CompilerConfiguration} is omitted
     */
    protected final int autoSelectionPriority;

    /**
     * The set of available {@linkplain HotSpotBackendFactory backends} that are
     * {@linkplain HotSpotBackendFactory#isAssociatedWith(CompilerConfigurationFactory) associated}
     * with this factory.
     */
    private final IdentityHashMap<Class<? extends Architecture>, HotSpotBackendFactory> backends = new IdentityHashMap<>();

    protected CompilerConfigurationFactory(String name, int autoSelectionPriority) {
        this.name = name;
        this.autoSelectionPriority = autoSelectionPriority;
        assert checkAndAddNewFactory(this);
    }

    protected final HotSpotBackendFactory getBackendFactory(Architecture arch) {
        return backends.get(arch.getClass());
    }

    protected abstract CompilerConfiguration createCompilerConfiguration();

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

    @SuppressWarnings("all")
    private static boolean assertionsEnabled() {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        return assertionsEnabled;
    }

    /**
     * List used to assert uniqueness of {@link #name} and {@link #autoSelectionPriority} across all
     * {@link CompilerConfigurationFactory} instances.
     */
    private static final List<CompilerConfigurationFactory> factories = assertionsEnabled() ? new ArrayList<>() : null;

    private static boolean checkAndAddNewFactory(CompilerConfigurationFactory factory) {
        for (CompilerConfigurationFactory other : factories) {
            assert !other.name.equals(factory.name) : factory.getClass().getName() + " cannot have the same selector as " + other.getClass().getName() + ": " + factory.name;
            assert other.autoSelectionPriority != factory.autoSelectionPriority : factory.getClass().getName() + " cannot have the same auto-selection priority as " + other.getClass().getName() +
                            ": " + factory.autoSelectionPriority;
        }
        factories.add(factory);
        return true;
    }

    private static List<CompilerConfigurationFactory> getAllCandidates() {
        List<CompilerConfigurationFactory> candidates = new ArrayList<>();
        for (CompilerConfigurationFactory candidate : GraalServices.load(CompilerConfigurationFactory.class)) {
            candidates.add(candidate);
        }
        Collections.sort(candidates);
        return candidates;
    }

    /**
     * Selects and instantiates the {@link CompilerConfigurationFactory} to use based on the value
     * of {@link Options#CompilerConfiguration} is supplied otherwise on the available factory that
     * has the highest auto-selection priority.
     */
    @SuppressWarnings("try")
    static CompilerConfigurationFactory selectFactory() {
        CompilerConfigurationFactory factory = null;
        try (InitTimer t = timer("CompilerConfigurationFactory.selectFactory")) {
            String value = Options.CompilerConfiguration.getValue();
            if ("help".equals(value)) {
                System.out.println("The available Graal compiler configurations are:");
                for (CompilerConfigurationFactory candidate : getAllCandidates()) {
                    System.out.println("    " + candidate.name);
                }
                System.exit(0);
            } else if (value != null) {
                for (CompilerConfigurationFactory candidate : GraalServices.load(CompilerConfigurationFactory.class)) {
                    if (candidate.name.equals(value)) {
                        factory = candidate;
                        break;
                    }
                }
                if (factory == null) {
                    throw new GraalError("Graal compiler configuration '%s' not found. Available configurations are: %s", value,
                                    getAllCandidates().stream().map(c -> c.name).collect(Collectors.joining(", ")));
                }
            } else {
                List<CompilerConfigurationFactory> candidates = getAllCandidates();
                if (candidates.isEmpty()) {
                    throw new GraalError("No %s providers found", CompilerConfigurationFactory.class.getName());
                }
                Collections.sort(candidates);
                factory = candidates.get(0);
            }
        }
        try (InitTimer t = timer("HotSpotBackendFactory.register")) {
            for (HotSpotBackendFactory backend : GraalServices.load(HotSpotBackendFactory.class)) {
                if (backend.isAssociatedWith(factory)) {
                    Class<? extends Architecture> arch = backend.getArchitecture();
                    HotSpotBackendFactory oldEntry = factory.backends.put(arch, backend);
                    assert oldEntry == null || oldEntry == backend : "duplicate Graal backend";
                }
            }
        }
        return factory;
    }
}
