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
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.ffi.NativeAccess;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.jni.RawBuffer;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.OS;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class DowncallStubs {
    private static final TruffleLogger LOGGER = TruffleLogger.getLogger(EspressoLanguage.ID, DowncallStubs.class);
    public static final int MAX_STUB_COUNT = Integer.MAX_VALUE - 8;

    private final Platform platform;
    private DowncallStub[] stubs;
    private final AtomicInteger nextId = new AtomicInteger(1); // 0 is reserved for null stub.

    public DowncallStubs(Platform platform) {
        this.platform = platform;
    }

    @TruffleBoundary
    public long makeStub(Klass[] pTypes, Klass rType, VMStorage[] inputRegs, VMStorage[] outRegs, boolean needsReturnBuffer, int capturedStateMask, boolean needsTransition) {
        int id = nextId.getAndIncrement();
        DowncallStub stub = create(pTypes, rType, inputRegs, outRegs, needsReturnBuffer, capturedStateMask, needsTransition);

        synchronized (this) {
            if (stubs == null) {
                assert id == 1;
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

        EnumSet<CapturableState> capturedStates = CapturableState.fromMask(capturedStateMask);
        assert validCapturableState(capturedStates, OS.getCurrent());

        LOGGER.fine(() -> {
            StringBuilder sb = new StringBuilder("Downcall stub request: ");
            if (!needsReturnBuffer) {
                sb.append("no ");
            }
            sb.append("ret buf, captured state=").append(capturedStates).append(", sig=(");
            for (int i = 0; i < pTypes.length; i++) {
                Klass pType = pTypes[i];
                sb.append(pType.getType());
                if (i < pTypes.length - 1) {
                    sb.append(", ");
                }
            }
            sb.append(")").append(rType.getType()).append(", in=(");
            for (int i = 0; i < inputRegs.length; i++) {
                VMStorage inputReg = inputRegs[i];
                sb.append(platform.toString(inputReg));
                if (i < inputRegs.length - 1) {
                    sb.append(", ");
                }
            }
            sb.append("), out=(");
            for (int i = 0; i < outRegs.length; i++) {
                VMStorage outReg = outRegs[i];
                sb.append(platform.toString(outReg));
                if (i < outRegs.length - 1) {
                    sb.append(", ");
                }
            }
            sb.append(")");
            return sb.toString();
        });

        ArgumentsCalculator argsCalc = platform.getArgumentsCalculator();
        int targetIndex = -1;
        int captureIndex = -1;
        int[] shuffle = new int[pTypes.length];
        NativeType[] nativeParamTypes = new NativeType[pTypes.length];
        int nativeIndex = 0;
        int nativeVarArgsIndex = -1;
        for (int i = 0; i < pTypes.length; i++) {
            Klass pType = pTypes[i];
            VMStorage inputReg = inputRegs[i];
            if (inputReg == null) {
                continue;
            }
            StorageType regType = inputReg.type(platform);
            if (regType.isPlaceholder()) {
                switch (inputReg.getStubLocation(platform)) {
                    case TARGET_ADDRESS -> targetIndex = i;
                    case CAPTURED_STATE_BUFFER -> captureIndex = i;
                    default -> throw EspressoError.unimplemented(inputReg.getStubLocation(platform).toString());
                }
            } else {
                VMStorage nextInputReg;
                Klass nextPType;
                if (i + 1 < pTypes.length) {
                    nextInputReg = inputRegs[i + 1];
                    nextPType = pTypes[i + 1];
                } else {
                    nextInputReg = null;
                    nextPType = null;
                }
                if (nativeVarArgsIndex < 0 && argsCalc.isVarArg(inputReg, pType, nextInputReg, nextPType)) {
                    nativeVarArgsIndex = nativeIndex;
                }
                int index = argsCalc.getNextInputIndex(inputReg, pType, nextInputReg, nextPType);
                if (index >= 0) {
                    NativeType nativeType = inputReg.asNativeType(platform, pType);
                    nativeParamTypes[nativeIndex] = nativeType;
                    shuffle[nativeIndex] = Shuffle.encode(i, nativeType);
                    nativeIndex++;
                } else if (index != ArgumentsCalculator.SKIP && !platform.ignoreDownCallArgument(inputReg)) {
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
        if (!capturedStates.isEmpty() && captureIndex < 0) {
            throw EspressoError.shouldNotReachHere("Didn't find the capture index in downcall arguments");
        }
        NativeSignature nativeSignature;
        if (nativeVarArgsIndex < 0) {
            nativeSignature = NativeSignature.create(nativeReturnType, Arrays.copyOf(nativeParamTypes, nativeIndex));
        } else {
            nativeSignature = NativeSignature.createVarArg(nativeReturnType, Arrays.copyOf(nativeParamTypes, nativeVarArgsIndex),
                            Arrays.copyOfRange(nativeParamTypes, nativeVarArgsIndex, nativeIndex));
        }
        DowncallStub downcallStub = new DowncallStub(targetIndex, Arrays.copyOf(shuffle, nativeIndex), nativeSignature, captureIndex, capturedStates);
        LOGGER.fine(() -> {
            StringBuilder sb = new StringBuilder("Creating downcall stub: targetIndex=");
            sb.append(downcallStub.targetIndex).append(" shuffle=").append(Arrays.toString(downcallStub.shuffle));
            sb.append(" sig=").append(downcallStub.signature).append(" capture=").append(capturedStates);
            if (downcallStub.captureIndex >= 0) {
                sb.append("@").append(downcallStub.captureIndex);
            }
            return sb.toString();
        });
        return downcallStub;
    }

    private static boolean validCapturableState(EnumSet<CapturableState> states, OS os) {
        for (CapturableState state : states) {
            assert state.isSupported(os);
        }
        return true;
    }

    public boolean freeStub(long downcallStub) {
        // TODO maybe trim this when possible.
        int id = Math.toIntExact(downcallStub);
        if (id <= 0 || id >= stubs.length) {
            LOGGER.warning(() -> "Unknown Stub handle in free downcall stub: " + id);
            // JDK will throw InternalError when we return false.
            return false;
        }
        if (stubs[id] == null) {
            return false;
        }
        stubs[id] = null;
        return true;
    }

    public DowncallStub getStub(long downcallStub, EspressoContext ctx) {
        int id = Math.toIntExact(downcallStub);
        DowncallStub stub;
        if (id <= 0 || id >= stubs.length) {
            stub = null;
        } else {
            stub = stubs[id];
        }
        if (stub == null) {
            // We don't expect to recover.
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Meta meta = ctx.getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_InternalError, "Unable to locate downcall stub with id: " + id);
        }
        return stub;
    }

    public static final class Shuffle {
        private static final int POINTER_ARG_FLAG = 1 << 31;
        private static final int INDEX_ARG_MASK = 0x7FFF_FFFF;

        private static int encode(int idx, NativeType type) {
            int res = idx;
            assert res == (res & INDEX_ARG_MASK);
            if (type == NativeType.POINTER) {
                res |= POINTER_ARG_FLAG;
            }
            return res;
        }

        private static int decodeIndex(int encoded) {
            return encoded & INDEX_ARG_MASK;
        }

        private static boolean decodeFlag(int encoded, int flag) {
            return (encoded & flag) != 0;
        }
    }

    public static final class DowncallStub {
        private final int targetIndex;
        @CompilationFinal(dimensions = 1) //
        private final int[] shuffle;
        final NativeSignature signature;
        private final int captureIndex;
        private final int captureMask;
        @CompilationFinal private Object callableSignature;
        @CompilationFinal private Object fallbackCallableSignature;

        public DowncallStub(int targetIndex, int[] shuffle, NativeSignature signature, int captureIndex, EnumSet<CapturableState> capturedStates) {
            this.targetIndex = targetIndex;
            this.shuffle = shuffle;
            this.signature = signature;
            this.captureIndex = captureIndex;
            this.captureMask = CapturableState.toMask(capturedStates);
            assert captureMask == 0 || captureIndex >= 0;
        }

        public long getTargetHandle(Object[] args) {
            return (long) args[targetIndex];
        }

        public long getCaptureAddress(Object[] args) {
            return (long) args[captureIndex];
        }

        @ExplodeLoop
        public Object[] processArgs(Object[] args, RawBuffer.Buffers buffers, EspressoContext ctx) {
            Object[] nativeArgs = new Object[shuffle.length];
            for (int i = 0; i < shuffle.length; i++) {
                int encoded = shuffle[i];
                Object arg = args[Shuffle.decodeIndex(encoded)];
                if (Shuffle.decodeFlag(encoded, Shuffle.POINTER_ARG_FLAG)) {
                    assert arg instanceof StaticObject;
                    arg = handlePointerArg(buffers, ctx, (StaticObject) arg);
                }
                nativeArgs[i] = arg;
            }
            return nativeArgs;
        }

        private static Object handlePointerArg(RawBuffer.Buffers buffers, EspressoContext ctx, StaticObject obj) {
            if (!obj.isArray()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ctx.getMeta().throwExceptionWithMessage(ctx.getMeta().java_lang_InternalError, "Unsupported java heap access in downcall stub: " + obj.getKlass());
            }
            RawBuffer buffer = RawBuffer.getNativeHeapPointer(obj, ctx);
            buffers.add(buffer, obj);
            return buffer.pointer();
        }

        @TruffleBoundary
        public static TruffleObject resolveTarget(long targetHandle, EspressoContext context) {
            return context.getVM().getFunction(targetHandle);
        }

        public TruffleObject getTarget(Object[] args, EspressoContext context) {
            long targetHandle = getTargetHandle(args);
            return resolveTarget(targetHandle, context);
        }

        private Object getCallableSignature(NativeAccess access, boolean forFallbackSymbol) {
            if (forFallbackSymbol) {
                return getFallbackCallableSignature(access);
            } else {
                return getCallableSignature(access);
            }
        }

        private Object getCallableSignature(NativeAccess access) {
            if (callableSignature == null) {
                if (CompilerDirectives.isPartialEvaluationConstant(this)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                callableSignature = access.getCallableSignature(signature, false);
            }
            return callableSignature;
        }

        private Object getFallbackCallableSignature(NativeAccess access) {
            if (fallbackCallableSignature == null) {
                if (CompilerDirectives.isPartialEvaluationConstant(this)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                fallbackCallableSignature = access.getCallableSignature(signature, true);
            }
            return fallbackCallableSignature;
        }

        public void captureState(Object[] args, InteropLibrary interop, EspressoContext context) {
            try {
                interop.execute(context.getVM().getMokapotCaptureState(), getCaptureAddress(args), captureMask);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        public boolean hasCapture() {
            return captureIndex >= 0;
        }

        @SuppressWarnings("try") // Throwable.addSuppressed blocklisted by SVM.
        public Object uncachedCall(Object[] args, EspressoContext context) {
            TruffleObject target = getTarget(args, context);
            NativeAccess access = context.getNativeAccess();
            RawBuffer.Buffers bb = new RawBuffer.Buffers();
            try {
                Object result = access.callSignature(getCallableSignature(access, access.isFallbackSymbol(target)), target, processArgs(args, bb, context));
                if (hasCapture()) {
                    captureState(args, InteropLibrary.getUncached(), context);
                }
                return result;
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            } finally {
                bb.writeBack(context);
            }
        }
    }
}
