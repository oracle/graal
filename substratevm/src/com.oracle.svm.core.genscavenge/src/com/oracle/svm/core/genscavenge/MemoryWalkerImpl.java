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
package com.oracle.svm.core.genscavenge;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.thread.VMOperation;

public class MemoryWalkerImpl extends MemoryWalker {

    @Platforms(Platform.HOSTED_ONLY.class)
    public MemoryWalkerImpl() {
        super();
    }

    /** Visit all the memory regions. */
    @Override
    public boolean visitMemory(MemoryWalker.Visitor memoryWalkerVisitor) {
        MemoryWalkerVMOperation vmOperation = new MemoryWalkerVMOperation(memoryWalkerVisitor);
        vmOperation.enqueue();
        return vmOperation.getResult();
    }

    /** A VMOperation that walks memory. */
    protected static final class MemoryWalkerVMOperation extends VMOperation {

        private MemoryWalker.Visitor memoryWalkerVisitor;
        private boolean result;

        protected MemoryWalkerVMOperation(MemoryWalker.Visitor memoryVisitor) {
            super("MemoryWalkerImpl.visitMemory", CallerEffect.BLOCKS_CALLER, SystemEffect.CAUSES_SAFEPOINT);
            this.memoryWalkerVisitor = memoryVisitor;
            this.result = false;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Allocation would change the memory being visited.")
        public void operate() {
            ThreadLocalAllocation.disableThreadLocalAllocation();
            boolean continueVisiting = true;
            if (continueVisiting) {
                continueVisiting = HeapImpl.getHeapImpl().walkHeap(memoryWalkerVisitor);
            }
            if (continueVisiting) {
                continueVisiting = CodeInfoTable.getImageCodeCache().walkImageCode(memoryWalkerVisitor);
            }
            if (continueVisiting) {
                continueVisiting = CodeInfoTable.getRuntimeCodeCache().walkRuntimeMethods(memoryWalkerVisitor);
            }
            result = continueVisiting;
        }

        protected boolean getResult() {
            return result;
        }
    }
}

@AutomaticFeature
class MemoryWalkerFeature implements Feature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return HeapOptions.UseCardRememberedSetHeap.getValue();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(MemoryWalker.class, new MemoryWalkerImpl());
    }
}
