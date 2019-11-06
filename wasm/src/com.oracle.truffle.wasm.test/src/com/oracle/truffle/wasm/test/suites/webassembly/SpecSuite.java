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
package com.oracle.truffle.wasm.test.suites.webassembly;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;

import com.oracle.truffle.wasm.test.util.sexpr.LiteralType;
import com.oracle.truffle.wasm.test.util.sexpr.WasmSExprSuite;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprAtomNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprListNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprLiteralNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprNode;
import com.oracle.truffle.wasm.utils.WasmBinaryTools;

public class SpecSuite extends WasmSExprSuite {

    @Override
    public void interpretSExprNode(SExprNode sExprNode) throws IOException, InterruptedException {
        if (!(sExprNode instanceof SExprListNode)) {
            System.err.println("Skipping: " + sExprNode);
            System.err.println("Invalid S-expression format");
            return;
        }
        SExprListNode listNode = (SExprListNode) sExprNode;
        SExprNode operator = listNode.nodeAt(0);
        switch (operator.toString()) {
            case "register": {
                break;
            }
            case "module": {
                handleModule(listNode);
                break;
            }
            case "assert_return": {
                break;
            }
            case "assert_malformed": {
                // Malformed text format is not applicable for our binary execution implementation,
                // since the text to binary compiler is responsible for detecting malformed modules.
                System.err.println("Skipping: " + sExprNode);
                break;
            }
            default: {
                Assert.fail("Invalid S-expression operator: " + operator);
                break;
            }
        }
    }

    private void handleModule(SExprListNode moduleNode) throws IOException, InterruptedException {
        String moduleName = readModuleName(moduleNode.nodeAt(1));
        String moduleFormat = readModuleFormat(moduleNode.nodeAt(moduleName == null ? 1 : 2));

        if (moduleFormat == null) {
            // Normal module definition (cannot be malformed).
            System.out.println("Compiling WebAssembly: " + moduleNode.toString());
            WasmBinaryTools.compileWat(moduleNode.toString());
        } else {
            // Module in binary or quoted format (may be malformed).
            String moduleContent = readRepeatedStringSequence(moduleNode, moduleName == null ? 2 : 3);
            switch (moduleFormat) {
                case "binary": {
                    // TODO: Convert hex string to byte[].
                    break;
                }
                case "quote": {
                    System.out.println("Compiling WebAssembly string: " + moduleContent);
                    WasmBinaryTools.compileWat(moduleContent);
                    break;
                }
            }
        }
    }

    private static SExprLiteralNode ensureLiteral(SExprNode node) {
        if (!(node instanceof SExprAtomNode)) {
            Assert.fail(String.format("Malformed module definition: expected %s, got %s", SExprAtomNode.class, node.getClass()));
        }
        return ((SExprAtomNode) node).value();
    }

    private String readModuleName(SExprNode node) {
        SExprLiteralNode possiblyLiteral = ensureLiteral(node);
        return possiblyLiteral.value().startsWith("$") ? possiblyLiteral.value() : null;
    }

    private String readModuleFormat(SExprNode node) {
        SExprLiteralNode possiblyLiteral = ensureLiteral(node);
        if (possiblyLiteral.type() != LiteralType.SYMBOL) {
            return null;
        }
        String value = possiblyLiteral.value();
        return Arrays.asList("binary", "quote").contains(value) ? value : null;
    }

    private String readStringLiteral(SExprNode stringPossibly) {
        SExprLiteralNode namePossiblyLiteral = ensureLiteral(stringPossibly);
        if (namePossiblyLiteral.type() != LiteralType.STRING) {
            return null;
        }
        return namePossiblyLiteral.value();
    }

    private String readRepeatedStringSequence(SExprListNode listNode, int startIndex) {
        List<SExprNode> nodes = listNode.nodes();
        List<SExprNode> subList = nodes.subList(startIndex, nodes.size());
        return subList.stream().map(this::readStringLiteral).collect(Collectors.joining(""));
    }
}
