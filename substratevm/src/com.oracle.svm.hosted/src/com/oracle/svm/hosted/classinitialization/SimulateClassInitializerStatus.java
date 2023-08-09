/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.classinitialization;

/**
 * The current status of a {@link SimulateClassInitializerClusterMember}.
 * 
 * See {@link SimulateClassInitializerSupport} for an overview of class initializer simulation.
 */
enum SimulateClassInitializerStatus {
    /** Dependencies of the cluster member are still being collected. */
    COLLECTING_DEPENDENCIES(false),
    /**
     * All dependencies of the cluster member itself have been collected, but cyclic dependencies
     * are still being analyzed. So no analysis decision can be made yet for the cluster member.
     */
    INIT_CANDIDATE(false),
    /**
     * Simulation of the cluster member was successful, and the result was published into
     * {@link SimulateClassInitializerSupport#analyzedClasses}.
     */
    PUBLISHED_AS_INITIALIZED(true),
    /**
     * Simulation of the cluster member was not successful, and the result was published into
     * {@link SimulateClassInitializerSupport#analyzedClasses}.
     */
    PUBLISHED_AS_NOT_INITIALIZED(true);

    final boolean published;

    SimulateClassInitializerStatus(boolean published) {
        this.published = published;
    }
}
