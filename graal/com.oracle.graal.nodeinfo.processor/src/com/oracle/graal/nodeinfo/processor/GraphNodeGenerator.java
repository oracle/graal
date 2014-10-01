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

    @SuppressWarnings("unused") private static final boolean GENERATE_ASSERTIONS = false;

    private final GraphNodeProcessor env;
    private final Types types;
    private final Elements elements;

    private final TypeElement Input;
    private final TypeElement OptionalInput;
    private final TypeElement Successor;

    final TypeElement Node;
    @SuppressWarnings("unused") private final TypeElement NodeList;
    private final TypeElement NodeInputList;
    private final TypeElement NodeSuccessorList;
    private final TypeElement ValueNumberable;
    @SuppressWarnings("unused") private final TypeElement Position;

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
        this.ValueNumberable = getTypeElement("com.oracle.graal.graph.Node.ValueNumberable");
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
                    if (modifiers.contains(PUBLIC) && !modifiers.contains(FINAL)) {
                        throw new ElementException(field, "Data field must be final if public otherwise it must be protected or package-private");
                    } else if (modifiers.contains(PRIVATE)) {
                        throw new ElementException(field, "Data field must be protected or package-private");
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
                createIsLeafNodeMethod();
            }

            createValueNumberMethod(node);
            createValueEqualsMethod();
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

    private void createValueNumberMethod(TypeElement node) {
        if (isAssignableWithErasure(node, ValueNumberable)) {
            genClass.add(new CodeVariableElement(modifiers(PRIVATE), getType(int.class), "valueNumber"));

            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), getType(int.class), "getValueNumber");
            CodeTreeBuilder b = method.createBuilder();
            b.startIf().string("valueNumber == 0").end().startBlock();
            b.startStatement().string("int number = " + node.hashCode()).end();
            for (VariableElement f : dataFields) {
                String fname = f.getSimpleName().toString();
                switch (f.asType().getKind()) {
                    case BOOLEAN:
                        b.startIf().string(fname).end().startBlock();
                        b.startStatement().string("number += 7").end();
                        b.end();
                        break;
                    case BYTE:
                    case SHORT:
                    case CHAR:
                    case INT:
                        b.startStatement().string("number += 13 * ", fname).end();
                        break;
                    case FLOAT:
                        b.startStatement().string("number += 17 * Float.floatToRawIntBits(", fname, ")").end();
                        break;
                    case LONG:
                        b.startStatement().string("number += 19 * ", fname + " ^ (", fname, " >>> 32)").end();
                        break;
                    case DOUBLE:
                        b.startStatement().string("long longValue = Double.doubleToRawLongBits(", fname, ")").end();
                        b.startStatement().string("number += 23 * longValue ^ (longValue >>> 32)").end();
                        break;
                    case ARRAY:
                        if (((ArrayType) f.asType()).getComponentType().getKind().isPrimitive()) {
                            b.startStatement().string("number += 31 * Arrays.hashCode(", fname, ")").end();
                        } else {
                            b.startStatement().string("number += 31 * Arrays.deepHashCode(", fname, ")").end();
                        }
                        break;
                    default:
                        b.startIf().string(fname, " != null").end().startBlock();
                        b.startStatement().string("number += 29 * ", fname + ".hashCode()").end();
                        b.end();
                        break;
                }
            }
            b.startStatement().string("valueNumber = number").end();
            b.end();
            b.startReturn().string("valueNumber").end();
            genClass.add(method);
            checkOnlyInGenNode(method);
        }
    }

    private void createValueEqualsMethod() {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), getType(boolean.class), "valueEqualsGen");
        addParameter(method, Node.asType(), "other");
        CodeTreeBuilder b = method.createBuilder();
        if (!dataFields.isEmpty()) {
            String other = "o";
            b.declaration(genClassName, other, "(" + genClassName + ") other");

            for (VariableElement f : dataFields) {
                String fname = f.getSimpleName().toString();
                switch (f.asType().getKind()) {
                    case BOOLEAN:
                    case BYTE:
                    case SHORT:
                    case CHAR:
                    case INT:
                    case FLOAT:
                    case LONG:
                    case DOUBLE:
                        b.startIf().string(other, ".", fname, " != ", fname).end().startBlock();
                        b.startStatement().string("return false").end();
                        b.end();
                        break;
                    case ARRAY:
                        if (((ArrayType) f.asType()).getComponentType().getKind().isPrimitive()) {
                            b.startIf().string("!").type(getType(Arrays.class)).string(".equals(", other, ".", fname, ", ", fname, ")").end().startBlock();
                        } else {
                            b.startIf().string("!").type(getType(Arrays.class)).string(".deepEquals(", other, ".", fname, ", ", fname, ")").end().startBlock();
                        }
                        b.startStatement().string("return false").end();
                        b.end();
                        break;
                    default:
                        b.startIf().string("!").type(getType(Objects.class)).string(".equals(", other, ".", fname, ", ", fname, ")").end().startBlock();
                        b.startStatement().string("return false").end();
                        b.end();
                        break;
                }
            }
        }
        b.startReturn().string("true").end();
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
}
