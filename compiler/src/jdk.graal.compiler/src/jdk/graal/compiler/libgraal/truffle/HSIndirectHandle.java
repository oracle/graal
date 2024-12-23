/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.libgraal.truffle;

import java.util.Objects;

/**
 * Base class for HS proxy classes loaded by {@code LibGraalClassLoader} that use
 * {@link java.lang.invoke.MethodHandle}s to perform JNI calls in a native-image host.
 *
 * <p>
 * Implementation: This class maintains a strong reference to an {@code HSObject} instance created
 * in the native-image host. For global or weak-global JNI references, the {@code HSObject}
 * registers a cleaner to manage the deletion of the JNI reference. When the
 * {@link HSIndirectHandle} instance becomes weakly reachable, the corresponding {@code HSObject}
 * instance in the native-image host also becomes weakly reachable. The registered cleaner then
 * deletes the associated JNI global or weak-global reference.
 * </p>
 */
class HSIndirectHandle {

    /**
     * The {@code HSObject} instance created in the native-image host.
     */
    final Object hsHandle;

    /**
     * Constructs an {@code HSIndirectHandle} with a non-null {@code HSObject} reference.
     *
     * @param hsHandle the reference to {@code HSObject} allocated in the native-image host, must
     *            not be null.
     * @throws NullPointerException if {@code hsHandle} is null.
     */
    HSIndirectHandle(Object hsHandle) {
        this.hsHandle = Objects.requireNonNull(hsHandle, "HsHandle must be non-null");
    }
}
