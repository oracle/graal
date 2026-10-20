/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.interpreter;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jni.headers.JNIFieldId;
import com.oracle.svm.guest.staging.core.metaspace.Metaspace;
import com.oracle.svm.shared.Uninterruptible;

/**
 * Encodes JNI field IDs for interpreter-backed classes.
 *
 * Instance field IDs store the field offset directly, while static field IDs store a pointer to a
 * metaspace node that keeps the declaring hub and offset together. The high bit tags both forms as
 * interpreter IDs and distinguishes them from regular image-build-time JNI IDs.
 */
public final class InterpreterJNIFieldIds {
    /** Marks a field id as using the interpreter field-id encoding. */
    private static final long TAG_MASK = 0x8000_0000_0000_0000L;

    /** Selects the pointer or offset payload stored below the tag bit. */
    private static final long PAYLOAD_MASK = ~TAG_MASK;

    private InterpreterJNIFieldIds() {
    }

    /**
     * Encodes an instance interpreter field id. The payload is the field offset with the high bit
     * set to distinguish it from AOT field ids.
     */
    public static JNIFieldId forInstanceField(int offset) {
        assert offset > 0;
        JNIFieldId result = Word.pointer(offset | TAG_MASK);
        assert isInterpreterFieldId(result);
        return result;
    }

    /**
     * Encodes a static interpreter field id. The payload is a metaspace pointer to the static
     * field-id node with the high bit set to distinguish it from AOT field ids.
     */
    public static JNIFieldId forStaticField(InterpreterJNIStaticFieldId id) {
        JNIFieldId result = Word.pointer(Word.objectToUntrackedPointer(id).rawValue() | TAG_MASK);
        assert canBeInterpreterStaticFieldId(result);
        return result;
    }

    /** Returns whether the field id uses the interpreter field-id encoding. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isInterpreterFieldId(JNIFieldId fieldId) {
        return (fieldId.rawValue() & TAG_MASK) != 0;
    }

    /** Returns whether the field id uses the interpreter static field-id encoding. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean canBeInterpreterStaticFieldId(JNIFieldId fieldId) {
        return isInterpreterFieldId(fieldId) && Metaspace.singleton().isInAddressSpace(Word.pointer(fieldId.rawValue() & PAYLOAD_MASK));
    }

    /** Returns the offset payload of a tagged instance field id. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static long getInstanceFieldOffset(JNIFieldId fieldId) {
        assert isInterpreterFieldId(fieldId);
        return fieldId.rawValue() & PAYLOAD_MASK;
    }

    /** Returns the static field offset stored in a tagged static field id. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static int getStaticFieldOffset(JNIFieldId fieldId) {
        assert canBeInterpreterStaticFieldId(fieldId);
        return asStaticFieldId(fieldId).getOffset();
    }

    /** Returns the installed image layer that owns the static field storage. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static int getStaticFieldLayerNum(JNIFieldId fieldId) {
        assert canBeInterpreterStaticFieldId(fieldId);
        return asStaticFieldId(fieldId).getLayerNum();
    }

    /** Returns the declaring class hub stored in a tagged static field id. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static DynamicHub getStaticFieldHolder(JNIFieldId fieldId) {
        assert canBeInterpreterStaticFieldId(fieldId);
        return asStaticFieldId(fieldId).getHolder();
    }

    /** Decodes a tagged static field id back to its metaspace node. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static InterpreterJNIStaticFieldId asStaticFieldId(JNIFieldId fieldId) {
        Pointer pointer = Word.pointer(fieldId.rawValue() & PAYLOAD_MASK);
        return (InterpreterJNIStaticFieldId) pointer.toObject();
    }

    /** Metaspace node containing the information needed to resolve a static interpreter field id. */
    public static final class InterpreterJNIStaticFieldId {
        /** Stores the declaring class hub for a static field id. */
        private DynamicHub holder;

        /** Stores the static field offset within the holder's static storage. */
        private int offset;

        /** Stores the installed image layer that owns the static field storage. */
        private int layerNum;

        private InterpreterJNIStaticFieldId() {
        }

        /** Allocates and initializes a static field-id node in metaspace. */
        public static InterpreterJNIStaticFieldId allocate(DynamicHub holder, int offset, int layerNum) {
            InterpreterJNIStaticFieldId result = Metaspace.singleton().allocateObject(InterpreterJNIStaticFieldId.class);
            result.holder = holder;
            result.offset = offset;
            result.layerNum = layerNum;
            return result;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public DynamicHub getHolder() {
            return holder;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public int getOffset() {
            return offset;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public int getLayerNum() {
            return layerNum;
        }
    }
}
