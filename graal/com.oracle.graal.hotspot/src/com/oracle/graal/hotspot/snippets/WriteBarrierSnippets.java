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
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.Snippet.ConstantParameter;
import com.oracle.graal.snippets.Snippet.Parameter;
import com.oracle.graal.snippets.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.snippets.SnippetTemplate.Arguments;
import com.oracle.graal.snippets.SnippetTemplate.Key;
import com.oracle.graal.snippets.nodes.*;
import com.oracle.graal.word.*;

public class WriteBarrierSnippets implements SnippetsInterface {

    private static final boolean TRACE = true;

    @Snippet
    public static void g1PreWriteBarrier(@Parameter("object") Object object, @Parameter("location") Object location, @ConstantParameter("doLoad") boolean doLoad) {
        Word thread = thread();
        trace(WriteBarrierSnippets.TRACE, "---------------G1 PRE Enter: 0x%016lx\n", thread);
        Pointer oop = Word.fromObject(object);
        Pointer field = Word.fromArray(object, location);
        Pointer previousOop = field.readWord(0);

        byte markingValue = thread.add(HotSpotSnippetUtils.g1SATBQueueMarkingOffset()).readByte(0);

        Word bufferAddress = thread.readWord(HotSpotSnippetUtils.g1SATBQueueBufferOffset());
        Word indexAddress = thread.add(HotSpotSnippetUtils.g1SATBQueueIndexOffset());
        Word indexValue = thread.readWord(HotSpotSnippetUtils.g1SATBQueueIndexOffset());

        trace(WriteBarrierSnippets.TRACE, "      G1 PRE thread address: 0x%16lx\n", thread);
        trace(WriteBarrierSnippets.TRACE, "      G1 PRE oop: 0x%16lx\n", oop);
        trace(WriteBarrierSnippets.TRACE, "      G1 PRE field: 0x%16lx\n", field);
        trace(WriteBarrierSnippets.TRACE, "      G1 PRE previous OOP: 0x%16lx\n", previousOop);
        trace(WriteBarrierSnippets.TRACE, "      G1 PRE QueueMarkingOffset: 0x%016lx\n", Word.signed(HotSpotSnippetUtils.g1SATBQueueMarkingOffset()));
        trace(WriteBarrierSnippets.TRACE, "      G1 PRE QueueBufferOffset: 0x%016lx\n", Word.signed(HotSpotSnippetUtils.g1SATBQueueBufferOffset()));
        trace(WriteBarrierSnippets.TRACE, "      G1 PRE QueueIndexOffset: 0x%016lx\n", Word.signed(HotSpotSnippetUtils.g1SATBQueueIndexOffset()));
        trace(WriteBarrierSnippets.TRACE, "      G1 PRE markingValue: 0x%016lx\n", Word.signed((int) markingValue));

        trace(WriteBarrierSnippets.TRACE, "      G1 PRE bufferAddress: 0x%016lx\n", bufferAddress);
        trace(WriteBarrierSnippets.TRACE, "      G1 PRE indexAddress: 0x%016lx\n", indexAddress);
        trace(WriteBarrierSnippets.TRACE, "      G1 PRE indexValue: 0x%016lx\n", indexValue);// in

        if (markingValue != (byte) 0) {
            if (doLoad) {
                previousOop = field.readWord(0);
                trace(WriteBarrierSnippets.TRACE, "      G1 PRE Do Load previous OOP: 0x%16lx\n", previousOop);
            }
            if (previousOop.notEqual(Word.zero())) {
                if (indexValue.notEqual(Word.zero())) {
                    Word nextIndex = indexValue.subtract(Word.signed(HotSpotSnippetUtils.wordSize()));
                    Word nextIndexX = nextIndex;
                    Word logAddress = bufferAddress.add(nextIndexX);
                    logAddress.writeWord(0, previousOop);
                    indexAddress.writeWord(0, nextIndex);
                    trace(WriteBarrierSnippets.TRACE, "      G1 PRE nextIndexindex: 0x%016lx\n", nextIndex);
                } else {
                    WriteBarrierPreStubCall.call(previousOop);
                }
            }
        }

        trace(WriteBarrierSnippets.TRACE, "---------------G1 PRE Exit: 0x%016lx\n", indexValue);

    }

