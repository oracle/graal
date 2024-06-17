/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.SpecializationStatistics;
import com.oracle.truffle.api.dsl.test.SpecializationStatisticsTestFactory.SpecializationStatisticTestNodeGen;
import com.oracle.truffle.api.dsl.test.SpecializationStatisticsTestFactory.UseSpecializationStatisticTestInliningNodeGen;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class SpecializationStatisticsTest {

    private static final String EXPECTED = " -----------------------------------------------------------------------------------------------------------------------------------------------------%n" +
                    "| Name                                                       Instances          Executions     Executions per instance %n" +
                    " -----------------------------------------------------------------------------------------------------------------------------------------------------%n" +
                    "| SpecializationStatisticTestNodeGen.Uncached                1 (25%)             1 (6%)         Min=         1 Avg=        1.00 Max=          1  MaxNode= N/A %n" +
                    "|   s0                                                         0 (0%)              0 (0%)         Min=         0 Avg=        0.00 Max=          0  MaxNode=  -  %n" +
                    "|   s1                                                         0 (0%)              0 (0%)         Min=         0 Avg=        0.00 Max=          0  MaxNode=  -  %n" +
                    "|   s2                                                         0 (0%)              0 (0%)         Min=         0 Avg=        0.00 Max=          0  MaxNode=  -  %n" +
                    "|   s3 <String>                                                1 (100%)            1 (100%)       Min=         1 Avg=        1.00 Max=          1  MaxNode= N/A %n" +
                    "|   --------------------------------------------------------------------------------------------------------------------------------------------------%n" +
                    "|   [s3]                                                       1 (100%)            1 (100%)       Min=         1 Avg=        1.00 Max=          1  MaxNode= N/A %n" +
                    " -----------------------------------------------------------------------------------------------------------------------------------------------------%n" +
                    "| Name                                                       Instances          Executions     Executions per instance %n" +
                    " -----------------------------------------------------------------------------------------------------------------------------------------------------%n" +
                    "| SpecializationStatisticTestInliningNodeGen.Inlined         1 (25%)             7 (39%)        Min=         7 Avg=        7.00 Max=          7  MaxNode= N/A %n" +
                    "|   s0 <int int>                                               1 (100%)            3 (43%)        Min=         3 Avg=        3.00 Max=          3  MaxNode= N/A %n" +
                    "|   s1 <int>                                                   1 (100%)            1 (14%)        Min=         1 Avg=        1.00 Max=          1  MaxNode= N/A %n" +
                    "|   s2 <String>                                                1 (100%)            1 (14%)        Min=         1 Avg=        1.00 Max=          1  MaxNode= N/A %n" +
                    "|   s3                                                         1 (100%)            2 (29%)        Min=         2 Avg=        2.00 Max=          2  MaxNode= N/A %n" +
                    "|     <String>                                                   1 (100%)            1 (50%)        Min=         1 Avg=        1.00 Max=          1  MaxNode= N/A %n" +
                    "|     <StringBuilder>                                            1 (100%)            1 (50%)        Min=         1 Avg=        1.00 Max=          1  MaxNode= N/A %n" +
                    "|   --------------------------------------------------------------------------------------------------------------------------------------------------%n" +
                    "|   [s0, s1, s2, s3]                                           1 (100%)            7 (100%)       Min=         7 Avg=        7.00 Max=          7  MaxNode= N/A %n" +
                    "|     s0                                                         1 (100%)            3 (43%)        Min=         3 Avg=        3.00 Max=          3  MaxNode= N/A %n" +
                    "|     s1                                                         1 (100%)            1 (14%)        Min=         1 Avg=        1.00 Max=          1  MaxNode= N/A %n" +
                    "|     s2                                                         1 (100%)            1 (14%)        Min=         1 Avg=        1.00 Max=          1  MaxNode= N/A %n" +
                    "|     s3                                                         1 (100%)            2 (29%)        Min=         2 Avg=        2.00 Max=          2  MaxNode= N/A %n" +
                    " -----------------------------------------------------------------------------------------------------------------------------------------------------%n" +
                    "| Name                                                       Instances          Executions     Executions per instance %n" +
                    " -----------------------------------------------------------------------------------------------------------------------------------------------------%n" +
                    "| SpecializationStatisticTestNodeGen                         2 (50%)            10 (56%)        Min=         3 Avg=        5.00 Max=          7  MaxNode= testLangFile0.file~1:0 %n" +
                    "|   s0 <int>                                                   1 (50%)             3 (30%)        Min=         3 Avg=        3.00 Max=          3  MaxNode= testLangFile0.file~1:0 %n" +
                    "|   s1 <int>                                                   1 (50%)             1 (10%)        Min=         1 Avg=        1.00 Max=          1  MaxNode= testLangFile0.file~1:0 %n" +
                    "|   s2 <String>                                                2 (100%)            4 (40%)        Min=         1 Avg=        2.00 Max=          3  MaxNode= testLangFile1.file~1:0 %n" +
                    "|   s3                                                         1 (50%)             2 (20%)        Min=         2 Avg=        2.00 Max=          2  MaxNode= testLangFile0.file~1:0 %n" +
                    "|     <String>                                                   1 (100%)            1 (50%)        Min=         1 Avg=        1.00 Max=          1  MaxNode= testLangFile0.file~1:0 %n" +
                    "|     <StringBuilder>                                            1 (100%)            1 (50%)        Min=         1 Avg=        1.00 Max=          1  MaxNode= testLangFile0.file~1:0 %n" +
                    "|   --------------------------------------------------------------------------------------------------------------------------------------------------%n" +
                    "|   [s0, s1, s2, s3]                                           1 (50%)             7 (70%)        Min=         7 Avg=        7.00 Max=          7  MaxNode= testLangFile0.file~1:0 %n" +
                    "|     s0                                                         1 (100%)            3 (43%)        Min=         3 Avg=        3.00 Max=          3  MaxNode= testLangFile0.file~1:0 %n" +
                    "|     s1                                                         1 (100%)            1 (14%)        Min=         1 Avg=        1.00 Max=          1  MaxNode= testLangFile0.file~1:0 %n" +
                    "|     s2                                                         1 (100%)            1 (14%)        Min=         1 Avg=        1.00 Max=          1  MaxNode= testLangFile0.file~1:0 %n" +
                    "|     s3                                                         1 (100%)            2 (29%)        Min=         2 Avg=        2.00 Max=          2  MaxNode= testLangFile0.file~1:0 %n" +
                    "|   [s2]                                                       1 (50%)             3 (30%)        Min=         3 Avg=        3.00 Max=          3  MaxNode= testLangFile1.file~1:0 %n" +
                    " -----------------------------------------------------------------------------------------------------------------------------------------------------%n";

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

    private Locale systemSetting;

    @Before
    public final void localeFixup() {
        systemSetting = Locale.getDefault();
        // make sure formatting (e.g. decimal separator) matches the expected string above
        Locale.setDefault(Locale.ENGLISH);
    }

    @After
    public final void localeRestore() {
        Locale.setDefault(systemSetting);
    }

    @Test
    public void testCustomEnterLeave() {
        SpecializationStatistics statistics = SpecializationStatistics.create();
        SpecializationStatistics prev = statistics.enter();

        createAndExecuteNodes();

        StringWriter writer = new StringWriter();
        statistics.printHistogram(new PrintWriter(writer));

        String contents = readExpectedOutput();

        Assert.assertEquals(contents, writer.toString());

        statistics.leave(prev);
    }

    private static String readExpectedOutput() {
        return EXPECTED.replaceAll("%n", System.lineSeparator());
    }

    private static void createAndExecuteNodes() {
        createAndExecuteNodesPart1();
        createAndExecuteNodesPart2();
        createAndExecuteInlinedNode();
    }

    private static void createAndExecuteInlinedNode() {
        UseSpecializationStatisticTestInliningNode node = UseSpecializationStatisticTestInliningNodeGen.create();
        node.execute(42);
        node.execute(43);
        node.execute(44);
        node.execute(45);
        node.execute("");
        node.execute(new StringBuilder());
        node.execute("");
    }

    private static void createAndExecuteNodesPart1() {
        SpecializationStatisticTestNode node = SpecializationStatisticTestNodeGen.create(0);
        node.execute(42);
        node.execute(43);
        node.execute(44);
        node.execute(45);
        node.execute("");
        node.execute(new StringBuilder());
        node.execute("");
    }

    private static void createAndExecuteNodesPart2() {
        SpecializationStatisticTestNode node = SpecializationStatisticTestNodeGen.create(1);
        node.execute("");
        node.execute("");
        node.execute("");

        node = SpecializationStatisticTestNodeGen.getUncached();
        node.execute("");
    }

    @Test
    public void testWithContextEnabled() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = newTestContextBuilder().option("engine.SpecializationStatistics", "true").logHandler(out).build()) {
            context.enter();
            createAndExecuteNodes();
            context.leave();
        }
        Assert.assertEquals(createLogEntry(readExpectedOutput()), new String(out.toByteArray()));
    }

    @Test
    public void testWithContextEnabledNestedEnter() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = newTestContextBuilder().option("engine.SpecializationStatistics", "true").logHandler(out).build()) {
            context.enter();
            context.enter();
            createAndExecuteNodesPart1();
            createAndExecuteInlinedNode();
            context.leave();
            createAndExecuteNodesPart2();
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
        try (Context context = newTestContextBuilder().option("engine.SpecializationStatistics", "false").logHandler(out).build()) {
            context.enter();
            createAndExecuteNodes();
            context.leave();
        }
        Assert.assertEquals("", new String(out.toByteArray()));
    }

    @Test
    public void testWithContextNoExecute() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = newTestContextBuilder().option("engine.SpecializationStatistics", "true").logHandler(out).build()) {
            context.enter();
            context.leave();
        }
        Assert.assertEquals(createLogEntry("No specialization statistics data was collected. " +
                        "Either no node with @Specialization annotations was executed or the interpreter was not compiled with -J-Dtruffle.dsl.GenerateSpecializationStatistics=true e.g as parameter to the javac tool."),
                        new String(out.toByteArray()));
    }

    private static Builder newTestContextBuilder() {
        return Context.newBuilder().allowExperimentalOptions(true).option("engine.WarnInterpreterOnly", "false");
    }

    @SpecializationStatistics.AlwaysEnabled
    @GenerateInline
    abstract static class SpecializationStatisticTestInliningNode extends Node {

        abstract Object execute(Node node, Object arg);

        @Specialization(guards = "arg == cachedArg", limit = "3", assumptions = "createAssumption()")
        public int s0(@SuppressWarnings("unused") int arg, @Cached("arg") int cachedArg) {
            return cachedArg;
        }

        @Specialization(replaces = "s0")
        public int s1(@SuppressWarnings("unused") int arg) {
            return arg;
        }

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

    }

    @SuppressWarnings("truffle-inlining")
    abstract static class UseSpecializationStatisticTestInliningNode extends Node {

        abstract Object execute(Object arg);

        @Specialization
        public Object s0(Object arg, @Cached(inline = true) SpecializationStatisticTestInliningNode node) {
            return node.execute(this, arg);
        }

    }

}
