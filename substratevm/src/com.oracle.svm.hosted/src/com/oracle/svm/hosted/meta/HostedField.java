/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.hosted.meta;

import java.lang.reflect.Field;

import com.oracle.graal.pointsto.infrastructure.OriginalFieldProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaField;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.meta.SharedField;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaTypeProfile;

/**
 * Store the compile-time information for a field in the Substrate VM, such as the field offset.
 */
public class HostedField extends HostedElement implements OriginalFieldProvider, SharedField, WrappedJavaField {

    public final AnalysisField wrapped;

    private final HostedType holder;
    private final HostedType type;

    protected int location;

    private final JavaTypeProfile typeProfile;

    static final int LOC_UNMATERIALIZED_STATIC_CONSTANT = -10;

    public HostedField(AnalysisField wrapped, HostedType holder, HostedType type, JavaTypeProfile typeProfile) {
        this.wrapped = wrapped;
        this.holder = holder;
        this.type = type;
        this.typeProfile = typeProfile;
        this.location = LOC_UNINITIALIZED;
    }

    @Override
    public AnalysisField getWrapped() {
        return wrapped;
    }

    public JavaTypeProfile getFieldTypeProfile() {
        return typeProfile;
    }

    protected void setLocation(int location) {
        assert this.location == LOC_UNINITIALIZED;
        assert location >= 0;
        this.location = location;
    }

    protected void setUnmaterializedStaticConstant() {
        assert this.location == LOC_UNINITIALIZED && isStatic();
        this.location = LOC_UNMATERIALIZED_STATIC_CONSTANT;
    }

    public boolean hasLocation() {
        return location >= 0;
    }

    /**
     * The offset or index of the field. The value depends on the kind of field:
     * <ul>
     * <li>instance fields: the offset (in bytes) from the origin of the instance.
     * <li>static fields of primitive type: the offset (in bytes) into the static primitive data
     * area.
     * <li>static reference fields: the offset (in bytes) into the static object data area.
     * <li>static fields that are never written (including but not limited to static final fields):
     * unused, this method must not be called.
     * </ul>
     */
    @Override
    public int getLocation() {
        return location;
    }

    @Override
    public boolean isAccessed() {
        return wrapped.isAccessed();
    }

    @Override
    public boolean isReachable() {
        return wrapped.isReachable();
    }

    public boolean isRead() {
        return wrapped.isRead();
    }

    @Override
    public boolean isWritten() {
        return wrapped.isWritten();
    }

    @Override
    public boolean isValueAvailable() {
        return wrapped.isValueAvailable();
    }

    @Override
    public String getName() {
        return wrapped.getName();
    }

    @Override
    public HostedType getType() {
        return type;
    }

    @Override
    public int getModifiers() {
        return wrapped.getModifiers();
    }

    @Override
    public int getOffset() {
        return getLocation();
    }

    @Override
    public int hashCode() {
        return wrapped.hashCode();
    }

    @Override
    public HostedType getDeclaringClass() {
        return holder;
    }

    @Override
    public boolean isInternal() {
        return wrapped.isInternal();
    }

    @Override
    public boolean isSynthetic() {
        return wrapped.isSynthetic();
    }

    @Override
    public String toString() {
        return "HostedField<" + format("%h.%n") + " -> " + wrapped.toString() + ", location: " + location + ">";
    }

    @Override
    public JavaKind getStorageKind() {
        return getType().getStorageKind();
    }

    @Override
    public Field getJavaField() {
        return wrapped.getJavaField();
    }
}
