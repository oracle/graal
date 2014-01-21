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
package com.oracle.graal.compiler.ptx.test;

import java.lang.ref.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.ptx.*;
import com.oracle.graal.nodes.*;

/**
 * A full GC on HotSpot will unload an nmethod that contains an embedded oop which is the only
 * reference to its referent. The nmethod created for a {@linkplain PTXWrapperBuilder PTX kernel
 * wrapper} has an embedded oop referring to a {@link HotSpotNmethod} object associated with the
 * nmethod for the installed PTX kernel. This embedded oop is a weak reference as described above so
 * there must be another strong reference from the wrapper to the {@link HotSpotNmethod} object.
 */
public class PTXMethodInvalidation2Test extends PTXTest {

    @Test
    public void test() {
        test("testSnippet", 100);
    }

    @Override
    protected InstalledCode getCode(ResolvedJavaMethod method, StructuredGraph graph) {
        InstalledCode code = super.getCode(method, graph);

        // Try hard to force a full GC
        int attempts = 0;
        WeakReference<Object> ref = new WeakReference<>(new Object());
        while (ref.get() != null) {
            System.gc();
            // Give up after 1000 attempts
            Assume.assumeTrue(++attempts < 1000);
        }

        Assert.assertFalse(code.getStart() == 0L);
        return code;
    }

    int f = 42;

    public int testSnippet(int delta) {
        return f + delta;
    }
}
