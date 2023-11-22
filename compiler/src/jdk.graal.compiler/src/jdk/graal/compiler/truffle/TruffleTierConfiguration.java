/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle;

import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.truffle.phases.TruffleCompilerPhases;

public final class TruffleTierConfiguration {
    private final PartialEvaluatorConfiguration configuration;
    private final Backend backend;
    private final Providers providers;
    private final Suites suites;
    private final LIRSuites lirSuites;

    public TruffleTierConfiguration(PartialEvaluatorConfiguration configuration, Backend backend, OptionValues options, KnownTruffleTypes knownTruffleTypes) {
        this(configuration, backend, backend.getProviders(), backend.getSuites().getDefaultSuites(options, backend.getTarget().arch), backend.getSuites().getDefaultLIRSuites(options),
                        knownTruffleTypes);
    }

    public TruffleTierConfiguration(PartialEvaluatorConfiguration configuration, Backend backend, Providers providers, Suites suites, LIRSuites lirSuites, KnownTruffleTypes knownTruffleTypes) {
        this.configuration = configuration;
        this.backend = backend;
        this.providers = providers.copyWith(new TruffleStringConstantFieldProvider(providers, knownTruffleTypes));
        this.suites = suites;
        this.lirSuites = lirSuites;
        TruffleCompilerPhases.register(knownTruffleTypes, providers, suites);
        this.suites.setImmutable();
    }

    public PartialEvaluatorConfiguration partialEvaluator() {
        return configuration;
    }

    public Backend backend() {
        return backend;
    }

    public Providers providers() {
        return providers;
    }

    public Suites suites() {
        return suites;
    }

    public LIRSuites lirSuites() {
        return lirSuites;
    }
}
