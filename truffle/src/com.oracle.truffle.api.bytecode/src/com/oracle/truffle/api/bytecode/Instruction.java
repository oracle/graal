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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import com.oracle.truffle.api.dsl.Bind.DefaultExpression;
import com.oracle.truffle.api.dsl.Introspection;
import com.oracle.truffle.api.dsl.Introspection.SpecializationInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Represents metadata for an instruction in a bytecode node.
 * <p>
 * Compatibility note: The data contained in instruction classes is subject to change without notice
 * between Truffle versions. This introspection API is therefore intended to be used for debugging
 * and tracing purposes only. Do not rely on instructions for your language semantics.
 * <p>
 * The current instruction can be bound using <code>@Bind Instruction instruction</code> from
 * {@link Operation operations}. This class is not intended to be subclasses by clients, only by DSL
 * generated code.
 *
 * @see BytecodeNode#getInstructions()
 * @see BytecodeNode#getInstructionsAsList()
 * @see BytecodeNode#getInstruction(int)
 * @since 24.2
 */
@DefaultExpression("$bytecodeNode.getInstruction($bytecodeIndex)")
public abstract class Instruction {

    /**
     * Internal constructor for generated code. Do not use.
     *
     * @since 24.2
     */
    protected Instruction(Object token) {
        BytecodeRootNodes.checkToken(token);
    }

    /**
     * Returns the bytecode node this instruction stems from.
     *
     * @since 24.2
     */
    public abstract BytecodeNode getBytecodeNode();

    /**
     * Returns the bytecode index of this instruction. A bytecode index is only valid for a given
     * {@link BytecodeNode}, it is therefore recommended to use {@link #getLocation()} instead
     * whenever possible.
     *
     * @ee {@link #getLocation()}
     * @since 24.2
     */
    public abstract int getBytecodeIndex();

    /**
     * Returns the length of this instruction in number of bytes.
     *
     * @since 24.2
     */
    public abstract int getLength();

    /**
     * Converts this instruction pointer into a bytecode location. It is recommended to use
     * {@link BytecodeLocation} to persist a bytecode location instead of using instruction.
     *
     * @since 24.2
     */
    public final BytecodeLocation getLocation() {
        return getBytecodeNode().getBytecodeLocation(getBytecodeIndex());
    }

    /**
     * Returns the name of this instruction. The name of the instruction is purely for human
     * consumption and no structure should be assumed. If two instructions have the same instruction
     * name then they also have the same {@link #getOperationCode() operation code}.
     * <p>
     * It is guaranteed that the name is no longer mutating after the instruction object was
     * created. The name of an instruction at a {@link BytecodeLocation location} may otherwise
     * change during execution due to quickening and other optimizations.
     *
     * @see #getOperationCode()
     * @since 24.2
     */
    public abstract String getName();

    /**
     * Returns the operation code of this instruction. The operation code of the instruction is
     * purely for human consumption and no values should be assumed. If two instructions have the
     * same instruction operation code then they also have the same {@link #getName() name}.
     * <p>
     * It is guaranteed that the operation code is no longer mutating after the instruction object
     * was created. The operation code of an instruction at a {@link BytecodeLocation location} may
     * otherwise change during execution due to quickening and other optimizations.
     *
     * @see #getName()
     * @since 24.2
     */
    public abstract int getOperationCode();

    /**
     * Returns an immutable list of immediate arguments for this instructions. The number of
     * arguments of an instruction remain stable during execution. The argument values on the other
     * hand may get mutated by bytecode execution.
     *
     * @since 24.2
     */
    public abstract List<Argument> getArguments();

    /**
     * Returns <code>true</code> if this instruction represents a bytecode or tag instrumentation
     * instruction, else <code>false</code>. Instrumentation instructions may get inserted
     * dynamically during execution, e.g. if a tag is materialized or an {@link Instrumentation} is
     * enabled {@link BytecodeRootNodes#update(BytecodeConfig) configured}.
     *
     * @since 24.2
     */
    public abstract boolean isInstrumentation();

