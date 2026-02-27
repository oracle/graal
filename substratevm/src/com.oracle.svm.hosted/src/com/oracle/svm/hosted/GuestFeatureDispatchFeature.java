/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.dynamicaccess.ForeignAccess;
import org.graalvm.nativeimage.dynamicaccess.JNIAccess;
import org.graalvm.nativeimage.dynamicaccess.ReflectiveAccess;
import org.graalvm.nativeimage.dynamicaccess.ResourceAccess;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.feature.JVMCIFeatureAccess;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.util.OriginalClassProvider;
import com.oracle.svm.util.OriginalMethodProvider;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Bridges feature callbacks from the hosted image builder to user-defined features running in the
 * guest context.
 */
@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
final class GuestFeatureDispatchFeature implements InternalFeature {

    private final GuestAccess guestAccess;
    private JavaConstant guestFeatureHandler;
    private ResolvedJavaType reflectiveAccessType;
    private ResolvedJavaType resourceAccessType;
    private ResolvedJavaType jniAccessType;
    private ResolvedJavaType foreignAccessType;
    private ResolvedJavaType beforeAnalysisAccessType;
    private ResolvedJavaType duringSetupAccessType;
    private ResolvedJavaType duringAnalysisAccessType;
    private ResolvedJavaType queryReachabilityAccessType;
    private ResolvedJavaType compilationAccessType;
    private ResolvedJavaMethod registerFeature;
    private ResolvedJavaMethod getCompatibilityPublishedFeatureSingletons;
    private ResolvedJavaMethod invokeReachabilityHandler;
    private ResolvedJavaMethod invokeMethodOverrideReachabilityHandler;
    private ResolvedJavaMethod invokeSubtypeReachabilityHandler;
    private ResolvedJavaMethod afterRegistrationForEachFeature;
    private ResolvedJavaMethod duringSetupForEachFeature;
    private ResolvedJavaMethod beforeAnalysisForEachFeature;
    private ResolvedJavaMethod duringAnalysisForEachFeature;
    private ResolvedJavaMethod afterAnalysisForEachFeature;
    private ResolvedJavaMethod onAnalysisExitForEachFeature;
    private ResolvedJavaMethod beforeUniverseBuildingForEachFeature;
    private ResolvedJavaMethod beforeCompilationForEachFeature;
    private ResolvedJavaMethod afterCompilationForEachFeature;
    private ResolvedJavaMethod beforeHeapLayoutForEachFeature;
    private ResolvedJavaMethod afterHeapLayoutForEachFeature;
    private ResolvedJavaMethod beforeImageWriteForEachFeature;
    private ResolvedJavaMethod afterImageWriteForEachFeature;
    private ResolvedJavaMethod cleanupForEachFeature;
    private Map<ResolvedJavaMethod, String> reflectiveAccessMappings;
    private Map<ResolvedJavaMethod, String> jniAccessMappings;
    private Map<ResolvedJavaMethod, String> resourceAccessMappings;
    private Map<ResolvedJavaMethod, String> commonFeatureAccessMappings;
    private Map<ResolvedJavaMethod, String> queryReachabilityMappings;
    private Map<ResolvedJavaMethod, String> beforeAnalysisMappings;
    private Map<ResolvedJavaMethod, String> duringAnalysisMappings;
    private Map<ResolvedJavaMethod, String> duringSetupMappings;
    private Map<ResolvedJavaMethod, String> compilationMappings;

    GuestFeatureDispatchFeature() {
        this.guestAccess = GuestAccess.get();
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return guestAccess.isFullyIsolated() && !FeatureHandler.Options.userEnabledFeatures().isEmpty();
    }

    @Override
    public void onRegistration(OnRegistrationAccess access) {
        ImageSingletons.add(GuestFeatureDispatchFeature.class, this);
    }

    boolean registerFeature(String featureName, ImageClassLoader loader, boolean printFeatures) {
        if (guestFeatureHandler == null) {
            initializeGuestFeatureHandler(loader);
        }
        return guestAccess.invoke(registerFeature, guestFeatureHandler, guestAccess.asGuestString(featureName), JavaConstant.forBoolean(printFeatures)).asBoolean();
    }

    List<String> getGuestCompatibilityPublishedFeatureSingletons() {
        if (guestFeatureHandler == null) {
            return List.of();
        }
        JavaConstant guestFeatureSingletons = guestAccess.invoke(getCompatibilityPublishedFeatureSingletons, guestFeatureHandler);
        return guestAccess.asGuestArrayElements(guestFeatureSingletons, guestAccess.elements.java_lang_String).map(guestAccess::asHostString).toList();
    }

