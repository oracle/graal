/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.util.metrics;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

import org.graalvm.collections.UnmodifiableEconomicMap;

import com.oracle.svm.hosted.webimage.logging.LoggableMetric;
import com.oracle.svm.hosted.webimage.logging.LoggerScope;

import jdk.graal.compiler.debug.MetricKey;

/**
 * A utility class that contains methods that the metric collector classes and other classes can
 * use, such as propagating specified metrics to the specified scope, forcing metrics to exist in
 * the specified scope, ..., etc.
 */
public final class MetricsUtil {

    /**
     * A utility method for convenient retrieval of the metrics found in the specified class. A
     * metric is a static field of type {@link MetricKey}.
     *
     * @return the list of metric keys found in the specified class
     */
    public static List<MetricKey> getClassMetricKeys(Class<?> clazz) {
        Field[] fields = clazz.getFields();
        List<MetricKey> metricKeys = new ArrayList<>(fields.length);
        try {
            for (Field f : fields) {
                if (Modifier.isStatic(f.getModifiers()) && f.get(null) instanceof MetricKey) {
                    metricKeys.add((MetricKey) f.get(null));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            assert false : "Unexpected non-static field in a *MetricKeys class";
        }
        return metricKeys;
    }

    /**
     * A utility method that propagates (adds) all metrics to the parent scope, except those that
     * are explicitly ignored.
     *
     * @param parent the parent scope to which metrics are added
     * @param metrics the metrics from the current scope
     * @param ignoredMetricKeys A list of metrics which should not be propagated
     */
    public static void propagateMetricsExcept(LoggerScope parent, UnmodifiableEconomicMap<MetricKey, LoggableMetric> metrics, Collection<MetricKey> ignoredMetricKeys) {
        StreamSupport.stream(metrics.getKeys().spliterator(), false).filter(key -> !ignoredMetricKeys.contains(key)).forEach(
                        (key) -> parent.counter(key).add(getOrDefault(metrics, key)));
    }

    /**
     * A utility method that propagates (adds) all metrics to the parent scope, except those that
     * are from the specified metric class (only fields that are static are considered).
     */
    public static void propagateMetricsExcept(LoggerScope parent, UnmodifiableEconomicMap<MetricKey, LoggableMetric> metrics, Class<?> clazz) {
        propagateMetricsExcept(parent, metrics, getClassMetricKeys(clazz));
    }

    /**
     * A utility method that propagates (adds) all metrics to the parent scope.
     *
     * @param parent the parent scope to which metrics are added
     * @param metrics the metrics from the current scope
     */
    public static void propagateMetrics(LoggerScope parent, UnmodifiableEconomicMap<MetricKey, LoggableMetric> metrics) {
        propagateMetricsExcept(parent, metrics, Collections.emptyList());
    }

    /**
     * A utility method that retrieves the value of the specified metric if it's present in the
     * metrics map, 0 otherwise.
     *
     * @param metrics the metrics map
     * @param metric specified metric whose value is retrieved
     */
    public static long getOrDefault(UnmodifiableEconomicMap<MetricKey, LoggableMetric> metrics, MetricKey metric) {
        return metrics.containsKey(metric) ? metrics.get(metric).get() : 0L;
    }

    /**
     * A utility method that forces existence of the specified metrics in the specified
     * {@link LoggerScope}. This method simply retrieves counters for the specified keys from the
     * specified scope, which will create these counters in the specified scope if they don't exist.
     *
     * @param metricKeys the metricKeys that should exist in the specified scope
     * @param scope the scope that should contain counters for the specified keys
     */
    public static void forceMetricExistence(LoggerScope scope, MetricKey... metricKeys) {
        Arrays.stream(metricKeys).forEach(scope::counter);
    }

    /**
     * A utility method that forces existence of all the metrics found in the specified metric class
     * (only static fields are considered).
     *
     * @param scope the scope that should contain the counters for the metrics found in the
     *            specified class
     * @param clazz the class whose metrics should be present in the specified scope
     */
    public static void forceMetricExistence(LoggerScope scope, Class<?> clazz) {
        forceMetricExistence(scope, getClassMetricKeys(clazz).toArray(new MetricKey[0]));
    }
}
