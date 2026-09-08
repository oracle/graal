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
import java.lang.reflect.Field;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.dynamicaccess.ForeignAccess;
import org.graalvm.nativeimage.dynamicaccess.JNIAccess;
import org.graalvm.nativeimage.dynamicaccess.ReflectiveAccess;
import org.graalvm.nativeimage.dynamicaccess.ResourceAccess;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.shared.util.VMError;

@Platforms(Platform.HOSTED_ONLY.class)
public final class GuestFeatureImpl {

    private GuestFeatureImpl() {
    }

    public abstract static class FeatureAccessImpl implements Feature.FeatureAccess {

        @Override
        public Class<?> findClassByName(String className) {
            for (ClassLoader classLoader : GuestFeatureHandler.singleton().classLoaders) {
                try {
                    return Class.forName(className, false, classLoader);
                } catch (ClassNotFoundException ignored) {
                }
            }
            return null;
        }

        @Override
        public List<Path> getApplicationClassPath() {
            return GuestFeatureHandler.singleton().applicationClassPath;
        }

        @Override
        public List<Path> getApplicationModulePath() {
            return GuestFeatureHandler.singleton().applicationModulePath;
        }

        @Override
        public ClassLoader getApplicationClassLoader() {
            return GuestFeatureHandler.singleton().classLoaders[GuestFeatureHandler.singleton().classLoaders.length - 1];
        }