    @Snippet
    public static void g1PostWriteBarrier(@Parameter("object") Object object, @Parameter("value") Object value, @Parameter("location") Object location) {
        Word thread = thread();
        trace(WriteBarrierSnippets.TRACE, "##############G1 POST Enter: 0x%016lx\n", thread);

        Pointer oop = Word.fromObject(object);
        Pointer field = Word.fromArray(object, location);
        Pointer writtenValue = Word.fromObject(value);

        Word bufferAddress = thread.readWord(HotSpotSnippetUtils.g1CardQueueBufferOffset());
        Word indexAddress = thread.add(HotSpotSnippetUtils.g1CardQueueIndexOffset());
        Word indexValue = thread.readWord(HotSpotSnippetUtils.g1CardQueueIndexOffset());

        trace(WriteBarrierSnippets.TRACE, "     G1 POST oop: 0x%16lx\n", oop);
        trace(WriteBarrierSnippets.TRACE, "     G1 POST field: 0x%16lx\n", field);

        trace(WriteBarrierSnippets.TRACE, "     G1 POST thread address: 0x%16lx\n", thread);
        trace(WriteBarrierSnippets.TRACE, "     G1 POST bufferAddress: 0x%016lx\n", bufferAddress);
        trace(WriteBarrierSnippets.TRACE, "     G1 POST indexAddress: 0x%016lx\n", indexAddress);
        trace(WriteBarrierSnippets.TRACE, "     G1 POST indexValue: 0x%016lx\n", indexValue);
        trace(WriteBarrierSnippets.TRACE, "     G1 POST written value: 0x%016lx\n", writtenValue);

        // Card Table
        Word base = (Word) oop.unsignedShiftRight(cardTableShift());
        long startAddress = cardTableStart();
        int displacement = 0;
        if (((int) startAddress) == startAddress) {
            displacement = (int) startAddress;
        } else {
            base = base.add(Word.unsigned(cardTableStart()));
        }

        // if (writtenValue.notEqual(Word.zero())) {
        Word xorResult = (((Word) field.xor(writtenValue)).unsignedShiftRight(HotSpotSnippetUtils.logOfHRGrainBytes()));
        trace(WriteBarrierSnippets.TRACE, "     G1 POST xor result: 0x%016lx\n", xorResult);

        if (xorResult.notEqual(Word.zero())) {
            if (writtenValue.notEqual(Word.zero())) {
                Word cardValue = base.readWord(displacement);
                char cardByte = base.readChar(displacement);
                trace(WriteBarrierSnippets.TRACE, "     G1 POST cardValue: 0x%016lx\n", cardValue);

                if (cardByte != (char) 0) {
                    base.writeWord(displacement, Word.zero()); // smash zero into card
                    if (indexValue.notEqual(Word.zero())) {
                        Word nextIndex = indexValue.subtract(HotSpotSnippetUtils.wordSize());
                        trace(WriteBarrierSnippets.TRACE, "     G1 POST nextIndex: 0x%016lx\n", nextIndex);

                        Word nextIndexX = nextIndex;
                        Word logAddress = bufferAddress.add(nextIndexX);
                        trace(WriteBarrierSnippets.TRACE, "     G1 POST logAddress: 0x%016lx\n", logAddress);

                        logAddress.writeWord(0, base.add(displacement));
                        indexAddress.writeWord(0, nextIndex);
                    } else {
                        trace(WriteBarrierSnippets.TRACE, "     G1 POST Card Address: 0x%016lx\n", base.add(displacement));
                        WriteBarrierPostStubCall.call(object, base.add(displacement));
                    }
                }
            }
        }
        // } else { Object clone intrinsic(?!)
        // }
        trace(WriteBarrierSnippets.TRACE, "#################G1 POST Exit: 0x%016lx\n", thread);

    }

    private static void trace(boolean enabled, String format, WordBase value) {
        if (enabled) {
            Log.printf(format, value.rawValue());
        }
    }

    @Snippet
    public static void serialFieldWriteBarrier(@Parameter("object") Object object) {
        verifyOop(object);
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
        private final boolean useG1GC;

        public Templates(CodeCacheProvider runtime, Assumptions assumptions, TargetDescription target, boolean useG1GC) {
            super(runtime, assumptions, target, WriteBarrierSnippets.class);
            serialFieldWriteBarrier = snippet("serialFieldWriteBarrier", Object.class);
            serialArrayWriteBarrier = snippet("serialArrayWriteBarrier", Object.class, Object.class);
            g1PreWriteBarrier = snippet("g1PreWriteBarrier", Object.class, Object.class, boolean.class);
            g1PostWriteBarrier = snippet("g1PostWriteBarrier", Object.class, Object.class, Object.class);
            this.useG1GC = useG1GC;
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
            Arguments arguments = new Arguments();
            arguments.add("object", writeBarrierPre.object());
            arguments.add("location", writeBarrierPre.location());
            SnippetTemplate template = cache.get(key, assumptions);
            template.instantiate(runtime, writeBarrierPre, DEFAULT_REPLACER, arguments);
        }

        public void lower(WriteBarrierPost writeBarrierPost, @SuppressWarnings("unused") LoweringTool tool) {
            ResolvedJavaMethod method = g1PostWriteBarrier;
            Key key = new Key(method);
            Arguments arguments = new Arguments();
            arguments.add("object", writeBarrierPost.object());
            arguments.add("location", writeBarrierPost.location());
            arguments.add("value", writeBarrierPost.value());
            SnippetTemplate template = cache.get(key, assumptions);
            template.instantiate(runtime, writeBarrierPost, DEFAULT_REPLACER, arguments);
        }

    }
}
