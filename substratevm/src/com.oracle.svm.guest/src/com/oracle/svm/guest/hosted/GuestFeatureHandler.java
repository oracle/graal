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
package com.oracle.svm.guest.hosted;

import java.lang.reflect.Executable;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.dynamicaccess.ForeignAccess;
import org.graalvm.nativeimage.dynamicaccess.JNIAccess;
import org.graalvm.nativeimage.dynamicaccess.ReflectiveAccess;
import org.graalvm.nativeimage.dynamicaccess.ResourceAccess;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.guest.staging.feature.FeatureHandlerBase;
import com.oracle.svm.guest.staging.util.UserError;
import com.oracle.svm.shared.BuilderInvoked;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.ReflectionUtil.ReflectionUtilError;

/**
 * Registers guest features and retains their dependency-first execution order.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class GuestFeatureHandler extends FeatureHandlerBase {

    final ClassLoader[] classLoaders;
    final List<Path> applicationClassPath;
    final List<Path> applicationModulePath;
    private final GuestFeatureImpl.RegistrationAccessImpl registrationAccess;

    public GuestFeatureHandler(ClassLoader[] classLoaders, String[] applicationClassPath, String[] applicationModulePath) {
        this.classLoaders = classLoaders;
        this.applicationClassPath = Arrays.stream(applicationClassPath).map(Path::of).toList();
        this.applicationModulePath = Arrays.stream(applicationModulePath).map(Path::of).toList();
        this.registrationAccess = new GuestFeatureImpl.RegistrationAccessImpl();
        ImageSingletons.add(GuestFeatureHandler.class, this);
    }

    static GuestFeatureHandler singleton() {
        return ImageSingletons.lookup(GuestFeatureHandler.class);
    }

    @BuilderInvoked
    public boolean registerFeature(String featureName, boolean printFeatures) {
        Class<?> featureClass = findClass(featureName, true);
        if (featureClass == null) {
            return false;
        }
        registerFeature(featureClass, null, registrationAccess, registrationAccess, true, printFeatures);
        return true;
    }

    @BuilderInvoked
    public String[] getCompatibilityPublishedFeatureSingletons() {
        return compatibilityPublishedFeatureSingletons.stream().map(Class::getName).toArray(String[]::new);
    }

    public void forEachFeature(Consumer<Feature> consumer) {
        for (Feature feature : featureInstances) {
            try {
                consumer.accept(feature);
            } catch (Throwable t) {
                throw handleFeatureError(feature, t);
            }
        }
    }

    public static class GuestFeatureException extends Error {
        static final long serialVersionUID = -5418434441580632458L;

        public GuestFeatureException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    @Override
    protected Error handleFeatureError(Feature feature, Throwable throwable) {
        String featureClassName = feature.getClass().getName();
        String throwableClassName = throwable.getClass().getName();
        return new GuestFeatureException("Feature defined by %s unexpectedly failed with a(n) %s. Please report this problem to the authors of %s.".formatted(featureClassName, throwableClassName,
                        featureClassName), throwable);
    }

    @BuilderInvoked
    public void afterRegistrationForEachFeature(ReflectiveAccess reflectiveAccess, ResourceAccess resourceAccess, JNIAccess jniAccess, ForeignAccess foreignAccess) {
        GuestFeatureImpl.AfterRegistrationAccessImpl access = new GuestFeatureImpl.AfterRegistrationAccessImpl(reflectiveAccess, resourceAccess, jniAccess, foreignAccess);
        forEachFeature(feature -> feature.afterRegistration(access));
    }

    @BuilderInvoked
    public void duringSetupForEachFeature(Feature.DuringSetupAccess duringSetupAccess) {
        GuestFeatureImpl.DuringSetupAccessImpl access = new GuestFeatureImpl.DuringSetupAccessImpl(duringSetupAccess);
        forEachFeature(feature -> feature.duringSetup(access));
    }

    @BuilderInvoked
    public void beforeAnalysisForEachFeature(Feature.BeforeAnalysisAccess beforeAnalysisAccess, Feature.QueryReachabilityAccess queryReachabilityAccess) {
        GuestFeatureImpl.BeforeAnalysisAccessImpl access = new GuestFeatureImpl.BeforeAnalysisAccessImpl(beforeAnalysisAccess, queryReachabilityAccess);
        forEachFeature(feature -> feature.beforeAnalysis(access));
    }

    @BuilderInvoked
    public void duringAnalysisForEachFeature(Feature.DuringAnalysisAccess duringAnalysisAccess) {
        GuestFeatureImpl.DuringAnalysisAccessImpl access = new GuestFeatureImpl.DuringAnalysisAccessImpl(duringAnalysisAccess);
        forEachFeature(feature -> feature.duringAnalysis(access));
    }

    @BuilderInvoked
    public static void invokeReachabilityHandler(Consumer<Feature.DuringAnalysisAccess> callback, Feature.DuringAnalysisAccess duringAnalysisAccess) {
        GuestFeatureImpl.DuringAnalysisAccessImpl access = new GuestFeatureImpl.DuringAnalysisAccessImpl(duringAnalysisAccess);
        callback.accept(access);
    }

    @BuilderInvoked
    public static void invokeMethodOverrideReachabilityHandler(BiConsumer<Feature.DuringAnalysisAccess, Executable> callback, Feature.DuringAnalysisAccess duringAnalysisAccess, Executable method) {
        GuestFeatureImpl.DuringAnalysisAccessImpl access = new GuestFeatureImpl.DuringAnalysisAccessImpl(duringAnalysisAccess);
        callback.accept(access, method);
    }

    @BuilderInvoked
    public static void invokeSubtypeReachabilityHandler(BiConsumer<Feature.DuringAnalysisAccess, Class<?>> callback, Feature.DuringAnalysisAccess duringAnalysisAccess, Class<?> type) {
        GuestFeatureImpl.DuringAnalysisAccessImpl access = new GuestFeatureImpl.DuringAnalysisAccessImpl(duringAnalysisAccess);
        callback.accept(access, type);
    }

    @BuilderInvoked
    public void afterAnalysisForEachFeature(Feature.QueryReachabilityAccess queryReachabilityAccess) {
        GuestFeatureImpl.AfterAnalysisAccessImpl access = new GuestFeatureImpl.AfterAnalysisAccessImpl(queryReachabilityAccess);
        forEachFeature(feature -> feature.afterAnalysis(access));
    }

    @BuilderInvoked
    public void onAnalysisExitForEachFeature() {
        GuestFeatureImpl.OnAnalysisExitAccessImpl access = new GuestFeatureImpl.OnAnalysisExitAccessImpl();
        forEachFeature(feature -> feature.onAnalysisExit(access));
    }

    @BuilderInvoked
    public void beforeUniverseBuildingForEachFeature() {
        GuestFeatureImpl.BeforeUniverseBuildingAccessImpl access = new GuestFeatureImpl.BeforeUniverseBuildingAccessImpl();
        forEachFeature(feature -> feature.beforeUniverseBuilding(access));
    }

    @BuilderInvoked
    public void beforeCompilationForEachFeature(Feature.CompilationAccess compilationAccess) {
        GuestFeatureImpl.BeforeCompilationAccessImpl access = new GuestFeatureImpl.BeforeCompilationAccessImpl(compilationAccess);
        forEachFeature(feature -> feature.beforeCompilation(access));
    }

    @BuilderInvoked
    public void afterCompilationForEachFeature(Feature.CompilationAccess compilationAccess) {
        GuestFeatureImpl.AfterCompilationAccessImpl access = new GuestFeatureImpl.AfterCompilationAccessImpl(compilationAccess);
        forEachFeature(feature -> feature.afterCompilation(access));
    }

    @BuilderInvoked
    public void beforeHeapLayoutForEachFeature(Feature.CompilationAccess compilationAccess) {
        GuestFeatureImpl.BeforeHeapLayoutAccessImpl access = new GuestFeatureImpl.BeforeHeapLayoutAccessImpl(compilationAccess);
        forEachFeature(feature -> feature.beforeHeapLayout(access));
    }

    @BuilderInvoked
    public void afterHeapLayoutForEachFeature() {
        GuestFeatureImpl.AfterHeapLayoutAccessImpl access = new GuestFeatureImpl.AfterHeapLayoutAccessImpl();
        forEachFeature(feature -> feature.afterHeapLayout(access));
    }

    @BuilderInvoked
    public void beforeImageWriteForEachFeature() {
        GuestFeatureImpl.BeforeImageWriteAccessImpl access = new GuestFeatureImpl.BeforeImageWriteAccessImpl();
        forEachFeature(feature -> feature.beforeImageWrite(access));
    }

    @BuilderInvoked
    public void afterImageWriteForEachFeature(String imagePath) {
        GuestFeatureImpl.AfterImageWriteAccessImpl access = new GuestFeatureImpl.AfterImageWriteAccessImpl(imagePath);
        forEachFeature(feature -> feature.afterImageWrite(access));
    }

    @BuilderInvoked
    public void cleanupForEachFeature() {
        forEachFeature(Feature::cleanup);
    }

    @Override
    protected boolean isInternalFeature(Feature feature) {
        return false;
    }

    @Override
    protected RuntimeException invalidFeatureClass(Class<?> featureClass) {
        return new IllegalArgumentException("Class does not implement %s: %s".formatted(Feature.class.getName(), featureClass.getName()));
    }

    @Override
    protected Feature instantiateFeature(Class<?> featureClass) {
        try {
            return (Feature) ReflectionUtil.newInstance(featureClass);
        } catch (ReflectionUtilError ex) {
            throw UserError.abort("Error instantiating Feature class %s. Ensure the class is not abstract and has a no-argument constructor.", featureClass.getTypeName());
        }
    }

    private Class<?> findClass(String className, boolean initialize) {
        for (ClassLoader classLoader : classLoaders) {
            try {
                return Class.forName(className, initialize, classLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

}
