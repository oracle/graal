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
package com.oracle.svm.core.jdk;

import java.io.FileDescriptor;
import java.nio.Buffer;
import java.nio.MappedByteBuffer;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;

@TargetClass(className = "java.lang.foreign.MemorySegment")
@SuppressWarnings("unused")
final class Target_java_lang_foreign_MemorySegment {
    @TargetElement(onlyWith = ForeignDisabled.class)
    @Substitute
    static Target_java_lang_foreign_MemorySegment ofBuffer(Buffer buffer) {
        throw ForeignDisabledSubstitutions.fail();
    }
}

@TargetClass(className = "java.nio.DirectByteBufferR")
@SuppressWarnings("unused")
final class Target_java_nio_DirectByteBufferR {

    @Alias
    protected Target_java_nio_DirectByteBufferR(int cap, long addr, FileDescriptor fd, Runnable unmapper, boolean isSync, Target_java_lang_foreign_MemorySegment segment) {
    }
}

@TargetClass(className = "sun.nio.ch.Util")
final class Target_sun_nio_ch_Util {

    @Substitute
    private static MappedByteBuffer newMappedByteBuffer(int size, long addr, FileDescriptor fd, Runnable unmapper, boolean isSync) {
        return SubstrateUtil.cast(new Target_java_nio_DirectByteBuffer(size, addr, fd, unmapper, isSync, null), MappedByteBuffer.class);
    }

    @Substitute
    static MappedByteBuffer newMappedByteBufferR(int size, long addr, FileDescriptor fd, Runnable unmapper, boolean isSync) {
        return SubstrateUtil.cast(new Target_java_nio_DirectByteBufferR(size, addr, fd, unmapper, isSync, null), MappedByteBuffer.class);
    }
}
