/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package pgocounts;

/**
 * Runs each kind of code that a {@code --pgo-instrument} image profiles a known number of times,
 * counts the runs itself and prints the counts as {@code count <name> <value>} lines. The
 * {@code pgo profile counts} gate task builds this program as an instrumented image, runs it once
 * and checks that the profile holds exactly these counts. Only the stack samples of the spins
 * depend on timing, so for those it checks a minimum.
 * <p>
 * Each profiled construct is alone in its own method, so the check finds it by method name. Each
 * counter sits on the path it counts, so it counts what the profile must record.
 */
public final class ProfileCountsWorkload {

    interface Shape {
        int sides();
    }

    static final class Circle implements Shape {
        @Override
        public int sides() {
            return 0;
        }
    }

    static final class Square implements Shape {
        @Override
        public int sides() {
            return 4;
        }
    }

    static final class Triangle implements Shape {
        @Override
        public int sides() {
            return 3;
        }
    }

    static final class FirstLock {
    }

    static final class SecondLock {
    }

    private static final Object FIRST_LOCK = new FirstLock();
    private static final Object SECOND_LOCK = new SecondLock();

    /** How long each sampled spin runs: long enough for many samples one millisecond apart. */
    private static final long SPIN_MILLIS = 200;

    static int thenRuns;
    static int elseRuns;
    static int loopCalls;
    static int loopBodyRuns;
    static int case0Runs;
    static int case1Runs;
    static int case2Runs;
    static int defaultRuns;
    static int firstSiteCalls;
    static int secondSiteCalls;
    static int calleeRuns;
    static int firstCallerCircles;
    static int firstCallerSquares;
    static int secondCallerCircles;
    static int secondCallerSquares;
    static int secondCallerTriangles;
    static int firstLocks;
    static int secondLocks;
    static int threadThenRuns;
    static int threadElseRuns;
    static long threadSpin;
    static int collections;

    private ProfileCountsWorkload() {
    }

    /** One branch. */
    static void branch(int value) {
        if (value % 3 == 0) {
            thenRuns++;
        } else {
            elseRuns++;
        }
    }

    /** One loop: its condition holds {@code trips} times and then fails once. */
    static void loop(int trips) {
        loopCalls++;
        for (int i = 0; i < trips; i++) {
            loopBodyRuns++;
        }
    }

    /** One switch with three cases and a default. */
    static void choose(int key) {
        switch (key) {
            case 0:
                case0Runs++;
                break;
            case 1:
                case1Runs++;
                break;
            case 2:
                case2Runs++;
                break;
            default:
                defaultRuns++;
                break;
        }
    }

    /**
     * The callee of the counted call edges. Its two calls of {@link Integer#toString(int)} keep it
     * from being a trivial method: Native Image inlines those before any instrumentation runs (in a
     * {@code --pgo} build too, so their calls need no profile).
     */
    static int callee(int value) {
        calleeRuns++;
        return Integer.toString(value).length() + Integer.toString(-value).length();
    }

    /** Two call sites of {@link #callee(int)}. */
    static int caller(int first, int second) {
        int total = 0;
        for (int i = 0; i < first; i++) {
            firstSiteCalls++;
            total += callee(i);
        }
        for (int i = 0; i < second; i++) {
            secondSiteCalls++;
            total += callee(i);
        }
        return total;
    }

    /** One interface call, whose receivers are counted by type and by calling context. */
    static int countSides(Shape[] shapes) {
        int total = 0;
        for (Shape shape : shapes) {
            total += shape.sides();
        }
        return total;
    }

    /** Calls {@link #countSides} with 70 circles and 30 squares. */
    static int sidesOfFirstCaller() {
        Shape[] shapes = new Shape[100];
        for (int i = 0; i < shapes.length; i++) {
            if (i % 10 < 7) {
                shapes[i] = new Circle();
                firstCallerCircles++;
            } else {
                shapes[i] = new Square();
                firstCallerSquares++;
            }
        }
        return countSides(shapes);
    }

    /** Calls {@link #countSides} with 10 circles, 20 squares and 30 triangles. */
    static int sidesOfSecondCaller() {
        Shape[] shapes = new Shape[60];
        for (int i = 0; i < shapes.length; i++) {
            if (i < 10) {
                shapes[i] = new Circle();
                secondCallerCircles++;
            } else if (i < 30) {
                shapes[i] = new Square();
                secondCallerSquares++;
            } else {
                shapes[i] = new Triangle();
                secondCallerTriangles++;
            }
        }
        return countSides(shapes);
    }

    /** Monitor enters, whose locked objects are counted by type. */
    static void lockFirst() {
        synchronized (FIRST_LOCK) {
            firstLocks++;
        }
    }

