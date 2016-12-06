/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * <h3>Accessing the Truffle Runtime</h3>
 *
 * <p>
 * The Truffle runtime can be accessed at any point in time globally using the static method
 * {@link Truffle#getRuntime()}. This method is guaranteed to return a non-null Truffle runtime
 * object with an identifying name. A Java Virtual Machine implementation can chose to replace the
 * default implementation of the {@link TruffleRuntime} interface with its own implementation for
 * providing improved performance.
 * </p>
 *
 * <p>
 * The next part of the Truffle API introduction is at
 * {@link com.oracle.truffle.api.test.RootNodeTest}.
 * </p>
 */
public class TruffleRuntimeTest {

    private TruffleRuntime runtime;

    @Before
    public void before() {
        this.runtime = Truffle.getRuntime();
    }

    private static RootNode createTestRootNode(SourceSection sourceSection) {
        return new RootNode(TestingLanguage.class, sourceSection, null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return 42;
            }
        };
    }

    // @Test
    public void verifyTheRealRuntimeIsUsedOnRealGraal() {
        TruffleRuntime r = Truffle.getRuntime();
        final String name = r.getClass().getName();
        if (name.endsWith("DefaultTruffleRuntime")) {
            fail("Wrong name " + name + " with following System.getProperties:\n" + System.getProperties().toString());
        }
    }

    @Test
    public void test() {
        assertNotNull(runtime);
        assertNotNull(runtime.getName());
    }

    @Test
    public void testCreateCallTarget() {
        RootNode rootNode = createTestRootNode(null);
        RootCallTarget target = runtime.createCallTarget(rootNode);
        assertNotNull(target);
        assertEquals(target.call(), 42);
        assertSame(rootNode, target.getRootNode());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetCallTargets1() {
        RootNode rootNode = createTestRootNode(null);
        RootCallTarget target = runtime.createCallTarget(rootNode);
        assertTrue(runtime.getCallTargets().contains(target));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetCallTargets2() {
        RootNode rootNode = createTestRootNode(null);
        RootCallTarget target1 = runtime.createCallTarget(rootNode);
        assertTrue(runtime.getCallTargets().contains(target1));
    }

    /*
     * This test case documents the use case for profilers and debuggers where they need to access
     * multiple call targets for the same source section. This case may happen when the optimization
     * system decides to duplicate call targets to achieve better performance.
     */
    @Test
    public void testGetCallTargets3() {
        Source source1 = Source.newBuilder("a\nb\n").name("").mimeType("content/unknown").build();
        SourceSection sourceSection1 = source1.createSection(1);
        SourceSection sourceSection2 = source1.createSection(2);

        RootNode rootNode1 = createTestRootNode(sourceSection1);
        RootNode rootNode2 = createTestRootNode(sourceSection2);
        RootNode rootNode2Copy = NodeUtil.cloneNode(rootNode2);

        assertSame(rootNode2.getSourceSection(), rootNode2Copy.getSourceSection());

        RootCallTarget target1 = runtime.createCallTarget(rootNode1);
        RootCallTarget target2 = runtime.createCallTarget(rootNode2);
        RootCallTarget target2Copy = runtime.createCallTarget(rootNode2Copy);

        Map<SourceSection, List<RootCallTarget>> groupedTargets = groupUniqueCallTargets();

        List<RootCallTarget> targets1 = groupedTargets.get(sourceSection1);
        assertEquals(1, targets1.size());
        assertEquals(target1, targets1.get(0));

        List<RootCallTarget> targets2 = groupedTargets.get(sourceSection2);
        assertEquals(2, targets2.size());
        // order of targets2 is not guaranteed
        assertTrue(target2 == targets2.get(0) ^ target2Copy == targets2.get(0));
        assertTrue(target2 == targets2.get(1) ^ target2Copy == targets2.get(1));
    }

    @SuppressWarnings("deprecation")
    private static Map<SourceSection, List<RootCallTarget>> groupUniqueCallTargets() {
        Map<SourceSection, List<RootCallTarget>> groupedTargets = new HashMap<>();
        for (RootCallTarget target : Truffle.getRuntime().getCallTargets()) {
            SourceSection section = target.getRootNode().getSourceSection();
            if (section == null) {
                // can not identify root node to a unique call target. Print warning?
                continue;
            }
            List<RootCallTarget> targets = groupedTargets.get(section);
            if (targets == null) {
                targets = new ArrayList<>();
                groupedTargets.put(section, targets);
            }
            targets.add(target);
        }
        return groupedTargets;
    }
}
