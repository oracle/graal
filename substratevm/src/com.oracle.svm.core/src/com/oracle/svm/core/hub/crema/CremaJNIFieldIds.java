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
package com.oracle.svm.core.hub.crema;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jni.headers.JNIFieldId;
import com.oracle.svm.core.metaspace.Metaspace;
import com.oracle.svm.shared.Uninterruptible;

public final class CremaJNIFieldIds {
    /** Marks a field id as using the Crema runtime-loaded field-id encoding. */
    private static final long TAG_MASK = 0x8000_0000_0000_0000L;

    /** Selects the pointer or offset payload stored below the tag bit. */
    private static final long PAYLOAD_MASK = ~TAG_MASK;

    private CremaJNIFieldIds() {
    }

    /**
     * Encodes an instance runtime-loaded field id. The payload is the field offset with the high bit
     * set to distinguish it from AOT field ids.
     */
    public static JNIFieldId forInstanceField(int offset) {
        assert offset > 0;
        JNIFieldId result = Word.pointer(offset | TAG_MASK);
        assert isCremaFieldId(result);
        return result;
    }

    /**
     * Encodes a static runtime-loaded field id. The payload is a metaspace pointer to the static
     * field-id node with the high bit set to distinguish it from AOT field ids.
     */
    public static JNIFieldId forStaticField(CremaJNIStaticFieldId id) {
        JNIFieldId result = Word.pointer(Word.objectToUntrackedPointer(id).rawValue() | TAG_MASK);
        assert isCremaFieldId(result);
        return result;
    }

    /** Returns whether the field id uses the runtime-loaded Crema field-id encoding. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isCremaFieldId(JNIFieldId fieldId) {
        return (fieldId.rawValue() & TAG_MASK) != 0;
    }

    /** Returns the offset payload of a tagged instance field id. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static long getInstanceFieldOffset(JNIFieldId fieldId) {
        assert isCremaFieldId(fieldId);
        return fieldId.rawValue() & PAYLOAD_MASK;
    }

    /** Returns the static field offset stored in a tagged static field id. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static int getStaticFieldOffset(JNIFieldId fieldId) {
        assert isCremaFieldId(fieldId);
        return asStaticFieldId(fieldId).getOffset();
    }

    /** Returns the declaring class hub stored in a tagged static field id. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static DynamicHub getStaticFieldHolder(JNIFieldId fieldId) {
        assert isCremaFieldId(fieldId);
        return asStaticFieldId(fieldId).getHolder();
    }

    /** Decodes a tagged static field id back to its metaspace node. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static CremaJNIStaticFieldId asStaticFieldId(JNIFieldId fieldId) {
        Pointer pointer = Word.pointer(fieldId.rawValue() & PAYLOAD_MASK);
        return (CremaJNIStaticFieldId) pointer.toObject();
    }

    public static final class CremaJNIStaticFieldId {
        /** Stores the declaring class hub for a static field id. */
        private DynamicHub holder;

        /** Stores the static field offset within the holder's static storage. */
        private int offset;

        /** Stores the next static field id node in the holder's linked list. */
        private CremaJNIStaticFieldId next;

        public static CremaJNIStaticFieldId allocate(DynamicHub holder, int offset, CremaJNIStaticFieldId next) {
            CremaJNIStaticFieldId result = Metaspace.singleton().allocateCremaJNIStaticFieldId();
            result.holder = holder;
            result.offset = offset;
            result.next = next;
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

        /** Finds an existing static field id node for the requested offset in this holder's list. */
        public CremaJNIStaticFieldId findStaticFieldId(int searchOffset) {
            CremaJNIStaticFieldId current = this;
            while (current != null) {
                if (current.offset == searchOffset) {
                    return current;
                }
                current = current.next;
            }
            return null;
        }
    }
}
