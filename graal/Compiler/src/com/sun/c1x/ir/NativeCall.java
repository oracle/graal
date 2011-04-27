/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.ir;

import com.sun.c1x.debug.*;
import com.sun.c1x.value.*;
import com.sun.cri.ri.*;

/**
 * Represents a call to a native function from within a native method stub.
 *
 * @author Doug Simon
 */
public final class NativeCall extends StateSplit {

    /**
     * The instruction that produces the native function address for this native call.
     */
    private Value address;

    /**
     * The native method for this native call.
     */
    public final RiMethod nativeMethod;

    /**
     * The signature of the call which is derived from {@link #nativeMethod} but is not
     * the same as its {@linkplain RiMethod#signature() signature}.
     */
    public final RiSignature signature;

    /**
     * The list of instructions that produce the arguments for this native call.
     */
    public final Value[] arguments;

    /**
     * Constructs a new NativeCall instruction.
     *
     * @param nativeMethod TODO
     * @param signature TODO
     * @param address TODO
     * @param args the list of instructions producing arguments to the invocation
     * @param stateBefore the state before executing the invocation
     */
    public NativeCall(RiMethod nativeMethod, RiSignature signature, Value address, Value[] args, FrameState stateBefore) {
        super(signature.returnKind().stackKind(), stateBefore);
        this.address = address;
        this.nativeMethod = nativeMethod;
        this.arguments = args;
        this.signature = signature;
        assert nativeMethod.jniSymbol() != null;
    }

    /**
     * The native function may call back into the VM which may call method that can trap.
     */
    @Override
    public boolean canTrap() {
        return true;
    }

    /**
     * Gets the instruction that produces the native function address for this native call.
     * @return the instruction
     */
    public Value address() {
        return address;
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        for (int i = 0; i < arguments.length; i++) {
            Value arg = arguments[i];
            if (arg != null) {
                arguments[i] = closure.apply(arg);
                assert arguments[i] != null;
            }
        }
        address = closure.apply(address);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitNativeCall(this);
    }

    @Override
    public void print(LogStream out) {
        out.print(nativeMethod.jniSymbol()).print('(');
        for (int i = 0; i < arguments.length; i++) {
            if (i > 0) {
                out.print(", ");
            }
            out.print(arguments[i]);
        }
        out.print(')');
    }
}
