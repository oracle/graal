/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.net.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;

/**
 * Implementation of {@link JavaType} for primitive HotSpot types.
 */
public final class HotSpotResolvedPrimitiveType extends HotSpotResolvedJavaType {

    private static final long serialVersionUID = -6208552348908071473L;
    private final Kind kind;

    /**
     * Gets the Graal mirror for a {@link Kind}.
     * 
     * @return the {@link HotSpotResolvedObjectType} corresponding to {@code kind}
     */
    public static ResolvedJavaType fromKind(Kind kind) {
        Class<?> javaClass = kind.toJavaClass();
        return fromClass(javaClass);
    }

    /**
     * Creates the Graal mirror for a primitive {@link Kind}.
     * 
     * <p>
     * <b>NOTE</b>: Creating an instance of this class does not install the mirror for the
     * {@link Class} type. Use {@link #fromKind(Kind)} or {@link #fromClass(Class)} instead.
     * </p>
     * 
     * @param kind the Kind to create the mirror for
     */
    public HotSpotResolvedPrimitiveType(Kind kind) {
        super(String.valueOf(Character.toUpperCase(kind.getTypeChar())));
        this.kind = kind;
        assert mirror().isPrimitive() : mirror() + " not a primitive type";
    }

    @Override
    public int getModifiers() {
        return Modifier.ABSTRACT | Modifier.FINAL | Modifier.PUBLIC;
    }

    @Override
    public ResolvedJavaType getArrayClass() {
        if (kind == Kind.Void) {
            return null;
        }
        Class<?> javaArrayMirror = Array.newInstance(mirror(), 0).getClass();
        return HotSpotResolvedObjectType.fromClass(javaArrayMirror);
    }

    @Override
    public ResolvedJavaType getComponentType() {
        return null;
    }

    @Override
    public ResolvedJavaType asExactType() {
        return this;
    }

    @Override
    public ResolvedJavaType getSuperclass() {
        return null;
    }

    @Override
    public ResolvedJavaType[] getInterfaces() {
        return new ResolvedJavaType[0];
    }

    @Override
    public ResolvedJavaType findLeastCommonAncestor(ResolvedJavaType otherType) {
        return null;
    }

    @Override
    public Constant getEncoding(Representation r) {
        throw GraalInternalError.unimplemented("HotSpotResolvedPrimitiveType.getEncoding");
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
    public boolean isArray() {
        return false;
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    public boolean isLinked() {
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
    public boolean isAssignableFrom(ResolvedJavaType other) {
        assert other != null;
        return other.equals(this);
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
        return "HotSpotResolvedPrimitiveType<" + kind + ">";
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
    public ResolvedJavaField[] getInstanceFields(boolean includeSuperclasses) {
        return new ResolvedJavaField[0];
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return null;
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return this;
    }

    @Override
    public void initialize() {
    }

    @Override
    public ResolvedJavaField findInstanceFieldWithOffset(long offset) {
        return null;
    }

    @Override
    public String getSourceFileName() {
        throw GraalInternalError.shouldNotReachHere();
    }

    @Override
    public Class<?> mirror() {
        return kind.toJavaClass();
    }

    @Override
    public URL getClassFilePath() {
        return null;
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public boolean isMember() {
        return false;
    }

    @Override
    public ResolvedJavaType getEnclosingType() {
        return null;
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors() {
        return new ResolvedJavaMethod[0];
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods() {
        return new ResolvedJavaMethod[0];
    }

    @Override
    public ResolvedJavaMethod getClassInitializer() {
        return null;
    }

    @Override
    public Constant newArray(int length) {
        return Constant.forObject(Array.newInstance(mirror(), length));
    }
}
