/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.snippets;

import static com.oracle.graal.hotspot.snippets.HotSpotSnippetUtils.*;
import static com.oracle.graal.snippets.SnippetTemplate.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.RuntimeCallTarget.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.Snippet.ConstantParameter;
import com.oracle.graal.snippets.Snippet.Parameter;
import com.oracle.graal.snippets.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.snippets.SnippetTemplate.Arguments;
import com.oracle.graal.snippets.SnippetTemplate.Key;
import com.oracle.graal.word.*;

public class WriteBarrierSnippets implements SnippetsInterface {

    private static boolean TRACE_COND = false;

    @Snippet
    public static int g1PreWriteBarrier(@Parameter("object") Object obj, @Parameter("expectedObject") Object expobj, @Parameter("location") Object location,
                    @ConstantParameter("doLoad") boolean doLoad, @ConstantParameter("name") String name, @ConstantParameter("profile") boolean profile) {

        int gcCycle = Word.unsigned(HotSpotSnippetUtils.gcCycleAddress()).readInt(0);
        if (obj == null) {
            return gcCycle;

        }
        TRACE_COND = profile;
        // if (!TRACE_COND) {
        // TRACE_COND = Word.unsigned(HotSpotSnippetUtils.gcCycleAddress()).readInt(0) > 650 ? true
// : false;
        // }
        int cause = 0;
        Word thread = thread();

        Object object = FixedValueAnchorNode.getObject(obj);
        Object expectedObject = FixedValueAnchorNode.getObject(expobj);

        Pointer oop = Word.fromObject(object);
        Pointer field = Word.fromArray(object, location);
        Pointer previousOop = Word.fromObject(expectedObject);
        long originalOop = oop.rawValue();
        long prevOop = field.readWord(0).rawValue();
        byte markingValue = thread.readByte(HotSpotSnippetUtils.g1SATBQueueMarkingOffset());

        if (doLoad) { // We need to generate the load of the previous value
            if (oop.equal(Word.zero())) {
                trace(TRACE_COND, "ERROR      Null Object 0x%16lx\n", Word.zero());
            }
            if (field.equal(Word.zero())) {
                trace(TRACE_COND, "ERROR      Null Field 0x%16lx\n", Word.zero());
            }
            if (previousOop.notEqual(Word.zero())) {
                trace(TRACE_COND, "ERROR      Field Loaded already 0x%16lx\n", previousOop);
            }
        } else {

            if (previousOop.equal(Word.zero())) {
                trace(TRACE_COND, "ERROR      Field is not Loaded already 0x%16lx\n", previousOop);
                trace(TRACE_COND, "ERROR      Field2 is not Loaded already 0x%16lx\n", oop);
                trace(TRACE_COND, name, oop);
                trace(TRACE_COND, "\n", oop);
            }

            if (previousOop.notEqual(field.readWord(0))) {
                trace(TRACE_COND, "ERROR      Field is not Loaded already in ref.Reference 0x%16lx\n", previousOop);
            }
        }

        Word bufferAddress = thread.readWord(HotSpotSnippetUtils.g1SATBQueueBufferOffset());
        Word indexAddress = thread.add(HotSpotSnippetUtils.g1SATBQueueIndexOffset());
        Word indexValue = indexAddress.readWord(0);
        trace(TRACE_COND, "TRACE  PRE     Field oop 0x%16lx prevValue 0x%16lx marking value %d \n", oop, field.readWord(0), Word.unsigned(markingValue));
        trace(TRACE_COND, "TRACE  PRE     Field oop 0x%16lx field 0x%16lx marking value %d \n", oop, field, Word.unsigned(markingValue));

        if (markingValue != (byte) 0) {
            if (doLoad) {
                previousOop = field.readWord(0);
                prevOop = previousOop.rawValue();

            }
            if (previousOop.notEqual(Word.zero())) {

                if (indexValue.notEqual(Word.zero())) {
                    Word nextIndex = indexValue.subtract(HotSpotSnippetUtils.wordSize());
                    Word logAddress = bufferAddress.add(nextIndex);
                    logAddress.writeWord(0, previousOop);
                    indexAddress.writeWord(0, nextIndex);
                    if (field.readWord(0).rawValue() != prevOop) {
                        trace(TRACE_COND, "ERROR      field SATB changed 0x%16lx\n", Word.unsigned(prevOop));
                    }

                    if (logAddress.readWord(0).rawValue() != prevOop) {
                        trace(TRACE_COND, "ERROR      field SATB changed in buff 0x%16lx\n", Word.unsigned(prevOop));
                    }

                    if (indexAddress.readWord(0).rawValue() != nextIndex.rawValue()) {
                        trace(TRACE_COND, "ERROR      index SATB changed in buff 0x%16lx\n", Word.unsigned(prevOop));
                    }

                } else {
                    WriteBarrierPreStubCall.call(previousOop);
                    Word bufferAddress1 = thread.readWord(HotSpotSnippetUtils.g1SATBQueueBufferOffset());
                    Word indexAddress1 = thread.add(HotSpotSnippetUtils.g1SATBQueueIndexOffset());
                    Word indexValue1 = indexAddress1.readWord(0);
                    if (bufferAddress1.add(indexValue1).readWord(0).rawValue() != prevOop) {
                        trace(TRACE_COND, "ERROR      field SATB changed native 0x%16lx\n", Word.unsigned(prevOop));
                    }

                }
            }
        }

        if (originalOop != oop.rawValue()) {
            trace(TRACE_COND, "ERROR      Address changed 0x%16lx\n", Word.unsigned(originalOop));
            trace(TRACE_COND, "ERROR      Address changed to  0x%16lx\n", oop);
        }

        if (prevOop != 0L && (prevOop != field.readWord(0).rawValue())) {
            trace(TRACE_COND, "ERROR      previousOop changed 0x%16lx\n", Word.unsigned(prevOop));
        }

        if (gcCycle != Word.unsigned(HotSpotSnippetUtils.gcCycleAddress()).readInt(0)) {
            trace(TRACE_COND, "ERROR      gcCycle within PRE changed %lu %lu\n", Word.unsigned(gcCycle), Word.unsigned(cause));
        }

        return gcCycle;
    }

