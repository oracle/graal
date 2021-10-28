/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.common;

/**
 * Describes the state and the configuration of a Truffle compilation.
 */
public interface TruffleCompilationTask {
    /**
     * Determines if this compilation has been cancelled.
     */
    boolean isCancelled();

    /**
     * Returns {@code true} if this is a last tier compilation.
     */
    boolean isLastTier();

    /**
     * Returns {@code true} if this is a first tier compilation.
     */
    default boolean isFirstTier() {
        return !isLastTier();
    }

    default int tier() {
        return isFirstTier() ? 1 : 2;
    }

    TruffleInliningData inliningData();

    boolean hasNextTier();

    default long time() {
        return 0;
    }

    default double weight() {
        return Double.NaN;
    }

    default double rate() {
        return Double.NaN;
    }

    default int queueChange() {
        return 0;
    }
}
