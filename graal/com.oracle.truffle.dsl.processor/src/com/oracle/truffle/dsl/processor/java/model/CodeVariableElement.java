/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.java.model;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.java.*;

public final class CodeVariableElement extends CodeElement<Element> implements VariableElement {

    private Name name;
    private TypeMirror type;
    private Object constantValue;

    private CodeTree init;

    public CodeVariableElement(TypeMirror type, String name) {
        super(ElementUtils.modifiers());
        this.type = type;
        this.name = CodeNames.of(name);
    }

    public CodeVariableElement(Set<Modifier> modifiers, TypeMirror type, String name) {
        super(modifiers);
        this.type = type;
        this.name = CodeNames.of(name);
    }

    public CodeVariableElement(Set<Modifier> modifiers, TypeMirror type, String name, String init) {
        this(modifiers, type, name);
        if (init != null) {
            this.init = new CodeTree(CodeTreeKind.STRING, null, init);
        }
    }

    public CodeTreeBuilder createInitBuilder() {
        CodeTreeBuilder builder = new CodeTreeBuilder(null);
        init = builder.getTree();
        init.setEnclosingElement(this);
        return builder;
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
