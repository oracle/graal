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

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.oracle.svm.hosted.webimage.logging.LoggerContext;
import com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys;

import jdk.graal.compiler.debug.MetricKey;

/**
 * A utility class used for measuring code size for different parts of the image. Wrap a block of
 * code using a try-with-resources statement and it will track how much code is added in that part
 * of the code. Upon measuring the code size, the value is passed to the provided handler, which
 * should add the value to the metric associate with the given {@link MetricKey}.<br>
 * <br>
 *
 * Example:
 *
 * <pre>
 *      try(CodeSizeCollector collector = new CodeSizeCollector(ImageBreakdownMetricKeys.EXTRA_DEFINITIONS_SIZE, codeBuffer::codeSize) {
 *          lowerExtraDefinitons();
 *      }
 * </pre>
 */
public class CodeSizeCollector implements AutoCloseable {
    private final Supplier<Integer> codeSizeSupplier;
    private final int startSize;
    private final Consumer<Integer> diffHandler;

    /**
     * @param codeSizeSupplier a function that should return current size of the image.
     * @param diffHandler a function that processes the image size difference.
     */
    public CodeSizeCollector(Supplier<Integer> codeSizeSupplier, Consumer<Integer> diffHandler) {
        this.codeSizeSupplier = codeSizeSupplier;
        this.startSize = codeSizeSupplier.get();
        this.diffHandler = diffHandler;
    }

    /**
     * Default constructor, use when tracking everything except object/array initialization.
     *
     * @param key key that will be used to record the calculated size through Logging API.
     * @param codeSizeSupplier a function that should return current size of the image.
     */
    public CodeSizeCollector(MetricKey key, Supplier<Integer> codeSizeSupplier) {
        this(codeSizeSupplier, diff -> LoggerContext.counter(key).add(diff));
    }

    /**
     * Constructs a CodeSizeCollector object that is used for tracking object/array initialization.
     *
     * @param codeSizeSupplier a function that should return current size of the image.
     */
    public static CodeSizeCollector trackObjectSize(Supplier<Integer> codeSizeSupplier) {
        return new CodeSizeCollector(codeSizeSupplier, CodeSizeCollector::incrementConstantSizeClass);
    }

    /**
     * Bumps the size-class histogram.
     *
     * @param diff the image size difference.
     */
    public static void incrementConstantSizeClass(Integer diff) {
        if (diff == 0) {
            return;
        }
        int sizeClass = 32 - Integer.numberOfLeadingZeros(diff - 1);
        if (sizeClass < ImageBreakdownMetricKeys.CONSTANT_SIZE_CLASSES.size()) {
            LoggerContext.counter(ImageBreakdownMetricKeys.CONSTANT_SIZE_CLASSES.get(sizeClass)).add(1);
        }
    }

    @Override
    public void close() {
        int diff = codeSizeSupplier.get() - startSize;
        assert diff >= 0 : "Negative code size";
        diffHandler.accept(diff);
    }
}
