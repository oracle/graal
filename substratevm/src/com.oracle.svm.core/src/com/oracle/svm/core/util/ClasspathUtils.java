/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import com.oracle.svm.core.OS;

public final class ClasspathUtils {

    public static final String cpWildcardSubstitute = "$JavaCla$$pathWildcard$ubstitute$";

    public static Path stringToClasspath(String cp) {
        String separators = Pattern.quote(File.separator);
        if (OS.getCurrent().equals(OS.WINDOWS)) {
            separators += "/"; /* on Windows also / is accepted as valid separator */
        }
        String[] components = cp.split("[" + separators + "]", Integer.MAX_VALUE);
        for (int i = 0; i < components.length; i++) {
            if (components[i].equals("*")) {
                components[i] = cpWildcardSubstitute;
            }
        }
        return Paths.get(String.join(File.separator, components));
    }

    public static String classpathToString(Path cp) {
        String[] components = cp.toString().split(Pattern.quote(File.separator), Integer.MAX_VALUE);
        for (int i = 0; i < components.length; i++) {
            if (components[i].equals(cpWildcardSubstitute)) {
                components[i] = "*";
            }
        }
        return String.join(File.separator, components);
    }

    public static boolean isJar(Path p) {
        Path fn = p.getFileName();
        assert fn != null;
        return fn.toString().toLowerCase().endsWith(".jar") && Files.isRegularFile(p);
    }
}
