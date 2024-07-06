/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.container;

import com.oracle.svm.core.Uninterruptible;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.jdk.Jvm;

import jdk.graal.compiler.api.replacements.Fold;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

/**
 * Query information about the machine that executes the native executable. This class bypasses the
 * container support (see {@link Container}) and directly queries the information from the operating
 * system.
 */
@AutomaticallyRegisteredImageSingleton
public class OperatingSystem {
    private int cachedActiveProcessorCount;
    private UnsignedWord cachedPhysicalMemorySize;

    @Platforms(Platform.HOSTED_ONLY.class)
    OperatingSystem() {
    }

    @Fold
    public static OperatingSystem singleton() {
        return ImageSingletons.lookup(OperatingSystem.class);
    }

    public int getActiveProcessorCount() {
        int value = Jvm.JVM_ActiveProcessorCount();
        cachedActiveProcessorCount = value;
        return value;
    }

    public int getCachedActiveProcessorCount() {
        return cachedActiveProcessorCount;
    }

    public UnsignedWord getPhysicalMemorySize() {
        UnsignedWord value = ImageSingletons.lookup(PhysicalMemory.PhysicalMemorySupport.class).size();
        cachedPhysicalMemorySize = value;
        return value;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public UnsignedWord getCachedPhysicalMemorySize() {
        return cachedPhysicalMemorySize;
    }
}
