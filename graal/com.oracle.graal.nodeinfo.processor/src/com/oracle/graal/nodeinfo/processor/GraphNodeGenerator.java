/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.nodeinfo.processor;

import static com.oracle.graal.nodeinfo.processor.GraphNodeGenerator.NodeRefsType.*;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.*;
import static java.util.Arrays.*;
import static javax.lang.model.element.Modifier.*;

import java.util.*;
import java.util.stream.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import javax.tools.Diagnostic.Kind;

import com.oracle.graal.nodeinfo.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.compiler.*;
import com.oracle.truffle.dsl.processor.java.compiler.Compiler;
import com.oracle.truffle.dsl.processor.java.model.*;

/**
 * Generates the source code for a Node class.
 */
public class GraphNodeGenerator {

    private static final boolean GENERATE_ASSERTIONS = false;

    private final GraphNodeProcessor env;
    private final Types types;
    private final Elements elements;

    private final TypeElement Input;
    private final TypeElement OptionalInput;
    private final TypeElement Successor;

    final TypeElement Node;
    private final TypeElement NodeList;
    private final TypeElement NodeInputList;
    private final TypeElement NodeSuccessorList;
    private final TypeElement Position;

    private final List<VariableElement> inputFields = new ArrayList<>();
    private final List<VariableElement> inputListFields = new ArrayList<>();
    private final List<VariableElement> successorFields = new ArrayList<>();
    private final List<VariableElement> successorListFields = new ArrayList<>();
    private final List<VariableElement> dataFields = new ArrayList<>();
    private final Set<VariableElement> optionalInputs = new HashSet<>();
    private final Map<VariableElement, VariableElement> inputTypes = new HashMap<>();

    private CodeTypeElement genClass;
    private String genClassName;

    public GraphNodeGenerator(GraphNodeProcessor processor) {
        this.env = processor;

        this.types = processor.getProcessingEnv().getTypeUtils();
        this.elements = processor.getProcessingEnv().getElementUtils();

        this.Input = getTypeElement("com.oracle.graal.graph.Node.Input");
        this.OptionalInput = getTypeElement("com.oracle.graal.graph.Node.OptionalInput");
        this.Successor = getTypeElement("com.oracle.graal.graph.Node.Successor");
        this.Node = getTypeElement("com.oracle.graal.graph.Node");
        this.NodeList = getTypeElement("com.oracle.graal.graph.NodeList");
        this.NodeInputList = getTypeElement("com.oracle.graal.graph.NodeInputList");
        this.NodeSuccessorList = getTypeElement("com.oracle.graal.graph.NodeSuccessorList");
        this.Position = getTypeElement("com.oracle.graal.graph.Position");
    }

    @SafeVarargs
    private static Collection<VariableElement> concat(List<VariableElement> fields1, List<VariableElement> fields2, List<VariableElement>... tail) {
        return new AbstractCollection<VariableElement>() {

            @Override
            public Iterator<VariableElement> iterator() {
                Stream<VariableElement> joined = Stream.concat(fields1.stream(), fields2.stream());
                for (List<VariableElement> t : tail) {
                    joined = Stream.concat(joined, t.stream());
                }
                return joined.iterator();
            }

            @Override
            public int size() {
                return fields1.size() + fields2.size();
            }
        };
    }

    /**
     * Returns a type element given a canonical name.
     *
     * @throw {@link NoClassDefFoundError} if a type element does not exist for {@code name}
     */
    public TypeElement getTypeElement(String name) {
        TypeElement typeElement = elements.getTypeElement(name);
        if (typeElement == null) {
            throw new NoClassDefFoundError(name);
        }
        return typeElement;
    }

    public TypeElement getTypeElement(Class<?> cls) {
        return getTypeElement(cls.getName());
    }

    public TypeMirror getType(String name) {
        return getTypeElement(name).asType();
    }

    public TypeMirror getType(Class<?> cls) {
        return ElementUtils.getType(getProcessingEnv(), cls);
    }

    public ProcessingEnvironment getProcessingEnv() {
        return env.getProcessingEnv();
    }

    private static String getGeneratedClassName(TypeElement node) {

        TypeElement typeElement = node;

        String genClassName = typeElement.getSimpleName().toString() + "Gen";
        Element enclosing = typeElement.getEnclosingElement();
        while (enclosing != null) {
            if (enclosing.getKind() == ElementKind.CLASS || enclosing.getKind() == ElementKind.INTERFACE) {
                if (enclosing.getModifiers().contains(Modifier.PRIVATE)) {
                    throw new ElementException(enclosing, "%s %s cannot be private", enclosing.getKind().name().toLowerCase(), enclosing);
                }
                genClassName = enclosing.getSimpleName() + "_" + genClassName;
            } else {
                assert enclosing.getKind() == ElementKind.PACKAGE;
            }
            enclosing = enclosing.getEnclosingElement();
        }
        return genClassName;
    }

    public boolean isAssignableWithErasure(Element from, Element to) {
        TypeMirror fromType = types.erasure(from.asType());
        TypeMirror toType = types.erasure(to.asType());
        return types.isAssignable(fromType, toType);
    }

