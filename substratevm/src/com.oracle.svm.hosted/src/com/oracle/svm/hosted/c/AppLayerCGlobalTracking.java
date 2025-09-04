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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.CGlobalDataImpl;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.imagelayer.CodeLocation;
import com.oracle.svm.hosted.imagelayer.HostedImageLayerBuildingSupport;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder;

/**
 * Discovers and links CGlobals that were also installed in a prior layer. This is needed to ensure
 * CGlobals refer to a consistent location regardless of the layer in which the code was compiled
 * in. In other words, if a CGlobal was assigned a chunk of static global data in a prior layer,
 * then the CGlobal defined in this layer must be a symbol reference to the same data chunk.
 */
public class AppLayerCGlobalTracking {
    record PriorLayerCGlobal(CGlobalDataImpl<?> impl,
                    SharedLayerSnapshotCapnProtoSchemaHolder.CGlobalDataInfo.Reader persistedInfo) {

    }

    /**
     * Keeps track of symbol names which were defined in prior layers. Used for matching
     * {@link CGlobalDataImpl}s within {@link #getCanonicalRepresentation}
     */
    private Map<String, PriorLayerCGlobal> symbolNameToPriorLayerCGlobals;
    /**
     * Keeps track of code locations for {@link CGlobalDataImpl}s defined in prior layers. Used for
     * matching {@link CGlobalDataImpl}s within {@link #getCanonicalRepresentation}
     */
    private Map<CodeLocation, PriorLayerCGlobal> codeLocationToPriorLayerCGlobals;
    /** Used for decoding CGlobal information from persisted graphs. */
    private List<PriorLayerCGlobal> priorLayerCGlobals;

    /**
     * Tracks CGlobals assigned a chunk of static global data in this layer that have references in
     * prior layers. We use this collection to validate that these CGlobals have been registered in
     * a way which allows linking to successfully complete.
     */
    private Set<CGlobalDataImpl<?>> cGlobalsWithPriorLayerReferences;
    /**
     * Tracks which CGlobals are defined in this layer that were
     * {@link CGlobalDataImpl#isSymbolReference()} in prior layers.
     */
    private Map<String, CGlobalDataImpl<?>> cGlobalsWithPriorLayerReferencesMap = new ConcurrentHashMap<>();

    /** Saves the results of {@link #getCanonicalRepresentation}. */
    private final Map<CGlobalDataImpl<?>, CGlobalDataImpl<?>> canonicalizationCache = new ConcurrentHashMap<>();

    /**
     * Tracks which {@link CGlobalDataImpl} a {@link CodeLocation} is linked to in the current
     * layer. This is used to ensure the same code location is not linked to multiple
     * {@link CGlobalDataImpl}s.
     */
    private final Map<CodeLocation, CGlobalDataImpl<?>> linkedCodeLocations = new ConcurrentHashMap<>();

    /**
     * A map used within {@link #createCGlobalDataInfo} to initialize {@link CGlobalDataInfo}s
     * appropriately.
     */
    private Map<CGlobalDataImpl<?>, PriorLayerCGlobal> cGlobalToPriorLayerCGlobals;

    private final CGlobalDataFeature cGlobalDataFeature;

    AppLayerCGlobalTracking(CGlobalDataFeature cGlobalDataFeature) {
        this.cGlobalDataFeature = cGlobalDataFeature;
    }

    /**
     * Registers a CGlobal which was a {@link CGlobalDataImpl#isSymbolReference()} in prior layers,
     * but is assigned of chunk of static global data in this layer.
     */
    public void registerCGlobalWithPriorLayerReference(CGlobalData<?> info) {
        /*
         * Note this code will either need to be adjusted to work with 3+ layers or we must forbid
         * this method from being called in intermediate layers.
         */
        VMError.guarantee(cGlobalsWithPriorLayerReferencesMap != null, "registerCGlobalWithPriorLayerReference cannot be called after afterRegistration is complete.");
        CGlobalDataImpl<?> data = (CGlobalDataImpl<?>) info;
        VMError.guarantee(data.symbolName != null && !data.isSymbolReference(),
                        "registerCGlobalWithPriorLayerReference can only be used to register CGlobals which were symbol references in prior layers and are not in the current layer" +
                                        ".%nIn addition, a symbol name must associated with the CGlobal so linking is possible.%nCGlobal: %s",
                        data.symbolName);
        var prev = cGlobalsWithPriorLayerReferencesMap.put(data.symbolName, data);
        VMError.guarantee(prev == null, "Multiple CGlobals registered with the same symbol name %s", data.symbolName);
    }

