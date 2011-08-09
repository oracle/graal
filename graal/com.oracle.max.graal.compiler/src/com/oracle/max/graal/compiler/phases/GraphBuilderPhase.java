/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.phases;

import static com.sun.cri.bytecode.Bytecodes.*;
import static java.lang.reflect.Modifier.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.graph.*;
import com.oracle.max.graal.compiler.graph.BlockMap.Block;
import com.oracle.max.graal.compiler.graph.BlockMap.BranchOverride;
import com.oracle.max.graal.compiler.graph.BlockMap.DeoptBlock;
import com.oracle.max.graal.compiler.graph.BlockMap.ExceptionBlock;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.compiler.util.LoopUtil.Loop;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.collections.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.DeoptimizeNode.DeoptAction;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.nodes.java.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;
import com.sun.cri.ri.RiType.Representation;

/**
 * The {@code GraphBuilder} class parses the bytecode of a method and builds the IR graph.
 * A number of optimizations may be performed during parsing of the bytecode, including value
 * numbering, inlining, constant folding, strength reduction, etc.
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

    private final GraalCompilation compilation;
    private CompilerGraph graph;

    private final CiStatistics stats;
    private final RiRuntime runtime;
    private final RiMethod method;
    private final RiConstantPool constantPool;
    private RiExceptionHandler[] exceptionHandlers;

    private final BytecodeStream stream;           // the bytecode stream
    private final LogStream log;
    private FrameStateBuilder frameState;          // the current execution state

    // bci-to-block mapping
    private Block[] blockFromBci;
    private ArrayList<Block> blockList;
    private HashMap<Integer, BranchOverride> branchOverride;

    private int nextBlockNumber;

    private ValueNode methodSynchronizedObject;
    private CiExceptionHandler unwindHandler;

    private ExceptionBlock unwindBlock;
    private Block returnBlock;

    private boolean storeResultGraph;

    // the worklist of blocks, sorted by depth first number
    private final PriorityQueue<Block> workList = new PriorityQueue<Block>(10, new Comparator<Block>() {
        public int compare(Block o1, Block o2) {
            return o1.blockID - o2.blockID;
        }
    });

    private FixedWithNextNode lastInstr;                 // the last instruction added

    private final Set<Block> blocksOnWorklist = new HashSet<Block>();
    private final Set<Block> blocksVisited = new HashSet<Block>();

    public static final Map<RiMethod, CompilerGraph> cachedGraphs = new WeakHashMap<RiMethod, CompilerGraph>();

    /**
     * Creates a new, initialized, {@code GraphBuilder} instance for a given compilation.
     *
     * @param compilation the compilation
     * @param ir the IR to build the graph into
     * @param graph
     */
    public GraphBuilderPhase(GraalCompilation compilation, RiMethod method, boolean inline) {
        super(inline ? "BuildInlineGraph" : "BuildGraph");
        this.compilation = compilation;

        this.runtime = compilation.runtime;
        this.method = method;
        this.stats = compilation.stats;
        this.log = GraalOptions.TraceBytecodeParserLevel > 0 ? new LogStream(TTY.out()) : null;
        this.stream = new BytecodeStream(method.code());

        this.constantPool = runtime.getConstantPool(method);
        this.storeResultGraph = GraalOptions.CacheGraphs;
    }

    @Override
    protected void run(Graph graph) {
        assert graph != null;
        this.graph = (CompilerGraph) graph;
        this.frameState = new FrameStateBuilder(method, graph);
        build();
    }

    @Override
    protected String getDetailedName() {
        return getName() + " " + method.holder().name() + "." + method.name() + method.signature().asString();
    }

    /**
     * Builds the graph for a the specified {@code IRScope}.
     *
     * @param createUnwind setting this to true will always generate an unwind block, even if there is no exception
     *            handler and the method is not synchronized
     */
    private void build() {
        if (log != null) {
            log.println();
            log.println("Compiling " + method);
        }

        // 2. compute the block map, setup exception handlers and get the entrypoint(s)
        BlockMap blockMap = compilation.getBlockMap(method);
        this.branchOverride = blockMap.branchOverride;

        exceptionHandlers = blockMap.exceptionHandlers();
        blockList = new ArrayList<Block>(blockMap.blocks);
        blockFromBci = new Block[method.codeSize()];
        for (int i = 0; i < blockList.size(); i++) {
            int blockID = nextBlockNumber();
            assert blockID == i;
            Block block = blockList.get(i);
            if (block.startBci >= 0 && !(block instanceof BlockMap.DeoptBlock)) {
                blockFromBci[block.startBci] = block;
            }
        }

        // 1. create the start block
        Block startBlock = nextBlock(FixedWithNextNode.SYNCHRONIZATION_ENTRY_BCI);
        markOnWorkList(startBlock);
        lastInstr = (FixedWithNextNode) createTarget(startBlock, frameState);
        graph.start().setNext(lastInstr);

        if (isSynchronized(method.accessFlags())) {
            // 4A.1 add a monitor enter to the start block
            methodSynchronizedObject = synchronizedObject(frameState, method);
            genMonitorEnter(methodSynchronizedObject, FixedWithNextNode.SYNCHRONIZATION_ENTRY_BCI);
        }

        // 4B.1 simply finish the start block
        finishStartBlock(startBlock);
        unwindHandler = new CiExceptionHandler(0, method.code().length, FixedWithNextNode.SYNCHRONIZATION_ENTRY_BCI, 0, null);

        // 5. SKIPPED: look for intrinsics

        // 6B.1 do the normal parsing
        addToWorkList(blockFromBci[0]);
        iterateAllBlocks();

        List<Loop> loops = LoopUtil.computeLoops(graph);
        NodeBitMap loopExits = graph.createNodeBitMap();
        for (Loop loop : loops) {
            loopExits.setUnion(loop.exits());
        }

        // remove Placeholders
        for (Node n : graph.getNodes()) {
            if (n instanceof PlaceholderNode && !loopExits.isMarked(n)) {
                PlaceholderNode p = (PlaceholderNode) n;
                p.replaceAndDelete(p.next());
            }
        }

        // remove FrameStates
        for (Node n : graph.getNodes()) {
            if (n instanceof FrameState) {
                if (n.usages().size() == 0 && n.predecessor() == null) {
                    n.delete();
                }
            }
        }

        if (storeResultGraph) {
            // Create duplicate graph.
            CompilerGraph duplicate = new CompilerGraph(null);
            Map<Node, Node> replacements = new HashMap<Node, Node>();
            replacements.put(graph.start(), duplicate.start());
            duplicate.addDuplicate(graph.getNodes(), replacements);

            cachedGraphs.put(method, duplicate);
        }
    }

    private int nextBlockNumber() {
        stats.blockCount++;
        return nextBlockNumber++;
    }

    private Block nextBlock(int bci) {
        Block block = new Block();
        block.startBci = bci;
        block.endBci = bci;
        block.blockID = nextBlockNumber();
        return block;
    }

    private Block unwindBlock(int bci) {
        if (unwindBlock == null) {
            unwindBlock = new ExceptionBlock();
            unwindBlock.startBci = -1;
            unwindBlock.endBci = -1;
            unwindBlock.deoptBci = bci;
            unwindBlock.blockID = nextBlockNumber();
            addToWorkList(unwindBlock);
        }
        return unwindBlock;
    }

    private Block returnBlock(int bci) {
        if (returnBlock == null) {
            returnBlock = new Block();
            returnBlock.startBci = bci;
            returnBlock.endBci = bci;
            returnBlock.blockID = nextBlockNumber();
            addToWorkList(returnBlock);
        }
        return returnBlock;
    }

    private void markOnWorkList(Block block) {
        blocksOnWorklist.add(block);
    }

    private boolean isOnWorkList(Block block) {
        return blocksOnWorklist.contains(block);
    }

    private void markVisited(Block block) {
        blocksVisited.add(block);
    }

    private boolean isVisited(Block block) {
        return blocksVisited.contains(block);
    }

    private void finishStartBlock(Block startBlock) {
        assert bci() == 0;
        appendGoto(createTargetAt(0, frameState));
    }

    public void mergeOrClone(Block target, FrameStateAccess newState) {
        StateSplit first = (StateSplit) target.firstInstruction;

        if (target.isLoopHeader && isVisited(target)) {
            first = (StateSplit) loopBegin(target).loopEnd().predecessor();
        }

        int bci = target.startBci;
        if (target instanceof ExceptionBlock) {
            bci = ((ExceptionBlock) target).deoptBci;
        }

        FrameState existingState = first.stateAfter();

        if (existingState == null) {
            // copy state because it is modified
            first.setStateAfter(newState.duplicate(bci));
        } else {
            if (!GraalOptions.AssumeVerifiedBytecode && !existingState.isCompatibleWith(newState)) {
                // stacks or locks do not match--bytecodes would not verify
                TTY.println(existingState.toString());
                TTY.println(newState.duplicate(0).toString());
                throw new CiBailout("stack or locks do not match");
            }
            assert existingState.localsSize() == newState.localsSize();
            assert existingState.stackSize() == newState.stackSize();

            if (first instanceof PlaceholderNode) {
                PlaceholderNode p = (PlaceholderNode) first;
                if (p.predecessor() == null) {
                    p.setStateAfter(newState.duplicate(bci));
                    return;
                } else {
                    MergeNode merge = new MergeNode(graph);
                    FixedNode next = p.next();
                    EndNode end = new EndNode(graph);
                    p.setNext(end);
                    merge.setNext(next);
                    merge.addEnd(end);
                    merge.setStateAfter(existingState);
                    p.setStateAfter(existingState.duplicate(bci));
                    if (!(next instanceof LoopEndNode)) {
                        target.firstInstruction = merge;
                    }
                    first = merge;
                }
            }

            existingState.merge((MergeNode) first, newState);
        }
    }

    private void insertLoopPhis(LoopBeginNode loopBegin, FrameState newState) {
        int stackSize = newState.stackSize();
        for (int i = 0; i < stackSize; i++) {
            // always insert phis for the stack
            ValueNode x = newState.stackAt(i);
            if (x != null) {
                newState.setupPhiForStack(loopBegin, i).addInput(x);
            }
        }
        int localsSize = newState.localsSize();
        for (int i = 0; i < localsSize; i++) {
            ValueNode x = newState.localAt(i);
            if (x != null) {
                newState.setupPhiForLocal(loopBegin, i).addInput(x);
            }
        }
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

    public boolean covers(RiExceptionHandler handler, int bci) {
        return handler.startBCI() <= bci && bci < handler.endBCI();
    }

    public boolean isCatchAll(RiExceptionHandler handler) {
        return handler.catchTypeCPI() == 0;
    }

    private FixedNode handleException(ValueNode exceptionObject, int bci) {
        assert bci == FixedWithNextNode.SYNCHRONIZATION_ENTRY_BCI || bci == bci() : "invalid bci";

        if (GraalOptions.UseExceptionProbability && method.invocationCount() > GraalOptions.MatureInvocationCount) {
            if (exceptionObject == null && method.exceptionProbability(bci) == 0) {
                return null;
            }
        }

        RiExceptionHandler firstHandler = null;
        // join with all potential exception handlers
        if (exceptionHandlers != null) {
            for (RiExceptionHandler handler : exceptionHandlers) {
                // if the handler covers this bytecode index, add it to the list
                if (covers(handler, bci)) {
                    firstHandler = handler;
                    break;
                }
            }
        }

        if (firstHandler == null) {
            firstHandler = unwindHandler;
        }

        if (firstHandler != null) {
            compilation.setHasExceptionHandlers();

            Block dispatchBlock = null;
            for (Block block : blockList) {
                if (block instanceof ExceptionBlock) {
                    ExceptionBlock excBlock = (ExceptionBlock) block;
                    if (excBlock.handler == firstHandler) {
                        dispatchBlock = block;
                        break;
                    }
                }
            }
            // if there's no dispatch block then the catch block needs to be a catch all
            if (dispatchBlock == null) {
                assert isCatchAll(firstHandler);
                int handlerBCI = firstHandler.handlerBCI();
                if (handlerBCI == FixedWithNextNode.SYNCHRONIZATION_ENTRY_BCI) {
                    dispatchBlock = unwindBlock(bci);
                } else {
                    dispatchBlock = blockFromBci[handlerBCI];
                }
            }
            PlaceholderNode p = new PlaceholderNode(graph);
            p.setStateAfter(frameState.duplicateWithoutStack(bci));

            ValueNode currentExceptionObject;
            ExceptionObjectNode newObj = null;
            if (exceptionObject == null) {
                newObj = new ExceptionObjectNode(graph);
                currentExceptionObject = newObj;
            } else {
                currentExceptionObject = exceptionObject;
            }
            FrameState stateWithException = frameState.duplicateWithException(bci, currentExceptionObject);
            if (newObj != null) {
                newObj.setStateAfter(stateWithException);
            }
            FixedNode target = createTarget(dispatchBlock, stateWithException);
            if (exceptionObject == null) {
                ExceptionObjectNode eObj = (ExceptionObjectNode) currentExceptionObject;
                eObj.setNext(target);
                p.setNext(eObj);
            } else {
                p.setNext(target);
            }
            return p;
        }
        return null;
    }

    private void genLoadConstant(int cpi) {
        Object con = constantPool.lookupConstant(cpi);

        if (con instanceof RiType) {
            // this is a load of class constant which might be unresolved
            RiType riType = (RiType) con;
            if (!riType.isResolved()) {
                storeResultGraph = false;
                append(new DeoptimizeNode(DeoptAction.InvalidateRecompile, graph));
                frameState.push(CiKind.Object, append(ConstantNode.forObject(null, graph)));
            } else {
                frameState.push(CiKind.Object, append(new ConstantNode(riType.getEncoding(Representation.JavaClass), graph)));
            }
        } else if (con instanceof CiConstant) {
            CiConstant constant = (CiConstant) con;
            frameState.push(constant.kind.stackKind(), appendConstant(constant));
        } else {
            throw new Error("lookupConstant returned an object of incorrect type");
        }
    }

    private void genLoadIndexed(CiKind kind) {
        ValueNode index = frameState.ipop();
        ValueNode array = frameState.apop();
        ValueNode length = append(new ArrayLengthNode(array, graph));
        ValueNode v = append(new LoadIndexedNode(array, index, length, kind, graph));
        frameState.push(kind.stackKind(), v);
    }

    private void genStoreIndexed(CiKind kind) {
        ValueNode value = frameState.pop(kind.stackKind());
        ValueNode index = frameState.ipop();
        ValueNode array = frameState.apop();
        ValueNode length = append(new ArrayLengthNode(array, graph));
        StoreIndexedNode result = new StoreIndexedNode(array, index, length, kind, value, graph);
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
                throw Util.shouldNotReachHere();
        }

    }

    private void genArithmeticOp(CiKind kind, int opcode) {
        genArithmeticOp(kind, opcode, false);
    }

    private void genArithmeticOp(CiKind result, int opcode, boolean canTrap) {
        ValueNode y = frameState.pop(result);
        ValueNode x = frameState.pop(result);
        boolean isStrictFP = isStrict(method.accessFlags());
        ArithmeticNode v;
        switch(opcode){
            case IADD:
            case LADD: v = new IntegerAddNode(result, x, y, graph); break;
            case FADD:
            case DADD: v = new FloatAddNode(result, x, y, isStrictFP, graph); break;
            case ISUB:
            case LSUB: v = new IntegerSubNode(result, x, y, graph); break;
            case FSUB:
            case DSUB: v = new FloatSubNode(result, x, y, isStrictFP, graph); break;
            case IMUL:
            case LMUL: v = new IntegerMulNode(result, x, y, graph); break;
            case FMUL:
            case DMUL: v = new FloatMulNode(result, x, y, isStrictFP, graph); break;
            case IDIV:
            case LDIV: v = new IntegerDivNode(result, x, y, graph); break;
            case FDIV:
            case DDIV: v = new FloatDivNode(result, x, y, isStrictFP, graph); break;
            case IREM:
            case LREM: v = new IntegerRemNode(result, x, y, graph); break;
            case FREM:
            case DREM: v = new FloatRemNode(result, x, y, isStrictFP, graph); break;
            default:
                throw new CiBailout("should not reach");
        }
        ValueNode result1 = append(v);
        if (canTrap) {
            append(new ValueAnchorNode(result1, graph));
        }
        frameState.push(result, result1);
    }

    private void genNegateOp(CiKind kind) {
        frameState.push(kind, append(new NegateNode(frameState.pop(kind), graph)));
    }

    private void genShiftOp(CiKind kind, int opcode) {
        ValueNode s = frameState.ipop();
        ValueNode x = frameState.pop(kind);
        ShiftNode v;
        switch(opcode){
            case ISHL:
            case LSHL: v = new LeftShiftNode(kind, x, s, graph); break;
            case ISHR:
            case LSHR: v = new RightShiftNode(kind, x, s, graph); break;
            case IUSHR:
            case LUSHR: v = new UnsignedRightShiftNode(kind, x, s, graph); break;
            default:
                throw new CiBailout("should not reach");
        }
        frameState.push(kind, append(v));
    }

    private void genLogicOp(CiKind kind, int opcode) {
        ValueNode y = frameState.pop(kind);
        ValueNode x = frameState.pop(kind);
        LogicNode v;
        switch(opcode){
            case IAND:
            case LAND: v = new AndNode(kind, x, y, graph); break;
            case IOR:
            case LOR: v = new OrNode(kind, x, y, graph); break;
            case IXOR:
            case LXOR: v = new XorNode(kind, x, y, graph); break;
            default:
                throw new CiBailout("should not reach");
        }
        frameState.push(kind, append(v));
    }

    private void genCompareOp(CiKind kind, int opcode, CiKind resultKind) {
        ValueNode y = frameState.pop(kind);
        ValueNode x = frameState.pop(kind);
        ValueNode value = append(new NormalizeCompareNode(opcode, resultKind, x, y, graph));
        if (!resultKind.isVoid()) {
            frameState.ipush(value);
        }
    }

    private void genConvert(int opcode, CiKind from, CiKind to) {
        CiKind tt = to.stackKind();
        frameState.push(tt, append(new ConvertNode(opcode, frameState.pop(from.stackKind()), tt, graph)));
    }

    private void genIncrement() {
        int index = stream().readLocalIndex();
        int delta = stream().readIncrement();
        ValueNode x = frameState.localAt(index);
        ValueNode y = append(ConstantNode.forInt(delta, graph));
        frameState.storeLocal(index, append(new IntegerAddNode(CiKind.Int, x, y, graph)));
    }

    private void genGoto(int fromBCI, int toBCI) {
        appendGoto(createTargetAt(toBCI, frameState));
    }

    private void ifNode(ValueNode x, Condition cond, ValueNode y) {
        assert !x.isDeleted() && !y.isDeleted();
        double probability = method.branchProbability(bci());
        if (probability < 0) {
            if (GraalOptions.TraceProbability) {
                TTY.println("missing probability in " + method + " at bci " + bci());
            }
            probability = 0.5;
        }

        IfNode ifNode = new IfNode(new CompareNode(x, cond, y, graph), probability, graph);
        append(ifNode);
        BlockMap.BranchOverride override = branchOverride.get(bci());
        FixedNode tsucc;
        if (override == null || override.taken == false) {
            tsucc = createTargetAt(stream().readBranchDest(), frameState);
        } else {
            tsucc = createTarget(override.block, frameState);
        }
        ifNode.setBlockSuccessor(0, tsucc);
        FixedNode fsucc;
        if (override == null || override.taken == true) {
            fsucc = createTargetAt(stream().nextBCI(), frameState);
        } else {
            fsucc = createTarget(override.block, frameState);
        }
        ifNode.setBlockSuccessor(1, fsucc);
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

    private void genThrow(int bci) {
        ValueNode exception = frameState.apop();
        FixedGuardNode node = new FixedGuardNode(new IsNonNullNode(exception, graph), graph);
        append(node);

        FixedNode entry = handleException(exception, bci);
        if (entry != null) {
            append(entry);
        } else {
            appendGoto(createTarget(unwindBlock(bci), frameState.duplicateWithException(bci, exception)));
        }
    }

    private void genCheckCast() {
        int cpi = stream().readCPI();
        RiType type = constantPool.lookupType(cpi, CHECKCAST);
        ConstantNode typeInstruction = genTypeOrDeopt(RiType.Representation.ObjectHub, type, type.isResolved());
        ValueNode object = frameState.apop();
        if (typeInstruction != null) {
//            InstanceOf instanceOf = new InstanceOf(typeInstruction, object, true, graph);
//            FixedGuard fixedGuard = new FixedGuard(instanceOf, graph);
//            append(fixedGuard);
//            CastNode castNode = new CastNode(object.kind, object, graph);
//            castNode.inputs().add(fixedGuard);
//            frameState.apush(castNode);
            frameState.apush(new CheckCastNode(typeInstruction, object, graph));
        } else {
            frameState.apush(appendConstant(CiConstant.NULL_OBJECT));
        }
    }

    private void genInstanceOf() {
        int cpi = stream().readCPI();
        RiType type = constantPool.lookupType(cpi, INSTANCEOF);
        ConstantNode typeInstruction = genTypeOrDeopt(RiType.Representation.ObjectHub, type, type.isResolved());
        ValueNode object = frameState.apop();
        if (typeInstruction != null) {
            frameState.ipush(append(new MaterializeNode(new InstanceOfNode(typeInstruction, object, false, graph), graph)));
        } else {
            frameState.ipush(appendConstant(CiConstant.INT_0));
        }
    }

    void genNewInstance(int cpi) {
        RiType type = constantPool.lookupType(cpi, NEW);
        if (type.isResolved()) {
            NewInstanceNode n = new NewInstanceNode(type, cpi, constantPool, graph);
            frameState.apush(append(n));
        } else {
            storeResultGraph = false;
            append(new DeoptimizeNode(DeoptAction.InvalidateRecompile, graph));
            frameState.apush(appendConstant(CiConstant.NULL_OBJECT));
        }
    }

    private void genNewTypeArray(int typeCode) {
        CiKind kind = CiKind.fromArrayTypeCode(typeCode);
        RiType elementType = runtime.asRiType(kind);
        NewTypeArrayNode nta = new NewTypeArrayNode(frameState.ipop(), elementType, graph);
        frameState.apush(append(nta));
    }

    private void genNewObjectArray(int cpi) {
        RiType type = constantPool.lookupType(cpi, ANEWARRAY);
        ValueNode length = frameState.ipop();
        if (type.isResolved()) {
            NewArrayNode n = new NewObjectArrayNode(type, length, graph);
            frameState.apush(append(n));
        } else {
            storeResultGraph = false;
            append(new DeoptimizeNode(DeoptAction.InvalidateRecompile, graph));
            frameState.apush(appendConstant(CiConstant.NULL_OBJECT));
        }

    }

    private void genNewMultiArray(int cpi) {
        RiType type = constantPool.lookupType(cpi, MULTIANEWARRAY);
        int rank = stream().readUByte(bci() + 3);
        ValueNode[] dims = new ValueNode[rank];
        for (int i = rank - 1; i >= 0; i--) {
            dims[i] = frameState.ipop();
        }
        if (type.isResolved()) {
            NewArrayNode n = new NewMultiArrayNode(type, dims, cpi, constantPool, graph);
            frameState.apush(append(n));
        } else {
            storeResultGraph = false;
            append(new DeoptimizeNode(DeoptAction.InvalidateRecompile, graph));
            frameState.apush(appendConstant(CiConstant.NULL_OBJECT));
        }
    }

    private void genGetField(int cpi, RiField field) {
        CiKind kind = field.kind();
        ValueNode receiver = frameState.apop();
        if (field.isResolved() && field.holder().isInitialized()) {
            LoadFieldNode load = new LoadFieldNode(receiver, field, graph);
            appendOptimizedLoadField(kind, load);
        } else {
            storeResultGraph = false;
            append(new DeoptimizeNode(DeoptAction.InvalidateRecompile, graph));
            frameState.push(kind.stackKind(), append(ConstantNode.defaultForKind(kind, graph)));
        }
    }

    private void genPutField(int cpi, RiField field) {
        ValueNode value = frameState.pop(field.kind().stackKind());
        ValueNode receiver = frameState.apop();
        if (field.isResolved() && field.holder().isInitialized()) {
            StoreFieldNode store = new StoreFieldNode(receiver, field, value, graph);
            appendOptimizedStoreField(store);
        } else {
            storeResultGraph = false;
            append(new DeoptimizeNode(DeoptAction.InvalidateRecompile, graph));
        }
    }

    private void genGetStatic(int cpi, RiField field) {
        RiType holder = field.holder();
        boolean isInitialized = field.isResolved() && holder.isInitialized();
        CiConstant constantValue = null;
        if (isInitialized) {
            constantValue = field.constantValue(null);
        }
        if (constantValue != null) {
            frameState.push(constantValue.kind.stackKind(), appendConstant(constantValue));
        } else {
            ValueNode container = genTypeOrDeopt(RiType.Representation.StaticFields, holder, isInitialized);
            CiKind kind = field.kind();
            if (container != null) {
                LoadFieldNode load = new LoadFieldNode(container, field, graph);
                appendOptimizedLoadField(kind, load);
            } else {
                // deopt will be generated by genTypeOrDeopt, not needed here
                frameState.push(kind.stackKind(), append(ConstantNode.defaultForKind(kind, graph)));
            }
        }
    }

    private void genPutStatic(int cpi, RiField field) {
        RiType holder = field.holder();
        ValueNode container = genTypeOrDeopt(RiType.Representation.StaticFields, holder, field.isResolved() && holder.isInitialized());
        ValueNode value = frameState.pop(field.kind().stackKind());
        if (container != null) {
            StoreFieldNode store = new StoreFieldNode(container, field, value, graph);
            appendOptimizedStoreField(store);
        } else {
            // deopt will be generated by genTypeOrDeopt, not needed here
        }
    }

    private ConstantNode genTypeOrDeopt(RiType.Representation representation, RiType holder, boolean initialized) {
        if (initialized) {
            return appendConstant(holder.getEncoding(representation));
        } else {
            storeResultGraph = false;
            append(new DeoptimizeNode(DeoptAction.InvalidateRecompile, graph));
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

    private void genInvokeStatic(RiMethod target, int cpi, RiConstantPool constantPool) {
        RiType holder = target.holder();
        boolean isInitialized = target.isResolved() && holder.isInitialized();
        if (!isInitialized && GraalOptions.ResolveClassBeforeStaticInvoke) {
            // Re-use the same resolution code as for accessing a static field. Even though
            // the result of resolution is not used by the invocation (only the side effect
            // of initialization is required), it can be commoned with static field accesses.
            genTypeOrDeopt(RiType.Representation.StaticFields, holder, isInitialized);
        }
        ValueNode[] args = frameState.popArguments(target.signature().argumentSlots(false));
        appendInvoke(INVOKESTATIC, target, args, cpi, constantPool);
    }

    private void genInvokeInterface(RiMethod target, int cpi, RiConstantPool constantPool) {
        ValueNode[] args = frameState.popArguments(target.signature().argumentSlots(true));
        genInvokeIndirect(INVOKEINTERFACE, target, args, cpi, constantPool);

    }

    private void genInvokeVirtual(RiMethod target, int cpi, RiConstantPool constantPool) {
        ValueNode[] args = frameState.popArguments(target.signature().argumentSlots(true));
        genInvokeIndirect(INVOKEVIRTUAL, target, args, cpi, constantPool);

    }

    private void genInvokeSpecial(RiMethod target, RiType knownHolder, int cpi, RiConstantPool constantPool) {
        assert target != null;
        assert target.signature() != null;
        ValueNode[] args = frameState.popArguments(target.signature().argumentSlots(true));
        invokeDirect(target, args, knownHolder, cpi, constantPool);

    }

    private void genInvokeIndirect(int opcode, RiMethod target, ValueNode[] args, int cpi, RiConstantPool constantPool) {
        ValueNode receiver = args[0];
        // attempt to devirtualize the call
        if (target.isResolved()) {
            RiType klass = target.holder();

            // 0. check for trivial cases
            if (target.canBeStaticallyBound() && !isAbstract(target.accessFlags())) {
                // check for trivial cases (e.g. final methods, nonvirtual methods)
                invokeDirect(target, args, target.holder(), cpi, constantPool);
                return;
            }
            // 1. check if the exact type of the receiver can be determined
            RiType exact = getExactType(klass, receiver);
            if (exact != null && exact.isResolved()) {
                // either the holder class is exact, or the receiver object has an exact type
                invokeDirect(exact.resolveMethodImpl(target), args, exact, cpi, constantPool);
                return;
            }
        }
        // devirtualization failed, produce an actual invokevirtual
        appendInvoke(opcode, target, args, cpi, constantPool);
    }

    private CiKind returnKind(RiMethod target) {
        return target.signature().returnKind();
    }

    private void invokeDirect(RiMethod target, ValueNode[] args, RiType knownHolder, int cpi, RiConstantPool constantPool) {
        appendInvoke(INVOKESPECIAL, target, args, cpi, constantPool);
    }

    private void appendInvoke(int opcode, RiMethod target, ValueNode[] args, int cpi, RiConstantPool constantPool) {
        CiKind resultType = returnKind(target);
        if (GraalOptions.DeoptALot) {
            storeResultGraph = false;
            DeoptimizeNode deoptimize = new DeoptimizeNode(DeoptAction.None, graph);
            deoptimize.setMessage("invoke " + target.name());
            append(deoptimize);
            frameState.pushReturn(resultType, ConstantNode.defaultForKind(resultType, graph));
        } else {
            InvokeNode invoke = new InvokeNode(bci(), opcode, resultType.stackKind(), args, target, target.signature().returnType(method.holder()), graph);
            ValueNode result = appendWithBCI(invoke);
            invoke.setExceptionEdge(handleException(null, bci()));
//            if (invoke.exceptionEdge() == null) {
//                TTY.println("no exception edge" + unwindHandler);
//            }
            frameState.pushReturn(resultType, result);
        }
    }

    private RiType getExactType(RiType staticType, ValueNode receiver) {
        RiType exact = staticType.exactType();
        if (exact == null) {
            exact = receiver.exactType();
            if (exact == null) {
                if (receiver.isConstant()) {
                    exact = runtime.getTypeOf(receiver.asConstant());
                }
                if (exact == null) {
                    RiType declared = receiver.declaredType();
                    exact = declared == null || !declared.isResolved() ? null : declared.exactType();
                }
            }
        }
        return exact;
    }

    private void callRegisterFinalizer() {
        // append a call to the finalizer registration
        append(new RegisterFinalizerNode(frameState.loadLocal(0), graph));
    }

    private void genReturn(ValueNode x) {
        frameState.clearStack();
        if (x != null) {
            frameState.push(x.kind, x);
        }
        appendGoto(createTarget(returnBlock(bci()), frameState));
    }

    private void genMonitorEnter(ValueNode x, int bci) {
        int lockNumber = frameState.locksSize();
        MonitorAddressNode lockAddress = null;
        if (runtime.sizeOfBasicObjectLock() != 0) {
            lockAddress = new MonitorAddressNode(lockNumber, graph);
            append(lockAddress);
        }
        MonitorEnterNode monitorEnter = new MonitorEnterNode(x, lockAddress, lockNumber, graph);
        appendWithBCI(monitorEnter);
        frameState.lock(x);
        if (bci == FixedWithNextNode.SYNCHRONIZATION_ENTRY_BCI) {
            monitorEnter.setStateAfter(frameState.create(0));
        }
    }

    private void genMonitorExit(ValueNode x) {
        int lockNumber = frameState.locksSize() - 1;
        if (lockNumber < 0) {
            throw new CiBailout("monitor stack underflow");
        }
        MonitorAddressNode lockAddress = null;
        if (runtime.sizeOfBasicObjectLock() != 0) {
            lockAddress = new MonitorAddressNode(lockNumber, graph);
            append(lockAddress);
        }
        appendWithBCI(new MonitorExitNode(x, lockAddress, lockNumber, graph));
        frameState.unlock();
    }

    private void genJsr(int dest) {
        throw new JSRNotSupportedBailout();
    }

    private void genRet(int localIndex) {
        throw new JSRNotSupportedBailout();
    }

    private void genTableswitch() {
        int bci = bci();
        ValueNode value = frameState.ipop();
        BytecodeTableSwitch ts = new BytecodeTableSwitch(stream(), bci);
        int max = ts.numberOfCases();
        List<FixedWithNextNode> list = new ArrayList<FixedWithNextNode>(max + 1);
        List<Integer> offsetList = new ArrayList<Integer>(max + 1);
        for (int i = 0; i < max; i++) {
            // add all successors to the successor list
            int offset = ts.offsetAt(i);
            list.add(null);
            offsetList.add(offset);
        }
        int offset = ts.defaultOffset();
        list.add(null);
        offsetList.add(offset);
        TableSwitchNode tableSwitch = new TableSwitchNode(value, list, ts.lowKey(), switchProbability(list.size(), bci), graph);
        for (int i = 0; i < offsetList.size(); ++i) {
            tableSwitch.setBlockSuccessor(i, createTargetAt(bci + offsetList.get(i), frameState));
        }
        append(tableSwitch);
    }

    private double[] switchProbability(int numberOfCases, int bci) {
        double[] prob = method.switchProbability(bci);
        if (prob != null) {
            assert prob.length == numberOfCases;
        } else {
            if (GraalOptions.TraceProbability) {
                TTY.println("Missing probability (switch) in " + method + " at bci " + bci);
            }
            prob = new double[numberOfCases];
            for (int i = 0; i < numberOfCases; i++) {
                prob[i] = 1.0d / numberOfCases;
            }
        }
        return prob;
    }

    private void genLookupswitch() {
        int bci = bci();
        ValueNode value = frameState.ipop();
        BytecodeLookupSwitch ls = new BytecodeLookupSwitch(stream(), bci);
        int max = ls.numberOfCases();
        List<FixedWithNextNode> list = new ArrayList<FixedWithNextNode>(max + 1);
        List<Integer> offsetList = new ArrayList<Integer>(max + 1);
        int[] keys = new int[max];
        for (int i = 0; i < max; i++) {
            // add all successors to the successor list
            int offset = ls.offsetAt(i);
            list.add(null);
            offsetList.add(offset);
            keys[i] = ls.keyAt(i);
        }
        int offset = ls.defaultOffset();
        list.add(null);
        offsetList.add(offset);
        LookupSwitchNode lookupSwitch = new LookupSwitchNode(value, list, keys, switchProbability(list.size(), bci), graph);
        for (int i = 0; i < offsetList.size(); ++i) {
            lookupSwitch.setBlockSuccessor(i, createTargetAt(bci + offsetList.get(i), frameState));
        }
        append(lookupSwitch);
    }

    private ConstantNode appendConstant(CiConstant constant) {
        return new ConstantNode(constant, graph);
    }

    private ValueNode append(FixedNode fixed) {
        if (fixed instanceof DeoptimizeNode && lastInstr.predecessor() != null) {
            Node cur = lastInstr;
            Node prev = cur;
            while (cur != cur.graph().start() && !(cur instanceof ControlSplitNode)) {
                assert cur.predecessor() != null;
                prev = cur;
                cur = cur.predecessor();
                if (cur.predecessor() == null) {
                    break;
                }

                if (cur instanceof ExceptionObjectNode) {
                    break;
                }
            }

            if (cur instanceof IfNode) {
                IfNode ifNode = (IfNode) cur;
                if (ifNode.falseSuccessor() == prev) {
                    FixedNode successor = ifNode.trueSuccessor();
                    ifNode.setTrueSuccessor(null);
                    BooleanNode condition = ifNode.compare();
                    FixedGuardNode fixedGuard = new FixedGuardNode(condition, graph);
                    fixedGuard.setNext(successor);
                    ifNode.replaceAndDelete(fixedGuard);
                    lastInstr = null;
                    return fixed;
                }
            } else if (prev != cur) {
                prev.replaceAtPredecessors(fixed);
                lastInstr = null;
                return fixed;
            }
        }
        lastInstr.setNext(fixed);
        lastInstr = null;
        return fixed;
    }

    private ValueNode append(FixedWithNextNode x) {
        return appendWithBCI(x);
    }

    private ValueNode append(ValueNode v) {
        return v;
    }

    private ValueNode appendWithBCI(FixedWithNextNode x) {
        assert x.predecessor() == null : "instruction should not have been appended yet";
        assert lastInstr.next() == null : "cannot append instruction to instruction which isn't end (" + lastInstr + "->" + lastInstr.next() + ")";
        lastInstr.setNext(x);
        lastInstr = x;
        return x;
    }

    private FixedNode createTargetAt(int bci, FrameStateAccess stateAfter) {
        return createTarget(blockFromBci[bci], stateAfter);
    }

    private FixedNode createTarget(Block block, FrameStateAccess stateAfter) {
        assert block != null && stateAfter != null;
        assert block.isLoopHeader || block.firstInstruction == null || block.firstInstruction.next() == null :
            "non-loop block must be iterated after all its predecessors. startBci=" + block.startBci + ", " + block.getClass().getSimpleName() + ", " + block.firstInstruction.next();

        if (block.isExceptionEntry) {
            assert stateAfter.stackSize() == 1;
        }

        if (block.firstInstruction == null) {
            if (block.isLoopHeader) {
                LoopBeginNode loopBegin = new LoopBeginNode(graph);
                loopBegin.addEnd(new EndNode(graph));
                LoopEndNode loopEnd = new LoopEndNode(graph);
                loopEnd.setLoopBegin(loopBegin);
                PlaceholderNode pBegin = new PlaceholderNode(graph);
                pBegin.setNext(loopBegin.forwardEdge());
                PlaceholderNode pEnd = new PlaceholderNode(graph);
                pEnd.setNext(loopEnd);
                loopBegin.setStateAfter(stateAfter.duplicate(block.startBci));
                block.firstInstruction = pBegin;
            } else {
                block.firstInstruction = new PlaceholderNode(graph);
            }
        }
        mergeOrClone(block, stateAfter);
        addToWorkList(block);

        FixedNode result = null;
        if (block.isLoopHeader && isVisited(block)) {
            result = (StateSplit) loopBegin(block).loopEnd().predecessor();
        } else {
            result = block.firstInstruction;
        }

        assert result instanceof MergeNode || result instanceof PlaceholderNode : result;

        if (result instanceof MergeNode) {
            if (result instanceof LoopBeginNode) {
                result = ((LoopBeginNode) result).forwardEdge();
            } else {
                EndNode end = new EndNode(graph);
                ((MergeNode) result).addEnd(end);
                PlaceholderNode p = new PlaceholderNode(graph);
                int bci = block.startBci;
                if (block instanceof ExceptionBlock) {
                    bci = ((ExceptionBlock) block).deoptBci;
                }
                p.setStateAfter(stateAfter.duplicate(bci));
                p.setNext(end);
                result = p;
            }
        }
        assert !(result instanceof LoopBeginNode || result instanceof MergeNode);
        return result;
    }

    private ValueNode synchronizedObject(FrameStateAccess state, RiMethod target) {
        if (isStatic(target.accessFlags())) {
            ConstantNode classConstant = new ConstantNode(target.holder().getEncoding(Representation.JavaClass), graph);
            return append(classConstant);
        } else {
            return state.localAt(0);
        }
    }

    private void iterateAllBlocks() {
        Block block;
        while ((block = removeFromWorkList()) != null) {

            // remove blocks that have no predecessors by the time it their bytecodes are parsed
            if (block.firstInstruction == null) {
                markVisited(block);
                continue;
            }

            if (!isVisited(block)) {
                markVisited(block);
                // now parse the block
                if (block.isLoopHeader) {
                    LoopBeginNode begin = loopBegin(block);
                    FrameState preLoopState = ((StateSplit) block.firstInstruction).stateAfter();
                    assert preLoopState != null;
                    FrameState duplicate = preLoopState.duplicate(preLoopState.bci);
                    begin.setStateAfter(duplicate);
                    insertLoopPhis(begin, duplicate);
                    lastInstr = begin;
                } else {
                    lastInstr = block.firstInstruction;
                }
                frameState.initializeFrom(((StateSplit) lastInstr).stateAfter());
                assert lastInstr.next() == null : "instructions already appended at block " + block.blockID;

                if (block == returnBlock) {
                    createReturnBlock(block);
                } else if (block == unwindBlock) {
                    createUnwindBlock(block);
                } else if (block instanceof ExceptionBlock) {
                    createExceptionDispatch((ExceptionBlock) block);
                } else if (block instanceof DeoptBlock) {
                    createDeoptBlock((DeoptBlock) block);
                } else {
                    frameState.setRethrowException(false);
                    iterateBytecodesForBlock(block);
                }
            }
        }
        for (Block b : blocksVisited) {
            if (b.isLoopHeader) {
                LoopBeginNode begin = loopBegin(b);
                LoopEndNode loopEnd = begin.loopEnd();
                StateSplit loopEndPred = (StateSplit) loopEnd.predecessor();

//              This can happen with degenerated loops like this one:
//                for (;;) {
//                    try {
//                        break;
//                    } catch (UnresolvedException iioe) {
//                    }
//                }
                if (loopEndPred.stateAfter() != null) {
                    //loopHeaderMerge.stateBefore().merge(begin, end.stateBefore());
                    //assert loopHeaderMerge.equals(end.stateBefore());
                    begin.stateAfter().merge(begin, loopEndPred.stateAfter());
                } else {
                    loopEndPred.delete();
                    loopEnd.delete();
                    MergeNode merge = new MergeNode(graph);
                    merge.addEnd(begin.forwardEdge());
                    FixedNode next = begin.next();
                    begin.setNext(null);
                    merge.setNext(next);
                    merge.setStateAfter(begin.stateAfter());
                    begin.replaceAndDelete(merge);
                }
            }
        }
    }

    private static LoopBeginNode loopBegin(Block block) {
        EndNode endNode = (EndNode) block.firstInstruction.next();
        LoopBeginNode loopBegin = (LoopBeginNode) endNode.merge();
        return loopBegin;
    }

    private void createDeoptBlock(DeoptBlock block) {
        storeResultGraph = false;
        append(new DeoptimizeNode(DeoptAction.InvalidateReprofile, graph));
    }

    private void createUnwindBlock(Block block) {
        if (Modifier.isSynchronized(method.accessFlags())) {
            genMonitorExit(methodSynchronizedObject);
        }
        UnwindNode unwindNode = new UnwindNode(frameState.apop(), graph);
        graph.setUnwind(unwindNode);
        append(unwindNode);
    }

    private void createReturnBlock(Block block) {
        if (method.isConstructor() && method.holder().superType() == null) {
            callRegisterFinalizer();
        }
        CiKind returnKind = method.signature().returnKind().stackKind();
        ValueNode x = returnKind == CiKind.Void ? null : frameState.pop(returnKind);
        assert frameState.stackSize() == 0;

        if (Modifier.isSynchronized(method.accessFlags())) {
            genMonitorExit(methodSynchronizedObject);
        }
        ReturnNode returnNode = new ReturnNode(x, graph);
        graph.setReturn(returnNode);
        append(returnNode);
    }

    private void createExceptionDispatch(ExceptionBlock block) {
        if (block.handler == null) {
            assert frameState.stackSize() == 1 : "only exception object expected on stack, actual size: " + frameState.stackSize();
            createUnwindBlock(block);
        } else {
            assert frameState.stackSize() == 1 : frameState;

            Block nextBlock = block.next == null ? unwindBlock(block.deoptBci) : block.next;


            RiType catchType = block.handler.catchType();
            ConstantNode typeInstruction = genTypeOrDeopt(RiType.Representation.ObjectHub, catchType, catchType.isResolved());
            if (typeInstruction != null) {
                FixedNode catchSuccessor = createTarget(blockFromBci[block.handler.handlerBCI()], frameState);
                FixedNode nextDispatch = createTarget(nextBlock, frameState);
                ValueNode exception = frameState.stackAt(0);
                IfNode ifNode = new IfNode(new InstanceOfNode(typeInstruction, exception, false, graph), 0.5, graph);
                append(ifNode);
                ifNode.setTrueSuccessor(catchSuccessor);
                ifNode.setFalseSuccessor(nextDispatch);
            }
        }
    }

    private void appendGoto(FixedNode target) {
        if (lastInstr != null) {
            lastInstr.setNext(target);
        }
    }

    private void iterateBytecodesForBlock(Block block) {
        assert frameState != null;

        stream.setBCI(block.startBci);

        int endBCI = stream.endBCI();

        int bci = block.startBci;
        while (bci < endBCI) {
            Block nextBlock = blockFromBci[bci];
            if (nextBlock != null && nextBlock != block) {
                assert !nextBlock.isExceptionEntry;
                // we fell through to the next block, add a goto and break
                appendGoto(createTarget(nextBlock, frameState));
                break;
            }
            // read the opcode
            int opcode = stream.currentBC();
            traceState();
            traceInstruction(bci, opcode, bci == block.startBci);
            processBytecode(bci, opcode);

            if (lastInstr == null || IdentifyBlocksPhase.isBlockEnd(lastInstr) || lastInstr.next() != null) {
                break;
            }

            stream.next();
            bci = stream.currentBCI();
            if (lastInstr instanceof StateSplit) {
                StateSplit stateSplit = (StateSplit) lastInstr;
                if (stateSplit.stateAfter() == null && stateSplit.needsStateAfter()) {
                    stateSplit.setStateAfter(frameState.create(bci));
                }
            }
        }
    }

    private void traceState() {
        if (GraalOptions.TraceBytecodeParserLevel >= TRACELEVEL_STATE && !TTY.isSuppressed()) {
            log.println(String.format("|   state [nr locals = %d, stack depth = %d, method = %s]", frameState.localsSize(), frameState.stackSize(), method));
            for (int i = 0; i < frameState.localsSize(); ++i) {
                ValueNode value = frameState.localAt(i);
                log.println(String.format("|   local[%d] = %-8s : %s", i, value == null ? "bogus" : value.kind.javaName, value));
            }
            for (int i = 0; i < frameState.stackSize(); ++i) {
                ValueNode value = frameState.stackAt(i);
                log.println(String.format("|   stack[%d] = %-8s : %s", i, value == null ? "bogus" : value.kind.javaName, value));
            }
            for (int i = 0; i < frameState.locksSize(); ++i) {
                ValueNode value = frameState.lockAt(i);
                log.println(String.format("|   lock[%d] = %-8s : %s", i, value == null ? "bogus" : value.kind.javaName, value));
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
            case LDC2_W         : genLoadConstant(stream.readCPI()); break;
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
            case IMUL           : genArithmeticOp(CiKind.Int, opcode); break;
            case IDIV           : // fall through
            case IREM           : genArithmeticOp(CiKind.Int, opcode, true); break;
            case LADD           : // fall through
            case LSUB           : // fall through
            case LMUL           : genArithmeticOp(CiKind.Long, opcode); break;
            case LDIV           : // fall through
            case LREM           : genArithmeticOp(CiKind.Long, opcode, true); break;
            case FADD           : // fall through
            case FSUB           : // fall through
            case FMUL           : // fall through
            case FDIV           : // fall through
            case FREM           : genArithmeticOp(CiKind.Float, opcode); break;
            case DADD           : // fall through
            case DSUB           : // fall through
            case DMUL           : // fall through
            case DDIV           : // fall through
            case DREM           : genArithmeticOp(CiKind.Double, opcode); break;
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
            case I2L            : genConvert(opcode, CiKind.Int   , CiKind.Long  ); break;
            case I2F            : genConvert(opcode, CiKind.Int   , CiKind.Float ); break;
            case I2D            : genConvert(opcode, CiKind.Int   , CiKind.Double); break;
            case L2I            : genConvert(opcode, CiKind.Long  , CiKind.Int   ); break;
            case L2F            : genConvert(opcode, CiKind.Long  , CiKind.Float ); break;
            case L2D            : genConvert(opcode, CiKind.Long  , CiKind.Double); break;
            case F2I            : genConvert(opcode, CiKind.Float , CiKind.Int   ); break;
            case F2L            : genConvert(opcode, CiKind.Float , CiKind.Long  ); break;
            case F2D            : genConvert(opcode, CiKind.Float , CiKind.Double); break;
            case D2I            : genConvert(opcode, CiKind.Double, CiKind.Int   ); break;
            case D2L            : genConvert(opcode, CiKind.Double, CiKind.Long  ); break;
            case D2F            : genConvert(opcode, CiKind.Double, CiKind.Float ); break;
            case I2B            : genConvert(opcode, CiKind.Int   , CiKind.Byte  ); break;
            case I2C            : genConvert(opcode, CiKind.Int   , CiKind.Char  ); break;
            case I2S            : genConvert(opcode, CiKind.Int   , CiKind.Short ); break;
            case LCMP           : genCompareOp(CiKind.Long, opcode, CiKind.Int); break;
            case FCMPL          : genCompareOp(CiKind.Float, opcode, CiKind.Int); break;
            case FCMPG          : genCompareOp(CiKind.Float, opcode, CiKind.Int); break;
            case DCMPL          : genCompareOp(CiKind.Double, opcode, CiKind.Int); break;
            case DCMPG          : genCompareOp(CiKind.Double, opcode, CiKind.Int); break;
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
            case IF_ACMPEQ      : genIfSame(frameState.peekKind(), Condition.EQ); break;
            case IF_ACMPNE      : genIfSame(frameState.peekKind(), Condition.NE); break;
            case GOTO           : genGoto(stream.currentBCI(), stream.readBranchDest()); break;
            case JSR            : genJsr(stream.readBranchDest()); break;
            case RET            : genRet(stream.readLocalIndex()); break;
            case TABLESWITCH    : genTableswitch(); break;
            case LOOKUPSWITCH   : genLookupswitch(); break;
            case IRETURN        : genReturn(frameState.ipop()); break;
            case LRETURN        : genReturn(frameState.lpop()); break;
            case FRETURN        : genReturn(frameState.fpop()); break;
            case DRETURN        : genReturn(frameState.dpop()); break;
            case ARETURN        : genReturn(frameState.apop()); break;
            case RETURN         : genReturn(null  ); break;
            case GETSTATIC      : cpi = stream.readCPI(); genGetStatic(cpi, constantPool.lookupField(cpi, opcode)); break;
            case PUTSTATIC      : cpi = stream.readCPI(); genPutStatic(cpi, constantPool.lookupField(cpi, opcode)); break;
            case GETFIELD       : cpi = stream.readCPI(); genGetField(cpi, constantPool.lookupField(cpi, opcode)); break;
            case PUTFIELD       : cpi = stream.readCPI(); genPutField(cpi, constantPool.lookupField(cpi, opcode)); break;
            case INVOKEVIRTUAL  : cpi = stream.readCPI(); genInvokeVirtual(constantPool.lookupMethod(cpi, opcode), cpi, constantPool); break;
            case INVOKESPECIAL  : cpi = stream.readCPI(); genInvokeSpecial(constantPool.lookupMethod(cpi, opcode), null, cpi, constantPool); break;
            case INVOKESTATIC   : cpi = stream.readCPI(); genInvokeStatic(constantPool.lookupMethod(cpi, opcode), cpi, constantPool); break;
            case INVOKEINTERFACE: cpi = stream.readCPI(); genInvokeInterface(constantPool.lookupMethod(cpi, opcode), cpi, constantPool); break;
            case NEW            : genNewInstance(stream.readCPI()); break;
            case NEWARRAY       : genNewTypeArray(stream.readLocalIndex()); break;
            case ANEWARRAY      : genNewObjectArray(stream.readCPI()); break;
            case ARRAYLENGTH    : genArrayLength(); break;
            case ATHROW         : genThrow(stream.currentBCI()); break;
            case CHECKCAST      : genCheckCast(); break;
            case INSTANCEOF     : genInstanceOf(); break;
            case MONITORENTER   : genMonitorEnter(frameState.apop(), stream.currentBCI()); break;
            case MONITOREXIT    : genMonitorExit(frameState.apop()); break;
            case MULTIANEWARRAY : genNewMultiArray(stream.readCPI()); break;
            case IFNULL         : genIfNull(Condition.EQ); break;
            case IFNONNULL      : genIfNull(Condition.NE); break;
            case GOTO_W         : genGoto(stream.currentBCI(), stream.readFarBranchDest()); break;
            case JSR_W          : genJsr(stream.readFarBranchDest()); break;
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
            log.println(sb.toString());
        }
    }

    private void genArrayLength() {
        frameState.ipush(append(new ArrayLengthNode(frameState.apop(), graph)));
    }

    /**
     * Adds a block to the worklist, if it is not already in the worklist.
     * This method will keep the worklist topologically stored (i.e. the lower
     * DFNs are earlier in the list).
     * @param block the block to add to the work list
     */
    private void addToWorkList(Block block) {
        if (!isOnWorkList(block)) {
            markOnWorkList(block);
            sortIntoWorkList(block);
        }
    }

    private void sortIntoWorkList(Block top) {
        workList.offer(top);
    }

    /**
     * Removes the next block from the worklist. The list is sorted topologically, so the
     * block with the lowest depth first number in the list will be removed and returned.
     * @return the next block from the worklist; {@code null} if there are no blocks
     * in the worklist
     */
    private Block removeFromWorkList() {
        return workList.poll();
    }
}
