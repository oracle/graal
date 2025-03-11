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
package com.oracle.svm.configure.test.config;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.configure.ClassNameSupport;
import com.oracle.svm.configure.test.AddExports;

import jdk.vm.ci.meta.MetaUtil;

@AddExports({"jdk.internal.vm.ci/jdk.vm.ci.meta"})
public class ClassNameSupportTest {
    @Test
    public void testNameAliases() {
        for (Class<?> clazz : List.of(Object.class, int.class, EnclosingClass.class, EnclosingClass.InnerClass.class, EnclosingClass.StaticInnerClass.class,
                        new EnclosingClass().anonymousClass().getClass(), Object[].class, int[].class, EnclosingClass.InnerClass[].class)) {
            testNameAliasesForClass(clazz);
        }
    }

    private static void testNameAliasesForClass(Class<?> clazz) {
        String typeName = clazz.getTypeName();
        String reflectionName = clazz.getName();

        Assert.assertTrue(ClassNameSupport.isValidTypeName(typeName));
        Assert.assertTrue(ClassNameSupport.isValidReflectionName(reflectionName));

        Assert.assertEquals(typeName, ClassNameSupport.reflectionNameToTypeName(reflectionName));
        Assert.assertEquals(reflectionName, ClassNameSupport.typeNameToReflectionName(typeName));

        /* Primitive classes cannot be accessed through JNI */
        if (!clazz.isPrimitive()) {
            String internalName = MetaUtil.toInternalName(reflectionName);
            String jniName = internalName.startsWith("L") ? internalName.substring(1, internalName.length() - 1) : internalName;

            Assert.assertTrue(ClassNameSupport.isValidJNIName(jniName));

            Assert.assertEquals(typeName, ClassNameSupport.jniNameToTypeName(jniName));
            Assert.assertEquals(reflectionName, ClassNameSupport.jniNameToReflectionName(jniName));
            Assert.assertEquals(jniName, ClassNameSupport.typeNameToJNIName(typeName));
            Assert.assertEquals(jniName, ClassNameSupport.reflectionNameToJNIName(reflectionName));
        }
    }
}

class EnclosingClass {
    Object anonymousClass() {
        return new Object() {
            @Override
            public String toString() {
                return null;
            }
        };
    }

    class InnerClass {
    }

    static class StaticInnerClass {
    }
}
