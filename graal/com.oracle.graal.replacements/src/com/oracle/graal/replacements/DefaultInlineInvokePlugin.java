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
package com.oracle.graal.replacements;

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.java.GraphBuilderPhase.Options.*;

import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.nodes.*;
import com.oracle.jvmci.meta.*;

public final class DefaultInlineInvokePlugin implements InlineInvokePlugin {
    private final ReplacementsImpl replacements;

    public DefaultInlineInvokePlugin(ReplacementsImpl replacements) {
        this.replacements = replacements;
    }

    public InlineInfo getInlineInfo(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args, JavaType returnType) {
        InlineInfo inlineInfo = replacements.getInlineInfo(b, method, args, returnType);
        if (inlineInfo == null) {
            if (InlineDuringParsing.getValue() && method.hasBytecodes() && method.getCode().length <= TrivialInliningSize.getValue() && b.getDepth() < InlineDuringParsingMaxDepth.getValue()) {
                return new InlineInfo(method, false);
            }
        }
        return inlineInfo;
    }

    public void notifyOfNoninlinedInvoke(GraphBuilderContext b, ResolvedJavaMethod method, Invoke invoke) {
        replacements.notifyOfNoninlinedInvoke(b, method, invoke);
    }
}
