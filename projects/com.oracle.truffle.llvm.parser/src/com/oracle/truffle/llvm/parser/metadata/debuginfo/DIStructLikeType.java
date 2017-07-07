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
package com.oracle.truffle.llvm.parser.metadata.debuginfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class DIStructLikeType extends DIType {

    private final List<DIMemberType> members;

    DIStructLikeType(long size, long align, long offset) {
        super(size, align, offset);
        this.members = new ArrayList<>();
    }

    private DIStructLikeType(Supplier<String> name, long size, long align, long offset, List<DIMemberType> members) {
        super(size, align, offset);
        setName(name);
        this.members = members;
    }

    public void addMember(DIMemberType member) {
        members.add(member);
    }

    public int getMemberCount() {
        return members.size();
    }

    public String getMemberName(int i) {
        if (0 <= i && i < members.size()) {
            return members.get(i).getName();
        } else {
            return null;
        }
    }

    public DIType getMemberType(int i) {
        if (0 <= i && i < members.size()) {
            return members.get(i).getOffsetElementType();
        } else {
            return null;
        }
    }

    @Override
    DIType getOffset(long newOffset) {
        return new DIStructLikeType(this::getName, getSize(), getAlign(), newOffset, members);
    }
}
