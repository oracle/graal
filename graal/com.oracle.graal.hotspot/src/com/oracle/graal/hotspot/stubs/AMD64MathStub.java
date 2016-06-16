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
package com.oracle.graal.hotspot.stubs;

import static com.oracle.graal.compiler.target.Backend.ARITHMETIC_LOG;
import static com.oracle.graal.compiler.target.Backend.ARITHMETIC_LOG10;

import com.oracle.graal.compiler.common.spi.ForeignCallDescriptor;
import com.oracle.graal.hotspot.HotSpotForeignCallLinkage;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.replacements.Snippet;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;
import com.oracle.graal.replacements.nodes.UnaryMathIntrinsicNode;

/**
 * Stub called to support java.lang.Math.
 */
public class AMD64MathStub extends SnippetStub {

    private final ForeignCallDescriptor descriptor;

    public AMD64MathStub(ForeignCallDescriptor descriptor, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super("unary", providers, linkage);
        this.descriptor = descriptor;
    }

    @Override
    protected Object getConstantParameterValue(int index, String name) {
        if (index == 1) {
            if (descriptor == ARITHMETIC_LOG) {
                return UnaryOperation.LOG;
            }
            if (descriptor == ARITHMETIC_LOG10) {
                return UnaryOperation.LOG10;
            }
            throw new InternalError("Unknown operation " + descriptor);
        }
        return super.getConstantParameterValue(index, name);
    }

    @Snippet
    private static double unary(double value, @ConstantParameter UnaryOperation operation) {
        return UnaryMathIntrinsicNode.compute(value, operation);
    }
}