    /**
     * Returns the most concrete source section associated with this instruction. If no source
     * section is available for this instruction or source sections have not yet been materialized,
     * then <code>null</code> is returned. Source sections may be materialized by calling
     * {@link BytecodeRootNodes#update(BytecodeConfig) update} with
     * {@link BytecodeConfig#WITH_SOURCE}.
     *
     * @since 24.2
     */
    public final SourceSection getSourceSection() {
        BytecodeNode bytecode = getBytecodeNode();
        if (bytecode.getSourceInformation() == null) {
            // avoid materialization of source info
            return null;
        }
        return bytecode.getSourceLocation(getBytecodeIndex());
    }

    /**
     * Returns all source section associated with this instruction starting with the most concrete
     * source section. If no source sections are available, then an empty array is returned. If
     * source sections have not yet been materialized, then <code>null</code> is returned. Source
     * sections may be materialized by calling {@link BytecodeRootNodes#update(BytecodeConfig)
     * update} with {@link BytecodeConfig#WITH_SOURCE}.
     *
     * @since 24.2
     */
    public final SourceSection[] getSourceSections() {
        BytecodeNode bytecode = getBytecodeNode();
        if (bytecode.getSourceInformation() == null) {
            // avoid materialization of source info
            return null;
        }
        return getBytecodeNode().getSourceLocations(getBytecodeIndex());
    }

    /**
     * Returns the bytecode index of the next instruction. This method is useful to quickly find the
     * next instruction. The next bytecode index is computed as <code>{@link #getBytecodeIndex()} +
     * {@link #getLength()}</code>.
     * <p>
     * The next bytecode index may no longer be a valid index if this instruction is the last
     * instruction. Use {@link BytecodeNode#getInstructions()} to walk all instructions efficiently
     * and safely. Since the bytecode encoding is variable length, there is no efficient way to get
     * to the previous bytecode index. Only forward traversial is efficient. If random access is
     * desired use {@link BytecodeNode#getInstructionsAsList()}.
     *
     * @since 24.2
     */
    public final int getNextBytecodeIndex() {
        return getBytecodeIndex() + getLength();
    }

    /**
     * Returns the next instruction object. Implemented by generated code, intended for internal use
     * only.
     *
     * @since 24.2
     */
    protected abstract Instruction next();

    /**
     * {@inheritDoc}
     *
     * @since 24.2
     */
    @Override
    public final int hashCode() {
        return Objects.hash(getBytecodeNode(), getBytecodeIndex(), getOperationCode());
    }

