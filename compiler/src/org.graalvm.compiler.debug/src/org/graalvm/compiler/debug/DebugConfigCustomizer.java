/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug;

import java.util.Collection;

import org.graalvm.compiler.options.OptionValues;

/**
 * Service for specifying {@link DebugDumpHandler}s and {@link DebugVerifyHandler}s when
 * constructing a {@link GraalDebugConfig}.
 */
public interface DebugConfigCustomizer {
    /**
     * Adds {@link DebugDumpHandler}s to {@code dumpHandlers}.
     *
     * @param options options that may be used to configure any handlers created by this method
     * @param dumpHandlers the list to which the new handlers should be added
     * @param handlerArgs extra parameters that may be used to configure the new handlers
     */
    default void addDumpHandlersTo(OptionValues options, Collection<DebugDumpHandler> dumpHandlers, Object... handlerArgs) {
    }

    /**
     * Adds {@link DebugVerifyHandler}s to {@code verifyHandlers}.
     *
     * @param options options that may be used to configure any handlers created by this method
     * @param verifyHandlers the list to which the new handlers should be added
     */
    default void addVerifyHandlersTo(OptionValues options, Collection<DebugVerifyHandler> verifyHandlers) {
    }
}
