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

package com.oracle.svm.hosted.webimage.wasmgc.codegen;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.meta.SubstrateMethodPointerConstant;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.webimage.WebImageCodeCache;
import com.oracle.svm.hosted.webimage.WebImageHostedConfiguration;
import com.oracle.svm.hosted.webimage.codegen.LowerableResource;
import com.oracle.svm.hosted.webimage.codegen.LowerableResources;
import com.oracle.svm.hosted.webimage.codegen.WebImageProviders;
import com.oracle.svm.hosted.webimage.js.JSBody;
import com.oracle.svm.hosted.webimage.js.JSKeyword;
import com.oracle.svm.hosted.webimage.wasm.WasmJSCounterparts;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.ast.visitors.WasmElementCreator;
import com.oracle.svm.hosted.webimage.wasm.ast.visitors.WasmRelocationVisitor;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmLMLowerableResources;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmCodeGen;
import com.oracle.svm.hosted.webimage.wasmgc.WasmFunctionIdConstant;
import com.oracle.svm.hosted.webimage.wasmgc.WebImageWasmGCCodeCache;
import com.oracle.svm.hosted.webimage.wasmgc.ast.visitors.WasmGCElementCreator;
import com.oracle.svm.hosted.webimage.wasmgc.codegen.WasmGCHeapWriter.ObjectData;
import com.oracle.svm.hosted.webimage.wasmgc.image.WasmGCImageHeapLayoutInfo;
import com.oracle.svm.webimage.hightiercodegen.CodeBuffer;
import com.oracle.svm.webimage.hightiercodegen.Emitter;
import com.oracle.svm.webimage.hightiercodegen.IEmitter;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.Reference;
import jdk.vm.ci.meta.VMConstant;

public class WebImageWasmGCCodeGen extends WebImageWasmCodeGen {

    public WebImageWasmGCCodeGen(WebImageCodeCache codeCache, List<HostedMethod> hostedEntryPoints, HostedMethod mainEntryPoint, WebImageProviders providers, DebugContext debug,
                    WebImageHostedConfiguration config) {
        super(codeCache, hostedEntryPoints, mainEntryPoint, providers, debug, config);
    }

    @Override
    protected WebImageWasmGCProviders getProviders() {
        return (WebImageWasmGCProviders) super.getProviders();
    }

    @Override
    protected void writeImageHeap() {
        WasmGCHeapWriter heapWriter = new WasmGCHeapWriter(codeCache, getProviders());
        WasmGCImageHeapLayoutInfo layout = heapWriter.layout();
        setLayout(layout);
        afterHeapLayout();
        heapWriter.write(layout, module);
        processRelocations(heapWriter);
    }

    @Override
    protected void genWasmModule() {
        super.genWasmModule();

        for (Map.Entry<HostedMethod, String> entry : ((WebImageWasmGCCodeCache) codeCache).getExportedMethodMetadata().entrySet()) {
            HostedMethod m = entry.getKey();
            String exportedName = entry.getValue();
            module.addFunctionExport(getProviders().idFactory().forMethod(m), exportedName, m.format("Metadata for %h.%n(%p)%r"));
        }

        for (Map.Entry<HostedMethod, String> entry : ((WebImageWasmGCCodeCache) codeCache).getExportedSingleAbstractMethods().entrySet()) {
            HostedMethod m = entry.getKey();
            String exportedName = entry.getValue();
            module.addFunctionExport(getProviders().idFactory().forMethod(m), exportedName, "Single abstract method for " + m.getDeclaringClass().getName());
        }
    }

