/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.profile;

import java.io.*;
import java.util.*;
import java.util.Arrays;

import com.sun.max.*;
import com.sun.max.profile.Metrics.*;
import com.sun.max.util.timer.*;

public class GlobalMetrics {

    static class EntryComparator implements Comparator<Map.Entry<String, Metric>> {
        public int compare(Map.Entry<String, Metric> a, Map.Entry<String, Metric> b) {
            return String.CASE_INSENSITIVE_ORDER.compare(a.getKey(), b.getKey());
        }
    }

    static class MetricSet<T extends Metric> {
        private final Class<T> clazz;
        private final Map<String, T> metrics = new HashMap<String, T>();

        MetricSet(Class<T> mClass) {
            clazz = mClass;
        }
    }

    protected static final Map<Class<? extends Metric>, MetricSet> metricSets = new HashMap<Class<? extends Metric>, MetricSet>();

    /**
     * This method allocates a new counter with the specified name and adds it to the global
     * metric list. If a previous metric with the same name exists, it will return a reference
     * to the first one created.
     * @param name the name of the metric for which to create a counter
     * @return a reference to a code {@code Counter} object which can be incremented and accumulated
     */
    public static Metrics.Counter newCounter(String name) {
        if (name == null) {
            return new Metrics.Counter();
        }
        return getCounter(name);
    }

    public static TimerMetric newTimer(String name, Clock clock) {
        if (name == null) {
            return new TimerMetric(new MultiThreadTimer(clock));
        }
        return getTimer(name, clock);
    }

    public static Metrics.Rate newRate(String name, Metrics.Counter count, Clock clock) {
        if (name == null) {
            return new Rate(count, clock);
        }
        return getRate(name, count, clock);
    }

    static synchronized Metrics.Counter getCounter(String name) {
        Metrics.Counter counter = getMetric(name, Metrics.Counter.class);
        if (counter == null) {
            counter = setMetric(name, Metrics.Counter.class, new Metrics.Counter());
        }
        return counter;
    }

    static synchronized TimerMetric getTimer(String name, Clock clock) {
        TimerMetric timer = getMetric(name, TimerMetric.class);
        if (timer == null) {
            timer = setMetric(name, TimerMetric.class, new TimerMetric(new MultiThreadTimer(clock)));
        }
        return timer;
    }

    static synchronized Metrics.Rate getRate(String name, Metrics.Counter count, Clock clock) {
        Metrics.Rate rate = getMetric(name, Metrics.Rate.class);
        if (rate == null) {
            rate = setMetric(name, Metrics.Rate.class, new Metrics.Rate(count, clock));
        }
        return rate;
    }

    public static <T extends Metric> T getMetric(String name, Class<T> mClass) {
        final MetricSet<T> metricSet = Utils.cast(metricSets.get(mClass));
        if (metricSet != null) {
            final T metric = metricSet.metrics.get(name);
            if (metric != null) {
                return metric;
            }
        }
        return null;
    }

    public static <T extends Metric> T setMetric(String name, Class<T> mClass, T metric) {
        MetricSet<T> metricSet = Utils.cast(metricSets.get(mClass));
        if (metricSet == null) {
            metricSet = new MetricSet<T>(mClass);
            metricSets.put(mClass, metricSet);
        }
        metricSet.metrics.put(name, metric);
        return metric;
    }

    /**
     * Resets of all the currently registered metrics.
     */
    public static synchronized void reset() {
        for (MetricSet<? extends Metric> metricSet : metricSets.values()) {
            for (Metric metric : metricSet.metrics.values()) {
                metric.reset();
            }
        }
    }

    /**
     * This method prints a report of all the metrics that have been created during this
     * execution run.
     * @param stream the print stream to which to print the report
     */
    public static synchronized void report(PrintStream stream) {
        final Map<String, Metric> allMetrics = new HashMap<String, Metric>();
        for (MetricSet<? extends Metric> metricSet : metricSets.values()) {
            allMetrics.putAll(metricSet.metrics);
        }

        Map.Entry<String, Metric>[] array = Utils.cast(new Map.Entry[allMetrics.size()]);
        array = allMetrics.entrySet().toArray(array);
        Arrays.sort(array, new GlobalMetrics.EntryComparator());
        for (Map.Entry<String, Metric> entry : array) {
            if (entry.getKey().length() > Metrics.longestMetricName) {
                Metrics.longestMetricName = entry.getKey().length();
            }
        }
        for (Map.Entry<String, Metric> entry : array) {
            entry.getValue().report(entry.getKey(), stream);
        }
        stream.flush();
    }

}
