/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class GlobUtils {

    private GlobUtils() {
    }

    /* list of glob wildcards we are always escaping because they are not supported yet */
    public static final List<Character> ALWAYS_ESCAPED_GLOB_WILDCARDS = List.of('?', '[', ']', '{', '}');
    public static final String STAR = "*";
    public static final String STAR_STAR = "**";
    public static final String LEVEL_IDENTIFIER = "/";
    public static final String SAME_LEVEL_IDENTIFIER = "#";
    private static final Pattern threeConsecutiveStarsRegex = Pattern.compile(".*[*]{3,}.*");
    private static final Pattern emptyLevelsRegex = Pattern.compile(".*/{2,}.*");
    private static final String ALL_UNNAMED = "ALL_UNNAMED";

    public static String transformToTriePath(String resource, String module) {
        String resolvedModuleName;
        if (module == null || module.isEmpty()) {
            resolvedModuleName = ALL_UNNAMED;
        } else {
            resolvedModuleName = StringUtil.toSlashSeparated(module);
        }

        /* prepare for concatenation */
        if (!resolvedModuleName.endsWith("/")) {
            resolvedModuleName += "/";
        }

        /*
         * if somebody wrote resource like: /foo/bar/** we already append / in resolvedModuleName,
         * and we don't want module//foo/bar/**
         */
        String resolvedResourceName;
        if (resource.startsWith("/")) {
            resolvedResourceName = resource.substring(1);
        } else {
            resolvedResourceName = resource;
        }

        return resolvedModuleName + resolvedResourceName;
    }

    public static String validatePattern(String pattern) {
        StringBuilder sb = new StringBuilder();

        if (pattern.isEmpty()) {
            sb.append("Pattern ").append(pattern).append(" : Pattern cannot be empty. ");
            return sb.toString();
        }

        // check if pattern contains more than 2 consecutive * characters. Example: a/***/b
        if (threeConsecutiveStarsRegex.matcher(pattern).matches()) {
            sb.append("Pattern contains more than two consecutive * characters. ");
        }

        /* check if pattern contains empty levels. Example: a//b */
        if (emptyLevelsRegex.matcher(pattern).matches()) {
            sb.append("Pattern contains empty levels. ");
        }

        /* check unnecessary ** repetition */
        if (pattern.contains("**/**")) {
            sb.append("Pattern contains invalid sequence **/**. Valid pattern should have ** followed by something other than **. ");
        }

        /* check if there are unescaped wildcards */
        boolean escapeMode = false;
        for (int i = 0; i < pattern.length(); i++) {
            char current = pattern.charAt(i);
            if (ALWAYS_ESCAPED_GLOB_WILDCARDS.contains(current) && !escapeMode) {
                sb.append("Pattern contains unescaped character ").append(current).append(". ");
            }

            escapeMode = current == '\\';
        }

        // check if pattern (that matches something from classpath) contains ** without previous
        // Literal parent. Example: */**/... or **/...
        if (pattern.startsWith(ALL_UNNAMED)) {
            List<List<GlobToken>> patternParts = tokenize(pattern);

            // remove ALL_UNNAMED prefix
            patternParts.removeFirst();

            // check glob without module prefix
            outer: for (List<GlobToken> levelTokens : patternParts) {
                for (GlobToken token : levelTokens) {
                    if (token.kind == GlobToken.Kind.LITERAL) {
                        break outer;
                    } else if (token.kind == GlobToken.Kind.STAR_STAR) {
                        String patternWithoutModule = pattern.substring(ALL_UNNAMED.length() + 1);
                        LogUtils.warning("Pattern: " + patternWithoutModule + " contains ** without previous literal. " +
                                        "This pattern is too generic and therefore can match many resources. " +
                                        "Please make the pattern more specific by adding non-generic level before ** level.");
                    }
                }
            }
        }

        if (!sb.isEmpty()) {
            sb.insert(0, "Invalid pattern " + pattern + ". Reasons: ");
        }

        return sb.toString();
    }

    public static List<List<GlobToken>> tokenize(String glob) {
        String pattern = !glob.endsWith("/") ? glob : glob.substring(0, glob.length() - 1);
        List<List<GlobToken>> parts = new ArrayList<>();
        for (String level : pattern.split(LEVEL_IDENTIFIER)) {
            parts.add(tokenizePart(level));
        }
        return parts;
    }

    private static List<GlobToken> tokenizePart(String glob) {
        if (glob.equals(STAR_STAR)) {
            return List.of(new GlobToken(GlobToken.Kind.STAR_STAR, glob));
        } else if (glob.equals(STAR)) {
            return List.of(new GlobToken(GlobToken.Kind.STAR, glob));
        }
        // some combination of LITERAL_STAR and LITERAL tokens.
        List<GlobToken> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean escaped = false;
        for (char c : glob.toCharArray()) {
            currentToken.append(c);
            if (c == STAR.charAt(0) && !escaped) {
                tokens.add(new GlobToken(GlobToken.Kind.LITERAL_STAR, currentToken.toString()));
                currentToken.setLength(0); // clear
            }
            escaped = c == '\\';
        }
        if (!currentToken.isEmpty()) {
            tokens.add(new GlobToken(GlobToken.Kind.LITERAL, currentToken.toString()));
        }
        return tokens;
    }

    public record GlobToken(Kind kind, String value) {
        public enum Kind {
            STAR_STAR,
            STAR,
            LITERAL_STAR,
            LITERAL
        }
    }
}
