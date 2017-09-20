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
package com.oracle.truffle.llvm.runtime.debug.scope;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class LLVMSourceScopeVariables implements TruffleObject {

    public static boolean isInstance(TruffleObject object) {
        return object instanceof LLVMSourceScopeVariables;
    }

    private final Map<Object, LLVMDebugObject> vars;

    LLVMSourceScopeVariables(Map<Object, LLVMDebugObject> vars) {
        this.vars = vars;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return VariablesMessageResolutionForeign.ACCESS;
    }

    @MessageResolution(receiverType = LLVMSourceScopeVariables.class)
    static final class VariablesMessageResolution {

        @Resolve(message = "KEYS")
        abstract static class VariablesKeyNode extends Node {

            @TruffleBoundary
            public Object access(LLVMSourceScopeVariables vars) {
                return new VariableNames(vars.vars.keySet());
            }

        }

        @Resolve(message = "KEY_INFO")
        abstract static class VariablesKeyInfoNode extends Node {

            @TruffleBoundary
            public Object access(LLVMSourceScopeVariables vars, Object key) {
                if (key == null || !vars.vars.containsKey(key)) {
                    return 0;
                } else {
                    return 0b11;
                }
            }

        }

        @Resolve(message = "READ")
        abstract static class VariablesReadNode extends Node {

            @TruffleBoundary
            public Object access(LLVMSourceScopeVariables vars, Object name) {
                if (vars.vars.containsKey(name)) {
                    return vars.vars.get(name);
                } else {
                    throw UnknownIdentifierException.raise(String.valueOf(name));
                }
            }

        }

    }

    static final class VariableNames implements TruffleObject {

        public static boolean isInstance(TruffleObject object) {
            return object instanceof VariableNames;
        }

        private final List<Object> names;

        VariableNames(Set<Object> names) {
            this.names = new ArrayList<>(names);
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return VariableNamesMessageResolutionForeign.ACCESS;
        }

        @MessageResolution(receiverType = VariableNames.class)
        static final class VariableNamesMessageResolution {

            @Resolve(message = "HAS_SIZE")
            abstract static class VariableNamesHasSizeNode extends Node {

                public Object access(@SuppressWarnings("unused") VariableNames varNames) {
                    return true;
                }

            }

            @Resolve(message = "GET_SIZE")
            abstract static class VariableNamesGetSizeNode extends Node {

                @TruffleBoundary
                public Object access(VariableNames varNames) {
                    return varNames.names.size();
                }

            }

            @Resolve(message = "READ")
            abstract static class VariableNamesReadNode extends Node {

                @TruffleBoundary
                public Object access(VariableNames varNames, int index) {
                    if (index >= 0 && index < varNames.names.size()) {
                        return varNames.names.get(index);
                    } else {
                        return "No such index: " + index;
                    }
                }

            }

        }
    }
}
