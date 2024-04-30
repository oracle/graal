/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.compacting;

import static jdk.graal.compiler.replacements.AllocationSnippets.FillContent.WITH_GARBAGE_IF_ASSERTIONS_ENABLED;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatArrayNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatObjectNode;
import com.oracle.svm.core.heap.FillerObject;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.util.UnsignedUtils;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.vm.ci.meta.JavaKind;

/**
 * Overwrites dead objects with filler objects so that heap walks or scans that use card tables
 * cannot encounter them (and their broken references).
 */
public final class SweepingVisitor implements ObjectMoveInfo.Visitor {
    @Fold
    static int byteArrayMinSize() {
        return NumUtil.safeToInt(ConfigurationValues.getObjectLayout().getArraySize(JavaKind.Byte, 0, false));
    }

    @Fold
    static int byteArrayBaseOffset() {
        return ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.Byte);
    }

    @Override
    public boolean visit(Pointer objSeq, UnsignedWord size, Pointer newAddress, Pointer nextObjSeq) {
        if (nextObjSeq.isNonNull()) {
            Pointer gapStart = objSeq.add(size);
            assert gapStart.belowOrEqual(nextObjSeq);
            if (gapStart.notEqual(nextObjSeq)) {
                writeFillerObjectAt(gapStart, nextObjSeq.subtract(gapStart));
            }
        }
        return true;
    }

    private static void writeFillerObjectAt(Pointer p, UnsignedWord size) {
        assert size.aboveThan(0);
        if (size.aboveOrEqual(byteArrayMinSize())) {
            int length = UnsignedUtils.safeToInt(size.subtract(byteArrayBaseOffset()));
            FormatArrayNode.formatArray(p, byte[].class, length, true, false, WITH_GARBAGE_IF_ASSERTIONS_ENABLED, false);
        } else {
            FormatObjectNode.formatObject(p, FillerObject.class, true, WITH_GARBAGE_IF_ASSERTIONS_ENABLED, false);
        }
        assert LayoutEncoding.getSizeFromObjectInGC(p.toObject()).equal(size);
    }
}
