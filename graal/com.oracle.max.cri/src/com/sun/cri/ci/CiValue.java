/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.cri.ci;

import java.io.*;

/**
 * Abstract base class for values manipulated by the compiler. All values have a {@linkplain CiKind kind} and are immutable.
 */
public abstract class CiValue implements Serializable {
    private static final long serialVersionUID = -6909397188697766469L;

    @SuppressWarnings("serial")
    public static CiValue IllegalValue = new CiValue(CiKind.Illegal) {
        @Override
        public String toString() {
            return "-";
        }
    };

    /**
     * The kind of this value.
     */
    public final CiKind kind;

    /**
     * Initializes a new value of the specified kind.
     * @param kind the kind
     */
    protected CiValue(CiKind kind) {
        this.kind = kind;
    }

    /**
     * String representation of the kind, which should be the end of all {@link #toString()} implementation of subclasses.
     */
    protected final String kindSuffix() {
        return "|" + kind.typeChar;
    }
}
