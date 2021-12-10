/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class WasmFrame {

    private WasmFrame() {
        // no instances
    }

    /* Stack operations */

    public static void pushLong(VirtualFrame frame, int slot, long value) {
        frame.setLong(slot, value);
    }

    public static void pushInt(VirtualFrame frame, int slot, int value) {
        frame.setInt(slot, value);
    }

    public static void pushFloat(VirtualFrame frame, int slot, float value) {
        frame.setFloat(slot, value);
    }

    public static void pushDouble(VirtualFrame frame, int slot, double value) {
        frame.setDouble(slot, value);
    }

    public static void drop(VirtualFrame frame, int slot) {
        if (CompilerDirectives.inCompiledCode()) {
            // Needed to avoid keeping track of popped slots in FrameStates.
            frame.clear(slot);
        }
    }

    public static void copy(VirtualFrame frame, int sourceSlot, int targetSlot) {
        frame.copy(sourceSlot, targetSlot);
    }

    public static long popLong(VirtualFrame frame, int slot) {
        long result = frame.getLong(slot);
        if (CompilerDirectives.inCompiledCode()) {
            // Needed to avoid keeping track of popped slots in FrameStates.
            frame.clear(slot);
        }
        return result;
    }

    public static int popInt(VirtualFrame frame, int slot) {
        int result = frame.getInt(slot);
        if (CompilerDirectives.inCompiledCode()) {
            // Needed to avoid keeping track of popped slots in FrameStates.
            frame.clear(slot);
        }
        return result;
    }

    public static float popFloat(VirtualFrame frame, int slot) {
        float result = frame.getFloat(slot);
        if (CompilerDirectives.inCompiledCode()) {
            // Needed to avoid keeping track of popped slots in FrameStates.
            frame.clear(slot);
        }
        return result;
    }

    public static double popDouble(VirtualFrame frame, int slot) {
        double result = frame.getDouble(slot);
        if (CompilerDirectives.inCompiledCode()) {
            // Needed to avoid keeping track of popped slots in FrameStates.
            frame.clear(slot);
        }
        return result;
    }
}
