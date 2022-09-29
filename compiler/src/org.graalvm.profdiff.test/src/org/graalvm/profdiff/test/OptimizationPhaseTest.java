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
package org.graalvm.profdiff.test;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.core.optimization.OptimizationPhase;
import org.junit.Test;

public class OptimizationPhaseTest {
    @Test
    public void recursiveOptimizationList() {
        OptimizationPhase rootPhase = new OptimizationPhase("RootPhase");
        OptimizationPhase phaseA = new OptimizationPhase("A");
        OptimizationPhase phaseB = new OptimizationPhase("B");
        OptimizationPhase phaseC = new OptimizationPhase("C");
        OptimizationPhase phaseD = new OptimizationPhase("D");
        Optimization optimization1 = new Optimization("foo", "1", null, null);
        Optimization optimization2 = new Optimization("foo", "2", null, null);
        Optimization optimization3 = new Optimization("foo", "3", null, null);
        Optimization optimization4 = new Optimization("foo", "4", null, null);
        Optimization optimization5 = new Optimization("foo", "5", null, null);

        rootPhase.addChild(optimization1);
        rootPhase.addChild(phaseA);
        phaseA.addChild(optimization2);
        rootPhase.addChild(optimization3);
        rootPhase.addChild(phaseB);
        phaseB.addChild(phaseC);
        phaseC.addChild(optimization4);
        phaseC.addChild(optimization5);
        phaseB.addChild(phaseD);

        List<Optimization> expected = List.of(optimization1, optimization2, optimization3, optimization4, optimization5);
        assertEquals(expected, rootPhase.getOptimizationsRecursive());
    }
}
