/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.typestate;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.stream.StreamSupport;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.common.JVMCIError;

/** Helper methods for type state. */
public class TypeStateUtils {

    private static final MethodHandle bitSetArrayAccess;
    private static final MethodHandle wordInUseAccess;
    private static final MethodHandle sizeIsStickyAccess;
    private static final MethodHandle trimToSizeAccess;
    static {
        try {
            bitSetArrayAccess = MethodHandles.lookup().unreflectGetter(ReflectionUtil.lookupField(BitSet.class, "words"));
            wordInUseAccess = MethodHandles.lookup().unreflectGetter(ReflectionUtil.lookupField(BitSet.class, "wordsInUse"));
            sizeIsStickyAccess = MethodHandles.lookup().unreflectGetter(ReflectionUtil.lookupField(BitSet.class, "sizeIsSticky"));
            trimToSizeAccess = MethodHandles.lookup().unreflect(ReflectionUtil.lookupMethod(BitSet.class, "trimToSize"));
        } catch (IllegalAccessException t) {
            throw JVMCIError.shouldNotReachHere(t);
        }
    }

    /**
     * This method gives access to the java.lang.BitSet's array of bytes. We need that array for
     * performance reasons (~30% reduction in GC pressure) so we use reflection to get the value of
     * the array.
     *
     * @param bitSet to be intruded
     * @return the array belonging to the bitSet. Please use this value responsibly: not modify or
     *         loose track of this value.
     */
    public static long[] extractBitSetField(BitSet bitSet) {
        try {
            return (long[]) bitSetArrayAccess.invokeExact(bitSet);
        } catch (Throwable t) {
            throw JVMCIError.shouldNotReachHere(t);
        }
    }

    public static int getWordsInUse(BitSet bitSet) {
        try {
            return (int) wordInUseAccess.invokeExact(bitSet);
        } catch (Throwable t) {
            throw JVMCIError.shouldNotReachHere(t);
        }
    }

    public static boolean getSizeIsSticky(BitSet bitSet) {
        try {
            return (boolean) sizeIsStickyAccess.invokeExact(bitSet);
        } catch (Throwable t) {
            throw JVMCIError.shouldNotReachHere(t);
        }
    }

    public static boolean needsTrim(BitSet bitSet) {
        int wordsInUse = getWordsInUse(bitSet);
        long[] words = extractBitSetField(bitSet);
        return wordsInUse != words.length;
    }

    private static void trimBitSetToSize(BitSet bs) {
        try {
            trimToSizeAccess.invokeExact(bs);
        } catch (Throwable t) {
            throw JVMCIError.shouldNotReachHere(t);
        }
    }

    /** Return true if {@code first} is a superset of {@code second}. */
    public static boolean isSuperset(BitSet first, BitSet second) {
        if (first.length() >= second.length()) {
            long[] bits1 = TypeStateUtils.extractBitSetField(first);
            long[] bits2 = TypeStateUtils.extractBitSetField(second);

            boolean isSuperset = true;
            int numberOfWords = Math.min(bits1.length, bits2.length);
            for (int i = 0; i < numberOfWords; i++) {
                /* bits2 is a subset of bits1 */
                if ((bits1[i] & bits2[i]) != bits2[i]) {
                    isSuperset = false;
                    break;
                }
            }
            return isSuperset;
        }
        return false;
    }

    public static AnalysisObject[] concat(AnalysisObject[] oa1, AnalysisObject[] oa2) {
        int resultSize = oa1.length + oa2.length;

        AnalysisObject[] result = new AnalysisObject[resultSize];

        System.arraycopy(oa1, 0, result, 0, oa1.length);
        System.arraycopy(oa2, 0, result, oa1.length, oa2.length);

        return result;
    }

    /** Returns the union of the two analysis object arrays of the same type. */
    public static AnalysisObject[] union(PointsToAnalysis bb, AnalysisObject[] a1, AnalysisObject[] a2) {
        // assert this.type() == other.type();

        if (a1.length == 1 && bb.analysisPolicy().isSummaryObject(a1[0])) {
            /* Signal merge with the context insensitive object. */
            bb.analysisPolicy().noteMerge(bb, a1);
            bb.analysisPolicy().noteMerge(bb, a2);
            return a1;
        } else if (a2.length == 1 && bb.analysisPolicy().isSummaryObject(a2[0])) {
            /* Signal merge with the context insensitive object. */
            bb.analysisPolicy().noteMerge(bb, a1);
            bb.analysisPolicy().noteMerge(bb, a2);
            return a2;
        } else {
            if (a1.length >= a2.length) {
                return arraysUnion(bb, a1, a2);
            } else {
                return arraysUnion(bb, a2, a1);
            }
        }
    }

