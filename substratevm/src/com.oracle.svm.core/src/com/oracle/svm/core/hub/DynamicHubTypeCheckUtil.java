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
package com.oracle.svm.core.hub;

import java.util.HashSet;
import java.util.Set;

import com.oracle.svm.core.SubstrateOptions;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.GraalError;

/**
 * Contains utility for computing type check info for DynamicHubs, such as constants and functions
 * needed for {@link SubstrateOptions#useInterfaceHashing()}.
 *
 */
public final class DynamicHubTypeCheckUtil {

    // Constants for interface hashing

    /**
     * The offset of the {@code shift} inside the {@code hashParam}.
     * {@code shift = hashParam >>> offset}. See {@link #hashParam(int[])} for details.
     */
    public static final int HASHING_SHIFT_OFFSET = 24;

    /**
     * The bitmask to be used on the encoded {@code hashParam} to exclude the {@code shift}.
     * {@code p = hashParam & bitmask}. See {@link #hashParam(int[])} for details.
     */
    public static final int HASHING_PARAM_MASK = NumUtil.getNbitNumberInt(HASHING_SHIFT_OFFSET);

    /**
     * The number of bits that the {@code iTableOffset} is shifted inside a hash table entry.
     * {@code hashTable[hash(interfaceID)] = (iTableOffset << shift | interfaceID}. See the
     * documentation on TypeCheckBuilder for details.
     */
    public static final int HASHING_ITABLE_SHIFT = 16;

    /**
     * The bitmask that extracts the {@code interfaceID} from a hash table entry.
     * {@code hashTable[hash(interfaceID)] = (iTableOffset << shift | interfaceID}. See the
     * documentation on TypeCheckBuilder for details.
     */
    public static final int HASHING_INTERFACE_MASK = NumUtil.getNbitNumberInt(HASHING_ITABLE_SHIFT);

    public record TypeCheckData(int[] openTypeWorldTypeCheckSlots, int[] openTypeWorldInterfaceHashTable, int openTypeWorldInterfaceHashParam, short numIterableInterfaces) {
    }

    /**
     * Computes type check data for {@link DynamicHub}s in the open type world.
     * </p>
     * With {@link SubstrateOptions#useInterfaceHashing()} disabled,
     * {@link DynamicHub#getOpenTypeWorldTypeCheckSlots() openTypeWorldTypeCheckSlots} contain two
     * entries per interface, one for the typeID and one for the itable offset:
     *
     * <pre>
     * checkSlots = [...typeIDs...; interfaceID_0 ; itableOffset_0 ; ... ]
     * </pre>
     * <p>
     * Interface type checks and resolving interface method calls requires iterating over this data.
     * <p>
     * With {@link SubstrateOptions#useInterfaceHashing()} enabled,
     * {@link DynamicHub#getOpenTypeWorldTypeCheckSlots() openTypeWorldTypeCheckSlots} only contain
     * information about the type hierarchy:
     *
     * <pre>
     * checkSlots = [ ... ; parent.typeID ; this.typeID]
     * </pre>
     * <p>
     * Type check information for interfaces is encoded in a hash table. This hash table is computed
     * based on the implemented interfaces. Instead of the interface {@code typeID}, an orthogonal
     * {@code interfaceID} is used to identify interfaces within the hash table. The
     * {@code interfaceID} is smaller than the {@code typeID} which yields smaller hash tables. The
     * hash table entries encode the following:
     *
     * <pre>
     * hashTable[hash(interfaceID)] = (iTableOffset << {@link #HASHING_ITABLE_SHIFT}) | interfaceID
     * </pre>
     * <p>
     * Interfaces that have interfaceIDs > {@link SubstrateOptions#InterfaceHashingMaxId} are
     * encoded in the checkSlots array as before.
     */
    public static TypeCheckData computeOpenTypeWorldTypeCheckData(boolean implementsMethods, int[] typeHierarchy, int[] interfaceIDs, int[] iTableStartingOffsets, long vTableBaseOffset,
                    long vTableEntrySize) {
        int hashParam = 0;
        int[] hashTable = null;

        boolean useInterfaceHashing = SubstrateOptions.useInterfaceHashing();
        int numHashedInterfaces = 0;

        if (useInterfaceHashing) {
            // count interfaces that can be hashed (i.e., their IDs are smaller than
            // SubstrateOptions.interfaceHashingMaxId())
            for (int interfaceID : interfaceIDs) {
                if (interfaceID <= SubstrateOptions.interfaceHashingMaxId()) {
                    numHashedInterfaces++;
                }
            }

            int[] hashedInterfaces = new int[numHashedInterfaces];

            // collect hashable interfaces
            int hashedIdx = 0;
            for (int interfaceID : interfaceIDs) {
                if (interfaceID <= SubstrateOptions.interfaceHashingMaxId()) {
                    hashedInterfaces[hashedIdx++] = interfaceID;
                }
            }

            // calculate hash parameter for hashable interfaces
            hashParam = DynamicHubTypeCheckUtil.hashParam(hashedInterfaces);
            hashTable = new int[(hashParam & HASHING_PARAM_MASK) + 1];
        }

        // Remaining interfaces that are not covered by the hash table and need to be iterated.
        short numIterableInterfaces = (short) (interfaceIDs.length - numHashedInterfaces);

        int numClassTypes = typeHierarchy.length;
        int[] openTypeWorldTypeCheckSlots = new int[numClassTypes + numIterableInterfaces * 2];
        System.arraycopy(typeHierarchy, 0, openTypeWorldTypeCheckSlots, 0, numClassTypes);

        int iterableInterfaceIdx = 0;
        for (int interfaceIdx = 0; interfaceIdx < interfaceIDs.length; interfaceIdx++) {
            int interfaceID = interfaceIDs[interfaceIdx];
            if (useInterfaceHashing && interfaceID <= SubstrateOptions.interfaceHashingMaxId()) {
                int offset = implementsMethods ? Math.toIntExact(vTableBaseOffset + iTableStartingOffsets[interfaceIdx] * vTableEntrySize) : Short.MIN_VALUE;
                GraalError.guarantee(NumUtil.isShort(offset), "ItableDynamicHubOffset cannot be encoded as a short. Try -H:-UseInterfaceHashing.");
                hashTable[DynamicHubTypeCheckUtil.hash(interfaceID, hashParam)] = ((offset << HASHING_ITABLE_SHIFT) | interfaceID);
            } else {
                int offset = implementsMethods ? Math.toIntExact(vTableBaseOffset + iTableStartingOffsets[interfaceIdx] * vTableEntrySize) : 0xBADD0D1D;
                openTypeWorldTypeCheckSlots[iterableInterfaceIdx * 2 + numClassTypes] = interfaceID;
                openTypeWorldTypeCheckSlots[iterableInterfaceIdx * 2 + numClassTypes + 1] = offset;
                iterableInterfaceIdx += 1;
            }
        }

        GraalError.guarantee(iterableInterfaceIdx == numIterableInterfaces, "Computation of interface type check data failed.");
        return new TypeCheckData(openTypeWorldTypeCheckSlots, hashTable, hashParam, numIterableInterfaces);
    }

