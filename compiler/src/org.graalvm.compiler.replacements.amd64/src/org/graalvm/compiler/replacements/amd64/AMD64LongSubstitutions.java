/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.amd64;

// JaCoCo Exclude

import static org.graalvm.compiler.replacements.NodeIntrinsificationProvider.INJECTED_TARGET;
import static org.graalvm.compiler.replacements.amd64.AMD64IntegerSubstitutions.lzcnt;
import static org.graalvm.compiler.replacements.amd64.AMD64IntegerSubstitutions.tzcnt;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.replacements.nodes.BitScanForwardNode;
import org.graalvm.compiler.replacements.nodes.BitScanReverseNode;

@ClassSubstitution(Long.class)
public class AMD64LongSubstitutions {

    @MethodSubstitution
    @SuppressFBWarnings(value = "NP_NULL_PARAM_DEREF_NONVIRTUAL", justification = "foldable method parameters are injected")
    public static int numberOfLeadingZeros(long i) {
        if (lzcnt(INJECTED_TARGET)) {
            return AMD64CountLeadingZerosNode.countLeadingZeros(i);
        }
        if (i == 0) {
            return 64;
        }
        return 63 - BitScanReverseNode.unsafeScan(i);
    }

    @MethodSubstitution
    @SuppressFBWarnings(value = "NP_NULL_PARAM_DEREF_NONVIRTUAL", justification = "foldable method parameters are injected")
    public static int numberOfTrailingZeros(long i) {
        if (tzcnt(INJECTED_TARGET)) {
            return AMD64CountTrailingZerosNode.countTrailingZeros(i);
        }

        if (i == 0) {
            return 64;
        }
        return BitScanForwardNode.unsafeScan(i);
    }
}