    private static AnalysisObject[] arraysUnion(PointsToAnalysis bb, AnalysisObject[] a1, AnalysisObject[] a2) {
        // assert bb.options().allocationSiteSensitiveHeap();
        assert a1.length >= a2.length : "Union is commutative, must call it with a1 being the bigger state";
        assert a1.length > 1 || !bb.analysisPolicy().isSummaryObject(a1[0]) : a1;
        assert a2.length > 1 || !bb.analysisPolicy().isSummaryObject(a2[0]) : a2;

        // TOOD check same type
        // assert !bb.options().extendedAsserts() || (a1.checkState(bb.options()) &&
        // a2.checkState(bb.options()));

        if (a1 == a2) {
            return a1;
        }

        if (a1[a1.length - 1].getId() < a2[0].getId()) {
            /*
             * Speculate that objects in a2 follow after objects in a1. Many times a2 contains a
             * single object which is 'newer' than objects in a1.
             */
            return checkUnionSize(bb, a1, a2, concat(a1, a2));
        } else if (a2[a2.length - 1].getId() < a1[0].getId()) {
            /*
             * Speculate that objects in a1 follow after objects in a2.
             */
            return checkUnionSize(bb, a1, a2, concat(a2, a1));
        } else {
            /* Speculate that a1 already contains all elements of a2, i.e., the result is a1. */
            int idx1 = 0;
            int idx2 = 0;
            while (idx1 < a1.length) {
                AnalysisObject o1 = a1[idx1];
                AnalysisObject o2 = a2[idx2];

                if (o1.getId() < o2.getId()) {
                    idx1++;
                } else if (o1 == o2) {
                    idx1++;
                    idx2++;
                    if (idx2 == a2.length) {
                        /*
                         * The speculation succeeded: we walked down the whole a2 array, and all of
                         * its element were already included in a1.
                         */
                        return a1;
                    }
                } else {
                    /* The speculation failed. */
                    break;
                }
            }

            List<AnalysisObject> objectsList = new ArrayList<>(a1.length + a2.length);

            /* Add the beginning of the a1 list that we already walked above. */
            objectsList.addAll(Arrays.asList(a1).subList(0, idx1));

            while (idx1 < a1.length && idx2 < a2.length) {
                AnalysisObject o1 = a1[idx1];
                AnalysisObject o2 = a2[idx2];

                if (o1.equals(o2)) {
                    objectsList.add(o1);
                    idx1++;
                    idx2++;
                } else { // keep the list sorted by the id
                    assert o1.getId() != o2.getId() : o1 + ", " + o2;
                    if (o1.getId() < o2.getId()) {
                        objectsList.add(o1);
                        idx1++;
                    } else {
                        objectsList.add(o2);
                        idx2++;
                    }
                }
            }

            if (idx1 < a1.length) {
                assert idx2 == a2.length : idx2;
                objectsList.addAll(Arrays.asList(a1).subList(idx1, a1.length));
            } else if (idx2 < a2.length) {
                assert idx1 == a1.length : idx1;
                objectsList.addAll(Arrays.asList(a2).subList(idx2, a2.length));
            }
            return checkUnionSize(bb, a1, a2, objectsList.toArray(new AnalysisObject[objectsList.size()]));
        }

    }

    private static AnalysisObject[] checkUnionSize(PointsToAnalysis bb, AnalysisObject[] oa1, AnalysisObject[] oa2, AnalysisObject[] result) {
        assert result.length >= 2 : result;

        if (bb.analysisPolicy().limitObjectArrayLength() && (result.length > bb.analysisPolicy().maxObjectSetSize())) {
            AnalysisObject rObj = result[0].type().getContextInsensitiveAnalysisObject();
            bb.analysisPolicy().noteMerge(bb, oa1);
            bb.analysisPolicy().noteMerge(bb, oa2);
            bb.analysisPolicy().noteMerge(bb, rObj);
            return new AnalysisObject[]{rObj};
        } else {
            return result;
        }
    }

    /**
     * Check if a type state contains only context insensitive objects, i.e., the only information
     * it stores is the set of types.
     */
    public static boolean isContextInsensitiveTypeState(BigBang bb, TypeState state) {
        for (AnalysisObject object : state.objects(bb)) {
            if (!object.isContextInsensitiveObject()) {
                return false;
            }
        }
        return true;
    }

    public static boolean holdsSingleTypeState(AnalysisObject[] objects) {
        return holdsSingleTypeState(objects, objects.length);
    }

    @SuppressWarnings("RedundantIfStatement")
    public static boolean holdsSingleTypeState(AnalysisObject[] objects, int size) {
        int firstType = objects[0].getTypeId();
        int lastType = objects[size - 1].getTypeId();
        if (firstType == lastType) {
            /* Objects are sorted, first and last have the same type, must be single type. */
            return true;
        }
        return false;
    }

