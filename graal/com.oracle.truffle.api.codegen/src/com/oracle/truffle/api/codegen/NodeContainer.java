/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.codegen;

import java.lang.annotation.*;

import com.oracle.truffle.api.nodes.*;

/**
 * A node container can be used to enable Truffle-DSL in classes which do not extend {@link Node}.
 * Compared to normal {@link Node} implementation the nodes are not identified by a class but by
 * their method name. There are cases were method signatures are matching exactly but should be in
 * the same {@link Node}. In this case use {@link NodeId} to disambiguate such cases.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface NodeContainer {

    /** The node class to use as base class for {@link Node} definitions grouped by method names. */
    Class<? extends Node> value();

}
