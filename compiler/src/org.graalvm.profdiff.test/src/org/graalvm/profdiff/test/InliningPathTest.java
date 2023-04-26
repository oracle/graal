/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.profdiff.core.inlining.InliningPath;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.junit.Test;

public class InliningPathTest {
    @Test
    public void factoryMethodWorks() {
        InliningPath expected = new InliningPath(List.of(
                        new InliningPath.PathElement("a()", -1),
                        new InliningPath.PathElement("b()", 2),
                        new InliningPath.PathElement("c()", 3)));
        assertEquals(expected, InliningPath.of("a()", -1, "b()", 2, "c()", 3));
    }

    /**
     * Verifies that {@link InliningPath#fromRootToNode(InliningTreeNode)} identifies the path
     * correctly.
     *
     * The following inlining tree is tested:
     *
     * <pre>
     * Inlining tree
     *     a() at bci -1
     *         b() at bci 1
     *             (indirect) c() at bci 2
     *                 d() at bci 3
     * </pre>
     */
    @Test
    public void pathFromRootToNode() {
        InliningTreeNode a = new InliningTreeNode("a()", -1, true, null, false, null, false);
        InliningTreeNode b = new InliningTreeNode("b()", 1, true, null, false, null, false);
        InliningTreeNode c = new InliningTreeNode("c()", 2, false, null, true, null, true);
        InliningTreeNode d = new InliningTreeNode("d()", 3, true, null, false, null, false);
        a.addChild(b);
        b.addChild(c);
        c.addChild(d);

        InliningPath actual = InliningPath.fromRootToNode(d);
        InliningPath expected = new InliningPath(List.of(
                        new InliningPath.PathElement("a()", -1),
                        new InliningPath.PathElement("b()", 1),
                        new InliningPath.PathElement("d()", 3)));
        assertEquals(expected, actual);
    }

    @Test
    public void prefixWorks() {
        InliningPath path = new InliningPath(List.of(
                        new InliningPath.PathElement("a()", -1),
                        new InliningPath.PathElement("b()", 1),
                        new InliningPath.PathElement("c()", 2)));
        List<InliningPath> prefixes = List.of(
                        InliningPath.EMPTY,
                        new InliningPath(List.of(new InliningPath.PathElement("a()", -1))),
                        new InliningPath(List.of(
                                        new InliningPath.PathElement("a()", -1),
                                        new InliningPath.PathElement("b()", 1))),
                        new InliningPath(List.of(
                                        new InliningPath.PathElement("a()", -1),
                                        new InliningPath.PathElement("b()", 1),
                                        new InliningPath.PathElement("c()", 2))));
        prefixes.forEach(prefix -> assertTrue(prefix.isPrefixOf(path)));

        List<InliningPath> notPrefixes = List.of(
                        new InliningPath(List.of(new InliningPath.PathElement("b()", -1))),
                        new InliningPath(List.of(
                                        new InliningPath.PathElement("a()", -1),
                                        new InliningPath.PathElement("b()", 2))),
                        new InliningPath(List.of(
                                        new InliningPath.PathElement("a()", -1),
                                        new InliningPath.PathElement("b()", 1),
                                        new InliningPath.PathElement("c()", 2),
                                        new InliningPath.PathElement("d()", 3))));
        notPrefixes.forEach(prefix -> assertFalse(prefix.isPrefixOf(path)));
    }
}
