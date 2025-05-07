/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.sbom;

import java.util.Set;

/**
 * Defines the available values for the SBOM feature. See {@link SBOMFeature.Options} for more
 * information about the individual values.
 * <p>
 * Note: the only supported SBOM value for GraalVM Community Edition is
 * {@link SBOMValues#disableSBOM}.
 */
public class SBOMValues {
    public static final String cyclonedxFormat = "cyclonedx";
    /**
     * The SBOM feature is disabled by passing '--enable-sbom=false'.
     */
    public static final String disableSBOM = "false";
    public static final String strict = "strict";
    /**
     * If set, then the classes, fields, constructors, and methods that are used in the image are
     * collected and included in the SBOM.
     */
    public static final String classLevel = "class-level";

    public static final class StorageOption {
        public static final String embed = "embed";
        public static final String export = "export";
        public static final String classpath = "classpath";
        public static final Set<String> supportedStorageValues = Set.of(embed, export, classpath);
    }
}
