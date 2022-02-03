/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.resources.ResourceStorageEntry;

@SuppressWarnings("unused")
@TargetClass(value = java.lang.Module.class)
final class Target_java_lang_Module {

    @Alias //
    private String name;

    @SuppressWarnings("static-method")
    @Substitute
    private InputStream getResourceAsStream(String resourceName) {
        ResourceStorageEntry res = Resources.get(name, resourceName);
        return res == null ? null : new ByteArrayInputStream(res.getData().get(0));
    }

    @Substitute //
    @TargetElement(onlyWith = JDK11OrEarlier.class)
    private static void defineModule0(Module module, boolean isOpen, String version, String location, String[] pns) {
        ModuleUtil.defineModule(module, isOpen, Arrays.asList(pns));
    }

    @Substitute
    private static void addReads0(Module from, Module to) {
        if (Objects.isNull(from)) {
            throw new NullPointerException("from_module is null");
        }
    }

    @Substitute
    private static void addExports0(Module from, String pn, Module to) {
        if (Objects.isNull(to)) {
            throw new NullPointerException("to_module is null");
        }

        ModuleUtil.checkFromModuleAndPackageNullability(from, pn);
        ModuleUtil.checkIsPackageContainedInModule(pn, from);
    }

    @Substitute
    private static void addExportsToAll0(Module from, String pn) {
        ModuleUtil.checkFromModuleAndPackageNullability(from, pn);
        ModuleUtil.checkIsPackageContainedInModule(pn, from);
    }

    @Substitute
    private static void addExportsToAllUnnamed0(Module from, String pn) {
        ModuleUtil.checkFromModuleAndPackageNullability(from, pn);
        if (from.isNamed()) {
            ModuleUtil.checkIsPackageContainedInModule(pn, from);
        }
    }
}
