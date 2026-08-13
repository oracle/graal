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

import com.oracle.svm.core.jni.headers.JNIMethodId;
import com.oracle.svm.core.metaspace.Metaspace;
import com.oracle.svm.shared.Uninterruptible;

/**
 * Encodes JNI method IDs for runtime-loaded Crema classes.
 *
 * Each ID is a tagged pointer to a metaspace node that references the resolved Crema method.
 * The high-bit tag also lets JNI call trampolines distinguish Crema methods from image-build-time
 * methods.
 */
public final class CremaJNIMethodIds {
    /** Marks a method id as a pointer to a Crema runtime-loaded method-id node. */
    private static final long TAG_MASK = 0x8000_0000_0000_0000L;

    private static final long PAYLOAD_MASK = ~TAG_MASK;

    private CremaJNIMethodIds() {
    }

    /**
     * Encodes a runtime-loaded method id. The payload is a metaspace pointer to the method-id node
     * with the high bit set to distinguish it from AOT method ids.
     */
    public static JNIMethodId forMethod(CremaJNIMethodId id) {
        JNIMethodId result = Word.pointer(Word.objectToUntrackedPointer(id).rawValue() | TAG_MASK);
        assert isCremaMethodId(result);
        return result;
    }

    /** Returns whether the method id uses the runtime-loaded Crema method-id encoding. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isCremaMethodId(JNIMethodId methodId) {
        return (methodId.rawValue() & TAG_MASK) != 0 && Metaspace.singleton().isInAddressSpace(Word.pointer(methodId.rawValue() & PAYLOAD_MASK));
    }

    /** Returns the resolved Crema method stored in a tagged method id. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static CremaResolvedJavaMethod getMethod(JNIMethodId methodId) {
        assert isCremaMethodId(methodId);
        Pointer pointer = Word.pointer(methodId.rawValue() & PAYLOAD_MASK);
        return ((CremaJNIMethodId) pointer.toObject()).method;
    }

    public static final class CremaJNIMethodId {
        private CremaResolvedJavaMethod method;

        private CremaJNIMethodId() {
        }

        /** Allocates and initializes a method-id node in metaspace. */
        public static CremaJNIMethodId allocate(CremaResolvedJavaMethod method) {
            CremaJNIMethodId result = Metaspace.singleton().allocateObject(CremaJNIMethodId.class);
            result.method = method;
            return result;
        }
    }
}
