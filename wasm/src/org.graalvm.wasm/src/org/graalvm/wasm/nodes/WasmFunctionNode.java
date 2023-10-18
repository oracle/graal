/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.wasm.nodes.WasmFrame.dropPrimitive;
import static org.graalvm.wasm.nodes.WasmFrame.dropReference;
import static org.graalvm.wasm.nodes.WasmFrame.popBoolean;
import static org.graalvm.wasm.nodes.WasmFrame.popDouble;
import static org.graalvm.wasm.nodes.WasmFrame.popFloat;
import static org.graalvm.wasm.nodes.WasmFrame.popInt;
import static org.graalvm.wasm.nodes.WasmFrame.popLong;
import static org.graalvm.wasm.nodes.WasmFrame.popReference;
import static org.graalvm.wasm.nodes.WasmFrame.pushDouble;
import static org.graalvm.wasm.nodes.WasmFrame.pushFloat;
import static org.graalvm.wasm.nodes.WasmFrame.pushInt;
import static org.graalvm.wasm.nodes.WasmFrame.pushLong;
import static org.graalvm.wasm.nodes.WasmFrame.pushReference;

import org.graalvm.wasm.BinaryStreamParser;
import org.graalvm.wasm.SymbolTable;
import org.graalvm.wasm.WasmArguments;
import org.graalvm.wasm.WasmCodeEntry;
import org.graalvm.wasm.WasmConstant;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmFunction;
import org.graalvm.wasm.WasmFunctionInstance;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmMath;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmTable;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.constants.Bytecode;
import org.graalvm.wasm.constants.BytecodeBitEncoding;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.memory.WasmMemory;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;

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
public final class WasmFunctionNode extends Node implements BytecodeOSRNode {
    private static final float MIN_FLOAT_TRUNCATABLE_TO_INT = Integer.MIN_VALUE;
    private static final float MAX_FLOAT_TRUNCATABLE_TO_INT = 2147483520f;
    private static final float MIN_FLOAT_TRUNCATABLE_TO_U_INT = -0.99999994f;
    private static final float MAX_FLOAT_TRUNCATABLE_TO_U_INT = 4294967040f;

    private static final double MIN_DOUBLE_TRUNCATABLE_TO_INT = -2147483648.9999997;
    private static final double MAX_DOUBLE_TRUNCATABLE_TO_INT = 2147483647.9999998;
    private static final double MIN_DOUBLE_TRUNCATABLE_TO_U_INT = -0.9999999999999999;
    private static final double MAX_DOUBLE_TRUNCATABLE_TO_U_INT = 4294967295.9999995;

    private static final float MIN_FLOAT_TRUNCATABLE_TO_LONG = Long.MIN_VALUE;
    private static final float MAX_FLOAT_TRUNCATABLE_TO_LONG = 9223371487098961900.0f;
    private static final float MIN_FLOAT_TRUNCATABLE_TO_U_LONG = MIN_FLOAT_TRUNCATABLE_TO_U_INT;
    private static final float MAX_FLOAT_TRUNCATABLE_TO_U_LONG = 18446742974197924000.0f;

    private static final double MIN_DOUBLE_TRUNCATABLE_TO_LONG = Long.MIN_VALUE;
    private static final double MAX_DOUBLE_TRUNCATABLE_TO_LONG = 9223372036854774800.0;
    private static final double MIN_DOUBLE_TRUNCATABLE_TO_U_LONG = MIN_DOUBLE_TRUNCATABLE_TO_U_INT;
    private static final double MAX_DOUBLE_TRUNCATABLE_TO_U_LONG = 18446744073709550000.0;

    private static final int REPORT_LOOP_STRIDE = 1 << 8;

    static {
        assert Integer.bitCount(REPORT_LOOP_STRIDE) == 1 : "must be a power of 2";
    }

    private final WasmModule module;
    private final WasmCodeEntry codeEntry;

    @Children private Node[] callNodes;
    @CompilationFinal private Object osrMetadata;

    @CompilationFinal private int bytecodeStartOffset;
    @CompilationFinal private int bytecodeEndOffset;
    @CompilationFinal(dimensions = 1) private byte[] bytecode;
    @CompilationFinal private WasmNotifyFunction notifyFunction;

    public WasmFunctionNode(WasmModule module, WasmCodeEntry codeEntry, int bytecodeStartOffset, int bytecodeEndOffset) {
        this.module = module;
        this.codeEntry = codeEntry;
        this.bytecodeStartOffset = bytecodeStartOffset;
        this.bytecodeEndOffset = bytecodeEndOffset;
        this.bytecode = codeEntry.bytecode();
    }

    @SuppressWarnings("hiding")
    public void initializeCallNodes(Node[] callNodes) {
        assert this.callNodes == null;
        this.callNodes = callNodes;
    }

    public int startOffset() {
        return bytecodeStartOffset;
    }

    private void enterErrorBranch() {
        codeEntry.errorBranch();
    }

    @SuppressWarnings("hiding")
    void updateBytecode(byte[] bytecode, int bytecodeStartOffset, int bytecodeEndOffset, WasmNotifyFunction notifyFunction) {
        this.bytecode = bytecode;
        this.bytecodeStartOffset = bytecodeStartOffset;
        this.bytecodeEndOffset = bytecodeEndOffset;
        this.notifyFunction = notifyFunction;
    }

    private WasmMemory memory(WasmInstance instance) {
        return memory(instance, 0);
    }

    private WasmMemory memory(WasmInstance instance, int index) {
        return module.memory(instance, index);
    }

    // region OSR support
    private static final class WasmOSRInterpreterState {
        final int stackPointer;
        final int line;

        WasmOSRInterpreterState(int stackPointer, int line) {
            this.stackPointer = stackPointer;
            this.line = line;
        }
    }

    @Override
    public Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
        WasmOSRInterpreterState state = (WasmOSRInterpreterState) interpreterState;
        WasmContext context = WasmContext.get(this);
        WasmInstance instance = ((WasmRootNode) getRootNode()).instance(osrFrame);
        return executeBodyFromOffset(context, instance, osrFrame, target, state.stackPointer, state.line);
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

    public void execute(VirtualFrame frame, WasmContext context, WasmInstance instance) {
        executeBodyFromOffset(context, instance, frame, bytecodeStartOffset, codeEntry.localCount(), -1);
    }

