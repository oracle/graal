/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.SubprocessTestUtils;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tests that Truffle compilation JFR events expose enough information to reconstruct the
 * {@code engine.TraceCompilationDetails} log output. The synthetic language below exercises
 * successful compilations, queued, started, and dequeued compilations, compilation failures,
 * assumption invalidations, and deoptimizations.
 */
public class JFRCompilationEventsTest {
    private static final DateTimeFormatter UTC_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").withZone(ZoneOffset.UTC);
    private static final Pattern DONE_TIME_PATTERN = Pattern.compile("\\|Time\\s+[^|]+\\|AST");
    private static final Pattern QUEUED_TIME_PATTERN = Pattern.compile(" Time\\s+[^|]+\\|UTC");
    private static final Pattern FAILED_TIME_PATTERN = Pattern.compile("\\|Time\\s+[^|]+\\|Reason:");
    private static final Pattern QUEUE_SIZE_PATTERN = Pattern.compile("Queue: Size\\s+\\d+");
    private static final Pattern UTC_PATTERN = Pattern.compile("\\|UTC\\s+([^|]+)\\|Src");
    private static final String INV_PADDING = "                                                                                                                              ";
    private static final String DEOPT_PADDING = "                                                                                                            ";

    @Test
    public void testJFRTraceCompilationParity() throws IOException, InterruptedException {
        TruffleTestAssumptions.assumeOptimizingRuntime();
        TruffleTestAssumptions.assumeWeakEncapsulation();
        assumeJfrClassesAvailable();
        DeoptInvalidateFailureLanguage.resetState();
        Runnable test = () -> {
            StringBuilder jfrOutputBuilder = new StringBuilder();
            ByteArrayOutputStream contextOutputBytes = new ByteArrayOutputStream();
            try (PrintStream contextOutput = new PrintStream(contextOutputBytes, false, StandardCharsets.UTF_8);
                            RecordingStream rs = new RecordingStream();
                            Context context = Context.newBuilder().allowExperimentalOptions(true) //
                                            .option("engine.CompileImmediately", "true") //
                                            .option("engine.BackgroundCompilation", "false") //
                                            .option("engine.TraceCompilationDetails", "true") //
                                            .option("engine.CompilationFailureAction", "Silent") //
                                            .out(contextOutput) //
                                            .err(contextOutput) //
                                            .build()) {
                rs.enable("jdk.graal.compiler.truffle.Compilation");
                rs.enable("jdk.graal.compiler.truffle.CompilationQueued");
                rs.enable("jdk.graal.compiler.truffle.CompilationStarted");
                rs.enable("jdk.graal.compiler.truffle.CompilationDequeued");
                rs.enable("jdk.graal.compiler.truffle.CompilationFailure");
                rs.enable("jdk.graal.compiler.truffle.Deoptimization");
                rs.enable("jdk.graal.compiler.truffle.AssumptionInvalidation");
                rs.onEvent("jdk.graal.compiler.truffle.Compilation", new Consumer<RecordedEvent>() {
                    @Override
                    public void accept(RecordedEvent recordedEvent) {
                        if (!recordedEvent.getBoolean("success")) {
                            return;
                        }
                        Duration duration = recordedEvent.getDuration();
                        long durationMs = Math.round(duration.toNanos() / 1_000_000d);
                        long peTimeMs = recordedEvent.getLong("peTime");
                        long backendTimeMs = durationMs - peTimeMs;
                        String utcStartTime = UTC_FORMATTER.format(recordedEvent.getStartTime());
                        String time = String.format("%4d(%4d+%-4d)ms", durationMs, peTimeMs, backendTimeMs);
                        String jfrOutputLine = String.format(
                                        "[engine] opt done   engine=%-2d id=%-5d %-50s |Tier %d|Time %18s|AST %4d|Inlined %3dY %3dN|IR %6d/%6d|CodeSize %7d|Addr 0x%012x|CompId %-7s|UTC %s|Src %s",
                                        recordedEvent.getLong("engineId"),
                                        recordedEvent.getLong("id"),
                                        recordedEvent.getString("rootFunction"),
                                        recordedEvent.getInt("truffleTier"),
                                        time,
                                        recordedEvent.getInt("astSize"),
                                        recordedEvent.getInt("inlinedCalls"),
                                        recordedEvent.getInt("dispatchedCalls"),
                                        recordedEvent.getInt("peNodeCount"),
                                        recordedEvent.getInt("graalNodeCount"),
                                        recordedEvent.getInt("compiledCodeSize"),
                                        recordedEvent.getLong("compiledCodeAddress"),
                                        recordedEvent.getString("compilationId"),
                                        utcStartTime,
                                        formatSource(recordedEvent));
                        jfrOutputBuilder.append(jfrOutputLine).append(System.lineSeparator());
                    }
                });
                rs.onEvent("jdk.graal.compiler.truffle.CompilationQueued", new Consumer<RecordedEvent>() {
                    @Override
                    public void accept(RecordedEvent recordedEvent) {
                        String utcStartTime = UTC_FORMATTER.format(recordedEvent.getStartTime());
                        int queueChange = recordedEvent.getInt("queueChange");
                        char queueChangeSign = queueChange >= 0 ? '+' : '-';
                        String jfrOutputLine = String.format(
                                        "[engine] opt queued engine=%-2d id=%-5d %-50s |Tier %d|Count/Thres  %9d/%9d|Queue: Size %4d Change %c%-2d Load %5.2f Time %5dus                                   |UTC %s|Src %s",
                                        recordedEvent.getLong("engineId"),
                                        recordedEvent.getLong("id"),
                                        recordedEvent.getString("rootFunction"),
                                        recordedEvent.getInt("tier"),
                                        recordedEvent.getInt("count"),
                                        recordedEvent.getInt("threshold"),
                                        recordedEvent.getInt("queueSize"),
                                        queueChangeSign,
                                        Math.abs(queueChange),
                                        recordedEvent.getDouble("queueLoad"),
                                        recordedEvent.getLong("queueTime"),
                                        utcStartTime,
                                        formatSource(recordedEvent));
                        jfrOutputBuilder.append(jfrOutputLine).append(System.lineSeparator());
                    }
                });
                rs.onEvent("jdk.graal.compiler.truffle.CompilationDequeued", new Consumer<RecordedEvent>() {
                    @Override
                    public void accept(RecordedEvent recordedEvent) {
                        String utcStartTime = UTC_FORMATTER.format(recordedEvent.getStartTime());
                        int queueChange = recordedEvent.getInt("queueChange");
                        char queueChangeSign = queueChange == 0 ? ' ' : (queueChange > 0 ? '+' : '-');
                        String jfrOutputLine = String.format(
                                        "[engine] opt unque. engine=%-2d id=%-5d %-50s |Tier %d|Count/Thres  %9d/%9d|Queue: Size %4d Change %c%-2d Load %5.2f Time %5dus                                   |UTC %s|Src %s|Reason %s",
                                        recordedEvent.getLong("engineId"),
                                        recordedEvent.getLong("id"),
                                        recordedEvent.getString("rootFunction"),
                                        recordedEvent.getInt("tier"),
                                        recordedEvent.getInt("count"),
                                        recordedEvent.getInt("threshold"),
                                        recordedEvent.getInt("queueSize"),
                                        queueChangeSign,
                                        Math.abs(queueChange),
                                        recordedEvent.getDouble("queueLoad"),
                                        recordedEvent.getLong("queueTime"),
                                        utcStartTime,
                                        formatSource(recordedEvent),
                                        recordedEvent.getString("reason"));
                        jfrOutputBuilder.append(jfrOutputLine).append(System.lineSeparator());
                    }
                });
                rs.onEvent("jdk.graal.compiler.truffle.CompilationStarted", new Consumer<RecordedEvent>() {
                    @Override
                    public void accept(RecordedEvent recordedEvent) {
                        String utcStartTime = UTC_FORMATTER.format(recordedEvent.getStartTime());
                        int queueChange = recordedEvent.getInt("queueChange");
                        char queueChangeSign = queueChange >= 0 ? '+' : '-';
                        String jfrOutputLine = String.format(
                                        "[engine] opt start  engine=%-2d id=%-5d %-50s |Tier %d|Priority %9d|Rate %8s|Queue: Size %4d Change %c%-2d Load %5.2f Time %5dus                                   |UTC %s|Src %s|Bonuses %s",
                                        recordedEvent.getLong("engineId"),
                                        recordedEvent.getLong("id"),
                                        recordedEvent.getString("rootFunction"),
                                        recordedEvent.getInt("tier"),
                                        recordedEvent.getLong("priority"),
                                        recordedEvent.getString("rate"),
                                        recordedEvent.getInt("queueSize"),
                                        queueChangeSign,
                                        Math.abs(queueChange),
                                        recordedEvent.getDouble("queueLoad"),
                                        recordedEvent.getLong("queueTime"),
                                        utcStartTime,
                                        formatSource(recordedEvent),
                                        recordedEvent.getString("bonuses"));
                        jfrOutputBuilder.append(jfrOutputLine).append(System.lineSeparator());
                    }
                });
                rs.onEvent("jdk.graal.compiler.truffle.CompilationFailure", new Consumer<RecordedEvent>() {
                    @Override
                    public void accept(RecordedEvent recordedEvent) {
                        String utcStartTime = UTC_FORMATTER.format(recordedEvent.getStartTime());
                        long durationMs = Math.round(recordedEvent.getDuration().toNanos() / 1_000_000d);
                        String time = String.format("%4dms", durationMs);
                        String jfrOutputLine = String.format("[engine] opt failed engine=%-2d id=%-5d %-50s |Tier %d|Time %18s|Reason: %s|UTC %s|Src %s",
                                        recordedEvent.getLong("engineId"),
                                        recordedEvent.getLong("id"),
                                        recordedEvent.getString("rootFunction"),
                                        recordedEvent.getInt("truffleTier"),
                                        time,
                                        recordedEvent.getString("failureReason"),
                                        utcStartTime,
                                        formatSource(recordedEvent));
                        jfrOutputBuilder.append(jfrOutputLine).append(System.lineSeparator());
                    }
                });
                rs.onEvent("jdk.graal.compiler.truffle.AssumptionInvalidation", new Consumer<RecordedEvent>() {
                    @Override
                    public void accept(RecordedEvent recordedEvent) {
                        String utcStartTime = UTC_FORMATTER.format(recordedEvent.getStartTime());
                        String jfrOutputLine = String.format("[engine] opt inval. engine=%-2d id=%-5d %-50s  " + INV_PADDING + "|UTC %s|Src %s|Reason %s",
                                        recordedEvent.getLong("engineId"),
                                        recordedEvent.getLong("id"),
                                        recordedEvent.getString("rootFunction"),
                                        utcStartTime,
                                        formatSource(recordedEvent),
                                        recordedEvent.getString("reason"));
                        jfrOutputBuilder.append(jfrOutputLine).append(System.lineSeparator());
                    }
                });
                rs.onEvent("jdk.graal.compiler.truffle.Deoptimization", new Consumer<RecordedEvent>() {
                    @Override
                    public void accept(RecordedEvent recordedEvent) {
                        String utcStartTime = UTC_FORMATTER.format(recordedEvent.getStartTime());
                        String jfrOutputLine = String.format("[engine] opt deopt  engine=%-2d id=%-5d %-50s |Invalidated %5b|" + DEOPT_PADDING + "|UTC %s|Src %s|Reason %s",
                                        recordedEvent.getLong("engineId"),
                                        recordedEvent.getLong("id"),
                                        recordedEvent.getString("rootFunction"),
                                        recordedEvent.getBoolean("invalidated"),
                                        utcStartTime,
                                        formatSource(recordedEvent),
                                        recordedEvent.getString("reason"));
                        jfrOutputBuilder.append(jfrOutputLine).append(System.lineSeparator());
                    }
                });
                rs.startAsync();

                Source source = Source.newBuilder(DeoptInvalidateFailureLanguage.ID, "ignored source text", "DeoptOnceSource").build();
                Instant evalStart = Instant.now();
                DeoptInvalidateFailureLanguage.scheduleRecoverablePrepareFailure();
                Assert.assertEquals(42, context.eval(source).asInt());
                Assert.assertEquals(42, context.eval(source).asInt());
                DeoptInvalidateFailureLanguage.invalidateExternalAssumption();
                Assert.assertEquals(42, context.eval(source).asInt());
                DeoptInvalidateFailureLanguage.schedulePermanentPrepareFailure();
                Assert.assertEquals(42, context.eval(source).asInt());
                Instant evalEnd = Instant.now();
                rs.stop();
                rs.awaitTermination();

                String jfrOutput = jfrOutputBuilder.toString();
                contextOutput.flush();
                String contextLogOutput = contextOutputBytes.toString(StandardCharsets.UTF_8);

                List<String> jfrCompilationLogLines = extractCompilationLogLines(jfrOutput);
                List<String> contextCompilationLogLines = extractCompilationLogLines(contextLogOutput);
                normalizeStartQueuedOrder(contextCompilationLogLines);
                normalizeStartQueuedOrder(jfrCompilationLogLines);

                Assert.assertTrue("No [engine] opt done lines found in context logger output.", hasPrefix(contextCompilationLogLines, "[engine] opt done"));
                Assert.assertTrue("No [engine] opt queued lines found in context logger output.", hasPrefix(contextCompilationLogLines, "[engine] opt queued"));
                Assert.assertTrue("No [engine] opt unque. lines found in context logger output.", hasPrefix(contextCompilationLogLines, "[engine] opt unque."));
                Assert.assertTrue("No [engine] opt start lines found in context logger output.", hasPrefix(contextCompilationLogLines, "[engine] opt start"));
                Assert.assertTrue("No [engine] opt failed lines found in context logger output.", hasPrefix(contextCompilationLogLines, "[engine] opt failed"));
                Assert.assertTrue("No [engine] opt inval. lines found in context logger output.", hasPrefix(contextCompilationLogLines, "[engine] opt inval."));
                Assert.assertTrue("No [engine] opt deopt lines found in context logger output.", hasPrefix(contextCompilationLogLines, "[engine] opt deopt"));
                Assert.assertEquals("Different number of compilation log lines ([engine] opt done/queued/unque./start/failed/inval./deopt).", contextCompilationLogLines.size(),
                                jfrCompilationLogLines.size());
                for (int i = 0; i < jfrCompilationLogLines.size(); i++) {
                    Assert.assertEquals("Different compilation log line at index " + i,
                                    normalizeForComparison(contextCompilationLogLines.get(i)),
                                    normalizeForComparison(jfrCompilationLogLines.get(i)));
                    assertUtcWithinEval(contextCompilationLogLines.get(i), evalStart, evalEnd);
                    assertUtcWithinEval(jfrCompilationLogLines.get(i), evalStart, evalEnd);
                }
            } catch (IOException | InterruptedException e) {
                throw new AssertionError(e);
            }
        };
        SubprocessTestUtils.newBuilder(JFRCompilationEventsTest.class, test).postfixVmOption("-Djdk.graal.CompilationFailureAction=Silent").onExit(new Consumer<SubprocessTestUtils.Subprocess>() {
            @Override
            public void accept(SubprocessTestUtils.Subprocess subprocess) {
                for (String line : subprocess.output) {
                    System.err.println(line);
                }
            }
        }).run();
    }

