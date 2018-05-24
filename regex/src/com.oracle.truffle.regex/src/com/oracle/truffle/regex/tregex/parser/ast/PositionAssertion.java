/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.regex.tregex.parser.ast;

import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

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
    public PositionAssertion copy(RegexAST ast, boolean recursive) {
        return ast.register(new PositionAssertion(this));
    }

    public RegexASTNode getNext() {
        return next;
    }

    public void setNext(RegexASTNode next) {
        this.next = next;
    }

    @Override
    public String toString() {
        switch (type) {
            case CARET:
                return "^";
            case DOLLAR:
                return "$";
        }
        throw new IllegalStateException();
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return toJson("PositionAssertion" + type);
    }
}
