/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.factories;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.nodes.func.LLVMArgNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMAtExitNode;
import com.oracle.truffle.llvm.nodes.func.LLVMBeginCatchNode;
import com.oracle.truffle.llvm.nodes.func.LLVMEndCatchNode;
import com.oracle.truffle.llvm.nodes.func.LLVMFreeExceptionNode;
import com.oracle.truffle.llvm.nodes.func.LLVMRethrowNode;
import com.oracle.truffle.llvm.nodes.func.LLVMThrowExceptionNode;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMAbortNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMAtExitNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMACosNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMASinNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMATan2NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMATanNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMAbsNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMCeilNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMCosNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMCoshNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMExpNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFAbsNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFloorNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLAbsNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLog10NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLogNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMPowNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMRintNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMSinNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMSinhNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMSqrtNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMTanNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMTanhNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMToUpperNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMExitNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMSignalNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMTruffleOnlyIntrinsicsFactory.LLVMStrCmpNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMTruffleOnlyIntrinsicsFactory.LLVMStrlenNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMTruffleReadBytesNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleAddressToFunctionNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleBinaryFactory.LLVMTruffleHasSizeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleBinaryFactory.LLVMTruffleIsBoxedNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleBinaryFactory.LLVMTruffleIsExecutableNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleBinaryFactory.LLVMTruffleIsNullNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleExecuteNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleGetArgNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleGetSizeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleHandleToManagedNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleImportCachedNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleImportNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleInvokeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleIsTruffleObjectNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleManagedMallocNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleManagedToHandleNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleReadFactory.LLVMTruffleReadFromIndexNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleReadFactory.LLVMTruffleReadFromNameNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleReadNBytesNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleReadNStringNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleReadStringNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleReleaseHandleNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleWriteFactory.LLVMTruffleWriteToIndexNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleWriteFactory.LLVMTruffleWriteToNameNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsicRootNodeFactory.LLVMIntrinsicExpressionNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemoryIntrinsicFactory.LLVMCallocNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemoryIntrinsicFactory.LLVMFreeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemoryIntrinsicFactory.LLVMMallocNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMComplexDiv;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMComplexDivSC;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMComplexMul;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMExitException;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NativeIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.interop.ToLLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.Type;

public class LLVMNativeIntrinsicsProvider implements NativeIntrinsicProvider {

    @Override
    @TruffleBoundary
    public final boolean isIntrinsified(String name) {
        return factories.containsKey(name);
    }

    @Override
    public final RootCallTarget generateIntrinsic(String name, FunctionType type) {
        CompilerAsserts.neverPartOfCompilation();
        if (factories.containsKey(name)) {
            return factories.get(name).generate(type);
        }
        return null;
    }

    @Override
    public final boolean forceInline(String name) {
        CompilerAsserts.neverPartOfCompilation();
        if (factories.containsKey(name)) {
            return factories.get(name).forceInline;
        }
        return false;
    }

    protected final Map<String, LLVMNativeIntrinsicFactory> factories = new HashMap<>();
    protected final LLVMLanguage language;
    protected final LLVMContext context;

    public LLVMNativeIntrinsicsProvider(LLVMContext context, LLVMLanguage language) {
        this.language = language;
        this.context = context;
    }

    public abstract static class LLVMNativeIntrinsicFactory {
        private final boolean forceInline;

        public LLVMNativeIntrinsicFactory(boolean forceInline) {
            this.forceInline = forceInline;
        }

        protected abstract RootCallTarget generate(FunctionType type);
    }

    public LLVMNativeIntrinsicsProvider collectIntrinsics() {
        registerTruffleIntrinsics();
        registerAbortIntrinsics();
        registerTruffleOnlyIntrinsics();
        registerMathFunctionIntrinsics();
        registerMemoryFunctionIntrinsics();
        registerExceptionIntrinsics();
        registerComplexNumberIntrinsics();
        return this;
    }

    protected RootCallTarget wrap(LLVMExpressionNode node) {
        return Truffle.getRuntime().createCallTarget(LLVMIntrinsicExpressionNodeGen.create(language, node));
    }

    protected LLVMExpressionNode[] argumentsArray(int startIndex, int arity) {
        LLVMExpressionNode[] args = new LLVMExpressionNode[arity];
        for (int i = 0; i < arity; i++) {
            args[i] = LLVMArgNodeGen.create(i + startIndex);
        }
        return args;
    }

    protected Type[] argumentsTypes(int startIndex, Type[] types) {
        Type[] args = new Type[types.length - startIndex];
        for (int i = startIndex; i < types.length; i++) {
            args[i - startIndex] = types[i];
        }
        return args;
    }

