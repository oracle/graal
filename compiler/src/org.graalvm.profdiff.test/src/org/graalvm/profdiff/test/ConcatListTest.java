/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.graalvm.profdiff.diff.ConcatList;
import org.junit.Test;

public class ConcatListTest {
    @Test
    public void concat() {
        ConcatList<Integer> foo = new ConcatList<>();
        foo.append(1);
        ConcatList<Integer> bar = new ConcatList<>();
        bar.append(2);
        foo.transferFrom(bar);
        assertEquals(List.of(1, 2), foo.toList());
        assertTrue(bar.isEmpty());
    }

    @Test
    public void concatEmpty() {
        ConcatList<Integer> foo = new ConcatList<>();
        ConcatList<Integer> bar = new ConcatList<>();
        bar.append(1);
        bar.append(2);
        assertFalse(bar.isEmpty());
        foo.transferFrom(bar);
        assertEquals(List.of(1, 2), foo.toList());
        assertTrue(bar.isEmpty());
    }

    @Test
    public void concatWithEmpty() {
        ConcatList<Integer> foo = new ConcatList<>();
        ConcatList<Integer> bar = new ConcatList<>();
        foo.append(1);
        foo.append(2);
        foo.transferFrom(bar);
        assertEquals(List.of(1, 2), foo.toList());
        assertTrue(bar.isEmpty());
    }

    @Test
    public void prepend() {
        ConcatList<Integer> foo = new ConcatList<>();
        foo.prepend(3);
        foo.prepend(2);
        foo.prepend(1);
        assertEquals(List.of(1, 2, 3), foo.toList());
    }
}
