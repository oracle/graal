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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.tck.impl.TruffleLanguageRunner.JavaScriptRunner;

/**
 * Tests with code snippets referencing JavaScript (executed only when an implementation of
 * JavaScript is around).
 */
@RunWith(JavaScriptRunner.class)
public class PolyglotEngineWithJavaScript {

    private Context context;

    @Before
    public void initEngine() {
        context = Context.newBuilder().build();
    }

    @After
    public void disposeEngine() {
        context.close();
    }

// @formatter:off

    @Test
    public void testCallJavaScriptFunctionFromJava() {
        callJavaScriptFunctionFromJava();
    }

    // BEGIN: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#callJavaScriptFunctionFromJava
    @FunctionalInterface
    interface Multiplier {
        int multiply(int a, int b);
    }

    public void callJavaScriptFunctionFromJava() {
        Source src = Source.newBuilder("js",
            "(function (a, b) {\n" +
            "  return a * b;\n" +
            "})\n",
            "mul.js").buildLiteral();

        // Evaluate JavaScript function definition
        Value jsFunction = context.eval(src);

        // Create Java access to JavaScript function
        Multiplier mul = jsFunction.as(Multiplier.class);

        assertEquals(42, mul.multiply(6, 7));
        assertEquals(144, mul.multiply(12, 12));
        assertEquals(256, mul.multiply(32, 8));
    }
    // END: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#callJavaScriptFunctionFromJava

    @Test
    public void testCallJavaScriptFunctionsWithSharedStateFromJava() {
        callJavaScriptFunctionsWithSharedStateFromJava();
    }

    // BEGIN: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#callJavaScriptFunctionsWithSharedStateFromJava
    interface Counter {
        void addTime(int hours, int minutes, int seconds);
        int timeInSeconds();
    }

    public void callJavaScriptFunctionsWithSharedStateFromJava() {
        Source src = Source.newBuilder("js", "" +
             "(function() {\n" +
             "  var seconds = 0;\n" +
             "  function addTime(h, m, s) {\n" +
             "    seconds += 3600 * h;\n" +
             "    seconds += 60 * m;\n" +
             "    seconds += s;\n" +
             "  }\n" +
             "  function time() {\n" +
             "    return seconds;\n" +
             "  }\n" +
             "  return {\n" +
             "    'addTime': addTime,\n" +
             "    'timeInSeconds': time\n" +
             "  }\n" +
             "})\n",
            "CountSeconds.js").buildLiteral();

        // Evaluate JavaScript function definition
        Value jsFunction = context.eval(src);

        // Execute the JavaScript function
        Value jsObject = jsFunction.execute();

        // Create Java access to the JavaScript object
        Counter counter = jsObject.as(Counter.class);

        counter.addTime(6, 30, 0);
        counter.addTime(9, 0, 0);
        counter.addTime(12, 5, 30);

        assertEquals(99330, counter.timeInSeconds());
    }
    // END: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#callJavaScriptFunctionsWithSharedStateFromJava

    @Test
    public void testAccessFieldsOfJavaObject() {
        accessFieldsOfJavaObject();
    }

    @Test
    public void testAccessFieldsOfJavaObjectWithConverter() {
        accessFieldsOfJavaObjectWithConverter();
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
        Source src = Source.newBuilder("js", "" +
            "(function(t) {\n" +
            "  return 3600 * t.hours + 60 * t.minutes + t.seconds;\n" +
            "})\n",
            "MomentToSeconds.js").buildLiteral();

        final Moment javaMoment = new Moment(6, 30, 10);

        // Evaluate the JavaScript function definition
        Value jsFunction = context.eval(src);

        // Execute the JavaScript function, passing a Java object argument
        Value jsSeconds = jsFunction.execute(javaMoment);

        // Convert foreign object result to desired Java type
        int seconds = jsSeconds.as(Number.class).intValue();

        assertEquals(3600 * 6 + 30 * 60 + 10, seconds);
    }
    // END: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessFieldsOfJavaObject

    // BEGIN: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessFieldsOfJavaObjectWithConverter

    @FunctionalInterface
    interface MomentConverter {
        int toSeconds(Moment moment);
    }

    public void accessFieldsOfJavaObjectWithConverter() {
        Source src = Source.newBuilder("js", "" +
            "(function(t) {\n" +
            "  return 3600 * t.hours + 60 * t.minutes + t.seconds;\n" +
            "})\n",
            "MomentToSeconds.js").buildLiteral();

        final Moment javaMoment = new Moment(6, 30, 10);

        // Evaluate the JavaScript function definition
        final Value jsFunction = context.eval(src);

        // Convert the function to desired Java type
        MomentConverter converter = jsFunction.as(MomentConverter.class);

        // Execute the JavaScript function as a Java foreign function
        int seconds = converter.toSeconds(javaMoment);

        assertEquals(3600 * 6 + 30 * 60 + 10, seconds);
    }
    // END: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessFieldsOfJavaObjectWithConverter

    @Test
    public void testCreateJavaScriptFactoryForJavaClass() {
        createJavaScriptFactoryForJavaClass();
    }

    // BEGIN: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#createJavaScriptFactoryForJavaClass

