/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.nodes.test;

import static jdk.compiler.graal.java.BytecodeParserOptions.InlineDuringParsing;

import jdk.compiler.graal.core.test.GraalCompilerTest;
import jdk.compiler.graal.core.phases.HighTier;
import jdk.compiler.graal.options.OptionValues;
import org.junit.Test;

public class ExceptionLivenessTest extends GraalCompilerTest {
    @Test
    public void testNewarray() {
        OptionValues options = new OptionValues(getInitialOptions(), HighTier.Options.Inline, false, InlineDuringParsing, false);
        test(options, "newarraySnippet");
    }

    public static int[] newarraySnippet() {
        int[] array = new int[4];

        dummy();
        try {
            array = new int[-10];
        } catch (NegativeArraySizeException exc3) {
        }
        return array;
    }

    static void dummy() {
    }
}
