/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Assumptions.Assumption;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleAssumption;

/**
 * A mechanism for Truffle to update a {@link CompilationResult} before it is
 * {@linkplain CompilationResult#close() closed} by the compiler.
 */
class TruffleCompilationResultBuilderFactory implements CompilationResultBuilderFactory {

    /**
     * The graph being compiled.
     */
    private final StructuredGraph graph;

    /**
     * List into which {@link TruffleAssumption}s are added.
     */
    private final List<TruffleAssumption> validAssumptions;

    TruffleCompilationResultBuilderFactory(StructuredGraph graph, List<TruffleAssumption> validAssumptions) {
        this.graph = graph;
        this.validAssumptions = validAssumptions;
    }

    @Override
    public CompilationResultBuilder createBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder, FrameContext frameContext,
                    OptionValues options, DebugContext debug, CompilationResult compilationResult, Register uncompressedNullRegister) {
        return new CompilationResultBuilder(codeCache, foreignCalls, frameMap, asm, dataBuilder, frameContext, options, debug, compilationResult, uncompressedNullRegister) {
            @Override
            protected void closeCompilationResult() {
                CompilationResult result = this.compilationResult;
                result.setMethods(graph.method(), graph.getMethods());
                result.setSpeculationLog(graph.getSpeculationLog());
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

    static void processAssumption(Set<Assumption> newAssumptions, Assumption assumption, List<TruffleAssumption> manual) {
        if (assumption != null) {
            if (assumption instanceof TruffleAssumption) {
                TruffleAssumption assumptionValidAssumption = (TruffleAssumption) assumption;
                manual.add(assumptionValidAssumption);
            } else {
                newAssumptions.add(assumption);
            }
        }
    }
}
