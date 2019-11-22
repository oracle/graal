/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.snippets.KnownIntrinsics.readCallerStackPointer;

import java.util.ArrayList;
import java.util.Arrays;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.thread.JavaVMOperation;

/**
 * Can be used to debug object liveness.
 *
 * <pre>
 * PathElements pathElements = new PathElements(10);
 * PathExhibitor pathFinder = PathExhibitor.factory();
 * PathExhibitor.TestingBackDoor.findPathToObject(pathFinder, jarEntry, pathElements);
 * pathElements.toLog(Log.log());
 * </pre>
 */
public class PathExhibitor {

    public static PathExhibitor factory() {
        return new PathExhibitor();
    }

    public boolean findPathToRoot(final Object leaf) {
        PathElements singleElement = new PathElements(1);
        // Put the obj in the path as a leaf.
        PathElement currentElement = LeafElement.factory(leaf);
        Object currentObject = leaf;
        for (; /* break */;) {
            // Current is an element of the path.
            path.add(currentElement);
            // Walk backwards one step.
            singleElement.clear();
            findPathToObject(currentObject, singleElement);
            currentElement = singleElement.isEmpty() ? null : singleElement.get(0);
            // Look for reasons to stop walking.
            // - I didn't find a path to the current object.
            if (currentElement == null) {
                // No pointer to current object: The path ends here.
                break;
            }
            // Update the current object.
            currentObject = KnownIntrinsics.convertUnknownValue(currentElement.getObject(), Object.class);
            // - The current element is a root.
            if (currentObject == null) {
                // Add element to path and stop.
                path.add(currentElement);
                break;
            }
            // - I have seen this object before.
            if (checkForCycles(currentObject)) {
                final CyclicElement cyclic = CyclicElement.factory(currentObject);
                path.add(cyclic);
                break;
            }
        }
        return true;
    }

    public void toLog(final Log log) {
        for (PathElement element : path) {
            log.newline();
            element.toLog(log);
        }
    }

    protected void findPathToObject(Object obj, PathElements result) {
        if (obj == null || !result.isSpaceAvailable()) {
            return;
        }
        // Look in all the roots.
        findPathInHeap(obj, result);
        findPathInBootImageHeap(obj, result);
        findPathInStack(obj, result);
    }

    @NeverInline("Starting a stack walk in the caller frame")
    protected void findPathInStack(final Object obj, PathElements result) {
        if (!result.isSpaceAvailable()) {
            return;
        }

        stackFrameVisitor.initialize(obj, result);
        Pointer sp = readCallerStackPointer();
        JavaStackWalker.walkCurrentThread(sp, stackFrameVisitor);
        stackFrameVisitor.reset();
    }

    protected void findPathInBootImageHeap(final Object targetObject, PathElements result) {
        Heap.getHeap().walkImageHeapObjects(new ObjectVisitor() {
            @Override
            public boolean visitObject(Object obj) {
                if (!result.isSpaceAvailable()) {
                    return false;
                }

                bootImageHeapObjRefVisitor.initialize(obj, targetObject, result);
                return InteriorObjRefWalker.walkObject(obj, bootImageHeapObjRefVisitor);
            }
        });
    }

    protected void findPathInHeap(final Object obj, PathElements result) {
        if (!result.isSpaceAvailable()) {
            return;
        }

        heapObjectVisitor.initialize(obj, result);
        HeapImpl.getHeapImpl().walkObjects(heapObjectVisitor);
    }

    protected boolean checkForCycles(final Object currentObject) {
        boolean result = false;
        for (PathElement seen : path) {
            final Object seenObject = seen.getObject();
            if (currentObject == seenObject) {
                result = true;
                break;
            }
        }
        return result;
    }

    protected PathExhibitor() {
        this.path = new ArrayList<>();
    }

    // Immutable state.
    protected final ArrayList<PathElement> path;

    // Immutable static state.
    // - For walking the stack.
    protected static final FrameSlotVisitor frameSlotVisitor = new FrameSlotVisitor();
    protected static final FrameVisitor stackFrameVisitor = new FrameVisitor();
    // - For walking the native image heap.
    protected static final BootImageHeapObjRefVisitor bootImageHeapObjRefVisitor = new BootImageHeapObjRefVisitor();
    // - For walking the heap.
    protected static final HeapObjRefVisitor heapObjRefVisitor = new HeapObjRefVisitor();
    protected static final HeapObjectVisitor heapObjectVisitor = new HeapObjectVisitor();

    public abstract static class PathElement {

        /** Report this path element. */
        public abstract Log toLog(Log log);

        /** Return the base object for this path element, or null. */
        public abstract Object getObject();
    }

    /** A reusable StackFrameVisitor. */
    public static class FrameVisitor implements StackFrameVisitor {

        // Lazily-initialized state,
        // so I do not allocate one of these for each target for each frame.
        /** A pointer to the object I am looking for. */
        protected Pointer targetPointer;
        private PathElements result;

        protected FrameVisitor() {
        }

