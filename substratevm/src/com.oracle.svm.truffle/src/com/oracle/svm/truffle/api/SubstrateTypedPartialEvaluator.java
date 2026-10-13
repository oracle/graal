/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.api;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.truffle.compiler.TruffleCompilable;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.truffle.TruffleCompilerConfiguration;
import jdk.graal.compiler.truffle.TruffleTierContext;
import jdk.graal.compiler.truffle.phases.InstrumentPhase;
import jdk.graal.compiler.truffle.phases.InstrumentationSuite;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SubstrateTypedPartialEvaluator extends SubstratePartialEvaluator {

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateTypedPartialEvaluator(TruffleCompilerConfiguration config, GraphBuilderConfiguration graphBuilderConfigForRoot) {
        super(config, graphBuilderConfigForRoot);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    protected void registerGraphBuilderInvocationPlugins(InvocationPlugins invocationPlugins, boolean canDelayIntrinsification) {
        super.registerGraphBuilderInvocationPlugins(invocationPlugins, canDelayIntrinsification);
        TypedEntrySupport.registerRuntimePlugins(invocationPlugins, canDelayIntrinsification);
    }

    static final class SubstrateTypedInstrumentationSuite extends InstrumentationSuite {

        SubstrateTypedInstrumentationSuite(InstrumentPhase.InstrumentationConfiguration instrumentationCfg, InstrumentPhase.Instrumentation instrumentation) {
            super(instrumentationCfg, instrumentation);
            appendPhase(new BasePhase<TruffleTierContext>() {
                @Override
                protected void run(StructuredGraph graph, TruffleTierContext context) {
                    TypedEntrySupport.afterTruffleTier(graph);
                }
            });
        }
    }

    @Override
    public ResolvedJavaMethod rootForCallTarget(TruffleCompilable callTarget) {
        return TypedEntrySupport.singleton().rootForCallTarget(callTarget);
    }
}
