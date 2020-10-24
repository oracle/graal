/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.compiler.core.common.calc.UnsignedMath;

import com.oracle.svm.core.util.VMError;

/**
 * Native image type checks are performed by a range check (see
 * {@link com.oracle.svm.core.graal.snippets.TypeSnippets} for specific implementation details).
 *
 * <p>
 * For the class dependency hierarchy, assigning each type a unique id that can be used within a
 * range check can be accomplished by assigning ids via a preorder traversal of the hierarchy graph.
 * However, because classes/interfaces may implement multiple interfaces, there might not be a
 * single graph traversal which accurately encapsulates all needed range checks. Therefore, instead
 * of a type having a single id, each type has an array of ids, where each index represents the
 * type's id in the specific subset of range checks the index covers.
 *
 * <p>
 * In our implementation, we separately handle type checks for class and interface types: class
 * checks are assigned via a preorder graph traversal, while interface checks are assigned by
 * finding maximal subsets of the required range checks which satisfy the consecutive ones property
 * (C1P).
 *
 * <p>
 * Given a matrix of boolean values, the consecutive ones property holds if the columns of the
 * matrix can be reordered so that, within each row, all of the set columns are contiguous. When
 * mapping type checks to a boolean matrix, the columns/rows are the types against which the check
 * will be performed. An row-column entry is true if row.isAssignableFrom(column) should be true. If
 * an ordering can be found which ensures all set columns are contiguous, then it is possible to
 * assign ids in a order which satisfies all encapsulated range checks.
 *
 * <p>
 * For determining whether a given subset of the range checks satisfies C1P, we use the algorithm
 * described in "A Simple Test for the Consecutive Ones Property" by Wen-Lain Hsu. In this work, the
 * author is able to simplify the process of determining whether C1P is satisfied by determining
 * which rows <i>strictly overlap</i>. Two rows strictly overlap iff:
 * <ol>
 * <li>There is at least one matching column set in both rows</li>
 * <li>One row is not a subset of the other</li>
 * </ol>
 *
 * <p>
 * Identifying strictly overlapping relations allows the C1P problem to be decomposed and solved in
 * an incremental manner.
 */
public class TypeCheckBuilder {
    private static final int SLOT_CAPACITY = 1 << 16;

    private final HostedType objectType;
    private final List<HostedType> allTypes;

    /** We only assign type ids to Types which are reachable {@link #shouldIncludeType}. */
    private Set<HostedType> allIncludedTypes;

    /**
     * Within the type graph, roots are types without a super type (i.e. {@link Object} and
     * primitives).
     */
    private List<HostedType> allIncludedRoots;

    /**
     * All included types in sorted order based on the type's height within the type graph.
     */
    private List<HostedType> heightOrderedTypes;

    private int numTypeCheckSlots = -1;

    /**
     * Map created to describe the type hierarchy graph.
     */
    private Map<HostedType, List<HostedType>> subtypeMap = new HashMap<>();

    public TypeCheckBuilder(List<HostedType> types, HostedType objectType) {
        this.allTypes = types;
        this.objectType = objectType;
    }

    public int getNumTypeCheckSlots() {
        assert numTypeCheckSlots != -1;
        return numTypeCheckSlots;
    }

    /**
     * Only need to calculate type check ids for reachable types.
     */
    private static boolean shouldIncludeType(HostedType type) {
        return type.getWrapped().isReachable();
    }

    /**
     * For type checking purposes, arrays with an interface element type should also be considered
     * as interfaces.
     */
    private static boolean isInterface(HostedType type) {
        return type.isInterface() || (type.isArray() && type.getBaseType().isInterface());
    }

    /**
     * Returns the short equivalent of the integer value while ensuring the value does not exceed
     * the slot capacity.
     */
    private static short getShortValue(int intValue) {
        assert intValue < (1 << 16);
        return (short) intValue;
    }

    /**
     * Calculates all of the needed type check id information and stores it in the HostedTypes.
     */
    public boolean calculateIDs() {
        computeMetadata();
        ClassIDBuilder classBuilder = new ClassIDBuilder(objectType, allIncludedRoots, heightOrderedTypes, subtypeMap);
        classBuilder.computeSlots();
        InterfaceIDBuilder interfaceBuilder = new InterfaceIDBuilder(classBuilder.numClassSlots, heightOrderedTypes, subtypeMap);
        interfaceBuilder.computeSlots();
        generateTypeCheckSlots(classBuilder, interfaceBuilder);
        assert TypeCheckValidator.compareTypeIDResults(heightOrderedTypes);
        return true;
    }

    private void computeMetadata() {
        allIncludedTypes = allTypes.stream().filter(t -> shouldIncludeType(t)).collect(Collectors.toSet());
        computeSubtypeInformation();

        Map<HostedType, Set<HostedType>> parentMap = new HashMap<>();
        for (HostedType type : allIncludedTypes) {
            for (HostedType subtype : subtypeMap.get(type)) {
                assert shouldIncludeType(subtype);
                parentMap.computeIfAbsent(subtype, k -> new HashSet<>()).add(type);
            }
        }
        allIncludedRoots = allIncludedTypes.stream().filter(t -> !parentMap.containsKey(t)).collect(Collectors.toList());
        heightOrderedTypes = generateHeightOrder(allIncludedRoots);
    }

    /**
     * Calculating a sorted list based on the height of each node. This allows one to create the
     * interface graph and compute all class descendants in one iteration of the nodes.
     */
    private List<HostedType> generateHeightOrder(List<HostedType> roots) {

        /* Set initial height of all nodes to an impossible height */
        Map<HostedType, Integer> heightMap = new HashMap<>();
        allIncludedTypes.forEach(t -> heightMap.put(t, Integer.MIN_VALUE));

        /* Find the height of each tree. */
        for (HostedType root : roots) {
            generateHeightOrderHelper(0, root, heightMap);
        }

        /* Now create a sorted array from this information. */
        return allIncludedTypes.stream().sorted(Comparator.comparingInt(heightMap::get)).collect(Collectors.toList());
    }

    /**
     * Helper method to assist with determining the height of each node.
     */
    private void generateHeightOrderHelper(int depth, HostedType node, Map<HostedType, Integer> heightMap) {
        assert shouldIncludeType(node);
        heightMap.compute(node, (k, currentHeight) -> Integer.max(depth, currentHeight));

        for (HostedType subtype : subtypeMap.get(node)) {
            generateHeightOrderHelper(depth + 1, subtype, heightMap);
        }
    }

