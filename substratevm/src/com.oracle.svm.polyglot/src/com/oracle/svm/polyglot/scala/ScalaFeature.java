/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.polyglot.scala;

// Checkstyle: stop

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;

import jdk.vm.ci.meta.MetaAccessProvider;

// Checkstyle: resume
@AutomaticFeature
public class ScalaFeature implements GraalFeature {

    static class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(ScalaFeature.class);
        }
    }

    static class HasMangledPopulateNamesMapMethod implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(ScalaFeature.class) && ImageSingletons.lookup(ScalaFeature.class).hasMangledPopulatesNameMapMethod;
        }
    }

    static class HasPopulateNamesMapMethod implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(ScalaFeature.class) && !ImageSingletons.lookup(ScalaFeature.class).hasMangledPopulatesNameMapMethod;
        }
    }

    private boolean hasMangledPopulatesNameMapMethod;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return access.findClassByName("scala.Predef") != null;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        Class<?> scalaEnum = access.findClassByName("scala.Enumeration");
        try {
            hasMangledPopulatesNameMapMethod = scalaEnum.getMethod("scala$Enumeration$$populateNameMap") != null;
        } catch (NoSuchMethodException e) {
            /* ignore, does not have a method */
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        initializeScalaEnumerations(access);
    }

    @Override
    public void registerNodePlugins(MetaAccessProvider metaAccess, Plugins plugins, boolean analysis, boolean hosted) {
        if (hosted && analysis) {
            plugins.appendNodePlugin(new ScalaAnalysisPlugin());
        }
    }

    private void initializeScalaEnumerations(BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;
        if (hasMangledPopulatesNameMapMethod) {
            Class<?> scalaEnum = access.findClassByName("scala.Enumeration");
            Stream<Field> scalaModuleFields = access.findSubclasses(scalaEnum).stream().map(ScalaFeature::getModuleField).filter(Objects::nonNull);
            scalaModuleFields.forEach(ScalaFeature::preInitializeEnum);
        }
    }

    private static void preInitializeEnum(Field enumField) {
        /* initialize the enumeration */
        try {
            Object instance = enumField.get(null);
            Method populateNameMapMethod = enumField.getDeclaringClass().getMethod("scala$Enumeration$$populateNameMap");
            populateNameMapMethod.setAccessible(true);
            populateNameMapMethod.invoke(instance);
            populateNameMapMethod.setAccessible(false);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw UserError.abort("scala.Enumeration does not have a method populateNameMap. Check that you are using a compatible version of Scala.");
        }
    }

    private static Field getModuleField(Class<?> clazz) {
        try {
            return clazz.getField("MODULE$");
        } catch (NoSuchFieldException e1) {
            return null; // not a module
        }
    }
}
