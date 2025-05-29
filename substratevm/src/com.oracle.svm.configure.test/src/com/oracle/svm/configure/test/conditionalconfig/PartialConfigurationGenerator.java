/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.test.conditionalconfig;

import static org.junit.Assume.assumeTrue;

import org.junit.Test;

/**
 * Like {@link ConfigurationGenerator}, but performs the work across multiple test methods. The
 * agent is run separately for each and configured to emit "partial" configurations, and then the
 * results are combined using the {@code native-image-configure generate-conditional} command. This
 * test is invoked manually from mx.
 */
public class PartialConfigurationGenerator {

    private static void runIfEnabled(Runnable runnable) {
        assumeTrue("Test must be explicitly enabled because it is not designed for regular execution", Boolean.getBoolean(PartialConfigurationGenerator.class.getName() + ".enabled"));
        runnable.run();
    }

    @Test
    public void createConfigPartOne() {
        runIfEnabled(NoPropagationNecessary::runTest);
    }

    @Test
    public void createConfigPartTwo() {
        runIfEnabled(PropagateToParent::runTest);
    }

    @Test
    public void createConfigPartThree() {
        runIfEnabled(PropagateButLeaveCommonConfiguration::runTest);
    }

    @Test
    public void createConfigPartFour() {
        runIfEnabled(PropagateThroughRecursiveCall::runTest);
    }
}
