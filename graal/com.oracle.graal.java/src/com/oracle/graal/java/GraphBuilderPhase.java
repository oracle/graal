/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java;

import static com.oracle.graal.java.bytecode.Bytecodes.*;
import static java.lang.reflect.Modifier.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.compiler.util.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.java.BciBlockMapping.Block;
import com.oracle.graal.java.BciBlockMapping.ExceptionDispatchBlock;
import com.oracle.graal.java.bytecode.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.cri.ri.RiType.Representation;
import com.oracle.max.cri.ri.RiTypeProfile.ProfiledType;
import com.oracle.max.criutils.*;

/**
 * The {@code GraphBuilder} class parses the bytecode of a method and builds the IR graph.
 */
public final class GraphBuilderPhase extends Phase {

    /**
     * The minimum value to which {@link GraalOptions#TraceBytecodeParserLevel} must be set to trace
     * the bytecode instructions as they are parsed.
     */
    public static final int TRACELEVEL_INSTRUCTIONS = 1;

    /**
     * The minimum value to which {@link GraalOptions#TraceBytecodeParserLevel} must be set to trace
     * the frame state before each bytecode instruction as it is parsed.
     */
    public static final int TRACELEVEL_STATE = 2;

    private StructuredGraph currentGraph;

    private final RiRuntime runtime;
    private RiConstantPool constantPool;
    private RiResolvedMethod method;
    private RiProfilingInfo profilingInfo;

    private BytecodeStream stream;           // the bytecode stream
    private final LogStream log;

    private FrameStateBuilder frameState;          // the current execution state
    private Block currentBlock;

    private ValueNode methodSynchronizedObject;
    private ExceptionDispatchBlock unwindBlock;
    private Block returnBlock;

    private FixedWithNextNode lastInstr;                 // the last instruction added

    private final GraphBuilderConfiguration graphBuilderConfig;
    private final OptimisticOptimizations optimisticOpts;

    private long graphId;

    /**
     * Node that marks the begin of block during bytecode parsing.  When a block is identified the first
     * time as a jump target, the placeholder is created and used as the successor for the jump.  When the
     * block is seen the second time, a MergeNode is created to correctly merge the now two different
     * predecessor states.
     */
    private static class BlockPlaceholderNode extends FixedWithNextNode implements Node.IterableNodeType {
        public BlockPlaceholderNode() {
            super(StampFactory.forVoid());
        }
    }

    private Block[] loopHeaders;

    public GraphBuilderPhase(RiRuntime runtime, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts) {
        this.graphBuilderConfig = graphBuilderConfig;
        this.optimisticOpts = optimisticOpts;
        this.runtime = runtime;
        this.log = GraalOptions.TraceBytecodeParserLevel > 0 ? new LogStream(TTY.out()) : null;
    }

    @Override
    protected void run(StructuredGraph graph) {
        method = graph.method();
        graphId = graph.graphId();
        profilingInfo = method.profilingInfo();
        assert method.code() != null : "method must contain bytecodes: " + method;
        this.stream = new BytecodeStream(method.code());
        this.constantPool = method.getConstantPool();
        unwindBlock = null;
        returnBlock = null;
        methodSynchronizedObject = null;
        this.currentGraph = graph;
        this.frameState = new FrameStateBuilder(method, graph, graphBuilderConfig.eagerResolving());
        build();
    }

    @Override
    protected String getDetailedName() {
        return getName() + " " + CiUtil.format("%H.%n(%p):%r", method);
    }

    private BciBlockMapping createBlockMap() {
        BciBlockMapping map = new BciBlockMapping(method);
        map.build();
        Debug.dump(map, CiUtil.format("After block building %f %R %H.%n(%P)", method));

        return map;
    }

    private void build() {
        if (log != null) {
            log.println();
            log.println("Compiling " + method);
        }

        if (GraalOptions.PrintProfilingInformation) {
            TTY.println("Profiling info for " + method);
            TTY.println(CiUtil.indent(CiUtil.profileToString(profilingInfo, method, CiUtil.NEW_LINE), "  "));
        }

        // compute the block map, setup exception handlers and get the entrypoint(s)
        BciBlockMapping blockMap = createBlockMap();
        loopHeaders = blockMap.loopHeaders;

        lastInstr = currentGraph.start();
        if (isSynchronized(method.accessFlags())) {
            // add a monitor enter to the start block
            currentGraph.start().setStateAfter(frameState.create(FrameState.BEFORE_BCI));
            methodSynchronizedObject = synchronizedObject(frameState, method);
            lastInstr = genMonitorEnter(methodSynchronizedObject);
        }
        frameState.clearNonLiveLocals(blockMap.startBlock.localsLiveIn);

        // finish the start block
        ((StateSplit) lastInstr).setStateAfter(frameState.create(0));
        if (blockMap.startBlock.isLoopHeader) {
            appendGoto(createTarget(blockMap.startBlock, frameState));
        } else {
            blockMap.startBlock.firstInstruction = lastInstr;
            blockMap.startBlock.entryState = frameState;
        }

        for (Block block : blockMap.blocks) {
            processBlock(block);
        }
        processBlock(returnBlock);
        processBlock(unwindBlock);

        Debug.dump(currentGraph, "After bytecode parsing");

        connectLoopEndToBegin();

        // remove Placeholders
        for (BlockPlaceholderNode n : currentGraph.getNodes(BlockPlaceholderNode.class)) {
            currentGraph.removeFixed(n);
        }

        // remove dead FrameStates
        for (Node n : currentGraph.getNodes(FrameState.class)) {
            if (n.usages().size() == 0 && n.predecessor() == null) {
                n.safeDelete();
            }
        }
    }

    private Block unwindBlock(int bci) {
        if (unwindBlock == null) {
            unwindBlock = new ExceptionDispatchBlock();
            unwindBlock.startBci = -1;
            unwindBlock.endBci = -1;
            unwindBlock.deoptBci = bci;
            unwindBlock.blockID = Integer.MAX_VALUE;
        }
        return unwindBlock;
    }

    private Block returnBlock(int bci) {
        if (returnBlock == null) {
            returnBlock = new Block();
            returnBlock.startBci = bci;
            returnBlock.endBci = bci;
            returnBlock.blockID = Integer.MAX_VALUE;
        }
        return returnBlock;
    }

    public BytecodeStream stream() {
        return stream;
    }

    public int bci() {
        return stream.currentBCI();
    }

    private void loadLocal(int index, CiKind kind) {
        frameState.push(kind, frameState.loadLocal(index));
    }

    private void storeLocal(CiKind kind, int index) {
        frameState.storeLocal(index, frameState.pop(kind));
    }

    public static boolean covers(RiExceptionHandler handler, int bci) {
        return handler.startBCI() <= bci && bci < handler.endBCI();
    }

    public static boolean isCatchAll(RiExceptionHandler handler) {
        return handler.catchTypeCPI() == 0;
    }

    private DispatchBeginNode handleException(ValueNode exceptionObject, int bci) {
        assert bci == FrameState.BEFORE_BCI || bci == bci() : "invalid bci";
        Debug.log("Creating exception dispatch edges at %d, exception object=%s, exception seen=%s", bci, exceptionObject, profilingInfo.getExceptionSeen(bci));

        Block dispatchBlock = currentBlock.exceptionDispatchBlock();
        // The exception dispatch block is always for the last bytecode of a block, so if we are not at the endBci yet,
        // there is no exception handler for this bci and we can unwind immediately.
        if (bci != currentBlock.endBci || dispatchBlock == null) {
            dispatchBlock = unwindBlock(bci);
        }

        FrameStateBuilder dispatchState = frameState.copy();
        dispatchState.clearStack();

        DispatchBeginNode dispatchBegin = currentGraph.add(new DispatchBeginNode());
        dispatchBegin.setStateAfter(dispatchState.create(bci));

        if (exceptionObject == null) {
            ExceptionObjectNode newExceptionObject = currentGraph.add(new ExceptionObjectNode(runtime));
            dispatchState.apush(newExceptionObject);
            dispatchState.setRethrowException(true);
            newExceptionObject.setStateAfter(dispatchState.create(bci));

            FixedNode target = createTarget(dispatchBlock, dispatchState);
            dispatchBegin.setNext(newExceptionObject);
            newExceptionObject.setNext(target);
        } else {
            dispatchState.apush(exceptionObject);
            dispatchState.setRethrowException(true);

            FixedNode target = createTarget(dispatchBlock, dispatchState);
            dispatchBegin.setNext(target);
        }
        return dispatchBegin;
    }

    private void genLoadConstant(int cpi, int opcode) {
        Object con = lookupConstant(cpi, opcode);

        if (con instanceof RiType) {
            // this is a load of class constant which might be unresolved
            RiType riType = (RiType) con;
            if (riType instanceof RiResolvedType) {
                frameState.push(CiKind.Object, append(ConstantNode.forCiConstant(((RiResolvedType) riType).getEncoding(Representation.JavaClass), runtime, currentGraph)));
            } else {
                append(currentGraph.add(new DeoptimizeNode(RiDeoptAction.InvalidateRecompile, RiDeoptReason.Unresolved, graphId)));
                frameState.push(CiKind.Object, append(ConstantNode.forObject(null, runtime, currentGraph)));
            }
        } else if (con instanceof CiConstant) {
            CiConstant constant = (CiConstant) con;
            frameState.push(constant.kind.stackKind(), appendConstant(constant));
        } else {
            throw new Error("lookupConstant returned an object of incorrect type");
        }
    }

