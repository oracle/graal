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
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.heap.NativeImageInfo;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.snippets.KnownIntrinsics;

/**
 * Run an ObjectReferenceVisitor ({@link GreyToBlackObjRefVisitor}) over any interior object
 * references in the Object, turning this Object from grey to black.
 *
 * This visitor is used during GC and so it must be constructed during native image generation.
 *
 * The vanilla visitObject method is not inlined, but there is a visitObjectInline available for
 * performance critical code.
 */
public final class GreyToBlackObjectVisitor implements ObjectVisitor {

    private final DiagnosticReporter diagnosticReporter;

    @Platforms(Platform.HOSTED_ONLY.class)
    public static GreyToBlackObjectVisitor factory(final ObjectReferenceVisitor objRefVisitor) {
        return new GreyToBlackObjectVisitor(objRefVisitor);
    }

    public void reset() {
        if (diagnosticReporter != null) {
            diagnosticReporter.reset();
        }
    }

    /** Visit the interior Pointers of an Object. */
    @Override
    public boolean visitObject(final Object o) {
        return visitObjectInline(o);
    }

    @Override
    @AlwaysInline("GC performance")
    public boolean visitObjectInline(final Object o) {
        final Log trace = Log.noopLog();
        if (diagnosticReporter != null) {
            diagnosticReporter.noteObject(o);
        }
        // TODO: Why would this be passed a null Object?
        if (o == null) {
            return true;
        }
        trace.string("[GreyToBlackObjectVisitor:").string("  o: ").object(o);
        DiscoverableReferenceProcessing.discoverDiscoverableReference(o);
        InteriorObjRefWalker.walkObjectInline(o, objRefVisitor);
        trace.string("]").newline();
        return true;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private GreyToBlackObjectVisitor(final ObjectReferenceVisitor objRefVisitor) {
        super();
        this.objRefVisitor = objRefVisitor;
        if (DiagnosticReporter.getHistoryLength() > 0) {
            this.diagnosticReporter = new DiagnosticReporter();
            SubstrateUtil.DiagnosticThunkRegister.getSingleton().register(diagnosticReporter);
        } else {
            this.diagnosticReporter = null;
        }
    }

    // Immutable state.
    private final ObjectReferenceVisitor objRefVisitor;

    /*
     * History.
     */

    static final class DiagnosticReporter implements SubstrateUtil.DiagnosticThunk {

        static class Options {

            @Option(help = "Keep history of GreyToBlackObjectVisits.  0 implies no history is kept.")//
            static final HostedOptionKey<Integer> GreyToBlackObjectVisitorDiagnosticHistory = new HostedOptionKey<>(0);
        }

        /** The current value of the history index. */
        private long historyCount;

        /** A history of objects. Kept as Words to avoid holding references. */
        private final Word[] objectHistory;

        /** A history of the headers of those objects. */
        private final UnsignedWord[] headerHistory;

        @Platforms(Platform.HOSTED_ONLY.class)
        DiagnosticReporter() {
            super();
            this.historyCount = 0;
            this.objectHistory = new Word[getHistoryLength()];
            this.headerHistory = new UnsignedWord[getHistoryLength()];
        }

        /** Forget all history. */
        public void reset() {
            historyCount = 0;
            for (int i = 0; i < getHistoryLength(); i += 1) {
                objectHistory[i] = WordFactory.zero();
                headerHistory[i] = WordFactory.zero();
            }
        }

        /** Note a historical object. */
        public void noteObject(Object o) {
            final int index = countToIndex(historyCount);
            /*
             * Converting the object to a Word will require a matching
             * `KnownIntrinsics.convertUnknownValue(objectEntry, Object.class)` to convert it back
             * into an Object.
             */
            objectHistory[index] = Word.objectToUntrackedPointer(o);
            /* Danger: This read might segfault! Is "carefully" careful enough? */
            headerHistory[index] = ObjectHeaderImpl.readHeaderFromObjectCarefully(o);
            historyCount += 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate during printing diagnostics.")
        public void invokeWithoutAllocation() {
            if (historyCount > 0) {
                final Log log = Log.log().string("[GreyToBlackObjectVisitor.RealDiagnosticReporter.invoke:")
                                .string("  history / count:  ")
                                .signed(getHistoryLength()).string(" / ").signed(historyCount)
                                .indent(true);
                final Pointer firstRORPointer = Word.objectToUntrackedPointer(NativeImageInfo.firstReadOnlyReferenceObject);
                final Pointer lastRORPointer = Word.objectToUntrackedPointer(NativeImageInfo.lastReadOnlyReferenceObject);
                final ObjectHeaderImpl ohi = HeapImpl.getHeapImpl().getObjectHeaderImpl();
                /*
                 * Report the history from the next available slot in the ring buffer. The older
                 * history is more reliable, since I have already used that to visit objects. The
                 * most recent history is more suspect, because this is the first use of it.
                 */
                for (int count = 0; count < getHistoryLength(); count += 1) {
                    final int index = countToIndex(historyCount + count);
                    /* The address of the object. */
                    log.string("  index: ").unsigned(index, 3, Log.RIGHT_ALIGN);
                    final Word objectEntry = objectHistory[index];
                    log.string("  objectEntry: ").hex(objectEntry);
                    /* The decoding of the object header. */
                    final UnsignedWord headerEntry = headerHistory[index];
                    final UnsignedWord headerHubBits = ObjectHeader.clearBits(headerEntry);
                    final UnsignedWord headerHeaderBits = ObjectHeaderImpl.getHeaderBitsFromHeaderCarefully(headerEntry);
                    log.string("  headerEntry: ").hex(headerEntry)
                                    .string(" = ").hex(headerHubBits)
                                    .string(" | ").hex(headerHeaderBits)
                                    .string(" / ").string(ohi.toStringFromHeader(headerEntry));
                    /* Print details about the hub if it looks okay. */
                    final boolean headerInImageHeap = ((headerHubBits.aboveOrEqual(firstRORPointer)) && headerHubBits.belowOrEqual(lastRORPointer));
                    if (headerInImageHeap) {
                        final Pointer hubBitsAsPointer = (Pointer) headerHubBits;
                        final Object hubBitsAsObject = KnownIntrinsics.convertUnknownValue(hubBitsAsPointer.toObject(), Object.class);
                        final DynamicHub hubBitsAsDynamicHub = (DynamicHub) hubBitsAsObject;
                        log.string("  class: ").string(hubBitsAsDynamicHub.getName());
                        /* Try to get details from the object. */
                        final Object entryAsObject = KnownIntrinsics.convertUnknownValue(objectEntry.toObject(), Object.class);
                        if (LayoutEncoding.isArray(entryAsObject)) {
                            final int length = KnownIntrinsics.readArrayLength(entryAsObject);
                            log.string("  length: ").signed(length);
                        }
                    } else {
                        log.string("  header not in image heap");
                    }
                    log.newline();
                }
                log.string("]").indent(false).flush();
            }
        }

        private static int getHistoryLength() {
            return Options.GreyToBlackObjectVisitorDiagnosticHistory.getValue().intValue();
        }

        /** Map a counter to the bounds of a history array. */
        private static int countToIndex(long value) {
            return (int) (value % getHistoryLength());
        }
    }
}
