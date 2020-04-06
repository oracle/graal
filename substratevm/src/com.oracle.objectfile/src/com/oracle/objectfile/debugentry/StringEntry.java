/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

/**
 * Used to retain a unique (up to equals) copy of a String. Also flags whether the String needs to
 * be located in the debug_string section and, if so, tracks the offset at which it gets written.
 */
public class StringEntry {
    private String string;
    private int offset;
    private boolean addToStrSection;

    StringEntry(String string) {
        this.string = string;
        this.offset = -1;
    }

    public String getString() {
        return string;
    }

    public int getOffset() {
        /*
         * Offset must be set before this can be fetched
         */
        assert offset >= 0;
        return offset;
    }

    public void setOffset(int offset) {
        assert this.offset < 0;
        assert offset >= 0;
        this.offset = offset;
    }

    public boolean isAddToStrSection() {
        return addToStrSection;
    }

    public void setAddToStrSection() {
        this.addToStrSection = true;
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || !(object instanceof StringEntry)) {
            return false;
        } else {
            StringEntry other = (StringEntry) object;
            return this == other || string.equals(other.string);
        }
    }

    @Override
    public int hashCode() {
        return string.hashCode() + 37;
    }

    @Override
    public String toString() {
        return string;
    }

    public boolean isEmpty() {
        return string.length() == 0;
    }
}