    private void computeSubtypeInformation() {
        /*
         * We cannot use the sub-type information from the HostType because there are some minor
         * differences regarding array types. Therefore we build the sub-type information from
         * scratch.
         */
        Map<HostedType, Set<HostedType>> allTypeCheckSubTypes = new HashMap<>();
        allIncludedTypes.stream().forEach(t -> allTypeCheckSubTypes.put(t, new HashSet<>()));

        Set<HostedType> descendantsToCompute = new HashSet<>();
        for (HostedType type : allIncludedTypes) {

            if (type.getSuperclass() != null) {
                HostedType superClass;
                if (!type.isArray()) {
                    /* Non-arrays get their normal superclass. */
                    superClass = type.getSuperclass();
                } else {
                    /*
                     * For arrays, the superclass is always Object, but for type checking purposes
                     * if is useful to create a different graph.
                     */
                    HostedType baseType = type.getBaseType();
                    int dim = type.getArrayDimension();
                    assert dim >= 1;
                    if (baseType.isInterface()) {
                        /* Want object of appropriate depth. */
                        superClass = getHighestDimArrayType(objectType, dim);
                        /* For interface arrays have to compute all possible descendants. */
                        descendantsToCompute.add(baseType);
                    } else if (baseType.isPrimitive()) {
                        /* Superclass should be object of one less dimension. */
                        superClass = getHighestDimArrayType(objectType, dim - 1);
                    } else {
                        // is an instance class
                        HostedType baseSuperClass = baseType.getSuperclass();
                        if (baseSuperClass == null) {
                            /*
                             * This means this in an object array. In this case its parent should be
                             * the object type of one less dimension.
                             */
                            superClass = getHighestDimArrayType(objectType, dim - 1);
                        } else {
                            /* Otherwise make the super class the equivalent base superclass. */
                            superClass = baseSuperClass.getArrayClass(dim);
                            while (!isTypeIncluded(superClass)) {
                                /*
                                 * Not all array type check supertypes are reachable. In this case,
                                 * make its superclass the first reachable super class.
                                 */
                                baseSuperClass = baseSuperClass.getSuperclass();
                                if (baseSuperClass == null) {
                                    /* means object type of the array size wasn't reachable */
                                    superClass = getHighestDimArrayType(objectType, dim - 1);
                                } else {
                                    superClass = baseSuperClass.getArrayClass(dim);
                                }
                            }
                        }
                    }
                }

                allTypeCheckSubTypes.get(superClass).add(type);
            }

            if (type.isInterface()) {
                assert type.getSuperclass() == null;
                allTypeCheckSubTypes.get(objectType).add(type);
            }

            for (HostedInterface interfaceType : type.getInterfaces()) {
                if (!type.isArray()) {
                    allTypeCheckSubTypes.get(interfaceType).add(type);
                } else {
                    /*
                     * For arrays, the two implemented interfaces are Serializable and Cloneable.
                     * For these, the implemented interface should be of one less dimension.
                     */
                    int dim = type.getArrayDimension();
                    HostedType baseType = interfaceType.getBaseType();
                    HostedType arrayInterfaceType = getHighestDimArrayType(baseType, dim - 1);
                    allTypeCheckSubTypes.get(arrayInterfaceType).add(type);
                }
            }

        }

        Map<HostedType, Set<HostedType>> descendantsMap = computeNonArrayDescendants(descendantsToCompute);
        for (HostedType type : allIncludedTypes) {
            Set<HostedType> typeCheckSubTypeSet = allTypeCheckSubTypes.get(type);
            if (type.isArray() && type.getBaseType().isInterface()) {
                /*
                 * For array interfaces, also adding as subtypes all arrays of the same dimension
                 * which implement this interfaces.
                 */
                int dim = type.getArrayDimension();
                assert dim >= 1;
                Set<HostedType> descendants = descendantsMap.get(type.getBaseType());
                for (HostedType descendant : descendants) {
                    assert !descendant.isArray();
                    HostedType arrayDescendant = descendant.getArrayClass(dim);
                    if (isTypeIncluded(arrayDescendant)) {
                        typeCheckSubTypeSet.add(arrayDescendant);
                    }
                }
            }
            List<HostedType> typeCheckSubTypes = typeCheckSubTypeSet.stream().sorted().collect(Collectors.toList());
            subtypeMap.put(type, typeCheckSubTypes);
        }
    }

    /**
     * Checks whether the given type is present.
     */
    private boolean isTypeIncluded(HostedType type) {
        return type != null && allIncludedTypes.contains(type);
    }

    /**
     * Retrieves the highest dimensioned array for the provided type in the range [0, dimMax].
     */
    private HostedType getHighestDimArrayType(HostedType type, int dimMax) {
        assert type != null;
        int dim = dimMax;
        HostedType result;
        do {
            result = type.getArrayClass(dim);
            dim--;
        } while (!isTypeIncluded(result));

        return result;
    }

    /**
     * Computes the non array descendant set for each of the provided types.
     */
    private Map<HostedType, Set<HostedType>> computeNonArrayDescendants(Set<HostedType> types) {
        Map<HostedType, Set<HostedType>> descendantsMap = new HashMap<>();
        for (HostedType type : types) {
            computeNonArrayDescendantsHelper(type, descendantsMap);
        }

        return descendantsMap;
    }

    private void computeNonArrayDescendantsHelper(HostedType type, Map<HostedType, Set<HostedType>> descendantsMap) {
        if (descendantsMap.containsKey(type)) {
            // already computed this map
            return;
        }
        Set<HostedType> descendants = new HashSet<>();
        for (HostedType child : type.subTypes) {
            if (child.isArray()) {
                continue; // only care about non-array subtypes
            }
            descendants.add(child);
            computeNonArrayDescendantsHelper(child, descendantsMap);
            descendants.addAll(descendantsMap.get(child));
        }
        descendantsMap.put(type, descendants);

    }

    /**
     * Combines the class and interface slots array into one array of shorts and sets this
     * information in the hosted type.
     */
    private void generateTypeCheckSlots(ClassIDBuilder classBuilder, InterfaceIDBuilder interfaceBuilder) {
        int numClassSlots = classBuilder.numClassSlots;
        numTypeCheckSlots = numClassSlots + interfaceBuilder.numInterfaceSlots;
        int numSlots = getNumTypeCheckSlots();
        for (HostedType type : allIncludedTypes) {
            short[] typeCheckSlots = new short[numSlots];
            int[] slots = classBuilder.classSlotIDMap.get(type);
            for (int i = 0; i < slots.length; i++) {
                typeCheckSlots[i] = getShortValue(slots[i]);
                assert typeCheckSlots[i] < SLOT_CAPACITY;
            }
            slots = interfaceBuilder.interfaceSlotIDMap.get(type);
            if (slots != null) {
                for (int i = 0; i < slots.length; i++) {
                    typeCheckSlots[numClassSlots + i] = getShortValue(slots[i]);
                    assert typeCheckSlots[numClassSlots + i] < SLOT_CAPACITY;
                }
            }

            type.setTypeCheckSlots(typeCheckSlots);
        }

    }

    /**
     * Contains all logic needed to assign the class slot ids.
     */
    private static final class ClassIDBuilder {
        final HostedType objectType;
        final List<HostedType> allIncludedRoots;
        final List<HostedType> heightOrderedTypes;
        final Map<HostedType, List<HostedType>> subtypeMap;

        final Map<HostedType, int[]> classSlotIDMap = new HashMap<>();
        int numClassSlots = -1;

        private static final class TypeState {
            final int reservedID;
            final int slotNum;
            final int assignedID;
            final int maxSubtypeID;

            private TypeState(int reservedID, int slotNum, int assignedID, int maxSubtypeID) {
                this.reservedID = reservedID;
                this.slotNum = slotNum;
                this.assignedID = assignedID;
                this.maxSubtypeID = maxSubtypeID;
            }
        }

        ClassIDBuilder(HostedType objectType, List<HostedType> allIncludedRoots, List<HostedType> heightOrderedTypes, Map<HostedType, List<HostedType>> subtypeMap) {
            this.objectType = objectType;
            this.heightOrderedTypes = heightOrderedTypes;
            this.allIncludedRoots = allIncludedRoots;
            this.subtypeMap = subtypeMap;
        }

        void computeSlots() {
            Map<HostedType, Integer> numClassDescendantsMap = computeNumClassDescendants();
            calculateIDs(numClassDescendantsMap);
        }

        Map<HostedType, Integer> computeNumClassDescendants() {
            Map<HostedType, Integer> numClassDescendantsMap = new HashMap<>();
            for (int i = heightOrderedTypes.size() - 1; i >= 0; i--) {
                HostedType type = heightOrderedTypes.get(i);
                if (isInterface(type)) {
                    continue;
                }
                int numDescendants = 0;
                for (HostedType child : subtypeMap.get(type)) {
                    if (isInterface(child)) {
                        continue;
                    }
                    /* Adding child and its descendants. */
                    numDescendants += 1 + numClassDescendantsMap.get(child);
                }
                numClassDescendantsMap.put(type, numDescendants);
            }
            return numClassDescendantsMap;
        }

