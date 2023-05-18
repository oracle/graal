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
package com.oracle.truffle.dsl.processor.operations.parser;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.firstLetterUpperCase;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getQualifiedName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getSimpleName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getTypeElement;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isAssignable;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.typeEqualsAny;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PUBLIC;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedPackageElement;
import com.oracle.truffle.dsl.processor.operations.model.InstructionModel;
import com.oracle.truffle.dsl.processor.operations.model.InstructionModel.Signature;
import com.oracle.truffle.dsl.processor.operations.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.operations.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.operations.model.OperationModel;
import com.oracle.truffle.dsl.processor.operations.model.OperationModel.OperationKind;
import com.oracle.truffle.dsl.processor.operations.model.OperationsModel;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;
import com.oracle.truffle.dsl.processor.parser.NodeParser;

public class CustomOperationParser extends AbstractParser<OperationModel> {

    private enum ParseMode {
        OPERATION,
        OPERATION_PROXY,
        SHORT_CIRCUIT_OPERATION
    }

    private final OperationsModel parent;
    private final AnnotationMirror mirror;
    private final ParseMode mode;

    private CustomOperationParser(OperationsModel parent, AnnotationMirror mirror, ParseMode mode) {
        this.parent = parent;
        this.mirror = mirror;
        this.mode = mode;
    }

    public static CustomOperationParser forOperation(OperationsModel parent, AnnotationMirror mirror) {
        return new CustomOperationParser(parent, mirror, ParseMode.OPERATION);
    }

    public static CustomOperationParser forOperationProxy(OperationsModel parent, AnnotationMirror mirror) {
        return new CustomOperationParser(parent, mirror, ParseMode.OPERATION_PROXY);
    }

    public static CustomOperationParser forShortCircuitOperation(OperationsModel parent, AnnotationMirror mirror) {
        return new CustomOperationParser(parent, mirror, ParseMode.SHORT_CIRCUIT_OPERATION);
    }

    @Override
    protected OperationModel parse(Element element, List<AnnotationMirror> ignored) {
        TypeElement typeElement = (TypeElement) element;
        OperationModel result = parseImpl(typeElement);
        if (result.hasErrors() && isProxy()) {
            AnnotationValue proxiedClassValue = ElementUtils.getAnnotationValue(mirror, "value", false);
            parent.addError(mirror, proxiedClassValue, "Encountered errors using %s as an OperationProxy. These errors must be resolved before the DSL can proceed.", typeElement.getQualifiedName());
        }
        return result;
    }

