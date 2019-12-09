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

import java.util.ArrayList;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
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
import com.oracle.svm.core.log.StringBuilderLog;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;

/**
 * Can be used to debug object liveness.
 */
public class PathExhibitor {

    public static PathExhibitor factory() {
        return new PathExhibitor();
    }

    @NeverInline("Starting a stack walk in the caller frame")
    public void findPathToRange(Pointer rangeBegin, Pointer rangeEnd) {
        assert VMOperation.isInProgressAtSafepoint();
        if (rangeBegin.isNull() || rangeBegin.aboveThan(rangeEnd)) {
            return;
        }
        PathEdge edge = new PathEdge();
        findPathToTarget(new RangeTargetMatcher(rangeBegin, rangeEnd), edge, KnownIntrinsics.readCallerStackPointer());
        if (edge.isFilled()) {
            path.add(edge.getTo());
            path.add(edge.getFrom());
            Object fromObj = KnownIntrinsics.convertUnknownValue(edge.getFrom().getObject(), Object.class);

            // Add the rest of the path
            findPathToRoot(fromObj, edge, KnownIntrinsics.readCallerStackPointer());
        }
    }

    @NeverInline("Starting a stack walk in the caller frame")
    public void findPathToRoot(Object leaf) {
        findPathToRoot(leaf, new PathEdge(), KnownIntrinsics.readCallerStackPointer());
    }

    void findPathToRoot(Object leaf, PathEdge currentEdge, Pointer currentThreadWalkStackPointer) {
        assert VMOperation.isInProgressAtSafepoint();
        if (leaf == null) {
            return;
        }
        Object currentTargetObj = leaf;
        for (; /* break */;) {
            // Walk backwards one step.
            currentEdge.reset();
            findPathToTarget(new ObjectTargetMatcher(currentTargetObj), currentEdge, currentThreadWalkStackPointer);

            PathElement currentElement = null;
            if (currentEdge.isFilled()) {
                currentElement = currentEdge.getFrom();
                if (path.isEmpty()) {
                    path.add(currentEdge.getTo());
                }
            }
            // Look for reasons to stop walking.
            // - I didn't find a path to the current object.
            if (currentElement == null) {
                // No pointer to current object: The path ends here.
                break;
            }
            // Update the current object.
            currentTargetObj = KnownIntrinsics.convertUnknownValue(currentElement.getObject(), Object.class);
            // - The current element is a root.
            if (currentTargetObj == null) {
                // Add element to path and stop.
                path.add(currentElement);
                break;
            }
            // - I have seen this object before.
            if (checkForCycles(currentTargetObj)) {
                final CyclicElement cyclic = CyclicElement.factory(currentTargetObj);
                path.add(cyclic);
                break;
            }
            path.add(currentElement);
        }
    }

    public PathElement[] getPath() {
        return path.toArray(new PathElement[0]);
    }

    public void toLog(final Log log) {
        for (PathElement element : path) {
            log.newline();
            element.toLog(log);
        }
    }

    protected void findPathToTarget(TargetMatcher target, PathEdge edge, Pointer currentThreadWalkStackPointer) {
        assert target != null && !edge.isFilled();
        // Look in all the roots.
        findPathInHeap(target, edge);
        findPathInBootImageHeap(target, edge);
        findPathInStack(target, edge, currentThreadWalkStackPointer);
    }

    protected void findPathInStack(TargetMatcher target, PathEdge edge, Pointer currentThreadWalkStackPointer) {
        if (edge.isFilled()) {
            return;
        }

        stackFrameVisitor.initialize(target, edge);
        JavaStackWalker.walkCurrentThread(currentThreadWalkStackPointer, stackFrameVisitor);
        stackFrameVisitor.reset();

        if (SubstrateOptions.MultiThreaded.getValue()) {
            IsolateThread thread = VMThreads.firstThread();
            while (!edge.isFilled() && thread.isNonNull()) {
                if (thread.notEqual(CurrentIsolate.getCurrentThread())) { // walked above
                    stackFrameVisitor.initialize(target, edge);
                    JavaStackWalker.walkThread(thread, stackFrameVisitor);
                    stackFrameVisitor.reset();
                }
                thread = VMThreads.nextThread(thread);
            }
        }
    }

    protected void findPathInBootImageHeap(TargetMatcher target, PathEdge result) {
        Heap.getHeap().walkImageHeapObjects(new ObjectVisitor() {
            @Override
            public boolean visitObject(Object obj) {
                if (result.isFilled()) {
                    return false;
                }
                bootImageHeapObjRefVisitor.initialize(obj, target, result);
                return InteriorObjRefWalker.walkObject(obj, bootImageHeapObjRefVisitor);
            }
        });
    }

    protected void findPathInHeap(TargetMatcher target, PathEdge result) {
        if (result.isFilled()) {
            return;
        }
        heapObjectVisitor.initialize(target, result);
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

        @Override
        public String toString() {
            StringBuilderLog log = new StringBuilderLog();
            toLog(log);
            return log.getResult();
        }
    }

    interface TargetMatcher {
        boolean matches(Object obj);
    }

    static class ObjectTargetMatcher implements TargetMatcher {
        final Object target;

        ObjectTargetMatcher(Object target) {
            this.target = target;
        }

        @Override
        public boolean matches(Object obj) {
            return (obj == target);
        }
    }

    static class RangeTargetMatcher implements TargetMatcher {
        final Pointer targetBegin;
        final Pointer targetEnd;

        RangeTargetMatcher(Pointer rangeBegin, Pointer rangeEndExclusive) {
            this.targetBegin = rangeBegin;
            this.targetEnd = rangeEndExclusive;
        }

