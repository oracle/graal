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
package com.oracle.svm.hosted.phases.priorityinline;

import static java.util.Comparator.comparingDouble;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.oracle.svm.hosted.phases.priorityinline.SubstratePriorityInliningPhase.TrackIPEAMode;

import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.nodes.spi.VirtualizableAllocation;
import jdk.graal.compiler.phases.common.priorityinline.CallTree;

/**
 * This class stores results of frequency calculations for every VirtualizableAllocation produced in
 * {@link InterproceduralPartialEscapeAnalysisUtil#afterExpansionPhase(CallTree, InterproceduralPartialEscapeAnalysisPhase.AnalysisResult)}
 * for debugging and collecting statistics. For each VirtualizableAllocation we store the
 * materialization frequency, number of invokes it is passed into (and explored by Priority Inliner)
 * and the applied boost value used to increase localBenefit in the CallTree. Furthermore, we also
 * store the PriorityInlinerPhase round, and results can be filtered to only show the last round for
 * every VirtualizableAllocation.
 * <p>
 * Keep in mind these calculations are only the approximations used for IPEA. For most accurate
 * statistics one should set -H:IPEACutoffMaterializationWeight=1.
 * <p>
 * See {@link SubstratePriorityInliningPhase.TrackIPEAMode} and
 * {@link SubstratePriorityInliningPhase.Options} to enable dumping of statistics.
 **/

public class InterProceduralPartialEscapeAnalysisStatistics {

    private static final int CALLTREE_NAME_WIDTH = 100;
    private static final int ALLOCATION_NAME_WIDTH = 30;
    private static final int NUMBER_WIDTH = 6;
    private static final int HISTOGRAM_NUMBER_WIDTH = 15;
    private static final int TABLE_WIDTH = 171;
    private static final String BLUE = "\u001b[34m";
    private static final String RESET = "\u001b[0m";
    private static final String FORMAT_STRING_ENTRY = "%s | %s | %s | %s | %s | %s |";
    private static final String MAT_FREQ_HISTOGRAM_TITLE = "Materialization Frequency Distribution";
    private static final String INJ_HISTOGRAM_TITLE = "Number injected Invokes Distribution";

    private final ArrayList<Entry> entries = new ArrayList<>();

    private static final class Entry {
        private final CallTree callTree;
        private final VirtualizableAllocation allocation;
        private final int round;
        private final int numberInjectedInvokes;
        private final double materializationFrequency;
        private final double appliedBoost;

        private Entry(CallTree callTree, VirtualizableAllocation allocation, int numberInjectedInvokes, double materializationFrequency, double appliedBoost) {
            this.callTree = callTree;
            this.allocation = allocation;
            this.round = callTree.state().round();
            this.numberInjectedInvokes = numberInjectedInvokes;
            this.materializationFrequency = materializationFrequency;
            this.appliedBoost = appliedBoost;
        }
    }

    private static final Comparator<Entry> FREQUENCY_COMPARATOR = comparingDouble(x -> x.materializationFrequency);

    static final class IPEAStatisticsThread extends Thread {

        private static class Bucket {
            private final double left;
            private final double right;
            private int count;

            Bucket(double left, double right) {
                this.left = left;
                this.right = right;
                this.count = 0;
            }

            public void inc() {
                count++;
            }

            public void set(int x) {
                count = x;
            }

            public void printHeader() {
                TTY.print(pad(String.format("%.2f - %.2f | ", left, right), HISTOGRAM_NUMBER_WIDTH));
            }

            public void printValue() {
                TTY.print(pad(String.format("%d | ", count), HISTOGRAM_NUMBER_WIDTH));
            }
        }

        private final InterProceduralPartialEscapeAnalysisStatistics statistics;
        private final TrackIPEAMode trackIPEAMode;
        private final int numberHistogramBuckets;

        IPEAStatisticsThread(InterProceduralPartialEscapeAnalysisStatistics statistics, TrackIPEAMode trackIPEAMode, int numberHistogramBuckets) {
            this.statistics = statistics;
            this.trackIPEAMode = trackIPEAMode;
            this.numberHistogramBuckets = numberHistogramBuckets;
        }

        private static void printEntry(Entry entry) {
            TTY.println(FORMAT_STRING_ENTRY,
                            pad(entry.callTree.root().getReadonlySubgraph().method().format("%H.%n"), CALLTREE_NAME_WIDTH),
                            pad(entry.allocation.toString(), ALLOCATION_NAME_WIDTH),
                            pad(String.valueOf(entry.round), NUMBER_WIDTH),
                            pad(String.valueOf(entry.numberInjectedInvokes), NUMBER_WIDTH),
                            pad(String.format("%.2f", entry.materializationFrequency), NUMBER_WIDTH),
                            pad(String.format("%.2f", entry.appliedBoost), NUMBER_WIDTH));
            printDivider();
        }

        private static String pad(String text, int width) {
            if (text.length() > width) {
                return "... " + text.substring(text.length() - width + 4);
            } else {
                return String.join("", Collections.nCopies(width - text.length(), " ")) + text;
            }
        }

        private static void printDivider() {
            TTY.println(String.join("", Collections.nCopies(TABLE_WIDTH, "-")));
        }