    private void processRelocations(WasmGCHeapWriter heapWriter) {
        /*
         * Resolves all relocations in the AST. Constant object references are replaced with a read
         * of the global variable holding that image heap object. For method pointers, a Wasm table
         * is built containing all functions referenced in method pointer relocations. The
         * relocations are replaced with the index of the function in the table.
         */
        new WasmRelocationVisitor() {
            @Override
            public void visitUnprocessedRelocation(Instruction.Relocation relocation) {
                Reference targetRef = relocation.target;
                if (targetRef instanceof ConstantReference constantReference) {
                    VMConstant constant = constantReference.getConstant();
                    switch (constant) {
                        case ImageHeapConstant imageHeapConstant -> {
                            ObjectData data = heapWriter.getConstantInfo(imageHeapConstant);
                            assert data.isEmbedded() : "Found relocation that referenced an object that was not seen before " + relocation + ", " + imageHeapConstant;
                            relocation.setValue(data.getGlobalVariable().getter());
                        }
                        case SubstrateMethodPointerConstant methodPointerConstant -> {
                            MethodPointer methodPointer = methodPointerConstant.pointer();
                            int funtableIndex = getFunctionTableIndex(methodPointer);
                            relocation.setValue(Instruction.Const.forLong(funtableIndex));
                            relocation.setComment(methodPointer.getMethod().format("Method pointer for: %H.%n(%P)"));
                        }
                        case WasmFunctionIdConstant functionIdConstant -> {
                            WasmId.Func function = functionIdConstant.function();
                            int funtableIndex = getFunctionTableIndex(function);
                            relocation.setValue(Instruction.Const.forInt(funtableIndex));
                            relocation.setComment("Function id for: " + function);
                        }
                        default -> throw GraalError.unimplemented("Unsupported constant relocation: " + constant); // ExcludeFromJacocoGeneratedReport
                    }
                } else {
                    throw GraalError.unimplemented("Unsupported relocation reference: " + targetRef); // ExcludeFromJacocoGeneratedReport
                }

                GraalError.guarantee(relocation.wasProcessed(), "Relocation %s was not processed, target: %s", relocation, targetRef);
            }
        }.visitModule(module);
    }

    @Override
    protected WasmElementCreator getElementsCreator() {
        return new WasmGCElementCreator(getProviders());
    }

    @Override
    protected void validateModule() {
        // TODO GR-56363 Use implementation in superclass once validator works with WasmGC
    }

    @Override
    protected void emitBootstrapDefinitions() {
        super.emitBootstrapDefinitions();
        emitJSBodyImports();
    }

    /**
     * Generates the import object for the {@link WasmJSCounterparts#JSBODY_MODULE_NAME} module
     * containing all JS code provided through {@link JSBody} nodes.
     * <p>
     * For each function, generates:
     *
     * <pre>{@code
     * (...args) => (function(arg1, arg2) { JS CODE }).call(...args);
     * }</pre>
     *
     * The {@code this} pointer is provided by the caller as the first argument ({@code null} for
     * static methods).
     */
    protected void emitJSBodyImports() {
        Map<String, IEmitter> jsBodyDefinitions = new TreeMap<>();
        for (var entry : getProviders().getJSCounterparts().getJsBodyFunctions().entrySet()) {
            JSBody.JSCode jsCode = entry.getKey();
            WasmId.FunctionImport functionImport = entry.getValue();

            jsBodyDefinitions.put(functionImport.getDescriptor().name, t -> {
                CodeBuffer masm = t.getCodeBuffer();

                masm.emitText("(...args) => ");
                masm.emitKeyword(JSKeyword.LPAR);
                masm.emitKeyword(JSKeyword.FUNCTION);
                masm.emitKeyword(JSKeyword.LPAR);
                codeGenTool.genCommaList(Arrays.stream(jsCode.getArgs()).map(Emitter::of).collect(Collectors.toList()));
                masm.emitKeyword(JSKeyword.RPAR);
                masm.emitScopeBegin();
                masm.emitTry();
                for (String line : jsCode.getBody().split("\n")) {
                    masm.emitText(line);
                    masm.emitNewLine();
                }
                masm.emitCatch("e");
                /*
                 * For WasmGC, any JS errors have to be caught in JS code (Wasm cannot catch
                 * arbitrary JS errors)
                 */
                masm.emitText("conversion.handleJSError(e);");
                masm.emitScopeEnd();
                masm.emitScopeEnd();
                masm.emitKeyword(JSKeyword.RPAR);
                /*
                 * The first argument is always the receiver (null for static methods), which is
                 * bound to the JS 'this' pointer.
                 */
                masm.emitText(".call(...args)");
            });
        }

        codeGenTool.genResolvedVarAssignmentPrefix("wasmImports." + WasmJSCounterparts.JSBODY_MODULE_NAME);
        codeGenTool.genObject(jsBodyDefinitions);
        codeGenTool.getCodeBuffer().emitInsEnd();

        LowerableResources.lower(codeGenTool, LowerableResources.JSCONVERSION_COMMON);
        LowerableResources.lower(codeGenTool, WasmGCLowerableResources.JSCONVERSIONS);
    }

    @Override
    protected List<LowerableResource> getWasmBootstrapResources() {
        return List.of(WasmLMLowerableResources.BOOTSTRAP, WasmGCLowerableResources.CONVERSION, WasmGCLowerableResources.IMPORTS, WasmGCLowerableResources.RUNNER, LowerableResources.STACK_TRACE,
                        LowerableResources.CWD);
    }
}
