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
package com.oracle.svm.hosted.meta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.graalvm.collections.Pair;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.InvalidMethodPointerHandler;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.hosted.OpenTypeWorldFeature;
import com.oracle.svm.hosted.imagelayer.LayeredDispatchTableFeature;

import jdk.graal.compiler.debug.Assertions;

public final class VTableBuilder {
    private final HostedUniverse hUniverse;
    private final HostedMetaAccess hMetaAccess;

    private final OpenTypeWorldHubLayoutUtils openHubUtils;

    private VTableBuilder(HostedUniverse hUniverse, HostedMetaAccess hMetaAccess) {
        this.hUniverse = hUniverse;
        this.hMetaAccess = hMetaAccess;
        openHubUtils = SubstrateOptions.useClosedTypeWorldHubLayout() ? null : new OpenTypeWorldHubLayoutUtils(hUniverse);
    }

    public static void buildTables(HostedUniverse hUniverse, HostedMetaAccess hMetaAccess) {
        VTableBuilder builder = new VTableBuilder(hUniverse, hMetaAccess);
        if (SubstrateOptions.useClosedTypeWorldHubLayout()) {
            builder.buildClosedTypeWorldVTables();
            hUniverse.methods.forEach((k, v) -> v.finalizeIndirectCallVTableIndex());
        } else {
            builder.buildOpenTypeWorldDispatchTables();
            hUniverse.methods.forEach((k, v) -> v.finalizeIndirectCallVTableIndex());
            assert builder.verifyOpenTypeWorldDispatchTables();
        }
    }

    private static class OpenTypeWorldHubLayoutUtils {
        private final boolean closedTypeWorld;
        private final boolean registerTrackedTypes;
        private final boolean registerAllTypes;

        OpenTypeWorldHubLayoutUtils(HostedUniverse hUniverse) {
            closedTypeWorld = SubstrateOptions.useClosedTypeWorld();

            registerTrackedTypes = hUniverse.hostVM().enableTrackAcrossLayers();
            registerAllTypes = ImageLayerBuildingSupport.buildingApplicationLayer();
            assert !(registerTrackedTypes && registerAllTypes) : "We expect these flags to be mutually exclusive";
            assert (registerTrackedTypes || registerAllTypes) == ImageLayerBuildingSupport.buildingImageLayer() : "Type information must be registered during layered image builds";
        }

        /**
         * We are allowed to filter vtables as long as we know the type layout does not need to
         * match the layout of another layer.
         *
         * When filtering is allowed, we can filter out methods that we know will be simplified to
         * direct calls (i.e., when at most a single implementation exists for the given target).
         * See generateDispatchTable for the use of this filter.
         */
        private boolean filterVTableMethods(HostedType type) {
            return closedTypeWorld && !type.getWrapped().isInBaseLayer();
        }

        private boolean shouldIncludeType(HostedType type) {
            if (closedTypeWorld) {
                if (type.getWrapped().isInBaseLayer()) {
                    /*
                     * This check will be later removed.
                     *
                     * GR-60010 - We are currently loading base analysis types too late.
                     */
                    return type.getWrapped().isOpenTypeWorldDispatchTableMethodsCalculated();
                }

                /*
                 * When using the closed type world we know calls to unreachable types will be
                 * removed via graph strengthening. It is also always possible to see base layer
                 * types.
                 */
                return type.getWrapped().isReachable() || type.getWrapped().isInBaseLayer();
            } else {
                /*
                 * When using the open type world we are conservative and calculate metadata for all
                 * types seen during analysis.
                 */
                return type.getWrapped().isOpenTypeWorldDispatchTableMethodsCalculated();
            }
        }

        private boolean shouldRegisterType(HostedType type) {
            if (registerAllTypes) {
                return true;
            }
            if (registerTrackedTypes && type.getWrapped().isTrackedAcrossLayers()) {
                return true;
            }
            return false;
        }
    }

