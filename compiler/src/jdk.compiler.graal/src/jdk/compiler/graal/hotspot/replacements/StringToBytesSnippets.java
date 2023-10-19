/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hotspot.replacements;

import static jdk.compiler.graal.hotspot.GraalHotSpotVMConfig.INJECTED_METAACCESS;
import static jdk.compiler.graal.replacements.ReplacementsUtil.getArrayBaseOffset;
import static jdk.compiler.graal.replacements.SnippetTemplate.DEFAULT_REPLACER;

import jdk.compiler.graal.api.directives.GraalDirectives;
import jdk.compiler.graal.api.replacements.Snippet;
import jdk.compiler.graal.api.replacements.Snippet.ConstantParameter;
import jdk.compiler.graal.hotspot.meta.HotSpotProviders;
import jdk.compiler.graal.nodes.NamedLocationIdentity;
import jdk.compiler.graal.nodes.debug.StringToBytesNode;
import jdk.compiler.graal.nodes.extended.RawStoreNode;
import jdk.compiler.graal.nodes.java.NewArrayNode;
import jdk.compiler.graal.nodes.spi.LoweringTool;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.replacements.SnippetTemplate;
import jdk.compiler.graal.replacements.SnippetTemplate.AbstractTemplates;
import jdk.compiler.graal.replacements.SnippetTemplate.Arguments;
import jdk.compiler.graal.replacements.SnippetTemplate.SnippetInfo;
import jdk.compiler.graal.replacements.Snippets;
import jdk.compiler.graal.replacements.nodes.CStringConstant;
import jdk.compiler.graal.word.Word;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;

/**
 * The {@code StringToBytesSnippets} contains a snippet for lowering {@link StringToBytesNode}.
 */
public class StringToBytesSnippets implements Snippets {

    public static final LocationIdentity CSTRING_LOCATION = NamedLocationIdentity.immutable("CString location");

    @Snippet
    public static byte[] transform(@ConstantParameter Word cArray, @ConstantParameter int length, @ConstantParameter LocationIdentity locationIdentity) {
        int i = length;
        byte[] array = (byte[]) NewArrayNode.newUninitializedArray(byte.class, i);
        while (GraalDirectives.injectIterationCount(100, i-- > 0)) {
            // array[i] = cArray.readByte(i);
            RawStoreNode.storeByte(array, getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Byte) + i, cArray.readByte(i, CSTRING_LOCATION), JavaKind.Byte,
                            locationIdentity);
        }
        return array;
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo create;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, HotSpotProviders providers) {
            super(options, providers);
            create = snippet(providers, StringToBytesSnippets.class, "transform");
        }

        public void lower(StringToBytesNode stringToBytesNode, LoweringTool tool) {
            Arguments args = new Arguments(create, stringToBytesNode.graph().getGuardsStage(), tool.getLoweringStage());
            String value = stringToBytesNode.getValue();
            args.addConst("cArray", new CStringConstant(value));
            args.addConst("length", value.length());
            args.addConst("locationIdentity", LocationIdentity.init());
            SnippetTemplate template = template(tool, stringToBytesNode, args);
            template.instantiate(tool.getMetaAccess(), stringToBytesNode, DEFAULT_REPLACER, args);
        }

    }

}
