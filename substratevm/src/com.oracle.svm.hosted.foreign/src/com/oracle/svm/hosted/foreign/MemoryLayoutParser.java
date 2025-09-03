/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.BasedOnJDKClass;

/**
 * Parses a string into a {@link MemoryLayout}. The syntax is as follows, modeled after
 * {@link MemoryLayout} static methods and how they are combined to construct layouts in Java.
 *
 * <pre>
 * {@code
 *     Layout ::= Alignment | StructLayout | UnionLayout | SequenceLayout | ValueLayout | PaddingLayout
 *     Alignment ::= 'align' '(' Int ',' Layout ')'
 *     StructLayout ::= 'struct' '(' Layout* ')'
 *     UnionLayout ::=  'union' '(' Layout* ')'
 *     SequenceLayout ::= 'sequence' '(' Int ',' Layout ')'
 *     ValueLayout ::=  canonical layout (e.g. 'int', 'long', ...)
 *     PaddingLayout ::= 'padding' '(' Int ')'
 *     Int ::= a positive decimal integer
 * }
 * </pre>
 *
 * Sequences (denoted by '*') are comma separated.
 *
 * Valid canonical layouts are defined by {@link Linker#canonicalLayouts()} (usually delegates to
 * {@link jdk.internal.foreign.abi.SharedUtils#canonicalLayouts}).
 * 
 * This parser is a recursive descent parser. The entry points are {@link #parse(String, Map)} and
 * {@link #parseAllowVoid(String, Map)}). The top parser rule is {@link #parseLayout()}.
 */
@BasedOnJDKClass(MemoryLayout.class)
@Platforms(Platform.HOSTED_ONLY.class)
final class MemoryLayoutParser {

    private static final String ALIGN = "align";
    private static final String STRUCT = "struct";
    private static final String UNION = "union";
    private static final String SEQUENCE = "sequence";
    private static final String PADDING = "padding";

    private final String input;
    private final Map<String, MemoryLayout> canonicalLayouts;

    private int at;

    private MemoryLayoutParser(String input, Map<String, MemoryLayout> canonicalLayouts) {
        this.input = input;
        this.canonicalLayouts = canonicalLayouts;
        this.at = 0;
    }

    private char peek() {
        if (at == input.length()) {
            return '\0';
        }
        return input.charAt(at);
    }

    private char consume() {
        if (at == input.length()) {
            return '\0';
        }
        return input.charAt(at++);
    }

    private String consumeName() {
        int start = at;
        char c = peek();
        while (Character.getType(c) == Character.LOWERCASE_LETTER || c == '_' || c == '*' || c == ' ') {
            consume();
            c = peek();
        }
        return input.substring(start, at).trim().replaceAll("\\h+", " ");
    }

    private String peekName() {
        int start = at;
        String word = consumeName();
        at = start;
        return word;
    }

    private void discardSpaces() {
        while (Character.isSpaceChar(peek())) {
            consume();
        }
    }

    private void consumeChecked(char expected) throws MemoryLayoutParserException {
        char v = consume();
        if (v != expected) {
            throw new MemoryLayoutParserException("Expected " + expected + " but got " + v + " in " + input);
        }
    }

    private long parseLong() throws MemoryLayoutParserException {
        int start = at;
        if (!Character.isDigit(peek())) {
            throw new MemoryLayoutParserException("Expected a number at position " + at + " in \"" + input + "\"");
        }

        for (char c = peek(); Character.isLetterOrDigit(c) || c == '_'; c = peek()) {
            consume();
        }
        String substring = input.substring(start, at);
        try {
            return Long.parseLong(substring);
        } catch (NumberFormatException e) {
            throw new MemoryLayoutParserException("Expected a long at position " + start + " in \"" + input + "\"");
        }
    }

    private void consumeNameChecked(String name) {
        String actualName = consumeName();
        assert name.equals(actualName);
    }

    private void checkDone() throws MemoryLayoutParserException {
        if (at < input.length()) {
            throw new MemoryLayoutParserException("Parsing ended (at " + at + ") before its end: " + input);
        }
    }

    @SuppressWarnings("serial")
    protected static final class MemoryLayoutParserException extends Exception {
        public MemoryLayoutParserException(final String msg) {
            super(msg);
        }
    }

    private MemoryLayout[] parseLayoutList() throws MemoryLayoutParserException {
        discardSpaces();
        consumeChecked('(');
        discardSpaces();
        ArrayList<MemoryLayout> elements = new ArrayList<>();
        if (peek() != ')') {
            elements.add(parseLayout());
            discardSpaces();
        }
        while (peek() != ')') {
            consumeChecked(',');
            discardSpaces();
            elements.add(parseLayout());
            discardSpaces();
        }
        consumeChecked(')');
        return elements.toArray(MemoryLayout[]::new);
    }

    private MemoryLayout parseLayout() throws MemoryLayoutParserException {
        /*
         * "align", "struct", "union", "sequence", "padding", or a value layout
         */
        return switch (peekName()) {
            case ALIGN -> parseAlignment();
            case STRUCT -> parseGroupLayout(STRUCT, MemoryLayout::structLayout);
            case UNION -> parseGroupLayout(UNION, MemoryLayout::unionLayout);
            case SEQUENCE -> parseSequenceLayout();
            case PADDING -> parsePaddingLayout();
            default -> parseValueLayout();
        };
    }

    private MemoryLayout parseGroupLayout(String name, Function<MemoryLayout[], MemoryLayout> factory) throws MemoryLayoutParserException {
        consumeNameChecked(name);
        return factory.apply(parseLayoutList());
    }

    private MemoryLayout parseAlignment() throws MemoryLayoutParserException {
        consumeNameChecked(ALIGN);
        consumeChecked('(');
        long alignment = parseLong();
        consumeChecked(',');
        MemoryLayout element = parseLayout();
        consumeChecked(')');
        return element.withByteAlignment(alignment);
    }

    private MemoryLayout parsePaddingLayout() throws MemoryLayoutParserException {
        consumeNameChecked(PADDING);
        consumeChecked('(');
        long padding = parseLong();
        consumeChecked(')');
        return MemoryLayout.paddingLayout(padding);
    }

    private MemoryLayout parseSequenceLayout() throws MemoryLayoutParserException {
        consumeNameChecked(SEQUENCE);
        consumeChecked('(');
        long size = parseLong();
        consumeChecked(',');
        MemoryLayout element = parseLayout();
        consumeChecked(')');
        return MemoryLayout.sequenceLayout(size, element);
    }

    private MemoryLayout parseValueLayout() throws MemoryLayoutParserException {
        String name = consumeName();
        if (!canonicalLayouts.containsKey(name)) {
            throw new MemoryLayoutParserException("Unknown value layout: " + name + " at " + at + " in " + input);
        }
        return canonicalLayouts.get(name);
    }

    public static MemoryLayout parse(String input, Map<String, MemoryLayout> canonicalLayouts) throws MemoryLayoutParserException {
        MemoryLayoutParser parser = new MemoryLayoutParser(input, canonicalLayouts);
        MemoryLayout res = parser.parseLayout();
        parser.checkDone();
        return res;
    }

    public static Optional<MemoryLayout> parseAllowVoid(String input, Map<String, MemoryLayout> canonicalLayouts) throws MemoryLayoutParserException {
        if ("void".equals(input.trim())) {
            return Optional.empty();
        }
        return Optional.of(parse(input, canonicalLayouts));
    }
}
