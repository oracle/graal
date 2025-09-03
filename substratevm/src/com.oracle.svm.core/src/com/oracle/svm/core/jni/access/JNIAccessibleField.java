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
package com.oracle.svm.core.jni.access;

import java.lang.reflect.Modifier;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.BuildPhaseProvider.ReadyForCompilation;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jni.headers.JNIFieldId;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.JavaKind;

/**
 * Information on a field that can be looked up and accessed via JNI.
 */
public final class JNIAccessibleField extends JNIAccessibleMember {
    /* 10000000...0 */
    private static final UnsignedWord ID_STATIC_FLAG = Word.unsigned(-1L).unsignedShiftRight(1).add(1);
    /* 01000000...0 */
    private static final UnsignedWord ID_OBJECT_FLAG = ID_STATIC_FLAG.unsignedShiftRight(1);
    /* 00100000...0 */
    private static final UnsignedWord ID_NEGATIVE_FLAG = ID_OBJECT_FLAG.unsignedShiftRight(1);
    /* 00010000...0 */
    private static final UnsignedWord ID_LAYER_NUMBER_MASK = ID_NEGATIVE_FLAG.unsignedShiftRight(1);
    private static final int ID_LAYER_NUMBER_SHIFT = 60;
    /* 00001111...1 */
    private static final UnsignedWord ID_OFFSET_MASK = ID_LAYER_NUMBER_MASK.subtract(1);

    public static JNIAccessibleField negativeFieldQuery(JNIAccessibleClass jniClass) {
        return new JNIAccessibleField(jniClass, null, 0);
    }

    /**
     * For instance fields, the offset of the field in an object of the
     * {@linkplain JNIAccessibleMember#getDeclaringClass() declaring class}. For static fields,
     * depending on the field's type, the offset of the field in either
     * {@link StaticFieldsSupport#getStaticPrimitiveFieldsAtRuntime} or
     * {@link StaticFieldsSupport#getStaticObjectFieldsAtRuntime}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static WordBase getOffsetFromId(JNIFieldId id) {
        UnsignedWord result = ((UnsignedWord) id).and(ID_OFFSET_MASK);
        assert result.notEqual(0);
        return result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Object getStaticObjectFieldsAtRuntime(JNIFieldId fieldId) {
        int layerNumber = getLayerNumberFromId((UnsignedWord) fieldId);
        return StaticFieldsSupport.getStaticObjectFieldsAtRuntime(layerNumber);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Object getStaticPrimitiveFieldsAtRuntime(JNIFieldId fieldId) {
        int layerNumber = getLayerNumberFromId((UnsignedWord) fieldId);
        return StaticFieldsSupport.getStaticPrimitiveFieldsAtRuntime(layerNumber);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int getLayerNumberFromId(UnsignedWord id) {
        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            return (int) id.and(ID_LAYER_NUMBER_MASK).unsignedShiftRight(ID_LAYER_NUMBER_SHIFT).rawValue();
        } else {
            return MultiLayeredImageSingleton.UNUSED_LAYER_NUMBER;
        }
    }

    @Platforms(HOSTED_ONLY.class) private final UnsignedWord flags;

    /**
     * Represents the {@link JNIFieldId} of the field.
     *
     * From left (MSB) to right (LSB):
     * <ul>
     * <li>1 bit for a flag indicating whether the field is static</li>
     * <li>1 bit for a flag indicating whether the field is an object reference</li>
     * <li>1 bit for a flag indicating whether the field is a negative query</li>
     * <li>Remaining 61 bits for (unsigned) offset in the object</li>
     * </ul>
     */
    @UnknownPrimitiveField(availability = ReadyForCompilation.class)//
    private UnsignedWord id = Word.zero();

    @Platforms(HOSTED_ONLY.class)
    public JNIAccessibleField(JNIAccessibleClass declaringClass, JavaKind kind, int modifiers) {
        super(declaringClass);

        UnsignedWord bits = Modifier.isStatic(modifiers) ? ID_STATIC_FLAG : Word.zero();
        if (kind == null) {
            bits = bits.or(ID_NEGATIVE_FLAG);
        } else if (kind.isObject()) {
            bits = bits.or(ID_OBJECT_FLAG);
        }
        this.flags = bits;
    }

    public JNIFieldId getId() {
        return (JNIFieldId) id;
    }

    public boolean isStatic() {
        assert !id.equal(0);
        return id.and(ID_STATIC_FLAG).notEqual(0);
    }

    @Platforms(HOSTED_ONLY.class)
    public boolean isNegativeHosted() {
        return flags.and(ID_NEGATIVE_FLAG).notEqual(0);
    }

    public boolean isNegative() {
        assert !id.equal(0);
        return id.and(ID_NEGATIVE_FLAG).notEqual(0);
    }

    @Platforms(HOSTED_ONLY.class)
    public void finishBeforeCompilation(int offset, int layerNumber, EconomicSet<Class<?>> hidingSubclasses) {
        assert id.equal(0);
        assert isNegativeHosted() || ID_OFFSET_MASK.and(offset).equal(offset) : "Offset is too large to be encoded in the JNIAccessibleField ID";

        id = isNegativeHosted() ? flags : flags.or(offset);
        if (layerNumber == 1 || layerNumber == 0) {
            id = id.or(Word.unsigned(layerNumber).shiftLeft(ID_LAYER_NUMBER_SHIFT));
            VMError.guarantee(getLayerNumberFromId(id) == layerNumber);
        } else {
            assert layerNumber == MultiLayeredImageSingleton.UNUSED_LAYER_NUMBER;
        }
        setHidingSubclasses(hidingSubclasses);
    }
}
