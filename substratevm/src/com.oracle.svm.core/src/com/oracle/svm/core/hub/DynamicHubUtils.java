/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.Arrays;
import java.util.BitSet;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.collections.EconomicSet;

import com.oracle.svm.shared.AlwaysInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.graal.snippets.OpenTypeWorldDispatchTableSnippets;
import com.oracle.svm.guest.staging.hub.DynamicHubSupport;
import com.oracle.svm.guest.staging.core.heap.InstanceReferenceMapDecoder.InstanceReferenceMap;
import com.oracle.svm.jvmci.shared.meta.SharedType;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.DuplicatedInNativeCode;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Contains utilities for interacting with DynamicHubs, such as converting vtable indexes to
 * dispatch table offsets and calculating metadata needed for typecheck computations.
 */
public final class DynamicHubUtils {

    /** Offset stored for interface membership entries that have no dispatch table. */
    static final int INVALID_ITABLE_OFFSET = 0xBADD0D1D;

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setMetaType(DynamicHub hub, SharedType metaType) {
        assert hub.getCompanion().metaType == null;
        hub.getCompanion().metaType = (DynamicHubProvider) metaType;
    }

    public static SharedType getMetaType(DynamicHub hub) {
        return (SharedType) hub.getCompanion().metaType;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static ResolvedJavaType getInterpreterType(DynamicHub hub) {
        return (ResolvedJavaType) hub.getCompanion().interpreterType;
    }

    public static void setInterpreterType(DynamicHub hub, ResolvedJavaType interpreterType) {
        hub.getCompanion().interpreterType = (DynamicHubProvider) interpreterType;
    }

    public static DynamicHub getHub(SharedType type) {
        return ((DynamicHubProvider) type).getHub();
    }

    /**
     * Temporary forwarding method while {@code DynamicHub} remains core-owned for GR-78110.
     * Remove this wrapper and use {@link DynamicHubSupport#getInstanceReferenceMap(int)} from the
     * migrated hub-taking API when {@code DynamicHub} moves to guest staging.
     */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static InstanceReferenceMap getInstanceReferenceMap(DynamicHub hub) {
        return DynamicHubSupport.getInstanceReferenceMap(hub.getReferenceMapCompressedOffset());
    }

    /**
     * Temporary forwarding method while {@code DynamicHub} remains core-owned for GR-78110.
     * Remove this wrapper and use {@link DynamicHubSupport#hasEmptyReferenceMap(int)} from the
     * migrated hub-taking API when {@code DynamicHub} moves to guest staging.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean hasEmptyReferenceMap(DynamicHub hub) {
        return DynamicHubSupport.hasEmptyReferenceMap(hub.getReferenceMapCompressedOffset());
    }

    // Constants for interface hashing

    /**
     * The offset of the shift inside the shift-and-mask hash parameter (not used with PEXT).
     * {@code shift = hashParam >>> offset}. See {@link #hashParam(int[])} for details.
     */
    @DuplicatedInNativeCode //
    public static final int HASHING_SHIFT_OFFSET = 24;

    /**
     * Width of the mask in the packed shift-and-mask hash parameter. PEXT uses a full 32-bit mask.
     */
    public static final int INTERFACE_HASH_MASK_BITS = 16;

    /**
     * The bitmask that extracts the mask from the shift-and-mask hash parameter.
     * {@code p = hashParam & bitmask}. See {@link #hashParam(int[])} for details.
     * A constant expression lets option defaults use this value without initializing this class.
     */
    public static final int HASHING_PARAM_MASK = (1 << INTERFACE_HASH_MASK_BITS) - 1;

    /**
     * The byte offset occupies the upper 32 bits of each long hash table entry:
     * {@code ((long) iTableOffset << HASHING_ITABLE_SHIFT) | interfaceID}. The low 32 bits
     * contain the interface ID. Keeping the offset in the upper word permits an aligned 32-bit
     * load for unchecked dispatch.
     */
    public static final int HASHING_ITABLE_SHIFT = Integer.SIZE;

    /**
     * Construction limit that guarantees every eligible ID set has a collision-free
     * shift-and-mask hash: retaining all mask bits always suffices. PEXT construction currently
     * starts from that hash and inherits this limit, despite supporting a full 32-bit mask.
     * This is not a limit on the 32-bit ID field in a table entry or on supported interface IDs.
     * {@link SubstrateOptions#InterfaceHashingMaxId} may further restrict hashing eligibility;
     * IDs above the configured cutoff use iterative lookup.
     */
    @DuplicatedInNativeCode //
    public static final int MAX_HASHED_INTERFACE_ID = HASHING_PARAM_MASK;

    public record TypeCheckData(int[] openTypeWorldTypeCheckSlots, long[] openTypeWorldInterfaceHashTable, int openTypeWorldInterfaceHashParam, short numIterableInterfaces) {
    }

    /**
     * Computes type check data for {@link DynamicHub}s in the open type world.
     * <p>
     * With {@link SubstrateOptions#useInterfaceHashing()} disabled,
     * {@link DynamicHub#getOpenTypeWorldTypeCheckSlots() openTypeWorldTypeCheckSlots} contain two
     * entries per interface after the class-display prefix, one for the interface ID and one for
     * the itable byte offset:
     *
     * <pre>
     * checkSlots = [...typeIDs...; interfaceID_0 ; itableOffset_0 ; ... ]
     * </pre>
     * <p>
     * Interface type checks and resolving interface method calls require iterating over these pairs.
     * <p>
     * With {@link SubstrateOptions#useInterfaceHashing()} enabled,
     * interfaces whose IDs are at most {@link SubstrateOptions#interfaceHashingMaxId()} are
     * represented in a hash table. Remaining interfaces retain their pairs in the checkSlots
     * array. If all interfaces are hashed, only the class-display prefix remains:
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
     * hashTable[hash(interfaceID)] = ((long) iTableOffset << {@link #HASHING_ITABLE_SHIFT}) | interfaceID
     * </pre>
     * <p>
     * Each long entry holds the interface ID in its low 32 bits and the full byte offset in its
     * upper 32 bits. Hashing remains limited to unsigned 16-bit IDs, so bits 16 through 31 are
     * zero. Interface IDs are positive, so zero denotes an empty bucket. Both hosted and
     * runtime-loaded hubs use the same encoding.
     * <p>
     * When {@code implementsMethods} is false, entries still describe interface membership, but
     * their offsets are sentinels and must not be used for dispatch.
     * <p>
     * {@link #hash(int, int)} uses the image-wide choice between shift-and-mask and PEXT hashing.
     * With PEXT, the hash parameter is a bit selection mask and the table has
     * {@code 1 << Integer.bitCount(mask)} entries. For example, selecting bits with
     * {@code 0b10101} needs eight buckets instead of the shift-and-mask table's 22. Empty buckets
     * remain present so that queries for absent interfaces are always in bounds.
     */
    public static TypeCheckData computeOpenTypeWorldTypeCheckData(boolean implementsMethods, int[] typeHierarchy, int[] interfaceIDs, int[] iTableStartingOffsets, long vTableBaseOffset,
                    long vTableEntrySize) {
        return computeOpenTypeWorldTypeCheckData(implementsMethods, typeHierarchy, interfaceIDs, iTableStartingOffsets, vTableBaseOffset, vTableEntrySize,
                        SubstrateOptions.useInterfaceHashing(), SubstrateOptions.interfaceHashingMaxId(), SubstrateOptions.usePextInterfaceHashing());
    }

    static TypeCheckData computeOpenTypeWorldTypeCheckData(boolean implementsMethods, int[] typeHierarchy, int[] interfaceIDs, int[] iTableStartingOffsets, long vTableBaseOffset,
                    long vTableEntrySize, boolean useInterfaceHashing, int interfaceHashingMaxId, boolean usePext) {
        int hashParam = 0;
        long[] hashTable = null;

        int numHashedInterfaces = 0;

        if (useInterfaceHashing) {
            // Count interfaces whose IDs are covered by hashing.
            for (int interfaceID : interfaceIDs) {
                GraalError.guarantee(interfaceID > 0, "Interface IDs are expected to be strictly positive");
                if (interfaceID <= interfaceHashingMaxId) {
                    numHashedInterfaces++;
                }
            }

            int[] hashedInterfaces = new int[numHashedInterfaces];

            // collect hashable interfaces
            int hashedIdx = 0;
            for (int interfaceID : interfaceIDs) {
                if (interfaceID <= interfaceHashingMaxId) {
                    hashedInterfaces[hashedIdx++] = interfaceID;
                }
            }

            // calculate hash parameter for hashable interfaces
            hashParam = DynamicHubUtils.hashParam(hashedInterfaces);
            if (usePext) {
                hashParam = (hashParam & HASHING_PARAM_MASK) << (hashParam >>> HASHING_SHIFT_OFFSET);
                // Bound candidate masks and total ID checks, including during runtime class loading.
                // These limits affect compactness, not correctness: budget exhaustion retains the
                // existing injective mask, which may require a larger table.
                hashParam = minimizeCompressedHashMask(hashedInterfaces, hashParam, 4096, 65536);
            }
            // Reserve every possible index, including queries for interfaces not implemented here.
            hashTable = new long[usePext ? 1 << Integer.bitCount(hashParam) : (hashParam & HASHING_PARAM_MASK) + 1];
        }

        // Remaining interfaces that are not covered by the hash table and need to be iterated.
        short numIterableInterfaces = (short) (interfaceIDs.length - numHashedInterfaces);

        int numClassTypes = typeHierarchy.length;
        int[] openTypeWorldTypeCheckSlots = new int[numClassTypes + numIterableInterfaces * 2];
        System.arraycopy(typeHierarchy, 0, openTypeWorldTypeCheckSlots, 0, numClassTypes);

        int iterableInterfaceIdx = 0;
        for (int interfaceIdx = 0; interfaceIdx < interfaceIDs.length; interfaceIdx++) {
            int interfaceID = interfaceIDs[interfaceIdx];
            int offset = implementsMethods ? Math.toIntExact(vTableBaseOffset + iTableStartingOffsets[interfaceIdx] * vTableEntrySize) : INVALID_ITABLE_OFFSET;
            if (useInterfaceHashing && interfaceID <= interfaceHashingMaxId) {
                hashTable[hash(interfaceID, hashParam, usePext)] = (((long) offset << HASHING_ITABLE_SHIFT) | interfaceID);
            } else {
                openTypeWorldTypeCheckSlots[iterableInterfaceIdx * 2 + numClassTypes] = interfaceID;
                openTypeWorldTypeCheckSlots[iterableInterfaceIdx * 2 + numClassTypes + 1] = offset;
                iterableInterfaceIdx += 1;
            }
        }

        GraalError.guarantee(iterableInterfaceIdx == numIterableInterfaces, "Computation of interface type check data failed.");
        return new TypeCheckData(openTypeWorldTypeCheckSlots, hashTable, hashParam, numIterableInterfaces);
    }

    /**
     * Computes the hash using the image's fixed table format. With PEXT, {@code hashParam} is a
     * selection mask in the original interface ID's bit positions, and compression packs those
     * bits contiguously. Otherwise it encodes a shift and mask as described in {@link #hashParam(int[])}.
     *
     * @param val the unsigned 16-bit interface ID to be hashed
     * @param hashParam the parameter to be used for hashing
     */
    @AlwaysInline("Fold the image-wide interface hash format into each lookup")
    public static int hash(int val, int hashParam) {
        return hash(val, hashParam, SubstrateOptions.usePextInterfaceHashing());
    }

    @AlwaysInline("Fold the interface hash format into each lookup")
    static int hash(int val, int hashParam, boolean usePext) {
        if (usePext) {
            return Integer.compress(val, hashParam);
        }
        int shift = hashParam >>> HASHING_SHIFT_OFFSET;
        // Interface IDs fit in 16 bits, so the encoded shift bits cannot affect the result.
        return (val >>> shift) & hashParam;
    }

    /**
     * Computes a hashing parameter {@code hashParam = shift << HASHING_SHIFT_OFFSET | p}, such that
     * {@code (val>>>shift) & p} is injective (i.e., collision-free) for each value in {@code vals}.
     * The hash table will have size {@code p + 1}. Greedily removing high bits keeps this size
     * small, but a larger unshifted mask can sometimes yield a smaller table after shifting.
     * Therefore, also try discarding low bits before minimizing the mask. This only affects
     * metadata construction; lookups in shift-and-mask tables still use the same hash function.
     */
    public static int hashParam(int[] vals) {
        if (vals.length <= 1) {
            return 0;
        }

        /*
         * 1) Keep only discriminative bits, i.e., bits that are not the same in all values.
         * discriminativeBits(0011, 1001) = 1010 --> initial hashParam = 1010.
         */
        int discriminativeBits = discriminativeBits(vals);

        /*
         * 2) Clear bits that are not required for injectivity. E.g., highest bit of 1010 is not
         * needed such that 0011 & 1010 != 1001 & 1010. Updated hashParam = 0010.
         */
        EconomicSet<Integer> set = EconomicSet.create();
        int hashParam = minimizeHashMask(vals, discriminativeBits, set);

        /*
         * 3) Calculate the number of trailing 0s. This can be used to shift the hash parameter,
         * making it smaller in the process. This changes the hash function to: (id>>>shift) & p.
         * Updated hashParam = 0001 with shift = 1.
         */
        int shift = Integer.numberOfTrailingZeros(hashParam);
        int bestMask = hashParam >>> shift;
        int bestShift = shift;

        // A bit-selection hash needs at least ceil(log2(n)) bits. Skip the extra search if the
        // first mask already reaches that lower bound, as is common for small interface sets.
        int minimumTableSize = Integer.highestOneBit(2 * vals.length - 1);
        for (int minimumShift = shift + 1; minimumShift < INTERFACE_HASH_MASK_BITS && bestMask + 1 > minimumTableSize; minimumShift++) {
            int candidate = discriminativeBits & (-1 << minimumShift);
            if (!isValidHashParam(vals, candidate, set)) {
                // Discarding more low bits cannot separate values that already collide.
                break;
            }
            candidate = minimizeHashMask(vals, candidate, set);
            int candidateShift = Integer.numberOfTrailingZeros(candidate);
            int candidateMask = candidate >>> candidateShift;
            if (candidateMask < bestMask) {
                bestMask = candidateMask;
                bestShift = candidateShift;
            }
        }

        assert (bestMask & HASHING_PARAM_MASK) == bestMask : "Hash parameter is too large to be encoded.";
        assert (bestShift << HASHING_SHIFT_OFFSET) >>> HASHING_SHIFT_OFFSET == bestShift : "Shift is too large to be encoded: " + bestShift;

        return (bestShift << HASHING_SHIFT_OFFSET) | bestMask;
    }

    private static int minimizeHashMask(int[] vals, int initialMask, EconomicSet<Integer> tmp) {
        int mask = initialMask;
        for (int bit = Integer.highestOneBit(mask); bit != 0; bit >>>= 1) {
            if ((mask & bit) != 0) {
                int candidate = mask & ~bit;
                if (isValidHashParam(vals, candidate, tmp)) {
                    mask = candidate;
                }
            }
        }
        return mask;
    }

    /**
     * Tries masks with fewer bits, starting at the information-theoretic lower bound. Unlike the
     * shift-and-mask hash, compressed table size depends on the number of bits, not their positions.
     * The existing injective mask is retained if the search budget is exhausted. Bounding both
     * candidate masks and individual ID checks also limits work for runtime-loaded classes.
     */
    static int minimizeCompressedHashMask(int[] vals, int fallbackMask, int candidateBudget, int valueBudget) {
        if (vals.length <= 1) {
            return fallbackMask;
        }
        int minimumBits = Integer.SIZE - Integer.numberOfLeadingZeros(vals.length - 1);
        int fallbackBits = Integer.bitCount(fallbackMask);
        if (minimumBits == fallbackBits) {
            return fallbackMask;
        }
        // Stable search results preserve sharing even when interface enumeration order differs.
        int[] sorted = vals.clone();
        Arrays.sort(sorted);
        int discriminativeBits = discriminativeBits(sorted);
        int limit = 1 << Integer.bitCount(discriminativeBits);
        BitSet seen = new BitSet();
        int remainingCandidates = candidateBudget;
        int remainingValues = valueBudget;
        for (int bits = minimumBits; bits < fallbackBits; bits++) {
            // Enumerate fixed-size subsets, then map them onto the discriminative bit positions.
            int subset = (1 << bits) - 1;
            while (subset < limit) {
                if (remainingCandidates-- <= 0) {
                    return fallbackMask;
                }
                int candidate = Integer.expand(subset, discriminativeBits);
                seen.clear();
                boolean valid = true;
                for (int val : sorted) {
                    if (remainingValues-- <= 0) {
                        return fallbackMask;
                    }
                    int index = Integer.compress(val, candidate);
                    if (seen.get(index)) {
                        valid = false;
                        break;
                    }
                    seen.set(index);
                }
                if (valid) {
                    return candidate;
                }
                // Advance to the next integer with the same number of set bits.
                int lowestBit = Integer.lowestOneBit(subset);
                int next = subset + lowestBit;
                subset = next | (((next ^ subset) >>> 2) / lowestBit);
            }
        }
        return fallbackMask;
    }

    /** Returns the bit positions that distinguish the interface IDs. */
    private static int discriminativeBits(int[] vals) {
        int or = 0;
        int and = -1;
        for (int val : vals) {
            assert val > 0 && val <= MAX_HASHED_INTERFACE_ID : "Interface IDs exceed the supported hashing range.";
            or |= val;
            and &= val;
        }
        return or ^ and;
    }

    /**
     * Tests if the given hash parameter is perfect for the given set of {@code vals}. This means
     * that masking the values will not produce any collisions. Search masks do not encode a shift
     * and must not be interpreted using the image's selected table format.
     */
    private static boolean isValidHashParam(int[] vals, int hashParam, EconomicSet<Integer> tmp) {
        tmp.clear();
        for (int v : vals) {
            if (!tmp.add(v & hashParam)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Calculates the offset of a virtual call when the receiver is of type {@code thisHub} and the
     * target method's declaring class is of type {@code callTargetHub}.
     */
    public static int determineDispatchTableOffset(DynamicHub thisHub, DynamicHub callTargetHub, int vTableIndex) {
        if (SubstrateOptions.useClosedTypeWorldHubLayout() || !callTargetHub.isInterface()) {
            return KnownOffsets.singleton().getVTableOffset(vTableIndex, true);
        } else {
            int indexOffset = KnownOffsets.singleton().getVTableOffset(vTableIndex, false);
            return NumUtil.safeToInt(indexOffset + OpenTypeWorldDispatchTableSnippets.determineITableStartingOffset(thisHub, callTargetHub.getInterfaceID()));
        }
    }
}
