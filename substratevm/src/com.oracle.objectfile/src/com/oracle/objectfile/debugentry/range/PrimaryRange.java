/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.debugentry.range;

import com.oracle.objectfile.debugentry.MethodEntry;

public class PrimaryRange extends Range {
    /**
     * The first subrange in the range covered by this primary or null if this primary as no
     * subranges.
     */
    protected SubRange firstCallee;
    /**
     * The last subrange in the range covered by this primary.
     */
    protected SubRange lastCallee;

    protected PrimaryRange(MethodEntry methodEntry, int lo, int hi, int line) {
        super(methodEntry, lo, hi, line, -1);
        this.firstCallee = null;
        this.lastCallee = null;
    }

    @Override
    public boolean isPrimary() {
        return true;
    }

    @Override
    protected void addCallee(SubRange callee) {
        assert this.lo <= callee.lo;
        assert this.hi >= callee.hi;
        assert callee.caller == this;
        assert callee.siblingCallee == null;
        if (this.firstCallee == null) {
            assert this.lastCallee == null;
            this.firstCallee = this.lastCallee = callee;
        } else {
            this.lastCallee.siblingCallee = callee;
            this.lastCallee = callee;
        }
    }

    @Override
    public SubRange getFirstCallee() {
        return firstCallee;
    }

    @Override
    public boolean isLeaf() {
        return firstCallee == null;
    }
}
