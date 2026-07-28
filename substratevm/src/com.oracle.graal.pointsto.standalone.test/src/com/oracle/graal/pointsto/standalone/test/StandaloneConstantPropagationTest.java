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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.standalone.test.classes.StandaloneConstantPropagationCase;

/**
 * Covers constant propagation through several standalone analysis paths.
 */
public class StandaloneConstantPropagationTest extends StandaloneAnalysisTest {

    /**
     * Verifies that the sink sees the exact singleton type across direct, return, and field-load
     * propagation paths.
     */
    @Test
    public void testConstantPropagationPaths() {
        assertConstantPropagationFor("propagateDirect");
        assertConstantPropagationFor("propagateViaReturn");
        assertConstantPropagationFor("propagateViaFieldLoadAndNullCheck");
    }

    /**
     * Verifies that the trivial static final constants in this fixture are folded by standalone
     * analysis.
     */
    @Test
    public void testConstantFieldFolding() {
        runAnalysis(StandaloneConstantPropagationCase.class);

        var instanceField = findField(StandaloneConstantPropagationCase.class, "INSTANCE");
        assertTrue("Expected INSTANCE to be folded.", instanceField.isFolded());
    }

    private void assertConstantPropagationFor(String entryMethodName) {
        runAnalysisMethod(StandaloneConstantPropagationCase.class, entryMethodName);

        var sink = findMethod(StandaloneConstantPropagationCase.class, "sink", Object.class);
        assertParameterTypes(sink, 0, StandaloneConstantPropagationCase.Singleton.class);
        assertParameterState(sink, 0, state -> {
            assertFalse("Did not expect null in the sink state.", state.canBeNull());
            assertTrue("Expected standalone propagation to preserve a constant image-heap value.",
                            state.asConstant() instanceof ImageHeapConstant);
        });
    }
}
