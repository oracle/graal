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

import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.SectionName;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.code.ExplicitCallingConvention;
import com.oracle.svm.core.graal.code.StubCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.pltgot.IdentityMethodAddressResolver;
import com.oracle.svm.core.pltgot.MethodAddressResolver;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.image.NativeImage;
import com.oracle.svm.hosted.image.RelocatableBuffer;

/**
 * An example dynamic method address resolver implementation.
 *
 * The code of all reachable methods is located in the text section of the generated image. This
 * resolver introduces another section ('.svm_methodtable') that contains absolute addresses of
 * methods whose address is dynamically resolved. The methodtable section is the same size as the
 * GOT, and the index of any method is the same in both. This feature registers the
 * {@link IdentityMethodAddressResolver} resolver that, when a method is called for the first time,
 * lookups the absolute method address in the above section. This address is then written in the
 * appropriate GOT entry and is used for subsequent calls of the same method.
 *
 */

public class IdentityMethodAddressResolverFeature implements InternalFeature {

    // Restrict segment names to 16 chars on Mach-O.
    public static final SectionName SVM_METHODTABLE = new SectionName.ProgbitsSectionName("svm_methodtbl");

    private RelocatableBuffer offsetsSectionBuffer;

    private ObjectFile.ProgbitsSectionImpl offsetsSectionBufferImpl;

    protected class IdentityMethodAddressResolverSupport implements MethodAddressResolutionSupport {
        private static boolean isAllowed(SharedMethod method) {
            if (AnnotationAccess.isAnnotationPresent(method, CEntryPoint.class)) {
                return false;
            }
            if (AnnotationAccess.isAnnotationPresent(method, CFunction.class)) {
                return false;
            }
            if (AnnotationAccess.isAnnotationPresent(method, StubCallingConvention.class)) {
                return false;
            }
            if (AnnotationAccess.isAnnotationPresent(method, Uninterruptible.class)) {
                return false;
            }
            if (AnnotationAccess.isAnnotationPresent(method, SubstrateForeignCallTarget.class)) {
                return false;
            }
            if (AnnotationAccess.isAnnotationPresent(method.getDeclaringClass(), InternalVMMethod.class)) {
                return false;
            }
            if (AnnotationAccess.isAnnotationPresent(method, ExplicitCallingConvention.class) &&
                            AnnotationAccess.getAnnotation(method, ExplicitCallingConvention.class).value().equals(SubstrateCallingConventionKind.ForwardReturnValue)) {
                /*
                 * Methods that use ForwardReturnValue calling convention can't be resolved with
                 * PLT/GOT on AMD64 because
                 * AMD64MethodAddressResolutionDispatcher.resolveMethodAddress uses the same calling
                 * convention, and we can't save the callers value of the `rax` register on AMD64
                 * without spilling it.
                 */
                return false;
            }
            return true;
        }

        @Override
        @SuppressWarnings("unused")
        public boolean shouldCallViaPLTGOT(SharedMethod caller, SharedMethod callee) {
            return isAllowed(callee);
        }

        @Override
        public boolean shouldCallViaPLTGOT(SharedMethod callee) {
            return isAllowed(callee);
        }

        @Override
        public void augmentImageObjectFile(ObjectFile imageObjectFile) {
            GOTEntryAllocator gotEntryAllocator = HostedPLTGOTConfiguration.singleton().getGOTEntryAllocator();
            SharedMethod[] got = gotEntryAllocator.getGOT();
            long methodCount = got.length;
            int wordSize = ConfigurationValues.getTarget().wordSize;
            long gotSectionSize = methodCount * wordSize;
            offsetsSectionBuffer = new RelocatableBuffer(gotSectionSize, imageObjectFile.getByteOrder());
            offsetsSectionBufferImpl = new BasicProgbitsSectionImpl(offsetsSectionBuffer.getBackingArray());
            String name = SVM_METHODTABLE.getFormatDependentName(imageObjectFile.getFormat());
            ObjectFile.Section offsetsSection = imageObjectFile.newProgbitsSection(name, imageObjectFile.getPageSize(), true, false, offsetsSectionBufferImpl);

            ObjectFile.RelocationKind relocationKind = ObjectFile.RelocationKind.getDirect(wordSize);
            for (int gotEntryNo = 0; gotEntryNo < got.length; ++gotEntryNo) {
                offsetsSectionBuffer.addRelocationWithoutAddend(gotEntryNo * wordSize, relocationKind, new MethodPointer(got[gotEntryNo], false));
            }

            imageObjectFile.createDefinedSymbol(offsetsSection.getName(), offsetsSection, 0, 0, false, false);
            imageObjectFile.createDefinedSymbol("__svm_methodtable_begin", offsetsSection, 0, wordSize, false, SubstrateOptions.InternalSymbolsAreGlobal.getValue());
            imageObjectFile.createDefinedSymbol("__svm_methodtable_end", offsetsSection, gotSectionSize, wordSize, false, SubstrateOptions.InternalSymbolsAreGlobal.getValue());
        }

        @Override
        public MethodAddressResolver createMethodAddressResolver() {
            return new IdentityMethodAddressResolver();
        }
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(PLTGOTFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        HostedPLTGOTConfiguration.singleton().initializeMethodAddressResolutionSupport(IdentityMethodAddressResolverSupport::new);
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        prepareOffsetsSection((NativeImage) ((FeatureImpl.BeforeImageWriteAccessImpl) access).getImage());
    }

    private void prepareOffsetsSection(NativeImage image) {
        image.markRelocationSitesFromBuffer(offsetsSectionBuffer, offsetsSectionBufferImpl);
    }
}
