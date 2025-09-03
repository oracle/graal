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

import static com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.OPCODE_WIDTH;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getSimpleName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isPrimitive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionImmediate;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationKind;
import com.oracle.truffle.dsl.processor.expression.DSLExpression;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.library.ExportsData;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.Template;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;

public class BytecodeDSLModel extends Template implements PrettyPrintable {

    private final ProcessorContext context;
    public final TypeElement templateType;
    // The generated class.
    public final String modelName;
    // The abstract builder class (different from builderType if GenerateBytecodeTestVariants used)
    public final TypeMirror abstractBuilderType;

    public BytecodeDSLModel(ProcessorContext context, TypeElement templateType, AnnotationMirror mirror, String name,
                    TypeMirror abstractBuilderType) {
        super(context, templateType, mirror);
        this.context = context;
        this.templateType = templateType;
        this.modelName = name;
        this.abstractBuilderType = abstractBuilderType;
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
    private final HashMap<OperationModel, CustomOperationModel> operationsToCustomOperations = new HashMap<>();
    private LinkedHashMap<String, InstructionModel> instructions = new LinkedHashMap<>();
    // instructions indexed by # of short immediates (i.e., their lengths are [2, 4, 6, ...]).
    public InstructionModel[] invalidateInstructions;

    public DeclaredType languageClass;
    public boolean enableUncachedInterpreter;
    public String defaultUncachedThreshold;
    public DSLExpression defaultUncachedThresholdExpression;
    public boolean enableSerialization;
    public boolean enableQuickening;
    public boolean allowUnsafe;
    public boolean enableYield;
    public boolean enableMaterializedLocalAccesses;
    public boolean storeBciInFrame;
    public boolean bytecodeDebugListener;
    public boolean additionalAssertions;
    public boolean enableSpecializationIntrospection;
    public boolean enableTagInstrumentation;
    public boolean enableRootTagging;
    public boolean enableRootBodyTagging;
    public boolean enableBlockScoping;
    public String defaultLocalValue;
    public DSLExpression defaultLocalValueExpression;
    public String variadicStackLimit;
    public DSLExpression variadicStackLimitExpression;

    public ExecutableElement fdConstructor;
    public ExecutableElement fdBuilderConstructor;
    public ExecutableElement interceptControlFlowException;
    public ExecutableElement interceptInternalException;
    public ExecutableElement interceptTruffleException;

    public TypeSystemData typeSystem;
    public Set<TypeMirror> boxingEliminatedTypes = Set.of();
    public List<VariableElement> serializedFields;

    public OperationModel blockOperation;
    public OperationModel rootOperation;
    public OperationModel conditionalOperation;
    public OperationModel whileOperation;
    public OperationModel tryCatchOperation;
    public OperationModel tryFinallyOperation;
    public OperationModel tryCatchOtherwiseOperation;
    public OperationModel finallyHandlerOperation;
    public OperationModel loadConstantOperation;
    public OperationModel loadNullOperation;
    public OperationModel loadLocalOperation;
    public OperationModel loadLocalMaterializedOperation;
    public OperationModel tagOperation;
    public OperationModel storeLocalOperation;
    public OperationModel storeLocalMaterializedOperation;
    public OperationModel ifThenOperation;
    public OperationModel ifThenElseOperation;
    public OperationModel returnOperation;
    public OperationModel sourceSectionPrefixOperation;
    public OperationModel sourceSectionSuffixOperation;
    public OperationModel sourceOperation;
    public CustomOperationModel prolog = null;
    public CustomOperationModel epilogReturn = null;
    public CustomOperationModel epilogExceptional = null;

    public InstructionModel nullInstruction;
    public InstructionModel popInstruction;
    public InstructionModel dupInstruction;
    public InstructionModel returnInstruction;
    public InstructionModel branchInstruction;
    public InstructionModel branchBackwardInstruction;
    public InstructionModel branchFalseInstruction;
    public InstructionModel storeLocalInstruction;
    public InstructionModel throwInstruction;
    public InstructionModel loadConstantInstruction;
    public InstructionModel loadNullInstruction;
    public InstructionModel yieldInstruction;
    public InstructionModel loadVariadicInstruction;
    public InstructionModel splatVariadicInstruction;
    public InstructionModel createVariadicInstruction;
    public InstructionModel emptyVariadicInstruction;
    public InstructionModel tagEnterInstruction;
    public InstructionModel tagLeaveValueInstruction;
    public InstructionModel tagLeaveVoidInstruction;
    public InstructionModel tagYieldInstruction;
    public InstructionModel tagResumeInstruction;
    public InstructionModel clearLocalInstruction;

    public final List<CustomOperationModel> instrumentations = new ArrayList<>();
    public ExportsData tagTreeNodeLibrary;

    /**
     * Whether any custom operation has variadic arguments.
     */
    public boolean hasCustomVariadic;

    /**
     * The maximum variadic offset that was used. <code>maximumVariadicOffset == 0</code> can be
     * used to optimize whether offsets are used at all.
     */
    public int maximumVariadicOffset;

    /**
     * Whether any instruction has a variadic return. {@link #hasVariadicReturn} implies
     * {@link #hasCustomVariadic}.
     */
    public boolean hasVariadicReturn;

    public String getName() {
        return modelName;
    }

    private List<TypeMirror> providedTags;
    private Set<String> providedTagsSet;

    public List<TypeMirror> getProvidedTags() {
        if (providedTags == null) {
            AnnotationMirror mirror = ElementUtils.findAnnotationMirror(ElementUtils.castTypeElement(languageClass), types.ProvidedTags);
            if (mirror == null) {
                providedTags = Collections.emptyList();
            } else {
                providedTags = ElementUtils.getAnnotationValueList(TypeMirror.class, mirror, "value");
            }
        }
        return providedTags;
    }

    public boolean isTagProvided(TypeMirror tagClass) {
        if (providedTagsSet == null) {
            providedTagsSet = getProvidedTags().stream()//
                            .map(ElementUtils::getUniqueIdentifier)//
                            .distinct().collect(Collectors.toSet());
        }
        return providedTagsSet.contains(ElementUtils.getUniqueIdentifier(tagClass));
    }

    public Signature signature(Class<?> returnType, Class<?>... argumentTypes) {
        TypeMirror[] arguments = new TypeMirror[argumentTypes.length];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = context.getType(argumentTypes[i]);
        }
        return new Signature(context.getType(returnType), List.of(arguments));
    }

