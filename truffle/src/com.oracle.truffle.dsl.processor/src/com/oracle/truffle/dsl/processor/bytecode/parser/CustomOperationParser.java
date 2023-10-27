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
package com.oracle.truffle.dsl.processor.bytecode.parser;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.firstLetterUpperCase;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getSimpleName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getTypeElement;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isAssignable;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isObject;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.typeEqualsAny;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PUBLIC;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModel;
import com.oracle.truffle.dsl.processor.bytecode.model.CustomOperationModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.Signature;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationKind;
import com.oracle.truffle.dsl.processor.bytecode.model.ShortCircuitInstructionModel;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedPackageElement;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;
import com.oracle.truffle.dsl.processor.parser.NodeParser;

public final class CustomOperationParser extends AbstractParser<CustomOperationModel> {

    private final ProcessorContext context;
    private final BytecodeDSLModel parent;
    private final DeclaredType annotationType;
    private final boolean forProxyValidation;

    private CustomOperationParser(ProcessorContext context, BytecodeDSLModel parent, DeclaredType annotationType, boolean forProxyValidation) {
        this.context = context;
        this.parent = parent;
        this.annotationType = annotationType;
        this.forProxyValidation = forProxyValidation;
    }

    public static CustomOperationParser forProxyValidation() {
        ProcessorContext context = ProcessorContext.getInstance();
        CodeTypeElement dummyBytecodeClass = new CodeTypeElement(Set.of(), ElementKind.CLASS, null, "DummyBytecodeClass");
        dummyBytecodeClass.setSuperClass(context.getTypes().Node);
        dummyBytecodeClass.setEnclosingElement(new GeneratedPackageElement("dummy"));
        return new CustomOperationParser(
                        context,
                        new BytecodeDSLModel(context, dummyBytecodeClass, null, ""),
                        context.getTypes().OperationProxy_Proxyable,
                        true);
    }

    public static CustomOperationParser forCodeGeneration(BytecodeDSLModel parent, DeclaredType annotationType) {
        ProcessorContext context = parent.getContext();
        if (isHandled(context, annotationType)) {
            return new CustomOperationParser(context, parent, annotationType, false);
        } else {
            throw new IllegalArgumentException(String.format("%s does not handle the %s annotation.", CustomOperationParser.class.getName(), annotationType));
        }
    }

