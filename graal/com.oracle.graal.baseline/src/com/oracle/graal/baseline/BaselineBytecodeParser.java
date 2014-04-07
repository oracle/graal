/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.phases.GraalOptions.*;
import static java.lang.reflect.Modifier.*;

import java.util.*;

import com.oracle.graal.alloc.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.ResolvedJavaType.Representation;
import com.oracle.graal.bytecode.*;
import com.oracle.graal.compiler.alloc.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.*;
import com.oracle.graal.java.*;
import com.oracle.graal.java.BciBlockMapping.BciBlock;
import com.oracle.graal.lir.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.calc.FloatConvertNode.FloatConvert;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.phases.*;

public class BaselineBytecodeParser extends AbstractBytecodeParser<Value, LIRFrameStateBuilder> implements BytecodeParserTool {
    private Backend backend;
    protected LIRGenerator gen;
    private LIRGenerationResult lirGenRes;
    private BytecodeLIRBuilder lirBuilder;

    public BaselineBytecodeParser(MetaAccessProvider metaAccess, ResolvedJavaMethod method, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                    LIRFrameStateBuilder frameState, BytecodeStream stream, ProfilingInfo profilingInfo, ConstantPool constantPool, int entryBCI, Backend backend) {

        super(metaAccess, method, graphBuilderConfig, optimisticOpts, frameState, stream, profilingInfo, constantPool, entryBCI);
        this.backend = backend;
    }

    public LIRGenerationResult getLIRGenerationResult() {
        return lirGenRes;
    }

    @Override
    protected void build() {
        if (PrintProfilingInformation.getValue()) {
            TTY.println("Profiling info for " + MetaUtil.format("%H.%n(%p)", method));
            TTY.println(MetaUtil.indent(MetaUtil.profileToString(profilingInfo, method, CodeUtil.NEW_LINE), "  "));
        }

        try (Indent indent = Debug.logAndIndent("build graph for %s", method)) {

            // compute the block map, setup exception handlers and get the entrypoint(s)
            BciBlockMapping blockMap = BciBlockMapping.create(method);
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

            FrameMap frameMap = backend.newFrameMap(null);
            TargetDescription target = backend.getTarget();
            CallingConvention cc = CodeUtil.getCallingConvention(backend.getProviders().getCodeCache(), CallingConvention.Type.JavaCallee, method, false);
            this.lirGenRes = backend.newLIRGenerationResult(lir, frameMap, null);
            this.gen = backend.newLIRGenerator(cc, lirGenRes);
            this.lirBuilder = backend.newBytecodeLIRBuilder(gen, this);

            try (Scope ds = Debug.scope("BackEnd", lir)) {
                try (Scope s = Debug.scope("LIRGen", gen)) {

                    // possibly add all the arguments to slots in the local variable array

                    for (BciBlock block : blockMap.blocks) {
                        emitBlock(block);
                    }

                    gen.beforeRegisterAllocation();
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

    private void emitBlock(BciBlock b) {
        if (lirGenRes.getLIR().getLIRforBlock(b) == null) {
            for (BciBlock pred : b.getPredecessors()) {
                if (!b.isLoopHeader() || !pred.isLoopEnd()) {
                    emitBlock(pred);
                }
            }
            processBlock(b);
        }
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
        return gen.emitAdd(x, y);
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
    protected void genIf(Value x, Condition cond, Value y) {
        assert currentBlock.getSuccessors().size() == 2;
        BciBlock trueBlock = currentBlock.getSuccessors().get(0);
        BciBlock falseBlock = currentBlock.getSuccessors().get(1);
        if (trueBlock == falseBlock) {
            appendGoto(createTarget(trueBlock, frameState));
            return;
        }

        double probability = profilingInfo.getBranchTakenProbability(bci());
        if (probability < 0) {
            assert probability == -1 : "invalid probability";
            Debug.log("missing probability in %s at bci %d", method, bci());
            probability = 0.5;
        }

        if (!optimisticOpts.removeNeverExecutedCode()) {
            if (probability == 0) {
                probability = 0.0000001;
            } else if (probability == 1) {
                probability = 0.999999;
            }
        }

        LabelRef trueDestination = LabelRef.forSuccessor(lirGenRes.getLIR(), currentBlock, 0);
        LabelRef falseDestination = LabelRef.forSuccessor(lirGenRes.getLIR(), currentBlock, 1);

        gen.emitCompareBranch(x.getKind(), x, y, cond, false, trueDestination, falseDestination, probability);
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
    protected void genReturn(Value x) {
        gen.emitReturn(x);
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
    protected void genIntegerSwitch(Value value, ArrayList<BciBlock> actualSuccessors, int[] keys, double[] keyProbabilities, int[] keySuccessors) {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

    @Override
    protected Value appendConstant(Constant constant) {
        if (gen.canInlineConstant(constant)) {
            return constant;
        }
        return gen.emitMove(constant);
    }

    @Override
    protected Value append(Value v) {
        return v;
    }

    @Override
    protected Value genDeoptimization() {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

    @Override
    protected Value createTarget(BciBlock trueBlock, LIRFrameStateBuilder state) {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

    @Override
    protected void processBlock(BciBlock block) {
        iterateBytecodesForBlock(block);
    }

    @Override
    protected void appendGoto(Value target) {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

    @Override
    protected void iterateBytecodesForBlock(BciBlock block) {
        gen.doBlockStart(block);

        if (block == gen.getResult().getLIR().getControlFlowGraph().getStartBlock()) {
            assert block.getPredecessorCount() == 0;
            lirBuilder.emitPrologue(method);
        } else {
            assert block.getPredecessorCount() > 0;
        }

        int endBCI = stream.endBCI();

        stream.setBCI(block.startBci);
        int bci = block.startBci;
        BytecodesParsed.add(block.endBci - bci);

        while (bci < endBCI) {

            // read the opcode
            int opcode = stream.currentBC();
            // traceState();
            traceInstruction(bci, opcode, bci == block.startBci);

            processBytecode(bci, opcode);

            stream.next();
            bci = stream.currentBCI();

            if (bci < endBCI) {
                if (bci > block.endBci) {
                    if (block.numNormalSuccessors() == 1) {
                        assert !block.getSuccessor(0).isExceptionEntry;
                        // we fell through to the next block, add a goto and break
                        LabelRef label = LabelRef.forSuccessor(lirGenRes.getLIR(), block.getSuccessor(0), 0);
                        gen.emitJump(label);
                    }
                    break;
                }
            }
        }

        assert LIR.verifyBlock(gen.getResult().getLIR(), block);
        gen.doBlockEnd(block);
    }

    public void storeLocal(int i, Value x) {
        frameState.storeLocal(i, x);
    }

}