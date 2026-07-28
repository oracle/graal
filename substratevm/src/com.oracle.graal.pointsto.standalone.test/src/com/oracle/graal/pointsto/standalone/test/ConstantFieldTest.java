/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.standalone.test.classes.ConstantFieldCase;

/**
 * Verifies standalone handling of static final fields whose declaring class can be initialized
 * eagerly during analysis.
 */
public class ConstantFieldTest extends StandaloneAnalysisTest {

    /**
     * The fixture is initialized eagerly, so the class initializer is not modeled as a reachable
     * runtime method and the static final field can be folded while preserving reachability triggered
     * by the folded value.
     */
    @Test
    public void testConstantField() {
        runAnalysis(ConstantFieldCase.class);
        assertReachable(findMethod(ConstantFieldCase.ConstantType.class, "foo"));
        AnalysisField constantField = findField(ConstantFieldCase.class, "constantField");
        AnalysisType constantFieldCase = findClass(ConstantFieldCase.class);
        assertNotReachable(findClassInitializer(ConstantFieldCase.class));
        assertTrue("The declaring class should be initialized under unified standalone semantics.", constantFieldCase.isInitialized());
        assertTrue("The constant field should be folded under unified standalone semantics.", constantField.isFolded());
    }
}
