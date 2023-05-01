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
package com.oracle.svm.core.threadlocal;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.heap.InstanceReferenceMapDecoder;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.heap.UnknownPrimitiveField;

public class VMThreadLocalMTSupport {
    @UnknownPrimitiveField public int vmThreadSize = -1;
    @UnknownObjectField(types = {byte[].class}) public byte[] vmThreadReferenceMapEncoding;
    @UnknownPrimitiveField public long vmThreadReferenceMapIndex = -1;

    @Platforms(Platform.HOSTED_ONLY.class)
    public VMThreadLocalMTSupport() {
    }

    @Fold
    public static VMThreadLocalMTSupport singleton() {
        return ImageSingletons.lookup(VMThreadLocalMTSupport.class);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public NonmovableArray<Byte> getThreadLocalsReferenceMap() {
        assert vmThreadReferenceMapEncoding != null;
        return NonmovableArrays.fromImageHeap(vmThreadReferenceMapEncoding);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getThreadLocalsReferenceMapIndex() {
        long result = vmThreadReferenceMapIndex;
        assert result >= 0;
        assert (int) result == result;
        return (int) result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void walk(IsolateThread isolateThread, ObjectReferenceVisitor referenceVisitor) {
        NonmovableArray<Byte> threadRefMapEncoding = NonmovableArrays.fromImageHeap(vmThreadReferenceMapEncoding);
        InstanceReferenceMapDecoder.walkOffsetsFromPointer((Pointer) isolateThread, threadRefMapEncoding, vmThreadReferenceMapIndex, referenceVisitor, null);
    }
}
