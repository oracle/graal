/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.foreign;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.util.BasedOnJDKFile;

public final class SubstrateMappedMemoryUtils {

    /**
     * Similar to {@code java.nio.MappedMemoryUtils.load} but does not read a byte from each loaded
     * page. This method can therefore safely be called from {@code ScopedMemoryAccess.loadInternal}
     * because it never accesses the native memory directly.
     *
     * OpenJDK's original implementation reads the first byte of each page to ensure that the pages
     * will be loaded into physical memory. We don't do so because this may cause unnecessary cache
     * invalidations of actually needed memory. Also, we assume that those reads are only beneficial
     * on some platforms we don't support anyway.
     * 
     * This method must not be inlined because it does a JNI call and inlining it will most
     * certainly exhaust the inlining budget such that other calls that need to be inlined (for
     * correctness) cannot be inlined.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+14/src/java.base/share/classes/java/nio/MappedMemoryUtils.java#L50-L77")
    @NeverInline("contains JNI call")
    static void load(long address, boolean isSync, long size) {
        // no need to load a sync mapped buffer
        if (isSync) {
            return;
        }
        if ((address == 0) || (size == 0)) {
            return;
        }
        long offset = Target_java_nio_MappedMemoryUtils.mappingOffset(address);
        long length = Target_java_nio_MappedMemoryUtils.mappingLength(offset, size);
        Target_java_nio_MappedMemoryUtils.load0(Target_java_nio_MappedMemoryUtils.mappingAddress(address, offset), length);
    }
}
