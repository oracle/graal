/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.compiler.tests;

import java.util.*;

import org.junit.*;


public class EndlessRecompilationTest {
    private static final int COMPILATION_ITERATIONS = 5 * 1000;
    private static final int WORK_ITERATIONS = 1000 * 1000 * 1000;
    private static final int COMPILATION_WAIT_TIME_MS = 2000;
    private static final int MAX_WORK_TIME_MS = 5 * 1000;

    @Test
    public void test() {
        int[] data = new int[] {1};
        Worker[] workers = new Worker[] {new A(), new B()};
        Worker violation = new C();

        sleep();

        System.out.println("Compiling all Worker.doWork() methods");
        for (int i = 0; i < 2 * COMPILATION_ITERATIONS; i++) {
            workers[i % workers.length].doWork(data);
        }
        sleep();

        System.out.println("Compiling delegateWork() with all path but without any inlining because SmallCompiledCodeSize prevents inlining");
        for (int i = 0; i < COMPILATION_ITERATIONS; i++) {
            delegateWork(workers[i % workers.length], data);
        }
        sleep();

        System.out.println("Warmup for delegateWorkFurther()");
        for (int i = 0; i < COMPILATION_ITERATIONS / 2; i++) {
            delegateWorkFurther(workers[i % workers.length], data);
        }
        sleep();

        System.out.println("Deoptimize all compiled Worker.doWork() methods");
        for (int i = 0; i < workers.length; i++) {
            workers[i].doWork(null);
        }
        sleep();

        System.out.println("Compile delegateWorkFurther() and do a polymorphic inlining because SmallCompiledCodeSize no longer prevents inlining");
        for (int i = 0; i < COMPILATION_ITERATIONS / 2; i++) {
            delegateWorkFurther(workers[i % workers.length], data);
        }
        sleep();

        System.out.println("Violating polymorphic inlining in delegateWorkFurther() and deoptimizing to method entry of delegateWorkFurther(). " +
                           "Interpreter will reexecute invocation of delegateWork(), which does not contain the inlining and won't deoptimize");
        long start = System.currentTimeMillis();
        for (int i = 0; i < WORK_ITERATIONS; i++) {
            delegateWorkFurther(violation, data);
        }
        long requiredTime = System.currentTimeMillis() - start;
        System.out.println(requiredTime);
    }

    private static int delegateWorkFurther(Worker w, int[] data) {
        return delegateWork(w, data);
    }

    private static int delegateWork(Worker w, int[] data) {
        return w.doWork(data);
    }

    private static void sleep() {
        try {
            Thread.sleep(COMPILATION_WAIT_TIME_MS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private interface Worker {
        int doWork(int[] data);
    }

    private static class A implements Worker {
        public int doWork(int[] data) {
            if (data == null) {
                return 0;
            } else {
                int result = 0;
                result += Arrays.binarySearch(data, 0);
                result += Arrays.binarySearch(data, 0);
                result += Arrays.binarySearch(data, 0);
                result += Arrays.binarySearch(data, 0);
                result += Arrays.binarySearch(data, 0);
                result += Arrays.binarySearch(data, 0);
                result += Arrays.binarySearch(data, 0);
                result += Arrays.binarySearch(data, 0);
                result += Arrays.binarySearch(data, 0);
                result += Arrays.binarySearch(data, 0);
                return result;
            }
        }
    }

    private static class B implements Worker {
        public int doWork(int[] data) {
            if (data == null) {
                return 0;
            } else {
                int result = 0;
                result += Arrays.binarySearch(data, 0);
                result += Arrays.binarySearch(data, 0);
                result += Arrays.binarySearch(data, 0);
                result += Arrays.binarySearch(data, 0);
                result += Arrays.binarySearch(data, 0);
                result += Arrays.binarySearch(data, 0);
                result += Arrays.binarySearch(data, 0);
                result += Arrays.binarySearch(data, 0);
                result += Arrays.binarySearch(data, 0);
                result += Arrays.binarySearch(data, 0);
                return result;
            }
        }
    }

    private static class C implements Worker {
        public int doWork(int[] data) {
            return 0;
        }
    }
}