    public TypeMirror findProvidedTag(TypeMirror searchTag) {
        if (!enableTagInstrumentation) {
            return null;
        }
        for (TypeMirror tag : getProvidedTags()) {
            if (ElementUtils.typeEquals(tag, searchTag)) {
                return tag;
            }
        }
        return null;
    }

    public TypeMirror getProvidedRootTag() {
        return findProvidedTag(types.StandardTags_RootTag);
    }

    public TypeMirror getProvidedRootBodyTag() {
        return findProvidedTag(types.StandardTags_RootBodyTag);
    }

    public boolean isBytecodeUpdatable() {
        return !getInstrumentations().isEmpty() || !getProvidedTags().isEmpty();
    }

    public InstructionModel getInvalidateInstruction(int length) {
        if (invalidateInstructions == null) {
            return null;
        }
        assert length % 2 == 0;
        return invalidateInstructions[(length - OPCODE_WIDTH) / 2];
    }

    public InstructionModel[] getInvalidateInstructions() {
        return invalidateInstructions;
    }

    public OperationModel operation(OperationKind kind, String name, String javadoc) {
        return operation(kind, name, javadoc, name);
    }

    public OperationModel operation(OperationKind kind, String name, String javadoc, String builderName) {
        if (operations.containsKey(name)) {
            addError("Multiple operations declared with name %s. Operation names must be distinct.", name);
            return null;
        }
        OperationModel op = new OperationModel(this, operationId++, kind, name, builderName, javadoc);
        operations.put(name, op);
        return op;
    }

    public List<CustomOperationModel> getInstrumentations() {
        return instrumentations;
    }

