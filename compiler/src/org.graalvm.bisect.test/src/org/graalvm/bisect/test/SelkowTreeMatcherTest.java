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
package org.graalvm.bisect.test;

import java.util.List;

import org.graalvm.bisect.core.CompilationUnit;
import org.graalvm.bisect.core.optimization.Optimization;
import org.graalvm.bisect.core.optimization.OptimizationPhase;
import org.graalvm.bisect.matching.tree.EditScript;
import org.graalvm.bisect.matching.tree.SelkowTreeMatcher;
import org.graalvm.collections.EconomicMap;
import org.junit.Assert;
import org.junit.Test;

public class SelkowTreeMatcherTest {
    @Test
    public void preferOptimizationInsertionAndDeletion() {
        Optimization foo = new Optimization("root", "foo", null, null);
        OptimizationPhase root1 = new OptimizationPhase("RootPhase");
        root1.addChild(foo);
        CompilationUnit method1 = new CompilationUnit("1", "foo", root1, 0, null);

        Optimization bar = new Optimization("root", "bar", null, null);
        OptimizationPhase root2 = new OptimizationPhase("RootPhase");
        root2.addChild(bar);
        CompilationUnit method2 = new CompilationUnit("2", "bar", root2, 0, null);

        SelkowTreeMatcher matcher = new SelkowTreeMatcher(1, 2, 1000);
        EditScript editScript = matcher.match(method1, method2);
        List<EditScript.DeltaNode> expected = List.of(
                        new EditScript.Identity(root1, root2, 0),
                        new EditScript.Delete(foo, 1),
                        new EditScript.Insert(bar, 1));
        Assert.assertEquals(expected, editScript.getDeltaNodes().toList());
    }

    /**
     * Tests that the Selkow tree matcher identifies the correct edit operations in dfs preorder.
     *
     * // @formatter:off
     * <pre>
     *                method1
     *                   |
     *                RootPhase
     *          _________|__________
     *        /          |          \
     * ToBeDeleted  ToBeRelabeled  ToBeUnchanged
     *               |       |
     *              foo     foo
     *
     *                method2
     *                   |
     *                RootPhase
     *                   |___________________________
     *                   |          |                \
     *               Relabeled  ToBeUnchanged   ToBeInserted
     *            _______|_______
     *          /        |       \
     *         foo foo{prop: 1}  foo
     * </pre>
     * // @formatter:on
     */
    @Test
    public void testOperations() {
        OptimizationPhase toBeDeleted = new OptimizationPhase("ToBeDeleted");
        OptimizationPhase toBeRelabeled = new OptimizationPhase("ToBeRelabeled");
        Optimization foo1 = new Optimization("ToBeRelabeled", "foo", null, null);
        Optimization foo3 = new Optimization("ToBeRelabeled", "foo", null, null);
        toBeRelabeled.addChild(foo1);
        toBeRelabeled.addChild(foo3);
        OptimizationPhase toBeUnchaged = new OptimizationPhase("ToBeUnchanged");
        OptimizationPhase root1 = new OptimizationPhase("RootPhase");
        root1.addChild(toBeDeleted);
        root1.addChild(toBeRelabeled);
        root1.addChild(toBeUnchaged);
        CompilationUnit method1 = new CompilationUnit("1", "method1", root1, 0, null);

        OptimizationPhase relabeled = new OptimizationPhase("Relabeled");
        Optimization foo1Clone = new Optimization("ToBeRelabeled", "foo", null, null);
        Optimization foo2 = new Optimization("ToBeRelabeled", "foo", null, EconomicMap.of("prop", 1));
        Optimization foo3Clone = new Optimization("ToBeRelabeled", "foo", null, null);
        relabeled.addChild(foo1Clone);
        relabeled.addChild(foo2);
        relabeled.addChild(foo3Clone);
        OptimizationPhase toBeInserted = new OptimizationPhase("ToBeInserted");
        OptimizationPhase root2 = new OptimizationPhase("RootPhase");
        root2.addChild(relabeled);
        OptimizationPhase toBeUnchagedClone = new OptimizationPhase("ToBeUnchanged");
        root2.addChild(toBeUnchagedClone);
        root2.addChild(toBeInserted);
        CompilationUnit method2 = new CompilationUnit("2", "method2", root2, 0, null);

        SelkowTreeMatcher matcher = new SelkowTreeMatcher(1, 2, 1000);
        EditScript editScript = matcher.match(method1, method2);
        List<EditScript.DeltaNode> expected = List.of(
                        new EditScript.Identity(root1, root2, 0),
                        new EditScript.Delete(toBeDeleted, 1),
                        new EditScript.Relabel(toBeRelabeled, relabeled, 1),
                        new EditScript.Identity(foo1, foo1Clone, 2),
                        new EditScript.Insert(foo2, 2),
                        new EditScript.Identity(foo3, foo3Clone, 2),
                        new EditScript.Identity(toBeUnchaged, toBeUnchagedClone, 1),
                        new EditScript.Insert(toBeInserted, 1));
        Assert.assertEquals(expected, editScript.getDeltaNodes().toList());
    }
}
