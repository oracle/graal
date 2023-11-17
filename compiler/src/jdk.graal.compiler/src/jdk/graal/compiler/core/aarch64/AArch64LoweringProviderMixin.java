/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.core.aarch64;

import jdk.graal.compiler.core.common.memory.MemoryExtendKind;
import jdk.graal.compiler.nodes.memory.ExtendableMemoryAccess;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.spi.LoweringProvider;

public interface AArch64LoweringProviderMixin extends LoweringProvider {

    @Override
    default boolean divisionOverflowIsJVMSCompliant() {
        return true;
    }

    @Override
    default Integer smallestCompareWidth() {
        return 32;
    }

    @Override
    default boolean supportsBulkZeroing() {
        return true;
    }

    @Override
    default boolean supportsRounding() {
        return true;
    }

    @Override
    default boolean writesStronglyOrdered() {
        /* AArch64 only requires a weak memory model. */
        return false;
    }

    @Override
    default boolean narrowsUseCastValue() {
        return true;
    }

    @Override
    default boolean supportsFoldingExtendIntoAccess(ExtendableMemoryAccess access, MemoryExtendKind extendKind) {
        if (!access.isCompatibleWithExtend(extendKind)) {
            return false;
        }

        boolean supportsSigned = false;
        boolean supportsZero = false;
        if (access instanceof ReadNode) {
            supportsZero = true;
            supportsSigned = !((ReadNode) access).ordersMemoryAccesses();
        }

        switch (extendKind) {
            case ZERO_16:
            case ZERO_32:
            case ZERO_64:
                return supportsZero;
            case SIGN_16:
            case SIGN_32:
            case SIGN_64:
                return supportsSigned;
        }
        return false;
    }
}