    public CGlobalDataInfo registerOrGetCGlobalDataInfoByPersistedIndex(int index) {
        var priorCGlobal = priorLayerCGlobals.get(index);
        return cGlobalDataFeature.registerAsAccessedOrGet(priorCGlobal.impl(), false);
    }

    public CGlobalDataImpl<?> registerOrGetCGlobalDataImplByPersistedIndex(int index) {
        var priorCGlobal = priorLayerCGlobals.get(index);
        cGlobalDataFeature.registerAsAccessedOrGet(priorCGlobal.impl(), false);
        return priorCGlobal.impl();
    }

    /**
     * CGlobals with links to prior layers may also have
     * {@code CGlobalDataInfo#definedAsGlobalInPriorLayer} set.
     */
    CGlobalDataInfo createCGlobalDataInfo(CGlobalDataImpl<?> impl) {
        PriorLayerCGlobal info = cGlobalToPriorLayerCGlobals.get(impl);
        if (info != null) {
            boolean definedAsGlobalInPriorLayer = info.persistedInfo().getIsGlobalSymbol();
            if (!info.impl().isSymbolReference()) {
                assert !info.persistedInfo().getIsGlobalSymbol() : info;
            }
            return cGlobalDataFeature.createCGlobalDataInfo(impl, definedAsGlobalInPriorLayer);
        }

        return null;
    }

    private CGlobalDataImpl<?> getCanonicalRepresentationForPriorLayerCGlobal(CGlobalDataImpl<?> input, PriorLayerCGlobal priorCGlobal) {
        /*
         * We currently expect the non-constant marker to be consistent across all layers and
         * declaration. If desired, in the future we can relax this to !(input.nonConstant &&
         * !canonicalRepresentation.nonConstant).
         *
         * We also expect newly non-symbol references defined in this layer to be explicitly labeled
         * via registerCGlobalWithPriorLayerReference.
         */
        CGlobalDataImpl<?> canonicalRepr = priorCGlobal.impl();
        VMError.guarantee(input.nonConstant == canonicalRepr.nonConstant);
        if (!input.isSymbolReference() && priorCGlobal.persistedInfo().getIsSymbolReference()) {
            VMError.guarantee(cGlobalsWithPriorLayerReferences.contains(canonicalRepr),
                            "Found CGlobal which was a symbol reference in a prior layer but is not in this layer: %s%n. If this is intentional please register via registerCGlobalWithPriorLayerReference",
                            input.symbolName);
        }
        return canonicalRepr;
    }

    /**
     * Returns the canonical representation for this CGlobal in this layer. This is needed because
     * {@link CGlobalDataImpl} instances from the prior layer can be loaded via persisted graphs;
     * without this canonicalization step it would be possible for multiple {@link CGlobalDataImpl}
     * instances to be linked to the same symbol.
     */
    CGlobalDataImpl<?> getCanonicalRepresentation(CGlobalDataImpl<?> input) {
        CGlobalDataImpl<?> result = canonicalizationCache.get(input);
        if (result != null) {
            return result;
        }

        if (input.symbolName != null) {
            var ref = symbolNameToPriorLayerCGlobals.get(input.symbolName);
            if (ref != null) {
                result = getCanonicalRepresentationForPriorLayerCGlobal(input, ref);
            }
        }
        if (result == null && input.codeLocation != null) {
            CodeLocation codeLocation = CodeLocation.fromStackFrame(input.codeLocation);
            var ref = codeLocationToPriorLayerCGlobals.get(codeLocation);
            if (ref != null) {
                var prev = linkedCodeLocations.put(codeLocation, input);
                VMError.guarantee(prev == null || prev == input, "Multiple inputs seen for same code location: %s %s %s", codeLocation, prev, input);
                result = getCanonicalRepresentationForPriorLayerCGlobal(input, ref);
            }
        }
        if (result == null) {
            result = input;
        }

        var prev = canonicalizationCache.putIfAbsent(input, result);
        VMError.guarantee(prev == null || prev.equals(result), "Cache is providing inconsistent results: %s %s %s", input, result, prev);
        return result;
    }

