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

package com.oracle.svm.hosted.webimage.wasm;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.nativeimage.Platform;

import com.oracle.svm.hosted.webimage.js.JSBody;
import com.oracle.svm.hosted.webimage.name.WebImageNamingConvention;
import com.oracle.svm.hosted.webimage.wasm.ast.ImportDescriptor;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmProviders;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmRefType;
import com.oracle.svm.webimage.functionintrinsics.JSSystemFunction;
import com.oracle.svm.webimage.platform.WebImageWasmGCPlatform;
import com.oracle.svm.webimage.wasm.types.WasmUtil;
import com.oracle.svm.webimage.wasm.types.WasmValType;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodes.NodeView;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Manages JS code required by WASM that is only known at compile time (in contrast to
 * {@link WasmImports}).
 * <p>
 * Each compilation (concurrently) registers JS code that it requires and gets back an imported
 * {@link com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId.Func function id} that it can call.
 * <p>
 * Supports {@link JSSystemFunction} through
 * {@link #idForJSFunction(WebImageWasmProviders, JSSystemFunction)} and {@link JSBody} through
 * {@link #idForJSBody(WebImageWasmProviders, JSBody)}.
 */
public class WasmJSCounterparts {

    /**
     * Import module name for imports created with
     * {@link #idForJSFunction(WebImageWasmProviders, JSSystemFunction)}.
     */
    public static final String JSFUNCTION_MODULE_NAME = "interop";

    /**
     * Import module name for imports created with
     * {@link #idForJSBody(WebImageWasmProviders, JSBody)}.
     */
    public static final String JSBODY_MODULE_NAME = "jsbody";

    /**
     * Maps {@link JSSystemFunction}s to the unique
     * {@link com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId.FunctionImport} generated for them.
     */
    private final ConcurrentMap<JSSystemFunction, WasmId.FunctionImport> jsFunctions = new ConcurrentHashMap<>();
    /**
     * Maps each JSBody code to the unique
     * {@link com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId.FunctionImport} generated for them.
     * <p>
     * Does not use the {@link JSBody} as the key to not keep all the graph data alive after
     * compilation.
     */
    private final ConcurrentMap<JSBody.JSCode, WasmId.FunctionImport> jsBodies = new ConcurrentHashMap<>();

    public List<JSSystemFunction> getFunctions() {
        return jsFunctions.keySet().stream().toList();
    }

    public Map<JSBody.JSCode, WasmId.FunctionImport> getJsBodyFunctions() {
        return Collections.unmodifiableMap(jsBodies);
    }

    /**
     * Requests/Looks up the {@link com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId.Func function
     * id} to call the given {@link JSSystemFunction}.
     */
    public WasmId.Func idForJSFunction(WebImageWasmProviders wasmProviders, JSSystemFunction function) {
        return jsFunctions.computeIfAbsent(function, fun -> wasmProviders.idFactory().forFunctionImport(createImport(wasmProviders.util(), fun)));
    }

    /**
     * Requests/Looks up the {@link com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId.Func function
     * id} to call the JS code generated for the given {@link JSBody}.
     *
     * @see #createImportForJSBody(WasmUtil, JSBody)
     */
    public WasmId.Func idForJSBody(WebImageWasmProviders wasmProviders, JSBody jsBody) {
        return jsBodies.computeIfAbsent(jsBody.getJsCode(), code -> wasmProviders.idFactory().forFunctionImport(createImportForJSBody(wasmProviders.util(), jsBody)));
    }

    /**
     * Determines how Java arguments should be represented in the imported Wasm function.
     * <p>
     * If the JS call has an object argument, we treat it as an {@code externref} since the JS
     * function expects a JS object.
     * <p>
     * Just using {@code externref} is easier than determining the exact type that is passed,
     * especially since the value may have undergone some conversion on the way.
     */
    private static WasmValType getArgumentType(JavaKind argumentKind, WasmUtil util) {
        return argumentKind == JavaKind.Object ? WasmRefType.EXTERNREF : util.mapType(argumentKind);
    }

    /**
     * Determines how the JS return type should be represented in the imported Wasm function.
     * <p>
     * In the WasmGC backend, any objects returned from JS calls are treated as external references.
     * At the call site, those externrefs are then made into a Java object (either by directly
     * converting the externref to a WasmGC type or by wrapping it in a WasmExtern instance).
     * <p>
     * In all other cases, the corresponding primitive type is used as the return value.
     */
    private static WasmValType getReturnType(Stamp returnStamp, WasmUtil util) {
        if (Platform.includedIn(WebImageWasmGCPlatform.class) && returnStamp.isObjectStamp()) {
            return WasmRefType.EXTERNREF;
        } else {
            return util.typeForStamp(returnStamp);
        }
    }

    private static ImportDescriptor.Function createImport(WasmUtil util, JSSystemFunction function) {
        WasmValType[] argTypes = Arrays.stream(function.getArgKinds())
                        .map(kind -> getArgumentType(kind, util))
                        .toArray(WasmValType[]::new);
        WasmValType returnType = getReturnType(function.stamp(), util);
        TypeUse typeUse = TypeUse.withOptionalResult(returnType, argTypes);

        return new ImportDescriptor.Function(JSFUNCTION_MODULE_NAME, function.getFunctionName(), typeUse, function.toString());
    }

    /**
     * Creates an import under the {@link #JSBODY_MODULE_NAME} module for the given {@link JSBody}
     * node.
     */
    private static ImportDescriptor.Function createImportForJSBody(WasmUtil util, JSBody jsBody) {
        JSBody.JSCode jsCode = jsBody.getJsCode();
        ResolvedJavaMethod method = jsBody.getMethod();
        WebImageNamingConvention namingConvention = WebImageNamingConvention.getInstance();

        WasmValType[] argTypes = jsBody.getArguments().stream()
                        .map(argNode -> {
                            JavaKind kind = util.kindForNode(argNode);
                            return getArgumentType(kind, util);
                        })
                        .toArray(WasmValType[]::new);
        WasmValType returnType = getReturnType(jsBody.asNode().stamp(NodeView.DEFAULT), util);
        TypeUse typeUse = TypeUse.withOptionalResult(returnType, argTypes);

        StringBuilder comment = new StringBuilder("User-provided JS code in ");
        comment.append(method.format("%H.%n(%P)%R")).append(": function(");
        for (int i = 0; i < jsCode.getArgs().length; i++) {
            if (i != 0) {
                comment.append(", ");
            }
            comment.append(jsCode.getArgs()[i]);
        }
        comment.append(") { ").append(jsCode.getBody()).append(" }");

        return new ImportDescriptor.Function(JSBODY_MODULE_NAME, namingConvention.identForType(method.getDeclaringClass()) + "." + namingConvention.identForMethod(method), typeUse,
                        comment.toString());
    }
}
