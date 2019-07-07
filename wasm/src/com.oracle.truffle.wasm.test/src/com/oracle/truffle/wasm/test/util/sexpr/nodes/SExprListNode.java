/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.wasm.test.util.sexpr.nodes;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.wasm.binary.Assert;

public class SExprListNode extends SExprNode {
    private List<SExprNode> nodes;

    public SExprListNode() {
        this.nodes = new ArrayList<>();
    }

    public SExprListNode(List<SExprNode> nodes) {
        this.nodes = nodes;
    }

    public List<SExprNode> nodes() {
        return nodes;
    }

    public SExprNode nodeAt(int index) {
        Assert.assertInRange(index, 0, nodes.size() - 1, "SExprListNode: requested nodeAt out-of-bounds");
        return nodes.get(index);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder("(");
        for (SExprNode node : nodes) {
            s.append(node.toString());
            s.append(" ");
        }
        s.setCharAt(s.length() - 1, ')');
        return s.toString();
    }
}
