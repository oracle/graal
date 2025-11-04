/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.Objects;

import com.oracle.truffle.api.bytecode.Instruction.Argument;
import com.oracle.truffle.api.bytecode.Instruction.Argument.Kind;

/**
 * Descriptor for a concrete bytecode instruction. Instances describe static properties of an
 * instruction, for example name, length and arguments.
 *
 * @since 25.1
 */
public abstract class InstructionDescriptor {

    /**
     * Internal constructor for generated code. Do not use.
     *
     * @since 25.1
     */
    protected InstructionDescriptor(Object token) {
        BytecodeRootNodes.checkToken(token);
    }

    /**
     * Returns the encoded size of this instruction in bytes.
     *
     * @since 25.1
     */
    public abstract int getLength();

    /**
     * Returns a human readable instruction name. Two descriptors with the same name also have the
     * same {@link #getOperationCode() operation code}.
     *
     * @see #getOperationCode()
     * @since 25.1
     */
    public abstract String getName();

    /**
     * Returns the operation code for this instruction. The value is only intended for debugging or
     * tooling. Two descriptors with the same operation code also have the same {@link #getName()
     * name}.
     *
     * @see #getName()
     * @since 25.1
     */
    public abstract int getOperationCode();

    /**
     * Returns an immutable list describing the immediate arguments of this instruction. The list
     * size and the descriptor kinds are stable, the runtime values of the arguments may change.
     *
     * @since 25.1
     */
    public abstract List<ArgumentDescriptor> getArgumentDescriptors();

    /**
     * Returns <code>true</code> if this instruction represents a bytecode or tag instrumentation
     * instruction, else <code>false</code>. Instrumentation instructions may get inserted
     * dynamically during execution, e.g., if a tag is materialized or an {@link Instrumentation} is
     * {@link BytecodeRootNodes#update(BytecodeConfig) configured}.
     *
     * @since 25.1
     */
    public abstract boolean isInstrumentation();

    /**
     * {@inheritDoc}
     *
     * @since 25.1
     */
    @Override
    public int hashCode() {
        return Objects.hash(getClass(), getOperationCode());
    }

    /**
     * {@inheritDoc}
     *
     * @since 25.1
     */
    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    /**
     * {@inheritDoc}
     *
     * @since 25.1
     */
    @Override
    public String toString() {
        return format(-1, this, 40);
    }

    static String format(int index, InstructionDescriptor instruction, int maxLabelWidth) {
        StringBuilder sb = new StringBuilder();
        if (index != -1) {
            sb.append(String.format("%3d ", index));
        }
        String label = formatLabel(instruction);
        sb.append(label);
        appendSpaces(sb, maxLabelWidth - label.length());
        String arguments = formatArguments(instruction);
        sb.append(arguments);
        return sb.toString();
    }

    private static void appendSpaces(StringBuilder sb, int spaces) {
        for (int i = 0; i < spaces; i++) {
            sb.append(' ');
        }
    }

    static String formatLabel(InstructionDescriptor instruction) {
        return String.format("%03x %5s %s", instruction.getOperationCode(), String.format("[%db]", instruction.getLength()), instruction.getName());
    }

    static String formatArguments(InstructionDescriptor instruction) {
        StringBuilder b = new StringBuilder(" ");
        for (ArgumentDescriptor a : instruction.getArgumentDescriptors()) {
            b.append(' ').append(a.toString());
        }
        return b.toString();
    }

    /**
     * Descriptor for a single immediate argument of an instruction. Captures kind, name and encoded
     * width of the argument.
     *
     * @since 25.1
     */
    public abstract static class ArgumentDescriptor {

        /**
         * Internal constructor for generated code. Do not use.
         *
         * @since 25.1
         */
        protected ArgumentDescriptor(Object token) {
            BytecodeRootNodes.checkToken(token);
        }

        /**
         * Returns the {@link Kind} of this argument descriptor.
         *
         * @since 25.1
         */
        public abstract Argument.Kind getKind();

        /**
         * Returns a human readable name for this argument. This could be for example
         * <code>"localOffset"</code> for a local variable access instruction. Arguments with the
         * same {@link #getKind()} may have different {@link #getName() names}. A name is typically
         * more descriptive than just the kind and should be preferred over the kind for debug
         * output.
         *
         * @since 25.1
         */
        public abstract String getName();

        /**
         * Returns width in bytes.
         *
         * @since 25.1
         */
        public abstract int getLength();

        /**
         * {@inheritDoc}
         *
         * @since 25.1
         */
        @Override
        public String toString() {
            return String.format("[%db]%s(%s)", getLength(), getName(), getKind());
        }

    }

}
