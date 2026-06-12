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

import com.oracle.graal.pointsto.standalone.test.classes.StandaloneFieldAndAllocationCase;

/**
 * Covers a compact but representative field-flow scenario for standalone analysis.
 */
public class StandaloneFieldAndAllocationFlowTest extends StandaloneAnalysisTest {

    /**
     * Verifies that standalone analysis keeps precise field states across allocations, preserves
     * nullable field state, and does not invent flow for an unused parameter.
     */
    @Test
    public void testFieldAndAllocationFlow() {
        runAnalysis(StandaloneFieldAndAllocationCase.class);

        assertFieldTypes(findField(StandaloneFieldAndAllocationCase.Holder.class, "exactField"),
                        StandaloneFieldAndAllocationCase.A.class,
                        StandaloneFieldAndAllocationCase.B.class);
        assertFieldTypes(findField(StandaloneFieldAndAllocationCase.Holder.class, "nullableField"),
                        StandaloneFieldAndAllocationCase.A.class,
                        StandaloneFieldAndAllocationCase.B.class);
        assertFieldCanBeNull(findField(StandaloneFieldAndAllocationCase.Holder.class, "nullableField"));

        var storeAndReturn = findMethod(StandaloneFieldAndAllocationCase.class, "storeAndReturn",
                        StandaloneFieldAndAllocationCase.Value.class,
                        StandaloneFieldAndAllocationCase.Holder.class,
                        Object.class,
                        boolean.class);
        assertParameterTypes(storeAndReturn, 0,
                        StandaloneFieldAndAllocationCase.A.class,
                        StandaloneFieldAndAllocationCase.B.class);
        assertParameterNotAnalyzed(storeAndReturn, 2);
        assertResultTypes(storeAndReturn,
                        StandaloneFieldAndAllocationCase.A.class,
                        StandaloneFieldAndAllocationCase.B.class);
    }
}