    private boolean verifyOpenTypeWorldDispatchTables() {
        HostedMethod invalidVTableEntryHandler = hMetaAccess.lookupJavaMethod(InvalidMethodPointerHandler.INVALID_VTABLE_ENTRY_HANDLER_METHOD);
        for (HostedType type : hUniverse.getTypes()) {
            if (!type.isInstantiated()) {
                /*
                 * Don't check uninstantiated types. They do not have their methods resolved.
                 */
                continue;
            }

            for (int i = 0; i < type.openTypeWorldDispatchTableSlotTargets.length; i++) {
                HostedMethod slotMethod = type.openTypeWorldDispatchTableSlotTargets[i];

                var resolvedMethod = (HostedMethod) type.resolveConcreteMethod(slotMethod, type);
                if (resolvedMethod == null) {
                    resolvedMethod = invalidVTableEntryHandler;
                }
                HostedMethod tableResolvedMethod = type.openTypeWorldDispatchTables[i];
                assert tableResolvedMethod.equals(resolvedMethod) : Assertions.errorMessage(type, slotMethod, tableResolvedMethod, resolvedMethod);

                // retrieve method from open world
                if (slotMethod.getDeclaringClass().isInterface()) {
                    int interfaceTypeID = slotMethod.getDeclaringClass().getTypeID();
                    int[] typeCheckSlots = type.getOpenTypeWorldTypeCheckSlots();
                    boolean found = false;
                    for (int itableIdx = 0; itableIdx < type.getNumInterfaceTypes(); itableIdx++) {
                        if (typeCheckSlots[type.getNumClassTypes() + itableIdx] == interfaceTypeID) {
                            HostedMethod dispatchResult = type.openTypeWorldDispatchTables[type.itableStartingOffsets[itableIdx] + slotMethod.getVTableIndex()];
                            assert dispatchResult.equals(resolvedMethod) : Assertions.errorMessage(slotMethod, dispatchResult, resolvedMethod);
                            found = true;
                            break;
                        }
                    }
                    assert found : Assertions.errorMessage(slotMethod, type, resolvedMethod);
                } else {
                    /*
                     * The class vtable starts at position 0 within the openTypeWorldDispatchTables,
                     * so it is unnecessary to check the itableStartingOffset.
                     */
                    HostedMethod openTypeWorldMethod = type.openTypeWorldDispatchTables[slotMethod.getVTableIndex()];
                    assert openTypeWorldMethod.equals(resolvedMethod) : Assertions.errorMessage(slotMethod, openTypeWorldMethod, resolvedMethod);
                }
            }

        }
        return true;
    }

    private List<HostedMethod> generateITable(HostedType type) {
        return generateDispatchTable(type, List.of());
    }

    /**
     * Tries to find an existing parent slot with an identical signature. If successful, this
     * method's index can be assigned to the same slot and no new dispatch slot is needed.
     */
    private static boolean findAndLinkToParentSlot(HostedMethod hMethod, List<HostedMethod> parentSlots) {
        for (int i = 0; i < parentSlots.size(); i++) {
            HostedMethod candidate = parentSlots.get(i);
            if (OpenTypeWorldFeature.matchingSignature(hMethod, candidate)) {
                assert candidate.computedVTableIndex == i : candidate.computedVTableIndex;
                installVTableIndex(hMethod, candidate.computedVTableIndex);
                return true;
            }
        }
        return false;
    }

    private static void installVTableIndex(HostedMethod hMethod, int index) {
        assert hMethod.computedVTableIndex == HostedMethod.MISSING_VTABLE_IDX : hMethod.computedVTableIndex;
        hMethod.computedVTableIndex = index;
    }

    private List<HostedMethod> generateDispatchTable(HostedType type, List<HostedMethod> parentClassTable) {
        Predicate<HostedMethod> includeMethod;
        if (openHubUtils.filterVTableMethods(type)) {
            // include only methods which will be indirect calls
            includeMethod = m -> {
                assert !m.isConstructor() : Assertions.errorMessage("Constructors should never be in dispatch tables", m);
                if (findAndLinkToParentSlot(m, parentClassTable)) {
                    // a prior slot has been found
                    return false;
                }
                if (m.implementations.length > 1) {
                    return true;
                } else {
                    if (m.wrapped.isVirtualRootMethod()) {
                        return !m.canBeStaticallyBound();
                    } else {
                        return false;
                    }
                }
            };
        } else {
            includeMethod = m -> {
                assert !m.isConstructor() : Assertions.errorMessage("Constructors should never be in dispatch tables", m);
                if (findAndLinkToParentSlot(m, parentClassTable)) {
                    // a prior slot has been found
                    return false;
                }
                /*
                 * We have to use the analysis method's canBeStaticallyBound implementation because
                 * within HostedMethod we sometimes do additional pruning when operating under the
                 * close type world assumption.
                 */
                return !m.getWrapped().canBeStaticallyBound();
            };
        }
        var table = type.getWrapped().getOpenTypeWorldDispatchTableMethods().stream().map(hUniverse::lookup).filter(includeMethod).sorted(HostedUniverse.METHOD_COMPARATOR).toList();

        int index = parentClassTable.size();
        for (HostedMethod typeMethod : table) {
            assert typeMethod.getDeclaringClass().equals(type) : typeMethod;
            installVTableIndex(typeMethod, index);
            index++;
        }

        if (openHubUtils.shouldRegisterType(type)) {
            LayeredDispatchTableFeature.singleton().registerDeclaredDispatchInfo(type, table);
        }

        return table;
    }

