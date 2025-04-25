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

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.hosted.FeatureHandler;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.webimage.code.WebImageCompileQueue;
import com.oracle.svm.hosted.webimage.codegen.WebImageProviders;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmFunctionTemplate;
import com.oracle.svm.hosted.webimage.wasmgc.codegen.WebImageWasmGCProviders;

import jdk.graal.compiler.debug.DebugContext;

public class WebImageWasmGCCompileQueue extends WebImageCompileQueue {

    @SuppressWarnings("this-escape")
    public WebImageWasmGCCompileQueue(FeatureHandler featureHandler, HostedUniverse hUniverse, RuntimeConfiguration runtimeConfiguration, DebugContext debug) {
        super(featureHandler, hUniverse, runtimeConfiguration, debug);
    }

    @Override
    protected void checkUninterruptibleAnnotations() {
        // Do nothing. Currently, the WasmGC backend has no use for uninterruptible methods
    }

    @Override
    protected void checkRestrictHeapAnnotations(DebugContext debug) {
        // Do nothing. Currently, the WasmGC backend does not limit heap access
    }

    /**
     * After all compilations have completed, schedules an additional compilation to generate all
     * requested {@link WasmFunctionTemplate function templates}.
     * <p>
     * See {@link WasmGCFunctionTemplateFeature} for more information.
     */
    @Override
    protected void compileAll() throws InterruptedException {
        super.compileAll();

        runOnExecutor(() -> {
            WebImageWasmGCProviders wasmProviders = (WebImageWasmGCProviders) ImageSingletons.lookup(WebImageProviders.class);
            HostedMethod placeholder = ((HostedMetaAccess) wasmProviders.getMetaAccess()).getUniverse().lookup(WasmGCFunctionTemplateFeature.getFunctionTemplatesPlaceholder());
            placeholder.compilationInfo.setCustomCompileFunction(WasmGCFunctionTemplateFeature::createFunctionTemplates);
            ensureCompiled(placeholder, new EntryPointReason());
        });
    }
}