    private void initializeGuestFeatureHandler(ImageClassLoader loader) {
        ResolvedJavaType handlerType = guestAccess.lookupType("com.oracle.svm.guest.hosted.GuestFeatureHandler");
        reflectiveAccessType = guestAccess.lookupType(ReflectiveAccess.class);
        resourceAccessType = guestAccess.lookupType(ResourceAccess.class);
        jniAccessType = guestAccess.lookupType(JNIAccess.class);
        foreignAccessType = guestAccess.lookupType(ForeignAccess.class);
        ResolvedJavaType biConsumerType = guestAccess.lookupType(BiConsumer.class);
        ResolvedJavaType executableType = guestAccess.lookupType(Executable.class);
        ResolvedJavaType classType = guestAccess.lookupType(Class.class);
        beforeAnalysisAccessType = guestAccess.lookupType(Feature.BeforeAnalysisAccess.class);
        duringSetupAccessType = guestAccess.lookupType(Feature.DuringSetupAccess.class);
        duringAnalysisAccessType = guestAccess.lookupType(Feature.DuringAnalysisAccess.class);
        queryReachabilityAccessType = guestAccess.lookupType(Feature.QueryReachabilityAccess.class);
        compilationAccessType = guestAccess.lookupType(Feature.CompilationAccess.class);
        ResolvedJavaType classLoaderArrayType = guestAccess.lookupType(ClassLoader[].class);
        ResolvedJavaType stringArrayType = guestAccess.lookupType(String[].class);
        ResolvedJavaMethod constructor = JVMCIReflectionUtil.getDeclaredConstructor(handlerType, classLoaderArrayType, stringArrayType, stringArrayType);

        JavaConstant classLoaders = guestAccess.asArrayConstant(guestAccess.lookupType(ClassLoader.class), loader.classLoaderSupport.getClassLoaders().toArray(JavaConstant[]::new));
        JavaConstant applicationClassPath = guestAccess.asGuestStringArray(loader.applicationClassPath().stream().map(Path::toString).toArray(String[]::new));
        JavaConstant applicationModulePath = guestAccess.asGuestStringArray(loader.applicationModulePath().stream().map(Path::toString).toArray(String[]::new));

        guestFeatureHandler = guestAccess.invoke(constructor, null, classLoaders, applicationClassPath, applicationModulePath);
        registerFeature = guestAccess.lookupMethod(handlerType, "registerFeature", String.class, boolean.class);
        getCompatibilityPublishedFeatureSingletons = guestAccess.lookupMethod(handlerType, "getCompatibilityPublishedFeatureSingletons");
        invokeReachabilityHandler = JVMCIReflectionUtil.getUniqueDeclaredMethod(handlerType, "invokeReachabilityHandler", guestAccess.elements.java_util_function_Consumer, duringAnalysisAccessType);
        invokeMethodOverrideReachabilityHandler = JVMCIReflectionUtil.getUniqueDeclaredMethod(handlerType, "invokeMethodOverrideReachabilityHandler", biConsumerType, duringAnalysisAccessType,
                        executableType);
        invokeSubtypeReachabilityHandler = JVMCIReflectionUtil.getUniqueDeclaredMethod(handlerType, "invokeSubtypeReachabilityHandler", biConsumerType, duringAnalysisAccessType, classType);
        afterRegistrationForEachFeature = JVMCIReflectionUtil.getUniqueDeclaredMethod(handlerType, "afterRegistrationForEachFeature", reflectiveAccessType, resourceAccessType, jniAccessType,
                        foreignAccessType);
        duringSetupForEachFeature = JVMCIReflectionUtil.getUniqueDeclaredMethod(handlerType, "duringSetupForEachFeature", duringSetupAccessType);
        beforeAnalysisForEachFeature = JVMCIReflectionUtil.getUniqueDeclaredMethod(handlerType, "beforeAnalysisForEachFeature", beforeAnalysisAccessType, queryReachabilityAccessType);
        duringAnalysisForEachFeature = JVMCIReflectionUtil.getUniqueDeclaredMethod(handlerType, "duringAnalysisForEachFeature", duringAnalysisAccessType);
        afterAnalysisForEachFeature = JVMCIReflectionUtil.getUniqueDeclaredMethod(handlerType, "afterAnalysisForEachFeature", queryReachabilityAccessType);
        onAnalysisExitForEachFeature = guestAccess.lookupMethod(handlerType, "onAnalysisExitForEachFeature");
        beforeUniverseBuildingForEachFeature = guestAccess.lookupMethod(handlerType, "beforeUniverseBuildingForEachFeature");
        beforeCompilationForEachFeature = JVMCIReflectionUtil.getUniqueDeclaredMethod(handlerType, "beforeCompilationForEachFeature", compilationAccessType);
        afterCompilationForEachFeature = JVMCIReflectionUtil.getUniqueDeclaredMethod(handlerType, "afterCompilationForEachFeature", compilationAccessType);
        beforeHeapLayoutForEachFeature = JVMCIReflectionUtil.getUniqueDeclaredMethod(handlerType, "beforeHeapLayoutForEachFeature", compilationAccessType);
        afterHeapLayoutForEachFeature = guestAccess.lookupMethod(handlerType, "afterHeapLayoutForEachFeature");
        beforeImageWriteForEachFeature = guestAccess.lookupMethod(handlerType, "beforeImageWriteForEachFeature");
        afterImageWriteForEachFeature = guestAccess.lookupMethod(handlerType, "afterImageWriteForEachFeature", String.class);
        cleanupForEachFeature = guestAccess.lookupMethod(handlerType, "cleanupForEachFeature");
        reflectiveAccessMappings = createReflectiveAccessMappings();
        jniAccessMappings = createJNIAccessMappings();
        resourceAccessMappings = createResourceAccessMappings();
        commonFeatureAccessMappings = createCommonFeatureAccessMappings();
        queryReachabilityMappings = createQueryReachabilityMappings();
        beforeAnalysisMappings = composeMappings(commonFeatureAccessMappings, queryReachabilityMappings);
        duringAnalysisMappings = beforeAnalysisMappings;
        duringSetupMappings = commonFeatureAccessMappings;
        compilationMappings = commonFeatureAccessMappings;
    }

