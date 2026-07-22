/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.ide;

/** Stable semantic categories used for IDE report payload-scope selection. */
public enum IDEReportCategory {
    REFLECTION("reflection", true),
    INLINING("inlining", true),
    UNREACHABLE("unreachable", true),
    DEVIRTUALIZATION("devirtualization", true),
    RETURN_VALUE("return-value", true),
    PARAMETER_VALUE("parameter-value", false),
    CONSTANT_FIELD("constant-field", false),
    CLASS_INITIALIZATION("class-initialization", false),
    INLINED_ONLY_METHOD("inlined-only-method", false);

    private final String serializedName;
    private final boolean includedInMinimalPayload;

    IDEReportCategory(String serializedName, boolean includedInMinimalPayload) {
        this.serializedName = serializedName;
        this.includedInMinimalPayload = includedInMinimalPayload;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean includedInMinimalPayload() {
        return includedInMinimalPayload;
    }
}
