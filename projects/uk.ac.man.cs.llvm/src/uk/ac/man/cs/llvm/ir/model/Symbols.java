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
/*
 * Copyright (c) 2016 University of Manchester
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package uk.ac.man.cs.llvm.ir.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import uk.ac.man.cs.llvm.ir.model.constants.Constant;
import uk.ac.man.cs.llvm.ir.types.MetaType;
import uk.ac.man.cs.llvm.ir.types.Type;

public final class Symbols {

    private static final int INITIAL_CAPACITY = 16;

    private Symbol[] symbols;

    private int size;

    public Symbols() {
        symbols = new Symbol[INITIAL_CAPACITY];
    }

    public void addSymbol(Symbol symbol) {
        ensureCapacity(size + 1);

        if (symbols[size] != null) {
            ((ForwardReference) symbols[size]).replace(symbol);
        }
        symbols[size++] = symbol;
    }

    public void addSymbols(Symbols symbols) {
        for (int i = 0; i < symbols.size; i++) {
            addSymbol(symbols.symbols[i]);
        }
    }

    public Constant getConstant(int index) {
        return (Constant) getSymbol(index);
    }

    public Constant[] getConstants(int[] indices) {
        Constant[] consts = new Constant[indices.length];

        for (int i = 0; i < indices.length; i++) {
            consts[i] = getConstant(indices[i]);
        }

        return consts;
    }

    public int getSize() {
        return size;
    }

    public Symbol getSymbol(int index) {
        if (index < size) {
            return symbols[index];
        }

        throw new IllegalStateException("Dependent required for forward references");
    }

    public Symbol getSymbol(int index, Symbol dependent) {
        if (index < size) {
            return symbols[index];
        } else {
            ensureCapacity(index + 1);

            ForwardReference ref = (ForwardReference) symbols[index];
            if (ref == null) {
                symbols[index] = ref = new ForwardReference();
            }
            ref.addDependent(dependent);

            return ref;
        }
    }

    public Symbol[] getSymbols(int[] indices) {
        Symbol[] syms = new Symbol[indices.length];

        for (int i = 0; i < indices.length; i++) {
            syms[i] = getSymbol(indices[i]);
        }

        return syms;
    }

    public void setSymbolName(int index, String name) {
        Symbol symbol = getSymbol(index);
        if (symbol instanceof ValueSymbol) {
            ((ValueSymbol) symbol).setName(name);
        }
    }

    private void ensureCapacity(int capacity) {
        while (symbols.length < capacity) {
            symbols = Arrays.copyOf(symbols, symbols.length << 1);
        }
    }

    private static class ForwardReference implements Symbol {

        private final List<Symbol> dependents = new ArrayList<>();

        ForwardReference() {
        }

        public void addDependent(Symbol dependent) {
            dependents.add(dependent);
        }

        public void replace(Symbol symbol) {
            for (Symbol dependent : dependents) {
                dependent.replace(this, symbol);
            }
        }

        @Override
        public Type getType() {
            return MetaType.UNKNOWN;
        }
    }
}
