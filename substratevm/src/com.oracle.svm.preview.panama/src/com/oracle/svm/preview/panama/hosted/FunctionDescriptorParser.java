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
package com.oracle.svm.preview.panama.hosted;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

@Platforms(Platform.HOSTED_ONLY.class)
public final class FunctionDescriptorParser {
    private FunctionDescriptorParser() {}

    private static class Impl {
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
                handleError("Expected " + expected + " but got " + v + "in " + layout);
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
                return FunctionDescriptor.ofVoid(arguments); //FunctionDescriptor.ofVoid(arguments);
            } else {
                return FunctionDescriptor.of(parseLayout(), arguments);
            }
        }

        private int parseInt() {
            int start = at;
            boolean atLeastOneDigit = Character.isDigit(peek());

            if (!atLeastOneDigit) {
                handleError("Expected a number at position " + at + " of layout " + layout);
            }

            while (Character.isDigit(peek())) {
                consume();
            }
            return Integer.parseInt(layout.substring(start, at));
        }

        private MemoryLayout parseLayout() {
            return switch (Character.toUpperCase(peek())) {
                case 'Z' -> parseValueLayout(boolean.class);
                case 'B' -> parseValueLayout(byte.class);
                case 'S' -> parseValueLayout(short.class);
                case 'C' -> parseValueLayout(char.class);
                case 'I' -> parseValueLayout(int.class);
                case 'J' -> parseValueLayout(long.class);
                case 'F' -> parseValueLayout(float.class);
                case 'D' -> parseValueLayout(double.class);
                case 'A' -> parseValueLayout(MemoryLayout.class);
                case '[' -> parseSequenceLayout();
                case '{' -> parseStructLayout();
                case '<' -> parseUnionLayout();
                case 'X' -> parsePaddingLayout();
                default -> handleError("Unknown carrier: " + peek());
            };

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
            int size = parseInt();
            consumeChecked(':');
            MemoryLayout element = parseLayout();
            consumeChecked(']');
            return MemoryLayout.sequenceLayout(size, element);
        }

        private MemoryLayout parseValueLayout(Class<?> carrier) {
            ByteOrder order = ByteOrder.BIG_ENDIAN;
            if (Character.isLowerCase(peek())) {
                order = ByteOrder.LITTLE_ENDIAN;
            }
            consume();
            MemoryLayout layout = MemoryLayout.valueLayout(carrier, order);
            int size = parseInt();
            assert layout.bitSize() == size;

            // Modifiers
            switch (peek()) {
                // Name
                case '(' -> handleError("Layout should not carry names: " + layout);
                // Alignment
                case '%' -> {
                    consume();
                    layout = layout.withBitAlignment(parseInt());
                }
            }

            return layout;
        }

        private void checkDone() {
            if (this.at < layout.length()) {
                handleError("Layout parsing ended (at " + this.at + ")before its end: " + layout);
            }
        }
    }

    @SuppressWarnings("serial")
    public final static class FunctionDescriptorParserException extends RuntimeException {
        public FunctionDescriptorParserException(final String msg) {
            super(msg);
        }
    }

    public static FunctionDescriptor parse(String input) {
        Impl parser = new Impl(input);
        FunctionDescriptor res = parser.parseDescriptor();
        parser.checkDone();
        return res;
    }

    private static <T> T handleError(String msg) {
        throw new FunctionDescriptorParserException(msg);
    }
}
