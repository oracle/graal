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

package com.oracle.svm.hosted.webimage.wasm.codegen;

import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.ENTIRE_IMAGE_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.WASM_IMAGE_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys.EMITTED_METHODS;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.InvalidMethodPointerHandler;
import com.oracle.svm.core.image.ImageHeapLayoutInfo;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.hosted.HeapBreakdownProvider;
import com.oracle.svm.hosted.NativeImageGenerator;
import com.oracle.svm.hosted.image.AbstractImage;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.webimage.WebImageCodeCache;
import com.oracle.svm.hosted.webimage.WebImageHostedConfiguration;
import com.oracle.svm.hosted.webimage.codegen.LowerableResource;
import com.oracle.svm.hosted.webimage.codegen.LowerableResources;
import com.oracle.svm.hosted.webimage.codegen.NameSpaceHideLowerer;
import com.oracle.svm.hosted.webimage.codegen.Runtime;
import com.oracle.svm.hosted.webimage.codegen.WebImageCodeGen;
import com.oracle.svm.hosted.webimage.codegen.WebImageCompilationResult;
import com.oracle.svm.hosted.webimage.codegen.WebImageProviders;
import com.oracle.svm.hosted.webimage.logging.LoggerContext;
import com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys;
import com.oracle.svm.hosted.webimage.name.WebImageNamingConvention;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.wasm.WasmJSCounterparts;
import com.oracle.svm.hosted.webimage.wasm.WebImageWasmHeapBreakdownProvider;
import com.oracle.svm.hosted.webimage.wasm.annotation.WasmStartFunction;
import com.oracle.svm.hosted.webimage.wasm.ast.ActiveFunctionElements;
import com.oracle.svm.hosted.webimage.wasm.ast.Export;
import com.oracle.svm.hosted.webimage.wasm.ast.Function;
import com.oracle.svm.hosted.webimage.wasm.ast.StartFunction;
import com.oracle.svm.hosted.webimage.wasm.ast.Table;
import com.oracle.svm.hosted.webimage.wasm.ast.WasmModule;
import com.oracle.svm.hosted.webimage.wasm.ast.id.ResolverContext;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.ast.visitors.WasmElementCreator;
import com.oracle.svm.hosted.webimage.wasm.ast.visitors.WasmIdResolver;
import com.oracle.svm.hosted.webimage.wasm.ast.visitors.WasmPrinter;
import com.oracle.svm.hosted.webimage.wasm.ast.visitors.WasmValidator;
import com.oracle.svm.hosted.webimage.wasm.debug.WasmDebug;
import com.oracle.svm.hosted.webimage.wasmgc.WasmGCFunctionTemplateFeature;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmRefType;
import com.oracle.svm.webimage.NamingConvention;
import com.oracle.svm.webimage.functionintrinsics.JSFunctionDefinition;
import com.oracle.svm.webimage.functionintrinsics.JSGenericFunctionDefinition;
import com.oracle.svm.webimage.functionintrinsics.JSSystemFunction;
import com.oracle.svm.webimage.hightiercodegen.Emitter;
import com.oracle.svm.webimage.hightiercodegen.IEmitter;
import com.oracle.svm.webimage.wasmgc.annotation.WasmExport;