    @Snippet
    public static void g1PostWriteBarrier(@Parameter("object") Object obj, @Parameter("value") Object value, @Parameter("location") Object location, @Parameter("preBarrier") int preBarrier,
                    @ConstantParameter("usePrecise") boolean usePrecise, @ConstantParameter("profile") boolean profile) {
        Word thread = thread();

        TRACE_COND = profile;
        int gcCycle = Word.unsigned(HotSpotSnippetUtils.gcCycleAddress()).readInt(0);
        if (gcCycle != preBarrier) {
            trace(TRACE_COND, "ERROR      gcCycle from PRE to POST changed %lu %lu\n", Word.unsigned(gcCycle), Word.unsigned(preBarrier));
        }

        Object object = FixedValueAnchorNode.getObject(obj);
        Object wrObject = FixedValueAnchorNode.getObject(value);

        Pointer oop = Word.fromObject(object);

        Pointer field1 = Word.fromArray(object, location);
        Pointer field;
        if (usePrecise) {
            field = Word.fromArray(object, location);
        } else {
            field = oop;
        }

        Pointer writtenValue = Word.fromObject(wrObject);

        long originalOop = oop.rawValue();
        long originalWv = writtenValue.rawValue();

        Word bufferAddress = thread.readWord(HotSpotSnippetUtils.g1CardQueueBufferOffset());
        Word indexAddress = thread.add(HotSpotSnippetUtils.g1CardQueueIndexOffset());
        Word indexValue = thread.readWord(HotSpotSnippetUtils.g1CardQueueIndexOffset());

        if (oop.equal(Word.zero())) {
            trace(TRACE_COND, "ERROR POST     Null Object 0x%16lx\n", Word.zero());
        }
        if (field.equal(Word.zero())) {
            trace(TRACE_COND, "ERROR POST     Null Field 0x%16lx\n", Word.zero());
        }

        Word xorResult = ((Word) field.xor(writtenValue)).unsignedShiftRight(HotSpotSnippetUtils.logOfHRGrainBytes());

        // Card Table
        Word cardBase = (Word) field.unsignedShiftRight(cardTableShift());
        long startAddress = cardTableStart();
        int displacement = 0;
        if (((int) startAddress) == startAddress) {
            displacement = (int) startAddress;
        } else {
            cardBase = cardBase.add(Word.unsigned(cardTableStart()));
        }
        Word cardAddress = cardBase.add(displacement);

        // trace(TRACE_COND, "      G1 POST from field 0x%016lx to obj 0x%16lx, marking %d\n",
// field, writtenValue, Word.signed(cardAddress.readByte(0)));
        // trace(TRACE_COND,
// "      G1 POST from field 0x%016lx to cardAddress 0x%16lx, xorResults %16lx\n", field,
// cardAddress, xorResult);

        trace(TRACE_COND, "TRACE  POST     Field oop 0x%16lx writtenValue 0x%16lx card value %d\n", oop, writtenValue, Word.signed(cardAddress.readByte(0)));
        trace(TRACE_COND, "TRACE  POST     Field oop 0x%16lx fieldRead 0x%16lx fieldAddress  0x%16lx\n", oop, field1.readWord(0), field1);

        if (xorResult.notEqual(Word.zero())) {
            if (writtenValue.notEqual(Word.zero())) {
                byte cardByte = cardAddress.readByte(0);
                if (cardByte != (byte) 0) {
                    cardAddress.writeByte(0, (byte) 0); // smash zero into card
                    if (indexValue.notEqual(Word.zero())) {
                        Word nextIndex = indexValue.subtract(HotSpotSnippetUtils.wordSize());
                        Word logAddress = bufferAddress.add(nextIndex);
                        logAddress.writeWord(0, cardAddress);
                        indexAddress.writeWord(0, nextIndex);
                    } else {
                        WriteBarrierPostStubCall.call(object, cardAddress);
                    }
                }
            }
        }
        // } else { Object clone intrinsic(?!)
        // }
        // trace(WriteBarrierSnippets.TRACE, "---------------G1 POST EXIT: %lu\n",
        if (originalOop != oop.rawValue()) {
            trace(TRACE_COND, "ERROR      Address changed 0x%16lx\n", Word.unsigned(originalOop));
            trace(TRACE_COND, "ERROR      Address changed to  0x%16lx\n", oop);
        }

        if (originalWv != writtenValue.rawValue()) {
            trace(TRACE_COND, "ERROR      Written Value  changed 0x%16lx\n", Word.unsigned(originalWv));
            trace(TRACE_COND, "ERROR      Written Value changed to  0x%16lx\n", writtenValue);
        }

        if (gcCycle != Word.unsigned(HotSpotSnippetUtils.gcCycleAddress()).readInt(0)) {
            trace(TRACE_COND, "ERROR      gcCycle within POST changed %lu\n", Word.unsigned(gcCycle));
        }

    }

