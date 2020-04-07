/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer;

/**
 * Constants related to component packages / bundles and their structure.
 */
public class BundleConstants {
    public static final String BUNDLE_ID = "Bundle-Symbolic-Name";  // NOI18N
    public static final String BUNDLE_NAME = "Bundle-Name";    // NOI18N
    public static final String BUNDLE_VERSION = "Bundle-Version"; // NOI18N
    public static final String BUNDLE_REQUIRED = "Bundle-RequireCapability"; // NOI18N
    public static final String BUNDLE_PROVIDED = "Bundle-ProvideCapability"; // NOI18N
    public static final String BUNDLE_DEPENDENCY = "Require-Bundle"; // NOI18N
    public static final String GRAALVM_CAPABILITY = "org.graalvm"; // NOI18N
    public static final String BUNDLE_POLYGLOT_PART = "x-GraalVM-Polyglot-Part"; // NOI18N
    public static final String BUNDLE_LICENSE_TYPE = "x-GraalVM-License-Type"; // NOI18N
    public static final String BUNDLE_LICENSE_PATH = "x-GraalVM-License-Path"; // NOI18N

    /**
     * In manifests, can specify the serial/hashtag for a component. Used mainly in installed
     * component storage, copied from download hash or ComponentInfo.
     */
    public static final String BUNDLE_SERIAL = "x-GraalVM-Serial"; // NOI18N

    /**
     * Extended optional attribute; marks directories, which should be removed completely without
     * checking for emptiness.
     */
    public static final String BUNDLE_WORKDIRS = "x-GraalVM-Working-Directories"; // NOI18N

    public static final String META_INF_PATH = "META-INF/"; // NOI18N
    public static final String META_INF_PERMISSIONS_PATH = "META-INF/permissions"; // NOI18N
    public static final String META_INF_SYMLINKS_PATH = "META-INF/symlinks"; // NOI18N

    public static final String GRAAL_COMPONENT_ID = GRAALVM_CAPABILITY; // NOI18N

    /**
     * Post-install message. In the future more x-GraalVM-Message might appear
     */
    public static final String BUNDLE_MESSAGE_POSTINST = "x-GraalVM-Message-PostInst"; // NOI18N

    /**
     * Version key in the release file.
     */
    public static final String GRAAL_VERSION = "graalvm_version"; // NOI18N

    /**
     * Component distribution tag. Can be one of:
     * <ul>
     * <li>bundled - installed by a base package. Cannot be removed.
     * <li>optional - installed as an add-on, can be removed. The default.
     * </ul>
     * Further values may be added in the future.
     * 
     * @since 20.0
     */
    public static final String BUNDLE_COMPONENT_DISTRIBUTION = "x-GraalVM-Component-Distribution"; // NOI18N

}
