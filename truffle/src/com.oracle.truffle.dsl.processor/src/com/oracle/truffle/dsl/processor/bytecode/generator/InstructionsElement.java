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
package com.oracle.truffle.dsl.processor.bytecode.generator;

import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.arrayOf;
import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.generic;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createConstructorUsingFields;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.SEALED;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateWidth;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionImmediate;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionImmediateEncoding;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

final class InstructionsElement extends AbstractElement {

    InstructionsElement(BytecodeRootNodeElement parent) {
        super(parent, Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "Instructions");
    }

    private ArgumentDescriptorElement argumentDescriptorImpl;
    private CodeTypeElement abstractArgument;

    void lazyInit() {
        /*
         * We order after cached instruction order to ensure we have a compact table switch layout.
         */
        List<List<InstructionModel>> instructionPartitions = BytecodeRootNodeElement.partitionInstructions(parent.model.getInstructions());
        int index = 1;
        for (List<InstructionModel> partition : instructionPartitions) {
            for (InstructionModel instruction : partition) {
                CodeVariableElement fld = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(short.class), instruction.getConstantName());

                fld.createInitBuilder().string(index++).end();
                fld.createDocBuilder().startDoc().lines(instruction.pp()).end(2);
                this.add(fld);
            }
        }

        argumentDescriptorImpl = this.add(new ArgumentDescriptorElement());
        abstractArgument = this.add(new AbstractArgumentElement());

        Set<String> generated = new HashSet<>();
        for (ImmediateKind kind : ImmediateKind.values()) {
            if (kind == ImmediateKind.LOCAL_INDEX && !parent.model.localAccessesNeedLocalIndex() && !parent.model.materializedLocalAccessesNeedLocalIndex()) {
                // Only generate immediate class for LocalIndex when needed.
                continue;
            }

            String className = getImmediateClassName(kind);
            if (generated.contains(className)) {
                continue;
            }
            if (kind == ImmediateKind.TAG_NODE && !parent.model.enableTagInstrumentation) {
                continue;
            }
            CodeTypeElement implType = this.add(new ArgumentElement(kind));
            abstractArgument.getPermittedSubclasses().add(implType.asType());
            generated.add(className);
        }

