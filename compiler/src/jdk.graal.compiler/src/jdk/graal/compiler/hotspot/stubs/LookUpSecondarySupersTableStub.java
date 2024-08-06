/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.stubs;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;
import static jdk.graal.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.LEAF_NO_VZERO;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.NO_LOCATIONS;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.NOT_LIKELY_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import org.graalvm.word.WordFactory;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage;
import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.hotspot.word.KlassPointer;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.word.Word;

/**
 * Stub for secondary supers table lookup in the case of collision.
 */
public class LookUpSecondarySupersTableStub extends SnippetStub {

    public static final int SECONDARY_SUPERS_TABLE_SIZE = 64;
    public static final int SECONDARY_SUPERS_TABLE_MASK = SECONDARY_SUPERS_TABLE_SIZE - 1;
    public static final long SECONDARY_SUPERS_BITMAP_FULL = ~0L;

    public static final HotSpotForeignCallDescriptor LOOKUP_SECONDARY_SUPERS_TABLE_SLOW_PATH = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NO_SIDE_EFFECT, NO_LOCATIONS,
                    "lookupSecondarySupersTableSlowPath", boolean.class, KlassPointer.class, Word.class, long.class, long.class);

    public LookUpSecondarySupersTableStub(OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super("lookupSecondarySupersTableSlowPath", options, providers, linkage);
    }

    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/8032d640c0d34fe507392a1d4faa4ff2005c771d/src/hotspot/cpu/x86/macroAssembler_x86.cpp#L4883-L4992",
              sha1 = "555f4e42531f3f1fc32bac28b2f4e3337b42374f")
    // @formatter:on
    @Snippet
    private static boolean lookupSecondarySupersTableSlowPath(KlassPointer t, Word secondarySupers, long bitmap, long index) {
        int length = secondarySupers.readInt(HotSpotReplacementsUtil.metaspaceArrayLengthOffset(INJECTED_VMCONFIG), HotSpotReplacementsUtil.METASPACE_ARRAY_LENGTH_LOCATION);

        if (probability(NOT_FREQUENT_PROBABILITY, bitmap == SECONDARY_SUPERS_BITMAP_FULL)) {
            // Degenerate case: more than 64 secondary supers.
            for (int i = 0; i < length; i++) {
                if (probability(NOT_LIKELY_PROBABILITY, t.equal(loadSecondarySupersElement(secondarySupers, i)))) {
                    return true;
                }
            }
            return false;
        }

        long i = index;
        long currentBitmap = bitmap;
        do {
            // Check for array wraparound.
            if (probability(NOT_LIKELY_PROBABILITY, i >= length)) {
                i = 0;
            }

            if (probability(NOT_LIKELY_PROBABILITY, t.equal(loadSecondarySupersElement(secondarySupers, i)))) {
                return true;
            }

            // This is slightly different from HotSpot stub - we first rotate the bitmap and then
            // test the next bit, to avoid while(true) loop.
            currentBitmap = Long.rotateRight(currentBitmap, 1);
            i++;
        } while (probability(NOT_LIKELY_PROBABILITY, (currentBitmap & 0b10) != 0));

        return false;
    }

    public static KlassPointer loadSecondarySupersElement(Word metaspaceArray, long index) {
        return KlassPointer.fromWord(metaspaceArray.readWord(WordFactory.signed(HotSpotReplacementsUtil.metaspaceArrayBaseOffset(INJECTED_VMCONFIG) + index * HotSpotReplacementsUtil.wordSize()),
                        HotSpotReplacementsUtil.SECONDARY_SUPERS_ELEMENT_LOCATION));
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native boolean lookupSecondarySupersTableStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, KlassPointer t, Word secondarySupers, long bitmap, long index);

    /**
     * Slow path called when there is a collision in the hashed lookup in the secondary supers
     * array. The caller must test the corresponding bit in bitmap for the {@code index}-th element.
     *
     * @param t type being checked
     * @param secondarySupers metaspace array from the receiver class for secondary supers
     * @param bitmap bitmap for the hashed secondary supers, rotated such that the second least
     *            significant bit points to the {@code index}-th element
     * @param index index pointing to the next element in the secondary supers array in the case of
     *            collision
     */
    public static boolean lookupSecondarySupersTableStub(KlassPointer t, Word secondarySupers, long bitmap, long index) {
        return lookupSecondarySupersTableStub(LOOKUP_SECONDARY_SUPERS_TABLE_SLOW_PATH, t, secondarySupers, bitmap, index);
    }
}
