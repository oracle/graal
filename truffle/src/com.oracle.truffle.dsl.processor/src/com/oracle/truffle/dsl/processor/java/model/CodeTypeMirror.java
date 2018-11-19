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

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.WildcardType;

public class CodeTypeMirror implements TypeMirror {

    private final TypeKind kind;

    public CodeTypeMirror(TypeKind kind) {
        this.kind = kind;
    }

    @Override
    public TypeKind getKind() {
        return kind;
    }

    @Override
    public <R, P> R accept(TypeVisitor<R, P> v, P p) {
        throw new UnsupportedOperationException();
    }

    public static class WildcardTypeMirror extends CodeTypeMirror implements WildcardType {

        private final TypeMirror extendsBounds;
        private final TypeMirror superBounds;

        public WildcardTypeMirror(TypeMirror extendsBounds, TypeMirror superBounds) {
            super(TypeKind.WILDCARD);
            this.extendsBounds = extendsBounds;
            this.superBounds = superBounds;
        }

        public TypeMirror getExtendsBound() {
            return extendsBounds;
        }

        public TypeMirror getSuperBound() {
            return superBounds;
        }

    }

    public static class ArrayCodeTypeMirror extends CodeTypeMirror implements ArrayType {

        private final TypeMirror component;

        public ArrayCodeTypeMirror(TypeMirror component) {
            super(TypeKind.ARRAY);
            this.component = component;
        }

        @Override
        public TypeMirror getComponentType() {
            return component;
        }

    }

    public static class DeclaredCodeTypeMirror extends CodeTypeMirror implements DeclaredType {

        private final TypeElement clazz;
        private final List<TypeMirror> typeArguments;

        public DeclaredCodeTypeMirror(TypeElement clazz) {
            this(clazz, Collections.<TypeMirror> emptyList());
        }

        public DeclaredCodeTypeMirror(TypeElement clazz, List<TypeMirror> typeArguments) {
            super(TypeKind.DECLARED);
            this.clazz = clazz;
            this.typeArguments = typeArguments;
        }

        @Override
        public Element asElement() {
            return clazz;
        }

        @Override
        public TypeMirror getEnclosingType() {
            return clazz.getEnclosingElement().asType();
        }

        @Override
        public List<TypeMirror> getTypeArguments() {
            return typeArguments;
        }

        @Override
        public String toString() {
            return clazz.getQualifiedName().toString();
        }

    }

    public List<? extends AnnotationMirror> getAnnotationMirrors() {
        throw new UnsupportedOperationException();
    }

    /**
     * @param annotationType
     */
    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param annotationType
     */
    public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
        throw new UnsupportedOperationException();
    }
}
