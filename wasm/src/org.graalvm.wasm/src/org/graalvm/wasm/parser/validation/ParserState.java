/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.wasm.Assert;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.collection.ByteArrayList;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.parser.validation.collections.ControlStack;
import org.graalvm.wasm.parser.validation.collections.ExtraDataList;
import org.graalvm.wasm.parser.validation.collections.entries.BranchTableEntry;
import org.graalvm.wasm.util.ExtraDataUtil;

/**
 * Represents the values and stack frames of a Wasm code section during validation. Stores
 * additional information used to generate parser nodes.
 */
public class ParserState {
    private static final byte[] EMPTY_ARRAY = new byte[0];
    private static final byte ANY = 0;

    private final ByteArrayList valueStack;
    private final ControlStack controlStack;
    private final ExtraDataList extraData;

    private int maxStackSize;

    public ParserState() {
        this.valueStack = new ByteArrayList();
        this.controlStack = new ControlStack();
        this.extraData = new ExtraDataList();

        this.maxStackSize = 0;
    }

    /**
     * Pops a value from the stack if possible. Throws an error on stack underflow.
     * 
     * @param expectedValueType The expectedValueType used for error generation.
     * @return The top of the stack or -1.
     */
    private byte popInternal(byte expectedValueType) {
        if (availableStackSize() == 0) {
            if (isCurrentStackUnreachable()) {
                return WasmType.UNKNOWN_TYPE;
            } else {
                if (expectedValueType == ANY) {
                    throw ValidationErrors.createExpectedAnyOnEmptyStack();
                } else {
                    throw ValidationErrors.createExpectedTypeOnEmptyStack(expectedValueType);
                }
            }
        }
        return valueStack.popBack();
    }

