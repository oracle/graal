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
package uk.ac.man.cs.llvm.ir.model.constants;

import java.util.Arrays;
import uk.ac.man.cs.llvm.ir.types.Type;
import uk.ac.man.cs.llvm.ir.types.VectorType;

public final class VectorConstant extends AbstractConstant {

    private final Constant[] elements;

    public VectorConstant(VectorType type, Constant[] elements) {
        super(type);
        this.elements = elements;
    }

    public VectorConstant(VectorType type, Constant element) {
        super(type);
        elements = new Constant[type.getElementCount()];
        Arrays.fill(elements, element);
    }

    public Constant getElement(int index) {
        return elements[index];
    }

    public Constant[] getElements() {
        return Arrays.copyOf(elements, elements.length);
    }

    public Type getElementType() {
        return ((VectorType) getType()).getElementType();
    }

    public int getLength() {
        return elements.length;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<");
        for (int i = 0; i < getLength(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Constant element = getElement(i);
            sb.append(element.getType()).append(" ").append(element);
        }
        return sb.append(">").toString();
    }
}
