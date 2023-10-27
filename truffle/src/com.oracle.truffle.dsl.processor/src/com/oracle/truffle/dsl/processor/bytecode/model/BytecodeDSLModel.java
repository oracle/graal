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

import static com.oracle.truffle.dsl.processor.java.ElementUtils.isPrimitive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.Signature;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationKind;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.Template;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;

public class BytecodeDSLModel extends Template implements PrettyPrintable {

    private final ProcessorContext context;
    public final TypeElement templateType;
    private final String suffix;

    public BytecodeDSLModel(ProcessorContext context, TypeElement templateType, AnnotationMirror mirror, String suffix) {
        super(context, templateType, mirror);
        this.context = context;
        this.templateType = templateType;
        this.suffix = suffix;
    }

    private int operationId = 1;

    private final LinkedHashMap<String, OperationModel> operations = new LinkedHashMap<>();
    /*
     * All regular (not short-circuit) custom operations, indexed by the underlying TypeElement.
     *
     * This mapping is used to ensure we only instantiate an operation once for any given
     * TypeElement. When we instantiate short-circuit operations, we create another operation for
     * the booleanConverter class; if the same converter is used multiple times (or the converter is
     * itself declared as an operation), we should create just a single operation for all usages.
     */
    private final HashMap<TypeElement, CustomOperationModel> customRegularOperations = new HashMap<>();
    private final List<CustomOperationModel> customShortCircuitOperations = new ArrayList<>();
    private LinkedHashMap<String, InstructionModel> instructions = new LinkedHashMap<>();

    public DeclaredType languageClass;
    public boolean enableUncachedInterpreter;
    public boolean enableSerialization;
    public boolean enableQuickening;
    public boolean allowUnsafe;
    public boolean enableYield;
    public boolean storeBciInFrame;
    public boolean specializationDebugListener;

    public ExecutableElement fdConstructor;
    public ExecutableElement fdBuilderConstructor;
    public ExecutableElement executeProlog;
    public ExecutableElement executeEpilog;

    public TypeSystemData typeSystem;
    public Set<TypeMirror> boxingEliminatedTypes = Set.of();
    public List<VariableElement> serializedFields;

    public boolean enableTracing;
    public String decisionsFilePath;
    public OptimizationDecisionsModel optimizationDecisions;

    public OperationModel blockOperation;
    public OperationModel rootOperation;

    public InstructionModel popInstruction;
    public InstructionModel dupInstruction;
    public InstructionModel trapInstruction;
    public InstructionModel returnInstruction;
    public InstructionModel branchInstruction;
    public InstructionModel branchBackwardInstruction;
    public InstructionModel branchFalseInstruction;
    public InstructionModel throwInstruction;
    public InstructionModel yieldInstruction;
    public InstructionModel[] popVariadicInstruction;
    public InstructionModel mergeVariadicInstruction;
    public InstructionModel storeNullInstruction;

    public String getName() {
        return templateType.getSimpleName() + suffix;
    }

    public List<TypeMirror> getProvidedTags() {
        AnnotationMirror providedTags = ElementUtils.findAnnotationMirror(ElementUtils.castTypeElement(languageClass), types.ProvidedTags);
        if (providedTags == null) {
            return Collections.emptyList();
        }
        return ElementUtils.getAnnotationValueList(TypeMirror.class, providedTags, "value");
    }