        /**
         * This method calculates the information needed to complete a type check against a
         * non-interface type. Due to Java's single inheritance property, the superclass type
         * hierarchy forms a tree. As pointed out in "Determining type, part, color and time
         * relationships" by Schubert et al., by assigning IDs via a preorder graph traversal, all
         * of a class type's subtype IDs are grouped together.
         * <p>
         * In our algorithm, in order to guarantee ID information can fit into two bytes, the type
         * ids are spread out into multiple slots when the two byte capacity is exceeded. To do so,
         * the concept of a reservedID is introduced. ReservedIDs are assigned backwards from the
         * slot's capacity and are used to guarantee subtyping works correct when a type's subtypes
         * will overfill the current slot.
         */
        void calculateIDs(Map<HostedType, Integer> numClassDescendantsMap) {
            ArrayList<Integer> currentIDs = new ArrayList<>();
            ArrayList<Integer> numReservedIDs = new ArrayList<>();
            currentIDs.add(0);
            numReservedIDs.add(0);
            for (HostedType root : allIncludedRoots) {
                assignID(root, numClassDescendantsMap, currentIDs, numReservedIDs);
            }

            /* Recording the number of slots reserved for class IDs. */
            assert numClassSlots == -1;
            numClassSlots = currentIDs.size();

            /* Setting class slot for interfaces to be the same as the object type. */
            for (HostedType type : heightOrderedTypes) {
                if (isInterface(type)) {
                    int dim = type.getArrayDimension();
                    assert !classSlotIDMap.containsKey(type);
                    classSlotIDMap.put(type, classSlotIDMap.get(objectType.getArrayClass(dim)));
                }
            }
        }

        /**
         * This method assigns ids to class types. Interfaces are performed using the information
         * calculated in {@link InterfaceIDBuilder}.
         */
        void assignID(HostedType type, Map<HostedType, Integer> numClassDescendantsMap, ArrayList<Integer> currentIDs, ArrayList<Integer> numReservedIDs) {
            assert shouldIncludeType(type) && !isInterface(type);

            int numClassDescendants = numClassDescendantsMap.get(type);
            TypeState state = generateTypeState(numClassDescendants, currentIDs, numReservedIDs);
            int reservedID = state.reservedID;
            int slotNum = state.slotNum;
            int assignedID = state.assignedID;
            int maxSubtypeID = state.maxSubtypeID;

            assert !classSlotIDMap.containsKey(type);
            classSlotIDMap.put(type, currentIDs.stream().mapToInt(n -> n).toArray());

            /* Now assigning IDs to children. */
            for (HostedType subtype : subtypeMap.get(type)) {
                if (isInterface(subtype)) {

                    /*
                     * Only iterate through the class hierarchy.
                     */
                    continue;
                }
                assignID(subtype, numClassDescendantsMap, currentIDs, numReservedIDs);

                assert currentIDs.get(slotNum) >= assignedID; // IDs should always be increasing.
            }

            /* Validating calculation of maxSubtypeID. */
            assert currentIDs.get(slotNum) == maxSubtypeID;

            /* Record type's slot and range. */
            type.setTypeCheckSlot(getShortValue(slotNum));
            type.setTypeCheckRange(getShortValue(assignedID), getShortValue(maxSubtypeID - assignedID + 1));
            if (reservedID != 0) {
                /* Must distinguish subsequent ID assignments from this type. */
                assert numReservedIDs.get(slotNum) == reservedID;
                int newNumReservedIDs = reservedID - 1;
                numReservedIDs.set(slotNum, newNumReservedIDs);
                currentIDs.set(slotNum, newNumReservedIDs == 0 ? 0 : SLOT_CAPACITY - newNumReservedIDs);
            }
        }

        static TypeState generateTypeState(int numClassDescendants, ArrayList<Integer> currentIDs, ArrayList<Integer> numReservedIDs) {
            /*
             * A reserved ID is assigned when this type's slot will overflow while assigning IDs to
             * its subtypes.
             */
            int reservedID = 0;
            int slotNum = currentIDs.size() - 1;
            /* first trying to assign next sequential id. */
            int assignedID = currentIDs.get(slotNum) + 1;
            /* Number of slot currently reserved. This effectively lowers the slot's capacity. */
            int currentNumReservedIDs = numReservedIDs.get(slotNum);
            int currentCapacity = SLOT_CAPACITY - currentNumReservedIDs;
            assert assignedID <= currentCapacity;

            if (assignedID == currentCapacity) {
                /*
                 * No more space left. Assigning overflowed slot appropriate "end" value.
                 */
                currentIDs.set(slotNum, currentNumReservedIDs == 0 ? 0 : SLOT_CAPACITY - currentNumReservedIDs);
                slotNum++;
                currentIDs.add(0);
                currentNumReservedIDs = 0;
                currentCapacity = SLOT_CAPACITY;
                numReservedIDs.add(currentNumReservedIDs);
                assignedID = 1;
            }
            int maxSubtypeID = assignedID + numClassDescendants;
            if (maxSubtypeID >= currentCapacity) {
                /*
                 * Means this types descendants will overfill this slot. In this case, need to
                 * reserved an ID and force all descendants to have values between the current
                 * assignable and the reserved ID (inclusive). Non-descendants are then assigned the
                 * next ID (mod capacity).
                 */
                if (assignedID + 1 == currentCapacity) {
                    /*
                     * Not enough space to add a reserved slot at end of the list, so must move to
                     * next slot.
                     */
                    currentIDs.set(slotNum, currentNumReservedIDs == 0 ? 0 : SLOT_CAPACITY - currentNumReservedIDs);
                    slotNum++;
                    currentIDs.add(0);
                    currentNumReservedIDs = 0;
                    currentCapacity = SLOT_CAPACITY;
                    numReservedIDs.add(currentNumReservedIDs);
                    assignedID = 1;
                    maxSubtypeID = assignedID + numClassDescendants;
                }

                /*
                 * Have to recheck whether a reservedID is needed since a new slot may have been
                 * added.
                 */
                if (maxSubtypeID >= currentCapacity) {
                    currentNumReservedIDs++;
                    reservedID = currentNumReservedIDs;
                    maxSubtypeID = SLOT_CAPACITY - reservedID;
                    numReservedIDs.set(slotNum, currentNumReservedIDs);
                }
            }

            currentIDs.set(slotNum, assignedID);

            return new TypeState(reservedID, slotNum, assignedID, maxSubtypeID);
        }

    }

    /**
     * Contains all logic needed to assign the interface slot ids.
     */
    private static final class InterfaceIDBuilder {
        final List<HostedType> heightOrderedTypes;
        final Map<HostedType, List<HostedType>> subtypeMap;
        final int startingSlotNum;

        final Map<HostedType, int[]> interfaceSlotIDMap = new HashMap<>();
        int numInterfaceSlots = -1;

        /**
         * This class is used to represent a type which is part of the interface graph
         * ({@link Graph}).
         */
        private static final class Node {
            Node[] sortedAncestors;
            Node[] sortedDescendants;

            int id;
            final HostedType type;
            final boolean isInterface;

            Set<HostedType> duplicates;

            Node(int id, HostedType type, boolean isInterface) {
                this.id = id;
                this.type = type;
                this.isInterface = isInterface;
            }
        }

        /**
         * This is the "interface graph" used to represent interface subtyping dependencies. Within
         * this graph, each node has a direct edge to all of the interfaces it implements.
         */
        private static class Graph {
            Node[] nodes;
            Node[] interfaceNodes;

            Graph(Node[] nodes) {
                this.nodes = nodes;
            }

