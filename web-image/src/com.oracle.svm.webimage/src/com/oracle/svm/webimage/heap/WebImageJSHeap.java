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
package com.oracle.svm.webimage.heap;

import java.lang.ref.Reference;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.GCImpl;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.heap.GC;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.RuntimeCodeInfoGCSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;

/**
 * SVM requires a {@link Heap} to be in the {@link ImageSingletons}. This class acts as a dummy
 * replacement for {@link HeapImpl} because {@link HeapImpl} is tightly coupled with {@link GCImpl}
 * and we do not need the GC. The method implementations are NOOP because we do not use them.
 */
public class WebImageJSHeap extends Heap {

    WebImageJSGC gc = new WebImageJSGC();

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void attachThread(IsolateThread isolateThread) {

    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void detachThread(IsolateThread isolateThread) {

    }

    @Override
    public void suspendAllocation() {

    }

    @Override
    public void resumeAllocation() {

    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isAllocationDisallowed() {
        return false;
    }

    @Override
    public GC getGC() {
        return gc;
    }

    @Override
    public RuntimeCodeInfoGCSupport getRuntimeCodeInfoGCSupport() {
        return null;
    }

    @Override
    public void walkObjects(ObjectVisitor visitor) {
    }

    @Override
    public void walkImageHeapObjects(ObjectVisitor visitor) {
    }

    @Override
    public void walkCollectedHeapObjects(ObjectVisitor visitor) {
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getClassCount() {
        return 0;
    }

    @Override
    protected List<Class<?>> getClassesInImageHeap() {
        return null;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public ObjectHeader getObjectHeader() {
        return null;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean tearDown() {
        return false;
    }

    @Override
    public void prepareForSafepoint() {

    }

    @Override
    public void endSafepoint() {

    }

    @Override
    public int getHeapBaseAlignment() {
        return 1;
    }

    @Override
    public int getImageHeapAlignment() {
        return 1;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public Pointer getImageHeapStart() {
        throw VMError.unimplemented("getImageHeapStart");
    }

    @Override
    public int getImageHeapOffsetInAddressSpace() {
        return 0;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInImageHeap(Object object) {
        return false;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInImageHeap(Pointer objectPtr) {
        return false;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInPrimaryImageHeap(Object object) {
        return false;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInPrimaryImageHeap(Pointer objectPtr) {
        return false;
    }

    @Override
    public boolean hasReferencePendingList() {
        return false;
    }

    @Override
    public void waitForReferencePendingList() {

    }

    @Override
    public void wakeUpReferencePendingListWaiters() {

    }

    @Override
    public Reference<?> getAndClearReferencePendingList() {
        return null;
    }

    @Override
    public boolean printLocationInfo(Log log, UnsignedWord value, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        return false;
    }

    @Override
    public void optionValueChanged(RuntimeOptionKey<?> key) {
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void dirtyAllReferencesOf(Object obj) {
    }

    @Override
    @Uninterruptible(reason = "Ensure that no GC can occur between this call and usage of the salt.", callerMustBe = true)
    public long getIdentityHashSalt(Object obj) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getThreadAllocatedMemory(IsolateThread thread) {
        return -1;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getUsedMemoryAfterLastGC() {
        return Word.zero();
    }

    @Override
    public void doReferenceHandling() {
    }

    @Override
    public long getMillisSinceLastWholeHeapExamined() {
        return -1;
    }

    @Override
    public boolean verifyImageHeapMapping() {
        return true;
    }
}