    private Signature signature(Class<?> returnType, Class<?>... argumentTypes) {
        TypeMirror[] arguments = new TypeMirror[argumentTypes.length];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = context.getType(argumentTypes[i]);
        }
        return new Signature(context.getType(returnType), List.of(arguments));
    }

    public void addDefault() {
        popInstruction = instruction(InstructionKind.POP, "pop", signature(void.class));
        dupInstruction = instruction(InstructionKind.DUP, "dup", signature(void.class));
        trapInstruction = instruction(InstructionKind.TRAP, "trap", signature(void.class));
        returnInstruction = instruction(InstructionKind.RETURN, "return", signature(void.class, Object.class));
        branchInstruction = instruction(InstructionKind.BRANCH, "branch", signature(void.class)) //
                        .addImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target");
        branchBackwardInstruction = instruction(InstructionKind.BRANCH_BACKWARD, "branch.backward", signature(void.class)) //
                        .addImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target");
        branchFalseInstruction = instruction(InstructionKind.BRANCH_FALSE, "branch.false", signature(void.class, Object.class)) //
                        .addImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target") //
                        .addImmediate(ImmediateKind.PROFILE, "branch_profile");
        throwInstruction = instruction(InstructionKind.THROW, "throw", signature(void.class, Object.class)) //
                        .addImmediate(ImmediateKind.INTEGER, "exception_local");

        blockOperation = operation(OperationKind.BLOCK, "Block") //
                        .setTransparent(true) //
                        .setVariadic(0) //
                        .setChildrenMustBeValues(false);
        rootOperation = operation(OperationKind.ROOT, "Root") //
                        .setVariadic(0) //
                        .setVoid(true) //
                        .setChildrenMustBeValues(false) //
                        .setOperationArgumentTypes(types.TruffleLanguage) //
                        .setOperationArgumentNames("language");
        operation(OperationKind.IF_THEN, "IfThen") //
                        .setVoid(true) //
                        .setNumChildren(2) //
                        .setChildrenMustBeValues(true, false);
        operation(OperationKind.IF_THEN_ELSE, "IfThenElse") //
                        .setVoid(true) //
                        .setNumChildren(3) //
                        .setChildrenMustBeValues(true, false, false);
        operation(OperationKind.CONDITIONAL, "Conditional") //
                        .setNumChildren(3) //
                        .setChildrenMustBeValues(true, true, true);
        operation(OperationKind.WHILE, "While") //
                        .setVoid(true) //
                        .setNumChildren(2) //
                        .setChildrenMustBeValues(true, false);
        operation(OperationKind.TRY_CATCH, "TryCatch") //
                        .setVoid(true) //
                        .setNumChildren(2) //
                        .setChildrenMustBeValues(false, false) //
                        .setOperationArgumentTypes(types.BytecodeLocal) //
                        .setOperationArgumentNames("exceptionLocal");
        operation(OperationKind.FINALLY_TRY, "FinallyTry") //
                        .setVoid(true) //
                        .setNumChildren(2) //
                        .setChildrenMustBeValues(false, false) //
                        .setOperationArgumentTypes(types.BytecodeLocal) //
                        .setOperationArgumentNames("exceptionLocal");
        operation(OperationKind.FINALLY_TRY_NO_EXCEPT, "FinallyTryNoExcept") //
                        .setVoid(true) //
                        .setNumChildren(2) //
                        .setChildrenMustBeValues(false, false);
        operation(OperationKind.LABEL, "Label") //
                        .setVoid(true) //
                        .setNumChildren(0) //
                        .setOperationArgumentTypes(types.BytecodeLabel) //
                        .setOperationArgumentNames("label");
        operation(OperationKind.BRANCH, "Branch") //
                        .setVoid(true) //
                        .setNumChildren(0) //
                        .setOperationArgumentTypes(types.BytecodeLabel) //
                        .setOperationArgumentNames("label") //
                        .setInstruction(branchInstruction);
        operation(OperationKind.LOAD_CONSTANT, "LoadConstant") //
                        .setNumChildren(0) //
                        .setOperationArgumentTypes(context.getType(Object.class)) //
                        .setOperationArgumentNames("constant") //
                        .setInstruction(instruction(InstructionKind.LOAD_CONSTANT, "load.constant", signature(Object.class)) //
                                        .addImmediate(ImmediateKind.CONSTANT, "constant"));
        operation(OperationKind.LOAD_ARGUMENT, "LoadArgument") //
                        .setNumChildren(0) //
                        .setOperationArgumentTypes(context.getType(int.class)) //
                        .setOperationArgumentNames("index") //
                        .setInstruction(instruction(InstructionKind.LOAD_ARGUMENT, "load.argument", signature(Object.class))//
                                        .addImmediate(ImmediateKind.INTEGER, "index"));
        operation(OperationKind.LOAD_LOCAL, "LoadLocal") //
                        .setNumChildren(0) //
                        .setOperationArgumentTypes(types.BytecodeLocal) //
                        .setOperationArgumentNames("local") //
                        .setInstruction(instruction(InstructionKind.LOAD_LOCAL, "load.local", signature(Object.class)) //
                                        .addImmediate(ImmediateKind.INTEGER, "index"));
        operation(OperationKind.LOAD_LOCAL_MATERIALIZED, "LoadLocalMaterialized") //
                        .setNumChildren(1) //
                        .setChildrenMustBeValues(true) //
                        .setOperationArgumentTypes(types.BytecodeLocal) //
                        .setOperationArgumentNames("local") //
                        .setInstruction(instruction(InstructionKind.LOAD_LOCAL_MATERIALIZED, "load.local.mat", signature(Object.class, Object.class)) //
                                        .addImmediate(ImmediateKind.INTEGER, "index"));
        operation(OperationKind.STORE_LOCAL, "StoreLocal") //
                        .setNumChildren(1) //
                        .setChildrenMustBeValues(true) //
                        .setVoid(true) //
                        .setOperationArgumentTypes(types.BytecodeLocal) //
                        .setOperationArgumentNames("local") //
                        .setInstruction(instruction(InstructionKind.STORE_LOCAL, "store.local", signature(void.class, Object.class)) //
                                        .addImmediate(ImmediateKind.INTEGER, "index"));
        operation(OperationKind.STORE_LOCAL_MATERIALIZED, "StoreLocalMaterialized") //
                        .setNumChildren(2) //
                        .setChildrenMustBeValues(true, true) //
                        .setVoid(true) //
                        .setOperationArgumentTypes(types.BytecodeLocal) //
                        .setOperationArgumentNames("local") //
                        .setInstruction(instruction(InstructionKind.STORE_LOCAL_MATERIALIZED, "store.local.mat",
                                        signature(void.class, Object.class, Object.class)) //
                                                        .addImmediate(ImmediateKind.INTEGER, "index"));
        operation(OperationKind.RETURN, "Return") //
                        .setNumChildren(1) //
                        .setChildrenMustBeValues(true) //
                        .setInstruction(returnInstruction);
        if (enableYield) {
            yieldInstruction = instruction(InstructionKind.YIELD, "yield", signature(void.class, Object.class)).addImmediate(ImmediateKind.CONSTANT, "location");
            operation(OperationKind.YIELD, "Yield") //
                            .setNumChildren(1) //
                            .setChildrenMustBeValues(true) //
                            .setInstruction(yieldInstruction);
        }
        operation(OperationKind.SOURCE, "Source") //
                        .setNumChildren(1) //
                        .setTransparent(true) //
                        .setOperationArgumentTypes(types.Source) //
                        .setOperationArgumentNames("source");
        operation(OperationKind.SOURCE_SECTION, "SourceSection") //
                        .setNumChildren(1) //
                        .setTransparent(true) //
                        .setOperationArgumentTypes(context.getType(int.class), context.getType(int.class)) //
                        .setOperationArgumentNames("index", "length");
        operation(OperationKind.INSTRUMENT_TAG, "Tag") //
                        .setNumChildren(1) //
                        .setTransparent(true) //
                        .setOperationArgumentVarArgs(true) //
                        .setOperationArgumentTypes(array(context.getDeclaredType(Class.class))) //
                        .setOperationArgumentNames("tags");

        popVariadicInstruction = new InstructionModel[9];
        for (int i = 0; i <= 8; i++) {
            popVariadicInstruction[i] = instruction(InstructionKind.LOAD_VARIADIC, "load.variadic_" + i, signature(void.class, Object.class));
            popVariadicInstruction[i].variadicPopCount = i;
        }
        mergeVariadicInstruction = instruction(InstructionKind.MERGE_VARIADIC, "merge.variadic", signature(Object.class, Object.class));
        storeNullInstruction = instruction(InstructionKind.STORE_NULL, "store.variadic_end", signature(Object.class));
    }

    private static TypeMirror array(TypeMirror el) {
        return new ArrayCodeTypeMirror(el);
    }

    public OperationModel operation(OperationKind kind, String name) {
        if (operations.containsKey(name)) {
            addError("Multiple operations declared with name %s. Operation names must be distinct.", name);
            return null;
        }
        OperationModel op = new OperationModel(this, operationId++, kind, name);
        operations.put(name, op);
        return op;
    }

    public CustomOperationModel customRegularOperation(OperationKind kind, String name, TypeElement typeElement, AnnotationMirror mirror) {
        OperationModel op = operation(kind, name);
        if (op == null) {
            return null;
        }
        CustomOperationModel customOp = new CustomOperationModel(context, this, typeElement, mirror, op);
        if (customRegularOperations.containsKey(typeElement)) {
            throw new AssertionError(String.format("Type element %s was used to instantiate more than one operation. This is a bug.", typeElement));
        }
        customRegularOperations.put(typeElement, customOp);

        return customOp;
    }

    public CustomOperationModel customShortCircuitOperation(OperationKind kind, String name, AnnotationMirror mirror) {
        OperationModel op = operation(kind, name);
        if (op == null) {
            return null;
        }
        CustomOperationModel customOp = new CustomOperationModel(context, this, null, mirror, op);
        customShortCircuitOperations.add(customOp);

        return customOp;
    }

    public CustomOperationModel getCustomOperationForType(TypeElement typeElement) {
        return customRegularOperations.get(typeElement);
    }

    public InstructionModel quickenInstruction(InstructionModel base, Signature signature, String specializationName) {
        InstructionModel model = instruction(base.kind, base.name + "$" + specializationName, signature, specializationName);
        model.getImmediates().clear();
        model.getImmediates().addAll(base.getImmediates());
        model.filteredSpecializations = base.filteredSpecializations;
        model.nodeData = base.nodeData;
        model.nodeType = base.nodeType;
        model.variadicPopCount = base.variadicPopCount;
        model.quickeningBase = base;
        model.operation = base.operation;
        model.shortCircuitData = base.shortCircuitData;
        base.quickenedInstructions.add(model);
        return model;
    }

    private InstructionModel instruction(InstructionKind kind, String name, Signature signature, String quickeningName) {
        if (instructions.containsKey(name)) {
            throw new AssertionError(String.format("Multiple instructions declared with name %s. Instruction names must be distinct.", name));
        }
        InstructionModel instr = new InstructionModel(kind, name, signature, quickeningName);
        instructions.put(name, instr);
        return instr;
    }

    public InstructionModel instruction(InstructionKind kind, String name, Signature signature) {
        return instruction(kind, name, signature, null);
    }

    public InstructionModel shortCircuitInstruction(String name, boolean continueWhen, boolean returnConvertedValue, InstructionModel booleanConverterInstruction) {
        if (instructions.containsKey(name)) {
            throw new AssertionError(String.format("Multiple instructions declared with name %s. Instruction names must be distinct.", name));
        }
        Signature signature = returnConvertedValue ? signature(Object.class, boolean.class, boolean.class) : signature(boolean.class, boolean.class, boolean.class);
        InstructionModel instr = instruction(InstructionKind.CUSTOM_SHORT_CIRCUIT, name, signature);
        instr.shortCircuitData = new ShortCircuitInstructionData(continueWhen, returnConvertedValue, booleanConverterInstruction);
        return instr;
    }

    @Override
    public Element getMessageElement() {
        return templateType;
    }

    public void finalizeInstructions() {
        LinkedHashMap<String, InstructionModel> newInstructions = new LinkedHashMap<>();

        for (var entry : instructions.entrySet()) {
            String name = entry.getKey();
            InstructionModel instruction = entry.getValue();
            if (instruction.isQuickening()) {
                continue;
            }
            newInstructions.put(name, instruction);
            for (InstructionModel derivedInstruction : instruction.getFlattenedQuickenedInstructions()) {
                newInstructions.put(derivedInstruction.name, derivedInstruction);
            }
        }

        int currentId = 1;
        for (InstructionModel m : newInstructions.values()) {
            m.setId(currentId++);
        }

        this.instructions = newInstructions;
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        ArrayList<MessageContainer> result = new ArrayList<>(customRegularOperations.values());
        result.addAll(customShortCircuitOperations);
        return Collections.unmodifiableList(result);
    }

    public boolean usesBoxingElimination() {
        return !boxingEliminatedTypes.isEmpty();
    }

    public boolean isBoxingEliminated(TypeMirror mirror) {
        if (!isPrimitive(mirror)) {
            return false;
        }
        if (ElementUtils.isVoid(mirror)) {
            return false;
        }
        return boxingEliminatedTypes.contains(mirror);
    }

    public Collection<OperationModel> getOperations() {
        return operations.values();
    }

    public Collection<InstructionModel> getInstructions() {
        return instructions.values();
    }

    public InstructionModel getInstructionByName(String name) {
        return instructions.get(name);
    }

    public boolean needsBciSlot() {
        return enableUncachedInterpreter || storeBciInFrame;
    }

    @Override
    public void pp(PrettyPrinter printer) {
        printer.field("operations", operations.values());
        printer.field("instructions", instructions.values());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + getName() + "]";
    }
}