    private void generateOpenTypeWorldDispatchTable(HostedInstanceClass type, Map<HostedType, List<HostedMethod>> classTablesMap, HostedMethod invalidDispatchTableEntryHandler) {
        var superClass = type.getSuperclass();
        List<HostedMethod> parentClassTable = superClass == null ? List.of() : classTablesMap.get(superClass);
        List<HostedMethod> classTableWithoutSuper = generateDispatchTable(type, parentClassTable);
        List<HostedMethod> classTableMethods;
        if (!classTableWithoutSuper.isEmpty()) {
            classTableMethods = new ArrayList<>(parentClassTable);
            classTableMethods.addAll(classTableWithoutSuper);
        } else {
            /*
             * If the type doesn't declare any new methods, then we can use the parent's class
             * table.
             */
            classTableMethods = parentClassTable;
        }
        classTablesMap.put(type, classTableMethods);

        if (!type.isAbstract()) {
            // create concrete dispatch classes
            List<HostedMethod> aggregatedTable = new ArrayList<>(classTableMethods);
            HostedType[] interfaces = type.typeCheckInterfaceOrder;
            type.itableStartingOffsets = new int[interfaces.length];
            int currentITableOffset = classTableMethods.size();
            for (int i = 0; i < interfaces.length; i++) {
                HostedType interfaceType = interfaces[i];
                List<HostedMethod> interfaceMethods = classTablesMap.get(interfaceType);

                type.itableStartingOffsets[i] = currentITableOffset;
                aggregatedTable.addAll(interfaceMethods);
                currentITableOffset += interfaceMethods.size();
            }
            type.openTypeWorldDispatchTables = new HostedMethod[aggregatedTable.size()];
            type.openTypeWorldDispatchTableSlotTargets = aggregatedTable.toArray(HostedMethod[]::new);

            boolean[] validTarget = new boolean[aggregatedTable.size()];
            Set<HostedMethod> seenResolvedMethods = SubstrateUtil.assertionsEnabled() ? new HashSet<>() : null;
            for (int i = 0; i < aggregatedTable.size(); i++) {
                HostedMethod method = aggregatedTable.get(i);
                /*
                 * To avoid segfaults when jumping to address 0, all unused dispatch table entries
                 * are filled with a stub that reports a fatal error.
                 */
                HostedMethod targetMethod = invalidDispatchTableEntryHandler;
                if (type.isInstantiated()) {
                    var resolvedMethod = (HostedMethod) type.resolveConcreteMethod(method, type);
                    if (resolvedMethod != null) {
                        targetMethod = resolvedMethod;
                        validTarget[i] = true;
                        if (seenResolvedMethods != null && i < classTableMethods.size()) {
                            /*
                             * Check that each resolved method within the class table is unique
                             */
                            var added = seenResolvedMethods.add(resolvedMethod);
                            assert added : Assertions.errorMessage("Multiple slots with same resolution method", resolvedMethod);
                        }
                    }
                }

                type.openTypeWorldDispatchTables[i] = targetMethod;
            }

            if (openHubUtils.shouldRegisterType(type)) {
                LayeredDispatchTableFeature.singleton().registerNonArrayDispatchTable(type, validTarget);
            }
        }
        if (RuntimeClassLoading.isSupported()) {
            assert !type.isInterface();
            List<HostedMethod> sourceTable;
            if (type.isAbstract()) {
                sourceTable = classTableMethods;
            } else {
                sourceTable = Arrays.asList(type.openTypeWorldDispatchTableSlotTargets);
            }
            type.cremaOpenTypeWorldDispatchTables = new HostedMethod[sourceTable.size()];
            for (int i = 0; i < sourceTable.size(); i++) {
                HostedMethod resultMethod = sourceTable.get(i);
                var resolvedMethod = (HostedMethod) type.resolveConcreteMethod(resultMethod, type);
                if (resolvedMethod != null) {
                    resultMethod = resolvedMethod;
                }
                type.cremaOpenTypeWorldDispatchTables[i] = resultMethod;
            }
        }

        for (HostedType subType : type.subTypes) {
            if (subType instanceof HostedInstanceClass instanceClass && openHubUtils.shouldIncludeType(subType)) {
                generateOpenTypeWorldDispatchTable(instanceClass, classTablesMap, invalidDispatchTableEntryHandler);
            }
        }
    }

