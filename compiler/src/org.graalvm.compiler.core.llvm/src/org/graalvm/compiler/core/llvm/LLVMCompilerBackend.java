/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.llvm;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.nodes.StructuredGraph;

public class LLVMCompilerBackend {
    private static final TimerKey EmitLLVM = DebugContext.timer("EmitLLVM").doc("Time spent generating LLVM from HIR.");
    private static final TimerKey Populate = DebugContext.timer("EmitCode").doc("Time spent populating the compilation result.");
    private static final TimerKey BackEnd = DebugContext.timer("BackEnd").doc("Time spent in EmitLLVM and Populate.");

    @SuppressWarnings("try")
    public static void emitBackEnd(LLVMGenerationProvider backend, StructuredGraph graph, CompilationResult result) {
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope s = debug.scope("BackEnd", graph.getLastSchedule()); DebugCloseable a = BackEnd.start(debug)) {
            LLVMGenerationResult genRes = emitLLVM(backend, graph);
            result.setHasUnsafeAccess(graph.hasUnsafeAccess());
            try (DebugCloseable p = Populate.start(debug)) {
                genRes.populate(result, graph);
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        } finally {
            graph.checkCancellation();
        }
    }

    @SuppressWarnings("try")
    private static LLVMGenerationResult emitLLVM(LLVMGenerationProvider backend, StructuredGraph graph) {
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope ds = debug.scope("EmitLLVM"); DebugCloseable a = EmitLLVM.start(debug)) {
            assert !graph.hasValueProxies();

            LLVMGenerationResult genResult = backend.newLLVMGenerationResult(graph.method());
            LLVMGenerator generator = backend.newLLVMGenerator(genResult);
            NodeLLVMBuilder nodeBuilder = backend.newNodeLLVMBuilder(graph, generator);

            /* LIR generation */
            LLVMGenerationPhase.run(nodeBuilder, graph);

            try (DebugContext.Scope s = debug.scope("LIRStages", nodeBuilder, genResult, null)) {
                /* Dump LIR along with HIR (the LIR is looked up from context) */
                debug.dump(DebugContext.BASIC_LEVEL, graph.getLastSchedule(), "After LIR generation");
                return genResult;
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        } finally {
            graph.checkCancellation();
        }
    }
}
