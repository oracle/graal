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
package com.oracle.svm.hosted.webimage.codegen;

import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.CONSTANT_SIZE_CLASSES;
import static com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys.EMITTED_METHODS;
import static com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys.EMITTED_TYPES;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.graalvm.collections.UnmodifiableEconomicMap;

import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageGenerator;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.webimage.CodeSizeDiagnostics;
import com.oracle.svm.hosted.webimage.JSCodeBuffer;
import com.oracle.svm.hosted.webimage.Labeler;
import com.oracle.svm.hosted.webimage.WebImageCodeCache;
import com.oracle.svm.hosted.webimage.WebImageHostedConfiguration;
import com.oracle.svm.hosted.webimage.codegen.compatibility.JSBenchmarkingCode;
import com.oracle.svm.hosted.webimage.logging.LoggableMetric;
import com.oracle.svm.hosted.webimage.logging.LoggerContext;
import com.oracle.svm.hosted.webimage.logging.LoggerScope;
import com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys;
import com.oracle.svm.hosted.webimage.metrickeys.MethodMetricKeys;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.options.WebImageOptions.CommentVerbosity;
import com.oracle.svm.hosted.webimage.util.metrics.CodeSizeCollector;
import com.oracle.svm.hosted.webimage.util.metrics.ImageMetricsCollector;
import com.oracle.svm.hosted.webimage.util.metrics.MethodMetricsCollector;
import com.oracle.svm.webimage.NamingConvention;
import com.oracle.svm.webimage.hightiercodegen.Emitter;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.MetricKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.common.JVMCIError;

public abstract class WebImageCodeGen {

    /**
     * If the name of the scope gets changed, please update the appropriate benchmark parsing rule.
     */
    public static final String CODE_GEN_SCOPE_NAME = "Code-Generation";
    public static final String WebImageEmitTimer = "emit";

    /**
     * DEBUG only, prints information about the types that were not found during "escaping" types.
     * <p>
     * TODO make option
     */
    protected static final boolean PRINT_OMITTED_TYPES = true;

    protected final OptionValues options;
    protected final JSCodeBuffer codeBuffer;

    protected final HostedUniverse hUniverse;
    protected final HostedMetaAccess hMetaAccess;
    private final WebImageProviders providers;
    protected final DebugContext debug;
    protected final String imageName;

    protected final String filename;
    /**
     * The lowering tool {@link JSCodeGenTool} to lower code.
     */
    protected final JSCodeGenTool codeGenTool;
    /**
     * Can be used to add timer instrumentation to JS code.
     */
    protected JSBenchmarkingCode benchmarkingCode;
    protected final MethodMetricsCollector methodMetricsCollector;
    protected final Labeler labeler;
    protected int compiledMethodBytes;

    protected final List<HostedMethod> hostedEntryPoints;

    /**
     * The method that is called when the image is run.
     * <p>
     * If this is an executable image, this will in turn call into the main entry point of the
     * client code. For shared libraries (see {@link SubstrateOptions#SharedLibrary}, this will only
     * run some initialization code.
     *
     * @see com.oracle.svm.webimage.WebImageJavaMainSupport#run(String[])
     * @see com.oracle.svm.webimage.WebImageJavaMainSupport#initializeLibrary(String[])
     * @see com.oracle.svm.hosted.webimage.codegen.compatibility.JSEntryPointCode
     */
    protected final HostedMethod mainEntryPoint;

    protected final WebImageCodeCache codeCache;
    protected final Map<HostedMethod, WebImageCompilationResult> compilations;

    protected final WebImageHostedConfiguration configuration;

    protected WebImageCodeGen(WebImageCodeCache codeCache, List<HostedMethod> hostedEntryPoints, HostedMethod mainEntryPoint, WebImageProviders providers, DebugContext debug,
                    WebImageHostedConfiguration config) {
        this.hMetaAccess = (HostedMetaAccess) providers.getMetaAccess();
        this.codeCache = codeCache;
        this.compilations = codeCache.webImageCompilationResults;
        this.hostedEntryPoints = hostedEntryPoints;
        this.mainEntryPoint = Objects.requireNonNull(mainEntryPoint);
        this.configuration = config;
        this.options = debug.getOptions();
        this.codeBuffer = config.createCodeBuffer(options);
        this.hUniverse = this.hMetaAccess.getUniverse();
        this.imageName = SubstrateOptions.Name.getValue(options);
        this.filename = this.imageName + ".js";
        this.methodMetricsCollector = new MethodMetricsCollector(codeBuffer);
        this.providers = providers;
        this.labeler = providers.labeler();
        this.debug = debug;
        this.codeGenTool = new JSCodeGenTool(providers, codeBuffer, configuration, new WebImageVariableAllocation());

        // set up method size provider
        CodeSizeDiagnostics.installMethodSizeProvider(method -> {
            try {
                return this.methodMetricsCollector.getMethodMetric(method, MethodMetricKeys.METHOD_SIZE).intValue();
            } catch (NullPointerException ex) {
                // Not all hosted methods are lowered due the the optimization with TypeControl.
                return 0;
            }
        });
    }

