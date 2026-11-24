/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.reflect;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.test.NativeImageBuildArgs;

/**
 * Registers a method and a field of a supertype for reflection only after subtypes that override or
 * hide them are reachable. The subtype members are not registered themselves, so the image must
 * record them as hiding members, and a lookup on the subtype must not return the supertype member.
 *
 * With this order, the check for hiding members is scheduled on another analysis thread while the
 * supertype member is being registered, and it must not miss that registration. The check only runs
 * without exact reachability metadata, which is the default for this test image.
 */
@NativeImageBuildArgs("--features=com.oracle.svm.test.reflect.HidingMemberRegistrationTest$TestFeature")
public class HidingMemberRegistrationTest {

    private static final String MEMBER_NAME = "member";

    public static class MethodBase {
        public int member() {
            return 1;
        }
    }

    public static class MethodChild extends MethodBase {
        @Override
        public int member() {
            return 2;
        }
    }

    public static class FieldBase {
        public int member = 1;
    }

    public static class FieldChild extends FieldBase {
        @SuppressWarnings("hiding")//
        public int member = 2;
    }

    public static class TestFeature implements Feature {
        private boolean registered;

        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            access.registerAsUsed(MethodChild.class);
            access.registerAsUsed(FieldChild.class);
        }

        @Override
        public void duringAnalysis(DuringAnalysisAccess access) {
            if (!registered) {
                /* The subtypes are reachable by now. */
                registered = true;
                try {
                    RuntimeReflection.register(MethodBase.class.getDeclaredMethod(MEMBER_NAME));
                    RuntimeReflection.register(FieldBase.class.getDeclaredField(MEMBER_NAME));
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
                access.requireAnalysisIteration();
            }
        }
    }

    @Test
    public void registeredMembersAreAccessible() throws ReflectiveOperationException {
        Assert.assertEquals(2, MethodBase.class.getMethod(MEMBER_NAME).invoke(new MethodChild()));
        Assert.assertEquals(1, FieldBase.class.getField(MEMBER_NAME).getInt(new FieldChild()));
    }

    @Test
    public void subtypeMethodHidesRegisteredMethod() {
        Class<?> declaringClass;
        try {
            declaringClass = MethodChild.class.getMethod(MEMBER_NAME).getDeclaringClass();
        } catch (NoSuchMethodException e) {
            declaringClass = null;
        }
        Assert.assertNotSame("MethodChild.getMethod() returned the overridden MethodBase.member()", MethodBase.class, declaringClass);
    }

    @Test
    public void subtypeFieldHidesRegisteredField() {
        Class<?> declaringClass;
        try {
            declaringClass = FieldChild.class.getField(MEMBER_NAME).getDeclaringClass();
        } catch (NoSuchFieldException e) {
            declaringClass = null;
        }
        Assert.assertNotSame("FieldChild.getField() returned the hidden FieldBase.member", FieldBase.class, declaringClass);
    }
}
