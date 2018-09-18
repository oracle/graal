/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.flavors;

import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.chardata.CodePointRange;
import com.oracle.truffle.regex.chardata.CodePointSet;
import com.oracle.truffle.regex.chardata.UnicodeCharacterProperties;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public final class PythonFlavor implements RegexFlavor {

    private enum PythonREMode {
        Str,
        Bytes
    }

    public static final PythonFlavor STR_INSTANCE = new PythonFlavor(PythonREMode.Str);
    public static final PythonFlavor BYTES_INSTANCE = new PythonFlavor(PythonREMode.Bytes);

    private final PythonREMode mode;

    private PythonFlavor(PythonREMode mode) {
        this.mode = mode;
    }

    @Override
    public RegexFlavorProcessor forRegex(RegexSource source) {
        return new PythonFlavorProcessor(source, mode);
    }

    private static final class PythonFlavorProcessor implements RegexFlavorProcessor {

        private enum TermCategory {
            Assertion,
            Atom,
            Quantifier,
            None
        }

        private static final class Lookbehind {
            public final int containedGroups;

            public Lookbehind(int containedGroups) {
                this.containedGroups = containedGroups;
            }
        }

        private static final class Group {
            public final int groupNumber;

            public Group(int groupNumber) {
                this.groupNumber = groupNumber;
            }
        }

        private static final CompilationFinalBitSet WHITESPACE = CompilationFinalBitSet.valueOf(' ', '\t', '\n', '\r', '\u000b', '\f');

        CodePointSet XID_START = UnicodeCharacterProperties.getProperty("XID_Start").copy().addRange(new CodePointRange('_'));
        CodePointSet XID_CONTINUE = UnicodeCharacterProperties.getProperty("XID_Continue");

        private final String inPattern;
        private final String inFlags;
        private final PythonREMode mode;

        private int position;
        private PythonFlags globalFlags;

        private final Deque<PythonFlags> flagsStack;
        private final StringBuilder outPattern;

        private TermCategory lastTerm;
        private Map<String, Integer> namedCaptureGroups;

        private boolean silent;
        private int groups;
        private Deque<Lookbehind> lookbehindStack;
        private Deque<Group> groupStack;

        private PythonFlavorProcessor(RegexSource source, PythonREMode mode) {
            this.inPattern = source.getPattern();
            this.inFlags = source.getGeneralFlags();
            this.mode = mode;
            this.position = 0;
            this.lastTerm = TermCategory.None;
            this.globalFlags = new PythonFlags(inFlags);
            this.flagsStack = new LinkedList<>();
            this.outPattern = new StringBuilder(inPattern.length());
            this.namedCaptureGroups = null;
            this.groups = 0;
            this.lookbehindStack = new ArrayDeque<>();
            this.groupStack = new ArrayDeque<>();
        }

        @Override
        public Map<String, Integer> getNamedCaptureGroups() {
            return namedCaptureGroups;
        }

        @Override
        public boolean isUnicodePattern() {
            return true;
        }

        @Override
        public void validate() throws RegexSyntaxException {
            silent = true;
            parse();
        }

        @Override
        public RegexSource toECMAScriptRegex() throws RegexSyntaxException, UnsupportedRegexException {
            silent = false;
            parse();
            return new RegexSource(outPattern.toString(), "su");
        }

        private void bailOut(String reason) throws UnsupportedRegexException {
            if (!silent) {
                throw new UnsupportedRegexException(reason);
            }
        }

        private void emit(String patternBits) {
            if (!silent) {
                outPattern.append(patternBits);
            }
        }

        private void emit(int codePoint) {
            if (!silent) {
                outPattern.appendCodePoint(codePoint);
            }
        }

        private int curChar() {
            switch (mode) {
                case Str:
                    return inPattern.codePointAt(position);
                case Bytes:
                    return inPattern.charAt(position);
                default:
                    throw new IllegalStateException();
            }
        }

        private int consumeChar() {
            final int c = curChar();
            advance();
            return c;
        }

        private String getMany(Predicate<Integer> pred) {
            StringBuilder out = new StringBuilder();
            while (!atEnd() && pred.test(curChar())) {
                out.appendCodePoint(consumeChar());
            }
            return out.toString();
        }

        private String getUpTo(int count, Predicate<Integer> pred) {
            StringBuilder out = new StringBuilder();
            int found = 0;
            while (found < count && !atEnd() && pred.test(curChar())) {
                out.appendCodePoint(consumeChar());
                found++;
            }
            return out.toString();
        }

        private void advance() {
            advance(1);
        }

        private void retreat() {
            advance(-1);
        }

        private void advance(int len) {
            switch (mode) {
                case Str:
                    position = inPattern.offsetByCodePoints(position, len);
                    break;
                case Bytes:
                    position += len;
                    break;
            }
        }

        private boolean lookahead(String match) {
            if (inPattern.length() - position < match.length()) {
                return false;
            }
            return inPattern.regionMatches(position, match, 0, match.length());
        }

        private boolean consumingLookahead(String match) {
            final boolean matches = lookahead(match);
            if (matches) {
                position += match.length();
            }
            return matches;
        }

        private boolean atEnd() {
            return position >= inPattern.length();
        }

        private PythonFlags getFlags() {
            return flagsStack.isEmpty() ? globalFlags : flagsStack.peek();
        }

        private void parse() {
            PythonFlags startFlags;
            globalFlags = globalFlags.fixFlags(mode);

            do {
                startFlags = globalFlags;

                disjunction();

                globalFlags = globalFlags.fixFlags(mode);
            } while (!globalFlags.equals(startFlags));

            if (!atEnd()) {
                assert curChar() == ')';
                throw syntaxErrorAtRel("unbalanced parenthesis", 0);
            }
        }

        private void disjunction() {
            while (true) {
                alternative();

                if (consumingLookahead("|")) {
                    emit("|");
                } else {
                    break;
                }
            }
        }

        private void comment() {
            while (!atEnd()) {
                int ch = consumeChar();
                if (ch == '\\' && !atEnd()) {
                    advance();
                } else if (ch == '\n') {
                    break;
                }
            }
        }

        private boolean assertionEscape() {
            switch (curChar()) {
                case 'A':
                    advance();
                    emit("^");
                    return true;
                case 'Z':
                    advance();
                    emit("$");
                    return true;
                case 'b':
                    advance();
                    if (getFlags().isUnicode()) {
                        // TODO: handle Python's unicode-aware \b
                    } else {
                        emit("\\b");
                    }
                    return true;
                case 'B':
                    advance();
                    if (getFlags().isUnicode()) {
                        // TODO: handle Python's unicode-aware \B
                    } else {
                        emit("\\B");
                    }
                    return true;
                default:
                    return false;
            }
        }

        private void emitCharSet(CodePointSet charSet) {
            // TODO: Can we drop support for non-unicode patterns?
            for (CodePointRange range : charSet.getRanges()) {
                if (range.isSingle()) {
                    int codePoint = range.lo;
                    if (isUnicodePattern()) {
                        emit("\\u{" + Integer.toHexString(codePoint) + "}");
                    } else {
                        if (codePoint <= 0xFFFF) {
                            emit("\\u" + Integer.toHexString(codePoint));
                        } // else: character not matchable in non-unicode regular expressions
                    }
                } else {
                    if (isUnicodePattern()) {
                        emit("\\u{" + Integer.toHexString(range.lo) + "}-\\u{" + Integer.toHexString(range.hi) + "}");
                    } else {
                        if (range.lo <= 0xFFFF) {
                            int hi = Math.min(range.hi, 0xFFFF);
                            emit("\\u" + Integer.toHexString(range.lo) + "-\\u" + Integer.toHexString(hi));
                        } // else: characters not matchable in non-unicode regular expressions
                    }
                }
            }
        }

        private boolean categoryEscape(boolean inCharClass) {
            switch (curChar()) {
                case 'd':
                    advance();
                    if (getFlags().isUnicode()) {
                        // The 'u' flag in Python is only permitted in 'str' patterns, which we translate
                        // into Unicode ECMAScript patterns.
                        assert isUnicodePattern();
                        // Python accepts characters with the Numeric_Type=Decimal property.
                        // As of Unicode 11.0.0, these happen to be exactly the characters
                        // in the Decimal_Number General Category.
                        String charSet = "\\p{General_Category=Decimal_Number}";
                        emit(inCharClass ? charSet : "[" + charSet + "]");
                    } else {
                        emit("\\d");
                    }
                    return true;
                case 'D':
                    advance();
                    if (getFlags().isUnicode()) {
                        assert isUnicodePattern();
                        String charSet = "\\P{General_Category=Decimal_Number}";
                        emit(inCharClass ? charSet : "[" + charSet + "]");
                    } else {
                        emit("\\D");
                    }
                    return true;
                case 's':
                    advance();
                    if (getFlags().isUnicode()) {
                        assert isUnicodePattern();
                        // Python accepts characters with either the Space_Separator General Category
                        // or one of the WS, B or S Bidi_Classes. A close analogue available in
                        // ECMAScript regular expressions is the White_Space Unicode property,
                        // which is only missing the characters \x1c-\x1f (as of Unicode 11.0.0).
                        String charSet = "\\p{White_Space}\\x1c-\\x1f";
                        emit(inCharClass ? charSet : "[" + charSet + "]");
                    } else {
                        emit("\\s");
                    }
                    return true;
                case 'S':
                    advance();
                    if (getFlags().isUnicode()) {
                        assert isUnicodePattern();
                        if (inCharClass) {
                            // We are inside a character class and so we cannot add all the characters
                            // in \P{White_Space} and then subtract \x1c-\x1f. Therefore, we will
                            // need to write out the definition of \P{White_Space} explicitly.
                            CodePointSet unicodeSpaces = UnicodeCharacterProperties.getProperty("White_Space");
                            CodePointSet pythonSpaces = unicodeSpaces.addRange(new CodePointRange('\u001c', '\u001f'));
                            CodePointSet complement = pythonSpaces.createInverse();
                            emitCharSet(complement);
                        } else {
                            String charSet = "\\p{White_Space}\\x1c-\\x1f";
                            emit("[^" + charSet + "]");
                        }
                    } else {
                        emit("\\S");
                    }
                    return true;
                case 'w':
                    advance();
                    if (getFlags().isUnicode()) {
                        assert isUnicodePattern();
                        // As alphabetic characters, Python accepts those in the general category L.
                        // As numeric, it takes any character with either Numeric_Type=Decimal,
                        // Numeric_Type=Digit or Numeric_Type=Numeric. As of Unicode 11.0.0, this
                        // corresponds to the general category Number, along with the following
                        // code points:
                        // F96B;CJK COMPATIBILITY IDEOGRAPH-F96B;Lo;0;L;53C3;;;3;N;;;;;
                        // F973;CJK COMPATIBILITY IDEOGRAPH-F973;Lo;0;L;62FE;;;10;N;;;;;
                        // F978;CJK COMPATIBILITY IDEOGRAPH-F978;Lo;0;L;5169;;;2;N;;;;;
                        // F9B2;CJK COMPATIBILITY IDEOGRAPH-F9B2;Lo;0;L;96F6;;;0;N;;;;;
                        // F9D1;CJK COMPATIBILITY IDEOGRAPH-F9D1;Lo;0;L;516D;;;6;N;;;;;
                        // F9D3;CJK COMPATIBILITY IDEOGRAPH-F9D3;Lo;0;L;9678;;;6;N;;;;;
                        // F9FD;CJK COMPATIBILITY IDEOGRAPH-F9FD;Lo;0;L;4EC0;;;10;N;;;;;
                        // 2F890;CJK COMPATIBILITY IDEOGRAPH-2F890;Lo;0;L;5EFE;;;9;N;;;;;
                        String alpha = "\\p{General_Category=Letter}";
                        String numeric = "\\p{General_Category=Number}\\uf96b\\uf973\\uf978\\uf9b2\\uf9d1\\uf9d3\\uf9fd\\u{2f890}";
                        String charSet = alpha + numeric + "_";
                        emit(inCharClass ? charSet : "[" + charSet + "]");
                    } else {
                        emit("\\w");
                    }
                    return true;
                case 'W':
                    advance();
                    if (getFlags().isUnicode()) {
                        assert isUnicodePattern();
                        if (inCharClass) {
                            CodePointSet alpha = UnicodeCharacterProperties.getProperty("General_Category=Letter");
                            CodePointSet numericExtras = CodePointSet.create(0xf96b, 0xf973, 0xf978, 0xf9b2, 0xf9d1, 0xf9d3, 0xf9fd, 0x2f890);
                            CodePointSet numeric = UnicodeCharacterProperties.getProperty("General_Category=Number").addSet(numericExtras);
                            CodePointSet pythonWordChars = alpha.addSet(numeric).addRange(new CodePointRange('_'));
                            CodePointSet complement = pythonWordChars.createInverse();
                            emitCharSet(complement);
                        } else {
                            String alpha = "\\p{General_Category=Letter}";
                            String numeric = "\\p{General_Category=Number}\\uf96b\\uf973\\uf978\\uf9b2\\uf9d1\\uf9d3\\uf9fd\\u{2f890}";
                            String charSet = alpha + numeric + "_";
                            emit("[^" + charSet + "]");
                        }
                    } else {
                        emit("\\W");
                    }
                    return true;
                default:
                    return false;
            }
        }

        private int characterEscape() {
            switch (curChar()) {
                case 'a':
                    advance();
                    emit("\\x07");
                    return '\u0007';
                case 'f':
                    advance();
                    emit("\\f");
                    return '\f';
                case 'n':
                    advance();
                    emit("\\n");
                    return '\n';
                case 'r':
                    advance();
                    emit("\\r");
                    return '\r';
                case 't':
                    advance();
                    emit("\\t");
                    return '\t';
                case 'v':
                    advance();
                    emit("\\x0b");
                    return '\u000b';
                case '\\':
                    advance();
                    emit("\\\\");
                    return '\\';
                case 'x': {
                    String code = getUpTo(2, PythonFlavorProcessor::isHexDigit);
                    if (code.length() < 2) {
                        throw syntaxErrorAtRel("incomplete escape \\x" + code, 2 + code.length());
                    }
                    emit("\\x" + code);
                    return Integer.parseInt(code, 16);
                }
                case 'u':
                case 'U':
                     // 'u' and 'U' escapes are supported only in 'str' patterns
                    if (mode == PythonREMode.Str) {
                        char escapeLead = (char)consumeChar();
                        int escapeLength;
                        switch (escapeLead) {
                            case 'u':
                                escapeLength = 4;
                                break;
                            case 'U':
                                escapeLength = 8;
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                        String code = getUpTo(escapeLength, PythonFlavorProcessor::isHexDigit);
                        if (code.length() < escapeLength) {
                            throw syntaxErrorAtRel("incomplete escape \\" + escapeLead + code, 2 + code.length());
                        }
                        try {
                            int codePoint = Integer.parseInt(code, 16);
                            if (codePoint > 0x10FFFF) {
                                throw syntaxErrorAtRel("unicode escape value \\" + escapeLead + code + " outside of range 0-0x10FFFF", 2 + code.length());
                            }
                            // We are working with a 'str' pattern, therefore the resulting ECMAScript
                            // pattern will be have the unicode flag and we can use the \\u{xxxxx}
                            // escape syntax. This ensures we get the correct semantics w.r.t.
                            // surrogates.
                            emit("\\u{" + Integer.toHexString(codePoint) + "}");
                            return codePoint;
                        } catch (NumberFormatException e) {
                            throw syntaxErrorAtRel("bad escape \\" + escapeLead + code, 2 + code.length());
                        }
                    } else {
                        // \\u or \\U in 'bytes' patterns
                        throw syntaxErrorAtRel("bad escape \\" + curChar(), 1);
                    }
                default:
                    if (isOctDigit(curChar())) {
                        String code = getUpTo(3, PythonFlavorProcessor::isOctDigit);
                        int codePoint = Integer.parseInt(code, 8);
                        if (codePoint > 0377) {
                            throw syntaxErrorAtRel("octal escape value \\" + code + " outside of range 0-o377", 1 + code.length());
                        }
                        // Octal escapes are easily mistaken for backreferences in ECMAScript regular
                        // expressions. To avoid confusion, we generate a non-ambiguous hex escape
                        // that works correctly both in unicode (str) and non-unicode (bytes)
                        // regular expressions.
                        emit("\\x" + Integer.toHexString(codePoint));
                        return codePoint;
                    } else if (isAsciiLetter(curChar())) {
                        throw syntaxErrorAtRel("bad escape \\" + curChar(), 1);
                    } else {
                        return consumeChar();
                    }
            }
        }

        private boolean backreference() {
            if (curChar() >= '1' && curChar() <= '9') {
                String number = getUpTo(2, PythonFlavorProcessor::isDecDigit);
                int groupNumber = Integer.parseInt(number);
                if (groupNumber > groups) {
                    throw syntaxErrorAtRel("invalid group reference " + number, number.length());
                }
                for (Group openGroup : groupStack) {
                    if (groupNumber == openGroup.groupNumber) {
                        throw syntaxErrorAtRel("cannot refer to an open group", number.length() + 1);
                    }
                }
                for (Lookbehind openLookbehind : lookbehindStack) {
                    if (groupNumber >= openLookbehind.containedGroups) {
                        throw syntaxErrorHere("cannot refer to group defined in the same lookbehind subpattern");
                    }
                }
                if (getFlags().isIgnoreCase()) {
                    bailOut("case insensitive backreferences not supported");
                } else {
                    emit("\\" + number);
                }
                return true;
            } else {
                return false;
            }
        }

        private static boolean isHexDigit(int c) {
            return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
        }

        private static boolean isOctDigit(int c) {
            return c >= '0' && c <= '7';
        }

        private static boolean isDecDigit(int c) {
            return c >= '0' && c <= '9';
        }

        private static boolean isAsciiLetter(int c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        }

        private RegexSyntaxException syntaxErrorAtRel(String message, int offset) {
            int intendedPosition = mode == PythonREMode.Str ? inPattern.offsetByCodePoints(position, -offset) : position - offset;
            return syntaxErrorAtAbs(message, intendedPosition);
        }

        private RegexSyntaxException syntaxErrorAtAbs(String message, int position) {
            int reportedPosition = mode == PythonREMode.Str ? inPattern.codePointCount(0, position) : position;
            return syntaxError(message, reportedPosition);
        }

        private RegexSyntaxException syntaxErrorHere(String message) {
            return syntaxErrorAtAbs(message, position);
        }

        private RegexSyntaxException syntaxError(String message, int position) {
            return new RegexSyntaxException(inPattern, inFlags, message, position);
        }

        private void escape() {
            if (assertionEscape()) {
                lastTerm = TermCategory.Assertion;
                return;
            }
            if (categoryEscape(false)) {
                lastTerm = TermCategory.Atom;
                return;
            }
            if (backreference()) {
                lastTerm = TermCategory.Atom;
                return;
            }
            characterEscape();
            lastTerm = TermCategory.Atom;
        }

        private Optional<Integer> classEscape() {
            if (categoryEscape(true)) {
                return Optional.empty();
            }
            return Optional.of(characterEscape());
        }

        private void alternative() {
            while (!atEnd() && curChar() != '|' && curChar() != ')') {
                int ch = consumeChar();

                if (getFlags().isVerbose()) {
                    if (WHITESPACE.get(ch)) {
                        continue;
                    }
                    if (ch == '#') {
                        comment();
                        continue;
                    }
                }

                switch (ch) {
                    case '\\':
                        escape();
                        break;
                    case '[':
                        charClass();
                        lastTerm = TermCategory.Atom;
                        break;
                    case '*':
                    case '+':
                    case '?':
                    case '{':
                        quantifier(ch);
                        lastTerm = TermCategory.Quantifier;
                        break;
                    case '.':
                        if (getFlags().isDotAll()) {
                            emit(".");
                        } else {
                            emit("[^\\n]");
                        }
                        lastTerm = TermCategory.Atom;
                        break;
                    case '(':
                        parens();
                        break;
                    case '^':
                        if (getFlags().isMultiline()) {
                            emit("(?:^|(?<=\\n))");
                        } else {
                            emit("^");
                        }
                        lastTerm = TermCategory.Assertion;
                        break;
                    case '$':
                        if (getFlags().isMultiline()) {
                            emit("(?:$|(?=\\n))");
                        } else {
                            emit("(?:$|(?=\\n$))");
                        }
                        lastTerm = TermCategory.Assertion;
                        break;
                    case ']':
                    case '}':
                        emit('\\');
                        emit(ch);
                        lastTerm = TermCategory.Atom;
                        break;
                    default:
                        emit(ch);
                        lastTerm = TermCategory.Atom;
                }
            }
        }

        private void parens() {
            int start = position - 1;

            if (!atEnd()) {
                final int ch0 = consumeChar();
                switch (ch0) {
                    case '?':
                        mustHaveMore();
                        final int ch1 = consumeChar();
                        switch (ch1) {
                            case 'P': {
                                mustHaveMore();
                                final int ch2 = consumeChar();
                                switch (ch2) {
                                    case '<': {
                                        String groupName = parseGroupName('>');
                                        group(true, Optional.of(groupName), start);
                                        break;
                                    }
                                    case '=': {
                                        String groupName = parseGroupName(')');
                                        if (namedCaptureGroups != null && namedCaptureGroups.containsKey(groupName)) {
                                            int groupNumber = namedCaptureGroups.get(groupName);
                                            for (Group openGroup : groupStack) {
                                                if (groupNumber == openGroup.groupNumber) {
                                                    throw syntaxErrorAtRel("cannot refer to an open group", groupName.length() + 1);
                                                }
                                            }
                                            for (Lookbehind openLookbehind : lookbehindStack) {
                                                if (groupNumber >= openLookbehind.containedGroups) {
                                                    throw syntaxErrorHere("cannot refer to group defined in the same lookbehind subpattern");
                                                }
                                            }
                                            emit("\\" + groupNumber);
                                            lastTerm = TermCategory.Atom;
                                        } else {
                                            throw syntaxErrorAtRel("unknown group name " + groupName, groupName.length() + 1);
                                        }
                                        break;
                                    }
                                    default:
                                        throw syntaxErrorAtRel("unknown extension ?P" + new String(Character.toChars(ch2)), 3);
                                }
                                break;
                            }

                            case ':':
                                group(false, Optional.empty(), start);
                                break;

                            case '#':
                                String comment = getMany(c ->  c != ')');
                                if (!consumingLookahead(")")) {
                                    throw syntaxErrorAtAbs("missing ), unterminated comment", start);
                                }
                                break;

                            case '<': {
                                mustHaveMore();
                                final int ch2 = consumeChar();
                                switch (ch2) {
                                    case '=':
                                        lookbehind(true);
                                        break;
                                    case '!':
                                        lookbehind(false);
                                        break;
                                    default:
                                        throw syntaxErrorAtRel("unknown extension ?<" + new String(Character.toChars(ch2)), 3);
                                }
                            }

                            case '=':
                                lookahead(true, start);
                                break;

                            case '!':
                                lookahead(false, start);
                                break;

                            case '(':
                                bailOut("conditional backreference groups not supported");
                                String groupId = getMany(c -> c != ')');
                                if (groupId.isEmpty()) {
                                    throw syntaxErrorHere("missing group name");
                                }
                                if (!consumingLookahead(Character.toString(')'))) {
                                    throw syntaxErrorAtRel("missing ), unterminated name", groupId.length());
                                }
                                int groupNumber;
                                if (checkGroupName(groupId)) {
                                    // group referenced by name
                                    if (namedCaptureGroups != null && namedCaptureGroups.containsKey(groupId)) {
                                        groupNumber = namedCaptureGroups.get(groupId);
                                    } else {
                                        throw syntaxErrorAtRel("unknown group name " + groupId, groupId.length() + 1);
                                    }
                                } else {
                                    try {
                                        groupNumber = Integer.parseInt(groupId);
                                    } catch (NumberFormatException e) {
                                        throw syntaxErrorAtRel("bad character in group name " + groupId, groupId.length() + 1);
                                    }
                                }
                                if (!lookbehindStack.isEmpty()) {
                                    for (Group openGroup : groupStack) {
                                        if (groupNumber == openGroup.groupNumber) {
                                            throw syntaxErrorHere("cannot refer to an open group");
                                        }
                                    }
                                }
                                for (Lookbehind openLookbehind : lookbehindStack) {
                                    if (groupNumber >= openLookbehind.containedGroups) {
                                        throw syntaxErrorHere("cannot refer to group defined in the same lookbehind subpattern");
                                    }
                                }
                                disjunction();
                                if (consumingLookahead("|")) {
                                    disjunction();
                                    if (lookahead("|")) {
                                        throw syntaxErrorHere("conditional backref with more than two branches");
                                    }
                                }
                                if (!consumingLookahead(")")) {
                                    throw syntaxErrorAtAbs("missing ), unterminated subpattern", start);
                                }
                                lastTerm = TermCategory.Atom;
                                break;

                            case '-':
                            case 'i':
                            case 'L':
                            case 'm':
                            case 's':
                            case 'x':
                            case 'a':
                            case 't':
                            case 'u':
                                flags(ch1, start);
                                break;

                            default:
                                throw syntaxErrorAtRel("unknown extension ?" + new String(Character.toChars(ch1)), 2);
                        }
                        break;

                    default:
                        retreat();
                        group(true, Optional.empty(), start);
                }
            } else {
                throw syntaxErrorAtAbs("missing ), unterminated subpattern", start);
            }
        }

        private void flags(int ch0, int start) {
            int ch = ch0;
            PythonFlags positiveFlags = PythonFlags.EMPTY_INSTANCE;
            while (PythonFlags.FLAGS.indexOf(ch) >= 0) {
                positiveFlags = positiveFlags.addFlag(ch);
                if (mode == PythonREMode.Str && ch == 'L') {
                    throw syntaxErrorHere("bad inline flags: cannot use 'L' flag with a str pattern");
                }
                if (mode == PythonREMode.Bytes && ch == 'u') {
                    throw syntaxErrorHere("bad inline flags: cannot use 'u' flag with a bytes pattern");
                }
                if (!positiveFlags.atMostOneType()) {
                    throw syntaxErrorHere("bad inline flags: flags 'a', 'u' and 'L' are incompatible");
                }
                if (atEnd()) {
                    throw syntaxErrorHere("missing -, : or )");
                }
                ch = consumeChar();
            }
            switch (ch) {
                case ')':
                    globalFlags = globalFlags.addFlags(positiveFlags);
                    lastTerm = TermCategory.Atom;
                    break;
                case ':':
                    if (positiveFlags.includesGlobalFlags()) {
                        throw syntaxErrorAtRel("bad inline flags: cannot turn on global flag", 1);
                    }
                    localFlags(positiveFlags, PythonFlags.EMPTY_INSTANCE, start);
                    break;
                case '-':
                    if (positiveFlags.includesGlobalFlags()) {
                        throw syntaxErrorAtRel("bad inline flags: cannot turn on global flag", 1);
                    }
                    if (atEnd()) {
                        throw syntaxErrorHere("missing flag");
                    }
                    ch = consumeChar();
                    PythonFlags negativeFlags = PythonFlags.EMPTY_INSTANCE;
                    while (PythonFlags.FLAGS.indexOf(ch) >= 0) {
                        negativeFlags = negativeFlags.addFlag(ch);
                        if (PythonFlags.TYPE_FLAGS.indexOf(ch) >= 0) {
                            throw syntaxErrorHere("bad inline flags: cannot turn off flags 'a', 'u' and 'L'");
                        }
                        if (atEnd()) {
                            throw syntaxErrorHere("missing :");
                        }
                        ch = consumeChar();
                    }
                    if (ch != ':') {
                        if (Character.isAlphabetic(ch)) {
                            throw syntaxErrorAtRel("uknown flag", 1);
                        } else {
                            throw syntaxErrorAtRel("missing :", 1);
                        }
                    }
                    if (negativeFlags.includesGlobalFlags()) {
                        throw syntaxErrorAtRel("bad inline flags: cannot turn off global flag", 1);
                    }
                    localFlags(positiveFlags, negativeFlags, start);
                    break;
                default:
                    if (Character.isAlphabetic(ch)) {
                        throw syntaxErrorAtRel("unknown flag", 1);
                    } else {
                        throw syntaxErrorAtRel("missing -, : or )", 1);
                    }
            }
        }

        private void localFlags(PythonFlags positiveFlags, PythonFlags negativeFlags, int start) {
            if (positiveFlags.overlaps(negativeFlags)) {
                throw syntaxErrorHere("bad inline flags: flag turned on and off");
            }
            PythonFlags typeFlags = new PythonFlags(PythonFlags.TYPE_FLAGS);
            PythonFlags otherTypes = typeFlags.delFlags(positiveFlags); // TODO: Handle the case when no type flag was set.
            flagsStack.push(getFlags().addFlags(positiveFlags).delFlags(negativeFlags).delFlags(otherTypes));
            group(false, Optional.empty(), start);
            flagsStack.pop();
        }

        private void lookahead(boolean positive, int position) {
            if (positive) {
                emit("(?=");
            } else {
                emit("(?!");
            }
            disjunction();
            if (consumingLookahead(")")) {
                emit(")");
            } else {
                throw syntaxErrorAtAbs("missing ), unterminated subpattern", position);
            }
            lastTerm = TermCategory.Assertion;
        }

        private void lookbehind(boolean positive) {
            if (positive) {
                emit("(?<=");
            } else {
                emit("(?<!");
            }
            lookbehindStack.push(new Lookbehind(groups + 1));
            disjunction();
            lookbehindStack.pop();
            if (consumingLookahead(")")) {
                emit(")");
            } else {
                throw syntaxErrorAtAbs("missing ), unterminated subpattern", position);
            }
            lastTerm = TermCategory.Assertion;
        }

        private String parseGroupName(char terminator) {
            String groupName = getMany(c -> c != terminator);
            if (groupName.isEmpty()) {
                throw syntaxErrorHere("missing group name");
            }
            if (!consumingLookahead(Character.toString(terminator))) {
                throw syntaxErrorAtRel("missing " + terminator + ", unterminated name", groupName.length());
            }
            if (!checkGroupName(groupName)) {
                throw syntaxErrorAtRel("bad character in group name " + groupName, groupName.length() + 1);
            }
            return groupName;
        }

        private void group(boolean capturing, Optional<String> optName, int start) {
            if (capturing) {
                groups++;
                groupStack.push(new Group(groups));
                emit("(");
            } else {
                emit("(?:");
            }
            optName.ifPresent(name -> {
                if (namedCaptureGroups == null) {
                    namedCaptureGroups = new HashMap<>();
                }
                if (namedCaptureGroups.containsKey(name)) {
                    throw syntaxErrorAtRel(String.format("redefinition of group name '%s' as group %d; was group %d", name, groups, namedCaptureGroups.get(name)), name.length() + 1);
                }
                namedCaptureGroups.put(name, groups);
            });
            disjunction();
            if (consumingLookahead(")")) {
                emit(")");
            } else {
                throw syntaxErrorAtAbs("missing ), unterminated subpattern", start);
            }
            if (capturing) {
                groupStack.pop();
            }
        }

        private void mustHaveMore() {
            if (atEnd()) {
                throw syntaxErrorHere("unexpected end of pattern");
            }
        }

        private boolean checkGroupName(String groupName) {
            if (groupName.isEmpty()) {
                return false;
            }
            for (int i = 0; i < groupName.length(); i = groupName.offsetByCodePoints(i, 1)) {
                int ch = groupName.codePointAt(i);
                if (i == 0 && !XID_START.contains(ch)) {
                    return false;
                }
                if (i > 0 && !XID_CONTINUE.contains(ch)) {
                    return false;
                }
            }
            return true;
        }

        private void quantifier(int ch) {
            int start = position - 1;
            if (ch == '{') {
                if (consumingLookahead("}")) {
                    emit("\\{\\}");
                    return;
                } else if (consumingLookahead(",}")) {
                    // Python interprets A{,} as A*, whereas ECMAScript does not accept such a range
                    // quantifier.
                    quantifier('*');
                    return;
                }

                Optional<BigInteger> lowerBound = Optional.empty();
                Optional<BigInteger> upperBound = Optional.empty();
                String lower = getMany(PythonFlavorProcessor::isDecDigit);
                if (!lower.isEmpty()) {
                    lowerBound = Optional.of(new BigInteger(lower));
                }
                if (consumingLookahead(",")) {
                    String upper = getMany(PythonFlavorProcessor::isDecDigit);
                    if (!upper.isEmpty()) {
                        upperBound = Optional.of(new BigInteger(upper));
                    }
                } else {
                    upperBound = lowerBound;
                }
                if (!consumingLookahead("}")) {
                    emit("\\{");
                    emit(inPattern.substring(start + 1, position));
                    return;
                }
                if (lowerBound.isPresent() && upperBound.isPresent() && lowerBound.get().compareTo(upperBound.get()) > 0) {
                    throw syntaxErrorAtAbs("min repeat greater than max repeat", start);
                }
                emit(inPattern.substring(start, position));
            } else {
                emit(new String(Character.toChars(ch)));
            }

            switch (lastTerm) {
                case None:
                case Assertion:
                    throw syntaxErrorAtAbs("nothing to repeat", start);
                case Quantifier:
                    throw syntaxErrorAtAbs("multiple repeat", start);
                case Atom:
                    if (consumingLookahead("?")) {
                        emit("?");
                    }
            }
        }

        private void charClass() {
            emit("[");
            int start = position - 1;
            if (consumingLookahead("^")) {
                emit("^");
            }
            int firstPosInside = position;
            classBody: while (true) {
                if (atEnd()) {
                    throw syntaxErrorAtAbs("unterminated character set", start);
                }
                int rangeStart = position;
                Optional<Integer> lowerBound;
                int ch = consumeChar();
                switch (ch) {
                    case ']':
                        if (position == firstPosInside + 1) {
                            emit("\\]");
                            lowerBound = Optional.of((int)']');
                        } else {
                            emit("]");
                            break classBody;
                        }
                        break;
                    case '\\':
                        lowerBound = classEscape();
                        break;
                    default:
                        emit(ch);
                        lowerBound = Optional.of(ch);
                }
                if (consumingLookahead("-")) {
                    emit("-");
                    if (atEnd()) {
                        throw syntaxErrorAtAbs("unterminated character set", start);
                    }
                    Optional<Integer> upperBound;
                    ch = consumeChar();
                    switch (ch) {
                        case ']':
                            emit("]");
                            break classBody;
                        case '\\':
                            upperBound = classEscape();
                            break;
                        default:
                            emit(ch);
                            upperBound = Optional.of(ch);
                    }
                    if (!lowerBound.isPresent() || !upperBound.isPresent() || upperBound.get() < lowerBound.get()) {
                        throw syntaxErrorAtAbs("bad character range " + inPattern.substring(rangeStart, position), rangeStart);
                    }
                }
            }
        }

        private static final class PythonFlags {

            private static final String FLAGS = "iLmsxatu";
            private static final String TYPE_FLAGS = "Lau";
            private static final String GLOBAL_FLAGS = "t";

            private final int value;

            public static final PythonFlags EMPTY_INSTANCE = new PythonFlags("");

            public PythonFlags(String source) {
                int value = 0;
                for (int i = 0; i < source.length(); i++) {
                    value |= maskForFlag(source.charAt(i));
                }
                this.value = value;
            }

            private PythonFlags(int value) {
                this.value = value;
            }

            private int maskForFlag(int flag) {
                assert FLAGS.indexOf(flag) >= 0;
                return 1 << FLAGS.indexOf(flag);
            }

            public boolean hasFlag(int flag) {
                return (this.value & maskForFlag(flag)) != 0;
            }

            public boolean isVerbose() {
                return hasFlag('x');
            }

            public boolean isUnicode() {
                return hasFlag('u');
            }

            public boolean isDotAll() {
                return hasFlag('s');
            }

            public boolean isMultiline() {
                return hasFlag('m');
            }

            public boolean isIgnoreCase() {
                return hasFlag('i');
            }

            public PythonFlags addFlag(int flag) {
                return new PythonFlags(this.value | maskForFlag(flag));
            }

            public PythonFlags delFlag(int flag) {
                return new PythonFlags(this.value & ~maskForFlag(flag));
            }

            public PythonFlags addFlags(PythonFlags otherFlags) {
                return new PythonFlags(this.value | otherFlags.value);
            }

            public PythonFlags delFlags(PythonFlags otherFlags) {
                return new PythonFlags(this.value & ~otherFlags.value);
            }

            public PythonFlags fixFlags(PythonREMode mode) {
                switch (mode) {
                    case Str:
                        if (hasFlag('L')) {
                            throw new RegexSyntaxException("cannot use LOCALE flag with a str pattern");
                        }
                        if (hasFlag('a') && hasFlag('u')) {
                            throw new RegexSyntaxException("ASCII and UNICODE flags are incompatible");
                        }
                        if (!hasFlag('a')) {
                            return addFlag('u');
                        } else {
                            return this;
                        }
                    case Bytes:
                        if (hasFlag('u')) {
                            throw new RegexSyntaxException("cannot use UNICODE flag with a bytes pattern");
                        }
                        if (hasFlag('a') && hasFlag('L')) {
                            throw new RegexSyntaxException("ASCII and LOCALE flags are incompatible");
                        }
                        return this;
                    default:
                        throw new IllegalStateException();
                }
            }

            public boolean atMostOneType() {
                int typeFlagOccurrences = 0;
                for (int i = 0; i < TYPE_FLAGS.length(); i++) {
                    if (hasFlag(TYPE_FLAGS.charAt(i))) {
                        typeFlagOccurrences++;
                    }
                }
                return typeFlagOccurrences <= 1;
            }

            public boolean includesGlobalFlags() {
                for (int i = 0; i < GLOBAL_FLAGS.length(); i++) {
                    if (hasFlag(GLOBAL_FLAGS.charAt(i))) {
                        return true;
                    }
                }
                return false;
            }

            public boolean overlaps(PythonFlags otherFlags) {
                return (this.value & otherFlags.value) != 0;
            }

            @Override
            public boolean equals(Object other) {
                return other instanceof PythonFlags && this.value == ((PythonFlags)other).value;
            }

            @Override
            public String toString() {
                StringBuilder out = new StringBuilder(FLAGS.length());
                for (int i = 0; i < FLAGS.length(); i++) {
                    char flag = FLAGS.charAt(i);
                    if (this.hasFlag(flag)) {
                        out.append(flag);
                    }
                }
                return out.toString();
            }
        }
    }
}
