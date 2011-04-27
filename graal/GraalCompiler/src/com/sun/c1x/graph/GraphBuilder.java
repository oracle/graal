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
package com.sun.c1x.graph;

import static com.sun.cri.bytecode.Bytecodes.*;
import static java.lang.reflect.Modifier.*;

import java.lang.reflect.*;
import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.graph.ScopeData.ReturnBlock;
import com.sun.c1x.ir.*;
import com.sun.c1x.ir.Value.Flag;
import com.sun.c1x.opt.*;
import com.sun.c1x.util.*;
import com.sun.c1x.value.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.bytecode.Bytecodes.JniOp;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;
import com.sun.cri.ri.RiType.Representation;

/**
 * The {@code GraphBuilder} class parses the bytecode of a method and builds the IR graph.
 * A number of optimizations may be performed during parsing of the bytecode, including value
 * numbering, inlining, constant folding, strength reduction, etc.
 *
 * @author Ben L. Titzer
 * @author Doug Simon
 */
public final class GraphBuilder {

    /**
     * The minimum value to which {@link C1XOptions#TraceBytecodeParserLevel} must be set to trace
     * the bytecode instructions as they are parsed.
     */
    public static final int TRACELEVEL_INSTRUCTIONS = 1;

    /**
     * The minimum value to which {@link C1XOptions#TraceBytecodeParserLevel} must be set to trace
     * the frame state before each bytecode instruction as it is parsed.
     */
    public static final int TRACELEVEL_STATE = 2;

    final IR ir;
    final C1XCompilation compilation;
    final CiStatistics stats;

    /**
     * Map used to implement local value numbering for the current block.
     */
    final ValueMap localValueMap;

    /**
     * Map used for local load elimination (i.e. within the current block).
     */
    final MemoryMap memoryMap;

    final Canonicalizer canonicalizer;     // canonicalizer which does strength reduction + constant folding
    ScopeData scopeData;                   // Per-scope data; used for inlining
    BlockBegin curBlock;                   // the current block
    MutableFrameState curState;            // the current execution state
    Instruction lastInstr;                 // the last instruction added
    final LogStream log;

    boolean skipBlock;                     // skip processing of the rest of this block
    private Value rootMethodSynchronizedObject;

    /**
     * Creates a new, initialized, {@code GraphBuilder} instance for a given compilation.
     *
     * @param compilation the compilation
     * @param ir the IR to build the graph into
     */
    public GraphBuilder(C1XCompilation compilation, IR ir) {
        this.compilation = compilation;
        this.ir = ir;
        this.stats = compilation.stats;
        this.memoryMap = C1XOptions.OptLocalLoadElimination ? new MemoryMap() : null;
        this.localValueMap = C1XOptions.OptLocalValueNumbering ? new ValueMap() : null;
        this.canonicalizer = C1XOptions.OptCanonicalize ? new Canonicalizer(compilation.runtime, compilation.method, compilation.target) : null;
        log = C1XOptions.TraceBytecodeParserLevel > 0 ? new LogStream(TTY.out()) : null;
    }

    /**
     * Builds the graph for a the specified {@code IRScope}.
     * @param scope the top IRScope
     */
    public void build(IRScope scope) {
        RiMethod rootMethod = compilation.method;

        if (log != null) {
            log.println();
            log.println("Compiling " + compilation.method);
        }

        // 1. create the start block
        ir.startBlock = new BlockBegin(0, ir.nextBlockNumber());
        BlockBegin startBlock = ir.startBlock;

        // 2. compute the block map and get the entrypoint(s)
        BlockMap blockMap = compilation.getBlockMap(scope.method, compilation.osrBCI);
        BlockBegin stdEntry = blockMap.get(0);
        BlockBegin osrEntry = compilation.osrBCI < 0 ? null : blockMap.get(compilation.osrBCI);
        pushRootScope(scope, blockMap, startBlock);
        MutableFrameState initialState = stateAtEntry(rootMethod);
        startBlock.mergeOrClone(initialState);
        BlockBegin syncHandler = null;

        // 3. setup internal state for appending instructions
        curBlock = startBlock;
        lastInstr = startBlock;
        lastInstr.setNext(null, -1);
        curState = initialState;

        if (isSynchronized(rootMethod.accessFlags())) {
            // 4A.1 add a monitor enter to the start block
            rootMethodSynchronizedObject = synchronizedObject(initialState, compilation.method);
            genMonitorEnter(rootMethodSynchronizedObject, Instruction.SYNCHRONIZATION_ENTRY_BCI);
            // 4A.2 finish the start block
            finishStartBlock(startBlock, stdEntry, osrEntry);

            // 4A.3 setup an exception handler to unlock the root method synchronized object
            syncHandler = new BlockBegin(Instruction.SYNCHRONIZATION_ENTRY_BCI, ir.nextBlockNumber());
            syncHandler.setExceptionEntry();
            syncHandler.setBlockFlag(BlockBegin.BlockFlag.IsOnWorkList);
            syncHandler.setBlockFlag(BlockBegin.BlockFlag.DefaultExceptionHandler);

            ExceptionHandler h = new ExceptionHandler(new CiExceptionHandler(0, rootMethod.code().length, -1, 0, null));
            h.setEntryBlock(syncHandler);
            scopeData.addExceptionHandler(h);
        } else {
            // 4B.1 simply finish the start block
            finishStartBlock(startBlock, stdEntry, osrEntry);
        }

        // 5.
        C1XIntrinsic intrinsic = C1XOptions.OptIntrinsify ? C1XIntrinsic.getIntrinsic(rootMethod) : null;
        if (intrinsic != null) {
            lastInstr = stdEntry;
            // 6A.1 the root method is an intrinsic; load the parameters onto the stack and try to inline it
            if (C1XOptions.OptIntrinsify && osrEntry == null) {
                // try to inline an Intrinsic node
                boolean isStatic = Modifier.isStatic(rootMethod.accessFlags());
                int argsSize = rootMethod.signature().argumentSlots(!isStatic);
                Value[] args = new Value[argsSize];
                for (int i = 0; i < args.length; i++) {
                    args[i] = curState.localAt(i);
                }
                if (tryInlineIntrinsic(rootMethod, args, isStatic, intrinsic)) {
                    // intrinsic inlining succeeded, add the return node
                    CiKind rt = returnKind(rootMethod).stackKind();
                    Value result = null;
                    if (rt != CiKind.Void) {
                        result = pop(rt);
                    }
                    genReturn(result);
                    BlockEnd end = (BlockEnd) lastInstr;
                    stdEntry.setEnd(end);
                    end.setStateAfter(curState.immutableCopy(bci()));
                }  else {
                    // try intrinsic failed; do the normal parsing
                    scopeData.addToWorkList(stdEntry);
                    iterateAllBlocks();
                }
            } else {
                // 6B.1 do the normal parsing
                scopeData.addToWorkList(stdEntry);
                iterateAllBlocks();
            }
        } else {
            RiType accessor = openAccessorScope(rootMethod);

            // 6B.1 do the normal parsing
            scopeData.addToWorkList(stdEntry);
            iterateAllBlocks();

            closeAccessorScope(accessor);
        }

        if (syncHandler != null && syncHandler.stateBefore() != null) {
            // generate unlocking code if the exception handler is reachable
            fillSyncHandler(rootMethodSynchronizedObject, syncHandler, false);
        }

        if (compilation.osrBCI >= 0) {
            BlockBegin osrBlock = blockMap.get(compilation.osrBCI);
            assert osrBlock.wasVisited();
            if (!osrBlock.stateBefore().stackEmpty()) {
                throw new CiBailout("cannot OSR with non-empty stack");
            }
        }
    }

    private void closeAccessorScope(RiType accessor) {
        if (accessor != null) {
            boundAccessor.set(null);
        }
    }

    private RiType openAccessorScope(RiMethod rootMethod) {
        RiType accessor = rootMethod.accessor();
        if (accessor != null) {
            assert boundAccessor.get() == null;
            boundAccessor.set(accessor);

            // What looks like an object receiver in the bytecode may not be a word value
            compilation.setNotTypesafe();
        }
        return accessor;
    }

    private void finishStartBlock(BlockBegin startBlock, BlockBegin stdEntry, BlockBegin osrEntry) {
        assert curBlock == startBlock;
        Base base = new Base(stdEntry, osrEntry);
        appendWithoutOptimization(base, 0);
        FrameState stateAfter = curState.immutableCopy(bci());
        base.setStateAfter(stateAfter);
        startBlock.setEnd(base);
        assert stdEntry.stateBefore() == null;
        stdEntry.mergeOrClone(stateAfter);
    }

    void pushRootScope(IRScope scope, BlockMap blockMap, BlockBegin start) {
        BytecodeStream stream = new BytecodeStream(scope.method.code());
        RiConstantPool constantPool = compilation.runtime.getConstantPool(scope.method);
        scopeData = new ScopeData(null, scope, blockMap, stream, constantPool);
        curBlock = start;
    }

    public boolean hasHandler() {
        return scopeData.hasHandler();
    }

    public IRScope scope() {
        return scopeData.scope;
    }

    public IRScope rootScope() {
        IRScope root = scope();
        while (root.caller != null) {
            root = root.caller;
        }
        return root;
    }

    public RiMethod method() {
        return scopeData.scope.method;
    }

    public BytecodeStream stream() {
        return scopeData.stream;
    }

    public int bci() {
        return scopeData.stream.currentBCI();
    }

    public int nextBCI() {
        return scopeData.stream.nextBCI();
    }

    private void ipush(Value x) {
        curState.ipush(x);
    }

    private void lpush(Value x) {
        curState.lpush(x);
    }

    private void fpush(Value x) {
        curState.fpush(x);
    }

    private void dpush(Value x) {
        curState.dpush(x);
    }

    private void apush(Value x) {
        curState.apush(x);
    }

    private void wpush(Value x) {
        curState.wpush(x);
    }

    private void push(CiKind kind, Value x) {
        curState.push(kind, x);
    }

    private void pushReturn(CiKind kind, Value x) {
        if (kind != CiKind.Void) {
            curState.push(kind.stackKind(), x);
        }
    }

    private Value ipop() {
        return curState.ipop();
    }

    private Value lpop() {
        return curState.lpop();
    }

    private Value fpop() {
        return curState.fpop();
    }

    private Value dpop() {
        return curState.dpop();
    }

    private Value apop() {
        return curState.apop();
    }

    private Value wpop() {
        return curState.wpop();
    }

    private Value pop(CiKind kind) {
        return curState.pop(kind);
    }

    private CiKind peekKind() {
        Value top = curState.stackAt(curState.stackSize() - 1);
        if (top == null) {
            top = curState.stackAt(curState.stackSize() - 2);
            assert top != null;
            assert top.kind.isDoubleWord();
        }
        return top.kind;
    }

    private void loadLocal(int index, CiKind kind) {
        push(kind, curState.loadLocal(index));
    }

    private void storeLocal(CiKind kind, int index) {
        if (scopeData.parsingJsr()) {
            // We need to do additional tracking of the location of the return
            // address for jsrs since we don't handle arbitrary jsr/ret
            // constructs. Here we are figuring out in which circumstances we
            // need to bail out.
            if (kind == CiKind.Object) {
                // might be storing the JSR return address
                Value x = curState.xpop();
                if (x.kind.isJsr()) {
                    setJsrReturnAddressLocal(index);
                    curState.storeLocal(index, x);
                } else {
                    // nope, not storing the JSR return address
                    assert x.kind.isObject();
                    curState.storeLocal(index, x);
                    overwriteJsrReturnAddressLocal(index);
                }
                return;
            } else {
                // not storing the JSR return address local, but might overwrite it
                overwriteJsrReturnAddressLocal(index);
            }
        }

        curState.storeLocal(index, pop(kind));
    }

    private void overwriteJsrReturnAddressLocal(int index) {
        if (index == scopeData.jsrEntryReturnAddressLocal()) {
            scopeData.setJsrEntryReturnAddressLocal(-1);
        }
    }

    private void setJsrReturnAddressLocal(int index) {
        scopeData.setJsrEntryReturnAddressLocal(index);

        // Also check parent jsrs (if any) at this time to see whether
        // they are using this local. We don't handle skipping over a
        // ret.
        for (ScopeData cur = scopeData.parent; cur != null && cur.parsingJsr() && cur.scope == scope(); cur = cur.parent) {
            if (cur.jsrEntryReturnAddressLocal() == index) {
                throw new CiBailout("subroutine overwrites return address from previous subroutine");
            }
        }
    }

