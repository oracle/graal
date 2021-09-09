/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler;

import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;

import jdk.vm.ci.code.Architecture;

public final class TruffleCompilerConfiguration {
    private final TruffleCompilerRuntime runtime;
    private final GraphBuilderConfiguration.Plugins plugins;
    private final SnippetReflectionProvider provider;
    private final TruffleTierConfiguration firstTier;
    private final TruffleTierConfiguration lastTier;

    public TruffleCompilerConfiguration(TruffleCompilerRuntime runtime, GraphBuilderConfiguration.Plugins plugins, SnippetReflectionProvider provider, TruffleTierConfiguration firstTier,
                    TruffleTierConfiguration lastTier) {
        this.runtime = runtime;
        this.plugins = plugins;
        this.provider = provider;
        this.firstTier = firstTier;
        this.lastTier = lastTier;
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

    public TruffleCompilerConfiguration withFirstTier(TruffleTierConfiguration tier) {
        return new TruffleCompilerConfiguration(runtime, plugins, provider, tier, lastTier);
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
        assert arch.equals(firstTier().backend().getTarget().arch) : "target architecture must be the same for first and list tier.";
        return arch;
    }
}
