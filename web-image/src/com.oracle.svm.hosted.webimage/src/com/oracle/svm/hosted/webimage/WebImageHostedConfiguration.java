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

import static com.oracle.svm.hosted.webimage.codegen.LowerableResources.bootstrap;
import static com.oracle.svm.hosted.webimage.codegen.LowerableResources.extra;
import static com.oracle.svm.hosted.webimage.codegen.LowerableResources.optional;
import static com.oracle.svm.hosted.webimage.codegen.LowerableResources.runtime;
import static com.oracle.svm.hosted.webimage.codegen.LowerableResources.thirdParty;
import static com.oracle.svm.hosted.webimage.options.WebImageOptions.getBackend;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodTypeFlowBuilder;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.svm.core.MissingRegistrationSupport;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.config.ObjectLayout.IdentityHashMode;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.hosted.FeatureHandler;
import com.oracle.svm.hosted.HeapBreakdownProvider;
import com.oracle.svm.hosted.HostedConfiguration;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.config.HybridLayoutSupport;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.image.NativeImageCodeCacheFactory;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import com.oracle.svm.hosted.webimage.code.WebImageJSCompileQueue;
import com.oracle.svm.hosted.webimage.codegen.JSCodeGenTool;
import com.oracle.svm.hosted.webimage.codegen.LowerableResource;
import com.oracle.svm.hosted.webimage.codegen.WebImageCodeGen;
import com.oracle.svm.hosted.webimage.codegen.WebImageEntryFunctionLowerer;
import com.oracle.svm.hosted.webimage.codegen.WebImageJSCodeGen;
import com.oracle.svm.hosted.webimage.codegen.WebImageJSNodeLowerer;
import com.oracle.svm.hosted.webimage.codegen.WebImageJSProviders;
import com.oracle.svm.hosted.webimage.codegen.WebImageProviders;
import com.oracle.svm.hosted.webimage.codegen.heap.JSBootImageHeapLowerer;
import com.oracle.svm.hosted.webimage.codegen.type.InvokeLoweringUtil;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.pointsto.WebImageMethodTypeFlowBuilder;
import com.oracle.svm.hosted.webimage.wasm.WebImageWasmCodeCache;
import com.oracle.svm.hosted.webimage.wasm.WebImageWasmHeapBreakdownProvider;
import com.oracle.svm.hosted.webimage.wasm.WebImageWasmLMCompileQueue;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmEntryFunctionLowerer;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmLMCodeGen;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmProviders;
import com.oracle.svm.hosted.webimage.wasmgc.WebImageWasmGCCodeCache;
import com.oracle.svm.hosted.webimage.wasmgc.WebImageWasmGCCompileQueue;
import com.oracle.svm.hosted.webimage.wasmgc.codegen.WebImageWasmGCCodeGen;
import com.oracle.svm.hosted.webimage.wasmgc.codegen.WebImageWasmGCProviders;
import com.oracle.svm.webimage.object.ConstantIdentityMapping;

import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.options.OptionValues;

/**
 * Default configuration for Web Image.
 *
 * It serves as an abstraction of important policies for Web Image.
 */
public class WebImageHostedConfiguration extends HostedConfiguration {

    public static void setDefaultIfEmpty() {
        if (!ImageSingletons.contains(HostedConfiguration.class)) {
            ImageSingletons.add(HostedConfiguration.class, new WebImageHostedConfiguration());
            CompressEncoding compressEncoding = new CompressEncoding(0, 0);
            ImageSingletons.add(CompressEncoding.class, compressEncoding);
            ObjectLayout objectLayout = createObjectLayout(IdentityHashMode.OBJECT_HEADER);
            ImageSingletons.add(ObjectLayout.class, objectLayout);
            ImageSingletons.add(HybridLayoutSupport.class, new HybridLayoutSupport());
        }
    }

    @Override
    public SVMHost createHostVM(OptionValues options, ImageClassLoader loader, ClassInitializationSupport classInitializationSupport, AnnotationSubstitutionProcessor annotationSubstitutions,
                    MissingRegistrationSupport missingRegistrationSupport) {
        return new WebImageHost(options, loader, classInitializationSupport, annotationSubstitutions, missingRegistrationSupport);
    }

    public static WebImageHostedConfiguration get() {
        return (WebImageHostedConfiguration) ImageSingletons.lookup(HostedConfiguration.class);
    }

    public JSCodeBuffer createCodeBuffer(OptionValues options) {
        return new JSCodeBuffer(options);
    }

    public JSBootImageHeapLowerer createBootImageHeapLowerer(WebImageJSProviders providers, JSCodeGenTool jsLTools, ConstantIdentityMapping identityMapping) {
        return new JSBootImageHeapLowerer(providers, jsLTools, identityMapping);
    }