            /*
             * This method tries to merge classes which implement the same interfaces into a single
             * node.
             *
             * Because typechecks against interfaces is partitioned from typechecks against class
             * types, it is possible for multiple classes to be represented by the same interface
             * node, provided they implement the same interfaces. Merging these "duplicate" classes
             * into a single node has many benefits, including improving build-time performance and
             * potentially reducing the number of interface slots.
             */
            void mergeDuplicates() {
                Map<Integer, ArrayList<Node>> interfaceHashMap = new HashMap<>();
                Map<Integer, ArrayList<Node>> classHashMap = new HashMap<>();
                Map<Node, Set<HostedType>> duplicateMap = new HashMap<>();

                /*
                 * First group each node based on a hash of its ancestors. This hashing reduces the
                 * number of nodes which need to be checked against for duplicates.
                 */
                for (Node node : nodes) {
                    Node[] ancestors = node.sortedAncestors;

                    int length = ancestors.length;
                    assert length != 0;

                    boolean isNodeInterface = node.isInterface;
                    if (length == 1) {
                        /*
                         * If a node has a single interface, and it is a class, then it should be
                         * merged into the interface.
                         */
                        if (!isNodeInterface) {
                            Node ancestor = ancestors[0];
                            recordDuplicateRelation(duplicateMap, ancestor, node);
                            nodes[node.id] = null;
                        }
                    } else {
                        int hash = getDuplicateHash(ancestors);
                        /*
                         * Have separate maps for interfaces and classes so that, when possible,
                         * classes are merged into the appropriate interface node. Note that it is
                         * not possible to merge interfaces into each other.
                         */
                        Map<Integer, ArrayList<Node>> destinationMap = isNodeInterface ? interfaceHashMap : classHashMap;
                        destinationMap.computeIfAbsent(hash, k -> new ArrayList<>()).add(node);
                    }
                }

                /* First trying to merge classes into a matching interface. */
                for (Map.Entry<Integer, ArrayList<Node>> entry : interfaceHashMap.entrySet()) {
                    ArrayList<Node> interfaces = entry.getValue();
                    ArrayList<Node> classes = classHashMap.get(entry.getKey());
                    if (classes == null) {
                        /*
                         * Not guaranteed any classes will exist which can be merged into this
                         * interface.
                         */
                        continue;
                    }
                    for (Node interfaceNode : interfaces) {
                        for (int i = 0; i < classes.size(); i++) {
                            Node classNode = classes.get(i);
                            if (classNode == null) {
                                /*
                                 * It is possible for this class to have already be merged into
                                 * another interface.
                                 */
                                continue;
                            }
                            if (tryMergeNodes(duplicateMap, interfaceNode, classNode)) {
                                classes.set(i, null);
                            }
                        }
                    }
                }

                /* Next, trying to merge classes into one another. */
                for (Map.Entry<Integer, ArrayList<Node>> entry : classHashMap.entrySet()) {
                    ArrayList<Node> classes = entry.getValue();
                    int numClasses = classes.size();
                    for (int i = 0; i < numClasses - 1; i++) {
                        Node classNode = classes.get(i);
                        if (classNode == null) {
                            /* Class may have been already merged. */
                            continue;
                        }
                        for (int j = i + 1; j < numClasses; j++) {
                            Node duplicateCandidate = classes.get(j);
                            if (duplicateCandidate == null) {
                                /* Class may have been already merged. */
                                continue;
                            }
                            if (tryMergeNodes(duplicateMap, classNode, duplicateCandidate)) {
                                classes.set(j, null);
                            }
                        }
                    }
                }

                /* Recording all duplicates within the merged node. */
                for (Map.Entry<Node, Set<HostedType>> entry : duplicateMap.entrySet()) {
                    entry.getKey().duplicates = entry.getValue();
                }

                /* Removing all empty nodes from the array. */
                ArrayList<Node> compactedNodeArray = new ArrayList<>();
                for (Node node : nodes) {
                    if (node == null) {
                        continue;
                    }
                    /* Have to recalculate the ids. */
                    node.id = compactedNodeArray.size();
                    compactedNodeArray.add(node);
                }
                nodes = compactedNodeArray.toArray(new Node[0]);
            }

            /**
             * This hash is used to help quickly identify potential duplicates within the graph.
             */
            static int getDuplicateHash(Node[] ancestors) {
                int length = ancestors.length;
                return (length << 16) + Arrays.stream(ancestors).mapToInt(n -> n.id * n.id).sum();
            }

            boolean tryMergeNodes(Map<Node, Set<HostedType>> duplicateMap, Node node, Node duplicateCandidate) {
                if (areDuplicates(node, duplicateCandidate)) {
                    /* removing node b and marking it as a duplicate of node a */
                    recordDuplicateRelation(duplicateMap, node, duplicateCandidate);

                    int duplicateIdx = duplicateCandidate.id;
                    assert !nodes[duplicateIdx].isInterface; // shouldn't be removing interfaces
                    /* removing the node from the map */
                    nodes[duplicateIdx] = null;
                    return true;
                }
                return false;
            }

            /**
             * Two nodes are duplicates if their ancestors exactly match.
             */
            static boolean areDuplicates(Node a, Node b) {
                Node[] aAncestors = a.sortedAncestors;
                Node[] bAncestors = b.sortedAncestors;
                if (aAncestors.length != bAncestors.length) {
                    return false;
                }
                for (int i = 0; i < aAncestors.length; i++) {
                    if (aAncestors[i] != bAncestors[i]) {
                        return false;
                    }
                }
                return true;
            }

            /**
             * Recording duplicate information which later will be placed into the merged nodes.
             */
            static void recordDuplicateRelation(Map<Node, Set<HostedType>> duplicateMap, Node node, Node duplicate) {
                assert !duplicateMap.containsKey(duplicate) : "By removing this node, I am losing record of some duplicates.";
                duplicateMap.computeIfAbsent(node, k -> new HashSet<>()).add(duplicate.type);
            }

            /**
             * Computing the descendants for each node of interface type. This information is needed
             * to determine which nodes must be assigned contiguous type ids.
             */
            void generateDescendantIndex() {
                Map<Node, Set<Node>> descendantMap = new HashMap<>();
                Node[] emptyDescendant = new Node[0];
                ArrayList<Node> interfaceList = new ArrayList<>();

                // iterating through children before parents
                for (int i = nodes.length - 1; i >= 0; i--) {
                    Node node = nodes[i];
                    if (node.isInterface) {
                        // recording descendant information
                        Set<Node> descendants = descendantMap.computeIfAbsent(node, k -> new HashSet<>());
                        descendants.add(node);
                        Node[] descendantArray = descendants.toArray(new Node[0]);
                        Arrays.sort(descendantArray, Comparator.comparingInt(n -> n.id));
                        node.sortedDescendants = descendantArray;
                        interfaceList.add(node);

                    } else {
                        // non-interface nodes don't have any requirements
                        node.sortedDescendants = emptyDescendant;
                    }
                    /*
                     * Relaying descendants to ancestors, but only need to add oneself, not all
                     * ancestors, due to the guarantees about the interface graph
                     */
                    for (Node ancestor : node.sortedAncestors) {
                        descendantMap.computeIfAbsent(ancestor, k -> new HashSet<>()).add(node);
                    }
                }
                this.interfaceNodes = interfaceList.toArray(new Node[0]);
                int maxDescendants = Integer.MIN_VALUE;
                int maxAncestors = Integer.MIN_VALUE;
                for (Node node : interfaceList) {
                    maxDescendants = Math.max(node.sortedDescendants.length, maxDescendants);
                    maxAncestors = Math.max(node.sortedAncestors.length, maxAncestors);
                }
                maxDescendants = Integer.MIN_VALUE;
                maxAncestors = Integer.MIN_VALUE;
                for (Node node : nodes) {
                    maxDescendants = Math.max(node.sortedDescendants.length, maxDescendants);
                    maxAncestors = Math.max(node.sortedAncestors.length, maxAncestors);
                }
            }

