/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodeinfo;

/**
 * Marker for a Node class generated on the basis of a {@link NodeInfo} annotation on its super
 * class.
 *
 * Testing whether a node class is generated:
 *
 * <pre>
 * Class<? extends Node> c = ...;
 * if (GeneratedNode.class.isAssignableFrom(c)) { ... }
 * </pre>
 *
 * Since a generated node class always subclasses the node from which it is generated:
 *
 * <pre>
 * if (GeneratedNode.class.isAssignableFrom(c)) {
 *     Class&lt;?&gt; original = c.getSuperclass();
 * }
 * </pre>
 *
 * Note: This used to be an annotation but was converted to an interface to avoid annotation parsing
 * when creating a NodeClass instance.
 */
public interface GeneratedNode {
}
