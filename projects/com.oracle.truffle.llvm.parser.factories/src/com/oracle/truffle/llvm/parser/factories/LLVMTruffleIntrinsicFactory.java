/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMTruffleReadBytesFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleAddressToFunctionFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleBinaryFactory.LLVMTruffleHasSizeFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleBinaryFactory.LLVMTruffleIsBoxedFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleBinaryFactory.LLVMTruffleIsExecutableFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleBinaryFactory.LLVMTruffleIsNullFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleExecuteFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleGetArgFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleGetSizeFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleHandleToManagedFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleImportCachedFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleImportFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleInvokeFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleIsTruffleObjectFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleManagedMallocFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleManagedToHandleFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleReadFactory.LLVMTruffleReadFromIndexFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleReadFactory.LLVMTruffleReadFromNameFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleReadNBytesFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleReadNStringFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleReadStringFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleReleaseHandleFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleUnboxFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleWriteFactory.LLVMTruffleWriteToIndexFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleWriteFactory.LLVMTruffleWriteToNameFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.ToLLVMNode;

final class LLVMTruffleIntrinsicFactory {

    private LLVMTruffleIntrinsicFactory() {
    }

    static LLVMExpressionNode create(String functionName, LLVMExpressionNode[] argNodes) {
        Object[] realArgNodes = new Object[argNodes.length - LLVMCallNode.ARG_START_INDEX];
        System.arraycopy(argNodes, LLVMCallNode.ARG_START_INDEX, realArgNodes, 0, realArgNodes.length);

        switch (functionName) {
            case "@truffle_write":
            case "@truffle_write_i":
            case "@truffle_write_l":
            case "@truffle_write_c":
            case "@truffle_write_f":
            case "@truffle_write_d":
            case "@truffle_write_b":
                return LLVMTruffleWriteToNameFactory.getInstance().createNode(realArgNodes);
            case "@truffle_write_idx":
            case "@truffle_write_idx_i":
            case "@truffle_write_idx_l":
            case "@truffle_write_idx_c":
            case "@truffle_write_idx_f":
            case "@truffle_write_idx_d":
            case "@truffle_write_idx_b":
                return LLVMTruffleWriteToIndexFactory.getInstance().createNode(realArgNodes);
            case "@truffle_read":
                return LLVMTruffleReadFromNameFactory.getInstance().createNode(ToLLVMNode.createNode(TruffleObject.class), realArgNodes[0], realArgNodes[1]);
            case "@truffle_read_i":
                return LLVMTruffleReadFromNameFactory.getInstance().createNode(ToLLVMNode.createNode(int.class), realArgNodes[0], realArgNodes[1]);
            case "@truffle_read_l":
                return LLVMTruffleReadFromNameFactory.getInstance().createNode(ToLLVMNode.createNode(long.class), realArgNodes[0], realArgNodes[1]);
            case "@truffle_read_c":
                return LLVMTruffleReadFromNameFactory.getInstance().createNode(ToLLVMNode.createNode(byte.class), realArgNodes[0], realArgNodes[1]);
            case "@truffle_read_f":
                return LLVMTruffleReadFromNameFactory.getInstance().createNode(ToLLVMNode.createNode(float.class), realArgNodes[0], realArgNodes[1]);
            case "@truffle_read_d":
                return LLVMTruffleReadFromNameFactory.getInstance().createNode(ToLLVMNode.createNode(double.class), realArgNodes[0], realArgNodes[1]);
            case "@truffle_read_b":
                return LLVMTruffleReadFromNameFactory.getInstance().createNode(ToLLVMNode.createNode(boolean.class), realArgNodes[0], realArgNodes[1]);
            case "@truffle_read_idx":
                return LLVMTruffleReadFromIndexFactory.getInstance().createNode(ToLLVMNode.createNode(TruffleObject.class), realArgNodes[0], realArgNodes[1]);
            case "@truffle_read_idx_i":
                return LLVMTruffleReadFromIndexFactory.getInstance().createNode(ToLLVMNode.createNode(int.class), realArgNodes[0], realArgNodes[1]);
            case "@truffle_read_idx_l":
                return LLVMTruffleReadFromIndexFactory.getInstance().createNode(ToLLVMNode.createNode(long.class), realArgNodes[0], realArgNodes[1]);
            case "@truffle_read_idx_c":
                return LLVMTruffleReadFromIndexFactory.getInstance().createNode(ToLLVMNode.createNode(byte.class), realArgNodes[0], realArgNodes[1]);
            case "@truffle_read_idx_f":
                return LLVMTruffleReadFromIndexFactory.getInstance().createNode(ToLLVMNode.createNode(float.class), realArgNodes[0], realArgNodes[1]);
            case "@truffle_read_idx_d":
                return LLVMTruffleReadFromIndexFactory.getInstance().createNode(ToLLVMNode.createNode(double.class), realArgNodes[0], realArgNodes[1]);
            case "@truffle_read_idx_b":
                return LLVMTruffleReadFromIndexFactory.getInstance().createNode(ToLLVMNode.createNode(boolean.class), realArgNodes[0], realArgNodes[1]);
            case "@truffle_unbox_i":
                assert realArgNodes.length == 1;
                return LLVMTruffleUnboxFactory.getInstance().createNode(ToLLVMNode.createNode(int.class), realArgNodes[0]);
            case "@truffle_unbox_l":
                assert realArgNodes.length == 1;
                return LLVMTruffleUnboxFactory.getInstance().createNode(ToLLVMNode.createNode(long.class), realArgNodes[0]);
            case "@truffle_unbox_c":
                assert realArgNodes.length == 1;
                return LLVMTruffleUnboxFactory.getInstance().createNode(ToLLVMNode.createNode(byte.class), realArgNodes[0]);
            case "@truffle_unbox_f":
                assert realArgNodes.length == 1;
                return LLVMTruffleUnboxFactory.getInstance().createNode(ToLLVMNode.createNode(float.class), realArgNodes[0]);
            case "@truffle_unbox_d":
                assert realArgNodes.length == 1;
                return LLVMTruffleUnboxFactory.getInstance().createNode(ToLLVMNode.createNode(double.class), realArgNodes[0]);
            case "@truffle_unbox_b":
                assert realArgNodes.length == 1;
                return LLVMTruffleUnboxFactory.getInstance().createNode(ToLLVMNode.createNode(boolean.class), realArgNodes[0]);

            case "@truffle_invoke":
                return LLVMTruffleInvokeFactory.getInstance().createNode(getInvokeArgs(TruffleObject.class, realArgNodes));
            case "@truffle_invoke_i":
                return LLVMTruffleInvokeFactory.getInstance().createNode(getInvokeArgs(int.class, realArgNodes));
            case "@truffle_invoke_l":
                return LLVMTruffleInvokeFactory.getInstance().createNode(getInvokeArgs(long.class, realArgNodes));
            case "@truffle_invoke_c":
                return LLVMTruffleInvokeFactory.getInstance().createNode(getInvokeArgs(byte.class, realArgNodes));
            case "@truffle_invoke_f":
                return LLVMTruffleInvokeFactory.getInstance().createNode(getInvokeArgs(float.class, realArgNodes));
            case "@truffle_invoke_d":
                return LLVMTruffleInvokeFactory.getInstance().createNode(getInvokeArgs(double.class, realArgNodes));
            case "@truffle_invoke_b":
                return LLVMTruffleInvokeFactory.getInstance().createNode(getInvokeArgs(boolean.class, realArgNodes));
            case "@truffle_execute":
                return LLVMTruffleExecuteFactory.getInstance().createNode(getExecuteArgs(TruffleObject.class, realArgNodes));
            case "@truffle_execute_i":
                return LLVMTruffleExecuteFactory.getInstance().createNode(getExecuteArgs(int.class, realArgNodes));
            case "@truffle_execute_l":
                return LLVMTruffleExecuteFactory.getInstance().createNode(getExecuteArgs(long.class, realArgNodes));
            case "@truffle_execute_c":
                return LLVMTruffleExecuteFactory.getInstance().createNode(getExecuteArgs(byte.class, realArgNodes));
            case "@truffle_execute_f":
                return LLVMTruffleExecuteFactory.getInstance().createNode(getExecuteArgs(float.class, realArgNodes));
            case "@truffle_execute_d":
                return LLVMTruffleExecuteFactory.getInstance().createNode(getExecuteArgs(double.class, realArgNodes));
            case "@truffle_execute_b":
                return LLVMTruffleExecuteFactory.getInstance().createNode(getExecuteArgs(boolean.class, realArgNodes));
            case "@truffle_get_arg":
                return LLVMTruffleGetArgFactory.getInstance().createNode(realArgNodes);
            case "@truffle_import":
                return LLVMTruffleImportFactory.getInstance().createNode(realArgNodes);
            case "@truffle_import_cached":
                return LLVMTruffleImportCachedFactory.getInstance().createNode(realArgNodes);
            case "@truffle_address_to_function":
                return LLVMTruffleAddressToFunctionFactory.getInstance().createNode(realArgNodes);
            case "@truffle_is_executable":
                return LLVMTruffleIsExecutableFactory.getInstance().createNode(realArgNodes);
            case "@truffle_is_null":
                return LLVMTruffleIsNullFactory.getInstance().createNode(realArgNodes);
            case "@truffle_has_size":
                return LLVMTruffleHasSizeFactory.getInstance().createNode(realArgNodes);
            case "@truffle_is_boxed":
                return LLVMTruffleIsBoxedFactory.getInstance().createNode(realArgNodes);
            case "@truffle_get_size":
                return LLVMTruffleGetSizeFactory.getInstance().createNode(realArgNodes);
            case "@truffle_read_string":
                return LLVMTruffleReadStringFactory.getInstance().createNode(realArgNodes);
            case "@truffle_read_n_string":
                return LLVMTruffleReadNStringFactory.getInstance().createNode(realArgNodes);
            case "@truffle_read_bytes":
                return LLVMTruffleReadBytesFactory.getInstance().createNode(realArgNodes);
            case "@truffle_read_n_bytes":
                return LLVMTruffleReadNBytesFactory.getInstance().createNode(realArgNodes);
            case "@truffle_is_truffle_object":
                return LLVMTruffleIsTruffleObjectFactory.getInstance().createNode(realArgNodes);
            case "@truffle_managed_malloc":
                return LLVMTruffleManagedMallocFactory.getInstance().createNode(realArgNodes);
            case "@truffle_handle_for_managed":
                return LLVMTruffleManagedToHandleFactory.getInstance().createNode(realArgNodes);
            case "@truffle_release_handle":
                return LLVMTruffleReleaseHandleFactory.getInstance().createNode(realArgNodes);
            case "@truffle_managed_from_handle":
                return LLVMTruffleHandleToManagedFactory.getInstance().createNode(realArgNodes);
            default:
                return null;

        }
    }

    private static Object[] getInvokeArgs(Class<?> type, Object[] realArgNodes) {
        // ToLLVMNode toLLVM, LLVMExpressionNode[] args, LLVMExpressionNode child0,
        // LLVMExpressionNode child1
        Object[] args = {ToLLVMNode.createNode(type), getArgumentNodes(realArgNodes, 2), realArgNodes[0], realArgNodes[1]};
        return args;
    }

    private static Object[] getExecuteArgs(Class<?> type, Object[] realArgNodes) {
        // ToLLVMNode toLLVM, LLVMExpressionNode[] args, LLVMExpressionNode child0
        Object[] args = {ToLLVMNode.createNode(type), getArgumentNodes(realArgNodes, 1), realArgNodes[0]};
        return args;
    }

    private static LLVMExpressionNode[] getArgumentNodes(Object[] realArgNodes, int offset) {
        LLVMExpressionNode[] args = new LLVMExpressionNode[realArgNodes.length - offset];
        for (int i = 0; i < args.length; i++) {
            args[i] = (LLVMExpressionNode) realArgNodes[i + offset];
        }
        return args;
    }

}
