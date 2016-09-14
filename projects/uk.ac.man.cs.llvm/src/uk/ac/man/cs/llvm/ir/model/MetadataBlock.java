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
package uk.ac.man.cs.llvm.ir.model;

import java.util.ArrayList;
import java.util.List;

import uk.ac.man.cs.llvm.ir.model.metadata.MetadataBaseNode;
import uk.ac.man.cs.llvm.ir.types.IntegerConstantType;
import uk.ac.man.cs.llvm.ir.types.Type;

public class MetadataBlock {

    protected final List<MetadataBaseNode> metadata = new ArrayList<>();

    protected int startIndex = 0;

    public void setStartIndex(int index) {
        startIndex = index;
    }

    public void add(MetadataBaseNode element) {
        metadata.add(element);
    }

    public MetadataBaseNode get(int index) {
        return metadata.get(index - startIndex);
    }

    public MetadataBaseNode getAbsolute(int index) { // TOOD: do index recalculation in getReference
        return metadata.get(index);
    }

    public int size() {
        return metadata.size();
    }

    public MetadataReference getReference(int index) {
        if (index == 0) {
            return voidRef;
        } else {
            return new Reference(index);
        }
    }

    public MetadataReference getReference(long index) {
        return getReference((int) index);
    }

    public MetadataReference getReference(Type t) {
        int index = (int) ((IntegerConstantType) t).getValue(); // TODO
        return getReference(index);
    }

    /**
     * Based on the idea of Optional, but used for automatic forward reference lookup.
     */
    public interface MetadataReference extends MetadataBaseNode {
        boolean isPresent();

        MetadataBaseNode get();

        int getIndex();
    }

    public static final VoidReference voidRef = new VoidReference();

    public static final class VoidReference implements MetadataReference {

        private VoidReference() {
        }

        @Override
        public boolean isPresent() {
            return false;
        }

        @Override
        public MetadataBaseNode get() {
            // TODO: better exception
            throw new IndexOutOfBoundsException("That's an empty reference");
        }

        @Override
        public int getIndex() {
            return -1;
        }

        @Override
        public String toString() {
            return "VoidReference";
        }
    }

    public final class Reference implements MetadataReference {
        public final int index;

        private Reference(int index) {
            this.index = index;
        }

        @Override
        public boolean isPresent() {
            return metadata.size() > index;
        }

        @Override
        public MetadataBaseNode get() {
            return metadata.get(index - startIndex);
        }

        @Override
        public int getIndex() {
            return index;
        }

        @Override
        public String toString() {
            return "!" + index;
        }
    }

}
