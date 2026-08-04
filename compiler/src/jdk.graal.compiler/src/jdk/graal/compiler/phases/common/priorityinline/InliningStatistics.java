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
package jdk.graal.compiler.phases.common.priorityinline;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.Integer.MIN_VALUE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import jdk.graal.compiler.debug.TTY;

public class InliningStatistics {
    private static class Entry {
        private static final String SYMBOL_GREAT = "\uD83D\uDE0E";
        private static final String SYMBOL_PROBLEMATIC = "\uD83D\uDE21";
        private static final String SYMBOL_OK = "\uD83D\uDE10";

        String methodName;
        long duration;
        int callTreeSize;
        int callTreeIrSize;
        int rootIrSize;
        int parsedIrSize;
        int invokesLeft;
        long extraAnalysisDuration;
        long optDuration;
        int peCount;
        int rounds;
        int numMethodsInlined;
        int extraMetric;

        Entry(String methodName, long duration, int callTreeSize, int callTreeIrSize, int rootIrSize, int parsedIrSize, long optDuration, int peCount, int invokesLeft, int rounds,
                        long extraAnalysisDuration, int numMethodsInlined, int extraMetric) {
            this.methodName = methodName;
            this.duration = duration;
            this.callTreeSize = callTreeSize;
            this.callTreeIrSize = callTreeIrSize;
            this.rootIrSize = rootIrSize;
            this.parsedIrSize = parsedIrSize;
            this.optDuration = optDuration;
            this.peCount = peCount;
            this.invokesLeft = invokesLeft;
            this.rounds = rounds;
            this.extraAnalysisDuration = extraAnalysisDuration;
            this.numMethodsInlined = numMethodsInlined;
            this.extraMetric = extraMetric;
        }

        void min(Entry that) {
            duration = Math.min(duration, that.duration);
            callTreeSize = Math.min(callTreeSize, that.callTreeSize);
            callTreeIrSize = Math.min(callTreeIrSize, that.callTreeIrSize);
            rootIrSize = Math.min(rootIrSize, that.rootIrSize);
            parsedIrSize = Math.min(parsedIrSize, that.parsedIrSize);
            invokesLeft = Math.min(invokesLeft, that.invokesLeft);
            optDuration = Math.min(optDuration, that.optDuration);
            extraAnalysisDuration = Math.min(extraAnalysisDuration, that.extraAnalysisDuration);
            extraMetric = Math.min(extraMetric, that.extraMetric);
            peCount = Math.min(peCount, that.peCount);
            rounds = Math.min(rounds, that.rounds);
            numMethodsInlined = Math.min(numMethodsInlined, that.numMethodsInlined);
        }

        void max(Entry that) {
            duration = Math.max(duration, that.duration);
            callTreeSize = Math.max(callTreeSize, that.callTreeSize);
            callTreeIrSize = Math.max(callTreeIrSize, that.callTreeIrSize);
            rootIrSize = Math.max(rootIrSize, that.rootIrSize);
            parsedIrSize = Math.max(parsedIrSize, that.parsedIrSize);
            invokesLeft = Math.max(invokesLeft, that.invokesLeft);
            optDuration = Math.max(optDuration, that.optDuration);
            extraAnalysisDuration = Math.max(extraAnalysisDuration, that.extraAnalysisDuration);
            extraMetric = Math.max(extraMetric, that.extraMetric);
            peCount = Math.max(peCount, that.peCount);
            rounds = Math.max(rounds, that.rounds);
            numMethodsInlined = Math.max(numMethodsInlined, that.numMethodsInlined);

        }

        void add(Entry that) {
            duration = duration + that.duration;
            callTreeSize = callTreeSize + that.callTreeSize;
            callTreeIrSize = callTreeIrSize + that.callTreeIrSize;
            rootIrSize = rootIrSize + that.rootIrSize;
            parsedIrSize = parsedIrSize + that.parsedIrSize;
            invokesLeft = invokesLeft + that.invokesLeft;
            optDuration = optDuration + that.optDuration;
            extraAnalysisDuration = extraAnalysisDuration + that.extraAnalysisDuration;
            extraMetric = extraMetric + that.extraMetric;
            peCount = peCount + that.peCount;
            rounds = rounds + that.rounds;
            numMethodsInlined = numMethodsInlined + that.numMethodsInlined;

        }

