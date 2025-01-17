/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.objectfile.debugentry;

import java.util.concurrent.atomic.AtomicInteger;

public record LocalEntry(String name, TypeEntry type, int slot, AtomicInteger line) {

    public LocalEntry(String name, TypeEntry type, int slot, int line) {
        /*
         * Use a AtomicInteger for the line number as it might change if we encounter the same local
         * variable in a different frame state with a lower line number
         */
        this(name, type, slot, new AtomicInteger(line));
    }

    @Override
    public String toString() {
        return String.format("Local(%s type=%s slot=%d line=%d)", name, type.getTypeName(), slot, getLine());
    }

    public void setLine(int newLine) {
        this.line.set(newLine);
    }

    public int getLine() {
        return line.get();
    }
}
