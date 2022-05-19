/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal;

import org.graalvm.compiler.lir.amd64.AMD64CalcStringAttributesOp;
import org.graalvm.compiler.replacements.amd64.AMD64CalcStringAttributesNode;
import org.graalvm.nativeimage.Platform.AMD64;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.graal.InternalFeature;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;

@AutomaticFeature
@Platforms(AMD64.class)
class AMD64CalcStringAttributesForeignCallsFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return !SubstrateOptions.useLLVMBackend();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        SubstrateGraalUtils.registerStubRoots(access, AMD64CalcStringAttributesForeignCalls.FOREIGN_CALLS);
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(AMD64CalcStringAttributesForeignCalls.FOREIGN_CALLS);
    }
}

@Platforms(AMD64.class)
class AMD64CalcStringAttributesForeignCalls {

    static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = SubstrateGraalUtils.mapStubs(
                    org.graalvm.compiler.replacements.amd64.AMD64CalcStringAttributesForeignCalls.STUBS,
                    AMD64CalcStringAttributesForeignCalls.class);

    // GENERATED CODE BEGIN
    

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int calcStringAttributesLatin1(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.intReturnValue(AMD64CalcStringAttributesOp.Op.LATIN1, false, array, offset, length);
    }    

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int calcStringAttributesBMP(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.intReturnValue(AMD64CalcStringAttributesOp.Op.BMP, false, array, offset, length);
    }    

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static long calcStringAttributesUTF8Valid(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.longReturnValue(AMD64CalcStringAttributesOp.Op.UTF_8, true, array, offset, length);
    }    

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static long calcStringAttributesUTF8Unknown(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.longReturnValue(AMD64CalcStringAttributesOp.Op.UTF_8, false, array, offset, length);
    }    

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static long calcStringAttributesUTF16Valid(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.longReturnValue(AMD64CalcStringAttributesOp.Op.UTF_16, true, array, offset, length);
    }    

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static long calcStringAttributesUTF16Unknown(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.longReturnValue(AMD64CalcStringAttributesOp.Op.UTF_16, false, array, offset, length);
    }    

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int calcStringAttributesUTF32(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.intReturnValue(AMD64CalcStringAttributesOp.Op.UTF_32, false, array, offset, length);
    }

    // GENERATED CODE END
}
