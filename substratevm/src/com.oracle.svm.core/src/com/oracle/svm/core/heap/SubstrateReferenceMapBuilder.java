/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.framemap.ReferenceMapBuilder;

import com.oracle.svm.core.CalleeSavedRegisters;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.ReferenceMap;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

public class SubstrateReferenceMapBuilder extends ReferenceMapBuilder {
    private final int totalFrameSize;
    private final SubstrateReferenceMap referenceMap;

    public SubstrateReferenceMapBuilder(int totalFrameSize) {
        this.totalFrameSize = totalFrameSize;
        this.referenceMap = new SubstrateReferenceMap();
    }

    @Override
    public void addLiveValue(Value value) {
        int offset;
        if (ValueUtil.isStackSlot(value)) {
            StackSlot stackSlot = ValueUtil.asStackSlot(value);
            offset = stackSlot.getOffset(totalFrameSize);
            assert referenceMap.debugMarkStackSlot(offset, stackSlot);

        } else if (CalleeSavedRegisters.supportedByPlatform() && ValueUtil.isRegister(value)) {
            Register register = ValueUtil.asRegister(value);
            offset = CalleeSavedRegisters.singleton().getOffsetInFrame(register);
            assert referenceMap.debugMarkRegister(ValueUtil.asRegister(value).number, value);

        } else {
            throw VMError.shouldNotReachHere(value.toString());
        }

        LIRKind kind = (LIRKind) value.getValueKind();
        if (!kind.isValue()) {

            if (kind.isUnknownReference()) {
                throw JVMCIError.shouldNotReachHere("unknown reference alive across safepoint");
            } else if (kind.isDerivedReference()) {
                throw JVMCIError.shouldNotReachHere("derived references not supported yet on Substrate VM");

            } else {
                int bytes = bytesPerElement(kind);
                for (int i = 0; i < kind.getPlatformKind().getVectorLength(); i++) {
                    if (kind.isReference(i)) {
                        boolean compressed = kind.isCompressedReference(i);
                        referenceMap.markReferenceAtOffset(offset + i * bytes, compressed);
                    }
                }
            }
        }
    }

    private static int bytesPerElement(LIRKind kind) {
        PlatformKind platformKind = kind.getPlatformKind();
        return platformKind.getSizeInBytes() / platformKind.getVectorLength();
    }

    @Override
    public ReferenceMap finish(LIRFrameState state) {
        return referenceMap;
    }

}
