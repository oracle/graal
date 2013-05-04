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

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.nodes.JumpToExceptionHandlerInCallerNode.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.hotspot.stubs.ExceptionHandlerStub.*;

import com.oracle.graal.api.code.RuntimeCallTarget.Descriptor;
import com.oracle.graal.api.code.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.Fold;
import com.oracle.graal.word.*;

/**
 * Stub called by an {@link UnwindNode}. This stub executes in the frame of the method throwing an
 * exception and completes by jumping to the exception handler in the calling frame.
 */
public class UnwindExceptionToCallerStub extends CRuntimeStub {

    public UnwindExceptionToCallerStub(final HotSpotRuntime runtime, Replacements replacements, TargetDescription target, HotSpotRuntimeCallTarget linkage) {
        super(runtime, replacements, target, linkage);
    }

    /**
     * The current frame is unwound by this stub. Therefore, it does not need to save any registers
     * as HotSpot uses a caller save convention.
     */
    @Override
    public boolean preservesRegisters() {
        return false;
    }

    @Snippet
    private static void unwindExceptionToCaller(Object exception, Word returnAddress) {
        checkNoExceptionInThread(assertionsEnabled());
        checkExceptionNotNull(assertionsEnabled(), exception);
        if (logging()) {
            printf("unwinding exception %p at return address %p\n", Word.fromObject(exception).rawValue(), returnAddress.rawValue());
        }

        Word handlerInCallerPc = exceptionHandlerForReturnAddress(EXCEPTION_HANDLER_FOR_RETURN_ADDRESS, thread(), returnAddress);

        if (logging()) {
            printf("handler for exception %p at return address %p is at %p\n", Word.fromObject(exception).rawValue(), returnAddress.rawValue(), handlerInCallerPc.rawValue());
        }

        jumpToExceptionHandlerInCaller(handlerInCallerPc, exception, returnAddress);
    }

    @Fold
    private static boolean logging() {
        return Boolean.getBoolean("graal.logUnwindExceptionToCallerStub");
    }

    @Fold
    @SuppressWarnings("all")
    private static boolean assertionsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled || graalRuntime().getConfig().cAssertions;
    }

    public static final Descriptor EXCEPTION_HANDLER_FOR_RETURN_ADDRESS = descriptorFor(UnwindExceptionToCallerStub.class, "exceptionHandlerForReturnAddress", false);

    @NodeIntrinsic(value = CRuntimeCall.class, setStampFromReturnType = true)
    public static native Word exceptionHandlerForReturnAddress(@ConstantNodeParameter Descriptor exceptionHandlerForReturnAddress, Word thread, Word returnAddress);
}
