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
package com.oracle.graal.hotspot.meta;

import com.oracle.graal.hotspot.*;

/**
 * A mechanism for safely conveying a HotSpot klassOop value from the compiler to the C++ code.
 * Such values should not be directly exposed to Java code as they are not real Java
 * objects. For instance, invoking a method on them or using them in an <code>instanceof</code>
 * expression will most likely crash the VM.
 */
public class HotSpotKlassOop extends CompilerObject {

    private static final long serialVersionUID = -5445542223575839143L;

    /**
     * The Java object from which the klassOop value can be derived (by the C++ code).
     */
    public final Class javaMirror;

    public HotSpotKlassOop(Class javaMirror) {
        this.javaMirror = javaMirror;
    }

    @Override
    public String toString() {
        return "HotSpotKlassOop<" + javaMirror.getName() + ">";
    }
}
