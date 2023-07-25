/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.foreign;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.foreign.AbiUtils;

/**
 * Parses a string into a {@link java.lang.foreign.FunctionDescriptor}. The syntax is as follows
 * 
 * <pre>
 * {@code
 *     Descriptor ::= '(' Layout* ')'  (Layout | Void)
 *     Layout ::=  [ Alignment '%' ] SimpleLayout
 *     SimpleLayout ::= ValueLayout | StructLayout | UnionLayout | SequenceLayout
 *     StructLayout ::= '{' Layout* '}' |
 *     UnionLayout ::=  '<' Layout* '>'
 *     SequenceLayout ::= '[' Size ':' Layout ']'
 *     ValueLayout ::= standard C types (e.g. 'bool', 'int', 'long long', ...)
 *     PaddingLayout ::= 'X' Int
 *     Void ::= 'void'
 *     Size ::= Int
 *     Alignment ::= Int
 *     Int ::= a positive (decimal) integer
 * }
 * </pre>
 * 
 * Sequences (denoted by '*') are comma separated.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class FunctionDescriptorParser {
    private FunctionDescriptorParser() {
    }

    private static final class Impl {
        private final String layout;
        private int at;

        private Impl(String input) {
            this.layout = input;
            this.at = 0;
        }

        private char peek() {
            if (this.at == layout.length()) {
                return '\0';
            }
            return layout.charAt(at);
        }

        private char consume() {
            if (this.at == layout.length()) {
                return '\0';
            }
            return layout.charAt(at++);
        }

        private String consumeName() {
            int start = this.at;
            char c = peek();
            while (Character.getType(c) == Character.LOWERCASE_LETTER || c == '_' || c == '*' || c == ' ') {
                consume();
                c = peek();
            }
            return layout.substring(start, this.at).trim().replaceAll("\\h+", " ");
        }

        private String peekName() {
            int start = this.at;
            String word = consumeName();
            this.at = start;
            return word;
        }

        private void discardSpaces() {
            while (Character.isSpaceChar(peek())) {
                consume();
            }
        }

        private void consumeChecked(char expected) {
            char v = consume();
            if (v != expected) {
                handleError("Expected " + expected + " but got " + v + " in " + layout);
            }
        }

        private <T> List<T> parseSequence(char sep, char start, char end, Supplier<T> parser) {
            discardSpaces();
            consumeChecked(start);
            discardSpaces();
            ArrayList<T> elements = new ArrayList<>();
            if (peek() != end) {
                elements.add(parser.get());
                discardSpaces();
            }
            while (peek() != end) {
                consumeChecked(sep);
                discardSpaces();
                elements.add(parser.get());
                discardSpaces();
            }
            consumeChecked(end);
            return elements;
        }

        private FunctionDescriptor parseDescriptor() {
            MemoryLayout[] arguments = parseSequence(',', '(', ')', this::parseLayout).toArray(new MemoryLayout[0]);
            discardSpaces();
            if (peekName().equals("void")) {
                consumeName();
                return FunctionDescriptor.ofVoid(arguments);
            } else {
                return FunctionDescriptor.of(parseLayout(), arguments);
            }
        }

        private long parseInt() {
            int start = at;
            boolean atLeastOneDigit = Character.isDigit(peek());

            if (!atLeastOneDigit) {
                handleError("Expected a number at position " + at + " of layout " + layout);
            }

            while (Character.isDigit(peek())) {
                consume();
            }
            return Long.parseLong(layout.substring(start, at));
        }

        private MemoryLayout parseLayout() {
            long alignment = -1;
            if (Character.isDigit(peek())) {
                alignment = parseInt();
                consumeChecked('%');
            }

            MemoryLayout layout = switch (peek()) {
                case '[' -> parseSequenceLayout();
                case '{' -> parseStructLayout();
                case '<' -> parseUnionLayout();
                case 'X' -> parsePaddingLayout();
                default -> parseValueLayout();
            };

            if (alignment >= 0) {
                layout = layout.withByteAlignment(alignment);
            }

            if (peek() == '(') {
                handleError("Layout parser does not support named layouts: " + layout);
            }

            return layout;
        }

        private MemoryLayout parseUnionLayout() {
            return MemoryLayout.unionLayout(parseSequence(',', '<', '>', this::parseLayout).toArray(new MemoryLayout[0]));
        }

        private MemoryLayout parseStructLayout() {
            return MemoryLayout.structLayout(parseSequence(',', '{', '}', this::parseLayout).toArray(new MemoryLayout[0]));
        }

        private MemoryLayout parsePaddingLayout() {
            consumeChecked('X');
            return MemoryLayout.paddingLayout(parseInt());
        }

        private MemoryLayout parseSequenceLayout() {
            consumeChecked('[');
            long size = parseInt();
            consumeChecked(':');
            MemoryLayout element = parseLayout();
            consumeChecked(']');
            return MemoryLayout.sequenceLayout(size, element);
        }

        private MemoryLayout parseValueLayout() {
            String name = consumeName();
            if (!AbiUtils.singleton().canonicalLayouts().containsKey(name)) {
                handleError("Unknown value layout: " + name + " at " + this.at + " in " + this.layout);
            }
            return AbiUtils.singleton().canonicalLayouts().get(name);
        }

        private void checkDone() {
            if (this.at < layout.length()) {
                handleError("Layout parsing ended (at " + this.at + ") before its end: " + layout);
            }
        }
    }

    public static FunctionDescriptor parse(String input) {
        try {
            Impl parser = new Impl(input);
            FunctionDescriptor res = parser.parseDescriptor();
            parser.checkDone();
            return res;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(input + " could not be parsed as a function descriptor.", e);
        }
    }

    @SuppressWarnings("serial")
    public static final class FunctionDescriptorParserException extends RuntimeException {
        public FunctionDescriptorParserException(final String msg) {
            super(msg);
        }
    }

    private static void handleError(String msg) {
        throw new FunctionDescriptorParserException(msg);
    }
}
