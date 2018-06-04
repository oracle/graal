/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import java.util.Arrays;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

import com.oracle.truffle.dsl.processor.java.ElementUtils;

public class CodeTypeParameterElement extends CodeElement<Element> implements TypeParameterElement {

    private final Name simpleName;
    private final List<TypeMirror> bounds;

    public CodeTypeParameterElement(String name, TypeMirror... bounds) {
        super(ElementUtils.modifiers());
        this.simpleName = CodeNames.of(name);
        this.bounds = Arrays.asList(bounds);
    }

    public TypeMirror asType() {
        return new CodeTypeParameterMirror(null, null);
    }

    public TypeMirror createMirror(TypeMirror upperBound, TypeMirror lowerBound) {
        return new CodeTypeParameterMirror(upperBound, lowerBound);
    }

    private class CodeTypeParameterMirror extends CodeTypeMirror implements TypeVariable {

        private final TypeMirror upperBound;
        private final TypeMirror lowerBound;

        CodeTypeParameterMirror(TypeMirror upperBound, TypeMirror lowerBound) {
            super(TypeKind.TYPEVAR);
            this.upperBound = upperBound;
            this.lowerBound = lowerBound;
        }

        public Element asElement() {
            return CodeTypeParameterElement.this;
        }

        public TypeMirror getUpperBound() {
            return upperBound;
        }

        public TypeMirror getLowerBound() {
            return lowerBound;
        }

    }

    public ElementKind getKind() {
        return ElementKind.TYPE_PARAMETER;
    }

    public Name getSimpleName() {
        return simpleName;
    }

    public <R, P> R accept(ElementVisitor<R, P> v, P p) {
        return null;
    }

    public Element getGenericElement() {
        return this;
    }

    public List<? extends TypeMirror> getBounds() {
        return bounds;
    }

}
