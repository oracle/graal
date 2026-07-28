/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.test;

import org.junit.Test;

import com.oracle.graal.pointsto.standalone.test.classes.StandaloneReturnedParameterCase;

/**
 * Verifies the standalone equivalent of the returned-parameter optimization coverage from the
 * regular points-to suite.
 */
public class StandaloneReturnedParameterOptimizationTest extends StandaloneAnalysisTest {

    private static final String DISABLE_OPTIMIZE_RETURNED_PARAMETER_OPTION = "-H:-OptimizeReturnedParameter";
    private static final String ENABLE_OPTIMIZE_RETURNED_PARAMETER_OPTION = "-H:+OptimizeReturnedParameter";

    /**
     * Without the optimization, both stores observe the merged helper return state. With the
     * optimization enabled, each store keeps its exact argument type.
     */
    @Test
    public void testReturnedParameterOptimization() {
        runAnalysis(StandaloneReturnedParameterCase.class, DISABLE_OPTIMIZE_RETURNED_PARAMETER_OPTION);
        assertFieldTypes(findField(StandaloneReturnedParameterCase.class, "temp1"),
                        StandaloneReturnedParameterCase.A.class,
                        StandaloneReturnedParameterCase.B.class);
        assertFieldTypes(findField(StandaloneReturnedParameterCase.class, "temp2"),
                        StandaloneReturnedParameterCase.A.class,
                        StandaloneReturnedParameterCase.B.class);

        runAnalysis(StandaloneReturnedParameterCase.class, ENABLE_OPTIMIZE_RETURNED_PARAMETER_OPTION);
        assertFieldTypes(findField(StandaloneReturnedParameterCase.class, "temp1"),
                        StandaloneReturnedParameterCase.A.class);
        assertFieldTypes(findField(StandaloneReturnedParameterCase.class, "temp2"),
                        StandaloneReturnedParameterCase.B.class);
    }
}
