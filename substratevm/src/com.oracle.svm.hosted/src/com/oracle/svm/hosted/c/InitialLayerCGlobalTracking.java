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
package com.oracle.svm.hosted.c;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.oracle.svm.core.c.CGlobalDataImpl;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.imagelayer.CodeLocation;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder;

/**
 * Tracks CGlobals installed in the initial layer. This information is used to ensure CGlobals refer
 * to a consistent location regardless of which layer the code was compiled in.
 */
public class InitialLayerCGlobalTracking {

    private static final String LAYERED_CGLOBAL_SYMBOL_PREFIX = "__svm_layered_cglobal";

    private final Map<CGlobalDataImpl<?>, Integer> toPersistEncodedDataIdx = new ConcurrentHashMap<>();
    private final AtomicInteger nextIdx = new AtomicInteger(0);

    private Map<CGlobalDataInfo, String> toPersistLayeredSymbolNameMap;

    private final CGlobalDataFeature cGlobalDataFeature;

    InitialLayerCGlobalTracking(CGlobalDataFeature cGlobalDataFeature) {
        this.cGlobalDataFeature = cGlobalDataFeature;
    }

    public void registerCGlobal(CGlobalDataImpl<?> data) {
        toPersistEncodedDataIdx.computeIfAbsent(data, key -> nextIdx.getAndIncrement());
    }

    public int getEncodedIndex(CGlobalDataImpl<?> data) {
        return toPersistEncodedDataIdx.get(data);
    }

    public CGlobalDataInfo[] getInfosOrderedByIndex() {
        return toPersistEncodedDataIdx.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue())
                        .map(e -> {
                            var result = cGlobalDataFeature.getDataInfo(e.getKey());
                            return Objects.requireNonNull(result);
                        })
                        .toArray(CGlobalDataInfo[]::new);
    }

    public void persistCGlobalInfo(CGlobalDataInfo info, Supplier<SharedLayerSnapshotCapnProtoSchemaHolder.CGlobalDataInfo.Builder> builderSupplier) {
        String layeredSymbolName = toPersistLayeredSymbolNameMap.get(info);
        assert layeredSymbolName != null;
        var builder = builderSupplier.get();
        builder.setLayeredSymbolName(layeredSymbolName);
        CGlobalDataImpl<?> data = info.getData();
        if (data.symbolName != null) {
            builder.initLinkingInfo().setOriginalSymbolName(data.symbolName);
        } else {
            /*
             * Note in layered image builds the uniqueness of creation sites is checked in
             * CGlobalDataFeature#createCGlobalDataInfo.
             */
            var location = builder.initLinkingInfo().initCodeLocation();
            CodeLocation codeLocation = CodeLocation.fromStackFrame(info.getData().codeLocation);
            location.setBytecodeIndex(codeLocation.bci());
            location.setStacktraceName(codeLocation.name());
        }
        builder.setNonConstant(data.nonConstant);
        builder.setIsGlobalSymbol(info.isGlobalSymbol());
        builder.setIsSymbolReference(info.isSymbolReference());
    }

    void writeData(CGlobalDataFeature.SymbolConsumer createSymbol,
                    Map<CGlobalDataImpl<?>, CGlobalDataInfo> fullMap) {
        VMError.guarantee(fullMap.size() == toPersistEncodedDataIdx.size());

        toPersistLayeredSymbolNameMap = new HashMap<>();
        int symbolCount = 0;
        for (CGlobalDataInfo info : fullMap.values()) {
            assert toPersistEncodedDataIdx.containsKey(info.getData()) : info;

            String layeredSymbolName;
            if (info.isSymbolReference()) {
                /*
                 * Symbol references only refer to the actual data, so these can be freely recreated
                 * across layers.
                 */
                layeredSymbolName = Objects.requireNonNull(info.getData().symbolName);
            } else {
                /*
                 * CGlobals linked to a chunk of global data must be referred to by symbol
                 * references within other layers. If a CGlobal already has a global symbol, then we
                 * can simply refer to this global name. Otherwise, we must associate an internal
                 * global symbol with this CGlobal so that we can refer to it in subsequent layers.
                 *
                 * Note hidden global symbols also need a new symbol to be created. This is because
                 * hidden global symbols are not exported from the shared layer images (which are
                 * shared libraries) and hence are not visible for dynamic linking.
                 */
                if (info.isGlobalSymbol() && !info.isHiddenSymbol()) {
                    layeredSymbolName = info.getData().symbolName;
                } else {
                    // create symbol name
                    layeredSymbolName = String.format("%s_%s", LAYERED_CGLOBAL_SYMBOL_PREFIX, symbolCount);
                    symbolCount++;
                    createSymbol.apply(info.getOffset(), layeredSymbolName, true);
                }
            }
            var prev = toPersistLayeredSymbolNameMap.put(info, layeredSymbolName);
            VMError.guarantee(prev == null);
        }
    }
}
