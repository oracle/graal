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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CShortPointer;

import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.core.JavaMainWrapper;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.ReplacingLocatableMultiOptionValue;
import com.oracle.svm.core.util.ExitStatus;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageGenerator;
import com.oracle.svm.hosted.NativeImageGeneratorRunner;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.c.CAnnotationProcessorCache;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.image.AbstractImage;
import com.oracle.svm.hosted.jdk.localization.LocalizationFeature;
import com.oracle.svm.hosted.option.HostedOptionParser;
import com.oracle.svm.hosted.webimage.logging.visualization.VisualizationSupport;
import com.oracle.svm.hosted.webimage.name.WebImageNamingConvention;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.options.WebImageOptions.CompilerBackend;
import com.oracle.svm.hosted.webimage.util.BenchmarkLogger;
import com.oracle.svm.hosted.webimage.wasm.WebImageWasmLMJavaMainSupport;
import com.oracle.svm.hosted.webimage.wasm.codegen.BinaryenCompat;
import com.oracle.svm.hosted.webimage.wasmgc.WebImageWasmGCJavaMainSupport;
import com.oracle.svm.webimage.WebImageJSJavaMainSupport;
import com.oracle.svm.webimage.WebImageJavaMainSupport;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionValues;

/**
 * Main entry point called from the driver for Web Image (despite the name) and the Native Image
 * Wasm backend.
 */
public class NativeImageWasmGeneratorRunner extends NativeImageGeneratorRunner {

    /**
     * @param args Hosted and runtime options
     */
    public static void main(String[] args) {
        new NativeImageWasmGeneratorRunner().start(args);
    }

    /**
     * Gathers all available hosted options declared in Web Image code.
     * <p>
     * This is used to verify that the hardcoded {@code ProvidedHostedOptions} property in the
     * {@code svm-wasm} tool macro contains all option names.
     */
    private static void dumpProvidedHostedOptions(HostedOptionParser optionParser) {
        EconomicMap<String, OptionDescriptor> allHostedOptions = optionParser.getAllHostedOptions();

        List<String> names = new ArrayList<>();

        for (OptionDescriptor value : allHostedOptions.getValues()) {
            if (!value.getDeclaringClass().getPackageName().contains("webimage")) {
                continue;
            }

            if (WebImageOptions.DebugOptions.DumpProvidedHostedOptionsAndExit == value.getOptionKey()) {
                continue;
            }

            // Do not print the Backend option, it's not available in the svm-wasm macro
            if (WebImageOptions.Backend == value.getOptionKey()) {
                continue;
            }

            String name = value.getName();

            if (value.getOptionValueType().equals(Boolean.class)) {
                names.add(name);
            } else {
                names.add(name + "=");
            }
        }

        names.sort(Comparator.naturalOrder());

        for (String name : names) {
            System.out.println(name);
        }
    }

    @Override
    public int build(ImageClassLoader classLoader) {
        final HostedOptionParser optionProvider = classLoader.classLoaderSupport.getHostedOptionParser();

        if (Boolean.TRUE.equals(optionProvider.getHostedValues().get(WebImageOptions.DebugOptions.DumpProvidedHostedOptionsAndExit))) {
            dumpProvidedHostedOptions(optionProvider);
            return ExitStatus.OK.getValue();
        }

        // Turn off fallback images, Web Image cannot be built as a fallback image.
        optionProvider.getHostedValues().put(SubstrateOptions.FallbackThreshold, SubstrateOptions.NoFallback);

        // We do not need to compile a GC because the JavaScript environment provides one.
        optionProvider.getHostedValues().put(SubstrateOptions.SupportedGCs, ReplacingLocatableMultiOptionValue.DelimitedString.buildWithCommaDelimiter());

        // Forcibly turn off CAnnotation processor cache
        optionProvider.getHostedValues().put(CAnnotationProcessorCache.Options.UseCAPCache, false);

        optionProvider.getHostedValues().put(SubstrateOptions.CompilerBackend, "webImage");
        /**
         * SVM provides two approaches of localization support:
         *
         * {@link com.oracle.svm.core.jdk.localization.OptimizedLocalizationSupport} and
         * {@link com.oracle.svm.core.jdk.localization.BundleContentSubstitutedLocalizationSupport}
         *
         * The latter depends on GZIPInputStream, which is not supported by Web Image Therefore, we
         * always use the first one.
         *
         * @see LocalizationFeature
         */
        optionProvider.getHostedValues().put(LocalizationFeature.Options.LocalizationOptimizedMode, true);

        // reduce image size
        optionProvider.getHostedValues().put(SubstrateOptions.IncludeMethodData, false);

        // we do not use the runtime option parser unless requested otherwise, in order to avoid
        // compiling unnecessary code
        if (!optionProvider.getHostedValues().containsKey(SubstrateOptions.ParseRuntimeOptions)) {
            optionProvider.getHostedValues().put(SubstrateOptions.ParseRuntimeOptions, false);
        }

        // force closed-world
        optionProvider.getHostedValues().put(SubstrateOptions.ClosedTypeWorld, true);

        CompilerBackend backend = WebImageOptions.getBackend(classLoader);

        if (backend == CompilerBackend.WASM || backend == CompilerBackend.WASMGC) {
            // For the Wasm backends, turn off closure compiler
            optionProvider.getHostedValues().put(WebImageOptions.ClosureCompiler, false);

            if (backend == CompilerBackend.WASMGC && !optionProvider.getHostedValues().containsKey(BinaryenCompat.Options.UseBinaryen)) {
                // For WasmGC backend, use binaryen by default
                optionProvider.getHostedValues().put(BinaryenCompat.Options.UseBinaryen, true);
            }

            if (!optionProvider.getHostedValues().containsKey(WebImageOptions.NamingConvention)) {
                // The naming convention does not affect the binary image (unless debug information
                // is embedded) and the REDUCED mode makes the text file a lot easier to read
                optionProvider.getHostedValues().put(WebImageOptions.NamingConvention, WebImageNamingConvention.NamingMode.REDUCED);
            }
        }

        if (WebImageOptions.isNativeImageBackend()) {
            // The Web Image visualization should not appear in the native-image launcher
            optionProvider.getHostedValues().put(VisualizationSupport.Options.Visualization, "");
        }

        return super.build(classLoader);
    }

