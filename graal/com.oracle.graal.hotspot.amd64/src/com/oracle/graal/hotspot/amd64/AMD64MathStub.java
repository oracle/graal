/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.hotspot.amd64.AMD64HotSpotForeignCallsProvider.ARITHMETIC_COS_STUB;
import static com.oracle.graal.hotspot.amd64.AMD64HotSpotForeignCallsProvider.ARITHMETIC_LOG10_STUB;
import static com.oracle.graal.hotspot.amd64.AMD64HotSpotForeignCallsProvider.ARITHMETIC_LOG_STUB;
import static com.oracle.graal.hotspot.amd64.AMD64HotSpotForeignCallsProvider.ARITHMETIC_SIN_STUB;
import static com.oracle.graal.hotspot.amd64.AMD64HotSpotForeignCallsProvider.ARITHMETIC_TAN_STUB;

import com.oracle.graal.compiler.common.spi.ForeignCallDescriptor;
import com.oracle.graal.hotspot.HotSpotForeignCallLinkage;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.hotspot.stubs.SnippetStub;
import com.oracle.graal.replacements.Snippet;
import com.oracle.graal.replacements.nodes.UnaryMathIntrinsicNode;
import com.oracle.graal.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;

/**
 * Stub called to support {@link Math}.
 */
public class AMD64MathStub extends SnippetStub {

    public AMD64MathStub(ForeignCallDescriptor descriptor, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(snippetName(descriptor), providers, linkage);
    }

    private static String snippetName(ForeignCallDescriptor descriptor) {
        if (descriptor == ARITHMETIC_LOG_STUB) {
            return "log";
        }
        if (descriptor == ARITHMETIC_LOG10_STUB) {
            return "log10";
        }
        if (descriptor == ARITHMETIC_SIN_STUB) {
            return "sin";
        }
        if (descriptor == ARITHMETIC_COS_STUB) {
            return "cos";
        }
        if (descriptor == ARITHMETIC_TAN_STUB) {
            return "tan";
        }
        throw new InternalError("Unknown operation " + descriptor);
    }

    @Snippet
    private static double log(double value) {
        return UnaryMathIntrinsicNode.compute(value, UnaryOperation.LOG);
    }

    @Snippet
    private static double log10(double value) {
        return UnaryMathIntrinsicNode.compute(value, UnaryOperation.LOG10);
    }

    @Snippet
    private static double sin(double value) {
        return UnaryMathIntrinsicNode.compute(value, UnaryOperation.SIN);
    }

    @Snippet
    private static double cos(double value) {
        return UnaryMathIntrinsicNode.compute(value, UnaryOperation.COS);
    }

    @Snippet
    private static double tan(double value) {
        return UnaryMathIntrinsicNode.compute(value, UnaryOperation.TAN);
    }

}
