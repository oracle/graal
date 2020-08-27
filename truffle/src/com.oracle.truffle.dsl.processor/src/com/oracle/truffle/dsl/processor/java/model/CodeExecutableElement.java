/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.java.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.java.ElementUtils;

public class CodeExecutableElement extends CodeElement<Element> implements ExecutableElement {

    private final List<TypeMirror> throwables = new ArrayList<>();
    private final List<VariableElement> parameters = parentableList(this, new ArrayList<VariableElement>());
    private final List<TypeParameterElement> typeParameters = parentableList(this, new ArrayList<TypeParameterElement>());

    private TypeMirror returnType;
    private Name name;

    private CodeTree bodyTree;
    private String body;
    private AnnotationValue defaultValue;
    private boolean varArgs;
    private CodeTree docTree;

    public CodeExecutableElement(TypeMirror returnType, String name) {
        super(ElementUtils.modifiers());
        this.returnType = returnType;
        this.name = CodeNames.of(name);
    }

    public CodeExecutableElement(Set<Modifier> modifiers, TypeMirror returnType, String name, CodeVariableElement... parameters) {
        super(modifiers);
        this.returnType = returnType;
        this.name = CodeNames.of(name);

        for (CodeVariableElement codeParameter : parameters) {
            addParameter(codeParameter);
        }
    }

    public void setVisibility(Modifier visibility) {
        ElementUtils.setVisibility(getModifiers(), visibility);
    }

    public Modifier getVisibility() {
        return ElementUtils.getVisibility(getModifiers());
    }

    /* Support JDK8 langtools. */
    public boolean isDefault() {
        return false;
    }

    @Override
    public List<TypeMirror> getThrownTypes() {
        return throwables;
    }

    @Override
    public TypeMirror asType() {
        return returnType;
    }

    @Override
    public ElementKind getKind() {
        if (getReturnType() == null) {
            return ElementKind.CONSTRUCTOR;
        } else {
            return ElementKind.METHOD;
        }
    }

    @Override
    public List<TypeParameterElement> getTypeParameters() {
        return typeParameters;
    }

    public void setVarArgs(boolean varargs) {
        this.varArgs = varargs;
    }

    @Override
    public boolean isVarArgs() {
        return varArgs;
    }

    public void setDefaultValue(AnnotationValue defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public AnnotationValue getDefaultValue() {
        return defaultValue;
    }

    @Override
    public Name getSimpleName() {
        return name;
    }

    public CodeTreeBuilder getBuilder() {
        CodeTree tree = this.bodyTree;
        return createBuilder().tree(tree);
    }

    public CodeTreeBuilder createDocBuilder() {
        CodeTreeBuilder builder = new CodeTreeBuilder(null);
        builder.setEnclosingElement(this);
        this.docTree = builder.getTree();
        return builder;
    }

    public CodeTreeBuilder createBuilder() {
        CodeTreeBuilder builder = new CodeTreeBuilder(null);
        builder.setEnclosingElement(this);
        this.bodyTree = builder.getTree();
        this.body = null;
        return builder;
    }

    public CodeTreeBuilder appendBuilder() {
        CodeTreeBuilder builder = new CodeTreeBuilder(null);
        builder.setEnclosingElement(this);
        if (bodyTree != null) {
            builder.tree(bodyTree);
        }
        this.bodyTree = builder.getTree();
        this.body = null;
        return builder;
    }

    public CodeTree getDocTree() {
        return docTree;
    }

    public void setDocTree(CodeTree docTree) {
        this.docTree = docTree;
    }

    public void setBodyTree(CodeTree body) {
        this.bodyTree = body;
    }

    public CodeTree getBodyTree() {
        return bodyTree;
    }

    public TypeMirror getReturnType() {
        return returnType;
    }

    @Override
    public List<VariableElement> getParameters() {
        return parameters;
    }

    public TypeMirror[] getParameterTypes() {
        TypeMirror[] types = new TypeMirror[getParameters().size()];
        for (int i = 0; i < types.length; i++) {
            types[i] = parameters.get(i).asType();
        }
        return types;
    }

    public void setReturnType(TypeMirror type) {
        returnType = type;
    }

    public void addParameter(VariableElement parameter) {
        parameters.add(parameter);
    }

    public void addThrownType(TypeMirror thrownType) {
        throwables.add(thrownType);
    }

    public void setSimpleName(Name name) {
        this.name = name;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getBody() {
        return body;
    }

    @Override
    public <R, P> R accept(ElementVisitor<R, P> v, P p) {
        return v.visitExecutable(this, p);
    }

    public static CodeExecutableElement cloneNoAnnotations(ExecutableElement executable) {
        CodeExecutableElement clone = CodeExecutableElement.clone(executable);
        clone.getAnnotationMirrors().clear();
        for (VariableElement var : clone.getParameters()) {
            ((CodeVariableElement) var).getAnnotationMirrors().clear();
        }
        return clone;
    }

    public static CodeExecutableElement clone(ExecutableElement method) {
        CodeExecutableElement copy = new CodeExecutableElement(method.getReturnType(), method.getSimpleName().toString());
        for (TypeMirror thrownType : method.getThrownTypes()) {
            copy.addThrownType(thrownType);
        }
        copy.setDefaultValue(method.getDefaultValue());

        for (TypeParameterElement parameter : method.getTypeParameters()) {
            copy.getTypeParameters().add(parameter);
        }
        for (AnnotationMirror mirror : method.getAnnotationMirrors()) {
            copy.addAnnotationMirror(mirror);
        }
        for (VariableElement var : method.getParameters()) {
            copy.addParameter(CodeVariableElement.clone(var));
        }
        for (Element element : method.getEnclosedElements()) {
            copy.add(element);
        }
        copy.getModifiers().addAll(method.getModifiers());
        copy.setVarArgs(method.isVarArgs());
        if (method instanceof CodeExecutableElement) {
            copy.setBodyTree(((CodeExecutableElement) method).getBodyTree());
        }
        return copy;
    }

    public TypeMirror getReceiverType() {
        throw new UnsupportedOperationException();
    }

    public void renameArguments(String... args) {
        for (int i = 0; i < args.length && i < getParameters().size(); i++) {
            ((CodeVariableElement) getParameters().get(i)).setName(args[i]);
        }
    }

    public void changeTypes(TypeMirror... args) {
        for (int i = 0; i < args.length && i < getParameters().size(); i++) {
            ((CodeVariableElement) getParameters().get(i)).setType(args[i]);
        }
    }
}
