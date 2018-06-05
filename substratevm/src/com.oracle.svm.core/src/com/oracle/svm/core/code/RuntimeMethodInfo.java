/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.lang.ref.WeakReference;

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.heap.ObjectReferenceWalker;
import com.oracle.svm.core.heap.PinnedAllocator;

import jdk.vm.ci.code.InstalledCode;

public final class RuntimeMethodInfo extends AbstractCodeInfo {

    @UnknownObjectField(types = {int[].class}) protected int[] deoptimizationStartOffsets;
    @UnknownObjectField(types = {byte[].class}) protected byte[] deoptimizationEncodings;
    @UnknownObjectField(types = {Object[].class}) protected Object[] deoptimizationObjectConstants;

    @Override
    public String getName() {
        return name;
    }

    /**
     * The {@link InstalledCode#getName() name of the InstalledCode}. Stored in a separate field so
     * that it is available even after the code is no longer available.
     *
     * Note that the String is not {@link PinnedAllocator pinned}, so this field must not be
     * accessed during garbage collection.
     */
    protected String name;

    /**
     * The handle to the compiled code for the outside world. We only have a weak reference to it,
     * to avoid keeping code alive.
     *
     * Note that the both the InstalledCode and the weak reference are not {@link PinnedAllocator
     * pinned}, so this field must not be accessed during garbage collection.
     */
    protected WeakReference<SubstrateInstalledCode> installedCode;

    /**
     * Provides the GC with the root pointers embedded into the runtime compiled code.
     *
     * This object is accessed during garbage collection, so it must be {@link PinnedAllocator
     * pinned}.
     */
    protected ObjectReferenceWalker constantsWalker;

    /**
     * The pinned allocator used to allocate all code meta data that is accessed during garbage
     * collection. It is {@link PinnedAllocator#release() released} only after the code has been
     * invalidated and it is guaranteed that no more stack frame of the code is present.
     */
    protected PinnedAllocator allocator;

    protected InstalledCodeObserver.InstalledCodeObserverHandle[] codeObserverHandles;

    private RuntimeMethodInfo() {
        throw shouldNotReachHere("Must be allocated with PinnedAllocator");
    }

    public void setData(CodePointer codeStart, UnsignedWord codeSize, SubstrateInstalledCode installedCode, ObjectReferenceWalker constantsWalker, PinnedAllocator allocator,
                    InstalledCodeObserver.InstalledCodeObserverHandle[] codeObserverHandles) {
        super.setData(codeStart, codeSize);
        this.name = installedCode.getName();
        this.installedCode = createInstalledCodeReference(installedCode);
        this.constantsWalker = constantsWalker;
        this.allocator = allocator;
        assert codeObserverHandles != null;
        this.codeObserverHandles = codeObserverHandles;
    }

    private static WeakReference<SubstrateInstalledCode> createInstalledCodeReference(SubstrateInstalledCode installedCode) {
        return new WeakReference<>(installedCode);
    }
}
