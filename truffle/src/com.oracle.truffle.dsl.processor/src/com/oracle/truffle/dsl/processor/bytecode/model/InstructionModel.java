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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationKind;
import com.oracle.truffle.dsl.processor.bytecode.parser.SpecializationSignatureParser.SpecializationSignature;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.model.CacheExpression;
import com.oracle.truffle.dsl.processor.model.InlineFieldData;
import com.oracle.truffle.dsl.processor.model.InlinedNodeData;
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
        TAG_YIELD_NULL,
        TAG_RESUME,
        TRACE_INSTRUCTION,
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
        INT(4),
        LONG(8);

        public final int byteSize;

        ImmediateWidth(int byteSize) {
            this.byteSize = byteSize;
        }

        public TypeMirror toType(ProcessorContext context) {
            return switch (this) {
                case BYTE -> context.getType(byte.class);
                case SHORT -> context.getType(short.class);
                case INT -> context.getType(int.class);
                case LONG -> context.getType(long.class);
            };
        }

        /**
         * Short name to use in fields/methods.
         */
        public String toEncodedName() {
            return switch (this) {
                case BYTE -> "B";
                case SHORT -> "S";
                case INT -> "I";
                case LONG -> "L";
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
        STATE_PROFILE("state", ImmediateWidth.SHORT),
        SHORT("short", ImmediateWidth.SHORT),
        INTEGER("int", ImmediateWidth.INT),
        BYTECODE_INDEX("bci", ImmediateWidth.INT),
        STACK_POINTER("sp", ImmediateWidth.SHORT),
        CONSTANT("const", ImmediateWidth.INT),
        CONSTANT_LONG("const_long", ImmediateWidth.LONG),
        CONSTANT_DOUBLE("const_double", ImmediateWidth.LONG),
        CONSTANT_INT("const_int", ImmediateWidth.INT),
        CONSTANT_FLOAT("const_float", ImmediateWidth.INT),
        CONSTANT_SHORT("const_short", ImmediateWidth.SHORT),
        CONSTANT_CHAR("const_char", ImmediateWidth.SHORT),
        CONSTANT_BYTE("const_byte", ImmediateWidth.SHORT),
        CONSTANT_BOOL("const_bool", ImmediateWidth.SHORT),
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

    public record InstructionImmediate(ImmediateKind kind, String name, InstructionImmediateEncoding encoding, Optional<ConstantOperandModel> constantOperand) {

        public InstructionImmediate(ImmediateKind kind, String name, InstructionImmediateEncoding encoding) {
            this(kind, name, encoding, Optional.empty());
        }

        public boolean explicit() {
            return encoding.explicit();
        }

        public int offset() {
            return encoding.offset();
        }
    }

    public record InstructionEncoding(List<InstructionImmediateEncoding> immediates, int length) implements Comparable<InstructionEncoding> {

        InstructionEncoding(InstructionModel instruction) {
            this(instruction.immediates.stream().map((i) -> i.encoding()).toList(),
                            instruction.getInstructionLength());
        }

        @Override
        public int compareTo(InstructionEncoding other) {
            if (this.equals(other)) {
                return 0;
            }

            // First, order by byte length.
            int diff = length - other.length;
            if (diff != 0) {
                return diff;
            }

            // Then, order by number of immediates.
            diff = immediates.size() - other.immediates.size();
            if (diff != 0) {
                return diff;
            }

            // If both match, order by each pairwise immediate's byte width.
            for (int i = 0; i < immediates.size(); i++) {
                diff = immediates.get(i).compareTo(other.immediates.get(i));
                if (diff != 0) {
                    return diff;
                }
            }

            throw new AssertionError("compareTo cannot determine that non-equal instruction encodings are not equal.");
        }

        public List<InstructionImmediateEncoding> getExplicitImmediateEncodings() {
            return immediates.stream().filter((i) -> i.explicit()).toList();
        }

    }

    public record InstructionImmediateEncoding(int offset, ImmediateWidth width, boolean explicit) implements Comparable<InstructionImmediateEncoding> {

        public static final InstructionImmediateEncoding NONE = new InstructionImmediateEncoding(0, null, false);

        @Override
        public int compareTo(InstructionImmediateEncoding other) {
            if (this.equals(other)) {
                return 0;
            }
            int diff = this.width.byteSize - other.width.byteSize;
            if (diff != 0) {
                return diff;
            }
            return Boolean.compare(this.explicit, other.explicit);
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

    /*
     * Defines a mapping from constant operand to immediate. Useful for loading constant operands
     * from bytecode.
     */
    public Map<ConstantOperandModel, InstructionImmediate> constantOperandImmediates = new HashMap<>();

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

    /*
     * Main constructor for instructions.
     */
    public InstructionModel(InstructionKind kind, String name, Signature signature) {
        this.kind = kind;
        this.name = name;
        this.signature = signature;
        this.quickeningName = null;
    }

    /*
     * Quickening constructor.
     */
    public InstructionModel(InstructionModel base, String quickeningName, Signature signature) {
        this.kind = base.kind;
        this.name = base.name + "$" + quickeningName;
        this.signature = signature;
        this.quickeningName = quickeningName;
        this.filteredSpecializations = base.filteredSpecializations;
        this.nodeData = base.nodeData;
        this.nodeType = base.nodeType;
        this.quickeningBase = base;
        this.operation = base.operation;
        this.shortCircuitModel = base.shortCircuitModel;
        for (InstructionImmediate imm : base.immediates) {
            addImmediate(imm);
        }
        base.quickenedInstructions.add(this);
    }

    public List<InstructionImmediate> getExplicitImmediates() {
        return immediates.stream().filter((i) -> i.explicit()).toList();
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
        printer.field("byteLength", byteLength);
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
            case TAG_YIELD_NULL:
                return true;
            default:
                return false;
        }
    }

    public boolean isTraceInstrumentation() {
        return kind == InstructionKind.TRACE_INSTRUCTION;
    }

    public boolean isInstrumentation() {
        if (isTraceInstrumentation()) {
            return true;
        } else if (isTagInstrumentation()) {
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
            case CUSTOM:
                return operation.kind == OperationKind.CUSTOM_YIELD;
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
        return addImmediate(immediateKind, immediateName, true);
    }

    public InstructionModel addImmediate(ImmediateKind immediateKind, String immediateName, boolean explicit) {
        addImmediate(new InstructionImmediate(immediateKind, immediateName, new InstructionImmediateEncoding(byteLength, immediateKind.width, explicit)));
        return this;
    }

    public InstructionModel addConstantOperandImmediate(ConstantOperandModel constantOperand, String immediateName) {
        addImmediate(new InstructionImmediate(constantOperand.kind(), immediateName, new InstructionImmediateEncoding(byteLength, constantOperand.kind().width, true), Optional.of(constantOperand)));
        return this;
    }

    private void addImmediate(InstructionImmediate immediate) {
        if (immediate.offset() != byteLength) {
            throw new AssertionError("Immediate has offset " + immediate.offset() + " but the instruction is currently only " + byteLength + " bytes long.");
        }
        immediates.add(immediate);
        if (immediate.constantOperand.isPresent()) {
            constantOperandImmediates.put(immediate.constantOperand.get(), immediate);
        }
        byteLength += immediate.kind.width.byteSize;
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
        return new InstructionEncoding(this);
    }

    public String getInternalName() {
        String operationName = switch (kind) {
            case CUSTOM -> {
                if (!name.startsWith("c.")) {
                    throw new AssertionError("Unexpected custom operation name: " + name);
                }
                yield name.substring(2) + "_";
            }
            case CUSTOM_SHORT_CIRCUIT -> {
                if (!name.startsWith("sc.")) {
                    throw new AssertionError("Unexpected short-circuit custom operation name: " + name);
                }
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
        if (!canInlineState() && (nodeData.needsState() || nodeData.isForceSpecialize())) {
            return false;
        }
        for (SpecializationData specialization : nodeData.getReachableSpecializations()) {
            for (CacheExpression cache : specialization.getCaches()) {
                if (cache.isAlwaysInitialized()) {
                    continue;
                }
                if (cache.isEncodedEnum()) {
                    continue;
                }
                return false;
            }

            /*
             * When storeBciInFrame is disabled, $node may be used to identify the bytecode
             * location, so if $node is bound we cannot use a node singleton. We can still use a
             * node singleton when storeBciInFrame is true: we just bind the bytecode node to $node
             * instead of the singleton. The bytecode node and the bytecode index is enough to
             * identify the stack trace.
             */
            if (!operation.parent.storeBciInFrame && specialization.isNodeReceiverBoundInAnyExpression()) {
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
            if (immediate.kind == ImmediateKind.SHORT && immediate.offset() % 2 != 0) {
                throw new AssertionError(String.format("Immediate %s of instruction %s should be short-aligned, but it appears at offset %s.",
                                immediate.name, name, immediate.offset()));
            }
        }
    }

    /**
     * Returns <code>true</code> if all state of this operation should get inlined into the
     * instruction, else <code>false</code>.
     *
     * Inlining the state is almost always beneficial as we already have the bytecode array in a
     * register.
     */
    public boolean canInlineState() {
        NodeData node = this.nodeData;
        if (node == null) {
            return false;
        }

        for (SpecializationData specialization : node.getReachableSpecializations()) {
            for (CacheExpression cache : specialization.getCaches()) {
                InlinedNodeData inlinedNode = cache.getInlinedNode();
                if (inlinedNode != null) {
                    for (InlineFieldData inlineField : inlinedNode.getFields()) {
                        /*
                         * Inlined node data cannot be supported if state is inlined.
                         */
                        if (inlineField.isState()) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

}