    private OperationModel parseImpl(TypeElement typeElement) {
        OperationKind kind = mode == ParseMode.SHORT_CIRCUIT_OPERATION
                        ? OperationKind.CUSTOM_SHORT_CIRCUIT
                        : OperationKind.CUSTOM_SIMPLE;

        String name = typeElement.getSimpleName().toString();
        if (name.endsWith("Node")) {
            name = name.substring(0, name.length() - 4);
        }

        if (isProxy()) {
            AnnotationValue nameValue = ElementUtils.getAnnotationValue(mirror, "operationName", false);
            if (nameValue != null) {
                name = (String) nameValue.getValue();
            }
        } else if (isShortCircuit()) {
            AnnotationValue nameValue = ElementUtils.getAnnotationValue(mirror, "name", false);
            if (nameValue != null) {
                name = (String) nameValue.getValue();
            }
        }

        OperationModel data = parent.operation(typeElement, kind, name);
        data.annotationMirror = mirror;

        if (name.contains("_")) {
            data.addError("Operation class name cannot contain underscores.");
        }

        boolean isNode = isAssignable(typeElement.asType(), types.NodeInterface);

        if (isNode) {
            if (isProxy()) {
                AnnotationMirror generateCached = NodeParser.findGenerateAnnotation(typeElement.asType(), types.GenerateCached);
                if (generateCached != null && !ElementUtils.getAnnotationValue(Boolean.class, generateCached, "value")) {
                    AnnotationValue proxyClass = ElementUtils.getAnnotationValue(mirror, "value");
                    parent.addError(mirror, proxyClass,
                                    "Class %s does not generate a cached node, so it cannot be used as an OperationProxy. Enable cached node generation using @GenerateCached(true) or delegate to this node using a regular Operation.",
                                    typeElement.getQualifiedName());
                    return data;
                }
            }
        } else {
            // operation specification

            if (!typeElement.getModifiers().contains(Modifier.FINAL)) {
                data.addError("Operation class must be declared final. Inheritance in operation specifications is not supported.");
            }

            if (typeElement.getEnclosingElement().getKind() != ElementKind.PACKAGE && !typeElement.getModifiers().contains(Modifier.STATIC)) {
                data.addError("Operation class must not be an inner class (non-static nested class). Declare the class as static.");
            }

            if (typeElement.getModifiers().contains(Modifier.PRIVATE)) {
                data.addError("Operation class must not be declared private. Remove the private modifier to make it visible.");
            }

            // TODO: Add cross-package visibility check

            if (!ElementUtils.isObject(typeElement.getSuperclass()) || !typeElement.getInterfaces().isEmpty()) {
                data.addError("Operation class must not extend any classes or implement any interfaces. Inheritance in operation specifications is not supported.");
            }

            // Ensure all non-private methods are static (except the default 0-argument
            // constructor).
            for (Element el : typeElement.getEnclosedElements()) {
                if (el.getModifiers().contains(Modifier.PRIVATE)) {
                    continue;
                }

                if (!el.getModifiers().contains(Modifier.STATIC)) {
                    if (el.getKind() == ElementKind.CONSTRUCTOR && ((ExecutableElement) el).getParameters().size() == 0) {
                        continue;
                    }
                    if (ElementUtils.findAnnotationMirror(el, types.Specialization) != null) {
                        continue; // non-static specializations get a different message; see below
                    }
                    data.addError(el, "Operation class must not contain non-static members.");
                }
            }
        }

        for (ExecutableElement specialization : findSpecializations(typeElement)) {
            if (!specialization.getModifiers().contains(Modifier.STATIC)) {
                // TODO: add docs explaining how to convert a non-static specialization method and
                // reference it in this error message.
                data.addError(specialization, "Operation class must only contain static specializations. This method should be rewritten as a static specialization.");
            }
        }

        if (data.hasErrors()) {
            return data;
        }

        CodeTypeElement nodeType;
        if (isNode) {
            nodeType = cloneTypeHierarchy(typeElement, ct -> {
                // Remove annotations that will cause {@link FlatNodeGenFactory} to generate
                // unnecessary code. We programmatically add @NodeChildren later, so remove them
                // here.
                ct.getAnnotationMirrors().removeIf(
                                m -> typeEqualsAny(m.getAnnotationType(), types.NodeChild, types.NodeChildren, types.GenerateUncached, types.GenerateNodeFactory, types.GenerateInline));
                // Remove all non-static or private elements, including all of the execute methods.
                ct.getEnclosedElements().removeIf(e -> !e.getModifiers().contains(Modifier.STATIC) || e.getModifiers().contains(Modifier.PRIVATE));
            });
        } else {
            nodeType = CodeTypeElement.cloneShallow(typeElement);
            nodeType.setSuperClass(types.Node);
        }

        nodeType.setEnclosingElement(null);

        Signature signature = determineSignature(data, nodeType);
        if (data.hasErrors()) {
            return data;
        }

        if (signature == null) {
            throw new AssertionError();
        }

        // Use @GenerateUncached so that FlatNodeGenFactory generates an uncached execute method.
        // The baseline interpreter will call this method.
        if (parent.enableBaselineInterpreter) {
            nodeType.addAnnotationMirror(new CodeAnnotationMirror(types.GenerateUncached));
        }

        nodeType.addAll(createExecuteMethods(signature));

        // Add @NodeChildren to this node for each argument to the operation. These get used by
        // FlatNodeGenFactory to synthesize specialization logic. We remove the fields afterwards.
        CodeAnnotationMirror nodeChildrenAnnotation = new CodeAnnotationMirror(types.NodeChildren);
        nodeChildrenAnnotation.setElementValue("value", new CodeAnnotationValue(createNodeChildAnnotations(signature).stream().map(CodeAnnotationValue::new).collect(Collectors.toList())));
        nodeType.addAnnotationMirror(nodeChildrenAnnotation);

        if (parent.enableTracing) {
            nodeType.addAnnotationMirror(new CodeAnnotationMirror(types.Introspectable));
        }

        data.numChildren = signature.valueCount;
        data.isVariadic = signature.isVariadic || isShortCircuit();
        data.isVoid = signature.isVoid;

        data.operationArguments = new TypeMirror[signature.localSetterCount + signature.localSetterRangeCount];
        for (int i = 0; i < signature.localSetterCount; i++) {
            data.operationArguments[i] = types.OperationLocal;
        }
        for (int i = 0; i < signature.localSetterRangeCount; i++) {
            // todo: we might want to migrate this to a special type that validates order
            // e.g. OperationLocalRange
            data.operationArguments[signature.localSetterCount + i] = new CodeTypeMirror.ArrayCodeTypeMirror(types.OperationLocal);
        }

        data.childrenMustBeValues = new boolean[signature.valueCount];
        Arrays.fill(data.childrenMustBeValues, true);

        data.instruction = createCustomInstruction(data, nodeType, signature, name);

        return data;
    }

