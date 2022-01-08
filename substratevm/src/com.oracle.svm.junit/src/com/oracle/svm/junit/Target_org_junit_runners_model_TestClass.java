/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.junit;

import java.lang.reflect.Constructor;

import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.junit.Assert;
import org.junit.runners.model.TestClass;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.CustomFieldValueComputer;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

@TargetClass(className = "org.junit.runners.model.TestClass", onlyWith = JUnitFeature.IsEnabled.class)
public final class Target_org_junit_runners_model_TestClass {

    public static final class OnlyConstructorComputer implements CustomFieldValueComputer {
        @Override
        public RecomputeFieldValue.ValueAvailability valueAvailability() {
            return RecomputeFieldValue.ValueAvailability.BeforeAnalysis;
        }

        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            TestClass testClass = (TestClass) receiver;
            if (testClass.getJavaClass() != null) {
                /* Make sure Class.forName works because Description.getTestClass can use it. */
                RuntimeReflection.register(testClass.getJavaClass());
                Constructor<?> constructor = testClass.getOnlyConstructor();
                RuntimeReflection.register(constructor);
                return constructor;
            } else {
                return null;
            }
        }
    }

    @Alias Class<?> clazz;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = OnlyConstructorComputer.class) Constructor<?> onlyConstructor;

    @Substitute
    public Constructor<?> getOnlyConstructor() {
        if (onlyConstructor == null && clazz != null) {
            // TestClass instances for each test class are allocated at image build time. Therefore,
            // reflective accesses to the constructors of the test classes are registered by
            // `OnlyConstructorComputer`. However, when running a @Theory, new instances of
            // TestClass are allocated at runtime. These new instances cannot use the value of
            // `onlyConstructor` computed at image build time. Therefore, in this case, we execute
            // the original method body.
            Constructor<?>[] constructors = clazz.getConstructors();
            Assert.assertEquals(1, constructors.length);
            return constructors[0];
        }
        return onlyConstructor;
    }
}
