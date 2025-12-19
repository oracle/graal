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
package com.oracle.svm.core.genscavenge;

import static com.oracle.svm.core.genscavenge.AbstractCollectionPolicy.isAligned;

import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.heap.ReferenceAccess;

import jdk.graal.compiler.word.Word;

/**
 * The methods in this class may only be used on {@link RawSizeParameters} that are allocated on the
 * stack and therefore not yet visible to other threads. Please keep the methods in this class to a
 * minimum.
 */
final class RawSizeParametersOnStackAccess {
    static void initialize(RawSizeParameters valuesOnStack,
                    UnsignedWord initialEdenSize, UnsignedWord edenSize, UnsignedWord maxEdenSize,
                    UnsignedWord initialSurvivorSize, UnsignedWord survivorSize, UnsignedWord maxSurvivorSize,
                    UnsignedWord initialOldSize, UnsignedWord oldSize, UnsignedWord maxOldSize,
                    UnsignedWord promoSize,
                    UnsignedWord initialYoungSize, UnsignedWord youngSize, UnsignedWord maxYoungSize,
                    UnsignedWord minHeapSize, UnsignedWord initialHeapSize, UnsignedWord heapSize, UnsignedWord maxHeapSize) {
        assert isAligned(maxHeapSize) && isAligned(maxYoungSize) && isAligned(initialHeapSize) && isAligned(initialEdenSize) && isAligned(initialSurvivorSize);

        assert initialEdenSize.belowOrEqual(initialYoungSize);
        assert edenSize.belowOrEqual(youngSize);
        assert maxEdenSize.belowOrEqual(maxYoungSize);

        assert initialSurvivorSize.belowOrEqual(initialYoungSize);
        assert survivorSize.belowOrEqual(youngSize);
        assert maxSurvivorSize.belowOrEqual(maxYoungSize);

        assert initialOldSize.belowOrEqual(initialHeapSize);
        assert oldSize.belowOrEqual(heapSize);
        assert maxOldSize.belowOrEqual(maxHeapSize);

        assert initialYoungSize.belowOrEqual(initialHeapSize);
        assert youngSize.belowOrEqual(heapSize);
        assert maxYoungSize.belowOrEqual(maxHeapSize);

        assert minHeapSize.belowOrEqual(initialHeapSize);
        assert initialHeapSize.belowOrEqual(maxHeapSize);
        assert heapSize.belowOrEqual(maxHeapSize);
        assert maxHeapSize.belowOrEqual(ReferenceAccess.singleton().getMaxAddressSpaceSize());

        valuesOnStack.setInitialEdenSize(initialEdenSize);
        valuesOnStack.setEdenSize(edenSize);
        valuesOnStack.setMaxEdenSize(maxEdenSize);

        valuesOnStack.setInitialSurvivorSize(initialSurvivorSize);
        valuesOnStack.setSurvivorSize(survivorSize);
        valuesOnStack.setMaxSurvivorSize(maxSurvivorSize);

        valuesOnStack.setInitialYoungSize(initialYoungSize);
        valuesOnStack.setYoungSize(youngSize);
        valuesOnStack.setMaxYoungSize(maxYoungSize);

        valuesOnStack.setInitialOldSize(initialOldSize);
        valuesOnStack.setOldSize(oldSize);
        valuesOnStack.setMaxOldSize(maxOldSize);

        valuesOnStack.setPromoSize(promoSize);

        valuesOnStack.setMinHeapSize(minHeapSize);
        valuesOnStack.setInitialHeapSize(initialHeapSize);
        valuesOnStack.setHeapSize(heapSize);
        valuesOnStack.setMaxHeapSize(maxHeapSize);

        valuesOnStack.setNext(Word.nullPointer());
    }
}