        public void initialize(final Object targetObject, PathElements res) {
            this.targetPointer = Word.objectToUntrackedPointer(targetObject);
            this.result = res;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while verifying the heap.")
        public boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptimizedFrame) {
            frameSlotVisitor.initialize(ip, deoptimizedFrame, targetPointer, result);
            return CodeInfoTable.visitObjectReferences(sp, ip, codeInfo, deoptimizedFrame, frameSlotVisitor);
        }

        public void reset() {
            targetPointer = WordFactory.nullPointer();
        }
    }

    private static class FrameSlotVisitor implements ObjectReferenceVisitor {
        // Lazily-initialized state,
        // so I do not allocate one of these for each target for each frame.
        /** The instruction pointer of the current frame. */
        protected CodePointer ip;
        protected DeoptimizedFrame deoptFrame;
        /** A pointer to the object I am looking for. */
        protected Pointer targetPointer;
        private PathElements result;

        protected FrameSlotVisitor() {
        }

        public void initialize(final CodePointer ipArg, final DeoptimizedFrame deoptFrameArg, final Pointer targetArg, PathElements res) {
            ip = ipArg;
            deoptFrame = deoptFrameArg;
            targetPointer = targetArg;
            result = res;
        }

        @Override
        public boolean visitObjectReference(final Pointer stackSlot, boolean compressed) {
            final Log trace = Log.noopLog();
            if (stackSlot.isNull()) {
                return true;
            }

            final Pointer referentPointer = ReferenceAccess.singleton().readObjectAsUntrackedPointer(stackSlot, compressed);
            trace.string("  referentPointer: ").hex(referentPointer);
            if (referentPointer.equal(targetPointer)) {
                result.add(StackElement.factory(stackSlot, ip, deoptFrame));
                return result.isSpaceAvailable();
            }
            return true;
        }
    }

    private static class BootImageHeapObjRefVisitor implements ObjectReferenceVisitor {
        // Lazily-initialized instance state.
        protected Object target;
        protected Object container;
        private PathElements result;

        protected BootImageHeapObjRefVisitor() {
        }

        @SuppressWarnings("hiding")
        public void initialize(Object container, Object target, PathElements result) {
            this.container = container;
            this.target = target;
            this.result = result;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed) {
            if (objRef.isNull()) {
                return true;
            }
            final Object referent = ReferenceAccess.singleton().readObjectAt(objRef, compressed);
            if (referent == target) {
                final UnsignedWord offset = objRef.subtract(Word.objectToUntrackedPointer(container));
                result.add(BootImageHeapElement.factory(container, offset));
                return result.isSpaceAvailable();
            }
            return true;
        }
    }

    private static class HeapObjectVisitor implements ObjectVisitor {

        // Lazily-initialized instance state.
        // - A pointer to the target object.
        protected Pointer targetPointer;
        private PathElements result;

        protected HeapObjectVisitor() {
        }

        public void initialize(final Object targetObject, PathElements res) {
            targetPointer = Word.objectToUntrackedPointer(targetObject);
            result = res;
        }

        @Override
        public boolean visitObject(final Object containerObject) {
            final Pointer containerPointer = Word.objectToUntrackedPointer(containerObject);
            heapObjRefVisitor.initialize(containerPointer, targetPointer, result);
            return InteriorObjRefWalker.walkObject(containerObject, heapObjRefVisitor);
        }
    }

    private static class HeapObjRefVisitor implements ObjectReferenceVisitor {

        // Lazily-initialized state.
        // - A pointer to the container of the object reference to the target.
        protected Pointer containerPointer;
        // - A pointer to the target object.
        protected Pointer targetPointer;
        private PathElements result;

        protected HeapObjRefVisitor() {
        }

        public void initialize(final Pointer container, final Pointer target, PathElements res) {
            // Initialize lazily-initialized state.
            containerPointer = container;
            targetPointer = target;
            result = res;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed) {
            if (objRef.isNull()) {
                return true;
            }
            final Pointer referentPointer = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
            if (referentPointer.equal(targetPointer)) {
                final UnsignedWord offset = objRef.subtract(containerPointer);
                final Object containerObject = containerPointer.toObject();
                result.add(HeapElement.factory(containerObject, offset));
                return result.isSpaceAvailable();
            }
            return true;
        }
    }

    /** A path element for a leaf. */
    public static class LeafElement extends PathElement {

        public static LeafElement factory(final Object leaf) {
            return new LeafElement(leaf);
        }

        @Override
        public Object getObject() {
            return leaf;
        }

        @Override
        public Log toLog(Log log) {
            log.string("[leaf:");
            log.string("  ").object(leaf);
            log.string("]");
            return log;
        }

        protected LeafElement(final Object leaf) {
            this.leaf = leaf;
        }

        // Immutable state.
        protected final Object leaf;
    }

    /** A path element for a reference from a Object field. */
    public static class HeapElement extends PathElement {

        public static HeapElement factory(final Object base, final UnsignedWord offset) {
            return new HeapElement(base, offset);
        }

        @Override
        public Object getObject() {
            return base;
        }