    public CustomOperationModel customRegularOperation(OperationKind kind, String name, String javadoc, TypeElement typeElement, AnnotationMirror mirror) {
        OperationModel op = operation(kind, name, javadoc);
        if (op == null) {
            return null;
        }
        CustomOperationModel operation = new CustomOperationModel(context, this, typeElement, mirror, op);
        if (customRegularOperations.containsKey(typeElement)) {
            throw new AssertionError(String.format("Type element %s was used to instantiate more than one operation. This is a bug.", typeElement));
        }
        customRegularOperations.put(typeElement, operation);
        operationsToCustomOperations.put(op, operation);

        if (kind == OperationKind.CUSTOM_INSTRUMENTATION) {
            op.setInstrumentationIndex(instrumentations.size());
            instrumentations.add(operation);
        } else if (ElementUtils.typeEquals(mirror.getAnnotationType(), types.Prolog)) {
            op.setInternal();
            if (prolog != null) {
                addError(typeElement, "%s is already annotated with @%s. A Bytecode DSL class can only declare one prolog.", getSimpleName(prolog.getTemplateType()),
                                getSimpleName(types.Prolog));
                return null;
            }

            prolog = operation;
        } else if (ElementUtils.typeEquals(mirror.getAnnotationType(), types.EpilogReturn)) {
            op.setInternal();
            op.setTransparent(true);
            op.setDynamicOperands(new DynamicOperandModel(List.of("value"), true, false));
            if (epilogReturn != null) {
                addError(typeElement, "%s is already annotated with @%s. A Bytecode DSL class can only declare one return epilog.", getSimpleName(epilogReturn.getTemplateType()),
                                getSimpleName(types.EpilogReturn));
                return null;
            }
            epilogReturn = operation;
        } else if (ElementUtils.typeEquals(mirror.getAnnotationType(), types.EpilogExceptional)) {
            op.setInternal();
            if (epilogExceptional != null) {
                addError(typeElement, "%s is already annotated with @%s. A Bytecode DSL class can only declare one exceptional epilog.", getSimpleName(epilogExceptional.getTemplateType()),
                                getSimpleName(types.EpilogExceptional));
                return null;
            }
            epilogExceptional = operation;
        }

        return operation;
    }

    public CustomOperationModel customShortCircuitOperation(String name, String javadoc, AnnotationMirror mirror) {
        OperationModel op = operation(OperationKind.CUSTOM_SHORT_CIRCUIT, name, javadoc);
        if (op == null) {
            return null;
        }
        CustomOperationModel customOp = new CustomOperationModel(context, this, null, mirror, op);
        customShortCircuitOperations.add(customOp);
        operationsToCustomOperations.put(op, customOp);

        return customOp;
    }

    public CustomOperationModel getCustomOperationForType(TypeElement typeElement) {
        return customRegularOperations.get(typeElement);
    }

    public InstructionModel quickenInstruction(InstructionModel base, Signature signature, String specializationName) {
        InstructionModel model = instruction(base.kind, base.name + "$" + specializationName, signature, specializationName);
        for (InstructionImmediate imm : base.getImmediates()) {
            model.addImmediate(imm.kind(), imm.name());
        }
        model.filteredSpecializations = base.filteredSpecializations;
        model.nodeData = base.nodeData;
        model.nodeType = base.nodeType;
        model.quickeningBase = base;
        model.operation = base.operation;
        model.shortCircuitModel = base.shortCircuitModel;
        base.quickenedInstructions.add(model);
        return model;
    }

