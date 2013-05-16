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

import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.hotspot.stubs.StubUtil.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.word.*;

/**
 * Stub called from {@link Log}.
 */
public class LogObjectStub extends CRuntimeStub {

    public LogObjectStub(final HotSpotRuntime runtime, Replacements replacements, TargetDescription target, HotSpotForeignCallLinkage linkage) {
        super(runtime, replacements, target, linkage);
    }

    @Snippet
    private static void logObject(Object object, int flags) {
        logObjectC(LOG_OBJECT_C, thread(), object, flags);
    }

    public static final ForeignCallDescriptor LOG_OBJECT_C = descriptorFor(LogObjectStub.class, "logObjectC");

    @NodeIntrinsic(CRuntimeCall.class)
    public static native void logObjectC(@ConstantNodeParameter ForeignCallDescriptor logObjectC, Word thread, Object object, int flags);
}
