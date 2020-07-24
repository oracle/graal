/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.UnsafePartitionKind;
import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.LinkerInvocation;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.code.CompilationInfoSupport;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.code.SharedRuntimeConfigurationBuilder;
import com.oracle.svm.hosted.image.AbstractBootImage;
import com.oracle.svm.hosted.image.AbstractBootImage.NativeImageKind;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.option.HostedOptionProvider;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

@SuppressWarnings("deprecation")
public class FeatureImpl {

    public abstract static class FeatureAccessImpl implements Feature.FeatureAccess {

        protected final FeatureHandler featureHandler;
        protected final ImageClassLoader imageClassLoader;
        protected final DebugContext debugContext;

        FeatureAccessImpl(FeatureHandler featureHandler, ImageClassLoader imageClassLoader, DebugContext debugContext) {
            this.featureHandler = featureHandler;
            this.imageClassLoader = imageClassLoader;
            this.debugContext = debugContext;
        }

        public ImageClassLoader getImageClassLoader() {
            return imageClassLoader;
        }

        @Override
        public Class<?> findClassByName(String className) {
            return imageClassLoader.findClassByName(className, false);
        }

        public <T> List<Class<? extends T>> findSubclasses(Class<T> baseClass) {
            return imageClassLoader.findSubclasses(baseClass, false);
        }

        public List<Class<?>> findAnnotatedClasses(Class<? extends Annotation> annotationClass) {
            return imageClassLoader.findAnnotatedClasses(annotationClass, false);
        }

        public List<Method> findAnnotatedMethods(Class<? extends Annotation> annotationClass) {
            return imageClassLoader.findAnnotatedMethods(annotationClass);
        }

        public List<Field> findAnnotatedFields(Class<? extends Annotation> annotationClass) {
            return imageClassLoader.findAnnotatedFields(annotationClass);
        }

        public FeatureHandler getFeatureHandler() {
            return featureHandler;
        }

        public DebugContext getDebugContext() {
            return debugContext;
        }

        @Override
        public List<Path> getApplicationClassPath() {
            return imageClassLoader.getClassLoader().imagecp;
        }

        @Override
        public List<Path> getApplicationModulePath() {
            /*
             * GR-16855: The image generator does not yet support a module path. This method will
             * return the proper module path when module support gets implemented.
             */
            return Collections.emptyList();
        }

        @Override
        public ClassLoader getApplicationClassLoader() {
            return imageClassLoader.getClassLoader();
        }
    }

    public static class IsInConfigurationAccessImpl extends FeatureAccessImpl implements Feature.IsInConfigurationAccess {
        IsInConfigurationAccessImpl(FeatureHandler featureHandler, ImageClassLoader imageClassLoader, DebugContext debugContext) {
            super(featureHandler, imageClassLoader, debugContext);
        }
    }

    public static class AfterRegistrationAccessImpl extends FeatureAccessImpl implements Feature.AfterRegistrationAccess {
        private final MetaAccessProvider metaAccess;
        private Pair<Method, CEntryPointData> mainEntryPoint;

        AfterRegistrationAccessImpl(FeatureHandler featureHandler, ImageClassLoader imageClassLoader, MetaAccessProvider metaAccess, Pair<Method, CEntryPointData> mainEntryPoint,
                        DebugContext debugContext) {
            super(featureHandler, imageClassLoader, debugContext);
            this.metaAccess = metaAccess;
            this.mainEntryPoint = mainEntryPoint;
        }

        public MetaAccessProvider getMetaAccess() {
            return metaAccess;
        }

        public void setMainEntryPoint(Pair<Method, CEntryPointData> mainEntryPoint) {
            this.mainEntryPoint = mainEntryPoint;
        }

        public Pair<Method, CEntryPointData> getMainEntryPoint() {
            return mainEntryPoint;
        }
    }

    abstract static class AnalysisAccessBase extends FeatureAccessImpl {

        protected final Inflation bb;

        AnalysisAccessBase(FeatureHandler featureHandler, ImageClassLoader imageClassLoader, Inflation bb, DebugContext debugContext) {
            super(featureHandler, imageClassLoader, debugContext);
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
            return type.isInTypeCheck() || type.isInstantiated();
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
            return method.isImplementationInvoked();
        }

        public Set<Class<?>> reachableSubtypes(Class<?> baseClass) {
            return reachableSubtypes(getMetaAccess().lookupJavaType(baseClass)).stream()
                            .map(AnalysisType::getJavaClass).collect(Collectors.toCollection(LinkedHashSet::new));
        }

