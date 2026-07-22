/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm.nodes;

import static org.graalvm.wasm.BinaryStreamParser.rawPeekI32;
import static org.graalvm.wasm.BinaryStreamParser.rawPeekI64;
import static org.graalvm.wasm.BinaryStreamParser.rawPeekI8;
import static org.graalvm.wasm.BinaryStreamParser.rawPeekU16;
import static org.graalvm.wasm.BinaryStreamParser.rawPeekU32;
import static org.graalvm.wasm.BinaryStreamParser.rawPeekU8;
import static org.graalvm.wasm.nodes.WasmFrame.drop;
import static org.graalvm.wasm.nodes.WasmFrame.dropObject;
import static org.graalvm.wasm.nodes.WasmFrame.dropPrimitive;
import static org.graalvm.wasm.nodes.WasmFrame.popBoolean;
import static org.graalvm.wasm.nodes.WasmFrame.popDouble;
import static org.graalvm.wasm.nodes.WasmFrame.popFloat;
import static org.graalvm.wasm.nodes.WasmFrame.popInt;
import static org.graalvm.wasm.nodes.WasmFrame.popLong;
import static org.graalvm.wasm.nodes.WasmFrame.popReference;
import static org.graalvm.wasm.nodes.WasmFrame.popVector128;
import static org.graalvm.wasm.nodes.WasmFrame.pushDouble;
import static org.graalvm.wasm.nodes.WasmFrame.pushFloat;
import static org.graalvm.wasm.nodes.WasmFrame.pushInt;
import static org.graalvm.wasm.nodes.WasmFrame.pushLong;
import static org.graalvm.wasm.nodes.WasmFrame.pushReference;
import static org.graalvm.wasm.nodes.WasmFrame.pushVector128;

import com.oracle.truffle.api.impl.FrameWithoutBoxing;
import com.oracle.truffle.api.nodes.ControlFlowException;
import org.graalvm.wasm.BinaryStreamParser;
import org.graalvm.wasm.GlobalRegistry;
import org.graalvm.wasm.WasmArguments;
import org.graalvm.wasm.WasmCodeEntry;
import org.graalvm.wasm.WasmConstant;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmFunction;
import org.graalvm.wasm.WasmFunctionInstance;
import org.graalvm.wasm.WasmTypedHeapObject;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmMath;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmTable;
import org.graalvm.wasm.WasmTag;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.vector.Vector128;
import org.graalvm.wasm.vector.Vector128Ops;
import org.graalvm.wasm.array.WasmArray;
import org.graalvm.wasm.array.WasmFloat32Array;
import org.graalvm.wasm.array.WasmFloat64Array;
import org.graalvm.wasm.array.WasmInt16Array;
import org.graalvm.wasm.array.WasmInt32Array;
import org.graalvm.wasm.array.WasmInt64Array;
import org.graalvm.wasm.array.WasmInt8Array;
import org.graalvm.wasm.array.WasmRefArray;
import org.graalvm.wasm.array.WasmVec128Array;
import org.graalvm.wasm.constants.Bytecode;
import org.graalvm.wasm.constants.BytecodeBitEncoding;
import org.graalvm.wasm.constants.ExceptionHandlerType;
import org.graalvm.wasm.constants.StackEffects;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.exception.WasmRuntimeException;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.memory.WasmMemoryLibrary;
import org.graalvm.wasm.parser.validation.ExceptionHandler;
import org.graalvm.wasm.struct.WasmStruct;
import org.graalvm.wasm.struct.WasmStructAccess;
import org.graalvm.wasm.types.DefinedType;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.EarlyInline;
import com.oracle.truffle.api.CompilerDirectives.EarlyEscapeAnalysis;
import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.HostCompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.staticobject.StaticProperty;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterHandler;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterFetchOpcode;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterHandlerConfig;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterHandlerConfig.Argument;

import java.io.Serial;

/**
 * This node represents the function body of a WebAssembly function. It executes the instruction
 * sequence contained in a function and represents the main interpreter loop. This node also
 * functions as a support node for the {@link WasmInstrumentableFunctionNode}. When an instrument
 * attaches, the {@link WasmInstrumentableFunctionNode} replaces the current instruction sequence of
 * this node with a slightly modified version. The modified version contains {@link Bytecode#NOTIFY}
 * instructions for all locations in the WebAssembly bytecode that map to a position in the source
 * code (C, C++, Rust, ...). When the {@link Bytecode#NOTIFY} instruction is executed, the
 * instrument gets notified that a certain line in the source code was reached.
 */
public final class WasmFunctionNode<V128> extends Node implements BytecodeOSRNode {

    private static final int REPORT_LOOP_STRIDE = 1 << 8;

    static {
        assert Integer.bitCount(REPORT_LOOP_STRIDE) == 1 : "must be a power of 2";
    }

    private final WasmModule module;
    private final WasmCodeEntry codeEntry;

    @Children private final Node[] callNodes;
    @CompilationFinal private Object osrMetadata;

    private final int bytecodeStartOffset;
    private final int bytecodeEndOffset;
    private final int exceptionTableOffset;
    @CompilationFinal(dimensions = 1) private final byte[] bytecode;
    @CompilationFinal private WasmNotifyFunction notifyFunction;

    @Children private final WasmMemoryLibrary[] memoryLibs;

    public WasmFunctionNode(WasmModule module, WasmCodeEntry codeEntry, int bytecodeStartOffset, int bytecodeEndOffset, int exceptionTableOffset, Node[] callNodes, WasmMemoryLibrary[] memoryLibs) {
        this.module = module;
        this.codeEntry = codeEntry;
        this.bytecodeStartOffset = bytecodeStartOffset;
        this.bytecodeEndOffset = bytecodeEndOffset;
        this.exceptionTableOffset = exceptionTableOffset;
        this.bytecode = codeEntry.bytecode();
        this.callNodes = new Node[callNodes.length];
        for (int childIndex = 0; childIndex < callNodes.length; childIndex++) {
            this.callNodes[childIndex] = insert(callNodes[childIndex].deepCopy());
        }
        this.memoryLibs = memoryLibs;
    }

    /**
     * Copies a function node with instrumentation enabled in the <code>bytecode</code>. This should
     * only be called by {@link WasmInstrumentableFunctionNode} when an instrument attaches and the
     * bytecode needs to be rewritten to include instrumentation instructions.
     *
     * @param node The existing {@link WasmFunctionNode} used for copying most information
     * @param bytecode The instrumented bytecode
     * @param notifyFunction The callback used by {@link Bytecode#NOTIFY} instructions to inform
     *            instruments about statements in the bytecode
     */
    WasmFunctionNode(WasmFunctionNode<V128> node, byte[] bytecode, int bytecodeStartOffset, int bytecodeEndOffset, int exceptionTableOffset, WasmNotifyFunction notifyFunction) {
        this.module = node.module;
        this.codeEntry = node.codeEntry;
        this.bytecodeStartOffset = bytecodeStartOffset;
        this.bytecodeEndOffset = bytecodeEndOffset;
        this.exceptionTableOffset = exceptionTableOffset;
        this.bytecode = bytecode;
        this.callNodes = new Node[node.callNodes.length];
        for (int childIndex = 0; childIndex < callNodes.length; childIndex++) {
            this.callNodes[childIndex] = insert(node.callNodes[childIndex].deepCopy());
        }
        this.memoryLibs = node.memoryLibs;
        this.notifyFunction = notifyFunction;
    }

    private static void enterErrorBranch(WasmCodeEntry codeEntry) {
        codeEntry.errorBranch();
    }

    private WasmMemory memory(WasmInstance instance, int index) {
        return instance.memory(index).checkSize(memoryLib(index), module.memoryInitialSize(index));
    }

    private static WasmMemory memory(WasmInstance instance, WasmMemoryLibrary[] memoryLibs, WasmModule module, int index) {
        return instance.memory(index).checkSize(memoryLibs[index], module.memoryInitialSize(index));
    }

    private WasmMemoryLibrary memoryLib(int memoryIndex) {
        return memoryLibs[memoryIndex];
    }

    @SuppressWarnings("unchecked")
    private Vector128Ops<V128> vector128Ops() {
        return (Vector128Ops<V128>) Vector128Ops.SINGLETON_IMPLEMENTATION;
    }

    // region OSR support

    record OSRInterpreterState(int lineNumber, int activeLegacyCatchCount) {
    }

    /**
     * Prepare for OSR. An interpreter that uses {@code long} targets must override this method.
     */
    @Override
    public void prepareOSR(long target) {
        // do nothing
    }

    @Override
    public void copyIntoOSRFrame(VirtualFrame osrFrame, VirtualFrame parentFrame, long target, Object targetMetadata) {
        BytecodeOSRNode.super.copyIntoOSRFrame(osrFrame, parentFrame, (int) target, targetMetadata);
    }

    @Override
    public Object executeOSR(VirtualFrame osrFrame, long target, Object interpreterState) {
        WasmInstance instance = ((WasmRootNode) getRootNode()).instance(osrFrame);
        int offset = (int) target;
        int stackPointer = (int) (target >>> 32);
        OSRInterpreterState osrInterpreterState = (OSRInterpreterState) interpreterState;
        int line = osrInterpreterState.lineNumber();
        int activeLegacyCatchCount = osrInterpreterState.activeLegacyCatchCount();
        return executeBodyFromOffset(instance, osrFrame, offset, stackPointer, line, activeLegacyCatchCount);
    }

    @Override
    public Object getOSRMetadata() {
        return osrMetadata;
    }

    @Override
    public void setOSRMetadata(Object osrMetadata) {
        this.osrMetadata = osrMetadata;
    }

    /** Preserve the first argument, i.e. the {@link WasmInstance}. */
    @Override
    public Object[] storeParentFrameInArguments(VirtualFrame parentFrame) {
        CompilerAsserts.neverPartOfCompilation();
        WasmInstance instance = ((WasmRootNode) getRootNode()).instance(parentFrame);
        Object[] osrFrameArgs = new Object[]{instance, parentFrame};
        assert WasmArguments.isValid(osrFrameArgs);
        return osrFrameArgs;
    }

    @Override
    public Frame restoreParentFrameFromArguments(Object[] arguments) {
        return (Frame) arguments[1];
    }

    // endregion OSR support

    /**
     * Smaller than int[1], does not kill int[] on write and doesn't need bounds checks.
     */
    private static final class BackEdgeCounter {
        /*
         * Maintain back edge count in a field so MERGE_EXPLODE can merge states.
         */

        int count;
    }

    public void execute(VirtualFrame frame, WasmInstance instance) {
        executeBodyFromOffset(instance, frame, bytecodeStartOffset, codeEntry.stackBase(), -1, 0);
    }

    private static final class VirtualState {

        @EarlyInline
        VirtualState(int stackPointer) {
            this.stackPointer = stackPointer;
        }

        int stackPointer;
    }

    private static final class State {

        @EarlyInline
        State(WasmFunctionNode<?> thiz, BackEdgeCounter backEdgeCounter, int interpreterBackEdgeCounter, byte[] bytecode, int lineIndex, WasmMemory zeroMemory, WasmMemoryLibrary zeroMemoryLib,
                        int localCount, int maxLegacyCatchDepth, WasmInstance instance, int activeLegacyCatchCount) {
            this.thiz = thiz;
            this.backEdgeCounter = backEdgeCounter;
            this.interpreterBackEdgeCounter = interpreterBackEdgeCounter;
            this.bytecode = bytecode;
            this.lineIndex = lineIndex;
            this.zeroMemory = zeroMemory;
            this.zeroMemoryLib = zeroMemoryLib;
            this.legacyCatchBase = localCount;
            this.stackBase = localCount + maxLegacyCatchDepth;
            this.instance = instance;
            this.module = thiz.module;
            this.language = WasmLanguage.get(thiz);
            this.memoryLibs = thiz.memoryLibs;
            this.notifyFunction = thiz.notifyFunction;
            this.activeLegacyCatchCount = activeLegacyCatchCount;
        }

        final WasmFunctionNode<?> thiz;
        final byte[] bytecode;
        int lineIndex;
        final int legacyCatchBase;
        final int stackBase;
        int interpreterBackEdgeCounter;
        final BackEdgeCounter backEdgeCounter;
        final WasmMemory zeroMemory;
        final WasmMemoryLibrary zeroMemoryLib;
        final WasmInstance instance;
        final WasmModule module;
        final WasmLanguage language;
        final WasmMemoryLibrary[] memoryLibs;
        final WasmNotifyFunction notifyFunction;
        int activeLegacyCatchCount;
    }

