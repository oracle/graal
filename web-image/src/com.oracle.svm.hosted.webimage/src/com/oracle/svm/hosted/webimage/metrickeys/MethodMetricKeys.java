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

import com.oracle.svm.hosted.webimage.logging.LoggerScope;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.MetricKey;

/**
 * A class used as a namespace for {@link MetricKey}s that are used as counter keys in
 * {@link LoggerScope#counter(MetricKey)} for tracking various per-method statistics and a total
 * number of reconstructed, ot reconstructed and methods without control flow.
 */
public class MethodMetricKeys {
    /**
     * A counter key that represents the size of a method.
     */
    public static final MetricKey METHOD_SIZE = DebugContext.counter("method-size");

    /**
     * A counter key that represents the number of splits found inside of a method.
     */
    public static final MetricKey NUM_SPLITS = DebugContext.counter("number-of-splits");

    /**
     * A counter key that represents the total number of reconstructed methods.
     */
    public static final MetricKey NUM_RECONSTRUCTED = DebugContext.counter("number-of-reconstructed-methods");

    /**
     * A counter key that represents the total number of methods without a control flow.
     */
    public static final MetricKey NUM_NO_CF = DebugContext.counter("number-of-no-control-flow-methods");

    /**
     * A counter key that represents the total number of X&&Y compound conditionals. This counter
     * key is used for both per-method metric and the total number from all methods.
     */
    public static final MetricKey NUM_COMPOUND_COND_XAAY = DebugContext.counter("number-of-compound-conditional-x&&y");

    /**
     * A counter key that represents the total number of X||Y compound conditionals. This counter
     * key is used for both per-method tracking and for tracking the total number from all methods.
     */
    public static final MetricKey NUM_COMPOUND_COND_XOOY = DebugContext.counter("number-of-compound-conditional-x||y");

    /**
     * A counter key that represents the total number of blocks found in method(s). This counter key
     * is used for both per-method tracking and for tracking the total number of blocks found in all
     * methods.
     */
    public static final MetricKey NUM_BLOCKS = DebugContext.counter("number-of-blocks");
}
