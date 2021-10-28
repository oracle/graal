/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.jdk11;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import javax.tools.JavaCompiler;

import org.junit.Assert;
import org.junit.Test;

public class ServiceLoaderTest {

    @Test
    public void test03JavaCompiler() {
        ServiceLoader<JavaCompiler> loader = ServiceLoader.load(JavaCompiler.class, ClassLoader.getSystemClassLoader());
        boolean foundJavacTool = false;
        List<JavaCompiler> unexpected = new ArrayList<>();

        for (JavaCompiler javaCompiler : loader) {
            if (javaCompiler.getClass().getName().equals("com.sun.tools.javac.api.JavacTool")) {
                foundJavacTool = true;
            } else {
                unexpected.add(javaCompiler);
            }
        }

        if (!unexpected.isEmpty()) {
            Assert.fail("Found unexpected JavaCompiler providers: " + unexpected);
        }
        Assert.assertTrue("Did not find JavacTool", foundJavacTool);
    }
}
