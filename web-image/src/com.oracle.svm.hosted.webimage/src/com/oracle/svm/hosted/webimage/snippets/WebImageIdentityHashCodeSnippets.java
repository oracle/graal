/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.snippets;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import com.oracle.svm.core.identityhashcode.IdentityHashCodeSupport;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.hosted.webimage.codegen.node.ReadIdentityHashCodeNode;
import com.oracle.svm.hosted.webimage.codegen.node.WriteIdentityHashCodeNode;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.IdentityHashCodeSnippets;
import jdk.graal.compiler.replacements.nodes.IdentityHashCodeNode;

/**
 * Snippet lowering for {@link IdentityHashCodeNode}.
 * <p>
 * The actual computation is behind a foreign call ({@link #doComputeIdentityHashCode(Object)}) to
 * reduce code size.
 *
 * @see ReadIdentityHashCodeNode
 * @see WriteIdentityHashCodeNode
 */
public class WebImageIdentityHashCodeSnippets extends IdentityHashCodeSnippets {
    public static final SnippetRuntime.SubstrateForeignCallDescriptor COMPUTE_IDENTITY_HASH_CODE = SnippetRuntime.findForeignCall(
                    WebImageIdentityHashCodeSnippets.class, "doComputeIdentityHashCode", NO_SIDE_EFFECT, IdentityHashCodeSupport.IDENTITY_HASHCODE_LOCATION);

    public static Templates createTemplates(OptionValues options, Providers providers) {
        return new Templates(new WebImageIdentityHashCodeSnippets(), options, providers, IdentityHashCodeSupport.IDENTITY_HASHCODE_LOCATION);
    }

    @Override
    protected int computeIdentityHashCode(Object thisObj) {
        return callComputeIdentityHashCode(COMPUTE_IDENTITY_HASH_CODE, thisObj);
    }

    @Node.NodeIntrinsic(ForeignCallNode.class)
    private static native int callComputeIdentityHashCode(@Node.ConstantNodeParameter ForeignCallDescriptor descriptor, Object obj);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static int doComputeIdentityHashCode(Object obj) {
        int identityHashCode = ReadIdentityHashCodeNode.read(obj);
        if (probability(SLOW_PATH_PROBABILITY, identityHashCode == 0)) {
            identityHashCode = IdentityHashCodeSupport.generateRandomHashCode();
            WriteIdentityHashCodeNode.set(obj, identityHashCode);
        }
        return identityHashCode;
    }
}
