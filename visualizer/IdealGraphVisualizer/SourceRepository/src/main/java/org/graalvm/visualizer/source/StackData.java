/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.source;

import java.util.List;
import java.util.Objects;

/**
 * Binds a Node to a source. In the future, the InputNode can have
 * multiple location traces, perhaps in the user language AND the
 * implementation language.
 */
final class StackData {
    private final int nodeId;
    private final String mimeType;
    private final List<Location> locations;

    StackData(int nodeId, String mimeType, List<Location> locations) {
        this.mimeType = mimeType;
        this.nodeId = nodeId;
        this.locations = locations;
    }

    public int getNodeId() {
        return nodeId;
    }

    public int size() {
        return locations.size();
    }

    public String getLanguageMimeType() {
        return mimeType;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 19 * hash + this.nodeId;
        hash = 19 * hash + Objects.hashCode(this.mimeType);
        return hash;
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
        final StackData other = (StackData) obj;
        if (this.nodeId != other.nodeId) {
            return false;
        }
        if (!Objects.equals(this.mimeType, other.mimeType)) {
            return false;
        }
        return true;
    }

    public List<Location> getLocations() {
        return locations;
    }
}
