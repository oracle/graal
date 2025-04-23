/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.imagelayer;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.InvalidMethodPointerHandler;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.snippets.OpenTypeWorldDispatchTableSnippets;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeCompilationAccessImpl;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.image.NativeImage;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.meta.VTableBuilder;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.options.Option;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Tracks the state of type dispatch tables across layers. Across layers, this information is used
 * to validate the consistency of dispatch table computations and track the resolution status of
 * dispatch table slots.
 *
 * The dispatch logic for virtual calls can be found in {@link OpenTypeWorldDispatchTableSnippets}.
 *
 * The contents of the dispatch tables are computed within {@link VTableBuilder}.
 *
 * We also track across layers virtual calls which need to be registered as roots in subsequent
 * layers. We must register these call as roots since new types can be instantiated in subsequent
 * layers which then may cause new call targets to be reachable from prior layers' virtual calls.
 *
 * We call {@link #recordVirtualCallTarget} to register a virtual call that must be added as a root
 * in a subsequent layer. The logic for installing roots in subsequent layers is performed in
 * {@link LayeredDispatchTableSupportFeature#beforeAnalysis}.
 */
public class LayeredDispatchTableSupport implements LayeredImageSingleton {
    public static final class Options {
        @Option(help = "Log discrepancies between layered open world type information. This is an experimental option which will be removed.")//
        public static final HostedOptionKey<Boolean> LogLayeredDispatchTableDiscrepancies = new HostedOptionKey<>(false);

        @Option(help = "Throw an error when there are discrepancies between layered open world type information. This is an experimental option which will be removed.")//
        public static final HostedOptionKey<Boolean> ErrorOnLayeredDispatchTableDiscrepancies = new HostedOptionKey<>(false);
    }

    final Set<String> priorUnresolvedSymbols;
    final Map<Integer, PriorDispatchTable> priorDispatchTables;
    /**
     * Map from typeId to set of names generated via {@link #generateFormattedMethodName}.
     */
    final Map<Integer, Set<String>> priorVirtualCallTargets;

    final Map<MethodPointer, HostedDispatchSlot> methodPointerToDispatchSlot = new IdentityHashMap<>();
    final Map<HostedType, HostedDispatchTable> typeToDispatchTable = new HashMap<>();
    final Set<HostedMethod> virtualCallTargets = ImageLayerBuildingSupport.buildingSharedLayer() ? ConcurrentHashMap.newKeySet() : null;

    private final boolean generateUnresolvedSymbolNames = ImageLayerBuildingSupport.buildingSharedLayer();

    /**
     * Cache of builderModules. Set in {@link LayeredDispatchTableSupportFeature#beforeCompilation}.
     */
    private Set<Module> builderModules;

    public LayeredDispatchTableSupport() {
        this(Map.of(), Set.of(), Map.of());
    }

    private LayeredDispatchTableSupport(Map<Integer, PriorDispatchTable> priorDispatchTables, Set<String> priorUnresolvedSymbols, Map<Integer, Set<String>> priorVirtualCallTargets) {
        this.priorDispatchTables = priorDispatchTables;
        this.priorUnresolvedSymbols = priorUnresolvedSymbols;
        this.priorVirtualCallTargets = priorVirtualCallTargets;
    }

    public static LayeredDispatchTableSupport singleton() {
        return ImageSingletons.lookup(LayeredDispatchTableSupport.class);
    }

    void installBuilderModules(Set<Module> newCoreTypes) {
        assert builderModules == null : builderModules;
        builderModules = newCoreTypes;
    }

    /**
     * Registers a virtual call target which will be added as a root in subsequent layers. Currently
     * we filter our all calls either originating from or targeting a {@link #builderModules}.
     */
    public void recordVirtualCallTarget(HostedMethod caller, HostedMethod callee) {
        Module callerModule = caller.getDeclaringClass().getJavaClass().getModule();
        Module calleeModule = callee.getDeclaringClass().getJavaClass().getModule();
        if (!(builderModules.contains(callerModule) || builderModules.contains(calleeModule))) {
            virtualCallTargets.add(callee);
        }
    }

    // GR-59009 remove once we persist more methods
    static String generateFormattedMethodName(AnalysisMethod method) {
        return method.format("%n(%P)%R");
    }

    public void registerDeclaredDispatchInfo(HostedType type, List<HostedMethod> declaredMethods) {
        var dispatchTable = new HostedDispatchTable();
        dispatchTable.type = type;
        dispatchTable.locallyDeclaredSlots = declaredMethods.toArray(HostedMethod[]::new);
        var prev = typeToDispatchTable.put(type, dispatchTable);
        assert prev == null : prev;
    }

    /**
     * During the initial stages of vtable building we do not explicitly calculate dispatch table
     * info for arrays. Instead, we know they share the same dispatch table internals as the Object
     * type.
     */
    public void registerArrayDispatchTable(HostedType arrayType, HostedType objectType) {
        assert !typeToDispatchTable.containsKey(arrayType);
        var objDispatchTable = typeToDispatchTable.get(objectType);
        var arrayDispatchTable = new HostedDispatchTable();
        arrayDispatchTable.type = arrayType;
        arrayDispatchTable.locallyDeclaredSlots = objDispatchTable.locallyDeclaredSlots;
        arrayDispatchTable.status = HubStatus.DISPATCH_INFO_CALCULATED;

        arrayDispatchTable.slots = Arrays.stream(objDispatchTable.slots).map(objSlotInfo -> {
            HostedDispatchSlot arraySlotInfo = new HostedDispatchSlot();
            arraySlotInfo.dispatchTable = arrayDispatchTable;
            arraySlotInfo.declaredMethod = objSlotInfo.declaredMethod;
            arraySlotInfo.resolvedMethod = objSlotInfo.resolvedMethod;
            arraySlotInfo.slotNum = objSlotInfo.slotNum;
            arraySlotInfo.status = objSlotInfo.status;

            return arraySlotInfo;
        }).toArray(HostedDispatchSlot[]::new);

        injectPriorLayerInfo(arrayType, arrayDispatchTable);

        var prev = typeToDispatchTable.put(arrayType, arrayDispatchTable);
        assert prev == null : prev;
    }

    public void registerNonArrayDispatchTable(HostedType type, boolean[] validTarget) {
        var dispatchTable = typeToDispatchTable.get(type);
        dispatchTable.status = HubStatus.DISPATCH_INFO_CALCULATED;

        assert dispatchTable.slots == null;

        var resolvedMethods = type.getOpenTypeWorldDispatchTables();
        var targetMethods = type.getOpenTypeWorldDispatchTableSlotTargets();
        int length = validTarget.length;
        assert resolvedMethods.length == length && targetMethods.length == length : Assertions.errorMessage(resolvedMethods, targetMethods, validTarget);

        HostedDispatchSlot[] slotInfos = new HostedDispatchSlot[length];
        for (int i = 0; i < length; i++) {
            HostedDispatchSlot slot = new HostedDispatchSlot();
            slot.dispatchTable = dispatchTable;
            slot.slotNum = i;
            slot.resolvedMethod = validTarget[i] ? resolvedMethods[i] : null;
            slot.declaredMethod = targetMethods[i];
            slot.status = validTarget[i] ? SlotResolutionStatus.COMPUTED : SlotResolutionStatus.UNRESOLVED;
            slotInfos[i] = slot;
        }

        dispatchTable.slots = slotInfos;

        injectPriorLayerInfo(type, dispatchTable);
    }

    private void injectPriorLayerInfo(HostedType type, HostedDispatchTable dispatchTable) {
        if (type.getWrapped().isInBaseLayer()) {
            var priorInfo = priorDispatchTables.get(type.getWrapped().getId());
            if (priorInfo != null) {
                compareTypeInfo(dispatchTable, priorInfo);
                dispatchTable.status = priorInfo.installed ? HubStatus.INSTALLED_PRIOR_LAYER : HubStatus.COMPUTED_PRIOR_LAYER;
                if (priorInfo.installed) {
                    // record symbol info for installed hubs
                    for (int i = 0; i < dispatchTable.slots.length; i++) {
                        HostedDispatchSlot slot = dispatchTable.slots[i];
                        PriorDispatchSlot priorSlot = priorInfo.slots[i];
                        if (priorSlot.status.isCompiled()) {
                            slot.status = SlotResolutionStatus.PRIOR_LAYER;
                            assert !priorSlot.slotSymbolName.equals(PriorDispatchSlot.INVALID_SYMBOL_NAME);
                            slot.symbol = priorSlot.slotSymbolName;
                        }
                    }
                }
            }
        }
    }

    private static String compareMethod(HostedMethod curMethod, PriorDispatchMethod priorMethod) {
        String errorMessage = "";
        int priorId = priorMethod.methodId();
        int curId = curMethod.getWrapped().getId();
        if (priorId != PriorDispatchMethod.UNKNOWN_ID) {
            if (curId != priorId) {
                errorMessage += String.format("mismatch in id %s %s%n", curId, priorId);
            }
        }

        String curSymbol = NativeImage.localSymbolNameForMethod(curMethod);
        String priorSymbol = priorMethod.symbolName;
        if (!curSymbol.equals(priorSymbol)) {
            errorMessage += String.format("mismatch in symbol name %s %s%n", curSymbol, priorSymbol);
        }

        int priorVTableIdx = priorMethod.vtableIndex;
        int curVTableIdx = curMethod.hasVTableIndex() ? curMethod.getVTableIndex() : PriorDispatchMethod.UNKNOWN_VTABLE_IDX;
        if (priorVTableIdx != PriorDispatchMethod.UNKNOWN_VTABLE_IDX && curVTableIdx != PriorDispatchMethod.UNKNOWN_VTABLE_IDX && priorVTableIdx != curVTableIdx) {
            errorMessage += String.format("mismatch in vtable index %s %s%n", curVTableIdx, priorVTableIdx);
        }
        if (!errorMessage.isEmpty()) {
            errorMessage = String.format("Issue while comparing method %s %s%n", curMethod.getQualifiedName(), priorMethod) + errorMessage;
        }

        return errorMessage;
    }

    private static void compareTypeInfo(HostedDispatchTable curInfo, PriorDispatchTable priorInfo) {
        if (!(Options.LogLayeredDispatchTableDiscrepancies.getValue() || Options.ErrorOnLayeredDispatchTableDiscrepancies.getValue())) {
            // it is not necessary to compare type info
            return;
        }

        String errorMessage = "";
        if (curInfo.locallyDeclaredSlots.length == priorInfo.locallyDeclaredSlots.length) {
            for (int i = 0; i < curInfo.locallyDeclaredSlots.length; i++) {
                errorMessage += compareMethod(curInfo.locallyDeclaredSlots[i], priorInfo.locallyDeclaredSlots[i]);
            }
        } else {
            errorMessage += String.format("Mismatch in locally declared slot length %s %s%n", curInfo.locallyDeclaredSlots.length, priorInfo.locallyDeclaredSlots.length);
        }

        if (curInfo.slots.length == priorInfo.slots.length) {
            for (int i = 0; i < curInfo.slots.length; i++) {
                var curSlotInfo = curInfo.slots[i];
                var priorSlotInfo = priorInfo.slots[i];
                compareMethod(curSlotInfo.declaredMethod, priorSlotInfo.declaredMethod);
                if (curSlotInfo.resolvedMethod != null && priorSlotInfo.status.isResolved()) {
                    compareMethod(curSlotInfo.resolvedMethod, priorSlotInfo.resolvedMethod);
                }
            }
        } else {
            errorMessage += String.format("Mismatch in dispatch table slot length %s %s%n", curInfo.slots.length, priorInfo.slots.length);
        }

        if (!errorMessage.isEmpty()) {
            String message = String.format("Issue while comparing dispatch table info: %s and %s%n%s", curInfo, priorInfo, errorMessage);
            if (Options.ErrorOnLayeredDispatchTableDiscrepancies.getValue()) {
                throw VMError.shouldNotReachHere(message);
            }
            if (Options.LogLayeredDispatchTableDiscrepancies.getValue()) {
                System.out.println(message);
            }
        }
    }

    /*
     * Recording a hub was written to the heap
     */
    public void registerWrittenDynamicHub(DynamicHub hub, AnalysisUniverse aUniverse, HostedUniverse hUniverse, Object vTable) {
        AnalysisType aType = ((SVMHost) aUniverse.hostVM()).lookupType(hub);
        HostedType hType = hUniverse.lookup(aType);

        assert hType.getWrapped().isReachable() : "All installed hubs should be reachable " + hType;

        int vtableLength = Array.getLength(vTable);
        if (VTableBuilder.hasEmptyDispatchTable(hType)) {
            assert vtableLength == 0 : hType;
            return;
        }

        var dispatchTable = typeToDispatchTable.get(hType);

        // upgrade status to being installed in current layer
        assert dispatchTable.status == HubStatus.DISPATCH_INFO_CALCULATED || dispatchTable.status == HubStatus.COMPUTED_PRIOR_LAYER : dispatchTable;
        dispatchTable.status = HubStatus.INSTALLED_CURRENT_LAYER;

        assert dispatchTable.slots.length == vtableLength : Assertions.errorMessage(vTable, dispatchTable.slots);

        for (int i = 0; i < vtableLength; i++) {
            MethodPointer methodPointer = (MethodPointer) Array.get(vTable, i);
            var slot = dispatchTable.slots[i];
            var prev = methodPointerToDispatchSlot.put(methodPointer, slot);
            assert prev == null : prev;
        }
    }

    private static String computeUnresolvedMethodSymbol(HostedDispatchSlot slotInfo, Map<ResolvedJavaMethod, String> methodToSymbolMap) {
        /*
         * First try to determine the resolved method. This is useful for deduplication.
         */
        ResolvedJavaMethod resolvedMethod = null;
        if (slotInfo.status == SlotResolutionStatus.NOT_COMPILED) {
            resolvedMethod = OriginalMethodProvider.getOriginalMethod(slotInfo.resolvedMethod);
        } else {
            assert slotInfo.status == SlotResolutionStatus.UNRESOLVED : slotInfo;
            ResolvedJavaType originalType = OriginalClassProvider.getOriginalType(slotInfo.dispatchTable.type);
            ResolvedJavaMethod originalMethod = OriginalMethodProvider.getOriginalMethod(slotInfo.declaredMethod);
            if (originalMethod != null) {
                resolvedMethod = originalType.resolveMethod(originalMethod, originalMethod.getDeclaringClass());
            }
        }

        String unresolvedTableSymbol;
        if (resolvedMethod != null) {
            unresolvedTableSymbol = methodToSymbolMap.computeIfAbsent(resolvedMethod, k -> String.format("%s_unresolvedVTableSym", NativeImage.localSymbolNameForMethod(k)));
        } else {
            unresolvedTableSymbol = SubstrateOptions.ImageSymbolsPrefix.getValue() +
                            String.format("unresolvedVTableSym_typeid%s_slot%s", slotInfo.dispatchTable.type.getWrapped().getId(), slotInfo.slotNum);
        }
        return unresolvedTableSymbol;
    }

    /*
     * Defines symbols for slots which either have yet to have their method compiled or for slots
     * which were defined in a prior layer, but have only had their method target resolved in this
     * layer.
     */
    public void defineDispatchTableSlotSymbols(ObjectFile objectFile, ObjectFile.Section textSection, NativeImageCodeCache codeCache, HostedMetaAccess metaAccess) {
        HostedMethod invalidMethod = metaAccess.lookupJavaMethod(InvalidMethodPointerHandler.INVALID_VTABLE_ENTRY_HANDLER_METHOD);

        Map<String, HostedMethod> resolvedPriorVTableMap = new HashMap<>();
        Set<String> unresolvedVTableSymbolNames = generateUnresolvedSymbolNames ? new HashSet<>() : null;

        Map<ResolvedJavaMethod, String> deduplicatedMethodMap = new HashMap<>();

        /*
         * First calculate symbols for all slots
         */
        for (var slotInfo : methodPointerToDispatchSlot.values()) {
            assert slotInfo.dispatchTable.status == HubStatus.INSTALLED_CURRENT_LAYER;

            if (slotInfo.status == SlotResolutionStatus.COMPUTED) {
                // check if this method is compiled
                slotInfo.status = slotInfo.resolvedMethod.isCompiled() ? SlotResolutionStatus.CURRENT_LAYER : SlotResolutionStatus.NOT_COMPILED;
            }

            String symbol = null;
            if (!slotInfo.status.isCompiled()) {
                assert slotInfo.status == SlotResolutionStatus.UNRESOLVED || slotInfo.status == SlotResolutionStatus.NOT_COMPILED : slotInfo;
                if (generateUnresolvedSymbolNames) {
                    /*
                     * We need to make a symbol for this method which can be resolved in a later
                     * build if the target is compiled.
                     */
                    symbol = computeUnresolvedMethodSymbol(slotInfo, deduplicatedMethodMap);
                    if (unresolvedVTableSymbolNames.add(symbol)) {
                        objectFile.createUndefinedSymbol(symbol, 0, true);
                    }
                }
            } else {
                /*
                 * The resolved method has been compiled so we can link to it.
                 */
                symbol = NativeImage.localSymbolNameForMethod(slotInfo.resolvedMethod);
            }
            slotInfo.symbol = symbol;
        }

        /*
         * Next determine if any vtable symbols defined in prior layers now have a resolved target.
         */
        for (HostedDispatchTable typeInfo : typeToDispatchTable.values()) {
            if (typeInfo.status == HubStatus.INSTALLED_PRIOR_LAYER) {
                for (HostedDispatchSlot slotInfo : typeInfo.slots) {
                    if (slotInfo.status == SlotResolutionStatus.COMPUTED && slotInfo.resolvedMethod.isCompiled()) {
                        /*
                         * Now that this method has been compiled we must define it.
                         */

                        var priorInfo = priorDispatchTables.get(slotInfo.dispatchTable.type.getWrapped().getId());
                        var symName = priorInfo.slots[slotInfo.slotNum].slotSymbolName;
                        var prev = resolvedPriorVTableMap.put(symName, slotInfo.resolvedMethod);
                        /*
                         * All slots with the same symbol name should have the same resolved method.
                         */
                        assert prev == null || prev.equals(slotInfo.resolvedMethod);
                    }
                }
            }
        }

        for (var entry : resolvedPriorVTableMap.entrySet()) {
            String symbol = entry.getKey();
            HostedMethod method = entry.getValue();
            CompilationResult result = codeCache.compilationResultFor(method);

            final int size = result == null ? 0 : result.getTargetCodeSize();
            objectFile.createDefinedSymbol(symbol, textSection, method.getCodeAddressOffset(), size, true, true);
        }

        /*
         * Finally, in the application layer we need to make sure all remaining undefined symbols
         * are defined to be linked to the invalid method handler.
         */
        if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
            priorUnresolvedSymbols.forEach(symbol -> {
                if (!resolvedPriorVTableMap.containsKey(symbol)) {
                    CompilationResult result = codeCache.compilationResultFor(invalidMethod);

                    final int size = result == null ? 0 : result.getTargetCodeSize();
                    objectFile.createDefinedSymbol(symbol, textSection, invalidMethod.getCodeAddressOffset(), size, true, true);
                }
            });
        }
    }

    // GR-58588 use injectedNotCompiled to track status of all MethodPointers
    public String getSymbolName(MethodPointer methodPointer, HostedMethod target, @SuppressWarnings("unused") boolean injectedNotCompiled) {
        var slotInfo = methodPointerToDispatchSlot.get(methodPointer);
        String symbol = NativeImage.localSymbolNameForMethod(target);
        if (slotInfo != null) {
            if (!slotInfo.status.isCompiled()) {
                assert slotInfo.status == SlotResolutionStatus.UNRESOLVED || slotInfo.status == SlotResolutionStatus.NOT_COMPILED : slotInfo;
                if (generateUnresolvedSymbolNames) {
                    assert slotInfo.symbol != null : slotInfo;
                    /*
                     * We have injected a new symbol which can be resolved in a later layer.
                     */
                    symbol = slotInfo.symbol;
                }
            } else {
                assert slotInfo.symbol.equals(symbol);
            }
        }
        return symbol;
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {

        /*
         * To reduce redundancy, we maintain a separate table of all methods we refer to within the
         * dispatch tables.
         */
        Map<HostedMethod, Integer> methodToOffsetMap = new HashMap<>();
        List<Boolean> methodBooleans = new ArrayList<>();
        List<Integer> methodInts = new ArrayList<>();
        List<String> methodStrings = new ArrayList<>();
        Function<HostedMethod, Integer> methodToOffsetMapper = (hMethod) -> {
            int offset;
            if (methodToOffsetMap.containsKey(hMethod)) {
                offset = methodToOffsetMap.get(hMethod);
            } else {
                offset = methodToOffsetMap.size();
                methodToOffsetMap.put(hMethod, offset);
                methodInts.add(hMethod.getWrapped().isTrackedAcrossLayers() ? hMethod.wrapped.getId() : PriorDispatchMethod.UNKNOWN_ID);
                methodInts.add(hMethod.hasVTableIndex() ? hMethod.getVTableIndex() : PriorDispatchMethod.UNKNOWN_VTABLE_IDX);
                methodBooleans.add(virtualCallTargets.contains(hMethod));
                methodStrings.add(NativeImage.localSymbolNameForMethod(hMethod));
                methodStrings.add(generateFormattedMethodName(hMethod.getWrapped()));
            }
            return offset;
        };

        /*
         * Write out dispatch tables for persisted types.
         */
        List<Integer> dispatchTableInts = new ArrayList<>();
        List<Boolean> dispatchTableBooleans = new ArrayList<>();
        List<Integer> dispatchSlotInts = new ArrayList<>();
        List<String> dispatchSlotStrings = new ArrayList<>();
        int nextSlotIdx = 0;
        for (HostedDispatchTable info : typeToDispatchTable.values()) {
            if (!info.type.getWrapped().isTrackedAcrossLayers()) {
                // if a type contains target of a virtual call, then it should be persisted
                assert Arrays.stream(info.locallyDeclaredSlots).noneMatch(virtualCallTargets::contains) : "Type should be persisted: " + info.type;
                continue;
            }

            List<Integer> localTargets = new ArrayList<>();
            List<Integer> slotOffsets = new ArrayList<>();

            for (var hMethod : info.locallyDeclaredSlots) {
                localTargets.add(methodToOffsetMapper.apply(hMethod));
            }

            boolean hubInstalled = info.status == HubStatus.INSTALLED_CURRENT_LAYER;
            if (info.slots != null) {
                for (var slotInfo : info.slots) {
                    var symbolName = hubInstalled ? slotInfo.symbol : PriorDispatchSlot.INVALID_SYMBOL_NAME;
                    dispatchSlotStrings.add(symbolName);
                    dispatchSlotInts.add(slotInfo.slotNum);
                    dispatchSlotInts.add(slotInfo.status.ordinal());
                    dispatchSlotInts.add(methodToOffsetMapper.apply(slotInfo.declaredMethod));
                    if (slotInfo.status.isResolved()) {
                        assert slotInfo.resolvedMethod != null;
                        dispatchSlotInts.add(methodToOffsetMapper.apply(slotInfo.resolvedMethod));
                    } else {
                        dispatchSlotInts.add(PriorDispatchSlot.UNKNOWN_METHOD);
                    }
                    slotOffsets.add(nextSlotIdx++);
                }
            }

            dispatchTableInts.add(info.type.getWrapped().getId());
            dispatchTableInts.add(localTargets.size());
            dispatchTableInts.add(slotOffsets.size());
            dispatchTableInts.addAll(localTargets);
            dispatchTableInts.addAll(slotOffsets);
            dispatchTableBooleans.add(hubInstalled);
        }

        writer.writeIntList("dispatchTableInts", dispatchTableInts);
        writer.writeBoolList("dispatchTableBooleans", dispatchTableBooleans);
        writer.writeIntList("dispatchSlotInts", dispatchSlotInts);
        writer.writeStringList("dispatchSlotStrings", dispatchSlotStrings);
        writer.writeBoolList("methodBooleans", methodBooleans);
        writer.writeIntList("methodInts", methodInts);
        writer.writeStringList("methodStrings", methodStrings);

        return PersistFlags.CREATE;
    }

    @SuppressWarnings("unused")
    public static Object createFromLoader(ImageSingletonLoader loader) {
        List<Integer> dispatchTableInts = loader.readIntList("dispatchTableInts");
        List<Boolean> dispatchTableBooleans = loader.readBoolList("dispatchTableBooleans");
        List<Integer> dispatchSlotInts = loader.readIntList("dispatchSlotInts");
        List<String> dispatchSlotStrings = loader.readStringList("dispatchSlotStrings");
        List<Boolean> methodBooleans = loader.readBoolList("methodBooleans");
        List<Integer> methodInts = loader.readIntList("methodInts");
        List<String> methodStrings = loader.readStringList("methodStrings");

        Set<String> unresolvedSymbols = new HashSet<>();
        Map<Integer, PriorDispatchTable> priorTypes = new HashMap<>();
        Map<Integer, Set<String>> priorVirtualCallTargets = new HashMap<>();

        ArrayList<PriorDispatchMethod> priorMethods = new ArrayList<>();
        var intIterator = methodInts.iterator();
        var stringIterator = methodStrings.iterator();
        var boolIterator = methodBooleans.iterator();
        while (stringIterator.hasNext()) {
            String symbol = stringIterator.next();
            String formattedName = stringIterator.next();
            int methodId = intIterator.next();
            int vtableIndex = intIterator.next();
            boolean isVirtualCallTarget = boolIterator.next();

            var target = new PriorDispatchMethod(methodId, symbol, vtableIndex, formattedName, isVirtualCallTarget);
            priorMethods.add(target);
        }

        ArrayList<PriorDispatchSlot> priorDispatchSlots = new ArrayList<>();
        intIterator = dispatchSlotInts.iterator();
        stringIterator = dispatchSlotStrings.iterator();
        while (stringIterator.hasNext()) {
            String slotSymbolName = stringIterator.next();
            int slotNum = intIterator.next();
            SlotResolutionStatus status = SlotResolutionStatus.values()[intIterator.next()];
            PriorDispatchMethod declaredMethod = priorMethods.get(intIterator.next());
            PriorDispatchMethod resolvedMethod = null;
            int index = intIterator.next();
            if (index != PriorDispatchSlot.UNKNOWN_METHOD) {
                resolvedMethod = priorMethods.get(index);
            }

            var slotInfo = new PriorDispatchSlot(declaredMethod, resolvedMethod, slotNum, status, slotSymbolName);
            priorDispatchSlots.add(slotInfo);
        }

        intIterator = dispatchTableInts.iterator();
        boolIterator = dispatchTableBooleans.iterator();
        while (intIterator.hasNext()) {
            int typeId = intIterator.next();
            boolean hubInstalled = boolIterator.next();
            int locallyDeclaredMethodsSize = intIterator.next();
            int allSlotsSize = intIterator.next();
            PriorDispatchMethod[] locallyDeclaredSlots = new PriorDispatchMethod[locallyDeclaredMethodsSize];
            PriorDispatchSlot[] dispatchTableSlots = new PriorDispatchSlot[allSlotsSize];
            for (int i = 0; i < locallyDeclaredMethodsSize; i++) {
                locallyDeclaredSlots[i] = priorMethods.get(intIterator.next());
            }
            for (int i = 0; i < allSlotsSize; i++) {
                dispatchTableSlots[i] = priorDispatchSlots.get(intIterator.next());
                if (hubInstalled) {
                    var status = dispatchTableSlots[i].status;
                    if (status == SlotResolutionStatus.UNRESOLVED || status == SlotResolutionStatus.NOT_COMPILED) {
                        assert !dispatchTableSlots[i].slotSymbolName.equals(PriorDispatchSlot.INVALID_SYMBOL_NAME);
                        unresolvedSymbols.add(dispatchTableSlots[i].slotSymbolName);
                    }

                }
            }

            var priorDispatchTable = new PriorDispatchTable(typeId, hubInstalled, locallyDeclaredSlots, dispatchTableSlots);
            Object prev = priorTypes.put(typeId, priorDispatchTable);
            assert prev == null : prev;

            Set<String> priorVirtualCallNames = Arrays.stream(locallyDeclaredSlots).filter(PriorDispatchMethod::isVirtualCallTarget).map(PriorDispatchMethod::formattedName)
                            .collect(Collectors.toSet());
            if (!priorVirtualCallNames.isEmpty()) {
                prev = priorVirtualCallTargets.put(typeId, priorVirtualCallNames);
                assert prev == null : prev;
            }
        }

        return new LayeredDispatchTableSupport(priorTypes, unresolvedSymbols, priorVirtualCallTargets);
    }

    enum HubStatus {
        UNINITIALIZED,
        DISPATCH_INFO_CALCULATED,
        COMPUTED_PRIOR_LAYER,
        INSTALLED_PRIOR_LAYER,
        INSTALLED_CURRENT_LAYER,
    }

    static class HostedDispatchTable {
        HostedType type;
        HostedMethod[] locallyDeclaredSlots;
        HostedDispatchSlot[] slots;

        HubStatus status = HubStatus.UNINITIALIZED;
    }

    enum SlotResolutionStatus {
        UNRESOLVED,
        COMPUTED, // the target is known, but it hasn't been compiled yet
        NOT_COMPILED,
        PRIOR_LAYER,
        CURRENT_LAYER;

        public boolean isResolved() {
            return this != UNRESOLVED;
        }

        public boolean isCompiled() {
            return this == PRIOR_LAYER || this == CURRENT_LAYER;
        }
    }

    static class HostedDispatchSlot {
        HostedDispatchTable dispatchTable;
        HostedMethod declaredMethod;
        HostedMethod resolvedMethod;
        int slotNum;
        SlotResolutionStatus status;

        /**
         * Stores the symbol installed for this slot. We must track this when the symbol is resolved
         * in a later layer.
         */
        String symbol;
    }

    /*
     * We track the dispatch table information recorded in prior layered builds so that we can
     * validate it is consistent across layers and also to ensure we resolve all placeholder
     * symbols.
     */

    record PriorDispatchTable(int typeID, boolean installed,
                    PriorDispatchMethod[] locallyDeclaredSlots, PriorDispatchSlot[] slots) {
    }

    record PriorDispatchSlot(
                    PriorDispatchMethod declaredMethod, PriorDispatchMethod resolvedMethod,
                    int slotNum, SlotResolutionStatus status, String slotSymbolName) {
        static final int UNKNOWN_METHOD = -1;
        static final String INVALID_SYMBOL_NAME = "invalid";
    }

    /**
     * Because methods are currently only persisted when a method is implementation invoked, it is
     * not always possible to match on method id. When it is not possible, we store
     * {@link PriorDispatchMethod#UNKNOWN_ID} as the value.
     *
     * GR-59009 will resolve this issue. As part of this we will also remove
     * {@link PriorDispatchMethod#formattedName}.
     */
    record PriorDispatchMethod(int methodId, String symbolName, int vtableIndex, String formattedName, boolean isVirtualCallTarget) {
        static final int UNKNOWN_ID = -1;
        static final int UNKNOWN_VTABLE_IDX = -1;
    }
}

@AutomaticallyRegisteredFeature
final class LayeredDispatchTableSupportFeature implements InternalFeature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingImageLayer();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (ImageLayerBuildingSupport.buildingInitialLayer()) {
            ImageSingletons.add(LayeredDispatchTableSupport.class, new LayeredDispatchTableSupport());
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
            var config = (FeatureImpl.BeforeAnalysisAccessImpl) access;
            var loader = HostedImageLayerBuildingSupport.singleton().getLoader();
            var singleton = LayeredDispatchTableSupport.singleton();
            for (var entry : singleton.priorVirtualCallTargets.entrySet()) {
                AnalysisType type = loader.getAnalysisTypeForBaseLayerId(entry.getKey());
                var methods = type.getOrCalculateOpenTypeWorldDispatchTableMethods();
                var virtualCallTargets = entry.getValue();
                methods.forEach(aMethod -> {
                    if (virtualCallTargets.contains(LayeredDispatchTableSupport.generateFormattedMethodName(aMethod))) {
                        config.registerAsRoot(aMethod, false, "in prior layer dispatch table");
                    }
                });
            }
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        BeforeCompilationAccessImpl access = (BeforeCompilationAccessImpl) a;
        LayeredDispatchTableSupport.singleton().installBuilderModules(access.getImageClassLoader().getBuilderModules());
    }
}
