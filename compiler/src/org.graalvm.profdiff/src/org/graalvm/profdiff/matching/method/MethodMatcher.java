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
package org.graalvm.profdiff.matching.method;

import org.graalvm.profdiff.core.Experiment;

/**
 * Creates a matching of methods across two experiments.
 */
public interface MethodMatcher {
    /**
     * Matches pairs of methods by their signature and then tries to match their respective
     * compilation units according to a heuristic. Returns an object describing the pairs of matched
     * methods, matched compilation units and also the list of unmatched compilation units.
     *
     * @param experiment1 the first experiment
     * @param experiment2 the second experiment
     * @return the description of the computed matching
     */
    MethodMatching match(Experiment experiment1, Experiment experiment2);
}