    List<ExceptionHandler> handleException(Instruction x, int bci) {
        if (!hasHandler()) {
            return Util.uncheckedCast(Collections.EMPTY_LIST);
        }

        ArrayList<ExceptionHandler> exceptionHandlers = new ArrayList<ExceptionHandler>();
        ScopeData curScopeData = scopeData;
        FrameState stateBefore = x.stateBefore();
        int scopeCount = 0;

        assert stateBefore != null : "exception handler state must be available for " + x;
        FrameState state = stateBefore;
        do {
            assert curScopeData.scope == state.scope() : "scopes do not match";
            assert bci == Instruction.SYNCHRONIZATION_ENTRY_BCI || bci == curScopeData.stream.currentBCI() : "invalid bci";

            // join with all potential exception handlers
            List<ExceptionHandler> handlers = curScopeData.exceptionHandlers();
            if (handlers != null) {
                for (ExceptionHandler handler : handlers) {
                    if (handler.covers(bci)) {
                        // if the handler covers this bytecode index, add it to the list
                        if (addExceptionHandler(exceptionHandlers, handler, curScopeData, state, scopeCount)) {
                            // if the handler was a default handler, we are done
                            return exceptionHandlers;
                        }
                    }
                }
            }
            // pop the scope to the next IRScope level
            // if parsing a JSR, skip scopes until the next IRScope level
            IRScope curScope = curScopeData.scope;
            while (curScopeData.parent != null && curScopeData.parent.scope == curScope) {
                curScopeData = curScopeData.parent;
            }
            if (curScopeData.parent == null) {
                // no more levels, done
                break;
            }
            // there is another level, pop
            state = state.callerState();
            bci = curScopeData.scope.callerBCI();
            curScopeData = curScopeData.parent;
            scopeCount++;

        } while (true);

        return exceptionHandlers;
    }

    /**
     * Adds an exception handler to the {@linkplain BlockBegin#exceptionHandlerBlocks() list}
     * of exception handlers for the {@link #curBlock current block}.
     *
     * @param exceptionHandlers
     * @param handler
     * @param curScopeData
     * @param curState the current state with empty stack
     * @param scopeCount
     * @return {@code true} if handler catches all exceptions (i.e. {@code handler.isCatchAll() == true})
     */
    private boolean addExceptionHandler(ArrayList<ExceptionHandler> exceptionHandlers, ExceptionHandler handler, ScopeData curScopeData, FrameState curState, int scopeCount) {
        compilation.setHasExceptionHandlers();

        BlockBegin entry = handler.entryBlock();
        FrameState entryState = entry.stateBefore();

        assert entry.bci() == handler.handler.handlerBCI();
        assert entry.bci() == -1 || entry == curScopeData.blockAt(entry.bci()) : "blocks must correspond";
        assert entryState == null || curState.locksSize() == entryState.locksSize() : "locks do not match";

        // exception handler starts with an empty expression stack
        curState = curState.immutableCopyWithEmptyStack();

        entry.mergeOrClone(curState);

        // add current state for correct handling of phi functions
        int phiOperand = entry.addExceptionState(curState);

        // add entry to the list of exception handlers of this block
        curBlock.addExceptionHandler(entry);

        // add back-edge from exception handler entry to this block
        if (!entry.predecessors().contains(curBlock)) {
            entry.addPredecessor(curBlock);
        }

        // clone exception handler
        ExceptionHandler newHandler = new ExceptionHandler(handler);
        newHandler.setPhiOperand(phiOperand);
        newHandler.setScopeCount(scopeCount);
        exceptionHandlers.add(newHandler);

        // fill in exception handler subgraph lazily
        if (!entry.wasVisited()) {
            curScopeData.addToWorkList(entry);
        } else {
            // This will occur for exception handlers that cover themselves. This code
            // pattern is generated by javac for synchronized blocks. See the following
            // for why this change to javac was made:
            //
            //   http://www.cs.arizona.edu/projects/sumatra/hallofshame/java-async-race.html
        }

        // stop when reaching catch all
        return handler.isCatchAll();
    }

    void genLoadConstant(int cpi) {
        Object con = constantPool().lookupConstant(cpi);

        if (con instanceof RiType) {
            // this is a load of class constant which might be unresolved
            RiType riType = (RiType) con;
            if (!riType.isResolved() || C1XOptions.TestPatching) {
                push(CiKind.Object, append(new ResolveClass(riType, RiType.Representation.JavaClass, null)));
            } else {
                push(CiKind.Object, append(new Constant(riType.getEncoding(Representation.JavaClass))));
            }
        } else if (con instanceof CiConstant) {
            CiConstant constant = (CiConstant) con;
            push(constant.kind.stackKind(), appendConstant(constant));
        } else {
            throw new Error("lookupConstant returned an object of incorrect type");
        }
    }

    void genLoadIndexed(CiKind kind) {
        FrameState stateBefore = curState.immutableCopy(bci());
        Value index = ipop();
        Value array = apop();
        Value length = null;
        if (cseArrayLength(array)) {
            length = append(new ArrayLength(array, stateBefore));
        }
        Value v = append(new LoadIndexed(array, index, length, kind, stateBefore));
        push(kind.stackKind(), v);
    }

    void genStoreIndexed(CiKind kind) {
        FrameState stateBefore = curState.immutableCopy(bci());
        Value value = pop(kind.stackKind());
        Value index = ipop();
        Value array = apop();
        Value length = null;
        if (cseArrayLength(array)) {
            length = append(new ArrayLength(array, stateBefore));
        }
        StoreIndexed result = new StoreIndexed(array, index, length, kind, value, stateBefore);
        append(result);
        if (memoryMap != null) {
            memoryMap.storeValue(value);
        }
    }

    void stackOp(int opcode) {
        switch (opcode) {
            case POP: {
                curState.xpop();
                break;
            }
            case POP2: {
                curState.xpop();
                curState.xpop();
                break;
            }
            case DUP: {
                Value w = curState.xpop();
                curState.xpush(w);
                curState.xpush(w);
                break;
            }
            case DUP_X1: {
                Value w1 = curState.xpop();
                Value w2 = curState.xpop();
                curState.xpush(w1);
                curState.xpush(w2);
                curState.xpush(w1);
                break;
            }
            case DUP_X2: {
                Value w1 = curState.xpop();
                Value w2 = curState.xpop();
                Value w3 = curState.xpop();
                curState.xpush(w1);
                curState.xpush(w3);
                curState.xpush(w2);
                curState.xpush(w1);
                break;
            }
            case DUP2: {
                Value w1 = curState.xpop();
                Value w2 = curState.xpop();
                curState.xpush(w2);
                curState.xpush(w1);
                curState.xpush(w2);
                curState.xpush(w1);
                break;
            }
            case DUP2_X1: {
                Value w1 = curState.xpop();
                Value w2 = curState.xpop();
                Value w3 = curState.xpop();
                curState.xpush(w2);
                curState.xpush(w1);
                curState.xpush(w3);
                curState.xpush(w2);
                curState.xpush(w1);
                break;
            }
            case DUP2_X2: {
                Value w1 = curState.xpop();
                Value w2 = curState.xpop();
                Value w3 = curState.xpop();
                Value w4 = curState.xpop();
                curState.xpush(w2);
                curState.xpush(w1);
                curState.xpush(w4);
                curState.xpush(w3);
                curState.xpush(w2);
                curState.xpush(w1);
                break;
            }
            case SWAP: {
                Value w1 = curState.xpop();
                Value w2 = curState.xpop();
                curState.xpush(w1);
                curState.xpush(w2);
                break;
            }
            default:
                throw Util.shouldNotReachHere();
        }

    }

    void genArithmeticOp(CiKind kind, int opcode) {
        genArithmeticOp(kind, opcode, null);
    }

    void genArithmeticOp(CiKind kind, int opcode, FrameState state) {
        genArithmeticOp(kind, opcode, kind, kind, state);
    }

    void genArithmeticOp(CiKind result, int opcode, CiKind x, CiKind y, FrameState state) {
        Value yValue = pop(y);
        Value xValue = pop(x);
        Value result1 = append(new ArithmeticOp(opcode, result, xValue, yValue, isStrict(method().accessFlags()), state));
        push(result, result1);
    }

    void genNegateOp(CiKind kind) {
        push(kind, append(new NegateOp(pop(kind))));
    }

    void genShiftOp(CiKind kind, int opcode) {
        Value s = ipop();
        Value x = pop(kind);
        // note that strength reduction of e << K >>> K is correctly handled in canonicalizer now
        push(kind, append(new ShiftOp(opcode, x, s)));
    }

    void genLogicOp(CiKind kind, int opcode) {
        Value y = pop(kind);
        Value x = pop(kind);
        push(kind, append(new LogicOp(opcode, x, y)));
    }

    void genCompareOp(CiKind kind, int opcode, CiKind resultKind) {
        Value y = pop(kind);
        Value x = pop(kind);
        Value value = append(new CompareOp(opcode, resultKind, x, y));
        if (!resultKind.isVoid()) {
            ipush(value);
        }
    }

    void genUnsignedCompareOp(CiKind kind, int opcode, int op) {
        Value y = pop(kind);
        Value x = pop(kind);
        ipush(append(new UnsignedCompareOp(opcode, op, x, y)));
    }

    void genConvert(int opcode, CiKind from, CiKind to) {
        CiKind tt = to.stackKind();
        push(tt, append(new Convert(opcode, pop(from.stackKind()), tt)));
    }

    void genIncrement() {
        int index = stream().readLocalIndex();
        int delta = stream().readIncrement();
        Value x = curState.localAt(index);
        Value y = append(Constant.forInt(delta));
        curState.storeLocal(index, append(new ArithmeticOp(IADD, CiKind.Int, x, y, isStrict(method().accessFlags()), null)));
    }

    void genGoto(int fromBCI, int toBCI) {
        boolean isSafepoint = !scopeData.noSafepoints() && toBCI <= fromBCI;
        append(new Goto(blockAt(toBCI), null, isSafepoint));
    }

    void ifNode(Value x, Condition cond, Value y, FrameState stateBefore) {
        BlockBegin tsucc = blockAt(stream().readBranchDest());
        BlockBegin fsucc = blockAt(stream().nextBCI());
        int bci = stream().currentBCI();
        boolean isSafepoint = !scopeData.noSafepoints() && tsucc.bci() <= bci || fsucc.bci() <= bci;
        append(new If(x, cond, false, y, tsucc, fsucc, isSafepoint ? stateBefore : null, isSafepoint));
    }

    void genIfZero(Condition cond) {
        Value y = appendConstant(CiConstant.INT_0);
        FrameState stateBefore = curState.immutableCopy(bci());
        Value x = ipop();
        ifNode(x, cond, y, stateBefore);
    }

    void genIfNull(Condition cond) {
        FrameState stateBefore = curState.immutableCopy(bci());
        Value y = appendConstant(CiConstant.NULL_OBJECT);
        Value x = apop();
        ifNode(x, cond, y, stateBefore);
    }

    void genIfSame(CiKind kind, Condition cond) {
        FrameState stateBefore = curState.immutableCopy(bci());
        Value y = pop(kind);
        Value x = pop(kind);
        ifNode(x, cond, y, stateBefore);
    }

    void genThrow(int bci) {
        FrameState stateBefore = curState.immutableCopy(bci());
        Throw t = new Throw(apop(), stateBefore, !scopeData.noSafepoints());
        appendWithoutOptimization(t, bci);
    }

    void genUnsafeCast(RiMethod method) {
        compilation.setNotTypesafe();
        RiSignature signature = method.signature();
        int argCount = signature.argumentCount(false);
        RiType accessingClass = scope().method.holder();
        RiType fromType;
        RiType toType = signature.returnType(accessingClass);
        if (argCount == 1) {
            fromType = signature.argumentTypeAt(0, accessingClass);
        } else {
            assert argCount == 0 : "method with @UNSAFE_CAST must have exactly 1 argument";
            fromType = method.holder();
        }
        CiKind from = fromType.kind();
        CiKind to = toType.kind();
        boolean redundant = compilation.archKindsEqual(to, from);
        curState.push(to, append(new UnsafeCast(toType, curState.pop(from), redundant)));
    }

    void genCheckCast() {
        int cpi = stream().readCPI();
        RiType type = constantPool().lookupType(cpi, CHECKCAST);
        boolean isInitialized = !C1XOptions.TestPatching && type.isResolved() && type.isInitialized();
        Value typeInstruction = genResolveClass(RiType.Representation.ObjectHub, type, isInitialized, cpi);
        CheckCast c = new CheckCast(type, typeInstruction, apop(), null);
        apush(append(c));
        checkForDirectCompare(c);
    }

    void genInstanceOf() {
        int cpi = stream().readCPI();
        RiType type = constantPool().lookupType(cpi, INSTANCEOF);
        boolean isInitialized = !C1XOptions.TestPatching && type.isResolved() && type.isInitialized();
        Value typeInstruction = genResolveClass(RiType.Representation.ObjectHub, type, isInitialized, cpi);
        InstanceOf i = new InstanceOf(type, typeInstruction, apop(), null);
        ipush(append(i));
        checkForDirectCompare(i);
    }

    private void checkForDirectCompare(TypeCheck check) {
        RiType type = check.targetClass();
        if (!type.isResolved() || type.isArrayClass()) {
            return;
        }
        if (assumeLeafClass(type)) {
            check.setDirectCompare();
        }
    }

    void genNewInstance(int cpi) {
        FrameState stateBefore = curState.immutableCopy(bci());
        RiType type = constantPool().lookupType(cpi, NEW);
        NewInstance n = new NewInstance(type, cpi, constantPool(), stateBefore);
        if (memoryMap != null) {
            memoryMap.newInstance(n);
        }
        apush(append(n));
    }

