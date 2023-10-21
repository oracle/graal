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

import java.util.Arrays;
import java.util.List;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.phases.tiers.Suites;

import com.oracle.truffle.compiler.TruffleCompilerRuntime;

import jdk.vm.ci.code.Architecture;

public final class TruffleCompilerConfiguration {
    private final TruffleCompilerRuntime runtime;
    private final GraphBuilderConfiguration.Plugins plugins;
    private final SnippetReflectionProvider provider;
    private final TruffleTierConfiguration firstTier;
    private final TruffleTierConfiguration lastTier;
    private final KnownTruffleTypes types;
    private final Suites hostSuite;

    public TruffleCompilerConfiguration(
                    TruffleCompilerRuntime runtime,
                    GraphBuilderConfiguration.Plugins plugins,
                    SnippetReflectionProvider provider,
                    TruffleTierConfiguration firstTier,
                    TruffleTierConfiguration lastTier,
                    KnownTruffleTypes knownTruffleTypes,
                    Suites hostSuite) {
        this.runtime = runtime;
        this.plugins = plugins;
        this.provider = provider;
        this.firstTier = firstTier;
        this.lastTier = lastTier;
        this.types = knownTruffleTypes;
        this.hostSuite = hostSuite;
    }

    public TruffleCompilerRuntime runtime() {
        return runtime;
    }

    public GraphBuilderConfiguration.Plugins plugins() {
        return plugins;
    }

    public SnippetReflectionProvider snippetReflection() {
        return provider;
    }

    public TruffleTierConfiguration firstTier() {
        return firstTier;
    }

    public TruffleTierConfiguration lastTier() {
        return lastTier;
    }

    public KnownTruffleTypes types() {
        return types;
    }

    public Suites hostSuite() {
        return hostSuite;
    }

    public TruffleCompilerConfiguration withFirstTier(TruffleTierConfiguration tier) {
        return new TruffleCompilerConfiguration(runtime, plugins, provider, tier, lastTier, types, hostSuite);
    }

    public List<Backend> backends() {
        if (lastTier.backend() == firstTier.backend()) {
            return Arrays.asList(lastTier.backend());
        } else {
            return Arrays.asList(firstTier.backend(), lastTier.backend());
        }
    }

    public Architecture architecture() {
        Architecture arch = lastTier().backend().getTarget().arch;
        assert arch.equals(firstTier().backend().getTarget().arch) : "target architecture must be the same for first and last tier.";
        return arch;
    }
}