            /*
             * Given the set of included types ordered by maximum height, this method creates the
             * appropriate interface graph.
             */
            static Graph buildInterfaceGraph(List<HostedType> heightOrderedTypes, Map<HostedType, List<HostedType>> subtypeMap) {
                Map<HostedType, Set<Node>> interfaceAncestors = new HashMap<>();

                /* By the time a node is reached, it will have all needed parent information. */
                ArrayList<Node> nodes = new ArrayList<>();
                for (HostedType type : heightOrderedTypes) {

                    boolean isTypeInterface = isInterface(type);
                    Set<Node> ancestors = interfaceAncestors.computeIfAbsent(type, k -> isTypeInterface ? new HashSet<>() : null);
                    if (ancestors == null) {
                        /* This node does not need to be part of the interface graph */
                        continue;
                    }

                    int id = nodes.size();
                    Node newNode = new Node(id, type, isTypeInterface);
                    nodes.add(newNode);

                    if (isTypeInterface) {
                        ancestors.add(newNode);
                    }
                    Node[] sortedAncestors = ancestors.toArray(new Node[0]);
                    Arrays.sort(sortedAncestors, Comparator.comparingInt(n -> n.id));
                    newNode.sortedAncestors = sortedAncestors;

                    /* Passing ancestor information to children. */
                    for (HostedType child : subtypeMap.get(type)) {
                        interfaceAncestors.computeIfAbsent(child, k -> new HashSet<>()).addAll(ancestors);
                    }
                }

                Node[] nodeArray = nodes.toArray(new Node[0]);
                int maxAncestors = -1;
                for (Node node : nodeArray) {
                    maxAncestors = Math.max(maxAncestors, node.sortedAncestors.length);
                }

                return new Graph(nodeArray);
            }

        }

        /**
         * This class represents a single ordering constraint. For range type checks to be possible,
         * all nodes contained within a contiguous group must have contiguous type ids.
         */
        private static final class ContiguousGroup {
            final int[] sortedGroupIds;

            PrimeMatrix primeMatrix;

            /* Using a timestamp to limit the number of times each group is checked. */
            int lastTimeStamp = -1; // hasn't been checked yet

            ContiguousGroup(int[] sortedGroupIds) {
                this.sortedGroupIds = sortedGroupIds;
            }
        }

        /**
         * This class manages the a single slot and its constraints.
         */
        private static final class InterfaceSlot {

            enum AddGroupingResult {
                CAPACITY_OVERFLOW,
                INVALID_C1P,
                SUCCESS,
            }

            /**
             * This slot's index.
             */
            final int id;
            int currentTimeStamp;

            /**
             * The number of IDs currently needed to cover the type checks contained within this
             * slot.
             */
            int numReservedIDs;

            /**
             * The prime matrices currently associated with this slot. See {@link PrimeMatrix} for
             * its definition.
             */
            Set<PrimeMatrix> matrices = new HashSet<>();
            /**
             * A map from an id to all of the ContiguousGroups which contain that id.
             */
            Map<Integer, Set<ContiguousGroup>> columnToGroupingMap = new HashMap<>();

            InterfaceSlot(int id) {
                this.id = id;
                currentTimeStamp = 0;
                /* Initially, one id is reserved for nodes not utilizing this slot. */
                numReservedIDs = 1;
            }

            /**
             * Attempts to add an interface's constraints to this slot. If it can't, then the
             * internal state is unchanged.
             *
             * @return whether the interface's constraints were added to this slot
             */
            AddGroupingResult tryAddGrouping(Node interfaceNode) {
                // first, create new grouping requirement representing this node
                int[] sortedGroupIds = Arrays.stream(interfaceNode.sortedDescendants).mapToInt(n -> n.id).toArray();
                ContiguousGroup newGrouping = new ContiguousGroup(sortedGroupIds);

                /*
                 * Next, determining which, if any, primeMatrices this new grouping links together.
                 */
                int timestamp = ++currentTimeStamp;
                ArrayList<ContiguousGroup> edges = new ArrayList<>();
                Set<PrimeMatrix> linkedPrimeMatrices = new HashSet<>();

                for (int column : sortedGroupIds) {
                    Set<ContiguousGroup> groupings = columnToGroupingMap.get(column);
                    if (groupings != null) {
                        for (ContiguousGroup existingGrouping : groupings) {
                            // only check group if it hasn't been checked already during this phase
                            if (existingGrouping.lastTimeStamp != timestamp) {
                                existingGrouping.lastTimeStamp = timestamp;
                                // seeing if the two groups strictly overlap
                                boolean strictlyOverlap = strictlyOverlaps(newGrouping, existingGrouping);
                                if (strictlyOverlap) {
                                    edges.add(existingGrouping);
                                    linkedPrimeMatrices.add(existingGrouping.primeMatrix);
                                }
                            }
                        }
                    }
                }

                /* Creating new prime matrix to represent the new connected subgraph. */
                PrimeMatrix newPrimeMatrix = new PrimeMatrix(newGrouping);
                newGrouping.primeMatrix = newPrimeMatrix;

                /*
                 * Link in connected prime matrices and check if new prime matrix satisfies
                 * consecutive ones property
                 */
                boolean satisfiesC1P = newPrimeMatrix.incorporateMatrices(linkedPrimeMatrices, edges);
                if (!satisfiesC1P) {
                    // Not successful: do not add any of this information.
                    return AddGroupingResult.INVALID_C1P;
                }

                // check the number of IDs needed still fit within the given capacity
                int numIDsDelta = newPrimeMatrix.c1POrdering.size() - linkedPrimeMatrices.stream().mapToInt(n -> n.c1POrdering.size()).sum();
                assert numIDsDelta >= 0; // new constraints cannot reduce number of IDs.
                int newNumReservedIDs = numReservedIDs + numIDsDelta;
                if (newNumReservedIDs > SLOT_CAPACITY) {
                    // too large -- cannot add this constraint.
                    return AddGroupingResult.CAPACITY_OVERFLOW;
                }

                /* Was successful -> need to update metadata. */
                // update size
                numReservedIDs = newNumReservedIDs;

                // add new prime matrix
                matrices.add(newPrimeMatrix);

                /*
                 * Remove invalidated prime matrices and update primeMatrix links in existing
                 * ContiguousGroups
                 */
                for (PrimeMatrix removedMatrix : linkedPrimeMatrices) {
                    // removing old matrix from set
                    matrices.remove(removedMatrix);
                    for (ContiguousGroup grouping : removedMatrix.containedGroups) {
                        grouping.primeMatrix = newPrimeMatrix;
                    }
                }

                // add new relation to proper columnToGroupingMap keys
                for (int connection : sortedGroupIds) {
                    columnToGroupingMap.computeIfAbsent(connection, k -> new HashSet<>()).add(newGrouping);
                }

                return AddGroupingResult.SUCCESS;
            }

            /**
             * This method is used to determine whether the two groups "strictly overlap", meaning:
             * <ol>
             * <li>There is a least one overlapping value</li>
             * <li>One group is not a subset of the other</li>
             * </ol>
             *
             * <p>
             * Note this method is assuming a and b are sorted, are not identical, and there is at
             * least one element of overlap.
             */
            static boolean strictlyOverlaps(ContiguousGroup a, ContiguousGroup b) {
                int[] aArray = a.sortedGroupIds;
                int[] bArray = b.sortedGroupIds;
                int aLength = aArray.length;
                int bLength = bArray.length;
                int aIdx = 0;
                int bIdx = 0;
                int numMatches = 0;
                while (aIdx < aLength && bIdx < bLength) {
                    int aValue = aArray[aIdx];
                    int bValue = bArray[bIdx];
                    if (aValue == bValue) {
                        numMatches++;
                        aIdx++;
                        bIdx++;
                    } else if (aValue < bValue) {
                        aIdx++;
                    } else {
                        /* aValue > bValue */
                        bIdx++;
                    }
                }
                int minLength = Math.min(aLength, bLength);
                /* Must have at least one element of overlap. */
                assert numMatches != 0 && numMatches <= minLength;
                /* The groups shouldn't be exactly the same, or else they could have been merged. */
                assert !(aLength == bLength && numMatches == aLength);
                return numMatches != minLength;
            }

