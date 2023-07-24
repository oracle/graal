/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime.panama;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.ffi.NativeAccess;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public final class DowncallStubs {
    public static final int MAX_STUB_COUNT = Integer.MAX_VALUE - 8;
    private final Platform platform;
    private DowncallStub[] stubs;
    private int nextId = 0;

    public DowncallStubs(Platform platform) {
        this.platform = platform;
    }

    @TruffleBoundary
    public long makeStub(Klass[] pTypes, Klass rType, VMStorage[] inputRegs, VMStorage[] outRegs, boolean needsReturnBuffer, int capturedStateMask, boolean needsTransition) {
        int id;
        synchronized (this) {
            id = nextId++;
        }
        DowncallStub stub = create(pTypes, rType, inputRegs, outRegs, needsReturnBuffer, capturedStateMask, needsTransition);

        synchronized (this) {
            if (stubs == null) {
                assert id == 0;
                stubs = new DowncallStub[8];
            } else if (id >= stubs.length) {
                long newSize = stubs.length * 2L;
                if (newSize > MAX_STUB_COUNT) {
                    newSize = MAX_STUB_COUNT;
                    if (id >= newSize) {
                        throw EspressoError.fatal("Too many stubs!");
                    }
                }
                stubs = Arrays.copyOf(stubs, (int) newSize);
            }
            stubs[id] = stub;
        }
        return id;
    }

    private DowncallStub create(Klass[] pTypes, Klass rType, VMStorage[] inputRegs, VMStorage[] outRegs,
                    boolean needsReturnBuffer, int capturedStateMask, @SuppressWarnings("unused") boolean needsTransition) {
        assert pTypes.length == inputRegs.length;
        EspressoError.guarantee(!needsReturnBuffer, "unimplemented");
        EspressoError.guarantee(capturedStateMask == 0, "unimplemented");

        ArgumentsCalculator argsCalc = platform.getArgumentsCalculator();
        int targetIndex = -1;
        int[] shuffle = new int[pTypes.length];
        NativeType[] nativeParamTypes = new NativeType[pTypes.length];
        int nativeIndex = 0;
        for (int i = 0; i < pTypes.length; i++) {
            Klass pType = pTypes[i];
            VMStorage inputReg = inputRegs[i];
            StorageType regType = inputReg.type(platform);
            if (regType.isPlaceholder()) {
                switch (inputReg.getStubLocation(platform)) {
                    case TARGET_ADDRESS -> targetIndex = i;
                    default -> throw EspressoError.unimplemented(inputReg.getStubLocation(platform).toString());
                }
            } else {
                int index = argsCalc.getNextInputIndex(inputReg, pType);
                if (index >= 0) {
                    shuffle[nativeIndex] = i;
                    nativeParamTypes[nativeIndex] = inputReg.asNativeType(platform, pType);
                    nativeIndex++;
                } else if (!platform.ignoreDownCallArgument(inputReg)) {
                    throw EspressoError.shouldNotReachHere("Cannot understand argument " + i + " in downcall: " + inputReg + " for type " + pType + " calc: " + argsCalc);
                }
            }
        }

        NativeType nativeReturnType = NativeType.VOID;
        if (outRegs.length > 0) {
            EspressoError.guarantee(outRegs.length == 1, "unimplemented");
            if (!argsCalc.checkReturn(outRegs[0], rType)) {
                throw EspressoError.shouldNotReachHere("Cannot understand out reg in downcall: " + outRegs[0] + " for type " + rType);
            }
            nativeReturnType = outRegs[0].asNativeType(platform, rType);
        }
        if (targetIndex < 0) {
            throw EspressoError.shouldNotReachHere("Didn't find the target index in downcall arguments");
        }
        NativeSignature nativeSignature = NativeSignature.create(nativeReturnType, Arrays.copyOf(nativeParamTypes, nativeIndex));
        return new DowncallStub(targetIndex, Arrays.copyOf(shuffle, nativeIndex), nativeSignature);
    }

    public boolean freeStub(long downcallStub) {
        // TODO maybe trim this when possible.
        int id = Math.toIntExact(downcallStub);
        if (stubs[id] == null) {
            return false;
        }
        stubs[id] = null;
        return true;
    }

    public DowncallStub getStub(long downcallStub) {
        int id = Math.toIntExact(downcallStub);
        return stubs[id];
    }

    public static final class DowncallStub {
        private final int targetIndex;
        @CompilationFinal(dimensions = 1) private final int[] shuffle;
        final NativeSignature signature;
        @CompilationFinal private Object callableSignature;

        public DowncallStub(int targetIndex, int[] shuffle, NativeSignature signature) {
            this.targetIndex = targetIndex;
            this.shuffle = shuffle;
            this.signature = signature;
        }

        public long getTargetHandle(Object[] args) {
            return (long) args[targetIndex];
        }

        @ExplodeLoop
        public Object[] processArgs(Object[] args) {
            Object[] nativeArgs = new Object[shuffle.length];
            for (int i = 0; i < shuffle.length; i++) {
                nativeArgs[i] = args[shuffle[i]];
            }
            return nativeArgs;
        }

        @TruffleBoundary
        public static TruffleObject resolveTarget(long targetHandle, EspressoContext context) {
            return context.getVM().getFunction(targetHandle);
        }

        public TruffleObject getTarget(Object[] args, EspressoContext context) {
            long targetHandle = getTargetHandle(args);
            return resolveTarget(targetHandle, context);
        }

        private Object getCallableSignature(NativeAccess access) {
            if (callableSignature == null) {
                if (CompilerDirectives.isPartialEvaluationConstant(this)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                callableSignature = access.getCallableSignature(signature, true);
            }
            return callableSignature;
        }

        public Object uncachedCall(Object[] args, EspressoContext context) {
            TruffleObject target = getTarget(args, context);
            NativeAccess access = context.getNativeAccess();
            try {
                return access.callSignature(getCallableSignature(access), target, processArgs(args));
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }
    }
}