    @Test
    public void testSingleRootNodeLanguageDeoptimizesTwiceThenStabilizes() throws IOException {
        TruffleTestAssumptions.assumeOptimizingRuntime();
        try (Context context = Context.newBuilder(DeoptInvalidateFailureLanguage.ID).allowExperimentalOptions(true) //
                        .option("engine.CompileImmediately", "true") //
                        .option("engine.BackgroundCompilation", "false") //
                        .option("engine.TraceCompilationDetails", "true") //
                        .build()) {
            Source source = Source.newBuilder(DeoptInvalidateFailureLanguage.ID, "ignored source text", "DeoptOnceSource").build();
            DeoptInvalidateFailureLanguage.scheduleRecoverablePrepareFailure();
            Assert.assertEquals(42, context.eval(source).asInt());
            Assert.assertEquals(42, context.eval(source).asInt());
            DeoptInvalidateFailureLanguage.invalidateExternalAssumption();
            Assert.assertEquals(42, context.eval(source).asInt());
            DeoptInvalidateFailureLanguage.schedulePermanentPrepareFailure();
            Assert.assertEquals(42, context.eval(source).asInt());
        }
    }

    private static List<String> extractCompilationLogLines(String output) {
        List<String> lines = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (line.startsWith("[engine] opt done") || line.startsWith("[engine] opt queued") || line.startsWith("[engine] opt unque.") || line.startsWith("[engine] opt start") ||
                            line.startsWith("[engine] opt failed") || line.startsWith("[engine] opt inval.") || line.startsWith("[engine] opt deopt")) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static String normalizeForComparison(String line) {
        String noTime = DONE_TIME_PATTERN.matcher(line).replaceFirst("|Time <ignored>|AST");
        noTime = QUEUED_TIME_PATTERN.matcher(noTime).replaceFirst(" Time <ignored>|UTC");
        noTime = FAILED_TIME_PATTERN.matcher(noTime).replaceFirst("|Time <ignored>|Reason:");
        noTime = QUEUE_SIZE_PATTERN.matcher(noTime).replaceFirst("Queue: Size <ignored>");
        return UTC_PATTERN.matcher(noTime).replaceFirst("|UTC <ignored>|Src");
    }

    private static void assumeJfrClassesAvailable() {
        try {
            Class.forName("jdk.jfr.consumer.RecordedEvent", false, ClassLoader.getPlatformClassLoader());
            Class.forName("jdk.jfr.consumer.RecordingStream", false, ClassLoader.getPlatformClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            Assume.assumeTrue("JFR classes are not available: " + e, false);
        }
    }

    private static String formatSource(Object event) {
        RecordedEvent recordedEvent = (RecordedEvent) event;
        String source = recordedEvent.getString("source");
        if ("n/a".equals(source)) {
            return "n/a";
        }
        return source + " " + recordedEvent.getString("sourceHash");
    }

    private static boolean hasPrefix(List<String> lines, String prefix) {
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void assertUtcWithinEval(String line, Instant evalStart, Instant evalEnd) {
        Instant utc = extractUtcInstant(line);
        Instant evalStartMs = evalStart.truncatedTo(ChronoUnit.MILLIS);
        Instant evalEndMs = evalEnd.truncatedTo(ChronoUnit.MILLIS);
        Assert.assertTrue("UTC is before eval start. UTC=" + utc + " evalStart=" + evalStartMs, !utc.isBefore(evalStartMs));
        Assert.assertTrue("UTC is after eval end. UTC=" + utc + " evalEnd=" + evalEndMs, !utc.isAfter(evalEndMs));
    }

    private static Instant extractUtcInstant(String line) {
        Matcher matcher = UTC_PATTERN.matcher(line);
        Assert.assertTrue("Missing UTC field in line: " + line, matcher.find());
        return Instant.from(UTC_FORMATTER.parse(matcher.group(1)));
    }

    private static void normalizeStartQueuedOrder(List<String> lines) {
        for (int i = 0; i < lines.size() - 1; i++) {
            if (lines.get(i).startsWith("[engine] opt start") && lines.get(i + 1).startsWith("[engine] opt queued")) {
                String queued = lines.get(i + 1);
                lines.set(i + 1, lines.get(i));
                lines.set(i, queued);
            }
        }
    }

    @TruffleLanguage.Registration(id = DeoptInvalidateFailureLanguage.ID, name = "DeoptOnceLanguage", version = "1.0")
    public static final class DeoptInvalidateFailureLanguage extends TruffleLanguage<Object> {
        static final String ID = "jfr-deopt-once-language";

        enum PrepareFailureMode {
            NONE,
            RECOVERABLE,
            PERMANENT
        }

        private static volatile Assumption externalAssumption = Truffle.getRuntime().createAssumption("external");
        private static volatile PrepareFailureMode prepareFailureMode = PrepareFailureMode.NONE;

        static void resetState() {
            externalAssumption = Truffle.getRuntime().createAssumption("external");
            prepareFailureMode = PrepareFailureMode.NONE;
        }

        static void invalidateExternalAssumption() {
            Assumption old = externalAssumption;
            old.invalidate();
            externalAssumption = Truffle.getRuntime().createAssumption("external-after-invalidate");
        }

        static void scheduleRecoverablePrepareFailure() {
            prepareFailureMode = PrepareFailureMode.RECOVERABLE;
        }

        static void schedulePermanentPrepareFailure() {
            prepareFailureMode = PrepareFailureMode.PERMANENT;
        }

        static PrepareFailureMode consumePrepareFailureMode() {
            PrepareFailureMode toReturn = prepareFailureMode;
            prepareFailureMode = PrepareFailureMode.NONE;
            return toReturn;
        }

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return new DeoptOnceRootNode(this).getCallTarget();
        }
    }

    static final class DeoptOnceRootNode extends RootNode {
        @CompilerDirectives.CompilationFinal private Assumption activeAssumption;

        DeoptOnceRootNode(TruffleLanguage<?> language) {
            super(language);
            activeAssumption = DeoptInvalidateFailureLanguage.externalAssumption;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            dummyBoundary();
            if (!activeAssumption.isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                activeAssumption = DeoptInvalidateFailureLanguage.externalAssumption;
            }
            return 42;
        }

        @Override
        protected boolean prepareForCompilation(boolean rootCompilation, int compilationTier, boolean lastTier) {
            DeoptInvalidateFailureLanguage.PrepareFailureMode failureMode = DeoptInvalidateFailureLanguage.consumePrepareFailureMode();
            if (failureMode == DeoptInvalidateFailureLanguage.PrepareFailureMode.RECOVERABLE) {
                return false;
            } else if (failureMode == DeoptInvalidateFailureLanguage.PrepareFailureMode.PERMANENT) {
                throw new RuntimeException("Intentionally fail compilation permanently in prepareForCompilation");
            }
            return true;
        }

        @CompilerDirectives.TruffleBoundary
        private static void dummyBoundary() {
        }
    }
}