    private static void trace(boolean enabled, String format, WordBase value) {
        if (enabled) {
            Log.printf(format, value.rawValue());
        }
    }

    private static void trace(boolean enabled, String format, WordBase value, WordBase value2) {
        if (enabled) {
            Log.printf(format, value.rawValue(), value2.rawValue());
        }
    }

    private static void trace(boolean enabled, String format, WordBase value, WordBase value2, WordBase value3) {
        if (enabled) {
            Log.printf(format, value.rawValue(), value2.rawValue(), value3.rawValue());
        }
    }

    @Snippet
    public static void serialFieldWriteBarrier(@Parameter("object") Object object) {
        Pointer oop = Word.fromObject(object);
        Word base = (Word) oop.unsignedShiftRight(cardTableShift());
        long startAddress = cardTableStart();
        int displacement = 0;
        if (((int) startAddress) == startAddress) {
            displacement = (int) startAddress;
        } else {
            base = base.add(Word.unsigned(cardTableStart()));
        }
        base.writeWord(displacement, Word.zero());
    }

    @Snippet
    public static void serialArrayWriteBarrier(@Parameter("object") Object object, @Parameter("location") Object location) {
        Pointer oop = Word.fromArray(object, location);
        Word base = (Word) oop.unsignedShiftRight(cardTableShift());
        long startAddress = cardTableStart();
        int displacement = 0;
        if (((int) startAddress) == startAddress) {
            displacement = (int) startAddress;
        } else {
            base = base.add(Word.unsigned(cardTableStart()));
        }
        base.writeWord(displacement, Word.zero());
    }

