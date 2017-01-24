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
import java.util.List;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
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

    @Test
    public void testIncrementor() {
        incrementor();
    }

    // BEGIN: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#incrementor

    interface Incrementor {
        int inc();
        int dec();
        int value();
    }

    public void incrementor() {
        Source src = Source.newBuilder("\n"
            + "(function() {\n"
            + "  class Incrementor {\n"
            + "     constructor(init) {\n"
            + "       this.value = init;\n"
            + "     }\n"
            + "     inc() {\n"
            + "       return ++this.value;\n"
            + "     }\n"
            + "     dec() {\n"
            + "       return --this.value;\n"
            + "     }\n"
            + "  }\n"
            + "  return function(init) {\n"
            + "    return new Incrementor(init);\n"
            + "  }\n"
            + "})\n"
        ).name("Incrementor.js").mimeType("text/javascript").build();

        final PolyglotEngine.Value factory = engine.eval(src).execute();
        Incrementor initFive = factory.execute(5).as(Incrementor.class);
        Incrementor initTen = factory.execute(10).as(Incrementor.class);

        initFive.inc();
        assertEquals("Now at seven", 7, initFive.inc());

        initTen.dec();
        assertEquals("Now at eight", 8, initTen.dec());
        initTen.dec();

        assertEquals("Values are the same", initFive.value(), initTen.value());
    }
    // END: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#incrementor


    @Test
    public void testArrayWithTypedElements() {
        arrayWithTypedElements();
    }

    // BEGIN: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#arrayWithTypedElements

    interface Point {
        int x();
        int y();
    }

    @FunctionalInterface
    interface PointProvider {
        List<Point> createPoints();
    }

    public void arrayWithTypedElements() {
        Source src = Source.newBuilder("\n"
            + "(function() {\n"
            + "  class Point {\n"
            + "     constructor(x, y) {\n"
            + "       this.x = x;\n"
            + "       this.y = y;\n"
            + "     }\n"
            + "  }\n"
            + "  return [ new Point(30, 15), new Point(5, 7) ];\n"
            + "})\n"
        ).name("ArrayOfPoints.js").mimeType("text/javascript").build();

        PointProvider provider = engine.eval(src).as(PointProvider.class);
        List<Point> points = provider.createPoints();
        assertEquals("Two points", 2, points.size());

        Point first = points.get(0);
        assertEquals(30, first.x());
        assertEquals(15, first.y());

        Point second = points.get(1);
        assertEquals(5, second.x());
        assertEquals(7, second.y());
    }
    // END: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#arrayWithTypedElements

    @Test
    public void tetsAccessJSONObjectProperties() {
        accessJSONObjectProperties();
    }


    // Checkstyle: stop
    // BEGIN: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessJSONObjectProperties

    @FunctionalInterface
    interface ParseJSON {
        List<Repository> parse();
    }

    interface Repository {
        int id();

        String name();

        Owner owner();

        boolean has_wiki();

        List<String> urls();
    }

    interface Owner {
        int id();

        String login();

        boolean site_admin();
    }

    public void accessJSONObjectProperties() {
        Source src = Source.newBuilder(
            "(function () { \n" +
            "  return function() {\n" +
            "    return [\n" +
            "      {\n" +
            "        \"id\": 6109440,\n" +
            "        \"name\": \"holssewebsocket\",\n" +
            "        \"owner\": {\n" +
            "          \"login\": \"jersey\",\n" +
            "          \"id\": 399710,\n" +
            "          \"site_admin\": false\n" +
            "        },\n" +
            "        \"urls\": [\n" +
            "          \"https://api.github.com/repos/jersey/hol\",\n" +
            "          \"https://api.github.com/repos/jersey/hol/forks\",\n" +
            "          \"https://api.github.com/repos/jersey/hol/teams\",\n" +
            "        ],\n" +
            "        \"has_wiki\": true\n" +
            "      }\n" +
            "    ]\n" +
            "  };\n" +
            "})\n"
        ).name("github-api-value.js").mimeType("text/javascript").build();
        ParseJSON parser = engine.eval(src).execute().as(ParseJSON.class);

        List<Repository> repos = parser.parse();
        assertEquals("One repo", 1, repos.size());
        assertEquals("holssewebsocket", repos.get(0).name());
        assertTrue("wiki", repos.get(0).has_wiki());
        assertEquals("3 urls", 3, repos.get(0).urls().size());
        final String url1 = repos.get(0).urls().get(0);
        assertEquals("1st", "https://api.github.com/repos/jersey/hol", url1);

        Owner owner = repos.get(0).owner();
        assertNotNull("Owner exists", owner);

        assertEquals("login", "jersey", owner.login());
        assertEquals("id", 399710, owner.id());
        assertFalse(owner.site_admin());
    }

    // END: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessJSONObjectProperties

    @Test
    @SuppressWarnings("deprecation")
    public void testHelloWorld() {
        com.oracle.truffle.tutorial.HelloWorld.runTests();
    }

    // Checkstyle: resume
}
