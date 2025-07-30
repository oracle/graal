/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.benchmark;

import org.graalvm.nativebridge.IsolateCreateException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Main {

    public static void main(String[] args) {
        Path processIsolateLauncher = Path.of(args[0]).toAbsolutePath();
        Path isolateLibrary = Path.of(args[1]).toAbsolutePath();
        if (!Files.isRegularFile(processIsolateLauncher)) {
            throw new IllegalArgumentException("Process isolate launcher must be a file.");
        }
        if (!Files.isRegularFile(isolateLibrary)) {
            throw new IllegalArgumentException("Isolate library must be a file.");
        }
        List<Mode> modes = Arrays.stream(Arrays.copyOfRange(args, 2, args.length)).map(Mode::from).toList();
        if (modes.isEmpty()) {
            modes = List.of(Mode.values());
        }
        Runner.Config config = new Runner.Config(isolateLibrary, processIsolateLauncher);

        for (Mode mode : modes) {
            try (Runner runner = mode.createRunner(config)) {
                Service service = runner.getService();
                int iterCount = 1_000_000;

                System.out.println("[BENCH] Running " + iterCount + " `noop` iterations using " + mode.option);
                long st = System.nanoTime();
                for (int i = 0; i < iterCount; i++) {
                    service.noop();
                }
                long et = System.nanoTime();
                et = TimeUnit.NANOSECONDS.toMillis(et - st);
                long iterationsPerMs = iterCount / et;
                System.out.println("[BENCH] " + iterationsPerMs + " operations per ms.");

                System.out.println("[BENCH] Running " + iterCount + " `primitives` iterations using " + mode.option);
                st = System.nanoTime();
                for (int i = 0; i < iterCount; i++) {
                    service.primitives(40, 2);
                }
                et = System.nanoTime();
                et = TimeUnit.NANOSECONDS.toMillis(et - st);
                iterationsPerMs = iterCount / et;
                System.out.println("[BENCH] " + iterationsPerMs + " operations per ms.");

                System.out.println("[BENCH] Running " + iterCount + " `references` iterations using " + mode.option);
                Service localService = BenchmarkFactory.createNonIsolated();
                st = System.nanoTime();
                for (int i = 0; i < iterCount; i++) {
                    service.references(service, localService);
                }
                et = System.nanoTime();
                et = TimeUnit.NANOSECONDS.toMillis(et - st);
                iterationsPerMs = iterCount / et;
                System.out.println("[BENCH] " + iterationsPerMs + " operations per ms.");

                System.out.println("[BENCH] Running " + iterCount + " `marshalled` iterations using " + mode.option);
                Point point1 = new Point(0, 0, 1);
                Point point2 = new Point(0, 1, 0);
                st = System.nanoTime();
                for (int i = 0; i < iterCount; i++) {
                    service.marshalled(point1, point2);
                }
                et = System.nanoTime();
                et = TimeUnit.NANOSECONDS.toMillis(et - st);
                iterationsPerMs = iterCount / et;
                System.out.println("[BENCH] " + iterationsPerMs + " operations per ms.");

                System.out.println("[BENCH] Running " + iterCount + " `marshalled` list iterations using " + mode.option);
                List<Point> points1 = new ArrayList<>();
                List<Point> points2 = new ArrayList<>();
                for (int i = 0; i < 100; i++) {
                    Point point = new Point(1, 0, 0);
                    points1.add(point);
                    points2.add(point);
                }
                st = System.nanoTime();
                for (int i = 0; i < iterCount; i++) {
                    service.marshalled2(points1, points2);
                }
                et = System.nanoTime();
                et = TimeUnit.NANOSECONDS.toMillis(et - st);
                iterationsPerMs = iterCount / et;
                System.out.println("[BENCH] " + iterationsPerMs + " operations per ms.");
            }
        }
    }

    private enum Mode {
        NO_ISOLATION("no-isolation", (c) -> {
            try {
                return new NoIsolationRunner(c);
            } catch (IsolateCreateException e) {
                throw sthrow(e, RuntimeException.class);
            }
        }),
        INTERNAL_ISOLATION("internal-isolation", (c) -> {
            try {
                return new InternalIsolationRunner(c);
            } catch (IsolateCreateException e) {
                throw sthrow(e, RuntimeException.class);
            }
        }),
        EXTERNAL_ISOLATION("external-isolation", (c) -> {
            try {
                return new ExternalIsolationRunner(c);
            } catch (IsolateCreateException e) {
                throw sthrow(e, RuntimeException.class);
            }
        });

        private static final Map<String, Mode> modeByName;
        static {
            modeByName = new HashMap<>();
            for (Mode mode : Mode.values()) {
                modeByName.put(mode.option, mode);
            }
        }

        private final String option;
        private final Function<Runner.Config, Runner> factory;

        Mode(String option, Function<Runner.Config, Runner> factory) {
            this.option = option;
            this.factory = factory;
        }

        Runner createRunner(Runner.Config config) {
            return factory.apply(config);
        }

        static Mode from(String option) {
            Mode res = modeByName.get(option);
            if (res == null) {
                throw new IllegalArgumentException("Unsupported mode " + option + ", supported modes are " +
                                Arrays.stream(values()).map((m) -> m.option).collect(Collectors.joining(", ")));
            }
            return res;
        }
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <T extends Throwable> RuntimeException sthrow(Throwable t, Class<T> clz) throws T {
        throw (T) t;
    }
}
