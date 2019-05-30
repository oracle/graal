/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;

@TargetClass(className = "java.lang.Module", onlyWith = JDK11OrLater.class)
public final class Target_java_lang_Module {
    @SuppressWarnings("static-method")
    @Substitute
    @TargetElement(name = "getResourceAsStream")
    public InputStream getResourceAsStream(String name) {
        List<byte[]> arr = Resources.get(name);
        return arr == null ? null : new ByteArrayInputStream(arr.get(0));
    }

    /*
     * All implementations of these stubs are completely empty no-op. This seems appropriate as
     * DynamicHub only references a singleton Module implementation anyhow, effectively neutering
     * the module system within JDK11.
     */

    @SuppressWarnings({"unused", "static-method"})
    @Substitute
    public boolean isReflectivelyExportedOrOpen(String pn, Target_java_lang_Module other, boolean open) {
        return true;
    }

    @SuppressWarnings({"unused", "static-method"})
    @Substitute
    private void implAddReads(Target_java_lang_Module other, boolean syncVM) {
    }

    @SuppressWarnings({"unused", "static-method"})
    @Substitute
    private void implAddExportsOrOpens(String pn,
                    Target_java_lang_Module other,
                    boolean open,
                    boolean syncVM) {
    }

    @SuppressWarnings({"unused", "static-method"})
    @Substitute
    void implAddUses(Class<?> service) {
    }

    @SuppressWarnings({"unused", "static-method"})
    @Substitute
    public boolean canUse(Class<?> service) {
        return true;
    }

    @SuppressWarnings({"unused", "static-method"})
    @Substitute
    public boolean canRead(Target_java_lang_Module other) {
        return true;
    }

    @Delete
    @TargetClass(className = "java.lang.Module", innerClass = "ReflectionData", onlyWith = JDK11OrLater.class)
    public static final class ReflectionData {
    }

}
