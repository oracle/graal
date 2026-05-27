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
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.SubprocessTestUtils;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Tests that long Truffle compilations are accumulated in JFR compiler statistics without losing
 * time while converting nanoseconds to milliseconds.
 */
public class JFRCompilationTimeOverflowTest {
    private static final String COMPILATION_EVENT = "jdk.graal.compiler.truffle.Compilation";
    private static final String COMPILER_STATISTICS_EVENT = "jdk.graal.compiler.truffle.CompilerStatistics";
    private static final long[] COMPILATION_DELAYS_MS = {25, 3_000, 25};
    private static final long LONG_COMPILATION_MIN_MS = 2_500;

    @Test
    public void testCompilerStatisticsCarriesSubMillisecondRemainder() throws Exception {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        Class<?> statisticsClass = getClassByName("com.oracle.truffle.runtime.debug.JFRListener$Statistics");
        Object statistics = getInstance(statisticsClass);
        Method finishCompilation = getMethod(statisticsClass, "finishCompilation", long.class, boolean.class, int.class);
        finishCompilation.invoke(statistics, 600_000L, false, 0);
        Assert.assertEquals(0L, getLongField(statisticsClass, statistics, "totalTime"));
        Assert.assertEquals(600_000L, getLongField(statisticsClass, statistics, "totalTimeNanosRemainder"));

        finishCompilation.invoke(statistics, 600_000L, false, 0);
        Assert.assertEquals(1L, getLongField(statisticsClass, statistics, "totalTime"));
        Assert.assertEquals(200_000L, getLongField(statisticsClass, statistics, "totalTimeNanosRemainder"));

        finishCompilation.invoke(statistics, (long) Integer.MAX_VALUE + 1L, false, 0);
        Assert.assertEquals(2_148L, getLongField(statisticsClass, statistics, "totalTime"));
        Assert.assertEquals(683_648L, getLongField(statisticsClass, statistics, "totalTimeNanosRemainder"));
    }

    @Test
    public void testLongCompilationTimeIsNotLostInCompilerStatistics() throws IOException, InterruptedException {
        TruffleTestAssumptions.assumeOptimizingRuntime();
        TruffleTestAssumptions.assumeWeakEncapsulation();
        assumeJfrClassesAvailable();
        Runnable test = () -> {
            List<Long> compilationEventTimes = Collections.synchronizedList(new ArrayList<>());
            List<StatisticsSnapshot> statisticsEvents = Collections.synchronizedList(new ArrayList<>());
            try (RecordingStream rs = new RecordingStream();
                            Context context = Context.newBuilder().allowExperimentalOptions(true) //
                                            .option("engine.CompileImmediately", "true") //
                                            .option("engine.BackgroundCompilation", "false") //
                                            .option("engine.MultiTier", "false") //
                                            .option("engine.CompilationFailureAction", "Silent") //
                                            .build()) {
                rs.enable(COMPILATION_EVENT).withThreshold(Duration.ZERO);
                rs.enable(COMPILER_STATISTICS_EVENT).withPeriod(Duration.ofMillis(100));
                rs.onEvent(COMPILATION_EVENT, new Consumer<RecordedEvent>() {
                    @Override
                    public void accept(RecordedEvent event) {
                        if (event.getBoolean("success")) {
                            compilationEventTimes.add(Math.round(event.getDuration().toNanos() / 1_000_000d));
                        }
                    }
                });
                rs.onEvent(COMPILER_STATISTICS_EVENT, new Consumer<RecordedEvent>() {
                    @Override
                    public void accept(RecordedEvent event) {
                        statisticsEvents.add(new StatisticsSnapshot(event.getLong("compiledMethods"), event.getLong("totalTime")));
                    }
                });
                rs.startAsync();

                for (int i = 0; i < COMPILATION_DELAYS_MS.length; i++) {
                    Source source = Source.newBuilder(TimingLanguage.ID, Long.toString(COMPILATION_DELAYS_MS[i]), "CompilationTimeOverflow" + i).build();
                    Assert.assertEquals(42, context.eval(source).asInt());
                }
                waitForCompilerStatistics(statisticsEvents, COMPILATION_DELAYS_MS.length);
                rs.stop();
                rs.awaitTermination();

                assertTimesMatch(compilationEventTimes, lastStatistics(statisticsEvents));
            } catch (IOException | InterruptedException e) {
                throw new AssertionError(e);
            }
        };
        SubprocessTestUtils.newBuilder(JFRCompilationTimeOverflowTest.class, test).postfixVmOption("-Djdk.graal.CompilationFailureAction=Silent").onExit(
                        new Consumer<SubprocessTestUtils.Subprocess>() {
                            @Override
                            public void accept(SubprocessTestUtils.Subprocess subprocess) {
                                for (String line : subprocess.output) {
                                    System.err.println(line);
                                }
                            }
                        }).run();
    }

