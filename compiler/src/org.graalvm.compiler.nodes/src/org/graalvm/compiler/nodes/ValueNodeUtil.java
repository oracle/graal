/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import static java.lang.Character.toLowerCase;

import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.memory.MemoryKill;

public class ValueNodeUtil {

    /**
     * Converts a given instruction to a value string. The representation of an node as a value is
     * formed by concatenating the {@linkplain jdk.vm.ci.meta.JavaKind#getTypeChar character}
     * denoting its {@linkplain ValueNode#getStackKind kind} and its id. For example, {@code "i13"}.
     *
     * @param value the instruction to convert to a value string. If {@code value == null}, then "-"
     *            is returned.
     * @return the instruction representation as a string
     */
    public static String valueString(ValueNode value) {
        return (value == null) ? "-" : ("" + toLowerCase(value.getStackKind().getTypeChar()) + value.toString(Verbosity.Id));
    }

    public static ValueNode asNode(MemoryKill node) {
        if (node == null) {
            return null;
        } else {
            return node.asNode();
        }
    }
}
