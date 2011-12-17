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
package com.oracle.max.graal.hotspot;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Implementation of RiType for resolved non-primitive HotSpot classes.
 */
public final class HotSpotTypeResolvedImpl extends HotSpotType implements HotSpotTypeResolved {

    private Class javaMirror;
    private String simpleName;
    private int accessFlags;
    private boolean hasFinalizer;
    private boolean hasSubclass;
    private boolean hasFinalizableSubclass;
    private boolean isArrayClass;
    private boolean isInstanceClass;
    private boolean isInterface;
    private int instanceSize;
    private HashMap<Long, RiResolvedField> fieldCache;
    private RiConstantPool pool;
    private RiResolvedType superType;
    private boolean superTypeSet;
    private RiResolvedField[] fields;
    private RiConstantPool constantPool;
    private boolean isInitialized;
    private RiResolvedType arrayOfType;

    private HotSpotTypeResolvedImpl() {
        super(null);
    }

    @Override
    public int accessFlags() {
        return accessFlags;
    }

    @Override
    public RiResolvedType arrayOf() {
        if (arrayOfType == null) {
           arrayOfType = (RiResolvedType) compiler.getVMEntries().RiType_arrayOf(this);
        }
        return arrayOfType;
    }

    @Override
    public RiResolvedType componentType() {
        assert isArrayClass();
        return (RiResolvedType) compiler.getVMEntries().RiType_componentType(this);
    }

    @Override
    public RiResolvedType uniqueConcreteSubtype() {
        return (RiResolvedType) compiler.getVMEntries().RiType_uniqueConcreteSubtype(this);
    }

    @Override
    public RiResolvedType superType() {
        if (!superTypeSet) {
            superType = (RiResolvedType) compiler.getVMEntries().RiType_superType(this);
            superTypeSet = true;
        }
        return superType;
    }

    @Override
    public RiResolvedType exactType() {
        if (Modifier.isFinal(accessFlags)) {
            return this;
        }
        return null;
    }

    @Override
    public CiConstant getEncoding(Representation r) {
        switch (r) {
            case JavaClass:
                return CiConstant.forObject(javaMirror);
            case ObjectHub:
                return CiConstant.forObject(this);
            case StaticFields:
                return CiConstant.forObject(javaMirror);
            case TypeInfo:
                return CiConstant.forObject(this);
            default:
                return null;
        }
    }

    @Override
    public CiKind getRepresentationKind(Representation r) {
        return CiKind.Object;
    }

    @Override
    public boolean hasFinalizableSubclass() {
        return hasFinalizableSubclass;
    }

    @Override
    public boolean hasFinalizer() {
        return hasFinalizer;
    }

    @Override
    public boolean hasSubclass() {
        return hasSubclass;
    }

    @Override
    public boolean isArrayClass() {
        return isArrayClass;
    }

    @Override
    public boolean isInitialized() {
        if (!isInitialized) {
            isInitialized = compiler.getVMEntries().RiType_isInitialized(this);
        }
        return isInitialized;
    }

    @Override
    public boolean isInstance(CiConstant obj) {
        return javaMirror.isInstance(obj);
    }

    @Override
    public boolean isInstanceClass() {
        return isInstanceClass;
    }

    @Override
    public boolean isInterface() {
        return isInterface;
    }

    @Override
    public boolean isSubtypeOf(RiResolvedType other) {
        if (other instanceof HotSpotTypeResolved) {
            return compiler.getVMEntries().RiType_isSubtypeOf(this, other);
        }
        // No resolved type is a subtype of an unresolved type.
        return false;
    }

    @Override
    public CiKind kind(boolean architecture) {
        return CiKind.Object;
    }

    @Override
    public RiResolvedMethod resolveMethodImpl(RiResolvedMethod method) {
        assert method instanceof HotSpotMethod;
        return (RiResolvedMethod) compiler.getVMEntries().RiType_resolveMethodImpl(this, method.name(), method.signature().asString());
    }

    @Override
    public String toString() {
        return "HotSpotType<" + simpleName + ", resolved>";
    }

    @Override
    public RiConstantPool constantPool() {
        if (constantPool == null) {
            constantPool = new HotSpotConstantPool(compiler, this);
        }
        return constantPool;
    }

    @Override
    public int instanceSize() {
        return instanceSize;
    }

    @Override
    public synchronized RiResolvedField createRiField(String name, RiType type, int offset, int flags) {
        RiResolvedField result = null;

        long id = offset + ((long) flags << 32);

        // (tw) Must cache the fields, because the local load elimination only works if the objects from two field lookups are equal.
        if (fieldCache == null) {
            fieldCache = new HashMap<Long, RiResolvedField>(8);
        } else {
            result = fieldCache.get(id);
        }

        if (result == null) {
            result = new HotSpotField(compiler, this, name, type, offset, flags);
            fieldCache.put(id, result);
        } else {
            assert result.name().equals(name);
            assert result.accessFlags() == flags;
        }

        return result;
    }

    @Override
    public RiResolvedMethod uniqueConcreteMethod(RiResolvedMethod method) {
        return ((HotSpotMethodResolved) method).uniqueConcreteMethod();
    }

    @Override
    public RiResolvedField[] declaredFields() {
        if (fields == null) {
            fields = compiler.getVMEntries().RiType_fields(this);
        }
        return fields;
    }

    @Override
    public Class< ? > toJava() {
        return javaMirror;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return toJava().getAnnotation(annotationClass);
    }
}
