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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.thread.JavaVMOperation;

/** Walker to visit different memory regions with a {@link MemoryWalker}. */
final class MemoryWalkerImpl extends MemoryWalker {
    @Platforms(Platform.HOSTED_ONLY.class)
    MemoryWalkerImpl() {
    }

    @Override
    public boolean visitMemory(MemoryWalker.Visitor visitor) {
        MemoryWalkerVMOperation op = new MemoryWalkerVMOperation(visitor);
        op.enqueue();
        return op.getResult();
    }

    static final class MemoryWalkerVMOperation extends JavaVMOperation {
        private final MemoryWalker.Visitor visitor;
        private boolean result = false;

        MemoryWalkerVMOperation(MemoryWalker.Visitor memoryVisitor) {
            super("MemoryWalkerImpl.visitMemory", SystemEffect.SAFEPOINT);
            this.visitor = memoryVisitor;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Allocation would change the memory being visited.")
        public void operate() {
            ThreadLocalAllocation.disableAndFlushForAllThreads();
            result = HeapImpl.getHeapImpl().walkMemory(visitor) &&
                            CodeInfoTable.getImageCodeCache().walkImageCode(visitor) &&
                            CodeInfoTable.getRuntimeCodeCache().walkRuntimeMethods(visitor);
        }

        boolean getResult() {
            return result;
        }
    }
}

@AutomaticFeature
class MemoryWalkerFeature implements Feature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.UseCardRememberedSetHeap.getValue();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(MemoryWalker.class, new MemoryWalkerImpl());
    }
}
