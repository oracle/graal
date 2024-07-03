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
package com.oracle.truffle.api.bytecode;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlotKind;

/**
 * @since 24.1
 */
public abstract class LocalVariable {

    /**
     * @since 24.1
     */
    public LocalVariable(Object token) {
        BytecodeRootNodes.checkToken(token);
    }

    public int getStartIndex() {
        return -1;
    }

    public int getEndIndex() {
        return -1;
    }

    /**
     * Returns the local index used when accessing local values with a local accessor like
     * {@link BytecodeNode#getLocalValue(int, Frame, int)}. Always returns an integer greater or
     * equal to zero. Note that the local offset can only be read if the current bytecode index is
     * between {@link #getStartIndex()} and {@link #getEndIndex()}(exclusive).
     *
     * @since 24.1
     */
    public abstract int getLocalOffset();

    public abstract int getLocalIndex();

    /**
     * Returns the type profile that was collected for this local. Returns <code>null</code> if no
     * profile was yet collected or the interpreter does not collect profiles.
     *
     * @since 24.1
     */
    public abstract FrameSlotKind getTypeProfile();

    public abstract Object getInfo();

    public abstract Object getName();

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("LocalVariable[");
        int startIndex = getStartIndex();
        String sep = "";
        if (startIndex != -1) {
            b.append(String.format("%03x-%03x", getStartIndex(), getEndIndex()));
            sep = ", ";
        }

        b.append(sep);
        b.append("index=");
        b.append(getLocalIndex());

        b.append(", offset=");
        b.append(getLocalOffset());

        Object name = getName();
        if (name != null) {
            b.append(", name=");
            b.append(name);
        }

        Object info = getInfo();
        if (info != null) {
            b.append(", info=");
            b.append(info);
        }

        FrameSlotKind kind = getTypeProfile();
        if (kind != null) {
            b.append(", profile=");
            b.append(kind.toString());
        }
        b.append("]");
        return b.toString();
    }

}