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

package com.oracle.svm.hosted.webimage.wasmgc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.webimage.wasm.ast.Function;
import com.oracle.svm.hosted.webimage.wasm.ast.Instructions;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmBlockContext;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmCodeGenTool;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmFunctionTemplate;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmBackend;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmCompilationResult;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmProviders;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmVariableAllocation;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.webimage.platform.WebImageWasmGCPlatform;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnreachableControlSinkNode;

/**
 * Supports correct generation of {@link WasmFunctionTemplate function templates}.
 * <p>
 * The templated functions are generated at the end of all {@link CompileQueue} compilations as a
 * separate compilation task with a custom compilation function ({@link #createFunctionTemplates})
 * in {@link WebImageWasmGCCompileQueue}. {@link FunctionTemplateHolder#placeHolderMethod()} is the
 * method that is used as the compilation unit to schedule the compilation.
 * <p>
 * A compilation task is scheduled so that the {@link WebImageWasmCompilationResult} and, more
 * importantly, the relocations stored inside are available as usual from the CompileQueue. This
 * allows the generated functions and relocated constants to be automatically added to the Wasm
 * module and picked up by the image heap builder respectively without specialized changes.
 * <p>
 * Function ids for the templates are requested during method compilation and only after all methods
 * finished compiling is a complete list of requested functions available. This is why this task has
 * to be scheduled manually at the end.
 */
@AutomaticallyRegisteredFeature
@Platforms(WebImageWasmGCPlatform.class)
public class WasmGCFunctionTemplateFeature implements InternalFeature {
    @Override
    public void afterAnalysis(Feature.AfterAnalysisAccess a) {
        FeatureImpl.AfterAnalysisAccessImpl access = (FeatureImpl.AfterAnalysisAccessImpl) a;
        /*
         * Store the AnalysisMethod for quick access. This also ensures a HostedMethod is created
         * from it.
         */
        FunctionTemplateHolder.singleton().functionTemplatesPlaceholder = access.getMetaAccess().lookupJavaMethod(ReflectionUtil.lookupMethod(FunctionTemplateHolder.class, "placeHolderMethod"));
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        FeatureImpl.BeforeCompilationAccessImpl access = (FeatureImpl.BeforeCompilationAccessImpl) a;
        HostedMethod placeholder = access.getUniverse().lookup(getFunctionTemplatesPlaceholder());
        /*
         * Make sure the method is not scheduled for compilation accidentally. We schedule it
         * manually at the very end.
         */
        placeholder.compilationInfo.setCustomCompileFunction((debug, method, identifier, reason, config) -> {
            throw GraalError.shouldNotReachHere(
                            "Function templates must not be created yet. This method has been scheduled for compilation too early, it must only be scheduled once all compilation finish:" + method);
        });
    }

    public static AnalysisMethod getFunctionTemplatesPlaceholder() {
        return FunctionTemplateHolder.singleton().functionTemplatesPlaceholder;
    }