    private void buildOpenTypeWorldDispatchTables() {
        /*
         * Map from type to class table (i.e. the type's vtable w/o any appended itables).
         */
        Map<HostedType, List<HostedMethod>> classTablesMap = new HashMap<>();

        for (HostedType type : hUniverse.getTypes()) {
            /*
             * Each interface has its own dispatch table. These can be directly determined via
             * looking at their declared methods.
             */
            if (type.isInterface() && openHubUtils.shouldIncludeType(type)) {
                List<HostedMethod> itable = generateITable(type);
                classTablesMap.put(type, itable);
                if (RuntimeClassLoading.isSupported()) {
                    type.cremaOpenTypeWorldDispatchTables = new HostedMethod[itable.size()];
                    for (int i = 0; i < itable.size(); i++) {
                        type.cremaOpenTypeWorldDispatchTables[i] = itable.get(i);
                    }
                }
            }
        }

        HostedMethod invalidDispatchTableEntryHandler = hMetaAccess.lookupJavaMethod(InvalidMethodPointerHandler.INVALID_VTABLE_ENTRY_HANDLER_METHOD);
        generateOpenTypeWorldDispatchTable((HostedInstanceClass) hUniverse.objectType(), classTablesMap, invalidDispatchTableEntryHandler);

        int[] emptyITableOffsets = new int[0];
        var objectType = hUniverse.getObjectClass();
        for (HostedType type : hUniverse.getTypes()) {
            if (type.isArray() && openHubUtils.shouldIncludeType(type)) {
                type.openTypeWorldDispatchTables = objectType.openTypeWorldDispatchTables;
                type.cremaOpenTypeWorldDispatchTables = objectType.cremaOpenTypeWorldDispatchTables;
                type.openTypeWorldDispatchTableSlotTargets = objectType.openTypeWorldDispatchTableSlotTargets;
                type.itableStartingOffsets = objectType.itableStartingOffsets;
                if (openHubUtils.shouldRegisterType(type)) {
                    LayeredDispatchTableFeature.singleton().registerArrayDispatchTable(type, objectType);
                }
            }
            if (type.openTypeWorldDispatchTables == null) {
                assert !openHubUtils.shouldIncludeType(type) || hasEmptyDispatchTable(type) : type;
                type.openTypeWorldDispatchTables = HostedMethod.EMPTY_ARRAY;
                type.openTypeWorldDispatchTableSlotTargets = HostedMethod.EMPTY_ARRAY;
                type.itableStartingOffsets = emptyITableOffsets;
            }
            if (RuntimeClassLoading.isSupported()) {
                if (type.isPrimitive()) {
                    type.cremaOpenTypeWorldDispatchTables = HostedMethod.EMPTY_ARRAY;
                }
                assert type.cremaOpenTypeWorldDispatchTables != null : "No dispatch tables for type " + type;
            }
        }
    }

    public static boolean hasEmptyDispatchTable(HostedType type) {
        /*
         * Note that array types are by definition abstract, i.e., if type.isArray() is true then
         * type.isAbstract() is true.
         */
        return (type.isInterface() || type.isPrimitive() || type.isAbstract()) && !type.isArray();
    }

