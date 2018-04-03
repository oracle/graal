/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.meta;

import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;

/**
 * Determines if methods in a given class can be intrinsified.
 *
 * Only classes loaded by the extension class loader or any loader in loader hierarchy spanning the
 * JVMCI loader to the boot loader can be intrinsified.
 *
 * This version of the class must be used on JDK 8.
 */
public final class IntrinsificationPredicate {
    private final EconomicSet<ClassLoader> trustedLoaders;

    public IntrinsificationPredicate(CompilerConfiguration compilerConfiguration) {
        trustedLoaders = EconomicSet.create();
        trustedLoaders.add(getExtLoader());
        ClassLoader cl = compilerConfiguration.getClass().getClassLoader();
        while (cl != null) {
            trustedLoaders.add(cl);
            cl = cl.getParent();
        }
    }

    private static ClassLoader getExtLoader() {
        try {
            Object launcher = Class.forName("sun.misc.Launcher").getMethod("getLauncher").invoke(null);
            ClassLoader appLoader = (ClassLoader) launcher.getClass().getMethod("getClassLoader").invoke(launcher);
            ClassLoader extLoader = appLoader.getParent();
            assert extLoader.getClass().getName().equals("sun.misc.Launcher$ExtClassLoader") : extLoader;
            return extLoader;
        } catch (Exception e) {
            throw new GraalError(e);
        }
    }

    public boolean apply(Class<?> declaringClass) {
        ClassLoader cl = declaringClass.getClassLoader();
        return cl == null || trustedLoaders.contains(cl);
    }
}
