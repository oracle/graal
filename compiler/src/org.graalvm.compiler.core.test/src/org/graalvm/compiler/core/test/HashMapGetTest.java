/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package org.graalvm.compiler.core.test;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.junit.Test;

import java.util.HashMap;

public class HashMapGetTest extends GraalCompilerTest {

    public static void mapGet(HashMap<Integer, Integer> map, Integer key) {
        map.get(key);
    }

    @Test
    public void hashMapTest() {
        HashMap<Integer, Integer> map = new HashMap<>();
        ResolvedJavaMethod get = getResolvedJavaMethod(HashMapGetTest.class, "mapGet");
        for (int i = 0; i < 5000; i++) {
            mapGet(map, i);
            map.put(i, i);
            mapGet(map, i);
        }
        test(get, null, map, new Integer(0));
        for (IfNode ifNode : lastCompiledGraph.getNodes(IfNode.TYPE)) {
            LogicNode condition = ifNode.condition();
            if (ifNode.getTrueSuccessorProbability() < 0.4 && condition instanceof ObjectEqualsNode) {
                assertTrue(ifNode.trueSuccessor().next() instanceof ReturnNode, "Expected return.", ifNode.trueSuccessor(), ifNode.trueSuccessor().next());
            }
        }
    }

}