    private void buildClosedTypeWorldVTables() {
        /*
         * We want to pack the vtables as tight as possible, i.e., we want to avoid filler slots as
         * much as possible. Filler slots are unavoidable because we use the vtable also for
         * interface calls, i.e., an interface method needs a vtable index that is filled for all
         * classes that implement that interface.
         *
         * Note that because of interface methods the same implementation method can be registered
         * multiple times in the same vtable, with a different index used by different interface
         * methods.
         *
         * The optimization goal is to reduce the overall number of vtable slots. To achieve a good
         * result, we process types in three steps: 1) java.lang.Object, 2) interfaces, 3) classes.
         */

        /*
         * The mutable vtables while this algorithm is running. Contains an ArrayList for each type,
         * which is in the end converted to the vtable array.
         */
        Map<HostedType, ArrayList<HostedMethod>> vtablesMap = new HashMap<>();

        /*
         * A bit set of occupied vtable slots for each type.
         */

        Map<HostedType, BitSet> usedSlotsMap = new HashMap<>();
        /*
         * The set of vtable slots used for this method. Because of interfaces, one method can have
         * multiple vtable slots. The assignment algorithm uses this table to find out if a suitable
         * vtable index already exists for a method.
         */
        Map<HostedMethod, Set<Integer>> vtablesSlots = new HashMap<>();

        for (HostedType type : hUniverse.getTypes()) {
            vtablesMap.put(type, new ArrayList<>());
            BitSet initialBitSet = new BitSet();
            usedSlotsMap.put(type, initialBitSet);
        }

        /*
         * 1) Process java.lang.Object first because the methods defined there (equals, hashCode,
         * toString, clone) are in every vtable. We must not have filler slots before these methods.
         */
        HostedInstanceClass objectClass = hUniverse.getObjectClass();
        assignImplementations(objectClass, vtablesMap, usedSlotsMap, vtablesSlots);

        /*
         * 2) Process interfaces. Interface methods have higher constraints on vtable slots because
         * the same slots need to be used in all implementation classes, which can be spread out
         * across the type hierarchy. We assign an importance level to each interface and then sort
         * by that number, to further reduce the filler slots.
         */
        List<Pair<HostedType, Integer>> interfaces = new ArrayList<>();
        for (HostedType type : hUniverse.getTypes()) {
            if (type.isInterface()) {
                /*
                 * We use the number of subtypes as the importance for an interface: If an interface
                 * is implemented often, then it can produce more unused filler slots than an
                 * interface implemented rarely. We do not multiply with the number of methods that
                 * the interface implements: there are usually no filler slots in between methods of
                 * an interface, i.e., an interface that declares many methods does not lead to more
                 * filler slots than an interface that defines only one method.
                 */
                int importance = collectSubtypes(type, new HashSet<>()).size();
                interfaces.add(Pair.create(type, importance));
            }
        }
        interfaces.sort((pair1, pair2) -> pair2.getRight() - pair1.getRight());
        for (Pair<HostedType, Integer> pair : interfaces) {
            assignImplementations(pair.getLeft(), vtablesMap, usedSlotsMap, vtablesSlots);
        }

        /*
         * 3) Process all implementation classes, starting with java.lang.Object and going
         * depth-first down the tree.
         */
        buildVTable(objectClass, vtablesMap, usedSlotsMap, vtablesSlots);

        /*
         * To avoid segfaults when jumping to address 0, all unused vtable entries are filled with a
         * stub that reports a fatal error.
         */
        HostedMethod invalidVTableEntryHandler = hMetaAccess.lookupJavaMethod(InvalidMethodPointerHandler.INVALID_VTABLE_ENTRY_HANDLER_METHOD);

        for (HostedType type : hUniverse.getTypes()) {
            if (type.isArray()) {
                type.closedTypeWorldVTable = objectClass.closedTypeWorldVTable;
            }
            if (type.closedTypeWorldVTable == null || type.closedTypeWorldVTable.length == 0) {
                assert type.isInterface() || type.isPrimitive() || type.closedTypeWorldVTable.length == 0;
                type.closedTypeWorldVTable = HostedMethod.EMPTY_ARRAY;
            }

            HostedMethod[] vtableArray = type.closedTypeWorldVTable;
            for (int i = 0; i < vtableArray.length; i++) {
                if (vtableArray[i] == null) {
                    vtableArray[i] = invalidVTableEntryHandler;
                }
            }
        }

        if (SubstrateUtil.assertionsEnabled()) {
            /* Check that all vtable entries are the correctly resolved methods. */
            for (HostedType type : hUniverse.getTypes()) {
                for (HostedMethod m : type.closedTypeWorldVTable) {
                    assert m.equals(invalidVTableEntryHandler) || m.equals(hUniverse.lookup(type.wrapped.resolveConcreteMethod(m.wrapped, type.wrapped)));
                }
            }
        }
    }

