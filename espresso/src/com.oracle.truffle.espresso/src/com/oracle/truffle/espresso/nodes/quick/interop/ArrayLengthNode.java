/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.nodes.quick.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * ARRAYLENGTH bytecode with interop extensions.
 *
 * <p>
 * Augmented with two interop extensions:
 * <ul>
 * <li>For Truffle buffers ({@link InteropLibrary#hasBufferElements(Object) buffer-like} foreign
 * objects) wrapped as {@code byte[]}, ARRAYLENGTH is mapped to
 * {@link InteropLibrary#getBufferSize(Object)}.
 * <li>For {@link InteropLibrary#hasArrayElements(Object) array-like} foreign objects wrapped as
 * {@code byte[]}, ARRAYLENGTH is mapped to {@link InteropLibrary#getBufferSize(Object)}.
 * </ul>
 *
 * <p>
 * If the size of the foreign array does NOT fit in an int, {@link Integer#MAX_VALUE} is returned.
 */
@ImportStatic(Utils.class)
public abstract class ArrayLengthNode extends QuickNode {
    protected static final int LIMIT = 3;

    protected ArrayLengthNode(int top, int callerBCI) {
        super(top, callerBCI);
    }

    @Override
    public final int execute(VirtualFrame frame, long[] primitives, Object[] refs) {
        StaticObject array = nullCheck(BytecodeNode.popObject(refs, top - 1));
        BytecodeNode.putInt(primitives, top - 1, executeGetLength(array));
        return Bytecodes.stackEffectOf(Bytecodes.ARRAYLENGTH);
    }

    abstract int executeGetLength(StaticObject array);

    @Specialization(guards = {
                    "array.isForeignObject()",
                    "isBufferLikeByteArray(context, interop, array)",
    })
    int doBufferLike(StaticObject array,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context,
                    @Cached BranchProfile sizeOverflowProfile) {
        try {
            long bufferLength = interop.getBufferSize(array.rawForeignObject());
            if (bufferLength > Integer.MAX_VALUE) {
                sizeOverflowProfile.enter();
                return Integer.MAX_VALUE;
            }
            return (int) bufferLength;
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Specialization(guards = {
                    "array.isForeignObject()",
                    "!isBufferLikeByteArray(context, interop, array)",
                    "isArrayLike(interop, array.rawForeignObject())"
    })
    int doArrayLike(StaticObject array,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context,
                    @Cached BranchProfile sizeOverflowProfile) {
        try {
            long arrayLength = interop.getArraySize(array.rawForeignObject());
            if (arrayLength > Integer.MAX_VALUE) {
                sizeOverflowProfile.enter();
                return Integer.MAX_VALUE;
            }
            return (int) arrayLength;
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Specialization(guards = "array.isEspressoObject()")
    int doEspresso(StaticObject array) {
        return InterpreterToVM.arrayLength(array);
    }
}
