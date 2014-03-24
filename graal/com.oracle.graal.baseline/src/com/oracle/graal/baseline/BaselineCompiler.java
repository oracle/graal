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
import com.oracle.graal.phases.*;

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
    private BytecodeParser parserHelper;

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
        OptimisticOptimizations optimisticOpts = OptimisticOptimizations.NONE;
        parserHelper = new BytecodeParser(metaAccess, graphBuilderConfig, optimisticOpts, frameState);

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

        // Indent indent = Debug.logAndIndent("build graph for %s", method);

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
        this.lirGen = backend.newLIRGenerator(null, cc, lirGenRes);

        try (Scope ds = Debug.scope("BackEnd", lir)) {
            try (Scope s = Debug.scope("LIRGen", lirGen)) {

                // possibly add all the arguments to slots in the local variable array

                for (BciBlock block : blockMap.blocks) {

                    lirGen.doBlock(block, method, this);
                }
                // indent.outdent();

                lirGen.beforeRegisterAllocation();
                Debug.dump(lir, "After LIR generation");
            } catch (Throwable e) {
                throw Debug.handle(e);
            }

            try (Scope s = Debug.scope("Allocator", lirGen)) {

                if (backend.shouldAllocateRegisters()) {
                    new LinearScan(target, lir, frameMap).allocate();
                }
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    public BytecodeStream stream() {
        return stream;
    }

    public int bci() {
        return stream.currentBCI();
    }

    private void loadLocal(int index, Kind kind) {
        parserHelper.loadLocal(index, kind);
    }

    private void storeLocal(Kind kind, int index) {
        parserHelper.storeLocal(kind, index);
    }

    public void processBlock(BciBlock block) {
        parserHelper.processBlock(block);
    }

    private void createExceptionDispatch(ExceptionDispatchBlock block) {
        throw GraalInternalError.unimplemented();
    }

    private void traceInstruction(int bci, int opcode, boolean blockStart) {
        if (Debug.isLogEnabled()) {
            StringBuilder sb = new StringBuilder(40);
            sb.append(blockStart ? '+' : '|');
            if (bci < 10) {
                sb.append("  ");
            } else if (bci < 100) {
                sb.append(' ');
            }
            sb.append(bci).append(": ").append(Bytecodes.nameOf(opcode));
            for (int i = bci + 1; i < stream.nextBCI(); ++i) {
                sb.append(' ').append(stream.readUByte(i));
            }
            if (!currentBlock.jsrScope.isEmpty()) {
                sb.append(' ').append(currentBlock.jsrScope);
            }
            Debug.log(sb.toString());
        }
    }

    private void genArrayLength() {
        throw GraalInternalError.unimplemented();
    }

    private void genReturn(Value x) {
        // frameState.setRethrowException(false);
        frameState.clearStack();
// if (graphBuilderConfig.eagerInfopointMode()) {
// append(new InfopointNode(InfopointReason.METHOD_END, frameState.create(bci())));
// }

// synchronizedEpilogue(FrameState.AFTER_BCI, x);
// if (frameState.lockDepth() != 0) {
// throw new BailoutException("unbalanced monitors");
// }

        lirGen.visitReturn(x);
    }

    public void setParameter(int i, Variable emitMove) {
        frameState.storeLocal(i, emitMove);
    }

    private class BytecodeParser extends BytecodeParseHelper<Value> {

        public BytecodeParser(MetaAccessProvider metaAccess, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, AbstractFrameStateBuilder<Value> frameState) {
            super(metaAccess, graphBuilderConfig, optimisticOpts, frameState);
        }

        @Override
        protected void handleUnresolvedLoadConstant(JavaType type) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void handleUnresolvedCheckCast(JavaType type, Value object) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void handleUnresolvedInstanceOf(JavaType type, Value object) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void handleUnresolvedNewInstance(JavaType type) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void handleUnresolvedNewObjectArray(JavaType type, Value length) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void handleUnresolvedNewMultiArray(JavaType type, List<Value> dims) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void handleUnresolvedLoadField(JavaField field, Value receiver) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void handleUnresolvedStoreField(JavaField field, Value value, Value receiver) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void handleUnresolvedExceptionType(Representation representation, JavaType type) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genLoadIndexed(Value index, Value array, Kind kind) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genStoreIndexed(Value array, Value index, Kind kind, Value value) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genIntegerAdd(Kind kind, Value x, Value y) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genIntegerSub(Kind kind, Value x, Value y) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genIntegerMul(Kind kind, Value x, Value y) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genFloatAdd(Kind kind, Value x, Value y, boolean isStrictFP) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genFloatSub(Kind kind, Value x, Value y, boolean isStrictFP) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genFloatMul(Kind kind, Value x, Value y, boolean isStrictFP) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genFloatDiv(Kind kind, Value x, Value y, boolean isStrictFP) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genFloatRem(Kind kind, Value x, Value y, boolean isStrictFP) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genIntegerDiv(Kind kind, Value x, Value y) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genIntegerRem(Kind kind, Value x, Value y) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genNegateOp(Value x) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genLeftShift(Kind kind, Value x, Value y) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genRightShift(Kind kind, Value x, Value y) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genUnsignedRightShift(Kind kind, Value x, Value y) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genAnd(Kind kind, Value x, Value y) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genOr(Kind kind, Value x, Value y) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genXor(Kind kind, Value x, Value y) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genNormalizeCompare(Value x, Value y, boolean isUnorderedLess) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genFloatConvert(FloatConvert op, Value input) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genNarrow(Value input, int bitCount) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genSignExtend(Value input, int bitCount) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genZeroExtend(Value input, int bitCount) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genObjectEquals(Value x, Value y) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genIntegerEquals(Value x, Value y) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genIntegerLessThan(Value x, Value y) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genUnique(Value x) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genIf(Value condition, Value falseSuccessor, Value trueSuccessor, double d) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void genThrow() {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genCheckCast(ResolvedJavaType type, Value object, JavaTypeProfile profileForTypeCheck, boolean b) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genInstanceOf(ResolvedJavaType type, Value object, JavaTypeProfile profileForTypeCheck) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genConditional(Value x) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value createNewInstance(ResolvedJavaType type, boolean fillContents) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value createNewArray(ResolvedJavaType elementType, Value length, boolean fillContents) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value createNewMultiArray(ResolvedJavaType type, List<Value> dims) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genLoadField(Value receiver, ResolvedJavaField field) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void emitNullCheck(Value receiver) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void emitBoundsCheck(Value index, Value length) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genArrayLength(Value x) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genStoreField(Value receiver, ResolvedJavaField field, Value value) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void genInvokeStatic(JavaMethod target) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void genInvokeInterface(JavaMethod target) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void genInvokeDynamic(JavaMethod target) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void genInvokeVirtual(JavaMethod target) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void genInvokeSpecial(JavaMethod target) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void genInvokeIndirect(InvokeKind invokeKind, ResolvedJavaMethod target, Value[] args) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void genReturn(Value x) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genMonitorEnter(Value x) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genMonitorExit(Value x, Value returnValue) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void genJsr(int dest) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void genRet(int localIndex) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void setBlockSuccessor(Value switchNode, int i, Value createBlockTarget) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value genIntegerSwitch(Value value, int size, int[] keys, double[] keyProbabilities, int[] keySuccessors) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value appendConstant(Constant constant) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value append(Value v) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value createTarget(BciBlock trueBlock, AbstractFrameStateBuilder<Value> state) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected Value createBlockTarget(double probability, BciBlock bciBlock, AbstractFrameStateBuilder<Value> stateAfter) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void processBlock(BciBlock block) {
            Indent indent = Debug.logAndIndent("Parsing block %s  firstInstruction: %s  loopHeader: %b", block, block.firstInstruction, block.isLoopHeader);
            currentBlock = block;
            iterateBytecodesForBlock(block);
            indent.outdent();
        }

        @Override
        protected void appendGoto(Value target) {
            // TODO Auto-generated method stub
            throw GraalInternalError.unimplemented("Auto-generated method stub");
        }

        @Override
        protected void iterateBytecodesForBlock(BciBlock block) {

            int endBCI = stream.endBCI();

            stream.setBCI(block.startBci);
            int bci = block.startBci;
            BytecodesParsed.add(block.endBci - bci);

            while (bci < endBCI) {

                // read the opcode
                int opcode = stream.currentBC();
                traceInstruction(bci, opcode, bci == block.startBci);
                if (bci == entryBCI) {
                    throw GraalInternalError.unimplemented();
                }
                processBytecode(bci, opcode);

                stream.next();
                bci = stream.currentBCI();
            }
        }
    }

}
