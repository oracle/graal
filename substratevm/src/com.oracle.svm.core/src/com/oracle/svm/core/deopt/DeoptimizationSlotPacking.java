/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.deopt;

import com.oracle.svm.core.Uninterruptible;

/**
 * First stack slot is always reserved for injecting the DeoptimizationFrame in the SVM calling
 * convention, aka. "deopt slot". Other users are PLT stubs (AArch64 only) and the
 * leaveInterpreterStub which can use this slot at the same time. Fortunately both fit into the same
 * slot, as the variable frame size of the leaveInterpreterStub fits into one byte, and the gotIndex
 * scales with the amount of methods in an image.
 *
 * This interface provides helpers that codify conventions how the deopt slot should be used.
 */
public interface DeoptimizationSlotPacking {
    long MAX_SIZE_VARIABLE_FRAMESIZE = 0xff;
    int POS_VARIABLE_FRAMESIZE = 56;
    int STACK_ALIGNMENT = 4; /* 0x10 stack alignment on AArch64 and AMD64 */
    long MASK_VARIABLE_FRAMESIZE = (MAX_SIZE_VARIABLE_FRAMESIZE << POS_VARIABLE_FRAMESIZE);
    long MASK_GOT_INDEX = ~(MAX_SIZE_VARIABLE_FRAMESIZE << POS_VARIABLE_FRAMESIZE);

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static long encodeVariableFrameSizeIntoDeoptSlot(long varSize) {
        assert ((varSize >> STACK_ALIGNMENT) & ~MAX_SIZE_VARIABLE_FRAMESIZE) == 0;
        return (varSize >> STACK_ALIGNMENT) << POS_VARIABLE_FRAMESIZE;
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static long decodeVariableFrameSizeFromDeoptSlot(long deoptSlot) {
        long stackSize = (deoptSlot >> POS_VARIABLE_FRAMESIZE) & MAX_SIZE_VARIABLE_FRAMESIZE;
        return stackSize << STACK_ALIGNMENT;
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static long decodeGOTIndex(long deoptSlot) {
        return deoptSlot & MASK_GOT_INDEX;
    }
}
