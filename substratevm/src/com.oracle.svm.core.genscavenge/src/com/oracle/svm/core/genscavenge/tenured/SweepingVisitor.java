/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.tenured;

import org.graalvm.word.Pointer;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.heap.FillerObject;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.JavaKind;

public class SweepingVisitor implements RelocationInfo.Visitor {

    private static final int MIN_ARRAY_SIZE = NumUtil.safeToInt(
            ConfigurationValues.getObjectLayout().getArraySize(JavaKind.Byte, 0, false)
    );

    @Override
    public boolean visit(Pointer p) {
        return visitInline(p);
    }

    @AlwaysInline("GC performance")
    @Override
    public boolean visitInline(Pointer p) {
        int size = RelocationInfo.readGapSize(p);
        if (size == 0) {
            return true;
        }

        Pointer gap = p.subtract(size);
        writeFillerObjectAt(gap, size);

        return true;
    }

    private static void writeFillerObjectAt(Pointer p, int size) {
        ObjectHeader objectHeader = Heap.getHeap().getObjectHeader();
        ObjectLayout objectLayout = ConfigurationValues.getObjectLayout();

        if (size >= MIN_ARRAY_SIZE) {
            Word encodedHeader = objectHeader.encodeAsUnmanagedObjectHeader(
                    SubstrateUtil.cast(byte[].class, DynamicHub.class)
            );
            objectHeader.initializeHeaderOfNewObject(p, encodedHeader, true);

            int baseOffset = objectLayout.getArrayBaseOffset(JavaKind.Byte);
            int indexShift = objectLayout.getArrayIndexShift(JavaKind.Byte);
            int length = (size - baseOffset) >> indexShift;
            p.writeInt(ConfigurationValues.getObjectLayout().getArrayLengthOffset(), length);
        } else {
            Word encodedHeader = objectHeader.encodeAsUnmanagedObjectHeader(
                    SubstrateUtil.cast(FillerObject.class, DynamicHub.class)
            );
            objectHeader.initializeHeaderOfNewObject(p, encodedHeader, false);
        }

        if (LayoutEncoding.getSizeFromObjectInGC(p.toObject()).notEqual(size)) {
            Log.log().string("DEBUG: filler object size mismatch")
                    .string(", expected=").signed(size)
                    .string(", actual=").signed(LayoutEncoding.getSizeFromObjectInGC(p.toObject()))
                    .newline().flush();
        }
        VMError.guarantee(
                LayoutEncoding.getSizeFromObjectInGC(p.toObject()).equal(size),
                "Filler object must fill gap completely"
        );
    }
}
