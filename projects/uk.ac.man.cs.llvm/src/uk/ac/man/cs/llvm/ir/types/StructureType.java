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
package uk.ac.man.cs.llvm.ir.types;

import uk.ac.man.cs.llvm.ir.model.ValueSymbol;

public final class StructureType implements AggregateType, ValueSymbol {

    private String name = ValueSymbol.UNKNOWN;

    private final boolean isPacked;

    private final Type[] types;

    public StructureType(boolean isPacked, Type[] types) {
        this.isPacked = isPacked;
        this.types = types;
    }

    @Override
    public int getAlignment() {
        return types == null || types.length == 0 ? Long.BYTES : types[0].getAlignment();
    }

    @Override
    public Type getElementType(int index) {
        return types[index];
    }

    @Override
    public int getElementCount() {
        return types.length;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Type getType() {
        return this;
    }

    public boolean isPacked() {
        return isPacked;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public int sizeof() {
        int size = 0;
        for (Type type : types) {
            size += type.sizeof() + calculatePadding(type.getAlignment(), size);
        }
        return size;
    }

    @Override
    public int sizeof(int alignment) {
        int size = 0;
        for (Type type : types) {
            size = size + type.sizeof(alignment) + calculatePadding(Math.min(alignment, type.getAlignment()), size);
        }
        return size;
    }

    public String toDeclarationString() {
        StringBuilder str = new StringBuilder();
        if (isPacked) {
            str.append("<{ ");
        } else {
            str.append("{ ");
        }

        for (int i = 0; i < types.length; i++) {
            Type type = types[i];
            if (i > 0) {
                str.append(", ");
            }
            if (type instanceof PointerType && ((PointerType) type).getPointeeType() == this) {
                str.append("%").append(getName()).append("*");
            } else {
                str.append(type);
            }
        }

        if (isPacked) {
            str.append(" }>");
        } else {
            str.append(" }");
        }

        return str.toString();
    }

    @Override
    public String toString() {
        if (name.equals(ValueSymbol.UNKNOWN)) {
            return toDeclarationString();
        } else {
            return "%" + name;
        }
    }

    private int calculatePadding(int alignment, int address) {
        if (isPacked || alignment == 1) {
            return 0;
        }
        int mask = alignment - 1;
        return (alignment - (address & mask)) & mask;
    }
}