    void genNewTypeArray(int typeCode) {
        FrameState stateBefore = curState.immutableCopy(bci());
        CiKind kind = CiKind.fromArrayTypeCode(typeCode);
        RiType elementType = compilation.runtime.asRiType(kind);
        apush(append(new NewTypeArray(ipop(), elementType, stateBefore)));
    }

    void genNewObjectArray(int cpi) {
        RiType type = constantPool().lookupType(cpi, ANEWARRAY);
        FrameState stateBefore = curState.immutableCopy(bci());
        NewArray n = new NewObjectArray(type, ipop(), stateBefore);
        apush(append(n));
    }

    void genNewMultiArray(int cpi) {
        RiType type = constantPool().lookupType(cpi, MULTIANEWARRAY);
        FrameState stateBefore = curState.immutableCopy(bci());
        int rank = stream().readUByte(bci() + 3);
        Value[] dims = new Value[rank];
        for (int i = rank - 1; i >= 0; i--) {
            dims[i] = ipop();
        }
        NewArray n = new NewMultiArray(type, dims, stateBefore, cpi, constantPool());
        apush(append(n));
    }

    void genGetField(int cpi, RiField field) {
        // Must copy the state here, because the field holder must still be on the stack.
        FrameState stateBefore = curState.immutableCopy(bci());
        boolean isLoaded = !C1XOptions.TestPatching && field.isResolved();
        LoadField load = new LoadField(apop(), field, false, stateBefore, isLoaded);
        appendOptimizedLoadField(field.kind(), load);
    }

    void genPutField(int cpi, RiField field) {
        // Must copy the state here, because the field holder must still be on the stack.
        FrameState stateBefore = curState.immutableCopy(bci());
        boolean isLoaded = !C1XOptions.TestPatching && field.isResolved();
        Value value = pop(field.kind().stackKind());
        appendOptimizedStoreField(new StoreField(apop(), field, value, false, stateBefore, isLoaded));
    }

    void genGetStatic(int cpi, RiField field) {
        RiType holder = field.holder();
        boolean isInitialized = !C1XOptions.TestPatching && field.isResolved() && holder.isResolved() && holder.isInitialized();
        CiConstant constantValue = null;
        if (isInitialized && C1XOptions.CanonicalizeConstantFields) {
            constantValue = field.constantValue(null);
        }
        if (constantValue != null) {
            push(constantValue.kind.stackKind(), appendConstant(constantValue));
        } else {
            Value container = genResolveClass(RiType.Representation.StaticFields, holder, isInitialized, cpi);
            LoadField load = new LoadField(container, field, true, null, isInitialized);
            appendOptimizedLoadField(field.kind(), load);
        }
    }

    void genPutStatic(int cpi, RiField field) {
        RiType holder = field.holder();
        boolean isInitialized = !C1XOptions.TestPatching && field.isResolved() && holder.isResolved() && holder.isInitialized();
        Value container = genResolveClass(RiType.Representation.StaticFields, holder, isInitialized, cpi);
        Value value = pop(field.kind().stackKind());
        StoreField store = new StoreField(container, field, value, true, null, isInitialized);
        appendOptimizedStoreField(store);
    }

    private Value genResolveClass(RiType.Representation representation, RiType holder, boolean initialized, int cpi) {
        Value holderInstr;
        if (initialized) {
            holderInstr = appendConstant(holder.getEncoding(representation));
        } else {
            holderInstr = append(new ResolveClass(holder, representation, null));
        }
        return holderInstr;
    }

    private void appendOptimizedStoreField(StoreField store) {
        if (memoryMap != null) {
            StoreField previous = memoryMap.store(store);
            if (previous == null) {
                // the store is redundant, do not append
                return;
            }
        }
        append(store);
    }

    private void appendOptimizedLoadField(CiKind kind, LoadField load) {
        if (memoryMap != null) {
            Value replacement = memoryMap.load(load);
            if (replacement != load) {
                // the memory buffer found a replacement for this load (no need to append)
                push(kind.stackKind(), replacement);
                return;
            }
        }
        // append the load to the instruction
        Value optimized = append(load);
        if (memoryMap != null && optimized != load) {
            // local optimization happened, replace its value in the memory map
            memoryMap.setResult(load, optimized);
        }
        push(kind.stackKind(), optimized);
    }

    /**
     * Temporary work-around to support the @ACCESSOR Maxine annotation.
     */
    private RiMethod handleInvokeAccessorOrBuiltin(RiMethod target) {
        target = bindAccessorMethod(target);
        if (target.intrinsic() != 0) {
            int intrinsic = target.intrinsic();
            int opcode = intrinsic & 0xff;
            switch (opcode) {
                case PREAD          : genLoadPointer(intrinsic); break;
                case PGET           : genLoadPointer(intrinsic); break;
                case PWRITE         : genStorePointer(intrinsic); break;
                case PSET           : genStorePointer(intrinsic); break;
                case PCMPSWP        : genCompareAndSwap(intrinsic); break;
                default:
                    throw new CiBailout("unknown bytecode " + opcode + " (" + nameOf(opcode) + ")");
            }
            return null;
        }
        return target;
    }

    void genInvokeStatic(RiMethod target, int cpi, RiConstantPool constantPool) {
        target = handleInvokeAccessorOrBuiltin(target);
        if (target == null) {
            return;
        }
        RiType holder = target.holder();
        boolean isInitialized = !C1XOptions.TestPatching && target.isResolved() && holder.isInitialized();
        if (!isInitialized && C1XOptions.ResolveClassBeforeStaticInvoke) {
            // Re-use the same resolution code as for accessing a static field. Even though
            // the result of resolution is not used by the invocation (only the side effect
            // of initialization is required), it can be commoned with static field accesses.
            genResolveClass(RiType.Representation.StaticFields, holder, isInitialized, cpi);
        }

        Value[] args = curState.popArguments(target.signature().argumentSlots(false));
        if (!tryRemoveCall(target, args, true)) {
            if (!tryInline(target, args)) {
                appendInvoke(INVOKESTATIC, target, args, true, cpi, constantPool);
            }
        }
    }

    void genInvokeInterface(RiMethod target, int cpi, RiConstantPool constantPool) {
        target = handleInvokeAccessorOrBuiltin(target);
        if (target == null) {
            return;
        }
        Value[] args = curState.popArguments(target.signature().argumentSlots(true));
        if (!tryRemoveCall(target, args, false)) {
            genInvokeIndirect(INVOKEINTERFACE, target, args, cpi, constantPool);
        }
    }

    void genInvokeVirtual(RiMethod target, int cpi, RiConstantPool constantPool) {
        target = handleInvokeAccessorOrBuiltin(target);
        if (target == null) {
            return;
        }
        Value[] args = curState.popArguments(target.signature().argumentSlots(true));
        if (!tryRemoveCall(target, args, false)) {
            genInvokeIndirect(INVOKEVIRTUAL, target, args, cpi, constantPool);
        }
    }

    void genInvokeSpecial(RiMethod target, RiType knownHolder, int cpi, RiConstantPool constantPool) {
        target = handleInvokeAccessorOrBuiltin(target);
        if (target == null) {
            return;
        }
        Value[] args = curState.popArguments(target.signature().argumentSlots(true));
        if (!tryRemoveCall(target, args, false)) {
            invokeDirect(target, args, knownHolder, cpi, constantPool);
        }
    }

    /**
     * Temporary work-around to support the @ACCESSOR Maxine annotation.
     */
    private static final Class<?> Accessor;
    static {
        Class<?> c = null;
        try {
            c = Class.forName("com.sun.max.unsafe.Accessor");
        } catch (ClassNotFoundException e) {
        }
        Accessor = c;
    }

    /**
     * Temporary work-around to support the @ACCESSOR Maxine annotation.
     */
    private static ThreadLocal<RiType> boundAccessor = new ThreadLocal<RiType>();

    /**
     * Temporary work-around to support the @ACCESSOR Maxine annotation.
     */
    private static RiMethod bindAccessorMethod(RiMethod target) {
        if (Accessor != null && target.isResolved() && target.holder().javaClass() == Accessor) {
            RiType accessor = boundAccessor.get();
            assert accessor != null : "Cannot compile call to method in " + target.holder() + " without enclosing @ACCESSOR annotated method";
            RiMethod newTarget = accessor.resolveMethodImpl(target);
            assert target != newTarget : "Could not bind " + target + " to a method in " + accessor;
            target = newTarget;
        }
        return target;
    }

    /**
     * Temporary work-around to support the @ACCESSOR Maxine annotation.
     */
    private boolean inlineWithBoundAccessor(RiMethod target, Value[] args, boolean forcedInline) {
        RiType accessor = target.accessor();
        if (accessor != null) {
            assert boundAccessor.get() == null;
            boundAccessor.set(accessor);
            try {
                // What looks like an object receiver in the bytecode may not be a word value
                compilation.setNotTypesafe();
                inline(target, args, forcedInline);
            } finally {
                boundAccessor.set(null);
            }
            return true;
        }
        return false;
    }

