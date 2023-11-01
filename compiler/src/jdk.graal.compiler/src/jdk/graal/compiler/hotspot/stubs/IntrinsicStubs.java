/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.stubs;

import jdk.graal.compiler.lir.GeneratedStubsHolder;
import jdk.graal.compiler.replacements.StringLatin1InflateNode;
import jdk.graal.compiler.replacements.StringUTF16CompressNode;
import jdk.graal.compiler.replacements.nodes.AESNode;
import jdk.graal.compiler.replacements.nodes.ArrayCompareToNode;
import jdk.graal.compiler.replacements.nodes.ArrayCopyWithConversionsNode;
import jdk.graal.compiler.replacements.nodes.ArrayEqualsNode;
import jdk.graal.compiler.replacements.nodes.ArrayIndexOfNode;
import jdk.graal.compiler.replacements.nodes.ArrayRegionCompareToNode;
import jdk.graal.compiler.replacements.nodes.ArrayRegionEqualsNode;
import jdk.graal.compiler.replacements.nodes.ArrayRegionEqualsWithMaskNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMulAddNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMultiplyToLenNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerSquareToLenNode;
import jdk.graal.compiler.replacements.nodes.CalcStringAttributesNode;
import jdk.graal.compiler.replacements.nodes.CipherBlockChainingAESNode;
import jdk.graal.compiler.replacements.nodes.CountPositivesNode;
import jdk.graal.compiler.replacements.nodes.CounterModeAESNode;
import jdk.graal.compiler.replacements.nodes.EncodeArrayNode;
import jdk.graal.compiler.replacements.nodes.GHASHProcessBlocksNode;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.MD5Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA1Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA256Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA3Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA512Node;
import jdk.graal.compiler.replacements.nodes.VectorizedHashCodeNode;
import jdk.graal.compiler.replacements.nodes.VectorizedMismatchNode;

@GeneratedStubsHolder(targetVM = "hotspot", sources = {
                ArrayIndexOfNode.class,
                ArrayEqualsNode.class,
                ArrayRegionEqualsNode.class,
                ArrayRegionEqualsWithMaskNode.class,
                ArrayCompareToNode.class,
                ArrayRegionCompareToNode.class,
                ArrayCopyWithConversionsNode.class,
                CalcStringAttributesNode.class,
                StringUTF16CompressNode.class,
                StringLatin1InflateNode.class,
                CountPositivesNode.class,
                EncodeArrayNode.class,
                VectorizedMismatchNode.class,
                VectorizedHashCodeNode.class,
                AESNode.class,
                CounterModeAESNode.class,
                CipherBlockChainingAESNode.class,
                GHASHProcessBlocksNode.class,
                BigIntegerMultiplyToLenNode.class,
                BigIntegerMulAddNode.class,
                BigIntegerSquareToLenNode.class,
                SHA1Node.class,
                SHA256Node.class,
                SHA3Node.class,
                SHA512Node.class,
                MD5Node.class,
})
public final class IntrinsicStubs {
}
