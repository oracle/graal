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
package org.graalvm.compiler.core.common.memory;

import static jdk.vm.ci.code.MemoryBarriers.JMM_POST_VOLATILE_READ;
import static jdk.vm.ci.code.MemoryBarriers.JMM_POST_VOLATILE_WRITE;
import static jdk.vm.ci.code.MemoryBarriers.JMM_PRE_VOLATILE_READ;
import static jdk.vm.ci.code.MemoryBarriers.JMM_PRE_VOLATILE_WRITE;
import static jdk.vm.ci.code.MemoryBarriers.LOAD_LOAD;
import static jdk.vm.ci.code.MemoryBarriers.LOAD_STORE;
import static jdk.vm.ci.code.MemoryBarriers.STORE_STORE;

/**
 * The new memory order modes (JDK9+) are defined with cumulative effect, from weakest to strongest:
 * Plain, Opaque, Release/Acquire, and Volatile. The existing Plain and Volatile modes are defined
 * compatibly with their pre-JDK 9 forms. Any guaranteed property of a weaker mode, plus more, holds
 * for a stronger mode. (Conversely, implementations are allowed to use a stronger mode than
 * requested for any access.) In JDK 9, these are provided without a full formal specification.
 */
public enum MemoryOrderMode {
    PLAIN(0, 0, 0, 0, false),
    /**
     * Opaque accesses are wrapped by dummy membars to avoid floating/hoisting, this is stronger
     * than required since Opaque mode does not directly impose any ordering constraints with
     * respect to other variables beyond Plain mode.
     */
    OPAQUE(0, 0, 0, 0, true),
    ACQUIRE(0, LOAD_LOAD | LOAD_STORE, 0, 0, true),
    RELEASE(0, 0, LOAD_STORE | STORE_STORE, 0, true),
    RELEASE_ACQUIRE(0, LOAD_LOAD | LOAD_STORE, LOAD_STORE | STORE_STORE, 0, true),
    VOLATILE(JMM_PRE_VOLATILE_READ, JMM_POST_VOLATILE_READ, JMM_PRE_VOLATILE_WRITE, JMM_POST_VOLATILE_WRITE, true);

    public final boolean emitBarriers;
    public final int preReadBarriers;
    public final int postReadBarriers;
    public final int preWriteBarriers;
    public final int postWriteBarriers;

    MemoryOrderMode(int preReadBarriers, int postReadBarriers, int preWriteBarriers, int postWriteBarriers, boolean emitBarriers) {
        this.emitBarriers = emitBarriers;
        this.preReadBarriers = preReadBarriers;
        this.postReadBarriers = postReadBarriers;
        this.preWriteBarriers = preWriteBarriers;
        this.postWriteBarriers = postWriteBarriers;
    }
}
