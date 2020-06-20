/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.ast;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.util.Exceptions;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * An assertion that succeeds when encountered at the beginning or at the end of the string we are
 * searching in.
 * <p>
 * Corresponds to the <strong>^</strong> and <strong>$</strong> right-hand sides of the
 * <em>Assertion</em> goal symbol in the ECMAScript RegExp syntax.
 * <p>
 * {@link PositionAssertion} nodes are also used for state sets of NFA initial states, which is why
 * they can have a next-pointer ({@link #getNext()}), see
 * {@link RegexAST#getNFAAnchoredInitialState(int)}.
 */
public class PositionAssertion extends Term {

    /**
     * The position assertions supported by ECMAScript RegExps.
     */
    public enum Type {
        /**
         * The <strong>^</strong> assertion, which matches at the beginning of the string.
         */
        CARET,
        /**
         * The <strong>$</strong> assertion, which matches at the end of the string.
         */
        DOLLAR
    }

    /**
     * Indicates which position assertion this node represents.
     */
    public final Type type;
    private RegexASTNode next;

    /**
     * Creates a {@link PositionAssertion} node of the given kind.
     *
     * @param type the kind of position assertion to create
     * @see Type
     */
    PositionAssertion(Type type) {
        this.type = type;
    }

    private PositionAssertion(PositionAssertion copy) {
        super(copy);
        type = copy.type;
    }

    @Override
    public PositionAssertion copy(RegexAST ast) {
        return ast.register(new PositionAssertion(this));
    }

    @Override
    public Term copyRecursive(RegexAST ast, CompilationBuffer compilationBuffer) {
        return copy(ast);
    }

    public RegexASTNode getNext() {
        return next;
    }

    public void setNext(RegexASTNode next) {
        this.next = next;
    }

    @Override
    public boolean isCaret() {
        return type == Type.CARET;
    }

    @Override
    public boolean isDollar() {
        return type == Type.DOLLAR;
    }

    @Override
    public boolean equalsSemantic(RegexASTNode obj) {
        return obj instanceof PositionAssertion && ((PositionAssertion) obj).type == type;
    }

    @TruffleBoundary
    @Override
    public String toString() {
        switch (type) {
            case CARET:
                return "^";
            case DOLLAR:
                return "$";
        }
        throw Exceptions.shouldNotReachHere();
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return toJson("PositionAssertion" + type);
    }
}
