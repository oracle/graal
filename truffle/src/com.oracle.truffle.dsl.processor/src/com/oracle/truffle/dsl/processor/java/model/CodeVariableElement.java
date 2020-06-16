/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.java.ElementUtils;

public final class CodeVariableElement extends CodeElement<Element> implements VariableElement {

    private Name name;
    private TypeMirror type;
    private Object constantValue;

    private CodeTree init;

    public CodeVariableElement(TypeMirror type, String name) {
        super(ElementUtils.modifiers());
        Objects.requireNonNull(type);
        Objects.requireNonNull(name);
        this.type = type;
        this.name = CodeNames.of(name);
    }

    public CodeVariableElement(Set<Modifier> modifiers, TypeMirror type, String name) {
        super(modifiers);
        Objects.requireNonNull(type);
        Objects.requireNonNull(name);
        this.type = type;
        this.name = CodeNames.of(name);
    }

    public CodeVariableElement(Set<Modifier> modifiers, TypeMirror type, String name, String init) {
        this(modifiers, type, name);
        if (init != null) {
            this.init = new CodeTree(null, CodeTreeKind.STRING, null, init);
        }
    }

    public CodeTreeBuilder createInitBuilder() {
        CodeTreeBuilder builder = new CodeTreeBuilder(null);
        builder.setEnclosingElement(this);
        init = builder.getTree();
        return builder;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CodeVariableElement) {
            CodeVariableElement other = (CodeVariableElement) obj;
            return Objects.equals(name, other.name) && //
                            ElementUtils.typeEquals(type, other.type) && //
                            Objects.equals(constantValue, other.constantValue) && //
                            Objects.equals(init, other.init) && super.equals(obj);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, ElementUtils.getTypeId(type), constantValue, init, super.hashCode());
    }

    public void setInit(CodeTree init) {
        this.init = init;
    }

    public CodeTree getInit() {
        return init;
    }

    public Name getSimpleName() {
        return name;
    }

    public TypeMirror getType() {
        return type;
    }

    @Override
    public TypeMirror asType() {
        return type;
    }

    @Override
    public String toString() {
        return super.toString() + "/* " + ElementUtils.getSimpleName(type) + "*/";
    }

    @Override
    public ElementKind getKind() {
        if (getEnclosingElement() instanceof ExecutableElement) {
            return ElementKind.PARAMETER;
        } else if (getEnclosingElement() instanceof TypeElement) {
            return ElementKind.FIELD;
        } else {
            return ElementKind.PARAMETER;
        }
    }

    public void setConstantValue(Object constantValue) {
        this.constantValue = constantValue;
    }

    @Override
    public Object getConstantValue() {
        return constantValue;
    }

    public String getName() {
        return getSimpleName().toString();
    }

    public void setSimpleName(Name name) {
        this.name = name;
    }

    public void setName(String name) {
        this.name = CodeNames.of(name);
    }

    public void setType(TypeMirror type) {
        this.type = type;
    }

    @Override
    public <R, P> R accept(ElementVisitor<R, P> v, P p) {
        return v.visitVariable(this, p);
    }

    public static CodeVariableElement clone(VariableElement var) {
        CodeVariableElement copy = new CodeVariableElement(var.getModifiers(), var.asType(), var.getSimpleName().toString());
        copy.setConstantValue(var.getConstantValue());
        for (AnnotationMirror mirror : var.getAnnotationMirrors()) {
            copy.addAnnotationMirror(mirror);
        }
        for (Element element : var.getEnclosedElements()) {
            copy.add(element);
        }
        return copy;
    }

}
