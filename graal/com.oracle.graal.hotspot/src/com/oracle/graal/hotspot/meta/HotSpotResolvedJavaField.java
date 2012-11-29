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
import com.oracle.graal.api.meta.ResolvedJavaType.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.phases.*;

/**
 * Represents a field in a HotSpot type.
 */
public class HotSpotResolvedJavaField extends CompilerObject implements ResolvedJavaField {

    // Must not conflict with any fields flags used by the VM - the assertion in the constructor checks this assumption
    private static final int FIELD_INTERNAL_FLAG = 0x80000000;

    private static final long serialVersionUID = 7692985878836955683L;
    private final HotSpotResolvedObjectType holder;
    private final String name;
    private final JavaType type;
    private final int offset;
    private final int flags;
    private Constant constant;

    public HotSpotResolvedJavaField(HotSpotResolvedObjectType holder, String name, JavaType type, int offset, int flags, boolean internal) {
        assert (flags & FIELD_INTERNAL_FLAG) == 0;
        this.holder = holder;
        this.name = name;
        this.type = type;
        assert offset != -1;
        this.offset = offset;
        if (internal) {
            this.flags = flags | FIELD_INTERNAL_FLAG;
        } else {
            this.flags = flags;
        }
    }

    @Override
    public int getModifiers() {
        return flags & Modifier.fieldModifiers();
    }

    @Override
    public boolean isInternal() {
        return (flags & FIELD_INTERNAL_FLAG) != 0;
    }

    private static final String SystemClassName = MetaUtil.toInternalName(System.class.getName());

    @Override
    public Constant readConstantValue(Constant receiver) {
        if (receiver == null) {
            assert Modifier.isStatic(flags);
            if (constant == null) {
                if (holder.isInitialized() && !holder.getName().equals(SystemClassName)) {
                    if (Modifier.isFinal(getModifiers()) || assumeStaticFieldsFinal(holder.mirror())) {
                        constant = readValue(receiver);
                    }
                }
            }
            return constant;
        } else {
            assert !Modifier.isStatic(flags);
            // TODO (chaeubl) HotSpot does not trust final non-static fields (see ciField.cpp)
            if (Modifier.isFinal(getModifiers())) {
                return readValue(receiver);
            }
        }
        return null;
    }

    @Override
    public Constant readValue(Constant receiver) {
        if (receiver == null) {
            assert Modifier.isStatic(flags);
            if (holder.isInitialized()) {
                Constant encoding = holder.getEncoding(getKind() == Kind.Object ? Representation.StaticObjectFields : Representation.StaticPrimitiveFields);
                return ReadNode.readUnsafeConstant(getKind(), encoding.asObject(), offset);
            }
            return null;
        } else {
            assert !Modifier.isStatic(flags);
            return ReadNode.readUnsafeConstant(getKind(), receiver.asObject(), offset);
        }
    }

    private static boolean assumeStaticFieldsFinal(Class< ? > clazz) {
        return clazz == GraalOptions.class;
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return holder;
    }

    @Override
    public Kind getKind() {
        return getType().getKind();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public JavaType getType() {
        return type;
    }

    public int offset() {
        return offset;
    }

    @Override
    public String toString() {
        return "HotSpotField<" + MetaUtil.format("%h.%n", this) + ":" + offset + ">";
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        Field javaField = toJava();
        if (javaField != null) {
            return javaField.getAnnotation(annotationClass);
        }
        return null;
    }

    private Field toJava() {
        try {
            return holder.mirror().getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