        @Override
        public boolean matches(Object obj) {
            Pointer objAddr = Word.objectToUntrackedPointer(obj);
            return objAddr.aboveOrEqual(targetBegin) && objAddr.belowThan(targetEnd);
        }
    }

    static class AbstractVisitor {
        TargetMatcher target;
        PathEdge result;

        void initialize(TargetMatcher targetMatcher, PathEdge resultPath) {
            this.target = targetMatcher;
            this.result = resultPath;
        }

        void reset() {
            initialize(null, null);
        }
    }

    public static class FrameVisitor extends AbstractVisitor implements StackFrameVisitor {
        FrameVisitor() {
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while visiting stack frames.")
        public boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptimizedFrame) {
            frameSlotVisitor.initialize(ip, deoptimizedFrame, target, result);
            return CodeInfoTable.visitObjectReferences(sp, ip, codeInfo, deoptimizedFrame, frameSlotVisitor);
        }
    }

    private static class FrameSlotVisitor extends AbstractVisitor implements ObjectReferenceVisitor {
        CodePointer ip;
        DeoptimizedFrame deoptFrame;

        FrameSlotVisitor() {
        }

        void initialize(CodePointer ipArg, DeoptimizedFrame deoptFrameArg, TargetMatcher targetMatcher, PathEdge edge) {
            super.initialize(targetMatcher, edge);
            ip = ipArg;
            deoptFrame = deoptFrameArg;
        }

        @Override
        public boolean visitObjectReference(final Pointer stackSlot, boolean compressed) {
            Log trace = Log.noopLog();
            if (stackSlot.isNull()) {
                return true;
            }
            Pointer referentPointer = ReferenceAccess.singleton().readObjectAsUntrackedPointer(stackSlot, compressed);
            trace.string("  referentPointer: ").hex(referentPointer);
            if (target.matches(referentPointer.toObject())) {
                result.fill(StackElement.factory(stackSlot, ip, deoptFrame), LeafElement.factory(referentPointer.toObject()));
                return false;
            }
            return true;
        }
    }

    private static class BootImageHeapObjRefVisitor extends AbstractVisitor implements ObjectReferenceVisitor {
        Object container;

        BootImageHeapObjRefVisitor() {
        }

        void initialize(Object containerObj, TargetMatcher targetMatcher, PathEdge resultPath) {
            super.initialize(targetMatcher, resultPath);
            this.container = containerObj;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed) {
            if (objRef.isNull()) {
                return true;
            }
            Object referent = ReferenceAccess.singleton().readObjectAt(objRef, compressed);
            if (target.matches(referent)) {
                final UnsignedWord offset = objRef.subtract(Word.objectToUntrackedPointer(container));
                result.fill(BootImageHeapElement.factory(container, offset), LeafElement.factory(referent));
                return false;
            }
            return true;
        }
    }

    private static class HeapObjectVisitor extends AbstractVisitor implements ObjectVisitor {
        HeapObjectVisitor() {
        }

        @Override
        public boolean visitObject(final Object containerObject) {
            final Pointer containerPointer = Word.objectToUntrackedPointer(containerObject);
            heapObjRefVisitor.initialize(containerPointer, target, result);
            return InteriorObjRefWalker.walkObject(containerObject, heapObjRefVisitor);
        }
    }

    private static class HeapObjRefVisitor extends AbstractVisitor implements ObjectReferenceVisitor {
        Pointer containerPointer;

        HeapObjRefVisitor() {
        }

        public void initialize(Pointer container, TargetMatcher targetMatcher, PathEdge edge) {
            super.initialize(targetMatcher, edge);
            containerPointer = container;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed) {
            if (objRef.isNull()) {
                return true;
            }
            Object containerObject = containerPointer.toObject();
            if (!isInterfering(containerObject)) {
                Pointer referentPointer = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
                if (target.matches(referentPointer.toObject())) {
                    UnsignedWord offset = objRef.subtract(containerPointer);
                    result.fill(HeapElement.factory(containerObject, offset), LeafElement.factory(referentPointer.toObject()));
                    return false;
                }
            }
            return true;
        }

        @NeverInline("Starting a stack walk in the caller frame")
        static boolean isInterfering(Object currentObject) {
            return currentObject instanceof PathElement || currentObject instanceof FindPathToObjectOperation || currentObject instanceof TargetMatcher;
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
            PathEdge result = new PathEdge();
            FindPathToObjectOperation op = new FindPathToObjectOperation(exhibitor, obj, result);
            op.enqueue();
            return result.isFilled() ? result.getFrom() : null;
        }
    }

    private static final class FindPathToObjectOperation extends JavaVMOperation {
        private final PathExhibitor exhibitor;
        private final Object object;
        private PathEdge result;

        FindPathToObjectOperation(PathExhibitor exhibitor, Object object, PathEdge result) {
            super("FindPathToObjectOperation", SystemEffect.SAFEPOINT);
            this.exhibitor = exhibitor;
            this.object = object;
            this.result = result;
        }

        @Override
        @NeverInline("Starting a stack walk.")
        protected void operate() {
            exhibitor.findPathToRoot(object, result, KnownIntrinsics.readCallerStackPointer());
        }
    }

    public static class PathEdge {
        private PathElement from;
        private PathElement to;

        public PathEdge() {
        }

        public boolean isFilled() {
            return from != null && to != null;
        }

        public PathElement getFrom() {
            return from;
        }

        public PathElement getTo() {
            return to;
        }

        public void fill(PathElement fromElem, PathElement toElem) {
            from = fromElem;
            to = toElem;
        }

        public void reset() {
            from = null;
            to = null;
        }

        public void toLog(Log log) {
            from.toLog(log);
            to.toLog(log);
        }
    }
}
