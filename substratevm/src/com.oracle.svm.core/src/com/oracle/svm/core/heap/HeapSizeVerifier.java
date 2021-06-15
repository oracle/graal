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

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
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

        if (maxHeapSize.notEqual(0) && minHeapSize.aboveThan(maxHeapSize)) {
            throw UserError.abort("The specified minimum heap size (" + format(minHeapSize) + ") is larger than the maximum heap size (" + format(maxHeapSize) + ")");
        }
        if (maxHeapSize.notEqual(0) && maxNewSize.aboveThan(maxHeapSize)) {
            throw UserError.abort("The specified maximum new generation size (" + format(maxNewSize) + ") is larger than the maximum heap size (" + format(maxHeapSize) + ")");
        }
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

    private static void verifyAgainstAddressSpace(UnsignedWord value, String valueName) throws UserException {
        UnsignedWord addressSpaceSize = ReferenceAccess.singleton().getAddressSpaceSize();
        if (value.aboveThan(addressSpaceSize)) {
            throw UserError.abort("The specified " + valueName + " (" + format(value) + ") is larger than the largest possible address space (" + format(addressSpaceSize) + ")");
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

@AutomaticFeature
class HostedHeapSizeVerifierFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        // At build-time, we can do a reasonable GC-independent verification of all the heap size
        // settings. At run-time, we can only do a validation against the address space because we
        // don't have a fixed order in which the options are set.
        HeapSizeVerifier.verifyHeapOptions();
    }
}
