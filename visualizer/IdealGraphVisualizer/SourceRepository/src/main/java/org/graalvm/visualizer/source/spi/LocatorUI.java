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

package org.graalvm.visualizer.source.spi;

import org.graalvm.visualizer.source.Location;

import java.awt.Image;
import java.util.concurrent.Future;

/**
 * Allows to register various methods of resolving locations - configures the
 * application to lookup source for the location.
 * <p/>
 * If the configuration succeeds, it is expected that {@link LocationResolver.Factory}
 * fires events to alert system that
 */
public interface LocatorUI {
    public String getDisplayName();

    public Image getIcon();

    public boolean accepts(Location l);

    /**
     * Resolves the location, returning success or failure of the configuration.
     *
     * @param l
     * @return
     */
    public Future<Boolean> resolve(Location l);
}
