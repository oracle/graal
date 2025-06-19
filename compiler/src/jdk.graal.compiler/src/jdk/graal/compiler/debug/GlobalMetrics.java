/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.debug;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.Pair;

import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.serviceprovider.IsolateUtil;

/**
 * Metric values that can be {@linkplain #add(DebugContext) updated} by multiple threads.
 */
public class GlobalMetrics {
    long[] values;

    /**
     * Adds the values in {@code debug} to the values in this object.
     */
    public synchronized void add(DebugContext debug) {
        values = debug.addValuesTo(values);
    }

    /**
     * Clears all values in this object.
     */
    public synchronized void clear() {
        values = null;
    }

    /**
     * Creates and returns a sorted map from metric names to their values in this object.
     */
    public EconomicMap<MetricKey, Long> asKeyValueMap() {
        List<MetricKey> keys = KeyRegistry.getKeys();
        Collections.sort(keys, MetricKey.NAME_COMPARATOR);
        EconomicMap<MetricKey, Long> res = EconomicMap.create(keys.size());
        long[] vals = values;
        for (MetricKey key : keys) {
            int index = ((AbstractKey) key).getIndex();
            if (vals == null || index >= vals.length) {
                res.put(key, 0L);
            } else {
                res.put(key, vals[index]);
            }
        }
        return res;
    }

    private static PrintStream openPrintStream(String metricsFile, Path[] outPath) throws IOException {
        if (metricsFile == null) {
            return DebugContext.getDefaultLogStream();
        } else {
            Path path = generateFileName(metricsFile);
            outPath[0] = path;
            return new PrintStream(Files.newOutputStream(path));
        }
    }

    static Path generateFileName(String nameTemplate) {
        String metricsFile = nameTemplate;
        if (metricsFile.contains("%p")) {
            metricsFile = metricsFile.replace("%p", GraalServices.getExecutionID());
        }
        Path path;
        if (IsolateUtil.getIsolateAddress() != 0L) {
            long isolateID = IsolateUtil.getIsolateID();
            int lastDot = metricsFile.lastIndexOf('.');
            if (lastDot != -1) {
                path = Paths.get(metricsFile.substring(0, lastDot) + '@' + isolateID + metricsFile.substring(lastDot));
            } else {
                path = Paths.get(metricsFile + isolateID);
            }
        } else {
            path = Paths.get(metricsFile);
        }
        return path;
    }

    /**
     * Prints the values in this object to the file specified by
     * {@link DebugOptions#AggregatedMetricsFile} if present otherwise to
     * {@link DebugContext#getDefaultLogStream()}.
     *
     * @return the path to which the metrics,if any, were printed
     */
    public Path print(OptionValues options) {
        return print(options, DebugOptions.AggregatedMetricsFile.getValue(options));
    }

    /**
     * Prints the values in this object to the file specified by {@code metricsFile} if it is
     * non-null otherwise to {@link DebugContext#getDefaultLogStream()}.
     *
     * @return the path to which the metrics,if any, were printed
     */
    public synchronized Path print(OptionValues options, String metricsFile) {
        Path pathWritten = null;
        if (values != null) {
            EconomicMap<MetricKey, Long> map = asKeyValueMap();
            boolean csv = metricsFile != null && (metricsFile.endsWith(".csv") || metricsFile.endsWith(".CSV"));
            PrintStream p = null;
            try {
                Path[] outPath = {null};
                p = openPrintStream(metricsFile, outPath);
                pathWritten = outPath[0];
                String isolateID = IsolateUtil.getIsolateID(false);
                if (!csv) {
                    if (!map.isEmpty()) {
                        p.printf("++ Aggregated Metrics %s ++%n", isolateID);
                    }
                }
                String csvFormat = CSVUtil.buildFormatString("%s", "%s", "%s");
                MapCursor<MetricKey, Long> e = map.getEntries();
                while (e.advance()) {
                    MetricKey key = e.getKey();
                    if (csv) {
                        Pair<String, String> valueAndUnit = key.toCSVFormat(e.getValue());
                        CSVUtil.Escape.println(p, csvFormat, key.getName(), valueAndUnit.getLeft(), valueAndUnit.getRight());
                    } else {
                        p.println(key.getName() + "=" + key.toHumanReadableFormat(e.getValue()));
                    }
                }
                if (!csv) {
                    if (!map.isEmpty()) {
                        p.printf("-- Aggregated Metrics %s --%n", isolateID);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                // Don't close DEFAULT_LOG_STREAM
                if (metricsFile != null && p != null) {
                    p.close();
                }
            }
        }

        if (DebugOptions.ListMetrics.getValue(options)) {
            PrintStream p = System.out;
            p.println("++ Metric Keys ++");
            List<MetricKey> keys = KeyRegistry.getKeys();
            Collections.sort(keys, MetricKey.NAME_COMPARATOR);
            for (MetricKey key : keys) {
                String name = key.getDocName();
                if (name != null) {
                    String doc = key.getDoc();
                    if (doc != null) {
                        p.println(name + ": " + doc);
                    } else {
                        p.println(name);
                    }
                }
            }
            p.println("-- Metric Keys --");
        }
        return pathWritten;
    }
}