    private static Class<?> getClassByName(String className) throws ClassNotFoundException {
        return Class.forName(className);
    }

    private static Object getInstance(Class<?> declaringClass) throws ReflectiveOperationException {
        Constructor<?> constructor = declaringClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Method getMethod(Class<?> declaringClass, String methodName, Class<?>... parameterTypes) throws ReflectiveOperationException {
        Method method = declaringClass.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static long getLongField(Class<?> declaringClass, Object receiver, String fieldName) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getLong(receiver);
    }

    private static void assertTimesMatch(List<Long> compilationEventTimes, StatisticsSnapshot statistics) {
        Assert.assertEquals("Unexpected number of successful Compilation events.", COMPILATION_DELAYS_MS.length, compilationEventTimes.size());
        Assert.assertEquals("Unexpected number of compiled methods in the latest CompilerStatistics event.", COMPILATION_DELAYS_MS.length, statistics.compiledMethods);

        long maxCompilationTime = Collections.max(compilationEventTimes);
        Assert.assertTrue("Expected one compilation to take at least " + LONG_COMPILATION_MIN_MS + "ms, but the longest Compilation event was " + maxCompilationTime + "ms.",
                        maxCompilationTime >= LONG_COMPILATION_MIN_MS);

        long expectedTotal = sum(COMPILATION_DELAYS_MS);
        long compilationEventTotal = sum(compilationEventTimes);
        Assert.assertTrue("Compilation events total is shorter than the configured prepare delays. actual=" + compilationEventTotal + "ms expectedAtLeast=" + expectedTotal + "ms.",
                        compilationEventTotal >= expectedTotal);
        Assert.assertTrue("CompilerStatistics totalTime is shorter than the configured prepare delays. actual=" + statistics.totalTime + "ms expectedAtLeast=" + expectedTotal + "ms.",
                        statistics.totalTime >= expectedTotal);
    }

    private static void waitForCompilerStatistics(List<StatisticsSnapshot> statisticsEvents, long expectedCompiledMethods) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(100).toNanos();
        while (System.nanoTime() < deadline) {
            StatisticsSnapshot statistics = lastStatisticsOrNull(statisticsEvents);
            if (statistics != null && statistics.compiledMethods >= expectedCompiledMethods) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for CompilerStatistics with at least " + expectedCompiledMethods + " compiled methods. Last event: " + lastStatisticsOrNull(statisticsEvents));
    }

    private static StatisticsSnapshot lastStatistics(List<StatisticsSnapshot> statisticsEvents) {
        StatisticsSnapshot statistics = lastStatisticsOrNull(statisticsEvents);
        Assert.assertNotNull("No CompilerStatistics events were recorded.", statistics);
        return statistics;
    }

    private static StatisticsSnapshot lastStatisticsOrNull(List<StatisticsSnapshot> statisticsEvents) {
        synchronized (statisticsEvents) {
            return statisticsEvents.isEmpty() ? null : statisticsEvents.get(statisticsEvents.size() - 1);
        }
    }

    private static long sum(long[] values) {
        long sum = 0;
        for (long value : values) {
            sum += value;
        }
        return sum;
    }

    private static long sum(List<Long> values) {
        long sum = 0;
        for (long value : values) {
            sum += value;
        }
        return sum;
    }

    private static void assumeJfrClassesAvailable() {
        try {
            Class.forName("jdk.jfr.consumer.RecordedEvent", false, ClassLoader.getPlatformClassLoader());
            Class.forName("jdk.jfr.consumer.RecordingStream", false, ClassLoader.getPlatformClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            Assume.assumeTrue("JFR classes are not available: " + e, false);
        }
    }

    private record StatisticsSnapshot(long compiledMethods, long totalTime) {
    }

    @TruffleLanguage.Registration(id = TimingLanguage.ID, name = "JFRCompilationTimeOverflowLanguage", version = "1.0")
    public static final class TimingLanguage extends TruffleLanguage<Object> {
        static final String ID = "jfr-compilation-time-overflow-language";

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            long prepareDelayMillis = Long.parseLong(request.getSource().getCharacters().toString());
            return new TimingRootNode(this, prepareDelayMillis).getCallTarget();
        }
    }

    static final class TimingRootNode extends RootNode {
        private static final AtomicInteger NEXT_ID = new AtomicInteger();

        private final String name;
        private final long prepareDelayMillis;

        TimingRootNode(TruffleLanguage<?> language, long prepareDelayMillis) {
            super(language);
            this.name = "jfrTimingRoot" + NEXT_ID.incrementAndGet();
            this.prepareDelayMillis = prepareDelayMillis;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return getName();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return 42;
        }

        @Override
        protected boolean prepareForCompilation(boolean rootCompilation, int compilationTier, boolean lastTier) {
            if (rootCompilation && prepareDelayMillis > 0) {
                try {
                    Thread.sleep(prepareDelayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
            return true;
        }

    }
}