        Set<AnalysisType> reachableSubtypes(AnalysisType baseType) {
            Set<AnalysisType> result = getUniverse().getSubtypes(baseType);
            result.removeIf(t -> !isReachable(t));
            return result;
        }

        public Set<Executable> reachableMethodOverrides(Executable baseMethod) {
            return reachableMethodOverrides(getMetaAccess().lookupJavaMethod(baseMethod)).stream()
                            .map(AnalysisMethod::getJavaMethod).collect(Collectors.toCollection(LinkedHashSet::new));
        }

        Set<AnalysisMethod> reachableMethodOverrides(AnalysisMethod baseMethod) {
            return getUniverse().getMethodImplementations(getBigBang(), baseMethod);
        }
    }

    public static class DuringSetupAccessImpl extends AnalysisAccessBase implements Feature.DuringSetupAccess {

        DuringSetupAccessImpl(FeatureHandler featureHandler, ImageClassLoader imageClassLoader, Inflation bb, DebugContext debugContext) {
            super(featureHandler, imageClassLoader, bb, debugContext);
        }

        @Override
        public void registerObjectReplacer(Function<Object, Object> replacer) {
            getUniverse().registerObjectReplacer(replacer);
        }

        public void registerSubstitutionProcessor(SubstitutionProcessor substitution) {
            getUniverse().registerFeatureSubstitution(substitution);
        }

        public void registerNativeSubstitutionProcessor(SubstitutionProcessor substitution) {
            getUniverse().registerFeatureNativeSubstitution(substitution);
        }

        /**
         * Registers a listener that is notified for every class that is identified as reachable by
         * the analysis. The newly discovered class is passed as a parameter to the listener.
         * <p>
         * The listener is called during the analysis, at similar times as
         * {@link Feature#duringAnalysis}. The first argument is a {@link DuringAnalysisAccess
         * access} object that allows to query the analysis state. If the handler performs changes
         * the analysis universe, e.g., makes new types or methods reachable, it needs to call
         * {@link DuringAnalysisAccess#requireAnalysisIteration()} to trigger a new iteration of the
         * analysis.
         *
         * @since 19.0
         */
        public void registerClassReachabilityListener(BiConsumer<DuringAnalysisAccess, Class<?>> listener) {
            getHostVM().registerClassReachabilityListener(listener);
        }

        public SVMHost getHostVM() {
            return bb.getHostVM();
        }
    }

    public static class BeforeAnalysisAccessImpl extends AnalysisAccessBase implements Feature.BeforeAnalysisAccess {

        private final NativeLibraries nativeLibraries;

        BeforeAnalysisAccessImpl(FeatureHandler featureHandler, ImageClassLoader imageClassLoader, Inflation bb, NativeLibraries nativeLibraries, DebugContext debugContext) {
            super(featureHandler, imageClassLoader, bb, debugContext);
            this.nativeLibraries = nativeLibraries;
        }

        public NativeLibraries getNativeLibraries() {
            return nativeLibraries;
        }

        @Override
        public void registerAsUsed(Class<?> clazz) {
            registerAsUsed(getMetaAccess().lookupJavaType(clazz));
        }

        public void registerAsUsed(AnalysisType aType) {
            aType.registerAsInTypeCheck();
        }

        @Override
        public void registerAsInHeap(Class<?> clazz) {
            registerAsInHeap(getMetaAccess().lookupJavaType(clazz));
        }

        public void registerAsInHeap(AnalysisType aType) {
            aType.registerAsInHeap();
        }

        @Override
        public void registerAsAccessed(Field field) {
            registerAsAccessed(getMetaAccess().lookupJavaField(field));
        }

        public void registerAsAccessed(AnalysisField aField) {
            aField.registerAsAccessed();
        }

        public void registerAsRead(AnalysisField aField) {
            aField.registerAsRead(null);
        }

        @Override
        public void registerAsUnsafeAccessed(Field field) {
            registerAsUnsafeAccessed(getMetaAccess().lookupJavaField(field));
        }

        public boolean registerAsUnsafeAccessed(AnalysisField aField) {
            if (!aField.isUnsafeAccessed()) {
                /* Register the field as unsafe accessed. */
                aField.registerAsAccessed();
                aField.registerAsUnsafeAccessed(bb.getUniverse());
                /* Force the update of registered unsafe loads and stores. */
                bb.forceUnsafeUpdate(aField);
                return true;
            }
            return false;
        }

        public void registerAsFrozenUnsafeAccessed(Field field) {
            registerAsFrozenUnsafeAccessed(getMetaAccess().lookupJavaField(field));
        }

