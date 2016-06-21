/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.graal.api.test.Graal;
import com.oracle.graal.runtime.RuntimeProvider;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.api.impl.TVMCI;

public class TruffleRuntimeTest {

    @Test
    public void testGraalCapabilities() {
        assertNotNull(Graal.getRuntime().getCapability(RuntimeProvider.class));
    }

    @Test
    public void testRuntimeAvailable() {
        assertNotNull(Truffle.getRuntime());
    }

    @Test
    public void testRuntimeIsGraalRuntime() {
        TruffleRuntime runtime = Truffle.getRuntime();
        assertTrue(runtime.getClass() != DefaultTruffleRuntime.class);
    }

    @Test
    public void testGetTVMCI() {
        TruffleRuntime runtime = Truffle.getRuntime();
        TVMCI tvmci = runtime.getCapability(TVMCI.class);
        assertNotNull("Truffle Virtual Machine Compiler Interface not found", tvmci);
        assertEquals("GraalTVMCI", tvmci.getClass().getSimpleName());

        abstract class TVMCISubclass extends TVMCI {
        }
        TVMCISubclass subclass = runtime.getCapability(TVMCISubclass.class);
        assertNull("Expected null return value for TVMCI subclass", subclass);
    }

    @Test
    public void testGetCapabilityObjectClass() {
        Object object = Truffle.getRuntime().getCapability(Object.class);
        assertNull("Expected null return value for Object.class", object);
    }
}
