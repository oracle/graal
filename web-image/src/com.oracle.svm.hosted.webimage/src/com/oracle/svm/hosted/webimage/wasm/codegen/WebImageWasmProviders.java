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

import java.io.PrintStream;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.webimage.codegen.WebImageProviders;
import com.oracle.svm.hosted.webimage.wasm.WasmJSCounterparts;
import com.oracle.svm.hosted.webimage.wasm.ast.id.KnownIds;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmIdFactory;
import com.oracle.svm.webimage.wasm.types.WasmLMUtil;
import com.oracle.svm.webimage.wasm.types.WasmUtil;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.ResolvedJavaType;

public class WebImageWasmProviders extends WebImageProviders {

    protected final WasmIdFactory idFactory = new WasmIdFactory();
    protected final KnownIds knownIds = createKnownIds(idFactory);

    protected final WasmJSCounterparts jsCounterparts = new WasmJSCounterparts();

    protected final WasmUtil util = createWasmUtil(this);

    @SuppressWarnings("this-escape")
    public WebImageWasmProviders(RuntimeConfiguration runtimeConfig, CoreProviders underlyingProviders, PrintStream out, DebugContext debug) {
        super(underlyingProviders, out, debug);

        // Once the provider is initialized we can use it to create the throwable tag
        knownIds.initializeJavaThrowableTag(this);

        /*
         * Propagate this provider to all backends. During lowering, all functions need to access
         * the same id factory and known ids.
         */
        runtimeConfig.getBackends().forEach(backend -> ((WebImageWasmBackend) backend).setWasmProviders(this));
    }

    /**
     * The receiver type that should be used when declaring or calling the Wasm function
     * representing the given method.
     */
    public ResolvedJavaType getReceiverType(HostedMethod hostedMethod) {
        assert hostedMethod.hasReceiver() : "Cannot determine receiver type for method without receiver: " + hostedMethod;
        return hostedMethod.getDeclaringClass();
    }

    protected WasmUtil createWasmUtil(WebImageWasmProviders providers) {
        return new WasmLMUtil(providers, ((HostedProviders) providers.getProviders()).getGraphBuilderPlugins());
    }

    public WasmIdFactory idFactory() {
        return idFactory;
    }

    public KnownIds knownIds() {
        return knownIds;
    }

    public WasmJSCounterparts getJSCounterparts() {
        return jsCounterparts;
    }

    public WasmUtil util() {
        return util;
    }

    public WebImageWasmNodeLowerer getNodeLowerer(WasmCodeGenTool codeGen) {
        return new WebImageWasmLMNodeLowerer(codeGen);
    }

    protected KnownIds createKnownIds(WasmIdFactory factory) {
        return new KnownIds(factory);
    }
}
