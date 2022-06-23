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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class LocalSetterRun {
    @CompilationFinal(dimensions = 2) //
    private static LocalSetterRun[][] localSetterRuns = new LocalSetterRun[8][];

    private static synchronized void resizeArray(int length) {
        if (localSetterRuns.length <= length) {
            int size = localSetterRuns.length;
            while (size <= length) {
                size = size << 1;
            }
            localSetterRuns = Arrays.copyOf(localSetterRuns, size);
        }
    }

    private static synchronized LocalSetterRun[] createSubArray(int length, int index) {
        LocalSetterRun[] target = localSetterRuns[length];
        if (target == null) {
            int size = 8;
            while (size <= index) {
                size = size << 1;
            }
            target = new LocalSetterRun[size];
            localSetterRuns[length] = target;
        }
        return target;
    }

    private static synchronized LocalSetterRun[] resizeSubArray(int length, int index) {
        LocalSetterRun[] target = localSetterRuns[length];
        if (target.length <= index) {
            int size = target.length;
            while (size <= index) {
                size = size << 1;
            }
            target = Arrays.copyOf(target, size);
            localSetterRuns[length] = target;
        }
        return target;
    }

    public static final LocalSetterRun EMPTY = new LocalSetterRun(0, 0);

    public static LocalSetterRun create(int start, int length) {
        CompilerAsserts.neverPartOfCompilation("use #get from compiled code");
        if (start < 0 || start > Short.MAX_VALUE) {
            throw new ArrayIndexOutOfBoundsException(start);
        }

        if (length <= 0 || length + start > Short.MAX_VALUE) {
            throw new ArrayIndexOutOfBoundsException(start + length);
        }

        if (localSetterRuns.length <= length) {
            resizeArray(length);
        }

        LocalSetterRun[] target = localSetterRuns[length];
        if (target == null) {
            target = createSubArray(length, start);
        }

        if (target.length <= start) {
            target = resizeSubArray(length, start);
        }

        LocalSetterRun result = target[start];
        if (result == null) {
            result = new LocalSetterRun(start, length);
            target[start] = result;
        }

        return result;
    }

    public static LocalSetterRun get(int start, int length) {
        return localSetterRuns[length][start];
    }

    private final int start;
    private final int length;

    private LocalSetterRun(int start, int length) {
        this.start = start;
        this.length = length;
    }

    @Override
    public String toString() {
        if (length == 0) {
            return "LocalSetterRun[]";
        }
        return String.format("LocalSetterRun[%d...%d]", start, start + length - 1);
    }

    public int length() {
        return length;
    }

    private void checkBounds(int offset) {
        if (offset >= length) {
            CompilerDirectives.transferToInterpreter();
            throw new ArrayIndexOutOfBoundsException(offset);
        }
    }

    public void setObject(VirtualFrame frame, int offset, Object value) {
        checkBounds(offset);
        LocalSetter.setObject(frame, start + offset, value);
    }

    public void setInt(VirtualFrame frame, int offset, int value) {
        checkBounds(offset);
        LocalSetter.setInt(frame, start + offset, value);
    }

    public void setLong(VirtualFrame frame, int offset, long value) {
        checkBounds(offset);
        LocalSetter.setLong(frame, start + offset, value);
    }

    public void setDouble(VirtualFrame frame, int offset, double value) {
        checkBounds(offset);
        LocalSetter.setDouble(frame, start + offset, value);
    }
}
