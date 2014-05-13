/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.test;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.truffle.test.nodes.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

@Ignore("Currently ignored due to problems with code coverage tools.")
public class AssumptionPartialEvaluationTest extends PartialEvaluationTest {

    public static Object constant42() {
        return 42;
    }

    @Test
    public void constantValue() {
        FrameDescriptor fd = new FrameDescriptor();
        Assumption assumption = Truffle.getRuntime().createAssumption();
        AbstractTestNode result = new ConstantWithAssumptionTestNode(assumption, 42);
        RootTestNode rootNode = new RootTestNode(fd, "constantValue", result);
        InstalledCode installedCode = assertPartialEvalEquals("constant42", rootNode);
        Assert.assertTrue(installedCode.isValid());
        try {
            assertDeepEquals(42, installedCode.executeVarargs(null, null, null));
        } catch (InvalidInstalledCodeException e) {
            Assert.fail("Code must not have been invalidated.");
        }
        Assert.assertTrue(installedCode.isValid());
        try {
            assumption.check();
        } catch (InvalidAssumptionException e) {
            Assert.fail("Assumption must not have been invalidated.");
        }
        assumption.invalidate();
        try {
            assumption.check();
            Assert.fail("Assumption must have been invalidated.");
        } catch (InvalidAssumptionException e) {
        }
        Assert.assertFalse(installedCode.isValid());

        try {
            installedCode.executeVarargs(null, null, null);
            Assert.fail("Code must have been invalidated.");
        } catch (InvalidInstalledCodeException e) {
        }
    }
}
