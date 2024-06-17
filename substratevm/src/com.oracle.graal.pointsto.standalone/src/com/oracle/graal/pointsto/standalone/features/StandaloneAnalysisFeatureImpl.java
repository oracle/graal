/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.features;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.standalone.StandaloneHost;

import jdk.graal.compiler.debug.DebugContext;

public class StandaloneAnalysisFeatureImpl {
    public abstract static class FeatureAccessImpl implements Feature.FeatureAccess {

        protected final StandaloneAnalysisFeatureManager featureManager;
        protected final ClassLoader analysisClassLoader;
        protected final DebugContext debugContext;

        FeatureAccessImpl(StandaloneAnalysisFeatureManager featureManager, ClassLoader classLoader, DebugContext debugContext) {
            this.featureManager = featureManager;
            this.analysisClassLoader = classLoader;
            this.debugContext = debugContext;
        }

        @Override
        public Class<?> findClassByName(String className) {
            try {
                return Class.forName(className, false, analysisClassLoader);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        public DebugContext getDebugContext() {
            return debugContext;
        }

        @Override
        public List<Path> getApplicationClassPath() {
            return null;
        }

        @Override
        public List<Path> getApplicationModulePath() {
            return null;
        }

        @Override
        public ClassLoader getApplicationClassLoader() {
            return analysisClassLoader;
        }
    }

    abstract static class AnalysisAccessBase extends FeatureAccessImpl {

        protected final BigBang bb;

        AnalysisAccessBase(StandaloneAnalysisFeatureManager featureManager, ClassLoader imageClassLoader, BigBang bb, DebugContext debugContext) {
            super(featureManager, imageClassLoader, debugContext);
            this.bb = bb;
        }

        public BigBang getBigBang() {
            return bb;
        }

        public AnalysisUniverse getUniverse() {
            return bb.getUniverse();
        }

        public AnalysisMetaAccess getMetaAccess() {
            return bb.getMetaAccess();
        }

        public boolean isReachable(Class<?> clazz) {
            return isReachable(getMetaAccess().lookupJavaType(clazz));
        }

        public boolean isReachable(AnalysisType type) {
            return type.isReachable();
        }

        public boolean isReachable(Field field) {
            return isReachable(getMetaAccess().lookupJavaField(field));
        }

        public boolean isReachable(AnalysisField field) {
            return field.isAccessed();
        }

        public boolean isReachable(Executable method) {
            return isReachable(getMetaAccess().lookupJavaMethod(method));
        }

        public boolean isReachable(AnalysisMethod method) {
            return method.isReachable();
        }

        public Set<Class<?>> reachableSubtypes(Class<?> baseClass) {
            return reachableSubtypes(getMetaAccess().lookupJavaType(baseClass)).stream()
                            .map(AnalysisType::getJavaClass).collect(Collectors.toCollection(LinkedHashSet::new));
        }

        Set<AnalysisType> reachableSubtypes(AnalysisType baseType) {
            Set<AnalysisType> result = baseType.getAllSubtypes();
            result.removeIf(t -> !isReachable(t));
            return result;
        }

        public Set<Executable> reachableMethodOverrides(Executable baseMethod) {
            return reachableMethodOverrides(getMetaAccess().lookupJavaMethod(baseMethod)).stream()
                            .map(AnalysisMethod::getJavaMethod)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        Set<AnalysisMethod> reachableMethodOverrides(AnalysisMethod baseMethod) {
            return AnalysisUniverse.getMethodImplementations(baseMethod, true);
        }
    }

    public static class BeforeAnalysisAccessImpl extends AnalysisAccessBase implements Feature.BeforeAnalysisAccess {

        public BeforeAnalysisAccessImpl(StandaloneAnalysisFeatureManager featureManager, ClassLoader imageClassLoader, BigBang bb, DebugContext debugContext) {
            super(featureManager, imageClassLoader, bb, debugContext);
        }

        @Override
        public void registerAsUsed(Class<?> clazz) {
            registerAsUsed(getMetaAccess().lookupJavaType(clazz), "registered from Feature API");
        }

        public void registerAsUsed(AnalysisType aType, Object reason) {
            aType.registerAsReachable(reason);
        }

        @Override
        public void registerAsInHeap(Class<?> clazz) {
            registerAsInHeap(getMetaAccess().lookupJavaType(clazz), "registered from Feature API");
        }

        public void registerAsInHeap(AnalysisType aType, Object reason) {
            aType.registerAsInstantiated(reason);
        }

        @Override
        public void registerAsUnsafeAllocated(Class<?> type) {
            getMetaAccess().lookupJavaType(type).registerAsUnsafeAllocated("registered from Feature API");
        }

        @Override
        public void registerAsAccessed(Field field) {
            registerAsAccessed(getMetaAccess().lookupJavaField(field), "registered from Feature API");
        }

        public void registerAsAccessed(AnalysisField aField, Object reason) {
            aField.registerAsAccessed(reason);
        }

        public void registerAsRead(Field field) {
            registerAsRead(getMetaAccess().lookupJavaField(field));
        }

        public void registerAsRead(AnalysisField aField) {
            aField.registerAsRead(null);
        }

        @Override
        public void registerAsUnsafeAccessed(Field field) {
            registerAsUnsafeAccessed(getMetaAccess().lookupJavaField(field), "registered from Feature API");
        }

        public boolean registerAsUnsafeAccessed(AnalysisField aField, Object reason) {
            if (!aField.isUnsafeAccessed()) {
                aField.registerAsUnsafeAccessed(reason);
                return true;
            }
            return false;
        }

        public void registerAsFrozenUnsafeAccessed(Field field) {
            registerAsFrozenUnsafeAccessed(getMetaAccess().lookupJavaField(field));
        }

        public void registerAsFrozenUnsafeAccessed(AnalysisField aField) {
            aField.registerAsFrozenUnsafeAccessed();
            registerAsUnsafeAccessed(aField, "registered from standalone feature");
        }

        public void registerAsUnsafeAccessed(Field field, Object reason) {
            registerAsUnsafeAccessed(getMetaAccess().lookupJavaField(field), reason);
        }

        public void registerAsInvoked(Executable method, boolean invokeSpecial, Object reason) {
            registerAsInvoked(getMetaAccess().lookupJavaMethod(method), invokeSpecial, reason);
        }

        public void registerAsInvoked(AnalysisMethod aMethod, boolean invokeSpecial, Object reason) {
            bb.addRootMethod(aMethod, invokeSpecial, reason);
        }

        public void registerUnsafeFieldsRecomputed(Class<?> clazz) {
            getMetaAccess().lookupJavaType(clazz).registerUnsafeFieldsRecomputed();
        }

        public StandaloneHost getHostVM() {
            return (StandaloneHost) bb.getHostVM();
        }

        @Override
        public void registerReachabilityHandler(Consumer<Feature.DuringAnalysisAccess> callback, Object... elements) {
        }

        @Override
        public void registerMethodOverrideReachabilityHandler(BiConsumer<Feature.DuringAnalysisAccess, Executable> callback, Executable baseMethod) {
        }

        @Override
        public void registerSubtypeReachabilityHandler(BiConsumer<Feature.DuringAnalysisAccess, Class<?>> callback, Class<?> baseClass) {
        }

        @Override
        public void registerClassInitializerReachabilityHandler(Consumer<Feature.DuringAnalysisAccess> callback, Class<?> clazz) {
        }

        @Override
        public void registerFieldValueTransformer(Field field, FieldValueTransformer transformer) {
        }
    }

    public static class DuringAnalysisAccessImpl extends BeforeAnalysisAccessImpl implements Feature.DuringAnalysisAccess {

        private boolean requireAnalysisIteration;

        public DuringAnalysisAccessImpl(StandaloneAnalysisFeatureManager featureManager, ClassLoader imageClassLoader, BigBang bb, DebugContext debugContext) {
            super(featureManager, imageClassLoader, bb, debugContext);
        }

        @Override
        public void requireAnalysisIteration() {
            requireAnalysisIteration = true;
        }

        public boolean getAndResetRequireAnalysisIteration() {
            boolean result = requireAnalysisIteration;
            requireAnalysisIteration = false;
            return result;
        }
    }

    public static class OnAnalysisExitAccessImpl extends AnalysisAccessBase implements Feature.OnAnalysisExitAccess {

        private final Map<Class<? extends Feature>, Object> analysisResults = new HashMap<>();

        public OnAnalysisExitAccessImpl(StandaloneAnalysisFeatureManager featureManager, ClassLoader imageClassLoader, BigBang bb, DebugContext debugContext) {
            super(featureManager, imageClassLoader, bb, debugContext);
        }

        public void setAnalysisResult(Class<? extends Feature> feature, Object result) {
            analysisResults.put(feature, result);
        }

        public Object getResult(Class<? extends Feature> feature) {
            return analysisResults.get(feature);
        }
    }
}
