/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes.instrument;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.instrument.impl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * SLDefaultVisualizer provides methods to get the names of SL's internal Truffle AST nodes.
 *
 */
public class SLDefaultVisualizer extends DefaultVisualizer {

    private final SLASTPrinter astPrinter;

    public SLDefaultVisualizer() {
        this.astPrinter = new SLASTPrinter();
    }

    @Override
    public ASTPrinter getASTPrinter() {
        return astPrinter;
    }

    @Override
    public String displayMethodName(Node node) {

        if (node == null) {
            return null;
        }
        RootNode root = node.getRootNode();
        if (root instanceof SLRootNode) {
            SLRootNode slRootNode = (SLRootNode) root;
            return slRootNode.toString();

        }
        return "unknown";
    }

    @Override
    public String displayCallTargetName(CallTarget callTarget) {
        if (callTarget instanceof RootCallTarget) {
            final RootCallTarget rootCallTarget = (RootCallTarget) callTarget;
            SLRootNode slRootNode = (SLRootNode) rootCallTarget.getRootNode();
            return slRootNode.toString();
        }
        return callTarget.toString();
    }

    @Override
    public String displayValue(ExecutionContext context, Object value) {
        if (value == SLNull.SINGLETON) {
            return "null";
        }
        return value.toString();
    }

    @Override
    public String displayIdentifier(FrameSlot slot) {

        final Object id = slot.getIdentifier();
        return id.toString();
    }
}
