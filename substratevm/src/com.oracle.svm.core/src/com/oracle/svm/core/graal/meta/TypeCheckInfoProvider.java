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
package com.oracle.svm.core.graal.meta;

import com.oracle.svm.core.hub.DynamicHubProvider;
import com.oracle.svm.jvmci.shared.meta.SharedType;

/**
 * Supplies closed-world subtype-check metadata for the image in which the compiled code will execute.
 * A static check embeds a start/range/slot predicate and reads the receiver's type-ID slots
 * at run time. Both must describe the same image: relocating a hub reference cannot repair
 * numeric predicates embedded from another image's layout.
 * <p>
 * The default provider reads the current type metadata. A compilation targeting a loaded image
 * can supply captured predicates without changing the metadata used to build the current image.
 * Such a provider must also prevent folding dynamic checks from current-image hubs when those
 * hubs will be rebound to the loaded image. Exact hub comparisons still require identity binding.
 * <p>
 * Selecting metadata does not establish analysis or layout compatibility, and cannot repair a
 * check already eliminated by analysis. Consumers must obtain this provider from the current
 * compilation, including when lowering compiler-generated checks.
 */
@FunctionalInterface
public interface TypeCheckInfoProvider {
    /** Uses the subtype-check metadata recorded for the type. */
    TypeCheckInfoProvider DEFAULT = type -> ((DynamicHubProvider) type).getTypeCheckInfo();

    /** Unsigned 16-bit values encoded as shorts, matching the closed-world hub layout. */
    record TypeCheckInfo(short start, short range, short slot) {
    }

    TypeCheckInfo getTypeCheckInfo(SharedType type);

    /** Whether dynamic checks may fold metadata loads from constant hubs in this build. */
    default boolean canFoldHubMetadata() {
        return true;
    }
}
