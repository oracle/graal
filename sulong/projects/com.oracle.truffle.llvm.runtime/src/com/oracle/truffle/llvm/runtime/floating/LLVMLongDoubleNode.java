/*
 * Copyright (c) 2023, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.floating;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.ContextExtension;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.interop.nfi.LLVMNativeConvertNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.nfi.api.SignatureLibrary;

public abstract class LLVMLongDoubleNode extends LLVMExpressionNode {

    final String name;
    private final String functionName;
    private final String signature;

    final ContextExtension.Key<NativeContextExtension> nativeCtxExtKey;

    public abstract LLVMLongDoubleFloatingPoint execute(Object... args);

    public final LongDoubleKinds kind;

    public enum LongDoubleKinds {
        FP80,
        FP128;
    }

    LLVMLongDoubleNode(String name, String signature, LongDoubleKinds kind) {
        this.name = name;
        this.functionName = "__sulong_longdouble_" + name;
        this.signature = signature;
        this.kind = kind;
        this.nativeCtxExtKey = LLVMLanguage.get(this).lookupContextExtension(NativeContextExtension.class);
    }

    protected NativeContextExtension.WellKnownNativeFunctionNode createFunction() {
        LLVMContext context = LLVMContext.get(this);
        NativeContextExtension nativeContextExtension = context.getContextExtensionOrNull(NativeContextExtension.class);
        if (nativeContextExtension == null) {
            return null;
        } else {
            return nativeContextExtension.getWellKnownNativeFunction(functionName, signature);
        }
    }

    protected NativeContextExtension.WellKnownNativeFunctionAndSignature getFunction() {
        NativeContextExtension nativeContextExtension = nativeCtxExtKey.get(LLVMContext.get(this));
        return nativeContextExtension.getWellKnownNativeFunctionAndSignature(functionName, signature);
    }

    protected LLVMNativeConvertNode createToLongDouble() {
        return switch (kind) {
            case FP80 -> LLVMNativeConvertNode.createFromNative(PrimitiveType.X86_FP80);
            case FP128 -> LLVMNativeConvertNode.createFromNative(PrimitiveType.F128);
        };
    }

    @NodeChild(value = "x", type = LLVMExpressionNode.class)
    @NodeChild(value = "y", type = LLVMExpressionNode.class)
    abstract static class LLVMLongDoubleNativeCallNode extends LLVMLongDoubleNode {

        LLVMLongDoubleNativeCallNode(String name, String signature, LongDoubleKinds kind) {
            super(name, signature, kind);
        }

        @Specialization(guards = "function != null")
        protected LLVMLongDoubleFloatingPoint doCall(Object x, Object y,
                        @Cached("createFunction()") NativeContextExtension.WellKnownNativeFunctionNode function,
                        @Cached("createToLongDouble()") LLVMNativeConvertNode nativeConvert) {
            try {
                Object ret = function.execute(x, y);
                return (LLVMLongDoubleFloatingPoint) nativeConvert.executeConvert(ret);
            } catch (InteropException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = "nativeCtxExtKey != null", replaces = "doCall")
        protected LLVMLongDoubleFloatingPoint doCallAOT(Object x, Object y,
                        @CachedLibrary(limit = "1") SignatureLibrary signatureLibrary,
                        @Cached("createToLongDouble()") LLVMNativeConvertNode nativeConvert) {
            NativeContextExtension.WellKnownNativeFunctionAndSignature wkFunSig = getFunction();
            try {
                Object ret = signatureLibrary.call(wkFunSig.getSignature(), wkFunSig.getFunction(), x, y);
                return (LLVMLongDoubleFloatingPoint) nativeConvert.executeConvert(ret);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @CompilerDirectives.TruffleBoundary
        @Specialization(guards = "nativeCtxExtKey == null")
        protected LLVMLongDoubleFloatingPoint doCallNoNative(LLVMLongDoubleFloatingPoint x, LLVMLongDoubleFloatingPoint y) {
            // imprecise workaround for cases in which NFI isn't available
            double xDouble = x.toDoubleValue();
            double yDouble = y.toDoubleValue();
            double result;
            switch (name) {
                case "add":
                    result = xDouble + yDouble;
                    break;
                case "sub":
                    result = xDouble - yDouble;
                    break;
                case "mul":
                    result = xDouble * yDouble;
                    break;
                case "div":
                    result = xDouble / yDouble;
                    break;
                case "mod":
                    result = xDouble % yDouble;
                    break;
                default:
                    throw new AssertionError("unexpected long double bit float operation: " + name + ", for the type: " + super.kind);
            }
            return switch (super.kind) {
                case FP80 -> LLVM80BitFloat.fromDouble(result);
                case FP128 -> LLVM128BitFloat.fromDouble(result);
            };
        }

        @Override
        public String toString() {
            return super.kind + " " + name;
        }
    }

    @NodeChild(value = "x", type = LLVMExpressionNode.class)
    abstract static class LLVMLongDoubleUnaryNativeCallNode extends LLVMLongDoubleNode {

        LLVMLongDoubleUnaryNativeCallNode(String name, String signature, LongDoubleKinds kind) {
            super(name, signature, kind);
        }

        @Specialization(guards = "function != null")
        protected LLVMLongDoubleFloatingPoint doCall(Object x,
                        @Cached("createFunction()") NativeContextExtension.WellKnownNativeFunctionNode function,
                        @Cached("createToLongDouble()") LLVMNativeConvertNode nativeConvert) {
            try {
                Object ret = function.execute(x);
                return (LLVMLongDoubleFloatingPoint) nativeConvert.executeConvert(ret);
            } catch (InteropException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = "nativeCtxExtKey != null", replaces = "doCall")
        protected LLVMLongDoubleFloatingPoint doCallAOT(Object x,
                        @CachedLibrary(limit = "1") SignatureLibrary signatureLibrary,
                        @Cached("createToLongDouble()") LLVMNativeConvertNode nativeConvert) {
            NativeContextExtension.WellKnownNativeFunctionAndSignature wkFunSig = getFunction();
            try {
                Object ret = signatureLibrary.call(wkFunSig.getSignature(), wkFunSig.getFunction(), x);
                return (LLVMLongDoubleFloatingPoint) nativeConvert.executeConvert(ret);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    public static LLVMLongDoubleNode createAddNode(LongDoubleKinds kind) {
        return createNativeCallNode("add", kind);
    }

    public static LLVMLongDoubleNode createSubNode(LongDoubleKinds kind) {
        return createNativeCallNode("sub", kind);
    }

    public static LLVMLongDoubleNode createMulNode(LongDoubleKinds kind) {
        return createNativeCallNode("mul", kind);
    }

    public static LLVMLongDoubleNode createDivNode(LongDoubleKinds kind) {
        return createNativeCallNode("div", kind);
    }

    public static LLVMLongDoubleNode createRemNode(LongDoubleKinds kind) {
        return createNativeCallNode("mod", kind);
    }

    public static LLVMLongDoubleNode createPowNode(LLVMExpressionNode x, LLVMExpressionNode y, LongDoubleKinds kind) {
        return createNativeCallNode("pow", kind, x, y);
    }

    private static LLVMLongDoubleNode createNativeCallNode(String name, LongDoubleKinds kind, LLVMExpressionNode x, LLVMExpressionNode y) {
        return switch (kind) {
            case FP80 -> LLVMLongDoubleNodeFactory.LLVMLongDoubleNativeCallNodeGen.create(name, "(FP80,FP80):FP80", kind, x, y);
            case FP128 -> LLVMLongDoubleNodeFactory.LLVMLongDoubleNativeCallNodeGen.create(name, "(FP128,FP128):FP128", kind, x, y);
        };
    }

    private static LLVMLongDoubleNode createNativeCallNode(String name, LongDoubleKinds kind) {
        return switch (kind) {
            case FP80 -> LLVMLongDoubleNodeFactory.LLVMLongDoubleNativeCallNodeGen.create(name, "(FP80,FP80):FP80", kind, null, null);
            case FP128 -> LLVMLongDoubleNodeFactory.LLVMLongDoubleNativeCallNodeGen.create(name, "(FP128,FP128):FP128", kind, null, null);
        };
    }

    public static LLVMLongDoubleNode createUnary(String name, LLVMExpressionNode x, LongDoubleKinds kind) {
        return switch (kind) {
            case FP80 -> LLVMLongDoubleNodeFactory.LLVMLongDoubleUnaryNativeCallNodeGen.create(name, "(FP80):FP80", kind, x);
            case FP128 -> LLVMLongDoubleNodeFactory.LLVMLongDoubleUnaryNativeCallNodeGen.create(name, "(FP128):FP128", kind, x);
        };
    }
}
