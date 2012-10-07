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

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.snippets.Snippet.InliningPolicy;

/**
 * Tests for the {@link Word} type.
 */
public class WordTest extends GraalCompilerTest implements SnippetsInterface {

    private final SnippetInstaller installer;

    public WordTest() {
        TargetDescription target = Graal.getRequiredCapability(GraalCompiler.class).target;
        installer = new SnippetInstaller(runtime, target);
    }

    private static final ThreadLocal<InliningPolicy> inliningPolicy = new ThreadLocal<>();

    @Override
    protected StructuredGraph parse(Method m) {
        ResolvedJavaMethod resolvedMethod = runtime.getResolvedJavaMethod(m);
        return installer.makeGraph(resolvedMethod, inliningPolicy.get());
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
                test("plus_long", word, (long) addend);
                test("plus_long", word, (long) -addend);
                test("minus_long", word, (long) addend);
                test("minus_long", word, (long) -addend);

                test("and_int", word, addend);
                test("and_int", word, -addend);
                test("or_int", word, addend);
                test("or_int", word, -addend);
                test("and_long", word, (long) addend);
                test("and_long", word, (long) -addend);
                test("or_long", word, (long) addend);
                test("or_long", word, (long) -addend);
            }
            for (long addend : words) {
                test("plus_int", word, (int) addend);
                test("minus_int", word, (int) addend);
                test("plus_int", word, -((int) addend));
                test("minus_int", word, -((int) addend));
                test("plus_long", word, addend);
                test("minus_long", word, addend);
                test("plus_long", word, -addend);
                test("minus_long", word, -addend);

                test("and_int", word, (int) addend);
                test("or_int", word, (int) addend);
                test("and_int", word, -((int) addend));
                test("or_int", word, -((int) addend));
                test("and_long", word, addend);
                test("or_long", word, addend);
                test("and_long", word, -addend);
                test("or_long", word, -addend);
            }
        }
    }

    @Test
    public void test_compare() {
        long[] words = new long[] {Long.MIN_VALUE, Long.MIN_VALUE + 1, -1L, 0L, 1L, Long.MAX_VALUE - 1, Long.MAX_VALUE};
        for (long word1 : words) {
            for (long word2 : words) {
                for (String method : new String[] {"aboveOrEqual", "above", "belowOrEqual", "below"}) {
                    test(method, word1, word2);
                    test(method, word2, word1);
                }
            }
        }
    }

    @Test
    public void test_fromObject() {
        inliningPolicy.set(new InliningPolicy() {
            public boolean shouldInline(ResolvedJavaMethod method, ResolvedJavaMethod caller) {
                return InliningPolicy.Default.shouldInline(method, caller) && !method.name().equals("hashCode");
            }
        });
        test("fromToObject", "object1", "object2");
        test("fromToObject", "object1", "object1");
        test("fromToObject", "object", null);
        test("fromToObject", null, "object");
        test("fromToObject", null, null);
        inliningPolicy.set(null);
    }

    @Snippet
    public static long plus_int(long word, int addend) {
        return Word.fromLong(word).plus(addend).toLong();
    }

    @Snippet
    public static long minus_int(long word, int addend) {
        return Word.fromLong(word).minus(addend).toLong();
    }

    @Snippet
    public static long plus_long(long word, long addend) {
        return Word.fromLong(word).plus(addend).toLong();
    }

    @Snippet
    public static long minus_long(long word, long addend) {
        return Word.fromLong(word).minus(addend).toLong();
    }

    @Snippet
    public static boolean aboveOrEqual(long word1, long word2) {
        return Word.fromLong(word1).aboveOrEqual(Word.fromLong(word2));
    }

    @Snippet
    public static boolean above(long word1, long word2) {
        return Word.fromLong(word1).above(Word.fromLong(word2));
    }

    @Snippet
    public static boolean belowOrEqual(long word1, long word2) {
        return Word.fromLong(word1).belowOrEqual(Word.fromLong(word2));
    }

    @Snippet
    public static boolean below(long word1, long word2) {
        return Word.fromLong(word1).below(Word.fromLong(word2));
    }

    @Snippet
    public static int fromToObject(Object o1, Object o2) {
        return Word.fromObject(o1).toObject().hashCode() + Word.fromObject(o2).toObject().hashCode();
    }

    @Snippet
    public static long and_int(long word, int addend) {
        return Word.fromLong(word).and(addend).toLong();
    }

    @Snippet
    public static long or_int(long word, int addend) {
        return Word.fromLong(word).or(addend).toLong();
    }

    @Snippet
    public static long and_long(long word, long addend) {
        return Word.fromLong(word).and(addend).toLong();
    }

    @Snippet
    public static long or_long(long word, long addend) {
        return Word.fromLong(word).or(addend).toLong();
    }
}