    void invokeGuestReachabilityHandler(JavaConstant callback, Feature.DuringAnalysisAccess access) {
        JavaConstant duringAnalysisAccess = this.guestAccess.createHostProxy(access, duringAnalysisAccessType, duringAnalysisMappings);
        this.guestAccess.invokeStatic(invokeReachabilityHandler, callback, duringAnalysisAccess);
    }

    void invokeGuestMethodOverrideReachabilityHandler(JavaConstant callback, JVMCIFeatureAccess.DuringAnalysisAccess access, ResolvedJavaMethod method) {
        JavaConstant executable;
        try {
            ResolvedJavaMethod originalMethod = OriginalMethodProvider.getOriginalMethod(method);
            executable = originalMethod == null ? null : guestAccess.asExecutableConstant(originalMethod);
        } catch (IllegalArgumentException | LinkageError ignored) {
            executable = null;
        }
        if (executable == null || executable.isNull()) {
            return;
        }
        JavaConstant duringAnalysisAccess = guestAccess.createHostProxy(access, duringAnalysisAccessType, duringAnalysisMappings);
        guestAccess.invokeStatic(invokeMethodOverrideReachabilityHandler, callback, duringAnalysisAccess, executable);
    }

    void invokeGuestSubtypeReachabilityHandler(JavaConstant callback, JVMCIFeatureAccess.DuringAnalysisAccess access, ResolvedJavaType type) {
        JavaConstant guestClass = guestAccess.getProviders().getConstantReflection().asJavaClass(OriginalClassProvider.getOriginalType(type));
        if (guestClass == null || guestClass.isNull()) {
            return;
        }
        JavaConstant duringAnalysisAccess = guestAccess.createHostProxy(access, duringAnalysisAccessType, duringAnalysisMappings);
        guestAccess.invokeStatic(invokeSubtypeReachabilityHandler, callback, duringAnalysisAccess, guestClass);
    }