    @Override
    protected void reportEpilog(String imageName, ProgressReporter reporter, ImageClassLoader classLoader, BuildOutcome buildOutcome, Throwable vmError, OptionValues parsedHostedOptions) {
        super.reportEpilog(imageName, reporter, classLoader, buildOutcome, vmError, parsedHostedOptions);
        if (buildOutcome.successful()) {
            BenchmarkLogger.printBuildTime((int) TimerCollection.singleton().get(TimerCollection.Registry.TOTAL).getTotalTime(), parsedHostedOptions);
        }
    }

    @Override
    protected NativeImageGenerator createImageGenerator(ImageClassLoader classLoader, HostedOptionParser optionParser, Pair<Method, CEntryPointData> mainEntryPointData, ProgressReporter reporter) {
        return new WebImageGenerator(classLoader, optionParser, mainEntryPointData, reporter);
    }

    @Override
    protected Pair<Method, CEntryPointData> createMainEntryPointData(AbstractImage.NativeImageKind imageKind, Method mainEntryPoint) {
        return Pair.createLeft(mainEntryPoint);
    }

    @Override
    protected Method getMainEntryMethod(ImageClassLoader classLoader) throws NoSuchMethodException {
        return switch (WebImageOptions.getBackend(classLoader)) {
            case JS -> WebImageJavaMainSupport.class.getDeclaredMethod("run", String[].class);
            case WASM -> WebImageWasmLMJavaMainSupport.class.getDeclaredMethod("run", int.class, CIntPointer.class, CShortPointer.class);
            case WASMGC -> WebImageWasmGCJavaMainSupport.class.getDeclaredMethod("run", String[].class);
        };
    }

    protected static Method getLibraryEntyPointMethod(ImageClassLoader classLoader) {
        try {
            return switch (WebImageOptions.getBackend(classLoader)) {
                case JS -> WebImageJavaMainSupport.class.getDeclaredMethod("initializeLibrary", String[].class);
                case WASM -> WebImageWasmLMJavaMainSupport.class.getDeclaredMethod("initializeLibrary", int.class, CIntPointer.class, CShortPointer.class);
                case WASMGC -> WebImageWasmGCJavaMainSupport.class.getDeclaredMethod("initializeLibrary", String[].class);
            };
        } catch (NoSuchMethodException e) {
            throw GraalError.shouldNotReachHere(e, "Could not reflectively lookup internal library entry point.");
        }
    }

    @Override
    protected JavaMainWrapper.JavaMainSupport createJavaMainSupport(Method javaMainMethod, ImageClassLoader classLoader) throws IllegalAccessException {
        return switch (WebImageOptions.getBackend(classLoader)) {
            case JS -> new WebImageJSJavaMainSupport(javaMainMethod);
            case WASM -> new WebImageWasmLMJavaMainSupport(javaMainMethod);
            case WASMGC -> new WebImageWasmGCJavaMainSupport(javaMainMethod);
        };
    }

    @Override
    protected void verifyMainEntryPoint(Method mainEntryPoint) {
    }
}