    protected WebImageProviders getProviders() {
        return providers;
    }

    private void saveCodegenCounters(LoggerScope scope, @SuppressWarnings("unused") UnmodifiableEconomicMap<MetricKey, LoggableMetric> metrics) {
        List<MetricKey> keys = new ArrayList<>();
        addSavedCodegenCounters(keys);
        LoggerContext.currentContext().saveCounters(scope, keys.toArray(new MetricKey[0]));
    }

    protected void addSavedCodegenCounters(List<MetricKey> keys) {
        keys.add(EMITTED_TYPES);
        keys.add(EMITTED_METHODS);
        keys.addAll(CONSTANT_SIZE_CLASSES);
    }

    @SuppressWarnings("try")
    public static WebImageCodeGen generateCode(WebImageCodeCache codeCache, List<HostedMethod> hostedEntryPoints, HostedMethod mainEntryPoint, WebImageProviders providers, DebugContext debug,
                    WebImageHostedConfiguration config, ImageClassLoader imageClassLoader) {
        WebImageCodeGen codegen = config.createCodeGen(codeCache, hostedEntryPoints, mainEntryPoint, providers, debug, imageClassLoader);
        try (LoggerScope loggerScope = LoggerContext.currentContext().scope(CODE_GEN_SCOPE_NAME, codegen::saveCodegenCounters)) {
            codegen.buildImage();
            return codegen;
        }
    }

    @SuppressWarnings("try")
    protected void buildImage() {
        try (Timer.StopTimer t = TimerCollection.createTimerAndStart(WebImageEmitTimer)) {
            emitCode();
        }

        postProcess();
    }

    protected void afterHeapLayout() {
        // after this point, the layout is final and must not be changed anymore
        assert !hasDuplicatedObjects(codeCache.nativeImageHeap) : "heap.getObjects() must not contain any duplicates";
        BuildPhaseProvider.markHeapLayoutFinished();
        codeCache.nativeImageHeap.getLayouter().afterLayout(codeCache.nativeImageHeap);
    }

    protected boolean hasDuplicatedObjects(NativeImageHeap heap) {
        Set<NativeImageHeap.ObjectInfo> deduplicated = Collections.newSetFromMap(new IdentityHashMap<>());
        deduplicated.addAll(heap.getObjects());
        return deduplicated.size() != heap.getObjectCount();
    }

    /**
     * Emit JS code into {@link #codeBuffer}.
     */
    protected abstract void emitCode();

    /**
     * Post-process the generated code.
     * <p>
     * Collect metrics, optimize, etc.
     */
    protected abstract void postProcess();

    /**
     * Emits the skeleton structure of a WebImage JS file.
     * <p>
     * Subclasses can override hooks called in the skeleton to emit code in the appropriate places.
     *
     * <pre>
     * {@code
     * // {@link #emitPreamble()}
     * var GraalVM = {};
     *
     * (function() { // JS.Code section
     *     (function() { // Untrusted code section
     *         (function() { // VM internals section
     *             // Handwritten bootstrap JavaScript code
     *             // {@link #emitBootstrapDefinitions()}
     *             const createVM = function(vmArgs, data) {
     *                 // Handwritten runtime JavaScript code
     *                 // {@link #emitRuntimeDefinitions()}
     *                 // Compiled JavaScript code
     *                 // {@link #emitCompiledCode()}
     *                 // Call to entry point
     *                 // {@link #emitEntryPointCall()}
     *             }
     *         })();
     *     })();
     *     // User-supplied JavaScript code
     *     // {@link #emitJSCodeFiles()}
     * })();
     *
     * (function() {
     *     // Optional autostart code.
     * })();
     * }
     * </pre>
     */
    @SuppressWarnings("try")
    protected void emitJSCode() {
        try (ImageMetricsCollector collector = new ImageMetricsCollector.PreClosure(codeBuffer)) {
            doEmitJSFile();
        }
    }

