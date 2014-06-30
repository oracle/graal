/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common.inlining.walker;

import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.nodes.StructuredGraph;

import com.oracle.graal.phases.common.inlining.info.elem.InlineableMacroNode;

/**
 * A {@link CallsiteHolder} that stands for an {@link InlineableMacroNode} in the stack realized by
 * {@link InliningData}.
 */
public final class CallsiteHolderDummy extends CallsiteHolder {

    public static final CallsiteHolderDummy DUMMY_CALLSITE_HOLDER = new CallsiteHolderDummy();

    private CallsiteHolderDummy() {
        // no instances other than the singleton
    }

    @Override
    public ResolvedJavaMethod method() {
        return null;
    }

    @Override
    public boolean hasRemainingInvokes() {
        return false;
    }

    @Override
    public StructuredGraph graph() {
        return null;
    }

    @Override
    public String toString() {
        return "<macro-node>";
    }
}
