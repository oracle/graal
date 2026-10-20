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

import com.oracle.svm.core.jni.headers.JNIMethodId;
import com.oracle.svm.guest.staging.core.metaspace.Metaspace;
import com.oracle.svm.shared.Uninterruptible;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Encodes JNI method IDs for interpreter-backed classes.
 *
 * Each ID is a tagged pointer to a metaspace node that references the resolved interpreter method.
 * The high-bit tag also lets JNI call trampolines distinguish interpreter methods from image-build-time
 * methods.
 */
public final class InterpreterJNIMethodIds {
    /** Marks a method id as a pointer to an interpreter method-id node. */
    private static final long TAG_MASK = 0x8000_0000_0000_0000L;

    private static final long PAYLOAD_MASK = ~TAG_MASK;

    private InterpreterJNIMethodIds() {
    }

    /**
     * Encodes an interpreter method id. The payload is a metaspace pointer to the method-id node
     * with the high bit set to distinguish it from AOT method ids.
     */
    public static JNIMethodId forMethod(InterpreterJNIMethodId id) {
        JNIMethodId result = Word.pointer(Word.objectToUntrackedPointer(id).rawValue() | TAG_MASK);
        assert isInterpreterMethodId(result);
        return result;
    }

    /** Returns whether the method id uses the interpreter method-id encoding. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isInterpreterMethodId(JNIMethodId methodId) {
        return (methodId.rawValue() & TAG_MASK) != 0 && Metaspace.singleton().isInAddressSpace(Word.pointer(methodId.rawValue() & PAYLOAD_MASK));
    }

    /** Returns the resolved interpreter method stored in a tagged method id. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static ResolvedJavaMethod getMethod(JNIMethodId methodId) {
        assert isInterpreterMethodId(methodId);
        Pointer pointer = Word.pointer(methodId.rawValue() & PAYLOAD_MASK);
        return ((InterpreterJNIMethodId) pointer.toObject()).method;
    }

    public static final class InterpreterJNIMethodId {
        private ResolvedJavaMethod method;

        private InterpreterJNIMethodId() {
        }

        /** Allocates and initializes a method-id node in metaspace. */
        public static InterpreterJNIMethodId allocate(ResolvedJavaMethod method) {
            InterpreterJNIMethodId result = Metaspace.singleton().allocateObject(InterpreterJNIMethodId.class);
            result.method = method;
            return result;
        }
    }
}