    private boolean isShortCircuit() {
        return mode == ParseMode.SHORT_CIRCUIT_OPERATION;
    }

    private boolean isProxy() {
        return mode == ParseMode.OPERATION_PROXY;
    }

    private List<AnnotationMirror> createNodeChildAnnotations(Signature signature) {
        List<AnnotationMirror> result = new ArrayList<>();

        TypeMirror[] boxingEliminated = parent.boxingEliminatedTypes.toArray(new TypeMirror[0]);

        for (int i = 0; i < signature.valueCount; i++) {
            result.add(createNodeChildAnnotation("child" + i, signature.getParameterType(i), boxingEliminated));
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

    private List<CodeExecutableElement> createExecuteMethods(Signature signature) {
        List<CodeExecutableElement> result = new ArrayList<>();

        if (signature.isVoid) {
            result.add(createExecuteMethod(signature, "executeVoid", context.getType(void.class), false, false));
        } else {
            result.add(createExecuteMethod(signature, "executeObject", context.getType(Object.class), false, false));

            List<TypeMirror> boxingEliminatedTypes;
            if (parent.boxingEliminatedTypes.isEmpty() || !signature.resultBoxingElimination) {
                boxingEliminatedTypes = new ArrayList<>();
            } else if (signature.possibleBoxingResults != null) {
                boxingEliminatedTypes = new ArrayList<>(signature.possibleBoxingResults);
            } else {
                boxingEliminatedTypes = new ArrayList<>(parent.boxingEliminatedTypes);
            }

            boxingEliminatedTypes.sort((o1, o2) -> getQualifiedName(o1).compareTo(getQualifiedName(o2)));

            for (TypeMirror ty : boxingEliminatedTypes) {
                if (!ElementUtils.isObject(ty)) {
                    result.add(createExecuteMethod(signature, "execute" + firstLetterUpperCase(getSimpleName(ty)), ty, true, false));
                }
            }
        }

        if (parent.enableBaselineInterpreter) {
            if (signature.isVoid) {
                result.add(createExecuteMethod(signature, "executeUncached", context.getType(void.class), false, true));
            } else {
                result.add(createExecuteMethod(signature, "executeUncached", context.getType(Object.class), false, true));
            }
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
                ex.addParameter(new CodeVariableElement(signature.getParameterType(i), "child" + i + "Value"));
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

    private InstructionModel createCustomInstruction(OperationModel data, CodeTypeElement nodeType, Signature signature, String nameSuffix) {
        InstructionKind kind = isShortCircuit() ? InstructionKind.CUSTOM_SHORT_CIRCUIT : InstructionKind.CUSTOM;
        String namePrefix = isShortCircuit() ? "sc." : "c.";

        InstructionModel instr = parent.instruction(kind, namePrefix + nameSuffix);
        instr.nodeType = nodeType;
        instr.signature = signature;

        try {
            NodeParser parser = NodeParser.createOperationParser();
            instr.nodeData = parser.parse(nodeType);
        } catch (Throwable ex) {
            StringWriter wr = new StringWriter();
            ex.printStackTrace(new PrintWriter(wr));
            data.addError("Error generating instruction for Operation node %s: \n%s", data.parent.getName(), wr.toString());
            return instr;
        }

        if (instr.nodeData == null) {
            data.addError("Error generating instruction for Operation node %s. This is likely a bug in the Operation DSL.", data.parent.getName());
            return instr;
        }

        if (instr.nodeData.getTypeSystem().isDefault()) {
            instr.nodeData.setTypeSystem(parent.typeSystem);
        }

        instr.nodeData.redirectMessages(parent);
        instr.nodeData.redirectMessagesOnGeneratedElements(parent);

        if (isShortCircuit()) {
            instr.continueWhen = (boolean) ElementUtils.getAnnotationValue(mirror, "continueWhen").getValue();
            instr.addImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target");
            instr.addImmediate(ImmediateKind.NODE, "node");
        } else {
            for (int i = 0; i < signature.valueBoxingElimination.length; i++) {
                if (signature.valueBoxingElimination[i]) {
                    instr.addImmediate(ImmediateKind.BYTECODE_INDEX, "child" + i + "_bci");
                }
            }

            for (int i = 0; i < signature.localSetterCount; i++) {
                instr.addImmediate(ImmediateKind.LOCAL_SETTER, "local_setter" + i);
            }

            for (int i = 0; i < signature.localSetterRangeCount; i++) {
                instr.addImmediate(ImmediateKind.LOCAL_SETTER_RANGE_START, "local_setter_range_start" + i);
                instr.addImmediate(ImmediateKind.LOCAL_SETTER_RANGE_LENGTH, "local_setter_range_length" + i);
            }
            instr.addImmediate(ImmediateKind.NODE, "node");
        }

        return instr;
    }

    private Signature determineSignature(OperationModel data, CodeTypeElement nodeType) {
        List<ExecutableElement> specializations = findSpecializations(nodeType);

        if (specializations.size() == 0) {
            data.addError("Operation class %s contains no specializations.", nodeType.getSimpleName());
            return null;
        }

        boolean isValid = true;
        Signature signature = null;

        for (ExecutableElement spec : specializations) {
            Signature other = determineSignature(data, spec);
            if (signature == null) {
                // first (valid) signature
                signature = other;
            } else if (other == null) {
                // invalid signature
                isValid = false;
            } else {
                isValid = mergeSignatures(data, signature, other, spec) && isValid;
            }

            if (other != null && isShortCircuit()) {
                if (spec.getReturnType().getKind() != TypeKind.BOOLEAN || other.valueCount != 1 || other.isVariadic || other.localSetterCount > 0 || other.localSetterRangeCount > 0) {
                    data.addError(spec, "Boolean converter operation specializations must only take one value parameter and return boolean.");
                    isValid = false;
                }
            }
        }

        if (!isValid || signature == null) {
            // signatures are invalid or inconsistent
            return null;
        }

        return signature;
    }

    private boolean mergeSignatures(OperationModel data, Signature a, Signature b, Element el) {
        boolean isValid = true;
        if (a.isVariadic != b.isVariadic) {
            data.addError(el, "Error calculating operation signature: either all or none of the specialization must be variadic (have a @%s annotated parameter)",
                            getSimpleName(types.Variadic));
            isValid = false;
        }
        if (a.isVoid != b.isVoid) {
            data.addError(el, "Error calculating operation signature: either all or none of the specialization must be declared void.");
            isValid = false;
        }
        if (a.valueCount != b.valueCount) {
            data.addError(el, "Error calculating operation signature: all specializations must have the same number of value arguments.");
            isValid = false;
        }
        if (a.localSetterCount != b.localSetterCount) {
            data.addError(el, "Error calculating operation signature: all specializations must have the same number of %s arguments.", getSimpleName(types.LocalSetter));
            isValid = false;
        }
        if (a.localSetterRangeCount != b.localSetterRangeCount) {
            data.addError(el, "Error calculating operation signature: all specializations must have the same number of %s arguments.", getSimpleName(types.LocalSetterRange));
            isValid = false;
        }

        if (!isValid) {
            return false;
        }

        a.resultBoxingElimination = a.resultBoxingElimination || b.resultBoxingElimination;

        if (a.possibleBoxingResults == null || b.possibleBoxingResults == null) {
            a.possibleBoxingResults = null;
        } else {
            a.possibleBoxingResults.addAll(b.possibleBoxingResults);
        }

        for (int i = 0; i < a.valueBoxingElimination.length; i++) {
            a.valueBoxingElimination[i] = a.valueBoxingElimination[i] || b.valueBoxingElimination[i];
        }

        return true;
    }

    private Signature determineSignature(OperationModel data, ExecutableElement spec) {

        boolean isValid = true;

        List<Boolean> canBeBoxingEliminated = new ArrayList<>();

        int numValues = 0;
        boolean hasVariadic = false;

        int numLocalSetters = 0;
        int numLocalSetterRanges = 0;

        // Each specialization should have parameters in the following order:
        // frame, value*, variadic, localSetter*, localSetterRange*
        // All parameters are optional, and the ones with * can be repeated multiple times.
        for (VariableElement param : spec.getParameters()) {
            if (isAssignable(param.asType(), types.Frame)) {
                // nothing, we ignore these
                continue;
            } else if (isAssignable(param.asType(), types.LocalSetter)) {
                if (isDSLParameter(param)) {
                    data.addError(param, "%s parameters must not be annotated with @%s or @%s.",
                                    getSimpleName(types.LocalSetter),
                                    getSimpleName(types.Cached),
                                    getSimpleName(types.Bind));
                    isValid = false;
                }
                if (numLocalSetterRanges > 0) {
                    data.addError(param, "%s parameters must precede %s parameters.",
                                    getSimpleName(types.LocalSetter), getSimpleName(types.LocalSetterRange));
                    isValid = false;
                }
                numLocalSetters++;
            } else if (isAssignable(param.asType(), types.LocalSetterRange)) {
                if (isDSLParameter(param)) {
                    data.addError(param, "%s parameters must not be annotated with @%s or @%s.",
                                    getSimpleName(types.LocalSetterRange),
                                    getSimpleName(types.Cached),
                                    getSimpleName(types.Bind));
                    isValid = false;
                }
                numLocalSetterRanges++;
            } else if (ElementUtils.findAnnotationMirror(param, types.Variadic) != null) {
                if (isDSLParameter(param)) {
                    data.addError(param, "@%s parameters must not be annotated with @%s or @%s.",
                                    getSimpleName(types.Variadic),
                                    getSimpleName(types.Cached),
                                    getSimpleName(types.Bind));
                    isValid = false;
                }
                if (hasVariadic) {
                    data.addError(param, "Multiple variadic parameters not allowed to an operation. Split up the operation if such behaviour is required.");
                    isValid = false;
                }
                if (numLocalSetterRanges > 0 || numLocalSetters > 0) {
                    data.addError(param, "Value parameters must precede %s and %s parameters.",
                                    getSimpleName(types.LocalSetter),
                                    getSimpleName(types.LocalSetterRange));
                    isValid = false;
                }
                canBeBoxingEliminated.add(false);
                numValues++;
                hasVariadic = true;
            } else if (isDSLParameter(param)) {
                // nothing, we ignore these
            } else {
                if (hasVariadic) {
                    data.addError(param, "Non-variadic value parameters must precede variadic parameters.");
                    isValid = false;
                }
                if (numLocalSetterRanges > 0 || numLocalSetters > 0) {
                    data.addError(param, "Value parameters must precede LocalSetter and LocalSetterRange parameters.");
                    isValid = false;
                }
                canBeBoxingEliminated.add(parent.isBoxingEliminated(param.asType()));
                numValues++;
            }
        }

        if (!isValid) {
            return null;
        }

        Signature signature = new Signature();
        signature.valueCount = numValues;
        signature.isVariadic = hasVariadic;
        signature.localSetterCount = numLocalSetters;
        signature.localSetterRangeCount = numLocalSetterRanges;
        signature.valueBoxingElimination = new boolean[numValues];

        for (int i = 0; i < numValues; i++) {
            signature.valueBoxingElimination[i] = canBeBoxingEliminated.get(i);
        }

        // short-circuit ops are never boxing-eliminated
        if (data.kind != OperationKind.CUSTOM_SHORT_CIRCUIT) {
            TypeMirror returnType = spec.getReturnType();
            if (ElementUtils.isVoid(spec.getReturnType())) {
                signature.isVoid = true;
                signature.resultBoxingElimination = false;
            } else if (parent.isBoxingEliminated(returnType)) {
                signature.resultBoxingElimination = true;
                signature.possibleBoxingResults = new HashSet<>(Set.of(returnType));
            } else if (ElementUtils.isObject(returnType)) {
                signature.resultBoxingElimination = false;
                signature.possibleBoxingResults = null;
            } else {
                signature.resultBoxingElimination = false;
                signature.possibleBoxingResults = new HashSet<>(Set.of(context.getType(Object.class)));
            }
        }

        return signature;
    }

    private boolean isDSLParameter(VariableElement param) {
        for (AnnotationMirror mir : param.getAnnotationMirrors()) {
            if (typeEqualsAny(mir.getAnnotationType(), types.Cached, types.CachedLibrary, types.Bind)) {
                return true;
            }
        }
        return false;
    }

    private List<ExecutableElement> findSpecializations(TypeElement te) {
        if (ElementUtils.isObject(te.asType())) {
            return new ArrayList<>();
        }

        List<ExecutableElement> result = findSpecializations(getTypeElement((DeclaredType) te.getSuperclass()));

        for (ExecutableElement ex : ElementFilter.methodsIn(te.getEnclosedElements())) {
            if (ElementUtils.findAnnotationMirror(ex, types.Specialization) != null) {
                result.add(ex);
            }
        }

        return result;
    }

    @Override
    public DeclaredType getAnnotationType() {
        return types.Operation;
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
