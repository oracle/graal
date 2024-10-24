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

import java.util.HashMap;
import java.util.Map;

import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.ProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile.RelocationKind;
import com.oracle.objectfile.SectionName;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.hosted.image.NativeImage;
import com.oracle.svm.hosted.image.RelocatableBuffer;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class PLTSectionSupport {

    public static final SectionName SVM_PLT_SECTION_NAME = new SectionName.ProgbitsSectionName("svm_plt");

    public static String pltSymbolNameForMethod(ResolvedJavaMethod method) {
        return "svm_plt_" + NativeImage.localSymbolNameForMethod(method);
    }

    private final Map<SharedMethod, Integer> methodPLTStubStart = new HashMap<>();
    private final Map<SharedMethod, Integer> methodPLTStubResolverOffset = new HashMap<>();

    private ProgbitsSectionImpl pltBufferImpl;
    private PLTStubGenerator stubGenerator;

    public PLTSectionSupport(PLTStubGenerator stubGenerator) {
        this.stubGenerator = stubGenerator;
    }

    void createPLTSection(SharedMethod[] got, ObjectFile objectFile, SubstrateBackend substrateBackend) {
        byte[] pltCode = stubGenerator.generatePLT(got, substrateBackend);
        int pltSectionSize = pltCode.length;

        RelocatableBuffer pltBuffer = new RelocatableBuffer(pltSectionSize, objectFile.getByteOrder());
        pltBufferImpl = new BasicProgbitsSectionImpl(pltBuffer.getBackingArray());
        String name = SVM_PLT_SECTION_NAME.getFormatDependentName(objectFile.getFormat());
        ObjectFile.Section pltSection = objectFile.newProgbitsSection(name, objectFile.getPageSize(), false, true, pltBufferImpl);

        pltBuffer.getByteBuffer().put(pltCode, 0, pltSectionSize);

        objectFile.createDefinedSymbol(pltSection.getName(), pltSection, 0, 0, true, false);

        for (SharedMethod method : got) {
            HostedMethod m = (HostedMethod) method;
            int offset = getMethodPLTStubStart(m);
            objectFile.createDefinedSymbol(pltSymbolNameForMethod(m), pltSection, offset, ConfigurationValues.getTarget().wordSize, true,
                            SubstrateOptions.InternalSymbolsAreGlobal.getValue());
        }

    }

    void markRelocationToPLTStub(ProgbitsSectionImpl section, int offset, RelocationKind relocationKind, SharedMethod target, long addend) {
        section.markRelocationSite(offset, relocationKind, pltSymbolNameForMethod(target), addend);
    }

    void markRelocationToPLTResolverJump(ProgbitsSectionImpl section, int offset, RelocationKind relocationKind, SharedMethod target) {
        assert methodPLTStubResolverOffset.get(target) != null : "Trying to mark a relocation to the `resolver-jump` part of the plt stub for a target that doesn't have a plt stub: " + target;
        section.markRelocationSite(offset, relocationKind, pltSymbolNameForMethod(target), methodPLTStubResolverOffset.get(target));
    }

    void markResolverMethodPatch(HostedMethod resolverMethod) {
        stubGenerator.markResolverMethodPatch(pltBufferImpl, resolverMethod);
    }

    public void recordMethodPLTStubStart(SharedMethod method, int offset) {
        methodPLTStubStart.put(method, offset);
    }

    public void recordMethodPLTStubResolverOffset(SharedMethod method, int resolverOffset) {
        methodPLTStubResolverOffset.put(method, resolverOffset);
    }

    private int getMethodPLTStubStart(SharedMethod method) {
        return methodPLTStubStart.get(method);
    }

}
