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

import static com.oracle.truffle.dsl.processor.java.ElementUtils.isPrimitive;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.typeEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.DeclaredCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.WildcardTypeMirror;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.Template;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;
import com.oracle.truffle.dsl.processor.operations.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.operations.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.operations.model.OperationModel.OperationKind;

public class OperationsModel extends Template implements InfoDumpable {

    private final ProcessorContext context;
    public final TypeElement templateType;

    public OperationsModel(ProcessorContext context, TypeElement templateType, AnnotationMirror mirror) {
        super(context, templateType, mirror);
        this.context = context;
        this.templateType = templateType;
    }

    private int operationId = 1;
    private int instructionId = 1;

    private final List<OperationModel> operations = new ArrayList<>();
    private final List<InstructionModel> instructions = new ArrayList<>();

    private final Map<String, OperationModel> operationNames = new HashMap<>();

    public boolean enableYield;
    public boolean enableSerialization = true;
    public boolean allowUnsafe;
    public DeclaredType languageClass;
    public ExecutableElement fdConstructor;
    public ExecutableElement fdBuilderConstructor;
    public boolean enableBaselineInterpreter;
    public TypeSystemData typeSystem;
    public Set<TypeMirror> boxingEliminatedTypes;
    public List<VariableElement> serializedFields;

    public boolean enableTracing;
    public String decisionsFilePath;
    public boolean enableOptimizations;
    public OptimizationDecisionsModel optimizationDecisions;

    public OperationModel blockOperation;
    public OperationModel rootOperation;

    public InstructionModel popInstruction;
    public InstructionModel branchInstruction;
    public InstructionModel branchBackwardInstruction;
    public InstructionModel branchFalseInstruction;
    public InstructionModel throwInstruction;
    public InstructionModel yieldInstruction;
    public InstructionModel[] popVariadicInstruction;
    public InstructionModel mergeVariadicInstruction;
    public InstructionModel storeNullInstruction;

    public List<TypeMirror> getProvidedTags() {
        AnnotationMirror providedTags = ElementUtils.findAnnotationMirror(ElementUtils.castTypeElement(languageClass), types.ProvidedTags);
        if (providedTags == null) {
            return Collections.emptyList();
        }
        return ElementUtils.getAnnotationValueList(TypeMirror.class, providedTags, "value");
    }

