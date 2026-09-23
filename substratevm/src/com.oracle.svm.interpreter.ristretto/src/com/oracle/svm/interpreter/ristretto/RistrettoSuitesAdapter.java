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
package com.oracle.svm.interpreter.ristretto;

import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.tiers.Suites;

/**
 * Adapts the graph builder plugins and compiler suites used by the Ristretto runtime compiler.
 * Implementations can contribute VM- or edition-specific plugins and phases to the runtime
 * compilation pipeline without making the CE runtime compiler depend directly on those
 * implementations.
 */
public interface RistrettoSuitesAdapter {
    /**
     * Adds graph builder plugins to the Ristretto compiler. Implementations should
     * register only plugins whose generated nodes can be lowered inside a native-image runtime
     * compilation, without depending on hosted services.
     */
    default void adaptGraphBuilderPlugins(@SuppressWarnings("unused") InvocationPlugins plugins) {
    }

    /**
     * Rewrites the copied SVM compiler suites before they are used by Ristretto. Implementations can
     * insert or replace phases when the replacement remains valid for runtime compilation and does
     * not make edition-specific hosted infrastructure reachable from CE.
     */
    default void adaptSuites(@SuppressWarnings("unused") Suites suites, @SuppressWarnings("unused") OptionValues options) {
    }
}