    /** Collects all subtypes of the provided type in the provided set. */
    private static Set<HostedType> collectSubtypes(HostedType type, Set<HostedType> allSubtypes) {
        if (allSubtypes.add(type)) {
            for (HostedType subtype : type.subTypes) {
                collectSubtypes(subtype, allSubtypes);
            }
        }
        return allSubtypes;
    }

    private void buildVTable(HostedClass clazz, Map<HostedType, ArrayList<HostedMethod>> vtablesMap, Map<HostedType, BitSet> usedSlotsMap, Map<HostedMethod, Set<Integer>> vtablesSlots) {
        assignImplementations(clazz, vtablesMap, usedSlotsMap, vtablesSlots);

        ArrayList<HostedMethod> vtable = vtablesMap.get(clazz);
        HostedMethod[] vtableArray = vtable.toArray(new HostedMethod[vtable.size()]);
        assert vtableArray.length == 0 || vtableArray[vtableArray.length - 1] != null : "Unnecessary entry at end of vtable";
        clazz.closedTypeWorldVTable = vtableArray;

        for (HostedType subClass : clazz.subTypes) {
            if (!subClass.isInterface() && !subClass.isArray()) {
                buildVTable((HostedClass) subClass, vtablesMap, usedSlotsMap, vtablesSlots);
            }
        }
    }

    private void assignImplementations(HostedType type, Map<HostedType, ArrayList<HostedMethod>> vtablesMap, Map<HostedType, BitSet> usedSlotsMap, Map<HostedMethod, Set<Integer>> vtablesSlots) {
        /*
         * Methods with 1 implementation do not need a vtable because invokes can be done as direct
         * calls without the need for a vtable. Methods with 0 implementations are unreachable.
         *
         * However, virtual roots (even those with 0 implementations) that cannot be statically
         * bound always need a vtable entry. This is because the vtable is used to invoke these
         * methods via reflection and/or jni.
         */
        Predicate<HostedMethod> vtableEntryRequired = (hMethod) -> {
            if (hMethod.implementations.length > 1) {
                return true;
            } else {
                if (hMethod.wrapped.isVirtualRootMethod()) {
                    return !hMethod.canBeStaticallyBound();
                } else {
                    return false;
                }
            }
        };

        for (HostedMethod method : type.getAllDeclaredMethods()) {
            /* We only need to look at methods that the static analysis registered as invoked. */
            if (method.wrapped.isInvoked() || method.wrapped.isImplementationInvoked()) {
                if (vtableEntryRequired.test(method)) {
                    assert !method.isConstructor() : Assertions.errorMessage("Constructors should never be in vtables", method);
                    /*
                     * Find a suitable vtable slot for the method, taking the existing vtable
                     * assignments into account.
                     */
                    int slot = findSlot(method, vtablesMap, usedSlotsMap, vtablesSlots);
                    method.computedVTableIndex = slot;

                    /* Assign the vtable slot for the type and all subtypes. */
                    assignImplementations(method.getDeclaringClass(), method, slot, vtablesMap);
                }
            }
        }
    }

    /**
     * Assign the vtable slot to the correct resolved method for all subtypes.
     */
    private void assignImplementations(HostedType type, HostedMethod method, int slot, Map<HostedType, ArrayList<HostedMethod>> vtablesMap) {
        if (type.wrapped.isInstantiated()) {
            assert (type.isInstanceClass() && !type.isAbstract()) || type.isArray();

            HostedMethod resolvedMethod = resolveMethod(type, method);
            if (resolvedMethod != null) {
                ArrayList<HostedMethod> vtable = vtablesMap.get(type);
                if (slot < vtable.size() && vtable.get(slot) != null) {
                    /* We already have a vtable entry from a supertype. Check that it is correct. */
                    assert vtable.get(slot).equals(resolvedMethod);
                } else {
                    resize(vtable, slot + 1);
                    assert vtable.get(slot) == null;
                    vtable.set(slot, resolvedMethod);
                }
                resolvedMethod.computedVTableIndex = slot;
            }
        }

        for (HostedType subtype : type.subTypes) {
            if (!subtype.isArray()) {
                assignImplementations(subtype, method, slot, vtablesMap);
            }
        }
    }

