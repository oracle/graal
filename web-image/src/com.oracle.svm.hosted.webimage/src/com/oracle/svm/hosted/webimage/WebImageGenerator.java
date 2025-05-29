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

import static com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys.ANALYSIS_METHODS;
import static com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys.ANALYSIS_TYPES;
import static com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys.HOSTED_METHODS;
import static com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys.HOSTED_TYPES;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.Pair;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.webimage.api.JS;

import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.core.JavaMainWrapper;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.graal.code.SubstratePlatformConfigurationProvider;
import com.oracle.svm.core.heap.BarrierSetProvider;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageGenerator;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.image.AbstractImage;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.option.HostedOptionProvider;
import com.oracle.svm.hosted.webimage.codegen.WebImage;
import com.oracle.svm.hosted.webimage.codegen.heap.ConstantMap;
import com.oracle.svm.hosted.webimage.js.JSSubstitutionProcessor;
import com.oracle.svm.hosted.webimage.logging.LoggableMetric;
import com.oracle.svm.hosted.webimage.logging.LoggerContext;
import com.oracle.svm.hosted.webimage.logging.LoggerScope;
import com.oracle.svm.hosted.webimage.logging.visualization.VisualizationSupport;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.wasm.annotation.WasmStartFunction;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmWebImage;
import com.oracle.svm.webimage.platform.WebImagePlatformConfigurationProvider;
import com.oracle.svm.webimage.wasmgc.annotation.WasmExport;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.MetricKey;
import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.MetaAccessProvider;

public class WebImageGenerator extends NativeImageGenerator {
    public static final String UNIVERSE_BUILD_SCOPE_NAME = "Universe-Build";
    public static final String COMPILE_QUEUE_SCOPE_NAME = "Compile-Queue";
    public static final String WebImageTotalTime = "total-WebImage";
    public static final String CodegenTimer = "codegen";

    /**
     * This method becomes the main entry point for non-executable images (e.g. shared libraries).
     */
    private final Method libraryInit;

    private AbstractImage.NativeImageKind imageKind;

    public WebImageGenerator(ImageClassLoader loader, HostedOptionProvider optionProvider, Pair<Method, CEntryPointData> mainEntryPoint, ProgressReporter reporter) {
        super(loader, optionProvider, mainEntryPoint, reporter);

        this.libraryInit = NativeImageWasmGeneratorRunner.getLibraryEntyPointMethod(loader);
    }

    private static void saveUniverseCounters(LoggerScope scope, @SuppressWarnings("unused") UnmodifiableEconomicMap<MetricKey, LoggableMetric> metrics) {
        LoggerContext.currentContext().saveCounters(scope, ANALYSIS_TYPES, HOSTED_TYPES, ANALYSIS_METHODS, HOSTED_METHODS);
    }

    @SuppressWarnings("try")
    @Override
    protected void doRun(Map<Method, CEntryPointData> entryPoints,
                    JavaMainWrapper.JavaMainSupport javaMainSupport, String imageName, AbstractImage.NativeImageKind k,
                    SubstitutionProcessor harnessSubstitutions) {
        OptionValues options = HostedOptionValues.singleton();
        setWebImageSystemProperties();
        try (LoggerContext loggerContext = new LoggerContext.Builder(options).stream(WebImageOptions.compilerPrinter(options)).build()) {
            try (Timer.StopTimer ignoredTimer = TimerCollection.createTimerAndStart(WebImageTotalTime)) {

                imageKind = k;
                super.doRun(entryPoints, javaMainSupport, imageName, k, new JSSubstitutionProcessor());

                try (LoggerScope universeScope = LoggerContext.currentContext().scope(UNIVERSE_BUILD_SCOPE_NAME, WebImageGenerator::saveUniverseCounters)) {
                    LoggerContext.counter(ANALYSIS_TYPES).add(bb.getUniverse().getTypes().size());
                    LoggerContext.counter(ANALYSIS_METHODS).add(bb.getUniverse().getMethods().size());
                    LoggerContext.counter(HOSTED_TYPES).add(hUniverse.getTypes().size());
                    LoggerContext.counter(HOSTED_METHODS).add(hUniverse.getMethods().size());
                }
            }
            VisualizationSupport.get().visualize(WebImageOptions.compilerPrinter(options));
        }
    }

    @Override
    protected boolean isStubBasedPluginsSupported() {
        return false;
    }

    @Override
    protected SubstratePlatformConfigurationProvider getPlatformConfig(MetaAccessProvider aMetaAcess) {
        BarrierSet barrierSet = ImageSingletons.lookup(BarrierSetProvider.class).createBarrierSet(aMetaAcess);
        return new WebImagePlatformConfigurationProvider(barrierSet);
    }

