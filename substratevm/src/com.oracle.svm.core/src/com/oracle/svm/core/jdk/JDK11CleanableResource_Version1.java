/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.io.File;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipFile;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

/**
 * JDK-8233234 changed the signature of a substituted constructor in
 * {@code java.util.ZipFile.CleanableResource} in some JDK 11 implementations. This predicate
 * matches one of the variations of the substituted constructor.
 */
public class JDK11CleanableResource_Version1 implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return JavaVersionUtil.JAVA_SPEC == 11 && hasConstructor(ZipFile.class, File.class, int.class);
    }

    /**
     * Determines if {@code java.util.zip.ZipFile$CleanableResource} has a constructor with a
     * signature matching {@code parameterTypes}.
     */
    static boolean hasConstructor(Class<?>... parameterTypes) {
        try {
            // Checkstyle: stop
            Class<?> c = Class.forName("java.util.zip.ZipFile$CleanableResource");
            c.getDeclaredConstructor(parameterTypes);
            return true;
            // Checkstyle: resume
        } catch (Exception e) {
            return false;
        }
    }
}
