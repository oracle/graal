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
package com.oracle.svm.hosted.analysis.tesa.effect;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.svm.hosted.analysis.tesa.AbstractTesa;
import com.oracle.svm.hosted.analysis.tesa.TesaEngine;

/**
 * The base effect used by the analyses implemented in {@link TesaEngine}.
 * <p>
 * The effects form a lattice ordered so that lower values mean higher optimization potential, the
 * best one being {@code noEffect} at the bottom, while the {@code anyEffect} value represents that
 * there is probably no optimization potential.
 * <p>
 * To guarantee termination (fixed point is found), any implementation of this interface should
 * represent a lattice with <b>finite height</b>, i.e. there should always be a finite amount of
 * calls to the {@link #combineEffects} method producing larger values (higher in the lattice)
 * before the {@code top} is reached.
 * <p>
 * To guarantee that the fixed point is reached fast, the height of the lattice should be limited,
 * further restricting the amount of {@link #combineEffects} calls before reaching {@code top}.
 * 
 * @see AbstractTesa
 * @see TesaEngine
 * 
 */
public interface TesaEffect<T extends TesaEffect<T>> {
    /**
     * Returns {@code true} if this element represent that anything is possible, which typically
     * suggests no optimization potential, i.e. the analysis run into an invoke with unknown
     * {@code targetMethod}, native call, or the analysis simply stopped tracking precise
     * information, e.g. because it started to be intractable. This is conceptually similar to a
     * <i>saturation</i> in {@link PointsToAnalysis}.
     */
    boolean isAnyEffect();

    /**
     * Returns {@code true} if this element represents that no effect was observed, suggesting the
     * strongest optimization potential.
     */
    boolean hasNoEffects();

    /**
     * Returns an element representing the meet ("merge", "union"), i.e. the "bigger" fact, obtained
     * by combining {@code this} lattice element and {@code other}.
     */
    T combineEffects(T other);
}
