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
package com.oracle.graal.hotspot.test;

import static com.oracle.graal.phases.GraalOptions.*;

import org.junit.*;

import com.oracle.graal.compiler.test.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.CompileTheWorld.Config;

/**
 * Tests {@link CompileTheWorld} functionality.
 */
public class CompileTheWorldTest extends GraalCompilerTest {

    @Test
    public void testRtJar() throws Throwable {
        boolean originalSetting = ExitVMOnException.getValue();
        // Compile a couple classes in rt.jar
        String file = System.getProperty("java.home") + "/lib/rt.jar";
        new CompileTheWorld(file, new Config(null), 1, 5, false).compile();
        ExitVMOnException.setValue(originalSetting);
    }

}
