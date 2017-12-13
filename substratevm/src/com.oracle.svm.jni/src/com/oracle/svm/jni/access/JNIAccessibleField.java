/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.access;

// Checkstyle: allow reflection

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.hosted.FeatureImpl.CompilationAccessImpl;
import com.oracle.svm.hosted.meta.HostedField;

/**
 * Information on a class that can be looked up and accessed via JNI.
 */
public final class JNIAccessibleField {
    private final JNIAccessibleClass declaringClass;
    private final String name;
    private final int modifiers;

    /**
     * For instance fields, the offset of the field in an object of {@link #declaringClass}. For
     * static fields, depending on the field's type, the offset of the field in either
     * {@link StaticFieldsSupport#getStaticPrimitiveFields()} or
     * {@link StaticFieldsSupport#getStaticObjectFields()}.
     */
    private int offset = -1;

    JNIAccessibleField(JNIAccessibleClass declaringClass, String name, int modifiers) {
        this.declaringClass = declaringClass;
        this.name = name;
        this.modifiers = modifiers;
    }

    public int getOffset() {
        assert offset != -1;
        return offset;
    }

    public boolean isStatic() {
        return Modifier.isStatic(modifiers);
    }

    void fillOffset(CompilationAccessImpl access) {
        assert offset == -1;
        try {
            Field reflField = declaringClass.getClassObject().getDeclaredField(name);
            HostedField field = access.getMetaAccess().lookupJavaField(reflField);
            assert field.hasLocation();
            offset = field.getLocation();
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
