/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.oracle.truffle.nfi.Lexer.Token;
import com.oracle.truffle.nfi.NativeSource.Content;
import com.oracle.truffle.nfi.NativeSource.ParsedLibrary;
import com.oracle.truffle.nfi.NativeSource.ParsedSignature;
import com.oracle.truffle.nfi.SignatureRootNode.ArgumentBuilderNode;
import com.oracle.truffle.nfi.SignatureRootNode.BuildSignatureNode;
import com.oracle.truffle.nfi.SignatureRootNode.GetTypeNode;
import com.oracle.truffle.nfi.SignatureRootNodeFactory.AddArgumentNodeGen;
import com.oracle.truffle.nfi.SignatureRootNodeFactory.BuildSignatureNodeGen;
import com.oracle.truffle.nfi.SignatureRootNodeFactory.GetArrayTypeNodeGen;
import com.oracle.truffle.nfi.SignatureRootNodeFactory.GetEnvTypeNodeGen;
import com.oracle.truffle.nfi.SignatureRootNodeFactory.GetSignatureTypeNodeGen;
import com.oracle.truffle.nfi.SignatureRootNodeFactory.GetSimpleTypeNodeGen;
import com.oracle.truffle.nfi.SignatureRootNodeFactory.MakeVarargsNodeGen;
import com.oracle.truffle.nfi.SignatureRootNodeFactory.SetRetTypeNodeGen;
import com.oracle.truffle.nfi.backend.spi.types.NativeLibraryDescriptor;
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;
import com.oracle.truffle.nfi.backend.spi.types.TypeFactory;

/**
 * Helper for implementors of the Truffle NFI.
 *
 * The Truffle NFI can be used to call native libraries from other Truffle languages. To load a
 * native library, the user should evaluate a source of the following syntax:
 *
 * <pre>
 * NFISource ::= [ BackendSelector ] ( LibraryDescriptor [ BindBlock ] | Signature )
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
            throw lexer.fail("unexpected token: expected '%s', but got '%s'", token.getName(), lexer.currentValue());
        }
    }

    private NativeSource parseNFISource() {
        String nfiId = null;
        if (lexer.peek() == Token.IDENTIFIER && lexer.peekValue().equalsIgnoreCase("with")) {
            lexer.next();
            if (lexer.next() != Token.IDENTIFIER) {
                throw lexer.fail("Expecting NFI backend identifier");
            }
            nfiId = lexer.currentValue();
        }

        Content c = parseNFISourceContent();
        return new NativeSource(nfiId, c);
    }

    private Content parseNFISourceContent() {
        switch (lexer.peek()) {
            case IDENTIFIER: {
                NativeLibraryDescriptor descriptor = parseLibraryDescriptor();
                ParsedLibrary ret = new ParsedLibrary(descriptor);
                if (lexer.next() == Token.OPENBRACE) {
                    for (;;) {
                        Token closeOrId = lexer.next();
                        if (closeOrId == Token.CLOSEBRACE) {
                            break;
                        }
                        if (closeOrId != Token.IDENTIFIER) {
                            throw lexer.fail("Expecting identifier in library body");
                        }
                        String ident = lexer.currentValue();

                        BuildSignatureNode signature = parseSignature();
                        ret.register(ident, signature);

                        if (lexer.next() != Token.SEMICOLON) {
                            throw lexer.fail("Expecting semicolon");
                        }
                    }
                }
                return ret;
            }

            case OPENPAREN: {
                BuildSignatureNode buildSig = parseSignature();
                return new ParsedSignature(buildSig);
            }

            default:
                lexer.next();
                throw lexer.fail("Expecting identifier or '('");
        }
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
        throw lexer.fail("expected 'load' or 'default', but got '%s'", keyword);
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
            throw lexer.fail("unexpected token: expected '|' or ')', but got '%s'", lexer.currentValue());
        }

        return flags;
    }

    private GetTypeNode parseType() {
        switch (lexer.peek()) {
            case OPENPAREN:
                BuildSignatureNode signature = parseSignature();
                return GetSignatureTypeNodeGen.create(signature);
            case OPENBRACKET:
                return parseArrayType();
            case IDENTIFIER:
                return parseSimpleType(true);
            default:
                lexer.next();
                throw lexer.fail("expected type, but got '%s'", lexer.currentValue());
        }
    }

    private BuildSignatureNode parseSignature() {
        expect(Token.OPENPAREN);

        ArrayList<ArgumentBuilderNode> args = new ArrayList<>();

        Token nextToken = lexer.peek();
        if (nextToken == Token.CLOSEPAREN) {
            lexer.next();
        } else {
            do {
                if (lexer.peek() == Token.ELLIPSIS) {
                    lexer.next();
                    args.add(MakeVarargsNodeGen.create());
                }

                GetTypeNode type = parseType();
                args.add(AddArgumentNodeGen.create(type));

                nextToken = lexer.next();
            } while (nextToken == Token.COMMA);
        }

        if (nextToken != Token.CLOSEPAREN) {
            throw lexer.fail("unexpected token: expected ',' or ')', but got '%s'", lexer.currentValue());
        }

        expect(Token.COLON);

        GetTypeNode retType = parseType();
        args.add(SetRetTypeNodeGen.create(retType));

        return BuildSignatureNodeGen.create(args.toArray(ArgumentBuilderNode.EMPTY));
    }

    private GetTypeNode parseArrayType() {
        expect(Token.OPENBRACKET);
        expect(Token.IDENTIFIER);
        NativeSimpleType type = getSimpleType(lexer.currentValue());
        expect(Token.CLOSEBRACKET);
        return GetArrayTypeNodeGen.create(type);
    }

    private GetTypeNode parseSimpleType(boolean envAllowed) {
        expect(Token.IDENTIFIER);
        String identifier = lexer.currentValue();
        if (envAllowed && "env".equalsIgnoreCase(identifier)) {
            return GetEnvTypeNodeGen.create();
        } else {
            return GetSimpleTypeNodeGen.create(getSimpleType(identifier));
        }
    }

    private NativeSimpleType getSimpleType(String identifier) {
        try {
            return NativeSimpleType.valueOf(identifier.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw lexer.fail("unknown simple type '%s'", identifier);
        }
    }
}