    /**
     * {@inheritDoc}
     *
     * @since 24.2
     */
    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof Instruction other) {
            return getBytecodeNode() == other.getBytecodeNode() && getBytecodeIndex() == other.getBytecodeIndex() && getOperationCode() == other.getOperationCode();
        } else {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 24.2
     */
    @Override
    public final String toString() {
        return formatInstruction(-1, this, 40, 60);
    }

    static String formatInstruction(int index, Instruction instruction, int maxLabelWidth, int maxArgumentWidth) {
        StringBuilder sb = new StringBuilder();
        if (index != -1) {
            sb.append(String.format("%3d ", index));
        }
        String label = formatLabel(instruction);
        sb.append(label);
        appendSpaces(sb, maxLabelWidth - label.length());
        String arguments = formatArguments(instruction);
        sb.append(arguments);
        appendSpaces(sb, maxArgumentWidth - arguments.length());
        SourceSection s = instruction.getSourceSection();
        if (s != null) {
            sb.append(" | ");
            sb.append(SourceInformation.formatSourceSection(s, 60));
        }
        return sb.toString();
    }

    private static void appendSpaces(StringBuilder sb, int spaces) {
        for (int i = 0; i < spaces; i++) {
            sb.append(' ');
        }
    }

    static String formatLabel(Instruction instruction) {
        return String.format("[%03x] %03x %s", instruction.getBytecodeIndex(), instruction.getOperationCode(), instruction.getName());
    }

    static String formatArguments(Instruction instruction) {
        StringBuilder b = new StringBuilder(" ");
        for (Argument a : instruction.getArguments()) {
            b.append(' ').append(a.toString());
        }
        return b.toString();
    }

    /**
     * Represents metadata for an argument of an instruction in a bytecode node.
     * <p>
     * Compatibility note: The data contained in instruction classes is subject to change without
     * notice between Truffle versions. This introspection API is therefore intended to be used for
     * debugging and tracing purposes only. Do not rely on instructions for your language semantics.
     *
     * @see Instruction#getArguments()
     * @since 24.2
     */
    public abstract static class Argument {

        /**
         * Internal constructor for generated code. Do not use.
         *
         * @since 24.2
         */
        protected Argument(Object token) {
            BytecodeRootNodes.checkToken(token);
        }

        /**
         * Returns the {@link Kind} of this argument. Depending on the kind you may use any of the
         * as prefixed methods.
         *
         * @since 24.2
         */
        public abstract Kind getKind();

        /**
         * Returns a humand readable name for this argument. This could be for example
         * <code>"localOffset"</code> for a local variable access instruction. Arguments with the
         * same {@link #getKind()} may have different {@link #getName() names}. A name is typically
         * more descriptive than just the kind and should be preferred over the kind for debug
         * output.
         *
         * @since 24.2
         */
        public abstract String getName();

        /**
         * Converts this argument to an <code>int</code> value. This method is only supported for
         * for the following kind: {@link Kind#INTEGER}. If called for arguments of other kinds then
         * an {@link UnsupportedOperationException} is thrown.
         *
         * @since 24.2
         */
        public int asInteger() throws UnsupportedOperationException {
            throw unsupported();
        }

        /**
         * Converts this argument to an bytecodeIndex. This method is only supported for for the
         * following kind: {@link Kind#BYTECODE_INDEX}. If called for arguments of other kinds then
         * an {@link UnsupportedOperationException} is thrown. If the returned value is >= 0 then
         * the bytecode index can be used to be converted to a {@link BytecodeLocation}.
         *
         * @since 24.2
         */
        public int asBytecodeIndex() {
            throw unsupported();
        }

        /**
         * Converts this argument to a object constant. This method is only supported for for the
         * following kind: {@link Kind#CONSTANT}. If called for arguments of other kinds then an
         * {@link UnsupportedOperationException} is thrown.
         *
         * @since 24.2
         */
        public Object asConstant() {
            throw unsupported();
        }

        /**
         * Converts this argument to a {@link Node cached node}. This method is only supported for
         * for the following kind: {@link Kind#NODE_PROFILE}. If called for arguments of other kinds
         * then an {@link UnsupportedOperationException} is thrown. The returned value is never
         * <code>null</code> if the {@link BytecodeTier} is {@link BytecodeTier#CACHED}.
         *
         * @since 24.2
         */
        public Node asCachedNode() {
            throw unsupported();
        }

        /**
         * Converts this argument to a {@link TagTreeNode tag tree node}. This method is only
         * supported for for the following kind: {@link Kind#TAG_NODE}. If called for arguments of
         * other kinds then an {@link UnsupportedOperationException} is thrown. The returned value
         * is never <code>null</code>.
         *
         * @since 24.2
         */
        public TagTreeNode asTagNode() {
            throw unsupported();
        }

        /**
         * Converts this argument to a localOffset. This method is only supported for for the
         * following kind: {@link Kind#LOCAL_OFFSET}. If called for arguments of other kinds then an
         * {@link UnsupportedOperationException} is thrown. This index may be used to access locals
         * with the local local access methods in {@link BytecodeNode}.
         *
         * @see BytecodeNode#getLocalValue(int, com.oracle.truffle.api.frame.Frame, int)
         * @since 24.2
         */
        public int asLocalOffset() {
            throw unsupported();
        }

        /**
         * Converts this argument to a localIndex. This method is only supported for for the
         * following kind: {@link Kind#LOCAL_INDEX}. If called for arguments of other kinds then an
         * {@link UnsupportedOperationException} is thrown. The local index can be used to index
         * into the list of {@link BytecodeNode#getLocals() locals}.
         *
         * @see BytecodeNode#getLocals()
         * @since 24.2
         */
        public int asLocalIndex() {
            throw unsupported();
        }

        /**
         * Converts this argument to a {@link BranchProfile branch profile}. This method is only
         * supported for for the following kind: {@link Kind#BRANCH_PROFILE}. If called for
         * arguments of other kinds then an {@link UnsupportedOperationException} is thrown. The
         * returned value is never <code>null</code>.
         *
         * @since 24.2
         */
        public BranchProfile asBranchProfile() {
            throw unsupported();
        }

        /**
         * Converts this argument to a {@link SpecializationInfo specialization info}. This method
         * is only supported for for the following kind: {@link Kind#NODE_PROFILE}. If called for
         * arguments of other kinds then an {@link UnsupportedOperationException} is thrown. The
         * specialization info is only available if
         * {@link GenerateBytecode#enableSpecializationIntrospection()} is set to <code>true</code>.
         *
         * @since 24.2
         */
        public final List<SpecializationInfo> getSpecializationInfo() {
            Node n = asCachedNode();
            if (Introspection.isIntrospectable(n)) {
                return Introspection.getSpecializations(n);
            } else {
                return null;
            }
        }

        private RuntimeException unsupported() {
            return new UnsupportedOperationException(String.format("Not supported for argument type %s.", getKind()));
        }

        /**
         * {@inheritDoc}
         *
         * @since 24.2
         */
        @Override
        public final String toString() {
            switch (getKind()) {
                case LOCAL_OFFSET:
                    return String.format("%s(%d)", getName(), asLocalOffset());
                case LOCAL_INDEX:
                    return String.format("%s(%d)", getName(), asLocalIndex());
                case INTEGER:
                    return String.format("%s(%d)", getName(), asInteger());
                case CONSTANT:
                    return String.format("%s(%s)", getName(), printConstant(asConstant()));
                case NODE_PROFILE:
                    return String.format("%s(%s)", getName(), printNodeProfile(asCachedNode()));
                case BYTECODE_INDEX:
                    return String.format("%s(%04x)", getName(), asBytecodeIndex());
                case BRANCH_PROFILE:
                    return String.format("%s(%s)", getName(), asBranchProfile().toString());
                case TAG_NODE:
                    return String.format("%s%s", getName(), printTagProfile(asTagNode()));
                default:
                    throw new UnsupportedOperationException("Unexpected argument kind " + getKind());
            }
        }

        private static String printTagProfile(TagTreeNode o) {
            if (o == null) {
                return "null";
            }
            return TagTreeNode.format(o);
        }

        private String printNodeProfile(Object o) {
            StringBuilder sb = new StringBuilder();
            if (o == null) {
                return "null";
            }
            sb.append(o.getClass().getSimpleName());
            List<SpecializationInfo> info = getSpecializationInfo();
            if (info != null) {
                sb.append("(");
                String sep = "";
                for (SpecializationInfo specialization : info) {
                    if (specialization.getInstances() == 0) {
                        continue;
                    }
                    sb.append(sep);
                    sb.append(specialization.getMethodName());
                    sep = "#";
                }
                sb.append(")");
            }
            return sb.toString();
        }

        private static String printConstant(Object value) {
            if (value == null) {
                return "null";
            }
            String typeString = value.getClass().getSimpleName();
            String valueString = value.getClass().isArray() ? printArray(value) : value.toString();
            if (valueString.length() > 100) {
                valueString = valueString.substring(0, 97) + "...";
            }
            return String.format("%s %s", typeString, valueString);
        }

        private static String printArray(Object array) {
            if (array instanceof Object[] objArr) {
                return Arrays.toString(objArr);
            } else if (array instanceof long[] longArr) {
                return Arrays.toString(longArr);
            } else if (array instanceof int[] intArr) {
                return Arrays.toString(intArr);
            } else if (array instanceof short[] shortArr) {
                return Arrays.toString(shortArr);
            } else if (array instanceof char[] charArr) {
                return Arrays.toString(charArr);
            } else if (array instanceof byte[] byteArr) {
                return Arrays.toString(byteArr);
            } else if (array instanceof double[] doubleArr) {
                return Arrays.toString(doubleArr);
            } else if (array instanceof float[] floatArr) {
                return Arrays.toString(floatArr);
            } else if (array instanceof boolean[] boolArr) {
                return Arrays.toString(boolArr);
            }
            throw new AssertionError(String.format("Unhandled array type %s", array));
        }

        /**
         * Represents kind of an {@link Argument}.
         *
         * @since 24.2
         */
        public enum Kind {
            /**
             * A constant argument to the instruction. Typically constants are used to encode
             * {@link ConstantOperand} and loadConstant builtin operations.
             *
             * @see Argument#asConstant()
             * @since 24.2
             */
            CONSTANT,

            /**
             * A bytecode index argument to the instruction. Typically a bytecode indices are used
             * to encode branch targets.
             *
             * @see Argument#asBytecodeIndex()
             * @since 24.2
             */
            BYTECODE_INDEX,

            /**
             * A integer argument to the instruction. Typically a integer arguments are used to
             * encode argument indices and other constants.
             *
             * @see Argument#asInteger()
             * @since 24.2
             */
            INTEGER,

            /**
             * A localOffset argument to the instruction. Typically a localOffset arguments are used
             * to encode arguments of load local builtin instructions.
             *
             * @see Argument#asLocalOffset()
             * @since 24.2
             */
            LOCAL_OFFSET,

            /**
             * A localIndex argument to the instruction. Typically a localIndex arguments are used
             * to encode arguments of load local builtin instructions.
             *
             * @see Argument#asLocalIndex()
             * @since 24.2
             */
            LOCAL_INDEX,

            /**
             * A node profile argument to the instruction. Typically a node profile arguments are
             * used to encode cached nodes.
             *
             * @see Argument#asCachedNode()
             * @since 24.2
             */
            NODE_PROFILE,

            /**
             * A branch profile argument to the instruction. Typically a branch profile is used for
             * branch instructions.
             *
             * @see Argument#asBranchProfile()
             * @since 24.2
             */
            BRANCH_PROFILE,
            TAG_NODE;
        }

        /**
         * Represents a branch profile.
         *
         * @since 24.2
         */
        public record BranchProfile(
                        /**
                         * The index of the profile for the branch profile table.
                         *
                         * @since 24.2
                         */
                        int index,

                        /**
                         * The number of times this conditional branch was taken.
                         *
                         * @since 24.2
                         */
                        int trueCount,

                        /**
                         * The number of times this conditional branch was not taken.
                         *
                         * @since 24.2
                         */
                        int falseCount) {

            /**
             * Returns the frequency recorded by this profile.
             *
             * @since 24.2
             */
            public double getFrequency() {
                int total = trueCount + falseCount;
                if (total == 0) {
                    return 0.0d;
                }
                return ((double) trueCount) / ((double) total);
            }

            /**
             * {@inheritDoc}
             *
             * @since 24.2
             */
            @Override
            public String toString() {
                if (trueCount + falseCount == 0) {
                    return index + ":never executed";
                }
                return String.format("%s:%.2f", index, getFrequency());
            }

        }

    }

    static final class InstructionIterable implements Iterable<Instruction> {

        private final BytecodeNode bytecodeNode;

        InstructionIterable(BytecodeNode bytecodeNode) {
            this.bytecodeNode = bytecodeNode;
        }

        @Override
        public Iterator<Instruction> iterator() {
            return new InstructionIterator(bytecodeNode.findInstruction(0));
        }

    }

    private static final class InstructionIterator implements Iterator<Instruction> {

        private Instruction current;

        InstructionIterator(Instruction start) {
            this.current = start;
        }

        public boolean hasNext() {
            return current != null;
        }

        public Instruction next() {
            if (current == null) {
                throw new NoSuchElementException();
            }
            Instruction next = current;
            current = next.next();
            return next;
        }

    }

}