    /**
     * Converts the path returned by the host-side {@link org.graalvm.nativeimage.hosted.Feature.AfterImageWriteAccess#getImagePath()}
     * to the string representation consumed in the guest context. The guest-side
     * {@code GuestFeatureImpl.AfterImageWriteAccessImpl#getImagePath()} reconstructs the path from
     * this representation.
     */
    private JavaConstant guestImagePath(AfterImageWriteAccess access) {
        Path imagePath = access.getImagePath();
        if (imagePath == null) {
            return JavaConstant.NULL_POINTER;
        }

        FileSystem defaultFileSystem = FileSystems.getDefault();
        FileSystemProvider defaultProvider = defaultFileSystem.provider();
        VMError.guarantee(imagePath.getFileSystem() == defaultFileSystem, "The image path must use the host default file system.");
        VMError.guarantee(imagePath.getFileSystem().provider() == defaultProvider, "The image path must use the host default file-system provider.");
        VMError.guarantee("file".equalsIgnoreCase(defaultProvider.getScheme()), "The host default file-system provider must use the 'file' scheme.");
        VMError.guarantee(imagePath.isAbsolute(), "The image path must be absolute.");

        String rawPath = imagePath.toString();
        VMError.guarantee(!rawPath.isEmpty(), "The image path must not be empty.");
        Path parsedPath;
        try {
            parsedPath = Path.of(rawPath);
        } catch (InvalidPathException ex) {
            throw VMError.shouldNotReachHere("The image path cannot be represented by the host default file system.", ex);
        }
        VMError.guarantee(parsedPath.equals(imagePath), "The image path changed when parsed by the host default file system.");
        VMError.guarantee(rawPath.equals(parsedPath.toString()), "The image path changed when serialized by the host default file system.");
        return guestAccess.asGuestString(rawPath);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        JVMCIFeatureAccess.AfterRegistrationAccess jvmciAccess = (JVMCIFeatureAccess.AfterRegistrationAccess) access;
        JavaConstant guestReflectiveAccess = guestAccess.createHostProxy(jvmciAccess.getJVMCIReflectiveAccess(), reflectiveAccessType, reflectiveAccessMappings);
        JavaConstant guestResourceAccess = guestAccess.createHostProxy(ResourceAccessImpl.singleton(), resourceAccessType, resourceAccessMappings);
        JavaConstant guestJNIAccess = guestAccess.createHostProxy(jvmciAccess.getJVMCIJNIAccess(), jniAccessType, jniAccessMappings);
        JavaConstant guestForeignAccess = guestAccess.createHostProxy(jvmciAccess.getJVMCIForeignAccess(), foreignAccessType);
        guestAccess.invoke(afterRegistrationForEachFeature, guestFeatureHandler, guestReflectiveAccess, guestResourceAccess, guestJNIAccess, guestForeignAccess);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        JavaConstant duringSetupAccess = guestAccess.createHostProxy(access, duringSetupAccessType, duringSetupMappings);
        guestAccess.invoke(duringSetupForEachFeature, guestFeatureHandler, duringSetupAccess);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        JavaConstant beforeAnalysisAccess = guestAccess.createHostProxy(access, beforeAnalysisAccessType, commonFeatureAccessMappings);
        JavaConstant queryReachabilityAccess = guestAccess.createHostProxy(access, queryReachabilityAccessType, beforeAnalysisMappings);
        guestAccess.invoke(beforeAnalysisForEachFeature, guestFeatureHandler, beforeAnalysisAccess, queryReachabilityAccess);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        JavaConstant duringAnalysisAccess = guestAccess.createHostProxy(access, duringAnalysisAccessType, duringAnalysisMappings);
        guestAccess.invoke(duringAnalysisForEachFeature, guestFeatureHandler, duringAnalysisAccess);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        JavaConstant queryReachabilityAccess = guestAccess.createHostProxy(access, queryReachabilityAccessType, beforeAnalysisMappings);
        guestAccess.invoke(afterAnalysisForEachFeature, guestFeatureHandler, queryReachabilityAccess);
    }

    @Override
    public void onAnalysisExit(OnAnalysisExitAccess access) {
        guestAccess.invoke(onAnalysisExitForEachFeature, guestFeatureHandler);
    }

    @Override
    public void beforeUniverseBuilding(BeforeUniverseBuildingAccess access) {
        guestAccess.invoke(beforeUniverseBuildingForEachFeature, guestFeatureHandler);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        JavaConstant compilationAccess = guestAccess.createHostProxy(access, compilationAccessType, compilationMappings);
        guestAccess.invoke(beforeCompilationForEachFeature, guestFeatureHandler, compilationAccess);
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        JavaConstant compilationAccess = guestAccess.createHostProxy(access, compilationAccessType, compilationMappings);
        guestAccess.invoke(afterCompilationForEachFeature, guestFeatureHandler, compilationAccess);
    }

    @Override
    public void beforeHeapLayout(BeforeHeapLayoutAccess access) {
        JavaConstant compilationAccess = guestAccess.createHostProxy(access, compilationAccessType, compilationMappings);
        guestAccess.invoke(beforeHeapLayoutForEachFeature, guestFeatureHandler, compilationAccess);
    }

