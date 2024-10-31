/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.filter.profiles.mgmt;

import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.filter.profiles.FilterProfile;
import org.openide.filesystems.FileObject;

/**
 * @author sdedic
 */
public interface ProfileStorage {
    public static final String PROFILES_FOLDER = "IGV/FilterProfiles"; // NOI18N
    public static final String DEFAULT_PROFILE_FOLDER = "Filters"; // NOI18N

    /**
     * Returns storage FileObject for a filter.
     *
     * @param f filter
     * @return storage
     */
    public FileObject getFilterStorage(Filter f);

    /**
     * Provides access to profile's stoage folder.
     *
     * @param p the profile
     * @return profile's folder.
     */
    public FileObject getProfileFolder(FilterProfile p);

    /**
     * Returns a Profile that corresponds to the folder.
     *
     * @param folder
     * @return
     */
    public FilterProfile getProfile(FileObject folder);

    public Filter getFilter(FileObject filter);

    public FileObject getProfilesRoot();

    /**
     * Creates a filter instance for the given storage. Returns an existing
     * filter instance, if it already exists for the given file.
     *
     * @param stroage
     * @param parent
     * @return
     */
    public Filter createFilter(FileObject stroage, FilterProfile parent);
}
