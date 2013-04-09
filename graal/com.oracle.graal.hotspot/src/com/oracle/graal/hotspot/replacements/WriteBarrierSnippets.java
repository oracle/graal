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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.hotspot.replacements.HotSpotSnippetUtils.*;
import static com.oracle.graal.replacements.SnippetTemplate.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.Snippet.Parameter;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.Key;
import com.oracle.graal.word.*;

public class WriteBarrierSnippets implements Snippets {

    @Snippet
    public static void serialArrayWriteBarrier(@Parameter("object") Object obj, @Parameter("location") Object location, @ConstantParameter("usePrecise") boolean usePrecise) {
        Object object = FixedValueAnchorNode.getObject(obj);
        Pointer oop;
        if (usePrecise) {
            oop = Word.fromArray(object, location);
        } else {
            oop = Word.fromObject(object);
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

    public static class Templates extends AbstractTemplates<WriteBarrierSnippets> {

        private final ResolvedJavaMethod serialArrayWriteBarrier;

        public Templates(CodeCacheProvider runtime, Replacements replacements, TargetDescription target) {
            super(runtime, replacements, target, WriteBarrierSnippets.class);
            serialArrayWriteBarrier = snippet("serialArrayWriteBarrier", Object.class, Object.class, boolean.class);
        }

        public void lower(SerialWriteBarrier arrayWriteBarrier, @SuppressWarnings("unused") LoweringTool tool) {
            ResolvedJavaMethod method = serialArrayWriteBarrier;
            Key key = new Key(method);
            key.add("usePrecise", arrayWriteBarrier.usePrecise());
            Arguments arguments = new Arguments();
            arguments.add("object", arrayWriteBarrier.getObject());
            arguments.add("location", arrayWriteBarrier.getLocation());
            SnippetTemplate template = cache.get(key);
            template.instantiate(runtime, arrayWriteBarrier, DEFAULT_REPLACER, arguments);
        }
    }
}
