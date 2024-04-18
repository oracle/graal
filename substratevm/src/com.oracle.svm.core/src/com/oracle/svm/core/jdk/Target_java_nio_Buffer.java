/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.nio.Buffer;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.util.VMError;

import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.JavaKind;

@TargetClass(className = "java.nio.Buffer")
public final class Target_java_nio_Buffer {
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = BufferAddressTransformer.class)//
    public long address;
}

/**
 * For array-based {@link Buffer} instances, the {@code address} field contains the memory offset of
 * the first array element accessed by the buffer, so that Unsafe memory accesses relative to the
 * {@code base} are possible. For buffers that are in the image heap, that address must be properly
 * recomputed from the hosted array offset to the runtime array offset. Note that the address can
 * point into the middle of an array, not just to the first array element. When buffers are wrapped,
 * the array kind of the innermost buffer defines the kind of array base offset and array index
 * scale to use for the recomputation.
 */
class BufferAddressTransformer implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        Buffer buffer = (Buffer) receiver;
        long hostedAddress = (Long) originalValue;

        if (buffer.isDirect()) {
            /*
             * Most direct buffers are disallowed in the image heap, but 1) this is not the place to
             * raise an error about it, and 2) some 0-length direct buffers are allowed. But no
             * address of the image generator can ever be valid at image run time, so we return an
             * illegal marker value.
             */
            return Long.valueOf(0xDEADBEEF00052885L);
        }

        Object bufferBase = SharedSecrets.getJavaNioAccess().getBufferBase(buffer);
        if (bufferBase == null) {
            /*
             * For example, StringCharBuffer does not have a backing array because all get()
             * operations are forwarded to the wrapped CharSequence.
             */
            VMError.guarantee(hostedAddress == 0, "When the buffer does not have a backing array, the address must be unused too: buffer %s of %s, address %s",
                            buffer, buffer.getClass(), hostedAddress);
            return hostedAddress;
        }
        VMError.guarantee(bufferBase.getClass().isArray(), "Buffer is not backed by an array: buffer %s of %s, address %s", buffer, buffer.getClass(), hostedAddress);

        int hostedBaseOffset = Unsafe.getUnsafe().arrayBaseOffset(bufferBase.getClass());
        int hostedIndexScale = Unsafe.getUnsafe().arrayIndexScale(bufferBase.getClass());

        ObjectLayout layout = ImageSingletons.lookup(ObjectLayout.class);
        JavaKind kind = JavaKind.fromJavaClass(bufferBase.getClass().getComponentType());
        int runtimeBaseOffset = layout.getArrayBaseOffset(kind);
        int runtimeIndexScale = layout.getArrayIndexScale(kind);

        VMError.guarantee(hostedIndexScale == runtimeIndexScale, "Currently the hosted and runtime array index scale is always the same, so we do not need to transform");
        VMError.guarantee(hostedAddress >= hostedBaseOffset, "invalid address: %s, %s", hostedAddress, hostedBaseOffset);
        return Long.valueOf(hostedAddress - hostedBaseOffset + runtimeBaseOffset);
    }
}
