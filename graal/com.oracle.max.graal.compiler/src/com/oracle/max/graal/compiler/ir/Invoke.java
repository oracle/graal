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
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code Invoke} instruction represents all kinds of method calls.
 */
public final class Invoke extends AbstractMemoryCheckpointNode implements ExceptionEdgeInstruction {

    @NodeSuccessor
    private FixedNode exceptionEdge;

    @NodeInput
    private final NodeInputList<Value> arguments;

    @Override
    public FixedNode exceptionEdge() {
        return exceptionEdge;
    }

    public void setExceptionEdge(FixedNode x) {
        updatePredecessors(exceptionEdge, x);
        exceptionEdge = x;
    }

    private final int argumentCount;

    private boolean canInline = true;

    public boolean canInline() {
        return canInline;
    }

    public void setCanInline(boolean b) {
        canInline = b;
    }

    public NodeInputList<Value> arguments() {
        return arguments;
    }

    public final int opcode;
    public final RiMethod target;
    public final RiType returnType;
    public final int bci; // XXX needed because we can not compute the bci from the sateBefore bci of this Invoke was optimized from INVOKEINTERFACE to INVOKESPECIAL

    /**
     * Constructs a new Invoke instruction.
     *
     * @param opcode the opcode of the invoke
     * @param result the result type
     * @param args the list of instructions producing arguments to the invocation, including the receiver object
     * @param isStatic {@code true} if this call is static (no receiver object)
     * @param target the target method being called
     */
    public Invoke(int bci, int opcode, CiKind result, Value[] args, RiMethod target, RiType returnType, Graph graph) {
        super(result, graph);
        arguments = new NodeInputList<Value>(this, args.length);
        this.opcode = opcode;
        this.target = target;
        this.returnType = returnType;
        this.bci = bci;

        this.argumentCount = args.length;
        for (int i = 0; i < args.length; i++) {
            arguments().set(i, args[i]);
        }
    }

    /**
     * Gets the opcode of this invoke instruction.
     * @return the opcode
     */
    public int opcode() {
        return opcode;
    }

    /**
     * Checks whether this is an invocation of a static method.
     * @return {@code true} if the invocation is a static invocation
     */
    public boolean isStatic() {
        return opcode == Bytecodes.INVOKESTATIC;
    }

    @Override
    public RiType declaredType() {
        return returnType;
    }

    /**
     * Gets the instruction that produces the receiver object for this invocation, if any.
     * @return the instruction that produces the receiver object for this invocation if any, {@code null} if this
     *         invocation does not take a receiver object
     */
    public Value receiver() {
        assert !isStatic();
        return arguments().get(0);
    }

    /**
     * Gets the target method for this invocation instruction.
     * @return the target method
     */
    public RiMethod target() {
        return target;
    }

    /**
     * Checks whether this invocation has a receiver object.
     * @return {@code true} if this invocation has a receiver object; {@code false} otherwise, if this is a
     *         static call
     */
    public boolean hasReceiver() {
        return !isStatic();
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitInvoke(this);
    }

    public CiKind[] signature() {
        CiKind receiver = isStatic() ? null : target.holder().kind();
        return Util.signatureToKinds(target.signature(), receiver);
    }

    @Override
    public void print(LogStream out) {
        int argStart = 0;
        if (hasReceiver()) {
            out.print(receiver()).print('.');
            argStart = 1;
        }

        RiMethod target = target();
        out.print(target.name()).print('(');
        for (int i = argStart; i < argumentCount; i++) {
            if (i > argStart) {
                out.print(", ");
            }
            out.print(arguments().get(i));
        }
        out.print(CiUtil.format(") [method: %H.%n(%p):%r]", target, false));
    }

    @Override
    public String toString() {
        return super.toString() + target;
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("opcode", Bytecodes.nameOf(opcode));
        properties.put("target", CiUtil.format("%H.%n(%p):%r", target, false));
        properties.put("bci", bci);
        return properties;
    }

    @Override
    public Node copy(Graph into) {
        Invoke x = new Invoke(bci, opcode, kind, new Value[argumentCount], target, returnType, into);
        x.setCanInline(canInline);
        super.copyInto(x);
        return x;
    }
}
