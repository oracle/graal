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
package org.graalvm.polybench;

import java.lang.reflect.InvocationTargetException;

public class MetricFactory {

    public static void loadMetric(Config config, String name) {
        switch (name) {
            case "peak-time":
                config.metric = new PeakTimeMetric();
                break;
            case "none":
                config.metric = new NoMetric();
                break;
            case "compilation-time":
                config.metric = new CompilationTimeMetric(CompilationTimeMetric.MetricType.COMPILATION);
                break;
            case "partial-evaluation-time":
                config.metric = new CompilationTimeMetric(CompilationTimeMetric.MetricType.PARTIAL_EVALUATION);
                break;
            case "one-shot":
                config.metric = new OneShotMetric();
                config.warmupIterations = 0;
                config.iterations = 1;
                break;
            case "allocated-bytes":
                config.metric = new AllocatedBytesMetric();
                break;
            default:
                String className = classNameFor(name);
                try {
                    Class<?> cls = Class.forName(className);
                    config.metric = (Metric) cls.getConstructor().newInstance();
                } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException | ClassCastException e) {
                    throw new IllegalArgumentException("Unknown metric: " + name + " (" + e.getMessage() + ")");
                }
        }
    }

    private static String classNameFor(String metricName) {
        String[] words = metricName.split("-");
        StringBuilder result = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            result.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        result.append("Metric");
        return result.toString();
    }

    static String metricNameFor(String className) {
        StringBuilder result = new StringBuilder(className.length());
        result.append(Character.toLowerCase(className.charAt(0)));
        for (int i = 1; i < className.length(); i++) {
            char c = className.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append('-');
            }
            result.append(Character.toLowerCase(c));
        }
        return result.toString();
    }
}
