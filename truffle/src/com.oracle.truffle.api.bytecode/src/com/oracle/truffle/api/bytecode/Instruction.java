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

import com.oracle.truffle.api.dsl.Introspection;
import com.oracle.truffle.api.dsl.Introspection.SpecializationInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

public abstract class Instruction {

    protected Instruction(Object token) {
        BytecodeRootNodes.checkToken(token);
    }

    public abstract BytecodeNode getBytecodeNode();

    public abstract int getBytecodeIndex();

    public final BytecodeLocation getLocation() {
        return getBytecodeNode().getBytecodeLocation(getBytecodeIndex());
    }

    public abstract String getName();

    public abstract int getOperationCode();

    public abstract List<Argument> getArguments();

    public abstract boolean isInstrumentation();

    public final SourceSection getSourceSection() {
        BytecodeNode bytecode = getBytecodeNode();
        if (bytecode.getSourceInformation() == null) {
            // avoid materialization of source info
            return null;
        }
        return bytecode.findSourceLocation(getBytecodeIndex());
    }

    public final SourceSection[] getSourceSections() {
        BytecodeNode bytecode = getBytecodeNode();
        if (bytecode.getSourceInformation() == null) {
            // avoid materialization of source info
            return null;
        }
        return getBytecodeNode().findSourceLocations(getBytecodeIndex());
    }

    public final int getNextBytecodeIndex() {
        return getBytecodeIndex() + getArguments().size() + 1;
    }

    protected abstract Instruction next();

    @Override
    public String toString() {
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

    public abstract static class Argument {

        protected Argument(Object token) {
            BytecodeRootNodes.checkToken(token);
        }

        public abstract Kind getKind();

        public abstract String getName();

        public int asInteger() {
            throw unsupported();
        }

        public int asBytecodeIndex() {
            throw unsupported();
        }

        public Object asConstant() {
            throw unsupported();
        }

        public Node asNodeProfile() {
            throw unsupported();
        }

        public Node asTagNode() {
            throw unsupported();
        }

        public int asLocalOffset() {
            throw unsupported();
        }

        public int asLocalIndex() {
            throw unsupported();
        }

        public BranchProfile asBranchProfile() {
            throw unsupported();
        }

        public final List<SpecializationInfo> getSpecializationInfo() {
            Node n = asNodeProfile();
            if (Introspection.isIntrospectable(n)) {
                return Introspection.getSpecializations(n);
            } else {
                return null;
            }
        }

        private RuntimeException unsupported() {
            return new UnsupportedOperationException(String.format("Not supported for argument type %s.", getKind()));
        }

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
                    return String.format("%s(%s)", getName(), printNodeProfile(asNodeProfile()));
                case BYTECODE_INDEX:
                    return String.format("%s(%04x)", getName(), asBytecodeIndex());
                case BRANCH_PROFILE:
                    return String.format("%s(%s)", getName(), asBranchProfile().toString());
                case TAG_NODE:
                    return String.format("%s%s", getName(), printTagProfile((TagTreeNode) asTagNode()));
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

        public enum Kind {
            CONSTANT,
            BYTECODE_INDEX,
            INTEGER,
            LOCAL_OFFSET,
            LOCAL_INDEX,
            NODE_PROFILE,
            BRANCH_PROFILE,
            TAG_NODE;
        }

        public record BranchProfile(int index, int trueCount, int falseCount) {

            public double getFrequency() {
                int total = trueCount + falseCount;
                if (total == 0) {
                    return 0.0d;
                }
                return ((double) trueCount) / ((double) total);
            }

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
