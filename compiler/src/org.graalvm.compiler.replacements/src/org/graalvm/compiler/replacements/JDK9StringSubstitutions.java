/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.java.LoadFieldNode;

// JaCoCo Exclude

/**
 * Substitutions for {@link String} methods for JDK9 and later.
 *
 * {@link String} changed in JDK9 to contain a byte array instead of a char array. We therefore need
 * new substitutions for the related methods.
 */
@ClassSubstitution(String.class)
public class JDK9StringSubstitutions {
    /**
     * Will be intrinsified with an {@link InvocationPlugin} to a {@link LoadFieldNode}.
     */
    public static native byte[] getValue(String s);

    public static native byte getByte(byte[] value, int i);

    public static native int getCoder(String s);

    public static boolean isCompactString(String s) {
        return getCoder(s) == 0;
    }
}
