/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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
package com.oracle.svm.test;

import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.substitute.SubstitutionReflectivityFilter;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.junit.Test;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

/**
 * Tests the {@link RuntimeReflection}.
 */
public class ReflectionRegistrationTest {

    public static class TestFeature implements Feature {

        @SuppressWarnings("unused")//
        int unusedVariableOne = 1;
        @SuppressWarnings("unused")//
        int unusedVariableTwo = 2;

        @Override
        public void beforeAnalysis(final BeforeAnalysisAccess access) {
            try {
                RuntimeReflection.register((Class<?>) null);
                assert false;
            } catch (NullPointerException e) {
                assert e.getMessage().startsWith("Cannot register null value");
            }
            try {
                RuntimeReflection.register((Executable) null);
                assert false;
            } catch (NullPointerException e) {
                assert e.getMessage().startsWith("Cannot register null value");
            }
            try {
                RuntimeReflection.register((Field) null);
                assert false;
            } catch (NullPointerException e) {
                assert e.getMessage().startsWith("Cannot register null value");
            }

            try {
                ImageSingletons.lookup(RuntimeReflectionSupport.class).register(null, this.getClass());
                assert false;
            } catch (NullPointerException e) {
                assert e.getMessage().startsWith("Cannot use null value");
            }

            try {
                ImageSingletons.lookup(RuntimeReflectionSupport.class).register(null, true, this.getClass().getMethods());
                assert false;
            } catch (NullPointerException e) {
                assert e.getMessage().startsWith("Cannot use null value");
            }

            try {
                ImageSingletons.lookup(RuntimeReflectionSupport.class).register(null, true, this.getClass().getFields());
                assert false;
            } catch (NullPointerException e) {
                assert e.getMessage().startsWith("Cannot use null value");
            }

            FeatureImpl.BeforeAnalysisAccessImpl impl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
            try {
                SubstitutionReflectivityFilter.shouldExclude((Class<?>) null, impl.getMetaAccess(), impl.getUniverse());
                assert false;
            } catch (NullPointerException e) {
                // expected
            }
            try {
                SubstitutionReflectivityFilter.shouldExclude((Executable) null, impl.getMetaAccess(), impl.getUniverse());
                assert false;
            } catch (NullPointerException e) {
                // expected
            }
            try {
                SubstitutionReflectivityFilter.shouldExclude((Field) null, impl.getMetaAccess(), impl.getUniverse());
                assert false;
            } catch (NullPointerException e) {
                // expected
            }
        }

    }

    @Test
    public void test() {
        // nothing to do
    }
}
