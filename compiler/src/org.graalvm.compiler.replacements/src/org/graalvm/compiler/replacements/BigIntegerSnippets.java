/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.nodes.BigIntegerMultiplyToLenNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.WordFactory;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

public class BigIntegerSnippets implements Snippets {

    protected static final MetaAccessProvider INJECTED_METAACCESS = null;

    public static class Templates extends SnippetTemplate.AbstractTemplates {

        public final SnippetTemplate.SnippetInfo implMultiplyToLen;

        public Templates(OptionValues options, Providers providers) {
            super(options, providers);

            this.implMultiplyToLen = snippet(providers, BigIntegerSnippets.class, "implMultiplyToLen");
        }
    }

    @Snippet(allowMissingProbabilities = true)
    public static int[] implMultiplyToLen(int[] x, int xlen, int[] y, int ylen, int[] zIn) {
        int[] zResult = zIn;
        int zLen;
        if (zResult == null || zResult.length < (xlen + ylen)) {
            zLen = xlen + ylen;
            zResult = new int[xlen + ylen];
        } else {
            zLen = zIn.length;
        }

        BigIntegerMultiplyToLenNode.apply(arrayStart(x), xlen, arrayStart(y), ylen, arrayStart(zResult), zLen);
        return zResult;
    }

    private static Word arrayStart(int[] a) {
        return WordFactory.unsigned(ComputeObjectAddressNode.get(a, ReplacementsUtil.getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Int)));
    }
}
