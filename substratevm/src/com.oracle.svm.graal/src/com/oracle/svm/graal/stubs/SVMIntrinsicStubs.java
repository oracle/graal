/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.stubs;

import org.graalvm.compiler.lir.GeneratedStubsHolder;
import org.graalvm.compiler.replacements.StringLatin1InflateNode;
import org.graalvm.compiler.replacements.StringUTF16CompressNode;
import org.graalvm.compiler.replacements.nodes.ArrayRegionEqualsWithMaskNode;
import org.graalvm.compiler.replacements.nodes.CalcStringAttributesNode;
import org.graalvm.compiler.replacements.nodes.AESNode;
import org.graalvm.compiler.replacements.nodes.ArrayCompareToNode;
import org.graalvm.compiler.replacements.nodes.ArrayCopyWithConversionsNode;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsNode;
import org.graalvm.compiler.replacements.nodes.ArrayIndexOfNode;
import org.graalvm.compiler.replacements.nodes.ArrayRegionCompareToNode;
import org.graalvm.compiler.replacements.nodes.ArrayRegionEqualsNode;
import org.graalvm.compiler.replacements.nodes.BigIntegerMultiplyToLenNode;
import org.graalvm.compiler.replacements.nodes.CipherBlockChainingAESNode;
import org.graalvm.compiler.replacements.nodes.CounterModeAESNode;
import org.graalvm.compiler.replacements.nodes.EncodeArrayNode;
import org.graalvm.compiler.replacements.nodes.GHASHProcessBlocksNode;
import org.graalvm.compiler.replacements.nodes.HasNegativesNode;
import org.graalvm.compiler.replacements.nodes.VectorizedMismatchNode;

@GeneratedStubsHolder(targetVM = "substrate", sources = {
                ArrayIndexOfNode.class,
                ArrayEqualsNode.class,
                ArrayRegionEqualsNode.class,
                ArrayCompareToNode.class,
                ArrayRegionCompareToNode.class,
                ArrayCopyWithConversionsNode.class,
                StringUTF16CompressNode.class,
                StringLatin1InflateNode.class,
                HasNegativesNode.class,
                EncodeArrayNode.class,
                VectorizedMismatchNode.class,
                ArrayRegionEqualsWithMaskNode.class,
                CalcStringAttributesNode.class,
                AESNode.class,
                CounterModeAESNode.class,
                CipherBlockChainingAESNode.class,
                GHASHProcessBlocksNode.class,
                BigIntegerMultiplyToLenNode.class,
})
public final class SVMIntrinsicStubs {
}
