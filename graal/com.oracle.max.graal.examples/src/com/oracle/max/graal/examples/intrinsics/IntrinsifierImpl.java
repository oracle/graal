/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.examples.intrinsics;

import java.util.*;

import com.oracle.max.graal.compiler.graph.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.extensions.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;


public class IntrinsifierImpl implements Intrinsifier {

    @Override
    public Graph intrinsicGraph(RiRuntime runtime, RiMethod caller, int bci, RiMethod method, List<? extends Node> parameters) {
        if (method.holder().name().equals("Lcom/oracle/max/graal/examples/intrinsics/SafeAddExample;") && method.name().equals("safeAdd")) {
            CompilerGraph graph = new CompilerGraph(runtime);
            Return returnNode = new Return(new SafeAdd(new Local(CiKind.Long, 0, graph), new Local(CiKind.Long, 1, graph), graph), graph);
            graph.start().setNext(returnNode);
            graph.setReturn(returnNode);
            return graph;
        }
        return null;
    }

}
