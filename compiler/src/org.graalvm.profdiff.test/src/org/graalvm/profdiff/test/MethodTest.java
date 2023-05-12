/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.test;

import org.graalvm.collections.Pair;
import org.graalvm.profdiff.core.Method;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MethodTest {
    @Test
    public void multiMethodSplit() {
        Pair<String, String> pair1 = Method.splitMultiMethodName("foo.bar.Baz()");
        assertEquals("foo.bar.Baz()", pair1.getLeft());
        assertNull(pair1.getRight());

        Pair<String, String> pair2 = Method.splitMultiMethodName("baz.bar.Foo%%Baz()");
        assertEquals("baz.bar.Foo()", pair2.getLeft());
        assertEquals("Baz", pair2.getRight());
    }

    @Test
    public void removeMultiMethodKey() {
        assertEquals("foo.Bar(Baz)", Method.removeMultiMethodKey("foo.Bar%%RemoveMe(Baz)"));
        assertEquals("Foo()", Method.removeMultiMethodKey("Foo()"));
    }
}
