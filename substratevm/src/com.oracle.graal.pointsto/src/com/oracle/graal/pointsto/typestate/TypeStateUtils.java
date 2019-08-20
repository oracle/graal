/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.common.JVMCIError;

/** Helper methods for type state. */
public class TypeStateUtils {

    private static final MethodHandle bitSetArrayAccess;
    private static final MethodHandle trimToSizeAccess;
    static {
        try {
            bitSetArrayAccess = MethodHandles.lookup().unreflectGetter(ReflectionUtil.lookupField(BitSet.class, "words"));
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
    static long[] extractBitSetField(BitSet bitSet) {
        try {
            return (long[]) bitSetArrayAccess.invokeExact(bitSet);
        } catch (Throwable t) {
            throw JVMCIError.shouldNotReachHere(t);
        }
    }

    static void trimBitSetToSize(BitSet bs) {
        try {
            trimToSizeAccess.invokeExact(bs);
        } catch (Throwable t) {
            throw JVMCIError.shouldNotReachHere(t);
        }

    }

    protected static AnalysisObject[] concat(AnalysisObject[] oa1, AnalysisObject[] oa2) {
        int resultSize = oa1.length + oa2.length;

        AnalysisObject[] result = new AnalysisObject[resultSize];

        System.arraycopy(oa1, 0, result, 0, oa1.length);
        System.arraycopy(oa2, 0, result, oa1.length, oa2.length);

        return result;
    }

    /** Returns the union of the two analysis object arrays of the same type. */
    protected static AnalysisObject[] union(BigBang bb, AnalysisObject[] a1, AnalysisObject[] a2) {
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

    private static AnalysisObject[] arraysUnion(BigBang bb, AnalysisObject[] a1, AnalysisObject[] a2) {
        // assert bb.options().allocationSiteSensitiveHeap();
        assert a1.length >= a2.length : "Union is commutative, must call it with a1 being the bigger state";
        assert a1.length > 1 || !bb.analysisPolicy().isSummaryObject(a1[0]);
        assert a2.length > 1 || !bb.analysisPolicy().isSummaryObject(a2[0]);

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
                    assert o1.getId() != o2.getId();
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
                assert idx2 == a2.length;
                objectsList.addAll(Arrays.asList(a1).subList(idx1, a1.length));
            } else if (idx2 < a2.length) {
                assert idx1 == a1.length;
                objectsList.addAll(Arrays.asList(a2).subList(idx2, a2.length));
            }
            return checkUnionSize(bb, a1, a2, objectsList.toArray(new AnalysisObject[objectsList.size()]));
        }

    }

    private static AnalysisObject[] checkUnionSize(BigBang bb, AnalysisObject[] oa1, AnalysisObject[] oa2, AnalysisObject[] result) {
        assert result.length >= 2;

        if (PointstoOptions.LimitObjectArrayLength.getValue(bb.getOptions()) && (result.length > PointstoOptions.MaxObjectSetSize.getValue(bb.getOptions()))) {
            AnalysisObject rObj = result[0].type().getContextInsensitiveAnalysisObject();
            bb.analysisPolicy().noteMerge(bb, oa1);
            bb.analysisPolicy().noteMerge(bb, oa2);
            bb.analysisPolicy().noteMerge(bb, rObj);
            return new AnalysisObject[]{rObj};
        } else {
            return result;
        }
    }

    /* Intersection. */

    /**
     * Returns the intersection of the two analysis object arrays of the same type. If one of them
     * contains a single context insensitive object, the other array is returned.
     */
    protected static AnalysisObject[] intersection(BigBang bb, AnalysisObject[] a1, AnalysisObject[] a2) {
        // assert this.type() == other.type();

        if (a1.length == 1 && a1[0].isContextInsensitiveObject()) {
            return a2;
        } else if (a2.length == 1 && a2[0].isContextInsensitiveObject()) {
            return a1;
        } else {
            if (a1.length <= a2.length) {
                return arraysIntersection(bb, a1, a2);
            } else {
                return arraysIntersection(bb, a2, a1);
            }
        }
    }

    /** Returns a list containing the intersection of the two object arrays. */
    private static AnalysisObject[] arraysIntersection(BigBang bb, AnalysisObject[] a1, AnalysisObject[] a2) {
        assert a1.length <= a2.length : "Intersection is commutative, must call it with a1 being the shorter array";

        if (a1 == a2) {
            return a1;
        }

        /* Speculate that a1 contains no more elements than a2, i.e., the result is a1. */

        int idx1 = 0;
        int idx2 = 0;
        while (idx2 < a2.length) {
            AnalysisObject o1 = a1[idx1];
            AnalysisObject o2 = a2[idx2];

            if (o2.getId() < o1.getId()) {
                idx2++;
            } else if (o1.equals(o2)) {
                /* If the objects are equal continue with speculation. */
                idx1++;
                idx2++;
                if (idx1 == a1.length) {
                    /*
                     * The speculation succeeded: we walked down the whole a1 array and it contained
                     * no more elements than a2.
                     */
                    return a1;
                }
            } else {
                /* The speculation failed. */
                break;
            }
        }

        List<AnalysisObject> rList = new ArrayList<>(a1.length);

        /* Add the beginning of the a1 list that we already walked above. */
        rList.addAll(Arrays.asList(a1).subList(0, idx1));

        while (idx1 < a1.length && idx2 < a2.length) {
            AnalysisObject o1 = a1[idx1];
            AnalysisObject o2 = a2[idx2];

            if (o1.equals(o2)) {
                rList.add(o1);
                idx1++;
                idx2++;
            } else { // keep the list sorted by the id
                assert o1.getId() != o2.getId();
                if (o1.getId() < o2.getId()) {
                    idx1++;
                } else {
                    idx2++;
                }
            }
        }

        /* For intersection the result must be smaller than the operands. */
        assert rList.size() <= a1.length && rList.size() <= a2.length;

        /*
         * If the LimitObjectArrayLength is enabled then the result MUST be smaller than
         * MaxObjectSetSize.
         */
        assert !PointstoOptions.LimitObjectArrayLength.getValue(bb.getOptions()) || rList.size() <= PointstoOptions.MaxObjectSetSize.getValue(bb.getOptions());

        if (rList.size() == 0) {
            return AnalysisObject.EMPTY_ARRAY;
        } else {
            AnalysisObject[] result = rList.toArray(new AnalysisObject[rList.size()]);
            assert !Arrays.equals(result, a1) && !Arrays.equals(result, a2);

            return result;
        }
    }

    /**
     * Check if a type state contains only context insensitive objects, i.e., the only information
     * it stores is the set of types.
     */
    static boolean isContextInsensitiveTypeState(TypeState state) {
        for (AnalysisObject object : state.objects()) {
            if (!object.isContextInsensitiveObject()) {
                return false;
            }
        }
        return true;
    }

    static boolean holdsSingleTypeState(AnalysisObject[] objects) {
        return holdsSingleTypeState(objects, objects.length);
    }

    @SuppressWarnings("RedundantIfStatement")
    static boolean holdsSingleTypeState(AnalysisObject[] objects, int size) {
        assert size > 0;
        int firstType = objects[0].getTypeId();
        int lastType = objects[size - 1].getTypeId();
        if (firstType == lastType) {
            /* Objects are sorted, first and last have the same type, must be single type. */
            return true;
        }
        return false;
    }

    /** Logical OR two bit sets without modifying the source. */
    protected static BitSet or(BitSet bs1, BitSet bs2) {
        BitSet bsr = (BitSet) bs1.clone();
        bsr.or(bs2);
        return bsr;
    }

    /** Logical AND two bit sets without modifying the source. */
    protected static BitSet and(BitSet bs1, BitSet bs2) {
        BitSet bsr = (BitSet) bs1.clone();
        bsr.and(bs2);
        return bsr;
    }

    /**
     * Logical AND-NOT of the two bit sets, i.e., clearing all bits in first operand whose
     * corresponding bits are set in the second one, without modifying the source.
     */
    static BitSet andNot(BitSet bs1, BitSet bs2) {
        BitSet bsr = (BitSet) bs1.clone();
        bsr.andNot(bs2);
        return bsr;
    }

    /**
     * Sets the bit specified by the index to {@code false} without modifying the source.
     */
    protected static BitSet clear(BitSet bs1, int bitIndex) {
        BitSet bsr = (BitSet) bs1.clone();
        bsr.clear(bitIndex);
        return bsr;
    }

    /**
     * Sets the bit specified by the index to {@code true} without modifying the source.
     */
    protected static BitSet set(BitSet bs1, int bitIndex) {
        BitSet bsr = (BitSet) bs1.clone();
        bsr.set(bitIndex);
        return bsr;
    }

}