    @Override
    protected void createAbstractImage(AbstractImage.NativeImageKind k, List<HostedMethod> hostedEntryPoints, NativeImageHeap heap, HostedMetaAccess hMetaAccess,
                    NativeImageCodeCache codeCache) {
        /*
         * For executable images, use the main entry point as provided by native image. Otherwise,
         * pass on the library initialization code as the main entry point.
         */
        HostedMethod mainEntryPointMethod = hMetaAccess.lookupJavaMethod(k.isExecutable ? mainEntryPoint.getLeft() : libraryInit);
        this.image = switch (WebImageOptions.getBackend()) {
            // For now the WasmGC backend does not require its own specialized WebImage subclass and
            // WasmWebImage has linear-memory specific code
            case JS -> new WebImage(k, hUniverse, hMetaAccess, nativeLibraries, heap, codeCache, hostedEntryPoints, loader, mainEntryPointMethod);
            case WASM, WASMGC -> new WasmWebImage(k, hUniverse, hMetaAccess, nativeLibraries, heap, codeCache, hostedEntryPoints, loader, mainEntryPointMethod);
        };
    }

    private static void setWebImageSystemProperties() {
        System.setProperty("svm.targetName", "Browser");
        System.setProperty("svm.targetArch", "ECMAScript 2015");
        System.setProperty(ImageInfo.PROPERTY_IMAGE_KIND_KEY, ImageInfo.PROPERTY_IMAGE_KIND_VALUE_EXECUTABLE);
    }

    @Override
    protected void setDefaultConfiguration() {
        // this must precede the super call such that our HostedConfiguration is used
        WebImageHostedConfiguration.setDefaultIfEmpty();
        super.setDefaultConfiguration();
    }

    @Override
    protected void registerEntryPoints(Map<Method, CEntryPointData> entryPoints) {
        if (WebImageOptions.getBackend() == WebImageOptions.CompilerBackend.WASM || WebImageOptions.getBackend() == WebImageOptions.CompilerBackend.WASMGC) {
            List<Method> startFunctions = loader.findAnnotatedMethods(WasmStartFunction.class);
            GraalError.guarantee(startFunctions.size() <= 1, "Only a single start function must exist: %s", startFunctions);

            if (!startFunctions.isEmpty()) {
                Method startFunction = startFunctions.getFirst();
                GraalError.guarantee(Modifier.isStatic(startFunction.getModifiers()), "Start function %s.%s is not static.", startFunction.getDeclaringClass().getName(), startFunction.getName());

                GraalError.guarantee(startFunction.getParameterCount() == 0 && startFunction.getReturnType() == void.class, "Start function %s.%s must not have arguments or a return value.",
                                startFunction.getDeclaringClass().getName(), startFunction.getName());

                entryPoints.put(startFunction, null);
            }

            for (Method m : loader.findAnnotatedMethods(WasmExport.class)) {
                GraalError.guarantee(Modifier.isStatic(m.getModifiers()), "Exported method %s.%s is not static. Add a static modifier to the method.", m.getDeclaringClass().getName(), m.getName());
                entryPoints.put(m, null);
            }
        }

        if (!imageKind.isExecutable) {
            /*
             * For non-executable images (shared libraries), the library initialization code needs
             * to be an entry point.
             */
            entryPoints.put(libraryInit, null);
        }

        for (Class<?> c : loader.findAnnotatedClasses(JS.Export.class, false)) {
            for (Method m : c.getDeclaredMethods()) {
                if (Modifier.isAbstract(m.getModifiers())) {
                    entryPoints.put(m, null);
                }
            }
        }
    }

    @Override
    protected void registerEntryPointStubs(Map<Method, CEntryPointData> entryPoints) {
        entryPoints.forEach((method, entryPointData) -> {
            bb.addRootMethod(method, true, "Entry point, registered in " + WebImageGenerator.class).registerAsNativeEntryPoint(new WebImageEntryPointData());
        });
    }

    /**
     * Currently we have our own heap encoding in the JS backend, there we do nothing. See
     * {@link ConstantMap}. Without this, we have a conflict regarding interning strings for which
     * we have a custom handling.
     */
    @Override
    protected void buildNativeImageHeap(NativeImageHeap heap, NativeImageCodeCache codeCache) {
        if (WebImageOptions.getBackend() != WebImageOptions.CompilerBackend.JS) {
            super.buildNativeImageHeap(heap, codeCache);
        }
    }

    @Override
    protected void checkForInvalidCallsToEntryPoints() {
    }

    @Override
    protected SubstrateTargetDescription createTarget() {
        Architecture architecture = GraalAccess.getOriginalTarget().arch;
        return new SubstrateTargetDescription(architecture, false, 16, 0, null);
    }
}
