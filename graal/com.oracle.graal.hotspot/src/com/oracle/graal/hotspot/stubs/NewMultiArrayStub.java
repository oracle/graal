/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.stubs;

import static com.oracle.graal.api.code.DeoptimizationAction.*;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.hotspot.replacements.HotSpotSnippetUtils.*;

import com.oracle.graal.api.code.RuntimeCallTarget.Descriptor;
import com.oracle.graal.api.code.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.word.*;

public class NewMultiArrayStub extends Stub {

    public NewMultiArrayStub(final HotSpotRuntime runtime, Replacements replacements, TargetDescription target, HotSpotRuntimeCallTarget linkage) {
        super(runtime, replacements, target, linkage, "newMultiArray");
    }

    @Override
    protected Arguments makeArguments(SnippetInfo stub) {
        Arguments args = new Arguments(stub);
        args.add("hub", null);
        args.add("rank", null);
        args.add("dims", null);
        return args;
    }

    @Snippet
    private static Object newMultiArray(Word hub, int rank, Word dims) {
        newMultiArrayC(NEW_MULTI_ARRAY_C, thread(), hub, rank, dims);

        if (clearPendingException(thread())) {
            getAndClearObjectResult(thread());
            DeoptimizeCallerNode.deopt(InvalidateReprofile, RuntimeConstraint);
        }
        return verifyOop(getAndClearObjectResult(thread()));
    }

    public static final Descriptor NEW_MULTI_ARRAY_C = new Descriptor("new_multi_array_c", false, void.class, Word.class, Word.class, int.class, Word.class);

    @NodeIntrinsic(CRuntimeCall.class)
    public static native void newMultiArrayC(@ConstantNodeParameter Descriptor newArrayC, Word thread, Word hub, int rank, Word dims);
}
