/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.arraycopy;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallSignature;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;

public interface ArrayCopyLookup {

    /**
     * Signature of an unsafe {@link System#arraycopy} stub.
     *
     * The signature is equivalent to {@link sun.misc.Unsafe#copyMemory(long, long, long)}. For the
     * semantics refer to {@link sun.misc.Unsafe#copyMemory(Object, long, Object, long, long)}.
     *
     * @see sun.misc.Unsafe#copyMemory
     */
    ForeignCallSignature UNSAFE_ARRAYCOPY = new ForeignCallSignature("unsafe_arraycopy", void.class, Word.class, Word.class, Word.class);

    /**
     * Signature of a generic {@link System#arraycopy} stub.
     *
     * Instead of throwing an {@link ArrayStoreException}, the stub is expected to return the number
     * of copied elements xor'd with {@code -1}. A return value of {@code 0} indicates that the
     * operation was successful.
     */
    ForeignCallSignature GENERIC_ARRAYCOPY = new ForeignCallSignature("generic_arraycopy", int.class, Word.class, int.class, Word.class, int.class, int.class);

    /**
     * Looks up the call descriptor for a fast checkcast {@link System#arraycopy} stub.
     *
     * @see CheckcastArrayCopyCallNode
     */
    ForeignCallDescriptor lookupCheckcastArraycopyDescriptor(boolean uninit);

    /**
     * Looks up the call descriptor for a specialized {@link System#arraycopy} stub.
     *
     * @see ArrayCopyCallNode
     */
    ForeignCallDescriptor lookupArraycopyDescriptor(JavaKind kind, boolean aligned, boolean disjoint, boolean uninit, LocationIdentity killedLocation);
}