    public boolean overridesBytecodeDebugListenerMethod(String methodName) {
        if (!bytecodeDebugListener) {
            return false;
        }
        ExecutableElement e = ElementUtils.findMethod(types.BytecodeDebugListener, methodName);
        if (e == null) {
            throw new IllegalArgumentException("Method with name " + methodName + " not found.");
        }

        TypeElement type = getTemplateType();
        while (type != null) {
            if (ElementUtils.findOverride(type, e) != null) {
                return true;
            }
            type = ElementUtils.castTypeElement(type.getSuperclass());
        }
        return false;

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

    public InstructionModel shortCircuitInstruction(String name, ShortCircuitInstructionModel shortCircuitModel) {
        if (instructions.containsKey(name)) {
            throw new AssertionError(String.format("Multiple instructions declared with name %s. Instruction names must be distinct.", name));
        }
        Signature signature = signature(shortCircuitModel.producesBoolean() ? boolean.class : Object.class, boolean.class, boolean.class);
        InstructionModel instr = instruction(InstructionKind.CUSTOM_SHORT_CIRCUIT, name, signature);
        instr.shortCircuitModel = shortCircuitModel;

        InstructionModel booleanConverterInstruction = shortCircuitModel.booleanConverterInstruction();
        if (booleanConverterInstruction != null) {
            booleanConverterInstruction.shortCircuitInstructions.add(instr);
        }

        return instr;
    }

    @Override
    public Element getMessageElement() {
        return templateType;
    }

    @Override
    public AnnotationMirror getMessageAnnotation() {
        return getTemplateTypeAnnotation();
    }

    public void finalizeInstructions() {
        BytecodeDSLBuiltins.addBuiltinsOnFinalize(this);

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

        short currentId = 1;
        for (InstructionModel m : newInstructions.values()) {
            m.setId(currentId++);
            m.validateAlignment();
            /*
             * Make sure the instruction format for quickening is valid.
             */
            if (m.isQuickening()) {
                InstructionModel root = m.getQuickeningRoot();
                if (root.getInstructionLength() != m.getInstructionLength()) {
                    throw new AssertionError(String.format(
                                    "All quickenings must have the same instruction length as the root instruction. " +
                                                    "Invalid instruction length %s for instruction %s. Expected length %s from root %s.",
                                    m.getInstructionLength(), m.name, root.getInstructionLength(), root.name));
                }
            }
        }

        this.instructions = newInstructions;
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        ArrayList<MessageContainer> result = new ArrayList<>(customRegularOperations.values());
        result.addAll(customShortCircuitOperations);

        for (InstructionModel model : instructions.values()) {
            if (model.nodeData != null) {
                result.add(model.nodeData);
            }
        }

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

    public OperationModel getOperationByName(String name) {
        return operations.get(name);
    }

    public Collection<OperationModel> getOperations() {
        return operations.values();
    }

    public Collection<OperationModel> getOperationsWithChildren() {
        List<OperationModel> result = new ArrayList<>();
        for (OperationModel operation : operations.values()) {
            if (operation.hasChildren()) {
                result.add(operation);
            }
        }
        return result;
    }

    public Collection<OperationModel> getUserOperations() {
        List<OperationModel> result = new ArrayList<>();
        for (OperationModel operation : operations.values()) {
            if (!operation.isInternal) {
                result.add(operation);
            }
        }
        return result;
    }

    public Collection<InstructionModel> getInstructions() {
        return instructions.values();
    }

    public InstructionModel getInstructionByName(String name) {
        return instructions.get(name);
    }

    public CustomOperationModel getCustomOperationForOperation(OperationModel op) {
        return operationsToCustomOperations.get(op);
    }

    public boolean needsBciSlot() {
        return enableUncachedInterpreter || storeBciInFrame;
    }

    public boolean localAccessesNeedLocalIndex() {
        // With block scoping, we need a local index to resolve boxing elimination tags.
        return enableBlockScoping && usesBoxingElimination();
    }

    public boolean materializedLocalAccessesNeedLocalIndex() {
        // With block scoping, we need a local index to resolve boxing elimination tags. We also use
        // it to do liveness checks when the bci is stored in the frame.
        return enableMaterializedLocalAccesses && enableBlockScoping && (usesBoxingElimination() || storeBciInFrame);
    }

    public boolean canValidateMaterializedLocalLiveness() {
        // We can check local liveness in materialized accesses if the bci is stored in the frame.
        return enableBlockScoping && storeBciInFrame;
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

    public OperationModel findOperation(OperationKind kind) {
        OperationModel found = null;
        for (OperationModel o : getOperations()) {
            if (o.kind == kind) {
                if (found != null) {
                    throw new IllegalStateException("Multiple operations of kind found.");
                }
                found = o;
            }
        }
        return found;

    }

    public void sortInstructionsByKind() {
        List<InstructionModel> sortedInstructions = this.instructions.values().stream().sorted((o1, o2) -> Integer.compare(o1.kind.ordinal(), o2.kind.ordinal())).toList();
        this.instructions.clear();
        for (InstructionModel instr : sortedInstructions) {
            this.instructions.put(instr.name, instr);
        }

    }

}
