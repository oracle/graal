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
package com.oracle.svm.polyglot.scala;

// Checkstyle: stop

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;

// Checkstyle: resume
@AutomaticFeature
public class ScalaFeature implements GraalFeature {

    public static final String UNSUPPORTED_SCALA_VERSION = "This is not a supported Scala version. native-image supports Scala 2.11.x and onwards.";

    static class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(ScalaFeature.class);
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return access.findClassByName("scala.Predef") != null;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        initializeScalaEnumerations(access);
        RuntimeClassInitialization.initializeAtBuildTime("scala.Symbol");
    }

    @Override
    public void registerGraphBuilderPlugins(Providers providers, Plugins plugins, boolean analysis, boolean hosted) {
        if (hosted && analysis) {
            plugins.appendNodePlugin(new ScalaAnalysisPlugin());
        }
    }

    private static boolean isValDef(Field[] fields, Method m) {
        return Arrays.stream(fields).anyMatch(fd -> fd.getName().equals(m.getName()) && fd.getType().equals(m.getReturnType()));
    }

    /**
     * Not all Scala enumerations can be pre-initialized. For that reason we support the Scala's
     * original mechanism for initializing enumerations reflectively.
     */
    private static void initializeScalaEnumerations(BeforeAnalysisAccess beforeAnalysisAccess) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) beforeAnalysisAccess;

        Class<?> scalaEnum = access.findClassByName("scala.Enumeration");
        UserError.guarantee(scalaEnum != null, UNSUPPORTED_SCALA_VERSION);
        Class<?> scalaEnumVal = access.findClassByName("scala.Enumeration$Val");
        UserError.guarantee(scalaEnumVal != null, UNSUPPORTED_SCALA_VERSION);
        Class<?> valueClass = access.findClassByName("scala.Enumeration$Value");
        UserError.guarantee(valueClass != null, UNSUPPORTED_SCALA_VERSION);

        access.findSubclasses(scalaEnum).forEach(enumClass -> {
            /* this is based on implementation of scala.Enumeration.populateNamesMap */
            RuntimeReflection.register(enumClass.getDeclaredFields());
            // all method relevant for Enums
            Method[] relevantMethods = Arrays.stream(enumClass.getDeclaredMethods())
                            .filter(m -> m.getParameterTypes().length == 0 &&
                                            m.getDeclaringClass() != scalaEnum &&
                                            valueClass.isAssignableFrom(m.getReturnType()) &&
                                            isValDef(enumClass.getDeclaredFields(), m))
                            .toArray(Method[]::new);
            RuntimeReflection.register(relevantMethods);
            try {
                RuntimeReflection.register(scalaEnumVal.getDeclaredMethod("id"));
            } catch (NoSuchMethodException e) {
                throw UserError.abort(UNSUPPORTED_SCALA_VERSION);
            }
        });
    }

}
