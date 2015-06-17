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

import java.lang.annotation.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

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
