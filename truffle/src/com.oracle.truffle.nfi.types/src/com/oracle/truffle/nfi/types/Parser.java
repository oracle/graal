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
 * Implementors of the Truffle NFI must use {@link #parseLibraryDescriptor(java.lang.CharSequence)}
 * to parse the source string in {@link TruffleLanguage#parse}. The syntax of the source string is:
 *
 * <pre>
 * LibraryDescriptor ::= LibraryDefinition BindBlock?
 * 
 * LibraryDefinition ::= DefaultLibrary | LoadLibrary
 *
 * DefaultLibrary ::= 'default'
 *
 * LoadLibrary ::= 'load' [ '(' ident { '|' ident } ')' ] string
 *
 * BindBlock ::= '{' BindDirective* '}'
 *
 * BindDirective ::= ident Signature ';'
 * </pre>
 *
 * Implementors of the Truffle NFI must use {@link #parseSignature(java.lang.CharSequence)} to parse
 * the signature argument string of the {@code bind} method on native symbols. The syntax of a
 * native signature is:
 *
 * <pre>
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

    private final Lexer lexer;

    private Parser(CharSequence source) {
        lexer = new Lexer(source);
    }

    private void expect(Token token) {
        if (lexer.next() != token) {
            throw new IllegalArgumentException(String.format("unexpected token: expected '%s', but got '%s'", token.getName(), lexer.currentValue()));
        }
    }

    private NativeLibraryDescriptor parseLibraryDescriptor() {
        NativeLibraryDescriptor ret;

        Token token = lexer.next();
        String keyword = lexer.currentValue();
        LIBRARY_DEFINITION: {
            if (token == Token.IDENTIFIER) {
                switch (keyword) {
                    case "load":
                        ret = parseLoadLibrary();
                        break LIBRARY_DEFINITION;
                    case "default":
                        ret = parseDefaultLibrary();
                        break LIBRARY_DEFINITION;
                }
            }
            throw new IllegalArgumentException(String.format("expected 'load' or 'default', but got '%s'", keyword));
        }

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
                NativeSignature sig = parseSignature();
                ret.register(ident, sig);
                if (lexer.next() != Token.SEMICOLON) {
                    throw new IllegalArgumentException("Expecting semicolon");
                }
            }
        }
        return ret;
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