        private static void printHeader() {
            TTY.println(FORMAT_STRING_ENTRY, pad("CallTree", CALLTREE_NAME_WIDTH), pad("Allocation type", ALLOCATION_NAME_WIDTH), pad("#R", NUMBER_WIDTH), pad("#I", NUMBER_WIDTH),
                            pad("F", NUMBER_WIDTH), pad("B", NUMBER_WIDTH));
            printDivider();
        }

        private ArrayList<Entry> filterLastRound() {
            ArrayList<Entry> filteredEntries = new ArrayList<>();
            for (Entry entry : statistics.entries()) {
                boolean pick = true;
                for (Entry inner : statistics.entries()) {
                    if (entry.allocation.equals(inner.allocation) && entry.round < inner.round) {
                        pick = false;
                    }
                }
                if (pick) {
                    filteredEntries.add(entry);
                }
            }
            return filteredEntries;
        }

        private static int countAndRemove(ArrayList<Entry> finalEntries, double val) {
            int count = 0;
            for (int i = 0; i < finalEntries.size(); ++i) {
                Entry entry = finalEntries.get(i);
                if (entry.materializationFrequency == val) {
                    finalEntries.remove(i);
                    count++;
                }
            }
            return count;
        }

        private static void applyPrinter(ArrayList<Bucket> buckets, Consumer<Bucket> f) {
            for (Bucket bucket : buckets) {
                f.accept(bucket);
            }
            TTY.println();
        }

        private void printMaterializationFrequencyHistogram(ArrayList<Entry> finalEntries) {
            ArrayList<Entry> entries = (ArrayList<Entry>) finalEntries.stream()
                            .filter(entry -> entry.numberInjectedInvokes > 0)
                            .collect(Collectors.toList());
            ArrayList<Bucket> buckets = new ArrayList<>();
            Optional<Entry> maxFrequency = statistics.entries().stream().max(FREQUENCY_COMPARATOR);
            if (maxFrequency.isPresent()) {
                double currBucket = 0.0D;

                Bucket zeroBucket = new Bucket(0.0D, 0.0D);
                zeroBucket.set(countAndRemove(entries, 0.0D));
                buckets.add(zeroBucket);
                Bucket oneBucket = new Bucket(1.0D, 1.0D);
                oneBucket.set(countAndRemove(entries, 1.0D));

                double incValue = maxFrequency.get().materializationFrequency / (numberHistogramBuckets - 2);
                int entryIndex = 0;
                int steps = (int) Math.ceil(1.0D / incValue);
                int i = 0;
                while (i < steps) {
                    Bucket bucket = new Bucket(currBucket, currBucket + incValue);
                    while (entryIndex < entries.size() && entries.get(entryIndex).materializationFrequency < currBucket + incValue) {
                        bucket.inc();
                        entryIndex++;
                    }
                    buckets.add(bucket);
                    currBucket += incValue;
                    i++;
                }
                buckets.add(oneBucket);

                TTY.println(BLUE + MAT_FREQ_HISTOGRAM_TITLE + RESET);
                printDividerHistogram(buckets);
                applyPrinter(buckets, Bucket::printHeader);
                printDividerHistogram(buckets);
                applyPrinter(buckets, Bucket::printValue);
                printDividerHistogram(buckets);
            }
        }

        private void printNumberInjectionsHistogram(ArrayList<Entry> finalEntries) {
            ArrayList<Bucket> buckets = new ArrayList<>();
            for (int i = 0; i < numberHistogramBuckets; ++i) {
                buckets.add(new Bucket(i, i + 1));
            }

            for (Entry entry : finalEntries) {
                if (entry.numberInjectedInvokes >= numberHistogramBuckets) {
                    buckets.get(numberHistogramBuckets - 1).inc();
                } else {
                    buckets.get(entry.numberInjectedInvokes).inc();
                }
            }

            TTY.println(BLUE + INJ_HISTOGRAM_TITLE + RESET);
            printDividerHistogram(buckets);
            applyPrinter(buckets, Bucket::printHeader);
            printDividerHistogram(buckets);
            applyPrinter(buckets, Bucket::printValue);
            printDividerHistogram(buckets);
        }

        private static void printDividerHistogram(ArrayList<Bucket> buckets) {
            TTY.println(String.join("", Collections.nCopies(buckets.size() * HISTOGRAM_NUMBER_WIDTH, "-")));
        }

        @Override
        public void run() {
            synchronized (statistics) {
                ArrayList<Entry> finalEntries = trackIPEAMode.lastRound() ? filterLastRound() : statistics.entries();
                finalEntries.sort(FREQUENCY_COMPARATOR);
                if (trackIPEAMode.verbose()) {
                    printHeader();
                    for (Entry entry : finalEntries) {
                        printEntry(entry);
                    }
                    printDivider();
                }

                if (trackIPEAMode.shouldTrack()) {
                    printMaterializationFrequencyHistogram(finalEntries);
                    printNumberInjectionsHistogram(finalEntries);
                }
            }
        }
    }

    public void enter(CallTree callTree, VirtualizableAllocation allocation, int numberInjectedInvokes, double materializationFrequency, double appliedBoost) {
        Entry entry = new Entry(callTree, allocation, numberInjectedInvokes, materializationFrequency, appliedBoost);
        synchronized (this) {
            entries.add(entry);
        }
    }

    public ArrayList<Entry> entries() {
        return entries;
    }
}
