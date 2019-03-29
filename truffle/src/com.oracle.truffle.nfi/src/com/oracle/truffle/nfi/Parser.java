/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi;

import com.oracle.truffle.nfi.spi.types.NativeSimpleType;
import com.oracle.truffle.nfi.spi.types.NativeTypeMirror;
import com.oracle.truffle.nfi.spi.types.NativeSignature;
import com.oracle.truffle.nfi.spi.types.NativeLibraryDescriptor;
import com.oracle.truffle.nfi.Lexer.Token;
import com.oracle.truffle.nfi.spi.types.TypeFactory;
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
final class Parser extends TypeFactory {

    static NativeSignature parseSignature(CharSequence source) {
        Parser parser = new Parser(source);
        NativeSignature ret = parser.parseSignature();
        parser.expect(Token.EOF);
        return ret;
    }

    static NativeSource parseNFISource(CharSequence source) {
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

        NativeLibraryDescriptor descriptor = parseLibraryDescriptor();
        NativeSource ret = new NativeSource(nfiId, descriptor);
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
                    return createDefaultLibrary();
            }
        }
        throw new IllegalArgumentException(String.format("expected 'load' or 'default', but got '%s'", keyword));
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
        return createLibraryDescriptor(filename, flags);
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
                return createFunctionTypeMirror(parseSignature());
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
            return createVarargsSignature(retType, fixedArgCount, args);
        } else {
            return createSignature(retType, args);
        }
    }

    private NativeTypeMirror parseArrayType() {
        expect(Token.OPENBRACKET);
        NativeTypeMirror elementType = parseSimpleType(false);
        expect(Token.CLOSEBRACKET);

        return createArrayTypeMirror(elementType);
    }

    private NativeTypeMirror parseSimpleType(boolean envAllowed) {
        expect(Token.IDENTIFIER);
        String identifier = lexer.currentValue();
        if (envAllowed && "env".equalsIgnoreCase(identifier)) {
            return createEnvTypeMirror();
        } else {
            NativeSimpleType simpleType = NativeSimpleType.valueOf(identifier.toUpperCase());
            return createSimpleTypeMirror(simpleType);
        }
    }
}