        public void registerAsFrozenUnsafeAccessed(AnalysisField aField) {
            aField.setUnsafeFrozenTypeState(true);
            registerAsUnsafeAccessed(aField);
        }

        public void registerAsUnsafeAccessed(Field field, UnsafePartitionKind partitionKind) {
            registerAsUnsafeAccessed(getMetaAccess().lookupJavaField(field), partitionKind);
        }

        public void registerAsUnsafeAccessed(AnalysisField aField, UnsafePartitionKind partitionKind) {
            if (!aField.isUnsafeAccessed()) {
                /* Register the field as unsafe accessed. */
                aField.registerAsAccessed();
                aField.registerAsUnsafeAccessed(bb.getUniverse(), partitionKind);
                /* Force the update of registered unsafe loads and stores. */
                bb.forceUnsafeUpdate(aField);
            }
        }

        public void registerAsInvoked(Executable method) {
            registerAsInvoked(getMetaAccess().lookupJavaMethod(method));
        }

        public void registerAsInvoked(AnalysisMethod aMethod) {
            bb.addRootMethod(aMethod).registerAsImplementationInvoked(null);
        }

        public void registerAsCompiled(Executable method) {
            registerAsCompiled(getMetaAccess().lookupJavaMethod(method));
        }

        public void registerAsCompiled(AnalysisMethod aMethod) {
            registerAsInvoked(aMethod);
            CompilationInfoSupport.singleton().registerForcedCompilation(aMethod);
        }

        public void registerUnsafeFieldsRecomputed(Class<?> clazz) {
            getMetaAccess().lookupJavaType(clazz).registerUnsafeFieldsRecomputed();
        }

        public SVMHost getHostVM() {
            return bb.getHostVM();
        }

        public void registerHierarchyForReflectiveInstantiation(Class<?> c) {
            findSubclasses(c).stream().filter(clazz -> !Modifier.isAbstract(clazz.getModifiers())).forEach(clazz -> RuntimeReflection.registerForReflectiveInstantiation(clazz));
        }

        @Override
        public void registerReachabilityHandler(Consumer<DuringAnalysisAccess> callback, Object... elements) {
            ReachabilityHandlerFeature.singleton().registerReachabilityHandler(this, callback, elements);
        }

        @Override
        public void registerMethodOverrideReachabilityHandler(BiConsumer<DuringAnalysisAccess, Executable> callback, Executable baseMethod) {
            ReachabilityHandlerFeature.singleton().registerMethodOverrideReachabilityHandler(this, callback, baseMethod);
        }

        @Override
        public void registerSubtypeReachabilityHandler(BiConsumer<DuringAnalysisAccess, Class<?>> callback, Class<?> baseClass) {
            ReachabilityHandlerFeature.singleton().registerSubtypeReachabilityHandler(this, callback, baseClass);
        }
    }

    public static class DuringAnalysisAccessImpl extends BeforeAnalysisAccessImpl implements Feature.DuringAnalysisAccess {

        private boolean requireAnalysisIteration;