    /**
     * We must ensure that CGlobals registered via {@link #registerCGlobalWithPriorLayerReference}
     * are set as global and not hidden. This is needed because symbols in prior layers must link to
     * them.
     */
    void validateCGlobals(Map<CGlobalDataImpl<?>, CGlobalDataInfo> map) {
        for (var impl : cGlobalsWithPriorLayerReferences) {
            CGlobalDataInfo info = map.get(impl);
            boolean validState = info != null && info.isGlobalSymbol() && !info.isHiddenSymbol();
            VMError.guarantee(validState, "CGlobals registered via registerCGlobalWithPriorLayerReference must also be registered as global and cannot be registered as hidden: %s", impl.symbolName);
        }
    }

    /** Load all CGlobals registered in prior layers. */
    public void initializePriorLayerCGlobals() {
        var loader = HostedImageLayerBuildingSupport.singleton().getLoader();
        Map<String, PriorLayerCGlobal> newSymbolNameToPriorLayerCGlobals = new HashMap<>();
        Map<CodeLocation, PriorLayerCGlobal> newCodeLocationToPriorLayerCGlobals = new HashMap<>();
        ArrayList<PriorLayerCGlobal> newPriorLayerCGlobals = new ArrayList<>();
        Set<CGlobalDataImpl<?>> newCGlobalsWithPriorLayerReferences = new HashSet<>();
        Map<CGlobalDataImpl<?>, PriorLayerCGlobal> newCGlobalToPriorLayerCGlobals = new HashMap<>();
        for (var persistedInfo : loader.getCGlobals()) {
            String symbolName = persistedInfo.getLayeredSymbolName().toString();
            CGlobalDataImpl<?> globalEntry = null;
            if (persistedInfo.getLinkingInfo().hasOriginalSymbolName()) {
                String originalSymbolName = persistedInfo.getLinkingInfo().getOriginalSymbolName().toString();
                globalEntry = cGlobalsWithPriorLayerReferencesMap.get(originalSymbolName);
                if (globalEntry != null) {
                    newCGlobalsWithPriorLayerReferences.add(globalEntry);
                    VMError.guarantee(persistedInfo.getIsSymbolReference(), "We can only override CGlobals which were symbol references in prior layers %s",
                                    originalSymbolName);
                }
            }

            if (globalEntry == null) {
                globalEntry = (CGlobalDataImpl<?>) CGlobalDataFactory.forSymbol(symbolName, persistedInfo.getNonConstant());
            }
            var priorInfo = new PriorLayerCGlobal(globalEntry, persistedInfo);

            /*
             * At this point we are not creating the CGlobalDataInfo. This is intentional so that we
             * do not need to install all prior CGlobals as symbols in this layer. Instead, we
             * create the CGlobalDataInfo upon the first reference to the CGlobalDataImpl.
             */
            if (persistedInfo.getLinkingInfo().hasOriginalSymbolName()) {
                String originalSymbolName = persistedInfo.getLinkingInfo().getOriginalSymbolName().toString();
                var previous = newSymbolNameToPriorLayerCGlobals.put(originalSymbolName, priorInfo);
                VMError.guarantee(previous == null);
            } else if (persistedInfo.getLinkingInfo().hasCodeLocation()) {
                var callSiteInfo = persistedInfo.getLinkingInfo().getCodeLocation();
                int bci = callSiteInfo.getBytecodeIndex();
                String stacktrace = callSiteInfo.getStacktraceName().toString();
                var previous = newCodeLocationToPriorLayerCGlobals.put(new CodeLocation(bci, stacktrace), priorInfo);
                VMError.guarantee(previous == null);
            }
            var previous = newCGlobalToPriorLayerCGlobals.put(globalEntry, priorInfo);
            VMError.guarantee(previous == null);
            newPriorLayerCGlobals.add(priorInfo);
        }
        priorLayerCGlobals = Collections.unmodifiableList(newPriorLayerCGlobals);
        symbolNameToPriorLayerCGlobals = Collections.unmodifiableMap(newSymbolNameToPriorLayerCGlobals);
        codeLocationToPriorLayerCGlobals = Collections.unmodifiableMap(newCodeLocationToPriorLayerCGlobals);
        cGlobalsWithPriorLayerReferences = Collections.unmodifiableSet(newCGlobalsWithPriorLayerReferences);
        cGlobalToPriorLayerCGlobals = Collections.unmodifiableMap(newCGlobalToPriorLayerCGlobals);
        cGlobalsWithPriorLayerReferencesMap = null; // throw on late registrations
    }
}
