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
package com.oracle.truffle.dsl.processor.operations.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.model.NodeData;

public class InstructionModel implements InfoDumpable {
    public enum InstructionKind {
        BRANCH,
        BRANCH_BACKWARD,
        BRANCH_FALSE,
        POP,
        INSTRUMENTATION_ENTER,
        INSTRUMENTATION_EXIT,
        INSTRUMENTATION_LEAVE,
        LOAD_ARGUMENT,
        LOAD_CONSTANT,
        LOAD_LOCAL,
        LOAD_LOCAL_MATERIALIZED,
        STORE_LOCAL,
        STORE_LOCAL_MATERIALIZED,
        LOAD_VARIADIC,
        MERGE_VARIADIC,
        STORE_NULL,

        RETURN,
        YIELD,
        THROW,

        CUSTOM,
        CUSTOM_QUICKENED,
        CUSTOM_SHORT_CIRCUIT,
        SUPERINSTRUCTION,
    }

    public enum ImmediateKind {
        INTEGER("int"),
        BYTECODE_INDEX("bci"),
        CONSTANT("const"),
        LOCAL_SETTER("setter"),
        LOCAL_SETTER_RANGE_START("setter_range_start"),
        LOCAL_SETTER_RANGE_LENGTH("setter_range_length"),
        NODE("node");

        final String shortName;

        private ImmediateKind(String shortName) {
            this.shortName = shortName;
        }
    }

    public static class InstructionImmediate {
        public final int offset;
        public final ImmediateKind kind;
        public final String name;

        public InstructionImmediate(int offset, ImmediateKind kind, String name) {
            this.offset = offset;
            this.kind = kind;
            this.name = name;
        }
    }

    public static final class Signature {
        private final ProcessorContext context = ProcessorContext.getInstance();
        // Number of value parameters (includes the variadic parameter, if it exists).
        public int valueCount;
        public boolean isVariadic;
        public int localSetterCount;
        public int localSetterRangeCount;

        public boolean[] valueBoxingElimination;
        public boolean resultBoxingElimination;
        public Set<TypeMirror> possibleBoxingResults;
        public boolean isVoid;

        public TypeMirror getParameterType(int i) {
            assert i > 0 && i < valueCount;
            if (isVariadic && i == valueCount - 1) {
                return context.getType(Object[].class);
            }
            return context.getType(Object.class);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            if (isVoid) {
                sb.append("void ");
            } else if (resultBoxingElimination) {
                if (possibleBoxingResults != null) {
                    sb.append(possibleBoxingResults).append(" ");
                } else {
                    sb.append("box ");
                }
            } else {
                sb.append("obj ");
            }

            sb.append("(");

            for (int i = 0; i < valueCount; i++) {
                sb.append(valueBoxingElimination[i] ? "box" : "obj");
                if (isVariadic && i == valueCount - 1) {
                    sb.append("...");
                }
                sb.append(", ");
            }

            for (int i = 0; i < localSetterCount; i++) {
                sb.append("local, ");
            }

            for (int i = 0; i < localSetterRangeCount; i++) {
                sb.append("localRange, ");
            }

            if (sb.charAt(sb.length() - 1) == ' ') {
                sb.delete(sb.length() - 2, sb.length());
            }

            sb.append(')');

            return sb.toString();
        }
    }

    public final int id;
    public final InstructionKind kind;
    public final String name;
    public CodeTypeElement nodeType;
    public Signature signature;
    public NodeData nodeData;
    public int variadicPopCount = -1;

    // Immediate values that get encoded in the bytecode.
    public final List<InstructionImmediate> immediates = new ArrayList<>();

    public boolean continueWhen;

    public List<InstructionModel> subInstructions;

    public InstructionModel(int id, InstructionKind kind, String name) {
        this.id = id;
        this.kind = kind;
        this.name = name;
    }

    public void dump(Dumper dumper) {
        dumper.print("Instruction %s", name);
        dumper.field("kind", kind);
        dumper.field("encoding", prettyPrintEncoding());
        if (nodeType != null) {
            dumper.field("nodeType", nodeType.getSimpleName());
        }
        if (signature != null) {
            dumper.field("signature", signature);
        }
    }

    public boolean isInstrumentationOnly() {
        switch (kind) {
            case INSTRUMENTATION_ENTER:
            case INSTRUMENTATION_EXIT:
            case INSTRUMENTATION_LEAVE:
                return true;
            default:
                return false;
        }
    }

    public boolean isControlFlow() {
        switch (kind) {
            case BRANCH:
            case BRANCH_BACKWARD:
            case BRANCH_FALSE:
            case RETURN:
            case YIELD:
            case THROW:
            case CUSTOM_SHORT_CIRCUIT:
                return true;
            default:
                return false;
        }
    }

    public boolean isCustomInstruction() {
        switch (kind) {
            case CUSTOM:
            case CUSTOM_SHORT_CIRCUIT:
            case CUSTOM_QUICKENED:
                return true;
            default:
                return false;
        }
    }

    public InstructionModel addImmediate(ImmediateKind immediateKind, String immediateName) {
        immediates.add(new InstructionImmediate(1 + immediates.size(), immediateKind, immediateName));
        return this;
    }

    public List<InstructionImmediate> getImmediates() {
        return immediates;
    }

    public List<InstructionImmediate> getImmediates(ImmediateKind immediateKind) {
        return immediates.stream().filter(imm -> imm.kind == immediateKind).toList();
    }

    public InstructionImmediate getImmediate(ImmediateKind immediateKind) {
        List<InstructionImmediate> filteredImmediates = getImmediates(immediateKind);
        assert filteredImmediates.size() == 1;
        return filteredImmediates.get(0);
    }

    public int getInstructionLength() {
        return 1 + immediates.size();
    }

    public String getInternalName() {
        return name.replace('.', '_');
    }

    public String getConstantName() {
        return getInternalName().toUpperCase();
    }

    @Override
    public String toString() {
        return String.format("Instruction(%s)", name);
    }

    public String prettyPrintEncoding() {
        StringBuilder b = new StringBuilder("[");
        b.append(id);
        for (InstructionImmediate imm : immediates) {
            b.append(", ");
            b.append(imm.kind.shortName);
            b.append(" (");
            b.append(imm.name);
            b.append(")");
        }
        b.append("]");
        return b.toString();
    }
}
