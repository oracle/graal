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

package org.graalvm.visualizer.source;

import org.netbeans.api.java.classpath.ClassPath;
import org.openide.util.Lookup;

import javax.swing.event.ChangeListener;
import java.net.URI;

/**
 * Abstraction which gives a location for sources. May correspond to a project,
 * or to a just collection of directories.
 */
public interface SourcesRoot {
    /**
     * Identifies the location. If possible, it should correspond to a file URL, if
     * the location is represented by a file (project, ...).
     *
     * @return URI identifier
     */
    public URI getURI();

    /**
     * Display name of the loation
     *
     * @return
     */
    public String getDisplayName();

    /**
     * Source path searchable to locate sources. The returned reference shall never
     * change; clients may attach listeners to it.
     *
     * @return
     */
    public ClassPath getSourcePath();

    /**
     * Provides additional services for this location
     *
     * @return
     */
    public Lookup getLookup();

    public void addChangeListener(ChangeListener l);

    public void removeChangeListener(ChangeListener l);

}
