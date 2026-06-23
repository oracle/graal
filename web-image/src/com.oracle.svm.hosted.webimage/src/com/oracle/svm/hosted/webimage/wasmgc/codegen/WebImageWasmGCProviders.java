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

import java.io.PrintStream;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.webimage.codegen.WebImageProviders;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.wasm.WasmImports;
import com.oracle.svm.hosted.webimage.wasm.ast.id.KnownIds;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmIdFactory;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmCodeGenTool;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmNodeLowerer;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmProviders;
import com.oracle.svm.hosted.webimage.wasmgc.ast.id.GCKnownIds;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmGCUtil;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.Disallowed;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.webimage.wasm.types.WasmUtil;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.ResolvedJavaType;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = Disallowed.class)
public class WebImageWasmGCProviders extends WebImageWasmProviders {

    protected final WasmGCBuilder builder;

    @SuppressWarnings("this-escape")
    public WebImageWasmGCProviders(RuntimeConfiguration runtimeConfig, CoreProviders underlyingProviders, PrintStream out, DebugContext debug) {
        super(runtimeConfig, underlyingProviders, out, debug);
        this.builder = new WasmGCBuilder(this);

        if (WebImageOptions.isStandaloneWasm()) {
            registerComponentImportRemappings();
        }
    }

    /**
     * Registers import name remappings for WebAssembly Component Model compatibility.
     * <p>
     * The component model requires fully-qualified module names
     * (e.g. {@code graalvm:standalone/io@0.1.0}) and kebab-case function names
     * (e.g. {@code print-char}).
     */
    private void registerComponentImportRemappings() {
        WasmIdFactory factory = idFactory();

        // Compat math imports
        factory.addImportRemapping(WasmImports.F32Rem, WasmImports.Component.F32Rem);
        factory.addImportRemapping(WasmImports.F64Rem, WasmImports.Component.F64Rem);
        factory.addImportRemapping(WasmImports.F64Log, WasmImports.Component.F64Log);
        factory.addImportRemapping(WasmImports.F64Log10, WasmImports.Component.F64Log10);
        factory.addImportRemapping(WasmImports.F64Sin, WasmImports.Component.F64Sin);
        factory.addImportRemapping(WasmImports.F64Cos, WasmImports.Component.F64Cos);
        factory.addImportRemapping(WasmImports.F64Tan, WasmImports.Component.F64Tan);
        factory.addImportRemapping(WasmImports.F64Tanh, WasmImports.Component.F64Tanh);
        factory.addImportRemapping(WasmImports.F64Exp, WasmImports.Component.F64Exp);
        factory.addImportRemapping(WasmImports.F64Pow, WasmImports.Component.F64Pow);
        factory.addImportRemapping(WasmImports.F64Cbrt, WasmImports.Component.F64Cbrt);

        // IO imports
        factory.addImportRemapping(WasmImports.printChar, WasmImports.Component.printChar);
        factory.addImportRemapping(WasmImports.printBuffer, WasmImports.Component.printBuffer);
        factory.addImportRemapping(WasmImports.hostTimeMs, WasmImports.Component.hostTimeMs);

        // WASI imports
        factory.addImportRemapping(WasmImports.wasiProcExit, WasmImports.Component.procExit);
    }

    public static WebImageWasmGCProviders singleton() {
        return (WebImageWasmGCProviders) ImageSingletons.lookup(WebImageProviders.class);
    }

    /**
     * All instance methods must use {@link Object} so that their function signature is compatible
     * with the one expected at indirect call sites.
     * <p>
     * To allow for regular vtable indirect calls, the receiver type would only have to be the same
     * for all methods that implement the same base method. However, for dynamic reflective calls,
     * the same call site is used regardless of receiver type (see
     * {@link com.oracle.svm.hosted.reflect.ReflectionExpandSignatureMethod}). To support this, all
     * instance methods have to use {@link Object} as their receiver type.
     * <p>
     * Making use of WasmGC's subtyping is not possible because it uses contravariant parameter
     * types, while receiver types are covariant.
     */
    @Override
    public ResolvedJavaType getReceiverType(HostedMethod hostedMethod) {
        assert hostedMethod.hasReceiver() : "Cannot determine receiver type for method without receiver: " + hostedMethod;
        return getMetaAccess().lookupJavaType(Object.class);
    }

    @Override
    protected WasmUtil createWasmUtil(WebImageWasmProviders providers) {
        return new WasmGCUtil(providers);
    }

    @Override
    public WebImageWasmNodeLowerer getNodeLowerer(WasmCodeGenTool codeGen) {
        return new WebImageWasmGCNodeLowerer(codeGen);
    }

    @Override
    public GCKnownIds knownIds() {
        return (GCKnownIds) super.knownIds();
    }

    @Override
    public WasmGCUtil util() {
        return (WasmGCUtil) super.util();
    }

    @Override
    protected KnownIds createKnownIds(WasmIdFactory factory) {
        return new GCKnownIds(factory);
    }

    public WasmGCBuilder builder() {
        return builder;
    }
}
