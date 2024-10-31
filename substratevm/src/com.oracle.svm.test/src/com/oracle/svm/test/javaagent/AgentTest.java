/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.svm.test.javaagent;

import com.oracle.svm.test.javaagent.agent1.TestJavaAgent1;
import com.oracle.svm.test.javaagent.agent2.TestJavaAgent2;
import org.junit.Assert;

public class AgentTest {

    private static void testPremain() {
        Assert.assertEquals("true", System.getProperty("instrument.enable"));
    }

    private static void testAgentOptions() {
        Assert.assertEquals("true", System.getProperty("test.agent1"));
        Assert.assertEquals("true", System.getProperty("test.agent2"));
    }

    private static void testPremainSequence() {
        String first = AgentPremainHelper.getFirst();
        String second = AgentPremainHelper.getSecond();
        Assert.assertNotNull(first);
        if (second != null) {
            String agentName = TestJavaAgent1.class.getName();
            String agent2Name = TestJavaAgent2.class.getName();

            if (first.equals(agentName)) {
                Assert.assertEquals(agent2Name, second);
            }
            if (first.equals(agent2Name)) {
                Assert.assertEquals(agentName, second);
            }
        }
    }

    public static void main(String[] args) {
        testPremain();
        testAgentOptions();
        testPremainSequence();
        System.out.println("Finished running Agent test.");
    }
}