            /**
             * Getting a valid C1P order for all nodes within this slot. Nodes part of the same set
             * can be assigned the same ID value.
             */
            List<Set<Integer>> getC1POrder() {
                /*
                 * Order prime matrices based on the # of nodes, in decreasing order.
                 *
                 * By doing so, each retrieved matrix ordering will either be:
                 *
                 * 1. Non-intersecting with the previously added nodes. In this case the matrix's
                 * C1P ordering can be added to the end.
                 *
                 * 2. A subset of one set previously added nodes contains the all of the matrix's
                 * nodes. In this case, that set can be split and the new C1P ordering can be added
                 * in this spot.
                 */
                List<PrimeMatrix> sizeOrderedMatrices = matrices.stream().sorted(Comparator.comparingInt(n -> -(n.containedNodes.size()))).collect(Collectors.toList());

                List<Set<Integer>> c1POrdering = new ArrayList<>();
                Set<Integer> coveredNodes = new HashSet<>();
                for (PrimeMatrix matrix : sizeOrderedMatrices) {

                    /* The new ordering constraints which must be applied. */
                    List<Set<Integer>> newOrderingConstraints = matrix.c1POrdering;

                    assert matrix.containedNodes.size() > 0; // can't have an empty matrix
                    Integer matrixRepresentativeNode = matrix.containedNodes.stream().findAny().get();
                    boolean hasOverlap = coveredNodes.contains(matrixRepresentativeNode);
                    if (!hasOverlap) {
                        // no overlap -> just add nodes to end of the list
                        c1POrdering.addAll(newOrderingConstraints);
                        coveredNodes.addAll(matrix.containedNodes);

                    } else {
                        /*
                         * when there is overlap, all overlapping nodes will be in one set in the
                         * current list.
                         */
                        assert coveredNodes.containsAll(matrix.containedNodes);

                        assert verifyC1POrderingProperty(c1POrdering, matrix);

                        for (int i = 0; i < c1POrdering.size(); i++) {
                            Set<Integer> item = c1POrdering.get(i);
                            /* It is enough to use one node to find where the overlap is. */
                            hasOverlap = item.contains(matrixRepresentativeNode);
                            if (hasOverlap) {
                                item.removeAll(matrix.containedNodes);
                                c1POrdering.addAll(i + 1, newOrderingConstraints);
                                if (item.size() == 0) {
                                    c1POrdering.remove(i);
                                }
                                break;
                            }
                        }
                    }
                }

                return c1POrdering;
            }

            /**
             * Verifying assumption that all of the overlap will be confined to one set within the
             * current c1POrdering.
             */
            static boolean verifyC1POrderingProperty(List<Set<Integer>> c1POrdering, PrimeMatrix matrix) {
                ArrayList<Integer> overlappingSets = new ArrayList<>();
                for (int i = 0; i < c1POrdering.size(); i++) {
                    Set<Integer> item = c1POrdering.get(i);
                    boolean hasOverlap = item.stream().anyMatch(n -> matrix.containedNodes.contains(n));
                    if (hasOverlap) {
                        overlappingSets.add(i);
                    }
                }
                return overlappingSets.size() == 1;
            }
        }

        /**
         * Within consecutive one property (C1P) testing literature, in a graph where each
         * {@link ContiguousGroup} is a node and edges are between nodes that that "strictly
         * overlap", the graph can be decomposed into connected subgraphs, known as prime matrices.
         * <p>
         * Once the graph's prime matrices have been identified, it is sufficient to test each prime
         * matrix individually for the C1P property.
         */
        private static class PrimeMatrix {

            final ContiguousGroup initialGroup;
            List<ContiguousGroup> containedGroups;

            /* all of the strictly ordered edges within this prime matrix */
            Map<ContiguousGroup, Set<ContiguousGroup>> edgeMap;

            /**
             * To verify the consecutive ones property (C1P), two data structures are needed, the
             * current ordering (c1POrdering) and another keeping track of all of the nodes
             * contained in the current ordering (containedNodes).
             */
            List<Set<Integer>> c1POrdering;
            Set<Integer> containedNodes;

            PrimeMatrix(ContiguousGroup initialGroup) {
                this.initialGroup = initialGroup;
                containedGroups = new ArrayList<>();
                containedGroups.add(initialGroup);
                edgeMap = new HashMap<>();
            }

            void initializeC1PInformation() {
                this.containedNodes = new HashSet<>();
                this.c1POrdering = new ArrayList<>();
            }

            void copyC1PInformation(PrimeMatrix src) {
                this.containedNodes = new HashSet<>(src.containedNodes);
                this.c1POrdering = new ArrayList<>();
                for (Set<Integer> entry : src.c1POrdering) {
                    this.c1POrdering.add(new HashSet<>(entry));
                }
            }

            /**
             * Adding in other prime matrices which strictly overlap the {@link #initialGroup}. All
             * of these prime matrices need to be combined into a single matrix, provided that an
             * ordering can be created where all of the contained groups satisfy the consecutive one
             * property.
             *
             * @param matrices the other prime matrices which need to be combined into this matrix
             * @param edges the links between the {@link #initialGroup} and groups within the other
             *            prime matrices
             */
            boolean incorporateMatrices(Set<PrimeMatrix> matrices, List<ContiguousGroup> edges) {
                assert containedGroups.size() == 1 : "Matrices can only be combined once";

                /*
                 * Finding the prime matrix being combined with the most ContainedGroups. By placing
                 * this prime matrix at the front of the spanning tree, it does not need to be
                 * rechecked for the consecutive ones property.
                 */
                PrimeMatrix largestMatrix = null;
                int largestMatrixSize = Integer.MIN_VALUE;
                for (PrimeMatrix matrix : matrices) {

                    int matrixSize = matrix.containedGroups.size();
                    assert matrixSize > 0;
                    if (matrixSize > largestMatrixSize) {
                        largestMatrixSize = matrixSize;
                        largestMatrix = matrix;
                    }
                }

                /* initializing the consecutive one property information */
                if (largestMatrix != null) {
                    copyC1PInformation(largestMatrix);
                } else {
                    initializeC1PInformation();
                }
                PrimeMatrix finalLargestMatrix = largestMatrix;

                /*
                 * To verify the C1P for the combined prime matrix, each ContiguousGroup requirement
                 * must be added, one by one, in the order of a travel of the matrix's spanning tree
                 * to see if a valid ordering can be created.
                 */
                List<ContiguousGroup> spanningTree = computeSpanningTree(edges, largestMatrix);
                int expectedNumNodes = 1 + matrices.stream().filter(n -> n != finalLargestMatrix).mapToInt(n -> n.containedGroups.size()).sum();
                assert spanningTree.size() == expectedNumNodes;
                for (ContiguousGroup grouping : spanningTree) {
                    if (!addGroupAndCheckC1P(grouping)) {
                        return false;
                    }
                }

                /*
                 * At this point the consecutive ones property has been satisfied for the combined
                 * matrix.
                 */

                /* Updating the contained groups and adding the connect prime matrix's edges. */
                for (PrimeMatrix matrix : matrices) {
                    List<ContiguousGroup> otherGroup = matrix.containedGroups;
                    assert otherGroup.stream().noneMatch(containedGroups::contains) : "the intersection between all prime matrices should be null";
                    containedGroups.addAll(otherGroup);

                    Map<ContiguousGroup, Set<ContiguousGroup>> otherEdgeMap = matrix.edgeMap;
                    for (Map.Entry<ContiguousGroup, Set<ContiguousGroup>> entry : otherEdgeMap.entrySet()) {
                        ContiguousGroup key = entry.getKey();
                        edgeMap.computeIfAbsent(key, k -> new HashSet<>()).addAll(entry.getValue());
                    }
                }

                /*
                 * Adding the edges between the initialGroup and ContiguousGroups within the other
                 * prime matrices.
                 */
                edgeMap.put(initialGroup, new HashSet<>());
                for (ContiguousGroup edge : edges) {
                    edgeMap.get(initialGroup).add(edge);
                    edgeMap.computeIfAbsent(edge, k -> new HashSet<>()).add(initialGroup);
                }

                return true;
            }