        DuringAnalysisAccessImpl(FeatureHandler featureHandler, ImageClassLoader imageClassLoader, Inflation bb, NativeLibraries nativeLibraries, DebugContext debugContext) {
            super(featureHandler, imageClassLoader, bb, nativeLibraries, debugContext);
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

    public static class AfterAnalysisAccessImpl extends AnalysisAccessBase implements Feature.AfterAnalysisAccess {
        AfterAnalysisAccessImpl(FeatureHandler featureHandler, ImageClassLoader imageClassLoader, Inflation bb, DebugContext debugContext) {
            super(featureHandler, imageClassLoader, bb, debugContext);
        }
    }

    public static class OnAnalysisExitAccessImpl extends AnalysisAccessBase implements Feature.OnAnalysisExitAccess {
        OnAnalysisExitAccessImpl(FeatureHandler featureHandler, ImageClassLoader imageClassLoader, Inflation bb, DebugContext debugContext) {
            super(featureHandler, imageClassLoader, bb, debugContext);
        }
    }

    public static class CompilationAccessImpl extends FeatureAccessImpl implements Feature.CompilationAccess {

        protected final AnalysisUniverse aUniverse;
        protected final HostedUniverse hUniverse;
        protected final HostedMetaAccess hMetaAccess;
        protected final NativeImageHeap heap;

        CompilationAccessImpl(FeatureHandler featureHandler, ImageClassLoader imageClassLoader, AnalysisUniverse aUniverse, HostedUniverse hUniverse, HostedMetaAccess hMetaAccess,
                        NativeImageHeap heap, DebugContext debugContext) {
            super(featureHandler, imageClassLoader, debugContext);
            this.aUniverse = aUniverse;
            this.hUniverse = hUniverse;
            this.hMetaAccess = hMetaAccess;
            this.heap = heap;
        }

        @Override
        public long objectFieldOffset(Field field) {
            return objectFieldOffset(getMetaAccess().lookupJavaField(field));
        }

        public long objectFieldOffset(HostedField hField) {
            int result = hField.getLocation();
            assert result > 0;
            return result;
        }

        @Override
        public void registerAsImmutable(Object object) {
            heap.registerAsImmutable(object);
        }

        @Override
        public void registerAsImmutable(Object root, Predicate<Object> includeObject) {
            Deque<Object> worklist = new ArrayDeque<>();
            IdentityHashMap<Object, Boolean> registeredObjects = new IdentityHashMap<>();

            worklist.push(root);

            while (!worklist.isEmpty()) {
                Object cur = worklist.pop();
                registerAsImmutable(cur);

                if (!getMetaAccess().optionalLookupJavaType(cur.getClass()).isPresent()) {
                    /*
                     * The type is unused (actually was never created by the static analysis), so we
                     * do not need to follow any children.
                     */
                } else if (cur instanceof Object[]) {
                    for (Object element : ((Object[]) cur)) {
                        addToWorklist(aUniverse.replaceObject(element), includeObject, worklist, registeredObjects);
                    }
                } else {
                    JavaConstant constant = SubstrateObjectConstant.forObject(cur);
                    for (HostedField field : ((HostedType) getMetaAccess().lookupJavaType(constant)).getInstanceFields(true)) {
                        if (field.isAccessed() && field.getStorageKind() == JavaKind.Object) {
                            Object fieldValue = SubstrateObjectConstant.asObject(field.readValue(constant));
                            addToWorklist(fieldValue, includeObject, worklist, registeredObjects);
                        }
                    }
                }
            }
        }

        private static void addToWorklist(Object object, Predicate<Object> includeObject, Deque<Object> worklist, IdentityHashMap<Object, Boolean> registeredObjects) {
            if (object == null || registeredObjects.containsKey(object)) {
                return;
            } else if (object instanceof DynamicHub || object instanceof Class) {
                /* Classes are handled specially, some fields of it are immutable and some not. */
                return;
            } else if (!includeObject.test(object)) {
                return;
            }
            registeredObjects.put(object, Boolean.TRUE);
            worklist.push(object);
        }

        public HostedMetaAccess getMetaAccess() {
            return hMetaAccess;
        }

        public HostedUniverse getUniverse() {
            return hUniverse;
        }

        public Collection<? extends SharedType> getTypes() {
            return hUniverse.getTypes();
        }

        public Collection<? extends SharedField> getFields() {
            return hUniverse.getFields();
        }

        public Collection<? extends SharedMethod> getMethods() {
            return hUniverse.getMethods();
        }
    }

    public static class BeforeCompilationAccessImpl extends CompilationAccessImpl implements Feature.BeforeCompilationAccess {
        SharedRuntimeConfigurationBuilder runtimeBuilder;

        BeforeCompilationAccessImpl(FeatureHandler featureHandler, ImageClassLoader imageClassLoader, AnalysisUniverse aUniverse, HostedUniverse hUniverse, HostedMetaAccess hMetaAccess,
                        NativeImageHeap heap, DebugContext debugContext, SharedRuntimeConfigurationBuilder runtime) {
            super(featureHandler, imageClassLoader, aUniverse, hUniverse, hMetaAccess, heap, debugContext);
            runtimeBuilder = runtime;
        }

        public SharedRuntimeConfigurationBuilder getRuntimeBuilder() {
            return runtimeBuilder;
        }
    }

    public static class AfterCompilationAccessImpl extends CompilationAccessImpl implements Feature.AfterCompilationAccess {
        private Collection<CompileQueue.CompileTask> compilationTasks;

        AfterCompilationAccessImpl(FeatureHandler featureHandler, ImageClassLoader imageClassLoader, AnalysisUniverse aUniverse, HostedUniverse hUniverse, HostedMetaAccess hMetaAccess,
                        Collection<CompileQueue.CompileTask> compilationTasks, NativeImageHeap heap, DebugContext debugContext) {
            super(featureHandler, imageClassLoader, aUniverse, hUniverse, hMetaAccess, heap, debugContext);
            this.compilationTasks = compilationTasks;
        }

        public Collection<CompileQueue.CompileTask> getCompilationTasks() {
            return compilationTasks;
        }
    }

    public static class AfterHeapLayoutAccessImpl extends FeatureAccessImpl implements Feature.AfterHeapLayoutAccess {
        protected final HostedMetaAccess hMetaAccess;
        protected final NativeImageHeap heap;

        AfterHeapLayoutAccessImpl(FeatureHandler featureHandler, ImageClassLoader imageClassLoader, NativeImageHeap heap, HostedMetaAccess hMetaAccess, DebugContext debugContext) {
            super(featureHandler, imageClassLoader, debugContext);
            this.heap = heap;
            this.hMetaAccess = hMetaAccess;
        }

        public HostedMetaAccess getMetaAccess() {
            return hMetaAccess;
        }

        public NativeImageHeap getHeap() {
            return heap;
        }
    }

    public static class BeforeImageWriteAccessImpl extends FeatureAccessImpl implements Feature.BeforeImageWriteAccess {
        private List<Function<LinkerInvocation, LinkerInvocation>> linkerInvocationTransformers = null;

        protected final String imageName;
        protected final AbstractBootImage image;
        protected final RuntimeConfiguration runtimeConfig;
        protected final AnalysisUniverse aUniverse;
        protected final HostedUniverse hUniverse;
        protected final HostedOptionProvider optionProvider;
        protected final HostedMetaAccess hMetaAccess;

        BeforeImageWriteAccessImpl(FeatureHandler featureHandler, ImageClassLoader imageClassLoader, String imageName, AbstractBootImage image, RuntimeConfiguration runtimeConfig,
                        AnalysisUniverse aUniverse, HostedUniverse hUniverse, HostedOptionProvider optionProvider, HostedMetaAccess hMetaAccess, DebugContext debugContext) {
            super(featureHandler, imageClassLoader, debugContext);
            this.imageName = imageName;
            this.image = image;
            this.runtimeConfig = runtimeConfig;
            this.aUniverse = aUniverse;
            this.hUniverse = hUniverse;
            this.optionProvider = optionProvider;
            this.hMetaAccess = hMetaAccess;
        }

        public String getImageName() {
            return imageName;
        }

        public AbstractBootImage getImage() {
            return image;
        }

        public RuntimeConfiguration getRuntimeConfiguration() {
            return runtimeConfig;
        }

        public HostedUniverse getHostedUniverse() {
            return hUniverse;
        }

        public HostedOptionProvider getHostedOptionProvider() {
            return optionProvider;
        }

        public HostedMetaAccess getHostedMetaAccess() {
            return hMetaAccess;
        }

        public Iterable<Function<LinkerInvocation, LinkerInvocation>> getLinkerInvocationTransformers() {
            if (linkerInvocationTransformers == null) {
                return Collections.emptyList();
            }
            return linkerInvocationTransformers;
        }

        public void registerLinkerInvocationTransformer(Function<LinkerInvocation, LinkerInvocation> transformer) {
            if (linkerInvocationTransformers == null) {
                linkerInvocationTransformers = new ArrayList<>();
            }
            linkerInvocationTransformers.add(transformer);
        }
    }

    public static class AfterImageWriteAccessImpl extends FeatureAccessImpl implements Feature.AfterImageWriteAccess {
        private final HostedUniverse hUniverse;
        protected final LinkerInvocation linkerInvocation;
        protected final Path tempDirectory;
        protected final NativeImageKind imageKind;

        AfterImageWriteAccessImpl(FeatureHandler featureHandler, ImageClassLoader imageClassLoader, HostedUniverse hUniverse, LinkerInvocation linkerInvocation, Path tempDirectory,
                        NativeImageKind imageKind,
                        DebugContext debugContext) {
            super(featureHandler, imageClassLoader, debugContext);
            this.hUniverse = hUniverse;
            this.linkerInvocation = linkerInvocation;
            this.tempDirectory = tempDirectory;
            this.imageKind = imageKind;
        }

        public HostedUniverse getUniverse() {
            return hUniverse;
        }

        @Override
        public Path getImagePath() {
            return linkerInvocation.getOutputFile();
        }

        public Path getTempDirectory() {
            return tempDirectory;
        }

        public NativeImageKind getImageKind() {
            return imageKind;
        }

        public List<String> getImageSymbols(boolean onlyGlobal) {
            return linkerInvocation.getImageSymbols(onlyGlobal);
        }
    }
}
