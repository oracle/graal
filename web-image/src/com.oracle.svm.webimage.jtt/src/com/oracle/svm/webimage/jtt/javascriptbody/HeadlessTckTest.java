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
package com.oracle.svm.webimage.jtt.javascriptbody;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.netbeans.html.json.tck.JavaScriptTCK;
import org.netbeans.html.json.tck.KOTest;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateOptions;

import net.java.html.js.tests.ExposedPropertiesTest;
import net.java.html.js.tests.GCBodyTest;

public final class HeadlessTckTest extends JavaScriptTCK {

    @NeverInline(value = "Test")
    public static void main(String[] args) throws ReflectiveOperationException {
        String className = args[0];
        String methodName = args[1];

        Class<?> clazz = Class.forName(className);
        Method m = clazz.getMethod(methodName);

        Object instance = clazz.getDeclaredConstructor().newInstance();
        m.invoke(instance);
        System.out.println("OK");
    }

    public static Class<?>[] findTestClasses() {
        List<Class<?>> candidates = new ArrayList<>(Arrays.asList(JavaScriptTCK.testClasses()));

        candidates.removeIf(c -> c == GCBodyTest.class || c == ExposedPropertiesTest.class);

        return candidates.toArray(new Class<?>[0]);
    }

    public static final class SetupReflectiveTests implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            if (SubstrateOptions.Class.getValue().equals(HeadlessTckTest.class.getName())) {
                RuntimeReflection.registerForReflectiveInstantiation(findTestClasses());
                RuntimeReflection.register(findTestClasses());
                for (Class<?> c : findTestClasses()) {
                    for (Method m : c.getMethods()) {
                        final KOTest a = AnnotationAccess.getAnnotation(m, KOTest.class);
                        if (a != null) {
                            RuntimeReflection.register(m);
                        }
                    }
                }
            }
        }
    }
}
