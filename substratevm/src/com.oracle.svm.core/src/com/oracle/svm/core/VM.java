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
package com.oracle.svm.core;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.VMError;

public final class VM {

    public final String version;
    public final String vendor;
    public final String vendorUrl;
    public final String runtimeName;

    @Platforms(Platform.HOSTED_ONLY.class)
    public VM(String config) {
        String versionStr = System.getProperty("org.graalvm.version");
        VMError.guarantee(versionStr != null);
        versionStr = "GraalVM " + versionStr;
        versionStr += " Java " + Runtime.version().toString();
        versionStr += " " + config;
        version = versionStr;
        vendor = System.getProperty("org.graalvm.vendor", "Oracle Corporation");
        vendorUrl = System.getProperty("org.graalvm.vendorurl", "https://www.graalvm.org/");
        runtimeName = System.getProperty("java.runtime.name", "Unknown Runtime Environment");
    }
}
