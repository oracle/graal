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

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.standalone.test.classes.StandaloneContextSensitiveReturnCase;

/**
 * Verifies that standalone analysis can run in a 1-object-sensitive mode and improve precision for
 * receiver-context-dependent returns.
 */
public class StandaloneContextSensitiveReturnTest extends StandaloneAnalysisTest {

    private static final String INSENSITIVE_CONTEXT_OPTION = "-H:AnalysisContextSensitivity=insens";
    private static final String ONE_OBJECT_CONTEXT_OPTION = "-H:AnalysisContextSensitivity=_1obj";
    private static final String DISABLE_OPTIMIZE_RETURNED_PARAMETER_OPTION = "-H:-OptimizeReturnedParameter";

    /**
     * Without receiver context, both stores see the merged identity result. With 1-object
     * sensitivity, each store keeps the exact value returned by its own factory receiver.
     */
    @Test
    public void testContextSensitiveReturnedValue() {
        runAnalysisMethod(StandaloneContextSensitiveReturnCase.class,
                        "run",
                        new Class<?>[0],
                        INSENSITIVE_CONTEXT_OPTION,
                        DISABLE_OPTIMIZE_RETURNED_PARAMETER_OPTION);
        assertInsensitiveResult();

        runAnalysisMethod(StandaloneContextSensitiveReturnCase.class,
                        "run",
                        new Class<?>[0],
                        ONE_OBJECT_CONTEXT_OPTION,
                        DISABLE_OPTIMIZE_RETURNED_PARAMETER_OPTION);
        assertOneObjectSensitiveResult();
    }

    private void assertInsensitiveResult() {
        AnalysisMethod identity = findMethod(StandaloneContextSensitiveReturnCase.Factory.class,
                        "identity",
                        StandaloneContextSensitiveReturnCase.Value.class);

        assertParameterTypes(identity, 1,
                        StandaloneContextSensitiveReturnCase.ValueA.class,
                        StandaloneContextSensitiveReturnCase.ValueB.class);
        assertResultTypes(identity,
                        StandaloneContextSensitiveReturnCase.ValueA.class,
                        StandaloneContextSensitiveReturnCase.ValueB.class);
        assertFieldTypes(findField(StandaloneContextSensitiveReturnCase.class, "firstResult"),
                        StandaloneContextSensitiveReturnCase.ValueA.class,
                        StandaloneContextSensitiveReturnCase.ValueB.class);
        assertFieldTypes(findField(StandaloneContextSensitiveReturnCase.class, "secondResult"),
                        StandaloneContextSensitiveReturnCase.ValueA.class,
                        StandaloneContextSensitiveReturnCase.ValueB.class);
    }

    private void assertOneObjectSensitiveResult() {
        AnalysisMethod identity = findMethod(StandaloneContextSensitiveReturnCase.Factory.class,
                        "identity",
                        StandaloneContextSensitiveReturnCase.Value.class);

        assertParameterTypes(identity, 1,
                        StandaloneContextSensitiveReturnCase.ValueA.class,
                        StandaloneContextSensitiveReturnCase.ValueB.class);
        assertResultTypes(identity,
                        StandaloneContextSensitiveReturnCase.ValueA.class,
                        StandaloneContextSensitiveReturnCase.ValueB.class);
        assertFieldTypes(findField(StandaloneContextSensitiveReturnCase.class, "firstResult"),
                        StandaloneContextSensitiveReturnCase.ValueA.class);
        assertFieldTypes(findField(StandaloneContextSensitiveReturnCase.class, "secondResult"),
                        StandaloneContextSensitiveReturnCase.ValueB.class);
    }
}
