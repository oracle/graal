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

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Operation parameter that allows an operation to update the value of a local. This class is
 * intended to be used in combination with the {@link ConstantOperand} annotation.
 * <p>
 * When a local setter is declared as a constant operand, the corresponding builder method will take
 * a {@link BytecodeLocal} argument representing the local to be updated.
 *
 * @since 24.2
 */
public final class LocalSetter {

    private final int localOffset;

    private LocalSetter(int localOffset) {
        this.localOffset = localOffset;
    }

    /**
     * Returns a string representation of a {@link LocalSetter}.
     *
     * @since 24.2
     */
    @Override
    public String toString() {
        return String.format("LocalSetter[%d]", localOffset);
    }

    /**
     * Stores an object into the local.
     *
     * @since 24.2
     */
    public void setObject(BytecodeNode node, int bci, VirtualFrame frame, Object value) {
        node.setLocalValue(bci, frame, localOffset, value);
    }

    /**
     * Stores an int into the local.
     *
     * @since 24.2
     */
    public void setInt(BytecodeNode node, int bci, VirtualFrame frame, int value) {
        node.setLocalValueInt(bci, frame, localOffset, value);
    }

    /**
     * Stores a long into the local.
     *
     * @since 24.2
     */
    public void setLong(BytecodeNode node, int bci, VirtualFrame frame, long value) {
        node.setLocalValueLong(bci, frame, localOffset, value);
    }

    /**
     * Stores a short into the local.
     *
     * @see #setObject(BytecodeNode, int, VirtualFrame, Object) the set method for an example on how
     *      to use it.
     * @since 24.2
     */
    public void setShort(BytecodeNode node, int bci, VirtualFrame frame, short value) {
        node.setLocalValueShort(bci, frame, localOffset, value);
    }

    /**
     * Stores a short into the local.
     *
     * @see #setObject(BytecodeNode, int, VirtualFrame, Object) the set method for an example on how
     *      to use it.
     * @since 24.2
     */
    public void setBoolean(BytecodeNode node, int bci, VirtualFrame frame, boolean value) {
        node.setLocalValueBoolean(bci, frame, localOffset, value);
    }

    /**
     * Stores a short into the local.
     *
     * @see #setObject(BytecodeNode, int, VirtualFrame, Object) the set method for an example on how
     *      to use it.
     * @since 24.2
     */
    public void setByte(BytecodeNode node, int bci, VirtualFrame frame, byte value) {
        node.setLocalValueByte(bci, frame, localOffset, value);
    }

    /**
     * Stores a float into the local.
     *
     * @since 24.2
     */
    public void setFloat(BytecodeNode node, int bci, VirtualFrame frame, float value) {
        node.setLocalValueFloat(bci, frame, localOffset, value);
    }

    /**
     * Stores a double into the local.
     *
     * @since 24.2
     */
    public void setDouble(BytecodeNode node, int bci, VirtualFrame frame, double value) {
        node.setLocalValueDouble(bci, frame, localOffset, value);
    }

    private static final int CACHE_SIZE = 64;

    @CompilationFinal(dimensions = 1) private static final LocalSetter[] CACHE = createCache();

    private static LocalSetter[] createCache() {
        LocalSetter[] setters = new LocalSetter[64];
        for (int i = 0; i < setters.length; i++) {
            setters[i] = new LocalSetter(i);
        }
        return setters;
    }

    /**
     * Obtains an existing {@link LocalSetter}.
     *
     * This method is invoked by the generated code and should not be called directly.
     *
     * @since 24.2
     */
    public static LocalSetter constantOf(BytecodeLocal local) {
        int offset = local.getLocalOffset();
        if (offset < CACHE_SIZE) {
            return CACHE[offset];
        }
        return new LocalSetter(offset);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LocalSetter otherSetter && this.localOffset == otherSetter.localOffset;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(localOffset);
    }

}