    @FunctionalInterface
    interface MomentFactory {
        Moment create(int h, int m, int s);
    }

    public void createJavaScriptFactoryForJavaClass() {
        Source src = Source.newBuilder("js", "" +
            "(function(Moment) {\n" +
            "  return function(h, m, s) {\n" +
            "     return new Moment(h, m, s);\n" +
            "  };\n" +
            "})\n",
            "ConstructMoment.js").buildLiteral();

        // Evaluate the JavaScript function definition
        final Value jsFunction = context.eval(src);

        // Create a JavaScript factory for the provided Java class
        final Value jsFactory = jsFunction.execute(Moment.class);

        // Convert the JavaScript factory to a Java foreign function
        MomentFactory momentFactory = jsFactory.as(MomentFactory.class);

        final Moment javaMoment = momentFactory.create(6, 30, 10);
        assertEquals("Hours", 6, javaMoment.hours);
        assertEquals("Minutes", 30, javaMoment.minutes);
        assertEquals("Seconds", 10, javaMoment.seconds);
    }
    // END: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#createJavaScriptFactoryForJavaClass

    @Test
    public void testCallJavaScriptClassFactoryFromJava() {
        callJavaScriptClassFactoryFromJava();
    }

    // BEGIN: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#callJavaScriptClassFactoryFromJava

    interface Incrementor {
        int inc();
        int dec();
        int value();
    }

    public void callJavaScriptClassFactoryFromJava() {
        Source src = Source.newBuilder("js", "" +
            "(function() {\n" +
            "  class JSIncrementor {\n" +
            "     constructor(init) {\n" +
            "       this.value = init;\n" +
            "     }\n" +
            "     inc() {\n" +
            "       return ++this.value;\n" +
            "     }\n" +
            "     dec() {\n" +
            "       return --this.value;\n" +
            "     }\n" +
            "  }\n" +
            "  return function(init) {\n" +
            "    return new JSIncrementor(init);\n" +
            "  }\n" +
            "})\n",
            "Incrementor.js").buildLiteral();

        // Evaluate JavaScript function definition
        Value jsFunction = context.eval(src);

        // Execute the JavaScript function
        Value jsFactory = jsFunction.execute();

        // Execute the JavaScript factory to create Java objects
        Incrementor initFive = jsFactory.execute(5).as(Incrementor.class);
        Incrementor initTen = jsFactory.execute(10).as(Incrementor.class);

        initFive.inc();
        assertEquals("Now at seven", 7, initFive.inc());

        initTen.dec();
        assertEquals("Now at eight", 8, initTen.dec());
        initTen.dec();

        assertEquals("Values are the same", initFive.value(), initTen.value());
    }
    // END: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#callJavaScriptClassFactoryFromJava


    @Test
    public void testAccessJavaScriptArrayWithTypedElementsFromJava() {
        accessJavaScriptArrayWithTypedElementsFromJava();
    }

    // BEGIN: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessJavaScriptArrayWithTypedElementsFromJava

    interface Point {
        int x();
        int y();
    }

    @FunctionalInterface
    interface PointProvider {
        List<Point> createPoints();
    }

    public void accessJavaScriptArrayWithTypedElementsFromJava() {
        Source src = Source.newBuilder("js", "" +
            "(function() {\n" +
            "  class Point {\n" +
            "     constructor(x, y) {\n" +
            "       this.x = x;\n" +
            "       this.y = y;\n" +
            "     }\n" +
            "  }\n" +
            "  return [ new Point(30, 15), new Point(5, 7) ];\n" +
            "})\n",
            "ArrayOfPoints.js").buildLiteral();

        // Evaluate the JavaScript function definition
        Value jsFunction = context.eval(src);

        // Create Java-typed access to the JavaScript function
        PointProvider pointProvider = jsFunction.as(PointProvider.class);

        // Invoke the JavaScript function to generate points
        List<Point> points = pointProvider.createPoints();

        assertEquals("Two points", 2, points.size());

        Point first = points.get(0);
        assertEquals(30, first.x());
        assertEquals(15, first.y());

        Point second = points.get(1);
        assertEquals(5, second.x());
        assertEquals(7, second.y());
    }
    // END: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessJavaScriptArrayWithTypedElementsFromJava

    @Test
    public void tetsAccessJSONObjectProperties() {
        accessJavaScriptJSONObjectFromJava();
    }


    // Checkstyle: stop
    // BEGIN: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessJavaScriptJSONObjectFromJava

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

    @FunctionalInterface
    interface ParseJSON {
        List<Repository> parse();
    }

    public void accessJavaScriptJSONObjectFromJava() {
        Source src = Source.newBuilder("js",
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
            "})\n",
            "github-api-value.js").buildLiteral();

        // Evaluate the JavaScript function definition
        Value jsFunction = context.eval(src);

        // Execute the JavaScript function to create the "mock parser"
        Value jsMockParser = jsFunction.execute();

        // Create Java-typed access to the "mock parser"
        ParseJSON mockParser = jsMockParser.as(ParseJSON.class);

        List<Repository> repos = mockParser.parse();
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

    // END: com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessJavaScriptJSONObjectFromJava

    // Checkstyle: resume
}
