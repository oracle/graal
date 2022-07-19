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
package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.Collection;
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

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.DeclaredCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedPackageElement;
import com.oracle.truffle.dsl.processor.model.MessageContainer.Message;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData.MethodProperties;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData.ParameterKind;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;
import com.oracle.truffle.dsl.processor.parser.NodeParser;

public class SingleOperationParser extends AbstractParser<SingleOperationData> {

    private final OperationsData parentData;
    private final AnnotationMirror proxyMirror;

    private static final String NODE = "Node";
    private final boolean isShortCircuit;

    public SingleOperationParser(OperationsData parentData) {
        this(parentData, null, false);
    }

    public SingleOperationParser(OperationsData parentData, AnnotationMirror proxyMirror) {
        this(parentData, proxyMirror, false);
    }

    public SingleOperationParser(OperationsData parentData, AnnotationMirror proxyMirror, boolean isShortCircuit) {
        this.parentData = parentData;
        this.proxyMirror = proxyMirror;
        this.isShortCircuit = isShortCircuit;
    }

    @Override
    protected SingleOperationData parse(Element element, List<AnnotationMirror> mirror) {

        TypeElement te;
        String name;
        SingleOperationData data;

        if (element == null) {
            if (proxyMirror == null) {
                throw new AssertionError();
            }

            AnnotationValue proxyTypeValue;
            if (isShortCircuit) {
                proxyTypeValue = ElementUtils.getAnnotationValue(proxyMirror, "booleanConverter");
            } else {
                proxyTypeValue = ElementUtils.getAnnotationValue(proxyMirror, "value");
            }

            TypeMirror proxyType = (TypeMirror) proxyTypeValue.getValue();
            if (proxyType.getKind() != TypeKind.DECLARED) {
                parentData.addError(proxyMirror, proxyTypeValue, "@OperationProxy'ed type must be a class, not %s", proxyType);
            }

            te = context.getTypeElement((DeclaredType) proxyType);

            if (isShortCircuit) {
                name = (String) ElementUtils.getAnnotationValue(proxyMirror, "name").getValue();
            } else {
                AnnotationValue nameFromAnnot = ElementUtils.getAnnotationValue(proxyMirror, "operationName", false);

                if (nameFromAnnot == null) {
                    String nameFromType = te.getSimpleName().toString();
                    // strip the `Node' suffix
                    if (nameFromType.endsWith(NODE)) {
                        name = nameFromType.substring(0, nameFromType.length() - NODE.length());
                    } else {
                        name = nameFromType;
                    }
                } else {
                    name = (String) nameFromAnnot.getValue();
                }
            }

            data = new SingleOperationData(context, null, proxyMirror, parentData, name, isShortCircuit);

        } else {
            if (proxyMirror != null) {
                throw new AssertionError();
            }

            if (!(element instanceof TypeElement)) {
                parentData.addError(element, "@Operation can only be attached to a type");
                return null;
            }

            te = (TypeElement) element;
            if (isShortCircuit) {
                name = (String) ElementUtils.getAnnotationValue(proxyMirror, "name").getValue();
            } else {
                name = te.getSimpleName().toString();
            }

            data = new SingleOperationData(context, te, null, parentData, name, false);
        }

        List<ExecutableElement> operationFunctions = new ArrayList<>();

        if (proxyMirror == null) {
            // @Operation annotated type

            if (!te.getModifiers().containsAll(Set.of(Modifier.STATIC, Modifier.FINAL))) {
                data.addError("@Operation annotated class must be declared static and final");
            }

            if (te.getModifiers().contains(Modifier.PRIVATE)) {
                data.addError("@Operation annotated class must not be declared private");
            }

            if (!ElementUtils.isObject(te.getSuperclass()) || !te.getInterfaces().isEmpty()) {
                data.addError("@Operation annotated class must not extend/implement anything. Inheritance is not supported.");
            }

            for (Element el : te.getEnclosedElements()) {
                if (el.getModifiers().contains(Modifier.PRIVATE)) {
                    // ignore everything private
                    continue;
                }

                if (el.getKind() != ElementKind.CONSTRUCTOR && !el.getModifiers().contains(Modifier.STATIC)) {
                    data.addError(el, "@Operation annotated class must not contain non-static members");
                }

                operationFunctions.addAll(findSpecializations(te.getEnclosedElements()));
            }
        } else {
            // proxied node

            for (ExecutableElement cel : findSpecializations(te.getEnclosedElements())) {
                if (!cel.getModifiers().contains(Modifier.STATIC)) {
                    data.addError("@OperationProxy'ed class must have all its specializations static. Use @Bind(\"this\") parameter if you need a Node instance.");
                }

                operationFunctions.add(cel);
            }
        }

        if (operationFunctions.isEmpty()) {
            data.addError("Operation contains no specializations");
            return data;
        }

        MethodProperties props = processMethod(data, operationFunctions.get(0));
        boolean isVariadic = props.isVariadic;

        for (ExecutableElement fun : operationFunctions) {
            MethodProperties props2 = processMethod(data, fun);
            props2.checkMatches(data, props);
            data.getThrowDeclarations().addAll(fun.getThrownTypes());
        }

        if (isShortCircuit && (props.numStackValues != 1 || props.isVariadic || !props.returnsValue)) {
            data.addError("Boolean converter must take exactly one argument, not be variadic, and return a value");
        }

        if (data.hasErrors()) {
            return data;
        }

        data.setMainProperties(props);

        CodeTypeElement clonedType = cloneTypeHierarchy(te, ct -> {
            // remove NodeChild annotations
            ct.getAnnotationMirrors().removeIf(m -> ElementUtils.typeEquals(m.getAnnotationType(), types.NodeChild) || ElementUtils.typeEquals(m.getAnnotationType(), types.NodeChildren));
            // remove GenerateUncached annotations - we do not care
            ct.getAnnotationMirrors().removeIf(m -> ElementUtils.typeEquals(m.getAnnotationType(), types.GenerateUncached));

            // remove all non-static or private elements
            // this includes all the execute methods
            ct.getEnclosedElements().removeIf(e -> !e.getModifiers().contains(Modifier.STATIC) || e.getModifiers().contains(Modifier.PRIVATE));
        });

        if (proxyMirror == null || isShortCircuit) {
            clonedType.setSuperClass(types.Node);
        }

        if (!isVariadic) {
            addBoxingEliminationNodeChildAnnotations(props, clonedType);
        }

        if (parentData.isGenerateUncached()) {
            clonedType.getAnnotationMirrors().add(new CodeAnnotationMirror(types.GenerateUncached));
            clonedType.add(createExecuteUncachedMethod(props));
        }

        clonedType.add(createExecuteMethod(props, isVariadic));

        NodeData nodeData = NodeParser.createOperationParser().parse(clonedType, false);

        if (nodeData == null) {
            data.addError("Could not parse invalid node: " + te.getSimpleName());
            return data;
        }

        if (proxyMirror != null && nodeData.hasErrors()) {
            for (Message m : nodeData.collectMessages()) {
                Message nm = new Message(proxyMirror, null, null, m.getOriginalContainer(), m.getText(), m.getKind());
                data.getMessages().add(nm);
            }
        } else {
            nodeData.redirectMessagesOnGeneratedElements(data);
        }
        data.setNodeData(nodeData);

        // replace the default node type system with Operations one if we have it
        if (nodeData.getTypeSystem().isDefault() && parentData.getTypeSystem() != null) {
            nodeData.setTypeSystem(parentData.getTypeSystem());
        }

        return data;
    }

