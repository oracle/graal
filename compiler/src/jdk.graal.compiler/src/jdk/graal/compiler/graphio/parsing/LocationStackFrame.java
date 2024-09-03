/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.graphio.parsing;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class LocationStackFrame {
    private final BinaryReader.Method method;
    private final int bci;
    private final List<LocationStratum> strata;
    private final LocationStackFrame parent;
    private int hashCode;

    LocationStackFrame(BinaryReader.Method method, int bci, List<LocationStratum> infos, LocationStackFrame parent) {
        this.method = method;
        this.bci = bci;
        this.parent = parent;
        this.strata = infos;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String sep = "";
        for (LocationStackFrame t = this; t != null; t = t.parent) {
            sb.append(sep);
            sb.append(methodHolderName(t)).append(".").append(t.method.name);
            for (LocationStratum s : t.strata) {
                if (s.file != null) {
                    sb.append("(").append(s.file).append(":").append(s.line).append(")");
                } else if (s.uri != null) {
                    sb.append("(").append(s.uri).append(":").append(s.line).append(")");
                }
            }
            sb.append(" [bci:").append(t.bci).append(']');
            sep = "\n";
        }
        return sb.toString();
    }

    private static String methodHolderName(LocationStackFrame t) {
        if (t != null && t.method != null && t.method.holder != null) {
            return t.method.holder.name;
        }
        return null;
    }

    /**
     * Returns name of the method, fully qualified.
     *
     * @return method name, in java notation.
     */
    public String getFullMethodName() {
        return method == null ? null : method.toString(Builder.Length.M);
    }

    BinaryReader.Method getMethod() {
        return method;
    }

    public LocationStackFrame getParent() {
        return parent;
    }

    public int getBci() {
        return bci;
    }

    /**
     * Returns filename of the 1st strata.
     */
    public String getFileName() {
        return strata.get(0).file;
    }

    public int getLine() {
        return strata.get(0).line;
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            int hash = 5;
            hash = 79 * hash + Objects.hashCode(this.method);
            hash = 79 * hash + this.bci;
            hash = 79 * hash + Objects.hashCode(this.strata);
            hash = 79 * hash + Objects.hashCode(this.parent);
            if (hash == 0) {
                hash = 79;
            }
            hashCode = hash;
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final LocationStackFrame other = (LocationStackFrame) obj;
        if (!Objects.equals(this.method, other.method)) {
            return false;
        }
        if (!Objects.equals(this.parent, other.parent)) {
            return false;
        }
        return Objects.equals(this.strata, other.strata);
    }

    public List<LocationStratum> getStrata() {
        return Collections.unmodifiableList(strata);
    }
}
