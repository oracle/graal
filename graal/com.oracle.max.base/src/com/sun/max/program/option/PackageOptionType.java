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
package com.sun.max.program.option;

import static com.sun.max.lang.Classes.*;


/**
 * The {@code PackageOptionType} class.
 * Created Nov 20, 2007
 */
public class PackageOptionType extends Option.Type<String> {
    public final String superPackage;

    public PackageOptionType(String superPackage) {
        super(String.class, "vm-package");
        this.superPackage = superPackage;
    }

    private Class packageClass(String pkgName) {
        try {
            return Class.forName(pkgName + ".Package");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Override
    public String parseValue(String string) {
        final String fullName = superPackage + "." + string;
        if (string != null && string.length() > 0) {
            Class pkgClass = packageClass(fullName);
            if (pkgClass == null) {
                pkgClass = packageClass(string);
            }
            if (pkgClass == null) {
                throw new Option.Error("Package not found: " + string + " (or " + fullName + ")");
            }
            return getPackageName(pkgClass);
        }
        return null;
    }

    @Override
    public String getValueFormat() {
        return "<package-name>";
    }
}
