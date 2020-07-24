/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.SpecializationStatistics;
import com.oracle.truffle.api.dsl.test.SpecializationStatisticsTestFactory.SpecializationStatisticTestNodeGen;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class SpecializationStatisticsTest {

    @GenerateUncached
    @NodeField(name = "index", type = int.class)
    @SpecializationStatistics.AlwaysEnabled
    abstract static class SpecializationStatisticTestNode extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == cachedArg", limit = "3", assumptions = "createAssumption()")
        public int s0(@SuppressWarnings("unused") int arg, @Cached("arg") int cachedArg) {
            return cachedArg;
        }

        @Specialization(replaces = "s0")
        public int s1(@SuppressWarnings("unused") int arg) {
            return arg;
        }

        protected abstract int getIndex();

        static Assumption createAssumption() {
            return Truffle.getRuntime().createAssumption();
        }

        @Specialization
        public String s2(String arg) {
            return arg;
        }

        @Specialization(replaces = "s2")
        public CharSequence s3(CharSequence arg) {
            return arg;
        }

        @Override
        public SourceSection getSourceSection() {
            if (!isAdoptable()) {
                return null;
            }
            return Source.newBuilder("testLang", String.valueOf(getIndex()), "testLangFile" + getIndex() + ".file").build().createSection(1);
        }

    }

    @Test
    public void testCustomEnterLeave() throws IOException {
        SpecializationStatistics statistics = SpecializationStatistics.create();
        SpecializationStatistics prev = statistics.enter();

        createAndExecuteNodes();

        StringWriter writer = new StringWriter();
        statistics.printHistogram(new PrintWriter(writer));

        String contents = readExpectedOutput();

        Assert.assertEquals(contents, writer.toString());

        statistics.leave(prev);
    }

    private static String readExpectedOutput() throws IOException {
        URL url = SpecializationStatisticsTest.class.getResource("SpecializationStatisticsTest.out");
        String contents;
        URLConnection conn = url.openConnection();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            contents = reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        return contents;
    }

    private static void createAndExecuteNodes() {
        SpecializationStatisticTestNode node = SpecializationStatisticTestNodeGen.create(0);
        node.execute(42);
        node.execute(43);
        node.execute(44);
        node.execute(45);
        node.execute("");
        node.execute(new StringBuilder());
        node.execute("");

        node = SpecializationStatisticTestNodeGen.create(1);
        node.execute("");
        node.execute("");
        node.execute("");

        node = SpecializationStatisticTestNodeGen.getUncached();
        node.execute("");
    }

    @Test
    public void testWithContextEnabled() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder().allowExperimentalOptions(true).option("engine.SpecializationStatistics", "true").logHandler(out).build()) {
            context.enter();
            createAndExecuteNodes();
            context.leave();
        }
        Assert.assertEquals(createLogEntry(readExpectedOutput()), new String(out.toByteArray()));
    }

    private static String createLogEntry(String logMessage) {
        return String.format("[engine] Specialization histogram: %n%s%n", logMessage);
    }

    @Test
    public void testWithContextDisabled() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder().allowExperimentalOptions(true).option("engine.SpecializationStatistics", "false").logHandler(out).build()) {
            context.enter();
            createAndExecuteNodes();
            context.leave();
        }
        Assert.assertEquals("", new String(out.toByteArray()));
    }

    @Test
    public void testWithContextNoExecute() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder().allowExperimentalOptions(true).option("engine.SpecializationStatistics", "true").logHandler(out).build()) {
            context.enter();
            context.leave();
        }
        Assert.assertEquals(createLogEntry("No specialization statistics data was collected. " +
                        "Either no node with @Specialization annotations was executed or the interpreter was not compiled with -J-Dtruffle.dsl.GenerateSpecializationStatistics=true e.g as parameter to the javac tool."),
                        new String(out.toByteArray()));
    }

}
