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

import static org.graalvm.compiler.replacements.ArrayIndexOf.NONE;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S1;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S2;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S4;

import java.util.Arrays;

import org.graalvm.compiler.replacements.ArrayIndexOf;
import org.graalvm.compiler.replacements.ArrayIndexOfNode;
import org.graalvm.nativeimage.Platform.AMD64;
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

@AutomaticFeature
@Platforms(AMD64.class)
class AMD64ArrayIndexOfForeignCallsFeature implements GraalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return !SubstrateOptions.useLLVMBackend();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        BeforeAnalysisAccessImpl impl = (BeforeAnalysisAccessImpl) access;
        AnalysisMetaAccess metaAccess = impl.getMetaAccess();
        for (SubstrateForeignCallDescriptor descriptor : AMD64ArrayIndexOfForeignCalls.FOREIGN_CALLS) {
            AnalysisMethod method = (AnalysisMethod) descriptor.findMethod(metaAccess);
            impl.registerAsCompiled(method, true);
        }
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(AMD64ArrayIndexOfForeignCalls.FOREIGN_CALLS);
    }
}

@Platforms(AMD64.class)
class AMD64ArrayIndexOfForeignCalls {
    // None of the following foreign calls kills any locations.

    static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = Arrays.stream(ArrayIndexOf.STUBS_AMD64)
                    .filter(x -> Arrays.stream(ArrayIndexOf.STUBS_AARCH64).noneMatch(y -> y == x))
                    .map(call -> SnippetRuntime.findForeignCall(AMD64ArrayIndexOfForeignCalls.class, call.getName(), true))
                    .toArray(SubstrateForeignCallDescriptor[]::new);

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB2S1(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S1, false, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB3S1(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S1, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB4S1(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S1, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB2S2(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S2, false, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB3S2(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S2, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB4S2(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S2, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfC2S2(char[] array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S2, S2, false, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfC3S2(char[] array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S2, S2, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfC4S2(char[] array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S2, S2, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf2S1(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S1, false, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf3S1(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S1, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf4S1(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S1, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf2S2(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S2, false, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf3S2(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S2, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf4S2(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S2, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf2S4(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S4, false, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf3S4(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S4, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf4S4(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S4, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfWithMaskBS1(byte[] array, long offset, int length, int fromIndex, int v1, int mask1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S1, false, true, array, offset, length, fromIndex, v1, mask1);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfWithMaskBS2(byte[] array, long offset, int length, int fromIndex, int v1, int mask1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S2, false, true, array, offset, length, fromIndex, v1, mask1);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveWithMaskBS1(byte[] array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S1, true, true, array, offset, length, fromIndex, v1, v2, mask1, mask2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveWithMaskBS2(byte[] array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S2, true, true, array, offset, length, fromIndex, v1, v2, mask1, mask2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfWithMaskCS2(char[] array, long offset, int length, int fromIndex, int v1, int mask1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S2, S2, false, true, array, offset, length, fromIndex, v1, mask1);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveWithMaskCS2(char[] array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S2, S2, true, true, array, offset, length, fromIndex, v1, v2, mask1, mask2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfWithMaskS1(Object array, long offset, int length, int fromIndex, int v1, int mask1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S1, false, true, array, offset, length, fromIndex, v1, mask1);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfWithMaskS2(Object array, long offset, int length, int fromIndex, int v1, int mask1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S2, false, true, array, offset, length, fromIndex, v1, mask1);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfWithMaskS4(Object array, long offset, int length, int fromIndex, int v1, int mask1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S4, false, true, array, offset, length, fromIndex, v1, mask1);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveWithMaskS1(Object array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S1, true, true, array, offset, length, fromIndex, v1, v2, mask1, mask2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveWithMaskS2(Object array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S2, true, true, array, offset, length, fromIndex, v1, v2, mask1, mask2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveWithMaskS4(Object array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S4, true, true, array, offset, length, fromIndex, v1, v2, mask1, mask2);
    }
}
