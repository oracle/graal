/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.wasm.Assert;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.collection.ByteArrayList;
import org.graalvm.wasm.collection.IntArrayList;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import java.util.ArrayList;

import static java.lang.Integer.compareUnsigned;

/**
 * Represents the values and stack frames of a Wasm code section during validation. Stores
 * additional information used to generate parser nodes.
 */
public class ValidationState {
    private static final byte[] EMPTY_ARRAY = new byte[0];
    private static final byte UNKNOWN = -1;
    private static final byte ANY = 0;

    private final ByteArrayList valueStack;
    private final ControlStack controlStack;

    private final IntArrayList intConstants;
    private final ArrayList<int[]> branchTables;
    private int profileCount;

    private int maxStackSize;

    public ValidationState() {
        this.valueStack = new ByteArrayList();
        this.controlStack = new ControlStack();

        this.intConstants = new IntArrayList();
        this.branchTables = new ArrayList<>();
        this.profileCount = 0;

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
                return UNKNOWN;
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
    private static boolean checkTypes(byte[] expectedTypes, byte[] actualTypes) {
        if (expectedTypes.length != actualTypes.length) {
            return false;
        }
        for (int i = 0; i < expectedTypes.length; i++) {
            if (expectedTypes[i] != actualTypes[i]) {
                return false;
            }
        }
        return true;
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
        if (actualValueType == UNKNOWN) {
            return expectedValueType;
        }
        if (expectedValueType == UNKNOWN) {
            return actualValueType;
        }
        if (expectedValueType != actualValueType) {
            throw ValidationErrors.createTypeMismatch(expectedValueType, actualValueType);
        }
        return actualValueType;
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
        int j = 0;
        for (int i = expectedValueTypes.length - 1; i >= 0; i--) {
            popped[j] = popChecked(expectedValueTypes[i]);
            j++;
        }
        return popped;
    }

    /**
     * @return The number of remaining values of the current control stack.
     */
    private int availableStackSize() {
        return valueStack.size() - controlStack.peek().getInitialStackSize();
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

    /**
     * Creates a new control frame that holds information about the current scope and pushes it onto
     * the control frame stack.
     * 
     * @param opcode The opcode of the control structure that was entered.
     * @param returnType The return type of the control structure that was entered.
     */
    public void enterBlock(int opcode, byte returnType) {
        byte[] out = EMPTY_ARRAY;
        if (returnType != WasmType.VOID_TYPE) {
            out = new byte[]{returnType};
        }
        enterBlock(opcode, EMPTY_ARRAY, out);
    }

    /**
     * Creates a new control frame based on the given information.
     * 
     * @param opcode The opcode of the control frame.
     * @param in The input value types of the control frame.
     * @param out The result value types of the control frame.
     */
    private void enterBlock(int opcode, byte[] in, byte[] out) {
        ControlFrame frame = new ControlFrame(opcode, in, out, valueStack.size(), false);
        controlStack.push(frame);
        pushAll(in);
    }

    /**
     * Finishes the current control frame and removes it from the control frame stack.
     * 
     * @throws WasmException If the number of return value types do not match with the remaining
     *             stack or the number of return values is greater than 1.
     */
    public void exitBlock() {
        Assert.assertTrue(!controlStack.isEmpty(), Failure.UNEXPECTED_END_OF_BLOCK);
        ControlFrame frame = controlStack.peek();
        byte[] resultTypes = frame.getResultTypes();
        if (availableStackSize() > resultTypes.length) {
            byte[] actualTypes = popAvailableUnchecked();
            if (!checkTypes(resultTypes, actualTypes)) {
                throw ValidationErrors.createResultTypesMismatch(resultTypes, actualTypes);
            }
        }
        checkReturnTypes(frame);
        controlStack.pop();
        Assert.assertIntLessOrEqual(resultTypes.length, 1, "A block cannot return more than one value.", Failure.INVALID_RESULT_ARITY);
        if (!frame.isIf()) {
            pushAll(resultTypes);
        }
    }

    public ControlFrame getBlock(int index) {
        return controlStack.get(index);
    }

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
    public void checkReturnTypes(ControlFrame frame) {
        byte[] resultTypes = frame.getResultTypes();
        if (isCurrentStackUnreachable()) {
            popAll(resultTypes);
        } else {
            byte[] actualTypes = popAvailableUnchecked(resultTypes);
            if (!checkTypes(resultTypes, actualTypes)) {
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
    public void checkParameterTypes(byte[] paramTypes) {
        if (isCurrentStackUnreachable()) {
            popAll(paramTypes);
        } else {
            byte[] actualTypes = popAvailableUnchecked(paramTypes);
            if (!checkTypes(paramTypes, actualTypes)) {
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
        if (!checkTypes(expectedTypes, actualTypes)) {
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
        unwindStack(frame.getInitialStackSize());
        frame.setUnreachable();
    }

    /**
     * @param index The control stack for which the value stack size should be returned.
     * @return Returns the value stack size of the control stack given by the index.
     */
    public int getValueStackSize(int index) {
        assert compareUnsigned(index, controlStackSize()) < 0;
        return controlStack.get(index).getInitialStackSize();
    }

    public int controlStackSize() {
        return controlStack.size();
    }

    public int valueStackSize() {
        return valueStack.size();
    }

    public int intConstantSize() {
        return intConstants.size();
    }

    public int branchTableSize() {
        return branchTables.size();
    }

    public int currentProfileCount() {
        return profileCount;
    }

    public int maxStackSize() {
        return maxStackSize;
    }

    public void useIntConstant(int constant) {
        intConstants.add(constant);
    }

    public void incrementProfileCount() {
        ++profileCount;
    }

    public void saveBranchTable(int[] branchTable) {
        branchTables.add(branchTable);
    }

    public IntArrayList intConstants() {
        return intConstants;
    }

    public ArrayList<int[]> branchTables() {
        return branchTables;
    }
}
