/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.utilities;

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;

/**
 * Helper function that allows to dump the AST during creation to a JSON format.
 */
public class JSONHelper {

    private static StringBuilder AstJsonDumpBuilder = new StringBuilder();

    public static void dumpNewChild(Node parentNode, Node childNode) {
        if (AstJsonDumpBuilder != null) {
            AstJsonDumpBuilder.append("{ \"action\": \"insertNode\", \"parentId\": \"" + getID(parentNode) + "\", \"newId\": \"" + getID(childNode) + "\" },\n");
        }
    }

    public static void dumpReplaceChild(Node oldNode, Node newNode, CharSequence reason) {
        if (AstJsonDumpBuilder != null) {
            AstJsonDumpBuilder.append("{ \"action\": \"replaceNode\", \"oldId\": \"" + getID(oldNode) + "\", \"newId\": \"" + getID(newNode) + "\", \"reason\": " + quote(reason) + " },\n");
        }
    }

    public static void dumpNewNode(Node newNode) {
        if (AstJsonDumpBuilder != null) {
            AstJsonDumpBuilder.append("{ \"action\": \"createNode\", \"newId\": \"" + getID(newNode) + "\", \"type\": \"" + getType(newNode) + "\", \"description\": \"" + newNode.getDescription() +
                            "\", \"language\": \"" + newNode.getLanguage() + "\"" + getSourceSectionInfo(newNode) + " },\n");
        }
    }

    private static String getSourceSectionInfo(Node newNode) {
        SourceSection sourceSection = newNode.getSourceSection();
        if (sourceSection != null) {
            return ", \"identifier\": \"" + sourceSection.getIdentifier() + "\" ";
        } else {
            return "";
        }
    }

    public static String getResult() {
        return AstJsonDumpBuilder.toString();
    }

    private static String getID(Node newChild) {
        return String.valueOf(newChild.hashCode());
    }

    private static String getType(Node node) {
        return node.getClass().getSimpleName();
    }

    private static String quote(CharSequence value) {
        StringBuilder builder = new StringBuilder(value.length() + 2);
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default: {
                    if (c < ' ') {
                        builder.append("\\u00");
                        builder.append(Character.forDigit((c >> 4) & 0xF, 16));
                        builder.append(Character.forDigit(c & 0xF, 16));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        builder.append('"');
        return builder.toString();
    }

    public static void restart() {
        AstJsonDumpBuilder = new StringBuilder();
    }
}