    private HostedMethod resolveMethod(HostedType type, HostedMethod method) {
        AnalysisMethod resolved = type.wrapped.resolveConcreteMethod(method.wrapped, type.wrapped);
        if (resolved == null || !resolved.isImplementationInvoked()) {
            return null;
        } else {
            assert !resolved.isAbstract();
            return hUniverse.lookup(resolved);
        }
    }

    private static void resize(ArrayList<?> list, int minSize) {
        list.ensureCapacity(minSize);
        while (list.size() < minSize) {
            list.add(null);
        }
    }

    private int findSlot(HostedMethod method, Map<HostedType, ArrayList<HostedMethod>> vtablesMap, Map<HostedType, BitSet> usedSlotsMap, Map<HostedMethod, Set<Integer>> vtablesSlots) {
        /*
         * Check if all implementation methods already have a common slot assigned. Each
         * implementation method can have multiple slots because of interfaces. We compute the
         * intersection of the slot sets for all implementation methods.
         */
        if (method.implementations.length > 0) {
            Set<Integer> resultSlots = vtablesSlots.get(method.implementations[0]);
            for (HostedMethod impl : method.implementations) {
                Set<Integer> implSlots = vtablesSlots.get(impl);
                if (implSlots == null) {
                    resultSlots = null;
                    break;
                }
                resultSlots.retainAll(implSlots);
            }
            if (resultSlots != null && !resultSlots.isEmpty()) {
                /*
                 * All implementations already have the same vtable slot assigned, so we can re-use
                 * that. If we have multiple candidates, we use the slot with the lowest number.
                 */
                int resultSlot = Integer.MAX_VALUE;
                for (int slot : resultSlots) {
                    resultSlot = Math.min(resultSlot, slot);
                }
                return resultSlot;
            }
        }
        /*
         * No slot found, we need to compute a new one. Check the whole subtype hierarchy for
         * constraints using bitset union, and then use the lowest slot number that is available in
         * all subtypes.
         */
        BitSet usedSlots = new BitSet();
        collectUsedSlots(method.getDeclaringClass(), usedSlots, usedSlotsMap);
        for (HostedMethod impl : method.implementations) {
            collectUsedSlots(impl.getDeclaringClass(), usedSlots, usedSlotsMap);
        }

        /*
         * The new slot number is the lowest slot number not occupied by any subtype, i.e., the
         * lowest index not set in the union bitset.
         */
        int resultSlot = usedSlots.nextClearBit(0);

        markSlotAsUsed(resultSlot, method.getDeclaringClass(), vtablesMap, usedSlotsMap);
        for (HostedMethod impl : method.implementations) {
            markSlotAsUsed(resultSlot, impl.getDeclaringClass(), vtablesMap, usedSlotsMap);

            vtablesSlots.computeIfAbsent(impl, k -> new HashSet<>()).add(resultSlot);
        }

        return resultSlot;
    }

    private void collectUsedSlots(HostedType type, BitSet usedSlots, Map<HostedType, BitSet> usedSlotsMap) {
        usedSlots.or(usedSlotsMap.get(type));
        for (HostedType sub : type.subTypes) {
            if (!sub.isArray()) {
                collectUsedSlots(sub, usedSlots, usedSlotsMap);
            }
        }
    }

    private void markSlotAsUsed(int resultSlot, HostedType type, Map<HostedType, ArrayList<HostedMethod>> vtablesMap, Map<HostedType, BitSet> usedSlotsMap) {
        assert resultSlot >= vtablesMap.get(type).size() || vtablesMap.get(type).get(resultSlot) == null;

        usedSlotsMap.get(type).set(resultSlot);
        for (HostedType sub : type.subTypes) {
            if (!sub.isArray()) {
                markSlotAsUsed(resultSlot, sub, vtablesMap, usedSlotsMap);
            }
        }
    }
}