        public void divide(int n) {
            duration = duration / n;
            callTreeSize = callTreeSize / n;
            callTreeIrSize = callTreeIrSize / n;
            rootIrSize = rootIrSize / n;
            parsedIrSize = parsedIrSize / n;
            invokesLeft = invokesLeft / n;
            optDuration = optDuration / n;
            extraAnalysisDuration = extraAnalysisDuration / n;
            extraMetric = extraMetric / n;
            peCount = peCount / n;
            rounds = rounds / n;
            numMethodsInlined = numMethodsInlined / n;

        }

        String estimate() {
            if (invokesLeft == 0) {
                return SYMBOL_GREAT;
            } else if (invokesLeft > 80) {
                return SYMBOL_PROBLEMATIC;
            } else if (callTreeIrSize > 10 * rootIrSize) {
                return SYMBOL_PROBLEMATIC;
            } else {
                return SYMBOL_OK;
            }
        }
    }

    private static final Comparator<Entry> DURATION_COMPARATOR = new Comparator<>() {
        @Override
        public int compare(Entry x, Entry y) {
            return Long.compare(x.duration, y.duration);
        }
    };
    private static final int NUMBER_WIDTH = 9;
    private static final int SMALL_NUMBER_WIDTH = 5;
    private static final int NAME_WIDTH = 60;
    private static final int TABLE_WIDTH = 187;
    private static final int DURATION_CRITICAL = 2000;
    private static final int DURATION_LARGE = 800;
    private static final int DURATION_MEDIUM = 200;
    private static final int TREE_SIZE_CRITICAL = 1200;
    private static final int TREE_SIZE_LARGE = 400;
    private static final int TREE_SIZE_MEDIUM = 100;
    private static final int IR_CRITICAL = 35000;
    private static final int IR_LARGE = 12000;
    private static final int IR_MEDIUM = 2500;
    private static final int PE_CRITICAL = 80;
    private static final int PE_LARGE = 35;
    private static final int PE_MEDIUM = 15;
    private static final String BLUE = "\u001b[34m";
    private static final String MAGENTA = "\u001b[35m";
    private static final String RED = "\u001b[31m";
    private static final String YELLOW = "\u001b[33m";
    private static final String GREEN = "\u001b[32m";
    private static final String RESET = "\u001b[0m";
    private static final String SYMBOL_MAGNIFIER = "\uD83D\uDD0D";
    private static final String SYMBOL_TREE = "\uD83C\uDF32";
    private static final String SYMBOL_EXPLORED = "\uD83C\uDF4F";
    private static final String SYMBOL_INLINED = "\uD83C\uDF4E";
    private static final String SYMBOL_INVOKE = "\uD83C\uDF00";
    private static final String SYMBOL_ESCAPE = "\uD83D\uDEAA";
    private static final String FORMAT_STRING = "%s | %s | %s " + SYMBOL_TREE + " | %s " + SYMBOL_EXPLORED + " | %s " + SYMBOL_INLINED +
                    " | %s | %s | %s " + SYMBOL_ESCAPE + " | %s " + SYMBOL_INVOKE + " | %s | %s | %s | %s";

    private ArrayList<Entry> entries = new ArrayList<>();

    public void enter(String name, long duration, int callTreeSize, int callTreeIrSize, int rootIrSize, int parsedIrSize, long optDuration, int peCount, int invokesLeft, int rounds,
                    long extraAnalysisDuration, int numMethodsInlined, int extraMetric) {
        synchronized (this) {
            entries.add(new Entry(name, duration, callTreeSize, callTreeIrSize, rootIrSize, parsedIrSize, optDuration, peCount, invokesLeft, rounds, extraAnalysisDuration, numMethodsInlined,
                            extraMetric));
        }
    }

    public void logLast() {
        synchronized (this) {
            int size = entries.size();
            Entry entry = entries.get(size - 1);
            if (size == 1) {
                printHeader("method name");
            }
            printLine(entry);
        }
    }

    public String pad(String text, int width) {
        if (text.length() > width) {
            return "... " + text.substring(text.length() - width + 4);
        } else {
            return String.join("", Collections.nCopies(width - text.length(), " ")) + text;
        }
    }

    public void printAll() {
        if (entries.size() > 0) {
            printHeader("method name");
            synchronized (this) {
                printSortedStatistics();
                printAggregateStatistics();
            }
        }
    }

    private void printSortedStatistics() {
        final Entry[] processedEntries = this.entries.toArray(new Entry[this.entries.size()]);
        Arrays.sort(processedEntries, DURATION_COMPARATOR);
        for (Entry entry : processedEntries) {
            printLine(entry);
        }
    }