    private static boolean isHandled(ProcessorContext context, TypeMirror annotationType) {
        Types typeUtils = context.getEnvironment().getTypeUtils();
        TruffleTypes truffleTypes = context.getTypes();
        for (DeclaredType handled : new DeclaredType[]{truffleTypes.Operation, truffleTypes.OperationProxy_Proxyable, truffleTypes.ShortCircuitOperation}) {
            if (typeUtils.isSameType(annotationType, handled)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected CustomOperationModel parse(Element element, List<AnnotationMirror> annotationMirrors) {
        /**
         * This entrypoint is only invoked by the TruffleProcessor to validate Proxyable nodes. We
         * directly invoke {@link parseCustomOperation} for code gen use cases.
         */
        assert annotationType == context.getTypes().OperationProxy_Proxyable;

        TypeElement typeElement = (TypeElement) element;
        if (annotationMirrors.size() != 1) {
            throw new IllegalArgumentException(String.format("Expected element %s to have one %s annotation, but %d found.", typeElement.getSimpleName(), annotationType, annotationMirrors.size()));
        }
        AnnotationMirror mirror = annotationMirrors.getFirst();

        return parseCustomOperation(typeElement, mirror);
    }

    public CustomOperationModel parseCustomOperation(TypeElement typeElement, AnnotationMirror mirror) {
        if (isShortCircuit()) {
            return parseCustomShortCircuitOperation(typeElement, mirror);
        } else {
            return parseCustomRegularOperation(typeElement, mirror);
        }
    }

    public CustomOperationModel parseCustomRegularOperation(TypeElement typeElement, AnnotationMirror mirror) {
        String name = getCustomOperationName(typeElement, mirror);
        CustomOperationModel customOperation = parent.customRegularOperation(OperationKind.CUSTOM_SIMPLE, name, typeElement, mirror);
        if (customOperation == null) {
            return null;
        }

        validateCustomOperation(customOperation, typeElement, mirror, name);
        if (customOperation.hasErrors()) {
            return customOperation;
        }

        CodeTypeElement generatedNode = createNodeForCustomInstruction(typeElement);
        List<ExecutableElement> specializations = findSpecializations(generatedNode);

        if (specializations.size() == 0) {
            customOperation.addError("Operation class %s contains no specializations.", generatedNode.getSimpleName());
            return null;
        }

        Signature signature = createPolymorphicSignature(specializations, customOperation);

        if (customOperation.hasErrors()) {
            return customOperation;
        }
        assert signature != null : "Signature could not be computed, but no error was reported";
        populateCustomOperationFields(customOperation.operation, signature);

        customOperation.operation.setInstruction(createCustomInstruction(customOperation, typeElement, generatedNode, signature, name));

        return customOperation;
    }

    public CustomOperationModel parseCustomShortCircuitOperation(TypeElement typeElement, AnnotationMirror mirror) {
        String name = getCustomOperationName(typeElement, mirror);
        CustomOperationModel customOperation = parent.customShortCircuitOperation(OperationKind.CUSTOM_SHORT_CIRCUIT, name, mirror);
        if (customOperation == null) {
            return null;
        }

        // All short-circuit operations have the same signature.
        OperationModel operation = customOperation.operation;
        operation.numChildren = 1;
        operation.isVariadic = true;
        operation.isVoid = false;
        operation.operationArgumentTypes = new TypeMirror[0];
        operation.childrenMustBeValues = new boolean[]{true};

        boolean continueWhen = (boolean) ElementUtils.getAnnotationValue(customOperation.getTemplateTypeAnnotation(), "continueWhen").getValue();
        boolean returnConvertedValue = (boolean) ElementUtils.getAnnotationValue(customOperation.getTemplateTypeAnnotation(), "returnConvertedValue").getValue();
        /*
         * NB: This creates a new operation for the boolean converter (or reuses one if such an
         * operation already exists).
         */
        InstructionModel booleanConverterInstruction = getOrCreateBooleanConverterInstruction(typeElement, mirror);
        ShortCircuitInstructionModel instruction = parent.shortCircuitInstruction("sc." + name, continueWhen, returnConvertedValue, booleanConverterInstruction);
        operation.instruction = instruction;

        instruction.addImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target");

        return customOperation;
    }

    private InstructionModel getOrCreateBooleanConverterInstruction(TypeElement typeElement, AnnotationMirror mirror) {
        CustomOperationModel result = parent.getCustomOperationForType(typeElement);
        if (result == null) {
            result = CustomOperationParser.forCodeGeneration(parent, types.Operation).parseCustomOperation(typeElement, mirror);
        }
        if (result == null || result.hasErrors()) {
            parent.addError(mirror, ElementUtils.getAnnotationValue(mirror, "booleanConverter"),
                            "Encountered errors using %s as a boolean converter. These errors must be resolved before the DSL can proceed.", getSimpleName(typeElement));
            return null;
        }

        List<ExecutableElement> specializations = findSpecializations(typeElement);
        assert specializations.size() != 0;

        boolean returnsBoolean = true;
        for (ExecutableElement spec : specializations) {
            if (spec.getReturnType().getKind() != TypeKind.BOOLEAN) {
                returnsBoolean = false;
                break;
            }
        }

        Signature sig = result.operation.instruction.signature;
        if (!returnsBoolean || sig.valueCount != 1 || sig.isVariadic || sig.localSetterCount > 0 || sig.localSetterRangeCount > 0) {
            parent.addError(mirror, ElementUtils.getAnnotationValue(mirror, "booleanConverter"),
                            "Specializations for boolean converter %s must only take one value parameter and return boolean.", getSimpleName(typeElement));
            return null;
        }

        return result.operation.instruction;
    }

    private String getCustomOperationName(TypeElement typeElement, AnnotationMirror mirror) {
        if (mirror != null && (isProxy() || isShortCircuit())) {
            AnnotationValue nameValue = ElementUtils.getAnnotationValue(mirror, "name", false);
            if (nameValue != null) {
                return (String) nameValue.getValue();
            }
        }

        String name = typeElement.getSimpleName().toString();
        if (name.endsWith("Node")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    /**
     * Validates the operation specification. Reports any errors on the {@link customOperation}.
     */
    private void validateCustomOperation(CustomOperationModel customOperation, TypeElement typeElement, AnnotationMirror mirror, String name) {
        if (name.contains("_")) {
            customOperation.addError("Operation class name cannot contain underscores.");
        }

        boolean isNode = isAssignable(typeElement.asType(), types.NodeInterface);
        if (isNode) {
            if (isProxy()) {
                AnnotationMirror generateCached = NodeParser.findGenerateAnnotation(typeElement.asType(), types.GenerateCached);
                if (generateCached != null && !ElementUtils.getAnnotationValue(Boolean.class, generateCached, "value")) {
                    customOperation.addError(
                                    "Class %s does not generate a cached node, so it cannot be used as an OperationProxy. Enable cached node generation using @GenerateCached(true) or delegate to this node using a regular Operation.",
                                    typeElement.getQualifiedName());
                    return;
                }
            }
        } else {
            // operation specification
            if (!typeElement.getModifiers().contains(Modifier.FINAL)) {
                customOperation.addError("Operation class must be declared final. Inheritance in operation specifications is not supported.");
            }
            if (typeElement.getEnclosingElement().getKind() != ElementKind.PACKAGE && !typeElement.getModifiers().contains(Modifier.STATIC)) {
                customOperation.addError("Operation class must not be an inner class (non-static nested class). Declare the class as static.");
            }
            if (typeElement.getModifiers().contains(Modifier.PRIVATE)) {
                customOperation.addError("Operation class must not be declared private. Remove the private modifier to make it visible.");
            }
            if (!ElementUtils.isObject(typeElement.getSuperclass()) || !typeElement.getInterfaces().isEmpty()) {
                customOperation.addError("Operation class must not extend any classes or implement any interfaces. Inheritance in operation specifications is not supported.");
            }

            // Ensure all non-private methods are static.
            for (Element el : typeElement.getEnclosedElements()) {
                if (el.getModifiers().contains(Modifier.PRIVATE)) {
                    continue;
                }

                if (!el.getModifiers().contains(Modifier.STATIC)) {
                    if (el.getKind() == ElementKind.CONSTRUCTOR && ((ExecutableElement) el).getParameters().size() == 0) {
                        continue; // ignore the default constructor
                    }
                    if (el.getKind() == ElementKind.METHOD && isSpecialization((ExecutableElement) el)) {
                        continue; // non-static specializations get a different message; see below
                    }
                    customOperation.addError(el, "Operation class must not contain non-static members.");
                }
            }
        }

        /**
         * The generated Node for this instruction does not subclass the original class defining the
         * specializations. Thus, each specialization should (1) be declared as static and (2) be
         * visible from the generated Node (i.e., public or package-private and in the same package
         * as the root node). Specialization visibility can be checked easily before we try to
         * generate the node.
         *
         * Similarly, the members (methods and fields) used in guard/cache expressions should (1)
         * not be instance fields/methods of the receiver and (2) be visible from the generated
         * Node. The first condition is "enforced" when we filter non-static members from the Node;
         * the {@link DSLExpressionResolver} should fail to resolve any instance member references.
         * The latter condition is checked during the regular resolution process.
         *
         */
        for (ExecutableElement specialization : findSpecializations(typeElement)) {
            if (!specialization.getModifiers().contains(Modifier.STATIC)) {
                // TODO: add docs explaining how to convert a non-static specialization method and
                // reference it in this error message.
                customOperation.addError(specialization, "Operation specializations must be static. This method should be rewritten as a static specialization.");
            }

            if (specialization.getModifiers().contains(Modifier.PRIVATE)) {
                customOperation.addError(specialization, "Operation specialization cannot be private.");
            } else if (!forProxyValidation && !ElementUtils.isVisible(parent.getTemplateType(), specialization)) {
                // We can only perform visibility checks during generation.
                parent.addError(mirror, null, "Operation %s's specialization \"%s\" must be visible from this node.", typeElement.getSimpleName(), specialization.getSimpleName());
            }
        }
    }

    /*
     * Creates a placeholder Node from the type element that will be passed to FlatNodeGenFactory.
     * We remove any members that are not needed for code generation.
     */
    private CodeTypeElement createNodeForCustomInstruction(TypeElement typeElement) {
        boolean isNode = isAssignable(typeElement.asType(), types.NodeInterface);
        CodeTypeElement nodeType;
        if (isNode) {
            nodeType = cloneTypeHierarchy(typeElement, ct -> {
                // Remove annotations that will cause {@link FlatNodeGenFactory} to generate
                // unnecessary code. We programmatically add @NodeChildren later, so remove them
                // here.
                ct.getAnnotationMirrors().removeIf(
                                m -> typeEqualsAny(m.getAnnotationType(), types.NodeChild, types.NodeChildren, types.GenerateUncached, types.GenerateCached, types.GenerateInline,
                                                types.GenerateNodeFactory));
                // Remove all non-static or private elements, including all of the execute methods.
                ct.getEnclosedElements().removeIf(e -> !e.getModifiers().contains(Modifier.STATIC) || e.getModifiers().contains(Modifier.PRIVATE));
            });
        } else {
            nodeType = CodeTypeElement.cloneShallow(typeElement);
            nodeType.setSuperClass(types.Node);
        }

        nodeType.setEnclosingElement(null);

        return nodeType;
    }

    /**
     * Adds annotations, methods, etc. to the {@link generatedNode} so that the desired code will be
     * generated by {@link FlatNodeGenFactory} during code generation.
     */
    private void addCustomInstructionNodeMembers(TypeElement originalTypeElement, CodeTypeElement generatedNode, Signature signature) {
        if (shouldGenerateUncached(originalTypeElement)) {
            generatedNode.addAnnotationMirror(new CodeAnnotationMirror(types.GenerateUncached));
        }
        generatedNode.addAll(createExecuteMethods(signature, originalTypeElement));

        /*
         * Add @NodeChildren to this node for each argument to the operation. These get used by
         * FlatNodeGenFactory to synthesize specialization logic. Since we directly execute the
         * children, we remove the fields afterwards.
         */
        CodeAnnotationMirror nodeChildrenAnnotation = new CodeAnnotationMirror(types.NodeChildren);
        nodeChildrenAnnotation.setElementValue("value", new CodeAnnotationValue(createNodeChildAnnotations(signature).stream().map(CodeAnnotationValue::new).collect(Collectors.toList())));
        generatedNode.addAnnotationMirror(nodeChildrenAnnotation);

        if (parent.enableTracing) {
            generatedNode.addAnnotationMirror(new CodeAnnotationMirror(types.Introspectable));
        }
    }

    /**
     * Uses the custom operation's {@link signature} to set the underlying {@link operation}'s
     * fields.
     */
    private void populateCustomOperationFields(OperationModel operation, Signature signature) {
        operation.numChildren = signature.valueCount;
        operation.isVariadic = signature.isVariadic || isShortCircuit();
        operation.isVoid = signature.isVoid;

        operation.operationArgumentTypes = new TypeMirror[signature.localSetterCount + signature.localSetterRangeCount];
        for (int i = 0; i < signature.localSetterCount; i++) {
            operation.operationArgumentTypes[i] = types.BytecodeLocal;
        }
        for (int i = 0; i < signature.localSetterRangeCount; i++) {
            // todo: we might want to migrate this to a special type that validates order
            // e.g. BytecodeLocalRange
            operation.operationArgumentTypes[signature.localSetterCount + i] = new CodeTypeMirror.ArrayCodeTypeMirror(types.BytecodeLocal);
        }
        operation.childrenMustBeValues = new boolean[signature.valueCount];
        Arrays.fill(operation.childrenMustBeValues, true);
    }

    private boolean isShortCircuit() {
        return context.getEnvironment().getTypeUtils().isSameType(annotationType, context.getTypes().ShortCircuitOperation);
    }

    private boolean isProxy() {
        return context.getEnvironment().getTypeUtils().isSameType(annotationType, context.getTypes().OperationProxy_Proxyable);
    }

    private List<AnnotationMirror> createNodeChildAnnotations(Signature signature) {
        List<AnnotationMirror> result = new ArrayList<>();

        for (int i = 0; i < signature.valueCount; i++) {
            result.add(createNodeChildAnnotation("child" + i, signature.getGenericType(i)));
        }
        for (int i = 0; i < signature.localSetterCount; i++) {
            result.add(createNodeChildAnnotation("localSetter" + i, types.LocalSetter));
        }
        for (int i = 0; i < signature.localSetterRangeCount; i++) {
            result.add(createNodeChildAnnotation("localSetterRange" + i, types.LocalSetterRange));
        }

        return result;
    }

    private CodeAnnotationMirror createNodeChildAnnotation(String name, TypeMirror regularReturn, TypeMirror... unexpectedReturns) {
        CodeAnnotationMirror mir = new CodeAnnotationMirror(types.NodeChild);
        mir.setElementValue("value", new CodeAnnotationValue(name));
        mir.setElementValue("type", new CodeAnnotationValue(createNodeChildType(regularReturn, unexpectedReturns).asType()));
        return mir;
    }

    private CodeTypeElement createNodeChildType(TypeMirror regularReturn, TypeMirror... unexpectedReturns) {
        CodeTypeElement c = new CodeTypeElement(Set.of(PUBLIC, ABSTRACT), ElementKind.CLASS, new GeneratedPackageElement(""), "C");
        c.setSuperClass(types.Node);

        c.add(createNodeChildExecute("execute", regularReturn, false));
        for (TypeMirror ty : unexpectedReturns) {
            c.add(createNodeChildExecute("execute" + firstLetterUpperCase(getSimpleName(ty)), ty, true));
        }

        return c;
    }

    private CodeExecutableElement createNodeChildExecute(String name, TypeMirror returnType, boolean withUnexpected) {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC, ABSTRACT), returnType, name);
        ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));

        if (withUnexpected) {
            ex.addThrownType(types.UnexpectedResultException);
        }

        return ex;
    }

    private List<CodeExecutableElement> createExecuteMethods(Signature signature, TypeElement typeElement) {
        List<CodeExecutableElement> result = new ArrayList<>();

        result.add(createExecuteMethod(signature, "executeObject", signature.returnType, false, false));

        if (shouldGenerateUncached(typeElement)) {
            result.add(createExecuteMethod(signature, "executeUncached", signature.returnType, false, true));
        }

        return result;
    }

    private CodeExecutableElement createExecuteMethod(Signature signature, String name, TypeMirror type, boolean withUnexpected, boolean uncached) {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC, ABSTRACT), type, name);
        if (withUnexpected) {
            ex.addThrownType(types.UnexpectedResultException);
        }

        ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));

        if (uncached) {
            for (int i = 0; i < signature.valueCount; i++) {
                ex.addParameter(new CodeVariableElement(signature.getGenericType(i), "child" + i + "Value"));
            }
            for (int i = 0; i < signature.localSetterCount; i++) {
                ex.addParameter(new CodeVariableElement(types.LocalSetter, "localSetter" + i + "Value"));
            }
            for (int i = 0; i < signature.localSetterRangeCount; i++) {
                ex.addParameter(new CodeVariableElement(types.LocalSetterRange, "localSetterRange" + i + "Value"));
            }
        }

        return ex;
    }

    /**
     * Creates and registers a new instruction for a custom operation.
     *
     * This method calls into the Truffle DSL's regular {@link NodeParser Node parsing} logic to
     * generate a {@link NodeData node model} that will later be used by {@link FlatNodeGenFactory
     * code generation} to generate code for the instruction.
     */
    private InstructionModel createCustomInstruction(CustomOperationModel customOperation, TypeElement originalTypeElement, CodeTypeElement generatedNode, Signature signature, String nameSuffix) {
        InstructionModel instr = parent.instruction(InstructionKind.CUSTOM, "c." + nameSuffix);
        instr.nodeType = generatedNode;
        instr.signature = signature;
        instr.nodeData = parseGeneratedNode(customOperation, originalTypeElement, generatedNode, signature);

        for (int i = 0; i < signature.localSetterCount; i++) {
            instr.addImmediate(ImmediateKind.LOCAL_SETTER, "local_setter" + i);
        }

        for (int i = 0; i < signature.localSetterRangeCount; i++) {
            instr.addImmediate(ImmediateKind.LOCAL_SETTER_RANGE_START, "local_setter_range_start" + i);
            instr.addImmediate(ImmediateKind.LOCAL_SETTER_RANGE_LENGTH, "local_setter_range_length" + i);
        }

        // NB: Node-to-bci lookups rely on the node being the last immediate.
        instr.addImmediate(ImmediateKind.NODE, "node");

        return instr;
    }

    /**
     * Use the {@link NodeParser} to parse the generated node specification.
     */
    private NodeData parseGeneratedNode(CustomOperationModel customOperation, TypeElement originalTypeElement, CodeTypeElement generatedNode, Signature signature) {
        if (forProxyValidation) {
            /*
             * A proxied node, by virtue of being a {@link Node}, will already be parsed and
             * validated during regular DSL processing. Re-parsing it here would lead to duplicate
             * error messages on the node itself.
             *
             * NB: We cannot check whether a Proxyable node's cache/guard expressions are visible
             * since it is not associated with a bytecode node during validation. This extra check
             * will happen when a bytecode node using this proxied node is generated.
             */
            return null;
        }

        // Add members to the generated node so that the proper node specification is parsed.
        addCustomInstructionNodeMembers(originalTypeElement, generatedNode, signature);

        NodeData result;
        try {
            NodeParser parser = NodeParser.createOperationParser(parent.getTemplateType());
            result = parser.parse(generatedNode, false);
        } catch (Throwable ex) {
            StringWriter wr = new StringWriter();
            ex.printStackTrace(new PrintWriter(wr));
            customOperation.addError("Error generating instruction for Operation node %s: \n%s", parent.getName(), wr.toString());
            return null;
        }

        if (result == null) {
            customOperation.addError("Error generating instruction for Operation node %s. This is likely a bug in the Bytecode DSL.", parent.getName());
            return null;
        }

        if (result.getTypeSystem().isDefault()) {
            result.setTypeSystem(parent.typeSystem);
        }

        result.redirectMessages(parent);
        result.redirectMessagesOnGeneratedElements(parent);

        return result;
    }

    /**
     * Computes a {@link Signature} from the node's set of specializations. Returns {@code null} if
     * there are no specializations or the specializations do not share a common signature.
     */
    public static Signature createPolymorphicSignature(List<ExecutableElement> specializations, MessageContainer errorTarget) {
        boolean isValid = true;
        Signature polymorphicSignature = null;
        for (ExecutableElement specialization : specializations) {
            Signature signature = createSignature(specialization, errorTarget);
            if (signature == null) {
                isValid = false;
                continue;
            }
            polymorphicSignature = mergeSignatures(signature, polymorphicSignature, specialization, errorTarget);
        }

        if (!isValid || polymorphicSignature == null) {
            // signatures are invalid or inconsistent
            return null;
        }
        return polymorphicSignature;
    }

    private static TruffleTypes types() {
        return ProcessorContext.types();
    }

    private static Signature mergeSignatures(Signature a, Signature b, Element el, MessageContainer errorTarget) {
        if (b == null) {
            return a;
        }
        if (a.isVariadic != b.isVariadic) {
            if (errorTarget != null) {
                errorTarget.addError(el, "Error calculating operation signature: either all or none of the specialization must be variadic (have a @%s annotated parameter)",
                                getSimpleName(types().Variadic));
            }
            return null;
        }
        if (a.isVoid != b.isVoid) {
            if (errorTarget != null) {
                errorTarget.addError(el, "Error calculating operation signature: either all or none of the specialization must be declared void.");
            }
            return null;
        }
        if (a.valueCount != b.valueCount) {
            if (errorTarget != null) {
                errorTarget.addError(el, "Error calculating operation signature: all specializations must have the same number of value arguments.");
            }
            return null;
        }
        if (a.localSetterCount != b.localSetterCount) {
            if (errorTarget != null) {
                errorTarget.addError(el, "Error calculating operation signature: all specializations must have the same number of %s arguments.",
                                getSimpleName(types().LocalSetter));
            }
            return null;
        }
        if (a.localSetterRangeCount != b.localSetterRangeCount) {
            if (errorTarget != null) {
                errorTarget.addError(el, "Error calculating operation signature: all specializations must have the same number of %s arguments.", getSimpleName(types().LocalSetterRange));
            }
            return null;
        }

        TypeMirror newReturnType = mergeIfPrimitiveType(a.context, a.returnType, b.returnType);
        TypeMirror[] mergedTypes = new TypeMirror[a.specializedTypes.size()];
        for (int i = 0; i < a.specializedTypes.size(); i++) {
            mergedTypes[i] = mergeIfPrimitiveType(a.context, a.specializedTypes.get(i), b.specializedTypes.get(i));
        }
        return new Signature(newReturnType, List.of(mergedTypes), a.isVariadic, a.localSetterCount, a.localSetterRangeCount);
    }

    private static TypeMirror mergeIfPrimitiveType(ProcessorContext context, TypeMirror a, TypeMirror b) {
        if (ElementUtils.typeEquals(ElementUtils.boxType(context, a), ElementUtils.boxType(context, b))) {
            return a;
        } else {
            return context.getType(Object.class);
        }
    }

    private static Signature createSignature(ExecutableElement specialization, MessageContainer errorTarget) {
        boolean isValid = true;
        final ProcessorContext context = ProcessorContext.getInstance();
        final TruffleTypes types = context.getTypes();
        List<VariableElement> valueParams = new ArrayList<>();
        boolean hasVariadic = false;
        int localSetterCount = 0;
        int localSetterRangeCount = 0;
        boolean isFallback = ElementUtils.findAnnotationMirror(specialization, types.Fallback) != null;

        // Each specialization should have parameters in the following order:
        // frame, value*, variadic, localSetter*, localSetterRange*
        // All parameters are optional, and the ones with * can be repeated multiple times.
        for (VariableElement param : specialization.getParameters()) {
            if (isAssignable(param.asType(), types.Frame)) {
                // nothing, we ignore these
                continue;
            } else if (isAssignable(param.asType(), types.LocalSetter)) {
                isValid = errorIfDSLParameter(types.LocalSetter, param, errorTarget) && isValid;
                if (localSetterRangeCount > 0) {
                    if (errorTarget != null) {
                        errorTarget.addError(param, "%s parameters must precede %s parameters.",
                                        getSimpleName(types.LocalSetter), getSimpleName(types.LocalSetterRange));
                    }
                    isValid = false;
                }
                localSetterCount++;
            } else if (isAssignable(param.asType(), types.LocalSetterRange)) {
                isValid = errorIfDSLParameter(types.LocalSetterRange, param, errorTarget) && isValid;
                localSetterRangeCount++;
            } else if (ElementUtils.findAnnotationMirror(param, types.Variadic) != null) {
                isValid = errorIfDSLParameter(types.Variadic, param, errorTarget) && isValid;
                if (hasVariadic) {
                    if (errorTarget != null) {
                        errorTarget.addError(param, "Multiple variadic parameters not allowed to an operation. Split up the operation if such behaviour is required.");
                    }
                    isValid = false;
                }
                if (localSetterRangeCount > 0 || localSetterCount > 0) {
                    if (errorTarget != null) {
                        errorTarget.addError(param, "Value parameters must precede %s and %s parameters.",
                                        getSimpleName(types.LocalSetter),
                                        getSimpleName(types.LocalSetterRange));
                    }
                    isValid = false;
                }
                valueParams.add(param);
                hasVariadic = true;
            } else if (isDSLParameter(param)) {
                // these do not affect the signature
            } else {
                if (hasVariadic) {
                    if (errorTarget != null) {
                        errorTarget.addError(param, "Non-variadic value parameters must precede variadic parameters.");
                    }
                    isValid = false;
                }
                if (localSetterRangeCount > 0 || localSetterCount > 0) {
                    if (errorTarget != null) {
                        errorTarget.addError(param, "Value parameters must precede LocalSetter and LocalSetterRange parameters.");
                    }
                    isValid = false;
                }
                if (isFallback) {
                    /**
                     * In the regular DSL, fallback specializations can take non-Object arguments if
                     * they agree with the type signature of the abstract execute method. Since we
                     * synthesize our own execute method that only takes Object arguments, fallback
                     * specializations with non-Object parameters are unsupported.
                     */
                    if (!isObject(param.asType())) {
                        if (errorTarget != null) {
                            errorTarget.addError(param, "Value parameters to @%s specializations of Operation nodes must have type %s.",
                                            getSimpleName(types.Fallback),
                                            getSimpleName(context.getDeclaredType(Object.class)));
                        }
                        isValid = false;
                    }
                }
                valueParams.add(param);
            }
        }

        if (!isValid) {
            return null;
        }

        List<TypeMirror> argumentTypes = valueParams.stream().map(v -> v.asType()).toList();
        return new Signature(specialization.getReturnType(), argumentTypes, hasVariadic, localSetterCount, localSetterRangeCount);
    }

    private static boolean isDSLParameter(VariableElement param) {
        for (AnnotationMirror mir : param.getAnnotationMirrors()) {
            if (typeEqualsAny(mir.getAnnotationType(), types().Cached, types().CachedLibrary, types().Bind)) {
                return true;
            }
        }
        return false;
    }

    private static boolean errorIfDSLParameter(TypeMirror paramType, VariableElement param, MessageContainer errorTarget) {
        if (isDSLParameter(param)) {
            if (errorTarget != null) {
                errorTarget.addError(param, "%s parameters must not be annotated with @%s, @%s, or @%s.",
                                getSimpleName(paramType),
                                getSimpleName(types().Cached),
                                getSimpleName(types().CachedLibrary),
                                getSimpleName(types().Bind));
            }
            return false;
        }
        return true;
    }

    private List<ExecutableElement> findSpecializations(TypeElement te) {
        if (ElementUtils.isObject(te.asType())) {
            return new ArrayList<>();
        }

        List<ExecutableElement> result = findSpecializations(getTypeElement((DeclaredType) te.getSuperclass()));

        for (ExecutableElement ex : ElementFilter.methodsIn(te.getEnclosedElements())) {
            if (isSpecialization(ex)) {
                result.add(ex);
            }
        }

        return result;
    }

    private boolean isSpecialization(ExecutableElement ex) {
        return ElementUtils.findAnnotationMirror(ex, types.Specialization) != null || ElementUtils.findAnnotationMirror(ex, types.Fallback) != null;
    }

    private boolean shouldGenerateUncached(TypeElement typeElement) {
        if (forProxyValidation) {
            /*
             * NB: When we're just validating a Proxyable node, we do not know whether it'll be used
             * in an uncached interpreter. However, a Proxyable can only be used in an uncached
             * interpreter when it declares @GenerateUncached, so this annotation suffices for
             * validation.
             */
            return NodeParser.isGenerateUncached(typeElement);
        } else {
            return parent.enableUncachedInterpreter;
        }
    }

    @Override
    public DeclaredType getAnnotationType() {
        return annotationType;
    }

    private CodeTypeElement cloneTypeHierarchy(TypeElement element, Consumer<CodeTypeElement> mapper) {
        CodeTypeElement result = CodeTypeElement.cloneShallow(element);
        if (!ElementUtils.isObject(element.getSuperclass())) {
            result.setSuperClass(cloneTypeHierarchy(context.getTypeElement((DeclaredType) element.getSuperclass()), mapper).asType());
        }

        mapper.accept(result);

        return result;
    }

}
