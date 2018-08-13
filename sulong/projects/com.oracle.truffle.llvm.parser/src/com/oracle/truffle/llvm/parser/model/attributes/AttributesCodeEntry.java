/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.model.attributes;

import java.util.Collections;
import java.util.List;

public class AttributesCodeEntry {

    public static final AttributesCodeEntry EMPTY = new AttributesCodeEntry(Collections.emptyList());

    private final List<AttributesGroup> codeEntry;

    public AttributesCodeEntry(List<AttributesGroup> codeEntry) {
        this.codeEntry = codeEntry;
    }

    public AttributesGroup getFunctionAttributesGroup() {
        for (AttributesGroup entry : codeEntry) {
            if (entry.isFunctionAttribute()) {
                return entry;
            }
        }
        return null;
    }

    public AttributesGroup getReturnAttributesGroup() {
        for (AttributesGroup entry : codeEntry) {
            if (entry.isReturnValueAttribute()) {
                return entry;
            }
        }
        return null;
    }

    public AttributesGroup getParameterAttributesGroup(int idx) {
        /*
         * parameter index enumeration is starting with 1 in the code entry, which means we need to
         * increment index by one to find the correct attribution.
         */
        for (AttributesGroup entry : codeEntry) {
            if (entry.isParameterAttribute() && entry.getParamIdx() == idx + 1) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "AttributesCodeEntry [codeEntry=" + codeEntry + "]";
    }
}
