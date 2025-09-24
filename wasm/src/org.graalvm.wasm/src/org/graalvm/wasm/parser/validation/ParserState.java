/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.parser.validation;

import static java.lang.Integer.compareUnsigned;

import java.util.ArrayList;

import org.graalvm.wasm.Assert;
import org.graalvm.wasm.SymbolTable;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.api.Vector128;
import org.graalvm.wasm.collection.IntArrayList;
import org.graalvm.wasm.constants.Bytecode;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.parser.bytecode.RuntimeBytecodeGen;

/**
 * Represents the values and stack frames of a Wasm code section during validation. Stores
 * additional information used to generate parser nodes.
 */
public class ParserState {
    private static final int[] EMPTY_ARRAY = new int[0];

    private final IntArrayList valueStack;
    private final ControlStack controlStack;
    private final RuntimeBytecodeGen bytecode;
    private final ArrayList<ExceptionTable> exceptionTables;
    private final SymbolTable symbolTable;

    private int maxStackSize;
    private boolean usesMemoryZero;

    public ParserState(RuntimeBytecodeGen bytecode, SymbolTable symbolTable) {
        this.valueStack = new IntArrayList();
        this.controlStack = new ControlStack();
        this.bytecode = bytecode;
        this.exceptionTables = new ArrayList<>();
        this.symbolTable = symbolTable;

        this.maxStackSize = 0;
    }

    /**
     * Pops a value from the stack if possible. Throws an error on stack underflow.
     *
     * @param expectedValueType The expectedValueType used for error generation.
     * @return The top of the stack or -1.
     */
    private int popInternal(int expectedValueType) {
        if (availableStackSize() == 0) {
            if (isCurrentStackUnreachable()) {
                return WasmType.UNKNOWN_TYPE;
            } else {
                if (expectedValueType == WasmType.TOP) {
                    throw ValidationErrors.createExpectedTopOnEmptyStack();
                } else {
                    throw ValidationErrors.createExpectedTypeOnEmptyStack(expectedValueType);
                }
            }
        }
        return valueStack.popBack();
    }

    /**
     * Pops the maximum available values from the current stack frame. If the number of values on
     * the stack is smaller than the number of expectedValueTypes, only the remaining stack values
     * are returned. If the number of values on the stack is greater or equal to the number of
     * expectedValueTypes, the values equal to the number of expectedValueTypes is popped from the
     * stack.
     *
     * @param expectedValueTypes Value types expected on the stack.
     * @return The maximum of available stack values smaller than the length of expectedValueTypes.
     */
    private int[] popAvailableUnchecked(int[] expectedValueTypes) {
        int availableStackSize = availableStackSize();
        int availableSize = Math.min(availableStackSize, expectedValueTypes.length);
        int[] popped = new int[availableSize];
        for (int i = availableSize - 1; i >= 0; i--) {
            popped[i] = popInternal(expectedValueTypes[i]);
        }
        return popped;
    }

    /**
     * Pops the maximum available values from the current stack frame.
     *
     * @return The maximum of available stack values.
     */
    private int[] popAvailableUnchecked() {
        int availableStackSize = availableStackSize();
        int[] popped = new int[availableStackSize];
        int j = 0;
        for (int i = availableStackSize - 1; i >= 0; i--) {
            popped[j] = popInternal(WasmType.TOP);
            j++;
        }
        return popped;
    }

