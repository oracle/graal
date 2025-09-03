/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.amd64;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.stubs.SnippetStub;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;

/**
 * Stub called to support {@link Math}.
 */
public class AMD64MathStub extends SnippetStub {

    public AMD64MathStub(UnaryOperation operation, OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(snippetName(operation), options, providers, linkage);
    }

    public AMD64MathStub(BinaryOperation operation, OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(snippetName(operation), options, providers, linkage);
    }

    private static String snippetName(UnaryOperation operation) {
        return switch (operation) {
            case SIN -> "sin";
            case SINH -> "sinh";
            case COS -> "cos";
            case TAN -> "tan";
            case TANH -> "tanh";
            case EXP -> "exp";
            case LOG -> "log";
            case LOG10 -> "log10";
            case CBRT -> "cbrt";
        };
    }

    private static String snippetName(BinaryOperation operation) {
        if (operation == BinaryOperation.POW) {
            return "pow";
        }
        throw GraalError.shouldNotReachHere("Unknown operation " + operation); // ExcludeFromJacocoGeneratedReport
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
    private static double sinh(double value) {
        return UnaryMathIntrinsicNode.compute(value, UnaryOperation.SINH);
    }

    @Snippet
    private static double cos(double value) {
        return UnaryMathIntrinsicNode.compute(value, UnaryOperation.COS);
    }

    @Snippet
    private static double tan(double value) {
        return UnaryMathIntrinsicNode.compute(value, UnaryOperation.TAN);
    }

    @Snippet
    private static double tanh(double value) {
        return UnaryMathIntrinsicNode.compute(value, UnaryOperation.TANH);
    }

    @Snippet
    private static double exp(double value) {
        return UnaryMathIntrinsicNode.compute(value, UnaryOperation.EXP);
    }

    @Snippet
    private static double pow(double value1, double value2) {
        return BinaryMathIntrinsicNode.compute(value1, value2, BinaryOperation.POW);
    }

    @Snippet
    private static double cbrt(double value) {
        return UnaryMathIntrinsicNode.compute(value, UnaryOperation.CBRT);
    }
}
