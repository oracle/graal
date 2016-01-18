/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.meta.Assumptions.Assumption;

import com.oracle.graal.asm.Assembler;
import com.oracle.graal.code.CompilationResult;
import com.oracle.graal.compiler.common.spi.ForeignCallsProvider;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.asm.CompilationResultBuilderFactory;
import com.oracle.graal.lir.asm.DataBuilder;
import com.oracle.graal.lir.asm.FrameContext;
import com.oracle.graal.lir.framemap.FrameMap;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.truffle.nodes.AssumptionValidAssumption;

/**
 * A mechanism for Truffle to update a {@link CompilationResult} before it is
 * {@linkplain CompilationResult#close() closed} by the Graal compiler.
 */
class TruffleCompilationResultBuilderFactory implements CompilationResultBuilderFactory {

    /**
     * The graph being compiled.
     */
    private final StructuredGraph graph;

    /**
     * List into which {@link AssumptionValidAssumption}s are added.
     */
    private final List<AssumptionValidAssumption> validAssumptions;

    public TruffleCompilationResultBuilderFactory(StructuredGraph graph, List<AssumptionValidAssumption> validAssumptions) {
        this.graph = graph;
        this.validAssumptions = validAssumptions;
    }

    public CompilationResultBuilder createBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder, FrameContext frameContext,
                    CompilationResult compilationResult) {
        return new CompilationResultBuilder(codeCache, foreignCalls, frameMap, asm, dataBuilder, frameContext, compilationResult) {
            @Override
            protected void closeCompilationResult() {
                CompilationResult result = this.compilationResult;
                result.setMethods(graph.method(), graph.getInlinedMethods());
                result.setBytecodeSize(graph.getBytecodeSize());

                Set<Assumption> newAssumptions = new HashSet<>();
                for (Assumption assumption : graph.getAssumptions()) {
                    TruffleCompilationResultBuilderFactory.processAssumption(newAssumptions, assumption, validAssumptions);
                }

                if (result.getAssumptions() != null) {
                    for (Assumption assumption : result.getAssumptions()) {
                        TruffleCompilationResultBuilderFactory.processAssumption(newAssumptions, assumption, validAssumptions);
                    }
                }

                result.setAssumptions(newAssumptions.toArray(new Assumption[newAssumptions.size()]));
                super.closeCompilationResult();
            }
        };
    }

    static void processAssumption(Set<Assumption> newAssumptions, Assumption assumption, List<AssumptionValidAssumption> manual) {
        if (assumption != null) {
            if (assumption instanceof AssumptionValidAssumption) {
                AssumptionValidAssumption assumptionValidAssumption = (AssumptionValidAssumption) assumption;
                manual.add(assumptionValidAssumption);
            } else {
                newAssumptions.add(assumption);
            }
        }
    }
}
