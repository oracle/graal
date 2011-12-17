/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler;

import java.util.*;

import com.oracle.max.criutils.*;

/**
 * This class contains timers that record the amount of time spent in various parts of the compiler. It builds a
 * hierarchical and a flat representation of the timing. In order to reliably create the hierarchy the following code
 * pattern should be used:
 *
 * <pre>
 * GraalTimers.startScope("name");
 * try {
 *      ...
 * } finally {
 *      GraalTimers.endScope();
 * }
 * </pre>
 */
public final class GraalTimers {

    private static class TimingScope {

        public final TimingScope parent;
        public final int level;
        public long time;
        public long count;
        public long subTime;
        public long startTime;

        public final ArrayList<String> subNames = new ArrayList<String>();
        public final ArrayList<TimingScope> subScopes = new ArrayList<TimingScope>();

        public TimingScope(TimingScope parent) {
            this.parent = parent;
            this.level = parent == null ? 0 : parent.level + 1;
        }

        private static final String PREFIX = "       |       |       |       |       |       |       |       |       |       +";

        public static void treeIndent(int i) {
            assert i < PREFIX.length() / 8;
            TTY.print(PREFIX.substring(PREFIX.length() - i * 8));
        }

        private void printScope(int level) {
            TTY.println("%3.0f%% %6.2fs %5d", time * 100.0 / parent.time, time / 1000000000.0, count);
            if (!subNames.isEmpty() && (time - subTime) > 0) {
                treeIndent(level + 1);
                TTY.print("%-40s", "self:");
                TTY.println("%3.0f%% %6.2fs %5d", (time - subTime) * 100.0 / time, (time - subTime) / 1000000000.0, count);
            }
            printSub(level + 1);
        }

        public void printSub(int level) {
            for (int i = 0; i < subNames.size(); i++) {
                String name = subNames.get(i);
                TimingScope scope = subScopes.get(i);
                treeIndent(level);
                TTY.print("%-40s", name + ":");
                scope.printScope(level);
            }
        }

        public long accumulateSub(Map<String, Long> times, Map<String, Long> counts) {
            long result = 0;
            for (int i = 0; i < subNames.size(); i++) {
                String name = subNames.get(i);
                TimingScope scope = subScopes.get(i);
                long totalTime = times.containsKey(name) ? times.get(name) : 0;
                long totalCount = counts.containsKey(name) ? counts.get(name) : 0;
                long myTime = scope.time - scope.subTime;
                times.put(name, totalTime + myTime);
                counts.put(name, totalCount + scope.count);
                result += myTime + scope.accumulateSub(times, counts);
            }
            return result;
        }
    }

    private final TimingScope rootScope = new TimingScope(null);

    private ThreadLocal<TimingScope> currentScope = new ThreadLocal<TimingScope>() {

        @Override
        protected TimingScope initialValue() {
            TimingScope scope = new TimingScope(rootScope);
            rootScope.subNames.add(Thread.currentThread().getName());
            rootScope.subScopes.add(scope);
            return scope;
        }
    };

    private ThreadLocal<Integer> currentLevel = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    public void startScope(Class< ? > clazz) {
        if (GraalOptions.Time) {
            startScope(clazz.getSimpleName());
        } else {
            currentLevel.set(currentLevel.get() + 1);
        }
    }

    public int currentLevel() {
        if (GraalOptions.Time) {
            return currentScope.get().level;
        } else {
            return currentLevel.get();
        }
    }

    public void startScope(String name) {
        if (GraalOptions.Time) {
            TimingScope current = currentScope.get();
            int index = current.subNames.indexOf(name);
            TimingScope sub;
            if (index == -1) {
                sub = new TimingScope(current);
                current.subNames.add(name);
                current.subScopes.add(sub);
            } else {
                sub = current.subScopes.get(index);
            }
            currentScope.set(sub);
            sub.startTime = System.nanoTime();
            sub.count++;
        } else {
            currentLevel.set(currentLevel.get() + 1);
        }
    }

    public void endScope() {
        if (GraalOptions.Time) {
            TimingScope current = currentScope.get();
            long time = System.nanoTime() - current.startTime;
            current.time += time;
            current.parent.subTime += time;
            currentScope.set(current.parent);
        } else {
            currentLevel.set(currentLevel.get() - 1);
        }
    }

    public void print() {
        if (GraalOptions.Time) {
            rootScope.time = 0;
            for (TimingScope scope : rootScope.subScopes) {
                scope.time = scope.subTime;
                rootScope.time += scope.subTime;
            }

            TTY.println("===============================");
            TTY.println("Total Compilation Time: %6.2fs", rootScope.time / 1000000000.0);
            TTY.println();

            rootScope.printSub(0);

            TreeMap<String, Long> times = new TreeMap<String, Long>();
            TreeMap<String, Long> counts = new TreeMap<String, Long>();
            long total = rootScope.accumulateSub(times, counts);

            TTY.println();
            for (String name : times.keySet()) {
                if (times.get(name) > 0) {
                    TTY.println("%-30s: %7.4f s (%5.2f%%) %5d", name, times.get(name) / 1000000000.0, times.get(name) * 100.0 / total, counts.get(name));
                }
            }
        }
    }
}
