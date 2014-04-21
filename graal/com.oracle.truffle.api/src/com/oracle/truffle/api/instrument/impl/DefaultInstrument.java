/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.api.instrument.impl;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;

/**
 * An {@link Instrument} that implements all {@link ExecutionEvents} notifications with empty
 * methods.
 */
public class DefaultInstrument extends InstrumentationNodeImpl implements Instrument {

    protected DefaultInstrument() {
    }

    public void enter(Node astNode, VirtualFrame frame) {
    }

    public void leave(Node astNode, VirtualFrame frame) {
    }

    public void leave(Node astNode, VirtualFrame frame, boolean result) {
        leave(astNode, frame, (Object) result);
    }

    public void leave(Node astNode, VirtualFrame frame, byte result) {
        leave(astNode, frame, (Object) result);
    }

    public void leave(Node astNode, VirtualFrame frame, short result) {
        leave(astNode, frame, (Object) result);
    }

    public void leave(Node astNode, VirtualFrame frame, int result) {
        leave(astNode, frame, (Object) result);
    }

    public void leave(Node astNode, VirtualFrame frame, long result) {
        leave(astNode, frame, (Object) result);
    }

    public void leave(Node astNode, VirtualFrame frame, char result) {
        leave(astNode, frame, (Object) result);
    }

    public void leave(Node astNode, VirtualFrame frame, float result) {
        leave(astNode, frame, (Object) result);
    }

    public void leave(Node astNode, VirtualFrame frame, double result) {
        leave(astNode, frame, (Object) result);
    }

    public void leave(Node astNode, VirtualFrame frame, Object result) {
    }

    public void leaveExceptional(Node astNode, VirtualFrame frame, Exception e) {
    }

}
