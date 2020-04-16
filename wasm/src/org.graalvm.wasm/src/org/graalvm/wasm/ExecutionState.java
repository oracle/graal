/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm;

import org.graalvm.wasm.collection.ByteArrayList;
import org.graalvm.wasm.collection.IntArrayList;
import org.graalvm.wasm.collection.LongArrayList;

import java.util.ArrayList;

public class ExecutionState {
    private int stackSize;
    private int maxStackSize;
    private int profileCount;
    private final ByteArrayList byteConstants;
    private final IntArrayList intConstants;
    private final LongArrayList longConstants;
    private final IntArrayList stackStates;
    private final IntArrayList continuationReturnLength;
    private final ArrayList<int[]> branchTables;
    private boolean reachable;

    public ExecutionState() {
        this.stackSize = 0;
        this.maxStackSize = 0;
        this.profileCount = 0;
        this.byteConstants = new ByteArrayList();
        this.intConstants = new IntArrayList();
        this.longConstants = new LongArrayList();
        this.stackStates = new IntArrayList();
        this.continuationReturnLength = new IntArrayList();
        this.branchTables = new ArrayList<>();
        this.reachable = true;
    }

    public boolean isReachable() {
        return this.reachable;
    }

    public void setReachable(boolean reachable) {
        this.reachable = reachable;
    }

    public void push() {
        stackSize++;
        maxStackSize = Math.max(stackSize, maxStackSize);
    }

    public void push(int n) {
        stackSize += n;
        maxStackSize = Math.max(stackSize, maxStackSize);
    }

    public void pop() {
        stackSize--;
    }

    public void pop(int n) {
        stackSize -= n;
    }

    public void setStackSize(int stackSize) {
        this.stackSize = stackSize;
    }

    public void useByteConstant(byte constant) {
        byteConstants.add(constant);
    }

    public void useIntConstant(int constant) {
        intConstants.add(constant);
    }

    public void pushStackState(int stackPosition) {
        stackStates.add(stackPosition);
    }

    public void popStackState() {
        stackStates.popBack();
    }

    public int stackStateCount() {
        return stackStates.size();
    }

    public int getStackState(int level) {
        if (stackStates.size() < level + 1) {
            Assert.fail("Branch to level " + level + " larger than the nesting " + stackStates.size());
        }
        return stackStates.get(stackStates.size() - 1 - level);
    }

    public void pushContinuationReturnLength(int n) {
        continuationReturnLength.add(n);
    }

    public void popContinuationReturnLength() {
        continuationReturnLength.popBack();
    }

    public int getContinuationReturnLength(int offset) {
        return continuationReturnLength.get(continuationReturnLength.size() - 1 - offset);
    }

    public int getRootBlockReturnLength() {
        return continuationReturnLength.get(0);
    }

    public int stackSize() {
        return stackSize;
    }

    public int maxStackSize() {
        return maxStackSize;
    }

    public int byteConstantOffset() {
        return byteConstants.size();
    }

    public int intConstantOffset() {
        return intConstants.size();
    }

    public byte[] byteConstants() {
        return byteConstants.toArray();
    }

    public int[] intConstants() {
        return intConstants.toArray();
    }

    public void useLongConstant(long literal) {
        longConstants.add(literal);
    }

    public int longConstantOffset() {
        return longConstants.size();
    }

    public long[] longConstants() {
        return longConstants.toArray();
    }

    public void saveBranchTable(int[] branchTable) {
        branchTables.add(branchTable);
    }

    public int branchTableOffset() {
        return branchTables.size();
    }

    public int[][] branchTables() {
        return branchTables.toArray(new int[0][]);
    }

    public void incrementProfileCount() {
        ++profileCount;
    }

    public int profileCount() {
        return profileCount;
    }
}
