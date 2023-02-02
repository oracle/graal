/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.snippets;

import java.util.ListIterator;
import java.util.Map;
import java.util.function.Predicate;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.tiers.MidTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.heap.RestrictHeapAccessCallees;
import com.oracle.svm.core.stack.StackOverflowCheck;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticallyRegisteredFeature
@Platforms(InternalPlatform.NATIVE_ONLY.class)
public final class StackOverflowCheckFeature implements InternalFeature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(StackOverflowCheck.class, new StackOverflowCheckImpl());
    }

    @Override
    public void registerGraalPhases(Providers providers, SnippetReflectionProvider snippetReflection, Suites suites, boolean hosted) {
        /*
         * There is no need to have the stack overflow check in the graph throughout most of the
         * compilation pipeline. Inserting it before the mid-tier lowering is done for pragmatic
         * reasons: the foreign call to the slow path needs a frame state, and that is handled
         * automatically when the stack overflow check is inserted and lowered before the
         * FrameStateAssignmentPhase runs.
         */
        ListIterator<BasePhase<? super MidTierContext>> position = suites.getMidTier().findPhase(LoweringPhase.class);
        position.previous();
        position.add(new InsertStackOverflowCheckPhase());
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(StackOverflowCheckImpl.FOREIGN_CALLS);
    }

    @Override
    @SuppressWarnings("unused")
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        Predicate<ResolvedJavaMethod> mustNotAllocatePredicate = null;
        if (hosted) {
            mustNotAllocatePredicate = method -> ImageSingletons.lookup(RestrictHeapAccessCallees.class).mustNotAllocate(method);
        }

        new StackOverflowCheckSnippets(options, providers, lowerings, mustNotAllocatePredicate);
    }
}