            /**
             * Computing the combined matrix's spanning tree for all groups which are not part of
             * the largest matrix.
             *
             * @param edges Connections from the {@link #initialGroup} to relationships in other
             *            matrices.
             */
            List<ContiguousGroup> computeSpanningTree(List<ContiguousGroup> edges, PrimeMatrix largestMatrix) {
                List<ContiguousGroup> list = new ArrayList<>();
                /*
                 * adding initial group first since it is the only relation which has cross-matrix
                 * edges
                 */
                list.add(initialGroup);

                Set<PrimeMatrix> coveredMatrices = new HashSet<>();
                if (largestMatrix != null) {
                    coveredMatrices.add(largestMatrix);
                }
                /* Appending spanning tree for each uncovered matrix. */
                for (ContiguousGroup edge : edges) {
                    PrimeMatrix matrix = edge.primeMatrix;
                    if (!coveredMatrices.contains(matrix)) {
                        coveredMatrices.add(matrix);
                        list.addAll(matrix.getSpanningTree(edge));
                    }
                }
                return list;
            }

            /**
             * Creating spanning for matrix starting from the provided node.
             * <p>
             * Note by virtue of the prime matrix graph property, all nodes are connected, i.e., are
             * reachable from any given node within the matrix.
             */
            List<ContiguousGroup> getSpanningTree(ContiguousGroup startingNode) {
                Set<ContiguousGroup> seenNodes = new HashSet<>();
                List<ContiguousGroup> list = new ArrayList<>();
                getSpanningTreeHelper(startingNode, list, seenNodes);
                return list;
            }

            void getSpanningTreeHelper(ContiguousGroup node, List<ContiguousGroup> list, Set<ContiguousGroup> seenNodes) {
                list.add(node);
                seenNodes.add(node);
                Set<ContiguousGroup> edges = this.edgeMap.get(node);
                if (edges != null) {
                    for (ContiguousGroup edge : edges) {
                        if (!seenNodes.contains(edge)) {
                            getSpanningTreeHelper(edge, list, seenNodes);
                        }
                    }
                }
            }

            /**
             * When trying to find a valid c1POrdering, a "color" is assigned to each set based on
             * the new set of grouping constraints being added.
             */
            enum SetColor {
                EMPTY, // no nodes in the set are colored
                PARTIAL, // some nodes in the set are colored
                FULL; // all nodes in the set are colored

                /**
                 * Return the color of the provide set, based on what is "colored" by the colored
                 * set.
                 */
                private static SetColor getSetColor(Set<Integer> set, Set<Integer> coloredSet) {
                    long numColoredNodes = set.stream().filter(coloredSet::contains).count();
                    if (numColoredNodes == 0) {
                        return SetColor.EMPTY;
                    } else if (numColoredNodes == set.size()) {
                        return SetColor.FULL;
                    } else {
                        return SetColor.PARTIAL;
                    }
                }

                /**
                 * Removes all nodes from the provided set and returns them as a new set.
                 *
                 * @return Set of colored nodes from original set.
                 */
                private static Set<Integer> splitOffColored(Set<Integer> set, Set<Integer> coloredSet) {
                    Set<Integer> coloredNodes = set.stream().filter(coloredSet::contains).collect(Collectors.toSet());
                    // assuming that this is invoked a partially colored set
                    assert coloredNodes.size() != 0 && coloredNodes.size() < set.size();
                    set.removeAll(coloredNodes);

                    return coloredNodes;
                }
            }

            /**
             * Attempts to add a new grouping restraint to the {@link #c1POrdering}.
             * <p>
             * The code here follows the algorithm proposed in "A Simple Test for the Consecutive
             * Ones Property" by Wen-Lain Hsu.
             *
             * @return if the new grouping constraint was able to be added.
             */
            boolean addGroupAndCheckC1P(ContiguousGroup grouping) {
                Set<Integer> newGroup = Arrays.stream(grouping.sortedGroupIds).boxed().collect(Collectors.toSet());

                /*
                 * Nodes that are part of this grouping, but aren't part of the current c1POrdering
                 */
                Set<Integer> uncoveredNodes = newGroup.stream().filter(n -> !containedNodes.contains(n)).collect(Collectors.toSet());

                int numSets = c1POrdering.size();
                if (numSets == 0) {
                    /* add the initial ordering requirement */
                    c1POrdering.add(uncoveredNodes);

                } else if (numSets == 1) {
                    /*
                     * always possible to add this constraint by computing A - (A ^ B), (A ^ B), and
                     * B - (A ^ B)
                     */
                    // nodes which are only in the original group
                    c1POrdering.get(0).removeAll(newGroup);
                    // nodes which are in both groups (i.e. the intersection)
                    c1POrdering.add(newGroup.stream().filter(containedNodes::contains).collect(Collectors.toSet()));
                    // nodes which are only in the new group
                    c1POrdering.add(uncoveredNodes);

                } else {
                    /*
                     * More than one set is already present, need to use coloring to try to find a
                     * valid ordering.
                     */
                    // COLUMN-PARTITION Algorithm Step 2

                    /*
                     * Within the current ordering, recording the color of each set and which are
                     * the leftmost and rightmost colored sets
                     */
                    SetColor[] setColors = new SetColor[c1POrdering.size()];
                    int leftIntersect = Integer.MIN_VALUE;
                    int rightIntersect = Integer.MIN_VALUE;
                    for (int i = 0; i < c1POrdering.size(); i++) {
                        SetColor color = SetColor.getSetColor(c1POrdering.get(i), newGroup);
                        setColors[i] = color;
                        if (color != SetColor.EMPTY) {
                            if (leftIntersect == Integer.MIN_VALUE) {
                                leftIntersect = i;
                            }
                            rightIntersect = i;
                        }
                    }
                    /*
                     * Properties of the prime matrix and spanning tree means the new grouping must
                     * have an overlap with the current c1POrdering.
                     */
                    assert leftIntersect != Integer.MIN_VALUE && rightIntersect != Integer.MIN_VALUE;

                    // checking if all sets in between the intersections are full
                    for (int i = leftIntersect + 1; i < rightIntersect; i++) {
                        if (setColors[i] != SetColor.FULL) {
                            // C1P cannot be satisfied
                            return false;
                        }
                    }

                    SetColor rightColor = setColors[rightIntersect];
                    SetColor leftColor = setColors[leftIntersect];
                    if (uncoveredNodes.size() == 0) {
                        /* COLUMN-PARTITION Algorithm STEP 2.1. */
                        splitColoredNodes(rightColor, newGroup, rightIntersect, rightIntersect);
                        if (leftIntersect != rightIntersect) {
                            splitColoredNodes(leftColor, newGroup, leftIntersect, leftIntersect + 1);
                        }

                    } else {
                        /*
                         * COLUMN-PARTITION Algorithm STEP 2.2 checking that either the left or
                         * right intersect are a subset of the the new grouping.
                         */
                        if (leftIntersect == 0 && leftColor == SetColor.FULL) {
                            /*
                             * splitting the right intersect to make sure nodes part of this
                             * collection are on the left size
                             */
                            splitColoredNodes(rightColor, newGroup, rightIntersect, rightIntersect);
                            /* This is being added to the front of the list. */
                            c1POrdering.add(0, uncoveredNodes);
                        } else if (rightIntersect == (numSets - 1) && rightColor == SetColor.FULL) {
                            splitColoredNodes(leftColor, newGroup, leftIntersect, leftIntersect + 1);
                            /* This is being added to the end of the list. */
                            c1POrdering.add(uncoveredNodes);
                        } else {
                            /* Could not find a valid ordering. */
                            return false;
                        }
                    }
                }

                /* Recording that these nodes have been covered now. */
                containedNodes.addAll(uncoveredNodes);
                return true;
            }

