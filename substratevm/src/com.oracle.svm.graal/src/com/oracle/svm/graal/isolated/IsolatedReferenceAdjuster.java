/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.isolated;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.code.ReferenceAdjuster;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.handles.ThreadLocalHandles;
import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.meta.DirectSubstrateObjectConstant;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Reference adjuster for {@linkplain ClientHandle handles} from an {@link IsolatedObjectConstant},
 * mirrored objects of an {@link IsolatedMirroredObject}, and {@linkplain ImageHeapObjects image
 * heap objects}.
 */
final class IsolatedReferenceAdjuster implements ReferenceAdjuster {
    private NonmovableArray<Pointer> addresses;
    private NonmovableArray<ObjectHandle> handles;
    private int count;

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public <T> void setConstantTargetInArray(NonmovableObjectArray<T> array, int index, JavaConstant constant) {
        if (constant instanceof IsolatedObjectConstant) {
            record(NonmovableArrays.addressOf(array, index), ConfigurationValues.getObjectLayout().getReferenceSize(), ((IsolatedObjectConstant) constant).getHandle());
        } else {
            @SuppressWarnings("unchecked")
            T target = (T) ((DirectSubstrateObjectConstant) constant).getObject();
            setObjectInArray(array, index, target);
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public <T> void setObjectInArray(NonmovableObjectArray<T> array, int index, T object) {
        if (object instanceof IsolatedMirroredObject<?>) {
            ClientHandle<?> mirror = ((IsolatedMirroredObject<?>) object).getMirror();
            assert !mirror.equal(IsolatedHandles.nullHandle()) : "Mirror object must not be null";
            record(NonmovableArrays.addressOf(array, index), ConfigurationValues.getObjectLayout().getReferenceSize(), mirror);
        } else {
            VMError.guarantee(ImageHeapObjects.isInImageHeap(object));
            NonmovableArrays.setObject(array, index, object);
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void setConstantTargetAt(PointerBase address, int length, JavaConstant constant) {
        if (constant instanceof IsolatedObjectConstant) {
            record(address, length, ((IsolatedObjectConstant) constant).getHandle());
        } else {
            Object target = ((DirectSubstrateObjectConstant) constant).getObject();
            if (target instanceof IsolatedMirroredObject<?>) {
                ClientHandle<?> mirror = ((IsolatedMirroredObject<?>) target).getMirror();
                assert !mirror.equal(IsolatedHandles.nullHandle()) : "Mirror object must not be null";
                record(address, ConfigurationValues.getObjectLayout().getReferenceSize(), mirror);
            } else {
                VMError.guarantee(ImageHeapObjects.isInImageHeap(target));
                ReferenceAdjuster.writeReference((Pointer) address, length, target);
            }
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public <T> NonmovableObjectArray<T> copyOfObjectArray(T[] source, NmtCategory nmtCategory) {
        @SuppressWarnings("unchecked")
        NonmovableObjectArray<T> copy = NonmovableArrays.createObjectArray((Class<T[]>) source.getClass(), source.length, nmtCategory);
        for (int i = 0; i < source.length; i++) {
            setObjectInArray(copy, i, source[i]);
        }
        return copy;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isFinished() {
        return addresses.isNull();
    }

    public ForeignIsolateReferenceAdjusterData exportData() {
        ForeignIsolateReferenceAdjusterData data = NativeMemory.malloc(SizeOf.get(ForeignIsolateReferenceAdjusterData.class), NmtCategory.Compiler);
        data.setCount(count);
        data.setAddresses(addresses);
        data.setHandles(handles);

        addresses = Word.nullPointer();
        handles = Word.nullPointer();
        count = 0;

        return data;
    }

    public static <T extends ObjectHandle> void adjustAndDispose(ForeignIsolateReferenceAdjusterData data, ThreadLocalHandles<T> handleSet) {
        int count = data.getCount();
        for (int i = 0; i < count; i++) {
            @SuppressWarnings("unchecked")
            T handle = (T) NonmovableArrays.getWord(data.getHandles(), i);
            Object targetObject = handleSet.getObject(handle);
            Pointer address = NonmovableArrays.getWord(data.getAddresses(), i);
            ReferenceAdjuster.writeReference(address, ConfigurationValues.getObjectLayout().getReferenceSize(), targetObject);
        }

        NonmovableArrays.releaseUnmanagedArray(data.getAddresses());
        NonmovableArrays.releaseUnmanagedArray(data.getHandles());
        NativeMemory.free(data);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void record(PointerBase address, int length, ObjectHandle handle) {
        growIfFull();
        NonmovableArrays.setWord(addresses, count, (Pointer) address);
        NonmovableArrays.setWord(handles, count, handle);
        count++;

        /*
         * Set to null now so GC cannot pick up uninitialized/zapped references (especially in
         * code). This also takes care of lengths other than the reference size so we don't have to
         * remember the individual length of this patch.
         */
        ReferenceAdjuster.writeReference((Pointer) address, length, null);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void growIfFull() {
        int oldSize = addresses.isNonNull() ? NonmovableArrays.lengthOf(addresses) : 0;
        if (count == oldSize) {
            int newSize = (oldSize != 0) ? (2 * oldSize) : 32;
            NonmovableArray<Pointer> newAddresses = NonmovableArrays.createWordArray(newSize, NmtCategory.Compiler);
            NonmovableArray<ObjectHandle> newHandles = NonmovableArrays.createWordArray(newSize, NmtCategory.Compiler);
            if (addresses.isNonNull()) {
                NonmovableArrays.arraycopy(addresses, 0, newAddresses, 0, oldSize);
                NonmovableArrays.releaseUnmanagedArray(addresses);
                NonmovableArrays.arraycopy(handles, 0, newHandles, 0, oldSize);
                NonmovableArrays.releaseUnmanagedArray(handles);
            }
            addresses = newAddresses;
            handles = newHandles;
        }
    }
}
