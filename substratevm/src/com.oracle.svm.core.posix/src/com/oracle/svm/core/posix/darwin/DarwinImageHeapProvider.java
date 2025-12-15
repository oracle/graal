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
package com.oracle.svm.core.posix.darwin;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.os.AbstractCopyingImageHeapProvider;
import com.oracle.svm.core.posix.headers.darwin.DarwinVirtualMemory;
import com.oracle.svm.core.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.core.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Disallowed;
import com.oracle.svm.core.traits.SingletonTraits;

/** Creates image heaps on Darwin that are copy-on-write clones of the loaded image heap. */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = Disallowed.class)
public class DarwinImageHeapProvider extends AbstractCopyingImageHeapProvider {
    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    protected int copyMemory(Pointer loadedImageHeap, UnsignedWord imageHeapSize, Pointer newImageHeap) {
        // Note: virtual memory must have been committed for vm_copy()
        if (DarwinVirtualMemory.vm_copy(DarwinVirtualMemory.mach_task_self(), loadedImageHeap, imageHeapSize, newImageHeap) != 0) {
            return CEntryPointErrors.MAP_HEAP_FAILED;
        }
        return CEntryPointErrors.NO_ERROR;
    }
}
