/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.tiers;

import static com.oracle.graal.phases.tiers.Suites.Options.*;

import java.util.*;

import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.*;

public final class Suites {

    static class Options {

        // @formatter:off
        @Option(help = "The compiler configuration to use")
        static final OptionValue<String> CompilerConfiguration = new OptionValue<>("");
        // @formatter:on
    }

    private final PhaseSuite<HighTierContext> highTier;
    private final PhaseSuite<MidTierContext> midTier;
    private final PhaseSuite<LowTierContext> lowTier;

    private static final CompilerConfiguration defaultConfiguration;
    private static final Map<String, CompilerConfiguration> configurations;

    public PhaseSuite<HighTierContext> getHighTier() {
        return highTier;
    }

    public PhaseSuite<MidTierContext> getMidTier() {
        return midTier;
    }

    public PhaseSuite<LowTierContext> getLowTier() {
        return lowTier;
    }

    static {
        configurations = new HashMap<>();
        CompilerConfiguration basic = null;
        CompilerConfiguration nonBasic = null;
        int nonBasicCount = 0;

        for (CompilerConfiguration config : Services.load(CompilerConfiguration.class)) {
            String name = config.getClass().getSimpleName();
            if (name.endsWith("CompilerConfiguration")) {
                name = name.substring(0, name.length() - "CompilerConfiguration".length());
            }
            name = name.toLowerCase();

            configurations.put(name, config);
            if (name.equals("basic")) {
                assert basic == null;
                basic = config;
            } else {
                nonBasic = config;
                nonBasicCount++;
            }
        }

        if (CompilerConfiguration.getValue().equals("")) {
            if (nonBasicCount == 1) {
                /*
                 * There is exactly one non-basic configuration. We use this one as default.
                 */
                defaultConfiguration = nonBasic;
            } else {
                /*
                 * There is either no extended configuration available, or more than one. In that
                 * case, default to "basic".
                 */
                defaultConfiguration = basic;
                if (defaultConfiguration == null) {
                    throw new GraalInternalError("unable to find basic compiler configuration");
                }
            }
        } else {
            defaultConfiguration = configurations.get(CompilerConfiguration.getValue());
            if (defaultConfiguration == null) {
                throw new GraalInternalError("unknown compiler configuration: " + CompilerConfiguration.getValue());
            }
        }
    }

    private Suites(CompilerConfiguration config) {
        highTier = config.createHighTier();
        midTier = config.createMidTier();
        lowTier = config.createLowTier();
    }

    public static Suites createDefaultSuites() {
        return new Suites(defaultConfiguration);
    }

    public static Suites createSuites(String name) {
        CompilerConfiguration config = configurations.get(name);
        if (config == null) {
            throw new GraalInternalError("unknown compiler configuration: " + name);
        }
        return new Suites(config);
    }

}
