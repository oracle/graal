/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.test.spec;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.graalvm.nativeimage.AnnotationAccess;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.netbeans.html.json.tck.KOTest;

import com.oracle.svm.hosted.webimage.test.util.JTTTestSuite;
import com.oracle.svm.webimage.jtt.javascriptbody.HeadlessTckTest;

/**
 * This test is currently broken (see GR-57165).
 */
@RunWith(Parameterized.class)
public class JS_JTT_TCK_Headless extends JTTTestSuite {

    @Parameters(name = "{0}.{1}")
    public static Collection<String[]> data() {
        List<String[]> list = new ArrayList<>();

        for (Class<?> clazz : HeadlessTckTest.findTestClasses()) {
            String className = clazz.getCanonicalName();
            for (Method m : clazz.getMethods()) {
                final KOTest a = AnnotationAccess.getAnnotation(m, KOTest.class);
                if (a != null) {
                    list.add(new String[]{className, m.getName()});
                }
            }
        }

        return list;
    }

    @Parameter public String className;

    @Parameter(1) public String methodName;

    /**
     * Compile our TCK test class only once. Each test invokes it with different arguments.
     */
    @BeforeClass
    public static void setupClass() {
        compileToJS(HeadlessTckTest.class);
    }

    @Test
    public void test() {
        testFileAgainstNoBuild(new String[]{"OK"}, className, methodName);
    }
}
