/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.profiles;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.Truffle;

@RunWith(SeparateClassloaderTestRunner.class)
public class LazyProfileLoadingTest {

    @Test
    public void testLazyLoading() {
        IntValueProfile.createIdentityProfile();
        assertDefaultProfile(IntValueProfile.class);

        DoubleValueProfile.createRawIdentityProfile();
        assertDefaultProfile(DoubleValueProfile.class);

        LongValueProfile.createIdentityProfile();
        assertDefaultProfile(LongValueProfile.class);

        FloatValueProfile.createRawIdentityProfile();
        assertDefaultProfile(FloatValueProfile.class);

        ByteValueProfile.createIdentityProfile();
        assertDefaultProfile(ByteValueProfile.class);

        LoopConditionProfile.createCountingProfile();
        assertDefaultProfile(LoopConditionProfile.class);

        BranchProfile.create();
        assertDefaultProfile(BranchProfile.class);

        PrimitiveValueProfile.createEqualityProfile();
        assertDefaultProfile(PrimitiveValueProfile.class);

        ConditionProfile.createBinaryProfile();
        assertLoaded(ConditionProfile.class.getName(), "Binary", "Disabled");

        ConditionProfile.createCountingProfile();
        assertLoaded(ConditionProfile.class.getName(), "Counting", "Disabled");

        ValueProfile.createClassProfile();
        assertLoaded(ValueProfile.class.getName(), "ExactClass", "Disabled");

        ValueProfile.createIdentityProfile();
        assertLoaded(ValueProfile.class.getName(), "Identity", "Disabled");
    }

    private void assertDefaultProfile(Class<?> clazz) {
        assertLoaded(clazz.getName(), "Enabled", "Disabled");
    }

    private void assertLoaded(String className, String posInnerClass, String negInnerClass) {
        String enabledClass = className + "$" + posInnerClass;
        String disabledClass = className + "$" + negInnerClass;
        if (Truffle.getRuntime().isProfilingEnabled()) {
            Assert.assertFalse(isLoaded(disabledClass));
            Assert.assertTrue(isLoaded(enabledClass));
        } else {
            Assert.assertFalse(isLoaded(enabledClass));
            Assert.assertTrue(isLoaded(disabledClass));
        }
    }

    private boolean isLoaded(String className) {
        ClassLoader classLoader = getClass().getClassLoader();
        Method m;
        try {
            m = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
            m.setAccessible(true);
            return m.invoke(classLoader, className) != null;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

}