    public static class Templates extends AbstractTemplates<WriteBarrierSnippets> {

        private final ResolvedJavaMethod serialFieldWriteBarrier;
        private final ResolvedJavaMethod serialArrayWriteBarrier;
        private final ResolvedJavaMethod g1PreWriteBarrier;
        private final ResolvedJavaMethod g1PostWriteBarrier;

        public Templates(CodeCacheProvider runtime, Assumptions assumptions, TargetDescription target, boolean useG1GC) {
            super(runtime, assumptions, target, WriteBarrierSnippets.class);
            serialFieldWriteBarrier = snippet("serialFieldWriteBarrier", Object.class);
            serialArrayWriteBarrier = snippet("serialArrayWriteBarrier", Object.class, Object.class);
            g1PreWriteBarrier = snippet("g1PreWriteBarrier", Object.class, Object.class, Object.class, boolean.class, String.class, boolean.class);
            g1PostWriteBarrier = snippet("g1PostWriteBarrier", Object.class, Object.class, Object.class, int.class, boolean.class, boolean.class);
            System.out.println("  useG1GC? " + (useG1GC ? "true" : "false"));
        }

        public void lower(ArrayWriteBarrier arrayWriteBarrier, @SuppressWarnings("unused") LoweringTool tool) {
            ResolvedJavaMethod method = serialArrayWriteBarrier;
            Key key = new Key(method);
            Arguments arguments = new Arguments();
            arguments.add("object", arrayWriteBarrier.object());
            arguments.add("location", arrayWriteBarrier.location());
            SnippetTemplate template = cache.get(key, assumptions);
            template.instantiate(runtime, arrayWriteBarrier, DEFAULT_REPLACER, arguments);
        }

        public void lower(FieldWriteBarrier fieldWriteBarrier, @SuppressWarnings("unused") LoweringTool tool) {
            ResolvedJavaMethod method = serialFieldWriteBarrier;
            Key key = new Key(method);
            Arguments arguments = new Arguments();
            arguments.add("object", fieldWriteBarrier.object());
            SnippetTemplate template = cache.get(key, assumptions);
            template.instantiate(runtime, fieldWriteBarrier, DEFAULT_REPLACER, arguments);
        }

        public void lower(WriteBarrierPre writeBarrierPre, @SuppressWarnings("unused") LoweringTool tool) {
            ResolvedJavaMethod method = g1PreWriteBarrier;
            Key key = new Key(method);
            key.add("doLoad", writeBarrierPre.doLoad());
            key.add("name", writeBarrierPre.getName());
            key.add("profile", writeBarrierPre.profile());
            Arguments arguments = new Arguments();
            arguments.add("object", writeBarrierPre.object());
            arguments.add("expectedObject", writeBarrierPre.expectedObject());
            arguments.add("location", writeBarrierPre.location());
            SnippetTemplate template = cache.get(key, assumptions);
            template.instantiate(runtime, writeBarrierPre, DEFAULT_REPLACER, arguments);
        }

        public void lower(WriteBarrierPost writeBarrierPost, @SuppressWarnings("unused") LoweringTool tool) {
            ResolvedJavaMethod method = g1PostWriteBarrier;
            Key key = new Key(method);
            key.add("usePrecise", writeBarrierPost.usePrecise());
            key.add("profile", writeBarrierPost.profile());
            Arguments arguments = new Arguments();
            arguments.add("object", writeBarrierPost.object());
            arguments.add("location", writeBarrierPost.location());
            arguments.add("value", writeBarrierPost.value());
            arguments.add("preBarrier", writeBarrierPost.getPreBarrier());
            SnippetTemplate template = cache.get(key, assumptions);
            template.instantiate(runtime, writeBarrierPost, DEFAULT_REPLACER, arguments);
        }

    }
}
