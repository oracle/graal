/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polybench.micro.expr;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.graalvm.polybench.micro.EvalError;

@NodeChild(value = "receiver", type = Expression.class)
public abstract class ExecuteExpression extends Expression {

    @Children Expression[] arguments;

    public ExecuteExpression(Expression[] arguments) {
        this.arguments = arguments;
    }

    @Specialization(limit = "3")
    @ExplodeLoop
    Object doRead(VirtualFrame frame, Object receiver,
                    @CachedLibrary("receiver") InteropLibrary interop) {
        Object[] params = new Object[arguments.length];
        for (int i = 0; i < params.length; i++) {
            params[i] = arguments[i].execute(frame);
        }
        try {
            return interop.execute(receiver, params);
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new EvalError(this, "Error during execute.");
        }
    }
}
