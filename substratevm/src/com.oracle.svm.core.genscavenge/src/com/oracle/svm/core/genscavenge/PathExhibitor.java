/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.svm.core.snippets.KnownIntrinsics.readReturnAddress;

import java.util.ArrayList;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.heap.NativeImageInfo;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;

public class PathExhibitor {

    public static PathExhibitor factory() {
        return new PathExhibitor();
    }

    public boolean findPathToRoot(final Object leaf) {
        // Put the obj in the path as a leaf.
        PathElement currentElement = LeafElement.factory(leaf);
        Object currentObject = leaf;
        for (; /* break */;) {
            // Current is an element of the path.
            path.add(currentElement);
            // Walk backwards one step.
            currentElement = findPathToObject(currentObject);
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
            // - The path leads to a path element
            if (currentObject instanceof PathElement) {
                // Record that.
                final InterferenceElement interference = InterferenceElement.factory();
                path.add(interference);
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

    protected PathElement findPathToObject(final Object obj) {
        PathElement result = null;
        if (obj == null) {
            return result;
        }
        // Look in all the roots.
        // - Look in the regular heap.
        result = findPathInHeap(obj);
        if (result != null) {
            return result;
        }
        // - Look in the native image heap.
        result = findPathInBootImageHeap(obj);
        if (result != null) {
            return result;
        }
        // - Look in the stack.
        result = findPathInStack(obj);
        if (result != null) {
            return result;
        }
        return null;
    }

    protected StackElement findPathInStack(final Object obj) {
        stackFrameVisitor.initialize(obj);
        Pointer sp = readCallerStackPointer();
        CodePointer ip = readReturnAddress();
        JavaStackWalker.walkCurrentThread(sp, ip, stackFrameVisitor);
        final StackElement result = frameSlotVisitor.getElement();
        return result;
    }

    protected PathElement findPathInBootImageHeap(final Object targetObject) {
        PathElement result = null;
        // Look in each of the partitions of the heap.
        if (result == null) {
            result = findPathInBootImageHeap(targetObject, NativeImageInfo.firstReadOnlyPrimitiveObject, NativeImageInfo.lastReadOnlyPrimitiveObject);
        }
        if (result == null) {
            result = findPathInBootImageHeap(targetObject, NativeImageInfo.firstReadOnlyReferenceObject, NativeImageInfo.lastReadOnlyReferenceObject);
        }
        if (result == null) {
            result = findPathInBootImageHeap(targetObject, NativeImageInfo.firstWritablePrimitiveObject, NativeImageInfo.lastWritablePrimitiveObject);
        }
        if (result == null) {
            result = findPathInBootImageHeap(targetObject, NativeImageInfo.firstWritableReferenceObject, NativeImageInfo.lastWritableReferenceObject);
        }
        return result;
    }

    protected PathElement findPathInBootImageHeap(final Object targetObject, final Object firstObject, final Object lastObject) {
        if ((firstObject == null) || (lastObject == null)) {
            return null;
        }
        final Pointer targetPointer = Word.objectToUntrackedPointer(targetObject);
        final Pointer firstPointer = Word.objectToUntrackedPointer(firstObject);
        final Pointer lastPointer = Word.objectToUntrackedPointer(lastObject);
        Pointer current = firstPointer;
        while (current.belowOrEqual(lastPointer)) {
            final Object bihObject = current.toObject();
            // I am not interested in references from path elements.
            if (!checkForInterference(bihObject)) {
                bootImageHeapObjRefVisitor.initialize(current, targetPointer);
                if (!InteriorObjRefWalker.walkObject(bihObject, bootImageHeapObjRefVisitor)) {
                    break;
                }
            }
            current = LayoutEncoding.getObjectEnd(bihObject);
        }
        return bootImageHeapObjRefVisitor.getElement();
    }

    protected HeapElement findPathInHeap(final Object obj) {
        heapObjectVisitor.initialize(obj);
        final HeapImpl heap = HeapImpl.getHeapImpl();
        heap.walkObjects(heapObjectVisitor);
        final HeapElement result = heapObjRefVisitor.getElement();
        return result;
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

    protected static boolean checkForInterference(final Object currentObject) {
        boolean result = false;
        if (currentObject instanceof PathElement) {
            result = true;
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

        protected FrameVisitor() {
            super();
            // All other state is lazily initialized.
        }

        public void initialize(final Object targetObject) {
            final Log trace = Log.noopLog();
            trace.string("[PathExhibitor.FrameVisitor.initialize:").newline();
            trace.string("  targetObject: ").object(targetObject);
            this.targetPointer = Word.objectToUntrackedPointer(targetObject);
            trace.string("]").newline();
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while verifying the heap.")
        public boolean visitFrame(Pointer sp, CodePointer ip, DeoptimizedFrame deoptimizedFrame) {
            final Log trace = Log.noopLog();
            trace.string("[PathExhibitor.FrameVisitor.visitFrame:").newline();
            trace.string("  sp: ").hex(sp);
            trace.string("  ip: ").hex(ip);
            trace.string("  deoptFrame: ").object(deoptimizedFrame);
            trace.newline();
            frameSlotVisitor.initialize(ip, deoptimizedFrame, targetPointer);
            boolean result = CodeInfoTable.visitObjectReferences(sp, ip, deoptimizedFrame, frameSlotVisitor);
            trace.string("  returns: ").bool(result);
            trace.string("]").newline();
            return result;
        }

        @Override
        public boolean epilogue() {
            targetPointer = WordFactory.nullPointer();
            return true;
        }
    }

    private static class FrameSlotVisitor implements ObjectReferenceVisitor {

        // Mutable state: if I have found the target object.
        protected StackElement element;

        // Lazily-initialized state,
        // so I do not allocate one of these for each target for each frame.
        /** The instruction pointer of the current frame. */
        protected CodePointer ip;
        protected DeoptimizedFrame deoptFrame;
        /** A pointer to the object I am looking for. */
        protected Pointer targetPointer;

        protected FrameSlotVisitor() {
            super();
            // All other state is initialized lazily.
        }

        public void initialize(final CodePointer ipArg, final DeoptimizedFrame deoptFrameArg, final Pointer targetArg) {
            final Log trace = Log.noopLog();
            trace.string("[PathExhibitor.FrameSlotVisitor.initialize:").newline();
            element = null;
            ip = ipArg;
            deoptFrame = deoptFrameArg;
            targetPointer = targetArg;
            trace.string("  element: ").object(element);
            trace.string("  ip: ").hex(ip);
            trace.string("  deoptFrame: ").object(deoptFrame);
            trace.string("  targetPointer: ").hex(targetPointer);
            trace.string("]").newline();
        }

        @Override
        public boolean visitObjectReference(final Pointer stackSlot, boolean compressed) {
            final Log trace = Log.noopLog();
            trace.string("[PathExhibitor.FrameSlotVisitor.visitObjectReference:").newline();
            trace.string("  stackSlot: ").hex(stackSlot);
            final boolean result;
            if (stackSlot.isNull()) {
                // Null is not the object I am looking for.
                result = true;
            } else {
                final Pointer referentPointer = ReferenceAccess.singleton().readObjectAsUntrackedPointer(stackSlot, compressed);
                trace.string("  referentPointer: ").hex(referentPointer);
                if (referentPointer.equal(targetPointer)) {
                    element = StackElement.factory(stackSlot, ip, deoptFrame);
                    // Found what I was looking for; do not continue searching.
                    result = false;
                } else {
                    result = true;
                }
            }
            trace.string("  returns: ").bool(result);
            trace.string("]").newline();
            return result;
        }

        public StackElement getElement() {
            final Log trace = Log.noopLog();
            trace.string("[PathExhibitor.FrameSlotVisitor.getElement:").newline();
            trace.string("  returns element: ").object(element);
            trace.string("]").newline();
            return element;
        }
    }

    private static class BootImageHeapObjRefVisitor implements ObjectReferenceVisitor {

        // Lazily-initialized instance state.
        protected Pointer targetPointer;
        protected Pointer containerPointer;
        protected BootImageHeapElement element;

        protected BootImageHeapObjRefVisitor() {
            super();
            // All other state is initialized lazily.
        }

        @Override
        public boolean prologue() {
            targetPointer = WordFactory.nullPointer();
            element = null;
            return true;
        }

        public void initialize(final Pointer container, final Pointer target) {
            containerPointer = container;
            targetPointer = target;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed) {
            if (objRef.isNull()) {
                // Null is not the object I am looking for.
                return true;
            }
            final Pointer referentPointer = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
            if (referentPointer.equal(targetPointer)) {
                final Object containerObject = containerPointer.toObject();
                final UnsignedWord offset = objRef.subtract(containerPointer);
                element = BootImageHeapElement.factory(containerObject, offset, referentPointer);
                // Found what I was looking for; do not continue searching.
                return false;
            }
            return true;
        }

        public BootImageHeapElement getElement() {
            return element;
        }
    }

    private static class HeapObjectVisitor implements ObjectVisitor {

        // Lazily-initialized instance state.
        // - A pointer to the target object.
        protected Pointer targetPointer;

        protected HeapObjectVisitor() {
            super();
            // All other state is initialized lazily.
        }

        @Override
        public boolean prologue() {
            targetPointer = WordFactory.nullPointer();
            return true;
        }

        public void initialize(final Object targetObject) {
            targetPointer = Word.objectToUntrackedPointer(targetObject);
        }

        @Override
        public boolean visitObject(final Object containerObject) {
            if (PathExhibitor.checkForInterference(containerObject)) {
                // I am not interested in references from path elements; continue visiting.
                return true;
            }
            final Pointer containerPointer = Word.objectToUntrackedPointer(containerObject);
            heapObjRefVisitor.initialize(containerPointer, targetPointer);
            if (!InteriorObjRefWalker.walkObject(containerObject, heapObjRefVisitor)) {
                // Found what I was looking for; do not continue visiting.
                return false;
            }
            return true;
        }
    }

    private static class HeapObjRefVisitor implements ObjectReferenceVisitor {

        // Lazily-initialized state.
        // - A pointer to the container of the object reference to the target.
        protected Pointer containerPointer;
        // - A pointer to the target object.
        protected Pointer targetPointer;
        // A path element if the target was found.
        protected HeapElement element;

        protected HeapObjRefVisitor() {
            super();
            // All other state is initialized lazily.
        }

        @Override
        public boolean prologue() {
            // Reset lazily-initialized state.
            containerPointer = WordFactory.nullPointer();
            targetPointer = WordFactory.nullPointer();
            return true;
        }

        public void initialize(final Pointer container, final Pointer target) {
            // Initialize lazily-initialized state.
            containerPointer = container;
            targetPointer = target;
        }

        public HeapElement getElement() {
            return element;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed) {
            if (objRef.isNull()) {
                // Null is not the object I am looking for.
                return true;
            }
            final Pointer referentPointer = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
            if (referentPointer.equal(targetPointer)) {
                final UnsignedWord offset = objRef.subtract(containerPointer);
                final Object containerObject = containerPointer.toObject();
                element = HeapElement.factory(containerObject, offset);
                // Found what I was looking for; do not continue searching.
                return false;
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

        public static BootImageHeapElement factory(final Object base, final UnsignedWord offset, final Pointer field) {
            return new BootImageHeapElement(base, offset, field);
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
            log.string("  field: ").hex(field);
            log.string("]");
            return log;
        }

        protected BootImageHeapElement(final Object base, final UnsignedWord offset, final Pointer field) {
            this.base = base;
            this.offset = offset;
            this.field = field;
        }

        // Immutable state.
        protected final Object base;
        protected final UnsignedWord offset;
        protected final Pointer field;
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

    /** A path element for when I find a PathElement. */
    public static class InterferenceElement extends PathElement {

        public static InterferenceElement factory() {
            return new InterferenceElement();
        }

        @Override
        public Object getObject() {
            // Cyclic elements are roots.
            return null;
        }

        @Override
        public Log toLog(Log log) {
            log.string("[interference");
            log.string("]");
            return log;
        }

        protected InterferenceElement() {
            // Nothing to do.
        }
    }

    /** For debugging. */
    public static final class TestingBackDoor {

        private TestingBackDoor() {
            // No instances.
        }

        public static PathElement findPathToObject(final PathExhibitor exhibitor, final Object obj) {
            return exhibitor.findPathToObject(obj);
        }
    }
}
