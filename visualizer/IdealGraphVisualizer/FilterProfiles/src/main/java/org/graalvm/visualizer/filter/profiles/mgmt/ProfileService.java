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
import org.graalvm.visualizer.filter.profiles.FilterRegistry;
import org.netbeans.api.annotations.common.NonNull;

import java.io.IOException;
import java.util.Set;

/**
 * Access to profile management.
 */
public interface ProfileService extends FilterRegistry {
    public static final String PROP_SELECTED_PROFILE = "selectedProfile"; // NOI18N

    /**
     * Sets the profile to be the current one. The current profile serves for
     * various UI parts to get default profile context, in the case they do not have
     * one. The selected profile should be updated whenever focus shifts to another
     * view or tool that uses a specific profile internally.
     *
     * @param p profile, not null
     */
    public void setSelectedProfile(@NonNull FilterProfile p);

    /**
     * Retrieves the current profile. Note that the current profile may be changed frequently
     * as focus changes to a tool that is bound to a specific profile. Changes can be
     * tracked by listening to {@link #PROP_SELECTED_PROFILE} property change.
     * <p/>
     * If no profile is 'current', the default profile will be returned.
     *
     * @return current profile instance
     */
    @NonNull
    public FilterProfile getSelectedProfile();

    /**
     * Creates a new profile, in a new folder. Copies settings of an existing
     * profile.
     *
     * @param name new profile's name
     * @return the created profile
     */
    public FilterProfile createProfile(String name, FilterProfile basedOn) throws IOException;

    /**
     * Deletes a filter profile. The default profile cannot be deleted ({@link IOException} is thrown).
     * All filters in the profile will be <b>deleted</b>.
     *
     * @param p profile to delete
     * @throws IOException if an error occurs.
     */
    public void deleteProfile(FilterProfile p) throws IOException;

    /**
     * Renames a profile. The new name cannot collide with other profile's folder storage name.
     *
     * @param profile the profile to rename
     * @param newName new name for the profile.
     * @throws IOException
     */
    public void renameProfile(FilterProfile profile, String newName) throws IOException;

    /**
     * Deletes a filter. It is not possible to delete a filter, which is still
     * used in other profiles.
     * <p/>
     * Note: the change in profile may not be visible immediately; the profile
     * may refresh asynchronously, but will fire an event when it discards
     * the deleted filter.
     *
     * @param f filter to delete
     * @throws IOException if the filter is used, or I/O exception occurred during deletion.
     */
    public void deleteFilter(Filter f) throws IOException;

    /**
     * Finds the profiles that use the filter.
     *
     * @param filter filter to search for
     * @return set of profiles that use the filter
     */
    public Set<FilterProfile> findLocations(Filter filter);

    /**
     * Delete the filter from all profiles. If the filter is referenced from
     * other profiles, it will delete all the references. The filter itself
     * will be deleted last.
     * <p/>
     * Note: the change in profiles may not be visible immediately; the profile
     * may refresh asynchronously, but will fire an event when they discard
     * the deleted filter.
     *
     * @param f the filter to delete
     * @throws IOException if an error occurs.
     */
    public void deleteFromAllProfiles(Filter f) throws IOException;

    /**
     * Finds linked filter in the default profile.
     *
     * @param fromProfile the initial filter.
     * @return Filter instance from the default profile, or {@code null}, if the
     * filter is not a link.
     */
    public Filter findDefaultFilter(Filter fromProfile);

    /**
     * Renames a filter, optionally with the linked shared one. If `withShared`
     *
     * @param f       filter to rename
     * @param newName the desired name
     */
    public void renameFilter(Filter f, String newName) throws IOException;
}
