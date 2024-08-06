/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class GCStressTest {

    @Before
    public void before() {
        Assume.assumeTrue("stress tests are being ignored", System.getProperty("gcstress") != null);
    }

    private static void runTest(int count, Runnable target) throws InterruptedException {
        Thread[] threads = new Thread[count];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(target);
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        System.out.println("Done");
    }

    public static void main(String[] args) throws InterruptedException {
        runTest(16, new DoList());
        runTest(16, new DoCopyOf());

    }

    static class DoList implements Runnable {

        static long globalTotal = 0;

        @Override
        public void run() {
            for (int k = 0; k < 800000; k++) {
                LinkedList<Integer> list = new LinkedList<>();
                for (int i = 0; i < 1000; i++) {
                    list.add(i);
                }
                int total = 0;
                Collections.reverse(list);
                for (Integer v : list) {
                    total += v;
                }
                globalTotal += total;
            }
        }
    }

    @Test
    public void list() throws InterruptedException {
        runTest(16, new DoList());
    }

    static class DoCopyOf implements Runnable {

        static long globalTotal = 0;

        @Override
        public void run() {
            for (int k = 0; k < 8000; k++) {
                Integer[] list = new Integer[0];
                for (int i = 0; i < 1000; i++) {
                    list = Arrays.copyOf(list, list.length + 1);
                    list[i] = i;
                }
                int total = 0;
                Arrays.sort(list, new Comparator<Integer>() {
                    @Override
                    public int compare(Integer x, Integer y) {
                        return (x < y) ? -1 : ((x.equals(y)) ? 1 : 0);
                    }
                });

                for (Integer v : list) {
                    total += v;
                }
                globalTotal += total;
            }
        }
    }

    @Test
    public void copyOf() throws InterruptedException {
        runTest(16, new DoCopyOf());
    }

    static class DoConcurrent implements Runnable {
        ConcurrentHashMap<Integer, String> map = new ConcurrentHashMap<>();

        @Override
        public void run() {
            for (int k = 0; k < 8000; k++) {
                for (int i = 0; i < 1000; i++) {
                    String s = map.get(i);
                    if (s == null) {
                        s = "";
                    }
                    s = s + i;
                    s = s.substring(s.length() - Math.min(s.length(), 10));
                    map.put(i, s);
                }
            }
        }
    }

    @Test
    public void concurrentMap() throws InterruptedException {
        runTest(16, new DoConcurrent());
    }
}
