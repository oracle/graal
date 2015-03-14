/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.meta.HotSpotResolvedObjectTypeImpl.*;

import java.lang.annotation.*;
import java.lang.reflect.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.hotspot.*;

/**
 * Represents a field in a HotSpot type.
 */
public class HotSpotResolvedJavaFieldImpl extends CompilerObject implements HotSpotResolvedJavaField, HotSpotProxified {

    private static final long serialVersionUID = 7692985878836955683L;
    private final HotSpotResolvedObjectTypeImpl holder;
    private final String name;
    private JavaType type;
    private final int offset;

    /**
     * This value contains all flags as stored in the VM including internal ones.
     */
    private final int modifiers;
    private final LocationIdentity locationIdentity = new LocationIdentity() {

    };

    public static class FieldLocationIdentity {
        HotSpotResolvedJavaFieldImpl inner;

        public FieldLocationIdentity(HotSpotResolvedJavaFieldImpl inner) {
            this.inner = inner;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof FieldLocationIdentity) {
                FieldLocationIdentity fieldLocationIdentity = (FieldLocationIdentity) obj;
                return inner.equals(fieldLocationIdentity.inner);

            }
            return false;
        }

        @Override
        public int hashCode() {
            return inner.hashCode();
        }
    }

    public HotSpotResolvedJavaFieldImpl(HotSpotResolvedObjectTypeImpl holder, String name, JavaType type, long offset, int modifiers) {
        this.holder = holder;
        this.name = name;
        this.type = type;
        assert offset != -1;
        assert offset == (int) offset : "offset larger than int";
        this.offset = (int) offset;
        this.modifiers = modifiers;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof HotSpotResolvedJavaField) {
            HotSpotResolvedJavaFieldImpl that = (HotSpotResolvedJavaFieldImpl) obj;
            if (that.offset != this.offset || that.isStatic() != this.isStatic()) {
                return false;
            } else if (this.holder.equals(that.holder)) {
                assert this.name.equals(that.name) && this.type.equals(that.type);
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public int getModifiers() {
        return modifiers & getReflectionFieldModifiers();
    }

    @Override
    public boolean isInternal() {
        return (modifiers & runtime().getConfig().jvmAccFieldInternal) != 0;
    }

    /**
     * Determines if a given object contains this field.
     *
     * @return true iff this is a non-static field and its declaring class is assignable from
     *         {@code object}'s class
     */
    public boolean isInObject(Object object) {
        if (isStatic()) {
            return false;
        }
        return getDeclaringClass().isAssignableFrom(HotSpotResolvedObjectTypeImpl.fromObjectClass(object.getClass()));
    }

    @Override
    public HotSpotResolvedObjectTypeImpl getDeclaringClass() {
        return holder;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public JavaType getType() {
        if (!(type instanceof ResolvedJavaType)) {
            // Don't allow unresolved types to hang around forever
            ResolvedJavaType resolved = type.resolve(holder);
            if (resolved != null) {
                type = resolved;
            }
        }
        return type;
    }

    public int offset() {
        return offset;
    }

    @Override
    public String toString() {
        return format("HotSpotField<%H.%n %t:") + offset + ">";
    }

    @Override
    public boolean isSynthetic() {
        return (runtime().getConfig().syntheticFlag & modifiers) != 0;
    }

    /**
     * Checks if this field has the {@link Stable} annotation.
     *
     * @return true if field has {@link Stable} annotation, false otherwise
     */
    public boolean isStable() {
        if ((runtime().getConfig().jvmAccFieldStable & modifiers) != 0) {
            return true;
        }
        assert getAnnotation(Stable.class) == null;
        if (ImplicitStableValues.getValue() && isImplicitStableField()) {
            return true;
        }
        return false;
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
        if (isInternal()) {
            return null;
        }
        try {
            return holder.mirror().getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private boolean isArray() {
        JavaType fieldType = getType();
        return fieldType instanceof ResolvedJavaType && ((ResolvedJavaType) fieldType).isArray();
    }

    private boolean isImplicitStableField() {
        if (isSynthetic()) {
            if (isSyntheticImplicitStableField()) {
                return true;
            }
        } else if (isWellKnownImplicitStableField()) {
            return true;
        }
        return false;
    }

    private boolean isSyntheticImplicitStableField() {
        assert this.isSynthetic();
        if (isStatic() && isArray()) {
            if (isFinal() && name.equals("$VALUES") || name.equals("ENUM$VALUES")) {
                // generated int[] field for EnumClass::values()
                return true;
            } else if (name.startsWith("$SwitchMap$") || name.startsWith("$SWITCH_TABLE$")) {
                // javac and ecj generate a static field in an inner class for a switch on an enum
                // named $SwitchMap$p$k$g$EnumClass and $SWITCH_TABLE$p$k$g$EnumClass, respectively
                return true;
            }
        }
        return false;
    }

    private boolean isWellKnownImplicitStableField() {
        return WellKnownImplicitStableField.test(this);
    }

    static class WellKnownImplicitStableField {
        /**
         * @return {@code true} if the field is a well-known stable field.
         */
        public static boolean test(HotSpotResolvedJavaField field) {
            return field.equals(STRING_VALUE_FIELD);
        }

        private static final ResolvedJavaField STRING_VALUE_FIELD;
        static {
            try {
                MetaAccessProvider metaAccess = runtime().getHostProviders().getMetaAccess();
                STRING_VALUE_FIELD = metaAccess.lookupJavaField(String.class.getDeclaredField("value"));
            } catch (SecurityException | NoSuchFieldException e) {
                throw new GraalInternalError(e);
            }
        }
    }

    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }
}
