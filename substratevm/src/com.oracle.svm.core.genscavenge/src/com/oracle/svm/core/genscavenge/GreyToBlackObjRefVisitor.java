/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionKey;

/**
 * This visitor is handed *Pointers to Object references* and if necessary it promotes the
 * referenced Object and replaces the Object reference with a forwarding pointer.
 *
 * This turns an individual Object reference from grey to black.
 *
 * Since this visitor is used during collection, one instance of it is constructed during native
 * image generation.
 *
 * The vanilla visitObjectReference method is not inlined, but there is a visitObjectReferenceInline
 * available for performance critical code.
 */
public class GreyToBlackObjRefVisitor implements ObjectReferenceVisitor {

    @Platforms(Platform.HOSTED_ONLY.class)
    public static GreyToBlackObjRefVisitor factory() {
        return new GreyToBlackObjRefVisitor();
    }

    @Override
    public boolean visitObjectReference(final Pointer objRef, boolean compressed) {
        return visitObjectReferenceInline(objRef, compressed);
    }

    /**
     * This visitor is deals in *Pointers to Object references*. As such it uses Pointer.readObject
     * and Pointer.writeObject on the Pointer, not Pointer.toObject and Word.fromObject(o).
     */
    @Override
    @AlwaysInline("GC performance")
    public boolean visitObjectReferenceInline(final Pointer objRef, boolean compressed) {
        getCounters().noteObjRef();
        final Log trace = Log.noopLog().string("[GreyToBlackObjRefVisitor.visitObjectReferenceInline:").string("  objRef: ").hex(objRef);
        if (objRef.isNull()) {
            getCounters().noteNullObjRef();
            trace.string(" null objRef ").hex(objRef).string("]").newline();
            return true;
        }
        // Read the referenced Object, carefully.
        final Pointer p = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
        trace.string("  p: ").hex(p);
        // It might be null.
        if (p.isNull()) {
            getCounters().noteNullReferent();
            // Nothing to do.
            trace.string(" null").string("]").newline();
            return true;
        }
        final UnsignedWord header = ObjectHeader.readHeaderFromPointer(p);
        final ObjectHeader ohi = HeapImpl.getHeapImpl().getObjectHeader();
        // It might be a forwarding pointer.
        if (ohi.isForwardedHeader(header)) {
            getCounters().noteForwardedReferent();
            trace.string("  forwards to ");
            // Update the reference to point to the forwarded Object.
            final Object obj = ohi.getForwardedObject(p);
            ReferenceAccess.singleton().writeObjectAt(objRef, obj, compressed);
            trace.object(obj);
            if (trace.isEnabled()) {
                trace.string("  objectHeader: ").string(ohi.toStringFromObject(obj)).string("]").newline();
            }
            return true;
        }
        // It might be a real Object.
        final Object obj = p.toObject();
        // If the object is not a heap object there's nothing to do.
        if (ohi.isNonHeapAllocatedHeader(header)) {
            getCounters().noteNonHeapReferent();
            // Non-heap objects do not get promoted.
            trace.string("  Non-heap obj: ").object(obj);
            if (trace.isEnabled()) {
                trace.string("  objectHeader: ").string(ohi.toStringFromObject(obj)).string("]").newline();
            }
            return true;
        }
        // Otherwise, promote it if necessary, and update the Object reference.
        trace.string(" ").object(obj);
        if (trace.isEnabled()) {
            trace.string("  objectHeader: ").string(ohi.toStringFromObject(obj)).newline();
        }
        // Promote the Object if necessary, making it at least grey, and ...
        final Object copy = HeapImpl.getHeapImpl().promoteObject(obj);
        trace.string("  copy: ").object(copy);
        if (trace.isEnabled()) {
            trace.string("  objectHeader: ").string(ohi.toStringFromObject(copy));
        }
        // ... update the reference to point to the copy, making the reference black.
        if (copy != obj) {
            getCounters().noteCopiedReferent();
            trace.string(" updating objRef: ").hex(objRef).string(" with copy: ").object(copy);
            ReferenceAccess.singleton().writeObjectAt(objRef, copy, compressed);
        } else {
            getCounters().noteUnmodifiedReference();
        }
        trace.string("]").newline();
        return true;
    }

