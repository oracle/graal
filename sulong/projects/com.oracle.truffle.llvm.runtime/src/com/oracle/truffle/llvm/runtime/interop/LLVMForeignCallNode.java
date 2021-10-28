/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMGetStackFromThreadNode;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceFunctionType;
import com.oracle.truffle.llvm.runtime.interop.LLVMForeignCallNodeFactory.PackForeignArgumentsNodeGen;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Structured;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMThreadingStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;

/**
 * Used when an LLVM bitcode method is called from another language.
 */
public abstract class LLVMForeignCallNode extends RootNode {

    abstract static class PackForeignArgumentsNode extends LLVMNode {

        abstract Object[] execute(Object[] arguments, LLVMStack stack) throws ArityException;

        @Children final LLVMGetInteropParamNode[] toLLVM;
        final int numberOfSourceArguments;

        /**
         * The purpose is to produce a tree of nodes that will map bitcode parameters to interop
         * parameters. Since there may be a mismatch between those, this node handles the packing by
         * creating the respective ParamNode and delegating the execution to that. Note however that
         * the different cases of mapping (e.g. structs) are handled in this constructor.
         * <p>
         * Example: struct Point { double a; double b; };
         * </p>
         *
         * <pre>
         * JS:     double      object      double      object       double
         *           |        /     \        |        /     \         |
         * LLVM:    int    double  double   int    double  double    int
         *           |        \     /        |        \     /         |
         * C:       int     struct Point    int     struct Point     int
         * </pre>
         *
         * @param bitcodeFunctionType The LLVM/bitcode-level function types.
         * @param interopType The "foreign" function type.
         * @param sourceType The source (e.g. C) level function type.
         */
        public static PackForeignArgumentsNode create(FunctionType bitcodeFunctionType, LLVMInteropType interopType, LLVMSourceFunctionType sourceType) {
            CompilerAsserts.neverPartOfCompilation();
            int numberOfBitcodeParams = bitcodeFunctionType.getNumberOfArguments();
            LLVMGetInteropParamNode[] toLLVM = new LLVMGetInteropParamNode[numberOfBitcodeParams];

            if (interopType instanceof LLVMInteropType.Function) {
                assert sourceType != null : "A function interop type without debug information is not supported";

                LLVMInteropType.Function interopFunctionType = (LLVMInteropType.Function) interopType;

                for (int bitcodeArgIdx = 0, prevIdx = -1; bitcodeArgIdx < numberOfBitcodeParams; bitcodeArgIdx++) {
                    Type bitcodeParameterType = bitcodeFunctionType.getArgumentType(bitcodeArgIdx);
                    LLVMSourceFunctionType.SourceArgumentInformation bitcodeParameterInfo = sourceType.getSourceArgumentInformation(bitcodeArgIdx);

                    assert toLLVM[bitcodeArgIdx] == null;

                    if (bitcodeParameterInfo == null) {
                        int currentIdx = prevIdx + 1;

                        LLVMInteropType interopParameterType = interopFunctionType.getParameter(currentIdx);

                        if (interopParameterType instanceof LLVMInteropType.Value) {
                            toLLVM[bitcodeArgIdx] = LLVMGetInteropPrimitiveParamNode.create(currentIdx, (LLVMInteropType.Value) interopParameterType);
                        } else if (interopParameterType instanceof LLVMInteropType.Structured) {
                            toLLVM[bitcodeArgIdx] = LLVMGetInteropPrimitiveParamNode.create(currentIdx,
                                            LLVMInteropType.Value.pointer((LLVMInteropType.Structured) interopParameterType, LLVMNode.ADDRESS_SIZE_IN_BYTES));
                        } else {
                            // interop only supported for value types
                            toLLVM[bitcodeArgIdx] = LLVMGetInteropPrimitiveParamNode.create(currentIdx, ForeignToLLVM.convert(bitcodeParameterType));
                        }

                        prevIdx = currentIdx;
                    } else {
                        int sourceArgIndex = bitcodeParameterInfo.getSourceArgIndex();

                        LLVMInteropType targetObjectType = interopFunctionType.getParameter(sourceArgIndex);

                        LLVMInteropType.Struct targetObjectStructType = (LLVMInteropType.Struct) targetObjectType;
                        assert bitcodeParameterInfo.getBitcodeArgIndex() == bitcodeArgIdx;

                        Type targetMemberType = bitcodeFunctionType.getArgumentType(bitcodeArgIdx);
                        assert targetMemberType == bitcodeParameterType;

                        if (Long.compareUnsigned(sourceArgIndex, numberOfBitcodeParams) >= 0) {
                            throw new ArrayIndexOutOfBoundsException(String.format("Source argument index (%s) is out of bitcode parameters list bounds (which is of length %s)",
                                            Long.toUnsignedString(sourceArgIndex), Integer.toUnsignedString(numberOfBitcodeParams)));
                        }

                        int offsetInBytes = bitcodeParameterInfo.getOffset() / Byte.SIZE;

                        toLLVM[bitcodeArgIdx] = LLVMGetInteropStructParamNode.create(targetObjectStructType, sourceArgIndex, offsetInBytes,
                                        ForeignToLLVM.convert(targetMemberType));

                        prevIdx = sourceArgIndex;
                    }
                }
            } else {
                assert sourceType == null;
                // Debug info is unavailable, so interop parameter types are also unavailable.
                for (int i = 0; i < numberOfBitcodeParams; i++) {
                    toLLVM[i] = LLVMGetInteropPrimitiveParamNode.create(i, ForeignToLLVM.convert(bitcodeFunctionType.getArgumentType(i)));
                }
            }

            return PackForeignArgumentsNodeGen.create(toLLVM, sourceType);
        }

