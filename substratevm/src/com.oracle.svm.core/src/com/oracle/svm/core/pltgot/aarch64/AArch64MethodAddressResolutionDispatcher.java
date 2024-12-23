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
package com.oracle.svm.core.pltgot.aarch64;

import static com.oracle.svm.core.pltgot.ExitMethodAddressResolutionNode.exitMethodAddressResolution;

import jdk.graal.compiler.word.Word;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.deopt.DeoptimizationSlotPacking;
import com.oracle.svm.core.graal.code.StubCallingConvention;
import com.oracle.svm.core.pltgot.MethodAddressResolutionDispatcher;
import com.oracle.svm.core.snippets.KnownIntrinsics;

import jdk.graal.compiler.nodes.UnreachableNode;

public final class AArch64MethodAddressResolutionDispatcher extends MethodAddressResolutionDispatcher {
    /**
     * This method is never called directly, instead we jump to it through a scratch register from
     * the PLT stub (see {@code AArch64PLTStubGenerator}). The PLT stub writes the GOT entry into
     * the stack frame padding space after the return address that is conventionally used for the
     * deopt frame data. We do this in order to avoid having to spill the argument passing registers
     * to the stack when we call @{code resolveMethodAddress}. Note that we cannot use the full
     * word, see {@link DeoptimizationSlotPacking} on the convention that should be used.
     */
    @StubCallingConvention
    @Uninterruptible(reason = "PLT/GOT method address resolution doesn't support interruptible code paths.")
    @NeverInline("This method must never be inlined or called directly because we only jump to it from the PLT stub.")
    public static void resolveMethodAddress() {
        Pointer paddingSlot = KnownIntrinsics.readCallerStackPointer();
        long gotEntry = DeoptimizationSlotPacking.decodeGOTIndex(paddingSlot.readWord(0).rawValue());
        long resolvedMethodAddress = MethodAddressResolutionDispatcher.resolveMethodAddress(gotEntry);
        exitMethodAddressResolution(Word.pointer(resolvedMethodAddress));
        throw UnreachableNode.unreachable();
    }
}
