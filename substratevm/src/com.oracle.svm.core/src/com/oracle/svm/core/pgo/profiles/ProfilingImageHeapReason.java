/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.pgo.profiles;

import java.util.Objects;

/// Serialized node representation of a Context-Augmented Heap Path (CAHP).
///
/// Each node is identified by `id` and linked to its parent via `parentId`, forming the CAHP tree
/// used by object-access profile dumping and optimized-image build.
///
/// - `id`: unique node identifier within one CAHP tree
/// - `typeId`: reason-kind identifier associated with this node
/// - `parentId`: parent node identifier (`0` for the root)
/// - `value`: optional payload value for the node, such as method or type details
/// - `objectAlignments`: optional list of image-heap 8-byte alignments mapped to this reason
public record ProfilingImageHeapReason(
                int id,
                byte typeId,
                int parentId,
                String value,
                long[] objectAlignments) {

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProfilingImageHeapReason that = (ProfilingImageHeapReason) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
