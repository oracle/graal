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
import com.oracle.graal.word.*;

public class WriteBarrierSnippets implements SnippetsInterface {

    @Snippet
    public static void g1PreWriteBarrier(@Parameter("object") Object object, @Parameter("previousValue") Word previousValue, @Parameter("doLoad") boolean doLoad) {
        Word thread = thread();
        Pointer oop = Word.fromObject(object);
        Word markingAddress = thread.add(HotSpotSnippetUtils.g1SATBQueueMarkingOffset());
        Word bufferAddress = thread.add(HotSpotSnippetUtils.g1SATBQueueBufferOffset());
        Word indexAddress = thread.add(HotSpotSnippetUtils.g1SATBQueueIndexOffset());
        Word prevValue = previousValue;

        Word marking = markingAddress.readWord(0);
        if (marking.notEqual(Word.zero())) {
            if (doLoad) {
                prevValue = (Word) Word.fromObject(oop.readObject(0));
            }

            if (prevValue.notEqual(Word.zero())) {
                if (indexAddress.readInt(0) != 0) {
                    Word nextIndex = indexAddress.subtract(Word.signed(HotSpotSnippetUtils.wordSize()));
                    Word nextIndexX = nextIndex;
                    Word logAddress = bufferAddress.add(nextIndexX);
                    logAddress.writeWord(0, prevValue);
                    indexAddress.writeWord(0, nextIndex);
                } else {
                    WriteBarrierPostStubCall.call(object);
                }
            }
        }
    }

    @Snippet
    public static void g1PostWriteBarrier(@Parameter("object") Object object, @Parameter("value") Word value) {
        Word thread = thread();
        Pointer oop = Word.fromObject(object);

        Word bufferAddress = thread.add(HotSpotSnippetUtils.g1CardQueueBufferOffset());
        Word indexAddress = thread.add(HotSpotSnippetUtils.g1CardQueueIndexOffset());

        // Card Table
        Word base = (Word) oop.unsignedShiftRight(cardTableShift());
        long startAddress = cardTableStart();
        int displacement = 0;
        if (((int) startAddress) == startAddress) {
            displacement = (int) startAddress;
        } else {
            base = base.add(Word.unsigned(cardTableStart()));
        }

        if (value.notEqual(0)) {
            Word xorResult = (((Word) oop.xor(value)).unsignedShiftRight(HotSpotSnippetUtils.logOfHRGrainBytes()));
            if (xorResult.notEqual(Word.zero())) {
                if (value.notEqual(Word.zero())) {
                    Word cardValue = base.readWord(displacement);
                    if (cardValue.notEqual(Word.zero())) {
                        base.writeWord(displacement, Word.zero()); // smash zero into card
                        if (indexAddress.readInt(0) != 0) {
                            Word nextIndex = indexAddress.subtract(Word.signed(HotSpotSnippetUtils.wordSize()));
                            Word nextIndexX = nextIndex;
                            Word logAddress = bufferAddress.add(nextIndexX);
                            logAddress.writeWord(0, base.add(displacement));
                            indexAddress.writeWord(0, nextIndex);
                        } else {
                            WriteBarrierPostStubCall.call(object);
                        }
                    }
                }
            }
        }
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
            g1PreWriteBarrier = snippet("g1PreWriteBarrier", Object.class, Word.class, boolean.class);
            g1PostWriteBarrier = snippet("g1PostWriteBarrier", Object.class, Word.class);
            this.useG1GC = useG1GC;
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
            Arguments arguments = new Arguments();
            arguments.add("object", writeBarrierPre.object());
            arguments.add("previousValue", writeBarrierPre.previousValue());
            arguments.add("doLoad", writeBarrierPre.doLoad());
            SnippetTemplate template = cache.get(key, assumptions);
            template.instantiate(runtime, writeBarrierPre, DEFAULT_REPLACER, arguments);
        }

        public void lower(WriteBarrierPost writeBarrierPost, @SuppressWarnings("unused") LoweringTool tool) {
            ResolvedJavaMethod method = g1PostWriteBarrier;
            Key key = new Key(method);
            Arguments arguments = new Arguments();
            arguments.add("object", writeBarrierPost.object());
            arguments.add("value", writeBarrierPost.value());
            SnippetTemplate template = cache.get(key, assumptions);
            template.instantiate(runtime, writeBarrierPost, DEFAULT_REPLACER, arguments);
        }

    }
}