    private void genInvokeIndirect(int opcode, RiMethod target, Value[] args, int cpi, RiConstantPool constantPool) {
        Value receiver = args[0];
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
            // 2. check if an assumed leaf method can be found
            RiMethod leaf = getAssumedLeafMethod(target, receiver);
            if (leaf != null && leaf.isResolved() && !isAbstract(leaf.accessFlags()) && leaf.holder().isResolved()) {
                if (C1XOptions.PrintAssumptions) {
                    TTY.println("Optimistic invoke direct because of leaf method to " + leaf);
                }
                invokeDirect(leaf, args, null, cpi, constantPool);
                return;
            } else if (C1XOptions.PrintAssumptions) {
                TTY.println("Could not make leaf method assumption for target=" + target + " leaf=" + leaf + " receiver.declaredType=" + receiver.declaredType());
            }
            // 3. check if the either of the holder or declared type of receiver can be assumed to be a leaf
            exact = getAssumedLeafType(klass, receiver);
            if (exact != null && exact.isResolved()) {
                RiMethod targetMethod = exact.resolveMethodImpl(target);
                if (C1XOptions.PrintAssumptions) {
                    TTY.println("Optimistic invoke direct because of leaf type to " + targetMethod);
                }
                // either the holder class is exact, or the receiver object has an exact type
                invokeDirect(targetMethod, args, exact, cpi, constantPool);
                return;
            } else if (C1XOptions.PrintAssumptions) {
                TTY.println("Could not make leaf type assumption for type " + klass);
            }
        }
        // devirtualization failed, produce an actual invokevirtual
        appendInvoke(opcode, target, args, false, cpi, constantPool);
    }

    private CiKind returnKind(RiMethod target) {
        return target.signature().returnKind();
    }

    private void invokeDirect(RiMethod target, Value[] args, RiType knownHolder, int cpi, RiConstantPool constantPool) {
        if (!tryInline(target, args)) {
            // could not optimize or inline the method call
            appendInvoke(INVOKESPECIAL, target, args, false, cpi, constantPool);
        }
    }

    private void appendInvoke(int opcode, RiMethod target, Value[] args, boolean isStatic, int cpi, RiConstantPool constantPool) {
        CiKind resultType = returnKind(target);
        Value result = append(new Invoke(opcode, resultType.stackKind(), args, isStatic, target, target.signature().returnType(compilation.method.holder()), null));
        pushReturn(resultType, result);
    }

    private RiType getExactType(RiType staticType, Value receiver) {
        RiType exact = staticType.exactType();
        if (exact == null) {
            exact = receiver.exactType();
            if (exact == null) {
                if (receiver.isConstant()) {
                    exact = compilation.runtime.getTypeOf(receiver.asConstant());
                }
                if (exact == null) {
                    RiType declared = receiver.declaredType();
                    exact = declared == null || !declared.isResolved() ? null : declared.exactType();
                }
            }
        }
        return exact;
    }

    private RiType getAssumedLeafType(RiType type) {
        if (isFinal(type.accessFlags())) {
            return type;
        }
        RiType assumed = null;
        if (C1XOptions.UseAssumptions) {
            assumed = type.uniqueConcreteSubtype();
            if (assumed != null) {
                if (C1XOptions.PrintAssumptions) {
                    TTY.println("Recording concrete subtype assumption in context of " + type.name() + ": " + assumed.name());
                }
                compilation.assumptions.recordConcreteSubtype(type, assumed);
            }
        }
        return assumed;
    }

    private RiType getAssumedLeafType(RiType staticType, Value receiver) {
        RiType assumed = getAssumedLeafType(staticType);
        if (assumed != null) {
            return assumed;
        }
        RiType declared = receiver.declaredType();
        if (declared != null && declared.isResolved()) {
            assumed = getAssumedLeafType(declared);
            return assumed;
        }
        return null;
    }

    private RiMethod getAssumedLeafMethod(RiMethod target, Value receiver) {
        RiMethod assumed = getAssumedLeafMethod(target);
        if (assumed != null) {
            return assumed;
        }
        RiType declared = receiver.declaredType();
        if (declared != null && declared.isResolved() && !declared.isInterface()) {
            RiMethod impl = declared.resolveMethodImpl(target);
            if (impl != null) {
                assumed = getAssumedLeafMethod(impl);
            }
        }
        return assumed;
    }

    void callRegisterFinalizer() {
        Value receiver = curState.loadLocal(0);
        RiType declaredType = receiver.declaredType();
        RiType receiverType = declaredType;
        RiType exactType = receiver.exactType();
        if (exactType == null && declaredType != null) {
            exactType = declaredType.exactType();
        }
        if (exactType == null && receiver instanceof Local && ((Local) receiver).javaIndex() == 0) {
            // the exact type isn't known, but the receiver is parameter 0 => use holder
            receiverType = compilation.method.holder();
            exactType = receiverType.exactType();
        }
        boolean needsCheck = true;
        if (exactType != null) {
            // we have an exact type
            needsCheck = exactType.hasFinalizer();
        } else {
            // if either the declared type of receiver or the holder can be assumed to have no finalizers
            if (declaredType != null && !declaredType.hasFinalizableSubclass()) {
                if (compilation.recordNoFinalizableSubclassAssumption(declaredType)) {
                    needsCheck = false;
                }
            }

            if (receiverType != null && !receiverType.hasFinalizableSubclass()) {
                if (compilation.recordNoFinalizableSubclassAssumption(receiverType)) {
                    needsCheck = false;
                }
            }
        }

        if (needsCheck) {
            // append a call to the registration intrinsic
            loadLocal(0, CiKind.Object);
            FrameState stateBefore = curState.immutableCopy(bci());
            append(new Intrinsic(CiKind.Void, C1XIntrinsic.java_lang_Object$init,
                                 null, curState.popArguments(1), false, stateBefore, true, true));
            C1XMetrics.InlinedFinalizerChecks++;
        }
    }

    void genReturn(Value x) {
        if (C1XIntrinsic.getIntrinsic(method()) == C1XIntrinsic.java_lang_Object$init) {
            callRegisterFinalizer();
        }

        // If inlining, then returns become gotos to the continuation point.
        if (scopeData.continuation() != null) {
            if (isSynchronized(method().accessFlags())) {
                // if the inlined method is synchronized, then the monitor
                // must be released before jumping to the continuation point
                assert C1XOptions.OptInlineSynchronized;
                Value object = curState.lockAt(0);
                if (object instanceof Instruction) {
                    Instruction obj = (Instruction) object;
                    if (!obj.isAppended()) {
                        appendWithoutOptimization(obj, Instruction.SYNCHRONIZATION_ENTRY_BCI);
                    }
                }
                genMonitorExit(object, Instruction.SYNCHRONIZATION_ENTRY_BCI);
            }

            // empty stack for return value
            curState.truncateStack(0);
            if (x != null) {
                curState.push(x.kind, x);
            }
            Goto gotoCallee = new Goto(scopeData.continuation(), null, false);

            // ATTN: assumption: curState is not used further down, else add .immutableCopy()
            scopeData.updateSimpleInlineInfo(curBlock, lastInstr, curState);

            // State at end of inlined method is the state of the caller
            // without the method parameters on stack, including the
            // return value, if any, of the inlined method on operand stack.
            curState = scopeData.continuationState().copy();
            if (x != null) {
                curState.push(x.kind, x);
            }

            // The current bci is in the wrong scope, so use the bci of the continuation point.
            appendWithoutOptimization(gotoCallee, scopeData.continuation().bci());
            return;
        }

        curState.truncateStack(0);
        if (Modifier.isSynchronized(method().accessFlags())) {
            FrameState stateBefore = curState.immutableCopy(bci());
            // unlock before exiting the method
            int lockNumber = curState.totalLocksSize() - 1;
            MonitorAddress lockAddress = null;
            if (compilation.runtime.sizeOfBasicObjectLock() != 0) {
                lockAddress = new MonitorAddress(lockNumber);
                append(lockAddress);
            }
            append(new MonitorExit(rootMethodSynchronizedObject, lockAddress, lockNumber, stateBefore));
            curState.unlock();
        }
        append(new Return(x, !scopeData.noSafepoints()));
    }

    /**
     * Gets the number of locks held.
     */
    private int locksSize() {
        return curState.locksSize();
    }

    void genMonitorEnter(Value x, int bci) {
        int lockNumber = locksSize();
        MonitorAddress lockAddress = null;
        if (compilation.runtime.sizeOfBasicObjectLock() != 0) {
            lockAddress = new MonitorAddress(lockNumber);
            append(lockAddress);
        }
        MonitorEnter monitorEnter = new MonitorEnter(x, lockAddress, lockNumber, null);
        appendWithoutOptimization(monitorEnter, bci);
        curState.lock(scope(), x, lockNumber + 1);
        monitorEnter.setStateAfter(curState.immutableCopy(bci));
        killMemoryMap(); // prevent any optimizations across synchronization
    }

    void genMonitorExit(Value x, int bci) {
        int lockNumber = curState.totalLocksSize() - 1;
        if (lockNumber < 0) {
            throw new CiBailout("monitor stack underflow");
        }
        MonitorAddress lockAddress = null;
        if (compilation.runtime.sizeOfBasicObjectLock() != 0) {
            lockAddress = new MonitorAddress(lockNumber);
            append(lockAddress);
        }
        appendWithoutOptimization(new MonitorExit(x, lockAddress, lockNumber, null), bci);
        curState.unlock();
        killMemoryMap(); // prevent any optimizations across synchronization
    }

    void genJsr(int dest) {
        for (ScopeData cur = scopeData; cur != null && cur.parsingJsr() && cur.scope == scope(); cur = cur.parent) {
            if (cur.jsrEntryBCI() == dest) {
                // the jsr/ret pattern includes a recursive invocation
                throw new CiBailout("recursive jsr/ret structure");
            }
        }
        push(CiKind.Jsr, append(Constant.forJsr(nextBCI())));
        tryInlineJsr(dest);
    }

    void genRet(int localIndex) {
        if (!scopeData.parsingJsr()) {
            throw new CiBailout("ret encountered when not parsing subroutine");
        }

        if (localIndex != scopeData.jsrEntryReturnAddressLocal()) {
            throw new CiBailout("jsr/ret structure is too complicated");
        }
        // rets become non-safepoint gotos
        append(new Goto(scopeData.jsrContinuation(), null, false));
    }

    void genTableswitch() {
        int bci = bci();
        BytecodeTableSwitch ts = new BytecodeTableSwitch(stream(), bci);
        int max = ts.numberOfCases();
        List<BlockBegin> list = new ArrayList<BlockBegin>(max + 1);
        boolean isBackwards = false;
        for (int i = 0; i < max; i++) {
            // add all successors to the successor list
            int offset = ts.offsetAt(i);
            list.add(blockAt(bci + offset));
            isBackwards |= offset < 0; // track if any of the successors are backwards
        }
        int offset = ts.defaultOffset();
        isBackwards |= offset < 0; // if the default successor is backwards
        list.add(blockAt(bci + offset));
        boolean isSafepoint = isBackwards && !scopeData.noSafepoints();
        FrameState stateBefore = isSafepoint ? curState.immutableCopy(bci()) : null;
        append(new TableSwitch(ipop(), list, ts.lowKey(), stateBefore, isSafepoint));
    }

    void genLookupswitch() {
        int bci = bci();
        BytecodeLookupSwitch ls = new BytecodeLookupSwitch(stream(), bci);
        int max = ls.numberOfCases();
        List<BlockBegin> list = new ArrayList<BlockBegin>(max + 1);
        int[] keys = new int[max];
        boolean isBackwards = false;
        for (int i = 0; i < max; i++) {
            // add all successors to the successor list
            int offset = ls.offsetAt(i);
            list.add(blockAt(bci + offset));
            keys[i] = ls.keyAt(i);
            isBackwards |= offset < 0; // track if any of the successors are backwards
        }
        int offset = ls.defaultOffset();
        isBackwards |= offset < 0; // if the default successor is backwards
        list.add(blockAt(bci + offset));
        boolean isSafepoint = isBackwards && !scopeData.noSafepoints();
        FrameState stateBefore = isSafepoint ? curState.immutableCopy(bci()) : null;
        append(new LookupSwitch(ipop(), list, keys, stateBefore, isSafepoint));
    }

    /**
     * Determines whether the length of an array should be extracted out as a separate instruction
     * before an array indexing instruction. This exposes it to CSE.
     * @param array
     * @return
     */
    private boolean cseArrayLength(Value array) {
        // checks whether an array length access should be generated for CSE
        if (C1XOptions.OptCSEArrayLength) {
            // always access the length for CSE
            return true;
        } else if (array.isConstant()) {
            // the array itself is a constant
            return true;
        } else if (array instanceof LoadField && ((LoadField) array).constantValue() != null) {
            // the length is derived from a constant array
            return true;
        } else if (array instanceof NewArray) {
            // the array is derived from an allocation
            final Value length = ((NewArray) array).length();
            return length != null && length.isConstant();
        }
        return false;
    }

    private Value appendConstant(CiConstant type) {
        return appendWithBCI(new Constant(type), bci(), false);
    }

    private Value append(Instruction x) {
        return appendWithBCI(x, bci(), C1XOptions.OptCanonicalize);
    }

    private Value appendWithoutOptimization(Instruction x, int bci) {
        return appendWithBCI(x, bci, false);
    }

    private Value appendWithBCI(Instruction x, int bci, boolean canonicalize) {
        if (canonicalize) {
            // attempt simple constant folding and strength reduction
            Value r = canonicalizer.canonicalize(x);
            List<Instruction> extra = canonicalizer.extra();
            if (extra != null) {
                // the canonicalization introduced instructions that should be added before this
                for (Instruction i : extra) {
                    appendWithBCI(i, bci, false); // don't try to canonicalize the new instructions
                }
            }
            if (r instanceof Instruction) {
                // the result is an instruction that may need to be appended
                x = (Instruction) r;
            } else {
                // the result is not an instruction (and thus cannot be appended)
                return r;
            }
        }
        if (x.isAppended()) {
            // the instruction has already been added
            return x;
        }
        if (localValueMap != null) {
            // look in the local value map
            Value r = localValueMap.findInsert(x);
            if (r != x) {
                C1XMetrics.LocalValueNumberHits++;
                if (r instanceof Instruction) {
                    assert ((Instruction) r).isAppended() : "instruction " + r + "is not appended";
                }
                return r;
            }
        }

        assert x.next() == null : "instruction should not have been appended yet";
        assert lastInstr.next() == null : "cannot append instruction to instruction which isn't end (" + lastInstr + "->" + lastInstr.next() + ")";
        if (lastInstr instanceof Base) {
            assert x instanceof Intrinsic : "may only happen when inlining intrinsics";
            Instruction prev = lastInstr.prev(lastInstr.block());
            prev.setNext(x, bci);
            x.setNext(lastInstr, bci);
        } else {
            lastInstr = lastInstr.setNext(x, bci);
        }
        if (++stats.nodeCount >= C1XOptions.MaximumInstructionCount) {
            // bailout if we've exceeded the maximum inlining size
            throw new CiBailout("Method and/or inlining is too large");
        }

        if (memoryMap != null && hasUncontrollableSideEffects(x)) {
            // conservatively kill all memory if there are unknown side effects
            memoryMap.kill();
        }

        if (x instanceof StateSplit) {
            StateSplit stateSplit = (StateSplit) x;
            if (!stateSplit.isStateCleared() && stateSplit.stateBefore() == null) {
                stateSplit.setStateBefore(curState.immutableCopy(bci));
            }
        }

        if (x.canTrap()) {
            // connect the instruction to any exception handlers
            x.setExceptionHandlers(handleException(x, bci));
        }

        return x;
    }

    private boolean hasUncontrollableSideEffects(Value x) {
        return x instanceof Invoke || x instanceof Intrinsic && !((Intrinsic) x).preservesState() || x instanceof ResolveClass;
    }

    private BlockBegin blockAtOrNull(int bci) {
        return scopeData.blockAt(bci);
    }

    private BlockBegin blockAt(int bci) {
        BlockBegin result = blockAtOrNull(bci);
        assert result != null : "Expected a block to begin at " + bci;
        return result;
    }

    boolean tryInlineJsr(int jsrStart) {
        // start a new continuation point.
        // all ret instructions will be replaced with gotos to this point
        BlockBegin cont = blockAt(nextBCI());

        // push callee scope
        pushScopeForJsr(cont, jsrStart);

        BlockBegin jsrStartBlock = blockAt(jsrStart);
        assert !jsrStartBlock.wasVisited();
        Goto gotoSub = new Goto(jsrStartBlock, null, false);
        gotoSub.setStateAfter(curState.immutableCopy(bci()));
        assert jsrStartBlock.stateBefore() == null;
        jsrStartBlock.setStateBefore(curState.immutableCopy(bci()));
        append(gotoSub);
        curBlock.setEnd(gotoSub);
        lastInstr = curBlock = jsrStartBlock;

        scopeData.addToWorkList(jsrStartBlock);

        iterateAllBlocks();

        if (cont.stateBefore() != null) {
            if (!cont.wasVisited()) {
                scopeData.parent.addToWorkList(cont);
            }
        }

        BlockBegin jsrCont = scopeData.jsrContinuation();
        assert jsrCont == cont && (!jsrCont.wasVisited() || jsrCont.isParserLoopHeader());
        assert lastInstr != null && lastInstr instanceof BlockEnd;

        // continuation is in work list, so end iteration of current block
        skipBlock = true;
        popScopeForJsr();
        C1XMetrics.InlinedJsrs++;
        return true;
    }

    void pushScopeForJsr(BlockBegin jsrCont, int jsrStart) {
        BytecodeStream stream = new BytecodeStream(scope().method.code());
        RiConstantPool constantPool = scopeData.constantPool;
        ScopeData data = new ScopeData(scopeData, scope(), scopeData.blockMap, stream, constantPool, jsrStart);
        BlockBegin continuation = scopeData.continuation();
        data.setContinuation(continuation);
        if (continuation != null) {
            assert scopeData.continuationState() != null;
            data.setContinuationState(scopeData.continuationState().copy());
        }
        data.setJsrContinuation(jsrCont);
        scopeData = data;
    }

    void pushScope(RiMethod target, BlockBegin continuation) {
        // prepare callee scope
        IRScope calleeScope = new IRScope(scope(), curState.immutableCopy(bci()), target, -1);
        BlockMap blockMap = compilation.getBlockMap(calleeScope.method, -1);
        calleeScope.setStoresInLoops(blockMap.getStoresInLoops());
        // prepare callee state
        curState = curState.pushScope(calleeScope);
        BytecodeStream stream = new BytecodeStream(target.code());
        RiConstantPool constantPool = compilation.runtime.getConstantPool(target);
        ScopeData data = new ScopeData(scopeData, calleeScope, blockMap, stream, constantPool);
        data.setContinuation(continuation);
        scopeData = data;
    }

    MutableFrameState stateAtEntry(RiMethod method) {
        MutableFrameState state = new MutableFrameState(scope(), -1, method.maxLocals(), method.maxStackSize());
        int index = 0;
        if (!isStatic(method.accessFlags())) {
            // add the receiver and assume it is non null
            Local local = new Local(method.holder().kind(), index);
            local.setFlag(Value.Flag.NonNull, true);
            local.setDeclaredType(method.holder());
            state.storeLocal(index, local);
            index = 1;
        }
        RiSignature sig = method.signature();
        int max = sig.argumentCount(false);
        RiType accessingClass = method.holder();
        for (int i = 0; i < max; i++) {
            RiType type = sig.argumentTypeAt(i, accessingClass);
            CiKind kind = type.kind().stackKind();
            Local local = new Local(kind, index);
            if (type.isResolved()) {
                local.setDeclaredType(type);
            }
            state.storeLocal(index, local);
            index += kind.sizeInSlots();
        }
        return state;
    }

    boolean tryRemoveCall(RiMethod target, Value[] args, boolean isStatic) {
        if (target.isResolved()) {
            if (C1XOptions.OptIntrinsify) {
                // try to create an intrinsic node instead of a call
                C1XIntrinsic intrinsic = C1XIntrinsic.getIntrinsic(target);
                if (intrinsic != null && tryInlineIntrinsic(target, args, isStatic, intrinsic)) {
                    // this method is not an intrinsic
                    return true;
                }
            }
            if (C1XOptions.CanonicalizeFoldableMethods) {
                // next try to fold the method call
                if (tryFoldable(target, args)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean tryInlineIntrinsic(RiMethod target, Value[] args, boolean isStatic, C1XIntrinsic intrinsic) {
        boolean preservesState = true;
        boolean canTrap = false;

        Instruction result = null;

        // handle intrinsics differently
        switch (intrinsic) {

            case java_lang_System$arraycopy:
                if (compilation.runtime.supportsArrayIntrinsics()) {
                    break;
                } else {
                    return false;
                }
            case java_lang_Object$getClass:
                canTrap = true;
                break;
            case java_lang_Thread$currentThread:
                break;
            case java_util_Arrays$copyOf:
                if (args[0].declaredType() != null && args[0].declaredType().isArrayClass() && compilation.runtime.supportsArrayIntrinsics()) {
                    break;
                } else {
                    return false;
                }
            case java_lang_Object$init: // fall through
            case java_lang_String$equals: // fall through
            case java_lang_String$compareTo: // fall through
            case java_lang_String$indexOf: // fall through
            case java_lang_Math$max: // fall through
            case java_lang_Math$min: // fall through
            case java_lang_Math$atan2: // fall through
            case java_lang_Math$pow: // fall through
            case java_lang_Math$exp: // fall through
            case java_nio_Buffer$checkIndex: // fall through
            case java_lang_System$identityHashCode: // fall through
            case java_lang_System$currentTimeMillis: // fall through
            case java_lang_System$nanoTime: // fall through
            case java_lang_Object$hashCode: // fall through
            case java_lang_Class$isAssignableFrom: // fall through
            case java_lang_Class$isInstance: // fall through
            case java_lang_Class$getModifiers: // fall through
            case java_lang_Class$isInterface: // fall through
            case java_lang_Class$isArray: // fall through
            case java_lang_Class$isPrimitive: // fall through
            case java_lang_Class$getSuperclass: // fall through
            case java_lang_Class$getComponentType: // fall through
            case java_lang_reflect_Array$getLength: // fall through
            case java_lang_reflect_Array$newArray: // fall through
            case java_lang_Double$doubleToLongBits: // fall through
            case java_lang_Float$floatToIntBits: // fall through
            case java_lang_Math$sin: // fall through
            case java_lang_Math$cos: // fall through
            case java_lang_Math$tan: // fall through
            case java_lang_Math$log: // fall through
            case java_lang_Math$log10: // fall through
            case java_lang_Integer$bitCount: // fall through
            case java_lang_Integer$reverseBytes: // fall through
            case java_lang_Long$bitCount: // fall through
            case java_lang_Long$reverseBytes: // fall through
            case java_lang_Object$clone:  return false;
            // TODO: preservesState and canTrap for complex intrinsics
        }



        // get the arguments for the intrinsic
        CiKind resultType = returnKind(target);

        if (C1XOptions.PrintInlinedIntrinsics) {
            TTY.println("Inlining intrinsic: " + intrinsic);
        }

        // Create state before intrinsic.
        for (int i = 0; i < args.length; ++i) {
            if (args[i] != null) {
                curState.push(args[i].kind.stackKind(), args[i]);
            }
        }

        // Create the intrinsic node.
        if (intrinsic == C1XIntrinsic.java_lang_System$arraycopy) {
            result = genArrayCopy(target, args);
        } else if (intrinsic == C1XIntrinsic.java_util_Arrays$copyOf) {
            result = genArrayClone(target, args);
        } else {
            result = new Intrinsic(resultType.stackKind(), intrinsic, target, args, isStatic, curState.immutableCopy(bci()), preservesState, canTrap);
        }

        // Pop arguments.
        curState.popArguments(args.length);

        pushReturn(resultType, append(result));
        stats.intrinsicCount++;
        return true;
    }

    private Instruction genArrayClone(RiMethod target, Value[] args) {
        FrameState state = curState.immutableCopy(bci());
        Value array = args[0];
        RiType type = array.declaredType();
        assert type != null && type.isResolved() && type.isArrayClass();
        Value newLength = args[1];

        Value oldLength = append(new ArrayLength(array, state));
        Value newArray = append(new NewObjectArrayClone(newLength, array, state));
        Value copyLength = append(new IfOp(newLength, Condition.LT, oldLength, newLength, oldLength));
        append(new ArrayCopy(array, Constant.forInt(0), newArray, Constant.forInt(0), copyLength, null, null));
        return (Instruction) newArray;
    }

    private Instruction genArrayCopy(RiMethod target, Value[] args) {
        FrameState state = curState.immutableCopy(bci());
        Instruction result;
        Value src = args[0];
        Value srcPos = args[1];
        Value dest = args[2];
        Value destPos = args[3];
        Value length = args[4];

        // Check src start pos.
        Value srcLength = append(new ArrayLength(src, state));

        // Check dest start pos.
        Value destLength = srcLength;
        if (src != dest) {
            destLength = append(new ArrayLength(dest, state));
        }

        // Check src end pos.
        Value srcEndPos = append(new ArithmeticOp(IADD, CiKind.Int, srcPos, length, false, null));
        append(new BoundsCheck(srcEndPos, srcLength, state, Condition.LE));

        // Check dest end pos.
        Value destEndPos = srcEndPos;
        if (destPos != srcPos) {
            destEndPos = append(new ArithmeticOp(IADD, CiKind.Int, destPos, length, false, null));
        }
        append(new BoundsCheck(destEndPos, destLength, state, Condition.LE));

        Value zero = append(Constant.forInt(0));
        append(new BoundsCheck(length, zero, state, Condition.GE));
        append(new BoundsCheck(srcPos, zero, state, Condition.GE));
        append(new BoundsCheck(destPos, zero, state, Condition.GE));

        result = new ArrayCopy(src, srcPos, dest, destPos, length, target, state);
        return result;
    }

    private boolean tryFoldable(RiMethod target, Value[] args) {
        CiConstant result = Canonicalizer.foldInvocation(compilation.runtime, target, args);
        if (result != null) {
            if (C1XOptions.TraceBytecodeParserLevel > 0) {
                log.println("|");
                log.println("|   [folded " + target + " --> " + result + "]");
                log.println("|");
            }

            pushReturn(returnKind(target), append(new Constant(result)));
            return true;
        }
        return false;
    }

    private boolean tryInline(RiMethod target, Value[] args) {
        boolean forcedInline = compilation.runtime.mustInline(target);
        if (forcedInline) {
            for (IRScope scope = scope().caller; scope != null; scope = scope.caller) {
                if (scope.method.equals(target)) {
                    throw new CiBailout("Cannot recursively inline method that is force-inlined: " + target);
                }
            }
            C1XMetrics.InlineForcedMethods++;
        }
        if (forcedInline || checkInliningConditions(target)) {
            if (C1XOptions.TraceBytecodeParserLevel > 0) {
                log.adjustIndentation(1);
                log.println("\\");
                log.adjustIndentation(1);
                if (C1XOptions.TraceBytecodeParserLevel < TRACELEVEL_STATE) {
                    log.println("|   [inlining " + target + "]");
                    log.println("|");
                }
            }
            if (!inlineWithBoundAccessor(target, args, forcedInline)) {
                inline(target, args, forcedInline);
            }

            if (C1XOptions.TraceBytecodeParserLevel > 0) {
                if (C1XOptions.TraceBytecodeParserLevel < TRACELEVEL_STATE) {
                    log.println("|");
                    log.println("|   [return to " + curState.scope().method + "]");
                }
                log.adjustIndentation(-1);
                log.println("/");
                log.adjustIndentation(-1);
            }
            return true;
        }
        return false;
    }

    private boolean checkInliningConditions(RiMethod target) {
        if (!C1XOptions.OptInline) {
            return false; // all inlining is turned off
        }
        if (!target.isResolved()) {
            return cannotInline(target, "unresolved method");
        }
        if (target.code() == null) {
            return cannotInline(target, "method has no code");
        }
        if (!target.holder().isInitialized()) {
            return cannotInline(target, "holder is not initialized");
        }
        if (recursiveInlineLevel(target) > C1XOptions.MaximumRecursiveInlineLevel) {
            return cannotInline(target, "recursive inlining too deep");
        }
        if (target.code().length > scopeData.maxInlineSize()) {
            return cannotInline(target, "inlinee too large for this level");
        }
        if (scopeData.scope.level + 1 > C1XOptions.MaximumInlineLevel) {
            return cannotInline(target, "inlining too deep");
        }
        if (stats.nodeCount > C1XOptions.MaximumDesiredSize) {
            return cannotInline(target, "compilation already too big " + "(" + compilation.stats.nodeCount + " nodes)");
        }
        if (compilation.runtime.mustNotInline(target)) {
            C1XMetrics.InlineForbiddenMethods++;
            return cannotInline(target, "inlining excluded by runtime");
        }
        if (compilation.runtime.mustNotCompile(target)) {
            return cannotInline(target, "compile excluded by runtime");
        }
        if (isSynchronized(target.accessFlags()) && !C1XOptions.OptInlineSynchronized) {
            return cannotInline(target, "is synchronized");
        }
        if (target.exceptionHandlers().length != 0 && !C1XOptions.OptInlineExcept) {
            return cannotInline(target, "has exception handlers");
        }
        if (!target.hasBalancedMonitors()) {
            return cannotInline(target, "has unbalanced monitors");
        }
        if (target.isConstructor()) {
            if (compilation.runtime.isExceptionType(target.holder())) {
                // don't inline constructors of throwable classes unless the inlining tree is
                // rooted in a throwable class
                if (!compilation.runtime.isExceptionType(rootScope().method.holder())) {
                    return cannotInline(target, "don't inline Throwable constructors");
                }
            }
        }
        return true;
    }

    private boolean cannotInline(RiMethod target, String reason) {
        if (C1XOptions.PrintInliningFailures) {
            TTY.println("Cannot inline " + target.toString() + " into " + compilation.method.toString() + " because of " + reason);
        }
        return false;
    }

    private void inline(RiMethod target, Value[] args, boolean forcedInline) {
        BlockBegin orig = curBlock;
        if (!forcedInline && !isStatic(target.accessFlags())) {
            // the receiver object must be null-checked for instance methods
            Value receiver = args[0];
            if (!receiver.isNonNull() && !receiver.kind.isWord()) {
                NullCheck check = new NullCheck(receiver, null);
                args[0] = append(check);
            }
        }

        // Introduce a new callee continuation point. All return instructions
        // in the callee will be transformed to Goto's to the continuation
        BlockBegin continuationBlock = blockAtOrNull(nextBCI());
        boolean continuationExisted = true;
        if (continuationBlock == null) {
            // there was not already a block starting at the next BCI
            continuationBlock = new BlockBegin(nextBCI(), ir.nextBlockNumber());
            continuationBlock.setDepthFirstNumber(0);
            continuationExisted = false;
        }
        // record the number of predecessors before inlining, to determine
        // whether the inlined method has added edges to the continuation
        int continuationPredecessors = continuationBlock.predecessors().size();

        // push the target scope
        pushScope(target, continuationBlock);

        // pass parameters into the callee state
        FrameState calleeState = curState;
        for (int i = 0; i < args.length; i++) {
            Value arg = args[i];
            if (arg != null) {
                calleeState.storeLocal(i, arg);
            }
        }

        // setup state that is used at returns from the inlined method.
        // this is essentially the state of the continuation block,
        // but without the return value on the stack.
        scopeData.setContinuationState(scope().callerState);

        Value lock = null;
        BlockBegin syncHandler = null;
        // inline the locking code if the target method is synchronized
        if (Modifier.isSynchronized(target.accessFlags())) {
            // lock the receiver object if it is an instance method, the class object otherwise
            lock = synchronizedObject(curState, target);
            syncHandler = new BlockBegin(Instruction.SYNCHRONIZATION_ENTRY_BCI, ir.nextBlockNumber());
            syncHandler.setNext(null, -1);
            inlineSyncEntry(lock, syncHandler);
        }

        BlockBegin calleeStartBlock = blockAt(0);
        if (calleeStartBlock.isParserLoopHeader()) {
            // the block is a loop header, so we have to insert a goto
            Goto gotoCallee = new Goto(calleeStartBlock, null, false);
            gotoCallee.setStateAfter(curState.immutableCopy(bci()));
            appendWithoutOptimization(gotoCallee, 0);
            curBlock.setEnd(gotoCallee);
            calleeStartBlock.mergeOrClone(calleeState);
            lastInstr = curBlock = calleeStartBlock;
            scopeData.addToWorkList(calleeStartBlock);
            // now iterate over all the blocks
            iterateAllBlocks();
        } else {
            // ready to resume parsing inlined method into this block
            iterateBytecodesForBlock(0, true);
            // now iterate over the rest of the blocks
            iterateAllBlocks();
        }

        assert continuationExisted || !continuationBlock.wasVisited() : "continuation should not have been parsed if we created it";

        ReturnBlock simpleInlineInfo = scopeData.simpleInlineInfo();
        if (simpleInlineInfo != null && curBlock == orig) {
            // Optimization: during parsing of the callee we
            // generated at least one Goto to the continuation block. If we
            // generated exactly one, and if the inlined method spanned exactly
            // one block (and we didn't have to Goto its entry), then we snip
            // off the Goto to the continuation, allowing control to fall
            // through back into the caller block and effectively performing
            // block merging. This allows local load elimination and local value numbering
            // to take place across multiple callee scopes if they are relatively simple, and
            // is currently essential to making inlining profitable. It also reduces the
            // number of blocks in the CFG
            lastInstr = simpleInlineInfo.returnPredecessor;
            curState = simpleInlineInfo.returnState.popScope();
            lastInstr.setNext(null, -1);
        } else if (continuationPredecessors == continuationBlock.predecessors().size()) {
            // Inlining caused the instructions after the invoke in the
            // caller to not reachable any more (i.e. no control flow path
            // in the callee was terminated by a return instruction).
            // So skip filling this block with instructions!
            assert continuationBlock == scopeData.continuation();
            assert lastInstr instanceof BlockEnd;
            skipBlock = true;
        } else {
            // Resume parsing in continuation block unless it was already parsed.
            // Note that if we don't change lastInstr here, iteration in
            // iterateBytecodesForBlock will stop when we return.
            if (!scopeData.continuation().wasVisited()) {
                // add continuation to work list instead of parsing it immediately
                assert lastInstr instanceof BlockEnd;
                scopeData.parent.addToWorkList(scopeData.continuation());
                skipBlock = true;
            }
        }

        // fill the exception handler for synchronized methods with instructions
        if (syncHandler != null && syncHandler.stateBefore() != null) {
            // generate unlocking code if the exception handler is reachable
            fillSyncHandler(lock, syncHandler, true);
        } else {
            popScope();
        }

        stats.inlineCount++;
    }

    private Value synchronizedObject(FrameState curState2, RiMethod target) {
        if (isStatic(target.accessFlags())) {
            Constant classConstant = new Constant(target.holder().getEncoding(Representation.JavaClass));
            return appendWithoutOptimization(classConstant, Instruction.SYNCHRONIZATION_ENTRY_BCI);
        } else {
            return curState2.localAt(0);
        }
    }

    private void inlineSyncEntry(Value lock, BlockBegin syncHandler) {
        genMonitorEnter(lock, Instruction.SYNCHRONIZATION_ENTRY_BCI);
        syncHandler.setExceptionEntry();
        syncHandler.setBlockFlag(BlockBegin.BlockFlag.IsOnWorkList);
        ExceptionHandler handler = new ExceptionHandler(new CiExceptionHandler(0, method().code().length, -1, 0, null));
        handler.setEntryBlock(syncHandler);
        scopeData.addExceptionHandler(handler);
    }

    private void fillSyncHandler(Value lock, BlockBegin syncHandler, boolean inlinedMethod) {
        BlockBegin origBlock = curBlock;
        MutableFrameState origState = curState;
        Instruction origLast = lastInstr;

        lastInstr = curBlock = syncHandler;
        while (lastInstr.next() != null) {
            // go forward to the end of the block
            lastInstr = lastInstr.next();
        }
        curState = syncHandler.stateBefore().copy();

        int bci = Instruction.SYNCHRONIZATION_ENTRY_BCI;
        Value exception = appendWithoutOptimization(new ExceptionObject(curState.immutableCopy(bci)), bci);

        assert lock != null;
        assert curState.locksSize() > 0 && curState.lockAt(locksSize() - 1) == lock;
        if (lock instanceof Instruction) {
            Instruction l = (Instruction) lock;
            if (!l.isAppended()) {
                lock = appendWithoutOptimization(l, Instruction.SYNCHRONIZATION_ENTRY_BCI);
            }
        }
        // exit the monitor
        genMonitorExit(lock, Instruction.SYNCHRONIZATION_ENTRY_BCI);

        // exit the context of the synchronized method
        if (inlinedMethod) {
            popScope();
            bci = curState.scope().callerBCI();
            curState = curState.popScope();
        }

        apush(exception);
        genThrow(bci);
        BlockEnd end = (BlockEnd) lastInstr;
        curBlock.setEnd(end);
        end.setStateAfter(curState.immutableCopy(bci()));

        curBlock = origBlock;
        curState = origState;
        lastInstr = origLast;
    }

    private void iterateAllBlocks() {
        BlockBegin b;
        while ((b = scopeData.removeFromWorkList()) != null) {
            if (!b.wasVisited()) {
                if (b.isOsrEntry()) {
                    // this is the OSR entry block, set up edges accordingly
                    setupOsrEntryBlock();
                    // this is no longer the OSR entry block
                    b.setOsrEntry(false);
                }
                b.setWasVisited(true);
                // now parse the block
                killMemoryMap();
                curBlock = b;
                curState = b.stateBefore().copy();
                lastInstr = b;
                b.setNext(null, -1);

                iterateBytecodesForBlock(b.bci(), false);
            }
        }
    }

    private void popScope() {
        int maxLocks = scope().maxLocks();
        scopeData = scopeData.parent;
        scope().updateMaxLocks(maxLocks);
    }

    private void popScopeForJsr() {
        scopeData = scopeData.parent;
    }

    private void setupOsrEntryBlock() {
        assert compilation.isOsrCompilation();

        int osrBCI = compilation.osrBCI;
        BytecodeStream s = scopeData.stream;
        RiOsrFrame frame = compilation.getOsrFrame();
        s.setBCI(osrBCI);
        s.next(); // XXX: why go to next bytecode?

        // create a new block to contain the OSR setup code
        ir.osrEntryBlock = new BlockBegin(osrBCI, ir.nextBlockNumber());
        ir.osrEntryBlock.setOsrEntry(true);
        ir.osrEntryBlock.setDepthFirstNumber(0);

        // get the target block of the OSR
        BlockBegin target = scopeData.blockAt(osrBCI);
        assert target != null && target.isOsrEntry();

        MutableFrameState state = target.stateBefore().copy();
        ir.osrEntryBlock.setStateBefore(state);

        killMemoryMap();
        curBlock = ir.osrEntryBlock;
        curState = state.copy();
        lastInstr = ir.osrEntryBlock;

        // create the entry instruction which represents the OSR state buffer
        // input from interpreter / JIT
        Instruction e = new OsrEntry();
        e.setFlag(Value.Flag.NonNull, true);

        for (int i = 0; i < state.localsSize(); i++) {
            Value local = state.localAt(i);
            Value get;
            int offset = frame.getLocalOffset(i);
            if (local != null) {
                // this is a live local according to compiler
                if (local.kind.isObject() && !frame.isLiveObject(i)) {
                    // the compiler thinks this is live, but not the interpreter
                    // pretend that it passed null
                    get = appendConstant(CiConstant.NULL_OBJECT);
                } else {
                    Value oc = appendConstant(CiConstant.forInt(offset));
                    get = append(new UnsafeGetRaw(local.kind, e, oc, 0, true));
                }
                state.storeLocal(i, get);
            }
        }

        assert state.callerState() == null;
        state.clearLocals();
        // ATTN: assumption: state is not used further below, else add .immutableCopy()
        Goto g = new Goto(target, state, false);
        append(g);
        ir.osrEntryBlock.setEnd(g);
        target.mergeOrClone(ir.osrEntryBlock.end().stateAfter());
    }

    private BlockEnd iterateBytecodesForBlock(int bci, boolean inliningIntoCurrentBlock) {
        skipBlock = false;
        assert curState != null;
        BytecodeStream s = scopeData.stream;
        s.setBCI(bci);

        BlockBegin block = curBlock;
        BlockEnd end = null;
        boolean pushException = block.isExceptionEntry() && block.next() == null;
        int prevBCI = bci;
        int endBCI = s.endBCI();
        boolean blockStart = true;

        while (bci < endBCI) {
            BlockBegin nextBlock = blockAtOrNull(bci);
            if (bci == 0 && inliningIntoCurrentBlock) {
                if (!nextBlock.isParserLoopHeader()) {
                    // Ignore the block boundary of the entry block of a method
                    // being inlined unless the block is a loop header.
                    nextBlock = null;
                    blockStart = false;
                }
            }
            if (nextBlock != null && nextBlock != block) {
                // we fell through to the next block, add a goto and break
                end = new Goto(nextBlock, null, false);
                lastInstr = lastInstr.setNext(end, prevBCI);
                break;
            }
            // read the opcode
            int opcode = s.currentBC();

            // check for active JSR during OSR compilation
            if (compilation.isOsrCompilation() && scope().isTopScope() && scopeData.parsingJsr() && s.currentBCI() == compilation.osrBCI) {
                throw new CiBailout("OSR not supported while a JSR is active");
            }

            // push an exception object onto the stack if we are parsing an exception handler
            if (pushException) {
                FrameState stateBefore = curState.immutableCopy(bci());
                apush(append(new ExceptionObject(stateBefore)));
                pushException = false;
            }

            traceState();
            traceInstruction(bci, s, opcode, blockStart);
            processBytecode(bci, s, opcode);

            prevBCI = bci;

            if (lastInstr instanceof BlockEnd) {
                end = (BlockEnd) lastInstr;
                break;
            }
            s.next();
            bci = s.currentBCI();
            blockStart = false;
        }

        // stop processing of this block
        if (skipBlock) {
            skipBlock = false;
            return (BlockEnd) lastInstr;
        }

        // if the method terminates, we don't need the stack anymore
        if (end instanceof Return || end instanceof Throw) {
            curState.clearStack();
        }

        // connect to begin and set state
        // NOTE that inlining may have changed the block we are parsing
        assert end != null : "end should exist after iterating over bytecodes";
        end.setStateAfter(curState.immutableCopy(bci()));
        curBlock.setEnd(end);
        // propagate the state
        for (BlockBegin succ : end.successors()) {
            assert succ.predecessors().contains(curBlock);
            succ.mergeOrClone(end.stateAfter());
            scopeData.addToWorkList(succ);
        }
        return end;
    }

    private void traceState() {
        if (C1XOptions.TraceBytecodeParserLevel >= TRACELEVEL_STATE && !TTY.isSuppressed()) {
            log.println(String.format("|   state [nr locals = %d, stack depth = %d, method = %s]", curState.localsSize(), curState.stackSize(), curState.scope().method));
            for (int i = 0; i < curState.localsSize(); ++i) {
                Value value = curState.localAt(i);
                log.println(String.format("|   local[%d] = %-8s : %s", i, value == null ? "bogus" : value.kind.javaName, value));
            }
            for (int i = 0; i < curState.stackSize(); ++i) {
                Value value = curState.stackAt(i);
                log.println(String.format("|   stack[%d] = %-8s : %s", i, value == null ? "bogus" : value.kind.javaName, value));
            }
            for (int i = 0; i < curState.locksSize(); ++i) {
                Value value = curState.lockAt(i);
                log.println(String.format("|   lock[%d] = %-8s : %s", i, value == null ? "bogus" : value.kind.javaName, value));
            }
        }
    }

    private void processBytecode(int bci, BytecodeStream s, int opcode) {
        int cpi;

        // Checkstyle: stop
        switch (opcode) {
            case NOP            : /* nothing to do */ break;
            case ACONST_NULL    : apush(appendConstant(CiConstant.NULL_OBJECT)); break;
            case ICONST_M1      : ipush(appendConstant(CiConstant.INT_MINUS_1)); break;
            case ICONST_0       : ipush(appendConstant(CiConstant.INT_0)); break;
            case ICONST_1       : ipush(appendConstant(CiConstant.INT_1)); break;
            case ICONST_2       : ipush(appendConstant(CiConstant.INT_2)); break;
            case ICONST_3       : ipush(appendConstant(CiConstant.INT_3)); break;
            case ICONST_4       : ipush(appendConstant(CiConstant.INT_4)); break;
            case ICONST_5       : ipush(appendConstant(CiConstant.INT_5)); break;
            case LCONST_0       : lpush(appendConstant(CiConstant.LONG_0)); break;
            case LCONST_1       : lpush(appendConstant(CiConstant.LONG_1)); break;
            case FCONST_0       : fpush(appendConstant(CiConstant.FLOAT_0)); break;
            case FCONST_1       : fpush(appendConstant(CiConstant.FLOAT_1)); break;
            case FCONST_2       : fpush(appendConstant(CiConstant.FLOAT_2)); break;
            case DCONST_0       : dpush(appendConstant(CiConstant.DOUBLE_0)); break;
            case DCONST_1       : dpush(appendConstant(CiConstant.DOUBLE_1)); break;
            case BIPUSH         : ipush(appendConstant(CiConstant.forInt(s.readByte()))); break;
            case SIPUSH         : ipush(appendConstant(CiConstant.forInt(s.readShort()))); break;
            case LDC            : // fall through
            case LDC_W          : // fall through
            case LDC2_W         : genLoadConstant(s.readCPI()); break;
            case ILOAD          : loadLocal(s.readLocalIndex(), CiKind.Int); break;
            case LLOAD          : loadLocal(s.readLocalIndex(), CiKind.Long); break;
            case FLOAD          : loadLocal(s.readLocalIndex(), CiKind.Float); break;
            case DLOAD          : loadLocal(s.readLocalIndex(), CiKind.Double); break;
            case ALOAD          : loadLocal(s.readLocalIndex(), CiKind.Object); break;
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
            case ISTORE         : storeLocal(CiKind.Int, s.readLocalIndex()); break;
            case LSTORE         : storeLocal(CiKind.Long, s.readLocalIndex()); break;
            case FSTORE         : storeLocal(CiKind.Float, s.readLocalIndex()); break;
            case DSTORE         : storeLocal(CiKind.Double, s.readLocalIndex()); break;
            case ASTORE         : storeLocal(CiKind.Object, s.readLocalIndex()); break;
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
            case IREM           : genArithmeticOp(CiKind.Int, opcode, curState.immutableCopy(bci())); break;
            case LADD           : // fall through
            case LSUB           : // fall through
            case LMUL           : genArithmeticOp(CiKind.Long, opcode); break;
            case LDIV           : // fall through
            case LREM           : genArithmeticOp(CiKind.Long, opcode, curState.immutableCopy(bci())); break;
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
            case IF_ACMPEQ      : genIfSame(peekKind(), Condition.EQ); break;
            case IF_ACMPNE      : genIfSame(peekKind(), Condition.NE); break;
            case GOTO           : genGoto(s.currentBCI(), s.readBranchDest()); break;
            case JSR            : genJsr(s.readBranchDest()); break;
            case RET            : genRet(s.readLocalIndex()); break;
            case TABLESWITCH    : genTableswitch(); break;
            case LOOKUPSWITCH   : genLookupswitch(); break;
            case IRETURN        : genReturn(ipop()); break;
            case LRETURN        : genReturn(lpop()); break;
            case FRETURN        : genReturn(fpop()); break;
            case DRETURN        : genReturn(dpop()); break;
            case ARETURN        : genReturn(apop()); break;
            case RETURN         : genReturn(null  ); break;
            case GETSTATIC      : cpi = s.readCPI(); genGetStatic(cpi, constantPool().lookupField(cpi, opcode)); break;
            case PUTSTATIC      : cpi = s.readCPI(); genPutStatic(cpi, constantPool().lookupField(cpi, opcode)); break;
            case GETFIELD       : cpi = s.readCPI(); genGetField(cpi, constantPool().lookupField(cpi, opcode)); break;
            case PUTFIELD       : cpi = s.readCPI(); genPutField(cpi, constantPool().lookupField(cpi, opcode)); break;
            case INVOKEVIRTUAL  : cpi = s.readCPI(); genInvokeVirtual(constantPool().lookupMethod(cpi, opcode), cpi, constantPool()); break;
            case INVOKESPECIAL  : cpi = s.readCPI(); genInvokeSpecial(constantPool().lookupMethod(cpi, opcode), null, cpi, constantPool()); break;
            case INVOKESTATIC   : cpi = s.readCPI(); genInvokeStatic(constantPool().lookupMethod(cpi, opcode), cpi, constantPool()); break;
            case INVOKEINTERFACE: cpi = s.readCPI(); genInvokeInterface(constantPool().lookupMethod(cpi, opcode), cpi, constantPool()); break;
            case NEW            : genNewInstance(s.readCPI()); break;
            case NEWARRAY       : genNewTypeArray(s.readLocalIndex()); break;
            case ANEWARRAY      : genNewObjectArray(s.readCPI()); break;
            case ARRAYLENGTH    : genArrayLength(); break;
            case ATHROW         : genThrow(s.currentBCI()); break;
            case CHECKCAST      : genCheckCast(); break;
            case INSTANCEOF     : genInstanceOf(); break;
            case MONITORENTER   : genMonitorEnter(apop(), s.currentBCI()); break;
            case MONITOREXIT    : genMonitorExit(apop(), s.currentBCI()); break;
            case MULTIANEWARRAY : genNewMultiArray(s.readCPI()); break;
            case IFNULL         : genIfNull(Condition.EQ); break;
            case IFNONNULL      : genIfNull(Condition.NE); break;
            case GOTO_W         : genGoto(s.currentBCI(), s.readFarBranchDest()); break;
            case JSR_W          : genJsr(s.readFarBranchDest()); break;
            default:
                processExtendedBytecode(bci, s, opcode);
        }
        // Checkstyle: resume
    }

    private void processExtendedBytecode(int bci, BytecodeStream s, int opcode) {
        // Checkstyle: stop
        switch (opcode) {
            case UNSAFE_CAST    : genUnsafeCast(constantPool().lookupMethod(s.readCPI(), (byte)Bytecodes.UNSAFE_CAST)); break;
            case WLOAD          : loadLocal(s.readLocalIndex(), CiKind.Word); break;
            case WLOAD_0        : loadLocal(0, CiKind.Word); break;
            case WLOAD_1        : loadLocal(1, CiKind.Word); break;
            case WLOAD_2        : loadLocal(2, CiKind.Word); break;
            case WLOAD_3        : loadLocal(3, CiKind.Word); break;

            case WSTORE         : storeLocal(CiKind.Word, s.readLocalIndex()); break;
            case WSTORE_0       : // fall through
            case WSTORE_1       : // fall through
            case WSTORE_2       : // fall through
            case WSTORE_3       : storeLocal(CiKind.Word, opcode - WSTORE_0); break;

            case WCONST_0       : wpush(appendConstant(CiConstant.ZERO)); break;
            case WDIV           : // fall through
            case WREM           : genArithmeticOp(CiKind.Word, opcode, curState.immutableCopy(bci())); break;
            case WDIVI          : genArithmeticOp(CiKind.Word, opcode, CiKind.Word, CiKind.Int, curState.immutableCopy(bci())); break;
            case WREMI          : genArithmeticOp(CiKind.Int, opcode, CiKind.Word, CiKind.Int, curState.immutableCopy(bci())); break;

            case READREG        : genLoadRegister(s.readCPI()); break;
            case WRITEREG       : genStoreRegister(s.readCPI()); break;
            case INCREG         : genIncRegister(s.readCPI()); break;

            case PREAD          : genLoadPointer(PREAD      | (s.readCPI() << 8)); break;
            case PGET           : genLoadPointer(PGET       | (s.readCPI() << 8)); break;
            case PWRITE         : genStorePointer(PWRITE    | (s.readCPI() << 8)); break;
            case PSET           : genStorePointer(PSET      | (s.readCPI() << 8)); break;
            case PCMPSWP        : genCompareAndSwap(PCMPSWP | (s.readCPI() << 8)); break;
            case MEMBAR         : genMemoryBarrier(s.readCPI()); break;

            case WRETURN        : genReturn(wpop()); break;
            case INFOPOINT      : genInfopoint(INFOPOINT | (s.readUByte(bci() + 1) << 16), s.readUByte(bci() + 2) != 0); break;
            case JNICALL        : genNativeCall(s.readCPI()); break;
            case JNIOP          : genJniOp(s.readCPI()); break;
            case ALLOCA         : genStackAllocate(); break;

            case MOV_I2F        : genConvert(opcode, CiKind.Int, CiKind.Float ); break;
            case MOV_F2I        : genConvert(opcode, CiKind.Float, CiKind.Int ); break;
            case MOV_L2D        : genConvert(opcode, CiKind.Long, CiKind.Double ); break;
            case MOV_D2L        : genConvert(opcode, CiKind.Double, CiKind.Long ); break;

            case UCMP           : genUnsignedCompareOp(CiKind.Int, opcode, s.readCPI()); break;
            case UWCMP          : genUnsignedCompareOp(CiKind.Word, opcode, s.readCPI()); break;

            case STACKHANDLE    : genStackHandle(s.readCPI() == 0); break;
            case BREAKPOINT_TRAP: genBreakpointTrap(); break;
            case PAUSE          : genPause(); break;
            case LSB            : // fall through
            case MSB            : genSignificantBit(opcode);break;

            case TEMPLATE_CALL  : genTemplateCall(constantPool().lookupMethod(s.readCPI(), (byte)Bytecodes.TEMPLATE_CALL)); break;
            case ICMP           : genCompareOp(CiKind.Int, opcode, CiKind.Void); break;
            case WCMP           : genCompareOp(CiKind.Word, opcode, CiKind.Void); break;

            case BREAKPOINT:
                throw new CiBailout("concurrent setting of breakpoint");
            default:
                throw new CiBailout("Unsupported opcode " + opcode + " (" + nameOf(opcode) + ") [bci=" + bci + "]");
        }
        // Checkstyle: resume
    }

    private void traceInstruction(int bci, BytecodeStream s, int opcode, boolean blockStart) {
        if (C1XOptions.TraceBytecodeParserLevel >= TRACELEVEL_INSTRUCTIONS && !TTY.isSuppressed()) {
            StringBuilder sb = new StringBuilder(40);
            sb.append(blockStart ? '+' : '|');
            if (bci < 10) {
                sb.append("  ");
            } else if (bci < 100) {
                sb.append(' ');
            }
            sb.append(bci).append(": ").append(Bytecodes.nameOf(opcode));
            for (int i = bci + 1; i < s.nextBCI(); ++i) {
                sb.append(' ').append(s.readUByte(i));
            }
            log.println(sb.toString());
        }
    }

    private void genPause() {
        append(new Pause());
    }

    private void genBreakpointTrap() {
        append(new BreakpointTrap());
    }

    private void genStackHandle(boolean isCategory1) {
        Value value = curState.xpop();
        wpush(append(new StackHandle(value)));
    }

    private void genStackAllocate() {
        Value size = pop(CiKind.Int);
        wpush(append(new StackAllocate(size)));
    }

    private void genSignificantBit(int opcode) {
        Value value = pop(CiKind.Word);
        push(CiKind.Int, append(new SignificantBitOp(value, opcode)));
    }

    private void appendSnippetCall(RiSnippetCall snippetCall) {
        Value[] args = new Value[snippetCall.arguments.length];
        RiMethod snippet = snippetCall.snippet;
        RiSignature signature = snippet.signature();
        assert signature.argumentCount(!isStatic(snippet.accessFlags())) == args.length;
        for (int i = args.length - 1; i >= 0; --i) {
            CiKind argKind = signature.argumentKindAt(i);
            if (snippetCall.arguments[i] == null) {
                args[i] = pop(argKind);
            } else {
                args[i] = append(new Constant(snippetCall.arguments[i]));
            }
        }

        if (!tryRemoveCall(snippet, args, true)) {
            if (!tryInline(snippet, args)) {
                appendInvoke(snippetCall.opcode, snippet, args, true, (char) 0, constantPool());
            }
        }
    }

    private void genJniOp(int operand) {
        RiSnippets snippets = compilation.runtime.getSnippets();
        switch (operand) {
            case JniOp.LINK: {
                RiMethod nativeMethod = scope().method;
                RiSnippetCall linkSnippet = snippets.link(nativeMethod);
                if (linkSnippet.result != null) {
                    wpush(appendConstant(linkSnippet.result));
                } else {
                    appendSnippetCall(linkSnippet);
                }
                break;
            }
            case JniOp.J2N: {
                RiMethod nativeMethod = scope().method;
                appendSnippetCall(snippets.enterNative(nativeMethod));
                break;
            }
            case JniOp.N2J: {
                RiMethod nativeMethod = scope().method;
                appendSnippetCall(snippets.enterVM(nativeMethod));
                break;
            }
        }
    }

    private void genNativeCall(int cpi) {
        Value nativeFunctionAddress = wpop();
        RiSignature sig = constantPool().lookupSignature(cpi);
        Value[] args = curState.popArguments(sig.argumentSlots(false));

        RiMethod nativeMethod = scope().method;
        CiKind returnKind = sig.returnKind();
        pushReturn(returnKind, append(new NativeCall(nativeMethod, sig, nativeFunctionAddress, args, null)));

        // Sign extend or zero the upper bits of a return value smaller than an int to
        // preserve the invariant that all such values are represented by an int
        // in the VM. We cannot rely on the native C compiler doing this for us.
        switch (sig.returnKind()) {
            case Boolean:
            case Byte: {
                genConvert(I2B, CiKind.Int, CiKind.Byte);
                break;
            }
            case Short: {
                genConvert(I2S, CiKind.Int, CiKind.Short);
                break;
            }
            case Char: {
                genConvert(I2C, CiKind.Int, CiKind.Char);
                break;
            }
        }
    }

    void genTemplateCall(RiMethod method) {
        RiSignature sig = method.signature();
        Value[] args = curState.popArguments(sig.argumentSlots(false));
        assert args.length <= 2;
        CiKind returnKind = sig.returnKind();
        Value address = null;
        Value receiver = null;
        if (args.length == 1) {
            address = args[0];
            assert address.kind.isWord();
        } else if (args.length == 2) {
            address = args[0];
            assert address.kind.isWord();
            receiver = args[1];
            assert receiver.kind.isObject();
        }
        pushReturn(returnKind, append(new TemplateCall(returnKind, address, receiver)));
    }

    private void genInfopoint(int opcode, boolean inclFrame) {
        // TODO: create slimmer frame state if inclFrame is false
        FrameState state = curState.immutableCopy(bci());
        assert opcode != SAFEPOINT || !scopeData.noSafepoints() : "cannot place explicit safepoint in uninterruptible code scope";
        Value result = append(new Infopoint(opcode, state));
        if (!result.kind.isVoid()) {
            push(result.kind, result);
        }
    }

    private void genLoadRegister(int registerId) {
        CiRegister register = compilation.registerConfig.getRegisterForRole(registerId);
        if (register == null) {
            throw new CiBailout("Unsupported READREG operand " + registerId);
        }
        LoadRegister load = new LoadRegister(CiKind.Word, register);
        RiRegisterAttributes regAttr = compilation.registerConfig.getAttributesMap()[register.number];
        if (regAttr.isNonZero) {
            load.setFlag(Flag.NonNull);
        }
        wpush(append(load));
    }

    private void genStoreRegister(int registerId) {
        CiRegister register = compilation.registerConfig.getRegisterForRole(registerId);
        if (register == null) {
            throw new CiBailout("Unsupported WRITEREG operand " + registerId);
        }
        Value value = pop(CiKind.Word);
        append(new StoreRegister(CiKind.Word, register, value));
    }

    private void genIncRegister(int registerId) {
        CiRegister register = compilation.registerConfig.getRegisterForRole(registerId);
        if (register == null) {
            throw new CiBailout("Unsupported INCREG operand " + registerId);
        }
        Value value = pop(CiKind.Int);
        append(new IncrementRegister(register, value));
    }

    /**
     * Gets the data kind corresponding to a given pointer operation opcode.
     * The data kind may be more specific than a {@linkplain CiKind#stackKind()}.
     *
     * @return the kind of value at the address accessed by the pointer operation denoted by {@code opcode}
     */
    private static CiKind dataKindForPointerOp(int opcode) {
        switch (opcode) {
            case PGET_BYTE          :
            case PSET_BYTE          :
            case PREAD_BYTE         :
            case PREAD_BYTE_I       :
            case PWRITE_BYTE        :
            case PWRITE_BYTE_I      : return CiKind.Byte;
            case PGET_CHAR          :
            case PREAD_CHAR         :
            case PREAD_CHAR_I       : return CiKind.Char;
            case PGET_SHORT         :
            case PSET_SHORT         :
            case PREAD_SHORT        :
            case PREAD_SHORT_I      :
            case PWRITE_SHORT       :
            case PWRITE_SHORT_I     : return CiKind.Short;
            case PGET_INT           :
            case PSET_INT           :
            case PREAD_INT          :
            case PREAD_INT_I        :
            case PWRITE_INT         :
            case PWRITE_INT_I       : return CiKind.Int;
            case PGET_FLOAT         :
            case PSET_FLOAT         :
            case PREAD_FLOAT        :
            case PREAD_FLOAT_I      :
            case PWRITE_FLOAT       :
            case PWRITE_FLOAT_I     : return CiKind.Float;
            case PGET_LONG          :
            case PSET_LONG          :
            case PREAD_LONG         :
            case PREAD_LONG_I       :
            case PWRITE_LONG        :
            case PWRITE_LONG_I      : return CiKind.Long;
            case PGET_DOUBLE        :
            case PSET_DOUBLE        :
            case PREAD_DOUBLE       :
            case PREAD_DOUBLE_I     :
            case PWRITE_DOUBLE      :
            case PWRITE_DOUBLE_I    : return CiKind.Double;
            case PGET_WORD          :
            case PSET_WORD          :
            case PREAD_WORD         :
            case PREAD_WORD_I       :
            case PWRITE_WORD        :
            case PWRITE_WORD_I      : return CiKind.Word;
            case PGET_REFERENCE     :
            case PSET_REFERENCE     :
            case PREAD_REFERENCE    :
            case PREAD_REFERENCE_I  :
            case PWRITE_REFERENCE   :
            case PWRITE_REFERENCE_I : return CiKind.Object;
            default:
                throw new CiBailout("Unsupported pointer operation opcode " + opcode + "(" + nameOf(opcode) + ")");
        }
    }

    /**
     * Pops the value producing the scaled-index or the byte offset for a pointer operation.
     * If compiling for a 64-bit platform and the value is an {@link CiKind#Int} parameter,
     * then a conversion is inserted to sign extend the int to a word.
     *
     * This is required as the value is used as a 64-bit value and so the high 32 bits
     * need to be correct.
     *
     * @param isInt specifies if the value is an {@code int}
     */
    private Value popOffsetOrIndexForPointerOp(boolean isInt) {
        if (isInt) {
            Value offsetOrIndex = ipop();
            if (compilation.target.arch.is64bit() && offsetOrIndex instanceof Local) {
                return append(new Convert(I2L, offsetOrIndex, CiKind.Word));
            }
            return offsetOrIndex;
        }
        return wpop();
    }

    private void genLoadPointer(int opcode) {
        FrameState stateBefore = curState.immutableCopy(bci());
        CiKind dataKind = dataKindForPointerOp(opcode);
        Value offsetOrIndex;
        Value displacement;
        if ((opcode & 0xff) == PREAD) {
            offsetOrIndex = popOffsetOrIndexForPointerOp(opcode >= PREAD_BYTE_I && opcode <= PREAD_REFERENCE_I);
            displacement = null;
        } else {
            offsetOrIndex = popOffsetOrIndexForPointerOp(true);
            displacement = ipop();
        }
        Value pointer = wpop();
        push(dataKind.stackKind(), append(new LoadPointer(dataKind, opcode, pointer, displacement, offsetOrIndex, stateBefore, false)));
    }

    private void genStorePointer(int opcode) {
        FrameState stateBefore = curState.immutableCopy(bci());
        CiKind dataKind = dataKindForPointerOp(opcode);
        Value value = pop(dataKind.stackKind());
        Value offsetOrIndex;
        Value displacement;
        if ((opcode & 0xff) == PWRITE) {
            offsetOrIndex = popOffsetOrIndexForPointerOp(opcode >= PWRITE_BYTE_I && opcode <= PWRITE_REFERENCE_I);
            displacement = null;
        } else {
            offsetOrIndex = popOffsetOrIndexForPointerOp(true);
            displacement = ipop();
        }
        Value pointer = wpop();
        append(new StorePointer(opcode, dataKind, pointer, displacement, offsetOrIndex, value, stateBefore, false));
    }

    private static CiKind kindForCompareAndSwap(int opcode) {
        switch (opcode) {
            case PCMPSWP_INT        :
            case PCMPSWP_INT_I      : return CiKind.Int;
            case PCMPSWP_WORD       :
            case PCMPSWP_WORD_I     : return CiKind.Word;
            case PCMPSWP_REFERENCE  :
            case PCMPSWP_REFERENCE_I: return CiKind.Object;
            default:
                throw new CiBailout("Unsupported compare-and-swap opcode " + opcode + "(" + nameOf(opcode) + ")");
        }
    }

    private void genCompareAndSwap(int opcode) {
        FrameState stateBefore = curState.immutableCopy(bci());
        CiKind kind = kindForCompareAndSwap(opcode);
        Value newValue = pop(kind);
        Value expectedValue = pop(kind);
        Value offset;
        offset = popOffsetOrIndexForPointerOp(opcode >= PCMPSWP_INT_I && opcode <= PCMPSWP_REFERENCE_I);
        Value pointer = wpop();
        push(kind, append(new CompareAndSwap(opcode, pointer, offset, expectedValue, newValue, stateBefore, false)));
    }


    private void genMemoryBarrier(int barriers) {
        int explicitMemoryBarriers = barriers & ~compilation.target.arch.implicitMemoryBarriers;
        if (explicitMemoryBarriers != 0) {
            append(new MemoryBarrier(explicitMemoryBarriers));
        }
    }

    private void genArrayLength() {
        FrameState stateBefore = curState.immutableCopy(bci());
        ipush(append(new ArrayLength(apop(), stateBefore)));
    }

    void killMemoryMap() {
        if (localValueMap != null) {
            localValueMap.killAll();
        }
        if (memoryMap != null) {
            memoryMap.kill();
        }
    }

    boolean assumeLeafClass(RiType type) {
        if (type.isResolved()) {
            if (isFinal(type.accessFlags())) {
                return true;
            }

            if (C1XOptions.UseAssumptions) {
                RiType assumed = type.uniqueConcreteSubtype();
                if (assumed != null && assumed == type) {
                    if (C1XOptions.PrintAssumptions) {
                        TTY.println("Recording leaf class assumption for " + type.name());
                    }
                    compilation.assumptions.recordConcreteSubtype(type, assumed);
                    return true;
                }
            }
        }
        return false;
    }

    RiMethod getAssumedLeafMethod(RiMethod method) {
        if (method.isResolved()) {
            if (method.isLeafMethod()) {
                return method;
            }

            if (C1XOptions.UseAssumptions) {
                RiMethod assumed = method.uniqueConcreteMethod();
                if (assumed != null) {
                    if (C1XOptions.PrintAssumptions) {
                        TTY.println("Recording concrete method assumption in context of " + method.holder().name() + ": " + assumed.name());
                    }
                    compilation.assumptions.recordConcreteMethod(method, assumed);
                    return assumed;
                } else {
                    if (C1XOptions.PrintAssumptions) {
                        TTY.println("Did not find unique concrete method for " + method);
                    }
                }
            }
        }
        return null;
    }

    private int recursiveInlineLevel(RiMethod target) {
        int rec = 0;
        IRScope scope = scope();
        while (scope != null) {
            if (scope.method != target) {
                break;
            }
            scope = scope.caller;
            rec++;
        }
        return rec;
    }

    private RiConstantPool constantPool() {
        return scopeData.constantPool;
    }
}