    /**
     * Pops the maximum available values form the current stack frame. If the number of values on
     * the stack is smaller than the number of expectedValueTypes, only the remaining stack values
     * are returned. If the number of values on the stack is greater or equal to the number of
     * expectedValueTypes, the values equal to the number of expectedValueTypes is popped from the
     * stack.
     * 
     * @param expectedValueTypes Value types expected on the stack.
     * @return The maximum of available stack values smaller than the length of expectedValueTypes.
     */
    private byte[] popAvailableUnchecked(byte[] expectedValueTypes) {
        int availableStackSize = availableStackSize();
        int availableSize = Math.min(availableStackSize, expectedValueTypes.length);
        byte[] popped = new byte[availableSize];
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
    private byte[] popAvailableUnchecked() {
        int availableStackSize = availableStackSize();
        byte[] popped = new byte[availableStackSize];
        int j = 0;
        for (int i = availableStackSize - 1; i >= 0; i--) {
            popped[j] = popInternal(ANY);
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
    private boolean isTypeMismatch(byte[] expectedTypes, byte[] actualTypes) {
        if (expectedTypes.length != actualTypes.length) {
            return true;
        }
        if (isCurrentStackUnreachable()) {
            return false;
        }
        for (int i = 0; i < expectedTypes.length; i++) {
            if (expectedTypes[i] != actualTypes[i]) {
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
    public void push(byte valueType) {
        valueStack.push(valueType);
        maxStackSize = Math.max(valueStack.size(), maxStackSize);
    }

    /**
     * Pushes all provided value types onto the stack.
     * 
     * @param valueTypes The value types that should be added.
     */
    public void pushAll(byte[] valueTypes) {
        for (byte valueType : valueTypes) {
            push(valueType);
        }
    }

    /**
     * Pops the topmost value type from the stack. Throws an error if the stack is empty.
     * 
     * @return The value type on top of the stack or -1.
     * @throws WasmException If the stack is empty.
     */
    public byte pop() {
        return popInternal(ANY);
    }

    /**
     * Pops the topmost value type from the stack and checks if it is equivalent to the given value.
     * 
     * @param expectedValueType The expected value type.
     * @return The value type on top of the stack.
     * @throws WasmException If the stack is empty or the value types do not match.
     */
    public byte popChecked(byte expectedValueType) {
        final byte actualValueType = popInternal(expectedValueType);
        if (actualValueType != expectedValueType && actualValueType != WasmType.UNKNOWN_TYPE && expectedValueType != WasmType.UNKNOWN_TYPE) {
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
            final byte value = valueStack.popBack();
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
    public byte[] popAll(byte[] expectedValueTypes) {
        byte[] popped = new byte[expectedValueTypes.length];
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

    public void enterFunction(byte[] returnTypes) {
        enterBlock(EMPTY_ARRAY, returnTypes);
    }

    /**
     * Creates a new block frame that holds information about the current block and pushes it onto
     * the control frame stack.
     *
     * @param paramTypes The param types of the block that was entered.
     * @param resultTypes The result types of the block that was entered.
     */
    public void enterBlock(byte[] paramTypes, byte[] resultTypes) {
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
     * @param offset The offset in the wasm binary of the loop that was entered.
     */
    public void enterLoop(byte[] paramTypes, byte[] resultTypes, int offset) {
        ControlFrame frame = new LoopFrame(paramTypes, resultTypes, valueStack.size(), false, offset, extraData.nextEntryLocation(), extraData.nextEntryIndex());
        controlStack.push(frame);
        pushAll(paramTypes);
    }

    /**
     * Creates a new if frame that holds information about the current if and pushes it onto the
     * control frame stack.
     *
     * @param paramTypes The param types of the if and else branch that was entered.
     * @param resultTypes The result type of the if and else branch that was entered.
     * @param offset The offset in the wasm binary of the if that was entered.
     */
    public void enterIf(byte[] paramTypes, byte[] resultTypes, int offset) {
        ControlFrame frame = new IfFrame(paramTypes, resultTypes, valueStack.size(), false, extraData.addIf(offset));
        controlStack.push(frame);
        pushAll(paramTypes);
    }

    /**
     * Gets the current control frame and tries to enter the else branch.
     *
     * @param offset The offset in the wasm binary of the else branch that was entered.
     */
    public void enterElse(int offset) {
        ControlFrame frame = controlStack.peek();
        frame.enterElse(this, extraData, offset);
        pushAll(frame.paramTypes());
    }

    /**
     * Unwinds the frame up to the given limit. After using this method, the values should be pushed
     * back onto the stack.
     * 
     * @return The value types on the stack.
     */
    private byte[] unwindStackToInitialFrameStackSize(int initialFrameStackSize) {
        final int stackSize = valueStack.size();
        final byte[] unwindTypes = new byte[stackSize - initialFrameStackSize];
        for (int i = unwindTypes.length - 1; i >= 0; i--) {
            unwindTypes[i] = valueStack.popBack();
        }
        return unwindTypes;
    }

    /**
     * Performs the necessary branch checks and adds the conditional branch information in the extra
     * data array.
     *
     * @param branchLabel The target label.
     * @param offset The offset in the wasm binary of the conditional branch.
     */
    public void addConditionalBranch(int branchLabel, int offset) {
        checkLabelExists(branchLabel);
        ControlFrame frame = getFrame(branchLabel);
        final byte[] labelTypes = frame.labelTypes();
        popAll(labelTypes);
        final byte[] unwindValueTypes = unwindStackToInitialFrameStackSize(frame.initialStackSize());
        frame.addBranchTarget(extraData.addConditionalBranch(offset, ExtraDataUtil.extractUnwindType(unwindValueTypes)));
        pushAll(unwindValueTypes);
        pushAll(labelTypes);
    }

    /**
     * Performs the necessary branch checks and adds the unconditional branch information to the
     * extra data array.
     * 
     * @param branchLabel The target label.
     * @param offset The offset in the wasm binary of the unconditional branch.
     */
    public void addUnconditionalBranch(int branchLabel, int offset) {
        checkLabelExists(branchLabel);
        ControlFrame frame = getFrame(branchLabel);
        final byte[] labelTypes = frame.labelTypes();
        popAll(labelTypes);
        final byte[] unwindValueTypes = unwindStackToInitialFrameStackSize(frame.initialStackSize());
        frame.addBranchTarget(extraData.addUnconditionalBranch(offset, ExtraDataUtil.extractUnwindType(unwindValueTypes)));
        pushAll(unwindValueTypes);
    }

    /**
     * Performs the necessary branch checks and adds the branch table information to the extra data
     * array.
     * 
     * @param branchLabels The target labels.
     * @param offset The offset in the wasm binary of the branch table.
     */
    public void addBranchTable(int[] branchLabels, int offset) {
        int branchLabel = branchLabels[branchLabels.length - 1];
        checkLabelExists(branchLabel);
        ControlFrame frame = getFrame(branchLabel);
        byte[] branchLabelReturnTypes = frame.labelTypes();
        BranchTableEntry branchTable = extraData.addBranchTable(branchLabels.length, offset);
        for (int i = 0; i < branchLabels.length; i++) {
            int otherBranchLabel = branchLabels[i];
            checkLabelExists(otherBranchLabel);
            frame = getFrame(otherBranchLabel);
            byte[] otherBranchLabelReturnTypes = frame.labelTypes();
            checkLabelTypes(branchLabelReturnTypes, otherBranchLabelReturnTypes);
            byte[] returnTypes = popAll(otherBranchLabelReturnTypes);
            byte[] unwindValueTypes = unwindStackToInitialFrameStackSize(frame.initialStackSize());
            frame.addBranchTarget(branchTable.updateItemUnwindType(i, ExtraDataUtil.extractUnwindType(unwindValueTypes)));
            pushAll(unwindValueTypes);
            pushAll(returnTypes);
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
    }

    /**
     * Adds the index of an indirect call node to the extra data array.
     * 
     * @param nodeIndex The index of the indirect call.
     */
    public void addIndirectCall(int nodeIndex) {
        extraData.addIndirectCall(nodeIndex);
    }

    /**
     * Adds the index of a direct call node to the extra data array.
     * 
     * @param nodeIndex The index of the direct call.
     */
    public void addCall(int nodeIndex) {
        extraData.addCall(nodeIndex);
    }

    /**
     * Finishes the current control frame and removes it from the control frame stack.
     * 
     * @param offset The offset in the wasm binary.
     * @param multiValue If multiple return values are supported.
     * 
     * @throws WasmException If the number of return value types do not match with the remaining
     *             stack or the number of return values is greater than 1.
     */
    public void exit(int offset, boolean multiValue) {
        Assert.assertTrue(!controlStack.isEmpty(), Failure.UNEXPECTED_END_OF_BLOCK);
        ControlFrame frame = controlStack.peek();
        byte[] resultTypes = frame.resultTypes();

        frame.exit(extraData, offset);

        checkStackAfterFrameExit(frame, resultTypes);

        controlStack.pop();
        if (!multiValue) {
            Assert.assertIntLessOrEqual(resultTypes.length, 1, "A block cannot return more than one value.", Failure.INVALID_RESULT_ARITY);
        }
        pushAll(resultTypes);
    }

    /**
     * Checks that the expected return types are actually on the value stack.
     * 
     * @param frame The frame that is exited.
     * @param resultTypes The expected return types of the frame.
     */
    void checkStackAfterFrameExit(ControlFrame frame, byte[] resultTypes) {
        if (availableStackSize() > resultTypes.length) {
            byte[] actualTypes = popAvailableUnchecked();
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
        byte[] resultTypes = frame.resultTypes();
        if (isCurrentStackUnreachable()) {
            popAll(resultTypes);
        } else {
            byte[] actualTypes = popAvailableUnchecked(resultTypes);
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
    public void checkParamTypes(byte[] paramTypes) {
        if (isCurrentStackUnreachable()) {
            popAll(paramTypes);
        } else {
            byte[] actualTypes = popAvailableUnchecked(paramTypes);
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
    public void checkLabelTypes(byte[] expectedTypes, byte[] actualTypes) {
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

    public int[] extraData() {
        return extraData.extraDataArray();
    }
}
