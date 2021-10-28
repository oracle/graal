/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Handler;

import org.graalvm.polyglot.Value;

@SuppressWarnings("unused")
interface Metric {

    /**
     * Validates the mode and polyglot options parsed from the command line.
     *
     * @throws IllegalStateException when the mode or polyglot options cannot be used with the
     *             metric.
     */
    default void validateConfig(Config config, Map<String, String> polyglotOptions) {
    }

    /**
     * Returns engine options required by the {@link Metric}. The returned options are set on the
     * polyglot context.
     */
    default Map<String, String> getEngineOptions(Config config) {
        return Collections.emptyMap();
    }

    /**
     * Allows Metric to forward engine logging into supplied logger.
     */
    default Handler getLogHandler() {
        return null;
    }

    default void parseBenchSpecificOptions(Value runner) {
    }

    default void beforeIteration(boolean warmup, int iteration, Config config) {
    }

    default void afterIteration(boolean warmup, int iteration, Config config) {
    }

    default Optional<Double> reportAfterIteration(Config config) {
        return Optional.empty();
    }

    default Optional<Double> reportAfterAll() {
        return Optional.empty();
    }

    default void reset() {
    }

    default String unit() {
        return "n/a";
    }

    String name();
}
