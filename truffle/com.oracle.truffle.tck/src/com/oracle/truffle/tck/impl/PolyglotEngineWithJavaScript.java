/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests with code snippets referencing JavaScript. They are used from {@link PolyglotEngine} & co.
 * classes, but executed only when implementation of JavaScript is around.
 */
@RunWith(JavaScriptRunner.class)
public class PolyglotEngineWithJavaScript {

    private PolyglotEngine engine;

    @Before
    public void initEngine() {
        engine = PolyglotEngine.newBuilder().build();
    }

    @After
    public void disposeEngine() {
        engine.dispose();
    }

// @formatter:off

    @Test
    public void testDefineJavaScriptFunctionAndUseItFromJava() {
        defineJavaScriptFunctionAndUseItFromJava();
    }

    // BEGIN: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#defineJavaScriptFunctionAndUseItFromJava
    @FunctionalInterface
    interface Mul {
        int mul(int a, int b);
    }

    private void defineJavaScriptFunctionAndUseItFromJava() {
        Mul multiply = engine.eval(Source.newBuilder(
            "(function (a, b) {\n" +
            "  return a * b;" +
            "})"
        ).mimeType("text/javascript").name("mul.js").build()).as(Mul.class);

        assertEquals(42, multiply.mul(6, 7));
        assertEquals(144, multiply.mul(12, 12));
        assertEquals(256, multiply.mul(32, 8));
    }
    // END: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#defineJavaScriptFunctionAndUseItFromJava

    @Test
    public void testDefineMultipleJavaScriptFunctionsAndUseItFromJava() {
        defineMultipleJavaScriptFunctionsAndUseItFromJava();
    }

    // BEGIN: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#defineMultipleJavaScriptFunctionsAndUseItFromJava
    interface Times {
        void addTime(int hours, int minutes, int seconds);
        int timeInSeconds();
    }

    public void defineMultipleJavaScriptFunctionsAndUseItFromJava() {
        Source src = Source.newBuilder("\n"
            + "(function() {\n"
            + "  var seconds = 0;\n"
            + "  function addTime(h, m, s) {\n"
            + "    seconds += 3600 * h;\n"
            + "    seconds += 60 * m;\n"
            + "    seconds += s;\n"
            + "  }\n"
            + "  function time() {\n"
            + "    return seconds;\n"
            + "  }\n"
            + "  return {\n"
            + "    'addTime': addTime,\n"
            + "    'timeInSeconds': time\n"
            + "  }\n"
            + "})\n"
        ).name("CountSeconds.js").mimeType("text/javascript").build();

        Times times = engine.eval(src).execute().as(Times.class);
        times.addTime(6, 30, 0);
        times.addTime(9, 0, 0);
        times.addTime(12, 5, 30);

        assertEquals(99330, times.timeInSeconds());
    }
    // END: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#defineMultipleJavaScriptFunctionsAndUseItFromJava

    @Test
    public void testAccessFieldsOfJavaObject() {
        accessFieldsOfJavaObject();
    }

    @Test
    public void testAccessFieldsOfJavaObjectWithConvertor() {
        accessFieldsOfJavaObjectWithConvertor();
    }

    // BEGIN: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessFieldsOfJavaObject

    public static final class Moment {
        public final int hours;
        public final int minutes;
        public final int seconds;

        public Moment(int hours, int minutes, int seconds) {
            this.hours = hours;
            this.minutes = minutes;
            this.seconds = seconds;
        }
    }

    public void accessFieldsOfJavaObject() {
        Source src = Source.newBuilder("\n"
            + "(function(t) {\n"
            + "  return 3600 * t.hours + 60 * t.minutes + t.seconds;\n"
            + "})\n"
        ).name("MomentToSeconds.js").mimeType("text/javascript").build();

        final Moment m = new Moment(6, 30, 10);
        int value = engine.eval(src).execute(m).as(Number.class).intValue();
        assertEquals(3600 * 6 + 30 * 60 + 10, value);
    }
    // END: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessFieldsOfJavaObject

    // BEGIN: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessFieldsOfJavaObjectWithConvertor

    @FunctionalInterface
    interface MomentConvertor {
        int toSeconds(Moment moment);
    }

    public void accessFieldsOfJavaObjectWithConvertor() {
        Source src = Source.newBuilder("\n"
            + "(function(t) {\n"
            + "  return 3600 * t.hours + 60 * t.minutes + t.seconds;\n"
            + "})\n"
        ).name("MomentToSeconds.js").mimeType("text/javascript").build();

        MomentConvertor convertor = engine.eval(src).as(MomentConvertor.class);

        final Moment m = new Moment(6, 30, 10);
        assertEquals(3600 * 6 + 30 * 60 + 10, convertor.toSeconds(m));
    }
    // END: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessFieldsOfJavaObjectWithConvertor

    @Test
    public void testCreateNewMoment() {
        createNewMoment();
    }

    // BEGIN: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#createNewMoment

    interface MomentFactory {
        Moment create(int h, int m, int s);
    }

    public void createNewMoment() {
        Source src = Source.newBuilder("\n"
            + "(function(Moment) {\n"
            + "  return function(h, m, s) {\n"
            + "     return new Moment(h, m, s);\n"
            + "  };\n"
            + "})\n"
        ).name("ConstructMoment.js").mimeType("text/javascript").build();

        MomentFactory newMoment = engine.eval(src).execute(
            Moment.class // provides access to Moment class
        ).as(MomentFactory.class);

        final Moment m = newMoment.create(6, 30, 10);
        assertEquals("Hours", 6, m.hours);
        assertEquals("Minutes", 30, m.minutes);
        assertEquals("Seconds", 10, m.seconds);
    }
    // END: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#createNewMoment
}
