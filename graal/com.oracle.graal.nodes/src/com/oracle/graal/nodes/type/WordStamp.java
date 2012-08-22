/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.type;

import com.oracle.graal.api.meta.*;

/**
 * Models the word type.
 */
public class WordStamp extends Stamp {

    private final boolean nonNull;

    public WordStamp(Kind wordKind, boolean nonNull) {
        super(wordKind);
        this.nonNull = nonNull;
    }

    @Override
    public boolean nonNull() {
        return nonNull;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append(kind().typeChar);
        str.append(nonNull ? "!" : "");
        return str.toString();
    }

    @Override
    public boolean alwaysDistinct(Stamp otherStamp) {
        return false;
    }

    @Override
    public Stamp meet(Stamp otherStamp) {
        WordStamp other = (WordStamp) otherStamp;
        boolean meetNonNull = nonNull && other.nonNull;
        if (meetNonNull == this.nonNull) {
            return this;
        } else if (meetNonNull == other.nonNull) {
            return other;
        } else {
            return new WordStamp(kind(), meetNonNull);
        }
    }

    @Override
    public Stamp join(Stamp otherStamp) {
        WordStamp other = (WordStamp) otherStamp;
        boolean joinNonNull = nonNull || other.nonNull;
        if (joinNonNull == this.nonNull) {
            return this;
        } else if (joinNonNull == other.nonNull) {
            return other;
        } else {
            return new WordStamp(kind(), joinNonNull);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (nonNull ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        WordStamp other = (WordStamp) obj;
        if (nonNull != other.nonNull) {
            return false;
        }
        return true;
    }
}
