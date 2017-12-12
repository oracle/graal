/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.types;

import com.oracle.truffle.nfi.types.Lexer.Token;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper for implementors of the Truffle NFI.
 *
 * The Truffle NFI can be used to call native libraries from other Truffle languages. To load a
 * native library, the user should evaluate a source of the following syntax:
 *
 * <pre>
 * NativeSource ::= [ BackendSelector ] LibraryDescriptor [ BindBlock ]
 *
 * BackendSelector ::= 'with' ident
 *
 * BindBlock ::= '{' { BindDirective } '}'
 *
 * BindDirective ::= ident Signature ';'
 *
 * LibraryDescriptor ::= DefaultLibrary | LoadLibrary
 *
 * DefaultLibrary ::= 'default'
 *
 * LoadLibrary ::= 'load' [ '(' ident { '|' ident } ')' ] string
 *
 * Signature ::= '(' [ Type { ',' Type } ] [ '...' Type { ',' Type } ] ')' ':' Type
 *
 * Type ::= Signature | SimpleType | ArrayType | EnvType
 *
 * SimpleType ::= ident
 *
 * ArrayType ::= '[' SimpleType ']'
 *
 * EnvType ::= 'env'
 * </pre>
 *
 * The BackendSelector ('with' ident) can be used to explicitly select an alternative backend for
 * the Truffle NFI. If the BackendSelector is missing, the default backend (selector 'native') is
 * used.
 *
 * Implementors of Truffle NFI backends must parse their source string using the
 * {@link #parseLibraryDescriptor(java.lang.CharSequence)} function, and must use
 * {@link #parseSignature(java.lang.CharSequence)} to parse the signature argument string of the
 * {@code bind} method on native symbols.
 */
public final class Parser {

    public static NativeLibraryDescriptor parseLibraryDescriptor(CharSequence source) {
        Parser parser = new Parser(source);
        NativeLibraryDescriptor ret = parser.parseLibraryDescriptor();
        parser.expect(Token.EOF);
        return ret;
    }

    public static NativeSignature parseSignature(CharSequence source) {
        Parser parser = new Parser(source);
        NativeSignature ret = parser.parseSignature();
        parser.expect(Token.EOF);
        return ret;
    }

    public static NativeSource parseNFISource(CharSequence source) {
        Parser parser = new Parser(source);
        NativeSource ret = parser.parseNFISource();
        parser.expect(Token.EOF);
        return ret;
    }

    private final Lexer lexer;

    private Parser(CharSequence source) {
        lexer = new Lexer(source);
    }

    private void expect(Token token) {
        if (lexer.next() != token) {
            throw new IllegalArgumentException(String.format("unexpected token: expected '%s', but got '%s'", token.getName(), lexer.currentValue()));
        }
    }

    private NativeSource parseNFISource() {
        String nfiId = null;
        if (lexer.peek() == Token.IDENTIFIER && lexer.peekValue().equalsIgnoreCase("with")) {
            lexer.next();
            if (lexer.next() != Token.IDENTIFIER) {
                throw new IllegalArgumentException("Expecting NFI impementation identifier");
            }
            nfiId = lexer.currentValue();
        }

        lexer.mark();
        parseLibraryDescriptor();

        NativeSource ret = new NativeSource(nfiId, lexer.markedValue());
        if (lexer.next() == Token.OPENBRACE) {
            for (;;) {
                Token closeOrId = lexer.next();
                if (closeOrId == Token.CLOSEBRACE) {
                    break;
                }
                if (closeOrId != Token.IDENTIFIER) {
                    throw new IllegalArgumentException("Expecting identifier in library body");
                }
                String ident = lexer.currentValue();

                lexer.mark();
                parseSignature();
                ret.register(ident, lexer.markedValue());

                if (lexer.next() != Token.SEMICOLON) {
                    throw new IllegalArgumentException("Expecting semicolon");
                }
            }
        }

        return ret;
    }

    private NativeLibraryDescriptor parseLibraryDescriptor() {
        Token token = lexer.next();
        String keyword = lexer.currentValue();
        if (token == Token.IDENTIFIER) {
            switch (keyword) {
                case "load":
                    return parseLoadLibrary();
                case "default":
                    return parseDefaultLibrary();
            }
        }
        throw new IllegalArgumentException(String.format("expected 'load' or 'default', but got '%s'", keyword));
    }

    private static NativeLibraryDescriptor parseDefaultLibrary() {
        return new NativeLibraryDescriptor(null, null);
    }

    private String parseIdentOrString() {
        if (lexer.peek() == Token.IDENTIFIER) {
            // support strings without quotes if they contain only identifier legal characters
            lexer.next();
        } else {
            expect(Token.STRING);
        }
        return lexer.currentValue();
    }

    private NativeLibraryDescriptor parseLoadLibrary() {
        List<String> flags = null;
        if (lexer.peek() == Token.OPENPAREN) {
            flags = parseFlags();
        }

        String filename = parseIdentOrString();
        return new NativeLibraryDescriptor(filename, flags);
    }

    private List<String> parseFlags() {
        expect(Token.OPENPAREN);

        ArrayList<String> flags = new ArrayList<>();

        Token nextToken;
        do {
            expect(Token.IDENTIFIER);
            flags.add(lexer.currentValue());
            nextToken = lexer.next();
        } while (nextToken == Token.OR);

        if (nextToken != Token.CLOSEPAREN) {
            throw new IllegalArgumentException(String.format("unexpected token: expected '|' or ')', but got '%s'", lexer.currentValue()));
        }

        return flags;
    }

    private NativeTypeMirror parseType() {
        switch (lexer.peek()) {
            case OPENPAREN:
                return new NativeFunctionTypeMirror(parseSignature());
            case OPENBRACKET:
                return parseArrayType();
            case IDENTIFIER:
                return parseSimpleType(true);
            default:
                throw new IllegalArgumentException(String.format("expected type, but got '%s'", lexer.currentValue()));
        }
    }

    private NativeSignature parseSignature() {
        expect(Token.OPENPAREN);

        List<NativeTypeMirror> args;
        int fixedArgCount = -1;
        if (lexer.peek() == Token.CLOSEPAREN) {
            lexer.next();
            args = Collections.emptyList();
        } else {
            args = new ArrayList<>();
            Token nextToken;
            do {
                if (lexer.peek() == Token.ELLIPSIS) {
                    lexer.next();
                    fixedArgCount = args.size();
                }

                args.add(parseType());
                nextToken = lexer.next();
            } while (nextToken == Token.COMMA);

            if (nextToken != Token.CLOSEPAREN) {
                throw new IllegalArgumentException(String.format("unexpected token: expected ',' or ')', but got '%s'", lexer.currentValue()));
            }
        }

        expect(Token.COLON);

        NativeTypeMirror retType = parseType();

        if (fixedArgCount >= 0) {
            return NativeSignature.prepareVarargs(retType, fixedArgCount, args);
        } else {
            return NativeSignature.prepare(retType, args);
        }
    }

    private NativeArrayTypeMirror parseArrayType() {
        expect(Token.OPENBRACKET);
        NativeTypeMirror elementType = parseSimpleType(false);
        expect(Token.CLOSEBRACKET);

        return new NativeArrayTypeMirror(elementType);
    }

    private NativeTypeMirror parseSimpleType(boolean envAllowed) {
        expect(Token.IDENTIFIER);
        String identifier = lexer.currentValue();
        if (envAllowed && "env".equalsIgnoreCase(identifier)) {
            return new NativeEnvTypeMirror();
        } else {
            NativeSimpleType simpleType = NativeSimpleType.valueOf(identifier.toUpperCase());
            return new NativeSimpleTypeMirror(simpleType);
        }
    }
}
