/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.IsolateArgumentParser;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.container.Container;
import com.oracle.svm.core.container.OperatingSystem;
import com.oracle.svm.shared.util.UnsignedUtils;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;

/**
 * Contains static methods to get configuration of physical memory.
 */
public class PhysicalMemory {
    private static final UnsignedWord UNSET_SENTINEL = UnsignedUtils.MAX_VALUE;
    private static UnsignedWord cachedSize = UNSET_SENTINEL;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isInitialized() {
        return cachedSize != UNSET_SENTINEL;
    }

    /**
     * Populates the cache for the size of physical memory in bytes, querying it from the OS if it
     * has not been initialized yet.
     *
     * This method might allocate and use synchronization, so it is not safe to call it from inside
     * a VMOperation or during early stages of a thread or isolate.
     */
    public static void initialize() {
        assert !isInitialized() : "Physical memory already initialized.";
        long memoryLimit = IsolateArgumentParser.singleton().getLongOptionValue(IsolateArgumentParser.getOptionIndex(SubstrateOptions.ConcealedOptions.MaxRAM));
        if (memoryLimit > 0) {
            cachedSize = Word.unsigned(memoryLimit);
        } else if (Container.singleton().isContainerized()) {
            cachedSize = Container.singleton().getPhysicalMemory();
        } else {
            cachedSize = OperatingSystem.singleton().getPhysicalMemorySize();
        }
    }

    /** Returns the amount of used physical memory in bytes, or -1 if not supported. */
    public static long usedSize() {
        return ImageSingletons.lookup(PlatformPhysicalMemorySupport.class).usedSize();
    }

    /**
     * Returns the size of physical memory in bytes that has been cached at startup.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord size() {
        VMError.guarantee(isInitialized(), "Cached physical memory size is not available");
        return cachedSize;
    }
}