    private void addBoxingEliminationNodeChildAnnotations(MethodProperties props, CodeTypeElement ct) {
        int i = 0;
        int localI = 0;
        CodeTypeElement childType = createRegularNodeChild();
        CodeAnnotationMirror repAnnotation = new CodeAnnotationMirror(types.NodeChildren);
        List<CodeAnnotationValue> anns = new ArrayList<>();
        for (ParameterKind param : props.parameters) {
            CodeAnnotationMirror ann = new CodeAnnotationMirror(types.NodeChild);
            if (param == ParameterKind.STACK_VALUE) {
                ann.setElementValue("value", new CodeAnnotationValue("$child" + i));
                ann.setElementValue("type", new CodeAnnotationValue(new DeclaredCodeTypeMirror(childType)));
                anns.add(new CodeAnnotationValue(ann));
                i++;
            } else if (param == ParameterKind.LOCAL_SETTER) {
                ann.setElementValue("value", new CodeAnnotationValue("$localRef" + localI));
                ann.setElementValue("type", new CodeAnnotationValue(new DeclaredCodeTypeMirror(createLocalSetterNodeChild())));
                anns.add(new CodeAnnotationValue(ann));
                localI++;
            } else if (param == ParameterKind.LOCAL_SETTER_ARRAY) {
                ann.setElementValue("value", new CodeAnnotationValue("$localRefArray"));
                ann.setElementValue("type", new CodeAnnotationValue(new DeclaredCodeTypeMirror(createLocalSetterArrayNodeChild())));
                anns.add(new CodeAnnotationValue(ann));
            }
        }
        repAnnotation.setElementValue("value", new CodeAnnotationValue(anns));
        ct.addAnnotationMirror(repAnnotation);
    }