    /**
     * Compute the hash for a given value. This is done by bitwise {@code &} and an (optional)
     * shift. See {@link #hashParam(int[])} for details on the hash parameter computation.
     *
     * @param val the value to be hashed
     * @param hashParam the parameter to be used for hashing
     */
    public static int hash(int val, int hashParam) {
        int shift = hashParam >>> HASHING_SHIFT_OFFSET;
        return (val >>> shift) & (hashParam & HASHING_PARAM_MASK);
    }

    /**
     * Computes a hashing parameter {@code hashParam = shift << HASHING_SHIFT_OFFSET | p}, such that
     * {@code (val>>>shift) & p} is injective (i.e., collision-free) for each value in {@code vals}.
     * The hash table will have size {@code p + 1} which is why a minimal hash parameter is
     * computed.
     */
    public static int hashParam(int[] vals) {
        if (vals.length <= 1) {
            return 0;
        }

        /*
         * 1) Keep only discriminative bits, i.e., bits that are not the same in all values.
         * discriminativeBits(0011, 1001) = 1010 --> initial hashParam = 1010.
         */
        int or = vals[0];
        int and = vals[0];

        for (int i = 1; i < vals.length; i++) {
            assert vals[i] != 0 : "Value to hash must not be 0 to be distinct from empty hash table entries.";
            or |= vals[i];
            and &= vals[i];
        }
        int hashParam = or ^ and;

        assert (or & HASHING_INTERFACE_MASK) == or : "Values are too large to be encoded in a hash table.";

        /*
         * 2) Clear bits that are not required for injectivity. E.g., highest bit of 1010 is not
         * needed such that 0011 & 1010 != 1001 & 1010. Updated hashParam = 0010.
         */
        HashSet<Integer> set = new HashSet<>();
        for (int i = 31; i >= 0; i--) {
            int bitI = 1 << i;
            if ((hashParam & bitI) != 0) {
                int hashParamToTest = hashParam & ~bitI;
                if (isValidHashParam(vals, hashParamToTest, set)) {
                    hashParam = hashParamToTest;
                }
                set.clear();
            }
        }

        /*
         * 3) Calculate the number of trailing 0s. This can be used to shift the hash parameter,
         * making it smaller in the process. This changes the hash function to: (id>>>shift) & p.
         * Updated hashParam = 0001 with shift = 1.
         */
        int shift = 0;
        for (int i = 0; i < 31; i++) {
            if ((hashParam & (1 << i)) == 0) {
                shift++;
            } else {
                break;
            }
        }

        assert (hashParam & HASHING_PARAM_MASK) == hashParam : "Hash parameter is too large to be encoded.";
        assert (shift << HASHING_SHIFT_OFFSET) >>> HASHING_SHIFT_OFFSET == shift : "Shift is too large to be encoded: " + shift;

        return hashParam == 0 ? hashParam : (shift << HASHING_SHIFT_OFFSET) | (hashParam >>> shift);
    }

    /**
     * Tests if the given hash parameter is perfect for the given set of {@code vals}. This means
     * that {@link #hash(int, int)} will not produce any collisions for {@code hashParam} and
     * {@code vals}.
     */
    private static boolean isValidHashParam(int[] vals, int hashParam, Set<Integer> tmp) {
        for (int v : vals) {
            int hash = hash(v, hashParam);
            if (tmp.contains(hash)) {
                return false;
            }
            tmp.add(hash);
        }
        return true;
    }
}