    @Override
    public void afterHeapLayout(AfterHeapLayoutAccess access) {
        guestAccess.invoke(afterHeapLayoutForEachFeature, guestFeatureHandler);
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        guestAccess.invoke(beforeImageWriteForEachFeature, guestFeatureHandler);
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        guestAccess.invoke(afterImageWriteForEachFeature, guestFeatureHandler, guestImagePath(access));
    }

    @Override
    public void cleanup() {
        guestAccess.invoke(cleanupForEachFeature, guestFeatureHandler);
    }

    private Map<ResolvedJavaMethod, String> createReflectiveAccessMappings() {
        return Map.of(
                        guestMethod(reflectiveAccessType, "register", AccessCondition.class, Class[].class), "registerClasses",
                        guestMethod(reflectiveAccessType, "register", AccessCondition.class, Executable[].class), "registerExecutables",
                        guestMethod(reflectiveAccessType, "register", AccessCondition.class, Field[].class), "registerFields",
                        guestMethod(reflectiveAccessType, "registerForSerialization", AccessCondition.class, Class[].class), "registerForSerializationClasses",
                        guestMethod(reflectiveAccessType, "registerProxy", AccessCondition.class, Class[].class), "registerProxyInterfaces",
                        guestMethod(reflectiveAccessType, "registerForUnsafeAllocation", AccessCondition.class, Class[].class), "registerForUnsafeAllocationClasses");
    }

    private Map<ResolvedJavaMethod, String> createJNIAccessMappings() {
        return Map.of(
                        guestMethod(jniAccessType, "register", AccessCondition.class, Class[].class), "registerClasses",
                        guestMethod(jniAccessType, "register", AccessCondition.class, Executable[].class), "registerExecutables",
                        guestMethod(jniAccessType, "register", AccessCondition.class, Field[].class), "registerFields");
    }

    private Map<ResolvedJavaMethod, String> createResourceAccessMappings() {
        return Map.of(
                        guestMethod(resourceAccessType, "register", AccessCondition.class, Module.class, String.class), "registerResource",
                        guestMethod(resourceAccessType, "registerResourceBundle", AccessCondition.class, java.util.ResourceBundle[].class), "registerResourceBundles");
    }

    private Map<ResolvedJavaMethod, String> createCommonFeatureAccessMappings() {
        Map<ResolvedJavaMethod, String> result = new HashMap<>();
        Set<ResolvedJavaType> visited = new HashSet<>();
        for (ResolvedJavaType proxyType : List.of(duringSetupAccessType, beforeAnalysisAccessType, duringAnalysisAccessType, queryReachabilityAccessType, compilationAccessType)) {
            addCommonMappings(result, proxyType, visited);
        }
        return Map.copyOf(result);
    }

    private static void addCommonMappings(Map<ResolvedJavaMethod, String> mappings, ResolvedJavaType type, Set<ResolvedJavaType> visited) {
        if (!visited.add(type)) {
            return;
        }
        for (ResolvedJavaMethod method : type.getDeclaredMethods(false)) {
            String hostName = switch (method.getName()) {
                case "findClassByName", "getApplicationClassPath", "getApplicationModulePath", "getApplicationClassLoader" ->
                    "unexpectedGuestFeatureCallback" + method.getSignature().getParameterCount(false);
                default -> null;
            };
            if (hostName != null) {
                mappings.put(method, hostName);
            }
        }
        for (ResolvedJavaType superInterface : type.getInterfaces()) {
            addCommonMappings(mappings, superInterface, visited);
        }
    }

    private Map<ResolvedJavaMethod, String> createQueryReachabilityMappings() {
        return Map.of(
                        guestMethod(queryReachabilityAccessType, "reachableSubtypes", Class.class), "reachableSubtypesAsSet",
                        guestMethod(queryReachabilityAccessType, "reachableMethodOverrides", Executable.class), "reachableMethodOverridesAsSet");
    }

    private static Map<ResolvedJavaMethod, String> composeMappings(Map<ResolvedJavaMethod, String> first, Map<ResolvedJavaMethod, String> second) {
        Map<ResolvedJavaMethod, String> result = new HashMap<>(first);
        result.putAll(second);
        return Map.copyOf(result);
    }

    private ResolvedJavaMethod guestMethod(ResolvedJavaType declaringType, String name, Class<?>... parameterTypes) {
        return JVMCIReflectionUtil.getUniqueDeclaredMethod(guestAccess.getProviders().getMetaAccess(), declaringType, name, parameterTypes);
    }
}
