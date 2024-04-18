/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.heap.Target_jdk_internal_ref_Cleaner;
import com.oracle.svm.core.util.VMError;

@TargetClass(className = "java.nio.DirectByteBuffer")
public final class Target_java_nio_DirectByteBuffer {

    /**
     * We disallow direct byte buffers ({@link java.nio.MappedByteBuffer} instances) in the image
     * heap, with one exception: we allow 0-length non-file-based buffers. For example, Netty has a
     * singleton empty buffer referenced from a static field, and a lot of Netty classes reference
     * this buffer statically.
     *
     * Such buffers do actually not have a valid address, see {@link BufferAddressTransformer}. But
     * since the capacity is 0, no memory can ever be accessed. We therefore allow this "dangling"
     * address. However, we must never call free() for that address, so we remove the Cleaner
     * registered for the buffer by resetting the field {@link #cleaner}.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    Target_jdk_internal_ref_Cleaner cleaner;

    @Alias
    @SuppressWarnings("unused")
    @TargetElement(onlyWith = JDK20OrEarlier.class)
    public Target_java_nio_DirectByteBuffer(long addr, int cap) {
        throw VMError.shouldNotReachHere("This is an alias to the original constructor in the target class, so this code is unreachable");
    }

    @Alias
    @SuppressWarnings("unused")
    @TargetElement(onlyWith = JDK21OrLater.class)
    public Target_java_nio_DirectByteBuffer(long addr, long cap) {
        throw VMError.shouldNotReachHere("This is an alias to the original constructor in the target class, so this code is unreachable");
    }
}
