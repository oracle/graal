/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.test.instrument;

import java.io.*;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.nodes.controlflow.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * This sample instrument provides an example of a naive way to check if two numbers in SL are
 * equivalent using their variable names. This instrument is designed to be attached to an
 * {@link SLReturnNode}, but provides no guards against this.
 */
public class SLCheckVariableEqualityInstrument extends Instrument {

    private final String varName1;
    private final String varName2;
    private final PrintStream output;

    /**
     * Constructor
     *
     * @param varName1 The name of the first variable to compare
     * @param varName2 The name of the second variable to compare
     * @param output The {@link PrintStream} from the context used to print results. See
     *            {@link SLContext#getOutput()} for more info.
     */
    public SLCheckVariableEqualityInstrument(String varName1, String varName2, PrintStream output) {
        this.varName1 = varName1;
        this.varName2 = varName2;
        this.output = output;
    }

    /**
     * In the instrumentation test, this instrument is attached to a return statement. Since returns
     * are handled via exceptions in Simple, we need to override the leaveExceptional method. This
     * method does very limited error checking and simply prints "true" if the passed-in variables
     * match or "false" if they do not.
     */
    @Override
    public void leaveExceptional(Node astNode, VirtualFrame frame, Exception e) {
        FrameSlot f1 = frame.getFrameDescriptor().findFrameSlot(varName1);
        FrameSlot f2 = frame.getFrameDescriptor().findFrameSlot(varName2);

        if (f1 == null || f2 == null)
            output.println("false");
        else {
            try {
                output.println(frame.getLong(f1) == frame.getLong(f2));
            } catch (FrameSlotTypeException e1) {
                e1.printStackTrace();
            }
        }

    }
}