    private void genLoadIndexed(CiKind kind) {
        emitExplicitExceptions(frameState.peek(1), frameState.peek(0));

        ValueNode index = frameState.ipop();
        ValueNode array = frameState.apop();
        ValueNode v = append(currentGraph.add(new LoadIndexedNode(array, index, kind, graphId)));
        frameState.push(kind.stackKind(), v);
    }

    private void genStoreIndexed(CiKind kind) {
        emitExplicitExceptions(frameState.peek(2), frameState.peek(1));

        ValueNode value = frameState.pop(kind.stackKind());
        ValueNode index = frameState.ipop();
        ValueNode array = frameState.apop();
        StoreIndexedNode result = currentGraph.add(new StoreIndexedNode(array, index, kind, value, graphId));
        append(result);
    }

    private void stackOp(int opcode) {
        switch (opcode) {
            case POP: {
                frameState.xpop();
                break;
            }
            case POP2: {
                frameState.xpop();
                frameState.xpop();
                break;
            }
            case DUP: {
                ValueNode w = frameState.xpop();
                frameState.xpush(w);
                frameState.xpush(w);
                break;
            }
            case DUP_X1: {
                ValueNode w1 = frameState.xpop();
                ValueNode w2 = frameState.xpop();
                frameState.xpush(w1);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case DUP_X2: {
                ValueNode w1 = frameState.xpop();
                ValueNode w2 = frameState.xpop();
                ValueNode w3 = frameState.xpop();
                frameState.xpush(w1);
                frameState.xpush(w3);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case DUP2: {
                ValueNode w1 = frameState.xpop();
                ValueNode w2 = frameState.xpop();
                frameState.xpush(w2);
                frameState.xpush(w1);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case DUP2_X1: {
                ValueNode w1 = frameState.xpop();
                ValueNode w2 = frameState.xpop();
                ValueNode w3 = frameState.xpop();
                frameState.xpush(w2);
                frameState.xpush(w1);
                frameState.xpush(w3);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case DUP2_X2: {
                ValueNode w1 = frameState.xpop();
                ValueNode w2 = frameState.xpop();
                ValueNode w3 = frameState.xpop();
                ValueNode w4 = frameState.xpop();
                frameState.xpush(w2);
                frameState.xpush(w1);
                frameState.xpush(w4);
                frameState.xpush(w3);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case SWAP: {
                ValueNode w1 = frameState.xpop();
                ValueNode w2 = frameState.xpop();
                frameState.xpush(w1);
                frameState.xpush(w2);
                break;
            }
            default:
                throw GraalInternalError.shouldNotReachHere();
        }

    }

    private void genArithmeticOp(CiKind result, int opcode, boolean canTrap) {
        ValueNode y = frameState.pop(result);
        ValueNode x = frameState.pop(result);
        boolean isStrictFP = isStrict(method.accessFlags());
        ArithmeticNode v;
        switch(opcode){
            case IADD:
            case LADD: v = new IntegerAddNode(result, x, y); break;
            case FADD:
            case DADD: v = new FloatAddNode(result, x, y, isStrictFP); break;
            case ISUB:
            case LSUB: v = new IntegerSubNode(result, x, y); break;
            case FSUB:
            case DSUB: v = new FloatSubNode(result, x, y, isStrictFP); break;
            case IMUL:
            case LMUL: v = new IntegerMulNode(result, x, y); break;
            case FMUL:
            case DMUL: v = new FloatMulNode(result, x, y, isStrictFP); break;
            case IDIV:
            case LDIV: v = new IntegerDivNode(result, x, y); break;
            case FDIV:
            case DDIV: v = new FloatDivNode(result, x, y, isStrictFP); break;
            case IREM:
            case LREM: v = new IntegerRemNode(result, x, y); break;
            case FREM:
            case DREM: v = new FloatRemNode(result, x, y, isStrictFP); break;
            default:
                throw new GraalInternalError("should not reach");
        }
        ValueNode result1 = append(currentGraph.unique(v));
        if (canTrap) {
            append(currentGraph.add(new ValueAnchorNode(result1)));
        }
        frameState.push(result, result1);
    }

    private void genNegateOp(CiKind kind) {
        frameState.push(kind, append(currentGraph.unique(new NegateNode(frameState.pop(kind)))));
    }

    private void genShiftOp(CiKind kind, int opcode) {
        ValueNode s = frameState.ipop();
        ValueNode x = frameState.pop(kind);
        ShiftNode v;
        switch(opcode){
            case ISHL:
            case LSHL: v = new LeftShiftNode(kind, x, s); break;
            case ISHR:
            case LSHR: v = new RightShiftNode(kind, x, s); break;
            case IUSHR:
            case LUSHR: v = new UnsignedRightShiftNode(kind, x, s); break;
            default:
                throw new GraalInternalError("should not reach");
        }
        frameState.push(kind, append(currentGraph.unique(v)));
    }

    private void genLogicOp(CiKind kind, int opcode) {
        ValueNode y = frameState.pop(kind);
        ValueNode x = frameState.pop(kind);
        LogicNode v;
        switch(opcode){
            case IAND:
            case LAND: v = new AndNode(kind, x, y); break;
            case IOR:
            case LOR: v = new OrNode(kind, x, y); break;
            case IXOR:
            case LXOR: v = new XorNode(kind, x, y); break;
            default:
                throw new GraalInternalError("should not reach");
        }
        frameState.push(kind, append(currentGraph.unique(v)));
    }

    private void genCompareOp(CiKind kind, boolean isUnorderedLess) {
        ValueNode y = frameState.pop(kind);
        ValueNode x = frameState.pop(kind);
        frameState.ipush(append(currentGraph.unique(new NormalizeCompareNode(x, y, isUnorderedLess))));
    }

    private void genConvert(ConvertNode.Op opcode) {
        ValueNode input = frameState.pop(opcode.from.stackKind());
        frameState.push(opcode.to.stackKind(), append(currentGraph.unique(new ConvertNode(opcode, input))));
    }

    private void genIncrement() {
        int index = stream().readLocalIndex();
        int delta = stream().readIncrement();
        ValueNode x = frameState.loadLocal(index);
        ValueNode y = append(ConstantNode.forInt(delta, currentGraph));
        frameState.storeLocal(index, append(currentGraph.unique(new IntegerAddNode(CiKind.Int, x, y))));
    }

    private void genGoto() {
        double probability = profilingInfo.getBranchTakenProbability(bci());
        if (probability < 0) {
            probability = 1;
        }
        appendGoto(createTarget(probability, currentBlock.successors.get(0), frameState));
        assert currentBlock.numNormalSuccessors() == 1;
    }

    private void ifNode(ValueNode x, Condition cond, ValueNode y) {
        assert !x.isDeleted() && !y.isDeleted();
        assert currentBlock.numNormalSuccessors() == 2;
        Block trueBlock = currentBlock.successors.get(0);
        Block falseBlock = currentBlock.successors.get(1);
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

        // the mirroring and negation operations get the condition into canonical form
        boolean mirror = cond.canonicalMirror();
        boolean negate = cond.canonicalNegate();

        ValueNode a = mirror ? y : x;
        ValueNode b = mirror ? x : y;

        CompareNode condition;
        assert !a.kind().isFloatOrDouble();
        if (cond == Condition.EQ || cond == Condition.NE) {
            if (a.kind() == CiKind.Object) {
                condition = new ObjectEqualsNode(a, b);
            } else {
                condition = new IntegerEqualsNode(a, b);
            }
        } else {
            assert a.kind() != CiKind.Object && !cond.isUnsigned();
            condition = new IntegerLessThanNode(a, b);
        }
        condition = currentGraph.unique(condition);

        BeginNode trueSuccessor = createBlockTarget(probability, trueBlock, frameState);
        BeginNode falseSuccessor = createBlockTarget(1 - probability, falseBlock, frameState);

        IfNode ifNode = negate ? new IfNode(condition, falseSuccessor, trueSuccessor, 1 - probability) : new IfNode(condition, trueSuccessor, falseSuccessor, probability);
        append(currentGraph.add(ifNode));
    }

    private void genIfZero(Condition cond) {
        ValueNode y = appendConstant(CiConstant.INT_0);
        ValueNode x = frameState.ipop();
        ifNode(x, cond, y);
    }

    private void genIfNull(Condition cond) {
        ValueNode y = appendConstant(CiConstant.NULL_OBJECT);
        ValueNode x = frameState.apop();
        ifNode(x, cond, y);
    }

    private void genIfSame(CiKind kind, Condition cond) {
        ValueNode y = frameState.pop(kind);
        ValueNode x = frameState.pop(kind);
        assert !x.isDeleted() && !y.isDeleted();
        ifNode(x, cond, y);
    }

    private void genThrow() {
        ValueNode exception = frameState.apop();
        FixedGuardNode node = currentGraph.add(new FixedGuardNode(currentGraph.unique(new IsNullNode(exception)), RiDeoptReason.NullCheckException, RiDeoptAction.InvalidateReprofile, true, graphId));
        append(node);
        append(handleException(exception, bci()));
    }

    private RiType lookupType(int cpi, int bytecode) {
        eagerResolvingForSnippets(cpi, bytecode);
        RiType result = constantPool.lookupType(cpi, bytecode);
        assert !graphBuilderConfig.eagerResolvingForSnippets() || result instanceof RiResolvedType;
        return result;
    }

    private RiMethod lookupMethod(int cpi, int opcode) {
        eagerResolvingForSnippets(cpi, opcode);
        RiMethod result = constantPool.lookupMethod(cpi, opcode);
        assert !graphBuilderConfig.eagerResolvingForSnippets() || ((result instanceof RiResolvedMethod) && ((RiResolvedMethod) result).holder().isInitialized());
        return result;
    }

    private RiField lookupField(int cpi, int opcode) {
        eagerResolvingForSnippets(cpi, opcode);
        RiField result = constantPool.lookupField(cpi, opcode);
        assert !graphBuilderConfig.eagerResolvingForSnippets() || (result instanceof RiResolvedField && ((RiResolvedField) result).holder().isInitialized());
        return result;
    }

    private Object lookupConstant(int cpi, int opcode) {
        eagerResolving(cpi, opcode);
        Object result = constantPool.lookupConstant(cpi);
        assert !graphBuilderConfig.eagerResolving() || !(result instanceof RiType) || (result instanceof RiResolvedType);
        return result;
    }

    private void eagerResolving(int cpi, int bytecode) {
        if (graphBuilderConfig.eagerResolving()) {
            constantPool.loadReferencedType(cpi, bytecode);
        }
    }

    private void eagerResolvingForSnippets(int cpi, int bytecode) {
        if (graphBuilderConfig.eagerResolvingForSnippets()) {
            constantPool.loadReferencedType(cpi, bytecode);
        }
    }

    private RiTypeProfile getProfileForTypeCheck(RiResolvedType type) {
        if (!optimisticOpts.useTypeCheckHints() || TypeCheckHints.isFinalClass(type)) {
            return null;
        } else {
            RiResolvedType uniqueSubtype = type.uniqueConcreteSubtype();
            if (uniqueSubtype != null) {
                return new RiTypeProfile(0.0D, new ProfiledType(uniqueSubtype, 1.0D));
            } else {
                return profilingInfo.getTypeProfile(bci());
            }
        }
    }

    private void genCheckCast() {
        int cpi = stream().readCPI();
        RiType type = lookupType(cpi, CHECKCAST);
        boolean initialized = type instanceof RiResolvedType;
        if (initialized) {
            ConstantNode typeInstruction = genTypeOrDeopt(RiType.Representation.ObjectHub, type, true);
            ValueNode object = frameState.apop();
            CheckCastNode checkCast = currentGraph.add(new CheckCastNode(typeInstruction, (RiResolvedType) type, object, getProfileForTypeCheck((RiResolvedType) type)));
            append(checkCast);
            frameState.apush(checkCast);
        } else {
            ValueNode object = frameState.apop();
            append(currentGraph.add(new FixedGuardNode(currentGraph.unique(new IsNullNode(object)), RiDeoptReason.Unresolved, RiDeoptAction.InvalidateRecompile, graphId)));
            frameState.apush(appendConstant(CiConstant.NULL_OBJECT));
        }
    }

    private void genInstanceOf() {
        int cpi = stream().readCPI();
        RiType type = lookupType(cpi, INSTANCEOF);
        ValueNode object = frameState.apop();
        if (type instanceof RiResolvedType) {
            RiResolvedType resolvedType = (RiResolvedType) type;
            ConstantNode hub = appendConstant(resolvedType.getEncoding(RiType.Representation.ObjectHub));
            InstanceOfNode instanceOfNode = new InstanceOfNode(hub, (RiResolvedType) type, object, getProfileForTypeCheck(resolvedType));
            frameState.ipush(append(MaterializeNode.create(currentGraph.unique(instanceOfNode), currentGraph)));
        } else {
            BlockPlaceholderNode successor = currentGraph.add(new BlockPlaceholderNode());
            DeoptimizeNode deopt = currentGraph.add(new DeoptimizeNode(RiDeoptAction.InvalidateRecompile, RiDeoptReason.Unresolved, graphId));
            IfNode ifNode = currentGraph.add(new IfNode(currentGraph.unique(new IsNullNode(object)), successor, deopt, 0));
            append(ifNode);
            lastInstr = successor;
            frameState.ipush(appendConstant(CiConstant.INT_0));
        }
    }

    void genNewInstance(int cpi) {
        RiType type = lookupType(cpi, NEW);
        if (type instanceof RiResolvedType) {
            NewInstanceNode n = currentGraph.add(new NewInstanceNode((RiResolvedType) type));
            frameState.apush(append(n));
        } else {
            append(currentGraph.add(new DeoptimizeNode(RiDeoptAction.InvalidateRecompile, RiDeoptReason.Unresolved, graphId)));
            frameState.apush(appendConstant(CiConstant.NULL_OBJECT));
        }
    }

    /**
     * Gets the kind of array elements for the array type code that appears
     * in a {@link Bytecodes#NEWARRAY} bytecode.
     * @param code the array type code
     * @return the kind from the array type code
     */
    public static CiKind arrayTypeCodeToKind(int code) {
        // Checkstyle: stop
        switch (code) {
            case 4:  return CiKind.Boolean;
            case 5:  return CiKind.Char;
            case 6:  return CiKind.Float;
            case 7:  return CiKind.Double;
            case 8:  return CiKind.Byte;
            case 9:  return CiKind.Short;
            case 10: return CiKind.Int;
            case 11: return CiKind.Long;
            default: throw new IllegalArgumentException("unknown array type code: " + code);
        }
        // Checkstyle: resume
    }

    private void genNewTypeArray(int typeCode) {
        CiKind kind = arrayTypeCodeToKind(typeCode);
        RiResolvedType elementType = runtime.asRiType(kind);
        NewTypeArrayNode nta = currentGraph.add(new NewTypeArrayNode(frameState.ipop(), elementType));
        frameState.apush(append(nta));
    }

    private void genNewObjectArray(int cpi) {
        RiType type = lookupType(cpi, ANEWARRAY);
        ValueNode length = frameState.ipop();
        if (type instanceof RiResolvedType) {
            NewArrayNode n = currentGraph.add(new NewObjectArrayNode((RiResolvedType) type, length));
            frameState.apush(append(n));
        } else {
            append(currentGraph.add(new DeoptimizeNode(RiDeoptAction.InvalidateRecompile, RiDeoptReason.Unresolved, graphId)));
            frameState.apush(appendConstant(CiConstant.NULL_OBJECT));
        }

    }

    private void genNewMultiArray(int cpi) {
        RiType type = lookupType(cpi, MULTIANEWARRAY);
        int rank = stream().readUByte(bci() + 3);
        ValueNode[] dims = new ValueNode[rank];
        for (int i = rank - 1; i >= 0; i--) {
            dims[i] = frameState.ipop();
        }
        if (type instanceof RiResolvedType) {
            FixedWithNextNode n = currentGraph.add(new NewMultiArrayNode((RiResolvedType) type, dims));
            frameState.apush(append(n));
        } else {
            append(currentGraph.add(new DeoptimizeNode(RiDeoptAction.InvalidateRecompile, RiDeoptReason.Unresolved, graphId)));
            frameState.apush(appendConstant(CiConstant.NULL_OBJECT));
        }
    }

    private void genGetField(RiField field) {
        emitExplicitExceptions(frameState.peek(0), null);

        CiKind kind = field.kind(false);
        ValueNode receiver = frameState.apop();
        if ((field instanceof RiResolvedField) && ((RiResolvedField) field).holder().isInitialized()) {
            LoadFieldNode load = currentGraph.add(new LoadFieldNode(receiver, (RiResolvedField) field, graphId));
            appendOptimizedLoadField(kind, load);
        } else {
            append(currentGraph.add(new DeoptimizeNode(RiDeoptAction.InvalidateRecompile, RiDeoptReason.Unresolved, graphId)));
            frameState.push(kind.stackKind(), append(ConstantNode.defaultForKind(kind, currentGraph)));
        }
    }

    public static class ExceptionInfo {

        public final FixedWithNextNode exceptionEdge;
        public final ValueNode exception;

        public ExceptionInfo(FixedWithNextNode exceptionEdge, ValueNode exception) {
            this.exceptionEdge = exceptionEdge;
            this.exception = exception;
        }
    }

    private void emitNullCheck(ValueNode receiver) {
        BlockPlaceholderNode trueSucc = currentGraph.add(new BlockPlaceholderNode());
        BlockPlaceholderNode falseSucc = currentGraph.add(new BlockPlaceholderNode());
        IfNode ifNode = currentGraph.add(new IfNode(currentGraph.unique(new IsNullNode(receiver)), trueSucc, falseSucc, 1));

        append(ifNode);
        lastInstr = falseSucc;

        if (GraalOptions.OmitHotExceptionStacktrace) {
            ValueNode exception = ConstantNode.forObject(new NullPointerException(), runtime, currentGraph);
            trueSucc.setNext(handleException(exception, bci()));
        } else {
            RuntimeCallNode call = currentGraph.add(new RuntimeCallNode(CiRuntimeCall.CreateNullPointerException));
            call.setStateAfter(frameState.create(bci()));
            trueSucc.setNext(call);
            call.setNext(handleException(call, bci()));
        }
    }

    private void emitBoundsCheck(ValueNode index, ValueNode length) {
        BlockPlaceholderNode trueSucc = currentGraph.add(new BlockPlaceholderNode());
        BlockPlaceholderNode falseSucc = currentGraph.add(new BlockPlaceholderNode());
        IfNode ifNode = currentGraph.add(new IfNode(currentGraph.unique(new IntegerBelowThanNode(index, length)), trueSucc, falseSucc, 1));

        append(ifNode);
        lastInstr = trueSucc;

        if (GraalOptions.OmitHotExceptionStacktrace) {
            ValueNode exception = ConstantNode.forObject(new ArrayIndexOutOfBoundsException(), runtime, currentGraph);
            falseSucc.setNext(handleException(exception, bci()));
        } else {
            RuntimeCallNode call = currentGraph.add(new RuntimeCallNode(CiRuntimeCall.CreateOutOfBoundsException, new ValueNode[] {index}));
            call.setStateAfter(frameState.create(bci()));
            falseSucc.setNext(call);
            call.setNext(handleException(call, bci()));
        }
    }

    private void emitExplicitExceptions(ValueNode receiver, ValueNode outOfBoundsIndex) {
        assert receiver != null;
        if (!GraalOptions.AllowExplicitExceptionChecks || (optimisticOpts.useExceptionProbability() && profilingInfo.getExceptionSeen(bci()) == RiExceptionSeen.FALSE)) {
            return;
        }

        emitNullCheck(receiver);
        if (outOfBoundsIndex != null) {
            ValueNode length = append(currentGraph.add(new ArrayLengthNode(receiver)));
            emitBoundsCheck(outOfBoundsIndex, length);
        }
        Debug.metric("ExplicitExceptions").increment();
    }

    private void genPutField(RiField field) {
        emitExplicitExceptions(frameState.peek(1), null);

        ValueNode value = frameState.pop(field.kind(false).stackKind());
        ValueNode receiver = frameState.apop();
        if (field instanceof RiResolvedField && ((RiResolvedField) field).holder().isInitialized()) {
            StoreFieldNode store = currentGraph.add(new StoreFieldNode(receiver, (RiResolvedField) field, value, graphId));
            appendOptimizedStoreField(store);
        } else {
            append(currentGraph.add(new DeoptimizeNode(RiDeoptAction.InvalidateRecompile, RiDeoptReason.Unresolved, graphId)));
        }
    }

    private void genGetStatic(RiField field) {
        RiType holder = field.holder();
        boolean isInitialized = (field instanceof RiResolvedField) && ((RiResolvedType) holder).isInitialized();
        CiConstant constantValue = null;
        if (isInitialized) {
            constantValue = ((RiResolvedField) field).constantValue(null);
        }
        if (constantValue != null) {
            frameState.push(constantValue.kind.stackKind(), appendConstant(constantValue));
        } else {
            ValueNode container = genTypeOrDeopt(RiType.Representation.StaticFields, holder, isInitialized);
            CiKind kind = field.kind(false);
            if (container != null) {
                LoadFieldNode load = currentGraph.add(new LoadFieldNode(container, (RiResolvedField) field, graphId));
                appendOptimizedLoadField(kind, load);
            } else {
                // deopt will be generated by genTypeOrDeopt, not needed here
                frameState.push(kind.stackKind(), append(ConstantNode.defaultForKind(kind, currentGraph)));
            }
        }
    }

    private void genPutStatic(RiField field) {
        RiType holder = field.holder();
        ValueNode container = genTypeOrDeopt(RiType.Representation.StaticFields, holder, field instanceof RiResolvedField && ((RiResolvedType) holder).isInitialized());
        ValueNode value = frameState.pop(field.kind(false).stackKind());
        if (container != null) {
            StoreFieldNode store = currentGraph.add(new StoreFieldNode(container, (RiResolvedField) field, value, graphId));
            appendOptimizedStoreField(store);
        } else {
            // deopt will be generated by genTypeOrDeopt, not needed here
        }
    }

    private ConstantNode genTypeOrDeopt(RiType.Representation representation, RiType holder, boolean initialized) {
        if (initialized) {
            return appendConstant(((RiResolvedType) holder).getEncoding(representation));
        } else {
            append(currentGraph.add(new DeoptimizeNode(RiDeoptAction.InvalidateRecompile, RiDeoptReason.Unresolved, graphId)));
            return null;
        }
    }

    private void appendOptimizedStoreField(StoreFieldNode store) {
        append(store);
    }

    private void appendOptimizedLoadField(CiKind kind, LoadFieldNode load) {
        // append the load to the instruction
        ValueNode optimized = append(load);
        frameState.push(kind.stackKind(), optimized);
    }

    private void genInvokeStatic(RiMethod target) {
        if (target instanceof RiResolvedMethod) {
            RiResolvedMethod resolvedTarget = (RiResolvedMethod) target;
            RiResolvedType holder = resolvedTarget.holder();
            if (!holder.isInitialized() && GraalOptions.ResolveClassBeforeStaticInvoke) {
                genInvokeDeopt(target, false);
            } else {
                ValueNode[] args = frameState.popArguments(resolvedTarget.signature().argumentSlots(false), resolvedTarget.signature().argumentCount(false));
                appendInvoke(InvokeKind.Static, resolvedTarget, args);
            }
        } else {
            genInvokeDeopt(target, false);
        }
    }

    private void genInvokeInterface(RiMethod target) {
        if (target instanceof RiResolvedMethod) {
            ValueNode[] args = frameState.popArguments(target.signature().argumentSlots(true), target.signature().argumentCount(true));
            genInvokeIndirect(InvokeKind.Interface, (RiResolvedMethod) target, args);
        } else {
            genInvokeDeopt(target, true);
        }
    }

    private void genInvokeVirtual(RiMethod target) {
        if (target instanceof RiResolvedMethod) {
            ValueNode[] args = frameState.popArguments(target.signature().argumentSlots(true), target.signature().argumentCount(true));
            genInvokeIndirect(InvokeKind.Virtual, (RiResolvedMethod) target, args);
        } else {
            genInvokeDeopt(target, true);
        }

    }

    private void genInvokeSpecial(RiMethod target) {
        if (target instanceof RiResolvedMethod) {
            assert target != null;
            assert target.signature() != null;
            ValueNode[] args = frameState.popArguments(target.signature().argumentSlots(true), target.signature().argumentCount(true));
            invokeDirect((RiResolvedMethod) target, args);
        } else {
            genInvokeDeopt(target, true);
        }
    }

    private void genInvokeDeopt(RiMethod unresolvedTarget, boolean withReceiver) {
        append(currentGraph.add(new DeoptimizeNode(RiDeoptAction.InvalidateRecompile, RiDeoptReason.Unresolved, graphId)));
        frameState.popArguments(unresolvedTarget.signature().argumentSlots(withReceiver), unresolvedTarget.signature().argumentCount(withReceiver));
        CiKind kind = unresolvedTarget.signature().returnKind(false);
        if (kind != CiKind.Void) {
            frameState.push(kind.stackKind(), append(ConstantNode.defaultForKind(kind, currentGraph)));
        }
    }

    private void genInvokeIndirect(InvokeKind invokeKind, RiResolvedMethod target, ValueNode[] args) {
        ValueNode receiver = args[0];
        // attempt to devirtualize the call
        RiResolvedType klass = target.holder();

        // 0. check for trivial cases
        if (target.canBeStaticallyBound() && !isAbstract(target.accessFlags())) {
            // check for trivial cases (e.g. final methods, nonvirtual methods)
            invokeDirect(target, args);
            return;
        }
        // 1. check if the exact type of the receiver can be determined
        RiResolvedType exact = klass.exactType();
        if (exact == null && receiver.objectStamp().isExactType()) {
            exact = receiver.objectStamp().type();
        }
        if (exact != null) {
            // either the holder class is exact, or the receiver object has an exact type
            invokeDirect(exact.resolveMethodImpl(target), args);
            return;
        }
        // devirtualization failed, produce an actual invokevirtual
        appendInvoke(invokeKind, target, args);
    }

    private void invokeDirect(RiResolvedMethod target, ValueNode[] args) {
        appendInvoke(InvokeKind.Special, target, args);
    }

    private void appendInvoke(InvokeKind invokeKind, RiResolvedMethod targetMethod, ValueNode[] args) {
        CiKind resultType = targetMethod.signature().returnKind(false);
        if (GraalOptions.DeoptALot) {
            DeoptimizeNode deoptimize = currentGraph.add(new DeoptimizeNode(RiDeoptAction.None, RiDeoptReason.RuntimeConstraint, graphId));
            deoptimize.setMessage("invoke " + targetMethod.name());
            append(deoptimize);
            frameState.pushReturn(resultType, ConstantNode.defaultForKind(resultType, currentGraph));
            return;
        }

        MethodCallTargetNode callTarget = currentGraph.add(new MethodCallTargetNode(invokeKind, targetMethod, args, targetMethod.signature().returnType(method.holder())));
        // be conservative if information was not recorded (could result in endless recompiles otherwise)
        if (optimisticOpts.useExceptionProbability() && profilingInfo.getExceptionSeen(bci()) == RiExceptionSeen.FALSE) {
            ValueNode result = appendWithBCI(currentGraph.add(new InvokeNode(callTarget, bci(), graphId)));
            frameState.pushReturn(resultType, result);

        } else {
            DispatchBeginNode exceptionEdge = handleException(null, bci());
            InvokeWithExceptionNode invoke = currentGraph.add(new InvokeWithExceptionNode(callTarget, exceptionEdge, bci(), graphId));
            ValueNode result = append(invoke);
            frameState.pushReturn(resultType, result);
            Block nextBlock = currentBlock.successors.get(0);

            assert bci() == currentBlock.endBci;
            frameState.clearNonLiveLocals(currentBlock.localsLiveOut);

            invoke.setNext(createTarget(nextBlock, frameState));
            invoke.setStateAfter(frameState.create(nextBlock.startBci));
        }
    }

    private void callRegisterFinalizer() {
        // append a call to the finalizer registration
        append(currentGraph.add(new RegisterFinalizerNode(frameState.loadLocal(0))));
    }

    private void genReturn(ValueNode x) {
        frameState.clearStack();
        if (x != null) {
            frameState.push(x.kind(), x);
        }
        appendGoto(createTarget(returnBlock(bci()), frameState));
    }

    private MonitorEnterNode genMonitorEnter(ValueNode x) {
        MonitorEnterNode monitorEnter = currentGraph.add(new MonitorEnterNode(x));
        appendWithBCI(monitorEnter);
        return monitorEnter;
    }

    private MonitorExitNode genMonitorExit(ValueNode x) {
        MonitorExitNode monitorExit = currentGraph.add(new MonitorExitNode(x));
        appendWithBCI(monitorExit);
        return monitorExit;
    }

    private void genJsr(int dest) {
        Block successor = currentBlock.jsrSuccessor;
        assert successor.startBci == dest : successor.startBci + " != " + dest + " @" + bci();
        JsrScope scope = currentBlock.jsrScope;
        if (!successor.jsrScope.pop().equals(scope)) {
            throw new JsrNotSupportedBailout("unstructured control flow (internal limitation)");
        }
        if (successor.jsrScope.nextReturnAddress() != stream().nextBCI()) {
            throw new JsrNotSupportedBailout("unstructured control flow (internal limitation)");
        }
        frameState.push(CiKind.Jsr, ConstantNode.forJsr(stream().nextBCI(), currentGraph));
        appendGoto(createTarget(successor, frameState));
    }

    private void genRet(int localIndex) {
        Block successor = currentBlock.retSuccessor;
        ValueNode local = frameState.loadLocal(localIndex);
        JsrScope scope = currentBlock.jsrScope;
        int retAddress = scope.nextReturnAddress();
        append(currentGraph.add(new FixedGuardNode(currentGraph.unique(new IntegerEqualsNode(local, ConstantNode.forJsr(retAddress, currentGraph))), RiDeoptReason.JavaSubroutineMismatch, RiDeoptAction.InvalidateReprofile, graphId)));
        if (!successor.jsrScope.equals(scope.pop())) {
            throw new JsrNotSupportedBailout("unstructured control flow (ret leaves more than one scope)");
        }
        appendGoto(createTarget(successor, frameState));
    }

    private void genTableswitch() {
        int bci = bci();
        ValueNode value = frameState.ipop();
        BytecodeTableSwitch ts = new BytecodeTableSwitch(stream(), bci);

        int nofCases = ts.numberOfCases() + 1; // including default case
        assert currentBlock.numNormalSuccessors() == nofCases;

        double[] probabilities = switchProbability(nofCases, bci);
        TableSwitchNode tableSwitch = currentGraph.add(new TableSwitchNode(value, ts.lowKey(), probabilities));
        for (int i = 0; i < nofCases; ++i) {
            tableSwitch.setBlockSuccessor(i, createBlockTarget(probabilities[i], currentBlock.successors.get(i), frameState));
        }
        append(tableSwitch);
    }

    private double[] switchProbability(int numberOfCases, int bci) {
        double[] prob = profilingInfo.getSwitchProbabilities(bci);
        if (prob != null) {
            assert prob.length == numberOfCases;
        } else {
            Debug.log("Missing probability (switch) in %s at bci %d", method, bci);
            prob = new double[numberOfCases];
            for (int i = 0; i < numberOfCases; i++) {
                prob[i] = 1.0d / numberOfCases;
            }
        }
        assert allPositive(prob);
        return prob;
    }

    private static boolean allPositive(double[] a) {
        for (double d : a) {
            if (d < 0) {
                return false;
            }
        }
        return true;
    }

    private void genLookupswitch() {
        int bci = bci();
        ValueNode value = frameState.ipop();
        BytecodeLookupSwitch ls = new BytecodeLookupSwitch(stream(), bci);

        int nofCases = ls.numberOfCases() + 1; // including default case
        assert currentBlock.numNormalSuccessors() == nofCases;

        int[] keys = new int[nofCases - 1];
        for (int i = 0; i < nofCases - 1; ++i) {
            keys[i] = ls.keyAt(i);
        }
        double[] probabilities = switchProbability(nofCases, bci);
        LookupSwitchNode lookupSwitch = currentGraph.add(new LookupSwitchNode(value, keys, probabilities));
        for (int i = 0; i < nofCases; ++i) {
            lookupSwitch.setBlockSuccessor(i, createBlockTarget(probabilities[i], currentBlock.successors.get(i), frameState));
        }
        append(lookupSwitch);
    }

    private ConstantNode appendConstant(CiConstant constant) {
        return ConstantNode.forCiConstant(constant, runtime, currentGraph);
    }

    private ValueNode append(FixedNode fixed) {
        lastInstr.setNext(fixed);
        lastInstr = null;
        return fixed;
    }

    private ValueNode append(FixedWithNextNode x) {
        return appendWithBCI(x);
    }

    private static ValueNode append(ValueNode v) {
        return v;
    }

    private ValueNode appendWithBCI(FixedWithNextNode x) {
        assert x.predecessor() == null : "instruction should not have been appended yet";
        assert lastInstr.next() == null : "cannot append instruction to instruction which isn't end (" + lastInstr + "->" + lastInstr.next() + ")";
        lastInstr.setNext(x);
        lastInstr = x;
        return x;
    }

    private static class Target {
        FixedNode fixed;
        FrameStateBuilder state;
        public Target(FixedNode fixed, FrameStateBuilder state) {
            this.fixed = fixed;
            this.state = state;
        }
    }

    private Target checkLoopExit(FixedNode target, Block targetBlock, FrameStateBuilder state) {
        if (currentBlock != null) {
            long exits = currentBlock.loops & ~targetBlock.loops;
            if (exits != 0) {
                LoopExitNode firstLoopExit = null;
                LoopExitNode lastLoopExit = null;

                int pos = 0;
                ArrayList<Block> exitLoops = new ArrayList<>(Long.bitCount(exits));
                do {
                    int lMask = 1 << pos;
                    if ((exits & lMask) != 0) {
                        exitLoops.add(loopHeaders[pos]);
                        exits &= ~lMask;
                    }
                    pos++;
                } while (exits != 0);

                Collections.sort(exitLoops, new Comparator<Block>() {
                    @Override
                    public int compare(Block o1, Block o2) {
                        return Long.bitCount(o2.loops) - Long.bitCount(o1.loops);
                    }
                });

                int bci = targetBlock.startBci;
                if (targetBlock instanceof ExceptionDispatchBlock) {
                    bci = ((ExceptionDispatchBlock) targetBlock).deoptBci;
                }
                FrameStateBuilder newState = state.copy();
                for (Block loop : exitLoops) {
                    LoopBeginNode loopBegin = (LoopBeginNode) loop.firstInstruction;
                    LoopExitNode loopExit = currentGraph.add(new LoopExitNode(loopBegin));
                    if (lastLoopExit != null) {
                        lastLoopExit.setNext(loopExit);
                    }
                    if (firstLoopExit == null) {
                        firstLoopExit = loopExit;
                    }
                    lastLoopExit = loopExit;
                    Debug.log("Target %s (%s) Exits %s, scanning framestates...", targetBlock, target, loop);
                    newState.insertProxies(loopExit, loop.entryState);
                    loopExit.setStateAfter(newState.create(bci));
                }

                lastLoopExit.setNext(target);
                return new Target(firstLoopExit, newState);
            }
        }
        return new Target(target, state);
    }

    private FixedNode createTarget(double probability, Block block, FrameStateBuilder stateAfter) {
        assert probability >= 0 && probability <= 1;
        if (probability == 0 && optimisticOpts.removeNeverExecutedCode()) {
            return currentGraph.add(new DeoptimizeNode(RiDeoptAction.InvalidateReprofile, RiDeoptReason.UnreachedCode, graphId));
        } else {
            return createTarget(block, stateAfter);
        }
    }

    private FixedNode createTarget(Block block, FrameStateBuilder state) {
        assert block != null && state != null;
        assert !block.isExceptionEntry || state.stackSize() == 1;

        if (block.firstInstruction == null) {
            // This is the first time we see this block as a branch target.
            // Create and return a placeholder that later can be replaced with a MergeNode when we see this block again.
            block.firstInstruction = currentGraph.add(new BlockPlaceholderNode());
            Target target = checkLoopExit(block.firstInstruction, block, state);
            FixedNode result = target.fixed;
            block.entryState = target.state == state ? state.copy() : target.state;
            block.entryState.clearNonLiveLocals(block.localsLiveIn);

            Debug.log("createTarget %s: first visit, result: %s", block, block.firstInstruction);
            return result;
        }

        // We already saw this block before, so we have to merge states.
        if (!block.entryState.isCompatibleWith(state)) {
            throw new CiBailout("stacks do not match; bytecodes would not verify");
        }

        if (block.firstInstruction instanceof LoopBeginNode) {
            assert block.isLoopHeader && currentBlock.blockID >= block.blockID : "must be backward branch";
            // Backward loop edge. We need to create a special LoopEndNode and merge with the loop begin node created before.
            LoopBeginNode loopBegin = (LoopBeginNode) block.firstInstruction;
            Target target = checkLoopExit(currentGraph.add(new LoopEndNode(loopBegin)), block, state);
            FixedNode result = target.fixed;
            block.entryState.merge(loopBegin, target.state);

            Debug.log("createTarget %s: merging backward branch to loop header %s, result: %s", block, loopBegin, result);
            return result;
        }
        assert currentBlock == null || currentBlock.blockID < block.blockID : "must not be backward branch";
        assert block.firstInstruction.next() == null : "bytecodes already parsed for block";

        if (block.firstInstruction instanceof BlockPlaceholderNode) {
            // This is the second time we see this block. Create the actual MergeNode and the End Node for the already existing edge.
            // For simplicity, we leave the placeholder in the graph and just append the new nodes after the placeholder.
            BlockPlaceholderNode placeholder = (BlockPlaceholderNode) block.firstInstruction;

            // The EndNode for the already existing edge.
            EndNode end = currentGraph.add(new EndNode());
            // The MergeNode that replaces the placeholder.
            MergeNode mergeNode  = currentGraph.add(new MergeNode());
            FixedNode next = placeholder.next();

            placeholder.setNext(end);
            mergeNode.addForwardEnd(end);
            mergeNode.setNext(next);

            block.firstInstruction = mergeNode;
        }

        MergeNode mergeNode = (MergeNode) block.firstInstruction;

        // The EndNode for the newly merged edge.
        EndNode newEnd = currentGraph.add(new EndNode());
        Target target = checkLoopExit(newEnd, block, state);
        FixedNode result = target.fixed;
        block.entryState.merge(mergeNode, target.state);
        mergeNode.addForwardEnd(newEnd);

        Debug.log("createTarget %s: merging state, result: %s", block, result);
        return result;
    }

    /**
     * Returns a block begin node with the specified state.  If the specified probability is 0, the block
     * deoptimizes immediately.
     */
    private BeginNode createBlockTarget(double probability, Block block, FrameStateBuilder stateAfter) {
        FixedNode target = createTarget(probability, block, stateAfter);
        BeginNode begin = BeginNode.begin(target);

        assert !(target instanceof DeoptimizeNode && begin.stateAfter() != null) :
            "We are not allowed to set the stateAfter of the begin node, because we have to deoptimize to a bci _before_ the actual if, so that the interpreter can update the profiling information.";
        return begin;
    }

    private ValueNode synchronizedObject(FrameStateBuilder state, RiResolvedMethod target) {
        if (isStatic(target.accessFlags())) {
            return append(ConstantNode.forCiConstant(target.holder().getEncoding(Representation.JavaClass), runtime, currentGraph));
        } else {
            return state.loadLocal(0);
        }
    }

    private void processBlock(Block block) {
        // Ignore blocks that have no predecessors by the time it their bytecodes are parsed
        if (block == null || block.firstInstruction == null) {
            Debug.log("Ignoring block %s", block);
            return;
        }
        Debug.log("Parsing block %s  firstInstruction: %s  loopHeader: %b", block, block.firstInstruction, block.isLoopHeader);

        lastInstr = block.firstInstruction;
        frameState = block.entryState;
        currentBlock = block;

        frameState.cleanupDeletedPhis();
        if (lastInstr instanceof MergeNode) {
            int bci = block.startBci;
            if (block instanceof ExceptionDispatchBlock) {
                bci = ((ExceptionDispatchBlock) block).deoptBci;
            }
            ((MergeNode) lastInstr).setStateAfter(frameState.create(bci));
        }

        if (block == returnBlock) {
            frameState.setRethrowException(false);
            createReturn();
        } else if (block == unwindBlock) {
            frameState.setRethrowException(false);
            createUnwind();
        } else if (block instanceof ExceptionDispatchBlock) {
            createExceptionDispatch((ExceptionDispatchBlock) block);
        } else {
            frameState.setRethrowException(false);
            iterateBytecodesForBlock(block);
        }
    }

    private void connectLoopEndToBegin() {
        for (LoopBeginNode begin : currentGraph.getNodes(LoopBeginNode.class)) {
            if (begin.loopEnds().isEmpty()) {
                // Remove loop header without loop ends.
                // This can happen with degenerated loops like this one:
                // for (;;) {
                //     try {
                //         break;
                //     } catch (UnresolvedException iioe) {
                //     }
                // }
                assert begin.forwardEndCount() == 1;
                currentGraph.reduceDegenerateLoopBegin(begin);
            } else {
                GraphUtil.normalizeLoopBegin(begin);
            }
        }
    }

    private void createUnwind() {
        assert frameState.stackSize() == 1 : frameState;
        synchronizedEpilogue(FrameState.AFTER_EXCEPTION_BCI);
        UnwindNode unwindNode = currentGraph.add(new UnwindNode(frameState.apop()));
        append(unwindNode);
    }

    private void createReturn() {
        if (method.isConstructor() && method.holder().superType() == null) {
            callRegisterFinalizer();
        }
        CiKind returnKind = method.signature().returnKind(false).stackKind();
        ValueNode x = returnKind == CiKind.Void ? null : frameState.pop(returnKind);
        assert frameState.stackSize() == 0;

        // TODO (gdub) remove this when FloatingRead can handle this case
        if (Modifier.isSynchronized(method.accessFlags())) {
            append(currentGraph.add(new ValueAnchorNode(x)));
            assert !frameState.rethrowException();
        }

        synchronizedEpilogue(FrameState.AFTER_BCI);
        ReturnNode returnNode = currentGraph.add(new ReturnNode(x));
        append(returnNode);
    }

    private void synchronizedEpilogue(int bci) {
        if (Modifier.isSynchronized(method.accessFlags())) {
            MonitorExitNode monitorExit = genMonitorExit(methodSynchronizedObject);
            monitorExit.setStateAfter(frameState.create(bci));
            assert !frameState.rethrowException();
        }
    }

    private void createExceptionDispatch(ExceptionDispatchBlock block) {
        assert frameState.stackSize() == 1 : frameState;
        if (block.handler.isCatchAll()) {
            assert block.successors.size() == 1;
            appendGoto(createTarget(block.successors.get(0), frameState));
            return;
        }

        RiType catchType = block.handler.catchType();
        if (graphBuilderConfig.eagerResolving()) {
            catchType = lookupType(block.handler.catchTypeCPI(), INSTANCEOF);
        }
        boolean initialized = (catchType instanceof RiResolvedType);
        if (initialized && graphBuilderConfig.getSkippedExceptionTypes() != null) {
            RiResolvedType resolvedCatchType = (RiResolvedType) catchType;
            for (RiResolvedType skippedType : graphBuilderConfig.getSkippedExceptionTypes()) {
                initialized &= !resolvedCatchType.isSubtypeOf(skippedType);
                if (!initialized) {
                    break;
                }
            }
        }

        ConstantNode typeInstruction = genTypeOrDeopt(RiType.Representation.ObjectHub, catchType, initialized);
        if (typeInstruction != null) {
            Block nextBlock = block.successors.size() == 1 ? unwindBlock(block.deoptBci) : block.successors.get(1);
            FixedNode catchSuccessor = createTarget(block.successors.get(0), frameState);
            FixedNode nextDispatch = createTarget(nextBlock, frameState);
            ValueNode exception = frameState.stackAt(0);
            IfNode ifNode = currentGraph.add(new IfNode(currentGraph.unique(new InstanceOfNode(typeInstruction, (RiResolvedType) catchType, exception)), catchSuccessor, nextDispatch, 0.5));
            append(ifNode);
        }
    }

    private void appendGoto(FixedNode target) {
        if (lastInstr != null) {
            lastInstr.setNext(target);
        }
    }

    private static boolean isBlockEnd(Node n) {
        return trueSuccessorCount(n) > 1 || n instanceof ReturnNode || n instanceof UnwindNode || n instanceof DeoptimizeNode;
    }

    private static int trueSuccessorCount(Node n) {
        if (n == null) {
            return 0;
        }
        int i = 0;
        for (Node s : n.successors()) {
            if (Util.isFixed(s)) {
                i++;
            }
        }
        return i;
    }

    private void iterateBytecodesForBlock(Block block) {
        if (block.isLoopHeader) {
            // Create the loop header block, which later will merge the backward branches of the loop.
            EndNode preLoopEnd = currentGraph.add(new EndNode());
            LoopBeginNode loopBegin = currentGraph.add(new LoopBeginNode());
            lastInstr.setNext(preLoopEnd);
            // Add the single non-loop predecessor of the loop header.
            loopBegin.addForwardEnd(preLoopEnd);
            lastInstr = loopBegin;

            // Create phi functions for all local variables and operand stack slots.
            frameState.insertLoopPhis(loopBegin);
            loopBegin.setStateAfter(frameState.create(block.startBci));

            // We have seen all forward branches. All subsequent backward branches will merge to the loop header.
            // This ensures that the loop header has exactly one non-loop predecessor.
            block.firstInstruction = loopBegin;
            // We need to preserve the frame state builder of the loop header so that we can merge values for
            // phi functions, so make a copy of it.
            block.entryState = frameState.copy();

            Debug.log("  created loop header %s", loopBegin);
        }
        assert lastInstr.next() == null : "instructions already appended at block " + block;
        Debug.log("  frameState: %s", frameState);

        int endBCI = stream.endBCI();

        stream.setBCI(block.startBci);
        int bci = block.startBci;
        while (bci < endBCI) {
            // read the opcode
            int opcode = stream.currentBC();
            traceState();
            traceInstruction(bci, opcode, bci == block.startBci);
            processBytecode(bci, opcode);

            if (lastInstr == null || isBlockEnd(lastInstr) || lastInstr.next() != null) {
                break;
            }

            stream.next();
            bci = stream.currentBCI();

            if (bci > block.endBci) {
                frameState.clearNonLiveLocals(currentBlock.localsLiveOut);
            }
            if (lastInstr instanceof StateSplit) {
                if (lastInstr.getClass() == BeginNode.class) {
                    // BeginNodes do not need a frame state
                } else {
                    StateSplit stateSplit = (StateSplit) lastInstr;
                    if (stateSplit.stateAfter() == null) {
                        stateSplit.setStateAfter(frameState.create(bci));
                    }
                }
            }
            if (bci < endBCI) {
                if (bci > block.endBci) {
                    assert !block.successors.get(0).isExceptionEntry;
                    assert block.numNormalSuccessors() == 1;
                    // we fell through to the next block, add a goto and break
                    appendGoto(createTarget(block.successors.get(0), frameState));
                    break;
                }
            }
        }
    }

    private void traceState() {
        if (GraalOptions.TraceBytecodeParserLevel >= TRACELEVEL_STATE && !TTY.isSuppressed()) {
            log.println(String.format("|   state [nr locals = %d, stack depth = %d, method = %s]", frameState.localsSize(), frameState.stackSize(), method));
            for (int i = 0; i < frameState.localsSize(); ++i) {
                ValueNode value = frameState.localAt(i);
                log.println(String.format("|   local[%d] = %-8s : %s", i, value == null ? "bogus" : value.kind().javaName, value));
            }
            for (int i = 0; i < frameState.stackSize(); ++i) {
                ValueNode value = frameState.stackAt(i);
                log.println(String.format("|   stack[%d] = %-8s : %s", i, value == null ? "bogus" : value.kind().javaName, value));
            }
        }
    }

    private void processBytecode(int bci, int opcode) {
        int cpi;

        // Checkstyle: stop
        switch (opcode) {
            case NOP            : /* nothing to do */ break;
            case ACONST_NULL    : frameState.apush(appendConstant(CiConstant.NULL_OBJECT)); break;
            case ICONST_M1      : frameState.ipush(appendConstant(CiConstant.INT_MINUS_1)); break;
            case ICONST_0       : frameState.ipush(appendConstant(CiConstant.INT_0)); break;
            case ICONST_1       : frameState.ipush(appendConstant(CiConstant.INT_1)); break;
            case ICONST_2       : frameState.ipush(appendConstant(CiConstant.INT_2)); break;
            case ICONST_3       : frameState.ipush(appendConstant(CiConstant.INT_3)); break;
            case ICONST_4       : frameState.ipush(appendConstant(CiConstant.INT_4)); break;
            case ICONST_5       : frameState.ipush(appendConstant(CiConstant.INT_5)); break;
            case LCONST_0       : frameState.lpush(appendConstant(CiConstant.LONG_0)); break;
            case LCONST_1       : frameState.lpush(appendConstant(CiConstant.LONG_1)); break;
            case FCONST_0       : frameState.fpush(appendConstant(CiConstant.FLOAT_0)); break;
            case FCONST_1       : frameState.fpush(appendConstant(CiConstant.FLOAT_1)); break;
            case FCONST_2       : frameState.fpush(appendConstant(CiConstant.FLOAT_2)); break;
            case DCONST_0       : frameState.dpush(appendConstant(CiConstant.DOUBLE_0)); break;
            case DCONST_1       : frameState.dpush(appendConstant(CiConstant.DOUBLE_1)); break;
            case BIPUSH         : frameState.ipush(appendConstant(CiConstant.forInt(stream.readByte()))); break;
            case SIPUSH         : frameState.ipush(appendConstant(CiConstant.forInt(stream.readShort()))); break;
            case LDC            : // fall through
            case LDC_W          : // fall through
            case LDC2_W         : genLoadConstant(stream.readCPI(), opcode); break;
            case ILOAD          : loadLocal(stream.readLocalIndex(), CiKind.Int); break;
            case LLOAD          : loadLocal(stream.readLocalIndex(), CiKind.Long); break;
            case FLOAD          : loadLocal(stream.readLocalIndex(), CiKind.Float); break;
            case DLOAD          : loadLocal(stream.readLocalIndex(), CiKind.Double); break;
            case ALOAD          : loadLocal(stream.readLocalIndex(), CiKind.Object); break;
            case ILOAD_0        : // fall through
            case ILOAD_1        : // fall through
            case ILOAD_2        : // fall through
            case ILOAD_3        : loadLocal(opcode - ILOAD_0, CiKind.Int); break;
            case LLOAD_0        : // fall through
            case LLOAD_1        : // fall through
            case LLOAD_2        : // fall through
            case LLOAD_3        : loadLocal(opcode - LLOAD_0, CiKind.Long); break;
            case FLOAD_0        : // fall through
            case FLOAD_1        : // fall through
            case FLOAD_2        : // fall through
            case FLOAD_3        : loadLocal(opcode - FLOAD_0, CiKind.Float); break;
            case DLOAD_0        : // fall through
            case DLOAD_1        : // fall through
            case DLOAD_2        : // fall through
            case DLOAD_3        : loadLocal(opcode - DLOAD_0, CiKind.Double); break;
            case ALOAD_0        : // fall through
            case ALOAD_1        : // fall through
            case ALOAD_2        : // fall through
            case ALOAD_3        : loadLocal(opcode - ALOAD_0, CiKind.Object); break;
            case IALOAD         : genLoadIndexed(CiKind.Int   ); break;
            case LALOAD         : genLoadIndexed(CiKind.Long  ); break;
            case FALOAD         : genLoadIndexed(CiKind.Float ); break;
            case DALOAD         : genLoadIndexed(CiKind.Double); break;
            case AALOAD         : genLoadIndexed(CiKind.Object); break;
            case BALOAD         : genLoadIndexed(CiKind.Byte  ); break;
            case CALOAD         : genLoadIndexed(CiKind.Char  ); break;
            case SALOAD         : genLoadIndexed(CiKind.Short ); break;
            case ISTORE         : storeLocal(CiKind.Int, stream.readLocalIndex()); break;
            case LSTORE         : storeLocal(CiKind.Long, stream.readLocalIndex()); break;
            case FSTORE         : storeLocal(CiKind.Float, stream.readLocalIndex()); break;
            case DSTORE         : storeLocal(CiKind.Double, stream.readLocalIndex()); break;
            case ASTORE         : storeLocal(CiKind.Object, stream.readLocalIndex()); break;
            case ISTORE_0       : // fall through
            case ISTORE_1       : // fall through
            case ISTORE_2       : // fall through
            case ISTORE_3       : storeLocal(CiKind.Int, opcode - ISTORE_0); break;
            case LSTORE_0       : // fall through
            case LSTORE_1       : // fall through
            case LSTORE_2       : // fall through
            case LSTORE_3       : storeLocal(CiKind.Long, opcode - LSTORE_0); break;
            case FSTORE_0       : // fall through
            case FSTORE_1       : // fall through
            case FSTORE_2       : // fall through
            case FSTORE_3       : storeLocal(CiKind.Float, opcode - FSTORE_0); break;
            case DSTORE_0       : // fall through
            case DSTORE_1       : // fall through
            case DSTORE_2       : // fall through
            case DSTORE_3       : storeLocal(CiKind.Double, opcode - DSTORE_0); break;
            case ASTORE_0       : // fall through
            case ASTORE_1       : // fall through
            case ASTORE_2       : // fall through
            case ASTORE_3       : storeLocal(CiKind.Object, opcode - ASTORE_0); break;
            case IASTORE        : genStoreIndexed(CiKind.Int   ); break;
            case LASTORE        : genStoreIndexed(CiKind.Long  ); break;
            case FASTORE        : genStoreIndexed(CiKind.Float ); break;
            case DASTORE        : genStoreIndexed(CiKind.Double); break;
            case AASTORE        : genStoreIndexed(CiKind.Object); break;
            case BASTORE        : genStoreIndexed(CiKind.Byte  ); break;
            case CASTORE        : genStoreIndexed(CiKind.Char  ); break;
            case SASTORE        : genStoreIndexed(CiKind.Short ); break;
            case POP            : // fall through
            case POP2           : // fall through
            case DUP            : // fall through
            case DUP_X1         : // fall through
            case DUP_X2         : // fall through
            case DUP2           : // fall through
            case DUP2_X1        : // fall through
            case DUP2_X2        : // fall through
            case SWAP           : stackOp(opcode); break;
            case IADD           : // fall through
            case ISUB           : // fall through
            case IMUL           : genArithmeticOp(CiKind.Int, opcode, false); break;
            case IDIV           : // fall through
            case IREM           : genArithmeticOp(CiKind.Int, opcode, true); break;
            case LADD           : // fall through
            case LSUB           : // fall through
            case LMUL           : genArithmeticOp(CiKind.Long, opcode, false); break;
            case LDIV           : // fall through
            case LREM           : genArithmeticOp(CiKind.Long, opcode, true); break;
            case FADD           : // fall through
            case FSUB           : // fall through
            case FMUL           : // fall through
            case FDIV           : // fall through
            case FREM           : genArithmeticOp(CiKind.Float, opcode, false); break;
            case DADD           : // fall through
            case DSUB           : // fall through
            case DMUL           : // fall through
            case DDIV           : // fall through
            case DREM           : genArithmeticOp(CiKind.Double, opcode, false); break;
            case INEG           : genNegateOp(CiKind.Int); break;
            case LNEG           : genNegateOp(CiKind.Long); break;
            case FNEG           : genNegateOp(CiKind.Float); break;
            case DNEG           : genNegateOp(CiKind.Double); break;
            case ISHL           : // fall through
            case ISHR           : // fall through
            case IUSHR          : genShiftOp(CiKind.Int, opcode); break;
            case IAND           : // fall through
            case IOR            : // fall through
            case IXOR           : genLogicOp(CiKind.Int, opcode); break;
            case LSHL           : // fall through
            case LSHR           : // fall through
            case LUSHR          : genShiftOp(CiKind.Long, opcode); break;
            case LAND           : // fall through
            case LOR            : // fall through
            case LXOR           : genLogicOp(CiKind.Long, opcode); break;
            case IINC           : genIncrement(); break;
            case I2L            : genConvert(ConvertNode.Op.I2L); break;
            case I2F            : genConvert(ConvertNode.Op.I2F); break;
            case I2D            : genConvert(ConvertNode.Op.I2D); break;
            case L2I            : genConvert(ConvertNode.Op.L2I); break;
            case L2F            : genConvert(ConvertNode.Op.L2F); break;
            case L2D            : genConvert(ConvertNode.Op.L2D); break;
            case F2I            : genConvert(ConvertNode.Op.F2I); break;
            case F2L            : genConvert(ConvertNode.Op.F2L); break;
            case F2D            : genConvert(ConvertNode.Op.F2D); break;
            case D2I            : genConvert(ConvertNode.Op.D2I); break;
            case D2L            : genConvert(ConvertNode.Op.D2L); break;
            case D2F            : genConvert(ConvertNode.Op.D2F); break;
            case I2B            : genConvert(ConvertNode.Op.I2B); break;
            case I2C            : genConvert(ConvertNode.Op.I2C); break;
            case I2S            : genConvert(ConvertNode.Op.I2S); break;
            case LCMP           : genCompareOp(CiKind.Long, false); break;
            case FCMPL          : genCompareOp(CiKind.Float, true); break;
            case FCMPG          : genCompareOp(CiKind.Float, false); break;
            case DCMPL          : genCompareOp(CiKind.Double, true); break;
            case DCMPG          : genCompareOp(CiKind.Double, false); break;
            case IFEQ           : genIfZero(Condition.EQ); break;
            case IFNE           : genIfZero(Condition.NE); break;
            case IFLT           : genIfZero(Condition.LT); break;
            case IFGE           : genIfZero(Condition.GE); break;
            case IFGT           : genIfZero(Condition.GT); break;
            case IFLE           : genIfZero(Condition.LE); break;
            case IF_ICMPEQ      : genIfSame(CiKind.Int, Condition.EQ); break;
            case IF_ICMPNE      : genIfSame(CiKind.Int, Condition.NE); break;
            case IF_ICMPLT      : genIfSame(CiKind.Int, Condition.LT); break;
            case IF_ICMPGE      : genIfSame(CiKind.Int, Condition.GE); break;
            case IF_ICMPGT      : genIfSame(CiKind.Int, Condition.GT); break;
            case IF_ICMPLE      : genIfSame(CiKind.Int, Condition.LE); break;
            case IF_ACMPEQ      : genIfSame(CiKind.Object, Condition.EQ); break;
            case IF_ACMPNE      : genIfSame(CiKind.Object, Condition.NE); break;
            case GOTO           : genGoto(); break;
            case JSR            : genJsr(stream.readBranchDest()); break;
            case RET            : genRet(stream.readLocalIndex()); break;
            case TABLESWITCH    : genTableswitch(); break;
            case LOOKUPSWITCH   : genLookupswitch(); break;
            case IRETURN        : genReturn(frameState.ipop()); break;
            case LRETURN        : genReturn(frameState.lpop()); break;
            case FRETURN        : genReturn(frameState.fpop()); break;
            case DRETURN        : genReturn(frameState.dpop()); break;
            case ARETURN        : genReturn(frameState.apop()); break;
            case RETURN         : genReturn(null); break;
            case GETSTATIC      : cpi = stream.readCPI(); genGetStatic(lookupField(cpi, opcode)); break;
            case PUTSTATIC      : cpi = stream.readCPI(); genPutStatic(lookupField(cpi, opcode)); break;
            case GETFIELD       : cpi = stream.readCPI(); genGetField(lookupField(cpi, opcode)); break;
            case PUTFIELD       : cpi = stream.readCPI(); genPutField(lookupField(cpi, opcode)); break;
            case INVOKEVIRTUAL  : cpi = stream.readCPI(); genInvokeVirtual(lookupMethod(cpi, opcode)); break;
            case INVOKESPECIAL  : cpi = stream.readCPI(); genInvokeSpecial(lookupMethod(cpi, opcode)); break;
            case INVOKESTATIC   : cpi = stream.readCPI(); genInvokeStatic(lookupMethod(cpi, opcode)); break;
            case INVOKEINTERFACE: cpi = stream.readCPI(); genInvokeInterface(lookupMethod(cpi, opcode)); break;
            case NEW            : genNewInstance(stream.readCPI()); break;
            case NEWARRAY       : genNewTypeArray(stream.readLocalIndex()); break;
            case ANEWARRAY      : genNewObjectArray(stream.readCPI()); break;
            case ARRAYLENGTH    : genArrayLength(); break;
            case ATHROW         : genThrow(); break;
            case CHECKCAST      : genCheckCast(); break;
            case INSTANCEOF     : genInstanceOf(); break;
            case MONITORENTER   : genMonitorEnter(frameState.apop()); break;
            case MONITOREXIT    : genMonitorExit(frameState.apop()); break;
            case MULTIANEWARRAY : genNewMultiArray(stream.readCPI()); break;
            case IFNULL         : genIfNull(Condition.EQ); break;
            case IFNONNULL      : genIfNull(Condition.NE); break;
            case GOTO_W         : genGoto(); break;
            case JSR_W          : genJsr(stream.readBranchDest()); break;
            case BREAKPOINT:
                throw new CiBailout("concurrent setting of breakpoint");
            default:
                throw new CiBailout("Unsupported opcode " + opcode + " (" + nameOf(opcode) + ") [bci=" + bci + "]");
        }
        // Checkstyle: resume
    }

    private void traceInstruction(int bci, int opcode, boolean blockStart) {
        if (GraalOptions.TraceBytecodeParserLevel >= TRACELEVEL_INSTRUCTIONS && !TTY.isSuppressed()) {
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
            log.println(sb.toString());
        }
    }

    private void genArrayLength() {
        frameState.ipush(append(currentGraph.add(new ArrayLengthNode(frameState.apop()))));
    }
}
