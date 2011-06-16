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
package com.oracle.max.graal.compiler.ir;

import java.util.*;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

public class Deoptimize extends FixedNode {

    private static final int INPUT_COUNT = 0;
    private static final int SUCCESSOR_COUNT = 0;

    public static enum DeoptAction {
        None,                           // just interpret, do not invalidate nmethod
        Recompile,                      // recompile the nmethod; need not invalidate
        InvalidateReprofile,            // invalidate the nmethod, reset IC, maybe recompile
        InvalidateRecompile,            // invalidate the nmethod, recompile (probably)
        InvalidateStopCompiling,        // invalidate the nmethod and do not compile
    }

    private String message;
    private final DeoptAction action;

    public Deoptimize(DeoptAction action, Graph graph) {
        super(CiKind.Illegal, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        this.action = action;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }

    public DeoptAction action() {
        return action;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitDeoptimize(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("deoptimize");
    }

    @Override
    public String shortName() {
        return message == null ? "Deopt " : "Deopt " + message;
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("message", message);
        properties.put("action", action);
        return properties;
    }

    @Override
    public Node copy(Graph into) {
        Deoptimize x = new Deoptimize(action, into);
        x.setMessage(message);
        return x;
    }
}
