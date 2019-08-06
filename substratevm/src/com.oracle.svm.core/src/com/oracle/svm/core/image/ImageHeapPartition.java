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
package com.oracle.svm.core.image;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * A native image heap consist of multiple {@link ImageHeapPartition}s. Every object in the native
 * image heap, is assigned to a position within a {@link ImageHeapPartition}.
 */
@Platforms(value = Platform.HOSTED_ONLY.class)
public interface ImageHeapPartition {
    /** Returns the name of the partition. */
    String getName();

    /** Returns true if the partition is writable. */
    boolean isWritable();

    /** Reserves sufficient memory in this partition for the given object. */
    void allocate(ImageHeapObject info);

    /**
     * Returns the size of the partition (i.e., the sum of all allocated objects + some overhead).
     */
    long getSize();

    /** Adds some padding to the end of the partition. */
    void addPadding(long computePadding);

    /**
     * Sets the ELF/PE/Mach-O file position where this partition will be placed.
     */
    void setSection(String sectionName, long offsetInSection);

    /** Returns the name of the ELF/PE/Mach-O section to which this partition was assigned. */
    String getSectionName();

    /**
     * Returns the offset at which this partition will be placed in the specified ELF/PE/Mach-O
     * section.
     */
    long getOffsetInSection();
}
