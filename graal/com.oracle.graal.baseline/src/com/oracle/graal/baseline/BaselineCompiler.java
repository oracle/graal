/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.baseline;

import static com.oracle.graal.api.code.TypeCheckHints.*;
import static com.oracle.graal.bytecode.Bytecodes.*;
import static com.oracle.graal.phases.GraalOptions.*;
import static java.lang.reflect.Modifier.*;

import java.util.*;

import com.oracle.graal.alloc.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.ResolvedJavaType.Representation;
import com.oracle.graal.bytecode.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.alloc.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.*;
import com.oracle.graal.java.*;
import com.oracle.graal.java.BciBlockMapping.BciBlock;
import com.oracle.graal.java.BciBlockMapping.ExceptionDispatchBlock;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.calc.FloatConvertNode.FloatConvert;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.java.*;

/**
 * The {@code GraphBuilder} class parses the bytecode of a method and builds the IR graph.
 */
@SuppressWarnings("all")
public class BaselineCompiler implements BytecodeParser<BciBlock> {

    public BaselineCompiler(GraphBuilderConfiguration graphBuilderConfig, MetaAccessProvider metaAccess) {
        this.graphBuilderConfig = graphBuilderConfig;
        this.metaAccess = metaAccess;
    }

    private final MetaAccessProvider metaAccess;
    private ConstantPool constantPool;
    private ResolvedJavaMethod method;
    private int entryBCI;
    private ProfilingInfo profilingInfo;
    private BytecodeStream stream;           // the bytecode stream

    private Backend backend;
    private LIRGenerator lirGen;
    private LIRFrameStateBuilder frameState;
    private LIRGenerationResult lirGenRes;

    private BciBlock currentBlock;

    private ValueNode methodSynchronizedObject;
    private ExceptionDispatchBlock unwindBlock;

    private final GraphBuilderConfiguration graphBuilderConfig;
    private BciBlock[] loopHeaders;
    private BytecodeParseHelper<Value> parserHelper;

    /**
     * Meters the number of actual bytecodes parsed.
     */
    public static final DebugMetric BytecodesParsed = Debug.metric("BytecodesParsed");

    protected ResolvedJavaMethod getMethod() {
        return method;
    }

    public CompilationResult generate(ResolvedJavaMethod method, int entryBCI, Backend backend, CompilationResult compilationResult, ResolvedJavaMethod installedCodeOwner,
                    CompilationResultBuilderFactory factory) {
        this.method = method;
        this.entryBCI = entryBCI;
        this.backend = backend;
        profilingInfo = method.getProfilingInfo();
        assert method.getCode() != null : "method must contain bytecodes: " + method;
        this.stream = new BytecodeStream(method.getCode());
        this.constantPool = method.getConstantPool();
        unwindBlock = null;
        methodSynchronizedObject = null;
        TTY.Filter filter = new TTY.Filter(PrintFilter.getValue(), method);

        frameState = new LIRFrameStateBuilder(method);

        // build blocks and LIR instructions
        try {
            build();
        } finally {
            filter.remove();
        }

        // emitCode
        Assumptions assumptions = new Assumptions(OptAssumptions.getValue());
        GraalCompiler.emitCode(backend, assumptions, lirGenRes, compilationResult, installedCodeOwner, factory);

        return compilationResult;
    }

    protected void build() {
        if (PrintProfilingInformation.getValue()) {
            TTY.println("Profiling info for " + MetaUtil.format("%H.%n(%p)", method));
            TTY.println(MetaUtil.indent(MetaUtil.profileToString(profilingInfo, method, CodeUtil.NEW_LINE), "  "));
        }

        try (Indent indent = Debug.logAndIndent("build graph for %s", method)) {

            // compute the block map, setup exception handlers and get the entrypoint(s)
            BciBlockMapping blockMap = BciBlockMapping.create(method);
            loopHeaders = blockMap.loopHeaders;

            // add predecessors
            for (BciBlock block : blockMap.blocks) {
                for (BciBlock successor : block.getSuccessors()) {
                    successor.getPredecessors().add(block);
                }
            }

            if (isSynchronized(method.getModifiers())) {
                throw GraalInternalError.unimplemented("Handle synchronized methods");
            }

            // TODO: clear non live locals

            currentBlock = blockMap.startBlock;
            if (blockMap.startBlock.isLoopHeader) {
                throw GraalInternalError.unimplemented("Handle start block as loop header");
            }

            // add loops ? how do we add looks when we haven't parsed the bytecode?

            // create the control flow graph
            LIRControlFlowGraph cfg = new LIRControlFlowGraph(blockMap.blocks.toArray(new BciBlock[0]), new Loop[0]);

            BlocksToDoubles blockProbabilities = new BlocksToDoubles(blockMap.blocks.size());
            for (BciBlock b : blockMap.blocks) {
                blockProbabilities.put(b, 1);
            }

            // create the LIR
            List<? extends AbstractBlock<?>> linearScanOrder = ComputeBlockOrder.computeLinearScanOrder(blockMap.blocks.size(), blockMap.startBlock, blockProbabilities);
            List<? extends AbstractBlock<?>> codeEmittingOrder = ComputeBlockOrder.computeCodeEmittingOrder(blockMap.blocks.size(), blockMap.startBlock, blockProbabilities);
            LIR lir = new LIR(cfg, linearScanOrder, codeEmittingOrder);

            FrameMap frameMap = backend.newFrameMap();
            TargetDescription target = backend.getTarget();
            CallingConvention cc = CodeUtil.getCallingConvention(backend.getProviders().getCodeCache(), CallingConvention.Type.JavaCallee, method, false);
            this.lirGenRes = backend.newLIRGenerationResult(lir, frameMap, null);
            this.lirGen = backend.newLIRGenerator(cc, lirGenRes);

            try (Scope ds = Debug.scope("BackEnd", lir)) {
                try (Scope s = Debug.scope("LIRGen", lirGen)) {

                    // possibly add all the arguments to slots in the local variable array

                    for (BciBlock block : blockMap.blocks) {
                    }

                    lirGen.beforeRegisterAllocation();
                    Debug.dump(lir, "After LIR generation");
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }

                try (Scope s = Debug.scope("Allocator")) {

                    if (backend.shouldAllocateRegisters()) {
                        new LinearScan(target, lir, frameMap).allocate();
                    }
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    public void setParameter(int i, Variable emitMove) {
        frameState.storeLocal(i, emitMove);
    }

    public void processBlock(BciBlock block) {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }
}
