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
package com.oracle.graal.nodes.java;

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Loads an exception object passed by the runtime from a callee to an exception handler in a
 * caller. The node is only produced when lowering an {@link ExceptionObjectNode}.
 * <p>
 * The frame state upon entry to an exception handler is such that it is a
 * {@link BytecodeFrame#rethrowException rethrow exception} state and the stack contains exactly the
 * exception object (per the JVM spec) to rethrow. This means that the code generated for this node
 * must not cause a deoptimization as the runtime/interpreter would not have a valid location to
 * find the exception object to be rethrown.
 */
public class LoadExceptionObjectNode extends AbstractStateSplit implements Lowerable {

    public LoadExceptionObjectNode(Stamp stamp) {
        super(stamp);
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }
}