        static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Feature access is not implemented in the guest context.");
        }
    }

    abstract static class RegistrationAccessBase extends FeatureAccessImpl {

        RegistrationAccessBase() {
        }
    }

    /**
     * Access used while registering guest features. The same access instance is used for
     * {@link Feature#isInConfiguration(Feature.IsInConfigurationAccess)} and
     * {@link Feature#onRegistration(Feature.OnRegistrationAccess)}.
     */
    public static class RegistrationAccessImpl extends RegistrationAccessBase implements Feature.IsInConfigurationAccess, Feature.OnRegistrationAccess {

        public RegistrationAccessImpl() {
        }
    }

    public static class AfterRegistrationAccessImpl extends RegistrationAccessBase implements Feature.AfterRegistrationAccess {
        private final ReflectiveAccess reflectiveAccess;
        private final ResourceAccess resourceAccess;
        private final JNIAccess jniAccess;
        private final ForeignAccess foreignAccess;

        public AfterRegistrationAccessImpl(ReflectiveAccess reflectiveAccess, ResourceAccess resourceAccess, JNIAccess jniAccess, ForeignAccess foreignAccess) {
            this.reflectiveAccess = reflectiveAccess;
            this.resourceAccess = new GuestResourceAccess(resourceAccess);
            this.jniAccess = jniAccess;
            this.foreignAccess = foreignAccess;
        }

        @Override
        public ReflectiveAccess getReflectiveAccess() {
            return reflectiveAccess;
        }

        @Override
        public ResourceAccess getResourceAccess() {
            return resourceAccess;
        }

        @Override
        public JNIAccess getJNIAccess() {
            return jniAccess;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return foreignAccess;
        }
    }

    private static final class GuestResourceAccess implements ResourceAccess {
        private final ResourceAccess resourceAccess;

        GuestResourceAccess(ResourceAccess resourceAccess) {
            this.resourceAccess = resourceAccess;
        }

        @Override
        public void register(AccessCondition condition, Module module, String glob) {
            if (module == null || !module.isNamed()) {
                resourceAccess.register(condition, glob);
            } else {
                throw FeatureAccessImpl.unsupported();
            }
        }

        @Override
        public void registerResourceBundle(AccessCondition condition, ResourceBundle... bundles) {
            resourceAccess.registerResourceBundle(condition, bundles);
        }
    }

    abstract static class AnalysisAccessBase extends FeatureAccessImpl {
        private final Feature.QueryReachabilityAccess queryReachabilityAccess;

        AnalysisAccessBase(Feature.QueryReachabilityAccess queryReachabilityAccess) {
            this.queryReachabilityAccess = queryReachabilityAccess;
        }

        public boolean isReachable(Class<?> clazz) {
            return queryReachabilityAccess.isReachable(clazz);
        }

        public boolean isReachable(Field field) {
            return queryReachabilityAccess.isReachable(field);
        }

        public boolean isReachable(Executable method) {
            return queryReachabilityAccess.isReachable(method);
        }

        public Set<Class<?>> reachableSubtypes(Class<?> baseClass) {
            return queryReachabilityAccess.reachableSubtypes(baseClass);
        }

        public Set<Executable> reachableMethodOverrides(Executable baseMethod) {
            return queryReachabilityAccess.reachableMethodOverrides(baseMethod);
        }
    }

    public static class DuringSetupAccessImpl extends FeatureAccessImpl implements Feature.DuringSetupAccess {
        private final Feature.DuringSetupAccess duringSetupAccess;

        public DuringSetupAccessImpl(Feature.DuringSetupAccess duringSetupAccess) {
            this.duringSetupAccess = duringSetupAccess;
        }

        @Override
        public void registerObjectReplacer(Function<Object, Object> replacer) {
            duringSetupAccess.registerObjectReplacer(replacer);
        }

        @Override
        public <T> void registerObjectReachabilityHandler(Consumer<T> callback, Class<T> clazz) {
            duringSetupAccess.registerObjectReachabilityHandler(callback, clazz);
        }

        @Override
        public void registerBuildTimeBootstrapIndy(Executable method) {
            duringSetupAccess.registerBuildTimeBootstrapIndy(method);
        }

        @Override
        public void registerBuildTimeBootstrapCondy(Executable method) {
            duringSetupAccess.registerBuildTimeBootstrapCondy(method);
        }

    }

    public static class BeforeAnalysisAccessImpl extends AnalysisAccessBase implements Feature.BeforeAnalysisAccess {
        private final Feature.BeforeAnalysisAccess beforeAnalysisAccess;

        public BeforeAnalysisAccessImpl(Feature.BeforeAnalysisAccess guestBeforeAnalysisAccess, Feature.QueryReachabilityAccess queryReachabilityAccess) {
            super(queryReachabilityAccess);
            this.beforeAnalysisAccess = guestBeforeAnalysisAccess;
        }

        @Override
        public void registerAsUsed(Class<?> type) {
            beforeAnalysisAccess.registerAsUsed(type);
        }

        @Override
        public void registerAsInHeap(Class<?> type) {
            beforeAnalysisAccess.registerAsInHeap(type);
        }

        @Override
        public void registerAsUnsafeAllocated(Class<?> type) {
            beforeAnalysisAccess.registerAsUnsafeAllocated(type);
        }

        @Override
        public void registerAsAccessed(Field field) {
            beforeAnalysisAccess.registerAsAccessed(field);
        }

        @Override
        public void registerAsUnsafeAccessed(Field field) {
            beforeAnalysisAccess.registerAsUnsafeAccessed(field);
        }

        @Override
        public void registerReachabilityHandler(Consumer<Feature.DuringAnalysisAccess> callback, Object... elements) {
            beforeAnalysisAccess.registerReachabilityHandler(callback, elements);
        }

        @Override
        public void registerMethodOverrideReachabilityHandler(BiConsumer<Feature.DuringAnalysisAccess, Executable> callback, Executable baseMethod) {
            beforeAnalysisAccess.registerMethodOverrideReachabilityHandler(callback, baseMethod);
        }

        @Override
        public void registerSubtypeReachabilityHandler(BiConsumer<Feature.DuringAnalysisAccess, Class<?>> callback, Class<?> baseClass) {
            beforeAnalysisAccess.registerSubtypeReachabilityHandler(callback, baseClass);
        }

        @Override
        public void registerClassInitializerReachabilityHandler(Consumer<Feature.DuringAnalysisAccess> callback, Class<?> clazz) {
            beforeAnalysisAccess.registerClassInitializerReachabilityHandler(callback, clazz);
        }

        @Override
        public void registerFieldValueTransformer(Field field, FieldValueTransformer transformer) {
            // A guest wrapper backed by a host proxy is required because the field transformer is a guest callback.
            beforeAnalysisAccess.registerFieldValueTransformer(field, transformer);
        }
    }

    public static class DuringAnalysisAccessImpl extends BeforeAnalysisAccessImpl implements Feature.DuringAnalysisAccess {
        private final Feature.DuringAnalysisAccess duringAnalysisAccess;

        public DuringAnalysisAccessImpl(Feature.DuringAnalysisAccess duringAnalysisAccess) {
            super(duringAnalysisAccess, duringAnalysisAccess);
            this.duringAnalysisAccess = duringAnalysisAccess;
        }

        @Override
        public void requireAnalysisIteration() {
            duringAnalysisAccess.requireAnalysisIteration();
        }

        @Override
        public void registerFieldValueTransformer(Field field, FieldValueTransformer transformer) {
            duringAnalysisAccess.registerFieldValueTransformer(field, transformer);
        }
    }

    public static class AfterAnalysisAccessImpl extends AnalysisAccessBase implements Feature.AfterAnalysisAccess {

        public AfterAnalysisAccessImpl(Feature.QueryReachabilityAccess queryReachabilityAccess) {
            super(queryReachabilityAccess);
        }
    }

    public static class OnAnalysisExitAccessImpl extends FeatureAccessImpl implements Feature.OnAnalysisExitAccess {

        public OnAnalysisExitAccessImpl() {
        }
    }

    public abstract static class HostedFeatureAccessImpl extends FeatureAccessImpl {

        HostedFeatureAccessImpl() {
        }
    }

    public static class BeforeUniverseBuildingAccessImpl extends HostedFeatureAccessImpl implements Feature.BeforeUniverseBuildingAccess {

        public BeforeUniverseBuildingAccessImpl() {
        }
    }

    public static class CompilationAccessImpl extends HostedFeatureAccessImpl implements Feature.CompilationAccess {
        private final Feature.CompilationAccess compilationAccess;

        public CompilationAccessImpl(Feature.CompilationAccess compilationAccess) {
            this.compilationAccess = compilationAccess;
        }

        @Override
        public long objectFieldOffset(Field field) {
            return compilationAccess.objectFieldOffset(field);
        }

        @Override
        public void registerAsImmutable(Object object) {
            compilationAccess.registerAsImmutable(object);
        }

        @Override
        public void registerAsImmutable(Object root, Predicate<Object> includeObject) {
            compilationAccess.registerAsImmutable(root, includeObject);
        }
    }

    public static class BeforeCompilationAccessImpl extends CompilationAccessImpl implements Feature.BeforeCompilationAccess {

        public BeforeCompilationAccessImpl(Feature.CompilationAccess compilationAccess) {
            super(compilationAccess);
        }
    }

    public static class AfterCompilationAccessImpl extends CompilationAccessImpl implements Feature.AfterCompilationAccess {

        public AfterCompilationAccessImpl(Feature.CompilationAccess compilationAccess) {
            super(compilationAccess);
        }
    }

    public static class BeforeHeapLayoutAccessImpl extends CompilationAccessImpl implements Feature.BeforeHeapLayoutAccess {

        public BeforeHeapLayoutAccessImpl(Feature.CompilationAccess compilationAccess) {
            super(compilationAccess);
        }
    }

    public static class AfterHeapLayoutAccessImpl extends HostedFeatureAccessImpl implements Feature.AfterHeapLayoutAccess {

        public AfterHeapLayoutAccessImpl() {
        }
    }

    public static class BeforeImageWriteAccessImpl extends HostedFeatureAccessImpl implements Feature.BeforeImageWriteAccess {

        public BeforeImageWriteAccessImpl() {
        }
    }

    /**
     * Guest-side implementation of {@link org.graalvm.nativeimage.hosted.Feature.AfterImageWriteAccess} that reconstructs the
     * image path from the string representation provided by the host-side dispatch.
     */
    public static class AfterImageWriteAccessImpl extends HostedFeatureAccessImpl implements Feature.AfterImageWriteAccess {
        private final Path imagePath;

        public AfterImageWriteAccessImpl(String rawImagePath) {
            this.imagePath = parseImagePath(rawImagePath);
        }

        private static Path parseImagePath(String rawImagePath) {
            if (rawImagePath == null) {
                return null;
            }
            FileSystem defaultFileSystem = FileSystems.getDefault();
            VMError.guarantee("file".equalsIgnoreCase(defaultFileSystem.provider().getScheme()), "The guest default file-system provider must use the 'file' scheme.");
            Path parsedPath;
            try {
                parsedPath = Path.of(rawImagePath);
            } catch (InvalidPathException ex) {
                throw VMError.shouldNotReachHere("Invalid native-image path: %s".formatted(rawImagePath), ex);
            }
            VMError.guarantee(parsedPath.getFileSystem() == defaultFileSystem && parsedPath.getFileSystem().provider() == defaultFileSystem.provider(),
                            "The native-image path was not parsed by the guest default file system.");
            VMError.guarantee(parsedPath.isAbsolute(), "The native-image path must be absolute: %s".formatted(rawImagePath));
            VMError.guarantee(rawImagePath.equals(parsedPath.toString()), "The native-image path changed during parsing: %s".formatted(rawImagePath));
            return parsedPath;
        }

        @Override
        public Path getImagePath() {
            return imagePath;
        }
    }
}
