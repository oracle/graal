/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck.impl;

import static org.junit.Assert.assertEquals;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.tck.impl.TruffleLanguageRunner.RubyRunner;

/**
 * Tests with code snippets referencing Ruby (executed only when implementation of Ruby is around).
 */
@RunWith(RubyRunner.class)
public class PolyglotEngineWithRuby {

    private Context context;

    @Before
    public void initEngine() {
        context = Context.newBuilder().build();
    }

    @After
    public void disposeEngine() {
        context.close();
    }

    @Test
    public void testCallRubyFunctionFromJava() {
        callRubyFunctionFromJava();
    }

    // @formatter:off

    // BEGIN: com.oracle.truffle.tck.impl.PolyglotEngineWithRuby#callRubyFunctionFromJava
    @FunctionalInterface
    interface Multiplier {
        int multiply(int a, int b);
    }

    public void callRubyFunctionFromJava() {
        Source src = Source.newBuilder("ruby",
            "proc { |a, b|\n" +
            "  a * b" +
            "}",
            "mul.rb").buildLiteral();

        // Evaluate Ruby function definition
        Value rbFunction = context.eval(src);

        // Create Java access to Ruby function
        Multiplier mul = rbFunction.as(Multiplier.class);

        assertEquals(42, mul.multiply(6, 7));
        assertEquals(144, mul.multiply(12, 12));
        assertEquals(256, mul.multiply(32, 8));
    }
    // END: com.oracle.truffle.tck.impl.PolyglotEngineWithRuby#callRubyFunctionFromJava

    // @formatter:on

}
