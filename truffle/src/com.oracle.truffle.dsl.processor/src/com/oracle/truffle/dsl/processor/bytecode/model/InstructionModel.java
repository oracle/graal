/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.List;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationKind;
import com.oracle.truffle.dsl.processor.bytecode.parser.SpecializationSignatureParser.SpecializationSignature;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.SpecializationData;

public final class InstructionModel implements PrettyPrintable {
    public static final int OPCODE_WIDTH = 2; // short

    /*
     * Sort by how commonly they are used.
     */
    public enum InstructionKind {
        LOAD_ARGUMENT,
        LOAD_CONSTANT,
        LOAD_LOCAL,
        CLEAR_LOCAL,
        STORE_LOCAL,
        BRANCH,
        BRANCH_BACKWARD,
        BRANCH_FALSE,
        POP,
        DUP,
        LOAD_VARIADIC,
        CREATE_VARIADIC,
        EMPTY_VARIADIC,
        SPLAT_VARIADIC,
        LOAD_LOCAL_MATERIALIZED,
        STORE_LOCAL_MATERIALIZED,

        LOAD_NULL,
        RETURN,
        YIELD,
        THROW,
        MERGE_CONDITIONAL,

        CUSTOM,
        CUSTOM_SHORT_CIRCUIT,
        SUPERINSTRUCTION,

        LOAD_EXCEPTION,
        TAG_ENTER,
        TAG_LEAVE,
        TAG_LEAVE_VOID,
        TAG_YIELD,
        TAG_RESUME,
        INVALIDATE;

        public boolean isLocalVariableAccess() {
            switch (this) {
                case LOAD_LOCAL:
                case STORE_LOCAL:
                case CLEAR_LOCAL:
                    return true;
            }
            return false;
        }

        public boolean isCustom() {
            switch (this) {
                case CUSTOM:
                case CUSTOM_SHORT_CIRCUIT:
                    return true;
            }
            return false;
        }

        public boolean isLocalVariableMaterializedAccess() {
            switch (this) {
                case LOAD_LOCAL_MATERIALIZED:
                case STORE_LOCAL_MATERIALIZED:
                    return true;
            }
            return false;
        }
    }

    public enum ImmediateWidth {
        BYTE(1),
        SHORT(2),
        INT(4);

        public final int byteSize;

        ImmediateWidth(int byteSize) {
            this.byteSize = byteSize;
        }

        public TypeMirror toType(ProcessorContext context) {
            return switch (this) {
                case BYTE -> context.getType(byte.class);
                case SHORT -> context.getType(short.class);
                case INT -> context.getType(int.class);
            };
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public enum ImmediateKind {
        /**
         * Frame index of a local. Should not be directly exposed to users; instead we expose a
         * local offset, which is the logical offset of the local (frame_index - n_reserved_slots).
         */
        FRAME_INDEX("frame_index", ImmediateWidth.SHORT),
        /**
         * Index into the locals table. We may encode the local index when it is needed for
         * {@link BytecodeDSLModel#localAccessesNeedLocalIndex() local accesses} or
         * {@link BytecodeDSLModel#materializedLocalAccessesNeedLocalIndex() materialized local
         * accesses}.
         */
        LOCAL_INDEX("local_index", ImmediateWidth.SHORT),
        /**
         * Index into BytecodeRootNodes.nodes. Necessary for boxing elimination of materialized
         * local accesses.
         */
        LOCAL_ROOT("local_root", ImmediateWidth.SHORT),
        SHORT("short", ImmediateWidth.SHORT),
        INTEGER("int", ImmediateWidth.INT),
        BYTECODE_INDEX("bci", ImmediateWidth.INT),
        STACK_POINTER("sp", ImmediateWidth.SHORT),
        CONSTANT("const", ImmediateWidth.INT),
        NODE_PROFILE("node", ImmediateWidth.INT),
        TAG_NODE("tag", ImmediateWidth.INT),
        BRANCH_PROFILE("branch_profile", ImmediateWidth.INT);

        public final String shortName;
        public final ImmediateWidth width;

        ImmediateKind(String shortName, ImmediateWidth width) {
            this.shortName = shortName;
            this.width = width;
        }

        public TypeMirror toType(ProcessorContext context) {
            return width.toType(context);
        }
    }

    public record InstructionImmediate(int offset, ImmediateKind kind, String name) {

    }

    public static final class InstructionEncoding implements Comparable<InstructionEncoding> {
        public final ImmediateWidth[] immediates;
        public final int length;

        public InstructionEncoding(ImmediateWidth[] immediates, int length) {
            this.immediates = immediates;
            this.length = length;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(immediates);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof InstructionEncoding otherEncoding)) {
                return false;
            }
            if (immediates.length != otherEncoding.immediates.length) {
                return false;
            }
            for (int i = 0; i < immediates.length; i++) {
                if (immediates[i] != otherEncoding.immediates[i]) {
                    return false;
                }
            }
            return true;
        }

        public int compareTo(InstructionEncoding other) {
            // First, order by byte length.
            int diff = length - other.length;
            if (diff != 0) {
                return diff;
            }

            // Then, order by number of immediates.
            diff = immediates.length - other.immediates.length;
            if (diff != 0) {
                return diff;
            }

            // If both match, order by each pairwise immediate's byte width.
            for (int i = 0; i < immediates.length; i++) {
                if (immediates[i] != other.immediates[i]) {
                    return immediates[i].byteSize - other.immediates[i].byteSize;
                }
            }

            return 0;
        }
    }

