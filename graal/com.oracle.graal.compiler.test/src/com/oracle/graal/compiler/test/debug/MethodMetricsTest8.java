/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.compiler.test.debug;

import org.junit.Test;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugConfig;
import com.oracle.graal.debug.DebugConfigScope;
import com.oracle.graal.options.OptionValue.OverrideScope;
import com.oracle.graal.phases.Phase;

public class MethodMetricsTest8 extends MethodMetricsTest {

    @Override
    protected Phase additionalPhase() {
        return new MethodMetricPhases.CountingMorePhase();
    }

    @Override
    DebugConfig getConfig() {
        return overrideGraalDebugConfig(System.out, "MethodMetricsTest$CFApplications.cf01," +
                        "MethodMetricsTest$CFApplications.cf02," +
                        "MethodMetricsTest$CFApplications.cf03", "CountingMorePhase");
    }

    @Test
    @Override
    @SuppressWarnings("try")
    public void test() throws Throwable {
        try (DebugConfigScope s = Debug.setConfig(getConfig()); OverrideScope o = getOScope();) {
            executeMethod(CFApplications.class.getMethod("cf01", int.class), null, 10);
            executeMethod(CFApplications.class.getMethod("cf02", int.class), null, 10);
            executeMethod(CFApplications.class.getMethod("cf03", int.class), null, 10);
            executeMethod(CFApplications.class.getMethod("cf04", int.class), null, 10);
            assertValues();
        }
    }

    @Override
    void assertValues() throws Throwable {

    }
}
