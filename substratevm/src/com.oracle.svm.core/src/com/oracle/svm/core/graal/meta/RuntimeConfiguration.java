/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.meta;

import java.util.Collection;
import java.util.EnumMap;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig.ConfigKind;
import com.oracle.svm.core.meta.SharedMethod;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Configuration used by Graal at runtime to compile and install code in the same runtime.
 */
public final class RuntimeConfiguration {

    private final Providers providers;
    private final SnippetReflectionProvider snippetReflection;
    private final EnumMap<ConfigKind, SubstrateBackend> backends;
    private final Iterable<DebugHandlersFactory> debugHandlersFactories;
    private final WordTypes wordTypes;

    @Platforms(Platform.HOSTED_ONLY.class)
    public RuntimeConfiguration(Providers providers, SnippetReflectionProvider snippetReflection, EnumMap<ConfigKind, SubstrateBackend> backends, WordTypes wordTypes,
                    Iterable<DebugHandlersFactory> debugHandlersFactories) {
        this.providers = providers;
        this.snippetReflection = snippetReflection;
        this.backends = backends;
        this.debugHandlersFactories = debugHandlersFactories;
        this.wordTypes = wordTypes;

        for (SubstrateBackend backend : backends.values()) {
            backend.setRuntimeConfiguration(this);
        }
    }

    public Iterable<DebugHandlersFactory> getDebugHandlersFactories() {
        return debugHandlersFactories;
    }

    public Providers getProviders() {
        return providers;
    }

    public Collection<SubstrateBackend> getBackends() {
        return backends.values();
    }

    public SubstrateBackend lookupBackend(ResolvedJavaMethod method) {
        if (((SharedMethod) method).isEntryPoint()) {
            return backends.get(ConfigKind.NATIVE_TO_JAVA);
        } else {
            return backends.get(ConfigKind.NORMAL);
        }
    }

    public SubstrateBackend getBackendForNormalMethod() {
        return backends.get(ConfigKind.NORMAL);
    }

    public SnippetReflectionProvider getSnippetReflection() {
        return snippetReflection;
    }

    public WordTypes getWordTypes() {
        return wordTypes;
    }
}
