/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes;

import com.oracle.graal.hotspot.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.word.*;

/**
 * Sets up the {@linkplain HotSpotBackend#EXCEPTION_HANDLER_IN_CALLER arguments} expected by an
 * exception handler in the caller's frame, removes the current frame and jumps to said handler.
 */
public class JumpToExceptionHandlerInCallerNode extends ControlSinkNode implements LIRLowerable {

    @Input private ValueNode handlerInCallerPc;
    @Input private ValueNode exception;
    @Input private ValueNode exceptionPc;

    public JumpToExceptionHandlerInCallerNode(ValueNode handlerInCallerPc, ValueNode exception, ValueNode exceptionPc) {
        super(StampFactory.forVoid());
        this.handlerInCallerPc = handlerInCallerPc;
        this.exception = exception;
        this.exceptionPc = exceptionPc;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        ((HotSpotNodeLIRBuilder) gen).emitJumpToExceptionHandlerInCaller(handlerInCallerPc, exception, exceptionPc);
    }

    @NodeIntrinsic
    public static native void jumpToExceptionHandlerInCaller(Word handlerInCallerPc, Object exception, Word exceptionPc);
}
