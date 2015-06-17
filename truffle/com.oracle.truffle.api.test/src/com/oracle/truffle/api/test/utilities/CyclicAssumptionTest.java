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
package com.oracle.truffle.api.test.utilities;

import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.utilities.*;

public class CyclicAssumptionTest {

    @Test
    public void testIsValid() {
        final CyclicAssumption assumption = new CyclicAssumption("cyclic-assumption");
        assertTrue(assumption.getAssumption().isValid());
    }

    @Test
    public void testInvalidate() {
        final CyclicAssumption cyclicAssumption = new CyclicAssumption("cyclic-assumption");

        final Assumption firstAssumption = cyclicAssumption.getAssumption();
        assertEquals("cyclic-assumption", firstAssumption.getName());
        assertTrue(firstAssumption.isValid());

        cyclicAssumption.invalidate();

        assertFalse(firstAssumption.isValid());

        final Assumption secondAssumption = cyclicAssumption.getAssumption();
        assertEquals("cyclic-assumption", secondAssumption.getName());
        assertTrue(secondAssumption.isValid());

        cyclicAssumption.invalidate();

        assertFalse(firstAssumption.isValid());
        assertFalse(secondAssumption.isValid());
    }

}
