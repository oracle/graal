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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oracle.svm.hosted.webimage.logging.LoggerScope;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.MetricKey;

/**
 * A utility class that represents namespace of {@link MetricKey}s used as keys in
 * {@link LoggerScope#counter(MetricKey)} for metric tracking of detailed image size.<br>
 * <br>
 *
 * <b>NOTE:</b> All of the metrics below are parsed by benchmarking suite. If a metric name needs to
 * be changed or some metrics is removed, please update appropriate parsing rule.
 */
public class ImageBreakdownMetricKeys {
    /**
     * A counter key for the entire image size, including possible support files.
     */
    public static final MetricKey ENTIRE_IMAGE_SIZE = DebugContext.counter("entire-image-size");

    /**
     * A counter key for the size of the JavaScript file.
     */
    public static final MetricKey JS_IMAGE_SIZE = DebugContext.counter("js-image-size");

    /**
     * A counter key for the size of the WebAssembly file (Wasm backend only).
     */
    public static final MetricKey WASM_IMAGE_SIZE = DebugContext.counter("wasm-image-size");

    /**
     * A counter key for the size of initial definitions (hand-written JavaScript code that replaces
     * some Java functions or is a set of helper functions like Long64, unsigned math, arrayCopy).
     */
    public static final MetricKey INITIAL_DEFINITIONS_SIZE = DebugContext.counter("initial-definitions-size");

    /**
     * A counter key for the size of static fields.
     */
    public static final MetricKey STATIC_FIELDS_SIZE = DebugContext.counter("static-fields-size");

    /**
     * A counter key for the size of all type declarations (everything of a type that is not a
     * lowered Java method, i.e. the class header, field offset table, constructor).
     */
    public static final MetricKey TYPE_DECLARATIONS_SIZE = DebugContext.counter("type-declarations-size");

    /**
     * A counter key for the size of extra definitions (definitions of boxed types, JavaScript Body,
     * ...).
     */
    public static final MetricKey EXTRA_DEFINITIONS_SIZE = DebugContext.counter("extra-definitions-size");

    /**
     * A counter key for the total size of constants. Its value should be equal to
     * {@link #CONSTANT_DEFS_SIZE} + {@link #CONSTANT_INITS_SIZE}.
     */
    public static final MetricKey CONSTANTS_SIZE = DebugContext.counter("constants-size");

    /**
     * A counter key for the size of constant definitions.
     */
    public static final MetricKey CONSTANT_DEFS_SIZE = DebugContext.counter("constant-definitions-size");

    /**
     * A counter key for the size of constant initializations.
     */
    public static final MetricKey CONSTANT_INITS_SIZE = DebugContext.counter("constant-initializations-size");

    /**
     * A counter key for the size of all methods (reconstructed or not).
     */
    public static final MetricKey TOTAL_METHOD_SIZE = DebugContext.counter("total-method-size");

    /**
     * A counter key for the size of reconstructed methods.
     */
    public static final MetricKey RECONSTRUCTED_SIZE = DebugContext.counter("reconstructed-method-size");

    /**
     * A counter key for the size of methods that have no control flow.
     */
    public static final MetricKey NO_CF_SIZE = DebugContext.counter("no-control-flow-method-size");

    /**
     * Counters for size classes of emitted constant objects.
     *
     * A size class "i" represents the objects whose size (in bytes) in the image-heap is between
     * 2^(i-1) and 2^i. For example, the size class 0 contains objects of size 1 byte, and the size
     * class 3 contains objects whose size is more than 4 bytes and at most 8 bytes.
     *
     * The counter of the respective size-class represents the total number of image-heap objects in
     * that size-class.
     *
     * The name of each size-class of size 2^i ends with the exponent i.
     */
    public static final List<MetricKey> CONSTANT_SIZE_CLASSES;

    static {
        List<MetricKey> sizeClasses = new ArrayList<>();
        for (int i = 0; i < 31; i++) {
            sizeClasses.add(DebugContext.counter("constant-size-class-" + i));
        }
        CONSTANT_SIZE_CLASSES = Collections.unmodifiableList(sizeClasses);
    }
}
