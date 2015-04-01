/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrument;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Root of a tool-provided AST fragment that can be attached directly into an executing AST via
 * {@link Instrument#create(ToolNodeInstrumentListener, String)}.
 * <p>
 * <strong>Note:</strong> Instances of this class will in some situations be cloned by the
 * instrumentation platform for attachment at equivalent locations in cloned parent ASTs.
 */
public abstract class ToolNode extends Node implements InstrumentationNode.TruffleEvents, InstrumentationNode {

    public void enter(Node node, VirtualFrame vFrame) {
    }

    public void returnVoid(Node node, VirtualFrame vFrame) {
    }

    public void returnValue(Node node, VirtualFrame vFrame, Object result) {
    }

    public void returnExceptional(Node node, VirtualFrame vFrame, Exception exception) {
    }

    public String instrumentationInfo() {
        return null;
    }

}
