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
package com.oracle.truffle.llvm.parser.listeners.module;

import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;

public abstract class ModuleVersionHelper {

    public abstract void createFunction(Module m, long[] args);

    public abstract void createGlobalVariable(Module m, long[] args);

    public static final class ModuleV32 extends ModuleVersionHelper {

        @Override
        public void createFunction(Module m, long[] args) {
            FunctionType type = (FunctionType) ((PointerType) m.types.get(args[0])).getPointeeType();
            boolean isPrototype = args[2] != 0;

            m.generator.createFunction(type, isPrototype);
            m.symbols.add(type);
            if (!isPrototype) {
                m.functions.add(type);
            }
        }

        @Override
        public void createGlobalVariable(Module m, long[] args) {
            int i = 0;
            Type type = m.types.get(args[i++]);
            boolean isConstant = (args[i++] & 1) == 1;
            int initialiser = (int) args[i++];
            long linkage = args[i++];
            int align = (int) args[i];

            m.generator.createGlobal(type, isConstant, initialiser, align, linkage);
            m.symbols.add(type);
        }

    }

    public static final class ModuleV38 extends ModuleVersionHelper {

        @Override
        public void createFunction(Module m, long[] args) {
            FunctionType type = (FunctionType) m.types.get(args[0]);
            boolean isPrototype = args[2] != 0;

            m.generator.createFunction(type, isPrototype);
            m.symbols.add(type);
            if (!isPrototype) {
                m.functions.add(type);
            }
        }

        @Override
        public void createGlobalVariable(Module m, long[] args) {
            int i = 0;
            Type type = new PointerType(m.types.get(args[i++]));
            boolean isConstant = (args[i++] & 1) == 1;
            int initialiser = (int) args[i++];
            long linkage = args[i++];
            int align = (int) args[i];

            m.generator.createGlobal(type, isConstant, initialiser, align, linkage);
            m.symbols.add(type);
        }

    }
}
