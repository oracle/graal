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

public enum FailureHandlingKind {

    /**
     * Applying the workspace change is simply aborted if one of the changes provided fails. All
     * operations executed before the failing operation stay executed.
     */
    Abort("abort"),
    /**
     * All operations are executed transactional. That means they either all succeed or no changes
     * at all are applied to the workspace.
     */
    Transactional("transactional"),
    /**
     * If the workspace edit contains only textual file changes they are executed transactional. If
     * resource changes (create, rename or delete file) are part of the change the failure handling
     * startegy is abort.
     */
    TextOnlyTransactional("textOnlyTransactional"),
    /**
     * The client tries to undo the operations already executed. But there is no guaruntee that this
     * is succeeding.
     */
    Undo("undo");

    private final String stringValue;

    FailureHandlingKind(String stringValue) {
        this.stringValue = stringValue;
    }

    public String getStringValue() {
        return stringValue;
    }

    private static final Map<String, FailureHandlingKind> lookup = new HashMap<>();

    static {
        for (FailureHandlingKind value : FailureHandlingKind.values()) {
            lookup.put(value.getStringValue(), value);
        }
    }

    public static FailureHandlingKind get(String stringValue) {
        return lookup.get(stringValue);
    }
}