    private void scanFields(TypeElement node) {
        Compiler compiler = CompilerFactory.getCompiler(node);
        TypeElement currentClazz = node;
        do {
            for (VariableElement field : ElementFilter.fieldsIn(compiler.getEnclosedElementsInDeclarationOrder(currentClazz))) {
                Set<Modifier> modifiers = field.getModifiers();
                if (modifiers.contains(STATIC) || modifiers.contains(TRANSIENT)) {
                    continue;
                }

                List<? extends AnnotationMirror> annotations = field.getAnnotationMirrors();

                boolean isNonOptionalInput = findAnnotationMirror(annotations, Input) != null;
                boolean isOptionalInput = findAnnotationMirror(annotations, OptionalInput) != null;
                boolean isSuccessor = findAnnotationMirror(annotations, Successor) != null;

                if (isNonOptionalInput || isOptionalInput) {
                    if (findAnnotationMirror(annotations, Successor) != null) {
                        throw new ElementException(field, "Field cannot be both input and successor");
                    } else if (isNonOptionalInput && isOptionalInput) {
                        throw new ElementException(field, "Inputs must be either optional or non-optional");
                    } else if (isAssignableWithErasure(field, NodeInputList)) {
                        if (modifiers.contains(FINAL)) {
                            throw new ElementException(field, "Input list field must not be final");
                        }
                        if (modifiers.contains(PUBLIC) || modifiers.contains(PRIVATE)) {
                            throw new ElementException(field, "Input list field must be protected or package-private");
                        }
                        inputListFields.add(field);
                    } else {
                        if (!isAssignableWithErasure(field, Node) && field.getKind() == ElementKind.INTERFACE) {
                            throw new ElementException(field, "Input field type must be an interface or assignable to Node");
                        }
                        if (modifiers.contains(FINAL)) {
                            throw new ElementException(field, "Input field must not be final");
                        }
                        if (modifiers.contains(PUBLIC) || modifiers.contains(PRIVATE)) {
                            throw new ElementException(field, "Input field must be protected or package-private");
                        }
                        inputFields.add(field);
                    }
                    if (isOptionalInput) {
                        inputTypes.put(field, getAnnotationValue(VariableElement.class, findAnnotationMirror(annotations, OptionalInput), "value"));
                        optionalInputs.add(field);
                    } else {
                        inputTypes.put(field, getAnnotationValue(VariableElement.class, findAnnotationMirror(annotations, Input), "value"));
                    }
                } else if (isSuccessor) {
                    if (isAssignableWithErasure(field, NodeSuccessorList)) {
                        if (modifiers.contains(FINAL)) {
                            throw new ElementException(field, "Successor list field must not be final");
                        }
                        if (modifiers.contains(PUBLIC)) {
                            throw new ElementException(field, "Successor list field must not be public");
                        }
                        successorListFields.add(field);
                    } else {
                        if (!isAssignableWithErasure(field, Node)) {
                            throw new ElementException(field, "Successor field must be a Node type");
                        }
                        if (modifiers.contains(FINAL)) {
                            throw new ElementException(field, "Successor field must not be final");
                        }
                        if (modifiers.contains(PUBLIC) || modifiers.contains(PRIVATE)) {
                            throw new ElementException(field, "Successor field must be protected or package-private");
                        }
                        successorFields.add(field);
                    }

                } else {
                    if (isAssignableWithErasure(field, Node) && !field.getSimpleName().contentEquals("Null")) {
                        throw new ElementException(field, "Node field must be annotated with @" + Input.getSimpleName() + ", @" + OptionalInput.getSimpleName() + " or @" + Successor.getSimpleName());
                    }
                    if (isAssignableWithErasure(field, NodeInputList)) {
                        throw new ElementException(field, "NodeInputList field must be annotated with @" + Input.getSimpleName() + " or @" + OptionalInput.getSimpleName());
                    }
                    if (isAssignableWithErasure(field, NodeSuccessorList)) {
                        throw new ElementException(field, "NodeSuccessorList field must be annotated with @" + Successor.getSimpleName());
                    }
                    dataFields.add(field);
                }
            }
            currentClazz = getSuperType(currentClazz);
        } while (!isObject(getSuperType(currentClazz).asType()));
    }

