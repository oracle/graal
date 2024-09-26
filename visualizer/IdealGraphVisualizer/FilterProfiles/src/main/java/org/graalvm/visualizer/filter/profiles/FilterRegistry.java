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

package org.graalvm.visualizer.filter.profiles;

import org.openide.util.Lookup;

import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * @author sdedic
 */
public interface FilterRegistry /* extends Lookup.Provider */ {
    public static final String PROP_PROFILES = "profiles"; // NOI18N

    /**
     * Returns the default profile. This profile always exist (although it may be empty).
     *
     * @return the default profile with all filters.
     */
    public FilterProfile getDefaultProfile();

    /**
     * Returns all profiles. The list includes the {@link #getDefaultProfile}. The list is
     * ordered according to profile priority (not exposed in the UI).
     *
     * @return all defined profiles.
     */
    public List<FilterProfile> getProfiles();

    /**
     * Registers a change listener for the profile service.
     *
     * @param l listener
     */
    public void addPropertyChangeListener(PropertyChangeListener l);

    /**
     * Unregisters a listener.
     *
     * @param l instance
     */
    public void removePropertyChangeListener(PropertyChangeListener l);

    public Lookup getLookup();

}
