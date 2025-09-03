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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.graalvm.collections.Pair;

import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.WebImageCodeCache;
import com.oracle.svm.hosted.webimage.util.ReflectUtil;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.debug.DebugContext;

public class WebImageWasmGCCodeCache extends WebImageCodeCache {
    /**
     * For each method, stores the metadata string associated with it (if any).
     */
    private final Map<HostedMethod, String> exportedMethodMetadata = new LinkedHashMap<>();
    /**
     * For each method which is a single abstract method, stores the metadata string.
     */
    private final Map<HostedMethod, String> exportedSingleAbstractMethods = new LinkedHashMap<>();

    public WebImageWasmGCCodeCache(Map<HostedMethod, CompilationResult> compilationResultMap, NativeImageHeap imageHeap) {
        super(compilationResultMap, imageHeap);
    }

    @Override
    public void buildRuntimeMetadata(DebugContext debug, SnippetReflectionProvider snippetReflectionProvider) {
        super.buildRuntimeMetadata(debug, snippetReflectionProvider);

        for (Pair<HostedMethod, CompilationResult> orderedCompilation : getOrderedCompilations()) {
            HostedMethod method = orderedCompilation.getLeft();
            registerMethodMetadata(method, WasmGCMetadataLowerer.METADATA_PREFIX, exportedMethodMetadata);
        }

        for (HostedType type : nativeImageHeap.hMetaAccess.getUniverse().getTypes()) {
            Optional<HostedMethod> sam = ReflectUtil.singleAbstractMethodForClass(nativeImageHeap.hMetaAccess, type);
            if (sam.isPresent()) {
                HostedMethod method = sam.get();
                if (method.isCompiled()) {
                    registerMethodMetadata(method, WasmGCMetadataLowerer.SAM_PREFIX, exportedSingleAbstractMethods);
                }
            }
        }
    }

    private static void registerMethodMetadata(HostedMethod m, String prefix, Map<HostedMethod, String> map) {
        Optional<String> exportedName = WasmGCMetadataLowerer.createMethodMetadata(m, prefix);
        exportedName.ifPresent(name -> map.put(m, name));
    }

    public Map<HostedMethod, String> getExportedMethodMetadata() {
        return exportedMethodMetadata;
    }

    public Map<HostedMethod, String> getExportedSingleAbstractMethods() {
        return exportedSingleAbstractMethods;
    }
}
