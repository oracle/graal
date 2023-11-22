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
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.ffi.Callback;
import com.oracle.truffle.espresso.ffi.NativeAccess;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public class UpcallStubs {
    private final ConcurrentHashMap<Long, UpcallStub> upcallRoots; // keeps upcalls alive
    private final Platform platform;
    private final NativeAccess nativeAccess;
    private final EspressoLanguage language;

    public UpcallStubs(Platform platform, NativeAccess nativeAccess, EspressoLanguage language) {
        this.platform = platform;
        this.nativeAccess = nativeAccess;
        this.language = language;
        upcallRoots = new ConcurrentHashMap<>();
    }

    @TruffleBoundary
    public long makeStub(StaticObject mh, Method target, VMStorage[] argRegs, VMStorage[] retRegs, boolean needsReturnBuffer, long returnBufferSize) {
        UpcallStub stub = create(mh, target, argRegs, retRegs, needsReturnBuffer, returnBufferSize);
        long address = stub.getAddress();
        upcallRoots.put(address, stub);
        return address;
    }

    private UpcallStub create(StaticObject mh, Method target, VMStorage[] argRegs, VMStorage[] retRegs,
                    @SuppressWarnings("unused") boolean needsReturnBuffer, @SuppressWarnings("unused") long returnBufferSize) {
        // Do we really need resolved or are java kinds enough?
        Klass[] javaPTypes = target.resolveParameterKlasses();
        Klass javaRType = target.resolveReturnKlass();
        assert javaPTypes.length > 0;
        // We must inject the MH as the first argument.
        // Ignore it when looking at the native signature

        // TODO common out some of the argument shuffle computation with DowncallStubs
        ArgumentsCalculator argsCalc = platform.getArgumentsCalculator();
        // shuffle doesn't contain the 1st java argument (mh)
        int[] shuffle = new int[javaPTypes.length - 1];
        int nativeArgsCount = javaPTypes.length - 1;
        NativeType[] nativeParamTypes = new NativeType[nativeArgsCount];
        int nativeIndex = 0;
        for (int nativeArgIndex = 0; nativeArgIndex < nativeParamTypes.length; nativeArgIndex++) {
            int javaArgIndex = nativeArgIndex + 1;
            Klass pType = javaPTypes[javaArgIndex];
            VMStorage argReg = argRegs[nativeArgIndex];
            StorageType regType = argReg.type(platform);
            if (regType.isPlaceholder()) {
                switch (argReg.getStubLocation(platform)) {
                    default -> throw EspressoError.unimplemented(argReg.getStubLocation(platform).toString());
                }
            } else {
                VMStorage nextInputReg;
                Klass nextPType;
                if (nativeArgIndex + 1 < nativeParamTypes.length) {
                    nextInputReg = argRegs[nativeArgIndex + 1];
                    nextPType = javaPTypes[javaArgIndex + 1];
                } else {
                    nextInputReg = null;
                    nextPType = null;
                }
                int index = argsCalc.getNextInputIndex(argReg, pType, nextInputReg, nextPType);
                if (index >= 0) {
                    shuffle[javaArgIndex - 1] = index;
                    nativeParamTypes[nativeIndex] = argReg.asNativeType(platform, pType);
                    nativeIndex++;
                } else if (index != ArgumentsCalculator.SKIP && !platform.ignoreDownCallArgument(argReg)) {
                    throw EspressoError.shouldNotReachHere("Cannot understand argument " + nativeArgIndex + " in upcall: " + argReg + " for type " + pType + " calc: " + argsCalc);
                }
            }
        }

        NativeType nativeReturnType = NativeType.VOID;
        if (retRegs.length > 0) {
            EspressoError.guarantee(retRegs.length == 1, "unimplemented");
            if (!argsCalc.checkReturn(retRegs[0], javaRType)) {
                throw EspressoError.shouldNotReachHere("Cannot understand out reg in downcall: " + retRegs[0] + " for type " + javaRType);
            }
            nativeReturnType = retRegs[0].asNativeType(platform, javaRType);
        }

        NativeSignature nativeSignature = NativeSignature.create(nativeReturnType, Arrays.copyOf(nativeParamTypes, nativeIndex));
        CallTarget callTarget = target.getCallTarget();
        return new UpcallStub(callTarget, mh, shuffle, language, nativeArgsCount, nativeSignature, nativeAccess);
    }

    @TruffleBoundary
    public boolean freeStub(long addr) {
        return upcallRoots.remove(addr) != null;
    }

    public static class UpcallStub implements Callback.Function {
        private final TruffleObject nativeClosure;
        private final CallTarget callTarget;
        private final StaticObject mh;
        private final int[] shuffle;
        private final EspressoLanguage language;

        @SuppressWarnings("this-escape")
        public UpcallStub(CallTarget callTarget, StaticObject mh, int[] shuffle, EspressoLanguage language, int nativeArgsCount, NativeSignature nativeSignature, NativeAccess nativeAccess) {
            this.callTarget = callTarget;
            this.mh = mh;
            this.shuffle = shuffle;
            this.language = language;
            this.nativeClosure = nativeAccess.createNativeClosure(new Callback(nativeArgsCount, this), nativeSignature);
        }

        public long getAddress() {
            try {
                return InteropLibrary.getUncached(nativeClosure).asPointer(nativeClosure);
            } catch (UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public Object call(Object... args) {
            language.getThreadLocalState().clearPendingException();
            return callTarget.call(processArgs(args));
        }

        @ExplodeLoop
        private Object[] processArgs(Object[] args) {
            Object[] javaArgs = new Object[args.length + 1];
            javaArgs[0] = mh;
            for (int i = 0; i < shuffle.length; i++) {
                javaArgs[i + 1] = args[shuffle[i]];
            }
            return javaArgs;
        }
    }
}
