/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.java.AbstractBytecodeParser.Options.*;
import static java.lang.String.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.word.*;
import com.oracle.graal.java.*;
import com.oracle.graal.java.GraphBuilderContext.Replacement;
import com.oracle.graal.java.GraphBuilderPlugin.InlineInvokePlugin;
import com.oracle.graal.nodes.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.word.*;

public final class HotSpotInlineInvokePlugin implements InlineInvokePlugin {
    private final ReplacementsImpl replacements;
    private final NodeIntrinsificationPhase nodeIntrinsification;

    public HotSpotInlineInvokePlugin(NodeIntrinsificationPhase nodeIntrinsification, ReplacementsImpl replacements) {
        this.nodeIntrinsification = nodeIntrinsification;
        this.replacements = replacements;
    }

    public InlineInfo getInlineInfo(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args, JavaType returnType) {
        ResolvedJavaMethod subst = replacements.getMethodSubstitutionMethod(method);
        if (subst != null) {
            // Forced inlining of intrinsics
            return new InlineInfo(subst, true, true);
        }
        if (b.parsingReplacement()) {
            assert nodeIntrinsification.getIntrinsic(method) == null && method.getAnnotation(Word.Operation.class) == null && method.getAnnotation(HotSpotOperation.class) == null &&
                            !nodeIntrinsification.isFoldable(method) : format("%s should have been handled by %s", method.format("%H.%n(%p)"), DefaultGenericInvocationPlugin.class.getName());

            // Force inlining when parsing replacements
            return new InlineInfo(method, true, true);
        } else {
            assert nodeIntrinsification.getIntrinsic(method) == null : String.format("@%s method %s must only be called from within a replacement%n%s", NodeIntrinsic.class.getSimpleName(),
                            method.format("%h.%n"), b);
            if (InlineDuringParsing.getValue() && method.hasBytecodes() && method.getCode().length <= TrivialInliningSize.getValue() && b.getDepth() < InlineDuringParsingMaxDepth.getValue()) {
                return new InlineInfo(method, false, false);
            }
        }
        return null;
    }

    public void notifyOfNoninlinedInvoke(GraphBuilderContext b, ResolvedJavaMethod method, Invoke invoke) {
        if (b.parsingReplacement()) {
            boolean compilingSnippet = b.getRootMethod().getAnnotation(Snippet.class) != null;
            Replacement replacement = b.getReplacement();
            assert compilingSnippet : format("All calls in the replacement %s must be inlined or intrinsified: found call to %s", replacement.getReplacementMethod().format("%H.%n(%p)"),
                            method.format("%h.%n(%p)"));
        }
    }
}