    protected void registerTruffleIntrinsics() {
        LLVMNativeIntrinsicFactory truffleWrite = new LLVMNativeIntrinsicFactory(true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleWriteToNameNodeGen.create(type.getArgumentTypes()[2], LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        };
        factories.put("@truffle_write", truffleWrite);
        factories.put("@truffle_write_i", truffleWrite);
        factories.put("@truffle_write_l", truffleWrite);
        factories.put("@truffle_write_c", truffleWrite);
        factories.put("@truffle_write_f", truffleWrite);
        factories.put("@truffle_write_d", truffleWrite);
        factories.put("@truffle_write_b", truffleWrite);

        LLVMNativeIntrinsicFactory truffleWriteIdx = new LLVMNativeIntrinsicFactory(true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleWriteToIndexNodeGen.create(type.getArgumentTypes()[2], LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        };
        factories.put("@truffle_write_idx", truffleWriteIdx);
        factories.put("@truffle_write_idx_i", truffleWriteIdx);
        factories.put("@truffle_write_idx_l", truffleWriteIdx);
        factories.put("@truffle_write_idx_c", truffleWriteIdx);
        factories.put("@truffle_write_idx_f", truffleWriteIdx);
        factories.put("@truffle_write_idx_d", truffleWriteIdx);
        factories.put("@truffle_write_idx_b", truffleWriteIdx);

        factories.put("@truffle_read", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleReadFromNameNodeGen.create(ToLLVMNode.createNode(TruffleObject.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_i", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleReadFromNameNodeGen.create(ToLLVMNode.createNode(int.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_l", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleReadFromNameNodeGen.create(ToLLVMNode.createNode(long.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_c", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleReadFromNameNodeGen.create(ToLLVMNode.createNode(byte.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_f", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleReadFromNameNodeGen.create(ToLLVMNode.createNode(float.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_d", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleReadFromNameNodeGen.create(ToLLVMNode.createNode(double.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_b", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleReadFromNameNodeGen.create(ToLLVMNode.createNode(boolean.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_read_idx", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleReadFromIndexNodeGen.create(ToLLVMNode.createNode(TruffleObject.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_idx_i", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleReadFromIndexNodeGen.create(ToLLVMNode.createNode(int.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_idx_l", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleReadFromIndexNodeGen.create(ToLLVMNode.createNode(long.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_idx_c", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleReadFromIndexNodeGen.create(ToLLVMNode.createNode(byte.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_idx_f", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleReadFromIndexNodeGen.create(ToLLVMNode.createNode(float.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_idx_d", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleReadFromIndexNodeGen.create(ToLLVMNode.createNode(double.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_idx_b", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleReadFromIndexNodeGen.create(ToLLVMNode.createNode(boolean.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_unbox_i", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleUnboxNodeGen.create(ToLLVMNode.createNode(int.class), LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@truffle_unbox_l", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleUnboxNodeGen.create(ToLLVMNode.createNode(long.class), LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@truffle_unbox_c", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleUnboxNodeGen.create(ToLLVMNode.createNode(byte.class), LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@truffle_unbox_f", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleUnboxNodeGen.create(ToLLVMNode.createNode(float.class), LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@truffle_unbox_d", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleUnboxNodeGen.create(ToLLVMNode.createNode(double.class), LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@truffle_unbox_b", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleUnboxNodeGen.create(ToLLVMNode.createNode(boolean.class), LLVMArgNodeGen.create(1)));
            }
        });

        //

        factories.put("@truffle_invoke", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleInvokeNodeGen.create(ToLLVMNode.createNode(TruffleObject.class), argumentsArray(3, type.getArgumentTypes().length - 3),
                                argumentsTypes(3, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_invoke_i", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleInvokeNodeGen.create(ToLLVMNode.createNode(int.class), argumentsArray(3, type.getArgumentTypes().length - 3), argumentsTypes(3, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_invoke_l", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleInvokeNodeGen.create(ToLLVMNode.createNode(long.class), argumentsArray(3, type.getArgumentTypes().length - 3), argumentsTypes(3, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_invoke_c", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleInvokeNodeGen.create(ToLLVMNode.createNode(byte.class), argumentsArray(3, type.getArgumentTypes().length - 3), argumentsTypes(3, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_invoke_f", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleInvokeNodeGen.create(ToLLVMNode.createNode(float.class), argumentsArray(3, type.getArgumentTypes().length - 3), argumentsTypes(3, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_invoke_d", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleInvokeNodeGen.create(ToLLVMNode.createNode(double.class), argumentsArray(3, type.getArgumentTypes().length - 3), argumentsTypes(3, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_invoke_b", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleInvokeNodeGen.create(ToLLVMNode.createNode(boolean.class), argumentsArray(3, type.getArgumentTypes().length - 3), argumentsTypes(3, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        //

        factories.put("@truffle_execute", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleExecuteNodeGen.create(ToLLVMNode.createNode(TruffleObject.class), argumentsArray(2, type.getArgumentTypes().length - 2),
                                argumentsTypes(2, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_execute_i", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleExecuteNodeGen.create(ToLLVMNode.createNode(int.class), argumentsArray(2, type.getArgumentTypes().length - 2),
                                argumentsTypes(2, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_execute_l", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleExecuteNodeGen.create(ToLLVMNode.createNode(long.class), argumentsArray(2, type.getArgumentTypes().length - 2),
                                argumentsTypes(2, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_execute_c", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleExecuteNodeGen.create(ToLLVMNode.createNode(byte.class), argumentsArray(2, type.getArgumentTypes().length - 2),
                                argumentsTypes(2, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_execute_f", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleExecuteNodeGen.create(ToLLVMNode.createNode(float.class), argumentsArray(2, type.getArgumentTypes().length - 2),
                                argumentsTypes(2, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_execute_d", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleExecuteNodeGen.create(ToLLVMNode.createNode(double.class), argumentsArray(2, type.getArgumentTypes().length - 2),
                                argumentsTypes(2, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_execute_b", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleExecuteNodeGen.create(ToLLVMNode.createNode(boolean.class), argumentsArray(2, type.getArgumentTypes().length - 2),
                                argumentsTypes(2, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1)));
            }
        });

        //

        factories.put("@truffle_get_arg", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleGetArgNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        //

        factories.put("@truffle_import", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleImportNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_import_cached", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleImportCachedNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_address_to_function", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleAddressToFunctionNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_is_executable", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleIsExecutableNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_is_null", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleIsNullNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_has_size", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleHasSizeNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_is_boxed", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleIsBoxedNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_get_size", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleGetSizeNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_read_string", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleReadStringNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_read_n_string", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleReadNStringNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_read_bytes", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleReadBytesNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_read_n_bytes", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleReadNBytesNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_is_truffle_object", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleIsTruffleObjectNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_managed_malloc", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleManagedMallocNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_handle_for_managed", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleManagedToHandleNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_release_handle", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleReleaseHandleNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_managed_from_handle", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTruffleHandleToManagedNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
    }

    protected void registerAbortIntrinsics() {
        factories.put("@_gfortran_abort", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMAbortNodeGen.create());
            }
        });
        factories.put("@abort", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMAbortNodeGen.create());
            }
        });

        factories.put("@exit", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMExitNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@atexit", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMAtExitNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@signal", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMSignalNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
    }

    protected void registerTruffleOnlyIntrinsics() {
        factories.put("@strlen", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMStrlenNodeGen.create(context.getNativeLookup().getNativeFunction("@strlen"), LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@strcmp", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMStrCmpNodeGen.create(context.getNativeLookup().getNativeFunction("@strcmp"), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
    }

    protected void registerMathFunctionIntrinsics() {
        factories.put("@sqrt", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMSqrtNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@log", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMLogNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@log10", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMLog10NodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@rint", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMRintNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@ceil", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMCeilNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@floor", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMFloorNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@abs", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMAbsNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@labs", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMLAbsNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@fabs", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMFAbsNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@pow", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMPowNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@exp", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMExpNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@toupper", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMToUpperNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@sin", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMSinNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@sinf", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMSinNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@cos", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMCosNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@cosf", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMCosNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@tan", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTanNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@tanf", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTanNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@atan2", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMATan2NodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@atan2f", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMATan2NodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@asin", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMASinNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@asinf", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMASinNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@acos", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMACosNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@acosf", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMACosNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@atan", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMATanNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@atanf", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMATanNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@sinh", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMSinhNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@sinhf", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMSinhNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@cosh", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMCoshNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@coshf", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMCoshNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@tanh", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTanhNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@tanhf", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMTanhNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
    }

    protected void registerMemoryFunctionIntrinsics() {
        factories.put("@malloc", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMMallocNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@calloc", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMCallocNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@free", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(LLVMFreeNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
    }

    protected void registerExceptionIntrinsics() {
        factories.put("@__cxa_throw", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(new LLVMThrowExceptionNode(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        });
        factories.put("@__cxa_rethrow", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(new LLVMRethrowNode());
            }
        });
        factories.put("@__cxa_begin_catch", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(new LLVMBeginCatchNode(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@__cxa_end_catch", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(new LLVMEndCatchNode(LLVMArgNodeGen.create(0)));
            }
        });
        factories.put("@__cxa_free_exception", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(new LLVMFreeExceptionNode(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@__cxa_atexit", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(new LLVMAtExitNode(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        });
        factories.put("@__cxa_call_unexpected", new LLVMNativeIntrinsicFactory(true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(new LLVMExpressionNode() {
                    @Override
                    public Object executeGeneric(VirtualFrame frame) {
                        throw new LLVMExitException(134);
                    }
                });
            }
        });
    }

    public void registerComplexNumberIntrinsics() {
        factories.put("@__divdc3", new LLVMNativeIntrinsicFactory(true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(new LLVMComplexDiv(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3), LLVMArgNodeGen.create(4), LLVMArgNodeGen.create(5)));
            }
        });
        factories.put("@__muldc3", new LLVMNativeIntrinsicFactory(true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(new LLVMComplexMul(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3), LLVMArgNodeGen.create(4), LLVMArgNodeGen.create(5)));
            }
        });
        factories.put("@__divsc3", new LLVMNativeIntrinsicFactory(true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap(new LLVMComplexDivSC(LLVMArgNodeGen.create(0), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3), LLVMArgNodeGen.create(4)));
            }
        });
    }
}
