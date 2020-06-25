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
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

/**
 * Run an ObjectReferenceVisitor ({@link GreyToBlackObjRefVisitor}) over any interior object
 * references in the Object, turning this Object from grey to black.
 *
 * This visitor is used during GC and so it must be constructed during native image generation.
 */
final class GreyToBlackObjectVisitor implements ObjectVisitor {
    private final DiagnosticReporter diagnosticReporter;
    private final GreyToBlackObjRefVisitor objRefVisitor;

    @Platforms(Platform.HOSTED_ONLY.class)
    GreyToBlackObjectVisitor(GreyToBlackObjRefVisitor greyToBlackObjRefVisitor) {
        this.objRefVisitor = greyToBlackObjRefVisitor;
        if (DiagnosticReporter.getHistoryLength() > 0) {
            this.diagnosticReporter = new DiagnosticReporter();
            SubstrateUtil.DiagnosticThunkRegister.getSingleton().register(diagnosticReporter);
        } else {
            this.diagnosticReporter = null;
        }
    }

    public void reset() {
        if (diagnosticReporter != null) {
            diagnosticReporter.reset();
        }
    }

    @Override
    @NeverInline("Non-performance critical version")
    public boolean visitObject(Object o) {
        throw VMError.shouldNotReachHere("For performance reasons, this should not be called.");
    }

    @Override
    @AlwaysInline("GC performance")
    public boolean visitObjectInline(Object o) {
        if (diagnosticReporter != null) {
            diagnosticReporter.noteObject(o);
        }
        ReferenceObjectProcessing.discoverIfReference(o, objRefVisitor);
        InteriorObjRefWalker.walkObjectInline(o, objRefVisitor);
        return true;
    }

    /** A ring buffer of visited objects for diagnostics. */
    static final class DiagnosticReporter implements SubstrateUtil.DiagnosticThunk {

        static class Options {
            @Option(help = "Length of GreyToBlackObjectVisitor history for diagnostics. 0 implies no history is kept.") //
            static final HostedOptionKey<Integer> GreyToBlackObjectVisitorDiagnosticHistory = new HostedOptionKey<>(0);
        }

        /** The total count of all noted objects, used to compute the current array index. */
        private long historyCount;

        /** The history of objects. Kept as pointers to avoid holding references. */
        private final Word[] objectHistory;

        /** The history of the headers of the objects in {@link #objectHistory}. */
        private final UnsignedWord[] headerHistory;

        @Platforms(Platform.HOSTED_ONLY.class)
        DiagnosticReporter() {
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
            int index = countToIndex(historyCount);
            objectHistory[index] = Word.objectToUntrackedPointer(o);
            headerHistory[index] = ObjectHeaderImpl.readHeaderFromObjectCarefully(o);
            historyCount += 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate during printing diagnostics.")
        public void invokeWithoutAllocation(Log log) {
            if (historyCount > 0) {
                log.string("[GreyToBlackObjectVisitor.RealDiagnosticReporter.invoke:")
                                .string("  history / count:  ")
                                .signed(getHistoryLength()).string(" / ").signed(historyCount)
                                .indent(true);
                ImageHeapInfo imageHeapInfo = HeapImpl.getImageHeapInfo();
                /*
                 * Report the history from the next available slot in the ring buffer. The older
                 * history is more reliable, since I have already used that to visit objects. The
                 * most recent history is more suspect, because this is the first use of it.
                 */
                for (int count = 0; count < getHistoryLength(); count += 1) {
                    int index = countToIndex(historyCount + count);
                    log.string("  index: ").unsigned(index, 3, Log.RIGHT_ALIGN);
                    Word objectEntry = objectHistory[index];
                    log.string("  objectEntry: ").hex(objectEntry);
                    UnsignedWord headerEntry = headerHistory[index];
                    Pointer headerHub = (Pointer) ObjectHeaderImpl.clearBits(headerEntry);
                    UnsignedWord headerHeaderBits = ObjectHeaderImpl.getHeaderBitsFromHeaderCarefully(headerEntry);
                    log.string("  headerEntry: ").hex(headerEntry).string(" = ").hex(headerHub).string(" | ").hex(headerHeaderBits).string(" / ");
                    boolean headerInImageHeap = imageHeapInfo.isInReadOnlyReferencePartition(headerHub) ||
                                    imageHeapInfo.isInReadOnlyRelocatablePartition(headerHub);
                    if (headerInImageHeap) {
                        DynamicHub hub = (DynamicHub) KnownIntrinsics.convertUnknownValue(headerHub.toObject(), Object.class);
                        log.string("  class: ").string(hub.getName());
                        Object entryAsObject = KnownIntrinsics.convertUnknownValue(objectEntry.toObject(), Object.class);
                        if (LayoutEncoding.isArray(entryAsObject)) {
                            int length = KnownIntrinsics.readArrayLength(entryAsObject);
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
            return Options.GreyToBlackObjectVisitorDiagnosticHistory.getValue();
        }

        private static int countToIndex(long value) {
            return (int) (value % getHistoryLength());
        }
    }
}