    public WebImageCodeGen createCodeGen(WebImageCodeCache codeCache, List<HostedMethod> hostedEntryPoints, HostedMethod mainEntryPoint,
                    WebImageProviders providers, DebugContext debug, ImageClassLoader imageClassLoader) {
        return switch (getBackend()) {
            case JS -> new WebImageJSCodeGen(codeCache, hostedEntryPoints, mainEntryPoint, providers, debug, this, imageClassLoader);
            case WASM -> new WebImageWasmLMCodeGen(codeCache, hostedEntryPoints, mainEntryPoint, providers, debug, this);
            case WASMGC -> new WebImageWasmGCCodeGen(codeCache, hostedEntryPoints, mainEntryPoint, providers, debug, this);
        };
    }

    public WebImageJSNodeLowerer createNodeLowerer(JSCodeGenTool jsCodeGenTool) {
        return new WebImageJSNodeLowerer(jsCodeGenTool);
    }

    public WebImageEntryFunctionLowerer getEntryFunctionLowerer() {
        return switch (getBackend()) {
            case JS -> new WebImageEntryFunctionLowerer();
            case WASM, WASMGC -> new WebImageWasmEntryFunctionLowerer();
        };
    }

    /**
     * Collects a list of {@link LowerableResource}s that are used in the current compilation and
     * may affect the analysis.
     *
     * This method could be overridden by a subclass.
     */
    public List<LowerableResource> getAnalysisResources() {
        return Stream.concat(Arrays.stream(bootstrap), getJavaRelatedResources().stream()).collect(Collectors.toList());
    }

    /**
     * All resources that may contain reference to Java code.
     */
    public List<LowerableResource> getJavaRelatedResources() {
        if (getBackend() == WebImageOptions.CompilerBackend.JS) {
            return Stream.concat(Stream.concat(Stream.concat(Arrays.stream(runtime), Arrays.stream(extra)), optional.stream()), thirdParty.stream()).collect(Collectors.toList());
        } else {
            return List.of();
        }
    }

    public List<LowerableResource> getBootstrapResources() {
        return Arrays.stream(bootstrap).collect(Collectors.toList());
    }

    public InvokeLoweringUtil getInvokeLoweringUtil() {
        return new InvokeLoweringUtil();
    }

    @Override
    public MethodTypeFlowBuilder createMethodTypeFlowBuilder(PointsToAnalysis bb, PointsToAnalysisMethod method, MethodFlowsGraph flowsGraph, MethodFlowsGraph.GraphKind graphKind) {
        return new WebImageMethodTypeFlowBuilder(bb, method, flowsGraph, graphKind);
    }

    @Override
    public void collectMonitorFieldInfo(BigBang bb, HostedUniverse hUniverse, Set<AnalysisType> immutableTypes) {
        // Do nothing. We do not have/need monitor fields in Web Image
    }

    @Override
    public NativeImageCodeCacheFactory newCodeCacheFactory() {
        return new NativeImageCodeCacheFactory() {
            @Override
            public NativeImageCodeCache newCodeCache(CompileQueue compileQueue, NativeImageHeap heap, Platform targetPlatform, Path tempDir) {
                return switch (getBackend()) {
                    case JS -> new WebImageCodeCache(compileQueue.getCompilationResults(), heap);
                    case WASM -> new WebImageWasmCodeCache(compileQueue.getCompilationResults(), heap);
                    case WASMGC -> new WebImageWasmGCCodeCache(compileQueue.getCompilationResults(), heap);
                };
            }
        };

    }

    @Override
    public HeapBreakdownProvider createHeapBreakdownProvider() {
        return switch (getBackend()) {
            case JS -> new WebImageJSHeapBreakdownProvider();
            case WASM, WASMGC -> new WebImageWasmHeapBreakdownProvider();
        };
    }

    public WebImageProviders createProviders(RuntimeConfiguration runtimeConfig, PrintStream compilerPrinter, DebugContext debug) {
        return switch (getBackend()) {
            case JS -> new WebImageJSProviders(runtimeConfig.getProviders(), compilerPrinter, debug);
            case WASM -> new WebImageWasmProviders(runtimeConfig, runtimeConfig.getProviders(), compilerPrinter, debug);
            case WASMGC -> new WebImageWasmGCProviders(runtimeConfig, runtimeConfig.getProviders(), compilerPrinter, debug);
        };
    }

    @Override
    public CompileQueue createCompileQueue(DebugContext debug, FeatureHandler featureHandler, HostedUniverse hostedUniverse,
                    RuntimeConfiguration runtimeConfiguration, boolean deoptimizeAll) {
        return switch (getBackend()) {
            case JS -> new WebImageJSCompileQueue(featureHandler, hostedUniverse, runtimeConfiguration, debug);
            case WASM -> new WebImageWasmLMCompileQueue(featureHandler, hostedUniverse, runtimeConfiguration, debug);
            case WASMGC -> new WebImageWasmGCCompileQueue(featureHandler, hostedUniverse, runtimeConfiguration, debug);
        };
    }
}