            /**
             * If only partially colored, putting the colored nodes into a new set and inserting
             * them into a new place within the c1POrdering.
             */
            void splitColoredNodes(SetColor color, Set<Integer> coloredSet, int srcIndex, int dstIndex) {
                assert color != SetColor.EMPTY;
                if (color != SetColor.FULL) {
                    Set<Integer> newSet = SetColor.splitOffColored(c1POrdering.get(srcIndex), coloredSet);
                    c1POrdering.add(dstIndex, newSet);
                }
            }
        }

        InterfaceIDBuilder(int startingSlotNum, List<HostedType> heightOrderedTypes, Map<HostedType, List<HostedType>> subtypeMap) {
            this.startingSlotNum = startingSlotNum;
            this.heightOrderedTypes = heightOrderedTypes;
            this.subtypeMap = subtypeMap;
        }

        void computeSlots() {
            Graph interfaceGraph = Graph.buildInterfaceGraph(heightOrderedTypes, subtypeMap);
            interfaceGraph.mergeDuplicates();
            interfaceGraph.generateDescendantIndex();
            calculateInterfaceIDs(interfaceGraph);
        }

        void calculateInterfaceIDs(Graph graph) {
            assert graph.interfaceNodes != null;

            // initializing first slot
            ArrayList<InterfaceSlot> slots = new ArrayList<>();
            slots.add(new InterfaceSlot(slots.size()));

            // assigning interfaces to interface slots
            for (Node node : graph.interfaceNodes) {

                // first trying to adding grouping to existing slot
                boolean foundAssignment = false;
                boolean redoSort = false;
                for (InterfaceSlot slot : slots) {
                    InterfaceSlot.AddGroupingResult result = slot.tryAddGrouping(node);
                    if (result == InterfaceSlot.AddGroupingResult.SUCCESS) {
                        foundAssignment = true;
                        node.type.setTypeCheckSlot(getShortValue(slot.id + startingSlotNum));
                        break;
                    } else if (result == InterfaceSlot.AddGroupingResult.CAPACITY_OVERFLOW) {
                        /*
                         * If running into capacity overflows, should try to sort slots so that
                         * emptier slots are encountered first.
                         */
                        redoSort = true;
                    }
                }
                if (!foundAssignment) {
                    // a new slot is needed to satisfy this grouping
                    InterfaceSlot newSlot = new InterfaceSlot(slots.size());
                    InterfaceSlot.AddGroupingResult result = newSlot.tryAddGrouping(node);
                    assert result == InterfaceSlot.AddGroupingResult.SUCCESS : "must be able to add first node";
                    node.type.setTypeCheckSlot(getShortValue(newSlot.id + startingSlotNum));
                    slots.add(newSlot);
                }
                if (redoSort) {
                    slots.sort(Comparator.comparingInt(slot -> slot.numReservedIDs));
                }
            }

            // initializing all interface slots
            int numSlots = slots.size();
            assert numInterfaceSlots == -1;
            numInterfaceSlots = numSlots;
            for (Node node : graph.nodes) {
                assert !interfaceSlotIDMap.containsKey(node.type);
                interfaceSlotIDMap.put(node.type, new int[numSlots]);
            }

            // assigning slot IDs
            for (InterfaceSlot slot : slots) {
                List<Set<Integer>> c1POrder = slot.getC1POrder();
                int slotId = slot.id;
                int id = 1;
                for (Set<Integer> group : c1POrder) {
                    for (Integer nodeID : group) {
                        HostedType type = graph.nodes[nodeID].type;
                        interfaceSlotIDMap.get(type)[slotId] = id;
                    }
                    id++;
                }
            }

            // now computing ranges for each interface
            for (Node interfaceNode : graph.interfaceNodes) {
                int minId = Integer.MAX_VALUE;
                int maxId = Integer.MIN_VALUE;
                HostedType type = interfaceNode.type;
                int slotId = Short.toUnsignedInt(type.getTypeCheckSlot()) - startingSlotNum;
                Set<Integer> idCheck = new HashSet<>();
                for (Node descendant : interfaceNode.sortedDescendants) {
                    int id = interfaceSlotIDMap.get(descendant.type)[slotId];
                    assert id != 0;
                    minId = Integer.min(minId, id);
                    maxId = Integer.max(maxId, id);
                    idCheck.add(id);
                }
                type.setTypeCheckRange(getShortValue(minId), getShortValue(maxId - minId + 1));
            }

            // relaying information to duplicates
            for (Node node : graph.nodes) {
                if (node.duplicates != null) {
                    for (HostedType duplicate : node.duplicates) {
                        assert !interfaceSlotIDMap.containsKey(duplicate);
                        interfaceSlotIDMap.put(duplicate, interfaceSlotIDMap.get(node.type));
                    }
                }
            }
        }
    }

    private static final class TypeCheckValidator {

        static boolean compareTypeIDResults(List<HostedType> types) {
            int numTypes = types.size();
            for (int i = 0; i < numTypes; i++) {
                HostedType superType = types.get(i);
                for (int j = 0; j < numTypes; j++) {
                    HostedType checkedType = types.get(j);
                    boolean legacyCheck = legacyCheckAssignable(superType, checkedType);
                    boolean newCheck = newCheckAssignable(superType, checkedType);
                    boolean checksMatch = legacyCheck == newCheck;
                    if (!checksMatch) {
                        StringBuilder message = new StringBuilder();
                        message.append("\n********Type checks do not match:********\n");
                        message.append(String.format("super type: %s\n", superType.toString()));
                        message.append(String.format("checked type: %s\n", checkedType.toString()));
                        message.append(String.format("legacy check: %b\n", legacyCheck));
                        message.append(String.format("new check: %b\n", newCheck));
                        VMError.shouldNotReachHere(message.toString());
                    }
                }
            }
            return true;
        }

        static boolean legacyCheckAssignable(HostedType superType, HostedType checkedType) {
            int[] matches = superType.getAssignableFromMatches();
            int checkedID = checkedType.getTypeID();
            for (int i = 0; i < matches.length; i += 2) {
                int assignableStart = matches[i];
                int assignableEnd = assignableStart + matches[i + 1] - 1;
                if (checkedID >= assignableStart && checkedID <= assignableEnd) {
                    return true;
                }
            }
            return false;
        }

        static boolean newCheckAssignable(HostedType superType, HostedType checkedType) {
            int typeCheckStart = Short.toUnsignedInt(superType.getTypeCheckStart());
            int typeCheckRange = Short.toUnsignedInt(superType.getTypeCheckRange());
            int typeCheckSlot = Short.toUnsignedInt(superType.getTypeCheckSlot());
            int checkedTypeID = Short.toUnsignedInt(checkedType.getTypeCheckSlots()[typeCheckSlot]);
            if (UnsignedMath.belowThan(checkedTypeID - typeCheckStart, typeCheckRange)) {
                return true;
            }
            return false;
        }
    }
}
