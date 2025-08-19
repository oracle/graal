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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSObject;

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.graal.pointsto.util.Timer.StopTimer;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.core.InvalidMethodPointerHandler;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.JSCodeBuffer;
import com.oracle.svm.hosted.webimage.Labeler;
import com.oracle.svm.hosted.webimage.WebImageCodeCache;
import com.oracle.svm.hosted.webimage.WebImageHostedConfiguration;
import com.oracle.svm.hosted.webimage.codegen.compatibility.JSBenchmarkingCode;
import com.oracle.svm.hosted.webimage.codegen.compatibility.JSEntryPointCode;
import com.oracle.svm.hosted.webimage.codegen.heap.ConstantMap;
import com.oracle.svm.hosted.webimage.codegen.oop.ClassLowerer;
import com.oracle.svm.hosted.webimage.codegen.oop.ClassWithMirrorLowerer;
import com.oracle.svm.hosted.webimage.codegen.oop.StaticFieldLowerer;
import com.oracle.svm.hosted.webimage.codegen.type.TypeVtableLowerer;
import com.oracle.svm.hosted.webimage.logging.LoggerContext;
import com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys;
import com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.snippets.JSSnippetWithEmitterSupport;
import com.oracle.svm.hosted.webimage.snippets.JSSnippets;
import com.oracle.svm.hosted.webimage.util.AnnotationUtil;
import com.oracle.svm.hosted.webimage.util.TypeControlGraphPrinter;
import com.oracle.svm.hosted.webimage.util.metrics.CodeSizeCollector;
import com.oracle.svm.hosted.webimage.util.metrics.ImageMetricsCollector;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.webimage.hightiercodegen.CodeBuffer;
import com.oracle.svm.webimage.hightiercodegen.Emitter;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class WebImageJSCodeGen extends WebImageCodeGen {

    public static final String GenUniverseTimer = "(gen universe)";
    public static final String ClosureTimer = "closure";
    public static final String WriteTimer = "(write)";
    public static final String ClosureWhitespaceTimer = "(closure compiler whitespace)";

    private final WebImageTypeControl typeControl;

    protected final Map<HostedMethod, StructuredGraph> methodGraphs;
    private final ImageClassLoader imageClassLoader;

    public WebImageJSCodeGen(WebImageCodeCache codeCache, List<HostedMethod> hostedEntryPoints, HostedMethod mainEntryPoint,
                    WebImageProviders providers, DebugContext debug, WebImageHostedConfiguration config, ImageClassLoader imageClassLoader) {
        super(codeCache, hostedEntryPoints, mainEntryPoint, providers, debug, config);

        this.typeControl = ((WebImageJSProviders) providers).typeControl();
        this.methodGraphs = compilations.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getGraph()));
        this.imageClassLoader = imageClassLoader;
    }

    private static void lowerJavaScriptCode(CodeBuffer codeBuffer, String titleComment, InputStream is) {
        codeBuffer.emitText(titleComment);
        codeBuffer.emitNewLine();

        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        reader.lines().forEach(line -> {
            codeBuffer.emitText(line);
            codeBuffer.emitNewLine();
        });
        codeBuffer.emitNewLine();
    }

    private static int getMethodsSize() {
        String scopeName;
        if (WebImageProviders.isLabelInjectionEnabled()) {
            scopeName = ImageMetricsCollector.CLOSURE_SCOPE_NAME;
        } else if (!WebImageOptions.ClosureCompiler.getValue()) {
            scopeName = ImageMetricsCollector.PRE_CLOSURE_SCOPE_NAME;
        } else {
            // Note - We cannot determine methods size when we use Closure compiler without label
            // injection.
            return 0;
        }

        LoggerContext currentContext = LoggerContext.currentContext();
        String qualifiedScopeName = LoggerContext.getQualifiedScopeName(currentContext.currentScope().getName(), scopeName);
        Map<String, Number> savedCounters = currentContext.getSavedCounters(qualifiedScopeName, ImageMetricsCollector.SAVED_SIZE_BREAKDOWN_KEYS);

        return savedCounters.get(ImageBreakdownMetricKeys.TOTAL_METHOD_SIZE.getName()).intValue();
    }

    @Override
    protected WebImageJSProviders getProviders() {
        return (WebImageJSProviders) super.getProviders();
    }

    @Override
    protected void emitCode() {
        /* The JS backend doesn't really do any heap layouting, */
        afterHeapLayout();
        emitJSCode();
    }

    @Override
    @SuppressWarnings("try")
    protected void postProcess() {
        if (WebImageOptions.ClosureCompiler.getValue()) {
            getProviders().stdout().println("Compiling with final closure compiler pass.....");
            try (StopTimer t = TimerCollection.createTimerAndStart(ClosureTimer);
                            ImageMetricsCollector tracker = new ImageMetricsCollector.PostClosure(methodMetricsCollector, labeler, codeBuffer)) {
                // Overwrite with optimized code
                codeBuffer.setCodeBytes(getClosureCompilerSupport().applyClosureCompiler(codeBuffer.getCode()).getBytes());
            }
        }

        // image sizes
        codeBuffer.infoDump(getProviders());
        getProviders().stdout().println("----------------------------------------------------------");
        getProviders().stdout().println("Compiled methods class file method size[raw bytes]:" + JSCodeBuffer.bytesToString(compiledMethodBytes, true));
        getProviders().stdout().println("----------------------------------------------------------");

        LoggerContext.counter(UniverseMetricKeys.EMITTED_TYPES).add(typeControl.emittedTypes().size());

        codeCache.setCodeAreaSize(getMethodsSize());
    }

    protected String getClosureCompilerSupportClass() {
        return "com.oracle.svm.hosted.webimage.closurecompiler.ClosureCompilerSupportImpl";
    }

    private ClosureCompilerSupport getClosureCompilerSupport() {
        return ClosureCompilerSupport.getClosureSupport(getClosureCompilerSupportClass(), imageClassLoader.watchdog, codeGenTool, imageName);
    }

    protected Map<String, RuntimeConstants.ConstantDeclaration> getRuntimeConstants() {
        return RuntimeConstants.getConstantMap();
    }

    @Override
    protected void emitJSCode() {
        /*
         * Process all registered resources. This has to be done before any codegen because the
         * result is used for doing the final lowering of the resource and it affects the type
         * control.
         */
        configuration.getJavaRelatedResources().stream().filter(LowerableResource::isRegistered).forEach(
                        resource -> JSIntrinsifyFile.process(resource.getData(), getProviders()));

        if (WebImageOptions.UsePEA.getValue(options)) {
            getProviders().stdout().println("Compiling with PEA.....");
        }

        super.emitJSCode();
    }

    @Override
    protected void emitPreamble() {
        if (WebImageOptions.GenerateSourceMap.getValue(options)) {
            codeBuffer.emitText("//# sourceMappingURL=" + filename + ".map");
            codeBuffer.emitNewLine();
        }
    }

    /**
     * Bootstrap definitions are used for starting the VM, e.g., configuration.
     * <p>
     * No access to Java code.
     */
    @Override
    protected void emitBootstrapDefinitions() {
        configuration.getBootstrapResources().stream().filter(LowerableResource::isRegistered).forEach(
                        resource -> JSIntrinsifyFile.process(resource.getData(), getProviders()));
        LowerableResources.lower(codeGenTool, LowerableResources.bootstrap);

        /*
         * Set the endianness at build time to be used at runtime.
         */
        boolean isLittleEndianBuildTime = ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN);
        Runtime.SET_ENDIANNESS_FUN.emitCall(codeGenTool, Emitter.of(isLittleEndianBuildTime));
        codeGenTool.getCodeBuffer().emitInsEnd();
    }

    /**
     * Runtime definitions are used for runtime support.
     * <p>
     * They are lowered before Java code and heap objects. May access Java code in function body.
     */
    @Override
    protected void emitRuntimeDefinitions() {
        RuntimeConstants.lowerInitialDefinition(codeGenTool, this.getRuntimeConstants());
        LowerableResources.lower(codeGenTool, LowerableResources.runtime);
    }

    @Override
    @SuppressWarnings("try")
    protected void emitCompiledCode() {
        try (JSBenchmarkingCode.Timer t = benchmarkingCode.getTimer("Hosted Universe").start()) {
            try (StopTimer t1 = TimerCollection.createTimerAndStart(GenUniverseTimer)) {
                lowerHostedUniverse();
            }
        }

        KnownHubMapLowerer.classNameMapping(codeGenTool);

        try (JSBenchmarkingCode.Timer t = benchmarkingCode.getTimer(ConstantMap.WEB_IMAGE_CONST_PROPERTY_INIT_F_NAME.getFunctionName()).start()) {
            ConstantMap.WEB_IMAGE_CONST_PROPERTY_INIT_F_NAME.emitCall(codeGenTool);
            codeGenTool.genResolvedVarDeclPostfix(null);
            Runtime.INITIALIZE_JAVA_STRINGS.emitCall(codeGenTool);
            codeGenTool.genResolvedVarDeclPostfix(null);
        }

    }

    @Override
    protected void emitEntryPointCall() {
        JSEntryPointCode.ENTRY_POINT_FUN.emitCall(codeGenTool, Emitter.of(NameSpaceHideLowerer.VM_ARGS));
        codeBuffer.emitInsEnd();
    }

    @Override
    protected void emitJSCodeFiles() {
        HashSet<String> includedPaths = new HashSet<>();
        codeBuffer.emitNewLine();
        for (HostedType type : getProviders().typeControl().emittedTypes()) {
            var includes = AnnotationUtil.getDeclaredAnnotationsByType(type, JS.Code.Include.class, JS.Code.Include.Group.class, JS.Code.Include.Group::value);
            for (JS.Code.Include include : includes) {
                String path = include.value();
                if (includedPaths.contains(path)) {
                    continue;
                }
                includedPaths.add(path);

                String titleComment = "// Source file: " + include.value().replace("\n", "").replace("\r", "");
                InputStream is = type.getJavaClass().getResourceAsStream(path);
                if (is == null) {
                    throw new RuntimeException("Resource not found: " + path);
                }
                lowerJavaScriptCode(codeBuffer, titleComment, is);
            }
            var code = type.getDeclaredAnnotation(JS.Code.class);
            if (code != null) {
                String titleComment = "// Class file: " + type.getJavaClass().getName();
                lowerJavaScriptCode(codeBuffer, titleComment, new ByteArrayInputStream(code.value().getBytes(StandardCharsets.UTF_8)));
            }
        }
        codeBuffer.emitText(";;");
        codeBuffer.emitNewLine();
        codeBuffer.emitNewLine();
    }

    /**
     * Registers all necessary types in type control.
     *
     * These types can be considered the "root types" from which image generation starts.
     *
     * Furthermore, {@link InvalidMethodPointerHandler} might need to be registered explicitly if
     * {@link Object} references it in the vtable. If {@link Object} links to
     * {@link InvalidMethodPointerHandler} in the vtable, we cannot register it during our type
     * control because this would cause a circular dependency in {@link WebImageTypeControl}.
     */
    private void registerTypes() {
        for (HostedMethod m : hostedEntryPoints) {
            typeControl.requestTypeName(m.getDeclaringClass());
        }
        if (WebImageOptions.UseVtable.getValue(options)) {
            HostedType invalidVTableType = hMetaAccess.lookupJavaType(InvalidMethodPointerHandler.class);
            for (HostedMethod m : hUniverse.getType(JavaKind.Object).getVTable()) {
                if (m.getDeclaringClass().equals(invalidVTableType)) {
                    typeControl.requestTypeName(invalidVTableType);
                }
            }
        }

        /*
         * Any type that can be instantiated, must be explicitly generated. Normally, the type
         * control would already track this through allocation nodes, but Unsafe.allocateInstance
         * can instantiate anything, if its class instance exists.
         */
        for (HostedType type : hUniverse.getTypes()) {
            if (!type.isArray() && type.getWrapped().isUnsafeAllocated()) {
                typeControl.requestTypeName(type);
            }
        }

        HostedType jsObjectType = (HostedType) getProviders().getMetaAccess().lookupJavaType(JSObject.class);
        requestJSObjectSubclasses(jsObjectType);
    }

    private void requestJSObjectSubclasses(HostedType type) {
        // Only explicitly exported classes must be emitted.
        if (type.getJavaClass().equals(JSObject.class) || type.getAnnotation(JS.Export.class) != null) {
            typeControl.requestTypeName(type);
        }
        for (HostedType subtype : type.getSubTypes()) {
            requestJSObjectSubclasses(subtype);
        }
    }

    /**
     * process all reachable methods from here on.
     */
    @SuppressWarnings("try")
    private void lowerHostedUniverse() {
        HashMap<HostedType, HashMap<HostedField, Constant>> staticFields = new HashMap<>();

        registerTypes();

        /*
         * It is inconvenient to inject labels only for class header and endings. Therefore, the
         * code size collection for pre-closure and post-closure diverges for type declarations.
         *
         * The code in {@code PostClosure#collectMetrics} will deduct method size from type
         * declaration size.
         */
        try (Labeler.Injection injection = labeler.injectMetricLabel(codeBuffer, ImageBreakdownMetricKeys.TYPE_DECLARATIONS_SIZE)) {

            while (typeControl.hasNext()) {
                HostedType t = typeControl.next();
                JVMCIError.guarantee(!t.isArray(), "Must only query non array types from the type control %s", t.toString());

                // Gathers constant values from the static fields of the current type This also
                // registers the types of static fields as escaping types.
                HashMap<HostedField, Constant> fieldMap = StaticFieldLowerer.registerLowering(t, getProviders());
                // register constants for resolve
                staticFields.put(t, fieldMap);
                lowerType(t);
            }
        }

        try (CodeSizeCollector collector = new CodeSizeCollector(ImageBreakdownMetricKeys.TYPE_DECLARATIONS_SIZE, codeBuffer::codeSize)) {
            if (WebImageOptions.UseVtable.getValue(options)) {
                for (HostedType type : typeControl.emittedTypes()) {
                    new TypeVtableLowerer(type).lowerInitialDefinition(codeGenTool);
                }
            }
        }

        lowerExtraDefinitions();

        if (PRINT_OMITTED_TYPES) {
            try (DebugContext.Scope s = debug.scope("Emitted-Types")) {
                debug.log("");
                Collection<HostedType> emittedTypes = typeControl.queryEmittedTypes();
                Collection<HostedType> omittedTypes = typeControl.queryOmittedTypes();

                debug.log("* - Emitted Types size %s--------", emittedTypes.size());
                for (HostedType t : emittedTypes) {
                    debug.log("\t* - %s", t.toJavaName());
                }
                debug.log("* - Omitted Types size %s--------", omittedTypes.size());
                for (HostedType t : omittedTypes) {
                    debug.log("\t* - %s", t.toJavaName());
                }
                debug.log(System.lineSeparator());
            }
        }

        JSEntryPointCode.lower(codeGenTool, mainEntryPoint);
        // patch constants
        typeControl.postProcess(codeGenTool);

        // lower constants
        try (CodeSizeCollector collector = new CodeSizeCollector(ImageBreakdownMetricKeys.STATIC_FIELDS_SIZE, codeBuffer::codeSize);
                        Labeler.Injection injection = labeler.injectMetricLabel(codeBuffer, ImageBreakdownMetricKeys.STATIC_FIELDS_SIZE)) {
            for (HostedType t : typeControl.queryEmittedTypes()) {
                if (!t.isArray()) {
                    // static variables of this type
                    new StaticFieldLowerer(t, staticFields.get(t)).lower(codeGenTool);
                }
            }
        }

        // Lower late runtime modifications
        (new RuntimeModificationLowerer()).lower(codeGenTool);

        if (WebImageOptions.DebugOptions.DumpTypeControlGraph.getValue(options)) {
            TypeControlGraphPrinter.print(typeControl, methodMetricsCollector, SubstrateOptions.reportsPath(), ReportUtils.extractImageName(imageName));
        }
    }

    @SuppressWarnings("try")
    protected void lowerExtraDefinitions() {
        try (JSBenchmarkingCode.Timer t = benchmarkingCode.getTimer("Extra Definitions").start();
                        CodeSizeCollector sizeCollector = new CodeSizeCollector(ImageBreakdownMetricKeys.EXTRA_DEFINITIONS_SIZE, codeBuffer::codeSize);
                        Labeler.Injection injection = labeler.injectMetricLabel(codeBuffer, ImageBreakdownMetricKeys.EXTRA_DEFINITIONS_SIZE)) {
            ResolvedJavaMethod stringCharConstructor = getProviders().getMetaAccess().lookupJavaMethod(ReflectionUtil.lookupConstructor(String.class, char[].class));
            JSSnippetWithEmitterSupport stringCharConstructorSnippet = JSSnippets.instantiateStringCharConstructor(Emitter.of(stringCharConstructor));
            codeGenTool.lower(stringCharConstructorSnippet);
            LowerableResources.lower(codeGenTool, LowerableResources.extra);
            LowerableResources.lower(codeGenTool, LowerableResources.thirdParty.toArray(new LowerableResource[0]));
            Array.lowerArrrayVTable(codeGenTool);
        }
    }

    /**
     * Lowers a type and its methods down to a complete JavaScript class definition.
     */
    @SuppressWarnings("try")
    private void lowerType(HostedType type) {
        ResolvedJavaType jsObjectType = getProviders().getMetaAccess().lookupJavaType(JSObject.class);
        // new bytecode semantic
        ClassLowerer classLowerer;
        if (jsObjectType.isAssignableFrom(type)) {
            classLowerer = new ClassWithMirrorLowerer(options, debug, codeGenTool, methodGraphs, labeler, methodMetricsCollector, (b) -> compiledMethodBytes += b, type);
        } else {
            classLowerer = new ClassLowerer(options, debug, codeGenTool, methodGraphs, labeler, methodMetricsCollector, (b) -> compiledMethodBytes += b, type);
        }
        classLowerer.lower(typeControl);
    }
}
