/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.warmup.test;

import java.io.ByteArrayOutputStream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.tools.warmup.impl.WarmupEstimatorInstrument;

public class WarmupEstimatorTest {
    private static final String defaultSourceString = "ROOT(\n" +
                    "DEFINE(foo,ROOT(STATEMENT)),\n" +
                    "DEFINE(bar,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(foo))))),\n" +
                    "DEFINE(neverCalled,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(bar))))),\n" +
                    "CALL(bar)\n" +
                    ")";
    private static final Source defaultSource = makeSource(defaultSourceString);
    private ByteArrayOutputStream out;
    private ByteArrayOutputStream err;

    private static Source makeSource(String s) {
        return Source.newBuilder(InstrumentationTestLanguage.ID, s, "test").buildLiteral();
    }

    @Before
    public void setUp() {
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
    }

    @Test
    public void testBasic() {
        try (Context context = defaultContext().option(WarmupEstimatorInstrument.ID + ".Root", "foo").build()) {
            context.eval(defaultSource);
        }
        final String output = out.toString();
        assertContains(output, "Best time       |");
        assertContains(output, "Best iter       |");
        assertContains(output, "Epsilon         | 1.05");
        assertContains(output, "Peak Start Iter |");
        assertContains(output, "Peak Start Time |");
        assertContains(output, "Warmup time     |");
        assertContains(output, "Warmup cost     |");
        assertContains(output, "Iterations      | 10");
    }

    @Test
    public void testMultiRootName() {
        try (Context context = defaultContext().option(WarmupEstimatorInstrument.ID + ".Root", "foo,bar").build()) {
            context.eval(defaultSource);
        }
        final String output = out.toString();
        assertContains(output, "foo");
        assertContains(output, "bar");
    }

    @Test
    public void testMultiRootLine() {
        try (Context context = defaultContext().option(WarmupEstimatorInstrument.ID + ".Root", "::2,::3").build()) {
            context.eval(defaultSource);
        }
        final String output = out.toString();
        assertContains(output, "::2");
        assertContains(output, "::3");
    }

    @Test
    public void testMultiRootNameLine() {
        try (Context context = defaultContext().option(WarmupEstimatorInstrument.ID + ".Root", "foo::2,bar::3").build()) {
            context.eval(defaultSource);
        }
        final String output = out.toString();
        assertContains(output, "foo::2");
        assertContains(output, "bar::3");
    }

    @Test
    public void testRawOutput() {
        try (Context context = defaultContext().option(WarmupEstimatorInstrument.ID + ".Root", "foo").option(WarmupEstimatorInstrument.ID + ".Output", "raw").build()) {
            context.eval(defaultSource);
        }
        final String output = out.toString();
        Assert.assertTrue(output.startsWith("["));
        Assert.assertTrue(output.endsWith("]"));
        assertContains(output, "{");
        assertContains(output, "}");
        Assert.assertEquals(11, output.split(",").length);
    }

    private static void assertContains(String output, String expected) {
        Assert.assertTrue(output.contains(expected));
    }

    private Context.Builder defaultContext() {
        return Context.newBuilder().in(System.in).out(out).err(err).option(WarmupEstimatorInstrument.ID, "true").allowExperimentalOptions(true);
    }
}
