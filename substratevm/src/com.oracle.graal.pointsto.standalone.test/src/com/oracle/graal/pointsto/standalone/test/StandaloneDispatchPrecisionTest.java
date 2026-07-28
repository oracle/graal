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

import com.oracle.graal.pointsto.standalone.test.classes.StandaloneDispatchPrecisionCase;

/**
 * Strengthens invoke-target validation for standalone analysis across multiple dispatch shapes.
 */
public class StandaloneDispatchPrecisionTest extends StandaloneAnalysisTest {

    /**
     * Verifies exact callee sets and result types for abstract, interface, and final-receiver
     * dispatch.
     */
    @Test
    public void testDispatchPrecision() {
        runAnalysis(StandaloneDispatchPrecisionCase.class);

        var abstractDispatch = findMethod(StandaloneDispatchPrecisionCase.class, "dispatchAbstract", StandaloneDispatchPrecisionCase.AbstractWorker.class);
        assertInvokeCallees(abstractDispatch, findOnlyInvokeBci(abstractDispatch),
                        findMethod(StandaloneDispatchPrecisionCase.A.class, "work"),
                        findMethod(StandaloneDispatchPrecisionCase.B.class, "work"));
        assertResultTypes(abstractDispatch,
                        StandaloneDispatchPrecisionCase.ResultA.class,
                        StandaloneDispatchPrecisionCase.ResultB.class);

        var interfaceDispatch = findMethod(StandaloneDispatchPrecisionCase.class, "dispatchInterface", StandaloneDispatchPrecisionCase.Worker.class);
        assertInvokeCallees(interfaceDispatch, findOnlyInvokeBci(interfaceDispatch),
                        findMethod(StandaloneDispatchPrecisionCase.A.class, "work"),
                        findMethod(StandaloneDispatchPrecisionCase.B.class, "work"));
        assertResultTypes(interfaceDispatch,
                        StandaloneDispatchPrecisionCase.ResultA.class,
                        StandaloneDispatchPrecisionCase.ResultB.class);

        var finalDispatch = findMethod(StandaloneDispatchPrecisionCase.class, "dispatchFinal", StandaloneDispatchPrecisionCase.FinalWorker.class);
        assertInvokeCallees(finalDispatch, findOnlyInvokeBci(finalDispatch),
                        findMethod(StandaloneDispatchPrecisionCase.FinalWorker.class, "work"));
        assertResultTypes(finalDispatch, StandaloneDispatchPrecisionCase.ResultC.class);
    }
}
