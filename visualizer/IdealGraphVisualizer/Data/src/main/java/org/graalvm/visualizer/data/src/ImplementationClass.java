/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.data.src;

import org.openide.util.Lookup;

/**
 * A cookie to be used in {@link Lookup} to represent an implementation name
 * of a class. Useful for actions that want to jump to source code defining
 * current selected - e.g. {@code Utilities.actionsGlobalContext()} element. UI views
 * are encouraged to expose {@link ImplementationClass} instances whereever
 * a selection knows who's its implementation class.
 *
 * @since 1.5
 */
public final class ImplementationClass {
    private final String className;

    /**
     * Constructs new instance.
     *
     * @param className fully qualified name of a class
     * @since 1.5
     */
    public ImplementationClass(String className) {
        this.className = className;
    }

    /**
     * Name of the implementation class.
     *
     * @return fully qualified name of a class
     * @since 1.5
     */
    public String getName() {
        return className;
    }
}
