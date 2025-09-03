/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode;

/**
 * Functional interface containing a method to parse one or more nodes using a
 * {@link BytecodeBuilder}.
 * <p>
 * Implementations are commonly written as tree traversals. For example:
 *
 * <pre>
 * BytecodeRootNodes<MyBytecodeRootNode> nodes = MyBytecodeRootNodeGen.create(BytecodeConfig.DEFAULT, b -> {
 *     MyTree myTree = ...; // parse source code to AST
 *     b.beginRoot(...);
 *     myTree.accept(new MyTreeVisitor(b));
 *     b.endRoot();
 * })
 * </pre>
 *
 * In the above example, the visitor can use the builder {@code b} to emit bytecode.
 * <p>
 * The parser should be idempotent (i.e., it can be repeatedly invoked and produces the same
 * result). This is because a parser can be invoked multiple times to <a href=
 * "https://github.com/oracle/graal/blob/master/truffle/docs/bytecode_dsl/UserGuide.md#reparsing-metadata">reparse</a>
 * nodes (e.g., to add source information).
 * <p>
 * Additionally, if serialization is used, the parser should be free of most side effects. The only
 * side effects permitted are field writes on the generated root nodes (since fields are
 * serialized); all other side effects (e.g., non-builder method calls) will not be captured during
 * serialization.
 * <p>
 * Since the parser is kept alive for reparsing, any references it captures will be kept alive in
 * the heap. To reduce memory footprint, it is recommended (where possible) to construct input data
 * (e.g., a parse tree) inside the parser instead of capturing a reference to it.
 *
 * @param <T> the builder class of the bytecode root node
 * @since 24.2
 */
@FunctionalInterface
public interface BytecodeParser<T extends BytecodeBuilder> {
    /**
     * The parse method. Should be idempotent and free of side-effects.
     *
     * @since 24.2
     */
    void parse(T builder);
}