    public void addDefault() {
        popInstruction = instruction(InstructionKind.POP, "pop");
        branchInstruction = instruction(InstructionKind.BRANCH, "branch").addImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target");
        branchBackwardInstruction = instruction(InstructionKind.BRANCH_BACKWARD, "branch.backward").addImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target");
        branchFalseInstruction = instruction(InstructionKind.BRANCH_FALSE, "branch.false").addImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target");
        throwInstruction = instruction(InstructionKind.THROW, "throw").addImmediate(ImmediateKind.INTEGER, "exception_local");

        blockOperation = operation(OperationKind.BLOCK, "Block") //
                        .setTransparent(true) //
                        .setVariadic(0) //
                        .setChildrenMustBeValues(false);
        rootOperation = operation(OperationKind.ROOT, "Root") //
                        .setVariadic(0) //
                        .setVoid(true) //
                        .setChildrenMustBeValues(false) //
                        .setOperationArguments(types.TruffleLanguage);
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
                        .setOperationArguments(types.OperationLocal);
        operation(OperationKind.FINALLY_TRY, "FinallyTry") //
                        .setVoid(true) //
                        .setNumChildren(2) //
                        .setChildrenMustBeValues(false, false);
        operation(OperationKind.FINALLY_TRY_NO_EXCEPT, "FinallyTryNoExcept") //
                        .setVoid(true) //
                        .setNumChildren(2) //
                        .setChildrenMustBeValues(false, false);
        operation(OperationKind.LABEL, "Label") //
                        .setVoid(true) //
                        .setNumChildren(0) //
                        .setOperationArguments(types.OperationLabel);
        operation(OperationKind.BRANCH, "Branch") //
                        .setVoid(true) //
                        .setNumChildren(0) //
                        .setOperationArguments(types.OperationLabel) //
                        .setInstruction(branchInstruction);
        operation(OperationKind.LOAD_CONSTANT, "LoadConstant") //
                        .setNumChildren(0) //
                        .setOperationArguments(context.getType(Object.class)) //
                        .setInstruction(instruction(InstructionKind.LOAD_CONSTANT, "load.constant").addImmediate(ImmediateKind.CONSTANT, "constant"));
        operation(OperationKind.LOAD_ARGUMENT, "LoadArgument") //
                        .setNumChildren(0) //
                        .setOperationArguments(context.getType(int.class)) //
                        .setInstruction(instruction(InstructionKind.LOAD_ARGUMENT, "load.argument").addImmediate(ImmediateKind.INTEGER, "index"));
        operation(OperationKind.LOAD_LOCAL, "LoadLocal") //
                        .setNumChildren(0) //
                        .setOperationArguments(types.OperationLocal) //
                        .setInstruction(instruction(InstructionKind.LOAD_LOCAL, "load.local").addImmediate(ImmediateKind.INTEGER, "index"));
        operation(OperationKind.LOAD_LOCAL_MATERIALIZED, "LoadLocalMaterialized") //
                        .setNumChildren(1) //
                        .setChildrenMustBeValues(true) //
                        .setOperationArguments(types.OperationLocal) //
                        .setInstruction(instruction(InstructionKind.LOAD_LOCAL_MATERIALIZED, "load.local.mat").addImmediate(ImmediateKind.INTEGER, "index"));
        operation(OperationKind.STORE_LOCAL, "StoreLocal") //
                        .setNumChildren(1) //
                        .setChildrenMustBeValues(true) //
                        .setVoid(true) //
                        .setOperationArguments(types.OperationLocal) //
                        .setInstruction(instruction(InstructionKind.STORE_LOCAL, "store.local").addImmediate(ImmediateKind.INTEGER, "index"));
        operation(OperationKind.STORE_LOCAL_MATERIALIZED, "StoreLocalMaterialized") //
                        .setNumChildren(2) //
                        .setChildrenMustBeValues(true, true) //
                        .setVoid(true) //
                        .setOperationArguments(types.OperationLocal) //
                        .setInstruction(instruction(InstructionKind.STORE_LOCAL_MATERIALIZED, "store.local.mat").addImmediate(ImmediateKind.INTEGER, "index"));
        operation(OperationKind.RETURN, "Return") //
                        .setNumChildren(1) //
                        .setChildrenMustBeValues(true) //
                        .setInstruction(instruction(InstructionKind.RETURN, "return"));
        if (enableYield) {
            yieldInstruction = instruction(InstructionKind.YIELD, "yield").addImmediate(ImmediateKind.CONSTANT, "location");
            operation(OperationKind.YIELD, "Yield") //
                            .setNumChildren(1) //
                            .setChildrenMustBeValues(true) //
                            .setInstruction(yieldInstruction);
        }

        operation(OperationKind.SOURCE, "Source") //
                        .setNumChildren(1) //
                        .setTransparent(true) //
                        .setOperationArguments(types.Source);
        operation(OperationKind.SOURCE_SECTION, "SourceSection") //
                        .setNumChildren(1) //
                        .setTransparent(true) //
                        .setOperationArguments(context.getType(int.class), context.getType(int.class));
        operation(OperationKind.INSTRUMENT_TAG, "Tag") //
                        .setNumChildren(1) //
                        .setTransparent(true) //
                        .setOperationArguments(generic(context.getDeclaredType(Class.class), new WildcardTypeMirror(types.Tag, null)));

        popVariadicInstruction = new InstructionModel[9];
        for (int i = 0; i <= 8; i++) {
            popVariadicInstruction[i] = instruction(InstructionKind.LOAD_VARIADIC, "store.variadic_" + i);
            popVariadicInstruction[i].variadicPopCount = i;
        }
        mergeVariadicInstruction = instruction(InstructionKind.MERGE_VARIADIC, "merge.variadic");
        storeNullInstruction = instruction(InstructionKind.STORE_NULL, "store.variadic_end");
    }

    private static TypeMirror generic(DeclaredType el, TypeMirror... args) {
        return new DeclaredCodeTypeMirror((TypeElement) el.asElement(), List.of(args));
    }

    public OperationModel operation(OperationKind kind, String name) {
        return operation(null, kind, name);
    }

    public OperationModel operation(TypeElement template, OperationKind kind, String name) {
        OperationModel op = new OperationModel(this, template, operationId++, kind, name);
        operations.add(op);
        operationNames.put(name, op);
        return op;
    }

    public InstructionModel instruction(InstructionKind kind, String name) {
        InstructionModel instr = new InstructionModel(instructionId++, kind, name);
        instructions.add(instr);
        return instr;
    }

    @Override
    public Element getMessageElement() {
        return templateType;
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        return Collections.unmodifiableList(operations);
    }

    public boolean isBoxingEliminated(TypeMirror mirror) {
        if (!isPrimitive(mirror)) {
            return false;
        }

        for (TypeMirror mir : boxingEliminatedTypes) {
            if (typeEquals(mir, mirror)) {
                return true;
            }
        }

        return false;
    }

    public List<OperationModel> getOperations() {
        return Collections.unmodifiableList(operations);
    }

    public List<InstructionModel> getInstructions() {
        return Collections.unmodifiableList(instructions);
    }

    public InstructionModel getInstructionByName(String name) {
        for (InstructionModel instr : instructions) {
            if (instr.name.equals(name)) {
                return instr;
            }
        }
        return null;
    }

    public void dump(Dumper dumper) {
        dumper.field("operations", operations);
        dumper.field("instructions", instructions);
    }

    public boolean hasBoxingElimination() {
        return !boxingEliminatedTypes.isEmpty();
    }

}
