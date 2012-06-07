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
package com.oracle.graal.hotspot.ri;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;

/**
 * Implementation of RiType for resolved non-primitive HotSpot classes.
 */
public final class HotSpotTypeResolvedImpl extends HotSpotType implements HotSpotTypeResolved {

    private static final long serialVersionUID = 3481514353553840471L;

    private Class javaMirror;
    private String simpleName;
    private int accessFlags;
    private boolean hasFinalizer;
    private boolean hasSubclass;
    private boolean hasFinalizableSubclass;
    private int superCheckOffset;
    private boolean isArrayClass;
    private boolean isInstanceClass;
    private boolean isInterface;
    private int instanceSize;
    private HashMap<Long, RiResolvedField> fieldCache;
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
           arrayOfType = (RiResolvedType) compiler.getCompilerToVM().RiType_arrayOf(this);
        }
        return arrayOfType;
    }

    @Override
    public RiResolvedType componentType() {
        assert isArrayClass();
        return (RiResolvedType) compiler.getCompilerToVM().RiType_componentType(this);
    }

    @Override
    public RiResolvedType uniqueConcreteSubtype() {
        if (isArrayClass()) {
            return Modifier.isFinal(componentType().accessFlags()) ? this : null;
        } else {
            return (RiResolvedType) compiler.getCompilerToVM().RiType_uniqueConcreteSubtype(this);
        }
    }

    @Override
    public RiResolvedType superType() {
        if (!superTypeSet) {
            superType = (RiResolvedType) compiler.getCompilerToVM().RiType_superType(this);
            superTypeSet = true;
        }
        return superType;
    }

    @Override
    public RiResolvedType leastCommonAncestor(RiResolvedType otherType) {
        if (otherType instanceof HotSpotTypePrimitive) {
            return null;
        } else {
            return (RiResolvedType) compiler.getCompilerToVM().RiType_leastCommonAncestor(this, (HotSpotTypeResolved) otherType);
        }
    }

    @Override
    public RiResolvedType exactType() {
        if (Modifier.isFinal(accessFlags)) {
            return this;
        }
        return null;
    }

    @Override
    public RiConstant getEncoding(Representation r) {
        switch (r) {
            case JavaClass:
                return RiConstant.forObject(javaMirror);
            case ObjectHub:
                return RiConstant.forObject(klassOop());
            case StaticFields:
                return RiConstant.forObject(javaMirror);
            default:
                return null;
        }
    }

    @Override
    public RiKind getRepresentationKind(Representation r) {
        return RiKind.Object;
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
            isInitialized = compiler.getCompilerToVM().RiType_isInitialized(this);
        }
        return isInitialized;
    }

    @Override
    public boolean isInstance(RiConstant obj) {
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
            return compiler.getCompilerToVM().RiType_isSubtypeOf(this, other);
        }
        // No resolved type is a subtype of an unresolved type.
        return false;
    }

    @Override
    public RiKind kind(boolean architecture) {
        return RiKind.Object;
    }

    @Override
    public RiResolvedMethod resolveMethodImpl(RiResolvedMethod method) {
        assert method instanceof HotSpotMethod;
        return (RiResolvedMethod) compiler.getCompilerToVM().RiType_resolveMethodImpl(this, method.name(), method.signature().asString());
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
    public synchronized RiResolvedField createRiField(String fieldName, RiType type, int offset, int flags) {
        RiResolvedField result = null;

        long id = offset + ((long) flags << 32);

        // (thomaswue) Must cache the fields, because the local load elimination only works if the objects from two field lookups are identical.
        if (fieldCache == null) {
            fieldCache = new HashMap<>(8);
        } else {
            result = fieldCache.get(id);
        }

        if (result == null) {
            result = new HotSpotField(compiler, this, fieldName, type, offset, flags);
            fieldCache.put(id, result);
        } else {
            assert result.name().equals(fieldName);
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
            fields = compiler.getCompilerToVM().RiType_fields(this);
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

    @Override
    public RiResolvedType resolve(RiResolvedType accessingClass) {
        return this;
    }

    // this value may require identity semantics so cache it
    private HotSpotKlassOop klassOopCache;

    @Override
    public synchronized HotSpotKlassOop klassOop() {
        if (klassOopCache == null) {
            klassOopCache = new HotSpotKlassOop(compiler, javaMirror);
        }
        return klassOopCache;
    }

    private static final int SECONDARY_SUPER_CACHE_OFFSET = CompilerImpl.getInstance().getConfig().secondarySuperCacheOffset;

    public boolean isPrimaryType() {
        return SECONDARY_SUPER_CACHE_OFFSET != superCheckOffset;
    }

    public int superCheckOffset() {
        return superCheckOffset;
    }
}
