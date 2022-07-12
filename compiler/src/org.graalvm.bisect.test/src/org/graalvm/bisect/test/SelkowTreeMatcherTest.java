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

import org.graalvm.bisect.core.ExecutedMethodImpl;
import org.graalvm.bisect.core.optimization.Optimization;
import org.graalvm.bisect.core.optimization.OptimizationImpl;
import org.graalvm.bisect.core.optimization.OptimizationPhaseImpl;
import org.graalvm.bisect.matching.tree.EditScript;
import org.graalvm.bisect.matching.tree.SelkowTreeMatcher;
import org.graalvm.bisect.util.EconomicMapUtil;
import org.junit.Assert;
import org.junit.Test;

public class SelkowTreeMatcherTest {
    @Test
    public void preferOptimizationInsertionAndDeletion() {
        Optimization foo = new OptimizationImpl("root", "foo", 0, null);
        OptimizationPhaseImpl root1 = new OptimizationPhaseImpl("RootPhase");
        root1.addChild(foo);
        ExecutedMethodImpl method1 = new ExecutedMethodImpl("1", "foo", root1, 0, null);

        Optimization bar = new OptimizationImpl("root", "bar", 0, null);
        OptimizationPhaseImpl root2 = new OptimizationPhaseImpl("RootPhase");
        root2.addChild(bar);
        ExecutedMethodImpl method2 = new ExecutedMethodImpl("2", "bar", root2, 0, null);

        SelkowTreeMatcher matcher = new SelkowTreeMatcher();
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
        OptimizationPhaseImpl toBeDeleted = new OptimizationPhaseImpl("ToBeDeleted");
        OptimizationPhaseImpl toBeRelabeled = new OptimizationPhaseImpl("ToBeRelabeled");
        Optimization foo1 = new OptimizationImpl("ToBeRelabeled", "foo", 0, null);
        Optimization foo3 = new OptimizationImpl("ToBeRelabeled", "foo", 0, null);
        toBeRelabeled.addChild(foo1);
        toBeRelabeled.addChild(foo3);
        OptimizationPhaseImpl toBeUnchaged = new OptimizationPhaseImpl("ToBeUnchanged");
        OptimizationPhaseImpl root1 = new OptimizationPhaseImpl("RootPhase");
        root1.addChild(toBeDeleted);
        root1.addChild(toBeRelabeled);
        root1.addChild(toBeUnchaged);
        ExecutedMethodImpl method1 = new ExecutedMethodImpl("1", "method1", root1, 0, null);

        OptimizationPhaseImpl relabeled = new OptimizationPhaseImpl("Relabeled");
        Optimization foo1Clone = new OptimizationImpl("ToBeRelabeled", "foo", 0, null);
        Optimization foo2 = new OptimizationImpl("ToBeRelabeled", "foo", 0, EconomicMapUtil.of("prop", 1));
        Optimization foo3Clone = new OptimizationImpl("ToBeRelabeled", "foo", 0, null);
        relabeled.addChild(foo1Clone);
        relabeled.addChild(foo2);
        relabeled.addChild(foo3Clone);
        OptimizationPhaseImpl toBeInserted = new OptimizationPhaseImpl("ToBeInserted");
        OptimizationPhaseImpl root2 = new OptimizationPhaseImpl("RootPhase");
        root2.addChild(relabeled);
        OptimizationPhaseImpl toBeUnchagedClone = new OptimizationPhaseImpl("ToBeUnchanged");
        root2.addChild(toBeUnchagedClone);
        root2.addChild(toBeInserted);
        ExecutedMethodImpl method2 = new ExecutedMethodImpl("2", "method2", root2, 0, null);

        SelkowTreeMatcher matcher = new SelkowTreeMatcher();
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
