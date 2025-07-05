/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage;

import java.io.IOException;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;
import org.graalvm.nativeimage.impl.RuntimeJNIAccessSupport;
import org.graalvm.nativeimage.impl.RuntimeSystemPropertiesSupport;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSBigInt;
import org.graalvm.webimage.api.JSBoolean;
import org.graalvm.webimage.api.JSNumber;
import org.graalvm.webimage.api.JSObject;
import org.graalvm.webimage.api.JSString;
import org.graalvm.webimage.api.JSSymbol;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.configure.ConfigurationFile;
import com.oracle.svm.configure.ReflectionConfigurationParser;
import com.oracle.svm.configure.config.conditional.ConfigurationConditionResolver;
import com.oracle.svm.core.c.ProjectHeaderFile;
import com.oracle.svm.core.c.ProjectHeaderFileHeaderResolversRegistryFeature;
import com.oracle.svm.core.code.ImageCodeInfo;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.heap.RestrictHeapAccessCallees;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubCompanion;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.jdk.SystemInOutErrSupport;
import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import com.oracle.svm.core.jdk.buildtimeinit.FileSystemProviderBuildTimeInitSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.log.Loggers;
import com.oracle.svm.core.log.NoopLog;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.HostedConfiguration;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.hosted.image.NativeImageCodeCacheFactory;
import com.oracle.svm.hosted.jni.JNIAccessFeature;
import com.oracle.svm.hosted.reflect.NativeImageConditionResolver;
import com.oracle.svm.hosted.webimage.codegen.LowerableResources;
import com.oracle.svm.hosted.webimage.codegen.WebImageProviders;
import com.oracle.svm.hosted.webimage.name.WebImageNamingConvention;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.snippets.WebImageNonSnippetLowerings;
import com.oracle.svm.hosted.webimage.wasm.WasmLogHandler;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.webimage.WebImageSystemPropertiesSupport;
import com.oracle.svm.webimage.api.Nothing;
import com.oracle.svm.webimage.fs.FileSystemInitializer;
import com.oracle.svm.webimage.fs.WebImageNIOFileSystemProvider;
import com.oracle.svm.webimage.functionintrinsics.ImplicitExceptions;
import com.oracle.svm.webimage.jni.WebImageNativeLibrarySupport;
import com.oracle.svm.webimage.longemulation.Long64;
import com.oracle.svm.webimage.platform.WebImagePlatform;
import com.oracle.svm.webimage.print.WebImageOutErrPrinters;
import com.oracle.svm.webimage.print.WebImagePrintStream;
import com.oracle.svm.webimage.substitute.WebImageHttpHandlerSubstitutions;
import com.oracle.svm.webimage.substitute.system.WebImageFileSystem;
import com.oracle.svm.webimage.substitute.system.WebImageTempFileHelper;
import com.oracle.svm.webimage.substitute.system.WebImageTempFileHelperSupport;
import com.oracle.svm.webimage.substitute.system.WebImageTempFileHelperSupportWithoutSecureRandom;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticallyRegisteredFeature
@Platforms(WebImagePlatform.class)
public class WebImageFeature implements InternalFeature {
    private final JSEntryPointRegistry entryPointsData = new JSEntryPointRegistry();

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        List<Class<? extends Feature>> result = new ArrayList<>(1);
        result.add(ProjectHeaderFileHeaderResolversRegistryFeature.class);
        return result;
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        ImplicitExceptions.registerForeignCalls(foreignCalls);
    }

    @Override
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        Predicate<ResolvedJavaMethod> mustNotAllocatePredicate = null;
        if (hosted) {
            mustNotAllocatePredicate = method -> ImageSingletons.lookup(RestrictHeapAccessCallees.class).mustNotAllocate(method);
        }

        WebImageNonSnippetLowerings.registerLowerings(runtimeConfig, mustNotAllocatePredicate, options, providers, lowerings, hosted);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        FeatureImpl.BeforeAnalysisAccessImpl accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        BigBang bigbang = accessImpl.getBigBang();

        // For DynamicNewArrayLowerer
        bigbang.addRootField(DynamicHub.class, "companion");
        bigbang.addRootField(DynamicHubCompanion.class, "arrayHub");

        // TODO GR-62854 Remove again once JSBodyFeature is enabled
        bigbang.addRootClass(Nothing.class, true, false);

        /**
         * Required by {@link com.oracle.svm.hosted.meta.KnownOffsetsFeature}. In SVM this becomes
         * reachable through {@link com.oracle.svm.core.graal.snippets.CEntryPointSnippets}. We have
         * to make it reachable explicitly.
         */
        Field codeStart = ReflectionUtil.lookupField(ImageCodeInfo.class, "codeStart");
        access.registerAsAccessed(codeStart);

        if (WebImageOptions.getBackend() == WebImageOptions.CompilerBackend.JS) {

            // Ensure that the long emulation gets lowered.
            for (Method m : Long64.class.getDeclaredMethods()) {
                assert Modifier.isStatic(m.getModifiers()) : m;
                accessImpl.registerAsRoot(m, true, "Long64 support, registered in " + WebImageFeature.class);
            }
        }

        // SystemJimfsFileSystemProvider uses reflection to look up and call this method
        RuntimeReflection.register(ReflectionUtil.lookupMethod(ReflectionUtil.lookupClass("org.graalvm.shadowed.com.google.common.jimfs.JimfsFileSystem"), "toPath", URI.class));

        /*
         * The constructors of these classes are package-private to prevent user code from creating
         * objects. However, internal code needs to be able to create instances.
         */
        for (Class<?> clazz : new Class<?>[]{JSNumber.class, JSBigInt.class, JSSymbol.class, JSBoolean.class, JSObject.class, JSString.class}) {
            RuntimeReflection.register(ReflectionUtil.lookupConstructor(clazz));
        }

        LowerableResources.processResources(access, WebImageHostedConfiguration.get());

        /*
         * Clear caches for Locale and BaseLocale.
         *
         * These caches can contribute ~1MB to the image size, clearing them avoids this overhead at
         * the cost of having to recreate the Locale and BaseLocale objects once when they're
         * requested at run-time.
         */
        Field baseLocaleCacheField = accessImpl.findField("sun.util.locale.BaseLocale", "CACHE");
        Field localeCacheField = accessImpl.findField("java.util.Locale", "LOCALE_CACHE");
        access.registerFieldValueTransformer(baseLocaleCacheField, new ResetStableSupplierTransformer());
        access.registerFieldValueTransformer(localeCacheField, new ResetStableSupplierTransformer());
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        FeatureImpl.DuringSetupAccessImpl access = (FeatureImpl.DuringSetupAccessImpl) a;

        String entryPointConfig = WebImageOptions.EntryPointsConfig.getValue(ImageSingletons.lookup(HostedOptionValues.class));
        if (entryPointConfig != null) {
            ConfigurationConditionResolver<ConfigurationCondition> conditionResolver = new NativeImageConditionResolver(access.getImageClassLoader(),
                            ClassInitializationSupport.singleton());
            ReflectionConfigurationParser<ConfigurationCondition, Class<?>> parser = ConfigurationParserUtils.create(ConfigurationFile.REFLECTION, false, conditionResolver, entryPointsData, null,
                            null, null, access.getImageClassLoader());
            try {
                parser.parseAndRegister(Path.of(entryPointConfig).toUri());
            } catch (IOException ex) {
                throw VMError.shouldNotReachHere("Error reading the entry points configuration file: ", ex);
            }

            for (Executable m : entryPointsData.entryPoints) {
                AnalysisMethod am = access.getBigBang().addRootMethod(m, true, "Entry points from " + entryPointConfig + ", registered in " + WebImageFeature.class);
                // The following line is required for the method code to be generated
                // TODO: why adding it as a root method is not enough?
                SubstrateCompilationDirectives.singleton().registerForcedCompilation(am);
            }
        }
        access.getHostVM().registerNeverInlineTrivialHandler(this::neverInlineTrivial);

        // We don't need a BufferedOutputStream around FileOutputStream because JS already
        // implements this buffering.
        SystemInOutErrSupport.setOut(new WebImagePrintStream(WebImageOutErrPrinters.out));
        SystemInOutErrSupport.setErr(new WebImagePrintStream(WebImageOutErrPrinters.err));

        ImageSingletons.add(RuntimeSystemPropertiesSupport.class, new WebImageSystemPropertiesSupport());
        /* GR-42971 - Remove once SystemPropertiesSupport.class ImageSingletons use is gone. */
        ImageSingletons.add(SystemPropertiesSupport.class, (SystemPropertiesSupport) ImageSingletons.lookup(RuntimeSystemPropertiesSupport.class));

        ImageSingletons.add(NativeImageCodeCacheFactory.class, HostedConfiguration.instance().newCodeCacheFactory());

        // Make sure a naming convention exists.
        WebImageNamingConvention.initialize();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        FeatureImpl.AfterRegistrationAccessImpl accessImpl = (FeatureImpl.AfterRegistrationAccessImpl) access;
        if (WebImageOptions.supportRuntime(WebImageOptions.VMType.Browser)) {
            ImageSingletons.add(WebImageHttpHandlerSubstitutions.class, new WebImageHttpHandlerSubstitutions());
        }
        if (WebImageOptions.UseRandomForTempFiles.getValue(HostedOptionValues.singleton())) {
            ImageSingletons.add(WebImageTempFileHelperSupport.class, new WebImageTempFileHelperSupportWithoutSecureRandom());
        } else {
            ImageSingletons.add(WebImageTempFileHelperSupport.class, new WebImageTempFileHelperSupport());
        }

        ProjectHeaderFile.HeaderResolversRegistry.registerAdditionalResolver(new ProjectHeaderFile.FallbackHeaderResolver("../graal/substratevm/src"));

        // Exceptions from the default class initialization rules.
        // Similar to com.oracle.svm.hosted.jdk.JDKInitializationFeature

        RuntimeClassInitializationSupport rci = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);

        // This class gets initialized, causing the "unintentionally initialized at build time"
        // error. The initializer is simple and does not depend on other classes, so just allow it.
        rci.initializeAtBuildTime("org.graalvm.shadowed.com.google.common.jimfs.SystemJimfsFileSystemProvider", "service provider");
        rci.initializeAtBuildTime("org.graalvm.shadowed.com.google.common.collect.MapMakerInternalMap", "service provider");
        rci.initializeAtBuildTime("org.graalvm.shadowed.com.google.common.collect.MapMakerInternalMap$1", "service provider");
        rci.initializeAtBuildTime("org.graalvm.shadowed.com.google.common.collect.MapMakerInternalMap$EntrySet", "service provider");
        rci.initializeAtBuildTime("org.graalvm.shadowed.com.google.common.collect.MapMakerInternalMap$StrongKeyWeakValueSegment", "service provider");
        rci.initializeAtBuildTime("org.graalvm.shadowed.com.google.common.collect.MapMakerInternalMap$StrongKeyWeakValueEntry$Helper", "service provider");
        rci.initializeAtBuildTime("org.graalvm.shadowed.com.google.common.base.Equivalence$Equals", "service provider");

        // Initializing this class at build-time helps in determining the type of its static field
        // "systemProvider", which is necessary to compile some reflective accesses to the object.
        rci.initializeAtBuildTime("org.graalvm.shadowed.com.google.common.jimfs.Jimfs", "looks for service provider");

        rci.initializeAtRunTime(WebImageTempFileHelper.class, "instances of Random are not allowed in the image heap");
        rci.initializeAtRunTime(WebImageFileSystem.class, "Static fields need to read system properties at runtime");
        rci.initializeAtRunTime(FileSystemInitializer.class, "Static fields need to read system properties at runtime");
        rci.initializeAtRunTime("java.nio.file.FileSystems$DefaultFileSystemHolder", "Parts of static initializer is substituted to inject custom FileSystemProvider");

        for (Class<? extends JSObject> jsObjectSubclass : accessImpl.findSubclasses(JSObject.class)) {
            rci.initializeAtRunTime(jsObjectSubclass,
                            "Initialize JSObject subclasses at runtime, since their custom constructors create mirrors and set up fields for the mirrors.");
        }
        ImageSingletons.add(PlatformNativeLibrarySupport.class, new WebImageNativeLibrarySupport());

        switch (WebImageOptions.getBackend()) {
            case JS, WASMGC -> Loggers.setRealLog(new NoopLog());
            case WASM -> Log.finalizeDefaultLogHandler(new WasmLogHandler());
        }

        /*
         * We do not support Java calls from VM code yet. We do not support JNI parameters yet.
         *
         * We only require the singleton for the "makeLinkage" method, therefore we do not register
         * this feature.
         */
        // TODO(GR-35288): Reenable and implement proper JNI support.
        ImageSingletons.add(JNIAccessFeature.class, new JNIAccessFeature());
        ImageSingletons.add(RuntimeJNIAccessSupport.class, new WebImageRuntimeJNIAccessSupport());

        /*
         * Registers our own file system provider, which replaces the default provider for the
         * 'file' scheme.
         */
        FileSystemProviderBuildTimeInitSupport.register(WebImageNIOFileSystemProvider.INSTANCE);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        FeatureImpl.BeforeCompilationAccessImpl accessImpl = (FeatureImpl.BeforeCompilationAccessImpl) access;
        DebugContext debugContext = accessImpl.getDebugContext();
        OptionValues options = debugContext.getOptions();
        ImageSingletons.add(WebImageProviders.class, WebImageHostedConfiguration.get().createProviders(accessImpl.getRuntimeConfiguration(),
                        WebImageOptions.compilerPrinter(options), debugContext));
    }

    private boolean neverInlineTrivial(@SuppressWarnings("unused") AnalysisMethod caller, AnalysisMethod callee) {
        /*
         * Methods annotated with @JS are never trivial.
         */
        return AnnotationAccess.isAnnotationPresent(callee, JS.class);
    }

}
