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
package com.oracle.svm.core.pltgot.amd64;

import static com.oracle.svm.core.pltgot.ExitMethodAddressResolutionNode.exitMethodAddressResolution;

import jdk.graal.compiler.word.Word;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.graal.code.ExplicitCallingConvention;
import com.oracle.svm.core.graal.code.StubCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.pltgot.MethodAddressResolutionDispatcher;

import jdk.graal.compiler.nodes.UnreachableNode;

public final class AMD64MethodAddressResolutionDispatcher extends MethodAddressResolutionDispatcher {
    /**
     * This method is never called directly, we jump to it from a PLT stub for a method with a given
     * gotEntry. Because the {@link SubstrateCallingConventionKind#ForwardReturnValue} calling
     * convention takes the value of its one and only parameter {@code gotEntry} from the return
     * register, we load the got entry value of a method that we are resolving into the return value
     * register in the PLT stub.
     *
     * In the lir op for {@code exitMethodAddressResolution} we jump through the return register to
     * the {@code resolvedMethodAddress} and that's why the method {@code resolveMethodAddress} has
     * to have a return kind {@code long} so that the calleeSaved register restore for this method
     * doesn't override the return register. See
     * {@link jdk.graal.compiler.lir.asm.FrameContext#leave}.
     */
    @StubCallingConvention
    @Uninterruptible(reason = "PLT/GOT method address resolution doesn't support interruptible code paths.")
    @ExplicitCallingConvention(SubstrateCallingConventionKind.ForwardReturnValue)
    @NeverInline("This method must never be inlined or called directly because we only jump to it from the PLT stub.")
    public static long resolveMethodAddress(long gotEntry) {
        long resolvedMethodAddress = MethodAddressResolutionDispatcher.resolveMethodAddress(gotEntry);
        exitMethodAddressResolution(Word.pointer(resolvedMethodAddress));
        throw UnreachableNode.unreachable();
    }
}
