/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

    private final Name name;
    private final List<TypeMirror> bounds = new ArrayList<>();

    public CodeTypeParameterElement(Name name, TypeMirror... bounds) {
        super(ElementUtils.modifiers());
        this.name = name;
        this.bounds.addAll(Arrays.asList(bounds));
    }

    public TypeMirror asType() {
        return new Mirror(null, null);
    }

    public TypeMirror createMirror(TypeMirror upperBound, TypeMirror lowerBound) {
        return new Mirror(upperBound, lowerBound);
    }

    public ElementKind getKind() {
        return ElementKind.TYPE_PARAMETER;
    }

    public Name getSimpleName() {
        return name;
    }

    public <R, P> R accept(ElementVisitor<R, P> v, P p) {
        return v.visitTypeParameter(this, p);
    }

    public Element getGenericElement() {
        return getEnclosingElement();
    }

    public List<TypeMirror> getBounds() {
        return bounds;
    }

    private class Mirror extends CodeTypeMirror implements TypeVariable {

        private final TypeMirror upperBound;
        private final TypeMirror lowerBound;

        Mirror(TypeMirror upperBound, TypeMirror lowerBound) {
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

}