    @BytecodeInterpreterSwitch
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    @SuppressWarnings({"UnusedAssignment", "hiding"})
    public Object executeBodyFromOffset(WasmContext context, WasmInstance instance, VirtualFrame frame, int startOffset, int startStackPointer, int startLine) {
        final int localCount = codeEntry.localCount();
        final byte[] bytecode = this.bytecode;

        // The back edge count is stored in an object, since else the MERGE_EXPLODE policy would
        // interpret this as a constant value in every loop iteration. This would prevent the
        // compiler form merging branches, since every change to the back edge count would generate
        // a new unique state.
        final BackEdgeCounter backEdgeCounter = new BackEdgeCounter();

        int offset = startOffset;
        int stackPointer = startStackPointer;
        int line = startLine;

        // Note: The module may not have any memories.
        final WasmMemory zeroMemory = module.memoryCount() == 0 ? null : memory(instance);

        check(bytecode.length, (1 << 31) - 1);

        int opcode = Bytecode.UNREACHABLE;
        loop: while (offset < bytecodeEndOffset) {
            opcode = rawPeekU8(bytecode, offset);
            offset++;
            CompilerAsserts.partialEvaluationConstant(offset);
            switch (opcode) {
                case Bytecode.UNREACHABLE:
                    enterErrorBranch();
                    throw WasmException.create(Failure.UNREACHABLE, this);
                case Bytecode.NOP:
                    break;
                case Bytecode.SKIP_LABEL_U8:
                case Bytecode.SKIP_LABEL_U16:
                case Bytecode.SKIP_LABEL_I32:
                    offset += opcode;
                    break;
                case Bytecode.RETURN: {
                    // A return statement causes the termination of the current function, i.e.
                    // causes the execution to resume after the instruction that invoked
                    // the current frame.
                    if (backEdgeCounter.count > 0) {
                        LoopNode.reportLoopCount(this, backEdgeCounter.count);
                    }
                    final int resultCount = codeEntry.resultCount();
                    unwindStack(frame, stackPointer, localCount, resultCount);
                    dropStack(frame, stackPointer, localCount + resultCount);
                    return WasmConstant.RETURN_VALUE;
                }
                case Bytecode.LABEL_U8: {
                    final int value = rawPeekU8(bytecode, offset);
                    offset++;
                    final int stackSize = (value & BytecodeBitEncoding.LABEL_U8_STACK_VALUE);
                    final int targetStackPointer = stackSize + localCount;
                    switch ((value & BytecodeBitEncoding.LABEL_U8_RESULT_MASK)) {
                        case BytecodeBitEncoding.LABEL_U8_RESULT_NUM:
                            WasmFrame.copyPrimitive(frame, stackPointer - 1, targetStackPointer);
                            dropStack(frame, stackPointer, targetStackPointer + 1);
                            stackPointer = targetStackPointer + 1;
                            break;
                        case BytecodeBitEncoding.LABEL_U8_RESULT_REF:
                            WasmFrame.copyReference(frame, stackPointer - 1, targetStackPointer);
                            dropStack(frame, stackPointer, targetStackPointer + 1);
                            stackPointer = targetStackPointer + 1;
                            break;
                        default:
                            dropStack(frame, stackPointer, targetStackPointer);
                            stackPointer = targetStackPointer;
                            break;
                    }
                    break;
                }
                case Bytecode.LABEL_U16: {
                    final int value = rawPeekU16(bytecode, offset);
                    final int stackSize = rawPeekU8(bytecode, offset + 1);
                    offset += 2;
                    final int resultCount = (value & BytecodeBitEncoding.LABEL_U16_RESULT_VALUE);
                    final int resultType = (value & BytecodeBitEncoding.LABEL_U16_RESULT_TYPE_MASK);
                    final int targetStackPointer = stackSize + localCount;
                    switch (resultType) {
                        case BytecodeBitEncoding.LABEL_U16_RESULT_TYPE_NUM:
                            unwindPrimitiveStack(frame, stackPointer, targetStackPointer, resultCount);
                            break;
                        case BytecodeBitEncoding.LABEL_U16_RESULT_TYPE_REF:
                            unwindReferenceStack(frame, stackPointer, targetStackPointer, resultCount);
                            break;
                        case BytecodeBitEncoding.LABEL_U16_RESULT_TYPE_MIX:
                            unwindStack(frame, stackPointer, targetStackPointer, resultCount);
                            break;
                    }
                    dropStack(frame, stackPointer, targetStackPointer + resultCount);
                    stackPointer = targetStackPointer + resultCount;
                    break;
                }
                case Bytecode.LABEL_I32: {
                    final int resultType = rawPeekU8(bytecode, offset);
                    final int resultCount = rawPeekI32(bytecode, offset + 1);
                    final int stackSize = rawPeekI32(bytecode, offset + 5);
                    offset += 9;
                    final int targetStackPointer = stackSize + localCount;
                    switch (resultType) {
                        case BytecodeBitEncoding.LABEL_RESULT_TYPE_NUM:
                            unwindPrimitiveStack(frame, stackPointer, targetStackPointer, resultCount);
                            break;
                        case BytecodeBitEncoding.LABEL_RESULT_TYPE_REF:
                            unwindReferenceStack(frame, stackPointer, targetStackPointer, resultCount);
                            break;
                        case BytecodeBitEncoding.LABEL_RESULT_TYPE_MIX:
                            unwindStack(frame, stackPointer, targetStackPointer, resultCount);
                            break;
                    }
                    dropStack(frame, stackPointer, targetStackPointer + resultCount);
                    stackPointer = targetStackPointer + resultCount;
                    break;
                }
                case Bytecode.LOOP: {
                    if (CompilerDirectives.hasNextTier() && ++backEdgeCounter.count >= REPORT_LOOP_STRIDE) {
                        LoopNode.reportLoopCount(this, REPORT_LOOP_STRIDE);
                        backEdgeCounter.count = 0;
                    }
                    if (CompilerDirectives.inInterpreter() && BytecodeOSRNode.pollOSRBackEdge(this)) {
                        Object result = BytecodeOSRNode.tryOSR(this, offset, new WasmOSRInterpreterState(stackPointer, line), null, frame);
                        if (result != null) {
                            if (backEdgeCounter.count > 0) {
                                LoopNode.reportLoopCount(this, backEdgeCounter.count);
                            }
                            return result;
                        }
                    }
                    break;
                }
                case Bytecode.IF: {
                    stackPointer--;
                    if (profileCondition(bytecode, offset + 4, popBoolean(frame, stackPointer))) {
                        offset += 6;
                    } else {
                        final int offsetDelta = rawPeekI32(bytecode, offset);
                        offset += offsetDelta;
                    }
                    break;
                }
                case Bytecode.BR_U8: {
                    final int offsetDelta = rawPeekU8(bytecode, offset);
                    // BR_U8 encodes the back jump value as a positive byte value. BR_U8 can never
                    // perform a forward jump.
                    offset -= offsetDelta;
                    break;
                }
                case Bytecode.BR_I32: {
                    final int offsetDelta = rawPeekI32(bytecode, offset);
                    offset += offsetDelta;
                    break;
                }
                case Bytecode.BR_IF_U8: {
                    stackPointer--;
                    if (profileCondition(bytecode, offset + 1, popBoolean(frame, stackPointer))) {
                        final int offsetDelta = rawPeekU8(bytecode, offset);
                        // BR_IF_U8 encodes the back jump value as a positive byte value. BR_IF_U8
                        // can never perform a forward jump.
                        offset -= offsetDelta;
                    } else {
                        offset += 3;
                    }
                    break;
                }
                case Bytecode.BR_IF_I32: {
                    stackPointer--;
                    if (profileCondition(bytecode, offset + 4, popBoolean(frame, stackPointer))) {
                        final int offsetDelta = rawPeekI32(bytecode, offset);
                        offset += offsetDelta;
                    } else {
                        offset += 6;
                    }
                    break;
                }
                case Bytecode.BR_TABLE_U8: {
                    stackPointer--;
                    int index = popInt(frame, stackPointer);
                    final int size = rawPeekU8(bytecode, offset);
                    if (index < 0 || index >= size) {
                        // If unsigned index is larger or equal to the table size use the
                        // default (last) index.
                        index = size - 1;
                    }

                    if (CompilerDirectives.inInterpreter()) {
                        final int indexOffset = offset + 3 + index * 6;
                        updateBranchTableProfile(bytecode, offset + 1, indexOffset + 4);
                        final int offsetDelta = rawPeekI32(bytecode, indexOffset);
                        offset = indexOffset + offsetDelta;
                        break;
                    } else {
                        // This loop is implemented to create a separate path for every index. This
                        // guarantees that all values inside the if statement are treated as compile
                        // time constants, since the loop is unrolled.
                        for (int i = 0; i < size; i++) {
                            final int indexOffset = offset + 3 + i * 6;
                            if (profileBranchTable(bytecode, offset + 1, indexOffset + 4, i == index)) {
                                final int offsetDelta = rawPeekI32(bytecode, indexOffset);
                                offset = indexOffset + offsetDelta;
                                continue loop;
                            }
                        }
                    }
                    enterErrorBranch();
                    throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, this, "Should not reach here");
                }
                case Bytecode.BR_TABLE_I32: {
                    stackPointer--;
                    int index = popInt(frame, stackPointer);
                    final int size = rawPeekI32(bytecode, offset);
                    if (index < 0 || index >= size) {
                        // If unsigned index is larger or equal to the table size use the
                        // default (last) index.
                        index = size - 1;
                    }

                    if (CompilerDirectives.inInterpreter()) {
                        final int indexOffset = offset + 6 + index * 6;
                        updateBranchTableProfile(bytecode, offset + 4, indexOffset + 4);
                        final int offsetDelta = rawPeekI32(bytecode, indexOffset);
                        offset = indexOffset + offsetDelta;
                        break;
                    } else {
                        // This loop is implemented to create a separate path for every index. This
                        // guarantees that all values inside the if statement are treated as compile
                        // time constants, since the loop is unrolled.
                        for (int i = 0; i < size; i++) {
                            final int indexOffset = offset + 6 + i * 6;
                            if (profileBranchTable(bytecode, offset + 1, indexOffset + 4, i == index)) {
                                final int offsetDelta = rawPeekI32(bytecode, indexOffset);
                                offset = indexOffset + offsetDelta;
                                continue loop;
                            }
                        }
                    }
                    enterErrorBranch();
                    throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, this, "Should not reach here");
                }
                case Bytecode.CALL_U8:
                case Bytecode.CALL_I32: {
                    final int callNodeIndex;
                    final int functionIndex;
                    if (opcode == Bytecode.CALL_U8) {
                        callNodeIndex = rawPeekU8(bytecode, offset);
                        functionIndex = rawPeekU8(bytecode, offset + 1);
                        offset += 2;
                    } else {
                        callNodeIndex = rawPeekI32(bytecode, offset);
                        functionIndex = rawPeekI32(bytecode, offset + 4);
                        offset += 8;
                    }

                    WasmFunction function = module.symbolTable().function(functionIndex);
                    int paramCount = function.paramCount();

                    Object[] args = createArgumentsForCall(frame, function.typeIndex(), paramCount, stackPointer);
                    stackPointer -= paramCount;

                    Object result = executeDirectCall(instance, callNodeIndex, function, args);

                    final int resultCount = function.resultCount();
                    CompilerAsserts.partialEvaluationConstant(resultCount);
                    if (resultCount == 0) {
                        break;
                    } else if (resultCount == 1) {
                        final byte resultType = function.resultTypeAt(0);
                        CompilerAsserts.partialEvaluationConstant(resultType);
                        switch (resultType) {
                            case WasmType.I32_TYPE: {
                                pushInt(frame, stackPointer, (int) result);
                                stackPointer++;
                                break;
                            }
                            case WasmType.I64_TYPE: {
                                pushLong(frame, stackPointer, (long) result);
                                stackPointer++;
                                break;
                            }
                            case WasmType.F32_TYPE: {
                                pushFloat(frame, stackPointer, (float) result);
                                stackPointer++;
                                break;
                            }
                            case WasmType.F64_TYPE: {
                                pushDouble(frame, stackPointer, (double) result);
                                stackPointer++;
                                break;
                            }
                            case WasmType.FUNCREF_TYPE:
                            case WasmType.EXTERNREF_TYPE:
                                pushReference(frame, stackPointer, result);
                                stackPointer++;
                                break;
                            default: {
                                throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Unknown result type: %d", resultType);
                            }
                        }
                        break;
                    } else {
                        extractMultiValueResult(frame, stackPointer, result, resultCount, function.typeIndex());
                        stackPointer += resultCount;
                        break;
                    }
                }
                case Bytecode.CALL_INDIRECT_U8:
                case Bytecode.CALL_INDIRECT_I32: {
                    // Extract the function object.
                    stackPointer--;
                    final SymbolTable symtab = module.symbolTable();

                    final int callNodeIndex;
                    final int expectedFunctionTypeIndex;
                    final int tableIndex;
                    final int profileOffset;
                    if (opcode == Bytecode.CALL_INDIRECT_U8) {
                        callNodeIndex = rawPeekU8(bytecode, offset);
                        expectedFunctionTypeIndex = rawPeekU8(bytecode, offset + 1);
                        tableIndex = rawPeekU8(bytecode, offset + 2);
                        profileOffset = offset + 3;
                        offset += 5;
                    } else {
                        callNodeIndex = rawPeekI32(bytecode, offset);
                        expectedFunctionTypeIndex = rawPeekI32(bytecode, offset + 4);
                        tableIndex = rawPeekI32(bytecode, offset + 8);
                        profileOffset = offset + 12;
                        offset += 14;
                    }
                    final WasmTable table = context.tables().table(instance.tableAddress(tableIndex));
                    final Object[] elements = table.elements();
                    final int elementIndex = popInt(frame, stackPointer);
                    if (elementIndex < 0 || elementIndex >= elements.length) {
                        enterErrorBranch();
                        throw WasmException.format(Failure.UNDEFINED_ELEMENT, this, "Element index '%d' out of table bounds.", elementIndex);
                    }
                    // Currently, table elements may only be functions.
                    // We can add a check here when this changes in the future.
                    final Object element = elements[elementIndex];
                    if (element == WasmConstant.NULL) {
                        enterErrorBranch();
                        throw WasmException.format(Failure.UNINITIALIZED_ELEMENT, this, "Table element at index %d is uninitialized.", elementIndex);
                    }
                    final WasmFunctionInstance functionInstance;
                    final WasmFunction function;
                    final CallTarget target;
                    final WasmContext functionInstanceContext;
                    if (element instanceof WasmFunctionInstance) {
                        functionInstance = (WasmFunctionInstance) element;
                        function = functionInstance.function();
                        target = functionInstance.target();
                        functionInstanceContext = functionInstance.context();
                    } else {
                        enterErrorBranch();
                        throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Unknown table element type: %s", element);
                    }

                    int expectedTypeEquivalenceClass = symtab.equivalenceClass(expectedFunctionTypeIndex);

                    // Validate that the function type matches the expected type.
                    final boolean functionFromCurrentContext = functionInstanceContext == context;

                    if (profileCondition(bytecode, profileOffset, functionFromCurrentContext)) {
                        // We can do a quick equivalence-class check.
                        if (expectedTypeEquivalenceClass != function.typeEquivalenceClass()) {
                            enterErrorBranch();
                            failFunctionTypeCheck(function, expectedFunctionTypeIndex);
                        }
                    } else {
                        // The table is coming from a different context, so do a slow check.
                        // If the Wasm function is set to null, then the check must be performed
                        // in the body of the function. This is done when the function is
                        // provided externally (e.g. comes from a different language).
                        if (function != null && !function.type().equals(symtab.typeAt(expectedFunctionTypeIndex))) {
                            enterErrorBranch();
                            failFunctionTypeCheck(function, expectedFunctionTypeIndex);
                        }
                    }

                    // Invoke the resolved function.
                    int paramCount = module.symbolTable().functionTypeParamCount(expectedFunctionTypeIndex);
                    Object[] args = createArgumentsForCall(frame, expectedFunctionTypeIndex, paramCount, stackPointer);
                    stackPointer -= paramCount;
                    WasmArguments.setModuleInstance(args, functionInstance.moduleInstance());

                    // Enter function's context when it is not from the current one
                    final boolean enterContext = !functionFromCurrentContext;
                    TruffleContext truffleContext;
                    Object prev;
                    if (enterContext) {
                        truffleContext = functionInstanceContext.environment().getContext();
                        prev = truffleContext.enter(this);
                    } else {
                        truffleContext = null;
                        prev = null;
                    }

                    final Object result;
                    try {
                        result = executeIndirectCallNode(callNodeIndex, target, args);
                    } finally {
                        if (enterContext) {
                            truffleContext.leave(this, prev);
                        }
                    }

                    final int resultCount = module.symbolTable().functionTypeResultCount(expectedFunctionTypeIndex);
                    CompilerAsserts.partialEvaluationConstant(resultCount);
                    if (resultCount == 0) {
                        break;
                    } else if (resultCount == 1) {
                        final byte resultType = module.symbolTable().functionTypeResultTypeAt(expectedFunctionTypeIndex, 0);
                        CompilerAsserts.partialEvaluationConstant(resultType);
                        switch (resultType) {
                            case WasmType.I32_TYPE: {
                                pushInt(frame, stackPointer, (int) result);
                                stackPointer++;
                                break;
                            }
                            case WasmType.I64_TYPE: {
                                pushLong(frame, stackPointer, (long) result);
                                stackPointer++;
                                break;
                            }
                            case WasmType.F32_TYPE: {
                                pushFloat(frame, stackPointer, (float) result);
                                stackPointer++;
                                break;
                            }
                            case WasmType.F64_TYPE: {
                                pushDouble(frame, stackPointer, (double) result);
                                stackPointer++;
                                break;
                            }
                            case WasmType.FUNCREF_TYPE:
                            case WasmType.EXTERNREF_TYPE: {
                                pushReference(frame, stackPointer, result);
                                stackPointer++;
                                break;
                            }
                            default: {
                                throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Unknown result type: %d", resultType);
                            }
                        }
                        break;
                    } else {
                        extractMultiValueResult(frame, stackPointer, result, resultCount, expectedFunctionTypeIndex);
                        stackPointer += resultCount;
                        break;
                    }
                }
                case Bytecode.DROP: {
                    stackPointer--;
                    dropPrimitive(frame, stackPointer);
                    break;
                }
                case Bytecode.DROP_REF: {
                    stackPointer--;
                    dropReference(frame, stackPointer);
                    break;
                }
                case Bytecode.SELECT: {
                    if (popBoolean(frame, stackPointer - 1)) {
                        drop(frame, stackPointer - 2);
                    } else {
                        WasmFrame.copyPrimitive(frame, stackPointer - 2, stackPointer - 3);
                        dropPrimitive(frame, stackPointer - 2);
                    }
                    stackPointer -= 2;
                    break;
                }
                case Bytecode.SELECT_REF: {
                    if (popBoolean(frame, stackPointer - 1)) {
                        dropReference(frame, stackPointer - 2);
                    } else {
                        WasmFrame.copyReference(frame, stackPointer - 2, stackPointer - 3);
                        dropReference(frame, stackPointer - 2);
                    }
                    stackPointer -= 2;
                    break;
                }
                case Bytecode.LOCAL_GET_U8: {
                    final int index = rawPeekU8(bytecode, offset);
                    offset++;
                    local_get(frame, stackPointer, index);
                    stackPointer++;
                    break;
                }
                case Bytecode.LOCAL_GET_I32: {
                    final int index = rawPeekI32(bytecode, offset);
                    offset += 4;
                    local_get(frame, stackPointer, index);
                    stackPointer++;
                    break;
                }
                case Bytecode.LOCAL_GET_REF_U8: {
                    final int index = rawPeekU8(bytecode, offset);
                    offset++;
                    local_get_ref(frame, stackPointer, index);
                    stackPointer++;
                    break;
                }
                case Bytecode.LOCAL_GET_REF_I32: {
                    final int index = rawPeekI32(bytecode, offset);
                    offset += 4;
                    local_get_ref(frame, stackPointer, index);
                    stackPointer++;
                    break;
                }
                case Bytecode.LOCAL_SET_U8: {
                    final int index = rawPeekU8(bytecode, offset);
                    offset++;
                    stackPointer--;
                    local_set(frame, stackPointer, index);
                    break;
                }
                case Bytecode.LOCAL_SET_I32: {
                    final int index = rawPeekI32(bytecode, offset);
                    offset += 4;
                    stackPointer--;
                    local_set(frame, stackPointer, index);
                    break;
                }
                case Bytecode.LOCAL_SET_REF_U8: {
                    final int index = rawPeekU8(bytecode, offset);
                    offset++;
                    stackPointer--;
                    local_set_ref(frame, stackPointer, index);
                    break;
                }
                case Bytecode.LOCAL_SET_REF_I32: {
                    final int index = rawPeekI32(bytecode, offset);
                    offset += 4;
                    stackPointer--;
                    local_set_ref(frame, stackPointer, index);
                    break;
                }
                case Bytecode.LOCAL_TEE_U8: {
                    final int index = rawPeekU8(bytecode, offset);
                    offset++;
                    local_tee(frame, stackPointer - 1, index);
                    break;
                }
                case Bytecode.LOCAL_TEE_I32: {
                    final int index = rawPeekI32(bytecode, offset);
                    offset += 4;
                    local_tee(frame, stackPointer - 1, index);
                    break;
                }
                case Bytecode.LOCAL_TEE_REF_U8: {
                    final int index = rawPeekU8(bytecode, offset);
                    offset++;
                    local_tee_ref(frame, stackPointer - 1, index);
                    break;
                }
                case Bytecode.LOCAL_TEE_REF_I32: {
                    final int index = rawPeekI32(bytecode, offset);
                    offset += 4;
                    local_tee_ref(frame, stackPointer - 1, index);
                    break;
                }
                case Bytecode.GLOBAL_GET_U8: {
                    final int index = rawPeekU8(bytecode, offset);
                    offset++;
                    global_get(context, instance, frame, stackPointer, index);
                    stackPointer++;
                    break;
                }
                case Bytecode.GLOBAL_GET_I32: {
                    final int index = rawPeekI32(bytecode, offset);
                    offset += 4;
                    global_get(context, instance, frame, stackPointer, index);
                    stackPointer++;
                    break;
                }
                case Bytecode.GLOBAL_SET_U8: {
                    final int index = rawPeekU8(bytecode, offset);
                    offset++;
                    stackPointer--;
                    global_set(context, instance, frame, stackPointer, index);
                    break;
                }
                case Bytecode.GLOBAL_SET_I32: {
                    final int index = rawPeekI32(bytecode, offset);
                    offset += 4;
                    stackPointer--;
                    global_set(context, instance, frame, stackPointer, index);
                    break;
                }
                case Bytecode.I32_LOAD:
                case Bytecode.I64_LOAD:
                case Bytecode.F32_LOAD:
                case Bytecode.F64_LOAD:
                case Bytecode.I32_LOAD8_S:
                case Bytecode.I32_LOAD8_U:
                case Bytecode.I32_LOAD16_S:
                case Bytecode.I32_LOAD16_U:
                case Bytecode.I64_LOAD8_S:
                case Bytecode.I64_LOAD8_U:
                case Bytecode.I64_LOAD16_S:
                case Bytecode.I64_LOAD16_U:
                case Bytecode.I64_LOAD32_S:
                case Bytecode.I64_LOAD32_U: {
                    final int encoding = rawPeekU8(bytecode, offset);
                    offset++;
                    final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
                    final int offsetLength = encoding & BytecodeBitEncoding.MEMORY_OFFSET_MASK;
                    final int memoryIndex = rawPeekI32(bytecode, offset);
                    offset += 4;
                    final long memOffset;
                    switch (offsetLength) {
                        case BytecodeBitEncoding.MEMORY_OFFSET_U8:
                            memOffset = rawPeekU8(bytecode, offset);
                            offset++;
                            break;
                        case BytecodeBitEncoding.MEMORY_OFFSET_U32:
                            memOffset = rawPeekU32(bytecode, offset);
                            offset += 4;
                            break;
                        case BytecodeBitEncoding.MEMORY_OFFSET_I64:
                            memOffset = rawPeekI64(bytecode, offset);
                            offset += 8;
                            break;
                        default:
                            throw CompilerDirectives.shouldNotReachHere();
                    }
                    final long baseAddress;
                    if (indexType64 == 0) {
                        baseAddress = Integer.toUnsignedLong(popInt(frame, stackPointer - 1));
                    } else {
                        baseAddress = popLong(frame, stackPointer - 1);
                    }
                    final long address = effectiveMemoryAddress64(memOffset, baseAddress);
                    final WasmMemory memory = memory(instance, memoryIndex);
                    load(memory, frame, stackPointer - 1, opcode, address);
                    break;
                }
                case Bytecode.I32_LOAD_U8: {
                    final int memOffset = rawPeekU8(bytecode, offset);
                    offset++;

                    int baseAddress = popInt(frame, stackPointer - 1);
                    final long address = effectiveMemoryAddress(memOffset, baseAddress);

                    int value = zeroMemory.load_i32(this, address);
                    pushInt(frame, stackPointer - 1, value);
                    break;
                }
                case Bytecode.I32_LOAD_I32: {
                    final int memOffset = rawPeekI32(bytecode, offset);
                    offset += 4;

                    int baseAddress = popInt(frame, stackPointer - 1);
                    final long address = effectiveMemoryAddress(memOffset, baseAddress);

                    int value = zeroMemory.load_i32(this, address);
                    pushInt(frame, stackPointer - 1, value);
                    break;
                }
                case Bytecode.I64_LOAD_U8:
                case Bytecode.F32_LOAD_U8:
                case Bytecode.F64_LOAD_U8:
                case Bytecode.I32_LOAD8_S_U8:
                case Bytecode.I32_LOAD8_U_U8:
                case Bytecode.I32_LOAD16_S_U8:
                case Bytecode.I32_LOAD16_U_U8:
                case Bytecode.I64_LOAD8_S_U8:
                case Bytecode.I64_LOAD8_U_U8:
                case Bytecode.I64_LOAD16_S_U8:
                case Bytecode.I64_LOAD16_U_U8:
                case Bytecode.I64_LOAD32_S_U8:
                case Bytecode.I64_LOAD32_U_U8: {
                    final int memOffset = rawPeekU8(bytecode, offset);
                    offset++;

                    final int baseAddress = popInt(frame, stackPointer - 1);
                    final long address = effectiveMemoryAddress(memOffset, baseAddress);

                    load(zeroMemory, frame, stackPointer - 1, opcode, address);
                    break;
                }
                case Bytecode.I64_LOAD_I32:
                case Bytecode.F32_LOAD_I32:
                case Bytecode.F64_LOAD_I32:
                case Bytecode.I32_LOAD8_S_I32:
                case Bytecode.I32_LOAD8_U_I32:
                case Bytecode.I32_LOAD16_S_I32:
                case Bytecode.I32_LOAD16_U_I32:
                case Bytecode.I64_LOAD8_S_I32:
                case Bytecode.I64_LOAD8_U_I32:
                case Bytecode.I64_LOAD16_S_I32:
                case Bytecode.I64_LOAD16_U_I32:
                case Bytecode.I64_LOAD32_S_I32:
                case Bytecode.I64_LOAD32_U_I32: {
                    final int memOffset = rawPeekI32(bytecode, offset);
                    offset += 4;

                    final int baseAddress = popInt(frame, stackPointer - 1);
                    final long address = effectiveMemoryAddress(memOffset, baseAddress);

                    load(zeroMemory, frame, stackPointer - 1, opcode, address);
                    break;
                }
                case Bytecode.I32_STORE:
                case Bytecode.I64_STORE:
                case Bytecode.F32_STORE:
                case Bytecode.F64_STORE:
                case Bytecode.I32_STORE_8:
                case Bytecode.I32_STORE_16:
                case Bytecode.I64_STORE_8:
                case Bytecode.I64_STORE_16:
                case Bytecode.I64_STORE_32: {
                    final int flags = rawPeekU8(bytecode, offset);
                    offset++;
                    final int indexType64 = flags & BytecodeBitEncoding.MEMORY_64_FLAG;
                    final int offsetEncoding = flags & BytecodeBitEncoding.MEMORY_OFFSET_MASK;
                    final int memoryIndex = rawPeekI32(bytecode, offset);
                    offset += 4;
                    final long memOffset;
                    switch (offsetEncoding) {
                        case BytecodeBitEncoding.MEMORY_OFFSET_U8:
                            memOffset = rawPeekU8(bytecode, offset);
                            offset++;
                            break;
                        case BytecodeBitEncoding.MEMORY_OFFSET_U32:
                            memOffset = rawPeekU32(bytecode, offset);
                            offset += 4;
                            break;
                        case BytecodeBitEncoding.MEMORY_OFFSET_I64:
                            memOffset = rawPeekI64(bytecode, offset);
                            offset += 8;
                            break;
                        default:
                            throw CompilerDirectives.shouldNotReachHere();
                    }
                    final long baseAddress;
                    if (indexType64 == 0) {
                        baseAddress = Integer.toUnsignedLong(popInt(frame, stackPointer - 2));
                    } else {
                        baseAddress = popLong(frame, stackPointer - 2);
                    }
                    final long address = effectiveMemoryAddress64(memOffset, baseAddress);
                    final WasmMemory memory = memory(instance, memoryIndex);
                    store(memory, frame, stackPointer - 1, opcode, address);
                    stackPointer -= 2;
                    break;
                }
                case Bytecode.I32_STORE_U8: {
                    final int memOffset = rawPeekU8(bytecode, offset);
                    offset++;

                    final int baseAddress = popInt(frame, stackPointer - 2);
                    final long address = effectiveMemoryAddress(memOffset, baseAddress);

                    final int value = popInt(frame, stackPointer - 1);
                    zeroMemory.store_i32(this, address, value);
                    stackPointer -= 2;
                    break;
                }
                case Bytecode.I32_STORE_I32: {
                    final int memOffset = rawPeekI32(bytecode, offset);
                    offset += 4;

                    final int baseAddress = popInt(frame, stackPointer - 2);
                    final long address = effectiveMemoryAddress(memOffset, baseAddress);

                    final int value = popInt(frame, stackPointer - 1);
                    zeroMemory.store_i32(this, address, value);
                    stackPointer -= 2;
                    break;
                }
                case Bytecode.I64_STORE_U8:
                case Bytecode.F32_STORE_U8:
                case Bytecode.F64_STORE_U8:
                case Bytecode.I32_STORE_8_U8:
                case Bytecode.I32_STORE_16_U8:
                case Bytecode.I64_STORE_8_U8:
                case Bytecode.I64_STORE_16_U8:
                case Bytecode.I64_STORE_32_U8: {
                    final int memOffset = rawPeekU8(bytecode, offset);
                    offset++;

                    final int baseAddress = popInt(frame, stackPointer - 2);
                    final long address = effectiveMemoryAddress(memOffset, baseAddress);

                    store(zeroMemory, frame, stackPointer - 1, opcode, address);
                    stackPointer -= 2;

                    break;
                }
                case Bytecode.I64_STORE_I32:
                case Bytecode.F32_STORE_I32:
                case Bytecode.F64_STORE_I32:
                case Bytecode.I32_STORE_8_I32:
                case Bytecode.I32_STORE_16_I32:
                case Bytecode.I64_STORE_8_I32:
                case Bytecode.I64_STORE_16_I32:
                case Bytecode.I64_STORE_32_I32: {
                    final int memOffset = rawPeekI32(bytecode, offset);
                    offset += 4;

                    final int baseAddress = popInt(frame, stackPointer - 2);
                    final long address = effectiveMemoryAddress(memOffset, baseAddress);

                    store(zeroMemory, frame, stackPointer - 1, opcode, address);
                    stackPointer -= 2;

                    break;
                }
                case Bytecode.MEMORY_SIZE: {
                    final int memoryIndex = rawPeekI32(bytecode, offset);
                    offset += 4;
                    final WasmMemory memory = memory(instance, memoryIndex);
                    int pageSize = (int) memory.size();
                    pushInt(frame, stackPointer, pageSize);
                    stackPointer++;
                    break;
                }
                case Bytecode.MEMORY_GROW: {
                    final int memoryIndex = rawPeekI32(bytecode, offset);
                    offset += 4;
                    final WasmMemory memory = memory(instance, memoryIndex);
                    int extraSize = popInt(frame, stackPointer - 1);
                    int pageSize = (int) memory.size();
                    if (memory.grow(extraSize)) {
                        pushInt(frame, stackPointer - 1, pageSize);
                    } else {
                        pushInt(frame, stackPointer - 1, -1);
                    }
                    break;
                }
                case Bytecode.I32_CONST_I8: {
                    final int value = rawPeekI8(bytecode, offset);
                    offset++;

                    pushInt(frame, stackPointer, value);
                    stackPointer++;
                    break;
                }
                case Bytecode.I32_CONST_I32: {
                    final int value = rawPeekI32(bytecode, offset);
                    offset += 4;

                    pushInt(frame, stackPointer, value);
                    stackPointer++;
                    break;
                }
                case Bytecode.I64_CONST_I8: {
                    final long value = rawPeekI8(bytecode, offset);
                    offset++;
                    // endregion
                    pushLong(frame, stackPointer, value);
                    stackPointer++;
                    break;
                }
                case Bytecode.I64_CONST_I64: {
                    final long value = rawPeekI64(bytecode, offset);
                    offset += 8;
                    // endregion
                    pushLong(frame, stackPointer, value);
                    stackPointer++;
                    break;
                }
                case Bytecode.I32_EQZ:
                    i32_eqz(frame, stackPointer);
                    break;
                case Bytecode.I32_EQ:
                    i32_eq(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_NE:
                    i32_ne(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_LT_S:
                    i32_lt_s(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_LT_U:
                    i32_lt_u(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_GT_S:
                    i32_gt_s(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_GT_U:
                    i32_gt_u(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_LE_S:
                    i32_le_s(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_LE_U:
                    i32_le_u(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_GE_S:
                    i32_ge_s(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_GE_U:
                    i32_ge_u(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_EQZ:
                    i64_eqz(frame, stackPointer);
                    break;
                case Bytecode.I64_EQ:
                    i64_eq(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_NE:
                    i64_ne(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_LT_S:
                    i64_lt_s(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_LT_U:
                    i64_lt_u(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_GT_S:
                    i64_gt_s(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_GT_U:
                    i64_gt_u(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_LE_S:
                    i64_le_s(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_LE_U:
                    i64_le_u(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_GE_S:
                    i64_ge_s(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_GE_U:
                    i64_ge_u(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F32_EQ:
                    f32_eq(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F32_NE:
                    f32_ne(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F32_LT:
                    f32_lt(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F32_GT:
                    f32_gt(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F32_LE:
                    f32_le(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F32_GE:
                    f32_ge(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F64_EQ:
                    f64_eq(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F64_NE:
                    f64_ne(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F64_LT:
                    f64_lt(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F64_GT:
                    f64_gt(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F64_LE:
                    f64_le(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F64_GE:
                    f64_ge(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_CLZ:
                    i32_clz(frame, stackPointer);
                    break;
                case Bytecode.I32_CTZ:
                    i32_ctz(frame, stackPointer);
                    break;
                case Bytecode.I32_POPCNT:
                    i32_popcnt(frame, stackPointer);
                    break;
                case Bytecode.I32_ADD:
                    i32_add(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_SUB:
                    i32_sub(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_MUL:
                    i32_mul(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_DIV_S:
                    i32_div_s(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_DIV_U:
                    i32_div_u(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_REM_S:
                    i32_rem_s(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_REM_U:
                    i32_rem_u(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_AND:
                    i32_and(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_OR:
                    i32_or(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_XOR:
                    i32_xor(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_SHL:
                    i32_shl(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_SHR_S:
                    i32_shr_s(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_SHR_U:
                    i32_shr_u(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_ROTL:
                    i32_rotl(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_ROTR:
                    i32_rotr(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_CLZ:
                    i64_clz(frame, stackPointer);
                    break;
                case Bytecode.I64_CTZ:
                    i64_ctz(frame, stackPointer);
                    break;
                case Bytecode.I64_POPCNT:
                    i64_popcnt(frame, stackPointer);
                    break;
                case Bytecode.I64_ADD:
                    i64_add(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_SUB:
                    i64_sub(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_MUL:
                    i64_mul(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_DIV_S:
                    i64_div_s(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_DIV_U:
                    i64_div_u(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_REM_S:
                    i64_rem_s(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_REM_U:
                    i64_rem_u(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_AND:
                    i64_and(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_OR:
                    i64_or(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_XOR:
                    i64_xor(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_SHL:
                    i64_shl(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_SHR_S:
                    i64_shr_s(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_SHR_U:
                    i64_shr_u(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_ROTL:
                    i64_rotl(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I64_ROTR:
                    i64_rotr(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F32_CONST: {
                    float value = Float.intBitsToFloat(rawPeekI32(bytecode, offset));
                    offset += 4;
                    pushFloat(frame, stackPointer, value);
                    stackPointer++;
                    break;
                }
                case Bytecode.F32_ABS:
                    f32_abs(frame, stackPointer);
                    break;
                case Bytecode.F32_NEG:
                    f32_neg(frame, stackPointer);
                    break;
                case Bytecode.F32_CEIL:
                    f32_ceil(frame, stackPointer);
                    break;
                case Bytecode.F32_FLOOR:
                    f32_floor(frame, stackPointer);
                    break;
                case Bytecode.F32_TRUNC:
                    f32_trunc(frame, stackPointer);
                    break;
                case Bytecode.F32_NEAREST:
                    f32_nearest(frame, stackPointer);
                    break;
                case Bytecode.F32_SQRT:
                    f32_sqrt(frame, stackPointer);
                    break;
                case Bytecode.F32_ADD:
                    f32_add(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F32_SUB:
                    f32_sub(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F32_MUL:
                    f32_mul(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F32_DIV:
                    f32_div(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F32_MIN:
                    f32_min(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F32_MAX:
                    f32_max(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F32_COPYSIGN:
                    f32_copysign(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F64_CONST: {
                    double value = Double.longBitsToDouble(BinaryStreamParser.rawPeekI64(bytecode, offset));
                    offset += 8;
                    pushDouble(frame, stackPointer, value);
                    stackPointer++;
                    break;
                }
                case Bytecode.F64_ABS:
                    f64_abs(frame, stackPointer);
                    break;
                case Bytecode.F64_NEG:
                    f64_neg(frame, stackPointer);
                    break;
                case Bytecode.F64_CEIL:
                    f64_ceil(frame, stackPointer);
                    break;
                case Bytecode.F64_FLOOR:
                    f64_floor(frame, stackPointer);
                    break;
                case Bytecode.F64_TRUNC:
                    f64_trunc(frame, stackPointer);
                    break;
                case Bytecode.F64_NEAREST:
                    f64_nearest(frame, stackPointer);
                    break;
                case Bytecode.F64_SQRT:
                    f64_sqrt(frame, stackPointer);
                    break;
                case Bytecode.F64_ADD:
                    f64_add(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F64_SUB:
                    f64_sub(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F64_MUL:
                    f64_mul(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F64_DIV:
                    f64_div(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F64_MIN:
                    f64_min(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F64_MAX:
                    f64_max(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.F64_COPYSIGN:
                    f64_copysign(frame, stackPointer);
                    stackPointer--;
                    break;
                case Bytecode.I32_WRAP_I64:
                    i32_wrap_i64(frame, stackPointer);
                    break;
                case Bytecode.I32_TRUNC_F32_S:
                    i32_trunc_f32_s(frame, stackPointer);
                    break;
                case Bytecode.I32_TRUNC_F32_U:
                    i32_trunc_f32_u(frame, stackPointer);
                    break;
                case Bytecode.I32_TRUNC_F64_S:
                    i32_trunc_f64_s(frame, stackPointer);
                    break;
                case Bytecode.I32_TRUNC_F64_U:
                    i32_trunc_f64_u(frame, stackPointer);
                    break;
                case Bytecode.I64_EXTEND_I32_S:
                    i64_extend_i32_s(frame, stackPointer);
                    break;
                case Bytecode.I64_EXTEND_I32_U:
                    i64_extend_i32_u(frame, stackPointer);
                    break;
                case Bytecode.I64_TRUNC_F32_S:
                    i64_trunc_f32_s(frame, stackPointer);
                    break;
                case Bytecode.I64_TRUNC_F32_U:
                    i64_trunc_f32_u(frame, stackPointer);
                    break;
                case Bytecode.I64_TRUNC_F64_S:
                    i64_trunc_f64_s(frame, stackPointer);
                    break;
                case Bytecode.I64_TRUNC_F64_U:
                    i64_trunc_f64_u(frame, stackPointer);
                    break;
                case Bytecode.F32_CONVERT_I32_S:
                    f32_convert_i32_s(frame, stackPointer);
                    break;
                case Bytecode.F32_CONVERT_I32_U:
                    f32_convert_i32_u(frame, stackPointer);
                    break;
                case Bytecode.F32_CONVERT_I64_S:
                    f32_convert_i64_s(frame, stackPointer);
                    break;
                case Bytecode.F32_CONVERT_I64_U:
                    f32_convert_i64_u(frame, stackPointer);
                    break;
                case Bytecode.F32_DEMOTE_F64:
                    f32_demote_f64(frame, stackPointer);
                    break;
                case Bytecode.F64_CONVERT_I32_S:
                    f64_convert_i32_s(frame, stackPointer);
                    break;
                case Bytecode.F64_CONVERT_I32_U:
                    f64_convert_i32_u(frame, stackPointer);
                    break;
                case Bytecode.F64_CONVERT_I64_S:
                    f64_convert_i64_s(frame, stackPointer);
                    break;
                case Bytecode.F64_CONVERT_I64_U:
                    f64_convert_i64_u(frame, stackPointer);
                    break;
                case Bytecode.F64_PROMOTE_F32:
                    f64_promote_f32(frame, stackPointer);
                    break;
                case Bytecode.I32_REINTERPRET_F32:
                    i32_reinterpret_f32(frame, stackPointer);
                    break;
                case Bytecode.I64_REINTERPRET_F64:
                    i64_reinterpret_f64(frame, stackPointer);
                    break;
                case Bytecode.F32_REINTERPRET_I32:
                    f32_reinterpret_i32(frame, stackPointer);
                    break;
                case Bytecode.F64_REINTERPRET_I64:
                    f64_reinterpret_i64(frame, stackPointer);
                    break;
                case Bytecode.I32_EXTEND8_S:
                    i32_extend8_s(frame, stackPointer);
                    break;
                case Bytecode.I32_EXTEND16_S:
                    i32_extend16_s(frame, stackPointer);
                    break;
                case Bytecode.I64_EXTEND8_S:
                    i64_extend8_s(frame, stackPointer);
                    break;
                case Bytecode.I64_EXTEND16_S:
                    i64_extend16_s(frame, stackPointer);
                    break;
                case Bytecode.I64_EXTEND32_S:
                    i64_extend32_s(frame, stackPointer);
                    break;
                case Bytecode.REF_NULL:
                    pushReference(frame, stackPointer, WasmConstant.NULL);
                    stackPointer++;
                    break;
                case Bytecode.REF_IS_NULL:
                    final Object refType = popReference(frame, stackPointer - 1);
                    pushInt(frame, stackPointer - 1, refType == WasmConstant.NULL ? 1 : 0);
                    break;
                case Bytecode.REF_FUNC:
                    final int functionIndex = rawPeekI32(bytecode, offset);
                    final WasmFunction function = module.symbolTable().function(functionIndex);
                    final WasmFunctionInstance functionInstance = instance.functionInstance(function);
                    pushReference(frame, stackPointer, functionInstance);
                    stackPointer++;
                    offset += 4;
                    break;
                case Bytecode.TABLE_GET: {
                    final int tableIndex = rawPeekI32(bytecode, offset);
                    table_get(context, instance, frame, stackPointer, tableIndex);
                    offset += 4;
                    break;
                }
                case Bytecode.TABLE_SET: {
                    final int tableIndex = rawPeekI32(bytecode, offset);
                    table_set(context, instance, frame, stackPointer, tableIndex);
                    stackPointer -= 2;
                    offset += 4;
                    break;
                }
                case Bytecode.MISC: {
                    final int miscOpcode = rawPeekU8(bytecode, offset);
                    offset++;
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
                        case Bytecode.MEMORY_INIT_UNSAFE:
                        case Bytecode.MEMORY64_INIT:
                        case Bytecode.MEMORY64_INIT_UNSAFE: {
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
                            final int dst = popInt(frame, stackPointer - 3);
                            table_init(context, instance, n, src, dst, tableIndex, elementIndex);
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

                            final int n = popInt(frame, stackPointer - 1);
                            final int src = popInt(frame, stackPointer - 2);
                            final int dst = popInt(frame, stackPointer - 3);
                            table_copy(context, instance, n, src, dst, srcIndex, dstIndex);
                            stackPointer -= 3;
                            offset += 8;
                            break;
                        }
                        case Bytecode.TABLE_GROW: {
                            final int tableIndex = rawPeekI32(bytecode, offset);

                            final int n = popInt(frame, stackPointer - 1);
                            final Object val = popReference(frame, stackPointer - 2);

                            final int res = table_grow(context, instance, n, val, tableIndex);
                            pushInt(frame, stackPointer - 2, res);
                            stackPointer--;
                            offset += 4;
                            break;
                        }
                        case Bytecode.TABLE_SIZE: {
                            final int tableIndex = rawPeekI32(bytecode, offset);
                            table_size(context, instance, frame, stackPointer, tableIndex);
                            stackPointer++;
                            offset += 4;
                            break;
                        }
                        case Bytecode.TABLE_FILL: {
                            final int tableIndex = rawPeekI32(bytecode, offset);

                            final int n = popInt(frame, stackPointer - 1);
                            final Object val = popReference(frame, stackPointer - 2);
                            final int i = popInt(frame, stackPointer - 3);
                            table_fill(context, instance, n, val, i, tableIndex);
                            stackPointer -= 3;
                            offset += 4;
                            break;
                        }
                        case Bytecode.DATA_DROP_UNSAFE: {
                            final int dataIndex = rawPeekI32(bytecode, offset);
                            data_drop_unsafe(instance, dataIndex);
                            offset += 4;
                            break;
                        }
                        case Bytecode.MEMORY64_SIZE: {
                            final int memoryIndex = rawPeekI32(bytecode, offset);
                            offset += 4;
                            final WasmMemory memory = memory(instance, memoryIndex);
                            long pageSize = memory.size();
                            pushLong(frame, stackPointer, pageSize);
                            stackPointer++;
                            break;
                        }
                        case Bytecode.MEMORY64_GROW: {
                            final int memoryIndex = rawPeekI32(bytecode, offset);
                            offset += 4;
                            final WasmMemory memory = memory(instance, memoryIndex);
                            long extraSize = popLong(frame, stackPointer - 1);
                            long pageSize = memory.size();
                            if (memory.grow(extraSize)) {
                                pushLong(frame, stackPointer - 1, pageSize);
                            } else {
                                pushLong(frame, stackPointer - 1, -1L);
                            }
                            break;
                        }
                        default:
                            throw CompilerDirectives.shouldNotReachHere();
                    }
                    break;
                }
                case Bytecode.ATOMIC: {
                    final int atomicOpcode = rawPeekU8(bytecode, offset);
                    offset++;
                    CompilerAsserts.partialEvaluationConstant(atomicOpcode);
                    if (atomicOpcode == Bytecode.ATOMIC_FENCE) {
                        break;
                    }

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

                    final WasmMemory memory = memory(instance, memoryIndex);
                    final int stackPointerDecrement = executeAtomic(frame, stackPointer, atomicOpcode, memory, memOffset, indexType64);
                    stackPointer -= stackPointerDecrement;
                    break;
                }
                case Bytecode.NOTIFY: {
                    final int nextLine = rawPeekI32(bytecode, offset);
                    final int sourceCodeLocation = rawPeekI32(bytecode, offset + 4);
                    offset += 8;
                    if (notifyFunction != null) {
                        notifyFunction.notifyLine(frame, line, nextLine, sourceCodeLocation);
                    }
                    line = nextLine;
                    break;
                }
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
        return WasmConstant.RETURN_VALUE;
    }

    @TruffleBoundary
    private void failFunctionTypeCheck(WasmFunction function, int expectedFunctionTypeIndex) {
        throw WasmException.format(Failure.INDIRECT_CALL_TYPE__MISMATCH, this,
                        "Actual (type %d of function %s) and expected (type %d in module %s) types differ in the indirect call.",
                        function.typeIndex(), function.name(), expectedFunctionTypeIndex, module.name());
    }

    private void check(int v, int limit) {
        // This is a temporary hack to hoist values out of the loop.
        if (v >= limit) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, this, "array length too large");
        }
    }

    private Object executeDirectCall(WasmInstance instance, int callNodeIndex, WasmFunction function, Object[] args) {
        final boolean imported = function.isImported();
        CompilerAsserts.partialEvaluationConstant(imported);
        Node callNode = callNodes[callNodeIndex];
        if (imported) {
            WasmIndirectCallNode indirectCallNode = (WasmIndirectCallNode) callNode;
            WasmFunctionInstance functionInstance = instance.functionInstance(function.index());
            TruffleContext truffleContext = functionInstance.getTruffleContext();
            WasmArguments.setModuleInstance(args, functionInstance.moduleInstance());
            Object prev = truffleContext.enter(this);
            try {
                return indirectCallNode.execute(instance.target(function.index()), args);
            } finally {
                truffleContext.leave(this, prev);
            }
        } else {
            DirectCallNode directCallNode = (DirectCallNode) callNode;
            WasmArguments.setModuleInstance(args, instance);
            assert assertDirectCall(instance, function, directCallNode);
            return directCallNode.call(args);
        }
    }

    private boolean assertDirectCall(WasmInstance instance, WasmFunction function, DirectCallNode callNode) {
        WasmFunctionInstance functionInstance = instance.functionInstance(function.index());
        // functionInstance may be null for calls between functions of the same module.
        if (functionInstance == null) {
            assert !function.isImported();
            return true;
        }
        assert functionInstance.target() == callNode.getCallTarget();
        assert function.isImported() || functionInstance.context() == WasmContext.get(this);
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
    private long effectiveMemoryAddress64(long staticAddressOffset, long dynamicAddress) {
        try {
            return Math.addExact(dynamicAddress, staticAddressOffset);
        } catch (ArithmeticException e) {
            enterErrorBranch();
            throw WasmException.create(Failure.UNSPECIFIED_TRAP, "Memory address too large");
        }
    }

    private void load(WasmMemory memory, VirtualFrame frame, int stackPointer, int opcode, long address) {
        switch (opcode) {
            case Bytecode.I32_LOAD:
            case Bytecode.I32_LOAD_U8:
            case Bytecode.I32_LOAD_I32: {
                final int value = memory.load_i32(this, address);
                pushInt(frame, stackPointer, value);
                break;
            }
            case Bytecode.I64_LOAD:
            case Bytecode.I64_LOAD_U8:
            case Bytecode.I64_LOAD_I32: {
                final long value = memory.load_i64(this, address);
                pushLong(frame, stackPointer, value);
                break;
            }
            case Bytecode.F32_LOAD:
            case Bytecode.F32_LOAD_U8:
            case Bytecode.F32_LOAD_I32: {
                final float value = memory.load_f32(this, address);
                pushFloat(frame, stackPointer, value);
                break;
            }
            case Bytecode.F64_LOAD:
            case Bytecode.F64_LOAD_U8:
            case Bytecode.F64_LOAD_I32: {
                final double value = memory.load_f64(this, address);
                pushDouble(frame, stackPointer, value);
                break;
            }
            case Bytecode.I32_LOAD8_S:
            case Bytecode.I32_LOAD8_S_U8:
            case Bytecode.I32_LOAD8_S_I32: {
                final int value = memory.load_i32_8s(this, address);
                pushInt(frame, stackPointer, value);
                break;
            }
            case Bytecode.I32_LOAD8_U:
            case Bytecode.I32_LOAD8_U_U8:
            case Bytecode.I32_LOAD8_U_I32: {
                final int value = memory.load_i32_8u(this, address);
                pushInt(frame, stackPointer, value);
                break;
            }
            case Bytecode.I32_LOAD16_S:
            case Bytecode.I32_LOAD16_S_U8:
            case Bytecode.I32_LOAD16_S_I32: {
                final int value = memory.load_i32_16s(this, address);
                pushInt(frame, stackPointer, value);
                break;
            }
            case Bytecode.I32_LOAD16_U:
            case Bytecode.I32_LOAD16_U_U8:
            case Bytecode.I32_LOAD16_U_I32: {
                final int value = memory.load_i32_16u(this, address);
                pushInt(frame, stackPointer, value);
                break;
            }
            case Bytecode.I64_LOAD8_S:
            case Bytecode.I64_LOAD8_S_U8:
            case Bytecode.I64_LOAD8_S_I32: {
                final long value = memory.load_i64_8s(this, address);
                pushLong(frame, stackPointer, value);
                break;
            }
            case Bytecode.I64_LOAD8_U:
            case Bytecode.I64_LOAD8_U_U8:
            case Bytecode.I64_LOAD8_U_I32: {
                final long value = memory.load_i64_8u(this, address);
                pushLong(frame, stackPointer, value);
                break;
            }
            case Bytecode.I64_LOAD16_S:
            case Bytecode.I64_LOAD16_S_U8:
            case Bytecode.I64_LOAD16_S_I32: {
                final long value = memory.load_i64_16s(this, address);
                pushLong(frame, stackPointer, value);
                break;
            }
            case Bytecode.I64_LOAD16_U:
            case Bytecode.I64_LOAD16_U_U8:
            case Bytecode.I64_LOAD16_U_I32: {
                final long value = memory.load_i64_16u(this, address);
                pushLong(frame, stackPointer, value);
                break;
            }
            case Bytecode.I64_LOAD32_S:
            case Bytecode.I64_LOAD32_S_U8:
            case Bytecode.I64_LOAD32_S_I32: {
                final long value = memory.load_i64_32s(this, address);
                pushLong(frame, stackPointer, value);
                break;
            }
            case Bytecode.I64_LOAD32_U:
            case Bytecode.I64_LOAD32_U_U8:
            case Bytecode.I64_LOAD32_U_I32: {
                final long value = memory.load_i64_32u(this, address);
                pushLong(frame, stackPointer, value);
                break;
            }
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private void store(WasmMemory memory, VirtualFrame frame, int stackPointer, int opcode, long address) {
        switch (opcode) {
            case Bytecode.I32_STORE:
            case Bytecode.I32_STORE_U8:
            case Bytecode.I32_STORE_I32: {
                final int value = popInt(frame, stackPointer);
                memory.store_i32(this, address, value);
                break;
            }
            case Bytecode.I64_STORE:
            case Bytecode.I64_STORE_U8:
            case Bytecode.I64_STORE_I32: {
                final long value = popLong(frame, stackPointer);
                memory.store_i64(this, address, value);
                break;
            }
            case Bytecode.F32_STORE:
            case Bytecode.F32_STORE_U8:
            case Bytecode.F32_STORE_I32: {
                final float value = popFloat(frame, stackPointer);
                memory.store_f32(this, address, value);
                break;
            }
            case Bytecode.F64_STORE:
            case Bytecode.F64_STORE_U8:
            case Bytecode.F64_STORE_I32: {
                final double value = popDouble(frame, stackPointer);
                memory.store_f64(this, address, value);
                break;
            }
            case Bytecode.I32_STORE_8:
            case Bytecode.I32_STORE_8_U8:
            case Bytecode.I32_STORE_8_I32: {
                final int value = popInt(frame, stackPointer);
                memory.store_i32_8(this, address, (byte) value);
                break;
            }
            case Bytecode.I32_STORE_16:
            case Bytecode.I32_STORE_16_U8:
            case Bytecode.I32_STORE_16_I32: {
                final int value = popInt(frame, stackPointer);
                memory.store_i32_16(this, address, (short) value);
                break;
            }
            case Bytecode.I64_STORE_8:
            case Bytecode.I64_STORE_8_U8:
            case Bytecode.I64_STORE_8_I32: {
                final long value = popLong(frame, stackPointer);
                memory.store_i64_8(this, address, (byte) value);
                break;
            }
            case Bytecode.I64_STORE_16:
            case Bytecode.I64_STORE_16_U8:
            case Bytecode.I64_STORE_16_I32: {
                final long value = popLong(frame, stackPointer);
                memory.store_i64_16(this, address, (short) value);
                break;
            }
            case Bytecode.I64_STORE_32:
            case Bytecode.I64_STORE_32_U8:
            case Bytecode.I64_STORE_32_I32: {
                final long value = popLong(frame, stackPointer);
                memory.store_i64_32(this, address, (int) value);
                break;
            }
            default:
                throw CompilerDirectives.shouldNotReachHere();
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
            case Bytecode.MEMORY_INIT_UNSAFE: {
                n = popInt(frame, stackPointer - 1);
                src = popInt(frame, stackPointer - 2);
                dst = popInt(frame, stackPointer - 3);
                memory_init_unsafe(instance, n, src, dst, dataIndex, memoryIndex);
                break;
            }
            case Bytecode.MEMORY64_INIT_UNSAFE: {
                n = popInt(frame, stackPointer - 1);
                src = popInt(frame, stackPointer - 2);
                dst = popLong(frame, stackPointer - 3);
                memory_init_unsafe(instance, n, src, dst, dataIndex, memoryIndex);
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

    private int executeAtomic(VirtualFrame frame, int stackPointer, int opcode, WasmMemory memory, long memOffset, int indexType64) {
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
                final long address = effectiveMemoryAddress64(memOffset, baseAddress);
                executeAtomicAtAddress(memory, frame, stackPointer - 1, opcode, address);
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
                final long address = effectiveMemoryAddress64(memOffset, baseAddress);
                executeAtomicAtAddress(memory, frame, stackPointer - 1, opcode, address);
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
                final long address = effectiveMemoryAddress64(memOffset, baseAddress);
                executeAtomicAtAddress(memory, frame, stackPointer - 1, opcode, address);
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
                final long address = effectiveMemoryAddress64(memOffset, baseAddress);
                executeAtomicAtAddress(memory, frame, stackPointer - 1, opcode, address);
                return 2;
            }
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private void executeAtomicAtAddress(WasmMemory memory, VirtualFrame frame, int stackPointer, int opcode, long address) {
        switch (opcode) {
            case Bytecode.ATOMIC_NOTIFY: {
                final int count = popInt(frame, stackPointer);
                final int waitersNotified = memory.atomic_notify(this, address, count);
                pushInt(frame, stackPointer - 1, waitersNotified);
                break;
            }
            case Bytecode.ATOMIC_WAIT32: {
                final long timeout = popLong(frame, stackPointer);
                final int expected = popInt(frame, stackPointer - 1);
                final int status = memory.atomic_wait32(this, address, expected, timeout);
                pushInt(frame, stackPointer - 2, status);
                break;
            }
            case Bytecode.ATOMIC_WAIT64: {
                final long timeout = popLong(frame, stackPointer);
                final long expected = popLong(frame, stackPointer - 1);
                final int status = memory.atomic_wait64(this, address, expected, timeout);
                pushInt(frame, stackPointer - 2, status);
                break;
            }
            case Bytecode.ATOMIC_I32_LOAD: {
                final int value = memory.atomic_load_i32(this, address);
                pushInt(frame, stackPointer, value);
                break;
            }
            case Bytecode.ATOMIC_I64_LOAD: {
                final long value = memory.atomic_load_i64(this, address);
                pushLong(frame, stackPointer, value);
                break;
            }
            case Bytecode.ATOMIC_I32_LOAD8_U: {
                final int value = memory.atomic_load_i32_8u(this, address);
                pushInt(frame, stackPointer, value);
                break;
            }
            case Bytecode.ATOMIC_I32_LOAD16_U: {
                final int value = memory.atomic_load_i32_16u(this, address);
                pushInt(frame, stackPointer, value);
                break;
            }
            case Bytecode.ATOMIC_I64_LOAD8_U: {
                final long value = memory.atomic_load_i64_8u(this, address);
                pushLong(frame, stackPointer, value);
                break;
            }
            case Bytecode.ATOMIC_I64_LOAD16_U: {
                final long value = memory.atomic_load_i64_16u(this, address);
                pushLong(frame, stackPointer, value);
                break;
            }
            case Bytecode.ATOMIC_I64_LOAD32_U: {
                final long value = memory.atomic_load_i64_32u(this, address);
                pushLong(frame, stackPointer, value);
                break;
            }
            case Bytecode.ATOMIC_I32_STORE: {
                final int value = popInt(frame, stackPointer);
                memory.atomic_store_i32(this, address, value);
                break;
            }
            case Bytecode.ATOMIC_I64_STORE: {
                final long value = popLong(frame, stackPointer);
                memory.atomic_store_i64(this, address, value);
                break;
            }
            case Bytecode.ATOMIC_I32_STORE8: {
                final int value = popInt(frame, stackPointer);
                memory.atomic_store_i32_8(this, address, (byte) value);
                break;
            }
            case Bytecode.ATOMIC_I32_STORE16: {
                final int value = popInt(frame, stackPointer);
                memory.atomic_store_i32_16(this, address, (short) value);
                break;
            }
            case Bytecode.ATOMIC_I64_STORE8: {
                final long value = popLong(frame, stackPointer);
                memory.atomic_store_i64_8(this, address, (byte) value);
                break;
            }
            case Bytecode.ATOMIC_I64_STORE16: {
                final long value = popLong(frame, stackPointer);
                memory.atomic_store_i64_16(this, address, (short) value);
                break;
            }
            case Bytecode.ATOMIC_I64_STORE32: {
                final long value = popLong(frame, stackPointer);
                memory.atomic_store_i64_32(this, address, (int) value);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW_ADD: {
                final int value = popInt(frame, stackPointer);
                final int result = memory.atomic_rmw_add_i32(this, address, value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW_ADD: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_add_i64(this, address, value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW8_U_ADD: {
                final int value = popInt(frame, stackPointer);
                final int result = memory.atomic_rmw_add_i32_8u(this, address, (byte) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW16_U_ADD: {
                final int value = popInt(frame, stackPointer);
                final int result = memory.atomic_rmw_add_i32_16u(this, address, (short) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW8_U_ADD: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_add_i64_8u(this, address, (byte) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW16_U_ADD: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_add_i64_16u(this, address, (short) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW32_U_ADD: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_add_i64_32u(this, address, (int) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW_SUB: {
                final int value = popInt(frame, stackPointer);
                final int result = memory.atomic_rmw_sub_i32(this, address, value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW_SUB: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_sub_i64(this, address, value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW8_U_SUB: {
                final int value = popInt(frame, stackPointer);
                final int result = memory.atomic_rmw_sub_i32_8u(this, address, (byte) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW16_U_SUB: {
                final int value = popInt(frame, stackPointer);
                final int result = memory.atomic_rmw_sub_i32_16u(this, address, (short) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW8_U_SUB: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_sub_i64_8u(this, address, (byte) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW16_U_SUB: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_sub_i64_16u(this, address, (short) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW32_U_SUB: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_sub_i64_32u(this, address, (int) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW_AND: {
                final int value = popInt(frame, stackPointer);
                final int result = memory.atomic_rmw_and_i32(this, address, value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW_AND: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_and_i64(this, address, value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW8_U_AND: {
                final int value = popInt(frame, stackPointer);
                final int result = memory.atomic_rmw_and_i32_8u(this, address, (byte) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW16_U_AND: {
                final int value = popInt(frame, stackPointer);
                final int result = memory.atomic_rmw_and_i32_16u(this, address, (short) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW8_U_AND: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_and_i64_8u(this, address, (byte) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW16_U_AND: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_and_i64_16u(this, address, (short) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW32_U_AND: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_and_i64_32u(this, address, (int) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW_OR: {
                final int value = popInt(frame, stackPointer);
                final int result = memory.atomic_rmw_or_i32(this, address, value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW_OR: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_or_i64(this, address, value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW8_U_OR: {
                final int value = popInt(frame, stackPointer);
                final int result = memory.atomic_rmw_or_i32_8u(this, address, (byte) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW16_U_OR: {
                final int value = popInt(frame, stackPointer);
                final int result = memory.atomic_rmw_or_i32_16u(this, address, (short) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW8_U_OR: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_or_i64_8u(this, address, (byte) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW16_U_OR: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_or_i64_16u(this, address, (short) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW32_U_OR: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_or_i64_32u(this, address, (int) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW_XOR: {
                final int value = popInt(frame, stackPointer);
                final int result = memory.atomic_rmw_xor_i32(this, address, value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW_XOR: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_xor_i64(this, address, value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW8_U_XOR: {
                final int value = popInt(frame, stackPointer);
                final int result = memory.atomic_rmw_xor_i32_8u(this, address, (byte) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW16_U_XOR: {
                final int value = popInt(frame, stackPointer);
                final int result = memory.atomic_rmw_xor_i32_16u(this, address, (short) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW8_U_XOR: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_xor_i64_8u(this, address, (byte) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW16_U_XOR: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_xor_i64_16u(this, address, (short) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW32_U_XOR: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_xor_i64_32u(this, address, (int) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW_XCHG: {
                final int value = popInt(frame, stackPointer);
                final int result = memory.atomic_rmw_xchg_i32(this, address, value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW_XCHG: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_xchg_i64(this, address, value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW8_U_XCHG: {
                final int value = popInt(frame, stackPointer);
                final int result = memory.atomic_rmw_xchg_i32_8u(this, address, (byte) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW16_U_XCHG: {
                final int value = popInt(frame, stackPointer);
                final int result = memory.atomic_rmw_xchg_i32_16u(this, address, (short) value);
                pushInt(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW8_U_XCHG: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_xchg_i64_8u(this, address, (byte) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW16_U_XCHG: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_xchg_i64_16u(this, address, (short) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW32_U_XCHG: {
                final long value = popLong(frame, stackPointer);
                final long result = memory.atomic_rmw_xchg_i64_32u(this, address, (int) value);
                pushLong(frame, stackPointer - 1, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW_CMPXCHG: {
                final int replacement = popInt(frame, stackPointer);
                final int expected = popInt(frame, stackPointer - 1);
                final int result = memory.atomic_rmw_cmpxchg_i32(this, address, expected, replacement);
                pushInt(frame, stackPointer - 2, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW_CMPXCHG: {
                final long replacement = popLong(frame, stackPointer);
                final long expected = popLong(frame, stackPointer - 1);
                final long result = memory.atomic_rmw_cmpxchg_i64(this, address, expected, replacement);
                pushLong(frame, stackPointer - 2, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW8_U_CMPXCHG: {
                final int replacement = popInt(frame, stackPointer);
                final int expected = popInt(frame, stackPointer - 1);
                final int result = memory.atomic_rmw_cmpxchg_i32_8u(this, address, (byte) expected, (byte) replacement);
                pushInt(frame, stackPointer - 2, result);
                break;
            }
            case Bytecode.ATOMIC_I32_RMW16_U_CMPXCHG: {
                final int replacement = popInt(frame, stackPointer);
                final int expected = popInt(frame, stackPointer - 1);
                final int result = memory.atomic_rmw_cmpxchg_i32_16u(this, address, (short) expected, (short) replacement);
                pushInt(frame, stackPointer - 2, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW8_U_CMPXCHG: {
                final long replacement = popLong(frame, stackPointer);
                final long expected = popLong(frame, stackPointer - 1);
                final long result = memory.atomic_rmw_cmpxchg_i64_8u(this, address, (byte) expected, (byte) replacement);
                pushLong(frame, stackPointer - 2, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW16_U_CMPXCHG: {
                final long replacement = popLong(frame, stackPointer);
                final long expected = popLong(frame, stackPointer - 1);
                final long result = memory.atomic_rmw_cmpxchg_i64_16u(this, address, (short) expected, (short) replacement);
                pushLong(frame, stackPointer - 2, result);
                break;
            }
            case Bytecode.ATOMIC_I64_RMW32_U_CMPXCHG: {
                final long replacement = popLong(frame, stackPointer);
                final long expected = popLong(frame, stackPointer - 1);
                final long result = memory.atomic_rmw_cmpxchg_i64_32u(this, address, (int) expected, (int) replacement);
                pushLong(frame, stackPointer - 2, result);
                break;
            }
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    // Checkstyle: stop method name check

    private void global_set(WasmContext context, WasmInstance instance, VirtualFrame frame, int stackPointer, int index) {
        byte type = module.symbolTable().globalValueType(index);
        CompilerAsserts.partialEvaluationConstant(type);
        // For global.set, we don't need to make sure that the referenced global is
        // mutable.
        // This is taken care of by validation during wat to wasm compilation.
        switch (type) {
            case WasmType.I32_TYPE:
                context.globals().storeInt(instance.globalAddress(index), popInt(frame, stackPointer));
                break;
            case WasmType.F32_TYPE:
                context.globals().storeInt(instance.globalAddress(index), Float.floatToRawIntBits(popFloat(frame, stackPointer)));
                break;
            case WasmType.I64_TYPE:
                context.globals().storeLong(instance.globalAddress(index), popLong(frame, stackPointer));
                break;
            case WasmType.F64_TYPE:
                context.globals().storeLong(instance.globalAddress(index), Double.doubleToRawLongBits(popDouble(frame, stackPointer)));
                break;
            case WasmType.FUNCREF_TYPE:
            case WasmType.EXTERNREF_TYPE:
                context.globals().storeReference(instance.globalAddress(index), popReference(frame, stackPointer));
                break;
            default:
                throw WasmException.create(Failure.UNSPECIFIED_TRAP, this, "Local variable cannot have the void type.");
        }
    }

    private void global_get(WasmContext context, WasmInstance instance, VirtualFrame frame, int stackPointer, int index) {
        byte type = module.symbolTable().globalValueType(index);
        CompilerAsserts.partialEvaluationConstant(type);
        switch (type) {
            case WasmType.I32_TYPE:
                pushInt(frame, stackPointer, context.globals().loadAsInt(instance.globalAddress(index)));
                break;
            case WasmType.F32_TYPE:
                pushFloat(frame, stackPointer, Float.intBitsToFloat(context.globals().loadAsInt(instance.globalAddress(index))));
                break;
            case WasmType.I64_TYPE:
                pushLong(frame, stackPointer, context.globals().loadAsLong(instance.globalAddress(index)));
                break;
            case WasmType.F64_TYPE:
                pushDouble(frame, stackPointer, Double.longBitsToDouble(context.globals().loadAsLong(instance.globalAddress(index))));
                break;
            case WasmType.FUNCREF_TYPE:
            case WasmType.EXTERNREF_TYPE:
                pushReference(frame, stackPointer, context.globals().loadAsReference(instance.globalAddress(index)));
                break;
            default:
                throw WasmException.create(Failure.UNSPECIFIED_TRAP, this, "Local variable cannot have the void type.");
        }
    }

    private static void local_tee(VirtualFrame frame, int stackPointer, int index) {
        WasmFrame.copyPrimitive(frame, stackPointer, index);
    }

    private static void local_tee_ref(VirtualFrame frame, int stackPointer, int index) {
        WasmFrame.copyReference(frame, stackPointer, index);
    }

    private static void local_set(VirtualFrame frame, int stackPointer, int index) {
        WasmFrame.copyPrimitive(frame, stackPointer, index);
        if (CompilerDirectives.inCompiledCode()) {
            WasmFrame.dropPrimitive(frame, stackPointer);
        }
    }

    private static void local_set_ref(VirtualFrame frame, int stackPointer, int index) {
        WasmFrame.copyReference(frame, stackPointer, index);
        if (CompilerDirectives.inCompiledCode()) {
            WasmFrame.dropReference(frame, stackPointer);
        }
    }

    private static void local_get(VirtualFrame frame, int stackPointer, int index) {
        WasmFrame.copyPrimitive(frame, index, stackPointer);
    }

    private static void local_get_ref(VirtualFrame frame, int stackPointer, int index) {
        WasmFrame.copyReference(frame, index, stackPointer);
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
            enterErrorBranch();
            throw WasmException.create(Failure.INT_OVERFLOW, this);
        }
        int result;
        try {
            result = y / x;
        } catch (ArithmeticException e) {
            enterErrorBranch();
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
            enterErrorBranch();
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
            enterErrorBranch();
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
            enterErrorBranch();
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
            enterErrorBranch();
            throw WasmException.create(Failure.INT_OVERFLOW, this);
        }
        final long result;
        try {
            result = y / x;
        } catch (ArithmeticException e) {
            enterErrorBranch();
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
            enterErrorBranch();
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
            enterErrorBranch();
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
            enterErrorBranch();
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

    private void i32_trunc_f32_s(VirtualFrame frame, int stackPointer) {
        final float x = popFloat(frame, stackPointer - 1);
        if (Float.isNaN(x)) {
            enterErrorBranch();
            throw WasmException.create(Failure.INVALID_CONVERSION_TO_INT);
        } else if (x < MIN_FLOAT_TRUNCATABLE_TO_INT || x > MAX_FLOAT_TRUNCATABLE_TO_INT) {
            enterErrorBranch();
            throw WasmException.create(Failure.INT_OVERFLOW);
        }
        final int result = (int) WasmMath.truncFloatToLong(x);
        pushInt(frame, stackPointer - 1, result);
    }

    private void i32_trunc_f32_u(VirtualFrame frame, int stackPointer) {
        final float x = popFloat(frame, stackPointer - 1);
        if (Float.isNaN(x)) {
            enterErrorBranch();
            throw WasmException.create(Failure.INVALID_CONVERSION_TO_INT);
        } else if (x < MIN_FLOAT_TRUNCATABLE_TO_U_INT || x > MAX_FLOAT_TRUNCATABLE_TO_U_INT) {
            enterErrorBranch();
            throw WasmException.create(Failure.INT_OVERFLOW);
        }
        final int result = (int) WasmMath.truncFloatToUnsignedLong(x);
        pushInt(frame, stackPointer - 1, result);
    }

    private void i32_trunc_f64_s(VirtualFrame frame, int stackPointer) {
        final double x = popDouble(frame, stackPointer - 1);
        if (Double.isNaN(x)) {
            enterErrorBranch();
            throw WasmException.create(Failure.INVALID_CONVERSION_TO_INT);
        } else if (x < MIN_DOUBLE_TRUNCATABLE_TO_INT || x > MAX_DOUBLE_TRUNCATABLE_TO_INT) {
            enterErrorBranch();
            throw WasmException.create(Failure.INT_OVERFLOW);
        }
        final int result = (int) WasmMath.truncDoubleToLong(x);
        pushInt(frame, stackPointer - 1, result);
    }

    private void i32_trunc_f64_u(VirtualFrame frame, int stackPointer) {
        final double x = popDouble(frame, stackPointer - 1);
        if (Double.isNaN(x)) {
            enterErrorBranch();
            throw WasmException.create(Failure.INVALID_CONVERSION_TO_INT);
        } else if (x < MIN_DOUBLE_TRUNCATABLE_TO_U_INT || x > MAX_DOUBLE_TRUNCATABLE_TO_U_INT) {
            enterErrorBranch();
            throw WasmException.create(Failure.INT_OVERFLOW);
        }
        final int result = (int) WasmMath.truncDoubleToUnsignedLong(x);
        pushInt(frame, stackPointer - 1, result);
    }

    private static void i32_trunc_sat_f32_s(VirtualFrame frame, int stackPointer) {
        final float x = popFloat(frame, stackPointer - 1);
        final int result = (int) ExactMath.truncate(x);
        pushInt(frame, stackPointer - 1, result);
    }

    private static void i32_trunc_sat_f32_u(VirtualFrame frame, int stackPointer) {
        final float x = popFloat(frame, stackPointer - 1);
        final int result;
        if (Float.isNaN(x) || x < MIN_FLOAT_TRUNCATABLE_TO_U_INT) {
            result = 0;
        } else if (x > MAX_FLOAT_TRUNCATABLE_TO_U_INT) {
            result = 0xffff_ffff;
        } else {
            result = (int) WasmMath.truncFloatToUnsignedLong(x);
        }
        pushInt(frame, stackPointer - 1, result);
    }

    private static void i32_trunc_sat_f64_s(VirtualFrame frame, int stackPointer) {
        final double x = popDouble(frame, stackPointer - 1);
        final int result = (int) ExactMath.truncate(x);
        pushInt(frame, stackPointer - 1, result);
    }

    private static void i32_trunc_sat_f64_u(VirtualFrame frame, int stackPointer) {
        final double x = popDouble(frame, stackPointer - 1);
        final int result;
        if (Double.isNaN(x) || x < MIN_DOUBLE_TRUNCATABLE_TO_U_INT) {
            result = 0;
        } else if (x > MAX_DOUBLE_TRUNCATABLE_TO_U_INT) {
            result = 0xffff_ffff;
        } else {
            result = (int) WasmMath.truncDoubleToUnsignedLong(x);
        }
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
        if (Float.isNaN(x)) {
            enterErrorBranch();
            throw WasmException.create(Failure.INVALID_CONVERSION_TO_INT);
        } else if (x < MIN_FLOAT_TRUNCATABLE_TO_LONG || x > MAX_FLOAT_TRUNCATABLE_TO_LONG) {
            enterErrorBranch();
            throw WasmException.create(Failure.INT_OVERFLOW);
        }
        final long result = WasmMath.truncFloatToLong(x);
        pushLong(frame, stackPointer - 1, result);
    }

    private void i64_trunc_f32_u(VirtualFrame frame, int stackPointer) {
        final float x = popFloat(frame, stackPointer - 1);
        if (Float.isNaN(x)) {
            enterErrorBranch();
            throw WasmException.create(Failure.INVALID_CONVERSION_TO_INT);
        } else if (x < MIN_FLOAT_TRUNCATABLE_TO_U_LONG || x > MAX_FLOAT_TRUNCATABLE_TO_U_LONG) {
            enterErrorBranch();
            throw WasmException.create(Failure.INT_OVERFLOW);
        }
        final long result = WasmMath.truncFloatToUnsignedLong(x);
        pushLong(frame, stackPointer - 1, result);
    }

    private void i64_trunc_f64_s(VirtualFrame frame, int stackPointer) {
        final double x = popDouble(frame, stackPointer - 1);
        if (Double.isNaN(x)) {
            enterErrorBranch();
            throw WasmException.create(Failure.INVALID_CONVERSION_TO_INT);
        } else if (x < MIN_DOUBLE_TRUNCATABLE_TO_LONG || x > MAX_DOUBLE_TRUNCATABLE_TO_LONG) {
            enterErrorBranch();
            throw WasmException.create(Failure.INT_OVERFLOW);
        }
        final long result = WasmMath.truncDoubleToLong(x);
        pushLong(frame, stackPointer - 1, result);
    }

    private void i64_trunc_f64_u(VirtualFrame frame, int stackPointer) {
        final double x = popDouble(frame, stackPointer - 1);
        if (Double.isNaN(x)) {
            enterErrorBranch();
            throw WasmException.create(Failure.INVALID_CONVERSION_TO_INT);
        } else if (x < MIN_DOUBLE_TRUNCATABLE_TO_U_LONG || x > MAX_DOUBLE_TRUNCATABLE_TO_U_LONG) {
            enterErrorBranch();
            throw WasmException.create(Failure.INT_OVERFLOW);
        }
        final long result = WasmMath.truncDoubleToUnsignedLong(x);
        pushLong(frame, stackPointer - 1, result);
    }

    private static void i64_trunc_sat_f32_s(VirtualFrame frame, int stackPointer) {
        final float x = popFloat(frame, stackPointer - 1);
        final long result = (long) ExactMath.truncate(x);
        pushLong(frame, stackPointer - 1, result);
    }

    private static void i64_trunc_sat_f32_u(VirtualFrame frame, int stackPointer) {
        final float x = popFloat(frame, stackPointer - 1);
        final long result;
        if (Float.isNaN(x) || x < MIN_FLOAT_TRUNCATABLE_TO_U_LONG) {
            result = 0;
        } else {
            result = WasmMath.truncFloatToUnsignedLong(x);
        }
        pushLong(frame, stackPointer - 1, result);
    }

    private static void i64_trunc_sat_f64_s(VirtualFrame frame, int stackPointer) {
        final double x = popDouble(frame, stackPointer - 1);
        final long result = (long) ExactMath.truncate(x);
        pushLong(frame, stackPointer - 1, result);
    }

    private static void i64_trunc_sat_f64_u(VirtualFrame frame, int stackPointer) {
        final double x = popDouble(frame, stackPointer - 1);
        final long result;
        if (Double.isNaN(x) || x < MIN_DOUBLE_TRUNCATABLE_TO_U_LONG) {
            result = 0;
        } else {
            result = WasmMath.truncDoubleToUnsignedLong(x);
        }
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
        float result = WasmMath.unsignedLongToFloat(x);
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
        double result = WasmMath.unsignedLongToDouble(x);
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
    private void table_init(WasmContext context, WasmInstance instance, int length, int source, int destination, int tableIndex, int elementIndex) {
        final WasmTable table = context.tables().table(instance.tableAddress(tableIndex));
        final Object[] elementInstance = instance.elemInstance(elementIndex);
        final int elementInstanceLength;
        if (elementInstance == null) {
            elementInstanceLength = 0;
        } else {
            elementInstanceLength = elementInstance.length;
        }
        if (checkOutOfBounds(source, length, elementInstanceLength) || checkOutOfBounds(destination, length, table.size())) {
            enterErrorBranch();
            throw WasmException.create(Failure.OUT_OF_BOUNDS_TABLE_ACCESS);
        }
        if (length == 0) {
            return;
        }
        table.initialize(elementInstance, source, destination, length);
    }

    private void table_get(WasmContext context, WasmInstance instance, VirtualFrame frame, int stackPointer, int index) {
        final WasmTable table = context.tables().table(instance.tableAddress(index));
        final int i = popInt(frame, stackPointer - 1);
        if (i < 0 || i >= table.size()) {
            enterErrorBranch();
            throw WasmException.create(Failure.OUT_OF_BOUNDS_TABLE_ACCESS);
        }
        final Object value = table.get(i);
        pushReference(frame, stackPointer - 1, value);
    }

    private void table_set(WasmContext context, WasmInstance instance, VirtualFrame frame, int stackPointer, int index) {
        final WasmTable table = context.tables().table(instance.tableAddress(index));
        final Object value = popReference(frame, stackPointer - 1);
        final int i = popInt(frame, stackPointer - 2);
        if (i < 0 || i >= table.size()) {
            enterErrorBranch();
            throw WasmException.create(Failure.OUT_OF_BOUNDS_TABLE_ACCESS);
        }
        table.set(i, value);
    }

    private static void table_size(WasmContext context, WasmInstance instance, VirtualFrame frame, int stackPointer, int index) {
        final WasmTable table = context.tables().table(instance.tableAddress(index));
        pushInt(frame, stackPointer, table.size());
    }

    @TruffleBoundary
    private static int table_grow(WasmContext context, WasmInstance instance, int length, Object value, int index) {
        final WasmTable table = context.tables().table(instance.tableAddress(index));
        return table.grow(length, value);
    }

    @TruffleBoundary
    private void table_copy(WasmContext context, WasmInstance instance, int length, int source, int destination, int sourceTableIndex, int destinationTableIndex) {
        final WasmTable sourceTable = context.tables().table(instance.tableAddress(sourceTableIndex));
        final WasmTable destinationTable = context.tables().table(instance.tableAddress(destinationTableIndex));
        if (checkOutOfBounds(source, length, sourceTable.size()) || checkOutOfBounds(destination, length, destinationTable.size())) {
            enterErrorBranch();
            throw WasmException.create(Failure.OUT_OF_BOUNDS_TABLE_ACCESS);
        }
        if (length == 0) {
            return;
        }
        destinationTable.copyFrom(sourceTable, source, destination, length);
    }

    @TruffleBoundary
    private void table_fill(WasmContext context, WasmInstance instance, int length, Object value, int offset, int index) {
        final WasmTable table = context.tables().table(instance.tableAddress(index));
        if (checkOutOfBounds(offset, length, table.size())) {
            enterErrorBranch();
            throw WasmException.create(Failure.OUT_OF_BOUNDS_TABLE_ACCESS);
        }
        if (length == 0) {
            return;
        }
        table.fill(offset, length, value);
    }

    @TruffleBoundary
    private void memory_init(WasmInstance instance, int length, int source, long destination, int dataIndex, int memoryIndex) {
        final WasmMemory memory = memory(instance, memoryIndex);
        final int dataOffset = instance.dataInstanceOffset(dataIndex);
        final int dataLength = instance.dataInstanceLength(dataIndex);
        if (checkOutOfBounds(source, length, dataLength) || checkOutOfBounds(destination, length, memory.byteSize())) {
            enterErrorBranch();
            throw WasmException.create(Failure.OUT_OF_BOUNDS_MEMORY_ACCESS);
        }
        if (length == 0) {
            return;
        }
        memory.initialize(codeEntry.bytecode(), dataOffset + source, destination, length);
    }

    @TruffleBoundary
    private void memory_init_unsafe(WasmInstance instance, int length, int source, long destination, int dataIndex, int memoryIndex) {
        final WasmMemory memory = memory(instance, memoryIndex);
        final long dataAddress = instance.dataInstanceAddress(dataIndex);
        final int dataLength = instance.dataInstanceLength(dataIndex);
        if (checkOutOfBounds(source, length, dataLength) || checkOutOfBounds(destination, length, memory.byteSize())) {
            enterErrorBranch();
            throw WasmException.create(Failure.OUT_OF_BOUNDS_MEMORY_ACCESS);
        }
        if (length == 0) {
            return;
        }
        memory.initializeUnsafe(dataAddress, source, destination, length);
    }

    @TruffleBoundary
    private static void data_drop(WasmInstance instance, int dataIndex) {
        instance.dropDataInstance(dataIndex);
    }

    @TruffleBoundary
    private static void data_drop_unsafe(WasmInstance instance, int dataIndex) {
        instance.dropUnsafeDataInstance(dataIndex);
    }

    @TruffleBoundary
    private void memory_fill(WasmInstance instance, long length, int value, long offset, int memoryIndex) {
        final WasmMemory memory = memory(instance, memoryIndex);
        if (checkOutOfBounds(offset, length, memory.byteSize())) {
            enterErrorBranch();
            throw WasmException.create(Failure.OUT_OF_BOUNDS_MEMORY_ACCESS);
        }
        if (length == 0L) {
            return;
        }
        memory.fill(offset, length, (byte) value);
    }

    @TruffleBoundary
    private void memory_copy(WasmInstance instance, long length, long source, long destination, int destMemoryIndex, int srcMemoryIndex) {
        final WasmMemory destMemory = memory(instance, destMemoryIndex);
        final WasmMemory srcMemory = memory(instance, srcMemoryIndex);
        if (checkOutOfBounds(source, length, srcMemory.byteSize()) || checkOutOfBounds(destination, length, destMemory.byteSize())) {
            enterErrorBranch();
            throw WasmException.create(Failure.OUT_OF_BOUNDS_MEMORY_ACCESS);
        }
        if (length == 0L) {
            return;
        }
        destMemory.copyFrom(srcMemory, source, destination, length);
    }

    // Checkstyle: resume method name check

    private static boolean checkOutOfBounds(long offset, long length, long size) {
        return offset < 0L || length < 0L || offset + length < 0L || offset + length > size;
    }

    private static boolean checkOutOfBounds(int offset, int length, int size) {
        return offset < 0 || length < 0 || offset + length < 0 || offset + length > size;
    }

    @TruffleBoundary
    public void resolveCallNode(WasmInstance instance, int callNodeIndex) {
        Node unresolvedCallNode = callNodes[callNodeIndex];
        if (unresolvedCallNode instanceof WasmCallStubNode) {
            final WasmFunction function = ((WasmCallStubNode) unresolvedCallNode).function();
            final CallTarget target = instance.target(function.index());
            callNodes[callNodeIndex] = DirectCallNode.create(target);
        } else {
            assert unresolvedCallNode instanceof WasmIndirectCallNode : unresolvedCallNode;
        }
    }

    @ExplodeLoop
    private Object[] createArgumentsForCall(VirtualFrame frame, int functionTypeIndex, int numArgs, int stackPointerOffset) {
        CompilerAsserts.partialEvaluationConstant(numArgs);
        Object[] args = WasmArguments.createEmpty(numArgs);
        int stackPointer = stackPointerOffset;
        for (int i = numArgs - 1; i >= 0; --i) {
            stackPointer--;
            byte type = module.symbolTable().functionTypeParamTypeAt(functionTypeIndex, i);
            CompilerAsserts.partialEvaluationConstant(type);
            Object arg = switch (type) {
                case WasmType.I32_TYPE -> popInt(frame, stackPointer);
                case WasmType.I64_TYPE -> popLong(frame, stackPointer);
                case WasmType.F32_TYPE -> popFloat(frame, stackPointer);
                case WasmType.F64_TYPE -> popDouble(frame, stackPointer);
                case WasmType.FUNCREF_TYPE, WasmType.EXTERNREF_TYPE -> popReference(frame, stackPointer);
                default -> throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Unknown type: %d", type);
            };
            WasmArguments.setArgument(args, i, arg);
        }
        return args;
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
    private static void unwindReferenceStack(VirtualFrame frame, int stackPointer, int targetStackPointer, int targetResultCount) {
        CompilerAsserts.partialEvaluationConstant(stackPointer);
        CompilerAsserts.partialEvaluationConstant(targetResultCount);
        for (int i = 0; i < targetResultCount; ++i) {
            WasmFrame.copyReference(frame, stackPointer + i - targetResultCount, targetStackPointer + i);
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

    private static boolean profileCondition(byte[] data, final int profileOffset, boolean condition) {
        int t = rawPeekU8(data, profileOffset);
        int f = rawPeekU8(data, profileOffset + 1);
        boolean val = condition;
        if (val) {
            if (!CompilerDirectives.inInterpreter()) {
                if (t == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                if (f == 0) {
                    // Make this branch fold during PE
                    val = true;
                }
            } else {
                if (t < MAX_PROFILE_VALUE) {
                    data[profileOffset]++;
                }
            }
        } else {
            if (!CompilerDirectives.inInterpreter()) {
                if (f == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                if (t == 0) {
                    // Make this branch fold during PE
                    val = false;
                }
            } else {
                if (f < MAX_PROFILE_VALUE) {
                    data[profileOffset + 1]++;
                }
            }
        }
        if (CompilerDirectives.inInterpreter()) {
            return val;
        } else {
            int sum = t + f;
            return CompilerDirectives.injectBranchProbability((double) t / (double) sum, val);
        }
    }

    private static void updateBranchTableProfile(byte[] data, final int counterOffset, final int profileOffset) {
        assert CompilerDirectives.inInterpreter();
        int counter = rawPeekU16(data, counterOffset);
        if (counter < MAX_TABLE_PROFILE_VALUE) {
            BinaryStreamParser.writeU16(data, counterOffset, counter + 1);
            BinaryStreamParser.writeU16(data, profileOffset, rawPeekU16(data, profileOffset) + 1);
        }
    }

    private static boolean profileBranchTable(byte[] data, final int counterOffset, final int profileOffset, boolean condition) {
        int t = rawPeekU16(data, profileOffset);
        int sum = rawPeekU16(data, counterOffset);
        boolean val = condition;
        if (val) {
            if (t == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (t == sum) {
                // Make this branch fold during PE
                val = true;
            }
        } else {
            if (t == sum) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (t == 0) {
                // Make this branch fold during PE
                val = false;
            }
        }
        // Clamp probability
        final double probability = Math.min((double) t / (double) sum, 1.0);
        return CompilerDirectives.injectBranchProbability(probability, val);
    }

    /**
     * Extracts the multi value from the multi-value stack of the context or an external source. The
     * result values are put onto the value stack.
     *
     * @param frame The current frame.
     * @param stackPointer The current stack pointer.
     * @param result The result of the function call.
     * @param resultCount The expected number or result values.
     * @param functionTypeIndex The function type index of the called function.
     */
    @ExplodeLoop
    private void extractMultiValueResult(VirtualFrame frame, int stackPointer, Object result, int resultCount, int functionTypeIndex) {
        CompilerAsserts.partialEvaluationConstant(resultCount);
        if (result == WasmConstant.MULTI_VALUE) {
            final var multiValueStack = WasmLanguage.get(this).multiValueStack();
            final long[] primitiveMultiValueStack = multiValueStack.primitiveStack();
            final Object[] referenceMultiValueStack = multiValueStack.referenceStack();
            for (int i = 0; i < resultCount; i++) {
                final byte resultType = module.symbolTable().functionTypeResultTypeAt(functionTypeIndex, i);
                CompilerAsserts.partialEvaluationConstant(resultType);
                switch (resultType) {
                    case WasmType.I32_TYPE:
                        pushInt(frame, stackPointer + i, (int) primitiveMultiValueStack[i]);
                        break;
                    case WasmType.I64_TYPE:
                        pushLong(frame, stackPointer + i, primitiveMultiValueStack[i]);
                        break;
                    case WasmType.F32_TYPE:
                        pushFloat(frame, stackPointer + i, Float.intBitsToFloat((int) primitiveMultiValueStack[i]));
                        break;
                    case WasmType.F64_TYPE:
                        pushDouble(frame, stackPointer + i, Double.longBitsToDouble(primitiveMultiValueStack[i]));
                        break;
                    case WasmType.FUNCREF_TYPE:
                    case WasmType.EXTERNREF_TYPE:
                        pushReference(frame, stackPointer + i, referenceMultiValueStack[i]);
                        break;
                    default:
                        enterErrorBranch();
                        throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Unknown result type: %d", resultType);
                }
            }
        } else {
            // Multi-value is provided by an external source
            final InteropLibrary lib = InteropLibrary.getUncached();
            if (!lib.hasArrayElements(result)) {
                enterErrorBranch();
                throw WasmException.create(Failure.UNSUPPORTED_MULTI_VALUE_TYPE);
            }
            try {
                final int size = (int) lib.getArraySize(result);
                if (size != resultCount) {
                    enterErrorBranch();
                    throw WasmException.create(Failure.INVALID_MULTI_VALUE_ARITY);
                }
                for (int i = 0; i < size; i++) {
                    byte resultType = module.symbolTable().functionTypeResultTypeAt(functionTypeIndex, i);
                    Object value = lib.readArrayElement(result, i);
                    switch (resultType) {
                        case WasmType.I32_TYPE:
                            pushInt(frame, stackPointer + i, lib.asInt(value));
                            break;
                        case WasmType.I64_TYPE:
                            pushLong(frame, stackPointer + i, lib.asLong(value));
                            break;
                        case WasmType.F32_TYPE:
                            pushFloat(frame, stackPointer + i, lib.asFloat(value));
                            break;
                        case WasmType.F64_TYPE:
                            pushDouble(frame, stackPointer + i, lib.asDouble(value));
                            break;
                        case WasmType.FUNCREF_TYPE:
                        case WasmType.EXTERNREF_TYPE:
                            pushReference(frame, stackPointer + i, value);
                            break;
                        default:
                            enterErrorBranch();
                            throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Unknown result type: %d", resultType);
                    }
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                enterErrorBranch();
                throw WasmException.create(Failure.INVALID_TYPE_IN_MULTI_VALUE);
            }
        }
    }
}