        PackForeignArgumentsNode(LLVMGetInteropParamNode[] toLLVM, LLVMSourceFunctionType sourceType) {
            this.toLLVM = toLLVM;
            if (sourceType == null) {
                /*
                 * We don't have debug info, so we assume that there is a 1:1 mapping between source
                 * and bitcode arguments. We also assume that the number of passed in arguments is
                 * the same as the number of source arguments.
                 */
                this.numberOfSourceArguments = toLLVM.length;
            } else {
                this.numberOfSourceArguments = sourceType.getNumberOfParameters();
            }
        }

        @Specialization
        @ExplodeLoop
        Object[] packNonVarargs(Object[] arguments, LLVMStack stack, @Cached BranchProfile exceptionProfile) throws ArityException {
            if (arguments.length < numberOfSourceArguments) {
                exceptionProfile.enter();
                throw ArityException.create(numberOfSourceArguments, numberOfSourceArguments, arguments.length);
            }

            final Object[] packedArguments = new Object[1 + toLLVM.length];
            packedArguments[0] = stack;
            for (int i = 0; i < toLLVM.length; i++) {
                packedArguments[i + 1] = toLLVM[i].execute(arguments);
            }
            return packedArguments;
        }
    }

    protected final LLVMInteropType.Structured returnBaseType;

    @Child LLVMGetStackFromThreadNode getStack;
    @Child DirectCallNode callNode;
    @Child LLVMDataEscapeNode prepareValueForEscape;
    @Child PackForeignArgumentsNode packArguments;

    protected LLVMForeignCallNode(LLVMLanguage language, LLVMFunctionCode function, LLVMInteropType interopType, LLVMSourceFunctionType sourceType, Structured returnBaseType, Type escapeType) {
        super(language);
        this.returnBaseType = returnBaseType;
        this.getStack = LLVMGetStackFromThreadNode.create();
        this.callNode = DirectCallNode.create(getCallTarget(function));
        this.prepareValueForEscape = LLVMDataEscapeNode.create(escapeType);
        this.packArguments = PackForeignArgumentsNodeGen.create(function.getLLVMFunction().getType(), interopType, sourceType);
    }

    @Override
    public String toString() {
        return "LLVM:" + callNode.getCallTarget();
    }

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object result;
        LLVMThreadingStack threadingStack = LLVMContext.get(this).getThreadingStack();
        LLVMStack stack = getStack.executeWithTarget(threadingStack, Thread.currentThread());
        try {
            result = doCall(frame, stack);
        } catch (ArityException ex) {
            throw silenceException(RuntimeException.class, ex);
        } catch (TypeOverflowException ex) {
            throw silenceException(RuntimeException.class, ex);
        }
        return prepareValueForEscape.executeWithType(result, returnBaseType);
    }

    protected abstract Object doCall(VirtualFrame frame, LLVMStack stack) throws ArityException, TypeOverflowException;

    static CallTarget getCallTarget(LLVMFunctionCode functionCode) {
        CompilerAsserts.neverPartOfCompilation();
        if (functionCode.isLLVMIRFunction()) {
            return functionCode.getLLVMIRFunctionSlowPath();
        } else if (functionCode.isIntrinsicFunctionSlowPath()) {
            return functionCode.getIntrinsicSlowPath().cachedCallTarget(functionCode.getLLVMFunction().getType());
        } else {
            throw new AssertionError("native function not supported at this point: " + functionCode.getFunction());
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Exception> RuntimeException silenceException(@SuppressWarnings("unused") Class<E> type, Exception ex) throws E {
        throw (E) ex;
    }

    @Override
    public boolean isCloningAllowed() {
        return true;
    }
}
