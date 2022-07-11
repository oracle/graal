/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.amd64;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.SnippetStub;
import org.graalvm.compiler.lir.amd64.AMD64CalcStringAttributesOp;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.amd64.AMD64CalcStringAttributesNode;

public final class AMD64CalcStringAttributesStub extends SnippetStub {

    public AMD64CalcStringAttributesStub(OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(linkage.getDescriptor().getName(), options, providers, linkage);
    }

    @Snippet
    private static int calcStringAttributesLatin1(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.intReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.LATIN1, false);
    }

    @Snippet
    private static int calcStringAttributesBMP(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.intReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.BMP, false);
    }

    @Snippet
    private static long calcStringAttributesUTF8Valid(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.longReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.UTF_8, true);
    }

    @Snippet
    private static long calcStringAttributesUTF8Unknown(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.longReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.UTF_8, false);
    }

    @Snippet
    private static long calcStringAttributesUTF16Valid(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.longReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.UTF_16, true);
    }

    @Snippet
    private static long calcStringAttributesUTF16Unknown(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.longReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.UTF_16, false);
    }

    @Snippet
    private static int calcStringAttributesUTF32(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.intReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.UTF_32, false);
    }
}
