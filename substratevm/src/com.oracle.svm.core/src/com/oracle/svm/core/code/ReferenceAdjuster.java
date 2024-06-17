/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import java.nio.ByteOrder;

import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.nmt.NmtCategory;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Tool for adjusting references to objects in non-movable data structures.
 */
public interface ReferenceAdjuster {
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    <T> void setConstantTargetInArray(NonmovableObjectArray<T> array, int index, JavaConstant constant);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    <T> void setObjectInArray(NonmovableObjectArray<T> array, int index, T object);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void setConstantTargetAt(PointerBase address, int length, JavaConstant constant);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    default <T extends Constant> NonmovableObjectArray<Object> copyOfObjectConstantArray(T[] constants, NmtCategory nmtCategory) {
        NonmovableObjectArray<Object> objects = NonmovableArrays.createObjectArray(Object[].class, constants.length, nmtCategory);
        for (int i = 0; i < constants.length; i++) {
            setConstantTargetInArray(objects, i, (JavaConstant) constants[i]);
        }
        return objects;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    <T> NonmovableObjectArray<T> copyOfObjectArray(T[] source, NmtCategory nmtCategory);

    /** Indicates whether all object references have been written. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean isFinished();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void writeReference(Pointer address, int length, Object obj) {
        if (length == Long.BYTES && length > ConfigurationValues.getObjectLayout().getReferenceSize()) {
            /*
             * For 8-byte immediates in instructions despite using narrow 4-byte references: we zero
             * all 8 bytes and patch a narrow reference at the offset, which results in the same
             * 8-byte value with little-endian order.
             */
            assert nativeByteOrder() == ByteOrder.LITTLE_ENDIAN;
            address.writeLong(0, 0L);
        } else {
            assert length == ConfigurationValues.getObjectLayout().getReferenceSize() : "Unsupported reference constant size";
        }
        boolean compressed = ReferenceAccess.singleton().haveCompressedReferences();
        ReferenceAccess.singleton().writeObjectAt(address, obj, compressed);
    }

    @Fold
    static ByteOrder nativeByteOrder() {
        return ConfigurationValues.getTarget().arch.getByteOrder();
    }
}
