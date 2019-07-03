/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.allocationprofile;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.MetricsLogUtils;

public final class AllocationSite {

    public static class Options {
        @Option(help = "Enable runtime profiling of allocation")//
        public static final HostedOptionKey<Boolean> AllocationProfiling = new HostedOptionKey<>(false);

        @Option(help = "The minimum size in bytes required for printing an allocation profiling entry")//
        public static final RuntimeOptionKey<Integer> AllocationProfilingThreshold = new RuntimeOptionKey<>(1024 * 1024);

        @Option(help = "Print detailed information for each allocation site")//
        public static final RuntimeOptionKey<Boolean> PrintDetailedAllocationProfiling = new RuntimeOptionKey<>(true);
    }

    /**
     * The master list containing all allocation counters. The list access must be thread safe since
     * it is created during parsing when everything is concurrent.
     */
    private static final ConcurrentMap<AllocationSite, AllocationSite> sites = new ConcurrentHashMap<>();

    static {
        /*
         * The static analysis sees all involved types (AllocationSite, AllocationCounter) as
         * instantiated, but the actual objects are only created during compilation. Adding a unused
         * counter makes the types reachable for the static analysis.
         */
        lookup("__unused_to_make_counter_types_reachable__", "__").createCounter("__");
    }

    /** Allocation site name. */
    private final String siteName;
    /** Allocation class name. */
    private final String className;

    /**
     * Since the NewInstanceNode can be duplicated during inlining we allocated an allocation
     * counter for each compiled method. The reference wrapped by firstCounter field is effectively
     * the head of a single linked list containing all different counters for this allocation site.
     */
    private final AtomicReference<AllocationCounter> firstCounter;

    /** Cached total count during printing. */
    private long cachedCount;
    /** Cached total size during printing. */
    private long cachedSize;

    public static AllocationSite lookup(String siteName, String className) {
        return sites.computeIfAbsent(new AllocationSite(siteName, className), key -> key);
    }

    private AllocationSite(String siteName, String className) {
        this.siteName = siteName;
        this.className = className;
        this.firstCounter = new AtomicReference<>();
    }

    public AllocationCounter createCounter(String counterName) {
        AllocationCounter counter;
        do {
            counter = new AllocationCounter(counterName, firstCounter.get());
        } while (!firstCounter.compareAndSet(counter.getNext(), counter));
        return counter;
    }

    @Override
    public String toString() {
        return siteName + " : " + className;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AllocationSite) {
            AllocationSite other = (AllocationSite) obj;
            return this.className.equals(other.className) && this.siteName.equals(other.siteName);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return className.hashCode() ^ siteName.hashCode();
    }

    private static final Comparator<AllocationSite> sitesComparator = new Comparator<AllocationSite>() {
        @Override
        public int compare(AllocationSite o1, AllocationSite o2) {
            return Long.compare(o2.cachedSize, o1.cachedSize);
        }
    };

    private static final Comparator<AllocationCounter> counterComparator = (o1, o2) -> Long.compare(o2.getSize(), o1.getSize());

    /**
     * Summarizes the total allocation site object count and size and returns those allocations
     * sites that have the allocated size above the threshold.
     */
    public static List<AllocationSite> getSites() {

        List<AllocationSite> sortedSites = new ArrayList<>();
        for (AllocationSite site : sites.keySet()) {
            long totalCount = 0;
            long totalSize = 0;
            for (AllocationCounter counter = site.firstCounter.get(); counter != null; counter = counter.getNext()) {
                totalCount += counter.getCount();
                totalSize += counter.getSize();
            }
            site.cachedCount = totalCount;
            site.cachedSize = totalSize;

            if (totalSize >= Options.AllocationProfilingThreshold.getValue()) {
                sortedSites.add(site);
            }
        }

        sortedSites.sort(sitesComparator);

        return sortedSites;
    }

    public static void dumpProfilingResults() {
        dumpProfilingResults(Log.log());
    }

    public static void dumpProfilingResults(final Log log) {
        assert Options.AllocationProfiling.getValue();

        long totalAllocatedSize = 0;
        long totalAllocatedObjectCnt = 0;

        List<AllocationSite> sortedSites = getSites();

        List<AllocationCounter> counters = new ArrayList<>();
        log.string("Allocation site;Allocation class;Allocation count;Allocation size in bytes").newline();
        DecimalFormat grpFormatter = new DecimalFormat("###,###,###,###");

        for (AllocationSite site : sortedSites) {
            log.string(site.siteName).string(";").string(site.className).string(";").string(grpFormatter.format(site.cachedCount)).string(";").string(grpFormatter.format(site.cachedSize)).newline();

            for (AllocationCounter counter = site.firstCounter.get(); counter != null; counter = counter.getNext()) {
                totalAllocatedSize += counter.getSize();
                totalAllocatedObjectCnt += counter.getCount();

                if (counter.getSize() >= Options.AllocationProfilingThreshold.getValue() && AllocationSite.Options.PrintDetailedAllocationProfiling.getValue()) {
                    counters.add(counter);
                }
            }
            if (AllocationSite.Options.PrintDetailedAllocationProfiling.getValue()) {
                counters.sort(counterComparator);
                for (AllocationCounter counter : counters) {
                    log.string(";").string(counter.getName()).string(";").string(grpFormatter.format(counter.getCount())).string(";").string(grpFormatter.format(counter.getSize())).newline();
                }
                counters.clear();
            }
        }
        /* Print summary */
        MetricsLogUtils.logSection("Counters summary");

        MetricsLogUtils.logMemoryMetric("Total memory:", totalAllocatedSize);
        MetricsLogUtils.logCounterMetric("Total object:", totalAllocatedObjectCnt);
    }
}

@AutomaticFeature
class AllocationProfilingFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (AllocationSite.Options.AllocationProfiling.getValue()) {
            RuntimeSupport.getRuntimeSupport().addShutdownHook(AllocationSite::dumpProfilingResults);
        }
    }
}