import jdk.graal.compiler.debug.DebugContext;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class WebImageWasmCodeGen extends WebImageCodeGen {

    protected final String wasmFilename;
    protected final Path wasmFile;
    protected final String watFilename;
    protected final Path watFile;

    protected WasmModule module;

    /**
     * Function references that are used to populate global function table.
     * <p>
     * Contains a function reference for every method pointer relocation. Functions in the table can
     * be called using `call_indirect`.
     */
    protected final ActiveFunctionElements functionTableElements = new ActiveFunctionElements(0, WasmRefType.FUNCREF);

    private ImageHeapLayoutInfo layout = null;

    /**
     * Name of the function calling the WASM entry point.
     * <p>
     * Accepts the program arguments as an array of JS strings:
     *
     * <pre>
     * {@code void wasmRun(String[])}
     * </pre>
     */
    public static final JSFunctionDefinition ENTRY_POINT_FUN = new JSGenericFunctionDefinition("wasmRun", 1, false, null, false);

    protected WebImageWasmCodeGen(WebImageCodeCache codeCache, List<HostedMethod> hostedEntryPoints, HostedMethod mainEntryPoint,
                    WebImageProviders providers, DebugContext debug, WebImageHostedConfiguration config) {
        super(codeCache, hostedEntryPoints, mainEntryPoint, providers, debug, config);

        this.wasmFilename = this.filename + ".wasm";
        this.watFilename = this.filename + ".wat";
        this.watFile = NativeImageGenerator.generatedFiles(options).resolve(this.watFilename);
        this.wasmFile = NativeImageGenerator.generatedFiles(options).resolve(this.wasmFilename);
    }

    @Override
    protected WebImageWasmProviders getProviders() {
        return (WebImageWasmProviders) super.getProviders();
    }

    public ImageHeapLayoutInfo getLayout() {
        assert layout != null : "Image heap layout not set yet";
        return layout;
    }

    protected void setLayout(ImageHeapLayoutInfo layout) {
        assert this.layout == null : "Image heap layout already set";
        this.layout = Objects.requireNonNull(layout);
    }

    /**
     * Image heap size in the executable itself (or an approximation if image heap size cannot be
     * directly determined).
     * <p>
     * This number is used for statistics about the final executable (e.g. in
     * {@link AbstractImage#getImageHeapSize()}).
     */
    public long getImageHeapSize() {
        return getLayout().getSize();
    }

    /**
     * Theoretical image heap size, not necessarily the same as {@link #getImageHeapSize()}.
     * <p>
     * In particular, this does not include any optimizations made after layouting (e.g. omitting
     * empty space in {@link com.oracle.svm.hosted.webimage.wasm.ast.ActiveData}).
     * <p>
     * This number is mainly used to get the total heap size for image heap breakdown statistics.
     */
    public long getFullImageHeapSize() {
        return getLayout().getSize();
    }

    @Override
    protected void emitCode() {
        genWasmModule();
        assert module != null;

        fillNullMethodPointer();
        writeImageHeap();

        createMissingElements();
        getProviders().idFactory().freeze();

        patchIds();

        module.constructActiveDataSegments();
        ((WebImageWasmHeapBreakdownProvider) HeapBreakdownProvider.singleton()).setActualTotalHeapSize((int) getFullImageHeapSize());

        if (WebImageOptions.DebugOptions.VerificationPhases.getValue(options)) {
            validateModule();
        }

        emitJSCode();

        try (Writer writer = Files.newBufferedWriter(watFile)) {
            new WasmPrinter(writer).visitModule(module);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        assembleWasmFile(watFile, wasmFile);
    }

    @Override
    protected void postProcess() {
        long wasmSize = wasmFile.toFile().length();
        LoggerContext.counter(WASM_IMAGE_SIZE).add(wasmSize);
        LoggerContext.counter(ENTIRE_IMAGE_SIZE).add(wasmSize);
    }

    /**
     * A method pointer is represented as an index into the global function table and a value of 0
     * is interpreted as null. To make sure all method pointers are interpreted as non-null, the
     * first slot is filled with marker method.
     */
    protected void fillNullMethodPointer() {
        ResolvedJavaMethod nullMethod = WasmDebug.NULL_METHOD_POINTER.findMethod(getProviders().getMetaAccess());
        assert getProviders().idFactory().hasMethod(nullMethod) : "nullMethod was never generated";
        int nullMethodIndex = functionTableElements.getOrAddElement(getProviders().idFactory().forMethod(nullMethod));
        assert nullMethodIndex == 0 : nullMethodIndex + " functions were added to global function table before the null method";
    }

    /**
     * Gets or allocates a function table entry for the given method pointer.
     *
     * @see #getFunctionTableIndex(WasmId.Func)
     */
    protected int getFunctionTableIndex(MethodPointer methodPointer) {
        ResolvedJavaMethod method = methodPointer.getMethod();
        HostedMethod target = (method instanceof HostedMethod hostedMethod) ? hostedMethod : hUniverse.lookup(method);
        if (!target.isCompiled()) {
            target = hMetaAccess.lookupJavaMethod(InvalidMethodPointerHandler.METHOD_POINTER_NOT_COMPILED_HANDLER_METHOD);
        }

        WasmId.Func methodId = getProviders().idFactory().forMethod(target);
        return getFunctionTableIndex(methodId);
    }

    /**
     * Gets or allocates a function table entry for the given function id.
     *
     * @return Non-zero index into the function table that corresponds to the given method
     */
    protected int getFunctionTableIndex(WasmId.Func function) {
        int idx = functionTableElements.getOrAddElement(function);
        assert idx > 0 : idx;
        return idx;
    }

    protected abstract void writeImageHeap();

    @Override
    protected Collection<Path> writeFiles() {
        List<Path> outFiles = new ArrayList<>(super.writeFiles());
        // Only add final wasm file. The wat file shouldn't count towards the total image size
        outFiles.add(wasmFile);
        BuildArtifacts.singleton().add(BuildArtifacts.ArtifactType.EXECUTABLE, wasmFile);
        BuildArtifacts.singleton().add(BuildArtifacts.ArtifactType.BUILD_INFO, watFile);
        return outFiles;
    }

    /**
     * Assembles the WASM text format to the binary format using {@code wat2wasm}.
     *
     * @param watPath The path to the already existing WASM text file.
     * @param wasmPath The path where the resulting WASM binary file should be placed.
     */
    protected void assembleWasmFile(Path watPath, Path wasmPath) {
        try {
            WasmAssembler.singleton().assemble(watPath, wasmPath, options, getProviders().stdout()::println);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected void genWasmModule() {
        module = new WasmModule();

        for (WebImageCompilationResult result : compilations.values()) {
            WebImageWasmCompilationResult res = (WebImageWasmCompilationResult) result;

            res.getAllFunctions().forEach(module::addFunction);
            LoggerContext.counter(EMITTED_METHODS).increment();
        }

        LoggerContext.counter(UniverseMetricKeys.EMITTED_TYPES).add(compilations.keySet().stream().map(ResolvedJavaMethod::getDeclaringClass).distinct().count());

        // The main entry point method will always be exported as 'main'.
        module.addFunctionExport(getProviders().idFactory.forMethod(mainEntryPoint), "main", "Main Entry Point");

        for (HostedMethod entryPoint : hostedEntryPoints) {
            if (entryPoint.isAnnotationPresent(WasmExport.class)) {
                WasmExport annotation = entryPoint.getAnnotation(WasmExport.class);
                module.addFunctionExport(getProviders().idFactory().forMethod(entryPoint), annotation.value(), annotation.comment().isEmpty() ? null : annotation.comment());
            }

            if (entryPoint.isAnnotationPresent(WasmStartFunction.class)) {
                module.setStartFunction(new StartFunction(getProviders().idFactory().forMethod(entryPoint), null));
            }
        }

        getProviders().knownIds.getExports().forEach(module::addExport);
        module.addExport(new Export(Export.Type.TAG, getProviders().knownIds.getJavaThrowableTag(), "tag.throwable", "Tag for Java exceptions"));
    }

    /**
     * Inserts the necessary still missing elements (variables, imports, etc.) to the module.
     */
    protected void createMissingElements() {
        // Generate late templates.
        List<WasmFunctionTemplate<?>> templates = getProviders().knownIds().getLateFunctionTemplates();
        List<Function> functionTemplates = WasmGCFunctionTemplateFeature.createFunctionTemplates(templates, (t, f) -> t.createFunctionForIdLate(getProviders(), f));
        functionTemplates.forEach(module::addFunction);

        module.addTable(new Table(getProviders().knownIds().functionTable, functionTableElements, "Function table"));
        getElementsCreator().visitModule(module);
    }

    protected WasmElementCreator getElementsCreator() {
        return new WasmElementCreator();
    }

    /**
     * Walks the given module and resolves all instances of {@link WasmId}.
     */
    protected void patchIds() {
        NamingConvention namingConvention = WebImageNamingConvention.getInstance();
        new WasmIdResolver(new ResolverContext(namingConvention), getProviders().idFactory()).visitModule(module);
    }

    /**
     * Validates the given module against the WASM specification.
     */
    protected void validateModule() {
        new WasmValidator().visitModule(module);
    }

    @Override
    protected void emitPreamble() {
        // Nothing to do.
    }

    @Override
    protected void emitBootstrapDefinitions() {
        LowerableResources.lower(codeGenTool, LowerableResources.bootstrap);
        LowerableResources.lower(codeGenTool, getWasmBootstrapResources().toArray(LowerableResource[]::new));

        // WASM code is always little-endian.
        Runtime.SET_ENDIANNESS_FUN.emitCall(codeGenTool, Emitter.of(true));
        codeGenTool.getCodeBuffer().emitInsEnd();

        Map<String, IEmitter> interopImports = new TreeMap<>();
        for (JSSystemFunction f : getProviders().getJSCounterparts().getFunctions()) {
            /*
             * Dynamically generated imports simply forward all arguments to the actual target
             * function using the spread operator. We cannot simply pass a function reference
             * because the function would be called without any value for 'this'.
             */
            interopImports.put(f.getFunctionName(), Emitter.of("(...args) => " + f.getFunctionName() + "(...args)"));
        }

        codeGenTool.genResolvedVarAssignmentPrefix("wasmImports." + WasmJSCounterparts.JSFUNCTION_MODULE_NAME);
        codeGenTool.genObject(interopImports);
        codeGenTool.getCodeBuffer().emitInsEnd();
    }

    protected List<LowerableResource> getWasmBootstrapResources() {
        return List.of(WasmLMLowerableResources.STACK_TRACE, WasmLMLowerableResources.BOOTSTRAP, WasmLMLowerableResources.IMPORTS, WasmLMLowerableResources.INTEROP,
                        WasmLMLowerableResources.RUNNER, LowerableResources.CWD);
    }

    @Override
    protected void emitRuntimeDefinitions() {
        // Nothing to do, no runtime definitions yet.
    }

    @Override
    protected void emitCompiledCode() {
        // Nothing to do, there is no code compiled to JS.
    }

    @Override
    protected void emitEntryPointCall() {
        ENTRY_POINT_FUN.emitCall(codeGenTool, Emitter.of(NameSpaceHideLowerer.VM_ARGS));
        codeBuffer.emitInsEnd();
    }

    @Override
    protected void emitJSCodeFiles() {
        // TODO GR-42437 Support for JS.Code
    }
}
