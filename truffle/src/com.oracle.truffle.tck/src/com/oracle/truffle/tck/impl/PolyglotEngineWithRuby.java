/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
    // @formatter:on
}
