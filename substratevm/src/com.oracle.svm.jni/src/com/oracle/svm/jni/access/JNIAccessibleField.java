/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.access;

// Checkstyle: allow reflection

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.hosted.FeatureImpl.CompilationAccessImpl;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.jni.nativeapi.JNIFieldId;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Information on a field that can be looked up and accessed via JNI.
 */
public final class JNIAccessibleField extends JNIAccessibleMember {
    private static final UnsignedWord ID_STATIC_FLAG = WordFactory.unsigned(-1L).unsignedShiftRight(1).add(1);
    private static final UnsignedWord ID_OBJECT_FLAG = ID_STATIC_FLAG.unsignedShiftRight(1);
    private static final UnsignedWord ID_OFFSET_MASK = ID_OBJECT_FLAG.subtract(1);

    /**
     * For instance fields, the offset of the field in an object of the
     * {@linkplain JNIAccessibleMember#getDeclaringClass() declaring class}. For static fields,
     * depending on the field's type, the offset of the field in either
     * {@link StaticFieldsSupport#getStaticPrimitiveFields()} or
     * {@link StaticFieldsSupport#getStaticObjectFields()}.
     */
    public static WordBase getOffsetFromId(JNIFieldId id) {
        UnsignedWord result = ((UnsignedWord) id).and(ID_OFFSET_MASK);
        assert result.notEqual(0);
        return result;
    }

    private final String name;
    @Platforms(HOSTED_ONLY.class) private final UnsignedWord flags;
    private UnsignedWord id = WordFactory.zero();

    JNIAccessibleField(JNIAccessibleClass declaringClass, String name, JavaKind kind, int modifiers) {
        super(declaringClass);
        this.name = name;

        UnsignedWord bits = Modifier.isStatic(modifiers) ? ID_STATIC_FLAG : WordFactory.zero();
        bits = bits.or(kind.isObject() ? ID_OBJECT_FLAG : WordFactory.zero());
        this.flags = bits;
    }

    public JNIFieldId getId() {
        return (JNIFieldId) id;
    }

    public boolean isStatic() {
        assert !id.equal(0);
        return id.and(ID_STATIC_FLAG).notEqual(0);
    }

    void finishBeforeCompilation(CompilationAccessImpl access) {
        assert id.equal(0);
        try {
            Field reflField = getDeclaringClass().getClassObject().getDeclaredField(name);
            HostedField field = access.getMetaAccess().lookupJavaField(reflField);
            assert field.hasLocation();
            int offset = field.getLocation();
            assert ID_OFFSET_MASK.and(offset).equal(offset);
            this.id = flags.or(offset);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        setHidingSubclasses(access.getMetaAccess(), sub -> anyMatchName(sub.getInstanceFields(false)) || anyMatchName(sub.getStaticFields()));
    }

    private boolean anyMatchName(ResolvedJavaField[] fields) {
        for (ResolvedJavaField field : fields) {
            if (field.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
