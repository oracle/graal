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

package org.graalvm.wasm.parser.validation.collections;

import org.graalvm.wasm.parser.validation.ControlFrame;

/**
 * Represents a stack of control frames that are used for validation of modules.
 */
public class ControlStack {
    private ControlFrame[] stack;

    private int size;

    public ControlStack() {
        stack = new ControlFrame[4];
        size = 0;
    }

    private void ensureSize() {
        if (size == stack.length) {
            ControlFrame[] nStack = new ControlFrame[stack.length * 2];
            System.arraycopy(stack, 0, nStack, 0, size);
            stack = nStack;
        }
    }

    /**
     * Pushes the given control frame onto the stack.
     * 
     * @param frame A control frame.
     */
    public void push(ControlFrame frame) {
        ensureSize();
        stack[size] = frame;
        size++;
    }

    /**
     * Pops the topmost control frame from the stack.
     */
    public void pop() {
        assert size > 0 : "cannot pop from empty stack";
        size--;
    }

    /**
     * Returns the topmost stack value without removing it.
     * 
     * @return The topmost control frame.
     */
    public ControlFrame peek() {
        assert size > 0 : "cannot peek empty stack";
        return stack[size - 1];
    }

    /**
     * @param index Index from top of the stack
     * @return The value at (size - index - 1)
     */
    public ControlFrame get(int index) {
        assert (size - index - 1) >= 0 && (size - index - 1) < size : "invalid element index";
        return stack[size - index - 1];
    }

    /**
     * @return The control frame on the bottom of the stack.
     */
    public ControlFrame getFirst() {
        return stack[0];
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }
}