    private void printAggregateStatistics() {
        Entry min = new Entry("minimum", Long.MAX_VALUE, MAX_VALUE, MAX_VALUE, MAX_VALUE, MAX_VALUE, Long.MAX_VALUE, MAX_VALUE, MAX_VALUE, MAX_VALUE, Long.MAX_VALUE, MIN_VALUE, MAX_VALUE);
        Entry max = new Entry("maximum", Long.MIN_VALUE, MIN_VALUE, MIN_VALUE, MIN_VALUE, MAX_VALUE, Long.MIN_VALUE, MIN_VALUE, MIN_VALUE, MIN_VALUE, Long.MIN_VALUE, MAX_VALUE, MIN_VALUE);
        Entry avg = new Entry("average", 0L, 0, 0, 0, 0, 0L, 0, 0, 0, 0L, 0, 0);
        Entry tot = new Entry("total", 0L, 0, 0, 0, 0, 0L, 0, 0, 0, 0L, 0, 0);
        for (Entry entry : this.entries) {
            min.min(entry);
            max.max(entry);
            avg.add(entry);
            tot.add(entry);
        }
        avg.divide(this.entries.size());

        TTY.println();
        TTY.println();
        printHeader("metric");
        printLine(min);
        printLine(max);
        printLine(avg);
        printLine(tot);
    }

    private void printHeader(String firstTitle) {
        TTY.println(FORMAT_STRING, pad(firstTitle, NAME_WIDTH), pad("time", NUMBER_WIDTH),
                        pad("tree size", NUMBER_WIDTH), pad("#IR tree", NUMBER_WIDTH), pad("#IR root", NUMBER_WIDTH), pad("# parsed", NUMBER_WIDTH),
                        pad("opt. time", NUMBER_WIDTH), pad("#PEs", SMALL_NUMBER_WIDTH), pad("#invoke", NUMBER_WIDTH), pad("#rounds", NUMBER_WIDTH), pad("an. time", NUMBER_WIDTH),
                        pad("#inlined", NUMBER_WIDTH), pad("extra", NUMBER_WIDTH), SYMBOL_MAGNIFIER);
        TTY.println(String.join("", Collections.nCopies(TABLE_WIDTH, "-")));
    }

    private String color(String text, String colorCode, int width) {
        return colorCode + pad(text, width) + RESET;
    }

    private String colorNumberWithWidth(int width, long number, long critical, long high, long normal, String suffix) {
        if (number > critical) {
            return MAGENTA + pad(number + suffix, width) + RESET;
        } else if (number > high) {
            return RED + pad(number + suffix, width) + RESET;
        } else if (number > normal) {
            return YELLOW + pad(number + suffix, width) + RESET;
        } else {
            return GREEN + pad(number + suffix, width) + RESET;
        }
    }

    private String colorNumber(long number, long critical, long high, long normal, String suffix) {
        return colorNumberWithWidth(NUMBER_WIDTH, number, critical, high, normal, suffix);
    }

    private String colorSmallNumber(long number, long critical, long high, long normal, String suffix) {
        return colorNumberWithWidth(SMALL_NUMBER_WIDTH, number, critical, high, normal, suffix);
    }

    private String colorDuration(long duration) {
        return colorNumber(duration, DURATION_CRITICAL, DURATION_LARGE, DURATION_MEDIUM, " ms");
    }

    private String colorCallTreeSize(int callTreeSize) {
        return colorNumber(callTreeSize, TREE_SIZE_CRITICAL, TREE_SIZE_LARGE, TREE_SIZE_MEDIUM, "");
    }

    private String colorIrSize(int irSize) {
        return colorNumber(irSize, IR_CRITICAL, IR_LARGE, IR_MEDIUM, "");
    }

    private String colorPeCount(int peCount) {
        return colorSmallNumber(peCount, PE_CRITICAL, PE_LARGE, PE_MEDIUM, "");
    }

    private String colorInvokeCount(int invokesLeft) {
        return BLUE + pad(invokesLeft + "", NUMBER_WIDTH) + RESET;
    }

    private void printLine(Entry entry) {
        TTY.println(FORMAT_STRING, color(entry.methodName, BLUE, NAME_WIDTH), colorDuration(entry.duration),
                        colorCallTreeSize(entry.callTreeSize), colorIrSize(entry.callTreeIrSize),
                        colorIrSize(entry.rootIrSize), colorIrSize(entry.parsedIrSize),
                        colorDuration(entry.optDuration), colorPeCount(entry.peCount),
                        colorInvokeCount(entry.invokesLeft), colorCallTreeSize(entry.rounds), colorDuration(entry.extraAnalysisDuration), colorInvokeCount(entry.numMethodsInlined),
                        colorInvokeCount(entry.extraMetric), entry.estimate());
    }
}
