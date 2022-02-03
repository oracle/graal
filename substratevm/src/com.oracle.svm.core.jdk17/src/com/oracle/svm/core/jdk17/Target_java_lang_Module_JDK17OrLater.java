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
package com.oracle.svm.core.jdk17;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDK17OrLater;
import com.oracle.svm.core.jdk.ModuleUtil;

@SuppressWarnings("unused")
@TargetClass(value = java.lang.Module.class, onlyWith = JDK17OrLater.class)
public final class Target_java_lang_Module_JDK17OrLater {

    @Substitute
    private static void defineModule0(Module module, boolean isOpen, String version, String location, Object[] pns) {
        if (Arrays.stream(pns).anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Bad package name");
        }
        List<String> packages = Arrays.stream(pns).map(Object::toString).collect(Collectors.toUnmodifiableList());
        ModuleUtil.defineModule(module, isOpen, packages);
    }
}
