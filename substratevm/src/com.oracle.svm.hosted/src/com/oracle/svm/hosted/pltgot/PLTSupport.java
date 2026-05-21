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
package com.oracle.svm.hosted.pltgot;

import java.nio.ByteBuffer;

import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.ProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile.RelocationKind;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.hosted.image.NativeImage;
import com.oracle.svm.hosted.image.RelocatableBuffer;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/** Coordinates generating the PLT into the text section for method dispatch through the GOT. */
public class PLTSupport {

    public static final String SVM_PLT_SYMBOL_NAME = "svm_plt";

    public static String pltSymbolNameForMethod(ResolvedJavaMethod method) {
        return "svm_plt_stub_" + NativeImage.localSymbolNameForMethod(method);
    }

    private final PLTStubGenerator stubGenerator;

    private PLTStubGenerator.GeneratedPLT generatedPLT;
    private int pltTextOffset = -1;

    public PLTSupport(PLTStubGenerator stubGenerator) {
        this.stubGenerator = stubGenerator;
    }

    void generatePLT(SharedMethod[] got, SubstrateBackend substrateBackend) {
        assert generatedPLT == null && pltTextOffset == -1;
        generatedPLT = stubGenerator.generatePLT(got, substrateBackend);
    }

    private int getPLTCodeSize() {
        return generatedPLT.buffer().getBackingArray().length;
    }

    public int reserveTextSectionSpace(int textSectionSize) {
        assert pltTextOffset == -1;
        int pltCodeSize = getPLTCodeSize();
        if (pltCodeSize == 0) {
            return textSectionSize;
        }
        pltTextOffset = NumUtil.roundUp(textSectionSize, SubstrateOptions.buildTimeCodeAlignment());
        return pltTextOffset + pltCodeSize;
    }

    public void writeToTextSection(RelocatableBuffer textBuffer, ObjectFile objectFile, ObjectFile.Section textSection) {
        if (getPLTCodeSize() == 0) {
            return;
        }
        RelocatableBuffer pltBuffer = generatedPLT.buffer();
        byte[] pltCode = pltBuffer.getBackingArray();
        ByteBuffer buffer = textBuffer.getByteBuffer();
        assert pltTextOffset >= 0;
        assert pltTextOffset + pltCode.length <= buffer.limit();
        buffer.put(pltTextOffset, pltCode, 0, pltCode.length);

        objectFile.createDefinedSymbol(SVM_PLT_SYMBOL_NAME, textSection, pltTextOffset, 0, true, false, false);

        int wordSize = SubstrateTarget.getWordSize();
        generatedPLT.forEachStubStartOffset((method, offset) -> {
            int position = pltTextOffset + offset;
            boolean internalSymbolsAreGlobal = SubstrateOptions.InternalSymbolsAreGlobal.getValue();
            objectFile.createDefinedSymbol(pltSymbolNameForMethod(method), textSection, position, wordSize, true,
                            internalSymbolsAreGlobal, internalSymbolsAreGlobal);
        });

        pltBuffer.forEachRelocation((info, offset) -> textBuffer.addRelocationWithAddend(pltTextOffset + offset, info.getRelocationKind(), info.getAddend(), info.getTargetObject()));
    }

    void markRelocationToPLTStub(ProgbitsSectionImpl section, int offset, RelocationKind relocationKind, SharedMethod target, long addend) {
        section.markRelocationSite(offset, relocationKind, pltSymbolNameForMethod(target), addend);
    }

    void addMethodPLTStubResolverRelocation(RelocatableBuffer buffer, int offset, RelocationKind relocationKind, SharedMethod target) {
        buffer.addRelocationWithAddend(offset, relocationKind, generatedPLT.getResolverEntryDisplacement(target), new MethodPointer(target));
    }

    public int getMethodPLTStubCodeAddressOffset(SharedMethod method) {
        assert pltTextOffset >= 0;
        int stubOffset = generatedPLT.getStubStartOffset(method);
        assert stubOffset >= 0;
        return pltTextOffset + stubOffset;
    }
}
