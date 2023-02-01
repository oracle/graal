/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Alibaba Group Holding Limited. All rights reserved.
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
package com.oracle.graal.pointsto.standalone.test;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.Assert.assertNotNull;

public class StandaloneReflectionTest {

    public static void main(String[] args) {
        reflectClassTest();
        reflectFieldTest();
        reflectMethodTest();
    }

    private static void reflectClassTest() {
        try {
            // Use StringBuffer to generate reflect class name, in case that pointsto could infer
            // reflected class name directly from a constant String class name.
            StringBuilder clazzNameSB = new StringBuilder().append("com.oracle.graal.pointsto.standalone.test.ReflectClassTest");
            Class<?> reflectClassTest = Class.forName(clazzNameSB.toString());
            System.out.println(reflectClassTest.getName());
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
        }
    }

    private static void reflectFieldTest() {
        try {
            StringBuilder clazzNameBuff = new StringBuilder().append("com.oracle.graal.pointsto.standalone.test.ReflectFieldTest");
            Class<?> reflectFieldTest = Class.forName(clazzNameBuff.toString());
            StringBuilder fieldNameBuff = new StringBuilder().append("reflectFieldType");
            Field field = reflectFieldTest.getField(fieldNameBuff.toString());
            System.out.println(field.getName());
        } catch (ClassNotFoundException | NoSuchFieldException ex) {
            ex.printStackTrace();
        }
    }

    private static void reflectMethodTest() {
        try {
            StringBuilder clazzNameBuff = new StringBuilder().append("com.oracle.graal.pointsto.standalone.test.ReflectMethodTest");
            Class<?> reflectMethodTest = Class.forName(clazzNameBuff.toString());
            Object testClassInstance = reflectMethodTest.getConstructor().newInstance();
            StringBuilder methodNameBuff = new StringBuilder().append("reflectMethod");
            Method method = reflectMethodTest.getMethod(methodNameBuff.toString());
            method.invoke(testClassInstance);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException ex) {
            ex.printStackTrace();
        }
    }

    @Test
    public void test() throws IOException, ReflectiveOperationException {
        PointstoAnalyzerTester tester = new PointstoAnalyzerTester(this.getClass());
        Path outPutDirectory = tester.createTestTmpDir();
        Path reflectionConfigFilePath = tester.saveFileFromResource("/resources/reflection-config.json", outPutDirectory.resolve("reflection-config.json").normalize());
        assertNotNull("Fail to create reflection config file.", reflectionConfigFilePath);
        try {
            tester.setAnalysisArguments(tester.getTestClassName(),
                            "-H:AnalysisReflectionConfigFiles=" + reflectionConfigFilePath,
                            "-H:AnalysisTargetAppCP=" + tester.getTestClassJar());
            Class<ReflectMethodTest> refMethodClass = ReflectMethodTest.class;
            Class<ReflectFieldTest> refFieldClass = ReflectFieldTest.class;
            tester.setExpectedReachableTypes(tester.getTestClass(),
                            ReflectClassTestSuper.class,
                            refFieldClass,
                            ReflectFieldType.class,
                            refMethodClass,
                            ReflectMethodInvokeTestType.class);
            tester.setExpectedReachableMethods(refMethodClass.getDeclaredMethod("reflectMethod"));
            tester.setExpectedReachableFields(refFieldClass.getDeclaredField("reflectFieldType"));
            tester.runAnalysisAndAssert();
        } finally {
            tester.deleteTestTmpDir();
        }
    }
}

class ReflectClassTestSuper {
}

class ReflectClassTest extends ReflectClassTestSuper {
}

class ReflectFieldTest {
    public ReflectFieldType reflectFieldType = new ReflectFieldType();
}

class ReflectFieldType {
    @SuppressWarnings("unused") private int val;

    ReflectFieldType() {
        val = 0;
    }
}

class ReflectMethodTest {
    @SuppressWarnings("unused")
    ReflectMethodTest() {
    }

    @SuppressWarnings("unused")
    public void reflectMethod() {
        new ReflectMethodInvokeTestType();
    }
}

class ReflectMethodInvokeTestType {
}

class UnreachableClassCondition {
}

class UnreachableClassType {
}
