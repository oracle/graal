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
package com.oracle.svm.core.genscavenge.graal;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.HeapOptions;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallLinkage;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jdk.RuntimeFeature;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;

@AutomaticFeature
public class HeapFeature implements GraalFeature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return HeapOptions.UseCardRememberedSetHeap.getValue();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(RuntimeFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(Heap.class, new HeapImpl(access));
    }

    @Override
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers,
                    SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        // Even though I don't hold on to this instance,
        // it is preserved because it becomes the enclosing instance
        // for the lowerings registered within it.
        final BarrierSnippets barrierSnippets = BarrierSnippets.factory(options, factories, providers, snippetReflection);
        barrierSnippets.registerLowerings(lowerings);

        AllocationSnippets.registerLowerings(options, factories, providers, snippetReflection, lowerings);
    }

    @Override
    public void registerForeignCalls(RuntimeConfiguration runtimeConfig, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<SubstrateForeignCallDescriptor, SubstrateForeignCallLinkage> foreignCalls, boolean hosted) {

        AllocationSnippets.registerForeignCalls(runtimeConfig, providers, snippetReflection, foreignCalls, hosted);
    }
}