    /**
     * Determines if two parameter lists contain the
     * {@linkplain Types#isSameType(TypeMirror, TypeMirror) same} types.
     */
    private boolean parametersMatch(List<? extends VariableElement> p1, List<? extends VariableElement> p2) {
        if (p1.size() == p2.size()) {
            for (int i = 0; i < p1.size(); i++) {
                if (!types.isSameType(p1.get(i).asType(), p2.get(i).asType())) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Searches a type for a method based on a given name and parameter types.
     */
    private ExecutableElement findMethod(TypeElement type, String name, List<? extends VariableElement> parameters) {
        List<? extends ExecutableElement> methods = ElementFilter.methodsIn(type.getEnclosedElements());
        for (ExecutableElement method : methods) {
            if (method.getSimpleName().toString().equals(name)) {
                if (parametersMatch(method.getParameters(), parameters)) {
                    return method;
                }
            }
        }
        return null;
    }

    enum NodeRefsType {
        Inputs,
        Successors;

        String singular() {
            return name().substring(0, name().length() - 1);
        }
    }

    CodeCompilationUnit process(TypeElement node, boolean constructorsOnly) {
        try {
            return process0(node, constructorsOnly);
        } finally {
            reset();
        }
    }

    private CodeCompilationUnit process0(TypeElement node, boolean constructorsOnly) {

        CodeCompilationUnit compilationUnit = new CodeCompilationUnit();

        PackageElement packageElement = ElementUtils.findPackageElement(node);

        genClassName = getGeneratedClassName(node);
        genClass = new CodeTypeElement(modifiers(FINAL), ElementKind.CLASS, packageElement, genClassName);
        genClass.setSuperClass(node.asType());

        for (ExecutableElement constructor : ElementFilter.constructorsIn(node.getEnclosedElements())) {
            if (constructor.getModifiers().contains(PUBLIC)) {
                throw new ElementException(constructor, "Node class constructor must not be public");
            }

            checkFactoryMethodExists(node, constructor);

            CodeExecutableElement subConstructor = createConstructor(genClass, constructor);
            subConstructor.getModifiers().removeAll(Arrays.asList(PUBLIC, PRIVATE, PROTECTED));
            genClass.add(subConstructor);
        }

        if (!constructorsOnly) {
            DeclaredType generatedNode = (DeclaredType) getType(GeneratedNode.class);
            CodeAnnotationMirror generatedNodeMirror = new CodeAnnotationMirror(generatedNode);
            generatedNodeMirror.setElementValue(generatedNodeMirror.findExecutableElement("value"), new CodeAnnotationValue(node.asType()));
            genClass.getAnnotationMirrors().add(generatedNodeMirror);

            scanFields(node);

            boolean hasInputs = !inputFields.isEmpty() || !inputListFields.isEmpty();
            boolean hasSuccessors = !successorFields.isEmpty() || !successorListFields.isEmpty();

            if (hasInputs || hasSuccessors) {
                createGetNodeAtMethod();
                createGetInputTypeAtMethod();
                createGetNameOfMethod();
                createUpdateOrInitializeNodeAtMethod(false);
                createUpdateOrInitializeNodeAtMethod(true);
                createIsLeafNodeMethod();
                createPositionAccessibleFieldOrderClass(packageElement);

                if (!inputListFields.isEmpty() || !successorListFields.isEmpty()) {
                    createGetNodeListAtPositionMethod();
                    createSetNodeListAtMethod();
                }
            }

            if (hasInputs) {
                createIsOptionalInputAtMethod();
                createGetFirstLevelPositionsMethod(Inputs, inputFields, inputListFields);

                createContainsMethod(Inputs, inputFields, inputListFields);
                createIterableMethod(Inputs);

                CodeTypeElement inputsIteratorClass = createIteratorClass(Inputs, packageElement, inputFields, inputListFields);
                createAllIteratorClass(Inputs, inputsIteratorClass.asType(), packageElement, inputFields, inputListFields);
                createWithModCountIteratorClass(Inputs, inputsIteratorClass.asType(), packageElement);
                createIterableClass(Inputs, packageElement);
                createGetNodeAtMethod(NodeRefsType.Inputs, inputFields);
                createCountMethod(NodeRefsType.Inputs, inputFields.size(), inputListFields.size());
                if (!inputListFields.isEmpty()) {
                    createGetNodeListAtIndexMethod(NodeRefsType.Inputs, inputListFields);
                }
            }

            if (hasSuccessors) {
                createGetFirstLevelPositionsMethod(Successors, successorFields, successorListFields);

                createContainsMethod(Successors, successorFields, successorListFields);
                createIterableMethod(Successors);

                CodeTypeElement successorsIteratorClass = createIteratorClass(Successors, packageElement, successorFields, successorListFields);
                createAllIteratorClass(Successors, successorsIteratorClass.asType(), packageElement, successorFields, successorListFields);
                createWithModCountIteratorClass(Successors, successorsIteratorClass.asType(), packageElement);
                createIterableClass(Successors, packageElement);
                createGetNodeAtMethod(NodeRefsType.Successors, successorFields);
                createCountMethod(NodeRefsType.Successors, successorFields.size(), successorListFields.size());
                if (!successorListFields.isEmpty()) {
                    createGetNodeListAtIndexMethod(NodeRefsType.Successors, successorListFields);
                }
            }
        }
        compilationUnit.add(genClass);
        return compilationUnit;
    }

    /**
     * Checks that a public static factory method named {@code "create"} exists in {@code node}
     * whose signature matches that of a given constructor.
     *
     * @throws ElementException if the check fails
     */
    private void checkFactoryMethodExists(TypeElement node, ExecutableElement constructor) {
        ExecutableElement create = findMethod(node, "create", constructor.getParameters());
        if (create == null) {
            Formatter f = new Formatter();
            f.format("public static %s create(", node.getSimpleName());
            String sep = "";
            Formatter callArgs = new Formatter();
            for (VariableElement v : constructor.getParameters()) {
                f.format("%s%s %s", sep, ElementUtils.getSimpleName(v.asType()), v.getSimpleName());
                callArgs.format("%s%s", sep, v.getSimpleName());
                sep = ", ";
            }
            f.format(") { return USE_GENERATED_NODES ? new %s(%s) : new %s(%s); }", genClassName, callArgs, node.getSimpleName(), callArgs);
            throw new ElementException(constructor, "Missing Node class factory method '%s'", f);
        }
        if (!create.getModifiers().containsAll(asList(PUBLIC, STATIC))) {
            throw new ElementException(constructor, "Node class factory method must be public and static");
        }
    }

    private CodeExecutableElement createConstructor(TypeElement type, ExecutableElement element) {
        CodeExecutableElement executable = CodeExecutableElement.clone(getProcessingEnv(), element);

        // to create a constructor we have to set the return type to null.(TODO needs fix)
        executable.setReturnType(null);
        // we have to set the name manually otherwise <init> is inferred (TODO needs fix)
        executable.setSimpleName(CodeNames.of(type.getSimpleName().toString()));

        CodeTreeBuilder b = executable.createBuilder();
        b.startStatement().startSuperCall();
        for (VariableElement v : element.getParameters()) {
            b.string(v.getSimpleName().toString());
        }
        b.end().end();

        return executable;
    }

    private void reset() {
        inputFields.clear();
        inputListFields.clear();
        successorFields.clear();
        successorListFields.clear();
        dataFields.clear();
        optionalInputs.clear();
        inputTypes.clear();
        genClass = null;
        genClassName = null;
    }

    private CodeVariableElement addParameter(CodeExecutableElement method, TypeMirror type, String name) {
        return addParameter(method, type, name, true);
    }

    private CodeVariableElement addParameter(CodeExecutableElement method, TypeMirror type, String name, boolean checkHiding) {
        CodeVariableElement parameter = new CodeVariableElement(type, name);
        if (checkHiding && hidesField(parameter.getSimpleName().toString())) {
            DeclaredType suppress = (DeclaredType) getType(SuppressWarnings.class);
            CodeAnnotationMirror suppressMirror = new CodeAnnotationMirror(suppress);
            suppressMirror.setElementValue(suppressMirror.findExecutableElement("value"), new CodeAnnotationValue("hiding"));
            parameter.getAnnotationMirrors().add(suppressMirror);
        }
        method.addParameter(parameter);
        return parameter;
    }

    /**
     * Checks that a generated method overrides exactly one method in a super type and that the
     * super type is Node.
     */
    private void checkOnlyInGenNode(CodeExecutableElement method) {
        List<ExecutableElement> overriddenMethods = getDeclaredMethodsInSuperTypes(method.getEnclosingClass(), method.getSimpleName().toString(), method.getParameterTypes());
        for (ExecutableElement overriddenMethod : overriddenMethods) {
            if (!overriddenMethod.getEnclosingElement().equals(Node)) {
                env.message(Kind.WARNING, overriddenMethod, "This method is overridden in a generated subclass will never be called");
            }
        }
    }

    private void createIsLeafNodeMethod() {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), getType(boolean.class), "isLeafNode");
        method.createBuilder().startReturn().string("false").end();
        genClass.add(method);
        checkOnlyInGenNode(method);
    }

    private ExecutableElement createIsOptionalInputAtMethod() {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), getType(boolean.class), "isOptionalInputAt");
        addParameter(method, Position.asType(), "pos");
        CodeTreeBuilder b = method.createBuilder();
        if (GENERATE_ASSERTIONS) {
            b.startAssert().string("pos.isInput()").end();
        }
        if (!optionalInputs.isEmpty()) {
            b.startSwitch().string("pos.getIndex()").end().startBlock();
            int index = 0;
            for (VariableElement f : concat(inputFields, inputListFields)) {
                if (optionalInputs.contains(f)) {
                    b.startCase().string(String.valueOf(index)).end();
                }
                index++;
            }
            b.startStatement().string("return true").end();
            b.end();
        }
        b.startReturn().string("false").end();
        genClass.add(method);
        checkOnlyInGenNode(method);
        return method;
    }

    private ExecutableElement createGetFirstLevelPositionsMethod(NodeRefsType nodeRefsType, List<VariableElement> nodeFields, List<VariableElement> nodeListFields) {
        DeclaredType collectionOfPosition = types.getDeclaredType((TypeElement) types.asElement(getType(Collection.class)), Position.asType());
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), collectionOfPosition, "getFirstLevel" + nodeRefsType);
        CodeTreeBuilder b = method.createBuilder();
        b.startReturn().startNew(getTypeElement("com.oracle.graal.graph.FirstLevelPositionCollection").asType());
        b.string(String.valueOf(nodeFields.size()));
        b.string(String.valueOf(nodeListFields.size()));
        b.string(String.valueOf(nodeRefsType == NodeRefsType.Inputs));
        b.end().end();
        genClass.add(method);
        checkOnlyInGenNode(method);
        return method;
    }

    private CodeTypeElement createIteratorClass(NodeRefsType nodeRefsType, PackageElement packageElement, List<VariableElement> nodeFields, List<VariableElement> nodeListFields) {
        String name = nodeRefsType + "Iterator";
        CodeTypeElement cls = new CodeTypeElement(modifiers(PRIVATE), ElementKind.CLASS, packageElement, name);
        cls.setSuperClass(getType("com.oracle.graal.graph.NodeRefIterator"));

        // Constructor
        CodeExecutableElement ctor = new CodeExecutableElement(Collections.emptySet(), null, name);
        addParameter(ctor, getType(boolean.class), "callForward");
        CodeTreeBuilder b = ctor.createBuilder();
        b.startStatement().startSuperCall();
        b.string(genClassName, ".this");
        b.string(String.valueOf(nodeFields.size()));
        b.string(String.valueOf(nodeListFields.size()));
        b.string(String.valueOf(nodeRefsType == NodeRefsType.Inputs));
        b.end().end();
        b.startIf().string("callForward").end().startBlock();
        b.startStatement().string("forward()").end();
        b.end();
        cls.add(ctor);

        // Methods overriding those in NodeRefIterator
        createGetFieldMethod(cls, nodeFields, Node.asType(), "getNode");
        createGetFieldMethod(cls, nodeListFields, types.getDeclaredType(NodeList, types.getWildcardType(Node.asType(), null)), "getNodeList");
        genClass.add(cls);
        return cls;
    }

    private void createGetFieldMethod(CodeTypeElement cls, List<VariableElement> fields, TypeMirror returnType, String name) {
        if (!fields.isEmpty()) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED, FINAL), returnType, name);
            addParameter(method, getType(int.class), "at");
            CodeTreeBuilder b = method.createBuilder();
            createGetFieldCases(b, fields, returnType, null);
            cls.add(method);
        }
    }

    private void createGetFieldCases(CodeTreeBuilder b, List<VariableElement> fields, TypeMirror returnType, String returnExpressionSuffix) {
        for (int i = 0; i < fields.size(); i++) {
            VariableElement field = fields.get(i);
            b.startIf().string("at == " + i).end().startBlock();
            b.startReturn();
            if (returnExpressionSuffix == null && !isAssignableWithErasure(field, types.asElement(returnType))) {
                b.cast(((DeclaredType) returnType).asElement().getSimpleName().toString());
            }
            b.string(genClassName + ".this." + field.getSimpleName());
            if (returnExpressionSuffix != null) {
                b.string(returnExpressionSuffix);
            }
            b.end();
            b.end();
        }
        b.startThrow().startNew(getType(NoSuchElementException.class)).end().end();
    }

    private void createSetNodeListAtCases(CodeTreeBuilder b, List<VariableElement> fields, TypeMirror returnType, String returnExpressionSuffix) {
        for (int i = 0; i < fields.size(); i++) {
            VariableElement field = fields.get(i);
            b.startIf().string("at == " + i).end().startBlock();
            if (returnExpressionSuffix == null && !isAssignableWithErasure(field, types.asElement(returnType))) {
                b.cast(((DeclaredType) returnType).asElement().getSimpleName().toString());
            }
            b.startStatement();
            b.string(genClassName + ".this." + field.getSimpleName(), " = ");
            b.cast(field.asType(), CodeTreeBuilder.singleString("list"));
            b.end();
            b.end();
        }
    }

    private void createUpdateOrInitializeFieldCases(CodeTreeBuilder b, List<VariableElement> fields, boolean isInitialization, boolean isList) {
        boolean elseIf = false;
        for (int i = 0; i < fields.size(); i++) {
            VariableElement field = fields.get(i);
            String fieldRef = genClassName + ".this." + field.getSimpleName();
            if (!isList) {
                elseIf = b.startIf(elseIf);
                b.string("at == " + i).end().startBlock();
                if (!isInitialization) {
                    b.startStatement().string("Node old = ");
                    if (!isAssignableWithErasure(field, Node)) {
                        b.cast(Node.asType(), CodeTreeBuilder.singleString(fieldRef));
                    } else {
                        b.string(fieldRef);
                    }
                    b.end();
                }
                b.startStatement().string(fieldRef, " = ");
                if (!isAssignableWithErasure(Node, field)) {
                    b.cast(field.asType(), CodeTreeBuilder.singleString("newValue"));
                } else {
                    b.string("newValue");
                }
                b.end();
                if (!isInitialization) {
                    b.startIf().string("pos.isInput()").end().startBlock();
                    b.startStatement().string("updateUsages(old, newValue)").end();
                    b.end();
                    b.startElseBlock();
                    b.startStatement().string("updatePredecessor(old, newValue)").end();
                    b.end();
                }
                b.end();
            } else {
                elseIf = b.startIf(elseIf);
                b.string("at == " + i).end().startBlock();
                DeclaredType nodeListOfNode = types.getDeclaredType(NodeList, types.getWildcardType(Node.asType(), null));
                b.declaration(nodeListOfNode, "list", fieldRef);
                if (!isInitialization) {
                    // if (pos.getSubIndex() < list.size()) {
                    b.startIf().string("pos.getSubIndex() < list.size()").end().startBlock();
                    b.startStatement().string("list.set(pos.getSubIndex(), newValue)").end();
                    b.end();
                    b.startElseBlock();
                }

                b.startWhile().string("list.size() <= pos.getSubIndex()").end().startBlock();
                b.startStatement().string("list.add(null)").end();
                b.end();

                if (isInitialization) {
                    b.startStatement().string("list.initialize(pos.getSubIndex(), newValue)").end();
                } else {
                    b.startStatement().string("list.add(newValue)").end();
                    b.end();
                }

                b.end();
            }
        }
        b.startElseBlock();
        b.startThrow().startNew(getType(NoSuchElementException.class)).end().end();
        b.end();
    }

    private void createPositionAccessibleFieldOrderClass(PackageElement packageElement) {
        CodeTypeElement cls = new CodeTypeElement(modifiers(PUBLIC, STATIC), ElementKind.CLASS, packageElement, "FieldOrder");
        cls.getImplements().add(getType("com.oracle.graal.graph.NodeClass.PositionFieldOrder"));

        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), getType(String[].class), "getOrderedFieldNames");

        addParameter(method, getType(boolean.class), "input", false);

        CodeTreeBuilder b = method.createBuilder();
        b.startIf().string("input").end().startBlock();
        String initializer = concat(inputFields, inputListFields).stream().map(v -> v.getSimpleName().toString()).collect(Collectors.joining("\", \"", "\"", "\""));
        b.startStatement().string("return new String[] {", initializer, "}").end();
        b.end();
        b.startElseBlock();
        initializer = concat(successorFields, successorListFields).stream().map(v -> v.getSimpleName().toString()).collect(Collectors.joining("\", \"", "\"", "\""));
        b.startStatement().string("return new String[] {", initializer, "}").end();
        b.end();
        cls.add(method);

        genClass.add(cls);

    }

    private void createAllIteratorClass(NodeRefsType nodeRefsType, TypeMirror inputsIteratorType, PackageElement packageElement, List<VariableElement> nodeFields, List<VariableElement> nodeListFields) {

        String name = "All" + nodeRefsType + "Iterator";
        CodeTypeElement cls = new CodeTypeElement(modifiers(PRIVATE, FINAL), ElementKind.CLASS, packageElement, name);
        cls.setSuperClass(inputsIteratorType);

        // Constructor
        CodeExecutableElement ctor = new CodeExecutableElement(Collections.emptySet(), null, name);
        CodeTreeBuilder b = ctor.createBuilder();
        b.startStatement().startSuperCall();
        b.string("true");
        b.end().end();
        cls.add(ctor);

        // forward() method
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED), getType(void.class), "forward");
        b = method.createBuilder();
        int nodeFieldsSize = nodeFields.size();
        int nodeListFieldsSize = nodeListFields.size();
        String cond = "index < " + nodeFieldsSize;
        b.startIf().string(cond).end().startBlock();
        b.startStatement().string("index++").end();
        b.startIf().string(cond).end().startBlock();
        b.startStatement().string("return").end();
        b.end();
        b.end();
        b.startElseBlock();
        b.startStatement().string("subIndex++").end();
        b.end();
        int count = nodeFieldsSize + nodeListFieldsSize;
        b.startWhile().string("index < " + count).end().startBlock();
        b.startIf().string("subIndex == 0").end().startBlock();
        b.startStatement().string("list = getNodeList(index - " + nodeFieldsSize + ")").end();
        b.end();
        b.startIf().string("subIndex < list.size()").end().startBlock();
        b.startStatement().string("return").end();
        b.end();
        b.startStatement().string("subIndex = 0").end();
        b.startStatement().string("index++").end();
        b.end();

        cls.add(method);

        genClass.add(cls);
    }

    private void createWithModCountIteratorClass(NodeRefsType nodeRefsType, TypeMirror superType, PackageElement packageElement) {

        String name = nodeRefsType + "WithModCountIterator";
        CodeTypeElement cls = new CodeTypeElement(modifiers(PRIVATE, FINAL), ElementKind.CLASS, packageElement, name);
        cls.setSuperClass(superType);

        // modCount field
        cls.add(new CodeVariableElement(modifiers(PRIVATE, FINAL), getType(int.class), "modCount"));

        // Constructor
        CodeExecutableElement ctor = new CodeExecutableElement(Collections.emptySet(), null, name);
        CodeTreeBuilder b = ctor.createBuilder();
        b.startStatement().startSuperCall();
        b.string("false");
        b.end().end();
        b.startAssert().staticReference(getType("com.oracle.graal.graph.Graph"), "MODIFICATION_COUNTS_ENABLED").end();
        b.startStatement().string("this.modCount = modCount()").end();
        b.startStatement().string("forward()").end();
        cls.add(ctor);

        // hasNext, next and nextPosition methods
        overrideModWithCounterMethod(cls, "hasNext", getType(boolean.class));
        overrideModWithCounterMethod(cls, "next", Node.asType());
        overrideModWithCounterMethod(cls, "nextPosition", Position.asType());

        genClass.add(cls);
    }

    private static void overrideModWithCounterMethod(CodeTypeElement cls, String name, TypeMirror returnType) {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), returnType, name);
        CodeTreeBuilder b = method.createBuilder();
        b.startTryBlock();
        b.startStatement().string("return super." + name + "()").end();
        b.end().startFinallyBlock();
        b.startAssert().string("modCount == modCount() : \"must not be modified\"").end();
        b.end();
        cls.add(method);
    }

    private void createIterableClass(NodeRefsType nodeRefsType, PackageElement packageElement) {

        String name = nodeRefsType + "Iterable";
        CodeTypeElement cls = new CodeTypeElement(modifiers(PRIVATE), ElementKind.CLASS, packageElement, name);
        cls.getImplements().add(getType("com.oracle.graal.graph.NodeClassIterable"));

        // iterator() method
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, FINAL), getType("com.oracle.graal.graph.NodeRefIterator"), "iterator");
        CodeTreeBuilder b = method.createBuilder();
        b.startIf().staticReference(getType("com.oracle.graal.graph.Graph"), "MODIFICATION_COUNTS_ENABLED").end().startBlock();
        b.startStatement().string("return new " + nodeRefsType + "WithModCountIterator()").end();
        b.end();
        b.startElseBlock();
        b.startStatement().string("return new " + nodeRefsType + "Iterator(true)").end();
        b.end();
        cls.add(method);

        // withNullIterator() method
        method = new CodeExecutableElement(modifiers(PUBLIC, FINAL), getType("com.oracle.graal.graph.NodePosIterator"), "withNullIterator");
        b = method.createBuilder();
        b.startStatement().string("return new All" + nodeRefsType + "Iterator()").end();
        cls.add(method);

        // contains(Node) method
        method = new CodeExecutableElement(modifiers(PUBLIC, FINAL), getType(boolean.class), "contains");
        addParameter(method, Node.asType(), "n");
        b = method.createBuilder();
        b.startStatement().string("return " + nodeRefsType.name().toLowerCase() + "Contains(n)").end();
        cls.add(method);
        genClass.add(cls);
    }

    private void createContainsMethod(NodeRefsType nodeRefsType, List<VariableElement> nodeFields, List<VariableElement> nodeListFields) {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, FINAL), getType(boolean.class), nodeRefsType.name().toLowerCase() + "Contains");
        addParameter(method, Node.asType(), "n");
        CodeTreeBuilder b = method.createBuilder();
        for (VariableElement f : nodeFields) {
            b.startIf().string("n == " + f).end().startBlock();
            b.startStatement().string("return true").end();
            b.end();
        }
        for (VariableElement f : nodeListFields) {
            b.startIf().string(f + ".contains(n)").end().startBlock();
            b.startStatement().string("return true").end();
            b.end();
        }
        b.startStatement().string("return false").end();
        genClass.add(method);
        checkOnlyInGenNode(method);
    }

    private static final String API_TAG = "V2";

    private void createIterableMethod(NodeRefsType nodeRefsType) {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, FINAL), getType("com.oracle.graal.graph.NodeClassIterable"), (nodeRefsType == Inputs ? "inputs" : "successors") +
                        API_TAG);
        CodeTreeBuilder b = method.createBuilder();
        b.startStatement().string("return new " + nodeRefsType + "Iterable()").end();
        genClass.add(method);
        checkOnlyInGenNode(method);
    }

    private void createGetNodeAtMethod() {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, FINAL), Node.asType(), "getNodeAt");
        addParameter(method, Position.asType(), "pos");
        CodeTreeBuilder b = method.createBuilder();
        b.startIf().string("pos.isInput()").end().startBlock();
        createGetNodeAt(b, inputFields, inputListFields);
        b.end();
        b.startElseBlock();
        createGetNodeAt(b, successorFields, successorListFields);
        b.end();
        genClass.add(method);
        checkOnlyInGenNode(method);
    }

    private void createCountMethod(NodeRefsType nodeRefsType, int nodesCount, int nodeListsCount) {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, FINAL), getType(int.class), "get" + nodeRefsType + "Count");
        CodeTreeBuilder b = method.createBuilder();

        b.startStatement().string("return " + (nodeListsCount << 16 | nodesCount), " /* (" + nodeListsCount + " << 16 | " + nodesCount + ") */").end();
        genClass.add(method);
        checkOnlyInGenNode(method);
    }

    private void createGetNodeAtMethod(NodeRefsType nodeRefsType, List<VariableElement> nodes) {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, FINAL), Node.asType(), "get" + nodeRefsType.singular() + "NodeAt");
        addParameter(method, getType(int.class), "index");
        CodeTreeBuilder b = method.createBuilder();
        boolean justOne = nodes.size() == 1;
        if (!justOne) {
            b.startSwitch().string("index").end().startBlock();
        } else if (GENERATE_ASSERTIONS) {
            b.startAssert().string("index == 0").end();
        }
        int index = 0;
        for (VariableElement f : nodes) {
            if (!justOne) {
                b.startCase().string(String.valueOf(index)).end();
            }
            b.startReturn();
            if (!isAssignableWithErasure(f, Node)) {
                b.cast(((DeclaredType) Node.asType()).asElement().getSimpleName().toString());
            }
            b.string(genClassName + ".this." + f.getSimpleName());
            b.end();
            index++;
        }
        if (!justOne) {
            b.end();
            b.startThrow().startNew(getType(NoSuchElementException.class)).end().end();
        }
        genClass.add(method);
        checkOnlyInGenNode(method);
    }

    private void createGetNodeListAtIndexMethod(NodeRefsType nodeRefsType, List<VariableElement> nodeLists) {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, FINAL), types.getDeclaredType(NodeList, types.getWildcardType(Node.asType(), null)), "get" +
                        nodeRefsType.singular() + "NodeListAt");
        addParameter(method, getType(int.class), "index");
        CodeTreeBuilder b = method.createBuilder();

        boolean justOne = nodeLists.size() == 1;
        if (!justOne) {
            b.startSwitch().string("index").end().startBlock();
        } else if (GENERATE_ASSERTIONS) {
            b.startAssert().string("index == 0").end();
        }
        int index = 0;
        for (VariableElement f : nodeLists) {
            if (!justOne) {
                b.startCase().string(String.valueOf(index)).end();
            }
            b.startReturn();
            b.string(genClassName + ".this." + f.getSimpleName());
            b.end();
            index++;
        }
        if (!justOne) {
            b.end();
            b.startThrow().startNew(getType(NoSuchElementException.class)).end().end();
        }
        genClass.add(method);
        checkOnlyInGenNode(method);
    }

    private void createGetNodeListAtPositionMethod() {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, FINAL), types.getDeclaredType(NodeList, types.getWildcardType(Node.asType(), null)), "getNodeListAt");
        addParameter(method, Position.asType(), "pos");
        CodeTreeBuilder b = method.createBuilder();
        b.startIf().string("pos.isInput()").end().startBlock();
        createGetNodeListAt(b, inputFields, inputListFields);
        b.end();
        b.startElseBlock();
        createGetNodeListAt(b, successorFields, successorListFields);
        b.end();
        genClass.add(method);
        checkOnlyInGenNode(method);
    }

    private void createSetNodeListAtMethod() {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, FINAL), getType(void.class), "setNodeListAt");

        DeclaredType suppress = (DeclaredType) getType(SuppressWarnings.class);
        CodeAnnotationMirror suppressMirror = new CodeAnnotationMirror(suppress);
        suppressMirror.setElementValue(suppressMirror.findExecutableElement("value"), new CodeAnnotationValue("unchecked"));
        method.getAnnotationMirrors().add(suppressMirror);

        addParameter(method, Position.asType(), "pos");
        addParameter(method, types.getDeclaredType(NodeList, types.getWildcardType(Node.asType(), null)), "list");
        CodeTreeBuilder b = method.createBuilder();
        b.startIf().string("pos.isInput()").end().startBlock();
        createSetNodeListAt(b, inputFields, inputListFields);
        b.end();
        b.startElseBlock();
        createSetNodeListAt(b, successorFields, successorListFields);
        b.end();
        genClass.add(method);
        checkOnlyInGenNode(method);
    }

    private void createGetNameOfMethod() {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, FINAL), getType(String.class), "getNameOf");
        addParameter(method, Position.asType(), "pos");
        CodeTreeBuilder b = method.createBuilder();

        b.startIf().string("pos.isInput()").end().startBlock();
        createGetNameOf(b, inputFields, inputListFields);
        b.end();
        b.startElseBlock();
        createGetNameOf(b, successorFields, successorListFields);
        b.end();
        genClass.add(method);
        checkOnlyInGenNode(method);
    }

    private void createGetInputTypeAtMethod() {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, FINAL), getType(InputType.class), "getInputTypeAt");
        addParameter(method, Position.asType(), "pos");
        CodeTreeBuilder b = method.createBuilder();
        if (GENERATE_ASSERTIONS) {
            b.startAssert().string("pos.isInput()").end();
        }
        boolean hasNodes = !inputFields.isEmpty();
        boolean hasNodeLists = !inputListFields.isEmpty();
        if (hasNodeLists || hasNodes) {
            int index = 0;
            for (VariableElement f : concat(inputFields, inputListFields)) {
                b.startIf().string("pos.getIndex() == " + index).end().startBlock();
                b.startStatement().string("return ").staticReference(getType(InputType.class), inputTypes.get(f).getSimpleName().toString()).end();
                b.end();
                index++;
            }
        }
        b.startThrow().startNew(getType(NoSuchElementException.class)).end().end();
        genClass.add(method);
        checkOnlyInGenNode(method);
    }

    private boolean hidesField(String name) {
        for (VariableElement field : concat(inputFields, inputListFields, successorFields, successorListFields, dataFields)) {
            if (field.getSimpleName().contentEquals(name)) {
                return true;
            }
        }
        return false;
    }

    private void createUpdateOrInitializeNodeAtMethod(boolean isInitialization) {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, FINAL), getType(void.class), (isInitialization ? "initialize" : "update") + "NodeAt");
        addParameter(method, Position.asType(), "pos");
        addParameter(method, Node.asType(), "newValue");
        CodeTreeBuilder b = method.createBuilder();
        b.startIf().string("pos.isInput()").end().startBlock();
        createUpdateOrInitializeNodeAt(b, inputFields, inputListFields, isInitialization);
        b.end();
        b.startElseBlock();
        createUpdateOrInitializeNodeAt(b, successorFields, successorListFields, isInitialization);
        b.end();
        genClass.add(method);
        checkOnlyInGenNode(method);
    }

    private void createGetNodeAt(CodeTreeBuilder b, List<VariableElement> nodes, List<VariableElement> nodeLists) {
        boolean hasNodes = !nodes.isEmpty();
        boolean hasNodeLists = !nodeLists.isEmpty();
        if (!hasNodeLists && !hasNodes) {
            b.startThrow().startNew(getType(NoSuchElementException.class)).end().end();
        } else {
            if (hasNodes) {
                if (!hasNodeLists) {
                    if (GENERATE_ASSERTIONS) {
                        b.startAssert().string("pos.getSubIndex() == NOT_ITERABLE").end();
                    }
                } else {
                    b.startIf().string("pos.getSubIndex() == NOT_ITERABLE").end().startBlock();
                }
                b.declaration("int", "at", "pos.getIndex()");
                createGetFieldCases(b, nodes, Node.asType(), null);
                if (hasNodeLists) {
                    b.end();
                }
            }

            if (hasNodeLists) {
                if (!hasNodes) {
                    if (GENERATE_ASSERTIONS) {
                        b.startAssert().string("pos.getSubIndex() != NOT_ITERABLE").end();
                    }
                } else {
                    b.startElseBlock();
                }
                b.declaration("int", "at", "pos.getIndex() - " + nodes.size());
                createGetFieldCases(b, nodeLists, Node.asType(), ".get(pos.getSubIndex())");
                if (hasNodes) {
                    b.end();
                }
            }
        }
    }

    private void createGetNodeListAt(CodeTreeBuilder b, List<VariableElement> nodes, List<VariableElement> nodeLists) {
        boolean hasNodeLists = !nodeLists.isEmpty();
        if (!hasNodeLists) {
            b.startThrow().startNew(getType(NoSuchElementException.class)).end().end();
        } else {
            if (GENERATE_ASSERTIONS) {
                b.startAssert().string("pos.getSubIndex() == NODE_LIST").end();
            }
            b.declaration("int", "at", "pos.getIndex() - " + nodes.size());
            createGetFieldCases(b, nodeLists, Node.asType(), "");
        }
    }

    private void createSetNodeListAt(CodeTreeBuilder b, List<VariableElement> nodes, List<VariableElement> nodeLists) {
        boolean hasNodeLists = !nodeLists.isEmpty();
        if (!hasNodeLists) {
            b.startThrow().startNew(getType(NoSuchElementException.class)).end().end();
        } else {
            if (GENERATE_ASSERTIONS) {
                b.startAssert().string("pos.getSubIndex() == NODE_LIST").end();
            }
            b.declaration("int", "at", "pos.getIndex() - " + nodes.size());
            createSetNodeListAtCases(b, nodeLists, Node.asType(), "");
        }
    }

    private void createGetNameOf(CodeTreeBuilder b, List<VariableElement> nodes, List<VariableElement> nodeLists) {
        boolean hasNodes = !nodes.isEmpty();
        boolean hasNodeLists = !nodeLists.isEmpty();
        if (hasNodeLists || hasNodes) {
            int index = 0;
            for (VariableElement f : nodes) {
                b.startIf().string("pos.getIndex() == " + index).end().startBlock();
                b.startStatement().string("return \"" + f.getSimpleName() + "\"").end();
                b.end();
                index++;
            }
            for (VariableElement f : nodeLists) {
                b.startIf().string("pos.getIndex() == " + index).end().startBlock();
                b.startStatement().string("return \"" + f.getSimpleName() + "\"").end();
                b.end();
                index++;
            }
        }
        b.startThrow().startNew(getType(NoSuchElementException.class)).end().end();
    }

    private void createUpdateOrInitializeNodeAt(CodeTreeBuilder b, List<VariableElement> nodes, List<VariableElement> nodeLists, boolean isInitialization) {
        boolean hasNodes = !nodes.isEmpty();
        boolean hasNodeLists = !nodeLists.isEmpty();
        if (nodes.isEmpty() && nodeLists.isEmpty()) {
            b.startThrow().startNew(getType(NoSuchElementException.class)).end().end();
        } else {
            if (hasNodes) {
                if (!hasNodeLists) {
                    if (GENERATE_ASSERTIONS) {
                        b.startAssert().string("pos.getSubIndex() == NOT_ITERABLE").end();
                    }
                } else {
                    b.startIf().string("pos.getSubIndex() == NOT_ITERABLE").end().startBlock();
                }
                b.declaration("int", "at", "pos.getIndex()");
                createUpdateOrInitializeFieldCases(b, nodes, isInitialization, false);
                if (hasNodeLists) {
                    b.end();
                }
            }

            if (hasNodeLists) {
                if (!hasNodes) {
                    if (GENERATE_ASSERTIONS) {
                        b.startAssert().string("pos.getSubIndex() != NOT_ITERABLE").end();
                    }
                } else {
                    b.startElseBlock();
                }
                b.declaration("int", "at", "pos.getIndex() - " + nodes.size());
                createUpdateOrInitializeFieldCases(b, nodeLists, isInitialization, true);
                if (hasNodes) {
                    b.end();
                }
            }
        }
    }
}