    private short id = -1;
    private int byteLength = OPCODE_WIDTH;
    public final InstructionKind kind;
    public final String name;
    public final String quickeningName;
    public final Signature signature;
    public CodeTypeElement nodeType;
    public NodeData nodeData;

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

    public boolean generic;

    public boolean nonNull;

    /*
     * Alternative argument specialization type for builtin quickenings. E.g. for loadLocal
     * parameter types.
     */
    public TypeMirror specializedType;

    public ShortCircuitInstructionModel shortCircuitModel;

    /*
     * Contains the short circuit instructions that use this instruction as a converter.
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

    public SpecializationSignature getSpecializationSignature() {
        return operation.getSpecializationSignature(filteredSpecializations);
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

    public short getId() {
        if (id == -1) {
            throw new IllegalStateException("Id not yet assigned");
        }
        return id;
    }

    void setId(short id) {
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

    public boolean isSpecializedQuickening() {
        return quickeningBase != null && !returnTypeQuickening && !generic;
    }

    public boolean hasSpecializedQuickenings() {
        for (InstructionModel instr : quickenedInstructions) {
            if (instr.isSpecializedQuickening()) {
                return true;
            }
        }
        return false;
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

        if (getQuickeningRoot().hasQuickenings()) {
            String quickenKind;
            if (quickeningBase == null) {
                quickenKind = "base";
            } else {
                if (isReturnTypeQuickening()) {
                    quickenKind = "return-type";
                } else {
                    if (generic) {
                        quickenKind = "generic";
                    } else {
                        quickenKind = "specialized";
                    }
                }
            }
            printer.field("quicken-kind", quickenKind);
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
            case INVALIDATE:
                return true;
            default:
                return false;
        }
    }

    public boolean hasNodeImmediate() {
        switch (kind) {
            case CUSTOM:
                return !canUseNodeSingleton();
            default:
                return false;
        }
    }

    public InstructionModel addImmediate(ImmediateKind immediateKind, String immediateName) {
        immediates.add(new InstructionImmediate(byteLength, immediateKind, immediateName));
        byteLength += immediateKind.width.byteSize;
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

    public boolean hasImmediate(ImmediateKind immediateKind) {
        return !getImmediates(immediateKind).isEmpty();
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
        return byteLength;
    }

    public InstructionEncoding getInstructionEncoding() {
        ImmediateWidth[] immediateWidths = new ImmediateWidth[immediates.size()];
        for (int i = 0; i < immediateWidths.length; i++) {
            immediateWidths[i] = immediates.get(i).kind.width;
        }
        return new InstructionEncoding(immediateWidths, byteLength);
    }

    public String getInternalName() {
        String operationName = switch (kind) {
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
        StringBuilder b = new StringBuilder(operationName);
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

    public SpecializationData resolveSingleSpecialization() {
        List<SpecializationData> specializations = null;
        if (this.filteredSpecializations != null) {
            specializations = this.filteredSpecializations;
        } else if (this.nodeData != null) {
            specializations = this.nodeData.getReachableSpecializations();
        }
        if (specializations != null && specializations.size() == 1) {
            return specializations.get(0);
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("Instruction(%s)", name);
    }

    public String prettyPrintEncoding() {
        StringBuilder b = new StringBuilder("[");
        b.append(getId());
        b.append(" : short");
        for (InstructionImmediate imm : immediates) {
            b.append(", ");
            b.append(imm.name);
            if (!imm.name.equals(imm.kind.shortName)) {
                b.append(" (");
                b.append(imm.kind.shortName);
                b.append(")");
            }
            b.append(" : ");
            b.append(imm.kind.width);
        }
        b.append("]");
        return b.toString();
    }

    public boolean canUseNodeSingleton() {
        if (nodeData == null) {
            return false;
        }
        if (nodeData.needsState()) {
            return false;
        }
        for (SpecializationData specialization : nodeData.getReachableSpecializations()) {
            if (specialization.isNodeReceiverBoundInAnyExpression()) {
                return false;
            }
        }
        return true;
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

    public InstructionModel findSpecializedInstruction(TypeMirror type) {
        for (InstructionModel specialization : quickenedInstructions) {
            if (!specialization.generic && ElementUtils.typeEquals(type, specialization.specializedType)) {
                return specialization;
            }
        }
        return null;
    }

    public InstructionModel findGenericInstruction() {
        for (InstructionModel specialization : quickenedInstructions) {
            if (specialization.generic) {
                return specialization;
            }
        }
        return null;
    }

    public void validateAlignment() {
        /*
         * Unaligned accesses are not atomic. For correctness, since we overwrite opcodes for
         * quickening/invalidation, our instructions *must* be short-aligned.
         *
         * Additionally, byte array reads are only PE-constant when they are aligned. Thus, for
         * performance, it is crucial that opcodes and immediates are aligned. We enforce short
         * immediate alignment below.
         *
         * Uniquely, int immediates do *not* need to be int-aligned. Bytecode DSL interpreters use
         * special PE-able methods for int reads that split unaligned reads into multiple aligned
         * reads in compiled code. Since immediates are never modified, atomicity is not important.
         */
        if (getInstructionLength() % 2 != 0) {
            throw new AssertionError(String.format("All instructions should be short-aligned, but instruction %s has length %s.",
                            name, getInstructionLength()));
        }

        for (InstructionImmediate immediate : immediates) {
            if (immediate.kind == ImmediateKind.SHORT && immediate.offset % 2 != 0) {
                throw new AssertionError(String.format("Immediate %s of instruction %s should be short-aligned, but it appears at offset %s.",
                                immediate.name, name, immediate.offset));
            }
        }
    }
}
