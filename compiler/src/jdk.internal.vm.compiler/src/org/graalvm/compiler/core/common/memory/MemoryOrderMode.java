/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common.memory;

import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * The new memory order modes (JDK9+) are defined with cumulative effect, from weakest to strongest:
 * Plain, Opaque, Release/Acquire, and Volatile. The existing Plain and Volatile modes are defined
 * compatibly with their pre-JDK 9 forms. Any guaranteed property of a weaker mode, plus more, holds
 * for a stronger mode. (Conversely, implementations are allowed to use a stronger mode than
 * requested for any access.) In JDK 9, these are provided without a full formal specification.
 */
public enum MemoryOrderMode {
    /**
     * {@code PLAIN} accesses are "normal" Java memory operations. They do not have any memory
     * ordering guarantees.
     */
    PLAIN,
    /**
     * {@code OPAQUE} accesses are similar to {@link #PLAIN} accesses; they also do not have any
     * memory ordering guarantees. However, opaque writes are guaranteed to eventually become
     * visible to other threads.
     */
    OPAQUE,
    /**
     * {@code ACQUIRE} accesses are guaranteed to execute <b>before</b> all subsequent memory
     * accesses on the same thread. However, they do not impose any ordering requirements on prior
     * memory accesses (prior memory accesses can be executed after a subsequent acquire).
     * Traditionally {@code ACQUIRE}s are associated with loads.
     */
    ACQUIRE,
    /**
     * {@code RELEASE} accesses are guaranteed to execute <b>after</b> all prior memory accesses on
     * the same thread. However, they do not impose any ordering requirements on subsequent memory
     * accesses (subsequent memory accesses can be executed before a prior release). Traditionally
     * {@code RELEASE}s are associated with stores.
     */
    RELEASE,
    /**
     * {@code RELEASE_ACQUIRE} accesses are guaranteed to execute <b>after</b> all prior memory
     * access and <b>before</b> all subsequent memory accesses on the same thread. Traditionally
     * {@code RELEASE_ACQUIRE}s are associated with atomic operations.
     */
    RELEASE_ACQUIRE,
    /**
     * The behavior of {@code VOLATILE} is dependent on the type of access (load, store,
     * load&store):
     *
     * <ul>
     * <li>Load: same ordering requirements as {@link #ACQUIRE}</li>
     * <li>Store: same ordering requirements as {@link #RELEASE}</li>
     * <li>Load&Store (i.e., atomic): same ordering requirements as {@link #RELEASE_ACQUIRE}</li>
     * </ul>
     *
     * In addition, all volatile accesses are strictly ordered. This means that, within a given
     * thread, every volatile access must execute in program order w.r.t every other volatile access
     * in that same thread.
     */
    VOLATILE;

    public static boolean ordersMemoryAccesses(MemoryOrderMode memoryOrder) {
        return memoryOrder != PLAIN;
    }

    public static MemoryOrderMode getMemoryOrder(ResolvedJavaField field) {
        return field.isVolatile() ? VOLATILE : PLAIN;
    }
}
