/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.memory;

import org.graalvm.compiler.nodes.ValueNodeInterface;
import org.graalvm.word.LocationIdentity;

/**
 * This interface marks nodes that access some memory location, and that have an edge to the last
 * node that kills this location.
 */
public interface MemoryAccess extends ValueNodeInterface {

    LocationIdentity getLocationIdentity();

    /**
     *
     * @return a {@linkplain MemoryKill} that represents the last memory state in the memory graph
     *         for the {@linkplain LocationIdentity} returned by
     *         {@linkplain MemoryAccess#getLocationIdentity()}
     */
    MemoryKill getLastLocationAccess();

    /**
     * @param lla the {@link MemoryKill} that represents the last kill of the
     *            {@linkplain LocationIdentity} returned by
     *            {@linkplain MemoryAccess#getLocationIdentity()}
     */
    void setLastLocationAccess(MemoryKill lla);

}
