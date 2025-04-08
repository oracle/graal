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
 * A class that's used as a namespace for all {@link jdk.graal.compiler.debug.MetricKey}s that are
 * used for tracking statistics through Logging API for Stackifier control-flow reconstruction.
 *
 * @see com.oracle.svm.hosted.webimage.logging.LoggerContext
 * @see com.oracle.svm.hosted.webimage.logging.LoggerScope
 */
public final class StackifierMetricKeys {
    /**
     * A counter key for number of forward blocks.
     */
    public static final MetricKey NUM_FORWARD_BLOCKS = DebugContext.counter("stackifier-number-of-forward-blocks");

    /**
     * A counter key for number of else scopes.
     */
    public static final MetricKey NUM_ELSE_SCOPES = DebugContext.counter("stackifier-number-of-else-scopes");

    /**
     * A counter key for number of then scopes.
     */
    public static final MetricKey NUM_THEN_SCOPES = DebugContext.counter("stackifier-number-of-then-scopes");

    /**
     * A counter key for number of loop scopes.
     */
    public static final MetricKey NUM_LOOP_SCOPES = DebugContext.counter("stackifier-number-of-loop-scopes");
}
