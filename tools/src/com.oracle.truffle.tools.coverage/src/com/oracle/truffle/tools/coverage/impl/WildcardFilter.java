/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.coverage.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.graalvm.options.OptionType;

class WildcardFilter {

    static final OptionType<WildcardFilter> WILDCARD_FILTER_TYPE = new OptionType<>("Expression",
                    new Function<String, WildcardFilter>() {
                        @Override
                        public WildcardFilter apply(String filterWildcardExpression) {
                            if (filterWildcardExpression == null) {
                                return null;
                            }
                            String[] expressions = filterWildcardExpression.split(",");
                            List<Pattern> patterns = new ArrayList<>();
                            List<String> strings = new ArrayList<>();
                            for (int i = 0; i < expressions.length; i++) {
                                String expression = expressions[i];
                                expression = expression.trim();
                                if (expression.contains("?") || expression.contains("*")) {
                                    try {
                                        patterns.add(Pattern.compile(wildcardToRegex(expression)));
                                    } catch (PatternSyntaxException e) {
                                        throw new IllegalArgumentException(
                                                        String.format("Invalid wildcard pattern %s.", expression), e);
                                    }
                                } else {
                                    strings.add(expression);
                                }
                            }
                            return new WildcardFilter(strings, patterns, filterWildcardExpression);
                        }
                    });

    private static String wildcardToRegex(String wildcard) {
        StringBuilder s = new StringBuilder(wildcard.length());
        s.append('^');
        for (int i = 0, is = wildcard.length(); i < is; i++) {
            char c = wildcard.charAt(i);
            switch (c) {
                case '*':
                    s.append("\\S*");
                    break;
                case '?':
                    s.append("\\S");
                    break;
                // escape special regexp-characters
                case '(':
                case ')':
                case '[':
                case ']':
                case '$':
                case '^':
                case '.':
                case '{':
                case '}':
                case '|':
                case '\\':
                    s.append("\\");
                    s.append(c);
                    break;
                default:
                    s.append(c);
                    break;
            }
        }
        s.append('$');
        return s.toString();
    }

    boolean testWildcardExpressions(String value) {
        if (strings.isEmpty() && patterns.isEmpty()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        for (Pattern pattern : patterns) {
            if (pattern.matcher(value).matches()) {
                return true;
            }
        }
        for (String string : strings) {
            if (string.equals(value)) {
                return true;
            }
        }
        return false;
    }

    static final WildcardFilter DEFAULT = new WildcardFilter(new ArrayList<>(0), new ArrayList<>(0), "*");
    final List<String> strings;
    final List<Pattern> patterns;
    private final String expression;

    WildcardFilter(List<String> strings, List<Pattern> patterns, String expression) {
        Objects.requireNonNull(strings);
        Objects.requireNonNull(patterns);
        this.strings = strings;
        this.patterns = patterns;
        this.expression = expression;
    }

    @Override
    public String toString() {
        return expression;
    }
}