    static void lockSecond() {
        synchronized (SECOND_LOCK) {
            secondLocks++;
        }
    }

    /** The bottom of the sampled call chain {@code sampleOuter -> sampleMiddle -> sampleLeaf}. */
    static long sampleOuter() {
        return sampleMiddle() + 1;
    }

    static long sampleMiddle() {
        return sampleLeaf() + 1;
    }

    /**
     * Spins for {@link #SPIN_MILLIS}, so that the stack sampler sees this call chain. The sampler
     * runs only at safepoint checks, and the compiler drops the checks from loops that make a call
     * on every trip, so this loop calls {@link System#nanoTime()} only every 65,536 trips. It counts
     * with a long, so that its checks do not depend on the sampling image keeping those of loops
     * over an int range, which {@link #spinIntLoop} tests.
     */
    static long sampleLeaf() {
        long end = System.nanoTime() + SPIN_MILLIS * 1_000_000;
        long value = 0;
        for (long i = 0;; i++) {
            value = value * 31 + i;
            if ((i & 0xFFFF) == 0 && System.nanoTime() >= end) {
                return value;
            }
        }
    }

    /**
     * Spins for {@link #SPIN_MILLIS} in a loop over an int range. The compiler removes the safepoint
     * polls of such loops, so a sampling image must keep them for the sampler to see this method.
     */
    static long spinIntLoop() {
        long end = System.nanoTime() + SPIN_MILLIS * 1_000_000;
        long value = 0;
        do {
            for (int i = 0; i < 100_000; i++) {
                value = value * 31 + i;
            }
        } while (System.nanoTime() < end);
        return value;
    }

    /** One branch that only the second thread runs. */
    static void threadBranch(int value) {
        if (value % 4 == 0) {
            threadThenRuns++;
        } else {
            threadElseRuns++;
        }
    }

    /** The work of the second thread: a branch and a sampled spin. */
    static void threadWork() {
        for (int value = 0; value < 4000; value++) {
            threadBranch(value);
        }
        threadSpin = sampleOuter();
    }

    /** Allocates and runs full collections, which must add nothing to the profile. */
    static void collectGarbage() {
        for (int round = 0; round < 3; round++) {
            Object[] garbage = new Object[10_000];
            for (int i = 0; i < garbage.length; i++) {
                garbage[i] = new int[16];
            }
            System.gc();
            collections++;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        for (int value = 0; value < 3000; value++) {
            branch(value);
        }

        loop(1000);
        loop(234);
        loop(0);

        for (int i = 0; i < 1000; i++) {
            choose(i < 100 ? 0 : i < 300 ? 1 : i < 600 ? 2 : 7);
        }

        int digits = caller(30, 70);

        int sides = sidesOfFirstCaller() + sidesOfSecondCaller();

        for (int i = 0; i < 12; i++) {
            lockFirst();
        }
        for (int i = 0; i < 3; i++) {
            lockSecond();
        }

        Thread thread = new Thread(ProfileCountsWorkload::threadWork, "profile-counts-thread");
        thread.start();
        thread.join();
        long spin = sampleOuter();
        long intLoopSpin = spinIntLoop();

        collectGarbage();

        System.out.println("count thenRuns " + thenRuns);
        System.out.println("count elseRuns " + elseRuns);
        System.out.println("count loopCalls " + loopCalls);
        System.out.println("count loopBodyRuns " + loopBodyRuns);
        System.out.println("count case0Runs " + case0Runs);
        System.out.println("count case1Runs " + case1Runs);
        System.out.println("count case2Runs " + case2Runs);
        System.out.println("count defaultRuns " + defaultRuns);
        System.out.println("count firstSiteCalls " + firstSiteCalls);
        System.out.println("count secondSiteCalls " + secondSiteCalls);
        System.out.println("count calleeRuns " + calleeRuns);
        System.out.println("count firstCallerCircles " + firstCallerCircles);
        System.out.println("count firstCallerSquares " + firstCallerSquares);
        System.out.println("count secondCallerCircles " + secondCallerCircles);
        System.out.println("count secondCallerSquares " + secondCallerSquares);
        System.out.println("count secondCallerTriangles " + secondCallerTriangles);
        System.out.println("count firstLocks " + firstLocks);
        System.out.println("count secondLocks " + secondLocks);
        System.out.println("count threadThenRuns " + threadThenRuns);
        System.out.println("count threadElseRuns " + threadElseRuns);
        System.out.println("count spinMillis " + SPIN_MILLIS);
        System.out.println("count collections " + collections);
        System.out.println("checksums " + digits + " " + sides + " " + spin + " " + threadSpin + " " + intLoopSpin);
    }
}
