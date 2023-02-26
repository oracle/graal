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
package com.oracle.svm.core.jdk17;

import java.io.FileDescriptor;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDK17OrEarlier;
import com.oracle.svm.core.jdk.JDK17OrLater;

@TargetClass(className = "jdk.internal.access.foreign.MemorySegmentProxy", onlyWith = {JDK17OrLater.class, JDK17OrEarlier.class})
@SuppressWarnings("unused")
final class Target_jdk_internal_access_foreign_MemorySegmentProxy_JDK17 {
}

@TargetClass(className = "java.nio.DirectByteBuffer", onlyWith = {JDK17OrLater.class, JDK17OrEarlier.class})
@SuppressWarnings("unused")
final class Target_java_nio_DirectByteBuffer_JDK17 {

    @Alias
    protected Target_java_nio_DirectByteBuffer_JDK17(int cap, long addr, FileDescriptor fd, Runnable unmapper, boolean isSync,
                    Target_jdk_internal_access_foreign_MemorySegmentProxy_JDK17 segment) {
    }

}

@TargetClass(className = "java.nio.DirectByteBufferR", onlyWith = {JDK17OrLater.class, JDK17OrEarlier.class})
@SuppressWarnings("unused")
final class Target_java_nio_DirectByteBufferR_JDK17 {

    @Alias
    protected Target_java_nio_DirectByteBufferR_JDK17(int cap, long addr, FileDescriptor fd, Runnable unmapper, boolean isSync,
                    Target_jdk_internal_access_foreign_MemorySegmentProxy_JDK17 segment) {
    }

}

@TargetClass(className = "sun.nio.ch.Util", onlyWith = {JDK17OrLater.class, JDK17OrEarlier.class})
final class Target_sun_nio_ch_Util_JDK17 {

    @Substitute
    private static Target_java_nio_DirectByteBuffer_JDK17 newMappedByteBuffer(int size, long addr, FileDescriptor fd, Runnable unmapper, boolean isSync) {
        return new Target_java_nio_DirectByteBuffer_JDK17(size, addr, fd, unmapper, isSync, null);
    }

    @Substitute
    static Target_java_nio_DirectByteBufferR_JDK17 newMappedByteBufferR(int size, long addr, FileDescriptor fd, Runnable unmapper, boolean isSync) {
        return new Target_java_nio_DirectByteBufferR_JDK17(size, addr, fd, unmapper, isSync, null);
    }

}
