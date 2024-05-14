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
package com.oracle.truffle.dsl.processor.bytecode.model;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationKind;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.SpecializationData;

public final class InstructionModel implements PrettyPrintable {
    public enum InstructionKind {
        BRANCH,
        BRANCH_BACKWARD,
        BRANCH_FALSE,
        POP,
        DUP,
        TRAP,
        TAG_ENTER,
        TAG_LEAVE,
        TAG_LEAVE_VOID,
        TAG_YIELD,
        TAG_RESUME,
        LOAD_ARGUMENT,
        LOAD_CONSTANT,
        LOAD_LOCAL,
        CLEAR_LOCAL,
        LOAD_LOCAL_MATERIALIZED,
        STORE_LOCAL,
        STORE_LOCAL_MATERIALIZED,
        LOAD_VARIADIC,
        MERGE_VARIADIC,
        STORE_NULL,

        RETURN,
        YIELD,
        THROW,
        MERGE_CONDITIONAL,

        INVALIDATE,

        CUSTOM,
        CUSTOM_SHORT_CIRCUIT,
        SUPERINSTRUCTION,
    }

    public enum ImmediateKind {
        /**
         * Relative local offset into the frame. Without boxing elimination or local scoping
         * localOffset == localIndex.
         */
        LOCAL_OFFSET("localOffset"),
        /**
         * Local index into the locals table. Without boxing elimination or local scoping
         * localOffset == localIndex.
         */
        LOCAL_INDEX("localIndex"),
        /**
         * Index into BytecodeRootNodes.nodes. Necessary for boxing elimination.
         */
        LOCAL_ROOT("localRoot"),
        INTEGER("int"),
        BYTECODE_INDEX("bci"),
        CONSTANT("const"),
        NODE_PROFILE("node"),
        TAG_NODE("tag"),
        BRANCH_PROFILE("profile");

        final String shortName;

        ImmediateKind(String shortName) {
            this.shortName = shortName;
        }
    }

    public record InstructionImmediate(int offset, ImmediateKind kind, String name) {

    }

    private int id = -1;
    public final InstructionKind kind;
    public final String name;
    public final String quickeningName;
    public final Signature signature;
    public CodeTypeElement nodeType;
    public NodeData nodeData;
    public int variadicPopCount = -1;

    // Immediate values that get encoded in the bytecode.
    public final List<InstructionImmediate> immediates = new ArrayList<>();

    public List<InstructionModel> subInstructions;
    public final List<InstructionModel> quickenedInstructions = new ArrayList<>();

    public List<SpecializationData> filteredSpecializations;

    public InstructionModel quickeningBase;
    // operation this instruction stems from. null if none
    public OperationModel operation;

    /*
     * Used for return type boxing elimination quickenings.
     */
    public boolean returnTypeQuickening;

    /*
     * Alternative argument specialization type for builtin quickenings. E.g. for loadLocal
     * parameter types.
     */
    public TypeMirror specializedType;

    public ShortCircuitInstructionModel shortCircuitModel;

    /*
     * Contains all short circuit instructions used by converters.
     */
    public final List<InstructionModel> shortCircuitInstructions = new ArrayList<>();

    public InstructionModel(InstructionKind kind, String name, Signature signature, String quickeningName) {
        this.kind = kind;
        this.name = name;
        this.signature = signature;
        this.quickeningName = quickeningName;
    }

    public boolean isShortCircuitConverter() {
        return !shortCircuitInstructions.isEmpty();
    }

    public boolean isEpilogReturn() {
        if (this.operation == null) {
            return false;
        }
        var epilogReturn = operation.parent.epilogReturn;
        if (epilogReturn == null) {
            return false;
        }
        return epilogReturn.operation.instruction == this;
    }

    public boolean isEpilogExceptional() {
        if (this.operation == null) {
            return false;
        }
        var epilogExceptional = operation.parent.epilogExceptional;
        if (epilogExceptional == null) {
            return false;
        }
        return epilogExceptional.operation.instruction == this;
    }

    public int getId() {
        if (id == -1) {
            throw new IllegalStateException("Id not yet assigned");
        }
        return id;
    }

