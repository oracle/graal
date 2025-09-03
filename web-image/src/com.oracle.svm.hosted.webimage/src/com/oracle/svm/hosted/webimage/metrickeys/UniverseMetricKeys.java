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

package com.oracle.svm.hosted.webimage.metrickeys;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.MetricKey;

/**
 * A utility class that represents the namespace for the {@link MetricKey}s that are used for the
 * tracking of the number of types, using the
 * {@linkplain com.oracle.svm.hosted.webimage.logging.LoggerScope Logging API}.
 */
public class UniverseMetricKeys {
    /**
     * Represents the total number of types in the analysis universe.
     */
    public static final MetricKey ANALYSIS_TYPES = DebugContext.counter("analysis-types");

    /**
     * Represents the total number of types in the hosted universe.
     */
    public static final MetricKey HOSTED_TYPES = DebugContext.counter("hosted-types");

    /**
     * Represents the total number of types that were compiled in the CompileQueue.
     * <p>
     * A type is compiled, if one of its methods was compiled.
     */
    public static final MetricKey COMPILED_TYPES = DebugContext.counter("compiled-types");

    /**
     * Represents the total number of emitted types.
     */
    public static final MetricKey EMITTED_TYPES = DebugContext.counter("emitted-types");

    /**
     * Represents the total number of methods in the analysis universe.
     */
    public static final MetricKey ANALYSIS_METHODS = DebugContext.counter("analysis-methods");

    /**
     * Represents the total number of methods in the hosted universe.
     */
    public static final MetricKey HOSTED_METHODS = DebugContext.counter("hosted-methods");

    /**
     * Represents the total number of methods that were compiled in the CompileQueue.
     */
    public static final MetricKey COMPILED_METHODS = DebugContext.counter("compiled-methods");

    /**
     * Represents the total number of emitted methods.
     */
    public static final MetricKey EMITTED_METHODS = DebugContext.counter("emitted-methods");
}