    private CodeExecutableElement createExecuteMethod(MethodProperties props, boolean isVariadic) {

        Class<?> resType;
        if (isShortCircuit) {
            resType = boolean.class;
        } else if (props.returnsValue) {
            resType = Object.class;
        } else {
            resType = void.class;
        }
        CodeExecutableElement metExecute = new CodeExecutableElement(
                        Set.of(Modifier.PUBLIC, Modifier.ABSTRACT),
                        context.getType(resType), "execute");

        metExecute.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));

        if (isVariadic) {
            int i = 0;
            for (ParameterKind param : props.parameters) {
                switch (param) {
                    case STACK_VALUE:
                        metExecute.addParameter(new CodeVariableElement(context.getType(Object.class), "arg" + i));
                        break;
                    case VARIADIC:
                        metExecute.addParameter(new CodeVariableElement(context.getType(Object[].class), "arg" + i));
                        break;
                    case LOCAL_SETTER:
                    case LOCAL_SETTER_ARRAY:
                    case VIRTUAL_FRAME:
                        break;
                    default:
                        throw new UnsupportedOperationException("" + param);
                }
                i++;
            }
        }
        return metExecute;
    }

    private CodeExecutableElement createExecuteUncachedMethod(MethodProperties props) {

        Class<?> resType;
        if (isShortCircuit) {
            resType = boolean.class;
        } else if (props.returnsValue) {
            resType = Object.class;
        } else {
            resType = void.class;
        }
        CodeExecutableElement metExecute = new CodeExecutableElement(
                        Set.of(Modifier.PUBLIC, Modifier.ABSTRACT),
                        context.getType(resType), "executeUncached");

        metExecute.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));

        int i = 0;
        int lr = 0;
        for (ParameterKind param : props.parameters) {
            switch (param) {
                case STACK_VALUE:
                    metExecute.addParameter(new CodeVariableElement(context.getType(Object.class), "arg" + i));
                    break;
                case VARIADIC:
                    metExecute.addParameter(new CodeVariableElement(context.getType(Object[].class), "arg" + i));
                    break;
                case LOCAL_SETTER:
                    metExecute.addParameter(new CodeVariableElement(types.LocalSetter, "localRef" + (lr++)));
                    break;
                case LOCAL_SETTER_ARRAY:
                    metExecute.addParameter(new CodeVariableElement(types.LocalSetterRange, "localRefs"));
                    break;
                case VIRTUAL_FRAME:
                    break;
                default:
                    throw new UnsupportedOperationException("" + param);
            }
            i++;
        }

        return metExecute;
    }

    @Override
    public DeclaredType getAnnotationType() {
        return types.Operation;
    }

    private MethodProperties processMethod(SingleOperationData data, ExecutableElement method) {
        boolean isVariadic = false;
        int numLocalSetters = 0;
        List<ParameterKind> parameters = new ArrayList<>();

        for (VariableElement param : method.getParameters()) {
            if (isVariadicParameter(param)) {
                if (isVariadic) {
                    data.addError(method, "Multiple @Variadic arguments not allowed");
                }
                if (numLocalSetters != 0) {
                    data.addError(param, "Value arguments after LocalSetter not allowed");
                }
                isVariadic = true;
                parameters.add(ParameterKind.VARIADIC);
            } else if (ElementUtils.typeEquals(param.asType(), types.LocalSetter)) {
                parameters.add(ParameterKind.LOCAL_SETTER);
                numLocalSetters++;
            } else if (ElementUtils.typeEquals(param.asType(), types.LocalSetterRange)) {
                parameters.add(ParameterKind.LOCAL_SETTER_ARRAY);
                if (numLocalSetters != 0) {
                    data.addError(param, "Mixing regular and array local setters not allowed");
                }
                numLocalSetters = -1;
            } else if (!isIgnoredParameter(param)) {
                if (isVariadic) {
                    data.addError(method, "Value arguments after @Variadic not allowed");
                }
                if (numLocalSetters > 0) {
                    data.addError(param, "Value arguments after LocalSetter not allowed");
                }
                if (ElementUtils.typeEquals(param.asType(), types.Frame) || ElementUtils.typeEquals(param.asType(), types.VirtualFrame)) {
                    parameters.add(ParameterKind.VIRTUAL_FRAME);
                } else {
                    parameters.add(ParameterKind.STACK_VALUE);
                }
            }
        }

        boolean returnsValue = method.getReturnType().getKind() != TypeKind.VOID;

        MethodProperties props = new MethodProperties(method, parameters, isVariadic, returnsValue, numLocalSetters);

        return props;
    }

    private boolean isIgnoredParameter(VariableElement param) {
        if (ElementUtils.findAnnotationMirror(param, types.Cached) != null) {
            return true;
        } else if (ElementUtils.findAnnotationMirror(param, types.CachedLibrary) != null) {
            return true;
        } else if (ElementUtils.findAnnotationMirror(param, types.Bind) != null) {
            return true;
        }

        return false;
    }

    private static final String GENERIC_EXECUTE_NAME = "Generic";

    private CodeTypeElement createRegularNodeChild() {

        CodeTypeElement result = new CodeTypeElement(Set.of(Modifier.PUBLIC, Modifier.ABSTRACT), ElementKind.CLASS, new GeneratedPackageElement("p"), "C");
        result.setSuperClass(types.Node);

        result.add(createChildExecuteMethod(GENERIC_EXECUTE_NAME, context.getType(Object.class)));

        for (TypeKind unboxKind : parentData.getBoxingEliminatedTypes()) {
            result.add(createChildExecuteMethod(unboxKind.name(), new CodeTypeMirror(unboxKind)));
        }

        return result;
    }

    private CodeTypeElement createLocalSetterNodeChild() {
        CodeTypeElement result = new CodeTypeElement(Set.of(Modifier.PUBLIC, Modifier.ABSTRACT), ElementKind.CLASS, new GeneratedPackageElement("p"), "C");
        result.setSuperClass(types.Node);

        result.add(createChildExecuteMethod(GENERIC_EXECUTE_NAME, types.LocalSetter));

        return result;
    }

    private CodeTypeElement createLocalSetterArrayNodeChild() {
        CodeTypeElement result = new CodeTypeElement(Set.of(Modifier.PUBLIC, Modifier.ABSTRACT), ElementKind.CLASS, new GeneratedPackageElement("p"), "C");
        result.setSuperClass(types.Node);

        result.add(createChildExecuteMethod(GENERIC_EXECUTE_NAME, types.LocalSetterRange));

        return result;
    }

    private CodeExecutableElement createChildExecuteMethod(String name, TypeMirror retType) {
        CodeExecutableElement result = new CodeExecutableElement(Set.of(Modifier.PUBLIC, Modifier.ABSTRACT), retType, "execute" + name);
        // result.addParameter(new CodeVariableElement(types.Frame, "frame"));

        if (!GENERIC_EXECUTE_NAME.equals(name)) {
            result.addThrownType(types.UnexpectedResultException);
        }
        return result;
    }

    private boolean isVariadicParameter(VariableElement param) {
        return ElementUtils.findAnnotationMirror(param, types.Variadic) != null;
    }

    private boolean isSpecializationFunction(ExecutableElement el) {
        return ElementUtils.findAnnotationMirror(el, types.Specialization) != null;
    }

    private CodeTypeElement cloneTypeHierarchy(TypeElement element, Consumer<CodeTypeElement> mapper) {
        CodeTypeElement result = CodeTypeElement.cloneShallow(element);
        if (!ElementUtils.isObject(element.getSuperclass())) {
            result.setSuperClass(cloneTypeHierarchy(context.getTypeElement((DeclaredType) element.getSuperclass()), mapper).asType());
        }

        mapper.accept(result);

        return result;
    }

    private List<ExecutableElement> findSpecializations(Collection<? extends Element> elements) {
        return elements.stream().filter(x -> x instanceof ExecutableElement).map(x -> (ExecutableElement) x).filter(this::isSpecializationFunction).collect(Collectors.toUnmodifiableList());
    }
}