    @SuppressWarnings("try")
    private void doEmitJSFile() {
        emitPreamble();

        // use strict mode
        codeBuffer.emitStringLiteral("use strict");
        codeBuffer.emitInsEnd();

        codeBuffer.emitNewLine();

        codeBuffer.emitNewLine();
        codeBuffer.emitText("/**");
        codeBuffer.emitNewLine();
        codeBuffer.emitText(" * @suppress {checkVars,checkTypes,duplicate}");
        codeBuffer.emitNewLine();
        /*
         * So that the closure compiler does not collapse the vm object properties into their own
         * variables.
         */
        codeBuffer.emitText(" * @nocollapse");
        codeBuffer.emitNewLine();
        codeBuffer.emitText("*/");
        codeBuffer.emitNewLine();
        codeBuffer.emitText("var " + codeGenTool.vmClassName() + " = {};");
        codeBuffer.emitNewLine();

        benchmarkingCode = new JSBenchmarkingCode(codeGenTool, options);
        benchmarkingCode.lowerInitialDefinition();

        // Start code section that contains resources included with the JS.Code annotation.
        // This are larger sections of JavaScript code that is executed once,
        codeBuffer.emitText("(function() {");
        codeGenTool.genComment("JS.Code section", CommentVerbosity.MINIMAL);

        // Start untrusted code section.
        // This includes interop functions such shared library execution, and in the future code
        // from JS annotations.
        // This hides the VM runtime data and the VM-start functions from the untrusted code.
        codeBuffer.emitText("(function() {");
        codeGenTool.genComment("Untrusted code section", CommentVerbosity.MINIMAL);

        try (JSBenchmarkingCode.Timer totalTimer = benchmarkingCode.getTimer("Total").start()) {

            // Hide the internals of the VM class definition.
            codeBuffer.emitText("(function() {");
            codeGenTool.genComment("VM internals section", CommentVerbosity.MINIMAL);

            try (JSBenchmarkingCode.Timer t = benchmarkingCode.getTimer("Initial Definitions").start();
                            CodeSizeCollector sizeCollector = new CodeSizeCollector(ImageBreakdownMetricKeys.INITIAL_DEFINITIONS_SIZE, codeBuffer::codeSize);
                            Labeler.Injection injection = labeler.injectMetricLabel(codeBuffer, ImageBreakdownMetricKeys.INITIAL_DEFINITIONS_SIZE)) {
                emitBootstrapDefinitions();
            }

            NameSpaceHideLowerer.lowerNameSpaceHidingFunction(codeGenTool);

            try (JSBenchmarkingCode.Timer t = benchmarkingCode.getTimer("Initial Definitions").start();
                            CodeSizeCollector sizeCollector = new CodeSizeCollector(ImageBreakdownMetricKeys.INITIAL_DEFINITIONS_SIZE, codeBuffer::codeSize);
                            Labeler.Injection injection = labeler.injectMetricLabel(codeBuffer, ImageBreakdownMetricKeys.INITIAL_DEFINITIONS_SIZE)) {
                emitRuntimeDefinitions();
            }

            emitCompiledCode();

            try (JSBenchmarkingCode.Timer t = benchmarkingCode.getTimer("Runtime").start()) {
                emitEntryPointCall();
            }

            benchmarkingCode.lowerMeasuringResult();

            codeBuffer.emitNewLine();
            codeGenTool.genReturn(NamingConvention.VM_STATE);

            NameSpaceHideLowerer.lowerNameSpaceHidingEnd(codeGenTool);
            codeBuffer.emitNewLine();

            /*
             * Lowers the configuration class, which users can use to configure the VM settings
             */
            codeBuffer.emitText(codeGenTool.vmClassName() + "." + Runtime.CONFIG_CLASS_NAME + " = " + Runtime.CONFIG_CLASS_NAME + ";");
            codeBuffer.emitNewLine();

            configuration.getEntryFunctionLowerer().lowerEntryFunction(codeGenTool);

            codeGenTool.genComment("End VM internals section", CommentVerbosity.MINIMAL);
            codeBuffer.emitText("})();");
            codeBuffer.emitNewLine();
            codeBuffer.emitNewLine();

        }

        // End definition of the untrusted code section.
        codeGenTool.genComment("End untrusted code section", CommentVerbosity.MINIMAL);
        codeBuffer.emitText("})();");
        codeBuffer.emitNewLine();
        codeBuffer.emitNewLine();

        emitJSCodeFiles();

        // End definition of the code section that contains JavaScript code included via JS.Code
        // annotation.
        codeGenTool.genComment("End JS.Code annotation section", CommentVerbosity.MINIMAL);
        codeBuffer.emitText(("})();"));
        codeBuffer.emitNewLine();
        codeBuffer.emitNewLine();

        codeBuffer.emitText("(function() {");
        codeBuffer.emitNewLine();
        if (WebImageOptions.AutoRunVM.getValue(options)) {
            codeGenTool.genComment("Auto run VM.", CommentVerbosity.MINIMAL);

            LowerableResources.LOAD_CMD_ARGS.lower(codeGenTool);
            codeBuffer.emitNewLine();

            codeBuffer.emitText("const config = new " + codeGenTool.vmClassName() + "." + Runtime.CONFIG_CLASS_NAME + "();");
            codeBuffer.emitNewLine();

            String autoFetchLibs = WebImageOptions.AutoRunLibraries.getValue(options);
            if (!autoFetchLibs.equals("")) {
                try {
                    String[] pairs = autoFetchLibs.split(",");
                    for (String pair : pairs) {
                        String[] parts = pair.split(":");
                        codeBuffer.emitText("config.libraries[\"" + parts[0] + "\"] = \"" + parts[1] + "\"");
                        codeBuffer.emitNewLine();
                    }
                } catch (IndexOutOfBoundsException e) {
                    throw JVMCIError.shouldNotReachHere("Incorrectly specified list of libraries: " + autoFetchLibs);
                }
            }

            codeBuffer.emitText(codeGenTool.vmClassName() + ".");
            WebImageEntryFunctionLowerer.FUNCTION.emitCall(codeGenTool, Emitter.of("load_cmd_args()"), Emitter.of("config"));
            codeBuffer.emitText(".catch(console.error)");

            codeBuffer.emitInsEnd();
        } else {
            codeGenTool.genComment("Store the VM class into the global scope.", CommentVerbosity.MINIMAL);
            codeBuffer.emitText("globalThis[\"" + codeGenTool.vmClassName() + "\"] = " + codeGenTool.vmClassName() + ";");
            codeBuffer.emitNewLine();
        }
        codeBuffer.emitText("})();");
    }

