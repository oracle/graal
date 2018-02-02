/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.tck.impl.TruffleLanguageRunner.RRunner;

/**
 * Tests with code snippets in R language (executed only when an implementation of R is around).
 */
@RunWith(RRunner.class)
public class PolyglotEngineWithR {

    private Context engine;

    @Before
    public void initEngine() {
        engine = Context.newBuilder().build();
    }

    @After
    public void disposeEngine() {
        engine.close();
    }

    @Test
    public void testCallRtFunctionFromJava() {
        callRFunctionFromJava();
    }

    // BEGIN: com.oracle.truffle.tck.impl.PolyglotEngineWithR#callRFunctionFromJava
    @FunctionalInterface
    interface BinomQuantile {
        int qbinom(double q, int count, double prob);
    }

    public void callRFunctionFromJava() {
        Source src = Source.newBuilder("R", "qbinom", "qbinom.R").buildLiteral();
        BinomQuantile func = engine.eval(src).as(BinomQuantile.class);
        assertEquals(4, func.qbinom(0.37, 10, 0.5));
    }
    // END: com.oracle.truffle.tck.impl.PolyglotEngineWithR#callRFunctionFromJava
}