    /**
     * Custom compilation function for the function template placeholder method (see
     * {@link FunctionTemplateHolder#placeHolderMethod()}). A method reference for this method can
     * be used as a {@link com.oracle.svm.hosted.code.CompileQueue.CompileFunction} instance.
     * <p>
     * Sets up a {@link WasmCodeGenTool} and passes it to
     * {@link WasmFunctionTemplate#createFunctionForId(WasmCodeGenTool, WasmId.Func)} to build all
     * requested template functions. Functions are generated using
     * {@link #createFunctionTemplates(List, BiFunction)}.
     * <p>
     * The resulting {@link Function} instances are stored using
     * {@link WebImageWasmCompilationResult#addExtraFunction(Function)} and are thus available later
     * when the Wasm module is built.
     *
     * @see WebImageWasmGCCompileQueue
     */
    public static CompilationResult createFunctionTemplates(DebugContext debug, @SuppressWarnings("unused") HostedMethod method, CompilationIdentifier identifier,
                    @SuppressWarnings("unused") CompileQueue.CompileReason reason, RuntimeConfiguration config) {
        // Create stub graph. Its content are not accessed.
        StructuredGraph graph = new StructuredGraph.Builder(debug.getOptions(), debug).build();
        graph.addAfterFixed(graph.start(), graph.add(new UnreachableControlSinkNode()));

        WebImageWasmBackend backend = (WebImageWasmBackend) config.getBackendForNormalMethod();

        WebImageWasmCompilationResult result = backend.newCompilationResult(identifier, "WasmGC Function Templates");
        result.setGraph(graph);

        WebImageWasmVariableAllocation variableAllocation = new WebImageWasmVariableAllocation();
        WasmBlockContext topLevel = WasmBlockContext.getTopLevel(Instructions.asInstructions());

        WasmCodeGenTool codeGenTool = backend.createCodeGenTool(variableAllocation, result, topLevel, graph);

        List<WasmFunctionTemplate<?>> templates = backend.getWasmProviders().knownIds().getFunctionTemplates();
        List<Function> functionTemplates = createFunctionTemplates(templates, (t, f) -> t.createFunctionForId(codeGenTool, f));
        functionTemplates.forEach(result::addExtraFunction);

        return result;
    }

    /**
     * Generates all requested template functions for all given template instances.
     * <p>
     * Functions are generated in a loop until no template has any functions left to generate. This
     * allows templates to request ids from other templates during function generation.
     * <p>
     * After this, every template in {@code templates} is frozen.
     *
     * @param templates All templates that should be instantiated.
     * @param generator Accepts the template and one of its function ids to generate the function.
     *            This should call
     *            {@link WasmFunctionTemplate#createFunctionForId(WasmCodeGenTool, WasmId.Func)} or
     *            {@link WasmFunctionTemplate#createFunctionForIdLate(WebImageWasmProviders, WasmId.Func)}.
     */
    public static List<Function> createFunctionTemplates(List<WasmFunctionTemplate<?>> templates, BiFunction<WasmFunctionTemplate<?>, WasmId.Func, Function> generator) {
        List<Function> functions = new ArrayList<>();
        /*
         * When generating templates, the functions may request other templated function ids.
         * Because of that we repeatedly generate not yet generated functions until no template has
         * anything left to generate.
         */
        boolean hasWork;
        do {
            for (WasmFunctionTemplate<?> t : templates) {
                for (WasmId.Func f : t.getNotYetGenerated()) {
                    functions.add(generator.apply(t, f));
                }
            }

            hasWork = templates.stream().anyMatch(t -> !t.allFunctionsGenerated());
        } while (hasWork);

        templates.forEach(WasmFunctionTemplate::freeze);

        return functions;
    }
}

@AutomaticallyRegisteredImageSingleton
@Platforms(WebImageWasmGCPlatform.class)
class FunctionTemplateHolder {
    /**
     * Reference to the placeholder method used for compiling the {@link WasmFunctionTemplate
     * function templates}.
     * <p>
     * This method is used as the "compilation unit" for the custom compilation that generates all
     * requested templated functions.
     *
     * @see WasmGCFunctionTemplateFeature
     */
    protected AnalysisMethod functionTemplatesPlaceholder;

    @Fold
    public static FunctionTemplateHolder singleton() {
        return ImageSingletons.lookup(FunctionTemplateHolder.class);
    }

    /**
     * Placeholder method for Wasm function template "compilation".
     * <p>
     * The method is never actually compiled and is just a placeholder, the custom compilation
     * function ({@link WasmGCFunctionTemplateFeature#createFunctionTemplates}) does something
     * entirely different.
     *
     * @see WasmGCFunctionTemplateFeature
     */
    public static void placeHolderMethod() {
        throw VMError.shouldNotReachHere("This method should never be executed. It's a placeholder with a custom compilation function.");
    }
}
