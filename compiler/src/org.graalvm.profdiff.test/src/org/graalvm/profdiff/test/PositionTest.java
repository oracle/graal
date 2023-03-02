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

import org.graalvm.collections.EconomicMap;
import org.graalvm.profdiff.core.inlining.InliningPath;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.core.optimization.Position;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class PositionTest {
    @Test
    public void factoryMethodsAreEquivalent() {
        Position position = Position.create(List.of("c", "b", "a"), List.of(3, 2, 1));
        EconomicMap<String, Integer> map = EconomicMap.create();
        map.put("c", 3);
        map.put("b", 2);
        map.put("a", 1);
        assertEquals(position, Position.fromMap(map));
        assertEquals(position, Position.of("c", 3, "b", 2, "a", 1));
    }

    @Test
    public void singleEmptyPosition() {
        assertSame(Position.EMPTY, Position.create(null, null));
        assertSame(Position.EMPTY, Position.create(List.of(), List.of()));
        assertSame(Position.EMPTY, Position.fromMap(null));
        assertSame(Position.EMPTY, Position.fromMap(EconomicMap.emptyMap()));
        assertSame(Position.EMPTY, Position.of());
    }

    @Test
    public void pathToEnclosingMethod() {
        InliningPath actual = Position.create(List.of("c", "b", "a"), List.of(3, 2, 1)).enclosingMethodPath();
        InliningPath expected = new InliningPath(List.of(
                        new InliningPath.PathElement("a", Optimization.UNKNOWN_BCI),
                        new InliningPath.PathElement("b", 1),
                        new InliningPath.PathElement("c", 2)));
        assertEquals(expected, actual);
    }

    @Test
    public void emptyPathToEnclosingMethod() {
        assertSame(InliningPath.EMPTY, Position.EMPTY.enclosingMethodPath());
    }

    @Test
    public void stringFormatting() {
        Position position = Position.of("c", 4, "b", 3, "a", 2);
        assertEquals("{c: 4, b: 3, a: 2}", position.toString(true, null));
        assertEquals("2", position.toString(false, null));

        InliningPath pathA = InliningPath.of("a", -1);
        assertEquals("2", position.toString(false, pathA));

        InliningPath pathB = InliningPath.of("a", -1, "b", 2);
        assertEquals("3", position.toString(false, pathB));

        InliningPath pathC = InliningPath.of("a", -1, "b", 2, "c", 3);
        assertEquals("4", position.toString(false, pathC));
    }

    @Test
    public void relativePosition() {
        Position position = Position.of("c", 4, "b", 3, "a", 2);
        InliningPath enclosingA = InliningPath.of("a", -1);
        assertEquals(position, position.relativeTo(enclosingA));
        InliningPath enclosingB = InliningPath.of("a", -1, "b", 2);
        assertEquals(Position.of("c", 4, "b", 3), position.relativeTo(enclosingB));
        InliningPath enclosingC = InliningPath.of("a", -1, "b", 2, "c", 3);
        assertEquals(Position.of("c", 4), position.relativeTo(enclosingC));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidRelativePosition() {
        Position position = Position.of("c", 4, "b", 3, "a", 2);
        InliningPath path = InliningPath.of("d", -1);
        position.relativeTo(path);
    }

    @Test
    public void comparator() {
        Position a = Position.EMPTY;
        Position b = Position.of("a", 1);
        Position c = Position.of("a", 2);
        Position d = Position.of("b", 3, "a", 2);
        Position e = Position.of("c", 1);
        Position f = Position.of("c", 2);
        Position g = Position.of("a", 1, "c", 2);
        List<Position> expected = List.of(a, b, c, d, e, f, g);
        List<Position> actual = Stream.of(d, b, e, g, a, f, c).sorted().collect(Collectors.toList());
        assertEquals(expected, actual);
    }
}