    /** Logical OR two bit sets without modifying the source. */
    public static BitSet or(BitSet bs1, BitSet bs2) {
        /* The result is a clone of the larger set to avoid expanding it when executing the OR. */
        BitSet bsr;
        if (bs1.size() > bs2.size()) {
            bsr = getClone(bs1);
            bsr.or(bs2);
        } else {
            bsr = getClone(bs2);
            bsr.or(bs1);
        }
        assert !needsTrim(bsr) : bsr;
        return bsr;
    }

    /** Logical AND two bit sets without modifying the source. */
    public static BitSet and(BitSet bs1, BitSet bs2) {
        BitSet bsr;
        /* For AND is more efficient to clone the smaller bit set as the tail bits are 0. */
        if (bs1.size() < bs2.size()) {
            bsr = getClone(bs1);
            bsr.and(bs2);
        } else {
            bsr = getClone(bs2);
            bsr.and(bs1);
        }
        /* The result may need a trim after AND. */
        trimBitSetToSize(bsr);
        return bsr;
    }

    /**
     * Logical AND-NOT of the two bit sets, i.e., clearing all bits in first operand whose
     * corresponding bits are set in the second one, without modifying the source.
     */
    public static BitSet andNot(BitSet bs1, BitSet bs2) {
        /* AND-NOT is not commutative, so we cannot optimize based on set size. */
        BitSet bsr = getClone(bs1);
        bsr.andNot(bs2);
        /* The result may need a trim after AND-NOT. */
        trimBitSetToSize(bsr);
        return bsr;
    }

    /**
     * Sets the bit specified by the index to {@code false} without modifying the source.
     */
    public static BitSet clear(BitSet bs1, int bitIndex) {
        BitSet bsr = getClone(bs1);
        bsr.clear(bitIndex);
        /* The result may need a trim after a bit is cleared. */
        trimBitSetToSize(bsr);
        return bsr;
    }

    /**
     * Sets the bit specified by the index to {@code true} without modifying the source.
     */
    public static BitSet set(BitSet bs1, int bitIndex) {
        BitSet bsr;
        int highestSetIndex = bs1.length() - 1;
        /* Check if the new bit index exceeds the capacity of bs1. */
        if (bitIndex > highestSetIndex) {
            /* Preallocate the bit set to represent bitIndex without expansion. */
            bsr = new BitSet(bitIndex + 1);
            /* First add in the original bits, which will System.arraycopy() bs1 bits into bsr. */
            bsr.or(bs1);
            /* ... then set the new index. */
            bsr.set(bitIndex);
            /* Executing the OR first avoids element by element processing from 0 to bitIndex. */
        } else {
            /* The input set can represent bitIndex without expansion. */
            bsr = getClone(bs1);
            bsr.set(bitIndex);
        }
        assert !needsTrim(bsr) : bsr;
        return bsr;
    }

    /** Create a new bit set with the bits of the inputs IDs set. */
    public static BitSet newBitSet(int index1, int index2) {
        /* Preallocate the result bit set to represent index1 and index2 without any expansion. */
        BitSet bs = new BitSet(Math.max(index1, index2) + 1);
        bs.set(index1);
        bs.set(index2);
        assert !needsTrim(bs) : bs;
        return bs;
    }

    /**
     * Make a clone of the input bitSet ensuring the original doesn't get modified in the process.
     * <p>
     * We want to keep the original MultiTypeState.typesBitSet effectively immutable, so we use
     * BitSet.clone() when deriving a new BitSet since the set operations (and, or, etc.) mutate the
     * original object. No calls to mutating methods are made on it after it is set in the
     * MultiTypeState, thus we don't need to use any external synchronization.
     * <p>
     * BitSet.clone() breaks the informal contract that the clone method should not modify the
     * original object; it calls trimToSize() before creating a copy, but only if sizeIsSticky is
     * not set. All the BitSet objects that we create however set sizeIsSticky. We double-check here
     * that the original bitSet will not be mutated and that the clone will not need to be trimmed.
     * <p>
     * Since BitSet is not thread safe mutating it during cloning would be problematic in a
     * multithreaded environment. If for example you iterate over the bits at the same time as
     * another thread calls clone() the words[] array can be in an inconsistent state.
     */
    private static BitSet getClone(BitSet original) {
        assert getSizeIsSticky(original) : original;
        BitSet clone = (BitSet) original.clone();
        assert !needsTrim(clone) : clone;
        return clone;
    }

    public static boolean closeToAllInstantiated(BigBang bb, TypeState state) {
        if (state.isPrimitive()) {
            return false;
        }
        return closeToAllInstantiated(bb, state.typesCount());
    }

    public static boolean closeToAllInstantiated(BigBang bb, List<AnalysisType> state) {
        return closeToAllInstantiated(bb, state.size());
    }

    private static boolean closeToAllInstantiated(BigBang bb, int typeCount) {
        if (typeCount > 200) {
            int allInstCount = (int) StreamSupport.stream(bb.getAllInstantiatedTypes().spliterator(), false).count();
            return typeCount * 100L / allInstCount > 75;
        }
        return false;
    }

}
