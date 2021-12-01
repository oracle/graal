/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.wasm.parser.ir;

import com.oracle.truffle.api.nodes.Node;
import org.graalvm.wasm.WasmCodeEntry;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.nodes.WasmBlockNode;

import java.util.List;

/**
 * Represents information about a wasm block structure.
 */
public class BlockNode implements ParserNode {
    private final byte returnTypeId;

    private final int startOffset;
    private final int initialStackPointer;
    private final int initialIntConstantOffset;
    private final int initialBranchTableOffset;
    private final int initialProfileOffset;

    private final int byteLength;
    private final int intConstantsLength;
    private final int branchTablesLength;
    private final int profileCount;

    private final List<ParserNode> childNodes;

    public BlockNode(byte returnTypeId, int startOffset, int initialStackPointer, int initialIntConstantOffset, int initialBranchTableOffset, int initialProfileOffset,
                    int endOffset, int endIntConstantOffset, int endBranchTableOffset, int endProfileOffset, List<ParserNode> childNodes) {
        this.returnTypeId = returnTypeId;

        this.startOffset = startOffset;
        this.initialStackPointer = initialStackPointer;
        this.initialIntConstantOffset = initialIntConstantOffset;
        this.initialBranchTableOffset = initialBranchTableOffset;
        this.initialProfileOffset = initialProfileOffset;

        this.byteLength = endOffset - startOffset;
        this.intConstantsLength = endIntConstantOffset - initialIntConstantOffset;
        this.branchTablesLength = endBranchTableOffset - initialBranchTableOffset;
        this.profileCount = endProfileOffset - initialProfileOffset;

        this.childNodes = childNodes;
    }

    public WasmBlockNode createWasmBlockNode(WasmInstance instance, WasmCodeEntry codeEntry) {
        return new WasmBlockNode(instance, codeEntry, startOffset, returnTypeId, initialStackPointer, initialIntConstantOffset, initialBranchTableOffset, initialProfileOffset);
    }

    public void initializeWasmBlockNode(WasmBlockNode blockNode, Node[] children) {
        blockNode.initialize(children, byteLength, intConstantsLength, branchTablesLength, profileCount);
    }

    public int getByteLength() {
        return byteLength;
    }

    public byte getReturnTypeId() {
        return returnTypeId;
    }

    public List<ParserNode> getChildNodes() {
        return childNodes;
    }
}
