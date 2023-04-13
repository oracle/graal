/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.operation;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class LocalSetter {

    // LocalSetters are not specific to any OperationRootNode, since they just encapsulate a local
    // index. We use a static cache to share and reuse the objects for each node.
    @CompilationFinal(dimensions = 1) private static LocalSetter[] localSetters = new LocalSetter[512];

    private static synchronized void resizeLocals(int index) {
        if (localSetters.length <= index) {
            int size = localSetters.length;
            while (size <= index) {
                size = size << 1;
            }
            localSetters = Arrays.copyOf(localSetters, size);
        }
    }

    public static LocalSetter create(int index) {
        CompilerAsserts.neverPartOfCompilation("use #get in compiled code");
        if (index < 0 || index >= Short.MAX_VALUE) {
            throw new ArrayIndexOutOfBoundsException(index);
        }

        if (localSetters.length <= index) {
            resizeLocals(index);
        }

        LocalSetter result = localSetters[index];
        if (result == null) {
            result = new LocalSetter(index);
            localSetters[index] = result;
        }
        return result;
    }

    public static LocalSetter get(int index) {
        return localSetters[index];
    }

    static void setObject(VirtualFrame frame, int index, Object value) {
        FrameDescriptor descriptor = frame.getFrameDescriptor();
        descriptor.setSlotKind(index, FrameSlotKind.Object);
        frame.setObject(index, value);
    }

    @SuppressWarnings("unused")
    private static boolean checkFrameSlot(VirtualFrame frame, int index, FrameSlotKind target) {
        return false;
    }

    static void setLong(VirtualFrame frame, int index, long value) {
        if (checkFrameSlot(frame, index, FrameSlotKind.Long)) {
            frame.setLong(index, value);
        } else {
            frame.setObject(index, value);
        }
    }

    static void setInt(VirtualFrame frame, int index, int value) {
        if (checkFrameSlot(frame, index, FrameSlotKind.Int)) {
            frame.setInt(index, value);
        } else {
            frame.setObject(index, value);
        }
    }

    static void setDouble(VirtualFrame frame, int index, double value) {
        if (checkFrameSlot(frame, index, FrameSlotKind.Double)) {
            frame.setDouble(index, value);
        } else {
            frame.setObject(index, value);
        }
    }

    private final int index;

    private LocalSetter(int index) {
        this.index = index;
    }

    @Override
    public String toString() {
        return String.format("LocalSetter[%d]", index);
    }

    public void setObject(VirtualFrame frame, Object value) {
        setObject(frame, index, value);
    }

    public void setInt(VirtualFrame frame, int value) {
        setInt(frame, index, value);
    }

    public void setLong(VirtualFrame frame, long value) {
        setLong(frame, index, value);
    }

    public void setDouble(VirtualFrame frame, double value) {
        setDouble(frame, index, value);
    }
    // todo: other primitives
}
