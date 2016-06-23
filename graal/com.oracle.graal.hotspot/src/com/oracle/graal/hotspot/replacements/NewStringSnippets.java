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
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.nodes.debug.NewStringNode;
import com.oracle.graal.nodes.java.NewArrayNode;
import com.oracle.graal.nodes.java.NewInstanceNode;
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

public class NewStringSnippets implements Snippets {

    @Fold
    static long valueOffset() {
        try {
            return UNSAFE.objectFieldOffset(String.class.getDeclaredField("value"));
        } catch (Exception e) {
            throw new GraalError(e);
        }
    }

    @Fold
    static long hashOffset() {
        try {
            return UNSAFE.objectFieldOffset(String.class.getDeclaredField("hash"));
        } catch (Exception e) {
            throw new GraalError(e);
        }
    }

    @Fold
    static long arrayBaseOffset() {
        return UNSAFE.arrayBaseOffset(char[].class);
    }

    @Snippet
    public static String create(@ConstantParameter String compilationTimeString) {
        int i = compilationTimeString.length();
        char[] array = (char[]) NewArrayNode.newUninitializedArray(char.class, i);
        Word cArray = CStringConstant.cstring(compilationTimeString);
        while (i-- > 0) {
            // assuming it is ASCII string
            // array[i] = (char) cArray.readByte(i);
            UNSAFE.putChar(array, arrayBaseOffset() + i * 2, (char) cArray.readByte(i));
        }
        String newString = (String) newInstance(String.class, false);
        UNSAFE.putObject(newString, valueOffset(), array);
        UNSAFE.putInt(newString, hashOffset(), 0);
        return newString;
    }

    @NodeIntrinsic(NewInstanceNode.class)
    public static native Object newInstance(@ConstantNodeParameter Class<?> type, @ConstantNodeParameter boolean fillContent);

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo create = snippet(NewStringSnippets.class, "create");

        public Templates(HotSpotProviders providers, TargetDescription target) {
            super(providers, providers.getSnippetReflection(), target);
        }

        public void lower(NewStringNode runtimeStringNode, LoweringTool tool) {
            Arguments args = new Arguments(create, runtimeStringNode.graph().getGuardsStage(), tool.getLoweringStage());
            args.addConst("value", runtimeStringNode.getValue());
            SnippetTemplate template = template(args);
            template.instantiate(providers.getMetaAccess(), runtimeStringNode, DEFAULT_REPLACER, args);
        }

    }

}
