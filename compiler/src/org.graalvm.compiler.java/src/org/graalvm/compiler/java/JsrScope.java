/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.java;

public class JsrScope {

    public static final JsrScope EMPTY_SCOPE = new JsrScope();

    private final char returnAddress;

    private final JsrScope parent;

    private JsrScope(int returnBci, JsrScope parent) {
        assert returnBci == 0 || parent != null;
        this.returnAddress = (char) returnBci;
        assert this.returnAddress == returnBci;
        this.parent = parent;
    }

    public JsrScope() {
        this.returnAddress = 0;
        this.parent = null;
    }

    public int nextReturnAddress() {
        return returnAddress;
    }

    public JsrScope push(int returnBci) {
        assert returnBci != 0;
        JsrScope res = new JsrScope(returnBci, this);
        // System.out.printf("push(%d) onto %s -> %s%n", returnBci, this, res);
        return res;
    }

    public boolean isEmpty() {
        return returnAddress == 0;
    }

    public boolean isPrefixOf(JsrScope other) {
        if (isEmpty()) {
            return true;
        }
        JsrScope myAncestor = this;
        JsrScope otherAncestor = other;
        while (myAncestor != null) {
            if (otherAncestor == null) {
                return false;
            }
            if (myAncestor.returnAddress != otherAncestor.returnAddress) {
                return false;
            }
            myAncestor = myAncestor.parent;
        }
        return true;
    }

    public JsrScope pop() {
        if (isEmpty()) {
            return this;
        }
        return parent;
    }

    @Override
    public int hashCode() {
        int hc = returnAddress;
        JsrScope ancestor = parent;
        while (ancestor != null) {
            hc = hc ^ ancestor.returnAddress;
            ancestor = ancestor.parent;
        }
        return hc;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj != null && getClass() == obj.getClass()) {
            JsrScope ancestor = this;
            JsrScope otherAncestor = (JsrScope) obj;
            while (ancestor != null) {
                if (otherAncestor == null) {
                    return false;
                }
                if (otherAncestor.returnAddress != ancestor.returnAddress) {
                    return false;
                }
                ancestor = ancestor.parent;
                otherAncestor = otherAncestor.parent;
            }
            if (otherAncestor == null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (JsrScope ancestor = this; ancestor != null; ancestor = ancestor.parent) {
            if (!ancestor.isEmpty()) {
                if (sb.length() != 0) {
                    sb.append(", ");
                }
                sb.append((int) ancestor.returnAddress);
            }
        }
        return "[" + sb + "]";
    }
}
