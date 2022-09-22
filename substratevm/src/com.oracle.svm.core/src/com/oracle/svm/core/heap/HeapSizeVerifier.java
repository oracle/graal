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
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.UserError.UserException;

public final class HeapSizeVerifier {
    public static void verifyHeapOptions() {
        UnsignedWord minHeapSize = WordFactory.unsigned(SubstrateGCOptions.MinHeapSize.getValue());
        UnsignedWord maxHeapSize = WordFactory.unsigned(SubstrateGCOptions.MaxHeapSize.getValue());
        UnsignedWord maxNewSize = WordFactory.unsigned(SubstrateGCOptions.MaxNewSize.getValue());

        verifyMaxHeapSizeAgainstAddressSpace(maxHeapSize);
        verifyMinHeapSizeAgainstAddressSpace(minHeapSize);
        verifyMaxNewSizeAgainstAddressSpace(maxNewSize);
        verifyMinHeapSizeAgainstMaxHeapSize(minHeapSize);
        verifyMaxNewSizeAgainstMaxHeapSize(maxHeapSize);
    }

    public static void verifyMinHeapSizeAgainstAddressSpace(UnsignedWord minHeapSize) throws UserException {
        verifyAgainstAddressSpace(minHeapSize, "minimum heap size");
    }

    public static void verifyMaxHeapSizeAgainstAddressSpace(UnsignedWord maxHeapSize) throws UserException {
        verifyAgainstAddressSpace(maxHeapSize, "maximum heap size");
    }

    public static void verifyMaxNewSizeAgainstAddressSpace(UnsignedWord maxNewSize) {
        verifyAgainstAddressSpace(maxNewSize, "maximum new generation size");
    }

    private static void verifyAgainstAddressSpace(UnsignedWord actualValue, String actualValueName) {
        UnsignedWord addressSpaceSize = ReferenceAccess.singleton().getAddressSpaceSize();
        if (actualValue.aboveThan(addressSpaceSize)) {
            throwError(actualValue, actualValueName, addressSpaceSize, "largest possible address space");
        }
    }

    private static void verifyMinHeapSizeAgainstMaxHeapSize(UnsignedWord minHeapSize) {
        UnsignedWord maxHeapSize = WordFactory.unsigned(SubstrateGCOptions.MaxHeapSize.getValue());
        if (maxHeapSize.notEqual(0) && minHeapSize.aboveThan(maxHeapSize)) {
            throwError(minHeapSize, "minimum heap size", maxHeapSize, "maximum heap size");
        }
    }

    private static void verifyMaxNewSizeAgainstMaxHeapSize(UnsignedWord maxNewSize) {
        UnsignedWord maxHeapSize = WordFactory.unsigned(SubstrateGCOptions.MaxHeapSize.getValue());
        if (maxHeapSize.notEqual(0) && maxNewSize.aboveThan(maxHeapSize)) {
            throwError(maxNewSize, "maximum new generation size", maxHeapSize, "maximum heap size");
        }
    }

    private static void throwError(UnsignedWord actualValue, String actualValueName, UnsignedWord maxValue, String maxValueName) throws UserException {
        if (SubstrateUtil.HOSTED) {
            throw UserError.abort("The specified %s (%s) is larger than the %s (%s).", actualValueName, format(actualValue), maxValueName, format(maxValue));
        } else {
            throw new IllegalArgumentException(
                            "The specified " + actualValueName + " (" + format(actualValue) + ") is larger than the " + maxValueName + " (" + format(maxValue) + ").");
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
        // At build-time, we can do a reasonable GC-independent verification of all the heap size
        // settings.
        HeapSizeVerifier.verifyHeapOptions();
    }
}