    /**
     * Checks if two sets of value types are equivalent.
     *
     * @param expectedTypes The expected value types.
     * @param actualTypes The actual value types.
     * @return True if both are equivalent.
     */
    private boolean isTypeMismatch(int[] expectedTypes, int[] actualTypes) {
        if (expectedTypes.length != actualTypes.length) {
            return true;
        }
        if (isCurrentStackUnreachable()) {
            return false;
        }
        for (int i = 0; i < expectedTypes.length; i++) {
            if (!symbolTable.matches(expectedTypes[i], actualTypes[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pushes a value type onto the stack.
     *
     * @param valueType The value type that should be added.
     */
    public void push(int valueType) {
        valueStack.add(valueType);
        maxStackSize = Math.max(valueStack.size(), maxStackSize);
    }

    /**
     * Pushes all provided value types onto the stack.
     *
     * @param valueTypes The value types that should be added.
     */
    public void pushAll(int[] valueTypes) {
        for (int valueType : valueTypes) {
            push(valueType);
        }
    }

    /**
     * Pops the topmost value type from the stack. Throws an error if the stack is empty.
     *
     * @return The value type on top of the stack or -1.
     * @throws WasmException If the stack is empty.
     */
    public int pop() {
        return popInternal(WasmType.TOP);
    }

    /**
     * Pops the topmost value type from the stack and checks if it is equivalent to the given value.
     *
     * @param expectedValueType The expected value type.
     * @return The value type on top of the stack.
     * @throws WasmException If the stack is empty or the value types do not match.
     */
    public int popChecked(int expectedValueType) {
        final int actualValueType = popInternal(expectedValueType);
        if (!symbolTable.matches(expectedValueType, actualValueType) && actualValueType != WasmType.UNKNOWN_TYPE && expectedValueType != WasmType.UNKNOWN_TYPE) {
            throw ValidationErrors.createTypeMismatch(expectedValueType, actualValueType);
        }
        return actualValueType;
    }

    /**
     * Pops the topmost value type from the stack and checks if it is a reference type.
     *
     * @throws WasmException If the stack is empty or the value type is not a reference type.
     */
    public void popReferenceTypeChecked() {
        if (availableStackSize() != 0) {
            final int value = valueStack.popBack();
            if (WasmType.isReferenceType(value)) {
                return;
            }
            // Push value back onto the stack and perform a checked pop to get the correct error
            // message
            valueStack.push(value);
        }
        popChecked(WasmType.FUNCREF_TYPE);
    }

    /**
     * Pops the topmost value types from the stack and checks if they are equivalent to the given
     * set of value types.
     *
     * @param expectedValueTypes The expected value types.
     * @return The value types on top of the stack.
     * @throws WasmException If the stack is empty or the value types do not match.
     */
    public int[] popAll(int[] expectedValueTypes) {
        int[] popped = new int[expectedValueTypes.length];
        for (int i = expectedValueTypes.length - 1; i >= 0; i--) {
            popped[i] = popChecked(expectedValueTypes[i]);
        }
        return popped;
    }

    /**
     * @return The number of remaining values of the current control stack.
     */
    private int availableStackSize() {
        return valueStack.size() - controlStack.peek().initialStackSize();
    }

    /**
     * @return True, if the current control stack is declared as unreachable.
     */
    private boolean isCurrentStackUnreachable() {
        return controlStack.peek().isUnreachable();
    }

    /**
     * Removes all stack value up to a defined stack size.
     *
     * @param size The size of the stack after removing the values.
     */
    private void unwindStack(int size) {
        assert compareUnsigned(size, valueStack.size()) <= 0 : "invalid stack shrink size";
        while (valueStack.size() > size) {
            valueStack.popBack();
        }
    }

    public void enterFunction(int[] resultTypes) {
        enterBlock(EMPTY_ARRAY, resultTypes);
    }

    /**
     * Creates a new block frame that holds information about the current block and pushes it onto
     * the control frame stack.
     *
     * @param paramTypes The param types of the block that was entered.
     * @param resultTypes The result types of the block that was entered.
     */
    public void enterBlock(int[] paramTypes, int[] resultTypes) {
        ControlFrame frame = new BlockFrame(paramTypes, resultTypes, valueStack.size(), false);
        controlStack.push(frame);
        pushAll(paramTypes);
    }

    /**
     * Creates a new loop frame that holds information about the current loop and pushes it onto the
     * control frame stack.
     *
     * @param paramTypes The param types of the loop that was entered.
     * @param resultTypes The result types of the loop that was entered.
     */
    public void enterLoop(int[] paramTypes, int[] resultTypes) {
        final int label = bytecode.addLoopLabel(paramTypes.length, valueStack.size(), WasmType.getCommonValueType(resultTypes));
        ControlFrame frame = new LoopFrame(paramTypes, resultTypes, valueStack.size(), false, label);
        controlStack.push(frame);
        pushAll(paramTypes);
    }

    /**
     * Creates a new if frame that holds information about the current if and pushes it onto the
     * control frame stack.
     *
     * @param paramTypes The param types of the if and else branch that was entered.
     * @param resultTypes The result type of the if and else branch that was entered.
     */
    public void enterIf(int[] paramTypes, int[] resultTypes) {
        final int fixupLocation = bytecode.addIfLocation();
        ControlFrame frame = new IfFrame(paramTypes, resultTypes, valueStack.size(), false, fixupLocation);
        controlStack.push(frame);
        pushAll(paramTypes);
    }

    /**
     * Gets the current control frame and tries to enter the else branch.
     */
    public void enterElse() {
        ControlFrame frame = controlStack.peek();
        frame.enterElse(this, bytecode);
        pushAll(frame.paramTypes());
    }

    /**
     * Creates a new try-table frame that holds information about the current try table and pushes
     * it onto the control frame stack.
     * 
     * @param paramTypes The param types of the try table that was entered.
     * @param resultTypes The result types of the try table that was entered.
     * @param handlers The exception handlers of the try table that was entered.
     */
    public void enterTryTable(int[] paramTypes, int[] resultTypes, ExceptionHandler[] handlers) {
        final TryTableFrame frame = new TryTableFrame(paramTypes, resultTypes, valueStack.size(), false, bytecode.location(), handlers);
        controlStack.push(frame);

        exceptionTables.add(frame.table());
    }

    /**
     * Creates a new catch frame that holds information about the current catch clause and pushes it
     * onto the control frame stack.
     * 
     * @param opcode The opcode of the catch clause (exception handler type, see
     *            {@link org.graalvm.wasm.constants.ExceptionHandlerType}).
     * @param tag The tag of the catch clause, if available.
     * @param label The target label of the catch clause.
     * @return A new exception handler for the catch clause.
     */
    public ExceptionHandler enterCatchClause(int opcode, int tag, int label) {
        checkLabelExists(label);
        final ControlFrame labelFrame = getFrame(label);
        // we reuse the block frame, instead of introducing a new catch frame.
        final ControlFrame frame = new BlockFrame(WasmType.VOID_TYPE_ARRAY, labelFrame.labelTypes(), labelFrame.initialStackSize(), false);
        controlStack.push(frame);
        final ExceptionHandler e = new ExceptionHandler(opcode, tag);
        labelFrame.addExceptionHandler(e);
        return e;
    }

    /**
     * @return Whether the function contains any exception handlers.
     */
    public boolean needsExceptionTable() {
        return !exceptionTables.isEmpty();
    }

    /**
     * Generates an exception table at the current location in the bytecode. The exception table has
     * entries in the format:
     * 
     * <pre>
     *     from (4 byte) | to (4 byte) | opcode (1 byte) | tag (4 byte) (optional) | target (4 byte)
     * </pre>
     *
     * The exception table has a single 4 byte entry (0xffff_ffff) to indicate the end of the table.
     */
    public void generateExceptionTable() {
        for (ExceptionTable table : exceptionTables) {
            table.generateExceptionTable(bytecode);
        }
        // add end indicator
        bytecode.add(-1);
    }

    public void addInstruction(int instruction) {
        bytecode.addOp(instruction);
    }

    public void addSelectInstruction(int instruction) {
        bytecode.addSelect(instruction);
    }

    /**
     * Performs the necessary branch checks and adds the conditional branch information in the extra
     * data array.
     *
     * @param branchLabel The target label.
     */
    public void addConditionalBranch(int branchLabel) {
        checkLabelExists(branchLabel);
        ControlFrame frame = getFrame(branchLabel);
        final int[] labelTypes = frame.labelTypes();
        popAll(labelTypes);
        pushAll(labelTypes);
        frame.addBranchIf(bytecode);
    }

    /**
     * Performs the necessary branch checks and adds the unconditional branch information to the
     * extra data array.
     *
     * @param branchLabel The target label.
     */
    public void addUnconditionalBranch(int branchLabel) {
        checkLabelExists(branchLabel);
        ControlFrame frame = getFrame(branchLabel);
        final int[] labelTypes = frame.labelTypes();
        popAll(labelTypes);
        frame.addBranch(bytecode);
    }

    /**
     * Performs the necessary branch checks and adds the branch table information to the extra data
     * array.
     *
     * @param branchLabels The target labels.
     */
    public void addBranchTable(int[] branchLabels) {
        bytecode.addBranchTable(branchLabels.length);
        int branchLabel = branchLabels[branchLabels.length - 1];
        checkLabelExists(branchLabel);
        ControlFrame frame = getFrame(branchLabel);
        int[] branchLabelReturnTypes = frame.labelTypes();
        for (int otherBranchLabel : branchLabels) {
            checkLabelExists(otherBranchLabel);
            frame = getFrame(otherBranchLabel);
            int[] otherBranchLabelReturnTypes = frame.labelTypes();
            checkLabelTypes(branchLabelReturnTypes, otherBranchLabelReturnTypes);
            pushAll(popAll(otherBranchLabelReturnTypes));
            frame.addBranchTableItem(bytecode);
        }
        popAll(branchLabelReturnTypes);
    }

    /**
     * Performs the necessary checks for a function return.
     *
     * @param multiValue If multiple return values are supported.
     */
    public void addReturn(boolean multiValue) {
        ControlFrame frame = getRootBlock();
        if (!multiValue) {
            Assert.assertIntLessOrEqual(frame.labelTypeLength(), 1, Failure.INVALID_RESULT_ARITY);
        }
        checkResultTypes(frame);

        bytecode.addOp(Bytecode.RETURN);
    }

    /**
     * Adds the index of an indirect call node to the extra data array.
     *
     * @param nodeIndex The index of the indirect call.
     */
    public void addIndirectCall(int nodeIndex, int typeIndex, int tableIndex) {
        bytecode.addIndirectCall(nodeIndex, typeIndex, tableIndex);
    }

    /**
     * Adds the index of a direct call node to the extra data array.
     *
     * @param nodeIndex The index of the direct call.
     */
    public void addCall(int nodeIndex, int functionIndex) {
        bytecode.addCall(nodeIndex, functionIndex);
    }

    /**
     * Adds the mics flag to the bytecode.
     */
    public void addMiscFlag() {
        bytecode.addOp(Bytecode.MISC);
    }

    /**
     * Adds the atomic flag to the bytecode.
     */
    public void addAtomicFlag() {
        bytecode.addOp(Bytecode.ATOMIC);
    }

    /**
     * Adds the vector flag to the bytecode.
     */
    public void addVectorFlag() {
        bytecode.addOp(Bytecode.VECTOR);
    }

    /**
     * Adds the given instruction and an i32 immediate value to the bytecode.
     *
     * @param instruction The instruction
     * @param value The immediate value
     */
    public void addInstruction(int instruction, int value) {
        bytecode.addOp(instruction, value);
    }

    /**
     * Adds the given instruction and an i64 immediate value to the bytecode.
     *
     * @param instruction The instruction
     * @param value The immediate value
     */
    public void addInstruction(int instruction, long value) {
        bytecode.addOp(instruction, value);
    }

    /**
     * Adds the given instruction and an i128 immediate value to the bytecode.
     *
     * @param instruction The instruction
     * @param value The immediate value
     */
    public void addInstruction(int instruction, Vector128 value) {
        bytecode.addOp(instruction, value);
    }

    /**
     * Adds the given instruction and two i32 immediate values to the bytecode.
     *
     * @param instruction The instruction
     * @param value1 The first immediate value
     * @param value2 The second immediate value
     */
    public void addInstruction(int instruction, int value1, int value2) {
        bytecode.addOp(instruction, value1, value2);
    }

    /**
     * Adds the i8 or i32 version of the given instruction to the bytecode based on the given
     * immediate value. If the value fits into a signed i8 value, the i8 instruction and an i8 value
     * are added. Otherwise, the i32 instruction and an i32 value are added.
     *
     * @param instruction The i8 version of the instruction
     * @param value The immediate value.
     */
    public void addSignedInstruction(int instruction, int value) {
        bytecode.addSigned(instruction, instruction + 1, value);
    }

    /**
     * Adds the i8 or i64 version of the given instruction to the bytecode based on the given
     * immediate value. If the value fits into a signed i8 value, the i8 instruction and an i8 value
     * are added. Otherwise, the i64 instruction and i64 value are added.
     *
     * @param instruction The i8 version of the instruction
     * @param value The immediate value
     */
    public void addSignedInstruction(int instruction, long value) {
        bytecode.addSigned(instruction, instruction + 1, value);
    }

    /**
     * Adds the u8 or i32 version of the given instruction to the bytecode based on the given
     * immediate value. If the value fits into a u8 value, the u8 instruction and a u8 value are
     * added. Otherwise, the i32 instruction and an i32 value are added.
     *
     * @param instruction The u8 version of the instruction
     * @param value The immediate value
     */
    public void addUnsignedInstruction(int instruction, int value) {
        bytecode.addUnsigned(instruction, instruction + 1, value);
    }

    /**
     * Adds a memory instruction based on the given values and index type.
     *
     * @param baseInstruction The base version of the memory instruction
     * @param memoryIndex The index of the memory being accessed
     * @param value The immediate value
     * @param indexType64 If the index type is 64 bit.
     */
    public void addMemoryInstruction(int baseInstruction, int memoryIndex, long value, boolean indexType64) {
        markMemoryUsed(memoryIndex);
        bytecode.addMemoryInstruction(baseInstruction, baseInstruction + 1, baseInstruction + 2, memoryIndex, value, indexType64);
    }

    /**
     * Adds an extended (atomic or vector) memory instruction based on the given values and index
     * type.
     *
     * @param instruction The extended memory instruction
     * @param memoryIndex The index of the memory being accessed
     * @param value The immediate value
     * @param indexType64 If the index type is 64 bit.
     */
    public void addExtendedMemoryInstruction(int instruction, int memoryIndex, long value, boolean indexType64) {
        markMemoryUsed(memoryIndex);
        bytecode.addExtendedMemoryInstruction(instruction, memoryIndex, value, indexType64);
    }

    /**
     * Adds a lane-indexed vector memory instruction based on the given values and index type.
     *
     * @param instruction The vector memory instruction
     * @param memoryIndex The index of the memory being accessed
     * @param value The immediate value
     * @param indexType64 If the index type is 64 bit.
     * @param laneIndex The lane index
     */
    public void addVectorMemoryLaneInstruction(int instruction, int memoryIndex, long value, boolean indexType64, byte laneIndex) {
        markMemoryUsed(memoryIndex);
        bytecode.addExtendedMemoryInstruction(instruction, memoryIndex, value, indexType64);
        bytecode.add(laneIndex);
    }

    /**
     * Adds a lane-indexed vector instruction (extract_lane or replace_lane).
     *
     * @param instruction The vector instruction
     * @param laneIndex The lane index
     */
    public void addVectorLaneInstruction(int instruction, byte laneIndex) {
        bytecode.addOp(instruction);
        bytecode.add(laneIndex);
    }

    /**
     * Finishes the current control frame and removes it from the control frame stack.
     *
     * @param multiValue If multiple return values are supported.
     *
     * @throws WasmException If the number of return value types do not match with the remaining
     *             stack or the number of return values is greater than 1.
     * 
     * @return The types of the return values of the current frame.
     */
    public int[] exit(boolean multiValue) {
        Assert.assertTrue(!controlStack.isEmpty(), Failure.UNEXPECTED_END_OF_BLOCK);
        ControlFrame frame = controlStack.peek();
        int[] resultTypes = frame.resultTypes();
        frame.exit(bytecode);
        checkStackAfterFrameExit(frame, resultTypes);

        controlStack.pop();
        if (!multiValue) {
            Assert.assertIntLessOrEqual(resultTypes.length, 1, "A block cannot return more than one value.", Failure.INVALID_RESULT_ARITY);
        }
        return resultTypes;
    }

    /**
     * Checks that the expected return types are actually on the value stack.
     *
     * @param frame The frame that is exited.
     * @param resultTypes The expected return types of the frame.
     */
    void checkStackAfterFrameExit(ControlFrame frame, int[] resultTypes) {
        if (availableStackSize() > resultTypes.length) {
            int[] actualTypes = popAvailableUnchecked();
            if (isTypeMismatch(resultTypes, actualTypes)) {
                throw ValidationErrors.createResultTypesMismatch(resultTypes, actualTypes);
            }
        }
        checkResultTypes(frame);
    }

    /**
     * @param index The index of the frame from the top of the stack.
     * @return The frame at the given index.
     */
    public ControlFrame getFrame(int index) {
        return controlStack.get(index);
    }

    /**
     * @return The first (lowest) frame on the stack.
     */
    public ControlFrame getRootBlock() {
        return controlStack.getFirst();
    }

    /**
     * Checks if the return value types of the given control frame match the remaining value types
     * on the stack.
     *
     * @param frame The control frame for which the return values should be checked.
     * @throws WasmException If the return value types do not match the remaining value type on the
     *             stack.
     */
    private void checkResultTypes(ControlFrame frame) {
        int[] resultTypes = frame.resultTypes();
        if (isCurrentStackUnreachable()) {
            popAll(resultTypes);
        } else {
            int[] actualTypes = popAvailableUnchecked(resultTypes);
            if (isTypeMismatch(resultTypes, actualTypes)) {
                throw ValidationErrors.createResultTypesMismatch(resultTypes, actualTypes);
            }
        }
    }

    /**
     * Checks if the given parameter value types do match the current value types on the stack.
     *
     * @param paramTypes The expected value types.
     * @throws WasmException If the parameter value types and the vale types on the stack do not
     *             match.
     */
    public void checkParamTypes(int[] paramTypes) {
        if (isCurrentStackUnreachable()) {
            popAll(paramTypes);
        } else {
            int[] actualTypes = popAvailableUnchecked(paramTypes);
            if (isTypeMismatch(paramTypes, actualTypes)) {
                throw ValidationErrors.createParamTypesMismatch(paramTypes, actualTypes);
            }
        }
    }

    /**
     * Checks if the given label is a valid jump target.
     *
     * @param label The label which to jump to.
     * @throws WasmException If the label is out or reach.
     */
    public void checkLabelExists(int label) {
        if (compareUnsigned(label, controlStackSize()) >= 0) {
            throw ValidationErrors.createMissingLabel(label, controlStackSize() - 1);
        }
    }

    /**
     * Checks if the value types of two different labels match.
     *
     * @param expectedTypes The expected value types.
     * @param actualTypes The value types that should be checked.
     * @throws WasmException If the provided sets of value types do not match.
     */
    public void checkLabelTypes(int[] expectedTypes, int[] actualTypes) {
        if (isTypeMismatch(expectedTypes, actualTypes)) {
            throw ValidationErrors.createLabelTypesMismatch(expectedTypes, actualTypes);
        }
    }

    /**
     * Checks if the given function type is within range.
     *
     * @param typeIndex The function type.
     * @param max The number of available function types.
     * @throws WasmException If the given function type is greater or equal to the given maximum.
     */
    public void checkFunctionTypeExists(int typeIndex, int max) {
        if (compareUnsigned(typeIndex, max) >= 0) {
            throw ValidationErrors.createMissingFunctionType(typeIndex, max - 1);
        }
    }

    /**
     * Sets the current control stack unreachable.
     */
    public void setUnreachable() {
        ControlFrame frame = controlStack.peek();
        unwindStack(frame.initialStackSize());
        frame.setUnreachable();
    }

    public int controlStackSize() {
        return controlStack.size();
    }

    public int valueStackSize() {
        return valueStack.size();
    }

    public int maxStackSize() {
        return maxStackSize;
    }

    private void markMemoryUsed(int memoryIndex) {
        if (memoryIndex == 0) {
            usesMemoryZero = true;
        }
    }

    public boolean usesMemoryZero() {
        return usesMemoryZero;
    }
}
