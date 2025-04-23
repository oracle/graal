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
package org.graalvm.visualizer.filter;

import javax.swing.event.ChangeListener;

/**
 * Can create a filter, and update it. The filter can be just acquired,
 * or deliberately update from the underlying source. The update operation
 * can, in turn, either update the filter's definition, if possible, or can
 * create a new instance. The caller may force creation of new filter instance.
 * <p/>
 * If a new Filter instance is force-created, it must not be {@code equal} to
 * the previous one although the definition and effects are the same.
 *
 * @author sdedic
 */
public interface FilterProvider {
    /**
     * Returns a filter, but does not update it from
     * the underlying source.
     *
     * @return filter instance.
     */
    public Filter getFilter();

    /**
     * Updates the filter definitions, or create a new Filter instance. Unlike
     * {@link #getFilter), this method will update the filters, if they already exist,
     * with information from the source, e.g. filter script.
     *
     * @return updated FilterChain
     */
    public Filter createFilter(boolean createNew) throws IllegalStateException;

    public void addChangeListener(ChangeListener l);

    public void removeChangeListener(ChangeListener l);
}
