/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.source;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.GCUtils;
import com.oracle.truffle.api.test.common.TestUtils;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class SourceCacheTest {

    @Test
    public void testTraceSourceCache() throws Throwable {
        testCommon(SourceCacheTestLanguage.ID, Map.of("engine.TraceSourceCache", "true"), "[miss]", null);
    }

    @Test
    public void testTraceSourceCacheDetails() throws Throwable {
        testCommon(SourceCacheTestLanguage.ID, Map.of("engine.TraceSourceCacheDetails", "true"), "[miss, hit]", null);
    }

    @Test
    public void testTraceSourceCacheFailure() throws Throwable {
        testCommon(SourceCacheFailureTestLanguage.ID, Map.of("engine.TraceSourceCache", "true"), "[fail]", "DummyParseException");
    }

    @Test
    public void testTraceSourceCacheEviction() throws IOException {
        TruffleTestAssumptions.assumeWeakEncapsulation(); // Can't control GC in the isolate.
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); Context context = Context.newBuilder().option("engine.TraceSourceCache", "true").out(out).err(out).build()) {
            Source auxiliarySource = Source.newBuilder(SourceCacheTestLanguage.ID, "x", "AuxiliarySource").build();
            String sourceName = "TestSource";
            String[] sourceHash = new String[1];
            for (int i = 0; i < 2; i++) {
                Source source = Source.newBuilder(SourceCacheTestLanguage.ID, "", sourceName).build();
                int sourceHashCode = context.eval(source).asInt();
                sourceHash[0] = String.format("0x%08x", sourceHashCode);
                WeakReference<Source> souceRef = new WeakReference<>(source);
                source = null;
                GCUtils.assertGc("Source was not collected", souceRef);
                context.eval(auxiliarySource);
            }
            List<String> logs = new ArrayList<>();
            forEachLog(out.toByteArray(), (matcher) -> {
                String logType = matcher.group(1);
                if ("evict".equals(logType)) {
                    logs.add(logType);
                    Assert.assertEquals(sourceHash[0], matcher.group(2));
                    Assert.assertEquals(sourceName, matcher.group(3));
                }
            });
            // at least one
            Assert.assertFalse(logs.isEmpty());
            Assert.assertEquals("evict", logs.get(1));
        }
    }

    private static final Pattern LOG_PATTERN = Pattern.compile("^\\[engine] source-cache-(\\w+)\\s+(0x[0-9a-f]+) (\\w+).*UTC \\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}((?:\\|Error .*)?)$");

    private static void forEachLog(byte[] logBytes, Consumer<Matcher> logConsumer) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(logBytes)))) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher matcher = LOG_PATTERN.matcher(line);
                if (matcher.find()) {
                    logConsumer.accept(matcher);
                }
            }
        }
    }

    private static void testCommon(String languageId, Map<String, String> options, String expectedLogs, String failMessage) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); Context context = Context.newBuilder().options(options).out(out).err(out).build()) {
            String sourceName = "TestSource";
            Source source = Source.newBuilder(languageId, "", sourceName).build();
            String[] sourceHash = new String[1];
            Runnable testRunnable = () -> {
                int sourceHashCode = context.eval(source).asInt();
                sourceHash[0] = String.format("0x%08x", sourceHashCode);
            };
            if (failMessage != null) {
                AbstractPolyglotTest.assertFails(testRunnable, PolyglotException.class);
            } else {
                testRunnable.run();
                context.eval(source);
            }
            List<String> logs = new ArrayList<>();
            forEachLog(out.toByteArray(), (matcher) -> {
                String logType = matcher.group(1);
                logs.add(logType);
                if (!"fail".equals(logType)) {
                    Assert.assertEquals(sourceHash[0], matcher.group(2));
                } else {
                    Assert.assertTrue(matcher.group().endsWith("Error " + failMessage));
                }
                Assert.assertEquals(sourceName, matcher.group(3));
            });
            Assert.assertEquals(expectedLogs, Arrays.toString(logs.toArray()));
        }
    }

    @TruffleLanguage.Registration(contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
    static class SourceCacheTestLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        static final String ID = TestUtils.getDefaultLanguageId(SourceCacheTestLanguage.class);

        @Option(category = OptionCategory.USER, help = "Sharing Group.", stability = OptionStability.STABLE) //
        static OptionKey<String> SharingGroup = new OptionKey<>("");

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            int sourceHashCode = request.getSource().hashCode();
            return new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return sourceHashCode;
                }
            }.getCallTarget();
        }

        @Override
        protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
            /*
             * Forces creation of a new sharing layer for each context where the option SharingGroup
             * has a different value.
             */
            return firstOptions.get(SharingGroup).equals(newOptions.get(SharingGroup));
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new SourceCacheTestLanguageOptionDescriptors();
        }
    }

    @SuppressWarnings("serial")
    static class ParseException extends AbstractTruffleException {

        ParseException() {
            super("DummyParseException");
        }
    }

    @TruffleLanguage.Registration
    static class SourceCacheFailureTestLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        static final String ID = TestUtils.getDefaultLanguageId(SourceCacheFailureTestLanguage.class);

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            throw new ParseException();
        }
    }

    @Test
    public void testSourceCachesCleared() throws IOException {
        TruffleTestAssumptions.assumeWeakEncapsulation(); // Can't control GC in the isolate.
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); Engine engine = Engine.newBuilder().option("engine.TraceSourceCache", "true").out(out).err(out).build()) {
            String sourceName1;
            String sourceName2;
            Source source1 = Source.newBuilder(SourceCacheTestLanguage.ID, "1", sourceName1 = "TestSource1").build();
            Source source2 = Source.newBuilder(SourceCacheTestLanguage.ID, "2", sourceName2 = "TestSource2").build();
            try (Context context1 = Context.newBuilder().engine(engine).option(SourceCacheTestLanguage.ID + ".SharingGroup", "one").build()) {
                String sourceHash1 = String.format("0x%08x", context1.eval(source1).asInt());
                String sourceHash2 = String.format("0x%08x", context1.eval(source2).asInt());
                /*
                 * context2 creates a separate sharing layer because of the option SharingGroup and
                 * the implementation of the method SourceCacheTestLanguage#areOptionCompatible.
                 */
                try (Context context2 = Context.newBuilder().engine(engine).option(SourceCacheTestLanguage.ID + ".SharingGroup", "two").build()) {
                    WeakReference<Source> souceRef2 = new WeakReference<>(source2);
                    source2 = null;
                    GCUtils.assertGc("Source 2 was not collected", souceRef2);
                    /*
                     * The following context2 eval is supposed to clear source2 from context1
                     * layer's source cache.
                     */
                    context2.eval(source1);
                    WeakReference<Source> souceRef1 = new WeakReference<>(source1);
                    source1 = null;
                    GCUtils.assertGc("Source 1 was not collected", souceRef1);
                    /*
                     * The following context2 close is supposed to clear source1 both from context1
                     * layer's source cache and from context2 layer's source cache.
                     */
                }
                List<String> logs = new ArrayList<>();
                forEachLog(out.toByteArray(), (matcher) -> {
                    String logType = matcher.group(1);
                    String suffix;
                    String loggedHash = matcher.group(2);
                    String loggedName = matcher.group(3);
                    if (sourceName1.equals(loggedName)) {
                        Assert.assertEquals(sourceHash1, loggedHash);
                        suffix = "1";
                    } else if (sourceName2.equals(loggedName)) {
                        Assert.assertEquals(sourceHash2, loggedHash);
                        suffix = "2";
                    } else {
                        suffix = "Unknown";
                    }
                    logs.add(logType + suffix);
                });
                String[] expectedSequence = new String[]{"miss1", "miss2", "evict2", "miss1", "evict1", "evict1"};
                Assert.assertArrayEquals(expectedSequence, logs.toArray());
            }
        }
    }
}
