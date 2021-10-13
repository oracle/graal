/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.processor.builders;

import java.util.Collection;

public abstract class AbstractCodeBuilder {
    public static final char PAREN_OPEN = '(';
    public static final char PAREN_CLOSE = ')';
    public static final char SEMICOLON = ';';
    public static final char BLOCK_OPEN = '{';
    public static final char BLOCK_CLOSE = '}';

    protected String joinParts(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object part : parts) {
            sb.append(part);
        }
        return sb.toString();
    }

    protected StringBuilder joinPartsWith(StringBuilder sb, String delimiter, Collection<String> parts) {
        return joinPartsWith(sb, delimiter, parts.toArray());
    }

    protected StringBuilder joinPartsWith(StringBuilder sb, String delimiter, Object... parts) {
        for (Object part : parts) {
            sb.append(part).append(delimiter);
        }
        if (parts.length > 0) {
            sb.delete(sb.length() - delimiter.length(), sb.length());
        }
        return sb;
    }

    abstract void buildImpl(IndentingStringBuilder isb);
}
