/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.hotspot.replacements.UnsafeAccess.UNSAFE;
import static com.oracle.graal.replacements.SnippetTemplate.DEFAULT_REPLACER;

import com.oracle.graal.api.replacements.Fold;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.nodes.NamedLocationIdentity;
import com.oracle.graal.nodes.debug.StringToBytesNode;
import com.oracle.graal.nodes.java.NewArrayNode;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.replacements.Snippet;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.SnippetTemplate;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.replacements.Snippets;
import com.oracle.graal.replacements.nodes.CStringConstant;
import com.oracle.graal.word.Word;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;

/**
 * The {@code StringToBytesSnippets} contains a snippet for lowering {@link StringToBytesNode}.
 */
public class StringToBytesSnippets implements Snippets {

    @Fold
    static long arrayBaseOffset() {
        return UNSAFE.arrayBaseOffset(char[].class);
    }

    @Snippet
    public static byte[] transform(@ConstantParameter String compilationTimeString) {
        int i = compilationTimeString.length();
        byte[] array = (byte[]) NewArrayNode.newUninitializedArray(byte.class, i);
        Word cArray = CStringConstant.cstring(compilationTimeString);
        while (i-- > 0) {
            // array[i] = cArray.readByte(i);
            UNSAFE.putByte(array, arrayBaseOffset() + i, cArray.readByte(i));
        }
        return array;
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo create;

        public Templates(HotSpotProviders providers, TargetDescription target) {
            super(providers, providers.getSnippetReflection(), target);
            create = snippet(StringToBytesSnippets.class, "transform", NamedLocationIdentity.getArrayLocation(JavaKind.Byte));
        }

        public void lower(StringToBytesNode stringToBytesNode, LoweringTool tool) {
            Arguments args = new Arguments(create, stringToBytesNode.graph().getGuardsStage(), tool.getLoweringStage());
            args.addConst("compilationTimeString", stringToBytesNode.getValue());
            SnippetTemplate template = template(args);
            template.instantiate(providers.getMetaAccess(), stringToBytesNode, DEFAULT_REPLACER, args);
        }

    }

}
