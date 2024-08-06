/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.memory;

import jdk.graal.compiler.core.common.memory.MemoryExtendKind;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FixedWithNextNodeInterface;

/**
 * Nodes implementing this interface may be able to directly implement sign and/or zero extends as
 * part of the memory access. For a given access, the types of extensions supported is platform
 * specific.
 */
public interface ExtendableMemoryAccess extends MemoryAccess, FixedWithNextNodeInterface {

    /**
     * @return the current {@link MemoryExtendKind} of this memory access.
     */
    MemoryExtendKind getExtendKind();

    /**
     * @return whether this access can possibly be extended with any {@linkplain MemoryExtendKind}.
     */
    boolean isCompatibleWithExtend();

    /**
     * @return whether this access can possibly be extended with {@code newExtendKind}.
     */
    boolean isCompatibleWithExtend(MemoryExtendKind newExtendKind);

    /**
     * @return the number bits of the access. Note this can only be called if the access has a
     *         primitive stamp.
     */
    int getAccessBits();

    /**
     * @return a copy of this memory access with the provided extended kind. Note this can only be
     *         called if {@link #isCompatibleWithExtend(MemoryExtendKind)} is true. In addition
     *         {@link LoweringProvider#supportsFoldingExtendIntoAccess(ExtendableMemoryAccess, MemoryExtendKind)}
     *         should be called to check whether the underlying platform supports this extend kind.
     */
    FixedWithNextNode copyWithExtendKind(MemoryExtendKind extendKind);

    default boolean extendsAccess() {
        return getExtendKind().isExtended();
    }

}