    protected Counters getCounters() {
        return counters;
    }

    public Counters openCounters() {
        return getCounters().open();
    }

    public static class Options {
        @Option(help = "Develop demographics of the object references visited.")//
        public static final HostedOptionKey<Boolean> GreyToBlackObjRefDemographics = new HostedOptionKey<>(false);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected GreyToBlackObjRefVisitor() {
        super();
        if (Options.GreyToBlackObjRefDemographics.getValue()) {
            counters = RealCounters.factory();
        } else {
            counters = NoopCounters.factory();
        }
    }

    // Immutable state.
    protected final Counters counters;

    /** A set of counters. The default implementation is a noop. */
    public interface Counters extends AutoCloseable {

        Counters open();

        @Override
        void close();

        boolean isOpen();

        void noteObjRef();

        void noteNullObjRef();

        void noteNullReferent();

        void noteForwardedReferent();

        void noteNonHeapReferent();

        void noteCopiedReferent();

        void noteUnmodifiedReference();

        void toLog();

        void reset();
    }

    public static class RealCounters implements Counters {

        public static RealCounters factory() {
            return new RealCounters();
        }

        @Override
        public RealCounters open() {
            reset();
            isOpened = true;
            return this;
        }

        @Override
        public void close() {
            toLog();
            reset();
        }

        @Override
        public boolean isOpen() {
            return isOpened;
        }

        @Override
        public void noteObjRef() {
            objRef += 1L;
        }

        @Override
        public void noteNullObjRef() {
            nullObjRef += 1L;
        }

        @Override
        public void noteNullReferent() {
            nullReferent += 1L;
        }

        @Override
        public void noteForwardedReferent() {
            forwardedReferent += 1L;
        }

        @Override
        public void noteNonHeapReferent() {
            nonHeapReferent += 1L;
        }

        @Override
        public void noteCopiedReferent() {
            copiedReferent += 1L;
        }

        @Override
        public void noteUnmodifiedReference() {
            unmodifiedReference += 1L;
        }

        @Override
        public void toLog() {
            final Log log = Log.log();
            log.string("[GreyToBlackObjRefVisitor.counters:");
            log.string("  objRef: ").signed(objRef);
            log.string("  nullObjRef: ").signed(nullObjRef);
            log.string("  nullReferent: ").signed(nullReferent);
            log.string("  forwardedReferent: ").signed(forwardedReferent);
            log.string("  nonHeapReferent: ").signed(nonHeapReferent);
            log.string("  copiedReferent: ").signed(copiedReferent);
            log.string("  unmodifiedReference: ").signed(unmodifiedReference);
            log.string("]").newline();
        }

        @Override
        public void reset() {
            objRef = 0L;
            nullObjRef = 0L;
            nullReferent = 0L;
            forwardedReferent = 0L;
            nonHeapReferent = 0L;
            copiedReferent = 0L;
            unmodifiedReference = 0L;
            isOpened = false;
        }

        protected RealCounters() {
            reset();
        }

        protected long objRef;
        protected long nullObjRef;
        protected long nullReferent;
        protected long forwardedReferent;
        protected long nonHeapReferent;
        protected long copiedReferent;
        protected long unmodifiedReference;
        protected boolean isOpened;
    }

    public static class NoopCounters implements Counters {

        public static NoopCounters factory() {
            return new NoopCounters();
        }

        @Override
        public NoopCounters open() {
            return this;
        }

        @Override
        public void close() {
            return;
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public void noteObjRef() {
            return;
        }

        @Override
        public void noteNullObjRef() {
            return;
        }

        @Override
        public void noteNullReferent() {
            return;
        }

        @Override
        public void noteForwardedReferent() {
            return;
        }

        @Override
        public void noteNonHeapReferent() {
            return;
        }

        @Override
        public void noteCopiedReferent() {
            return;
        }

        @Override
        public void noteUnmodifiedReference() {
            return;
        }

        @Override
        public void toLog() {
            return;
        }

        @Override
        public void reset() {
            return;
        }

        protected NoopCounters() {
        }
    }
}
