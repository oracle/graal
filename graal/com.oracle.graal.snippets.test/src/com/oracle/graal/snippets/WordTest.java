/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.snippets;

import static com.oracle.graal.nodes.calc.Condition.*;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.tests.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;

/**
 * Tests for the {@link Word} type.
 */
public class WordTest extends GraalCompilerTest implements SnippetsInterface {

    private final SnippetInstaller installer;

    public WordTest() {
        TargetDescription target = Graal.getRequiredCapability(GraalCompiler.class).target;
        installer = new SnippetInstaller(runtime, target);
    }

    @Override
    protected StructuredGraph parse(Method m) {
        ResolvedJavaMethod resolvedMethod = runtime.getResolvedJavaMethod(m);
        return installer.makeGraph(resolvedMethod, null);
    }

    @Test
    public void test_arithmetic() {
        long[] words = new long[] {Long.MIN_VALUE, Long.MIN_VALUE + 1, -1L, 0L, 1L, Long.MAX_VALUE - 1, Long.MAX_VALUE};
        for (long word : words) {
            for (int addend = -1000; addend < 1000; addend++) {
                test("plus_int", word, addend);
                test("plus_int", word, -addend);
                test("minus_int", word, addend);
                test("minus_int", word, -addend);
            }
            for (long addend : words) {
                test("plus_int", word, (int) addend);
                test("minus_int", word, (int) addend);
                test("plus_int", word, -((int) addend));
                test("minus_int", word, -((int) addend));
            }
        }
    }

    @Test
    public void test_compare() {
        long[] words = new long[] {Long.MIN_VALUE, Long.MIN_VALUE + 1, -1L, 0L, 1L, Long.MAX_VALUE - 1, Long.MAX_VALUE};
        for (long word1 : words) {
            for (long word2 : words) {
                for (Condition cond : new Condition[] {AE, AT, EQ, NE, BE, BT}) {
                    test("compare" + cond.name(), word1, word2);
                    test("compare" + cond.name(), word2, word1);
                }
            }
        }
    }

    @Snippet
    public static long plus_int(long word, int addend) {
        return Word.fromLong(word).plus(addend).toLong();
    }

    @Snippet
    public static long minus_int(long word, int addend) {
        return Word.fromLong(word).plus(addend).toLong();
    }

    @Snippet
    public static boolean compareAE(long word1, long word2) {
        return Word.fromLong(word1).cmp(Condition.AE, Word.fromLong(word2));
    }
    @Snippet
    public static boolean compareAT(long word1, long word2) {
        return Word.fromLong(word1).cmp(Condition.AT, Word.fromLong(word2));
    }
    @Snippet
    public static boolean compareEQ(long word1, long word2) {
        return Word.fromLong(word1).cmp(Condition.EQ, Word.fromLong(word2));
    }
    @Snippet
    public static boolean compareNE(long word1, long word2) {
        return Word.fromLong(word1).cmp(Condition.NE, Word.fromLong(word2));
    }
    @Snippet
    public static boolean compareBE(long word1, long word2) {
        return Word.fromLong(word1).cmp(Condition.BE, Word.fromLong(word2));
    }
    @Snippet
    public static boolean compareBT(long word1, long word2) {
        return Word.fromLong(word1).cmp(Condition.BT, Word.fromLong(word2));
    }

}
