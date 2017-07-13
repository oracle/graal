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
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMExp2NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMExpNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFAbsNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFloorNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFmodNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLAbsNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLdexpNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLog10NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLog2NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLogNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMModfNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMPowNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMRintNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMSinNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMSinhNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMSqrtNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMTanNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMTanhNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCTypeIntrinsicsFactory.LLVMIsalphaNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCTypeIntrinsicsFactory.LLVMIsspaceNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCTypeIntrinsicsFactory.LLVMIsupperNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCTypeIntrinsicsFactory.LLVMToUpperNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCTypeIntrinsicsFactory.LLVMTolowerNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMExitNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMSignalNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMTruffleOnlyIntrinsicsFactory.LLVMStrCmpNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMTruffleOnlyIntrinsicsFactory.LLVMStrlenNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMTruffleReadBytesNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMLoadLibraryNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotEvalNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMSulongFunctionToNativePointerNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleAddressToFunctionNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleBinaryFactory.LLVMTruffleHasSizeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleBinaryFactory.LLVMTruffleIsBoxedNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleBinaryFactory.LLVMTruffleIsExecutableNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleBinaryFactory.LLVMTruffleIsNullNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleExecuteNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleFreeCStringNodeGen;
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
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleStringAsCStringNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleWriteFactory.LLVMTruffleWriteToIndexNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleWriteFactory.LLVMTruffleWriteToNameNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsicRootNodeFactory.LLVMIntrinsicExpressionNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemoryIntrinsicFactory.LLVMCallocNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemoryIntrinsicFactory.LLVMFreeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemoryIntrinsicFactory.LLVMMallocNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemoryIntrinsicFactory.LLVMReallocNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMComplexDiv;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMComplexMul;
import com.oracle.truffle.llvm.nodes.intrinsics.sulong.LLVMRunConstructorFunctionsNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.sulong.LLVMRunDestructorFunctionsNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.sulong.LLVMRunGlobalVariableInitalizationNodeGen;
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

    @Override
    public final boolean forceSplit(String name) {
        CompilerAsserts.neverPartOfCompilation();
        if (factories.containsKey(name)) {
            return factories.get(name).forceSplit;
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
        private final boolean forceSplit;

        public LLVMNativeIntrinsicFactory(boolean forceInline, boolean forceSplit) {
            this.forceInline = forceInline;
            this.forceSplit = forceSplit;
        }

        protected abstract RootCallTarget generate(FunctionType type);
    }

    public LLVMNativeIntrinsicsProvider collectIntrinsics() {
        registerTruffleIntrinsics();
        registerSulongIntrinsics();
        registerAbortIntrinsics();
        registerTruffleOnlyIntrinsics();
        registerMathFunctionIntrinsics();
        registerMemoryFunctionIntrinsics();
        registerExceptionIntrinsics();
        registerComplexNumberIntrinsics();
        return this;
    }

    protected RootCallTarget wrap(String functionName, LLVMExpressionNode node) {
        return Truffle.getRuntime().createCallTarget(LLVMIntrinsicExpressionNodeGen.create(language, functionName, node));
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

    protected void registerSulongIntrinsics() {
        factories.put("@sulong_run_global_variable_initialization", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@sulong_run_global_variable_initialization", LLVMRunGlobalVariableInitalizationNodeGen.create());
            }
        });
        factories.put("@sulong_run_constructor_functions", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@sulong_run_constructor_functions",
                                LLVMRunConstructorFunctionsNodeGen.create());
            }
        });
        factories.put("@sulong_run_destructor_functions", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@sulong_run_destructor_functions",
                                LLVMRunDestructorFunctionsNodeGen.create());
            }
        });
    }

    protected void registerTruffleIntrinsics() {
        factories.put("@truffle_write", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_write", LLVMTruffleWriteToNameNodeGen.create(type.getArgumentTypes()[2], LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        });
        factories.put("@truffle_write_i", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_write_i", LLVMTruffleWriteToNameNodeGen.create(type.getArgumentTypes()[2], LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        });
        factories.put("@truffle_write_l", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_write_l", LLVMTruffleWriteToNameNodeGen.create(type.getArgumentTypes()[2], LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        });
        factories.put("@truffle_write_c", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_write_c", LLVMTruffleWriteToNameNodeGen.create(type.getArgumentTypes()[2], LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        });
        factories.put("@truffle_write_f", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_write_f", LLVMTruffleWriteToNameNodeGen.create(type.getArgumentTypes()[2], LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        });
        factories.put("@truffle_write_d", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("truffle_write_d", LLVMTruffleWriteToNameNodeGen.create(type.getArgumentTypes()[2], LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        });
        factories.put("@truffle_write_b", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_write_b", LLVMTruffleWriteToNameNodeGen.create(type.getArgumentTypes()[2], LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        });
        factories.put("@truffle_write_idx", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_write_idx", LLVMTruffleWriteToIndexNodeGen.create(type.getArgumentTypes()[2], LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        });
        factories.put("@truffle_write_idx_i", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_write_idx_i", LLVMTruffleWriteToIndexNodeGen.create(type.getArgumentTypes()[2], LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        });
        factories.put("@truffle_write_idx_l", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_write_idx_l", LLVMTruffleWriteToIndexNodeGen.create(type.getArgumentTypes()[2], LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        });
        factories.put("@truffle_write_idx_c", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_write_idx_c", LLVMTruffleWriteToIndexNodeGen.create(type.getArgumentTypes()[2], LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        });
        factories.put("@truffle_write_idx_f", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_write_idx_f", LLVMTruffleWriteToIndexNodeGen.create(type.getArgumentTypes()[2], LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        });
        factories.put("@truffle_write_idx_d", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_write_idx_d", LLVMTruffleWriteToIndexNodeGen.create(type.getArgumentTypes()[2], LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        });
        factories.put("@truffle_write_idx_b", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_write_idx_b", LLVMTruffleWriteToIndexNodeGen.create(type.getArgumentTypes()[2], LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        });

        factories.put("@truffle_read", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_read", LLVMTruffleReadFromNameNodeGen.create(ToLLVMNode.createNode(TruffleObject.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_i", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_read_i", LLVMTruffleReadFromNameNodeGen.create(ToLLVMNode.createNode(int.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_l", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_read_l", LLVMTruffleReadFromNameNodeGen.create(ToLLVMNode.createNode(long.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_c", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_read_c", LLVMTruffleReadFromNameNodeGen.create(ToLLVMNode.createNode(byte.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_f", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_read_f", LLVMTruffleReadFromNameNodeGen.create(ToLLVMNode.createNode(float.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_d", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_read_d", LLVMTruffleReadFromNameNodeGen.create(ToLLVMNode.createNode(double.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_b", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_read_b", LLVMTruffleReadFromNameNodeGen.create(ToLLVMNode.createNode(boolean.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_read_idx", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_read_idx", LLVMTruffleReadFromIndexNodeGen.create(ToLLVMNode.createNode(TruffleObject.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_idx_i", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_read_idx_i", LLVMTruffleReadFromIndexNodeGen.create(ToLLVMNode.createNode(int.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_idx_l", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_read_idx_l", LLVMTruffleReadFromIndexNodeGen.create(ToLLVMNode.createNode(long.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_idx_c", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_read_idx_c", LLVMTruffleReadFromIndexNodeGen.create(ToLLVMNode.createNode(byte.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_idx_f", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_read_idx_f", LLVMTruffleReadFromIndexNodeGen.create(ToLLVMNode.createNode(float.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_idx_d", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_read_idx_d", LLVMTruffleReadFromIndexNodeGen.create(ToLLVMNode.createNode(double.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@truffle_read_idx_b", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_read_idx_b", LLVMTruffleReadFromIndexNodeGen.create(ToLLVMNode.createNode(boolean.class), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_unbox_i", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_unbox_i", LLVMTruffleUnboxNodeGen.create(ToLLVMNode.createNode(int.class), LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@truffle_unbox_l", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_unbox_l", LLVMTruffleUnboxNodeGen.create(ToLLVMNode.createNode(long.class), LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@truffle_unbox_c", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_unbox_c", LLVMTruffleUnboxNodeGen.create(ToLLVMNode.createNode(byte.class), LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@truffle_unbox_f", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_unbox_f", LLVMTruffleUnboxNodeGen.create(ToLLVMNode.createNode(float.class), LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@truffle_unbox_d", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_unbox_d", LLVMTruffleUnboxNodeGen.create(ToLLVMNode.createNode(double.class), LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@truffle_unbox_b", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_unbox_b", LLVMTruffleUnboxNodeGen.create(ToLLVMNode.createNode(boolean.class), LLVMArgNodeGen.create(1)));
            }
        });

        //

        factories.put("@truffle_invoke", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_invoke",
                                LLVMTruffleInvokeNodeGen.create(LLVMArgNodeGen.create(0), ToLLVMNode.createNode(TruffleObject.class), argumentsArray(3, type.getArgumentTypes().length - 3),
                                                argumentsTypes(3, type.getArgumentTypes()),
                                                LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_invoke_i", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_invoke_i",
                                LLVMTruffleInvokeNodeGen.create(LLVMArgNodeGen.create(0), ToLLVMNode.createNode(int.class), argumentsArray(3, type.getArgumentTypes().length - 3),
                                                argumentsTypes(3, type.getArgumentTypes()),
                                                LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_invoke_l", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_invoke_l",
                                LLVMTruffleInvokeNodeGen.create(LLVMArgNodeGen.create(0), ToLLVMNode.createNode(long.class), argumentsArray(3, type.getArgumentTypes().length - 3),
                                                argumentsTypes(3, type.getArgumentTypes()),
                                                LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_invoke_c", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_invoke_c",
                                LLVMTruffleInvokeNodeGen.create(LLVMArgNodeGen.create(0), ToLLVMNode.createNode(byte.class), argumentsArray(3, type.getArgumentTypes().length - 3),
                                                argumentsTypes(3, type.getArgumentTypes()),
                                                LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_invoke_f", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_invoke_f",
                                LLVMTruffleInvokeNodeGen.create(LLVMArgNodeGen.create(0), ToLLVMNode.createNode(float.class), argumentsArray(3, type.getArgumentTypes().length - 3),
                                                argumentsTypes(3, type.getArgumentTypes()),
                                                LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_invoke_d", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_invoke_d",
                                LLVMTruffleInvokeNodeGen.create(LLVMArgNodeGen.create(0), ToLLVMNode.createNode(double.class), argumentsArray(3, type.getArgumentTypes().length - 3),
                                                argumentsTypes(3, type.getArgumentTypes()),
                                                LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_invoke_b", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_invoke_b", LLVMTruffleInvokeNodeGen.create(LLVMArgNodeGen.create(0), ToLLVMNode.createNode(boolean.class), argumentsArray(3, type.getArgumentTypes().length - 3),
                                argumentsTypes(3, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        //

        factories.put("@truffle_execute", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_execute",
                                LLVMTruffleExecuteNodeGen.create(LLVMArgNodeGen.create(0), ToLLVMNode.createNode(TruffleObject.class), argumentsArray(2, type.getArgumentTypes().length - 2),
                                                argumentsTypes(2, type.getArgumentTypes()),
                                                LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_execute_i", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_execute_i", LLVMTruffleExecuteNodeGen.create(LLVMArgNodeGen.create(0), ToLLVMNode.createNode(int.class), argumentsArray(2, type.getArgumentTypes().length - 2),
                                argumentsTypes(2, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_execute_l", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_execute_l", LLVMTruffleExecuteNodeGen.create(LLVMArgNodeGen.create(0), ToLLVMNode.createNode(long.class), argumentsArray(2, type.getArgumentTypes().length - 2),
                                argumentsTypes(2, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_execute_c", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_execute_c", LLVMTruffleExecuteNodeGen.create(LLVMArgNodeGen.create(0), ToLLVMNode.createNode(byte.class), argumentsArray(2, type.getArgumentTypes().length - 2),
                                argumentsTypes(2, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_execute_f", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_execute_f", LLVMTruffleExecuteNodeGen.create(LLVMArgNodeGen.create(0), ToLLVMNode.createNode(float.class), argumentsArray(2, type.getArgumentTypes().length - 2),
                                argumentsTypes(2, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_execute_d", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_execute_d", LLVMTruffleExecuteNodeGen.create(LLVMArgNodeGen.create(0), ToLLVMNode.createNode(double.class), argumentsArray(2, type.getArgumentTypes().length - 2),
                                argumentsTypes(2, type.getArgumentTypes()),
                                LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_execute_b", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_execute_b",
                                LLVMTruffleExecuteNodeGen.create(LLVMArgNodeGen.create(0), ToLLVMNode.createNode(boolean.class), argumentsArray(2, type.getArgumentTypes().length - 2),
                                                argumentsTypes(2, type.getArgumentTypes()),
                                                LLVMArgNodeGen.create(1)));
            }
        });

        //

        factories.put("@truffle_import", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_import", LLVMTruffleImportNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_import_cached", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_import_cached", LLVMTruffleImportCachedNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_address_to_function", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_address_to_function", LLVMTruffleAddressToFunctionNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_is_executable", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_is_executable", LLVMTruffleIsExecutableNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_is_null", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_is_null", LLVMTruffleIsNullNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_has_size", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_has_size", LLVMTruffleHasSizeNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_is_boxed", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_is_boxed", LLVMTruffleIsBoxedNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_get_size", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_get_size", LLVMTruffleGetSizeNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_read_string", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_read_string", LLVMTruffleReadStringNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_read_n_string", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_read_n_string", LLVMTruffleReadNStringNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_read_bytes", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_read_bytes", LLVMTruffleReadBytesNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_read_n_bytes", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_read_n_bytes", LLVMTruffleReadNBytesNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_string_to_cstr", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_string_to_cstr", LLVMTruffleStringAsCStringNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_free_cstr", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_free_cstr", LLVMTruffleFreeCStringNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_is_truffle_object", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_is_truffle_object", LLVMTruffleIsTruffleObjectNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_managed_malloc", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_managed_malloc", LLVMTruffleManagedMallocNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_handle_for_managed", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_handle_for_managed", LLVMTruffleManagedToHandleNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_release_handle", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_release_handle", LLVMTruffleReleaseHandleNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_managed_from_handle", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_managed_from_handle", LLVMTruffleHandleToManagedNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@truffle_sulong_function_to_native_pointer", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_sulong_function_to_native_pointer", LLVMSulongFunctionToNativePointerNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@truffle_load_library", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_load_library", LLVMLoadLibraryNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@truffle_polyglot_eval", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@truffle_polyglot_eval", LLVMPolyglotEvalNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
    }

    protected void registerAbortIntrinsics() {
        factories.put("@_gfortran_abort", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@_gfortran_abort", LLVMAbortNodeGen.create());
            }
        });
        factories.put("@abort", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@abort", LLVMAbortNodeGen.create());
            }
        });

        factories.put("@exit", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@exit", LLVMExitNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@atexit", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@atexit", LLVMAtExitNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@signal", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@signal", LLVMSignalNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
    }

    protected void registerTruffleOnlyIntrinsics() {
        factories.put("@strlen", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@strlen", LLVMStrlenNodeGen.create(context.getNativeLookup().getNativeFunction("@strlen"), LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@strcmp", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@strcmp", LLVMStrCmpNodeGen.create(context.getNativeLookup().getNativeFunction("@strcmp"), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
    }

    protected void registerMathFunctionIntrinsics() {
        factories.put("@log2", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@log2", LLVMLog2NodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@sqrt", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@sqrt", LLVMSqrtNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@log", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@log", LLVMLogNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@log10", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@log10", LLVMLog10NodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@rint", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@rint", LLVMRintNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@ceil", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@ceil", LLVMCeilNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@floor", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@floor", LLVMFloorNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@abs", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@abs", LLVMAbsNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@labs", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@labs", LLVMLAbsNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@fabs", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@fabs", LLVMFAbsNodeGen.create(LLVMArgNodeGen.create(1), null));
            }
        });
        factories.put("@pow", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@pow", LLVMPowNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), null));
            }
        });
        factories.put("@exp", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@exp", LLVMExpNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@exp2", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@exp2", LLVMExp2NodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@sin", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@sin", LLVMSinNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@sinf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@sinf", LLVMSinNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@cos", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@cos", LLVMCosNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@cosf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@cosf", LLVMCosNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@tan", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@tan", LLVMTanNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@tanf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@tanf", LLVMTanNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@atan2", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@atan2", LLVMATan2NodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@atan2f", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@atan2f", LLVMATan2NodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@asin", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@asin", LLVMASinNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@asinf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@asinf", LLVMASinNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@acos", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@acos", LLVMACosNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@acosf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@acosf", LLVMACosNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@atan", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@atan", LLVMATanNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@atanf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@atanf", LLVMATanNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@sinh", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@sinh", LLVMSinhNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@sinhf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@sinhf", LLVMSinhNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@cosh", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@cosh", LLVMCoshNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@coshf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@coshf", LLVMCoshNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@tanh", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@tanh", LLVMTanhNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@tanhf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@tanhf", LLVMTanhNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });

        factories.put("@ldexp", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@ldexp", LLVMLdexpNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@modf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@modf", LLVMModfNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });

        factories.put("@fmod", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@fmod", LLVMFmodNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
    }

    protected void registerCTypeIntrinsics() {
        factories.put("@isalpha", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@isalpha", LLVMIsalphaNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@tolower", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@tolower", LLVMTolowerNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@toupper", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@toupper", LLVMToUpperNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@isspace", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@isspace", LLVMIsspaceNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@isupper", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@isupper", LLVMIsupperNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
    }

    protected void registerMemoryFunctionIntrinsics() {
        factories.put("@malloc", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@malloc", LLVMMallocNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@calloc", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@calloc", LLVMCallocNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@realloc", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@realloc", LLVMReallocNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2)));
            }
        });
        factories.put("@free", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@free", LLVMFreeNodeGen.create(LLVMArgNodeGen.create(1)));
            }
        });
    }

    protected void registerExceptionIntrinsics() {
        factories.put("@__cxa_throw", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@__cxa_throw", new LLVMThrowExceptionNode(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        });
        factories.put("@__cxa_rethrow", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@__cxa_rethrow", new LLVMRethrowNode());
            }
        });
        factories.put("@__cxa_begin_catch", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@__cxa_begin_catch", new LLVMBeginCatchNode(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@__cxa_end_catch", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@__cxa_end_catch", new LLVMEndCatchNode(LLVMArgNodeGen.create(0)));
            }
        });
        factories.put("@__cxa_free_exception", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@__cxa_free_exception", new LLVMFreeExceptionNode(LLVMArgNodeGen.create(1)));
            }
        });
        factories.put("@__cxa_atexit", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@__cxa_atexit", new LLVMAtExitNode(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3)));
            }
        });
        factories.put("@__cxa_call_unexpected", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@__cxa_call_unexpected", new LLVMExpressionNode() {
                    @Override
                    public Object executeGeneric(VirtualFrame frame) {
                        throw new LLVMExitException(134);
                    }
                });
            }
        });
    }

    public void registerComplexNumberIntrinsics() {
        factories.put("@__divdc3", new LLVMNativeIntrinsicFactory(true, false) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@__divdc3", new LLVMComplexDiv(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3), LLVMArgNodeGen.create(4), LLVMArgNodeGen.create(5)));
            }
        });
        factories.put("@__muldc3", new LLVMNativeIntrinsicFactory(true, false) {
            @Override
            protected RootCallTarget generate(FunctionType type) {
                return wrap("@__muldc3", new LLVMComplexMul(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3), LLVMArgNodeGen.create(4), LLVMArgNodeGen.create(5)));
            }
        });
    }
}
