/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

public class MemoryErrorDelegate extends VirtualMachineError {

    private static final long serialVersionUID = 8733484410412601660L;

    public static StackTraceElement[] EMPTY_STACK = new StackTraceElement[0];
    public static FrameInstance[] EMPTY_FRAMES = new FrameInstance[0];
    private static int DEFAULT_DESTACK = 10;

    private int emptyTheStack = DEFAULT_DESTACK;
    private boolean isStackOverflow;

    public MemoryErrorDelegate() {
        super();
    }

    public MemoryErrorDelegate deStack() {
        emptyTheStack--;
        return this;
    }

    public boolean check() {
        return emptyTheStack <= 0;
    }

    public MemoryErrorDelegate delegate(boolean _isStackOverflow) {
        this.isStackOverflow = _isStackOverflow;
        return this;
    }

    public EspressoException act(EspressoContext context, Meta meta) {
        EspressoException EE;
        if (isStackOverflow) {
            EE = context.getStackOverflow();
        } else {
            EE = context.getOutOfMemory();
        }
        StaticObject exception = EE.getException();
        InterpreterToVM.fillInStackTrace(context.getFrames(), exception, meta);
        return EE;
    }
}
