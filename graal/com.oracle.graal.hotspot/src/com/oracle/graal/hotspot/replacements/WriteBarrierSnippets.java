/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.phases.GraalOptions.*;
import static com.oracle.graal.replacements.SnippetTemplate.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

public class WriteBarrierSnippets implements Snippets {

    private static final SnippetCounter.Group countersWriteBarriers = SnippetCounters.getValue() ? new SnippetCounter.Group("WriteBarriers") : null;
    private static final SnippetCounter serialFieldWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "serialFieldWriteBarrier", "Number of Serial Field Write Barriers");
    private static final SnippetCounter serialArrayWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "serialArrayWriteBarrier", "Number of Serial Array Write Barriers");

    @Snippet
    public static void serialArrayWriteBarrier(Object obj, Object location, @ConstantParameter boolean usePrecise) {
        Object object = FixedValueAnchorNode.getObject(obj);
        Pointer oop;
        if (usePrecise) {
            oop = Word.fromArray(object, location);
            serialArrayWriteBarrierCounter.inc();
        } else {
            oop = Word.fromObject(object);
            serialFieldWriteBarrierCounter.inc();
        }
        Word base = (Word) oop.unsignedShiftRight(cardTableShift());
        long startAddress = cardTableStart();
        int displacement = 0;
        if (((int) startAddress) == startAddress) {
            displacement = (int) startAddress;
        } else {
            base = base.add(Word.unsigned(cardTableStart()));
        }
        base.writeByte(displacement, (byte) 0);
    }

    @Snippet
    public static void serialArrayRangeWriteBarrier(Object object, int startIndex, int length) {
        Object dest = FixedValueAnchorNode.getObject(object);
        int cardShift = cardTableShift();
        long cardStart = cardTableStart();
        final int scale = arrayIndexScale(Kind.Object);
        int header = arrayBaseOffset(Kind.Object);
        long dstAddr = GetObjectAddressNode.get(dest);
        long start = (dstAddr + header + (long) startIndex * scale) >>> cardShift;
        long end = (dstAddr + header + ((long) startIndex + length - 1) * scale) >>> cardShift;
        long count = end - start + 1;
        while (count-- > 0) {
            DirectStoreNode.store((start + cardStart) + count, false, Kind.Boolean);
        }
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo serialArrayWriteBarrier = snippet(WriteBarrierSnippets.class, "serialArrayWriteBarrier");
        private final SnippetInfo serialArrayRangeWriteBarrier = snippet(WriteBarrierSnippets.class, "serialArrayRangeWriteBarrier");

        public Templates(CodeCacheProvider runtime, Replacements replacements, TargetDescription target) {
            super(runtime, replacements, target);
        }

        public void lower(SerialWriteBarrier arrayWriteBarrier, @SuppressWarnings("unused") LoweringTool tool) {
            Arguments args = new Arguments(serialArrayWriteBarrier);
            args.add("obj", arrayWriteBarrier.getObject());
            args.add("location", arrayWriteBarrier.getLocation());
            args.addConst("usePrecise", arrayWriteBarrier.usePrecise());
            template(args).instantiate(runtime, arrayWriteBarrier, DEFAULT_REPLACER, args);
        }

        public void lower(SerialArrayRangeWriteBarrier arrayRangeWriteBarrier, @SuppressWarnings("unused") LoweringTool tool) {
            Arguments args = new Arguments(serialArrayRangeWriteBarrier);
            args.add("object", arrayRangeWriteBarrier.getObject());
            args.add("startIndex", arrayRangeWriteBarrier.getStartIndex());
            args.add("length", arrayRangeWriteBarrier.getLength());
            template(args).instantiate(runtime, arrayRangeWriteBarrier, DEFAULT_REPLACER, args);
        }
    }
}
