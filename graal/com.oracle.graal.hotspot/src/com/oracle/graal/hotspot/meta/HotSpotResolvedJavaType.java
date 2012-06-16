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
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;

/**
 * Implementation of RiType for resolved non-primitive HotSpot classes.
 */
public final class HotSpotResolvedJavaType extends HotSpotJavaType implements ResolvedJavaType {

    private static final long serialVersionUID = 3481514353553840471L;

    private Class javaMirror;
    private String simpleName;
    private int accessFlags;
    private boolean hasFinalizer;
    private boolean hasFinalizableSubclass;
    private int superCheckOffset;
    private boolean isArrayClass;
    private boolean isInstanceClass;
    private boolean isInterface;
    private int instanceSize;
    private HashMap<Long, ResolvedJavaField> fieldCache;
    private ResolvedJavaType superType;
    private boolean superTypeSet;
    private ResolvedJavaField[] fields;
    private ConstantPool constantPool;
    private boolean isInitialized;
    private ResolvedJavaType arrayOfType;

    private HotSpotResolvedJavaType() {
    }

    @Override
    public int accessFlags() {
        return accessFlags;
    }

    @Override
    public ResolvedJavaType arrayOf() {
        if (arrayOfType == null) {
           arrayOfType = (ResolvedJavaType) HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaType_arrayOf(this);
        }
        return arrayOfType;
    }

    @Override
    public ResolvedJavaType componentType() {
        assert isArrayClass();
        return (ResolvedJavaType) HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaType_componentType(this);
    }

    @Override
    public ResolvedJavaType uniqueConcreteSubtype() {
        if (isArrayClass()) {
            return Modifier.isFinal(componentType().accessFlags()) ? this : null;
        } else {
            return (ResolvedJavaType) HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaType_uniqueConcreteSubtype(this);
        }
    }

    @Override
    public ResolvedJavaType superType() {
        if (!superTypeSet) {
            superType = (ResolvedJavaType) HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaType_superType(this);
            superTypeSet = true;
        }
        return superType;
    }

    @Override
    public ResolvedJavaType leastCommonAncestor(ResolvedJavaType otherType) {
        if (otherType instanceof HotSpotTypePrimitive) {
            return null;
        } else {
            return (ResolvedJavaType) HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaType_leastCommonAncestor(this, (HotSpotResolvedJavaType) otherType);
        }
    }

    @Override
    public ResolvedJavaType exactType() {
        if (Modifier.isFinal(accessFlags)) {
            return this;
        }
        return null;
    }

    @Override
    public Constant getEncoding(Representation r) {
        switch (r) {
            case JavaClass:
                return Constant.forObject(javaMirror);
            case ObjectHub:
                return Constant.forObject(klassOop());
            case StaticFields:
                return Constant.forObject(javaMirror);
            default:
                assert false : "Should not reach here.";
                return null;
        }
    }

    @Override
    public Kind getRepresentationKind(Representation r) {
        return Kind.Object;
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
    public boolean isArrayClass() {
        return isArrayClass;
    }

    @Override
    public boolean isInitialized() {
        if (!isInitialized) {
            isInitialized = HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaType_isInitialized(this);
        }
        return isInitialized;
    }

    @Override
    public boolean isInstance(Constant obj) {
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
    public boolean isSubtypeOf(ResolvedJavaType other) {
        if (other instanceof HotSpotResolvedJavaType) {
            return HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaType_isSubtypeOf(this, other);
        }
        // No resolved type is a subtype of an unresolved type.
        return false;
    }

    @Override
    public Kind kind() {
        return Kind.Object;
    }

    @Override
    public ResolvedJavaMethod resolveMethodImpl(ResolvedJavaMethod method) {
        assert method instanceof HotSpotMethod;
        return (ResolvedJavaMethod) HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaType_resolveMethodImpl(this, method.name(), method.signature().asString());
    }

    @Override
    public String toString() {
        return "HotSpotType<" + simpleName + ", resolved>";
    }

    public ConstantPool constantPool() {
        if (constantPool == null) {
            constantPool = new HotSpotConstantPool(this);
        }
        return constantPool;
    }

    /**
     * Gets the instance size of this type. If an instance of this type cannot
     * be fast path allocated, then the returned value is negative (its absolute
     * value gives the size).
     */
    public int instanceSize() {
        return instanceSize;
    }

    public synchronized ResolvedJavaField createRiField(String fieldName, JavaType type, int offset, int flags) {
        ResolvedJavaField result = null;

        long id = offset + ((long) flags << 32);

        // (thomaswue) Must cache the fields, because the local load elimination only works if the objects from two field lookups are identical.
        if (fieldCache == null) {
            fieldCache = new HashMap<>(8);
        } else {
            result = fieldCache.get(id);
        }

        if (result == null) {
            result = new HotSpotResolvedJavaField(this, fieldName, type, offset, flags);
            fieldCache.put(id, result);
        } else {
            assert result.name().equals(fieldName);
            assert result.accessFlags() == flags;
        }

        return result;
    }

    @Override
    public ResolvedJavaMethod uniqueConcreteMethod(ResolvedJavaMethod method) {
        return ((HotSpotResolvedJavaMethod) method).uniqueConcreteMethod();
    }

    @Override
    public ResolvedJavaField[] declaredFields() {
        if (fields == null) {
            fields = HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaType_fields(this);
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
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return this;
    }

    // this value may require identity semantics so cache it
    private HotSpotKlassOop klassOopCache;

    @Override
    public synchronized HotSpotKlassOop klassOop() {
        if (klassOopCache == null) {
            klassOopCache = new HotSpotKlassOop(javaMirror);
        }
        return klassOopCache;
    }

    private static final int SECONDARY_SUPER_CACHE_OFFSET = HotSpotGraalRuntime.getInstance().getConfig().secondarySuperCacheOffset;

    public boolean isPrimaryType() {
        return SECONDARY_SUPER_CACHE_OFFSET != superCheckOffset;
    }

    public int superCheckOffset() {
        return superCheckOffset;
    }
}
