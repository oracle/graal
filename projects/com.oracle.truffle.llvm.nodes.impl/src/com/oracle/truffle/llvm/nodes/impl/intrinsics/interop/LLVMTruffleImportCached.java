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
package com.oracle.truffle.llvm.nodes.impl.intrinsics.interop;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.internal.SuppressFBWarnings;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMLanguage;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.interop.LLVMTruffleImportCachedFactory.ImportCacheNodeGen;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMAddressIntrinsic;
import com.oracle.truffle.llvm.types.LLVMAddress;

@NodeChild(type = LLVMAddressNode.class)
public abstract class LLVMTruffleImportCached extends LLVMAddressIntrinsic {

    @Child private ImportCache cache = ImportCacheNodeGen.create();

    @Specialization
    public Object executeIntrinsic(LLVMAddress value) {
        String id = LLVMTruffleIntrinsicUtil.readString(value);
        return cache.execute(id);
    }

    abstract static class ImportCache extends Node {

        public abstract Object execute(String name);

        @SuppressWarnings("unused")
        @Specialization(limit = "10", guards = {"stringEquals(name, cachedName)"})
        public Object importValue(String name, @Cached("name") String cachedName, @Cached("resolve(cachedName)") Object value) {
            return value;
        }

        protected static Object resolve(String name) {
            return LLVMLanguage.INSTANCE.getEnvironment().importSymbol(name);
        }

        @SuppressFBWarnings("ES_COMPARING_STRINGS_WITH_EQ")
        protected static boolean stringEquals(String s1, String s2) {
            return s1.equals(s2);
        }
    }

}
