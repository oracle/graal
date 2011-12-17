/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.collect;

import java.io.*;
import java.util.*;

import com.sun.max.*;

/**
 * A subclass of {@link Properties} that {@linkplain Collections#sort(java.util.List) sorts} its properties as they
 * are saved.
 */
public class SortedProperties extends Properties {

    /**
     * Overridden so that the properties are {@linkplain #store(Writer, String) saved} sorted according their keys.
     */
    @Override
    public synchronized Enumeration<Object> keys() {
        final Enumeration<Object> keysEnum = super.keys();
        final Vector<String> keyList = new Vector<String>(size());
        while (keysEnum.hasMoreElements()) {
            keyList.add((String) keysEnum.nextElement());
        }
        Collections.sort(keyList);
        final Class<Enumeration<Object>> type = null;
        return Utils.cast(type, keyList.elements());
    }
}