        @Override
        public Log toLog(Log log) {
            log.string("[heap:");
            log.string("  base: ").object(base);
            log.string("  offset: ").unsigned(offset);
            final Pointer objPointer = Word.objectToUntrackedPointer(base);
            final Pointer fieldObjRef = objPointer.add(offset);
            final Pointer fieldPointer = fieldObjRef.readWord(0);
            log.string("  field: ").hex(fieldPointer);
            log.string("]");
            return log;
        }

        protected HeapElement(final Object base, final UnsignedWord offset) {
            this.base = base;
            this.offset = offset;
        }

        // Immutable state.
        protected final Object base;
        protected final UnsignedWord offset;
    }

    /** A path element for a reference from a stack frame. */
    public static class StackElement extends PathElement {

        public static StackElement factory(final Pointer frameSlot, final CodePointer ip, final DeoptimizedFrame deoptFrame) {
            return new StackElement(frameSlot, ip, deoptFrame);
        }

        @Override
        public Object getObject() {
            // Stack frames are roots.
            return null;
        }

        @Override
        public Log toLog(Log log) {
            log.string("[stack:");
            log.string("  slot: ").hex(stackSlot);
            log.string("  deoptSourcePC: ").hex(deoptSourcePC);
            log.string("  ip: ").hex(ip);
            log.string("  value: ").hex(slotValue);
            log.string("]");
            return log;
        }

        protected StackElement(final Pointer stackSlot, final CodePointer ip, final DeoptimizedFrame deoptFrame) {
            // Turn the arguments into the data I need to for the log.
            this.stackSlot = stackSlot;
            this.deoptSourcePC = deoptFrame != null ? deoptFrame.getSourcePC() : WordFactory.nullPointer();
            this.ip = ip;
            this.slotValue = stackSlot.readWord(0);
        }

        // Immutable state.
        // - Supplied by the caller.
        protected final Pointer stackSlot;
        protected final CodePointer ip;
        protected final CodePointer deoptSourcePC;
        protected final Pointer slotValue;
    }

    /** A path element for a reference from the native image heap. */
    public static class BootImageHeapElement extends PathElement {

        public static BootImageHeapElement factory(final Object base, final UnsignedWord offset) {
            return new BootImageHeapElement(base, offset);
        }

        @Override
        public Object getObject() {
            // Native image heap objects are roots.
            return null;
        }

        @Override
        public Log toLog(Log log) {
            log.string("[native image heap:");
            log.string("  object: ").object(base);
            log.string("  offset: ").unsigned(offset);
            log.string("]");
            return log;
        }

        protected BootImageHeapElement(final Object base, final UnsignedWord offset) {
            this.base = base;
            this.offset = offset;
        }

        // Immutable state.
        protected final Object base;
        protected final UnsignedWord offset;
    }

    /** A path element for a cyclic reference. */
    public static class CyclicElement extends PathElement {

        public static CyclicElement factory(final Object previous) {
            return new CyclicElement(previous);
        }

        @Override
        public Object getObject() {
            // Cyclic elements are roots.
            return null;
        }

        @Override
        public Log toLog(Log log) {
            log.string("[cyclic:");
            log.string("  previous: ").object(previous);
            log.string("]");
            return log;
        }

        protected CyclicElement(final Object previous) {
            this.previous = previous;
        }

        // Immutable state.
        protected final Object previous;
    }

    /** For debugging. */
    public static final class TestingBackDoor {

        private TestingBackDoor() {
            // No instances.
        }

        public static PathElement findPathToObject(PathExhibitor exhibitor, Object obj) {
            PathElements result = new PathElements(1);
            findPathToObject(exhibitor, obj, result);
            return result.isEmpty() ? null : result.get(0);
        }

        public static void findPathToObject(PathExhibitor exhibitor, Object obj, PathElements result) {
            FindPathToObjectOperation op = new FindPathToObjectOperation(exhibitor, obj, result);
            op.enqueue();
        }
    }

    private static final class FindPathToObjectOperation extends JavaVMOperation {
        private final PathExhibitor exhibitor;
        private final Object object;
        private PathElements result;

        FindPathToObjectOperation(PathExhibitor exhibitor, Object object, PathElements result) {
            super("FindPathToObjectOperation", SystemEffect.SAFEPOINT);
            this.exhibitor = exhibitor;
            this.object = object;
            this.result = result;
        }

        @Override
        protected void operate() {
            exhibitor.findPathToObject(object, result);
        }
    }

    public static class PathElements {
        private PathElement[] elements;
        private int size;

        public PathElements(int maxSize) {
            elements = new PathElement[maxSize];
            size = 0;
        }

        public boolean isSpaceAvailable() {
            return size < elements.length;
        }

        public boolean isEmpty() {
            return size == 0;
        }

        public int size() {
            return size;
        }

        public PathElement get(int index) {
            return elements[index];
        }

        public void add(PathElement elem) {
            if (!isInterfering(elem.getObject())) {
                elements[size++] = elem;
            }
        }

        public void clear() {
            Arrays.fill(elements, null);
            size = 0;
        }

        public void toLog(Log log) {
            for (int i = 0; i < size; i++) {
                elements[i].toLog(log);
            }
        }

        private static boolean isInterfering(Object currentObject) {
            return currentObject instanceof PathElement || currentObject instanceof FindPathToObjectOperation;
        }
    }
}
