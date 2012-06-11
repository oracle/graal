/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.spi;

import com.oracle.graal.nodes.*;

/**
 * This interface marks a node as being able to negate its effect, this is intended for nodes that depend on a
 * BooleanNode condition. The canonical representation of has, for example, no way to represent a != b. If such an
 * expression appears during canonicalization the negated expression will be created (a == b) and the usages will be
 * negated, using this interface's {@link #negate()} method.
 */
public interface Negatable {

    /**
     * Tells this node that a condition it depends has been negated, and that it thus needs to invert its own effect.
     * For example, an {@link IfNode} would switch its true and false successors.
     */
    Negatable negate();
}
