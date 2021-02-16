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

import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.junit.runner.Description;
import org.junit.runner.Request;

import com.oracle.svm.reflect.hosted.ReflectionFeature;

public final class JUnitFeature implements Feature {

    public static class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(JUnitFeature.class);
        }
    }

    public static boolean isEnabledAndIncludesClass(Class<?> clazz) {
        if (ImageSingletons.contains(SVMJUnitRunner.class)) {
            Request request = ImageSingletons.lookup(SVMJUnitRunner.class).getJUnitRequest();
            return includesClass(request.getRunner().getDescription(), clazz);
        }
        return false;
    }

    private static boolean includesClass(Description dn, Class<?> clazz) {
        return clazz.equals(dn.getTestClass()) || dn.getChildren().stream().anyMatch(child -> includesClass(child, clazz));
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(ReflectionFeature.class);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        RuntimeClassInitialization.initializeAtBuildTime(SVMJUnitRunner.class);
        RuntimeClassInitialization.initializeAtBuildTime(org.junit.runner.Description.class);
        RuntimeClassInitialization.initializeAtBuildTime(org.junit.runners.ParentRunner.class);
        RuntimeClassInitialization.initializeAtBuildTime(org.junit.runners.Parameterized.class);
        RuntimeClassInitialization.initializeAtBuildTime("jcp.xml.dsig.internal.dom.XMLDSigRI");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.runners.ParentRunner$1");
        RuntimeClassInitialization.initializeAtBuildTime(org.junit.runner.Request.class);
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.runner.Request$1");
        RuntimeClassInitialization.initializeAtBuildTime(org.junit.runner.Runner.class);
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.runner.Runner$1");
        RuntimeClassInitialization.initializeAtBuildTime(org.junit.runner.JUnitCore.class);
        RuntimeClassInitialization.initializeAtBuildTime(org.junit.runners.Suite.class);
        RuntimeClassInitialization.initializeAtBuildTime(org.junit.runners.BlockJUnit4ClassRunner.class);
        RuntimeClassInitialization.initializeAtBuildTime(org.junit.runners.model.FrameworkMethod.class);
        RuntimeClassInitialization.initializeAtBuildTime(org.junit.runners.model.TestClass.class);
        RuntimeClassInitialization.initializeAtBuildTime(org.junit.runners.model.FrameworkField.class);
        RuntimeClassInitialization.initializeAtBuildTime(org.junit.Assert.class);
        RuntimeClassInitialization.initializeAtBuildTime(com.oracle.mxtool.junit.MxJUnitRequest.class);
        SVMJUnitRunner svmRunner = new SVMJUnitRunner(access);
        ImageSingletons.add(SVMJUnitRunner.class, svmRunner);
    }
}
