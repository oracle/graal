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

public class AgentPremainHelper {
    public static void load(Class<?> agentClass) {
        String firstAgent = System.getProperty("first.load.agent", null);
        if (firstAgent != null) {
            System.setProperty("second.load.agent", agentClass.getName());
        } else {
            System.setProperty("first.load.agent", agentClass.getName());
        }
    }

    public static String getFirst() {
        return System.getProperty("first.load.agent");
    }

    public static String getSecond() {
        return System.getProperty("second.load.agent");
    }

    public static void parseOptions(String agentArgs) {
        if (agentArgs != null && !agentArgs.isBlank()) {
            String[] argPairs = agentArgs.split(",");
            for (String argPair : argPairs) {
                String[] pair = argPair.split("=");
                if (pair.length == 2) {
                    System.setProperty(pair[0], pair[1]);
                }
            }
        }
    }
}
