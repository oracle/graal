/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.test.conditionalconfig;

import static org.junit.Assume.assumeTrue;

import java.lang.reflect.Proxy;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test that generates configuration metadata when traced by the agent. This output is tested
 * against the expected output in the config-dir directory by {@link ConfigurationVerifier}. This
 * test is invoked manually from mx.
 */
public class ConfigurationGenerator {

    @Test
    public void createTestConfig() {
        assumeTrue("Test must be explicitly enabled because it is not designed for regular execution", Boolean.getBoolean(ConfigurationGenerator.class.getName() + ".enabled"));
        NoPropagationNecessary.runTest();
        PropagateToParent.runTest();
        PropagateButLeaveCommonConfiguration.runTest();
        PropagateThroughRecursiveCall.runTest();
    }

}

/**
 * Utility class for generating dynamic configuration. Note that this class should never end up in
 * conditions.
 */
class ClassUtil {

    public static String qualifyName(String name) {
        return "com.oracle.svm.configure.test.conditionalconfig." + name;
    }

    public static void forName(String clazz) {
        try {
            Class.forName(qualifyName(clazz));
        } catch (ClassNotFoundException e) {
            Assert.fail("Test class " + qualifyName(clazz) + " not found! Exception: " + e);
        }
    }

    public static void getResource(String resourceName) {
        ClassUtil.class.getResource("resources/" + resourceName);
    }

    @SuppressWarnings("SuspiciousInvocationHandlerImplementation")
    public static void createProxy(Class<?>... interfaceList) {
        Proxy.newProxyInstance(ClassUtil.class.getClassLoader(), interfaceList, (proxy, method, args) -> null);
    }
}

/**
 * Expected condition: NoPropagationNecessary
 *
 * The agent sees a single call to runTest() that generates the configuration.
 */
class NoPropagationNecessary {

    public static void runTest() {
        ClassUtil.forName("NoPropagationNecessary$A");
        ClassUtil.getResource("NotPropagated.txt");
        ClassUtil.createProxy(IA.class);
    }

    @SuppressWarnings("unused")
    private static final class A {
    }

    interface IA {
    }
}

/**
 * Expected conditions: ParentA -> PropagateToParent.ParentA, ParentB -> PropagateToParent.ParentB
 *
 * The agent sees the method Util.doWork called twice with different configuration. It deduces that
 * the configuration must be propagated up the call chain.
 */
class PropagateToParent {

    public static void runTest() {
        ParentA.doWork();
        ParentB.doWork();
    }

    private static final class ParentA {
        static void doWork() {
            Util.doWork("PropagateToParent$ParentA", "PropagateToParentA.txt", IA.class);
        }
    }

    interface IA {
    }

    private static final class ParentB {
        static void doWork() {
            Util.doWork("PropagateToParent$ParentB", "PropagateToParentB.txt", IB.class);
        }
    }

    interface IB {
    }

    private static final class Util {
        static void doWork(String clazz, String resource, Class<?>... interfaceList) {
            ClassUtil.forName(clazz);
            ClassUtil.getResource(resource);
            ClassUtil.createProxy(interfaceList);
        }
    }

}

/**
 * Expected conditions: ParentA -> PropagateToParent.ParentA, ParentB -> PropagateToParent.ParentB,
 * Util -> C
 *
 * The agent sees the method Util.doWork called twice with different configuration. It deduces that
 * the configuration must be propagated up the call chain. However, the agent also sees the class C
 * appearing in both Util.doWork calls and does not propagate this configuration upwards.
 */
class PropagateButLeaveCommonConfiguration {

    public static void runTest() {
        ParentA.doWork();
        ParentB.doWork();
    }

    private static final class ParentA {
        static void doWork() {
            Util.doWork("PropagateButLeaveCommonConfiguration$ParentA", "PropagateToParentA.txt", IA.class);
        }
    }

    private static final class ParentB {
        static void doWork() {
            Util.doWork("PropagateButLeaveCommonConfiguration$ParentB", "PropagateToParentB.txt", IB.class);
        }
    }

    @SuppressWarnings("unused")
    private static final class C {
    }

    interface IA {
    }

    interface IB {
    }

    interface IC {
    }

    private static final class Util {
        static void doWork(String clazz, String resource, Class<?>... interfaceList) {
            ClassUtil.forName(clazz);
            ClassUtil.forName("PropagateButLeaveCommonConfiguration$C");
            ClassUtil.getResource(resource);
            ClassUtil.getResource("Common.txt");
            ClassUtil.createProxy(interfaceList);
            ClassUtil.createProxy(IC.class);
        }
    }

}

/**
 * This is a regression test. It ensures that configuration propagated from a recursive call does
 * not get lost (which can happen unless the configuration is propagated past any recursive
 * callers).
 */
@SuppressWarnings("unused")
class PropagateThroughRecursiveCall {

    public static void runTest() {
        ParentA.doWork();
        ParentB.doWork();
    }

    private static final class ParentA {
        static void doWork() {
            Recursive.recur(true, "PropagateThroughRecursiveCall$A");
        }
    }

    private static final class ParentB {
        static void doWork() {
            Recursive.recur(true, "PropagateThroughRecursiveCall$B");
        }
    }

    private static final class A1 {
    }

    private static final class A2 {
    }

    private static final class B1 {
    }

    private static final class B2 {
    }

    private static final class Recursive {
        static void recur(boolean recurse, String clazz) {
            ClassUtil.forName("PropagateThroughRecursiveCall$Recursive");
            if (recurse) {
                ClassUtil.forName(clazz + "1");
                recur(false, clazz);
            } else {
                ClassUtil.forName(clazz + "2");
            }

        }
    }
}
