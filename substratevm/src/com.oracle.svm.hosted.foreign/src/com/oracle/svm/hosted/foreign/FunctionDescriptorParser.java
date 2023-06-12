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
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * Parses a string into a {@link java.lang.foreign.FunctionDescriptor}. The syntax is highly
 * inspired from how function descriptors are printed by hotspot, but with some slight changes to
 * make it a bit more readable.
 * 
 * <pre>
 * {@code
 *     Descriptor ::= '(' Layout* ')'  (Layout | Void)
 *     Layout ::=  [ Alignment '%' ] SimpleLayout
 *     SimpleLayout ::= ValueLayout | StructLayout | UnionLayout | SequenceLayout
 *     StructLayout ::= '{' Layout* '}' |
 *     UnionLayout ::=  '<' Layout* '>'
 *     SequenceLayout ::= '[' Size ':' Layout ']'
 *     ValueLayout ::= 'z1' | 'b1' | 's2' | 'i4' | 'j8' | 'f4' | 'd8' | 'a??'
 *                              | 'Z1' | 'B1' | 'S2' | 'I4' | 'J8' | 'F4' | 'D8' | 'A??'
 *     PaddingLayout ::= 'x' Int
 *     Void ::= 'v'
 *     Size ::= Int
 *     Alignment ::= Int
 *     Int ::= a positive (decimal) integer
 * }
 * </pre>
 * 
 * The byte endianess of a value layout is defined the by capitalization of the first and only
 * letter (Capital means big-endian, lower means little-endian). The '??' in 'a??'/'A??' should be
 * replaced by the size (in bytes) of a pointer on the platform under consideration.
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

        private void consumeChecked(char expected) {
            char v = consume();
            if (v != expected) {
                handleError("Expected " + expected + " but got " + v + " in " + layout);
            }
        }

        private <T> List<T> parseSequence(char start, char end, Supplier<T> parser) {
            consumeChecked(start);
            ArrayList<T> elements = new ArrayList<>();
            while (peek() != end) {
                elements.add(parser.get());
            }
            consumeChecked(end);
            return elements;
        }

        private FunctionDescriptor parseDescriptor() {
            MemoryLayout[] arguments = parseSequence('(', ')', this::parseLayout).toArray(new MemoryLayout[0]);
            if (peek() == 'v') {
                consume();
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

            MemoryLayout layout = switch (Character.toUpperCase(peek())) {
                case 'Z' -> parseValueLayout(ValueLayout.JAVA_BOOLEAN);
                case 'B' -> parseValueLayout(ValueLayout.JAVA_BYTE);
                case 'S' -> parseValueLayout(ValueLayout.JAVA_SHORT);
                case 'C' -> parseValueLayout(ValueLayout.JAVA_CHAR);
                case 'I' -> parseValueLayout(ValueLayout.JAVA_INT);
                case 'J' -> parseValueLayout(ValueLayout.JAVA_LONG);
                case 'F' -> parseValueLayout(ValueLayout.JAVA_FLOAT);
                case 'D' -> parseValueLayout(ValueLayout.JAVA_DOUBLE);
                case 'A' -> parseValueLayout(ValueLayout.ADDRESS);
                case '[' -> parseSequenceLayout();
                case '{' -> parseStructLayout();
                case '<' -> parseUnionLayout();
                case 'X' -> parsePaddingLayout();
                default -> handleError("Unknown carrier: " + peek());
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
            return MemoryLayout.unionLayout(parseSequence('<', '>', this::parseLayout).toArray(new MemoryLayout[0]));
        }

        private MemoryLayout parseStructLayout() {
            return MemoryLayout.structLayout(parseSequence('{', '}', this::parseLayout).toArray(new MemoryLayout[0]));
        }

        private MemoryLayout parsePaddingLayout() {
            consumeChecked('x');
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

        private MemoryLayout parseValueLayout(ValueLayout baseLayout) {
            ByteOrder order = ByteOrder.BIG_ENDIAN;
            if (Character.isLowerCase(peek())) {
                order = ByteOrder.LITTLE_ENDIAN;
            }
            consume();
            MemoryLayout layout = baseLayout.withOrder(order);
            long size = parseInt();
            guarantee(layout.byteSize() == size,
                            "Value layout with carrier %s should have a byte size of %d (you specified %d)"
                                            .formatted(baseLayout.carrier(), layout.byteSize(), size));

            return layout;
        }

        private void checkDone() {
            if (this.at < layout.length()) {
                handleError("Layout parsing ended (at " + this.at + ") before its end: " + layout);
            }
        }
    }

    public static FunctionDescriptor parse(String input) {
        Impl parser = new Impl(input);
        FunctionDescriptor res = parser.parseDescriptor();
        parser.checkDone();
        return res;
    }

    @SuppressWarnings("serial")
    public static final class FunctionDescriptorParserException extends RuntimeException {
        public FunctionDescriptorParserException(final String msg) {
            super(msg);
        }
    }

    private static void guarantee(boolean b, String msg) {
        if (!b) {
            handleError(msg);
        }
    }

    private static <T> T handleError(String msg) {
        throw new FunctionDescriptorParserException(msg);
    }
}
