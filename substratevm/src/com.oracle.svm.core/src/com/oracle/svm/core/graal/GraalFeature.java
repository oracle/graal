/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal;

import java.util.Map;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;

public interface GraalFeature extends Feature {

    /**
     * Called to register foreign calls.
     * 
     * @param foreignCalls The foreign call registry to add to.
     *
     */
    default void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
    }

    /**
     * Called to register Graal invocation plugins.
     *
     * @param providers Providers that the lowering can use.
     * @param snippetReflection Snippet reflection providers.
     * @param plugins Contains the invocation plugins to add to.
     * @param reason The parsing phase that the plugins are registered for
     */
    default void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, Plugins plugins, ParsingReason reason) {
    }

    /**
     * Called to register Graal node plugins.
     *
     * @param providers Providers that the node plugins can use.
     * @param plugins The Plugins object where node plugins can be added to.
     * @param reason The parsing phase that the plugins are registered for
     */
    default void registerGraphBuilderPlugins(Providers providers, Plugins plugins, ParsingReason reason) {
    }

    /**
     * Called to register lowering providers for static analysis, ahead-of-time compilation, and
     * runtime compilation.
     * 
     * @param runtimeConfig The runtime configuration.
     * @param options The initial option values.
     * @param providers Providers that the lowering can use.
     * @param lowerings The lowering provider registry to add to.
     * @param hosted True if registering for ahead-of-time compilation, false if registering for
     */
    default void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
    }

    /**
     * Called to register lowering providers for static analysis, ahead-of-time compilation, and
     * runtime compilation.
     *
     * @param providers Providers that the lowering can use.
     * @param snippetReflection Snippet reflection providers.
     * @param suites The Graal compilation suites to add to.
     * @param hosted True if registering for ahead-of-time compilation, false if registering for
     */
    default void registerGraalPhases(Providers providers, SnippetReflectionProvider snippetReflection, Suites suites, boolean hosted) {
    }

    /**
     * Called to register InstalledCodeObserver factories (InstalledCodeObserver.Factory) for
     * runtime compilation.
     *
     * @param runtimeConfig The runtime configuration.
     */
    default void registerCodeObserver(RuntimeConfiguration runtimeConfig) {
    }
}