    /**
     * Emit code before the first line of JavaScript code.
     */
    protected abstract void emitPreamble();

    /**
     * Emit code for starting the runtime.
     */
    protected abstract void emitBootstrapDefinitions();

    /**
     * Emit code used from the compiled code.
     */
    protected abstract void emitRuntimeDefinitions();

    /**
     * Emit all JavaScript code that resulted from the compilation.
     */
    protected abstract void emitCompiledCode();

    /**
     * Emit code to run the compiled program's main entry point.
     * <p>
     * This code has access to the program arguments ({@link NameSpaceHideLowerer#VM_ARGS}) and the
     * data object ({@link NameSpaceHideLowerer#DATA_ARG}).
     *
     * @see com.oracle.svm.hosted.webimage.codegen.compatibility.JSEntryPointCode
     */
    protected abstract void emitEntryPointCall();

    /**
     * Emit code from {@link org.graalvm.webimage.api.JS.Code} and
     * {@link org.graalvm.webimage.api.JS.Code.Include} annotations.
     */
    protected abstract void emitJSCodeFiles();

    protected Collection<Path> writeFiles() {
        List<Path> writtenFiles = new ArrayList<>();
        BuildArtifacts artifacts = BuildArtifacts.singleton();

        Path outFile = NativeImageGenerator.generatedFiles(options).resolve(this.filename);
        codeBuffer.emitImage(outFile);
        writtenFiles.add(outFile);
        artifacts.add(BuildArtifacts.ArtifactType.EXECUTABLE, outFile);

        if (WebImageOptions.GenerateSourceMap.getValue(options)) {
            Path mapFile = NativeImageGenerator.generatedFiles(options).resolve(this.filename + ".map");
            codeBuffer.emitSourceMap(mapFile);
            writtenFiles.add(mapFile);
            artifacts.add(BuildArtifacts.ArtifactType.DEBUG_INFO, mapFile);
        }

        return writtenFiles;
    }

}