    @BytecodeInterpreterSwitch
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    @SuppressWarnings({"UnusedAssignment", "hiding"})
    @EarlyEscapeAnalysis
    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0xFF, arguments = {
                    @Argument(returnValue = true),
                    @Argument(expand = Argument.ExpansionKind.MATERIALIZED, fields = {@Argument.Field(name = "bytecode")}),
                    @Argument(expand = Argument.ExpansionKind.VIRTUAL),
                    @Argument(expand = Argument.ExpansionKind.MATERIALIZED, fields = {@Argument.Field(name = "indexedLocals"), @Argument.Field(name = "indexedPrimitiveLocals")})})
    public Object executeBodyFromOffset(WasmInstance instance, VirtualFrame virtualFrame, int startOffset, int startStackPointer, int startLineIndex, int startActiveLegacyCatchCount) {
        FrameWithoutBoxing frame = (FrameWithoutBoxing) virtualFrame;
        final int localCount = codeEntry.localCount();
        final int maxLegacyCatchDepth = codeEntry.maxLegacyCatchDepth();
        final int stackBase = codeEntry.stackBase();
        final byte[] bytecode = this.bytecode;

        // The back edge count is stored in an object, since else the MERGE_EXPLODE policy would
        // interpret this as a constant value in every loop iteration. This would prevent the
        // compiler form merging branches, since every change to the back edge count would generate
        // a new unique state.
        BackEdgeCounter backEdgeCounter = null;
        if (CompilerDirectives.hasNextTier() && CompilerDirectives.inCompiledCode()) {
            backEdgeCounter = new BackEdgeCounter();
        }

        int offset = startOffset;

        // Note: The module may not have any memories.
        final WasmMemory zeroMemory = !codeEntry.usesMemoryZero() ? null : memory(instance, 0);
        final WasmMemoryLibrary zeroMemoryLib = !codeEntry.usesMemoryZero() ? null : memoryLib(0);

        check(bytecode.length, (1 << 31) - 1);

        final State state = new State(this, backEdgeCounter, 0, bytecode, startLineIndex, zeroMemory, zeroMemoryLib, localCount, maxLegacyCatchDepth, instance, startActiveLegacyCatchCount);
        CompilerDirectives.ensureVirtualized(state);
        final VirtualState virtualState = new VirtualState(startStackPointer);
        CompilerDirectives.ensureVirtualized(virtualState);

        int opcode = Bytecode.UNREACHABLE;
        loop: while (true) {
            try {
                opcode = nextOpcode(offset, state, virtualState, frame);
                CompilerAsserts.partialEvaluationConstant(offset);
                switch (HostCompilerDirectives.markThreadedSwitch(opcode)) {
                    case Bytecode.UNREACHABLE: {
                        offset++;
                        enterErrorBranch(codeEntry);
                        throw WasmException.create(Failure.UNREACHABLE, this);
                    }
                    case Bytecode.NOP: {
                        offset = nopHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.SKIP_LABEL_U8: {
                        offset = skipLabelU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.SKIP_LABEL_U16: {
                        offset = skipLabelU16Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.SKIP_LABEL_I32: {
                        offset = skipLabelI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.RETURN: {
                        offset++;
                        if (offset >= bytecodeEndOffset) {
                            return WasmConstant.RETURN_VALUE;
                        }
                        // A return statement causes the termination of the current function, i.e.
                        // causes the execution to resume after the instruction that invoked
                        // the current frame.
                        if (CompilerDirectives.hasNextTier()) {
                            int backEdgeCount = CompilerDirectives.inCompiledCode() ? backEdgeCounter.count : state.interpreterBackEdgeCounter;
                            if (backEdgeCount > 0) {
                                LoopNode.reportLoopCount(this, backEdgeCount);
                            }
                        }
                        final int resultCount = codeEntry.resultCount();
                        unwindStack(frame, virtualState.stackPointer, stackBase, resultCount);
                        dropStack(frame, virtualState.stackPointer, stackBase + resultCount);
                        return WasmConstant.RETURN_VALUE;
                    }
                    case Bytecode.LABEL_U8: {
                        offset = labelU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.LABEL_U16: {
                        offset = labelU16Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.LABEL_I32: {
                        offset = labelI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.LOOP: {
                        offset = loopHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.IF: {
                        offset = ifHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.BR_U8: {
                        offset = brU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.BR_I32: {
                        offset = brI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.BR_IF_U8: {
                        offset = brIfU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.BR_IF_I32: {
                        offset = brIfI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.BR_TABLE_U8: {
                        offset = brTableU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.BR_TABLE_I32: {
                        offset = brTableI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.CALL_U8: {
                        offset = callU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.CALL_I32: {
                        offset = callI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.CALL_INDIRECT_U8: {
                        offset = callIndirectU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.CALL_INDIRECT_I32: {
                        offset = callIndirectI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.CALL_REF_U8: {
                        offset = callRefU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.CALL_REF_I32: {
                        offset = callRefI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.DROP: {
                        offset = dropHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.DROP_OBJ: {
                        offset = dropObjHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.SELECT: {
                        offset = selectHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.SELECT_OBJ: {
                        offset = selectObjHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.LOCAL_GET_U8: {
                        offset = localGetU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.LOCAL_GET_I32: {
                        offset = localGetI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.LOCAL_GET_OBJ_U8: {
                        offset = localGetObjU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.LOCAL_GET_OBJ_I32: {
                        offset = localGetObjI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.LOCAL_SET_U8: {
                        offset = localSetU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.LOCAL_SET_I32: {
                        offset = localSetI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.LOCAL_SET_OBJ_U8: {
                        offset = localSetObjU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.LOCAL_SET_OBJ_I32: {
                        offset = localSetObjI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.LOCAL_TEE_U8: {
                        offset = localTeeU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.LOCAL_TEE_I32: {
                        offset = localTeeI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.LOCAL_TEE_OBJ_U8: {
                        offset = localTeeObjU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.LOCAL_TEE_OBJ_I32: {
                        offset = localTeeObjI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.GLOBAL_GET_U8: {
                        offset = globalGetU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.GLOBAL_GET_I32: {
                        offset = globalGetI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.GLOBAL_SET_U8: {
                        offset = globalSetU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.GLOBAL_SET_I32: {
                        offset = globalSetI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_LOAD: {
                        offset = i32LoadHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD: {
                        offset = i64LoadHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_LOAD: {
                        offset = f32LoadHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_LOAD: {
                        offset = f64LoadHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_LOAD8_S: {
                        offset = i32Load8SHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_LOAD8_U: {
                        offset = i32Load8UHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_LOAD16_S: {
                        offset = i32Load16SHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_LOAD16_U: {
                        offset = i32Load16UHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD8_S: {
                        offset = i64Load8SHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD8_U: {
                        offset = i64Load8UHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD16_S: {
                        offset = i64Load16SHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD16_U: {
                        offset = i64Load16UHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD32_S: {
                        offset = i64Load32SHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD32_U: {
                        offset = i64Load32UHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_LOAD_U8: {
                        offset = i32LoadU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_LOAD_I32: {
                        offset = i32LoadI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD_U8: {
                        offset = i64LoadU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_LOAD_U8: {
                        offset = f32LoadU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_LOAD_U8: {
                        offset = f64LoadU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_LOAD8_S_U8: {
                        offset = i32Load8SU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_LOAD8_U_U8: {
                        offset = i32Load8UU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_LOAD16_S_U8: {
                        offset = i32Load16SU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_LOAD16_U_U8: {
                        offset = i32Load16UU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD8_S_U8: {
                        offset = i64Load8SU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD8_U_U8: {
                        offset = i64Load8UU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD16_S_U8: {
                        offset = i64Load16SU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD16_U_U8: {
                        offset = i64Load16UU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD32_S_U8: {
                        offset = i64Load32SU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD32_U_U8: {
                        offset = i64Load32UU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD_I32: {
                        offset = i64LoadI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_LOAD_I32: {
                        offset = f32LoadI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_LOAD_I32: {
                        offset = f64LoadI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_LOAD8_S_I32: {
                        offset = i32Load8SI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_LOAD8_U_I32: {
                        offset = i32Load8UI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_LOAD16_S_I32: {
                        offset = i32Load16SI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_LOAD16_U_I32: {
                        offset = i32Load16UI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD8_S_I32: {
                        offset = i64Load8SI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD8_U_I32: {
                        offset = i64Load8UI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD16_S_I32: {
                        offset = i64Load16SI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD16_U_I32: {
                        offset = i64Load16UI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD32_S_I32: {
                        offset = i64Load32SI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LOAD32_U_I32: {
                        offset = i64Load32UI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_STORE: {
                        offset = i32StoreHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_STORE: {
                        offset = i64StoreHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_STORE: {
                        offset = f32StoreHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_STORE: {
                        offset = f64StoreHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_STORE_8: {
                        offset = i32Store8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_STORE_16: {
                        offset = i32Store16Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_STORE_8: {
                        offset = i64Store8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_STORE_16: {
                        offset = i64Store16Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_STORE_32: {
                        offset = i64Store32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_STORE_U8: {
                        offset = i32StoreU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_STORE_I32: {
                        offset = i32StoreI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_STORE_U8: {
                        offset = i64StoreU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_STORE_U8: {
                        offset = f32StoreU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_STORE_U8: {
                        offset = f64StoreU8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_STORE_8_U8: {
                        offset = i32Store8U8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_STORE_16_U8: {
                        offset = i32Store16U8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_STORE_8_U8: {
                        offset = i64Store8U8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_STORE_16_U8: {
                        offset = i64Store16U8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_STORE_32_U8: {
                        offset = i64Store32U8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_STORE_I32: {
                        offset = i64StoreI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_STORE_I32: {
                        offset = f32StoreI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_STORE_I32: {
                        offset = f64StoreI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_STORE_8_I32: {
                        offset = i32Store8I32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_STORE_16_I32: {
                        offset = i32Store16I32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_STORE_8_I32: {
                        offset = i64Store8I32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_STORE_16_I32: {
                        offset = i64Store16I32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_STORE_32_I32: {
                        offset = i64Store32I32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.MEMORY_SIZE: {
                        offset = memorySizeHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.MEMORY_GROW: {
                        offset = memoryGrowHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_CONST_I8: {
                        offset = i32ConstI8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_CONST_I32: {
                        offset = i32ConstI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_CONST_I8: {
                        offset = i64ConstI8Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_CONST_I64: {
                        offset = i64ConstI64Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_EQZ: {
                        offset = i32EqzHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_EQ: {
                        offset = i32EqHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_NE: {
                        offset = i32NeHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_LT_S: {
                        offset = i32LtSHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_LT_U: {
                        offset = i32LtUHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_GT_S: {
                        offset = i32GtSHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_GT_U: {
                        offset = i32GtUHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_LE_S: {
                        offset = i32LeSHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_LE_U: {
                        offset = i32LeUHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_GE_S: {
                        offset = i32GeSHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_GE_U: {
                        offset = i32GeUHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_EQZ: {
                        offset = i64EqzHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_EQ: {
                        offset = i64EqHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_NE: {
                        offset = i64NeHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LT_S: {
                        offset = i64LtSHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LT_U: {
                        offset = i64LtUHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_GT_S: {
                        offset = i64GtSHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_GT_U: {
                        offset = i64GtUHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LE_S: {
                        offset = i64LeSHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_LE_U: {
                        offset = i64LeUHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_GE_S: {
                        offset = i64GeSHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_GE_U: {
                        offset = i64GeUHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_EQ: {
                        offset = f32EqHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_NE: {
                        offset = f32NeHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_LT: {
                        offset = f32LtHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_GT: {
                        offset = f32GtHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_LE: {
                        offset = f32LeHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_GE: {
                        offset = f32GeHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_EQ: {
                        offset = f64EqHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_NE: {
                        offset = f64NeHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_LT: {
                        offset = f64LtHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_GT: {
                        offset = f64GtHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_LE: {
                        offset = f64LeHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_GE: {
                        offset = f64GeHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_CLZ: {
                        offset = i32ClzHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_CTZ: {
                        offset = i32CtzHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_POPCNT: {
                        offset = i32PopcntHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_ADD: {
                        offset = i32AddHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_SUB: {
                        offset = i32SubHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_MUL: {
                        offset = i32MulHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_DIV_S: {
                        offset = i32DivSHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_DIV_U: {
                        offset = i32DivUHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_REM_S: {
                        offset = i32RemSHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_REM_U: {
                        offset = i32RemUHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_AND: {
                        offset = i32AndHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_OR: {
                        offset = i32OrHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_XOR: {
                        offset = i32XorHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_SHL: {
                        offset = i32ShlHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_SHR_S: {
                        offset = i32ShrSHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_SHR_U: {
                        offset = i32ShrUHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_ROTL: {
                        offset = i32RotlHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_ROTR: {
                        offset = i32RotrHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_CLZ: {
                        offset = i64ClzHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_CTZ: {
                        offset = i64CtzHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_POPCNT: {
                        offset = i64PopcntHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_ADD: {
                        offset = i64AddHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_SUB: {
                        offset = i64SubHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_MUL: {
                        offset = i64MulHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_DIV_S: {
                        offset = i64DivSHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_DIV_U: {
                        offset = i64DivUHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_REM_S: {
                        offset = i64RemSHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_REM_U: {
                        offset = i64RemUHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_AND: {
                        offset = i64AndHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_OR: {
                        offset = i64OrHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_XOR: {
                        offset = i64XorHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_SHL: {
                        offset = i64ShlHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_SHR_S: {
                        offset = i64ShrSHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_SHR_U: {
                        offset = i64ShrUHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_ROTL: {
                        offset = i64RotlHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_ROTR: {
                        offset = i64RotrHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_CONST: {
                        offset = f32ConstHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_ABS: {
                        offset = f32AbsHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_NEG: {
                        offset = f32NegHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_CEIL: {
                        offset = f32CeilHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_FLOOR: {
                        offset = f32FloorHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_TRUNC: {
                        offset = f32TruncHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_NEAREST: {
                        offset = f32NearestHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_SQRT: {
                        offset = f32SqrtHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_ADD: {
                        offset = f32AddHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_SUB: {
                        offset = f32SubHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_MUL: {
                        offset = f32MulHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_DIV: {
                        offset = f32DivHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_MIN: {
                        offset = f32MinHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_MAX: {
                        offset = f32MaxHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_COPYSIGN: {
                        offset = f32CopysignHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_CONST: {
                        offset = f64ConstHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_ABS: {
                        offset = f64AbsHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_NEG: {
                        offset = f64NegHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_CEIL: {
                        offset = f64CeilHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_FLOOR: {
                        offset = f64FloorHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_TRUNC: {
                        offset = f64TruncHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_NEAREST: {
                        offset = f64NearestHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_SQRT: {
                        offset = f64SqrtHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_ADD: {
                        offset = f64AddHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_SUB: {
                        offset = f64SubHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_MUL: {
                        offset = f64MulHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_DIV: {
                        offset = f64DivHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_MIN: {
                        offset = f64MinHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_MAX: {
                        offset = f64MaxHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_COPYSIGN: {
                        offset = f64CopysignHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_WRAP_I64: {
                        offset = i32WrapI64Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_TRUNC_F32_S: {
                        offset = i32TruncF32SHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_TRUNC_F32_U: {
                        offset = i32TruncF32UHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_TRUNC_F64_S: {
                        offset = i32TruncF64SHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_TRUNC_F64_U: {
                        offset = i32TruncF64UHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_EXTEND_I32_S: {
                        offset = i64ExtendI32SHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_EXTEND_I32_U: {
                        offset = i64ExtendI32UHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_TRUNC_F32_S: {
                        offset = i64TruncF32SHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_TRUNC_F32_U: {
                        offset = i64TruncF32UHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_TRUNC_F64_S: {
                        offset = i64TruncF64SHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_TRUNC_F64_U: {
                        offset = i64TruncF64UHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_CONVERT_I32_S: {
                        offset = f32ConvertI32SHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_CONVERT_I32_U: {
                        offset = f32ConvertI32UHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_CONVERT_I64_S: {
                        offset = f32ConvertI64SHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_CONVERT_I64_U: {
                        offset = f32ConvertI64UHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_DEMOTE_F64: {
                        offset = f32DemoteF64Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_CONVERT_I32_S: {
                        offset = f64ConvertI32SHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_CONVERT_I32_U: {
                        offset = f64ConvertI32UHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_CONVERT_I64_S: {
                        offset = f64ConvertI64SHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_CONVERT_I64_U: {
                        offset = f64ConvertI64UHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_PROMOTE_F32: {
                        offset = f64PromoteF32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_REINTERPRET_F32: {
                        offset = i32ReinterpretF32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_REINTERPRET_F64: {
                        offset = i64ReinterpretF64Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F32_REINTERPRET_I32: {
                        offset = f32ReinterpretI32Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.F64_REINTERPRET_I64: {
                        offset = f64ReinterpretI64Handler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_EXTEND8_S: {
                        offset = i32Extend8SHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I32_EXTEND16_S: {
                        offset = i32Extend16SHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_EXTEND8_S: {
                        offset = i64Extend8SHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_EXTEND16_S: {
                        offset = i64Extend16SHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.I64_EXTEND32_S: {
                        offset = i64Extend32SHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.REF_NULL: {
                        offset = refNullHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.REF_IS_NULL: {
                        offset = refIsNullHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.REF_FUNC: {
                        offset = refFuncHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.AGGREGATE: {
                        offset = aggregateHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.MISC: {
                        offset = miscHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.ATOMIC: {
                        offset = atomicHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.VECTOR: {
                        offset = vectorHandler(offset, state, virtualState, frame);
                        break;
                    }
                    case Bytecode.NOTIFY: {
                        offset = notifyHandler(offset, state, virtualState, frame);
                        break;
                    }
                    default:
                        throw CompilerDirectives.shouldNotReachHere();
                }
            } catch (WasmOSRException osrExc) {
                return osrExc.osrResult;
            } catch (WasmRuntimeException e) {
                codeEntry.exceptionBranch();
                CompilerAsserts.partialEvaluationConstant(virtualState.stackPointer);
                CompilerAsserts.partialEvaluationConstant(offset);

                int exceptionTableOffset = this.exceptionTableOffset;
                int handlerLookupOffset = offset;

                if (exceptionTableOffset == BytecodeBitEncoding.INVALID_EXCEPTION_TABLE_OFFSET) {
                    // no exception table, directly throw to the next function on the call stack
                    throw e;
                }

                /*
                 * The exception table is encoded as:
                 *
                 * from | to | type | tag index | target
                 *
                 * The values from (inclusive) and to (exclusive) define a range. If the current
                 * bytecode offset is inside this range, the entry defines a possible exception
                 * handler for the exception. If the type of the exception handler is catch or
                 * catch_ref, we check whether the expected tag defined by the tag index and the tag
                 * of the exception object match. For catch_all and catch_all_ref we don't have this
                 * check.
                 *
                 * The catch and catch_ref types push the fields of the exception onto the stack,
                 * catch_ref also pushes a reference to the exception itself, while catch_all_ref
                 * only pushes the reference, and catch_all doesn't push anything onto the stack.
                 * Legacy catches additionally keep the caught exception active for rethrow inside
                 * the catch body, while delegate continues lookup from the delegated label.
                 */
                while (true) {
                    final int handlerOffset = exceptionTableOffset;
                    final int from = rawPeekI32(bytecode, handlerOffset + ExceptionHandler.FROM_OFFSET);
                    if (from == -1) {
                        // we reached the end of the table
                        break;
                    }
                    final int to = exceptionHandlerTo(handlerOffset);
                    exceptionTableOffset = nextExceptionHandlerOffset(handlerOffset);

                    if (handlerLookupOffset < from || handlerLookupOffset >= to) {
                        continue;
                    }

                    final int catchType = exceptionHandlerType(handlerOffset);
                    final int tagIndex;
                    if (catchType == ExceptionHandlerType.CATCH || catchType == ExceptionHandlerType.CATCH_REF || catchType == ExceptionHandlerType.LEGACY_CATCH) {
                        tagIndex = exceptionHandlerTagIndex(handlerOffset);
                        if (e.tag() != instance.tag(tagIndex)) {
                            continue;
                        }
                    } else {
                        tagIndex = -1;
                        assert catchType == ExceptionHandlerType.CATCH_ALL || catchType == ExceptionHandlerType.CATCH_ALL_REF ||
                                        catchType == ExceptionHandlerType.LEGACY_CATCH_ALL || catchType == ExceptionHandlerType.LEGACY_DELEGATE : catchType;
                    }

                    final int target = exceptionHandlerTarget(handlerOffset);
                    if (catchType == ExceptionHandlerType.LEGACY_DELEGATE) {
                        // Legacy delegate targets a continuation in the exception table, not a
                        // bytecode offset. This skips intervening handlers that are not visible
                        // from the delegated label, even if they protect the same bytecode range.
                        exceptionTableOffset = target;
                        continue;
                    }

                    if (isLegacyCatchType(catchType)) {
                        // Legacy catch labels encode the final number of active legacy catches
                        // that must remain inside the catch body. Unwind first so entering an
                        // enclosing catch from a nested catch does not transiently exceed the
                        // reserved frame slots before the target label's helper runs.
                        final int targetDepth = legacyCatchTargetDepth(target);
                        final int catchEntryDepth = targetDepth - 1;
                        state.activeLegacyCatchCount = unwindLegacyCatchesToDepth(frame, state.legacyCatchBase, state.stackBase, state.activeLegacyCatchCount, catchEntryDepth);
                        // Legacy catch bodies keep the caught exception in the reserved frame area
                        // so rethrow and scope-exit cleanup can find it without using the operand
                        // stack layout.
                        final int exceptionSlot = state.legacyCatchBase + state.activeLegacyCatchCount;
                        assert exceptionSlot >= state.legacyCatchBase && exceptionSlot < state.stackBase : "Exceeded reserved legacy catch slots";
                        CompilerDirectives.isPartialEvaluationConstant(exceptionSlot);
                        pushReference(frame, exceptionSlot, e);
                        state.activeLegacyCatchCount++;
                    }
                    virtualState.stackPointer = pushExceptionFieldsAndReference(frame, e, virtualState.stackPointer, catchType, tagIndex);
                    offset = target;
                    continue loop;
                }
                throw e;
            }
        }
    }

    @SuppressWarnings("serial")
    private static class WasmOSRException extends ControlFlowException {

        @Serial private static final long serialVersionUID = 171629579745510728L;

        private final Object osrResult;

        WasmOSRException(Object osrResult) {
            this.osrResult = osrResult;
        }
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.LOOP}, safepoint = true)
    private static int loopHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        if (CompilerDirectives.hasNextTier()) {
            int count = CompilerDirectives.inCompiledCode() ? ++state.backEdgeCounter.count : ++state.interpreterBackEdgeCounter;
            if (CompilerDirectives.injectBranchProbability(0.001, count >= REPORT_LOOP_STRIDE)) {
                TruffleSafepoint.poll(state.thiz);
                LoopNode.reportLoopCount(state.thiz, REPORT_LOOP_STRIDE);
                if (CompilerDirectives.inInterpreter() && BytecodeOSRNode.pollOSRBackEdge(state.thiz, REPORT_LOOP_STRIDE)) {
                    OSRInterpreterState osrInterpreterState = new OSRInterpreterState(state.lineIndex, state.activeLegacyCatchCount);
                    Object result = BytecodeOSRNode.tryOSR(state.thiz, offset + 1 | ((long) virtualState.stackPointer << 32), osrInterpreterState, null, frame);
                    if (result != null) {
                        throw new WasmOSRException(result);
                    }
                }
                if (CompilerDirectives.inInterpreter()) {
                    state.interpreterBackEdgeCounter = 0;
                } else {
                    state.backEdgeCounter.count = 0;
                }
            }
        }
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.IF}, safepoint = false)
    private static int ifHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        virtualState.stackPointer--;
        if (profileCondition(state.bytecode, offset + 5, popBoolean(frame, virtualState.stackPointer))) {
            return offset + 7;
        }
        final int offsetDelta = rawPeekI32(state.bytecode, offset + 1);
        return offset + 1 + offsetDelta;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.BR_U8}, safepoint = false)
    private static int brU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int offsetDelta = rawPeekU8(state.bytecode, offset + 1);
        /*
         * BR_U8 encodes the back jump value as a positive byte value. BR_U8 can never perform a
         * forward jump.
         */
        return offset + 1 - offsetDelta;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.BR_I32}, safepoint = false)
    private static int brI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int offsetDelta = rawPeekI32(state.bytecode, offset + 1);
        return offset + 1 + offsetDelta;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.BR_IF_U8}, safepoint = false)
    private static int brIfU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        virtualState.stackPointer--;
        if (profileCondition(state.bytecode, offset + 2, popBoolean(frame, virtualState.stackPointer))) {
            final int offsetDelta = rawPeekU8(state.bytecode, offset + 1);
            /*
             * BR_IF_U8 encodes the back jump value as a positive byte value. BR_IF_U8 can never
             * perform a forward jump.
             */
            return offset + 1 - offsetDelta;
        }
        return offset + 4;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.BR_IF_I32}, safepoint = false)
    private static int brIfI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        virtualState.stackPointer--;
        if (profileCondition(state.bytecode, offset + 5, popBoolean(frame, virtualState.stackPointer))) {
            final int offsetDelta = rawPeekI32(state.bytecode, offset + 1);
            return offset + 1 + offsetDelta;
        }
        return offset + 7;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.BR_TABLE_U8}, safepoint = false)
    private static int brTableU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        virtualState.stackPointer--;
        int index = popInt(frame, virtualState.stackPointer);
        final int size = rawPeekU8(state.bytecode, offset + 1);
        final int counterOffset = offset + 2;
        if (HostCompilerDirectives.inInterpreterFastPath()) {
            if (index < 0 || index >= size) {
                // If unsigned index is larger or equal to the table size use the
                // default (last) index.
                index = size - 1;
            }
            final int indexOffset = offset + 4 + index * 6;
            updateBranchTableProfile(state.bytecode, counterOffset, indexOffset + 4);
            final int offsetDelta = rawPeekI32(state.bytecode, indexOffset);
            return indexOffset + offsetDelta;
        } else {
            /*
             * This loop is implemented to create a separate path for every index. This guarantees
             * that all values inside the if statement are treated as compile time constants, since
             * the loop is unrolled.
             *
             * We keep track of the sum of the preceding profiles to adjust the independent
             * probabilities to conditional ones. This gets explained in profileBranchTable().
             */
            int precedingSum = 0;
            for (int i = 0; i < size; i++) {
                final int indexOffset = offset + 4 + i * 6;
                int profile = rawPeekU16(state.bytecode, indexOffset + 4);
                if (profileBranchTable(state.bytecode, counterOffset, profile, precedingSum, i == index || i == size - 1)) {
                    final int offsetDelta = rawPeekI32(state.bytecode, indexOffset);
                    return indexOffset + offsetDelta;
                }
                precedingSum += profile;
            }
            throw CompilerDirectives.shouldNotReachHere("br_table");
        }
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.BR_TABLE_I32}, safepoint = false)
    private static int brTableI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        virtualState.stackPointer--;
        int index = popInt(frame, virtualState.stackPointer);
        final int size = rawPeekI32(state.bytecode, offset + 1);
        final int counterOffset = offset + 5;
        if (HostCompilerDirectives.inInterpreterFastPath()) {
            if (index < 0 || index >= size) {
                // If unsigned index is larger or equal to the table size use the
                // default (last) index.
                index = size - 1;
            }
            final int indexOffset = offset + 7 + index * 6;
            updateBranchTableProfile(state.bytecode, counterOffset, indexOffset + 4);
            final int offsetDelta = rawPeekI32(state.bytecode, indexOffset);
            return indexOffset + offsetDelta;
        } else {
            /*
             * This loop is implemented to create a separate path for every index. This guarantees
             * that all values inside the if statement are treated as compile time constants, since
             * the loop is unrolled.
             */
            int precedingSum = 0;
            for (int i = 0; i < size; i++) {
                final int indexOffset = offset + 7 + i * 6;
                int profile = rawPeekU16(state.bytecode, indexOffset + 4);
                if (profileBranchTable(state.bytecode, counterOffset, profile, precedingSum, i == index || i == size - 1)) {
                    final int offsetDelta = rawPeekI32(state.bytecode, indexOffset);
                    return indexOffset + offsetDelta;
                }
                precedingSum += profile;
            }
            throw CompilerDirectives.shouldNotReachHere("br_table");
        }
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.CALL_U8}, safepoint = false)
    private static int callU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int callNodeIndex = rawPeekU8(state.bytecode, offset + 1);
        final int functionIndex = rawPeekU8(state.bytecode, offset + 2);
        WasmFunction function = state.module.symbolTable().function(functionIndex);
        int paramCount = function.paramCount();
        Object[] args = state.thiz.createArgumentsForCall(frame, function.typeIndex(), paramCount, virtualState.stackPointer);
        virtualState.stackPointer -= paramCount;
        virtualState.stackPointer = state.thiz.executeDirectCall(frame, virtualState.stackPointer, state.instance, callNodeIndex, function, args);
        CompilerAsserts.partialEvaluationConstant(virtualState.stackPointer);
        return offset + 3;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.CALL_I32}, safepoint = false)
    private static int callI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int callNodeIndex = rawPeekI32(state.bytecode, offset + 1);
        final int functionIndex = rawPeekI32(state.bytecode, offset + 5);
        WasmFunction function = state.module.symbolTable().function(functionIndex);
        int paramCount = function.paramCount();
        Object[] args = state.thiz.createArgumentsForCall(frame, function.typeIndex(), paramCount, virtualState.stackPointer);
        virtualState.stackPointer -= paramCount;
        virtualState.stackPointer = state.thiz.executeDirectCall(frame, virtualState.stackPointer, state.instance, callNodeIndex, function, args);
        CompilerAsserts.partialEvaluationConstant(virtualState.stackPointer);
        return offset + 9;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.CALL_INDIRECT_U8}, safepoint = false)
    private static int callIndirectU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int callNodeIndex = rawPeekU8(state.bytecode, offset + 1);
        final int expectedFunctionTypeIndex = rawPeekU8(state.bytecode, offset + 2);
        final int tableIndex = rawPeekU8(state.bytecode, offset + 3);
        final WasmTable table = state.instance.table(tableIndex);
        final Object[] elements = table.elements();
        final long tableElementIndex = popTableIndex(frame, --virtualState.stackPointer, table);
        if (checkOutOfBounds(tableElementIndex, elements.length)) {
            enterErrorBranch(state.thiz.codeEntry);
            throw WasmException.format(Failure.UNDEFINED_ELEMENT, state.thiz, "Element index '%d' out of table bounds.", tableElementIndex);
        }
        final int elementIndex = (int) tableElementIndex;
        // Currently, table elements may only be functions.
        // We can add a check here when this changes in the future.
        final Object functionCandidate = elements[elementIndex];
        if (!(functionCandidate instanceof WasmFunctionInstance functionInstance)) {
            throw state.thiz.callIndirectNotAFunctionError(functionCandidate, elementIndex);
        }
        final WasmFunction function = functionInstance.function();
        final CallTarget target = functionInstance.target();
        final WasmContext functionInstanceContext = functionInstance.context();
        // Target function instance must be from the same context.
        assert functionInstanceContext == WasmContext.get(state.thiz);
        // Validate that the target function type matches the expected type of
        // the indirect call.
        if (!state.thiz.runTimeConcreteTypeCheck(expectedFunctionTypeIndex, functionInstance)) {
            enterErrorBranch(state.thiz.codeEntry);
            state.thiz.failFunctionTypeCheck(function, expectedFunctionTypeIndex);
        }
        // Invoke the resolved function.
        int paramCount = state.module.symbolTable().functionTypeParamCount(expectedFunctionTypeIndex);
        Object[] args = state.thiz.createArgumentsForCall(frame, expectedFunctionTypeIndex, paramCount, virtualState.stackPointer);
        virtualState.stackPointer -= paramCount;
        WasmArguments.setModuleInstance(args, functionInstance.moduleInstance());
        final Object result = state.thiz.executeIndirectCallNode(callNodeIndex, target, args);
        virtualState.stackPointer = state.thiz.pushIndirectCallResult(frame, virtualState.stackPointer, expectedFunctionTypeIndex, result, state.language);
        CompilerAsserts.partialEvaluationConstant(virtualState.stackPointer);
        return offset + 4;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.CALL_INDIRECT_I32}, safepoint = false)
    private static int callIndirectI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int callNodeIndex = rawPeekI32(state.bytecode, offset + 1);
        final int expectedFunctionTypeIndex = rawPeekI32(state.bytecode, offset + 5);
        final int tableIndex = rawPeekI32(state.bytecode, offset + 9);
        final WasmTable table = state.instance.table(tableIndex);
        final Object[] elements = table.elements();
        final long tableElementIndex = popTableIndex(frame, --virtualState.stackPointer, table);
        if (checkOutOfBounds(tableElementIndex, elements.length)) {
            enterErrorBranch(state.thiz.codeEntry);
            throw WasmException.format(Failure.UNDEFINED_ELEMENT, state.thiz, "Element index '%d' out of table bounds.", tableElementIndex);
        }
        final int elementIndex = (int) tableElementIndex;
        // Currently, table elements may only be functions.
        // We can add a check here when this changes in the future.
        final Object functionCandidate = elements[elementIndex];
        if (!(functionCandidate instanceof WasmFunctionInstance functionInstance)) {
            throw state.thiz.callIndirectNotAFunctionError(functionCandidate, elementIndex);
        }
        final WasmFunction function = functionInstance.function();
        final CallTarget target = functionInstance.target();
        final WasmContext functionInstanceContext = functionInstance.context();
        // Target function instance must be from the same context.
        assert functionInstanceContext == WasmContext.get(state.thiz);
        // Validate that the target function type matches the expected type of
        // the indirect call.
        if (!state.thiz.runTimeConcreteTypeCheck(expectedFunctionTypeIndex, functionInstance)) {
            enterErrorBranch(state.thiz.codeEntry);
            state.thiz.failFunctionTypeCheck(function, expectedFunctionTypeIndex);
        }
        // Invoke the resolved function.
        int paramCount = state.module.symbolTable().functionTypeParamCount(expectedFunctionTypeIndex);
        Object[] args = state.thiz.createArgumentsForCall(frame, expectedFunctionTypeIndex, paramCount, virtualState.stackPointer);
        virtualState.stackPointer -= paramCount;
        WasmArguments.setModuleInstance(args, functionInstance.moduleInstance());
        final Object result = state.thiz.executeIndirectCallNode(callNodeIndex, target, args);
        virtualState.stackPointer = state.thiz.pushIndirectCallResult(frame, virtualState.stackPointer, expectedFunctionTypeIndex, result, state.language);
        CompilerAsserts.partialEvaluationConstant(virtualState.stackPointer);
        return offset + 13;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.CALL_REF_U8}, safepoint = false)
    private static int callRefU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int callNodeIndex = rawPeekU8(state.bytecode, offset + 1);
        final int expectedFunctionTypeIndex = rawPeekU8(state.bytecode, offset + 2);
        final Object functionCandidate = popReference(frame, --virtualState.stackPointer);
        if (!(functionCandidate instanceof WasmFunctionInstance functionInstance)) {
            throw state.thiz.callRefNotAFunctionError(functionCandidate);
        }
        final CallTarget target = functionInstance.target();
        final WasmContext functionInstanceContext = functionInstance.context();
        // Target function instance must be from the same context.
        assert functionInstanceContext == WasmContext.get(state.thiz);
        // Invoke the resolved function.
        int paramCount = state.module.symbolTable().functionTypeParamCount(expectedFunctionTypeIndex);
        Object[] args = state.thiz.createArgumentsForCall(frame, expectedFunctionTypeIndex, paramCount, virtualState.stackPointer);
        virtualState.stackPointer -= paramCount;
        WasmArguments.setModuleInstance(args, functionInstance.moduleInstance());
        final Object result = state.thiz.executeIndirectCallNode(callNodeIndex, target, args);
        virtualState.stackPointer = state.thiz.pushIndirectCallResult(frame, virtualState.stackPointer, expectedFunctionTypeIndex, result, state.language);
        CompilerAsserts.partialEvaluationConstant(virtualState.stackPointer);
        return offset + 3;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.CALL_REF_I32}, safepoint = false)
    private static int callRefI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int callNodeIndex = rawPeekI32(state.bytecode, offset + 1);
        final int expectedFunctionTypeIndex = rawPeekI32(state.bytecode, offset + 5);
        final Object functionCandidate = popReference(frame, --virtualState.stackPointer);
        if (!(functionCandidate instanceof WasmFunctionInstance functionInstance)) {
            throw state.thiz.callRefNotAFunctionError(functionCandidate);
        }
        final CallTarget target = functionInstance.target();
        final WasmContext functionInstanceContext = functionInstance.context();
        // Target function instance must be from the same context.
        assert functionInstanceContext == WasmContext.get(state.thiz);
        // Invoke the resolved function.
        int paramCount = state.module.symbolTable().functionTypeParamCount(expectedFunctionTypeIndex);
        Object[] args = state.thiz.createArgumentsForCall(frame, expectedFunctionTypeIndex, paramCount, virtualState.stackPointer);
        virtualState.stackPointer -= paramCount;
        WasmArguments.setModuleInstance(args, functionInstance.moduleInstance());
        final Object result = state.thiz.executeIndirectCallNode(callNodeIndex, target, args);
        virtualState.stackPointer = state.thiz.pushIndirectCallResult(frame, virtualState.stackPointer, expectedFunctionTypeIndex, result, state.language);
        CompilerAsserts.partialEvaluationConstant(virtualState.stackPointer);
        return offset + 9;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.LABEL_I32}, safepoint = false)
    private static int labelI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int resultType = rawPeekU8(state.bytecode, offset + 1);
        final int resultCount = rawPeekI32(state.bytecode, offset + 2);
        final int stackSize = rawPeekI32(state.bytecode, offset + 6);
        final int targetStackPointer = stackSize + state.stackBase;
        switch (resultType) {
            case BytecodeBitEncoding.LABEL_RESULT_TYPE_NUM:
                unwindPrimitiveStack(frame, virtualState.stackPointer, targetStackPointer, resultCount);
                break;
            case BytecodeBitEncoding.LABEL_RESULT_TYPE_OBJ:
                unwindObjectStack(frame, virtualState.stackPointer, targetStackPointer, resultCount);
                break;
            case BytecodeBitEncoding.LABEL_RESULT_TYPE_MIX:
                unwindStack(frame, virtualState.stackPointer, targetStackPointer, resultCount);
                break;
        }
        dropStack(frame, virtualState.stackPointer, targetStackPointer + resultCount);
        virtualState.stackPointer = targetStackPointer + resultCount;
        return offset + 10;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.LABEL_U16}, safepoint = false)
    private static int labelU16Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int value = rawPeekU8(state.bytecode, offset + 1);
        final int stackSize = rawPeekU8(state.bytecode, offset + 2);
        final int resultCount = (value & BytecodeBitEncoding.LABEL_U16_RESULT_VALUE);
        final int resultType = (value & BytecodeBitEncoding.LABEL_U16_RESULT_TYPE_MASK);
        final int targetStackPointer = stackSize + state.stackBase;
        switch (resultType) {
            case BytecodeBitEncoding.LABEL_U16_RESULT_TYPE_NUM:
                unwindPrimitiveStack(frame, virtualState.stackPointer, targetStackPointer, resultCount);
                break;
            case BytecodeBitEncoding.LABEL_U16_RESULT_TYPE_OBJ:
                unwindObjectStack(frame, virtualState.stackPointer, targetStackPointer, resultCount);
                break;
            case BytecodeBitEncoding.LABEL_U16_RESULT_TYPE_MIX:
                unwindStack(frame, virtualState.stackPointer, targetStackPointer, resultCount);
                break;
        }
        dropStack(frame, virtualState.stackPointer, targetStackPointer + resultCount);
        virtualState.stackPointer = targetStackPointer + resultCount;
        return offset + 3;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.LABEL_U8}, safepoint = false)
    private static int labelU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int value = rawPeekU8(state.bytecode, offset + 1);
        final int stackSize = (value & BytecodeBitEncoding.LABEL_U8_STACK_VALUE);
        final int targetStackPointer = stackSize + state.stackBase;
        switch ((value & BytecodeBitEncoding.LABEL_U8_RESULT_MASK)) {
            case BytecodeBitEncoding.LABEL_U8_RESULT_NUM:
                WasmFrame.copyPrimitive(frame, virtualState.stackPointer - 1, targetStackPointer);
                dropStack(frame, virtualState.stackPointer, targetStackPointer + 1);
                virtualState.stackPointer = targetStackPointer + 1;
                break;
            case BytecodeBitEncoding.LABEL_U8_RESULT_OBJ:
                WasmFrame.copyObject(frame, virtualState.stackPointer - 1, targetStackPointer);
                dropStack(frame, virtualState.stackPointer, targetStackPointer + 1);
                virtualState.stackPointer = targetStackPointer + 1;
                break;
            default:
                dropStack(frame, virtualState.stackPointer, targetStackPointer);
                virtualState.stackPointer = targetStackPointer;
                break;
        }
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("all")
    @BytecodeInterpreterFetchOpcode
    private static int nextOpcode(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        assert offset < state.thiz.bytecodeEndOffset;
        return rawPeekU8(state.bytecode, offset);
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_CONST_I8}, safepoint = false)
    private static int i32ConstI8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int value = rawPeekI8(state.bytecode, offset + 1);
        pushInt(frame, virtualState.stackPointer, value);
        virtualState.stackPointer++;
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_CONST_I32}, safepoint = false)
    private static int i32ConstI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int value = rawPeekI32(state.bytecode, offset + 1);
        pushInt(frame, virtualState.stackPointer, value);
        virtualState.stackPointer++;
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.LOCAL_GET_I32}, safepoint = false)
    private static int localGetI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int index = rawPeekI32(state.bytecode, offset + 1);
        local_get(frame, virtualState.stackPointer, index);
        virtualState.stackPointer++;
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.LOCAL_GET_U8}, safepoint = false)
    private static int localGetU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int index = rawPeekU8(state.bytecode, offset + 1);
        local_get(frame, virtualState.stackPointer, index);
        virtualState.stackPointer++;
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.SKIP_LABEL_U8}, safepoint = false)
    private static int skipLabelU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        return offset + 1 + Bytecode.SKIP_LABEL_U8;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.SKIP_LABEL_U16}, safepoint = false)
    private static int skipLabelU16Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        return offset + 1 + Bytecode.SKIP_LABEL_U16;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.SKIP_LABEL_I32}, safepoint = false)
    private static int skipLabelI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        return offset + 1 + Bytecode.SKIP_LABEL_I32;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.NOP}, safepoint = false)
    private static int nopHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_EQZ}, safepoint = false)
    private static int i32EqzHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_eqz(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_EQ}, safepoint = false)
    private static int i32EqHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_eq(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_NE}, safepoint = false)
    private static int i32NeHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_ne(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_LT_S}, safepoint = false)
    private static int i32LtSHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_lt_s(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_LT_U}, safepoint = false)
    private static int i32LtUHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_lt_u(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_GT_S}, safepoint = false)
    private static int i32GtSHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_gt_s(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_GT_U}, safepoint = false)
    private static int i32GtUHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_gt_u(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_LE_S}, safepoint = false)
    private static int i32LeSHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_le_s(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_LE_U}, safepoint = false)
    private static int i32LeUHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_le_u(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_GE_S}, safepoint = false)
    private static int i32GeSHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_ge_s(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_GE_U}, safepoint = false)
    private static int i32GeUHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_ge_u(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_EQZ}, safepoint = false)
    private static int i64EqzHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_eqz(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_EQ}, safepoint = false)
    private static int i64EqHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_eq(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_NE}, safepoint = false)
    private static int i64NeHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_ne(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LT_S}, safepoint = false)
    private static int i64LtSHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_lt_s(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LT_U}, safepoint = false)
    private static int i64LtUHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_lt_u(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_GT_S}, safepoint = false)
    private static int i64GtSHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_gt_s(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_GT_U}, safepoint = false)
    private static int i64GtUHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_gt_u(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LE_S}, safepoint = false)
    private static int i64LeSHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_le_s(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LE_U}, safepoint = false)
    private static int i64LeUHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_le_u(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_GE_S}, safepoint = false)
    private static int i64GeSHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_ge_s(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_GE_U}, safepoint = false)
    private static int i64GeUHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_ge_u(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_EQ}, safepoint = false)
    private static int f32EqHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_eq(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_NE}, safepoint = false)
    private static int f32NeHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_ne(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_LT}, safepoint = false)
    private static int f32LtHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_lt(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_GT}, safepoint = false)
    private static int f32GtHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_gt(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_LE}, safepoint = false)
    private static int f32LeHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_le(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_GE}, safepoint = false)
    private static int f32GeHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_ge(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_EQ}, safepoint = false)
    private static int f64EqHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_eq(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_NE}, safepoint = false)
    private static int f64NeHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_ne(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_LT}, safepoint = false)
    private static int f64LtHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_lt(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_GT}, safepoint = false)
    private static int f64GtHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_gt(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_LE}, safepoint = false)
    private static int f64LeHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_le(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_GE}, safepoint = false)
    private static int f64GeHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_ge(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_ADD}, safepoint = false)
    private static int i32AddHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_add(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_SUB}, safepoint = false)
    private static int i32SubHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_sub(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_MUL}, safepoint = false)
    private static int i32MulHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_mul(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_DIV_S}, safepoint = false)
    private static int i32DivSHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        state.thiz.i32_div_s(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_DIV_U}, safepoint = false)
    private static int i32DivUHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        state.thiz.i32_div_u(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_REM_S}, safepoint = false)
    private static int i32RemSHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        state.thiz.i32_rem_s(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_REM_U}, safepoint = false)
    private static int i32RemUHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        state.thiz.i32_rem_u(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_AND}, safepoint = false)
    private static int i32AndHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_and(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_OR}, safepoint = false)
    private static int i32OrHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_or(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_XOR}, safepoint = false)
    private static int i32XorHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_xor(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_SHL}, safepoint = false)
    private static int i32ShlHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_shl(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_SHR_S}, safepoint = false)
    private static int i32ShrSHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_shr_s(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_SHR_U}, safepoint = false)
    private static int i32ShrUHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_shr_u(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_ROTL}, safepoint = false)
    private static int i32RotlHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_rotl(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_ROTR}, safepoint = false)
    private static int i32RotrHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_rotr(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.DROP}, safepoint = false)
    private static int dropHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        virtualState.stackPointer--;
        dropPrimitive(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.DROP_OBJ}, safepoint = false)
    private static int dropObjHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        virtualState.stackPointer--;
        dropObject(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.SELECT}, safepoint = false)
    private static int selectHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        if (profileCondition(state.bytecode, offset + 1, popBoolean(frame, virtualState.stackPointer - 1))) {
            drop(frame, virtualState.stackPointer - 2);
        } else {
            WasmFrame.copyPrimitive(frame, virtualState.stackPointer - 2, virtualState.stackPointer - 3);
            dropPrimitive(frame, virtualState.stackPointer - 2);
        }
        virtualState.stackPointer -= 2;
        return offset + 3;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.SELECT_OBJ}, safepoint = false)
    private static int selectObjHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        if (profileCondition(state.bytecode, offset + 1, popBoolean(frame, virtualState.stackPointer - 1))) {
            dropObject(frame, virtualState.stackPointer - 2);
        } else {
            WasmFrame.copyObject(frame, virtualState.stackPointer - 2, virtualState.stackPointer - 3);
            dropObject(frame, virtualState.stackPointer - 2);
        }
        virtualState.stackPointer -= 2;
        return offset + 3;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.LOCAL_GET_OBJ_U8}, safepoint = false)
    private static int localGetObjU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int index = rawPeekU8(state.bytecode, offset + 1);
        local_get_obj(frame, virtualState.stackPointer, index);
        virtualState.stackPointer++;
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.LOCAL_GET_OBJ_I32}, safepoint = false)
    private static int localGetObjI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int index = rawPeekI32(state.bytecode, offset + 1);
        local_get_obj(frame, virtualState.stackPointer, index);
        virtualState.stackPointer++;
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.LOCAL_SET_U8}, safepoint = false)
    private static int localSetU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int index = rawPeekU8(state.bytecode, offset + 1);
        virtualState.stackPointer--;
        local_set(frame, virtualState.stackPointer, index);
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.LOCAL_SET_I32}, safepoint = false)
    private static int localSetI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int index = rawPeekI32(state.bytecode, offset + 1);
        virtualState.stackPointer--;
        local_set(frame, virtualState.stackPointer, index);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.LOCAL_SET_OBJ_U8}, safepoint = false)
    private static int localSetObjU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int index = rawPeekU8(state.bytecode, offset + 1);
        virtualState.stackPointer--;
        local_set_obj(frame, virtualState.stackPointer, index);
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.LOCAL_SET_OBJ_I32}, safepoint = false)
    private static int localSetObjI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int index = rawPeekI32(state.bytecode, offset + 1);
        virtualState.stackPointer--;
        local_set_obj(frame, virtualState.stackPointer, index);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.LOCAL_TEE_U8}, safepoint = false)
    private static int localTeeU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int index = rawPeekU8(state.bytecode, offset + 1);
        local_tee(frame, virtualState.stackPointer - 1, index);
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.LOCAL_TEE_I32}, safepoint = false)
    private static int localTeeI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int index = rawPeekI32(state.bytecode, offset + 1);
        local_tee(frame, virtualState.stackPointer - 1, index);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.LOCAL_TEE_OBJ_U8}, safepoint = false)
    private static int localTeeObjU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int index = rawPeekU8(state.bytecode, offset + 1);
        local_tee_obj(frame, virtualState.stackPointer - 1, index);
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.LOCAL_TEE_OBJ_I32}, safepoint = false)
    private static int localTeeObjI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int index = rawPeekI32(state.bytecode, offset + 1);
        local_tee_obj(frame, virtualState.stackPointer - 1, index);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.GLOBAL_GET_U8}, safepoint = false)
    private static int globalGetU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int index = rawPeekU8(state.bytecode, offset + 1);
        final WasmInstance instance = state.instance;
        state.thiz.global_get(instance, frame, virtualState.stackPointer, index);
        virtualState.stackPointer++;
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.GLOBAL_GET_I32}, safepoint = false)
    private static int globalGetI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int index = rawPeekI32(state.bytecode, offset + 1);
        final WasmInstance instance = state.instance;
        state.thiz.global_get(instance, frame, virtualState.stackPointer, index);
        virtualState.stackPointer++;
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.GLOBAL_SET_U8}, safepoint = false)
    private static int globalSetU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int index = rawPeekU8(state.bytecode, offset + 1);
        virtualState.stackPointer--;
        final WasmInstance instance = state.instance;
        state.thiz.global_set(instance, frame, virtualState.stackPointer, index);
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.GLOBAL_SET_I32}, safepoint = false)
    private static int globalSetI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int index = rawPeekI32(state.bytecode, offset + 1);
        virtualState.stackPointer--;
        final WasmInstance instance = state.instance;
        state.thiz.global_set(instance, frame, virtualState.stackPointer, index);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_CONST_I8}, safepoint = false)
    private static int i64ConstI8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final long value = rawPeekI8(state.bytecode, offset + 1);
        pushLong(frame, virtualState.stackPointer, value);
        virtualState.stackPointer++;
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_CONST_I64}, safepoint = false)
    private static int i64ConstI64Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final long value = rawPeekI64(state.bytecode, offset + 1);
        pushLong(frame, virtualState.stackPointer, value);
        virtualState.stackPointer++;
        return offset + 9;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_CLZ}, safepoint = false)
    private static int i64ClzHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_clz(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_CTZ}, safepoint = false)
    private static int i64CtzHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_ctz(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_POPCNT}, safepoint = false)
    private static int i64PopcntHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_popcnt(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_ADD}, safepoint = false)
    private static int i64AddHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_add(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_SUB}, safepoint = false)
    private static int i64SubHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_sub(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_MUL}, safepoint = false)
    private static int i64MulHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_mul(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_DIV_S}, safepoint = false)
    private static int i64DivSHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        state.thiz.i64_div_s(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_DIV_U}, safepoint = false)
    private static int i64DivUHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        state.thiz.i64_div_u(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_REM_S}, safepoint = false)
    private static int i64RemSHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        state.thiz.i64_rem_s(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_REM_U}, safepoint = false)
    private static int i64RemUHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        state.thiz.i64_rem_u(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_AND}, safepoint = false)
    private static int i64AndHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_and(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_OR}, safepoint = false)
    private static int i64OrHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_or(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_XOR}, safepoint = false)
    private static int i64XorHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_xor(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_SHL}, safepoint = false)
    private static int i64ShlHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_shl(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_SHR_S}, safepoint = false)
    private static int i64ShrSHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_shr_s(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_SHR_U}, safepoint = false)
    private static int i64ShrUHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_shr_u(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_ROTL}, safepoint = false)
    private static int i64RotlHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_rotl(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_ROTR}, safepoint = false)
    private static int i64RotrHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_rotr(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_CONST}, safepoint = false)
    private static int f32ConstHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        float value = Float.intBitsToFloat(rawPeekI32(state.bytecode, offset + 1));
        pushFloat(frame, virtualState.stackPointer, value);
        virtualState.stackPointer++;
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_CONST}, safepoint = false)
    private static int f64ConstHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        double value = Double.longBitsToDouble(BinaryStreamParser.rawPeekI64(state.bytecode, offset + 1));
        pushDouble(frame, virtualState.stackPointer, value);
        virtualState.stackPointer++;
        return offset + 9;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_LOAD_U8}, safepoint = false)
    private static int i32LoadU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        int value = state.zeroMemoryLib.load_i32(state.zeroMemory, state.thiz, address);
        pushInt(frame, virtualState.stackPointer - 1, value);
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD_U8}, safepoint = false)
    private static int i64LoadU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = state.zeroMemoryLib.load_i64(state.zeroMemory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_LOAD_U8}, safepoint = false)
    private static int f32LoadU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final float value = state.zeroMemoryLib.load_f32(state.zeroMemory, state.thiz, address);
        pushFloat(frame, virtualState.stackPointer - 1, value);
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_LOAD_U8}, safepoint = false)
    private static int f64LoadU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final double value = state.zeroMemoryLib.load_f64(state.zeroMemory, state.thiz, address);
        pushDouble(frame, virtualState.stackPointer - 1, value);
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_LOAD8_S_U8}, safepoint = false)
    private static int i32Load8SU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final int value = state.zeroMemoryLib.load_i32_8s(state.zeroMemory, state.thiz, address);
        pushInt(frame, virtualState.stackPointer - 1, value);
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_LOAD8_U_U8}, safepoint = false)
    private static int i32Load8UU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final int value = state.zeroMemoryLib.load_i32_8u(state.zeroMemory, state.thiz, address);
        pushInt(frame, virtualState.stackPointer - 1, value);
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_LOAD16_S_U8}, safepoint = false)
    private static int i32Load16SU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final int value = state.zeroMemoryLib.load_i32_16s(state.zeroMemory, state.thiz, address);
        pushInt(frame, virtualState.stackPointer - 1, value);
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_LOAD16_U_U8}, safepoint = false)
    private static int i32Load16UU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final int value = state.zeroMemoryLib.load_i32_16u(state.zeroMemory, state.thiz, address);
        pushInt(frame, virtualState.stackPointer - 1, value);
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD8_S_U8}, safepoint = false)
    private static int i64Load8SU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = state.zeroMemoryLib.load_i64_8s(state.zeroMemory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD8_U_U8}, safepoint = false)
    private static int i64Load8UU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = state.zeroMemoryLib.load_i64_8u(state.zeroMemory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD16_S_U8}, safepoint = false)
    private static int i64Load16SU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = state.zeroMemoryLib.load_i64_16s(state.zeroMemory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD16_U_U8}, safepoint = false)
    private static int i64Load16UU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = state.zeroMemoryLib.load_i64_16u(state.zeroMemory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD32_S_U8}, safepoint = false)
    private static int i64Load32SU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = state.zeroMemoryLib.load_i64_32s(state.zeroMemory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD32_U_U8}, safepoint = false)
    private static int i64Load32UU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = state.zeroMemoryLib.load_i64_32u(state.zeroMemory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_LOAD_I32}, safepoint = false)
    private static int i32LoadI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        int value = state.zeroMemoryLib.load_i32(state.zeroMemory, state.thiz, address);
        pushInt(frame, virtualState.stackPointer - 1, value);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD_I32}, safepoint = false)
    private static int i64LoadI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = state.zeroMemoryLib.load_i64(state.zeroMemory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_LOAD_I32}, safepoint = false)
    private static int f32LoadI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final float value = state.zeroMemoryLib.load_f32(state.zeroMemory, state.thiz, address);
        pushFloat(frame, virtualState.stackPointer - 1, value);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_LOAD_I32}, safepoint = false)
    private static int f64LoadI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final double value = state.zeroMemoryLib.load_f64(state.zeroMemory, state.thiz, address);
        pushDouble(frame, virtualState.stackPointer - 1, value);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_LOAD8_S_I32}, safepoint = false)
    private static int i32Load8SI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final int value = state.zeroMemoryLib.load_i32_8s(state.zeroMemory, state.thiz, address);
        pushInt(frame, virtualState.stackPointer - 1, value);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_LOAD8_U_I32}, safepoint = false)
    private static int i32Load8UI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final int value = state.zeroMemoryLib.load_i32_8u(state.zeroMemory, state.thiz, address);
        pushInt(frame, virtualState.stackPointer - 1, value);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_LOAD16_S_I32}, safepoint = false)
    private static int i32Load16SI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final int value = state.zeroMemoryLib.load_i32_16s(state.zeroMemory, state.thiz, address);
        pushInt(frame, virtualState.stackPointer - 1, value);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_LOAD16_U_I32}, safepoint = false)
    private static int i32Load16UI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final int value = state.zeroMemoryLib.load_i32_16u(state.zeroMemory, state.thiz, address);
        pushInt(frame, virtualState.stackPointer - 1, value);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD8_S_I32}, safepoint = false)
    private static int i64Load8SI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = state.zeroMemoryLib.load_i64_8s(state.zeroMemory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD8_U_I32}, safepoint = false)
    private static int i64Load8UI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = state.zeroMemoryLib.load_i64_8u(state.zeroMemory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD16_S_I32}, safepoint = false)
    private static int i64Load16SI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = state.zeroMemoryLib.load_i64_16s(state.zeroMemory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD16_U_I32}, safepoint = false)
    private static int i64Load16UI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = state.zeroMemoryLib.load_i64_16u(state.zeroMemory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD32_S_I32}, safepoint = false)
    private static int i64Load32SI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = state.zeroMemoryLib.load_i64_32s(state.zeroMemory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD32_U_I32}, safepoint = false)
    private static int i64Load32UI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 1);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = state.zeroMemoryLib.load_i64_32u(state.zeroMemory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_STORE_U8}, safepoint = false)
    private static int i32StoreU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 2);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final int value = popInt(frame, virtualState.stackPointer - 1);
        state.zeroMemoryLib.store_i32(state.zeroMemory, state.thiz, address, value);
        virtualState.stackPointer -= 2;
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_STORE_U8}, safepoint = false)
    private static int i64StoreU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 2);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = popLong(frame, virtualState.stackPointer - 1);
        state.zeroMemoryLib.store_i64(state.zeroMemory, state.thiz, address, value);
        virtualState.stackPointer -= 2;
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_STORE_U8}, safepoint = false)
    private static int f32StoreU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 2);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final float value = popFloat(frame, virtualState.stackPointer - 1);
        state.zeroMemoryLib.store_f32(state.zeroMemory, state.thiz, address, value);
        virtualState.stackPointer -= 2;
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_STORE_U8}, safepoint = false)
    private static int f64StoreU8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 2);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final double value = popDouble(frame, virtualState.stackPointer - 1);
        state.zeroMemoryLib.store_f64(state.zeroMemory, state.thiz, address, value);
        virtualState.stackPointer -= 2;
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_STORE_8_U8}, safepoint = false)
    private static int i32Store8U8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 2);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final int value = popInt(frame, virtualState.stackPointer - 1);
        state.zeroMemoryLib.store_i32_8(state.zeroMemory, state.thiz, address, (byte) value);
        virtualState.stackPointer -= 2;
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_STORE_16_U8}, safepoint = false)
    private static int i32Store16U8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 2);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final int value = popInt(frame, virtualState.stackPointer - 1);
        state.zeroMemoryLib.store_i32_16(state.zeroMemory, state.thiz, address, (short) value);
        virtualState.stackPointer -= 2;
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_STORE_8_U8}, safepoint = false)
    private static int i64Store8U8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 2);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = popLong(frame, virtualState.stackPointer - 1);
        state.zeroMemoryLib.store_i64_8(state.zeroMemory, state.thiz, address, (byte) value);
        virtualState.stackPointer -= 2;
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_STORE_16_U8}, safepoint = false)
    private static int i64Store16U8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 2);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = popLong(frame, virtualState.stackPointer - 1);
        state.zeroMemoryLib.store_i64_16(state.zeroMemory, state.thiz, address, (short) value);
        virtualState.stackPointer -= 2;
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_STORE_32_U8}, safepoint = false)
    private static int i64Store32U8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekU8(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 2);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = popLong(frame, virtualState.stackPointer - 1);
        state.zeroMemoryLib.store_i64_32(state.zeroMemory, state.thiz, address, (int) value);
        virtualState.stackPointer -= 2;
        return offset + 2;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_STORE_I32}, safepoint = false)
    private static int i32StoreI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 2);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final int value = popInt(frame, virtualState.stackPointer - 1);
        state.zeroMemoryLib.store_i32(state.zeroMemory, state.thiz, address, value);
        virtualState.stackPointer -= 2;
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.MEMORY_SIZE}, safepoint = false)
    private static int memorySizeHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memoryIndex = rawPeekI32(state.bytecode, offset + 1);
        final WasmInstance instance = state.instance;
        final WasmMemory memory = memory(instance, state.memoryLibs, state.module, memoryIndex);
        int pageSize = (int) state.memoryLibs[memoryIndex].size(memory);
        pushInt(frame, virtualState.stackPointer, pageSize);
        virtualState.stackPointer++;
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.MEMORY_GROW}, safepoint = false)
    private static int memoryGrowHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memoryIndex = rawPeekI32(state.bytecode, offset + 1);
        final WasmInstance instance = state.instance;
        final WasmMemory memory = memory(instance, state.memoryLibs, state.module, memoryIndex);
        int extraSize = popInt(frame, virtualState.stackPointer - 1);
        int previousSize = (int) state.memoryLibs[memoryIndex].grow(memory, extraSize);
        pushInt(frame, virtualState.stackPointer - 1, previousSize);
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_STORE_I32}, safepoint = false)
    private static int i64StoreI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 2);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = popLong(frame, virtualState.stackPointer - 1);
        state.zeroMemoryLib.store_i64(state.zeroMemory, state.thiz, address, value);
        virtualState.stackPointer -= 2;
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_STORE_I32}, safepoint = false)
    private static int f32StoreI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 2);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final float value = popFloat(frame, virtualState.stackPointer - 1);
        state.zeroMemoryLib.store_f32(state.zeroMemory, state.thiz, address, value);
        virtualState.stackPointer -= 2;
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_STORE_I32}, safepoint = false)
    private static int f64StoreI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 2);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final double value = popDouble(frame, virtualState.stackPointer - 1);
        state.zeroMemoryLib.store_f64(state.zeroMemory, state.thiz, address, value);
        virtualState.stackPointer -= 2;
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_STORE_8_I32}, safepoint = false)
    private static int i32Store8I32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 2);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final int value = popInt(frame, virtualState.stackPointer - 1);
        state.zeroMemoryLib.store_i32_8(state.zeroMemory, state.thiz, address, (byte) value);
        virtualState.stackPointer -= 2;
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_STORE_16_I32}, safepoint = false)
    private static int i32Store16I32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 2);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final int value = popInt(frame, virtualState.stackPointer - 1);
        state.zeroMemoryLib.store_i32_16(state.zeroMemory, state.thiz, address, (short) value);
        virtualState.stackPointer -= 2;
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_STORE_8_I32}, safepoint = false)
    private static int i64Store8I32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 2);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = popLong(frame, virtualState.stackPointer - 1);
        state.zeroMemoryLib.store_i64_8(state.zeroMemory, state.thiz, address, (byte) value);
        virtualState.stackPointer -= 2;
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_STORE_16_I32}, safepoint = false)
    private static int i64Store16I32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 2);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = popLong(frame, virtualState.stackPointer - 1);
        state.zeroMemoryLib.store_i64_16(state.zeroMemory, state.thiz, address, (short) value);
        virtualState.stackPointer -= 2;
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_STORE_32_I32}, safepoint = false)
    private static int i64Store32I32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int memOffset = rawPeekI32(state.bytecode, offset + 1);
        final int baseAddress = popInt(frame, virtualState.stackPointer - 2);
        final long address = effectiveMemoryAddress(memOffset, baseAddress);
        final long value = popLong(frame, virtualState.stackPointer - 1);
        state.zeroMemoryLib.store_i64_32(state.zeroMemory, state.thiz, address, (int) value);
        virtualState.stackPointer -= 2;
        return offset + 5;
    }

    private static long getBaseAddress(int stackPointer, FrameWithoutBoxing frame, int indexType64) {
        if (indexType64 == 0) {
            return Integer.toUnsignedLong(popInt(frame, stackPointer));
        } else {
            return popLong(frame, stackPointer);
        }
    }

    private static long getMemOffset(int offset, byte[] bytecode, int offsetLength) {
        return switch (offsetLength) {
            case BytecodeBitEncoding.MEMORY_OFFSET_U8 -> rawPeekU8(bytecode, offset);
            case BytecodeBitEncoding.MEMORY_OFFSET_U32 -> rawPeekU32(bytecode, offset);
            case BytecodeBitEncoding.MEMORY_OFFSET_I64 -> rawPeekI64(bytecode, offset);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_LOAD}, safepoint = false)
    private static int i32LoadHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 1, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final int value = memoryLib.load_i32(memory, state.thiz, address);
        pushInt(frame, virtualState.stackPointer - 1, value);
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD}, safepoint = false)
    private static int i64LoadHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 1, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final long value = memoryLib.load_i64(memory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_LOAD}, safepoint = false)
    private static int f32LoadHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 1, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final float value = memoryLib.load_f32(memory, state.thiz, address);
        pushFloat(frame, virtualState.stackPointer - 1, value);
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_LOAD}, safepoint = false)
    private static int f64LoadHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 1, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final double value = memoryLib.load_f64(memory, state.thiz, address);
        pushDouble(frame, virtualState.stackPointer - 1, value);
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_LOAD8_S}, safepoint = false)
    private static int i32Load8SHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 1, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final int value = memoryLib.load_i32_8s(memory, state.thiz, address);
        pushInt(frame, virtualState.stackPointer - 1, value);
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_LOAD8_U}, safepoint = false)
    private static int i32Load8UHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 1, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final int value = memoryLib.load_i32_8u(memory, state.thiz, address);
        pushInt(frame, virtualState.stackPointer - 1, value);
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_LOAD16_S}, safepoint = false)
    private static int i32Load16SHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 1, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final int value = memoryLib.load_i32_16s(memory, state.thiz, address);
        pushInt(frame, virtualState.stackPointer - 1, value);
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_LOAD16_U}, safepoint = false)
    private static int i32Load16UHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 1, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final int value = memoryLib.load_i32_16u(memory, state.thiz, address);
        pushInt(frame, virtualState.stackPointer - 1, value);
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD8_S}, safepoint = false)
    private static int i64Load8SHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 1, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final long value = memoryLib.load_i64_8s(memory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD8_U}, safepoint = false)
    private static int i64Load8UHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 1, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final long value = memoryLib.load_i64_8u(memory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD16_S}, safepoint = false)
    private static int i64Load16SHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 1, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final long value = memoryLib.load_i64_16s(memory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD16_U}, safepoint = false)
    private static int i64Load16UHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 1, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final long value = memoryLib.load_i64_16u(memory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD32_S}, safepoint = false)
    private static int i64Load32SHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 1, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final long value = memoryLib.load_i64_32s(memory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_LOAD32_U}, safepoint = false)
    private static int i64Load32UHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 1, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final long value = memoryLib.load_i64_32u(memory, state.thiz, address);
        pushLong(frame, virtualState.stackPointer - 1, value);
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_STORE}, safepoint = false)
    private static int i32StoreHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 2, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final int value = popInt(frame, virtualState.stackPointer - 1);
        memoryLib.store_i32(memory, state.thiz, address, value);
        virtualState.stackPointer -= 2;
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_STORE}, safepoint = false)
    private static int i64StoreHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 2, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final long value = popLong(frame, virtualState.stackPointer - 1);
        memoryLib.store_i64(memory, state.thiz, address, value);
        virtualState.stackPointer -= 2;
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_STORE}, safepoint = false)
    private static int f32StoreHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 2, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final float value = popFloat(frame, virtualState.stackPointer - 1);
        memoryLib.store_f32(memory, state.thiz, address, value);
        virtualState.stackPointer -= 2;
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_STORE}, safepoint = false)
    private static int f64StoreHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 2, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final double value = popDouble(frame, virtualState.stackPointer - 1);
        memoryLib.store_f64(memory, state.thiz, address, value);
        virtualState.stackPointer -= 2;
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_STORE_8}, safepoint = false)
    private static int i32Store8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 2, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final int value = popInt(frame, virtualState.stackPointer - 1);
        memoryLib.store_i32_8(memory, state.thiz, address, (byte) value);
        virtualState.stackPointer -= 2;
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_STORE_16}, safepoint = false)
    private static int i32Store16Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 2, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final int value = popInt(frame, virtualState.stackPointer - 1);
        memoryLib.store_i32_16(memory, state.thiz, address, (short) value);
        virtualState.stackPointer -= 2;
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_STORE_8}, safepoint = false)
    private static int i64Store8Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 2, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final long value = popLong(frame, virtualState.stackPointer - 1);
        memoryLib.store_i64_8(memory, state.thiz, address, (byte) value);
        virtualState.stackPointer -= 2;
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_STORE_16}, safepoint = false)
    private static int i64Store16Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 2, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final long value = popLong(frame, virtualState.stackPointer - 1);
        memoryLib.store_i64_16(memory, state.thiz, address, (short) value);
        virtualState.stackPointer -= 2;
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_STORE_32}, safepoint = false)
    private static int i64Store32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int encoding = rawPeekU8(state.bytecode, offset + 1);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;

        final long memOffset = getMemOffset(offset + 6, state.bytecode, offsetLength);
        final long baseAddress = getBaseAddress(virtualState.stackPointer - 2, frame, indexType64);
        final long address = effectiveMemoryAddress64(memOffset, baseAddress, state.thiz);

        final int memoryIndex = rawPeekI32(state.bytecode, offset + 2);
        final WasmMemory memory = memory(state.instance, state.memoryLibs, state.module, memoryIndex);
        final WasmMemoryLibrary memoryLib = state.memoryLibs[memoryIndex];

        final long value = popLong(frame, virtualState.stackPointer - 1);
        memoryLib.store_i64_32(memory, state.thiz, address, (int) value);
        virtualState.stackPointer -= 2;
        return offset + 6 + offsetLength;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_CLZ}, safepoint = false)
    private static int i32ClzHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_clz(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_CTZ}, safepoint = false)
    private static int i32CtzHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_ctz(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_POPCNT}, safepoint = false)
    private static int i32PopcntHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_popcnt(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_ABS}, safepoint = false)
    private static int f32AbsHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_abs(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_NEG}, safepoint = false)
    private static int f32NegHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_neg(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_CEIL}, safepoint = false)
    private static int f32CeilHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_ceil(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_FLOOR}, safepoint = false)
    private static int f32FloorHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_floor(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_TRUNC}, safepoint = false)
    private static int f32TruncHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_trunc(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_NEAREST}, safepoint = false)
    private static int f32NearestHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_nearest(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_SQRT}, safepoint = false)
    private static int f32SqrtHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_sqrt(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_ADD}, safepoint = false)
    private static int f32AddHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_add(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_SUB}, safepoint = false)
    private static int f32SubHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_sub(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_MUL}, safepoint = false)
    private static int f32MulHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_mul(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_DIV}, safepoint = false)
    private static int f32DivHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_div(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_MIN}, safepoint = false)
    private static int f32MinHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_min(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_MAX}, safepoint = false)
    private static int f32MaxHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_max(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_COPYSIGN}, safepoint = false)
    private static int f32CopysignHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_copysign(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_ABS}, safepoint = false)
    private static int f64AbsHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_abs(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_NEG}, safepoint = false)
    private static int f64NegHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_neg(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_CEIL}, safepoint = false)
    private static int f64CeilHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_ceil(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_FLOOR}, safepoint = false)
    private static int f64FloorHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_floor(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_TRUNC}, safepoint = false)
    private static int f64TruncHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_trunc(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_NEAREST}, safepoint = false)
    private static int f64NearestHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_nearest(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_SQRT}, safepoint = false)
    private static int f64SqrtHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_sqrt(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_ADD}, safepoint = false)
    private static int f64AddHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_add(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_SUB}, safepoint = false)
    private static int f64SubHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_sub(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_MUL}, safepoint = false)
    private static int f64MulHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_mul(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_DIV}, safepoint = false)
    private static int f64DivHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_div(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_MIN}, safepoint = false)
    private static int f64MinHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_min(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_MAX}, safepoint = false)
    private static int f64MaxHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_max(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_COPYSIGN}, safepoint = false)
    private static int f64CopysignHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_copysign(frame, virtualState.stackPointer);
        virtualState.stackPointer--;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_WRAP_I64}, safepoint = false)
    private static int i32WrapI64Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_wrap_i64(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_TRUNC_F32_S}, safepoint = false)
    private static int i32TruncF32SHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        state.thiz.i32_trunc_f32_s(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_TRUNC_F32_U}, safepoint = false)
    private static int i32TruncF32UHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        state.thiz.i32_trunc_f32_u(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_TRUNC_F64_S}, safepoint = false)
    private static int i32TruncF64SHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        state.thiz.i32_trunc_f64_s(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_TRUNC_F64_U}, safepoint = false)
    private static int i32TruncF64UHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        state.thiz.i32_trunc_f64_u(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_EXTEND_I32_S}, safepoint = false)
    private static int i64ExtendI32SHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_extend_i32_s(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_EXTEND_I32_U}, safepoint = false)
    private static int i64ExtendI32UHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_extend_i32_u(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_TRUNC_F32_S}, safepoint = false)
    private static int i64TruncF32SHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        state.thiz.i64_trunc_f32_s(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_TRUNC_F32_U}, safepoint = false)
    private static int i64TruncF32UHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        state.thiz.i64_trunc_f32_u(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_TRUNC_F64_S}, safepoint = false)
    private static int i64TruncF64SHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        state.thiz.i64_trunc_f64_s(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_TRUNC_F64_U}, safepoint = false)
    private static int i64TruncF64UHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        state.thiz.i64_trunc_f64_u(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_CONVERT_I32_S}, safepoint = false)
    private static int f32ConvertI32SHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_convert_i32_s(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_CONVERT_I32_U}, safepoint = false)
    private static int f32ConvertI32UHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_convert_i32_u(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_CONVERT_I64_S}, safepoint = false)
    private static int f32ConvertI64SHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_convert_i64_s(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_CONVERT_I64_U}, safepoint = false)
    private static int f32ConvertI64UHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_convert_i64_u(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_DEMOTE_F64}, safepoint = false)
    private static int f32DemoteF64Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_demote_f64(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_CONVERT_I32_S}, safepoint = false)
    private static int f64ConvertI32SHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_convert_i32_s(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_CONVERT_I32_U}, safepoint = false)
    private static int f64ConvertI32UHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_convert_i32_u(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_CONVERT_I64_S}, safepoint = false)
    private static int f64ConvertI64SHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_convert_i64_s(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_CONVERT_I64_U}, safepoint = false)
    private static int f64ConvertI64UHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_convert_i64_u(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_PROMOTE_F32}, safepoint = false)
    private static int f64PromoteF32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_promote_f32(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_REINTERPRET_F32}, safepoint = false)
    private static int i32ReinterpretF32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_reinterpret_f32(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_REINTERPRET_F64}, safepoint = false)
    private static int i64ReinterpretF64Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_reinterpret_f64(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F32_REINTERPRET_I32}, safepoint = false)
    private static int f32ReinterpretI32Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f32_reinterpret_i32(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.F64_REINTERPRET_I64}, safepoint = false)
    private static int f64ReinterpretI64Handler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        f64_reinterpret_i64(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_EXTEND8_S}, safepoint = false)
    private static int i32Extend8SHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_extend8_s(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I32_EXTEND16_S}, safepoint = false)
    private static int i32Extend16SHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i32_extend16_s(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_EXTEND8_S}, safepoint = false)
    private static int i64Extend8SHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_extend8_s(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_EXTEND16_S}, safepoint = false)
    private static int i64Extend16SHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_extend16_s(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.I64_EXTEND32_S}, safepoint = false)
    private static int i64Extend32SHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        i64_extend32_s(frame, virtualState.stackPointer);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.REF_NULL}, safepoint = false)
    private static int refNullHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        pushReference(frame, virtualState.stackPointer, WasmConstant.NULL);
        virtualState.stackPointer++;
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.REF_IS_NULL}, safepoint = false)
    private static int refIsNullHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final Object refType = popReference(frame, virtualState.stackPointer - 1);
        pushInt(frame, virtualState.stackPointer - 1, refType == WasmConstant.NULL ? 1 : 0);
        return offset + 1;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.REF_FUNC}, safepoint = false)
    private static int refFuncHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int functionIndex = rawPeekI32(state.bytecode, offset + 1);
        final WasmFunction function = state.module.symbolTable().function(functionIndex);
        final WasmInstance instance = state.instance;
        final WasmFunctionInstance functionInstance = instance.functionInstance(function);
        pushReference(frame, virtualState.stackPointer, functionInstance);
        virtualState.stackPointer++;
        return offset + 5;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.AGGREGATE}, safepoint = false)
    private static int aggregateHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int aggregateOpcode = rawPeekU8(state.bytecode, offset + 1);
        CompilerAsserts.partialEvaluationConstant(aggregateOpcode);
        final int nextOffset;
        switch (aggregateOpcode) {
            case Bytecode.STRUCT_NEW: {
                final int structTypeIdx = rawPeekI32(state.bytecode, offset + 2);
                nextOffset = offset + 6;

                final WasmStructAccess structAccess = state.module.structTypeAccess(structTypeIdx);
                final int numFields = state.module.structTypeFieldCount(structTypeIdx);

                WasmStruct struct = structAccess.shape().getFactory().create(state.module.closedTypeAt(structTypeIdx));
                state.thiz.popStructFields(frame, structTypeIdx, struct, structAccess, numFields, virtualState.stackPointer);
                virtualState.stackPointer -= numFields;
                WasmFrame.pushReference(frame, virtualState.stackPointer++, struct);
                break;
            }
            case Bytecode.ARRAY_NEW_FIXED: {
                final int arrayTypeIdx = rawPeekI32(state.bytecode, offset + 2);
                final int length = rawPeekI32(state.bytecode, offset + 6);
                nextOffset = offset + 10;

                final DefinedType arrayType = state.module.closedTypeAt(arrayTypeIdx);
                final int elemType = state.module.arrayTypeElemType(arrayTypeIdx);

                WasmArray array = state.thiz.popArrayElements(frame, arrayType, elemType, length, virtualState.stackPointer);
                virtualState.stackPointer -= length;
                WasmFrame.pushReference(frame, virtualState.stackPointer++, array);
                break;
            }
            default: {
                nextOffset = state.thiz.executeAggregate(state.instance, frame, offset + 2, virtualState.stackPointer, aggregateOpcode);
                virtualState.stackPointer += StackEffects.getAggregateOpStackEffect(aggregateOpcode);
                break;
            }
        }
        return nextOffset;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.MISC}, safepoint = true)
    private static int miscHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int miscOpcode = rawPeekU8(state.bytecode, offset + 1);
        CompilerAsserts.partialEvaluationConstant(miscOpcode);
        final int nextOffset;
        switch (miscOpcode) {
            case Bytecode.THROW: {
                state.thiz.codeEntry.exceptionBranch();
                final int tagIndex = rawPeekI32(state.bytecode, offset + 2);
                final int functionTypeIndex = state.module.tagTypeIndex(tagIndex);
                final int numFields = state.module.functionTypeParamCount(functionTypeIndex);
                final Object[] fields = state.thiz.createFieldsForException(frame, functionTypeIndex, numFields, virtualState.stackPointer);
                virtualState.stackPointer -= numFields;
                throw state.thiz.createException(state.instance.tag(tagIndex), fields);
            }
            case Bytecode.THROW_REF: {
                state.thiz.codeEntry.exceptionBranch();
                final Object exception = popReference(frame, virtualState.stackPointer - 1);
                virtualState.stackPointer--;
                assert exception != null : "Exception object has to be a valid exception or wasm null";
                if (exception == WasmConstant.NULL) {
                    throw WasmException.create(Failure.NULL_REFERENCE);
                }
                assert exception instanceof WasmRuntimeException : "Only wasm exceptions can be thrown by throw_ref";
                throw (WasmRuntimeException) exception;
            }
            case Bytecode.RETHROW: {
                state.thiz.codeEntry.exceptionBranch();
                final int catchDepth = rawPeekI32(state.bytecode, offset + 2);
                assert catchDepth >= 0 && catchDepth < state.activeLegacyCatchCount : "Invalid active legacy catch depth";
                final int exceptionSlot = state.legacyCatchBase + state.activeLegacyCatchCount - 1 - catchDepth;
                assert exceptionSlot >= state.legacyCatchBase && exceptionSlot < state.stackBase;
                CompilerAsserts.partialEvaluationConstant(exceptionSlot);
                final WasmRuntimeException exception = (WasmRuntimeException) frame.getObjectStatic(exceptionSlot);
                // Rethrow selects the active legacy exception by depth, then dispatches it like a
                // normal throw from the current bytecode location.
                throw exception;
            }
            case Bytecode.LEGACY_CATCH_DROP: {
                assert state.activeLegacyCatchCount > 0 : "Missing active legacy catch";
                state.activeLegacyCatchCount--;
                final int exceptionSlot = state.legacyCatchBase + state.activeLegacyCatchCount;
                assert exceptionSlot >= state.legacyCatchBase && exceptionSlot < state.stackBase;
                CompilerAsserts.partialEvaluationConstant(exceptionSlot);
                WasmFrame.dropObject(frame, exceptionSlot);
                return offset + 2;
            }
            case Bytecode.LEGACY_CATCH_UNWIND: {
                final int targetDepth = rawPeekI32(state.bytecode, offset + 2);
                state.activeLegacyCatchCount = unwindLegacyCatchesToDepth(frame, state.legacyCatchBase, state.stackBase, state.activeLegacyCatchCount, targetDepth);
                return offset + 6;
            }
            case Bytecode.LEGACY_SKIP_LABEL_U8: {
                return offset + 10;
            }
            case Bytecode.LEGACY_SKIP_LABEL_U16: {
                return offset + 11;
            }
            case Bytecode.LEGACY_SKIP_LABEL_I32: {
                return offset + 18;
            }
            case Bytecode.BR_ON_NULL_U8: {
                Object reference = popReference(frame, --virtualState.stackPointer);
                if (profileCondition(state.bytecode, offset + 3, reference == WasmConstant.NULL)) {
                    final int offsetDelta = rawPeekU8(state.bytecode, offset + 2);
                    // BR_ON_NULL_U8 encodes the back jump value as a positive byte
                    // value. BR_ON_NULL_U8 can never perform a forward jump.
                    nextOffset = offset + 2 - offsetDelta;
                } else {
                    nextOffset = offset + 5;
                    pushReference(frame, virtualState.stackPointer++, reference);
                }
                break;
            }
            case Bytecode.BR_ON_NULL_I32: {
                Object reference = popReference(frame, --virtualState.stackPointer);
                if (profileCondition(state.bytecode, offset + 6, reference == WasmConstant.NULL)) {
                    final int offsetDelta = rawPeekI32(state.bytecode, offset + 2);
                    nextOffset = offset + 2 + offsetDelta;
                } else {
                    nextOffset = offset + 8;
                    pushReference(frame, virtualState.stackPointer++, reference);
                }
                break;
            }
            case Bytecode.BR_ON_NON_NULL_U8: {
                Object reference = popReference(frame, --virtualState.stackPointer);
                if (profileCondition(state.bytecode, offset + 3, reference != WasmConstant.NULL)) {
                    final int offsetDelta = rawPeekU8(state.bytecode, offset + 2);
                    // BR_ON_NULL_U8 encodes the back jump value as a positive byte
                    // value. BR_ON_NULL_U8
                    // can never perform a forward jump.
                    nextOffset = offset + 2 - offsetDelta;
                    pushReference(frame, virtualState.stackPointer++, reference);
                } else {
                    nextOffset = offset + 5;
                }
                break;
            }
            case Bytecode.BR_ON_NON_NULL_I32: {
                Object reference = popReference(frame, --virtualState.stackPointer);
                if (profileCondition(state.bytecode, offset + 6, reference != WasmConstant.NULL)) {
                    final int offsetDelta = rawPeekI32(state.bytecode, offset + 2);
                    nextOffset = offset + 2 + offsetDelta;
                    pushReference(frame, virtualState.stackPointer++, reference);
                } else {
                    nextOffset = offset + 8;
                }
                break;
            }
            default: {
                nextOffset = state.thiz.executeMisc(state.instance, frame, offset + 2, virtualState.stackPointer, miscOpcode);
                virtualState.stackPointer += StackEffects.getMiscOpStackEffect(miscOpcode);
                break;
            }
        }
        return nextOffset;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.ATOMIC}, safepoint = false)
    private static int atomicHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int atomicOpcode = rawPeekU8(state.bytecode, offset + 1);
        CompilerAsserts.partialEvaluationConstant(atomicOpcode);
        if (atomicOpcode == Bytecode.ATOMIC_FENCE) {
            return offset + 2;
        }
        final int encoding = rawPeekU8(state.bytecode, offset + 2);
        final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
        final int memoryIndex = rawPeekI32(state.bytecode, offset + 3);
        final int nextOffset;
        final long memOffset;
        if (indexType64 == 0) {
            memOffset = rawPeekU32(state.bytecode, offset + 7);
            nextOffset = offset + 11;
        } else {
            memOffset = rawPeekI64(state.bytecode, offset + 7);
            nextOffset = offset + 15;
        }
        final WasmInstance instance = state.instance;
        final WasmMemory memory = memory(instance, state.memoryLibs, state.module, memoryIndex);
        final int stackPointerDecrement = state.thiz.executeAtomic(frame, virtualState.stackPointer, atomicOpcode, memory,
                        state.memoryLibs[memoryIndex],
                        memOffset, indexType64);
        virtualState.stackPointer -= stackPointerDecrement;
        return nextOffset;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.VECTOR}, safepoint = false)
    private static int vectorHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int vectorOpcode = rawPeekU8(state.bytecode, offset + 1);
        CompilerAsserts.partialEvaluationConstant(vectorOpcode);
        final WasmInstance instance = state.instance;
        final int nextOffset = state.thiz.executeVector(instance, frame, offset + 2, virtualState.stackPointer, vectorOpcode);
        virtualState.stackPointer += StackEffects.getVectorOpStackEffect(vectorOpcode);
        return nextOffset;
    }

    @EarlyInline
    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(value = {Bytecode.NOTIFY}, safepoint = false)
    private static int notifyHandler(int offset, State state, VirtualState virtualState, FrameWithoutBoxing frame) {
        final int nextLineIndex = rawPeekI32(state.bytecode, offset + 1);
        final int sourceCodeLocation = rawPeekI32(state.bytecode, offset + 5);
        assert state.notifyFunction != null;
        state.notifyFunction.notifyLine(frame, state.lineIndex, nextLineIndex, sourceCodeLocation);
        state.lineIndex = nextLineIndex;
        return offset + 9;
    }

    private int pushExceptionFieldsAndReference(VirtualFrame frame, WasmRuntimeException e, int sourceStackPointer, int catchType, int tagIndex) {
        int stackPointer = sourceStackPointer;
        if (catchType == ExceptionHandlerType.CATCH || catchType == ExceptionHandlerType.CATCH_REF || catchType == ExceptionHandlerType.LEGACY_CATCH) {
            final int functionTypeIndex = module.tagTypeIndex(tagIndex);
            final int numFields = module.functionTypeParamCount(functionTypeIndex);
            if (numFields != 0) {
                pushExceptionFields(frame, e, functionTypeIndex, numFields, stackPointer);
                stackPointer += numFields;
            }
        }
        if (catchType == ExceptionHandlerType.CATCH_REF || catchType == ExceptionHandlerType.CATCH_ALL_REF) {
            pushReference(frame, stackPointer, e);
            stackPointer++;
        }
        CompilerAsserts.partialEvaluationConstant(stackPointer);
        return stackPointer;
    }

    @TruffleBoundary
    private void failFunctionTypeCheck(WasmFunction function, int expectedFunctionTypeIndex) {
        throw WasmException.format(Failure.INDIRECT_CALL_TYPE_MISMATCH, this,
                        "Actual (type %d of function %s) and expected (type %d in module %s) types differ in the indirect call.",
                        function.typeIndex(), function.name(), expectedFunctionTypeIndex, module.name());
    }

    @HostCompilerDirectives.InliningCutoff
    private WasmException callIndirectNotAFunctionError(Object functionCandidate, int elementIndex) {
        enterErrorBranch(codeEntry);
        if (functionCandidate == WasmConstant.NULL) {
            throw WasmException.format(Failure.UNINITIALIZED_ELEMENT, this, "Table element at index %d is uninitialized.", elementIndex);
        } else {
            throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Unknown table element type: %s", functionCandidate);
        }
    }

    @HostCompilerDirectives.InliningCutoff
    private WasmException callRefNotAFunctionError(Object functionCandidate) {
        enterErrorBranch(codeEntry);
        if (functionCandidate == WasmConstant.NULL) {
            throw WasmException.format(Failure.NULL_FUNCTION_REFERENCE, this, "Function reference is null");
        } else {
            throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Unknown function object: %s", functionCandidate);
        }
    }

    private void check(int v, int limit) {
        // This is a temporary hack to hoist values out of the loop.
        if (v >= limit) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, this, "array length too large");
        }
    }

    private int executeDirectCall(VirtualFrame frame, int stackPointer, WasmInstance instance, int callNodeIndex, WasmFunction function, Object[] args) {
        final boolean imported = function.isImported();
        CompilerAsserts.partialEvaluationConstant(imported);
        Node callNode = callNodes[callNodeIndex];
        assert assertDirectCall(instance, function, callNode);
        Object result;
        if (imported) {
            WasmIndirectCallNode indirectCallNode = (WasmIndirectCallNode) callNode;
            WasmFunctionInstance functionInstance = instance.functionInstance(function.index());
            WasmArguments.setModuleInstance(args, functionInstance.moduleInstance());
            result = indirectCallNode.execute(instance.target(function.index()), args);
        } else {
            WasmDirectCallNode directCallNode = (WasmDirectCallNode) callNode;
            WasmArguments.setModuleInstance(args, instance);
            result = directCallNode.execute(args);
        }
        return pushDirectCallResult(frame, stackPointer, function, result, WasmLanguage.get(this));
    }

    private static boolean assertDirectCall(WasmInstance instance, WasmFunction function, Node callNode) {
        WasmFunctionInstance functionInstance = instance.functionInstance(function.index());
        // functionInstance may be null for calls between functions of the same module.
        if (functionInstance == null) {
            assert !function.isImported();
            return true;
        }
        if (callNode instanceof WasmDirectCallNode directCallNode) {
            assert functionInstance.target() == directCallNode.target();
        } else {
            assert callNode instanceof WasmIndirectCallNode : callNode;
        }
        assert functionInstance.context() == WasmContext.get(null);
        return true;
    }

    private Object executeIndirectCallNode(int callNodeIndex, CallTarget target, Object[] args) {
        WasmIndirectCallNode callNode = (WasmIndirectCallNode) callNodes[callNodeIndex];
        return callNode.execute(target, args);
    }

    /**
     * The static address offset (u32) is added to the dynamic address (u32) operand, yielding a
     * 33-bit effective address that is the zero-based index at which the memory is accessed.
     */
    private static long effectiveMemoryAddress(int staticAddressOffset, int dynamicAddress) {
        return Integer.toUnsignedLong(dynamicAddress) + Integer.toUnsignedLong(staticAddressOffset);
    }

    /**
     * The static address offset (u64) is added to the dynamic address (u64) operand, yielding a
     * 65-bit effective address that is the zero-based index at which the memory is accessed.
     */
    private static long effectiveMemoryAddress64(long staticAddressOffset, long dynamicAddress, WasmFunctionNode<?> thiz) {
        try {
            return Math.addExact(dynamicAddress, staticAddressOffset);
        } catch (ArithmeticException e) {
            enterErrorBranch(thiz.codeEntry);
            throw WasmException.create(Failure.UNSPECIFIED_TRAP, "Memory address too large");
        }
    }

    private void executeMemoryInit(WasmInstance instance, VirtualFrame frame, int stackPointer, int opcode, int memoryIndex, int dataIndex) {
        final int n;
        final int src;
        final long dst;
        switch (opcode) {
            case Bytecode.MEMORY_INIT: {
                n = popInt(frame, stackPointer - 1);
                src = popInt(frame, stackPointer - 2);
                dst = popInt(frame, stackPointer - 3);
                memory_init(instance, n, src, dst, dataIndex, memoryIndex);
                break;
            }
            case Bytecode.MEMORY64_INIT: {
                n = popInt(frame, stackPointer - 1);
                src = popInt(frame, stackPointer - 2);
                dst = popLong(frame, stackPointer - 3);
                memory_init(instance, n, src, dst, dataIndex, memoryIndex);
                break;
            }
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private void executeMemoryCopy(WasmInstance instance, VirtualFrame frame, int stackPointer, int opcode, int destMemoryIndex, int srcMemoryIndex) {
        final long n;
        final long src;
        final long dst;
        switch (opcode) {
            case Bytecode.MEMORY_COPY: {
                n = popInt(frame, stackPointer - 1);
                src = popInt(frame, stackPointer - 2);
                dst = popInt(frame, stackPointer - 3);
                break;
            }
            case Bytecode.MEMORY64_COPY_D64_S64: {
                n = popLong(frame, stackPointer - 1);
                src = popLong(frame, stackPointer - 2);
                dst = popLong(frame, stackPointer - 3);
                break;
            }
            case Bytecode.MEMORY64_COPY_D64_S32: {
                n = popInt(frame, stackPointer - 1);
                src = popInt(frame, stackPointer - 2);
                dst = popLong(frame, stackPointer - 3);
                break;
            }
            case Bytecode.MEMORY64_COPY_D32_S64: {
                n = popInt(frame, stackPointer - 1);
                src = popLong(frame, stackPointer - 2);
                dst = popInt(frame, stackPointer - 3);
                break;
            }
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
        memory_copy(instance, n, src, dst, destMemoryIndex, srcMemoryIndex);
    }

    private void executeMemoryFill(WasmInstance instance, VirtualFrame frame, int stackPointer, int opcode, int memoryIndex) {
        final int val;
        final long n;
        final long dst;
        switch (opcode) {
            case Bytecode.MEMORY_FILL: {
                n = popInt(frame, stackPointer - 1);
                val = popInt(frame, stackPointer - 2);
                dst = popInt(frame, stackPointer - 3);
                break;
            }
            case Bytecode.MEMORY64_FILL: {
                n = popLong(frame, stackPointer - 1);
                val = popInt(frame, stackPointer - 2);
                dst = popLong(frame, stackPointer - 3);
                break;
            }
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
        memory_fill(instance, n, val, dst, memoryIndex);
    }

    @BytecodeInterpreterSwitch
    private int executeAggregate(WasmInstance instance, VirtualFrame frame, int startingOffset, int startingStackPointer, int aggregateOpcode) {
        int offset = startingOffset;
        int stackPointer = startingStackPointer;

        CompilerAsserts.partialEvaluationConstant(aggregateOpcode);
        switch (aggregateOpcode) {
            case Bytecode.STRUCT_NEW_DEFAULT: {
                final int structTypeIdx = rawPeekI32(bytecode, offset);
                offset += 4;

                final WasmStructAccess structAccess = module.structTypeAccess(structTypeIdx);
                final int numFields = module.structTypeFieldCount(structTypeIdx);

                WasmStruct struct = structAccess.shape().getFactory().create(module.closedTypeAt(structTypeIdx));
                initStructDefaultFields(structTypeIdx, struct, structAccess, numFields);
                WasmFrame.pushReference(frame, stackPointer, struct);
                stackPointer += 1;
                break;
            }
            case Bytecode.STRUCT_GET:
            case Bytecode.STRUCT_GET_S:
            case Bytecode.STRUCT_GET_U: {
                final int structTypeIdx = rawPeekI32(bytecode, offset);
                final int fieldIdx = rawPeekI32(bytecode, offset + 4);
                offset += 8;

                final StaticProperty property = module.structTypeAccess(structTypeIdx).properties()[fieldIdx];
                final int fieldType = module.structTypeFieldTypeAt(structTypeIdx, fieldIdx);
                CompilerAsserts.partialEvaluationConstant(fieldType);

                Object struct = WasmFrame.popReference(frame, stackPointer - 1);
                if (struct == WasmConstant.NULL) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.NULL_STRUCTURE_REFERENCE, this);
                }
                switch (fieldType) {
                    case WasmType.I8_TYPE ->
                        WasmFrame.pushInt(frame, stackPointer - 1, aggregateOpcode == Bytecode.STRUCT_GET_S ? property.getByte(struct) : Byte.toUnsignedInt(property.getByte(struct)));
                    case WasmType.I16_TYPE ->
                        WasmFrame.pushInt(frame, stackPointer - 1, aggregateOpcode == Bytecode.STRUCT_GET_S ? property.getShort(struct) : Short.toUnsignedInt(property.getShort(struct)));
                    case WasmType.I32_TYPE -> WasmFrame.pushInt(frame, stackPointer - 1, property.getInt(struct));
                    case WasmType.I64_TYPE -> WasmFrame.pushLong(frame, stackPointer - 1, property.getLong(struct));
                    case WasmType.F32_TYPE -> WasmFrame.pushFloat(frame, stackPointer - 1, property.getFloat(struct));
                    case WasmType.F64_TYPE -> WasmFrame.pushDouble(frame, stackPointer - 1, property.getDouble(struct));
                    case WasmType.V128_TYPE -> WasmFrame.pushVector128(frame, stackPointer - 1, vector128Ops().fromVector128((Vector128) property.getObject(struct)));
                    default -> {
                        assert WasmType.isReferenceType(fieldType);
                        WasmFrame.pushReference(frame, stackPointer - 1, property.getObject(struct));
                    }
                }
                break;
            }
            case Bytecode.STRUCT_SET: {
                final int structTypeIdx = rawPeekI32(bytecode, offset);
                final int fieldIdx = rawPeekI32(bytecode, offset + 4);
                offset += 8;

                final StaticProperty property = module.structTypeAccess(structTypeIdx).properties()[fieldIdx];
                final int fieldType = module.structTypeFieldTypeAt(structTypeIdx, fieldIdx);
                CompilerAsserts.partialEvaluationConstant(fieldType);

                Object struct = WasmFrame.popReference(frame, stackPointer - 2);
                if (struct == WasmConstant.NULL) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.NULL_STRUCTURE_REFERENCE, this);
                }
                switch (fieldType) {
                    case WasmType.I8_TYPE -> property.setByte(struct, (byte) WasmFrame.popInt(frame, stackPointer - 1));
                    case WasmType.I16_TYPE -> property.setShort(struct, (short) WasmFrame.popInt(frame, stackPointer - 1));
                    case WasmType.I32_TYPE -> property.setInt(struct, WasmFrame.popInt(frame, stackPointer - 1));
                    case WasmType.I64_TYPE -> property.setLong(struct, WasmFrame.popLong(frame, stackPointer - 1));
                    case WasmType.F32_TYPE -> property.setFloat(struct, WasmFrame.popFloat(frame, stackPointer - 1));
                    case WasmType.F64_TYPE -> property.setDouble(struct, WasmFrame.popDouble(frame, stackPointer - 1));
                    case WasmType.V128_TYPE -> property.setObject(struct, vector128Ops().toVector128(WasmFrame.popVector128(frame, stackPointer - 1)));
                    default -> {
                        assert WasmType.isReferenceType(fieldType);
                        property.setObject(struct, WasmFrame.popReference(frame, stackPointer - 1));
                    }
                }
                stackPointer -= 2;
                break;
            }
            case Bytecode.ARRAY_NEW: {
                final int arrayTypeIdx = rawPeekI32(bytecode, offset);
                offset += 4;

                final DefinedType arrayType = module.closedTypeAt(arrayTypeIdx);
                final int elemType = module.arrayTypeElemType(arrayTypeIdx);
                CompilerAsserts.partialEvaluationConstant(elemType);

                int length = WasmFrame.popInt(frame, stackPointer - 1);
                if (module.limits().exceedsArrayInstanceSizeLimit(length, elemType)) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.ARRAY_LENGTH_LIMIT_EXCEEDED);
                }
                WasmArray array = switch (elemType) {
                    case WasmType.I8_TYPE -> {
                        byte initialValue = (byte) WasmFrame.popInt(frame, stackPointer - 2);
                        yield new WasmInt8Array(arrayType, length, initialValue);
                    }
                    case WasmType.I16_TYPE -> {
                        short initialValue = (short) WasmFrame.popInt(frame, stackPointer - 2);
                        yield new WasmInt16Array(arrayType, length, initialValue);
                    }
                    case WasmType.I32_TYPE -> {
                        int initialValue = WasmFrame.popInt(frame, stackPointer - 2);
                        yield new WasmInt32Array(arrayType, length, initialValue);
                    }
                    case WasmType.I64_TYPE -> {
                        long initialValue = WasmFrame.popLong(frame, stackPointer - 2);
                        yield new WasmInt64Array(arrayType, length, initialValue);
                    }
                    case WasmType.F32_TYPE -> {
                        float initialValue = WasmFrame.popFloat(frame, stackPointer - 2);
                        yield new WasmFloat32Array(arrayType, length, initialValue);
                    }
                    case WasmType.F64_TYPE -> {
                        double initialValue = WasmFrame.popDouble(frame, stackPointer - 2);
                        yield new WasmFloat64Array(arrayType, length, initialValue);
                    }
                    case WasmType.V128_TYPE -> {
                        V128 initialValue = WasmFrame.popVector128(frame, stackPointer - 2);
                        yield new WasmVec128Array(arrayType, length, initialValue, vector128Ops());
                    }
                    default -> {
                        Object initialValue = WasmFrame.popReference(frame, stackPointer - 2);
                        yield new WasmRefArray(arrayType, length, initialValue);
                    }
                };
                WasmFrame.pushReference(frame, stackPointer - 2, array);
                stackPointer -= 1;
                break;
            }
            case Bytecode.ARRAY_NEW_DEFAULT: {
                final int arrayTypeIdx = rawPeekI32(bytecode, offset);
                offset += 4;

                final DefinedType arrayType = module.closedTypeAt(arrayTypeIdx);
                final int elemType = module.arrayTypeElemType(arrayTypeIdx);
                CompilerAsserts.partialEvaluationConstant(elemType);

                int length = WasmFrame.popInt(frame, stackPointer - 1);
                if (module.limits().exceedsArrayInstanceSizeLimit(length, elemType)) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.ARRAY_LENGTH_LIMIT_EXCEEDED);
                }
                WasmArray array = switch (elemType) {
                    case WasmType.I8_TYPE -> new WasmInt8Array(arrayType, length);
                    case WasmType.I16_TYPE -> new WasmInt16Array(arrayType, length);
                    case WasmType.I32_TYPE -> new WasmInt32Array(arrayType, length);
                    case WasmType.I64_TYPE -> new WasmInt64Array(arrayType, length);
                    case WasmType.F32_TYPE -> new WasmFloat32Array(arrayType, length);
                    case WasmType.F64_TYPE -> new WasmFloat64Array(arrayType, length);
                    case WasmType.V128_TYPE -> new WasmVec128Array(arrayType, length);
                    default -> new WasmRefArray(arrayType, length);
                };
                WasmFrame.pushReference(frame, stackPointer - 1, array);
                break;
            }
            case Bytecode.ARRAY_NEW_DATA: {
                final int arrayTypeIdx = rawPeekI32(bytecode, offset);
                final int dataIdx = rawPeekI32(bytecode, offset + 4);
                offset += 8;

                final DefinedType arrayType = module.closedTypeAt(arrayTypeIdx);
                final int elemType = module.arrayTypeElemType(arrayTypeIdx);
                CompilerAsserts.partialEvaluationConstant(elemType);
                final int dataOffset = instance.dataInstanceOffset(dataIdx);
                final int dataLength = instance.dataInstanceLength(dataIdx);

                int length = WasmFrame.popInt(frame, stackPointer - 1);
                int source = WasmFrame.popInt(frame, stackPointer - 2);
                if (checkOutOfBounds(source, (long) length * WasmType.storageByteSize(elemType), dataLength)) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.OUT_OF_BOUNDS_MEMORY_ACCESS);
                }
                if (module.limits().exceedsArrayInstanceSizeLimit(length, elemType)) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.ARRAY_LENGTH_LIMIT_EXCEEDED);
                }
                WasmArray array = switch (elemType) {
                    case WasmType.I8_TYPE -> new WasmInt8Array(arrayType, length, bytecode, dataOffset + source);
                    case WasmType.I16_TYPE -> new WasmInt16Array(arrayType, length, bytecode, dataOffset + source);
                    case WasmType.I32_TYPE -> new WasmInt32Array(arrayType, length, bytecode, dataOffset + source);
                    case WasmType.I64_TYPE -> new WasmInt64Array(arrayType, length, bytecode, dataOffset + source);
                    case WasmType.F32_TYPE -> new WasmFloat32Array(arrayType, length, bytecode, dataOffset + source);
                    case WasmType.F64_TYPE -> new WasmFloat64Array(arrayType, length, bytecode, dataOffset + source);
                    case WasmType.V128_TYPE -> new WasmVec128Array(arrayType, length, bytecode, dataOffset + source);
                    default -> throw CompilerDirectives.shouldNotReachHere("array with ref element type used in array.new_data");
                };
                WasmFrame.pushReference(frame, stackPointer - 2, array);
                stackPointer -= 1;
                break;
            }
            case Bytecode.ARRAY_NEW_ELEM: {
                final int arrayTypeIdx = rawPeekI32(bytecode, offset);
                final int elemIdx = rawPeekI32(bytecode, offset + 4);
                offset += 8;

                final DefinedType arrayType = module.closedTypeAt(arrayTypeIdx);
                final Object[] elemInstance = instance.elemInstance(elemIdx);

                int length = WasmFrame.popInt(frame, stackPointer - 1);
                int source = WasmFrame.popInt(frame, stackPointer - 2);
                if (checkOutOfBounds(source, length, elemInstance == null ? 0 : elemInstance.length)) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.OUT_OF_BOUNDS_TABLE_ACCESS);
                }
                if (module.limits().exceedsArrayInstanceSizeLimit(length, module.arrayTypeElemType(arrayTypeIdx))) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.ARRAY_LENGTH_LIMIT_EXCEEDED);
                }
                WasmRefArray array = length == 0 ? new WasmRefArray(arrayType, 0) : new WasmRefArray(arrayType, length, elemInstance, source);
                WasmFrame.pushReference(frame, stackPointer - 2, array);
                stackPointer -= 1;
                break;
            }
            case Bytecode.ARRAY_GET:
            case Bytecode.ARRAY_GET_S:
            case Bytecode.ARRAY_GET_U: {
                final int arrayTypeIdx = rawPeekI32(bytecode, offset);
                offset += 4;

                final int elemType = module.arrayTypeElemType(arrayTypeIdx);
                CompilerAsserts.partialEvaluationConstant(elemType);

                int index = WasmFrame.popInt(frame, stackPointer - 1);
                Object array = WasmFrame.popReference(frame, stackPointer - 2);

                if (array == WasmConstant.NULL) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.NULL_ARRAY_REFERENCE, this);
                }
                if (checkOutOfBounds(index, ((WasmArray) array).length())) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.OUT_OF_BOUNDS_ARRAY_ACCESS);
                }
                switch (elemType) {
                    case WasmType.I8_TYPE -> {
                        byte packedValue = ((WasmInt8Array) array).get(index);
                        int unpackedValue = aggregateOpcode == Bytecode.ARRAY_GET_S ? packedValue : Byte.toUnsignedInt(packedValue);
                        WasmFrame.pushInt(frame, stackPointer - 2, unpackedValue);
                    }
                    case WasmType.I16_TYPE -> {
                        short packedValue = ((WasmInt16Array) array).get(index);
                        int unpackedValue = aggregateOpcode == Bytecode.ARRAY_GET_S ? packedValue : Short.toUnsignedInt(packedValue);
                        WasmFrame.pushInt(frame, stackPointer - 2, unpackedValue);
                    }
                    case WasmType.I32_TYPE -> WasmFrame.pushInt(frame, stackPointer - 2, ((WasmInt32Array) array).get(index));
                    case WasmType.I64_TYPE -> WasmFrame.pushLong(frame, stackPointer - 2, ((WasmInt64Array) array).get(index));
                    case WasmType.F32_TYPE -> WasmFrame.pushFloat(frame, stackPointer - 2, ((WasmFloat32Array) array).get(index));
                    case WasmType.F64_TYPE -> WasmFrame.pushDouble(frame, stackPointer - 2, ((WasmFloat64Array) array).get(index));
                    case WasmType.V128_TYPE -> WasmFrame.pushVector128(frame, stackPointer - 2, ((WasmVec128Array) array).get(index, vector128Ops()));
                    default -> WasmFrame.pushReference(frame, stackPointer - 2, ((WasmRefArray) array).get(index));
                }
                stackPointer -= 1;
                break;
            }
            case Bytecode.ARRAY_SET: {
                final int arrayTypeIdx = rawPeekI32(bytecode, offset);
                offset += 4;

                final int elemType = module.arrayTypeElemType(arrayTypeIdx);
                CompilerAsserts.partialEvaluationConstant(elemType);

                int index = WasmFrame.popInt(frame, stackPointer - 2);
                Object array = WasmFrame.popReference(frame, stackPointer - 3);

                if (array == WasmConstant.NULL) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.NULL_ARRAY_REFERENCE, this);
                }
                if (checkOutOfBounds(index, ((WasmArray) array).length())) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.OUT_OF_BOUNDS_ARRAY_ACCESS);
                }
                switch (elemType) {
                    case WasmType.I8_TYPE -> ((WasmInt8Array) array).set(index, (byte) WasmFrame.popInt(frame, stackPointer - 1));
                    case WasmType.I16_TYPE -> ((WasmInt16Array) array).set(index, (short) WasmFrame.popInt(frame, stackPointer - 1));
                    case WasmType.I32_TYPE -> ((WasmInt32Array) array).set(index, WasmFrame.popInt(frame, stackPointer - 1));
                    case WasmType.I64_TYPE -> ((WasmInt64Array) array).set(index, WasmFrame.popLong(frame, stackPointer - 1));
                    case WasmType.F32_TYPE -> ((WasmFloat32Array) array).set(index, WasmFrame.popFloat(frame, stackPointer - 1));
                    case WasmType.F64_TYPE -> ((WasmFloat64Array) array).set(index, WasmFrame.popDouble(frame, stackPointer - 1));
                    case WasmType.V128_TYPE -> ((WasmVec128Array) array).set(index, WasmFrame.popVector128(frame, stackPointer - 1), vector128Ops());
                    default -> ((WasmRefArray) array).set(index, WasmFrame.popReference(frame, stackPointer - 1));
                }
                stackPointer -= 3;
                break;
            }
            case Bytecode.ARRAY_LEN: {
                Object array = WasmFrame.popReference(frame, stackPointer - 1);
                if (array == WasmConstant.NULL) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.NULL_ARRAY_REFERENCE, this);
                }
                WasmFrame.pushInt(frame, stackPointer - 1, ((WasmArray) array).length());
                break;
            }
            case Bytecode.ARRAY_FILL: {
                final int arrayTypeIdx = rawPeekI32(bytecode, offset);
                offset += 4;

                final int elemType = module.arrayTypeElemType(arrayTypeIdx);
                CompilerAsserts.partialEvaluationConstant(elemType);

                int length = WasmFrame.popInt(frame, stackPointer - 1);
                int destination = WasmFrame.popInt(frame, stackPointer - 3);
                Object array = WasmFrame.popReference(frame, stackPointer - 4);

                if (array == WasmConstant.NULL) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.NULL_ARRAY_REFERENCE, this);
                }
                if (checkOutOfBounds(destination, length, ((WasmArray) array).length())) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.OUT_OF_BOUNDS_ARRAY_ACCESS);
                }
                switch (elemType) {
                    case WasmType.I8_TYPE -> {
                        byte fillValue = (byte) WasmFrame.popInt(frame, stackPointer - 2);
                        ((WasmInt8Array) array).fill(destination, length, fillValue);
                    }
                    case WasmType.I16_TYPE -> {
                        short fillValue = (short) WasmFrame.popInt(frame, stackPointer - 2);
                        ((WasmInt16Array) array).fill(destination, length, fillValue);
                    }
                    case WasmType.I32_TYPE -> {
                        int fillValue = WasmFrame.popInt(frame, stackPointer - 2);
                        ((WasmInt32Array) array).fill(destination, length, fillValue);
                    }
                    case WasmType.I64_TYPE -> {
                        long fillValue = WasmFrame.popLong(frame, stackPointer - 2);
                        ((WasmInt64Array) array).fill(destination, length, fillValue);
                    }
                    case WasmType.F32_TYPE -> {
                        float fillValue = WasmFrame.popFloat(frame, stackPointer - 2);
                        ((WasmFloat32Array) array).fill(destination, length, fillValue);
                    }
                    case WasmType.F64_TYPE -> {
                        double fillValue = WasmFrame.popDouble(frame, stackPointer - 2);
                        ((WasmFloat64Array) array).fill(destination, length, fillValue);
                    }
                    case WasmType.V128_TYPE -> {
                        V128 fillValue = WasmFrame.popVector128(frame, stackPointer - 2);
                        ((WasmVec128Array) array).fill(destination, length, fillValue, vector128Ops());
                    }
                    default -> {
                        Object fillValue = WasmFrame.popReference(frame, stackPointer - 2);
                        ((WasmRefArray) array).fill(destination, length, fillValue);
                    }
                }
                stackPointer -= 4;
                break;
            }
            case Bytecode.ARRAY_COPY: {
                final int arrayTypeIdx = rawPeekI32(bytecode, offset);
                offset += 4;

                final int elemType = module.arrayTypeElemType(arrayTypeIdx);
                CompilerAsserts.partialEvaluationConstant(elemType);

                int length = WasmFrame.popInt(frame, stackPointer - 1);
                int source = WasmFrame.popInt(frame, stackPointer - 2);
                Object srcArray = WasmFrame.popReference(frame, stackPointer - 3);
                int destination = WasmFrame.popInt(frame, stackPointer - 4);
                Object dstArray = WasmFrame.popReference(frame, stackPointer - 5);

                if (srcArray == WasmConstant.NULL || dstArray == WasmConstant.NULL) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.NULL_ARRAY_REFERENCE, this);
                }
                if (checkOutOfBounds(destination, length, ((WasmArray) dstArray).length()) || checkOutOfBounds(source, length, ((WasmArray) srcArray).length())) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.OUT_OF_BOUNDS_ARRAY_ACCESS);
                }
                switch (elemType) {
                    case WasmType.I8_TYPE -> ((WasmInt8Array) dstArray).copyFrom((WasmInt8Array) srcArray, source, destination, length);
                    case WasmType.I16_TYPE -> ((WasmInt16Array) dstArray).copyFrom((WasmInt16Array) srcArray, source, destination, length);
                    case WasmType.I32_TYPE -> ((WasmInt32Array) dstArray).copyFrom((WasmInt32Array) srcArray, source, destination, length);
                    case WasmType.I64_TYPE -> ((WasmInt64Array) dstArray).copyFrom((WasmInt64Array) srcArray, source, destination, length);
                    case WasmType.F32_TYPE -> ((WasmFloat32Array) dstArray).copyFrom((WasmFloat32Array) srcArray, source, destination, length);
                    case WasmType.F64_TYPE -> ((WasmFloat64Array) dstArray).copyFrom((WasmFloat64Array) srcArray, source, destination, length);
                    case WasmType.V128_TYPE -> ((WasmVec128Array) dstArray).copyFrom((WasmVec128Array) srcArray, source, destination, length);
                    default -> ((WasmRefArray) dstArray).copyFrom((WasmRefArray) srcArray, source, destination, length);
                }
                stackPointer -= 5;
                break;
            }
            case Bytecode.ARRAY_INIT_DATA: {
                final int arrayTypeIdx = rawPeekI32(bytecode, offset);
                final int dataIdx = rawPeekI32(bytecode, offset + 4);
                offset += 8;

                final int elemType = module.arrayTypeElemType(arrayTypeIdx);
                CompilerAsserts.partialEvaluationConstant(elemType);
                final int dataOffset = instance.dataInstanceOffset(dataIdx);
                final int dataLength = instance.dataInstanceLength(dataIdx);

                int length = WasmFrame.popInt(frame, stackPointer - 1);
                int source = WasmFrame.popInt(frame, stackPointer - 2);
                int destination = WasmFrame.popInt(frame, stackPointer - 3);
                Object array = WasmFrame.popReference(frame, stackPointer - 4);

                if (array == WasmConstant.NULL) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.NULL_ARRAY_REFERENCE, this);
                }
                if (checkOutOfBounds(destination, length, ((WasmArray) array).length())) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.OUT_OF_BOUNDS_ARRAY_ACCESS);
                }
                if (checkOutOfBounds(source, (long) length * WasmType.storageByteSize(elemType), dataLength)) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.OUT_OF_BOUNDS_MEMORY_ACCESS);
                }
                switch (elemType) {
                    case WasmType.I8_TYPE -> ((WasmInt8Array) array).initialize(bytecode, dataOffset + source, destination, length);
                    case WasmType.I16_TYPE -> ((WasmInt16Array) array).initialize(bytecode, dataOffset + source, destination, length);
                    case WasmType.I32_TYPE -> ((WasmInt32Array) array).initialize(bytecode, dataOffset + source, destination, length);
                    case WasmType.I64_TYPE -> ((WasmInt64Array) array).initialize(bytecode, dataOffset + source, destination, length);
                    case WasmType.F32_TYPE -> ((WasmFloat32Array) array).initialize(bytecode, dataOffset + source, destination, length);
                    case WasmType.F64_TYPE -> ((WasmFloat64Array) array).initialize(bytecode, dataOffset + source, destination, length);
                    case WasmType.V128_TYPE -> ((WasmVec128Array) array).initialize(bytecode, dataOffset + source, destination, length);
                    default -> throw CompilerDirectives.shouldNotReachHere("array with ref element type used in array.init_data");
                }
                stackPointer -= 4;
                break;
            }
            case Bytecode.ARRAY_INIT_ELEM: {
                final int elemIdx = rawPeekI32(bytecode, offset);
                offset += 4;

                final Object[] elemInstance = instance.elemInstance(elemIdx);

                int length = WasmFrame.popInt(frame, stackPointer - 1);
                int source = WasmFrame.popInt(frame, stackPointer - 2);
                int destination = WasmFrame.popInt(frame, stackPointer - 3);
                Object array = WasmFrame.popReference(frame, stackPointer - 4);

                if (array == WasmConstant.NULL) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.NULL_ARRAY_REFERENCE, this);
                }
                if (checkOutOfBounds(destination, length, ((WasmArray) array).length())) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.OUT_OF_BOUNDS_ARRAY_ACCESS);
                }
                if (checkOutOfBounds(source, length, elemInstance == null ? 0 : elemInstance.length)) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.OUT_OF_BOUNDS_TABLE_ACCESS);
                }
                if (length > 0) {
                    ((WasmRefArray) array).initialize(elemInstance, source, destination, length);
                }
                stackPointer -= 4;
                break;
            }
            case Bytecode.REF_TEST: {
                final int expectedReferenceType = rawPeekI32(bytecode, offset);
                offset += 4;

                Object ref = WasmFrame.popReference(frame, stackPointer - 1);
                boolean match = runTimeTypeCheck(expectedReferenceType, ref);
                WasmFrame.pushInt(frame, stackPointer - 1, match ? 1 : 0);
                break;
            }
            case Bytecode.REF_CAST: {
                final int expectedReferenceType = rawPeekI32(bytecode, offset);
                offset += 4;

                Object ref = WasmFrame.popReference(frame, stackPointer - 1);
                boolean match = runTimeTypeCheck(expectedReferenceType, ref);
                if (!match) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.CAST_FAILURE, this);
                }
                WasmFrame.pushReference(frame, stackPointer - 1, ref);
                break;
            }
            case Bytecode.BR_ON_CAST_U8:
            case Bytecode.BR_ON_CAST_FAIL_U8: {
                final int expectedReferenceType = rawPeekI32(bytecode, offset);
                final boolean brOnCastFail = aggregateOpcode == Bytecode.BR_ON_CAST_FAIL_U8;

                // Peek at top of stack instead of popping and pushing the same value.
                Object ref = frame.getObjectStatic(stackPointer - 1);
                boolean match = runTimeTypeCheck(expectedReferenceType, ref);
                if (profileCondition(bytecode, offset + 5, match != brOnCastFail)) {
                    final int offsetDelta = rawPeekU8(bytecode, offset + 4);
                    // BR_ON_CAST_U8 encodes the back jump value as a positive byte value.
                    // BR_ON_CAST_U8 can never perform a forward jump.
                    offset = offset + 4 - offsetDelta;
                } else {
                    offset += 7;
                }
                break;
            }
            case Bytecode.BR_ON_CAST_I32:
            case Bytecode.BR_ON_CAST_FAIL_I32: {
                final int expectedReferenceType = rawPeekI32(bytecode, offset);
                final boolean brOnCastFail = aggregateOpcode == Bytecode.BR_ON_CAST_FAIL_I32;

                // Peek at top of stack instead of popping and pushing the same value.
                Object ref = frame.getObjectStatic(stackPointer - 1);
                boolean match = runTimeTypeCheck(expectedReferenceType, ref);
                if (profileCondition(bytecode, offset + 8, match != brOnCastFail)) {
                    final int offsetDelta = rawPeekI32(bytecode, offset + 4);
                    offset = offset + 4 + offsetDelta;
                } else {
                    offset += 10;
                }
                break;
            }
            case Bytecode.REF_I31: {
                int i32 = WasmFrame.popInt(frame, stackPointer - 1);
                Integer i31 = WasmType.asSignedI31(i32);
                WasmFrame.pushReference(frame, stackPointer - 1, i31);
                break;
            }
            case Bytecode.I31_GET_S:
            case Bytecode.I31_GET_U: {
                Object i31 = WasmFrame.popReference(frame, stackPointer - 1);
                if (i31 == WasmConstant.NULL) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.create(Failure.NULL_I31_REFERENCE, this);
                }
                int i32 = (int) i31;
                if (aggregateOpcode == Bytecode.I31_GET_U) {
                    i32 = WasmType.asUnsignedI31(i32);
                }
                WasmFrame.pushInt(frame, stackPointer - 1, i32);
                break;
            }
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }

        assert stackPointer - startingStackPointer == StackEffects.getAggregateOpStackEffect(aggregateOpcode);
        return offset;
    }

    private int executeMisc(WasmInstance instance, VirtualFrame frame, int startingOffset, int startingStackPointer, int miscOpcode) {
        int offset = startingOffset;
        int stackPointer = startingStackPointer;

        CompilerAsserts.partialEvaluationConstant(miscOpcode);
        switch (miscOpcode) {
            case Bytecode.I32_TRUNC_SAT_F32_S:
                i32_trunc_sat_f32_s(frame, stackPointer);
                break;
            case Bytecode.I32_TRUNC_SAT_F32_U:
                i32_trunc_sat_f32_u(frame, stackPointer);
                break;
            case Bytecode.I32_TRUNC_SAT_F64_S:
                i32_trunc_sat_f64_s(frame, stackPointer);
                break;
            case Bytecode.I32_TRUNC_SAT_F64_U:
                i32_trunc_sat_f64_u(frame, stackPointer);
                break;
            case Bytecode.I64_TRUNC_SAT_F32_S:
                i64_trunc_sat_f32_s(frame, stackPointer);
                break;
            case Bytecode.I64_TRUNC_SAT_F32_U:
                i64_trunc_sat_f32_u(frame, stackPointer);
                break;
            case Bytecode.I64_TRUNC_SAT_F64_S:
                i64_trunc_sat_f64_s(frame, stackPointer);
                break;
            case Bytecode.I64_TRUNC_SAT_F64_U:
                i64_trunc_sat_f64_u(frame, stackPointer);
                break;
            case Bytecode.MEMORY_INIT:
            case Bytecode.MEMORY64_INIT: {
                final int dataIndex = rawPeekI32(bytecode, offset);
                final int memoryIndex = rawPeekI32(bytecode, offset + 4);
                executeMemoryInit(instance, frame, stackPointer, miscOpcode, memoryIndex, dataIndex);
                stackPointer -= 3;
                offset += 8;
                break;
            }
            case Bytecode.DATA_DROP: {
                final int dataIndex = rawPeekI32(bytecode, offset);
                data_drop(instance, dataIndex);
                offset += 4;
                break;
            }
            case Bytecode.MEMORY_COPY:
            case Bytecode.MEMORY64_COPY_D64_S64:
            case Bytecode.MEMORY64_COPY_D64_S32:
            case Bytecode.MEMORY64_COPY_D32_S64: {
                final int destMemoryIndex = rawPeekI32(bytecode, offset);
                final int srcMemoryIndex = rawPeekI32(bytecode, offset + 4);
                executeMemoryCopy(instance, frame, stackPointer, miscOpcode, destMemoryIndex, srcMemoryIndex);
                stackPointer -= 3;
                offset += 8;
                break;
            }
            case Bytecode.MEMORY_FILL:
            case Bytecode.MEMORY64_FILL: {
                final int memoryIndex = rawPeekI32(bytecode, offset);
                executeMemoryFill(instance, frame, stackPointer, miscOpcode, memoryIndex);
                stackPointer -= 3;
                offset += 4;
                break;
            }
            case Bytecode.TABLE_INIT: {
                final int elementIndex = rawPeekI32(bytecode, offset);
                final int tableIndex = rawPeekI32(bytecode, offset + 4);

                final int n = popInt(frame, stackPointer - 1);
                final int src = popInt(frame, stackPointer - 2);
                final long dst = popTableIndex(frame, stackPointer - 3, instance.table(tableIndex));
                table_init(instance, n, src, dst, tableIndex, elementIndex);
                stackPointer -= 3;
                offset += 8;
                break;
            }
            case Bytecode.ELEM_DROP: {
                final int elementIndex = rawPeekI32(bytecode, offset);
                instance.dropElemInstance(elementIndex);
                offset += 4;
                break;
            }
            case Bytecode.TABLE_COPY: {
                final int srcIndex = rawPeekI32(bytecode, offset);
                final int dstIndex = rawPeekI32(bytecode, offset + 4);

                final WasmTable sourceTable = instance.table(srcIndex);
                final WasmTable destinationTable = instance.table(dstIndex);
                final long n = popTableCopyLength(frame, stackPointer - 1, sourceTable, destinationTable);
                final long src = popTableIndex(frame, stackPointer - 2, sourceTable);
                final long dst = popTableIndex(frame, stackPointer - 3, destinationTable);
                table_copy(instance, n, src, dst, srcIndex, dstIndex);
                stackPointer -= 3;
                offset += 8;
                break;
            }
            case Bytecode.TABLE_GROW: {
                final int tableIndex = rawPeekI32(bytecode, offset);

                final WasmTable table = instance.table(tableIndex);
                final long n = popTableIndex(frame, stackPointer - 1, table);
                final Object val = popReference(frame, stackPointer - 2);

                final long res = table_grow(instance, n, val, tableIndex);
                pushTableIndex(frame, stackPointer - 2, table, res);
                stackPointer--;
                offset += 4;
                break;
            }
            case Bytecode.TABLE_SIZE: {
                final int tableIndex = rawPeekI32(bytecode, offset);
                table_size(instance, frame, stackPointer, tableIndex);
                stackPointer++;
                offset += 4;
                break;
            }
            case Bytecode.TABLE_FILL: {
                final int tableIndex = rawPeekI32(bytecode, offset);

                final WasmTable table = instance.table(tableIndex);
                final long n = popTableIndex(frame, stackPointer - 1, table);
                final Object val = popReference(frame, stackPointer - 2);
                final long i = popTableIndex(frame, stackPointer - 3, table);
                table_fill(instance, n, val, i, tableIndex);
                stackPointer -= 3;
                offset += 4;
                break;
            }
            case Bytecode.MEMORY64_SIZE: {
                final int memoryIndex = rawPeekI32(bytecode, offset);
                offset += 4;
                final WasmMemory memory = memory(instance, memoryIndex);
                long pageSize = memoryLib(memoryIndex).size(memory);
                pushLong(frame, stackPointer, pageSize);
                stackPointer++;
                break;
            }
            case Bytecode.MEMORY64_GROW: {
                final int memoryIndex = rawPeekI32(bytecode, offset);
                offset += 4;
                final WasmMemory memory = memory(instance, memoryIndex);
                long extraSize = popLong(frame, stackPointer - 1);
                long previousSize = memoryLib(memoryIndex).grow(memory, extraSize);
                pushLong(frame, stackPointer - 1, previousSize);
                break;
            }
            case Bytecode.TABLE_GET: {
                final int tableIndex = rawPeekI32(bytecode, offset);
                table_get(instance, frame, stackPointer, tableIndex);
                offset += 4;
                break;
            }
            case Bytecode.TABLE_SET: {
                final int tableIndex = rawPeekI32(bytecode, offset);
                table_set(instance, frame, stackPointer, tableIndex);
                stackPointer -= 2;
                offset += 4;
                break;
            }
            case Bytecode.REF_AS_NON_NULL: {
                Object reference = popReference(frame, stackPointer - 1);
                if (reference == WasmConstant.NULL) {
                    enterErrorBranch(codeEntry);
                    throw WasmException.format(Failure.NULL_REFERENCE, this, "Function reference is null");
                }
                pushReference(frame, stackPointer - 1, reference);
                break;
            }
            case Bytecode.REF_EQ: {
                Object ref1 = popReference(frame, stackPointer - 1);
                Object ref2 = popReference(frame, stackPointer - 2);
                boolean equal = ref1 == ref2 || ref1 instanceof Integer int1 && ref2 instanceof Integer int2 && int1.intValue() == int2.intValue();
                pushInt(frame, stackPointer - 2, equal ? 1 : 0);
                stackPointer -= 1;
                break;
            }
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }

        assert stackPointer - startingStackPointer == StackEffects.getMiscOpStackEffect(miscOpcode);
        return offset;
    }

    private int executeAtomic(VirtualFrame frame, int stackPointer, int opcode, WasmMemory memory, WasmMemoryLibrary memoryLib, long memOffset, int indexType64) {
        switch (opcode) {
            case Bytecode.ATOMIC_NOTIFY:
            case Bytecode.ATOMIC_I32_RMW_ADD:
            case Bytecode.ATOMIC_I64_RMW_ADD:
            case Bytecode.ATOMIC_I32_RMW8_U_ADD:
            case Bytecode.ATOMIC_I32_RMW16_U_ADD:
            case Bytecode.ATOMIC_I64_RMW8_U_ADD:
            case Bytecode.ATOMIC_I64_RMW16_U_ADD:
            case Bytecode.ATOMIC_I64_RMW32_U_ADD:
            case Bytecode.ATOMIC_I32_RMW_SUB:
            case Bytecode.ATOMIC_I64_RMW_SUB:
            case Bytecode.ATOMIC_I32_RMW8_U_SUB:
            case Bytecode.ATOMIC_I32_RMW16_U_SUB:
            case Bytecode.ATOMIC_I64_RMW8_U_SUB:
            case Bytecode.ATOMIC_I64_RMW16_U_SUB:
            case Bytecode.ATOMIC_I64_RMW32_U_SUB:
            case Bytecode.ATOMIC_I32_RMW_AND:
            case Bytecode.ATOMIC_I64_RMW_AND:
            case Bytecode.ATOMIC_I32_RMW8_U_AND:
            case Bytecode.ATOMIC_I32_RMW16_U_AND:
            case Bytecode.ATOMIC_I64_RMW8_U_AND:
            case Bytecode.ATOMIC_I64_RMW16_U_AND:
            case Bytecode.ATOMIC_I64_RMW32_U_AND:
            case Bytecode.ATOMIC_I32_RMW_OR:
            case Bytecode.ATOMIC_I64_RMW_OR:
            case Bytecode.ATOMIC_I32_RMW8_U_OR:
            case Bytecode.ATOMIC_I32_RMW16_U_OR:
            case Bytecode.ATOMIC_I64_RMW8_U_OR:
            case Bytecode.ATOMIC_I64_RMW16_U_OR:
            case Bytecode.ATOMIC_I64_RMW32_U_OR:
            case Bytecode.ATOMIC_I32_RMW_XOR:
            case Bytecode.ATOMIC_I64_RMW_XOR:
            case Bytecode.ATOMIC_I32_RMW8_U_XOR:
            case Bytecode.ATOMIC_I32_RMW16_U_XOR:
            case Bytecode.ATOMIC_I64_RMW8_U_XOR:
            case Bytecode.ATOMIC_I64_RMW16_U_XOR:
            case Bytecode.ATOMIC_I64_RMW32_U_XOR:
            case Bytecode.ATOMIC_I32_RMW_XCHG:
            case Bytecode.ATOMIC_I64_RMW_XCHG:
            case Bytecode.ATOMIC_I32_RMW8_U_XCHG:
            case Bytecode.ATOMIC_I32_RMW16_U_XCHG:
            case Bytecode.ATOMIC_I64_RMW8_U_XCHG:
            case Bytecode.ATOMIC_I64_RMW16_U_XCHG:
            case Bytecode.ATOMIC_I64_RMW32_U_XCHG: {
                final long baseAddress;
                if (indexType64 == 0) {
                    baseAddress = popInt(frame, stackPointer - 2);
                } else {
                    baseAddress = popLong(frame, stackPointer - 2);
                }
                final long address = effectiveMemoryAddress64(memOffset, baseAddress, this);
                executeAtomicAtAddress(memory, memoryLib, frame, stackPointer - 1, opcode, address);
                return 1;
            }
            case Bytecode.ATOMIC_WAIT32:
            case Bytecode.ATOMIC_WAIT64:
            case Bytecode.ATOMIC_I32_RMW_CMPXCHG:
            case Bytecode.ATOMIC_I64_RMW_CMPXCHG:
            case Bytecode.ATOMIC_I32_RMW8_U_CMPXCHG:
            case Bytecode.ATOMIC_I32_RMW16_U_CMPXCHG:
            case Bytecode.ATOMIC_I64_RMW8_U_CMPXCHG:
            case Bytecode.ATOMIC_I64_RMW16_U_CMPXCHG:
            case Bytecode.ATOMIC_I64_RMW32_U_CMPXCHG: {
                final long baseAddress;
                if (indexType64 == 0) {
                    baseAddress = popInt(frame, stackPointer - 3);
                } else {
                    baseAddress = popLong(frame, stackPointer - 3);
                }
                final long address = effectiveMemoryAddress64(memOffset, baseAddress, this);
                executeAtomicAtAddress(memory, memoryLib, frame, stackPointer - 1, opcode, address);
                return 2;
            }
            case Bytecode.ATOMIC_I32_LOAD:
            case Bytecode.ATOMIC_I64_LOAD:
            case Bytecode.ATOMIC_I32_LOAD8_U:
            case Bytecode.ATOMIC_I32_LOAD16_U:
            case Bytecode.ATOMIC_I64_LOAD8_U:
            case Bytecode.ATOMIC_I64_LOAD16_U:
            case Bytecode.ATOMIC_I64_LOAD32_U: {
                final long baseAddress;
                if (indexType64 == 0) {
                    baseAddress = popInt(frame, stackPointer - 1);
                } else {
                    baseAddress = popLong(frame, stackPointer - 1);
                }
                final long address = effectiveMemoryAddress64(memOffset, baseAddress, this);
                executeAtomicAtAddress(memory, memoryLib, frame, stackPointer - 1, opcode, address);
                return 0;
            }
            case Bytecode.ATOMIC_I32_STORE:
            case Bytecode.ATOMIC_I64_STORE:
            case Bytecode.ATOMIC_I32_STORE8:
            case Bytecode.ATOMIC_I32_STORE16:
            case Bytecode.ATOMIC_I64_STORE8:
            case Bytecode.ATOMIC_I64_STORE16:
            case Bytecode.ATOMIC_I64_STORE32: {
                final long baseAddress;
                if (indexType64 == 0) {
                    baseAddress = popInt(frame, stackPointer - 2);
                } else {
                    baseAddress = popLong(frame, stackPointer - 2);
                }
                final long address = effectiveMemoryAddress64(memOffset, baseAddress, this);
                executeAtomicAtAddress(memory, memoryLib, frame, stackPointer - 1, opcode, address);
                return 2;
            }
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private void executeAtomicAtAddress(WasmMemory memory, WasmMemoryLibrary memoryLib, VirtualFrame frame, int stackPointer, int opcode, long address) {
        switch (opcode) {
            case Bytecode.ATOMIC_NOTIFY: {
                final int count = popInt(frame, stackPointer);
                final int waitersNotified = memoryLib.atomic_notify(memory, this, address, count);
                pushInt(frame, stackPointer - 1, waitersNotified);
                break;
            }
            case Bytecode.ATOMIC_WAIT32: {
                final long timeout = popLong(frame, stackPointer);
                final int expected = popInt(frame, stackPointer - 1);
                final int status = memoryLib.atomic_wait32(memory, this, address, expected, timeout);
                pushInt(frame, stackPointer - 2, status);
                break;
            }
            case Bytecode.ATOMIC_WAIT64: {
                final long timeout = popLong(frame, stackPointer);
                final long expected = popLong(frame, stackPointer - 1);
                final int status = memoryLib.atomic_wait64(memory, this, address, expected, timeout);
                pushInt(frame, stackPointer - 2, status);
                break;
            }
            case Bytecode.ATOMIC_I32_LOAD: {
                final int value = memoryLib.atomic_load_i32(memory, this, address);
                pushInt(frame, stackPointer, value);
                break;
            }
            case Bytecode.ATOMIC_I64_LOAD: {
                final long value = memoryLib.atomic_load_i64(memory, this, address);
                pushLong(frame, stackPointer, value);
                break;
            }
            case Bytecode.ATOMIC_I32_LOAD8_U: {
                final int value = memoryLib.atomic_load_i32_8u(memory, this, address);
                pushInt(frame, stackPointer, value);
                break;
            }
            case Bytecode.ATOMIC_I32_LOAD16_U: {
                final int value = memoryLib.atomic_load_i32_16u(memory, this, address);
                pushInt(frame, stackPointer, value);
                break;
            }
            case Bytecode.ATOMIC_I64_LOAD8_U: {
                final long value = memoryLib.atomic_load_i64_8u(memory, this, address);
                pushLong(frame, stackPointer, value);
                break;
            }
            case Bytecode.ATOMIC_I64_LOAD16_U: {
                final long value = memoryLib.atomic_load_i64_16u(memory, this, address);
                pushLong(frame, stackPointer, value);
                break;
            }
            case Bytecode.ATOMIC_I64_LOAD32_U: {
                final long value = memoryLib.atomic_load_i64_32u(memory, this, address);
                pushLong(frame, stackPointer, value);
                break;
            }
            case Bytecode.ATOMIC_I32_STORE: {
                final int value = popInt(frame, stackPointer);
                memoryLib.atomic_store_i32(memory, this, address, value);
                break;
            }
            case Bytecode.ATOMIC_I64_STORE: {
                final long value = popLong(frame, stackPointer);
                memoryLib.atomic_store_i64(memory, this, address, value);
                break;
            }
            case Bytecode.ATOMIC_I32_STORE8: {
                final int value = popInt(frame, stackPointer);
                memoryLib.atomic_store_i32_8(memory, this, address, (byte) value);
                break;
            }
            case Bytecode.ATOMIC_I32_STORE16: {
                final int value = popInt(frame, stackPointer);
                memoryLib.atomic_store_i32_16(memory, this, address, (short) value);
                break;
            }
            case Bytecode.ATOMIC_I64_STORE8: {
                final long value = popLong(frame, stackPointer);
                memoryLib.atomic_store_i64_8(memory, this, address, (byte) value);
                break;
            }
            case Bytecode.ATOMIC_I64_STORE16: {
                final long value = popLong(frame, stackPointer);
                memoryLib.atomic_store_i64_16(memory, this, address, (short) value);
                break;
            }
            case Bytecode.ATOMIC_I64_STORE32: {
                final long value = popLong(frame, stackPointer);
                memoryLib.atomic_store_i64_32(memory, this, address, (int) value);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW_ADD: {
                final int value = popInt(frame, stackPointer);
                final int result = memoryLib.atomic_rmw_add_i32(memory, this, address, value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW_ADD: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_add_i64(memory, this, address, value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW8_U_ADD: {
                final int value = popInt(frame, stackPointer);
                final int result = memoryLib.atomic_rmw_add_i32_8u(memory, this, address, (byte) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW16_U_ADD: {
                final int value = popInt(frame, stackPointer);
                final int result = memoryLib.atomic_rmw_add_i32_16u(memory, this, address, (short) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW8_U_ADD: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_add_i64_8u(memory, this, address, (byte) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW16_U_ADD: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_add_i64_16u(memory, this, address, (short) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW32_U_ADD: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_add_i64_32u(memory, this, address, (int) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW_SUB: {
                final int value = popInt(frame, stackPointer);
                final int result = memoryLib.atomic_rmw_sub_i32(memory, this, address, value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW_SUB: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_sub_i64(memory, this, address, value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW8_U_SUB: {
                final int value = popInt(frame, stackPointer);
                final int result = memoryLib.atomic_rmw_sub_i32_8u(memory, this, address, (byte) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW16_U_SUB: {
                final int value = popInt(frame, stackPointer);
                final int result = memoryLib.atomic_rmw_sub_i32_16u(memory, this, address, (short) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW8_U_SUB: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_sub_i64_8u(memory, this, address, (byte) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW16_U_SUB: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_sub_i64_16u(memory, this, address, (short) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW32_U_SUB: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_sub_i64_32u(memory, this, address, (int) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW_AND: {
                final int value = popInt(frame, stackPointer);
                final int result = memoryLib.atomic_rmw_and_i32(memory, this, address, value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW_AND: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_and_i64(memory, this, address, value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW8_U_AND: {
                final int value = popInt(frame, stackPointer);
                final int result = memoryLib.atomic_rmw_and_i32_8u(memory, this, address, (byte) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW16_U_AND: {
                final int value = popInt(frame, stackPointer);
                final int result = memoryLib.atomic_rmw_and_i32_16u(memory, this, address, (short) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW8_U_AND: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_and_i64_8u(memory, this, address, (byte) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW16_U_AND: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_and_i64_16u(memory, this, address, (short) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW32_U_AND: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_and_i64_32u(memory, this, address, (int) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW_OR: {
                final int value = popInt(frame, stackPointer);
                final int result = memoryLib.atomic_rmw_or_i32(memory, this, address, value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW_OR: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_or_i64(memory, this, address, value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW8_U_OR: {
                final int value = popInt(frame, stackPointer);
                final int result = memoryLib.atomic_rmw_or_i32_8u(memory, this, address, (byte) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW16_U_OR: {
                final int value = popInt(frame, stackPointer);
                final int result = memoryLib.atomic_rmw_or_i32_16u(memory, this, address, (short) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW8_U_OR: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_or_i64_8u(memory, this, address, (byte) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW16_U_OR: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_or_i64_16u(memory, this, address, (short) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW32_U_OR: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_or_i64_32u(memory, this, address, (int) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW_XOR: {
                final int value = popInt(frame, stackPointer);
                final int result = memoryLib.atomic_rmw_xor_i32(memory, this, address, value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW_XOR: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_xor_i64(memory, this, address, value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW8_U_XOR: {
                final int value = popInt(frame, stackPointer);
                final int result = memoryLib.atomic_rmw_xor_i32_8u(memory, this, address, (byte) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW16_U_XOR: {
                final int value = popInt(frame, stackPointer);
                final int result = memoryLib.atomic_rmw_xor_i32_16u(memory, this, address, (short) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW8_U_XOR: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_xor_i64_8u(memory, this, address, (byte) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW16_U_XOR: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_xor_i64_16u(memory, this, address, (short) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW32_U_XOR: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_xor_i64_32u(memory, this, address, (int) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW_XCHG: {
                final int value = popInt(frame, stackPointer);
                final int result = memoryLib.atomic_rmw_xchg_i32(memory, this, address, value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW_XCHG: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_xchg_i64(memory, this, address, value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW8_U_XCHG: {
                final int value = popInt(frame, stackPointer);
                final int result = memoryLib.atomic_rmw_xchg_i32_8u(memory, this, address, (byte) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW16_U_XCHG: {
                final int value = popInt(frame, stackPointer);
                final int result = memoryLib.atomic_rmw_xchg_i32_16u(memory, this, address, (short) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW8_U_XCHG: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_xchg_i64_8u(memory, this, address, (byte) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW16_U_XCHG: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_xchg_i64_16u(memory, this, address, (short) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW32_U_XCHG: {
                final long value = popLong(frame, stackPointer);
                final long result = memoryLib.atomic_rmw_xchg_i64_32u(memory, this, address, (int) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW_CMPXCHG: {
                final int replacement = popInt(frame, stackPointer);
                final int expected = popInt(frame, stackPointer - 1);
                final int result = memoryLib.atomic_rmw_cmpxchg_i32(memory, this, address, expected, replacement);
                pushInt(frame, stackPointer - 2, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW_CMPXCHG: {
                final long replacement = popLong(frame, stackPointer);
                final long expected = popLong(frame, stackPointer - 1);
                final long result = memoryLib.atomic_rmw_cmpxchg_i64(memory, this, address, expected, replacement);
                pushLong(frame, stackPointer - 2, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW8_U_CMPXCHG: {
                final int replacement = popInt(frame, stackPointer);
                final int expected = popInt(frame, stackPointer - 1);
                final int result = memoryLib.atomic_rmw_cmpxchg_i32_8u(memory, this, address, (byte) expected, (byte) replacement);
                pushInt(frame, stackPointer - 2, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW16_U_CMPXCHG: {
                final int replacement = popInt(frame, stackPointer);
                final int expected = popInt(frame, stackPointer - 1);
                final int result = memoryLib.atomic_rmw_cmpxchg_i32_16u(memory, this, address, (short) expected, (short) replacement);
                pushInt(frame, stackPointer - 2, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW8_U_CMPXCHG: {
                final long replacement = popLong(frame, stackPointer);
                final long expected = popLong(frame, stackPointer - 1);
                final long result = memoryLib.atomic_rmw_cmpxchg_i64_8u(memory, this, address, (byte) expected, (byte) replacement);
                pushLong(frame, stackPointer - 2, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW16_U_CMPXCHG: {
                final long replacement = popLong(frame, stackPointer);
                final long expected = popLong(frame, stackPointer - 1);
                final long result = memoryLib.atomic_rmw_cmpxchg_i64_16u(memory, this, address, (short) expected, (short) replacement);
                pushLong(frame, stackPointer - 2, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW32_U_CMPXCHG: {
                final long replacement = popLong(frame, stackPointer);
                final long expected = popLong(frame, stackPointer - 1);
                final long result = memoryLib.atomic_rmw_cmpxchg_i64_32u(memory, this, address, (int) expected, (int) replacement);
                pushLong(frame, stackPointer - 2, result);
                break;
            }
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @SuppressWarnings("hiding")
    private int executeVector(WasmInstance instance, VirtualFrame frame, int startingOffset, int startingStackPointer, int vectorOpcode) {
        final byte[] bytecode = this.bytecode;

        int offset = startingOffset;
        int stackPointer = startingStackPointer;

        switch (vectorOpcode) {
            case Bytecode.VECTOR_V128_LOAD:
            case Bytecode.VECTOR_V128_LOAD8X8_S:
            case Bytecode.VECTOR_V128_LOAD8X8_U:
            case Bytecode.VECTOR_V128_LOAD16X4_S:
            case Bytecode.VECTOR_V128_LOAD16X4_U:
            case Bytecode.VECTOR_V128_LOAD32X2_S:
            case Bytecode.VECTOR_V128_LOAD32X2_U:
            case Bytecode.VECTOR_V128_LOAD8_SPLAT:
            case Bytecode.VECTOR_V128_LOAD16_SPLAT:
            case Bytecode.VECTOR_V128_LOAD32_SPLAT:
            case Bytecode.VECTOR_V128_LOAD64_SPLAT:
            case Bytecode.VECTOR_V128_LOAD32_ZERO:
            case Bytecode.VECTOR_V128_LOAD64_ZERO: {
                final int encoding = rawPeekU8(bytecode, offset);
                offset++;
                final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
                final int memoryIndex = rawPeekI32(bytecode, offset);
                offset += 4;
                final long memOffset;
                if (indexType64 == 0) {
                    memOffset = rawPeekU32(bytecode, offset);
                    offset += 4;
                } else {
                    memOffset = rawPeekI64(bytecode, offset);
                    offset += 8;
                }
                final long baseAddress;
                if (indexType64 == 0) {
                    baseAddress = Integer.toUnsignedLong(popInt(frame, --stackPointer));
                } else {
                    baseAddress = popLong(frame, --stackPointer);
                }
                final long address = effectiveMemoryAddress64(memOffset, baseAddress, this);
                final WasmMemory memory = memory(instance, memoryIndex);
                loadVector(memory, memoryLib(memoryIndex), frame, stackPointer++, vectorOpcode, address);
                break;
            }
            case Bytecode.VECTOR_V128_STORE: {
                final int encoding = rawPeekU8(bytecode, offset);
                offset++;
                final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
                final int memoryIndex = rawPeekI32(bytecode, offset);
                offset += 4;
                final long memOffset;
                if (indexType64 == 0) {
                    memOffset = rawPeekU32(bytecode, offset);
                    offset += 4;
                } else {
                    memOffset = rawPeekI64(bytecode, offset);
                    offset += 8;
                }
                final V128 value = popVector128(frame, --stackPointer);
                final long baseAddress;
                if (indexType64 == 0) {
                    baseAddress = Integer.toUnsignedLong(popInt(frame, --stackPointer));
                } else {
                    baseAddress = popLong(frame, --stackPointer);
                }
                final long address = effectiveMemoryAddress64(memOffset, baseAddress, this);
                final WasmMemory memory = memory(instance, memoryIndex);
                storeVector(memory, memoryLib(memoryIndex), address, value);
                break;
            }
            case Bytecode.VECTOR_V128_LOAD8_LANE:
            case Bytecode.VECTOR_V128_LOAD16_LANE:
            case Bytecode.VECTOR_V128_LOAD32_LANE:
            case Bytecode.VECTOR_V128_LOAD64_LANE: {
                final int encoding = rawPeekU8(bytecode, offset);
                offset++;
                final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
                final int memoryIndex = rawPeekI32(bytecode, offset);
                offset += 4;
                final long memOffset;
                if (indexType64 == 0) {
                    memOffset = rawPeekU32(bytecode, offset);
                    offset += 4;
                } else {
                    memOffset = rawPeekI64(bytecode, offset);
                    offset += 8;
                }
                final int laneIndex = rawPeekU8(bytecode, offset);
                offset++;
                final V128 vec = popVector128(frame, --stackPointer);
                final long baseAddress;
                if (indexType64 == 0) {
                    baseAddress = Integer.toUnsignedLong(popInt(frame, --stackPointer));
                } else {
                    baseAddress = popLong(frame, --stackPointer);
                }
                final long address = effectiveMemoryAddress64(memOffset, baseAddress, this);
                final WasmMemory memory = memory(instance, memoryIndex);
                loadVectorLane(memory, memoryLib(memoryIndex), frame, stackPointer++, vectorOpcode, address, laneIndex, vec);
                break;
            }
            case Bytecode.VECTOR_V128_STORE8_LANE:
            case Bytecode.VECTOR_V128_STORE16_LANE:
            case Bytecode.VECTOR_V128_STORE32_LANE:
            case Bytecode.VECTOR_V128_STORE64_LANE: {
                final int encoding = rawPeekU8(bytecode, offset);
                offset++;
                final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
                final int memoryIndex = rawPeekI32(bytecode, offset);
                offset += 4;
                final long memOffset;
                if (indexType64 == 0) {
                    memOffset = rawPeekU32(bytecode, offset);
                    offset += 4;
                } else {
                    memOffset = rawPeekI64(bytecode, offset);
                    offset += 8;
                }
                final int laneIndex = rawPeekU8(bytecode, offset);
                offset++;
                final V128 vec = popVector128(frame, --stackPointer);
                final long baseAddress;
                if (indexType64 == 0) {
                    baseAddress = Integer.toUnsignedLong(popInt(frame, --stackPointer));
                } else {
                    baseAddress = popLong(frame, --stackPointer);
                }
                final long address = effectiveMemoryAddress64(memOffset, baseAddress, this);
                final WasmMemory memory = memory(instance, memoryIndex);
                storeVectorLane(memory, memoryLib(memoryIndex), vectorOpcode, address, laneIndex, vec);
                break;
            }
            case Bytecode.VECTOR_V128_CONST: {
                final V128 vector = vector128Ops().fromArray(bytecode, offset);
                offset += 16;

                pushVector128(frame, stackPointer++, vector);
                break;
            }
            case Bytecode.VECTOR_I8X16_SHUFFLE: {
                final V128 indices = vector128Ops().fromArray(bytecode, offset);
                offset += 16;

                V128 y = popVector128(frame, --stackPointer);
                V128 x = popVector128(frame, --stackPointer);
                V128 result = vector128Ops().i8x16_shuffle(x, y, indices);
                pushVector128(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_I8X16_EXTRACT_LANE_S:
            case Bytecode.VECTOR_I8X16_EXTRACT_LANE_U: {
                final int laneIndex = rawPeekU8(bytecode, offset);
                offset++;

                V128 vec = popVector128(frame, --stackPointer);
                int result = vector128Ops().i8x16_extract_lane(vec, laneIndex, vectorOpcode);
                pushInt(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_I8X16_REPLACE_LANE: {
                final int laneIndex = rawPeekU8(bytecode, offset);
                offset++;

                byte value = (byte) popInt(frame, --stackPointer);
                V128 vec = popVector128(frame, --stackPointer);
                V128 result = vector128Ops().i8x16_replace_lane(vec, laneIndex, value);
                pushVector128(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_I16X8_EXTRACT_LANE_S:
            case Bytecode.VECTOR_I16X8_EXTRACT_LANE_U: {
                final int laneIndex = rawPeekU8(bytecode, offset);
                offset++;

                V128 vec = popVector128(frame, --stackPointer);
                int result = vector128Ops().i16x8_extract_lane(vec, laneIndex, vectorOpcode);
                pushInt(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_I16X8_REPLACE_LANE: {
                final int laneIndex = rawPeekU8(bytecode, offset);
                offset++;

                short value = (short) popInt(frame, --stackPointer);
                V128 vec = popVector128(frame, --stackPointer);
                V128 result = vector128Ops().i16x8_replace_lane(vec, laneIndex, value);
                pushVector128(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_I32X4_EXTRACT_LANE: {
                final int laneIndex = rawPeekU8(bytecode, offset);
                offset++;

                V128 vec = popVector128(frame, --stackPointer);
                int result = vector128Ops().i32x4_extract_lane(vec, laneIndex);
                pushInt(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_I32X4_REPLACE_LANE: {
                final int laneIndex = rawPeekU8(bytecode, offset);
                offset++;

                int value = popInt(frame, --stackPointer);
                V128 vec = popVector128(frame, --stackPointer);
                V128 result = vector128Ops().i32x4_replace_lane(vec, laneIndex, value);
                pushVector128(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_I64X2_EXTRACT_LANE: {
                final int laneIndex = rawPeekU8(bytecode, offset);
                offset++;

                V128 vec = popVector128(frame, --stackPointer);
                long result = vector128Ops().i64x2_extract_lane(vec, laneIndex);
                pushLong(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_I64X2_REPLACE_LANE: {
                final int laneIndex = rawPeekU8(bytecode, offset);
                offset++;

                long value = popLong(frame, --stackPointer);
                V128 vec = popVector128(frame, --stackPointer);
                V128 result = vector128Ops().i64x2_replace_lane(vec, laneIndex, value);
                pushVector128(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_F32X4_EXTRACT_LANE: {
                final int laneIndex = rawPeekU8(bytecode, offset);
                offset++;

                V128 vec = popVector128(frame, --stackPointer);
                float result = vector128Ops().f32x4_extract_lane(vec, laneIndex);
                pushFloat(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_F32X4_REPLACE_LANE: {
                final int laneIndex = rawPeekU8(bytecode, offset);
                offset++;

                float value = popFloat(frame, --stackPointer);
                V128 vec = popVector128(frame, --stackPointer);
                V128 result = vector128Ops().f32x4_replace_lane(vec, laneIndex, value);
                pushVector128(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_F64X2_EXTRACT_LANE: {
                final int laneIndex = rawPeekU8(bytecode, offset);
                offset++;

                V128 vec = popVector128(frame, --stackPointer);
                double result = vector128Ops().f64x2_extract_lane(vec, laneIndex);
                pushDouble(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_F64X2_REPLACE_LANE: {
                final int laneIndex = rawPeekU8(bytecode, offset);
                offset++;

                double value = popDouble(frame, --stackPointer);
                V128 vec = popVector128(frame, --stackPointer);
                V128 result = vector128Ops().f64x2_replace_lane(vec, laneIndex, value);
                pushVector128(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_V128_NOT:
            case Bytecode.VECTOR_I8X16_ABS:
            case Bytecode.VECTOR_I8X16_NEG:
            case Bytecode.VECTOR_I8X16_POPCNT:
            case Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_S:
            case Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_U:
            case Bytecode.VECTOR_I16X8_EXTEND_LOW_I8X16_S:
            case Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8X16_S:
            case Bytecode.VECTOR_I16X8_EXTEND_LOW_I8X16_U:
            case Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8X16_U:
            case Bytecode.VECTOR_I16X8_ABS:
            case Bytecode.VECTOR_I16X8_NEG:
            case Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_S:
            case Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_U:
            case Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_S:
            case Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_S:
            case Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_U:
            case Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_U:
            case Bytecode.VECTOR_I32X4_ABS:
            case Bytecode.VECTOR_I32X4_NEG:
            case Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_S:
            case Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_S:
            case Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_U:
            case Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_U:
            case Bytecode.VECTOR_I64X2_ABS:
            case Bytecode.VECTOR_I64X2_NEG:
            case Bytecode.VECTOR_F32X4_CEIL:
            case Bytecode.VECTOR_F32X4_FLOOR:
            case Bytecode.VECTOR_F32X4_TRUNC:
            case Bytecode.VECTOR_F32X4_NEAREST:
            case Bytecode.VECTOR_F32X4_ABS:
            case Bytecode.VECTOR_F32X4_NEG:
            case Bytecode.VECTOR_F32X4_SQRT:
            case Bytecode.VECTOR_F64X2_CEIL:
            case Bytecode.VECTOR_F64X2_FLOOR:
            case Bytecode.VECTOR_F64X2_TRUNC:
            case Bytecode.VECTOR_F64X2_NEAREST:
            case Bytecode.VECTOR_F64X2_ABS:
            case Bytecode.VECTOR_F64X2_NEG:
            case Bytecode.VECTOR_F64X2_SQRT:
            case Bytecode.VECTOR_I32X4_TRUNC_SAT_F32X4_S:
            case Bytecode.VECTOR_I32X4_TRUNC_SAT_F32X4_U:
            case Bytecode.VECTOR_F32X4_CONVERT_I32X4_S:
            case Bytecode.VECTOR_F32X4_CONVERT_I32X4_U:
            case Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_S_ZERO:
            case Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_U_ZERO:
            case Bytecode.VECTOR_F64X2_CONVERT_LOW_I32X4_S:
            case Bytecode.VECTOR_F64X2_CONVERT_LOW_I32X4_U:
            case Bytecode.VECTOR_F32X4_DEMOTE_F64X2_ZERO:
            case Bytecode.VECTOR_F64X2_PROMOTE_LOW_F32X4:
            case Bytecode.VECTOR_I32X4_RELAXED_TRUNC_F32X4_S:
            case Bytecode.VECTOR_I32X4_RELAXED_TRUNC_F32X4_U:
            case Bytecode.VECTOR_I32X4_RELAXED_TRUNC_F64X2_S_ZERO:
            case Bytecode.VECTOR_I32X4_RELAXED_TRUNC_F64X2_U_ZERO: {
                V128 x = popVector128(frame, --stackPointer);
                V128 result = vector128Ops().unary(x, vectorOpcode);
                pushVector128(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_I8X16_SWIZZLE:
            case Bytecode.VECTOR_I8X16_EQ:
            case Bytecode.VECTOR_I8X16_NE:
            case Bytecode.VECTOR_I8X16_LT_S:
            case Bytecode.VECTOR_I8X16_LT_U:
            case Bytecode.VECTOR_I8X16_GT_S:
            case Bytecode.VECTOR_I8X16_GT_U:
            case Bytecode.VECTOR_I8X16_LE_S:
            case Bytecode.VECTOR_I8X16_LE_U:
            case Bytecode.VECTOR_I8X16_GE_S:
            case Bytecode.VECTOR_I8X16_GE_U:
            case Bytecode.VECTOR_I16X8_EQ:
            case Bytecode.VECTOR_I16X8_NE:
            case Bytecode.VECTOR_I16X8_LT_S:
            case Bytecode.VECTOR_I16X8_LT_U:
            case Bytecode.VECTOR_I16X8_GT_S:
            case Bytecode.VECTOR_I16X8_GT_U:
            case Bytecode.VECTOR_I16X8_LE_S:
            case Bytecode.VECTOR_I16X8_LE_U:
            case Bytecode.VECTOR_I16X8_GE_S:
            case Bytecode.VECTOR_I16X8_GE_U:
            case Bytecode.VECTOR_I32X4_EQ:
            case Bytecode.VECTOR_I32X4_NE:
            case Bytecode.VECTOR_I32X4_LT_S:
            case Bytecode.VECTOR_I32X4_LT_U:
            case Bytecode.VECTOR_I32X4_GT_S:
            case Bytecode.VECTOR_I32X4_GT_U:
            case Bytecode.VECTOR_I32X4_LE_S:
            case Bytecode.VECTOR_I32X4_LE_U:
            case Bytecode.VECTOR_I32X4_GE_S:
            case Bytecode.VECTOR_I32X4_GE_U:
            case Bytecode.VECTOR_I64X2_EQ:
            case Bytecode.VECTOR_I64X2_NE:
            case Bytecode.VECTOR_I64X2_LT_S:
            case Bytecode.VECTOR_I64X2_GT_S:
            case Bytecode.VECTOR_I64X2_LE_S:
            case Bytecode.VECTOR_I64X2_GE_S:
            case Bytecode.VECTOR_F32X4_EQ:
            case Bytecode.VECTOR_F32X4_NE:
            case Bytecode.VECTOR_F32X4_LT:
            case Bytecode.VECTOR_F32X4_GT:
            case Bytecode.VECTOR_F32X4_LE:
            case Bytecode.VECTOR_F32X4_GE:
            case Bytecode.VECTOR_F64X2_EQ:
            case Bytecode.VECTOR_F64X2_NE:
            case Bytecode.VECTOR_F64X2_LT:
            case Bytecode.VECTOR_F64X2_GT:
            case Bytecode.VECTOR_F64X2_LE:
            case Bytecode.VECTOR_F64X2_GE:
            case Bytecode.VECTOR_V128_AND:
            case Bytecode.VECTOR_V128_ANDNOT:
            case Bytecode.VECTOR_V128_OR:
            case Bytecode.VECTOR_V128_XOR:
            case Bytecode.VECTOR_I8X16_NARROW_I16X8_S:
            case Bytecode.VECTOR_I8X16_NARROW_I16X8_U:
            case Bytecode.VECTOR_I8X16_ADD:
            case Bytecode.VECTOR_I8X16_ADD_SAT_S:
            case Bytecode.VECTOR_I8X16_ADD_SAT_U:
            case Bytecode.VECTOR_I8X16_SUB:
            case Bytecode.VECTOR_I8X16_SUB_SAT_S:
            case Bytecode.VECTOR_I8X16_SUB_SAT_U:
            case Bytecode.VECTOR_I8X16_MIN_S:
            case Bytecode.VECTOR_I8X16_MIN_U:
            case Bytecode.VECTOR_I8X16_MAX_S:
            case Bytecode.VECTOR_I8X16_MAX_U:
            case Bytecode.VECTOR_I8X16_AVGR_U:
            case Bytecode.VECTOR_I16X8_NARROW_I32X4_S:
            case Bytecode.VECTOR_I16X8_NARROW_I32X4_U:
            case Bytecode.VECTOR_I16X8_Q15MULR_SAT_S:
            case Bytecode.VECTOR_I16X8_ADD:
            case Bytecode.VECTOR_I16X8_ADD_SAT_S:
            case Bytecode.VECTOR_I16X8_ADD_SAT_U:
            case Bytecode.VECTOR_I16X8_SUB:
            case Bytecode.VECTOR_I16X8_SUB_SAT_S:
            case Bytecode.VECTOR_I16X8_SUB_SAT_U:
            case Bytecode.VECTOR_I16X8_MUL:
            case Bytecode.VECTOR_I16X8_MIN_S:
            case Bytecode.VECTOR_I16X8_MIN_U:
            case Bytecode.VECTOR_I16X8_MAX_S:
            case Bytecode.VECTOR_I16X8_MAX_U:
            case Bytecode.VECTOR_I16X8_AVGR_U:
            case Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8X16_S:
            case Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8X16_S:
            case Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8X16_U:
            case Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8X16_U:
            case Bytecode.VECTOR_I32X4_ADD:
            case Bytecode.VECTOR_I32X4_SUB:
            case Bytecode.VECTOR_I32X4_MUL:
            case Bytecode.VECTOR_I32X4_MIN_S:
            case Bytecode.VECTOR_I32X4_MIN_U:
            case Bytecode.VECTOR_I32X4_MAX_S:
            case Bytecode.VECTOR_I32X4_MAX_U:
            case Bytecode.VECTOR_I32X4_DOT_I16X8_S:
            case Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_S:
            case Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_S:
            case Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_U:
            case Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_U:
            case Bytecode.VECTOR_I64X2_ADD:
            case Bytecode.VECTOR_I64X2_SUB:
            case Bytecode.VECTOR_I64X2_MUL:
            case Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_S:
            case Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_S:
            case Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_U:
            case Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_U:
            case Bytecode.VECTOR_F32X4_ADD:
            case Bytecode.VECTOR_F32X4_SUB:
            case Bytecode.VECTOR_F32X4_MUL:
            case Bytecode.VECTOR_F32X4_DIV:
            case Bytecode.VECTOR_F32X4_MIN:
            case Bytecode.VECTOR_F32X4_MAX:
            case Bytecode.VECTOR_F32X4_PMIN:
            case Bytecode.VECTOR_F32X4_PMAX:
            case Bytecode.VECTOR_F64X2_ADD:
            case Bytecode.VECTOR_F64X2_SUB:
            case Bytecode.VECTOR_F64X2_MUL:
            case Bytecode.VECTOR_F64X2_DIV:
            case Bytecode.VECTOR_F64X2_MIN:
            case Bytecode.VECTOR_F64X2_MAX:
            case Bytecode.VECTOR_F64X2_PMIN:
            case Bytecode.VECTOR_F64X2_PMAX:
            case Bytecode.VECTOR_I8X16_RELAXED_SWIZZLE:
            case Bytecode.VECTOR_F32X4_RELAXED_MIN:
            case Bytecode.VECTOR_F32X4_RELAXED_MAX:
            case Bytecode.VECTOR_F64X2_RELAXED_MIN:
            case Bytecode.VECTOR_F64X2_RELAXED_MAX:
            case Bytecode.VECTOR_I16X8_RELAXED_Q15MULR_S:
            case Bytecode.VECTOR_I16X8_RELAXED_DOT_I8X16_I7X16_S: {
                V128 y = popVector128(frame, --stackPointer);
                V128 x = popVector128(frame, --stackPointer);
                V128 result = vector128Ops().binary(x, y, vectorOpcode);
                pushVector128(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_V128_BITSELECT:
            case Bytecode.VECTOR_F32X4_RELAXED_MADD:
            case Bytecode.VECTOR_F32X4_RELAXED_NMADD:
            case Bytecode.VECTOR_F64X2_RELAXED_MADD:
            case Bytecode.VECTOR_F64X2_RELAXED_NMADD:
            case Bytecode.VECTOR_I8X16_RELAXED_LANESELECT:
            case Bytecode.VECTOR_I16X8_RELAXED_LANESELECT:
            case Bytecode.VECTOR_I32X4_RELAXED_LANESELECT:
            case Bytecode.VECTOR_I64X2_RELAXED_LANESELECT:
            case Bytecode.VECTOR_I32X4_RELAXED_DOT_I8X16_I7X16_ADD_S: {
                V128 z = popVector128(frame, --stackPointer);
                V128 y = popVector128(frame, --stackPointer);
                V128 x = popVector128(frame, --stackPointer);
                V128 result = vector128Ops().ternary(x, y, z, vectorOpcode);
                pushVector128(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_V128_ANY_TRUE:
            case Bytecode.VECTOR_I8X16_ALL_TRUE:
            case Bytecode.VECTOR_I8X16_BITMASK:
            case Bytecode.VECTOR_I16X8_ALL_TRUE:
            case Bytecode.VECTOR_I16X8_BITMASK:
            case Bytecode.VECTOR_I32X4_ALL_TRUE:
            case Bytecode.VECTOR_I32X4_BITMASK:
            case Bytecode.VECTOR_I64X2_ALL_TRUE:
            case Bytecode.VECTOR_I64X2_BITMASK: {
                V128 x = popVector128(frame, --stackPointer);
                int result = vector128Ops().vectorToInt(x, vectorOpcode);
                pushInt(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_I8X16_SHL:
            case Bytecode.VECTOR_I8X16_SHR_S:
            case Bytecode.VECTOR_I8X16_SHR_U:
            case Bytecode.VECTOR_I16X8_SHL:
            case Bytecode.VECTOR_I16X8_SHR_S:
            case Bytecode.VECTOR_I16X8_SHR_U:
            case Bytecode.VECTOR_I32X4_SHL:
            case Bytecode.VECTOR_I32X4_SHR_S:
            case Bytecode.VECTOR_I32X4_SHR_U:
            case Bytecode.VECTOR_I64X2_SHL:
            case Bytecode.VECTOR_I64X2_SHR_S:
            case Bytecode.VECTOR_I64X2_SHR_U: {
                int shift = popInt(frame, --stackPointer);
                V128 x = popVector128(frame, --stackPointer);
                V128 result = vector128Ops().shift(x, shift, vectorOpcode);
                pushVector128(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_I8X16_SPLAT: {
                int x = popInt(frame, --stackPointer);
                V128 result = vector128Ops().i8x16_splat((byte) x);
                pushVector128(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_I16X8_SPLAT: {
                int x = popInt(frame, --stackPointer);
                V128 result = vector128Ops().i16x8_splat((short) x);
                pushVector128(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_I32X4_SPLAT: {
                int x = popInt(frame, --stackPointer);
                V128 result = vector128Ops().i32x4_splat(x);
                pushVector128(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_I64X2_SPLAT: {
                long x = popLong(frame, --stackPointer);
                V128 result = vector128Ops().i64x2_splat(x);
                pushVector128(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_F32X4_SPLAT: {
                float x = popFloat(frame, --stackPointer);
                V128 result = vector128Ops().f32x4_splat(x);
                pushVector128(frame, stackPointer++, result);
                break;
            }
            case Bytecode.VECTOR_F64X2_SPLAT: {
                double x = popDouble(frame, --stackPointer);
                V128 result = vector128Ops().f64x2_splat(x);
                pushVector128(frame, stackPointer++, result);
                break;
            }
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }

        assert stackPointer - startingStackPointer == StackEffects.getVectorOpStackEffect(vectorOpcode);
        return offset;
    }

    private void loadVector(WasmMemory memory, WasmMemoryLibrary memoryLib, VirtualFrame frame, int stackPointer, int vectorOpcode, long address) {
        switch (vectorOpcode) {
            case Bytecode.VECTOR_V128_LOAD: {
                final V128 value = Vector128Ops.cast(memoryLib.load_i128(memory, this, address));
                pushVector128(frame, stackPointer, value);
                break;
            }
            case Bytecode.VECTOR_V128_LOAD8X8_S:
            case Bytecode.VECTOR_V128_LOAD8X8_U: {
                final long value = memoryLib.load_i64(memory, this, address);
                final V128 vec = vector128Ops().v128_load8x8(value, vectorOpcode);
                pushVector128(frame, stackPointer, vec);
                break;
            }
            case Bytecode.VECTOR_V128_LOAD16X4_S:
            case Bytecode.VECTOR_V128_LOAD16X4_U: {
                final long value = memoryLib.load_i64(memory, this, address);
                final V128 vec = vector128Ops().v128_load16x4(value, vectorOpcode);
                pushVector128(frame, stackPointer, vec);
                break;
            }
            case Bytecode.VECTOR_V128_LOAD32X2_S:
            case Bytecode.VECTOR_V128_LOAD32X2_U: {
                final long value = memoryLib.load_i64(memory, this, address);
                final V128 vec = vector128Ops().v128_load32x2(value, vectorOpcode);
                pushVector128(frame, stackPointer, vec);
                break;
            }
            case Bytecode.VECTOR_V128_LOAD8_SPLAT: {
                final byte value = (byte) memoryLib.load_i32_8s(memory, this, address);
                final V128 vec = vector128Ops().i8x16_splat(value);
                pushVector128(frame, stackPointer, vec);
                break;
            }
            case Bytecode.VECTOR_V128_LOAD16_SPLAT: {
                final short value = (short) memoryLib.load_i32_16s(memory, this, address);
                final V128 vec = vector128Ops().i16x8_splat(value);
                pushVector128(frame, stackPointer, vec);
                break;
            }
            case Bytecode.VECTOR_V128_LOAD32_SPLAT: {
                final int value = memoryLib.load_i32(memory, this, address);
                final V128 vec = vector128Ops().i32x4_splat(value);
                pushVector128(frame, stackPointer, vec);
                break;
            }
            case Bytecode.VECTOR_V128_LOAD64_SPLAT: {
                final long value = memoryLib.load_i64(memory, this, address);
                final V128 vec = vector128Ops().i64x2_splat(value);
                pushVector128(frame, stackPointer, vec);
                break;
            }
            case Bytecode.VECTOR_V128_LOAD32_ZERO: {
                final int value = memoryLib.load_i32(memory, this, address);
                final V128 vec = vector128Ops().v128_load32_zero(value);
                pushVector128(frame, stackPointer, vec);
                break;
            }
            case Bytecode.VECTOR_V128_LOAD64_ZERO: {
                final long value = memoryLib.load_i64(memory, this, address);
                final V128 vec = vector128Ops().v128_load64_zero(value);
                pushVector128(frame, stackPointer, vec);
                break;
            }
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private void storeVector(WasmMemory memory, WasmMemoryLibrary memoryLib, long address, V128 value) {
        memoryLib.store_i128(memory, this, address, value);
    }

    private void loadVectorLane(WasmMemory memory, WasmMemoryLibrary memoryLib, VirtualFrame frame, int stackPointer, int vectorOpcode, long address, int laneIndex, V128 vec) {
        switch (vectorOpcode) {
            case Bytecode.VECTOR_V128_LOAD8_LANE: {
                final byte value = (byte) memoryLib.load_i32_8s(memory, this, address);
                final V128 resultVec = vector128Ops().i8x16_replace_lane(vec, laneIndex, value);
                pushVector128(frame, stackPointer, resultVec);
                break;
            }
            case Bytecode.VECTOR_V128_LOAD16_LANE: {
                final short value = (short) memoryLib.load_i32_16s(memory, this, address);
                final V128 resultVec = vector128Ops().i16x8_replace_lane(vec, laneIndex, value);
                pushVector128(frame, stackPointer, resultVec);
                break;
            }
            case Bytecode.VECTOR_V128_LOAD32_LANE: {
                final int value = memoryLib.load_i32(memory, this, address);
                final V128 resultVec = vector128Ops().i32x4_replace_lane(vec, laneIndex, value);
                pushVector128(frame, stackPointer, resultVec);
                break;
            }
            case Bytecode.VECTOR_V128_LOAD64_LANE: {
                final long value = memoryLib.load_i64(memory, this, address);
                final V128 resultVec = vector128Ops().i64x2_replace_lane(vec, laneIndex, value);
                pushVector128(frame, stackPointer, resultVec);
                break;
            }
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private void storeVectorLane(WasmMemory memory, WasmMemoryLibrary memoryLib, int vectorOpcode, long address, int laneIndex, V128 vec) {
        switch (vectorOpcode) {
            case Bytecode.VECTOR_V128_STORE8_LANE: {
                byte value = vector128Ops().i8x16_extract_lane_s(vec, laneIndex);
                memoryLib.store_i32_8(memory, this, address, value);
                break;
            }
            case Bytecode.VECTOR_V128_STORE16_LANE: {
                short value = vector128Ops().i16x8_extract_lane_s(vec, laneIndex);
                memoryLib.store_i32_16(memory, this, address, value);
                break;
            }
            case Bytecode.VECTOR_V128_STORE32_LANE: {
                int value = vector128Ops().i32x4_extract_lane(vec, laneIndex);
                memoryLib.store_i32(memory, this, address, value);
                break;
            }
            case Bytecode.VECTOR_V128_STORE64_LANE: {
                long value = vector128Ops().i64x2_extract_lane(vec, laneIndex);
                memoryLib.store_i64(memory, this, address, value);
                break;
            }
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    // Checkstyle: stop method name check

    private void global_set(WasmInstance instance, VirtualFrame frame, int stackPointer, int index) {
        final int type = module.globalValueType(index);
        CompilerAsserts.partialEvaluationConstant(type);
        // For global.set, we don't need to make sure that the referenced global is
        // mutable.
        // This is taken care of by validation during wat to wasm compilation.
        assert module.symbolTable().isGlobalMutable(index) : index;
        final int globalAddress = module.symbolTable().globalAddress(index);
        final GlobalRegistry globals = instance.globals();

        switch (type) {
            case WasmType.I32_TYPE -> globals.storeInt(globalAddress, popInt(frame, stackPointer));
            case WasmType.F32_TYPE -> globals.storeFloat(globalAddress, popFloat(frame, stackPointer));
            case WasmType.I64_TYPE -> globals.storeLong(globalAddress, popLong(frame, stackPointer));
            case WasmType.F64_TYPE -> globals.storeDouble(globalAddress, popDouble(frame, stackPointer));
            case WasmType.V128_TYPE -> globals.storeVector128(globalAddress, vector128Ops().toVector128(popVector128(frame, stackPointer)));
            default -> {
                assert WasmType.isReferenceType(type);
                globals.storeReference(globalAddress, popReference(frame, stackPointer));
            }
        }
    }

    private void global_get(WasmInstance instance, VirtualFrame frame, int stackPointer, int index) {
        final int type = module.symbolTable().globalValueType(index);
        CompilerAsserts.partialEvaluationConstant(type);
        final int globalAddress = module.symbolTable().globalAddress(index);
        final GlobalRegistry globals = instance.globals();

        switch (type) {
            case WasmType.I32_TYPE -> pushInt(frame, stackPointer, globals.loadAsInt(globalAddress));
            case WasmType.F32_TYPE -> pushFloat(frame, stackPointer, globals.loadAsFloat(globalAddress));
            case WasmType.I64_TYPE -> pushLong(frame, stackPointer, globals.loadAsLong(globalAddress));
            case WasmType.F64_TYPE -> pushDouble(frame, stackPointer, globals.loadAsDouble(globalAddress));
            case WasmType.V128_TYPE -> pushVector128(frame, stackPointer, vector128Ops().fromVector128(globals.loadAsVector128(globalAddress)));
            default -> {
                assert WasmType.isReferenceType(type);
                pushReference(frame, stackPointer, globals.loadAsReference(globalAddress));
            }
        }
    }

    private static void local_tee(VirtualFrame frame, int stackPointer, int index) {
        WasmFrame.copyPrimitive(frame, stackPointer, index);
    }

    private static void local_tee_obj(VirtualFrame frame, int stackPointer, int index) {
        WasmFrame.copyObject(frame, stackPointer, index);
    }

    private static void local_set(VirtualFrame frame, int stackPointer, int index) {
        WasmFrame.copyPrimitive(frame, stackPointer, index);
        if (CompilerDirectives.inCompiledCode()) {
            WasmFrame.dropPrimitive(frame, stackPointer);
        }
    }

    private static void local_set_obj(VirtualFrame frame, int stackPointer, int index) {
        WasmFrame.copyObject(frame, stackPointer, index);
        if (CompilerDirectives.inCompiledCode()) {
            WasmFrame.dropObject(frame, stackPointer);
        }
    }

    private static void local_get(VirtualFrame frame, int stackPointer, int index) {
        WasmFrame.copyPrimitive(frame, index, stackPointer);
    }

    private static void local_get_obj(VirtualFrame frame, int stackPointer, int index) {
        WasmFrame.copyObject(frame, index, stackPointer);
    }

    private static void i32_eqz(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        pushInt(frame, stackPointer - 1, x == 0 ? 1 : 0);
    }

    private static void i64_eqz(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        pushInt(frame, stackPointer - 1, x == 0 ? 1 : 0);
    }

    private static void i32_eq(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y == x ? 1 : 0);
    }

    private static void i32_ne(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y != x ? 1 : 0);
    }

    private static void i32_lt_s(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y < x ? 1 : 0);
    }

    private static void i32_lt_u(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, Integer.compareUnsigned(y, x) < 0 ? 1 : 0);
    }

    private static void i32_gt_s(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y > x ? 1 : 0);
    }

    private static void i32_gt_u(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, Integer.compareUnsigned(y, x) > 0 ? 1 : 0);
    }

    private static void i32_le_s(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y <= x ? 1 : 0);
    }

    private static void i32_le_u(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, Integer.compareUnsigned(y, x) <= 0 ? 1 : 0);
    }

    private static void i32_ge_s(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y >= x ? 1 : 0);
    }

    private static void i32_ge_u(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, Integer.compareUnsigned(y, x) >= 0 ? 1 : 0);
    }

    private static void i64_eq(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y == x ? 1 : 0);
    }

    private static void i64_ne(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y != x ? 1 : 0);
    }

    private static void i64_lt_s(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y < x ? 1 : 0);
    }

    private static void i64_lt_u(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, Long.compareUnsigned(y, x) < 0 ? 1 : 0);
    }

    private static void i64_gt_s(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y > x ? 1 : 0);
    }

    private static void i64_gt_u(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, Long.compareUnsigned(y, x) > 0 ? 1 : 0);
    }

    private static void i64_le_s(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y <= x ? 1 : 0);
    }

    private static void i64_le_u(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, Long.compareUnsigned(y, x) <= 0 ? 1 : 0);
    }

    private static void i64_ge_s(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y >= x ? 1 : 0);
    }

    private static void i64_ge_u(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, Long.compareUnsigned(y, x) >= 0 ? 1 : 0);
    }

    private static void f32_eq(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float y = popFloat(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y == x ? 1 : 0);
    }

    private static void f32_ne(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float y = popFloat(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y != x ? 1 : 0);
    }

    private static void f32_lt(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float y = popFloat(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y < x ? 1 : 0);
    }

    private static void f32_gt(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float y = popFloat(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y > x ? 1 : 0);
    }

    private static void f32_le(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float y = popFloat(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y <= x ? 1 : 0);
    }

    private static void f32_ge(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float y = popFloat(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y >= x ? 1 : 0);
    }

    private static void f64_eq(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double y = popDouble(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y == x ? 1 : 0);
    }

    private static void f64_ne(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double y = popDouble(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y != x ? 1 : 0);
    }

    private static void f64_lt(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double y = popDouble(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y < x ? 1 : 0);
    }

    private static void f64_gt(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double y = popDouble(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y > x ? 1 : 0);
    }

    private static void f64_le(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double y = popDouble(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y <= x ? 1 : 0);
    }

    private static void f64_ge(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double y = popDouble(frame, stackPointer - 2);
        pushInt(frame, stackPointer - 2, y >= x ? 1 : 0);
    }

    private static void i32_clz(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int result = Integer.numberOfLeadingZeros(x);
        pushInt(frame, stackPointer - 1, result);
    }

    private static void i32_ctz(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int result = Integer.numberOfTrailingZeros(x);
        pushInt(frame, stackPointer - 1, result);
    }

    private static void i32_popcnt(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int result = Integer.bitCount(x);
        pushInt(frame, stackPointer - 1, result);
    }

    private static void i32_add(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        int result = y + x;
        pushInt(frame, stackPointer - 2, result);
    }

    private static void i32_sub(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        int result = y - x;
        pushInt(frame, stackPointer - 2, result);
    }

    private static void i32_mul(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        int result = y * x;
        pushInt(frame, stackPointer - 2, result);
    }

    private void i32_div_s(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        if (x == -1 && y == Integer.MIN_VALUE) {
            enterErrorBranch(codeEntry);
            throw WasmException.create(Failure.INT_OVERFLOW, this);
        }
        int result;
        try {
            result = y / x;
        } catch (ArithmeticException e) {
            enterErrorBranch(codeEntry);
            throw WasmException.create(Failure.INT_DIVIDE_BY_ZERO, this);
        }
        pushInt(frame, stackPointer - 2, result);
    }

    private void i32_div_u(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        int result;
        try {
            result = Integer.divideUnsigned(y, x);
        } catch (ArithmeticException e) {
            enterErrorBranch(codeEntry);
            throw WasmException.create(Failure.INT_DIVIDE_BY_ZERO, this);
        }
        pushInt(frame, stackPointer - 2, result);
    }

    private void i32_rem_s(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        int result;
        try {
            result = y % x;
        } catch (ArithmeticException e) {
            enterErrorBranch(codeEntry);
            throw WasmException.create(Failure.INT_DIVIDE_BY_ZERO, this);
        }
        pushInt(frame, stackPointer - 2, result);
    }

    private void i32_rem_u(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        int result;
        try {
            result = Integer.remainderUnsigned(y, x);
        } catch (ArithmeticException e) {
            enterErrorBranch(codeEntry);
            throw WasmException.create(Failure.INT_DIVIDE_BY_ZERO, this);
        }
        pushInt(frame, stackPointer - 2, result);
    }

    private static void i32_and(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        int result = y & x;
        pushInt(frame, stackPointer - 2, result);
    }

    private static void i32_or(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        int result = y | x;
        pushInt(frame, stackPointer - 2, result);
    }

    private static void i32_xor(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        int result = y ^ x;
        pushInt(frame, stackPointer - 2, result);
    }

    private static void i32_shl(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        int result = y << x;
        pushInt(frame, stackPointer - 2, result);
    }

    private static void i32_shr_s(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        int result = y >> x;
        pushInt(frame, stackPointer - 2, result);
    }

    private static void i32_shr_u(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        int result = y >>> x;
        pushInt(frame, stackPointer - 2, result);
    }

    private static void i32_rotl(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        int result = Integer.rotateLeft(y, x);
        pushInt(frame, stackPointer - 2, result);
    }

    private static void i32_rotr(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int y = popInt(frame, stackPointer - 2);
        int result = Integer.rotateRight(y, x);
        pushInt(frame, stackPointer - 2, result);
    }

    private static void i64_clz(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long result = Long.numberOfLeadingZeros(x);
        pushLong(frame, stackPointer - 1, result);
    }

    private static void i64_ctz(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long result = Long.numberOfTrailingZeros(x);
        pushLong(frame, stackPointer - 1, result);
    }

    private static void i64_popcnt(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long result = Long.bitCount(x);
        pushLong(frame, stackPointer - 1, result);
    }

    private static void i64_add(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        long result = y + x;
        pushLong(frame, stackPointer - 2, result);
    }

    private static void i64_sub(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        long result = y - x;
        pushLong(frame, stackPointer - 2, result);
    }

    private static void i64_mul(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        long result = y * x;
        pushLong(frame, stackPointer - 2, result);
    }

    private void i64_div_s(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        if (x == -1 && y == Long.MIN_VALUE) {
            enterErrorBranch(codeEntry);
            throw WasmException.create(Failure.INT_OVERFLOW, this);
        }
        final long result;
        try {
            result = y / x;
        } catch (ArithmeticException e) {
            enterErrorBranch(codeEntry);
            throw WasmException.create(Failure.INT_DIVIDE_BY_ZERO, this);
        }
        pushLong(frame, stackPointer - 2, result);
    }

    private void i64_div_u(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        long result;
        try {
            result = Long.divideUnsigned(y, x);
        } catch (ArithmeticException e) {
            enterErrorBranch(codeEntry);
            throw WasmException.create(Failure.INT_DIVIDE_BY_ZERO, this);
        }
        pushLong(frame, stackPointer - 2, result);
    }

    private void i64_rem_s(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        long result;
        try {
            result = y % x;
        } catch (ArithmeticException e) {
            enterErrorBranch(codeEntry);
            throw WasmException.create(Failure.INT_DIVIDE_BY_ZERO, this);
        }
        pushLong(frame, stackPointer - 2, result);
    }

    private void i64_rem_u(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        long result;
        try {
            result = Long.remainderUnsigned(y, x);
        } catch (ArithmeticException e) {
            enterErrorBranch(codeEntry);
            throw WasmException.create(Failure.INT_DIVIDE_BY_ZERO, this);
        }
        pushLong(frame, stackPointer - 2, result);
    }

    private static void i64_and(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        long result = y & x;
        pushLong(frame, stackPointer - 2, result);
    }

    private static void i64_or(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        long result = y | x;
        pushLong(frame, stackPointer - 2, result);
    }

    private static void i64_xor(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        long result = y ^ x;
        pushLong(frame, stackPointer - 2, result);
    }

    private static void i64_shl(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        long result = y << x;
        pushLong(frame, stackPointer - 2, result);
    }

    private static void i64_shr_s(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        long result = y >> x;
        pushLong(frame, stackPointer - 2, result);
    }

    private static void i64_shr_u(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        long result = y >>> x;
        pushLong(frame, stackPointer - 2, result);
    }

    private static void i64_rotl(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        long result = Long.rotateLeft(y, (int) x);
        pushLong(frame, stackPointer - 2, result);
    }

    private static void i64_rotr(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long y = popLong(frame, stackPointer - 2);
        long result = Long.rotateRight(y, (int) x);
        pushLong(frame, stackPointer - 2, result);
    }

    private static void f32_abs(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float result = Math.abs(x);
        pushFloat(frame, stackPointer - 1, result);
    }

    private static void f32_neg(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float result = -x;
        pushFloat(frame, stackPointer - 1, result);
    }

    private static void f32_ceil(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float result = (float) Math.ceil(x);
        pushFloat(frame, stackPointer - 1, result);
    }

    private static void f32_floor(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float result = (float) Math.floor(x);
        pushFloat(frame, stackPointer - 1, result);
    }

    private static void f32_trunc(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float result = ExactMath.truncate(x);
        pushFloat(frame, stackPointer - 1, result);
    }

    private static void f32_nearest(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float result = (float) Math.rint(x);
        pushFloat(frame, stackPointer - 1, result);
    }

    private static void f32_sqrt(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float result = (float) Math.sqrt(x);
        pushFloat(frame, stackPointer - 1, result);
    }

    private static void f32_add(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float y = popFloat(frame, stackPointer - 2);
        float result = y + x;
        pushFloat(frame, stackPointer - 2, result);
    }

    private static void f32_sub(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float y = popFloat(frame, stackPointer - 2);
        float result = y - x;
        pushFloat(frame, stackPointer - 2, result);
    }

    private static void f32_mul(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float y = popFloat(frame, stackPointer - 2);
        float result = y * x;
        pushFloat(frame, stackPointer - 2, result);
    }

    private static void f32_div(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float y = popFloat(frame, stackPointer - 2);
        float result = y / x;
        pushFloat(frame, stackPointer - 2, result);
    }

    private static void f32_min(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float y = popFloat(frame, stackPointer - 2);
        float result = Math.min(y, x);
        pushFloat(frame, stackPointer - 2, result);
    }

    private static void f32_max(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float y = popFloat(frame, stackPointer - 2);
        float result = Math.max(y, x);
        pushFloat(frame, stackPointer - 2, result);
    }

    private static void f32_copysign(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        float y = popFloat(frame, stackPointer - 2);
        float result = Math.copySign(y, x);
        pushFloat(frame, stackPointer - 2, result);
    }

    private static void f64_abs(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double result = Math.abs(x);
        pushDouble(frame, stackPointer - 1, result);
    }

    private static void f64_neg(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double result = -x;
        pushDouble(frame, stackPointer - 1, result);
    }

    private static void f64_ceil(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double result = Math.ceil(x);
        pushDouble(frame, stackPointer - 1, result);
    }

    private static void f64_floor(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double result = Math.floor(x);
        pushDouble(frame, stackPointer - 1, result);
    }

    private static void f64_trunc(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double result = ExactMath.truncate(x);
        pushDouble(frame, stackPointer - 1, result);
    }

    private static void f64_nearest(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double result = Math.rint(x);
        pushDouble(frame, stackPointer - 1, result);
    }

    private static void f64_sqrt(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double result = Math.sqrt(x);
        pushDouble(frame, stackPointer - 1, result);
    }

    private static void f64_add(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double y = popDouble(frame, stackPointer - 2);
        double result = y + x;
        pushDouble(frame, stackPointer - 2, result);
    }

    private static void f64_sub(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double y = popDouble(frame, stackPointer - 2);
        double result = y - x;
        pushDouble(frame, stackPointer - 2, result);
    }

    private static void f64_mul(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double y = popDouble(frame, stackPointer - 2);
        double result = y * x;
        pushDouble(frame, stackPointer - 2, result);
    }

    private static void f64_div(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double y = popDouble(frame, stackPointer - 2);
        double result = y / x;
        pushDouble(frame, stackPointer - 2, result);
    }

    private static void f64_min(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double y = popDouble(frame, stackPointer - 2);
        double result = Math.min(y, x);
        pushDouble(frame, stackPointer - 2, result);
    }

    private static void f64_max(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double y = popDouble(frame, stackPointer - 2);
        double result = Math.max(y, x);
        pushDouble(frame, stackPointer - 2, result);
    }

    private static void f64_copysign(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        double y = popDouble(frame, stackPointer - 2);
        double result = Math.copySign(y, x);
        pushDouble(frame, stackPointer - 2, result);
    }

    private static void i32_wrap_i64(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        int result = (int) (x & 0xFFFF_FFFFL);
        pushInt(frame, stackPointer - 1, result);
    }

    private WasmException trunc_f32_trap(float x) {
        if (Float.isNaN(x)) {
            throw WasmException.create(Failure.INVALID_CONVERSION_TO_INT, this);
        } else {
            throw WasmException.create(Failure.INT_OVERFLOW, this);
        }
    }

    private WasmException trunc_f64_trap(double x) {
        if (Double.isNaN(x)) {
            throw WasmException.create(Failure.INVALID_CONVERSION_TO_INT, this);
        } else {
            throw WasmException.create(Failure.INT_OVERFLOW, this);
        }
    }

    private void i32_trunc_f32_s(VirtualFrame frame, int stackPointer) {
        final float x = popFloat(frame, stackPointer - 1);
        final int result;
        if (x >= -0x1p31f && x < 0x1p31f) {
            result = (int) x;
        } else {
            enterErrorBranch(codeEntry);
            throw trunc_f32_trap(x);
        }
        pushInt(frame, stackPointer - 1, result);
    }

    private void i32_trunc_f32_u(VirtualFrame frame, int stackPointer) {
        final float x = popFloat(frame, stackPointer - 1);
        final int result;
        if (x > -1.0f && x < 0x1p32f) {
            result = ExactMath.truncateToUnsignedInt(x);
        } else {
            enterErrorBranch(codeEntry);
            throw trunc_f32_trap(x);
        }
        pushInt(frame, stackPointer - 1, result);
    }

    private void i32_trunc_f64_s(VirtualFrame frame, int stackPointer) {
        final double x = popDouble(frame, stackPointer - 1);
        final int result;
        if (x > -0x1.00000002p31 && x < 0x1p31) { // sic!
            result = (int) x;
        } else {
            enterErrorBranch(codeEntry);
            throw trunc_f64_trap(x);
        }
        pushInt(frame, stackPointer - 1, result);
    }

    private void i32_trunc_f64_u(VirtualFrame frame, int stackPointer) {
        final double x = popDouble(frame, stackPointer - 1);
        final int result;
        if (x > -1.0 && x < 0x1p32) {
            result = ExactMath.truncateToUnsignedInt(x);
        } else {
            enterErrorBranch(codeEntry);
            throw trunc_f64_trap(x);
        }
        pushInt(frame, stackPointer - 1, result);
    }

    private static void i32_trunc_sat_f32_s(VirtualFrame frame, int stackPointer) {
        final float x = popFloat(frame, stackPointer - 1);
        final int result = (int) x;
        pushInt(frame, stackPointer - 1, result);
    }

    private static void i32_trunc_sat_f32_u(VirtualFrame frame, int stackPointer) {
        final float x = popFloat(frame, stackPointer - 1);
        final int result = ExactMath.truncateToUnsignedInt(x);
        pushInt(frame, stackPointer - 1, result);
    }

    private static void i32_trunc_sat_f64_s(VirtualFrame frame, int stackPointer) {
        final double x = popDouble(frame, stackPointer - 1);
        final int result = (int) x;
        pushInt(frame, stackPointer - 1, result);
    }

    private static void i32_trunc_sat_f64_u(VirtualFrame frame, int stackPointer) {
        final double x = popDouble(frame, stackPointer - 1);
        final int result = ExactMath.truncateToUnsignedInt(x);
        pushInt(frame, stackPointer - 1, result);
    }

    private static void i64_extend_i32_s(VirtualFrame frame, int stackPointer) {
        long result = popInt(frame, stackPointer - 1);
        pushLong(frame, stackPointer - 1, result);
    }

    private static void i64_extend_i32_u(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        long result = x & 0xFFFF_FFFFL;
        pushLong(frame, stackPointer - 1, result);
    }

    private void i64_trunc_f32_s(VirtualFrame frame, int stackPointer) {
        final float x = popFloat(frame, stackPointer - 1);
        final long result;
        if (x >= -0x1p63f && x < 0x1p63f) {
            result = (long) x;
        } else {
            enterErrorBranch(codeEntry);
            throw trunc_f32_trap(x);
        }
        pushLong(frame, stackPointer - 1, result);
    }

    private void i64_trunc_f32_u(VirtualFrame frame, int stackPointer) {
        final float x = popFloat(frame, stackPointer - 1);
        final long result;
        if (x > -1.0f && x < 0x1p64f) {
            result = ExactMath.truncateToUnsignedLong(x);
        } else {
            enterErrorBranch(codeEntry);
            throw trunc_f32_trap(x);
        }
        pushLong(frame, stackPointer - 1, result);
    }

    private void i64_trunc_f64_s(VirtualFrame frame, int stackPointer) {
        final double x = popDouble(frame, stackPointer - 1);
        final long result;
        if (x >= -0x1p63 && x < 0x1p63) {
            result = (long) x;
        } else {
            enterErrorBranch(codeEntry);
            throw trunc_f64_trap(x);
        }
        pushLong(frame, stackPointer - 1, result);
    }

    private void i64_trunc_f64_u(VirtualFrame frame, int stackPointer) {
        final double x = popDouble(frame, stackPointer - 1);
        final long result;
        if (x > -1.0 && x < 0x1p64) {
            result = ExactMath.truncateToUnsignedLong(x);
        } else {
            enterErrorBranch(codeEntry);
            throw trunc_f64_trap(x);
        }
        pushLong(frame, stackPointer - 1, result);
    }

    private static void i64_trunc_sat_f32_s(VirtualFrame frame, int stackPointer) {
        final float x = popFloat(frame, stackPointer - 1);
        final long result = (long) x;
        pushLong(frame, stackPointer - 1, result);
    }

    private static void i64_trunc_sat_f32_u(VirtualFrame frame, int stackPointer) {
        final float x = popFloat(frame, stackPointer - 1);
        final long result = ExactMath.truncateToUnsignedLong(x);
        pushLong(frame, stackPointer - 1, result);
    }

    private static void i64_trunc_sat_f64_s(VirtualFrame frame, int stackPointer) {
        final double x = popDouble(frame, stackPointer - 1);
        final long result = (long) x;
        pushLong(frame, stackPointer - 1, result);
    }

    private static void i64_trunc_sat_f64_u(VirtualFrame frame, int stackPointer) {
        final double x = popDouble(frame, stackPointer - 1);
        final long result = ExactMath.truncateToUnsignedLong(x);
        pushLong(frame, stackPointer - 1, result);
    }

    private static void f32_convert_i32_s(VirtualFrame frame, int stackPointer) {
        final int x = popInt(frame, stackPointer - 1);
        pushFloat(frame, stackPointer - 1, x);
    }

    private static void f32_convert_i32_u(VirtualFrame frame, int stackPointer) {
        final int x = popInt(frame, stackPointer - 1);
        final float result = WasmMath.unsignedIntToFloat(x);
        pushFloat(frame, stackPointer - 1, result);
    }

    private static void f32_convert_i64_s(VirtualFrame frame, int stackPointer) {
        final long x = popLong(frame, stackPointer - 1);
        pushFloat(frame, stackPointer - 1, x);
    }

    private static void f32_convert_i64_u(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        float result = ExactMath.unsignedToFloat(x);
        pushFloat(frame, stackPointer - 1, result);
    }

    private static void f32_demote_f64(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        pushFloat(frame, stackPointer - 1, (float) x);
    }

    private static void f64_convert_i32_s(VirtualFrame frame, int stackPointer) {
        final int x = popInt(frame, stackPointer - 1);
        pushDouble(frame, stackPointer - 1, x);
    }

    private static void f64_convert_i32_u(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        double result = WasmMath.unsignedIntToDouble(x);
        pushDouble(frame, stackPointer - 1, result);
    }

    private static void f64_convert_i64_s(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        pushDouble(frame, stackPointer - 1, x);
    }

    private static void f64_convert_i64_u(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        double result = ExactMath.unsignedToDouble(x);
        pushDouble(frame, stackPointer - 1, result);
    }

    private static void f64_promote_f32(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        pushDouble(frame, stackPointer - 1, x);
    }

    private static void i32_reinterpret_f32(VirtualFrame frame, int stackPointer) {
        float x = popFloat(frame, stackPointer - 1);
        pushInt(frame, stackPointer - 1, Float.floatToRawIntBits(x));
    }

    private static void i64_reinterpret_f64(VirtualFrame frame, int stackPointer) {
        double x = popDouble(frame, stackPointer - 1);
        pushLong(frame, stackPointer - 1, Double.doubleToRawLongBits(x));
    }

    private static void f32_reinterpret_i32(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        pushFloat(frame, stackPointer - 1, Float.intBitsToFloat(x));
    }

    private static void f64_reinterpret_i64(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        pushDouble(frame, stackPointer - 1, Double.longBitsToDouble(x));
    }

    private static void i32_extend8_s(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int result = (x << 24) >> 24;
        pushInt(frame, stackPointer - 1, result);
    }

    private static void i32_extend16_s(VirtualFrame frame, int stackPointer) {
        int x = popInt(frame, stackPointer - 1);
        int result = (x << 16) >> 16;
        pushInt(frame, stackPointer - 1, result);
    }

    private static void i64_extend8_s(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long result = (x << 56) >> 56;
        pushLong(frame, stackPointer - 1, result);
    }

    private static void i64_extend16_s(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long result = (x << 48) >> 48;
        pushLong(frame, stackPointer - 1, result);
    }

    private static void i64_extend32_s(VirtualFrame frame, int stackPointer) {
        long x = popLong(frame, stackPointer - 1);
        long result = (x << 32) >> 32;
        pushLong(frame, stackPointer - 1, result);
    }

    @TruffleBoundary
    private void table_init(WasmInstance instance, int length, int source, long destination, int tableIndex, int elementIndex) {
        final WasmTable table = instance.table(tableIndex);
        final Object[] elementInstance = instance.elemInstance(elementIndex);
        final int elementInstanceLength;
        if (elementInstance == null) {
            elementInstanceLength = 0;
        } else {
            elementInstanceLength = elementInstance.length;
        }
        if (checkOutOfBounds(source, length, elementInstanceLength) || checkOutOfBounds(destination, length, table.size())) {
            enterErrorBranch(codeEntry);
            throw WasmException.create(Failure.OUT_OF_BOUNDS_TABLE_ACCESS);
        }
        if (length == 0) {
            return;
        }
        table.initialize(elementInstance, source, (int) destination, length);
    }

    private void table_get(WasmInstance instance, VirtualFrame frame, int stackPointer, int tableIndex) {
        final WasmTable table = instance.table(tableIndex);
        final long i = popTableIndex(frame, stackPointer - 1, table);
        if (checkOutOfBounds(i, table.size())) {
            enterErrorBranch(codeEntry);
            throw WasmException.create(Failure.OUT_OF_BOUNDS_TABLE_ACCESS);
        }
        final Object value = table.get((int) i);
        pushReference(frame, stackPointer - 1, value);
    }

    private void table_set(WasmInstance instance, VirtualFrame frame, int stackPointer, int tableIndex) {
        final WasmTable table = instance.table(tableIndex);
        final Object value = popReference(frame, stackPointer - 1);
        final long i = popTableIndex(frame, stackPointer - 2, table);
        if (checkOutOfBounds(i, table.size())) {
            enterErrorBranch(codeEntry);
            throw WasmException.create(Failure.OUT_OF_BOUNDS_TABLE_ACCESS);
        }
        table.set((int) i, value);
    }

    private static void table_size(WasmInstance instance, VirtualFrame frame, int stackPointer, int tableIndex) {
        final WasmTable table = instance.table(tableIndex);
        pushTableIndex(frame, stackPointer, table, table.size());
    }

    @TruffleBoundary
    private static long table_grow(WasmInstance instance, long length, Object value, int tableIndex) {
        final WasmTable table = instance.table(tableIndex);
        return table.grow(length, value);
    }

    @TruffleBoundary
    private void table_copy(WasmInstance instance, long length, long source, long destination, int sourceTableIndex, int destinationTableIndex) {
        final WasmTable sourceTable = instance.table(sourceTableIndex);
        final WasmTable destinationTable = instance.table(destinationTableIndex);
        if (checkOutOfBounds(source, length, sourceTable.size()) || checkOutOfBounds(destination, length, destinationTable.size())) {
            enterErrorBranch(codeEntry);
            throw WasmException.create(Failure.OUT_OF_BOUNDS_TABLE_ACCESS);
        }
        if (length == 0) {
            return;
        }
        destinationTable.copyFrom(sourceTable, (int) source, (int) destination, (int) length);
    }

    @TruffleBoundary
    private void table_fill(WasmInstance instance, long length, Object value, long offset, int tableIndex) {
        final WasmTable table = instance.table(tableIndex);
        if (checkOutOfBounds(offset, length, table.size())) {
            enterErrorBranch(codeEntry);
            throw WasmException.create(Failure.OUT_OF_BOUNDS_TABLE_ACCESS);
        }
        if (length == 0) {
            return;
        }
        table.fill((int) offset, (int) length, value);
    }

    @TruffleBoundary
    private void memory_init(WasmInstance instance, int length, int source, long destination, int dataIndex, int memoryIndex) {
        final WasmMemory memory = memory(instance, memoryIndex);
        final WasmMemoryLibrary memoryLib = memoryLib(memoryIndex);
        final int dataOffset = instance.dataInstanceOffset(dataIndex);
        final int dataLength = instance.dataInstanceLength(dataIndex);
        if (checkOutOfBounds(source, length, dataLength)) {
            enterErrorBranch(codeEntry);
            throw WasmException.create(Failure.OUT_OF_BOUNDS_MEMORY_ACCESS);
        }
        memoryLib.initialize(memory, null, codeEntry.bytecode(), dataOffset + source, destination, length);
    }

    @TruffleBoundary
    private static void data_drop(WasmInstance instance, int dataIndex) {
        instance.dropDataInstance(dataIndex);
    }

    @TruffleBoundary
    private void memory_fill(WasmInstance instance, long length, int value, long offset, int memoryIndex) {
        final WasmMemory memory = memory(instance, memoryIndex);
        memoryLib(memoryIndex).fill(memory, this, offset, length, (byte) value);
    }

    @TruffleBoundary
    private void memory_copy(WasmInstance instance, long length, long source, long destination, int destMemoryIndex, int srcMemoryIndex) {
        final WasmMemory destMemory = memory(instance, destMemoryIndex);
        final WasmMemory srcMemory = memory(instance, srcMemoryIndex);
        memoryLib(destMemoryIndex).copyFrom(destMemory, this, srcMemory, source, destination, length);
    }

    private static long popTableIndex(VirtualFrame frame, int stackPointer, WasmTable table) {
        return table.hasIndexType64() ? popLong(frame, stackPointer) : Integer.toUnsignedLong(popInt(frame, stackPointer));
    }

    private static long popTableCopyLength(VirtualFrame frame, int stackPointer, WasmTable sourceTable, WasmTable destinationTable) {
        return sourceTable.hasIndexType64() && destinationTable.hasIndexType64() ? popLong(frame, stackPointer) : Integer.toUnsignedLong(popInt(frame, stackPointer));
    }

    private static void pushTableIndex(VirtualFrame frame, int stackPointer, WasmTable table, long value) {
        if (table.hasIndexType64()) {
            pushLong(frame, stackPointer, value);
        } else {
            pushInt(frame, stackPointer, (int) value);
        }
    }

    // Checkstyle: resume method name check

    private static boolean checkOutOfBounds(int offset, int size) {
        return offset < 0 || offset >= size;
    }

    private static boolean checkOutOfBounds(long offset, int size) {
        return Long.compareUnsigned(offset, Integer.toUnsignedLong(size)) >= 0;
    }

    private static boolean checkOutOfBounds(int offset, int length, int size) {
        return offset < 0 || length < 0 || offset + length < 0 || offset + length > size;
    }

    private static boolean checkOutOfBounds(long offset, long length, int size) {
        return Long.compareUnsigned(offset, Integer.toUnsignedLong(size)) > 0 ||
                        Long.compareUnsigned(length, Integer.toUnsignedLong(size) - offset) > 0;
    }

    private static boolean checkOutOfBounds(int offset, long length, int size) {
        return offset < 0 || length < 0 || offset + length < 0 || offset + length > size;
    }

    @ExplodeLoop
    private Object[] createArgumentsForCall(VirtualFrame frame, int functionTypeIndex, int numArgs, int stackPointerOffset) {
        CompilerAsserts.partialEvaluationConstant(numArgs);
        Object[] args = WasmArguments.createEmpty(numArgs);
        int stackPointer = stackPointerOffset;
        for (int i = numArgs - 1; i >= 0; --i) {
            stackPointer--;
            int type = module.symbolTable().functionTypeParamTypeAt(functionTypeIndex, i);
            CompilerAsserts.partialEvaluationConstant(type);
            Object arg = switch (type) {
                case WasmType.I32_TYPE -> popInt(frame, stackPointer);
                case WasmType.I64_TYPE -> popLong(frame, stackPointer);
                case WasmType.F32_TYPE -> popFloat(frame, stackPointer);
                case WasmType.F64_TYPE -> popDouble(frame, stackPointer);
                case WasmType.V128_TYPE -> vector128Ops().toVector128(popVector128(frame, stackPointer));
                default -> {
                    assert WasmType.isReferenceType(type);
                    yield popReference(frame, stackPointer);
                }
            };
            WasmArguments.setArgument(args, i, arg);
        }
        return args;
    }

    @TruffleBoundary
    private WasmRuntimeException createException(WasmTag tag, Object[] fields) {
        return new WasmRuntimeException(this, tag, fields);
    }

    private int exceptionHandlerTo(int handlerOffset) {
        return rawPeekI32(bytecode, handlerOffset + ExceptionHandler.TO_OFFSET);
    }

    private int exceptionHandlerType(int handlerOffset) {
        return rawPeekU8(bytecode, handlerOffset + ExceptionHandler.TYPE_OFFSET);
    }

    private int exceptionHandlerTagIndex(int handlerOffset) {
        return rawPeekI32(bytecode, handlerOffset + ExceptionHandler.TAG_OFFSET);
    }

    private int exceptionHandlerTarget(int handlerOffset) {
        return rawPeekI32(bytecode, handlerOffset + ExceptionHandler.TARGET_OFFSET);
    }

    private static boolean isLegacyCatchType(int catchType) {
        return catchType == ExceptionHandlerType.LEGACY_CATCH || catchType == ExceptionHandlerType.LEGACY_CATCH_ALL;
    }

    private int legacyCatchTargetDepth(int target) {
        CompilerAsserts.partialEvaluationConstant(target);
        final int opcode = rawPeekU8(bytecode, target);
        final int unwindOffset;
        switch (opcode) {
            case Bytecode.LABEL_U8:
                unwindOffset = target + 2;
                break;
            case Bytecode.LABEL_U16:
                unwindOffset = target + 3;
                break;
            case Bytecode.LABEL_I32:
                unwindOffset = target + 10;
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere("Legacy catch target must point to a label");
        }
        assert rawPeekU8(bytecode, unwindOffset) == Bytecode.MISC : "Missing legacy catch unwind helper";
        assert rawPeekU8(bytecode, unwindOffset + 1) == Bytecode.LEGACY_CATCH_UNWIND : "Missing legacy catch unwind helper";
        final int targetDepth = rawPeekI32(bytecode, unwindOffset + 2);
        assert targetDepth > 0 : "Legacy catch target depth must include the entered catch";
        return targetDepth;
    }

    private static int nextExceptionHandlerOffset(int handlerOffset) {
        return handlerOffset + ExceptionHandler.SIZE;
    }

    /**
     * Unwinds active legacy catches until the requested nesting depth remains in the reserved
     * legacy-catch frame slots. Does so by clearing all legacy catch slots above the target depth.
     * This makes the set of updated slots static, allowing the compiler to see consistent frame
     * states regardless of from which state (legacy catch depth) we enter this point.
     *
     * @param frame the frame holding the reserved legacy-catch slots
     * @param legacyCatchBase the first legacy-catch slot in the frame
     * @param stackBase the first slot after the legacy-catch slots in the frame
     * @param activeLegacyCatchCount the current number of active legacy catches
     * @param targetDepth the number of active legacy catches that should remain after unwinding
     */
    @ExplodeLoop
    private static int unwindLegacyCatchesToDepth(VirtualFrame frame, int legacyCatchBase, int stackBase, int activeLegacyCatchCount, int targetDepth) {
        CompilerAsserts.partialEvaluationConstant(legacyCatchBase);
        CompilerAsserts.partialEvaluationConstant(stackBase);
        CompilerAsserts.partialEvaluationConstant(targetDepth);
        assert activeLegacyCatchCount >= targetDepth;
        for (int slot = legacyCatchBase + targetDepth; slot < stackBase; slot++) {
            CompilerAsserts.partialEvaluationConstant(slot);
            WasmFrame.dropObject(frame, slot);
        }
        return targetDepth;
    }

    @ExplodeLoop
    private Object[] createFieldsForException(VirtualFrame frame, int functionTypeIndex, int numFields, int stackPointerOffset) {
        CompilerAsserts.partialEvaluationConstant(numFields);
        CompilerAsserts.partialEvaluationConstant(functionTypeIndex);
        CompilerAsserts.partialEvaluationConstant(stackPointerOffset);
        final Object[] fields = new Object[numFields];
        int stackPointer = stackPointerOffset;
        for (int i = numFields - 1; i >= 0; --i) {
            stackPointer--;
            int type = module.symbolTable().functionTypeParamTypeAt(functionTypeIndex, i);
            CompilerAsserts.partialEvaluationConstant(type);
            final Object arg = switch (type) {
                case WasmType.I32_TYPE -> popInt(frame, stackPointer);
                case WasmType.I64_TYPE -> popLong(frame, stackPointer);
                case WasmType.F32_TYPE -> popFloat(frame, stackPointer);
                case WasmType.F64_TYPE -> popDouble(frame, stackPointer);
                case WasmType.V128_TYPE -> vector128Ops().toVector128(popVector128(frame, stackPointer));
                default -> {
                    assert WasmType.isReferenceType(type);
                    yield popReference(frame, stackPointer);
                }
            };
            fields[i] = arg;
        }
        return fields;
    }

    @ExplodeLoop
    private void pushExceptionFields(VirtualFrame frame, WasmRuntimeException e, int functionTypeIndex, int numFields, int stackPointerOffset) {
        CompilerAsserts.partialEvaluationConstant(numFields);
        CompilerAsserts.partialEvaluationConstant(functionTypeIndex);
        CompilerAsserts.partialEvaluationConstant(stackPointerOffset);
        final Object[] fields = e.fields();
        int stackPointer = stackPointerOffset;
        for (int i = 0; i < numFields; i++) {
            int type = module.symbolTable().functionTypeParamTypeAt(functionTypeIndex, i);
            CompilerAsserts.partialEvaluationConstant(type);
            switch (type) {
                case WasmType.I32_TYPE -> pushInt(frame, stackPointer, (int) fields[i]);
                case WasmType.I64_TYPE -> pushLong(frame, stackPointer, (long) fields[i]);
                case WasmType.F32_TYPE -> pushFloat(frame, stackPointer, (float) fields[i]);
                case WasmType.F64_TYPE -> pushDouble(frame, stackPointer, (double) fields[i]);
                case WasmType.V128_TYPE -> pushVector128(frame, stackPointer, vector128Ops().fromVector128((Vector128) fields[i]));
                default -> {
                    assert WasmType.isReferenceType(type);
                    pushReference(frame, stackPointer, fields[i]);
                }
            }
            stackPointer++;
        }
    }

    @ExplodeLoop
    private WasmArray popArrayElements(VirtualFrame frame, DefinedType arrayType, int elemType, int length, int stackPointerOffset) {
        CompilerAsserts.partialEvaluationConstant(elemType);
        CompilerAsserts.partialEvaluationConstant(length);
        CompilerAsserts.partialEvaluationConstant(stackPointerOffset);
        int stackPointer = stackPointerOffset;
        switch (elemType) {
            case WasmType.I8_TYPE: {
                byte[] fixedArray = new byte[length];
                for (int i = length - 1; i >= 0; i--) {
                    fixedArray[i] = (byte) WasmFrame.popInt(frame, --stackPointer);
                }
                return new WasmInt8Array(arrayType, fixedArray);
            }
            case WasmType.I16_TYPE: {
                short[] fixedArray = new short[length];
                for (int i = length - 1; i >= 0; i--) {
                    fixedArray[i] = (short) WasmFrame.popInt(frame, --stackPointer);
                }
                return new WasmInt16Array(arrayType, fixedArray);
            }
            case WasmType.I32_TYPE: {
                int[] fixedArray = new int[length];
                for (int i = length - 1; i >= 0; i--) {
                    fixedArray[i] = WasmFrame.popInt(frame, --stackPointer);
                }
                return new WasmInt32Array(arrayType, fixedArray);
            }
            case WasmType.I64_TYPE: {
                long[] fixedArray = new long[length];
                for (int i = length - 1; i >= 0; i--) {
                    fixedArray[i] = WasmFrame.popLong(frame, --stackPointer);
                }
                return new WasmInt64Array(arrayType, fixedArray);
            }
            case WasmType.F32_TYPE: {
                float[] fixedArray = new float[length];
                for (int i = length - 1; i >= 0; i--) {
                    fixedArray[i] = WasmFrame.popFloat(frame, --stackPointer);
                }
                return new WasmFloat32Array(arrayType, fixedArray);
            }
            case WasmType.F64_TYPE: {
                double[] fixedArray = new double[length];
                for (int i = length - 1; i >= 0; i--) {
                    fixedArray[i] = WasmFrame.popDouble(frame, --stackPointer);
                }
                return new WasmFloat64Array(arrayType, fixedArray);
            }
            case WasmType.V128_TYPE: {
                byte[] fixedArray = new byte[length << 4];
                for (int i = length - 1; i >= 0; i--) {
                    vector128Ops().intoArray(WasmFrame.popVector128(frame, --stackPointer), fixedArray, i << 4);
                }
                return new WasmVec128Array(arrayType, length, fixedArray);
            }
            default: {
                Object[] fixedArray = new Object[length];
                for (int i = length - 1; i >= 0; i--) {
                    fixedArray[i] = WasmFrame.popReference(frame, --stackPointer);
                }
                return new WasmRefArray(arrayType, fixedArray);
            }
        }
    }

    @ExplodeLoop
    private void popStructFields(VirtualFrame frame, int structTypeIndex, WasmStruct struct, WasmStructAccess structAccess, int numFields, int stackPointerOffset) {
        CompilerAsserts.partialEvaluationConstant(structTypeIndex);
        CompilerAsserts.partialEvaluationConstant(structAccess);
        CompilerAsserts.partialEvaluationConstant(numFields);
        CompilerAsserts.partialEvaluationConstant(stackPointerOffset);
        int stackPointer = stackPointerOffset;
        for (int fieldIndex = numFields - 1; fieldIndex >= 0; fieldIndex--) {
            int fieldType = module.structTypeFieldTypeAt(structTypeIndex, fieldIndex);
            CompilerAsserts.partialEvaluationConstant(fieldType);
            StaticProperty property = structAccess.properties()[fieldIndex];
            switch (fieldType) {
                case WasmType.I8_TYPE -> property.setByte(struct, (byte) WasmFrame.popInt(frame, --stackPointer));
                case WasmType.I16_TYPE -> property.setShort(struct, (short) WasmFrame.popInt(frame, --stackPointer));
                case WasmType.I32_TYPE -> property.setInt(struct, WasmFrame.popInt(frame, --stackPointer));
                case WasmType.I64_TYPE -> property.setLong(struct, WasmFrame.popLong(frame, --stackPointer));
                case WasmType.F32_TYPE -> property.setFloat(struct, WasmFrame.popFloat(frame, --stackPointer));
                case WasmType.F64_TYPE -> property.setDouble(struct, WasmFrame.popDouble(frame, --stackPointer));
                case WasmType.V128_TYPE -> property.setObject(struct, vector128Ops().toVector128(WasmFrame.popVector128(frame, --stackPointer)));
                default -> {
                    assert WasmType.isReferenceType(fieldType);
                    property.setObject(struct, WasmFrame.popReference(frame, --stackPointer));
                }
            }
        }
    }

    @ExplodeLoop
    private void initStructDefaultFields(int structTypeIndex, WasmStruct struct, WasmStructAccess structAccess, int numFields) {
        CompilerAsserts.partialEvaluationConstant(structTypeIndex);
        CompilerAsserts.partialEvaluationConstant(structAccess);
        CompilerAsserts.partialEvaluationConstant(numFields);
        for (int fieldIndex = 0; fieldIndex < numFields; fieldIndex++) {
            int fieldType = module.structTypeFieldTypeAt(structTypeIndex, fieldIndex);
            CompilerAsserts.partialEvaluationConstant(fieldType);
            boolean fieldIsReferenceType = WasmType.isReferenceType(fieldType);
            CompilerAsserts.partialEvaluationConstant(fieldIsReferenceType);
            if (fieldIsReferenceType) {
                structAccess.properties()[fieldIndex].setObject(struct, WasmConstant.NULL);
            }
        }
    }

    private boolean runTimeTypeCheck(int expectedReferenceType, Object object) {
        boolean isNullable = WasmType.isNullable(expectedReferenceType);
        CompilerAsserts.partialEvaluationConstant(isNullable);
        if (isNullable && object == WasmConstant.NULL) {
            return true;
        }
        boolean isConcreteReferenceType = WasmType.isConcreteReferenceType(expectedReferenceType);
        CompilerAsserts.partialEvaluationConstant(isConcreteReferenceType);
        if (isConcreteReferenceType) {
            int expectedTypeIndex = WasmType.getTypeIndex(expectedReferenceType);
            CompilerAsserts.partialEvaluationConstant(expectedTypeIndex);
            return object instanceof WasmTypedHeapObject heapObject && runTimeConcreteTypeCheck(expectedTypeIndex, heapObject);
        } else {
            int expectedAbstractHeapType = WasmType.getAbstractHeapType(expectedReferenceType);
            CompilerAsserts.partialEvaluationConstant(expectedAbstractHeapType);
            return module.closedHeapTypeOf(expectedAbstractHeapType).matchesValue(object);
        }
    }

    /**
     * This is an optimized version of {@link DefinedType#isSubtypeOf(DefinedType)} that makes use
     * of the type equivalence classes that are constructed after linking.
     */
    private boolean runTimeConcreteTypeCheck(int expectedTypeIndex, WasmTypedHeapObject heapObject) {
        DefinedType expectedType = module.closedTypeAt(expectedTypeIndex);
        DefinedType actualType = heapObject.type();
        if (actualType.typeEquivalenceClass() == expectedType.typeEquivalenceClass()) {
            return true;
        } else if (expectedType.isFinal()) {
            return false;
        } else {
            codeEntry.subtypingBranch();
            do {
                actualType = (DefinedType) actualType.superType();
            } while (actualType != null && actualType.typeEquivalenceClass() != expectedType.typeEquivalenceClass());
            return actualType != null;
        }
    }

    /**
     * Populates the stack with the result values of the current block (the one we are escaping
     * from). Reset the stack pointer to the target block stack pointer.
     *
     * @param frame The current frame.
     * @param stackPointer The current stack pointer.
     * @param targetStackPointer The stack pointer of the target block.
     * @param targetResultCount The result value count of the target block.
     */
    @ExplodeLoop
    private static void unwindPrimitiveStack(VirtualFrame frame, int stackPointer, int targetStackPointer, int targetResultCount) {
        CompilerAsserts.partialEvaluationConstant(stackPointer);
        CompilerAsserts.partialEvaluationConstant(targetResultCount);
        for (int i = 0; i < targetResultCount; ++i) {
            WasmFrame.copyPrimitive(frame, stackPointer + i - targetResultCount, targetStackPointer + i);
        }
    }

    @ExplodeLoop
    private static void unwindObjectStack(VirtualFrame frame, int stackPointer, int targetStackPointer, int targetResultCount) {
        CompilerAsserts.partialEvaluationConstant(stackPointer);
        CompilerAsserts.partialEvaluationConstant(targetResultCount);
        for (int i = 0; i < targetResultCount; ++i) {
            WasmFrame.copyObject(frame, stackPointer + i - targetResultCount, targetStackPointer + i);
        }
    }

    @ExplodeLoop
    private static void unwindStack(VirtualFrame frame, int stackPointer, int targetStackPointer, int targetResultCount) {
        CompilerAsserts.partialEvaluationConstant(stackPointer);
        CompilerAsserts.partialEvaluationConstant(targetResultCount);
        for (int i = 0; i < targetResultCount; ++i) {
            WasmFrame.copy(frame, stackPointer + i - targetResultCount, targetStackPointer + i);
        }
    }

    @ExplodeLoop
    private static void dropStack(VirtualFrame frame, int stackPointer, int targetStackPointer) {
        CompilerAsserts.partialEvaluationConstant(stackPointer);
        CompilerAsserts.partialEvaluationConstant(targetStackPointer);
        for (int i = targetStackPointer; i < stackPointer; ++i) {
            drop(frame, i);
        }
    }

    private static final int MAX_PROFILE_VALUE = 0x0000_00ff;
    private static final int MAX_TABLE_PROFILE_VALUE = 0x0000_ffff;

    @SuppressWarnings("all") // "The parameter condition should not be assigned."
    private static boolean profileCondition(byte[] data, final int profileOffset, boolean condition) {
        int t = rawPeekU8(data, profileOffset);
        int f = rawPeekU8(data, profileOffset + 1);
        if (condition) {
            if (t == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (CompilerDirectives.inInterpreter()) {
                if (t < MAX_PROFILE_VALUE) {
                    t++;
                } else {
                    // halve count rounding up, must never go from 1 to 0.
                    f = (f >>> 1) + (f & 0x1);
                    t = (MAX_PROFILE_VALUE >>> 1) + 1;
                    data[profileOffset + 1] = (byte) f;
                }
                data[profileOffset] = (byte) t;
                return condition;
            } else {
                if (f == 0) {
                    // Make this branch fold during PE
                    condition = true;
                }
            }
        } else {
            if (f == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (CompilerDirectives.inInterpreter()) {
                if (f < MAX_PROFILE_VALUE) {
                    f++;
                } else {
                    // halve count rounding up, must never go from 1 to 0.
                    t = (t >>> 1) + (t & 0x1);
                    f = (MAX_PROFILE_VALUE >>> 1) + 1;
                    data[profileOffset] = (byte) t;
                }
                data[profileOffset + 1] = (byte) f;
                return condition;
            } else {
                if (t == 0) {
                    // Make this branch fold during PE
                    condition = false;
                }
            }
        }
        return CompilerDirectives.injectBranchProbability((double) t / (double) (t + f), condition);
    }

    private static void updateBranchTableProfile(byte[] data, final int counterOffset, final int profileOffset) {
        CompilerAsserts.neverPartOfCompilation();
        int counter = rawPeekU16(data, counterOffset);
        int profile = rawPeekU16(data, profileOffset);
        /*
         * Even if the total hit counter has already reached the limit, we need to increment the
         * branch profile counter from 0 to 1 iff it's still 0 to mark the branch as having been
         * taken at least once, to prevent recurrent deoptimizations due to profileBranchTable
         * assuming that a value of 0 means the branch has never been reached.
         *
         * Similarly, we need to make sure we never increase any branch counter to the max value,
         * otherwise we can get into a situation where both the branch and the total counter values
         * are at the max value that we cannot recover from since we never decrease counter values;
         * profileBranchTable would then deoptimize every time that branch is not taken (see below).
         */
        assert profile != MAX_TABLE_PROFILE_VALUE;
        if (counter < MAX_TABLE_PROFILE_VALUE) {
            BinaryStreamParser.writeU16(data, counterOffset, counter + 1);
        }
        if ((counter < MAX_TABLE_PROFILE_VALUE || profile == 0) && (profile < MAX_TABLE_PROFILE_VALUE - 1)) {
            BinaryStreamParser.writeU16(data, profileOffset, profile + 1);
        }
    }

    private static boolean profileBranchTable(byte[] data, final int counterOffset, final int profile, int precedingSum, boolean condition) {
        int sum = rawPeekU16(data, counterOffset);
        boolean val = condition;
        if (val) {
            if (profile == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (profile == sum) {
                // Make this branch fold during PE
                val = true;
            }
        } else {
            if (profile == sum) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (profile == 0) {
                // If the profile is 0 there is no need for the calculation below. Additionally, the
                // predecessor probability may be 1 which could lead to a division by 0 later.
                return CompilerDirectives.injectBranchProbability(0.0, false);
            }
        }
        /*
         * The probabilities gathered by profiling are independent of each other. Since we are
         * injecting probabilities into a cascade of if statements, we need to adjust them. The
         * injected probability should indicate the probability that this if statement will be
         * entered given that the previous ones have not been entered. To do that, we keep track of
         * the probability that the preceding if statements have been entered and adjust this
         * statement's probability accordingly. When the compiler decides to generate an
         * IntegerSwitch node from the cascade of if statements, it converts the probabilities back
         * to independent ones.
         *
         * The adjusted probability can be calculated from the original probability as follows:
         *
         * branchProbability = profile / sum; predecessorProbability = precedingSum / sum;
         * adjustedProbability = branchProbability / (1 - predecessorProbability);
         *
         * Safer version of the above that also handles precedingSum >= sum, i.e. when the sum of
         * the preceding branch counters exceeds the total hit counter (e.g. due to saturation):
         */
        final double adjustedProbability = (double) profile / (double) (precedingSum < sum ? sum - precedingSum : sum);
        return CompilerDirectives.injectBranchProbability(Math.min(adjustedProbability, 1.0), val);
    }

    private int pushDirectCallResult(VirtualFrame frame, int stackPointer, WasmFunction function, Object result, WasmLanguage language) {
        final int resultCount = function.resultCount();
        CompilerAsserts.partialEvaluationConstant(resultCount);
        if (resultCount == 0) {
            return stackPointer;
        } else if (resultCount == 1) {
            final int resultType = function.resultTypeAt(0);
            pushResult(frame, stackPointer, resultType, result);
            return stackPointer + 1;
        } else {
            final int functionTypeIndex = function.typeIndex();
            extractMultiValueResult(frame, stackPointer, result, resultCount, functionTypeIndex, language);
            return stackPointer + resultCount;
        }
    }

    private int pushIndirectCallResult(VirtualFrame frame, int stackPointer, int expectedFunctionTypeIndex, Object result, WasmLanguage language) {
        final int resultCount = module.symbolTable().functionTypeResultCount(expectedFunctionTypeIndex);
        CompilerAsserts.partialEvaluationConstant(resultCount);
        if (resultCount == 0) {
            return stackPointer;
        } else if (resultCount == 1) {
            final int resultType = module.symbolTable().functionTypeResultTypeAt(expectedFunctionTypeIndex, 0);
            pushResult(frame, stackPointer, resultType, result);
            return stackPointer + 1;
        } else {
            extractMultiValueResult(frame, stackPointer, result, resultCount, expectedFunctionTypeIndex, language);
            return stackPointer + resultCount;
        }
    }

    private void pushResult(VirtualFrame frame, int stackPointer, int resultType, Object result) {
        CompilerAsserts.partialEvaluationConstant(resultType);
        switch (resultType) {
            case WasmType.I32_TYPE -> pushInt(frame, stackPointer, (int) result);
            case WasmType.I64_TYPE -> pushLong(frame, stackPointer, (long) result);
            case WasmType.F32_TYPE -> pushFloat(frame, stackPointer, (float) result);
            case WasmType.F64_TYPE -> pushDouble(frame, stackPointer, (double) result);
            case WasmType.V128_TYPE -> pushVector128(frame, stackPointer, vector128Ops().fromVector128((Vector128) result));
            default -> {
                assert WasmType.isReferenceType(resultType);
                pushReference(frame, stackPointer, result);
            }
        }
    }

    /**
     * Extracts the multi-value result from the multi-value stack of the context. The result values
     * are put onto the value stack.
     *
     * @param frame The current frame.
     * @param stackPointer The current stack pointer.
     * @param result The result of the function call.
     * @param resultCount The expected number or result values.
     * @param functionTypeIndex The function type index of the called function.
     */
    @ExplodeLoop
    private void extractMultiValueResult(VirtualFrame frame, int stackPointer, Object result, int resultCount, int functionTypeIndex, WasmLanguage language) {
        CompilerAsserts.partialEvaluationConstant(resultCount);
        assert result == WasmConstant.MULTI_VALUE : result;
        final var multiValueStack = language.multiValueStack();
        final long[] primitiveMultiValueStack = multiValueStack.primitiveStack();
        final Object[] objectMultiValueStack = multiValueStack.objectStack();
        for (int i = 0; i < resultCount; i++) {
            final int resultType = module.symbolTable().functionTypeResultTypeAt(functionTypeIndex, i);
            CompilerAsserts.partialEvaluationConstant(resultType);
            switch (resultType) {
                case WasmType.I32_TYPE -> pushInt(frame, stackPointer + i, (int) primitiveMultiValueStack[i]);
                case WasmType.I64_TYPE -> pushLong(frame, stackPointer + i, primitiveMultiValueStack[i]);
                case WasmType.F32_TYPE -> pushFloat(frame, stackPointer + i, Float.intBitsToFloat((int) primitiveMultiValueStack[i]));
                case WasmType.F64_TYPE -> pushDouble(frame, stackPointer + i, Double.longBitsToDouble(primitiveMultiValueStack[i]));
                case WasmType.V128_TYPE -> {
                    pushVector128(frame, stackPointer + i, vector128Ops().fromVector128((Vector128) objectMultiValueStack[i]));
                    objectMultiValueStack[i] = null;
                }
                default -> {
                    assert WasmType.isReferenceType(resultType);
                    pushReference(frame, stackPointer + i, objectMultiValueStack[i]);
                    objectMultiValueStack[i] = null;
                }
            }
        }
    }
}
