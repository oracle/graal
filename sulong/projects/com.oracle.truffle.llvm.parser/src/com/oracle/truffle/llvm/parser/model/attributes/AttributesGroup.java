/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AttributesGroup {

    private final long groupId;
    private final long paramIdx;

    private final ArrayList<Attribute> attributes = new ArrayList<>();

    public static final long RETURN_VALUE_IDX = 0;
    public static final long FUNCTION_ATTRIBUTE_IDX = 0xFFFFFFFFL;

    public AttributesGroup(long groupId, long paramIdx) {
        this.groupId = groupId;
        this.paramIdx = paramIdx;
    }

    public List<Attribute> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }

    public void addAttribute(Attribute attr) {
        attributes.add(attr);
    }

    public long getGroupId() {
        return groupId;
    }

    public long getParamIdx() {
        return paramIdx;
    }

    public boolean isReturnValueAttribute() {
        return paramIdx == RETURN_VALUE_IDX;
    }

    public boolean isFunctionAttribute() {
        return paramIdx == FUNCTION_ATTRIBUTE_IDX;
    }

    public boolean isParameterAttribute() {
        return !isReturnValueAttribute() && !isFunctionAttribute();
    }

    @Override
    public String toString() {
        return "AttributesGroup [groupId=" + groupId + ", paramIdx=" + paramIdx + ", attributes=" + attributes + "]";
    }
}