    void setId(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("Invalid id.");
        }
        if (this.id != -1) {
            throw new IllegalStateException("Id already assigned ");
        }
        this.id = id;
    }

    public List<InstructionModel> getFlattenedQuickenedInstructions() {
        if (quickenedInstructions.isEmpty()) {
            return quickenedInstructions;
        }
        List<InstructionModel> allInstructions = new ArrayList<>();
        for (InstructionModel child : this.quickenedInstructions) {
            allInstructions.add(child);
            allInstructions.addAll(child.getFlattenedQuickenedInstructions());
        }
        return allInstructions;
    }

    public String getQuickeningName() {
        return quickeningName;
    }

    public InstructionModel getQuickeningRoot() {
        if (quickeningBase != null) {
            return quickeningBase.getQuickeningRoot();
        }
        return this;
    }

    public String getQualifiedQuickeningName() {
        InstructionModel current = this;
        List<String> quickeningNames = new ArrayList<>();
        while (current != null) {
            if (current.quickeningName != null) {
                quickeningNames.add(0, current.quickeningName.replace('#', '_'));
            }
            current = current.quickeningBase;
        }
        return String.join("$", quickeningNames);
    }

    public boolean hasQuickenings() {
        return !quickenedInstructions.isEmpty();
    }

    public boolean isQuickening() {
        return quickeningBase != null;
    }

    public boolean isReturnTypeQuickening() {
        return returnTypeQuickening;
    }

    @Override
    public void pp(PrettyPrinter printer) {
        printer.print("Instruction %s", name);
        printer.field("kind", kind);
        printer.field("encoding", prettyPrintEncoding());
        if (nodeType != null) {
            printer.field("nodeType", nodeType.getSimpleName());
        }
        if (signature != null) {
            printer.field("signature", signature);
        }
    }

    public boolean isTagInstrumentation() {
        switch (kind) {
            case TAG_ENTER:
            case TAG_LEAVE:
            case TAG_LEAVE_VOID:
            case TAG_RESUME:
            case TAG_YIELD:
                return true;
            default:
                return false;
        }
    }

    public boolean isInstrumentation() {
        if (isTagInstrumentation()) {
            return true;
        } else if (kind == InstructionKind.CUSTOM) {
            return operation.kind == OperationKind.CUSTOM_INSTRUMENTATION;
        } else {
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
                return true;
            default:
                return false;
        }
    }

    public boolean hasNodeImmediate() {
        switch (kind) {
            case CUSTOM:
                return true;
            default:
                return false;
        }
    }

    public boolean hasConditionalBranch() {
        return kind == InstructionKind.BRANCH_FALSE;
    }

    public InstructionModel addImmediate(ImmediateKind immediateKind, String immediateName) {
        immediates.add(new InstructionImmediate(1 + immediates.size(), immediateKind, immediateName));
        return this;
    }

    public InstructionImmediate findImmediate(ImmediateKind immediateKind, String immediateName) {
        for (InstructionImmediate immediate : immediates) {
            if (immediate.kind == immediateKind && immediate.name.equals(immediateName)) {
                return immediate;
            }
        }
        return null;
    }

    public List<InstructionImmediate> getImmediates() {
        return immediates;
    }

    public List<InstructionImmediate> getImmediates(ImmediateKind immediateKind) {
        return immediates.stream().filter(imm -> imm.kind == immediateKind).toList();
    }

    public InstructionImmediate getImmediate(ImmediateKind immediateKind) {
        List<InstructionImmediate> filteredImmediates = getImmediates(immediateKind);
        if (filteredImmediates.isEmpty()) {
            return null;
        } else if (filteredImmediates.size() > 1) {
            throw new AssertionError("Too many immediates of kind " + immediateKind + ". Use getImmediates() instead. Found immediates: " + filteredImmediates);
        }
        return filteredImmediates.get(0);
    }

    public InstructionImmediate getImmediate(String immediateName) {
        return immediates.stream().filter(imm -> imm.name.equals(immediateName)).findAny().get();
    }

    public int getInstructionLength() {
        return 1 + immediates.size();
    }

    public String getInternalName() {
        String withoutPrefix = switch (kind) {
            case CUSTOM -> {
                assert name.startsWith("c.");
                yield name.substring(2) + "_";
            }
            case CUSTOM_SHORT_CIRCUIT -> {
                assert name.startsWith("sc.");
                yield name.substring(3) + "_";
            }
            default -> name;
        };
        StringBuilder b = new StringBuilder(withoutPrefix);
        for (int i = 0; i < b.length(); i++) {
            char c = b.charAt(i);
            switch (c) {
                case '.':
                    if (i + 1 < b.length()) {
                        b.setCharAt(i + 1, Character.toUpperCase(b.charAt(i + 1)));
                    }
                    b.deleteCharAt(i);
                    break;
                case '#':
                    b.setCharAt(i, '$');
                    break;
            }
        }
        return b.toString();
    }

    public String getConstantName() {
        return ElementUtils.createConstantName(getInternalName());
    }

    @Override
    public String toString() {
        return String.format("Instruction(%s)", name);
    }

    public String prettyPrintEncoding() {
        StringBuilder b = new StringBuilder("[");
        b.append(getId());
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

    public boolean needsBoxingElimination(BytecodeDSLModel model, int valueIndex) {
        if (!model.usesBoxingElimination()) {
            return false;
        }
        if (signature.isVariadicParameter(valueIndex)) {
            return false;
        }
        if (model.isBoxingEliminated(signature.getSpecializedType(valueIndex))) {
            return true;
        }
        for (InstructionModel quickenedInstruction : quickenedInstructions) {
            if (quickenedInstruction.needsBoxingElimination(model, valueIndex)) {
                return true;
            }
        }
        return false;
    }
}
