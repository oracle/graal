/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.opt;

import com.sun.c1x.graph.IR;
import com.sun.cri.ri.*;

/**
 * This class implements an iterative, flow-sensitve type analysis that can be
 * used to remove redundant checkcasts and instanceof tests as well as devirtualize
 * and deinterface method calls.
 *
 * @author Ben L. Titzer
 */
public class TypeAnalyzer {

    final IR ir;

    public TypeAnalyzer(IR ir) {
        this.ir = ir;
    }

    // type information sources:
    // new, anewarray, newarray
    // parameter types
    // instanceof
    // checkcast
    // array stores
    // array loads
    // field loads
    // exception handler type
    // equality comparison
    // call to Object.clone()
    // call to Class.newInstance()
    // call to Array.newInstance()
    // call to Class.isInstance()

    // possible optimizations:
    // remove redundant checkcasts
    // fold instanceof tests
    // remove dead branches in folded instanceof
    // detect redundant store checks
    // convert invokeinterface to invokevirtual when possible
    // convert invokevirtual to invokespecial when possible
    // remove finalizer checks
    // specialize array copy calls
    // transform reflective Class.newInstance() into allocation
    // transform reflective Array.newInstance() into allocation
    // transform reflective Class.isInstance() to checkcast
    // transform reflective method invocation to direct method invocation

    private static class TypeApprox {
        final RiType type = null;
        final boolean exact = false;
    }
}
