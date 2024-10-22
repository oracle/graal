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
package org.graalvm.visualizer.upgrader;

import java.lang.reflect.Method;

import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 * @author sdedic
 */
public class UpgradeAndExit {
    private static final String UPGRADER_NAME = "org.graalvm.visualizer.upgrader.Upgrader"; // NOI18N

    public static void main(String[] args) {
        System.err.println("eeeee");
        ClassLoader ldr = Lookup.getDefault().lookup(ClassLoader.class);
        try {
            Method m = ldr.loadClass(UPGRADER_NAME).getMethod("main", String[].class);
            m.invoke(null, (Object) args);
        } catch (ReflectiveOperationException ex) {
            Exceptions.printStackTrace(ex);
        }
        System.exit(0);
    }
}
