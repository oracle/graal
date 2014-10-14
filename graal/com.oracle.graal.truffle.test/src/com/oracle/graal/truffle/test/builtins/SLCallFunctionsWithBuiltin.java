/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.test.builtins;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.sl.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * Returns all functions that start with test.
 */
@NodeInfo(shortName = "callFunctionsWith")
public abstract class SLCallFunctionsWithBuiltin extends SLGraalRuntimeBuiltin {

    @Child private IndirectCallNode indirectCall = Truffle.getRuntime().createIndirectCallNode();

    @Specialization
    public SLNull runTests(VirtualFrame frame, String startsWith, SLFunction harness) {
        SLContext context = ((SLRootNode) getRootNode()).getSLContext();
        boolean found = false;
        for (SLFunction function : context.getFunctionRegistry().getFunctions()) {
            if (function.getName().startsWith(startsWith) && getSource(function) == getSource(harness) && getSource(function) != null) {
                indirectCall.call(frame, harness.getCallTarget(), new Object[]{function});
                found = true;
            }
        }
        if (!found) {
            throw new SLAssertionError("No tests found to execute.");
        }
        return SLNull.SINGLETON;
    }

    private static Source getSource(SLFunction function) {
        SourceSection section = function.getCallTarget().getRootNode().getSourceSection();
        if (section != null) {
            return section.getSource();
        }
        return null;
    }
}
