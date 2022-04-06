/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Arm Limited. All rights reserved.
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

import java.util.Arrays;

import org.graalvm.compiler.core.common.spi.ForeignCallSignature;
import org.graalvm.compiler.replacements.ArrayIndexOf;
import org.graalvm.compiler.replacements.ArrayIndexOfNode;
import org.graalvm.nativeimage.Platform.AARCH64;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;

import jdk.vm.ci.meta.JavaKind;

@AutomaticFeature
@Platforms(AARCH64.class)
class AArch64ArrayIndexOfForeignCallsFeature implements GraalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return !SubstrateOptions.useLLVMBackend();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        BeforeAnalysisAccessImpl impl = (BeforeAnalysisAccessImpl) access;
        AnalysisMetaAccess metaAccess = impl.getMetaAccess();
        for (SubstrateForeignCallDescriptor descriptor : AArch64ArrayIndexOfForeignCalls.FOREIGN_CALLS) {
            AnalysisMethod method = (AnalysisMethod) descriptor.findMethod(metaAccess);
            impl.registerAsCompiled(method);
        }
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(AArch64ArrayIndexOfForeignCalls.FOREIGN_CALLS);
    }
}

@Platforms(AARCH64.class)
class AArch64ArrayIndexOfForeignCalls {
    private static final ForeignCallSignature[] ORIGINAL_FOREIGN_CALLS = {
                    ArrayIndexOf.STUB_INDEX_OF_1_BYTE,
                    ArrayIndexOf.STUB_INDEX_OF_1_CHAR_COMPACT,
                    ArrayIndexOf.STUB_INDEX_OF_TWO_CONSECUTIVE_BYTES,
                    ArrayIndexOf.STUB_INDEX_OF_TWO_CONSECUTIVE_CHARS_COMPACT
    };

    static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = Arrays.stream(ORIGINAL_FOREIGN_CALLS)
                    .map(call -> SnippetRuntime.findForeignCall(AArch64ArrayIndexOfForeignCalls.class, call.getName(), true))
                    .toArray(SubstrateForeignCallDescriptor[]::new);

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf1Byte(byte[] array, int arrayLength, int fromIndex, byte b) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Byte, JavaKind.Byte, false, array, arrayLength, fromIndex, b);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf1CharCompact(byte[] array, int arrayLength, int fromIndex, char ch) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Byte, JavaKind.Char, false, array, arrayLength, fromIndex, ch);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveBytes(byte[] array, int arrayLength, int fromIndex, int searchValue) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Byte, JavaKind.Byte, true, array, arrayLength, fromIndex, searchValue);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveCharsCompact(byte[] array, int arrayLength, int fromIndex, int searchValue) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Byte, JavaKind.Char, true, array, arrayLength, fromIndex, searchValue);
    }
}
