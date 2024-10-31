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

import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.filter.FilterSequence;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.List;

/**
 * Provides management operations for filters in profiles.
 *
 * @author sdedic
 */
public interface FilterProfile {
    public static final String PROP_NAME = "name";
    public static final String PROP_FILTERS = "profileFilters";
    public static final String PROP_ENABLED_FILTERS = "enabledFilters";
    public static final String PROP_FILTER_ORDER = "filterOrder";

    public String getName();

    public void setName(String profileName) throws IOException;

    public void moveDown(Filter f) throws IOException;

    public void setEnabled(Filter f, boolean status) throws IOException;

    public void moveUp(Filter f) throws IOException;

    public Filter addSharedFilter(Filter f) throws IOException;

    public List<Filter> getProfileFilters();

    public FilterSequence getSelectedFilters();

    public FilterSequence getAllFilters();

    public void addPropertyChangeListener(PropertyChangeListener l);

    public void removePropertyChangeListener(PropertyChangeListener l);


}
