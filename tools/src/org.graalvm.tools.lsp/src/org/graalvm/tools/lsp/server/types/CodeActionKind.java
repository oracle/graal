/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.server.types;

import java.util.HashMap;
import java.util.Map;

/**
 * A set of predefined code action kinds.
 */
public enum CodeActionKind {

    /**
     * Empty kind.
     */
    Empty(""),
    /**
     * Base kind for quickfix actions: 'quickfix'.
     */
    QuickFix("quickfix"),
    /**
     * Base kind for refactoring actions: 'refactor'.
     */
    Refactor("refactor"),
    /**
     * Base kind for refactoring extraction actions: 'refactor.extract'.
     *
     * Example extract actions:
     *
     * - Extract method - Extract function - Extract variable - Extract interface from class - ...
     */
    RefactorExtract("refactor.extract"),
    /**
     * Base kind for refactoring inline actions: 'refactor.inline'.
     *
     * Example inline actions:
     *
     * - Inline function - Inline variable - Inline constant - ...
     */
    RefactorInline("refactor.inline"),
    /**
     * Base kind for refactoring rewrite actions: 'refactor.rewrite'.
     *
     * Example rewrite actions:
     *
     * - Convert JavaScript function to class - Add or remove parameter - Encapsulate field - Make
     * method static - Move method to base class - ...
     */
    RefactorRewrite("refactor.rewrite"),
    /**
     * Base kind for source actions: `source`.
     *
     * Source code actions apply to the entire file.
     */
    Source("source"),
    /**
     * Base kind for an organize imports source action: `source.organizeImports`.
     */
    SourceOrganizeImports("source.organizeImports"),
    /**
     * Base kind for auto-fix source actions: `source.fixAll`.
     *
     * Fix all actions automatically fix errors that have a clear fix that do not require user
     * input. They should not suppress errors or perform unsafe fixes such as generating new types
     * or classes.
     *
     * @since 3.15.0
     */
    SourceFixAll("source.fixAll");

    private final String stringValue;

    CodeActionKind(String stringValue) {
        this.stringValue = stringValue;
    }

    public String getStringValue() {
        return stringValue;
    }

    private static final Map<String, CodeActionKind> lookup = new HashMap<>();

    static {
        for (CodeActionKind value : CodeActionKind.values()) {
            lookup.put(value.getStringValue(), value);
        }
    }

    public static CodeActionKind get(String stringValue) {
        return lookup.get(stringValue);
    }
}