        this.add(createGetInstructionLength());
        this.add(createGetName());
        this.add(createIsInstrumentation());
        this.add(createGetArguments());
        this.add(createGetArgumentDescriptors());
    }

    public CodeTree call(String methodName, String argumentName) {
        CodeTreeBuilder b = new CodeTreeBuilder(null);
        b.startStaticCall(parent.instructionsElement.asType(), methodName).string(argumentName).end();
        return b.build();
    }

    private CodeExecutableElement createGetInstructionLength() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(int.class), "getLength");
        ex.addParameter(new CodeVariableElement(type(int.class), "opcode"));
        CodeTreeBuilder b = ex.createBuilder();
        b.startSwitch().string("opcode").end().startBlock();
        for (var instructions : BytecodeRootNodeElement.groupInstructionsByLength(parent.model.getInstructions())) {
            for (InstructionModel instruction : instructions) {
                b.startCase().tree(parent.createInstructionConstant(instruction)).end();
            }
            InstructionModel instruction = instructions.get(0);
            b.startCaseBlock();
            b.startReturn().string(instruction.getInstructionLength()).end();
            b.end();
        }
        b.end();
        b.tree(GeneratorUtils.createShouldNotReachHere("Invalid opcode"));
        return ex;
    }

    private CodeExecutableElement createGetName() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(String.class), "getName");
        ex.addParameter(new CodeVariableElement(type(int.class), "opcode"));
        CodeTreeBuilder b = ex.createBuilder();
        b.startSwitch().string("opcode").end().startBlock();
        for (InstructionModel instruction : parent.model.getInstructions()) {
            b.startCase().tree(parent.createInstructionConstant(instruction)).end();
            b.startCaseBlock();
            b.startReturn().doubleQuote(instruction.name).end();
            b.end();
        }
        b.end();
        b.tree(GeneratorUtils.createShouldNotReachHere("Invalid opcode"));
        return ex;
    }

    private CodeExecutableElement createIsInstrumentation() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(boolean.class), "isInstrumentation");
        ex.addParameter(new CodeVariableElement(type(int.class), "opcode"));
        CodeTreeBuilder b = ex.createBuilder();

        Map<Boolean, List<InstructionModel>> grouped = parent.model.getInstructions().stream().collect(BytecodeRootNodeElement.deterministicGroupingBy(InstructionModel::isInstrumentation));

        if (!grouped.containsKey(true)) {
            // Simplification: no instruction is an instrumentation instruction.
            b.startReturn().string("false").end();
            return ex;
        }

        b.startSwitch().string("opcode").end().startBlock();
        for (InstructionModel instrumentationInstruction : grouped.get(true)) {
            b.startCase().tree(parent.createInstructionConstant(instrumentationInstruction)).end();
        }
        b.startCaseBlock();
        b.startReturn().string("true").end();
        b.end();

        b.caseDefault();
        b.startCaseBlock();
        b.startReturn().string("false").end();
        b.end();

        b.end();
        return ex;
    }

    private CodeExecutableElement createGetArgumentDescriptors() {
        TypeMirror returnType = generic(List.class, types.InstructionDescriptor_ArgumentDescriptor);
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), returnType, "getArgumentDescriptors");
        ex.addParameter(new CodeVariableElement(type(int.class), "opcode"));

        CodeTreeBuilder b = ex.createBuilder();

        Map<EqualityCodeTree, List<InstructionModel>> caseGrouping = EqualityCodeTree.group(b, parent.model.getInstructions(), (InstructionModel instruction, CodeTreeBuilder group) -> {
            group.startCaseBlock();
            group.startReturn().startStaticCall(type(List.class), "of");

            for (InstructionImmediate immediate : resolveImmediates(instruction)) {
                group.tree(this.argumentDescriptorImpl.readArgumentConstant(immediate));
            }

            group.end().end(); // return
            group.end(); // case block
        });

        b.startSwitch().string("opcode").end().startBlock();
        for (var group : caseGrouping.entrySet()) {
            EqualityCodeTree key = group.getKey();
            for (InstructionModel instruction : group.getValue()) {
                b.startCase().tree(parent.createInstructionConstant(instruction)).end();
            }
            b.startCaseBlock();
            b.tree(key.getTree());
            b.end();
        }
        b.end(); // switch
        b.tree(GeneratorUtils.createShouldNotReachHere("Invalid opcode"));
        return ex;
    }

    static List<InstructionImmediate> resolveImmediates(InstructionModel instruction) {
        if (instruction.nodeData != null && instruction.canUseNodeSingleton()) {
            List<InstructionImmediate> immediates = new ArrayList<>(instruction.immediates);
            immediates.add(new InstructionImmediate(ImmediateKind.NODE_PROFILE, "node", InstructionImmediateEncoding.NONE, true, Optional.empty()));
            return immediates;
        } else {
            return instruction.immediates;
        }
    }

    private CodeExecutableElement createGetArguments() {
        TypeMirror returnType = generic(List.class, types.Instruction_Argument);
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), returnType, "getArguments");
        ex.addParameter(new CodeVariableElement(type(int.class), "opcode"));
        ex.addParameter(new CodeVariableElement(type(int.class), "bci"));
        ex.addParameter(new CodeVariableElement(parent.abstractBytecodeNode.asType(), "bytecode"));
        ex.addParameter(new CodeVariableElement(type(byte[].class), "bytecodes"));
        ex.addParameter(new CodeVariableElement(type(Object[].class), "constants"));

        CodeTreeBuilder b = ex.createBuilder();

        Map<EqualityCodeTree, List<InstructionModel>> caseGrouping = EqualityCodeTree.group(b, parent.model.getInstructions(), (InstructionModel instruction, CodeTreeBuilder group) -> {
            group.startCaseBlock();
            group.startReturn().startStaticCall(type(List.class), "of");
            for (InstructionImmediate immediate : resolveImmediates(instruction)) {
                emitCreateArgument(group, instruction, immediate);
            }
            group.end().end(); // return
            group.end(); // case block
        });

        b.startSwitch().string("opcode").end().startBlock();
        for (var group : caseGrouping.entrySet()) {
            EqualityCodeTree key = group.getKey();
            for (InstructionModel instruction : group.getValue()) {
                b.startCase().tree(parent.createInstructionConstant(instruction)).end();
            }
            b.startCaseBlock();
            b.tree(key.getTree());
            b.end();
        }
        b.end(); // switch
        b.tree(GeneratorUtils.createShouldNotReachHere("Invalid opcode"));
        return ex;
    }

    private void emitCreateArgument(CodeTreeBuilder b, InstructionModel instruction, InstructionImmediate immediate) {
        b.startGroup();
        b.newLine();
        b.startIndention();
        b.startNew(getImmediateClassName(immediate.kind()));
        b.tree(argumentDescriptorImpl.readArgumentConstant(immediate));
        b.string("bci + " + immediate.offset());

        for (CodeVariableElement var : createImmediateArguments(immediate.kind())) {
            String name = var.getName();
            switch (name) {
                case "bytecodeIndex":
                    b.string("bci");
                    break;
                case "singleton":
                    if (instruction.canUseNodeSingleton()) {
                        b.staticReference(BytecodeRootNodeElement.getCachedDataClassType(instruction), "SINGLETON");
                    } else {
                        b.string("null");
                    }
                    break;
                default:
                    b.string(var.getName());
                    break;
            }
        }

        b.end(); // indention
        b.end(); // line
        b.end(); // group
    }

    private static String getArgumentDescriptorKind(ImmediateKind kind) {
        return switch (kind) {
            case BRANCH_PROFILE -> "BRANCH_PROFILE";
            case BYTECODE_INDEX -> "BYTECODE_INDEX";
            case CONSTANT, CONSTANT_LONG, CONSTANT_DOUBLE, CONSTANT_INT, CONSTANT_FLOAT, CONSTANT_SHORT, CONSTANT_CHAR, CONSTANT_BYTE, CONSTANT_BOOL -> "CONSTANT";
            case FRAME_INDEX -> "LOCAL_OFFSET";
            case LOCAL_INDEX -> "LOCAL_INDEX";
            case SHORT, INTEGER, LOCAL_ROOT, STACK_POINTER, STATE_PROFILE -> "INTEGER";
            case NODE_PROFILE -> "NODE_PROFILE";
            case TAG_NODE -> "TAG_NODE";
        };
    }

    private static String getImmediateClassName(ImmediateKind kind) {
        switch (kind) {
            case BRANCH_PROFILE:
                return "BranchProfileArgument";
            case BYTECODE_INDEX:
                return "BytecodeIndexArgument";
            case CONSTANT:
                return "ConstantArgument";
            case CONSTANT_LONG:
                return "InlinedConstantLongArgument";
            case CONSTANT_DOUBLE:
                return "InlinedConstantDoubleArgument";
            case CONSTANT_INT:
                return "InlinedConstantIntArgument";
            case CONSTANT_FLOAT:
                return "InlinedConstantFloatArgument";
            case CONSTANT_SHORT:
                return "InlinedConstantShortArgument";
            case CONSTANT_CHAR:
                return "InlinedConstantCharArgument";
            case CONSTANT_BYTE:
                return "InlinedConstantByteArgument";
            case CONSTANT_BOOL:
                return "InlinedConstantBooleanArgument";
            case FRAME_INDEX:
                return "LocalOffsetArgument";
            case LOCAL_INDEX:
                return "LocalIndexArgument";
            case SHORT:
            case INTEGER:
            case LOCAL_ROOT:
            case STACK_POINTER:
            case STATE_PROFILE:
                return "IntegerArgument";
            case NODE_PROFILE:
                return "NodeProfileArgument";
            case TAG_NODE:
                return "TagNodeArgument";
        }
        throw new AssertionError("invalid kind");
    }

    private List<CodeVariableElement> createImmediateArguments(ImmediateKind immediateKind) {
        List<CodeVariableElement> args = new ArrayList<>();
        switch (immediateKind) {
            case CONSTANT:
                args.add(new CodeVariableElement(Set.of(FINAL), type(byte[].class), "bytecodes"));
                args.add(new CodeVariableElement(Set.of(FINAL), type(Object[].class), "constants"));
                break;
            case CONSTANT_INT:
            case CONSTANT_FLOAT:
            case CONSTANT_SHORT:
            case CONSTANT_CHAR:
            case CONSTANT_BYTE:
            case CONSTANT_BOOL:
                args.add(new CodeVariableElement(Set.of(FINAL), type(byte[].class), "bytecodes"));
                break;
            case SHORT:
            case LOCAL_ROOT:
            case STACK_POINTER:
            case INTEGER:
            case STATE_PROFILE:
                args.add(new CodeVariableElement(Set.of(FINAL), type(byte[].class), "bytecodes"));
                break;
            case BYTECODE_INDEX:
            case FRAME_INDEX:
                args.add(new CodeVariableElement(Set.of(FINAL), type(byte[].class), "bytecodes"));
                break;
            case NODE_PROFILE:
                args.add(new CodeVariableElement(Set.of(FINAL), parent.abstractBytecodeNode.asType(), "bytecode"));
                args.add(new CodeVariableElement(Set.of(FINAL), type(byte[].class), "bytecodes"));
                args.add(new CodeVariableElement(Set.of(FINAL), types.Node, "singleton"));
                args.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "bytecodeIndex"));
                break;
            default:
                args.add(new CodeVariableElement(Set.of(FINAL), parent.abstractBytecodeNode.asType(), "bytecode"));
                args.add(new CodeVariableElement(Set.of(FINAL), type(byte[].class), "bytecodes"));
                break;
        }
        return args;
    }

    final class ArgumentDescriptorElement extends CodeTypeElement {

        private final Map<DescriptorData, CodeVariableElement> descriptors = new HashMap<>();

        ArgumentDescriptorElement() {
            super(Set.of(PRIVATE, STATIC, FINAL),
                            ElementKind.CLASS, null, "ArgumentDescriptorImpl");
            this.setSuperClass(InstructionsElement.this.types.InstructionDescriptor_ArgumentDescriptor);
            this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), InstructionsElement.this.type(String.class), "name"));
            this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), InstructionsElement.this.types.Instruction_Argument_Kind, "kind"));
            this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), InstructionsElement.this.type(int.class), "length"));
            CodeExecutableElement constructor = this.add(createConstructorUsingFields(Set.of(), this, null));
            CodeTree tree = constructor.getBodyTree();
            CodeTreeBuilder b = constructor.createBuilder();
            b.startStatement().startSuperCall().staticReference(InstructionsElement.this.parent.bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
            b.tree(tree);

            this.add(createGetName());
            this.add(createGetLength());
            this.add(createGetKind());
        }

        record DescriptorData(String name, String descriptorKind, ImmediateWidth width) {
            DescriptorData(InstructionImmediate immediate) {
                this(getIntrospectionArgumentName(immediate), getArgumentDescriptorKind(immediate.kind()), immediate.encoding().width());
            }

            int byteSize() {
                return width == null ? 0 : width.byteSize;
            }

            String constantName() {
                return ElementUtils.createConstantName(name) + "_" + descriptorKind + "_" + byteSize();
            }
        }

        private static String getIntrospectionArgumentName(InstructionImmediate immediate) {
            if (immediate.kind() == ImmediateKind.FRAME_INDEX) {
                // We expose the frame_index as a local offset, so don't use the immediate name.
                return "local_offset";
            }
            return immediate.name();
        }

        private CodeTree readArgumentConstant(InstructionImmediate immediate) {
            CodeVariableElement constant = descriptors.computeIfAbsent(new ArgumentDescriptorElement.DescriptorData(immediate), (d) -> {
                return createArgumentConstant(d);
            });
            return CodeTreeBuilder.createBuilder().staticReference(asType(), constant.getName()).build();
        }

        private CodeVariableElement createArgumentConstant(DescriptorData data) {
            CodeVariableElement constant = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), asType(), data.constantName());
            constant.createInitBuilder().startNew(asType()).doubleQuote(data.name).staticReference(InstructionsElement.this.types.Instruction_Argument_Kind, data.descriptorKind).string(
                            data.byteSize()).end();
            this.add(constant);
            return constant;
        }

        private CodeExecutableElement createGetName() {
            CodeExecutableElement ex = GeneratorUtils.override(InstructionsElement.this.types.InstructionDescriptor_ArgumentDescriptor, "getName");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return this.name");
            return ex;
        }

        private CodeExecutableElement createGetLength() {
            CodeExecutableElement ex = GeneratorUtils.override(InstructionsElement.this.types.InstructionDescriptor_ArgumentDescriptor, "getLength");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return this.length");
            return ex;
        }

        private CodeExecutableElement createGetKind() {
            CodeExecutableElement ex = GeneratorUtils.override(InstructionsElement.this.types.InstructionDescriptor_ArgumentDescriptor, "getKind");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return this.kind");
            return ex;
        }

    }

    final class AbstractArgumentElement extends CodeTypeElement {

        AbstractArgumentElement() {
            super(Set.of(PRIVATE, SEALED, STATIC, ABSTRACT),
                            ElementKind.CLASS, null, "AbstractArgument");
            this.setSuperClass(InstructionsElement.this.types.Instruction_Argument);
            this.add(new CodeVariableElement(Set.of(FINAL), argumentDescriptorImpl.asType(), "descriptor"));
            this.add(new CodeVariableElement(Set.of(FINAL), InstructionsElement.this.type(int.class), "bci"));
            CodeExecutableElement constructor = this.add(createConstructorUsingFields(Set.of(), this, null));
            CodeTree tree = constructor.getBodyTree();
            CodeTreeBuilder b = constructor.createBuilder();
            b.startStatement().startSuperCall().staticReference(InstructionsElement.this.parent.bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
            b.tree(tree);

            this.add(new CodeVariableElement(Set.of(PROTECTED, STATIC, FINAL), InstructionsElement.this.types.BytecodeDSLAccess, "SAFE_ACCESS")) //
                            .createInitBuilder().tree(parent.createFastAccessFieldInitializer(false));
            this.add(new CodeVariableElement(Set.of(PROTECTED, STATIC, FINAL), InstructionsElement.this.types.ByteArraySupport, "SAFE_BYTES")) //
                            .createInitBuilder().startCall("SAFE_ACCESS.getByteArraySupport").end();
            this.add(createGetDescriptor());
        }

        private CodeExecutableElement createGetDescriptor() {
            CodeExecutableElement ex = GeneratorUtils.override(InstructionsElement.this.types.Instruction_Argument, "getDescriptor");
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return this.descriptor");
            return ex;
        }

    }

    final class ArgumentElement extends CodeTypeElement {

        ArgumentElement(ImmediateKind immediateKind) {
            super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, getImmediateClassName(immediateKind));
            this.setSuperClass(abstractArgument.asType());
            this.addAll(createImmediateArguments(immediateKind));
            this.add(createConstructorUsingFields(Set.of(), this));

            switch (immediateKind) {
                case BYTECODE_INDEX:
                    this.add(createAsBytecodeIndex());
                    break;
                case SHORT:
                case LOCAL_ROOT:
                case STACK_POINTER:
                    this.add(createAsInteger());
                    break;
                case FRAME_INDEX:
                    this.add(createAsLocalOffset());
                    break;
                case LOCAL_INDEX:
                    this.add(createAsLocalIndex());
                    break;
                case CONSTANT:
                    this.add(createAsConstant());
                    break;
                case CONSTANT_LONG:
                case CONSTANT_DOUBLE:
                case CONSTANT_INT:
                case CONSTANT_FLOAT:
                case CONSTANT_SHORT:
                case CONSTANT_CHAR:
                case CONSTANT_BYTE:
                case CONSTANT_BOOL:
                    this.add(createAsConstantInlined(immediateKind));
                    break;
                case NODE_PROFILE:
                    this.add(createAsCachedNode());
                    this.add(createGetSpecializationInfoInternal());
                    break;
                case BRANCH_PROFILE:
                    this.add(createAsBranchProfile());
                    break;
                case TAG_NODE:
                    this.add(createAsTagNode());
                    break;
                default:
                    throw new AssertionError("Unexpected kind");
            }
        }

        private static String readByteSafe(String array, String index) {
            return String.format("SAFE_BYTES.getByte(%s, %s)", array, index);
        }

        private static String readShortSafe(String array, String index) {
            return String.format("SAFE_BYTES.getShort(%s, %s)", array, index);
        }

        private static String readIntSafe(String array, String index) {
            return String.format("SAFE_BYTES.getInt(%s, %s)", array, index);
        }

        private static String readLongSafe(String array, String index) {
            return String.format("SAFE_BYTES.getLong(%s, %s)", array, index);
        }

        private static String readConstSafe(String index) {
            return String.format("SAFE_ACCESS.readObject(constants, %s)", index);
        }

        private CodeExecutableElement createAsBytecodeIndex() {
            CodeExecutableElement ex = GeneratorUtils.override(InstructionsElement.this.types.Instruction_Argument, "asBytecodeIndex");
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(InstructionsElement.this.type(byte[].class), "bc", "this.bytecodes");
            b.startReturn();

            b.string(readIntSafe("bc", "bci"));
            b.end();
            return ex;
        }

        private CodeExecutableElement createAsInteger() {
            CodeExecutableElement ex = GeneratorUtils.override(InstructionsElement.this.types.Instruction_Argument, "asInteger");
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(InstructionsElement.this.type(int.class), "width", "this.getDescriptor().getLength()");
            b.declaration(InstructionsElement.this.type(byte[].class), "bc", "this.bytecodes");
            b.startSwitch().string("width").end().startBlock();
            b.startCase().string("1").end();
            b.startCaseBlock().startReturn().string(readByteSafe("bc", "bci")).end(2);
            b.startCase().string("2").end();
            b.startCaseBlock().startReturn().string(readShortSafe("bc", "bci")).end(2);
            b.startCase().string("4").end();
            b.startCaseBlock().startReturn().string(readIntSafe("bc", "bci")).end(2);
            b.caseDefault().startCaseBlock();
            BytecodeRootNodeElement.emitThrowAssertionError(b, "\"Unexpected integer width \" + width");
            b.end();
            b.end(); // switch
            return ex;
        }

        private CodeExecutableElement createAsLocalOffset() {
            CodeExecutableElement ex = GeneratorUtils.override(InstructionsElement.this.types.Instruction_Argument, "asLocalOffset");
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(InstructionsElement.this.type(byte[].class), "bc", "this.bytecodes");
            b.startReturn();
            if (ImmediateKind.FRAME_INDEX.width != ImmediateWidth.SHORT) {
                throw new AssertionError("encoding changed");
            }
            b.string(readShortSafe("bc", "bci")).string(" - USER_LOCALS_START_INDEX");
            b.end();
            return ex;
        }

        private CodeExecutableElement createAsLocalIndex() {
            CodeExecutableElement ex = GeneratorUtils.override(InstructionsElement.this.types.Instruction_Argument, "asLocalIndex");
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(InstructionsElement.this.type(byte[].class), "bc", "this.bytecodes");
            b.startReturn();
            if (ImmediateKind.LOCAL_INDEX.width != ImmediateWidth.SHORT) {
                throw new AssertionError("encoding changed");
            }
            b.string(readShortSafe("bc", "bci"));
            b.end();
            return ex;
        }

        private CodeExecutableElement createAsConstant() {
            CodeExecutableElement ex = GeneratorUtils.override(InstructionsElement.this.types.Instruction_Argument, "asConstant");
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(InstructionsElement.this.type(byte[].class), "bc", "this.bytecodes");
            b.startReturn();
            if (ImmediateKind.CONSTANT.width != ImmediateWidth.INT) {
                throw new AssertionError("encoding changed");
            }
            b.string(readConstSafe(readIntSafe("bc", "bci")));
            b.end();
            return ex;
        }

        private CodeExecutableElement createGetSpecializationInfoInternal() {
            CodeExecutableElement ex = GeneratorUtils.override(InstructionsElement.this.types.Instruction_Argument, "getSpecializationInfoInternal");
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();

            if (InstructionsElement.this.parent.model.enableSpecializationIntrospection) {
                b.declaration(InstructionsElement.this.types.Node, "node", "asCachedNode()");
                b.startIf().startStaticCall(InstructionsElement.this.types.Introspection, "isIntrospectable").string("node").end().end().startBlock();
                b.startReturn();
                b.startStaticCall(InstructionsElement.this.types.Introspection, "getSpecializations").string("this.bytecode").string("this.bytecodeIndex").string("node").end();
                b.end(); // return
                b.end();
            }
            b.returnNull();
            return ex;
        }

        private CodeExecutableElement createAsConstantInlined(ImmediateKind kind) {
            CodeExecutableElement ex = GeneratorUtils.override(InstructionsElement.this.types.Instruction_Argument, "asConstant");
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(InstructionsElement.this.type(byte[].class), "bc", "this.bytecodes");
            b.startReturn();
            CodeTree read = CodeTreeBuilder.singleString(switch (kind.width) {
                case BYTE -> readByteSafe("bc", "bci");
                case SHORT -> readShortSafe("bc", "bci");
                case INT -> readIntSafe("bc", "bci");
                case LONG -> readLongSafe("bc", "bci");
            });
            b.tree(InstructionsElement.this.parent.decodeInlinedConstant(kind, read));
            b.end();
            return ex;
        }

        private CodeExecutableElement createAsCachedNode() {
            CodeExecutableElement ex = GeneratorUtils.override(InstructionsElement.this.types.Instruction_Argument, "asCachedNode");
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("this.bytecode == null").end().startBlock();
            b.returnNull();
            b.end();

            // we need to check this explicitly as we do not want to return the singleton node
            // for uncached
            b.startIf().string("this.bytecode.getTier() != ").staticReference(InstructionsElement.this.types.BytecodeTier, "CACHED").end().startBlock();
            b.returnNull();
            b.end();

            b.startIf().string("this.singleton != null").end().startBlock();
            b.startReturn().string("this.singleton").end();
            b.end();

            b.declaration(arrayOf(InstructionsElement.this.types.Node), "cachedNodes", "this.bytecode.getCachedNodes()");
            b.startIf().string("cachedNodes == null").end().startBlock();
            b.statement("return null");
            b.end();
            b.declaration(InstructionsElement.this.type(byte[].class), "bc", "this.bytecodes");
            b.startReturn();
            if (ImmediateKind.NODE_PROFILE.width != ImmediateWidth.INT) {
                throw new AssertionError("encoding changed");
            }
            b.string("cachedNodes[", readIntSafe("bc", "bci"), "]");
            b.end();
            return ex;
        }

        private CodeExecutableElement createAsTagNode() {
            CodeExecutableElement ex = GeneratorUtils.override(InstructionsElement.this.types.Instruction_Argument, "asTagNode");
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("this.bytecode == null").end().startBlock();
            b.returnNull();
            b.end();

            b.declaration(InstructionsElement.this.type(byte[].class), "bc", "this.bytecodes");
            b.declaration(InstructionsElement.this.parent.tagRootNode.asType(), "tagRoot", "this.bytecode.tagRoot");
            b.startIf().string("tagRoot == null").end().startBlock();
            b.statement("return null");
            b.end();
            b.startReturn();
            if (ImmediateKind.TAG_NODE.width != ImmediateWidth.INT) {
                throw new AssertionError("encoding changed");
            }
            b.tree(BytecodeRootNodeElement.readTagNodeSafe(CodeTreeBuilder.singleString(readIntSafe("bc", "bci"))));
            b.end();
            return ex;
        }

        private CodeExecutableElement createAsBranchProfile() {
            CodeExecutableElement ex = GeneratorUtils.override(InstructionsElement.this.types.Instruction_Argument, "asBranchProfile");
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("this.bytecode == null").end().startBlock();
            b.returnNull();
            b.end();

            b.declaration(InstructionsElement.this.type(byte[].class), "bc", "this.bytecodes");
            if (ImmediateKind.BRANCH_PROFILE.width != ImmediateWidth.INT) {
                throw new AssertionError("encoding changed");
            }
            b.declaration(InstructionsElement.this.type(int.class), "index", readIntSafe("bc", "bci"));
            b.declaration(InstructionsElement.this.type(int[].class), "profiles", "this.bytecode.getBranchProfiles()");
            b.startIf().string("profiles == null").end().startBlock();

            b.startReturn();
            b.startNew(InstructionsElement.this.types.Instruction_Argument_BranchProfile);
            b.string("index");
            b.string("0");
            b.string("0");
            b.end(); // new
            b.end(); // return

            b.end(); // block
            b.startReturn();
            b.startNew(InstructionsElement.this.types.Instruction_Argument_BranchProfile);
            b.string("index");
            b.string("profiles[index * 2]");
            b.string("profiles[index * 2 + 1]");
            b.end();
            b.end();
            return ex;
        }

    }

}
