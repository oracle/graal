/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.descriptors.Names;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.runtime.JavaVersion;

public interface LanguageAccess {
    EspressoLanguage getLanguage();

    default Names getNames() {
        return getLanguage().getNames();
    }

    default Types getTypes() {
        return getLanguage().getTypes();
    }

    default Signatures getSignatures() {
        return getLanguage().getSignatures();
    }

    default JavaVersion getJavaVersion() {
        return getLanguage().getJavaVersion();
    }

    default boolean isPreviewEnabled() {
        return getLanguage().isPreviewEnabled();
    }

    default EspressoOptions.SpecComplianceMode getSpecComplianceMode() {
        return getLanguage().getSpecComplianceMode();
    }
}
