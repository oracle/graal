/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.UserError.UserException;

/**
 * Verifies that the heap size options are used consistently. Note that some checks seem redundant
 * at first glance. However, those checks are needed because options don't necessarily have a value.
 */
public final class HeapSizeVerifier {
    private static final String MAX_HEAP_SIZE_NAME = "maximum heap size";
    private static final String MIN_HEAP_SIZE_NAME = "minimum heap size";
    private static final String MAX_NEW_SIZE_NAME = "maximum new generation size";

    /**
     * This method is executed once at build-time and once at runtime-time (after parsing all option
     * values).
     */
    public static void verifyHeapOptions() {
        verifyReservedAddressSpaceSize();
        verifyMaxHeapSize();
        verifyMinHeapSize();
        verifyMaxNewSize();
    }

    private static void verifyReservedAddressSpaceSize() {
        UnsignedWord reservedAddressSpaceSize = WordFactory.unsigned(SubstrateGCOptions.ReservedAddressSpaceSize.getValue());
        verifyAgainstMaxAddressSpaceSize(reservedAddressSpaceSize, "value of the option '" + SubstrateGCOptions.ReservedAddressSpaceSize.getName() + "'");
    }

    private static void verifyMaxHeapSize() {
        UnsignedWord maxHeapSize = WordFactory.unsigned(SubstrateGCOptions.MaxHeapSize.getValue());
        verifyMaxHeapSizeAgainstMaxAddressSpaceSize(maxHeapSize);
        verifyAgainstReservedAddressSpaceSize(maxHeapSize, MAX_HEAP_SIZE_NAME);
    }

    private static void verifyMinHeapSize() {
        UnsignedWord minHeapSize = WordFactory.unsigned(SubstrateGCOptions.MinHeapSize.getValue());
        verifyMinHeapSizeAgainstMaxAddressSpaceSize(minHeapSize);
        verifyAgainstReservedAddressSpaceSize(minHeapSize, MIN_HEAP_SIZE_NAME);

        UnsignedWord maxHeapSize = WordFactory.unsigned(SubstrateGCOptions.MaxHeapSize.getValue());
        if (maxHeapSize.notEqual(0) && minHeapSize.aboveThan(maxHeapSize)) {
            throwError(minHeapSize, MIN_HEAP_SIZE_NAME, maxHeapSize, MAX_HEAP_SIZE_NAME);
        }
    }

    private static void verifyMaxNewSize() {
        UnsignedWord maxNewSize = WordFactory.unsigned(SubstrateGCOptions.MaxNewSize.getValue());
        verifyMaxNewSizeAgainstMaxAddressSpaceSize(maxNewSize);
        verifyAgainstReservedAddressSpaceSize(maxNewSize, MAX_NEW_SIZE_NAME);

        UnsignedWord maxHeapSize = WordFactory.unsigned(SubstrateGCOptions.MaxHeapSize.getValue());
        if (maxHeapSize.notEqual(0) && maxNewSize.aboveThan(maxHeapSize)) {
            throwError(maxNewSize, MAX_NEW_SIZE_NAME, maxHeapSize, MAX_HEAP_SIZE_NAME);
        }
    }

    public static void verifyMinHeapSizeAgainstMaxAddressSpaceSize(UnsignedWord minHeapSize) throws UserException {
        verifyAgainstMaxAddressSpaceSize(minHeapSize, MIN_HEAP_SIZE_NAME);
    }

    public static void verifyMaxHeapSizeAgainstMaxAddressSpaceSize(UnsignedWord maxHeapSize) throws UserException {
        verifyAgainstMaxAddressSpaceSize(maxHeapSize, MAX_HEAP_SIZE_NAME);
    }

    public static void verifyMaxNewSizeAgainstMaxAddressSpaceSize(UnsignedWord maxNewSize) {
        verifyAgainstMaxAddressSpaceSize(maxNewSize, MAX_NEW_SIZE_NAME);
    }

    private static void verifyAgainstMaxAddressSpaceSize(UnsignedWord actualValue, String actualValueName) {
        UnsignedWord maxAddressSpaceSize = ReferenceAccess.singleton().getAddressSpaceSize();
        if (actualValue.aboveThan(maxAddressSpaceSize)) {
            throwError(actualValue, actualValueName, maxAddressSpaceSize, "largest possible heap address space");
        }
    }

    private static void verifyAgainstReservedAddressSpaceSize(UnsignedWord actualValue, String actualValueName) {
        UnsignedWord reservedAddressSpaceSize = WordFactory.unsigned(SubstrateGCOptions.ReservedAddressSpaceSize.getValue());
        if (reservedAddressSpaceSize.notEqual(0) && actualValue.aboveThan(reservedAddressSpaceSize)) {
            throwError(actualValue, actualValueName, reservedAddressSpaceSize, "value of the option '" + SubstrateGCOptions.ReservedAddressSpaceSize.getName() + "'");
        }
    }

    private static void throwError(UnsignedWord actualValue, String actualValueName, UnsignedWord maxValue, String maxValueName) throws UserException {
        if (SubstrateUtil.HOSTED) {
            throw UserError.abort("The specified %s (%s) must not be larger than the %s (%s).", actualValueName, format(actualValue), maxValueName, format(maxValue));
        } else {
            throw new IllegalArgumentException(
                            "The specified " + actualValueName + " (" + format(actualValue) + ") must not be larger than the " + maxValueName + " (" + format(maxValue) + ").");
        }
    }

    private static String format(UnsignedWord bytes) {
        String[] units = {"", "k", "m", "g", "t"};
        int index = 0;
        UnsignedWord value = bytes;
        while (value.unsignedRemainder(1024).equal(0) && index < units.length - 1) {
            value = value.unsignedDivide(1024);
            index++;
        }
        return value.rawValue() + units[index];
    }
}

@AutomaticallyRegisteredFeature
class HostedHeapSizeVerifierFeature implements InternalFeature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        /* At build-time, we can do a GC-independent verification of all the heap size settings. */
        HeapSizeVerifier.verifyHeapOptions();
    }
}
