/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heapdump;

import static com.oracle.svm.core.snippets.KnownIntrinsics.readCallerStackPointer;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.VMThreads;

/** A collection of utilities that might assist heap dumps. */
public class HeapDumpUtils {

    @UnknownObjectField(types = {byte[].class}) private byte[] fieldsMap;

    /** Extra methods for testing. */
    private final TestingBackDoor testingBackDoor;

    /** Constructor. */
    HeapDumpUtils() {
        this.testingBackDoor = new TestingBackDoor(this);
    }

    /** Accessor for the singleton. */
    public static HeapDumpUtils getHeapDumpUtils() {
        return ImageSingletons.lookup(HeapDumpUtils.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setFieldsMap(byte[] map) {
        fieldsMap = map;
    }

    /**
     * Walk all the objects in the heap, both the image heap and the garbage collected heap applying
     * a visitor to each.
     */
    public boolean walkHeapObjects(ObjectVisitor imageHeapVisitor, ObjectVisitor collectedHeapVisitor) {
        final WalkHeapObjectsOperation operation = new WalkHeapObjectsOperation(imageHeapVisitor, collectedHeapVisitor);
        return walkHeapObjectsWithoutAllocating(operation);
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Iterating the heap must not allocate in the heap.")
    boolean walkHeapObjectsWithoutAllocating(WalkHeapObjectsOperation operation) {
        operation.enqueue();
        return operation.getResult();
    }

    public int instanceSizeOf(Class<?> cls) {
        final int encoding = DynamicHub.fromClass(cls).getLayoutEncoding();
        if (LayoutEncoding.isPureInstance(encoding)) {
            return (int) LayoutEncoding.getPureInstanceSize(encoding).rawValue();
        } else {
            return 0;
        }
    }

    boolean isPrimitiveArray(Object obj) {
        final int encoding = KnownIntrinsics.readHub(obj).getLayoutEncoding();
        return LayoutEncoding.isPrimitiveArray(encoding);
    }

    public boolean isJavaPrimitiveArray(Object obj) {
        return (isPrimitiveArray(obj) &&
                        ((obj instanceof char[]) ||
                                        (obj instanceof byte[]) ||
                                        (obj instanceof int[]) ||
                                        (obj instanceof long[]) ||
                                        (obj instanceof boolean[]) ||
                                        (obj instanceof short[]) ||
                                        (obj instanceof double[]) ||
                                        (obj instanceof float[])));
    }

    /**
     * Return a pointer to an object. The result is untracked by the collector: it is treated as an
     * integer.
     */
    public Pointer objectToPointer(Object obj) {
        return Word.objectToUntrackedPointer(obj);
    }

    public byte[] getFieldsMap() {
        return fieldsMap;
    }

    public boolean walkStacks(StacksSlotsVisitor stacksSlotsVisitor) {
        final WalkStacksSlotsOperation walkStacksOperation = new WalkStacksSlotsOperation(stacksSlotsVisitor);
        walkStacksOperation.enqueue();
        return walkStacksOperation.getResult();
    }

    private static final class WalkHeapObjectsOperation extends JavaVMOperation {

        /* Instance state. */
        private final ObjectVisitor imageHeapVisitor;
        private final ObjectVisitor collectedHeapVisitor;
        boolean result;

        /** Constructor. */
        WalkHeapObjectsOperation(ObjectVisitor imageHeapVisitor, ObjectVisitor collectedHeapVisitor) {
            super(VMOperationInfos.get(WalkHeapObjectsOperation.class, "Walk Java heap for heap dump", SystemEffect.SAFEPOINT));
            this.imageHeapVisitor = imageHeapVisitor;
            this.collectedHeapVisitor = collectedHeapVisitor;
        }

        @Override
        public void operate() {
            operateWithoutAllocation();
        }

        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Do not allocate while walking the heap.")
        private void operateWithoutAllocation() {
            result = Heap.getHeap().walkImageHeapObjects(imageHeapVisitor) && Heap.getHeap().walkCollectedHeapObjects(collectedHeapVisitor);
        }

        private boolean getResult() {
            return result;
        }
    }

    private static final class WalkStacksSlotsOperation extends JavaVMOperation {

        private final StacksSlotsVisitor stacksSlotsVisitor;
        private boolean result;

        WalkStacksSlotsOperation(StacksSlotsVisitor stacksSlotsVisitor) {
            super(VMOperationInfos.get(WalkStacksSlotsOperation.class, "Walk stack for heap dump", SystemEffect.SAFEPOINT));
            this.stacksSlotsVisitor = stacksSlotsVisitor;
        }

        @Override
        public void operate() {
            operateWithoutAllocation();
        }

        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Do not allocate while walking thread stacks.")
        private void operateWithoutAllocation() {
            result = stacksSlotsVisitor.visitStacksSlots();
        }

        private boolean getResult() {
            return result;
        }
    }

    public abstract static class StacksSlotsVisitor extends StackFrameVisitor implements ObjectReferenceVisitor {

        private IsolateThread currentVMThread;
        private Pointer currentStackSP;
        private Pointer currentFrameSP;
        private CodePointer currentFrameIP;
        private DeoptimizedFrame currentDeoptimizedFrame;

        /** Constructor for subclasses. */
        public StacksSlotsVisitor() {
            /* Nothing to do. */
        }

        /*
         * Access methods for subclasses.
         */

        /** The current VMThread. */
        protected IsolateThread getVMThread() {
            return currentVMThread;
        }

        /** The stack pointer for the current VMThread. */
        protected Pointer getStackSP() {
            return currentStackSP;
        }

        /** The stack pointer for the current frame. */
        protected Pointer getFrameSP() {
            return currentFrameSP;
        }

        /** The instruction pointer for the current frame. */
        protected CodePointer getFrameIP() {
            return currentFrameIP;
        }

        /** The DeoptimizedFrame for the current frame. */
        protected DeoptimizedFrame getDeoptimizedFrame() {
            return currentDeoptimizedFrame;
        }

        @NeverInline("Starting a stack walk in the caller frame")
        protected boolean visitStacksSlots() {
            /* Visit the current thread, because it does not have a JavaFrameAnchor. */
            currentVMThread = CurrentIsolate.getCurrentThread();
            currentStackSP = readCallerStackPointer();
            JavaStackWalker.walkCurrentThread(currentStackSP, this);
            if (SubstrateOptions.MultiThreaded.getValue()) {
                /*
                 * Scan the stacks of all the threads. Other threads will be blocked at a safepoint
                 * (or in native code) so they will each have a JavaFrameAnchor in their VMThread.
                 */
                for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
                    if (vmThread == CurrentIsolate.getCurrentThread()) {
                        /*
                         * The current thread is already scanned by code above, so we do not have to
                         * do anything for it here. It might have a JavaFrameAnchor from earlier
                         * Java-to-C transitions, but certainly not at the top of the stack since it
                         * is running this code, so just this scan would be incomplete.
                         */
                        continue;
                    }
                    currentVMThread = vmThread;
                    currentStackSP = WordFactory.nullPointer();
                    currentFrameSP = WordFactory.nullPointer();
                    currentFrameIP = WordFactory.nullPointer();
                    currentDeoptimizedFrame = null;
                    JavaStackWalker.walkThread(vmThread, this);
                }
            }
            return true;
        }

        @Override
        public boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptimizedFrame) {
            /* Notice a change in thread. */
            if (currentStackSP.isNull()) {
                currentStackSP = sp;
            }
            currentFrameSP = sp;
            currentFrameIP = ip;
            currentDeoptimizedFrame = deoptimizedFrame;
            return CodeInfoTable.visitObjectReferences(sp, ip, codeInfo, deoptimizedFrame, this);
        }
    }

    public TestingBackDoor getTestingBackDoor() {
        return testingBackDoor;
    }

    /** Expose some {@link HeapDumpUtils} methods for testing. */
    public static class TestingBackDoor {

        /** The HeapDumpUtils instance to use. */
        private final HeapDumpUtils heapDumpUtils;

        /** Constructor. */
        TestingBackDoor(HeapDumpUtils utils) {
            this.heapDumpUtils = utils;
        }

        public byte[] getFieldsMap() {
            return heapDumpUtils.getFieldsMap();
        }

        public int instanceSizeOf(Class<?> cls) {
            return heapDumpUtils.instanceSizeOf(cls);
        }

        public boolean isJavaPrimitiveArray(Object obj) {
            return heapDumpUtils.isJavaPrimitiveArray(obj);
        }

        public boolean isPrimitiveArray(Object obj) {
            return heapDumpUtils.isPrimitiveArray(obj);
        }

        public Pointer objectToPointer(Object obj) {
            return heapDumpUtils.objectToPointer(obj);
        }

        public long sizeOf(Object obj) {
            final long result;
            if (obj == null) {
                result = 0;
            } else {
                final UnsignedWord objectSize = LayoutEncoding.getSizeFromObject(obj);
                result = objectSize.rawValue();
            }
            return result;
        }

        public boolean walkHeapObjects(ObjectVisitor imageHeapVisitor, ObjectVisitor collectedHeapVisitor) {
            return heapDumpUtils.walkHeapObjects(imageHeapVisitor, collectedHeapVisitor);
        }

        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Iterating the heap must not allocate in the heap.")
        public boolean walkInteriorReferences(Object obj, ObjectReferenceVisitor visitor) {
            return InteriorObjRefWalker.walkObject(obj, visitor);
        }

        public boolean walkStacks(StacksSlotsVisitor stacksSlotsVisitor) {
            return heapDumpUtils.walkStacks(stacksSlotsVisitor);
        }
    }
}
