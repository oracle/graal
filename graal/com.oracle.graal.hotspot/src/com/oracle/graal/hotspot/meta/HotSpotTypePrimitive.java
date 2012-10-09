/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import java.lang.annotation.*;
import java.lang.reflect.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;

/**
 * Implementation of {@link JavaType} for primitive HotSpot types.
 */
public final class HotSpotTypePrimitive extends HotSpotJavaType implements ResolvedJavaType {

    private static final long serialVersionUID = -6208552348908071473L;
    private Kind kind;
    private final HotSpotKlassOop klassOop;

    public HotSpotTypePrimitive(Kind kind) {
        this.kind = kind;
        this.name = String.valueOf(Character.toUpperCase(kind.getTypeChar()));
        this.klassOop = new HotSpotKlassOop(this);
    }

    @Override
    public int getModifiers() {
        assert kind != null && kind.toJavaClass() != null;
        return Modifier.ABSTRACT | Modifier.FINAL | Modifier.PUBLIC;
    }

    @Override
    public ResolvedJavaType getArrayClass() {
        return (ResolvedJavaType) HotSpotGraalRuntime.getInstance().getCompilerToVM().getPrimitiveArrayType(kind);
    }

    @Override
    public ResolvedJavaType getComponentType() {
        return null;
    }

    @Override
    public ResolvedJavaType getExactType() {
        return this;
    }

    @Override
    public ResolvedJavaType getSuperclass() {
        return null;
    }

    @Override
    public ResolvedJavaType findLeastCommonAncestor(ResolvedJavaType otherType) {
        return null;
    }

    @Override
    public Constant getEncoding(Representation r) {
        throw GraalInternalError.unimplemented("HotSpotTypePrimitive.getEncoding");
    }

    @Override
    public boolean hasFinalizableSubclass() {
        return false;
    }

    @Override
    public boolean hasFinalizer() {
        return false;
    }

    @Override
    public boolean isArrayClass() {
        return false;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public boolean isInstance(Constant obj) {
        return false;
    }

    @Override
    public boolean isInstanceClass() {
        return false;
    }

    @Override
    public boolean isInterface() {
        return false;
    }

    @Override
    public boolean isSubtypeOf(ResolvedJavaType other) {
        return false;
    }

    @Override
    public Kind getKind() {
        return kind;
    }

    @Override
    public ResolvedJavaMethod resolveMethod(ResolvedJavaMethod method) {
        return null;
    }

    @Override
    public String toString() {
        return "HotSpotTypePrimitive<" + kind + ">";
    }

    @Override
    public ResolvedJavaType findUniqueConcreteSubtype() {
        return this;
    }

    @Override
    public ResolvedJavaMethod findUniqueConcreteMethod(ResolvedJavaMethod method) {
        return null;
    }

    @Override
    public ResolvedJavaField[] getDeclaredFields() {
        return new ResolvedJavaField[0];
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return toJava().getAnnotation(annotationClass);
    }

    @Override
    public Class< ? > toJava() {
        return kind.toJavaClass();
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return this;
    }

    @Override
    public HotSpotKlassOop klassOop() {
        return klassOop;
    }
}
