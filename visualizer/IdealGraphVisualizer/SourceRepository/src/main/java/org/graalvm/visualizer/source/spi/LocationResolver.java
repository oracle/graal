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

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.source.FileKey;
import org.openide.filesystems.FileObject;

import javax.swing.event.ChangeListener;

/**
 * LocationResolver is asked to resolve FileObject for unresolved file locations. The instance may be
 * used multiple times to resolve multiple unresolved location, possibly caching results to speed up
 * the bulk operation.s
 */
public interface LocationResolver {
    /**
     * Attempts to resolve the location. May return {@code null} if the source/file
     * could not be found. Must not interact with the user. Should be registered under MIME
     * type of the source.
     *
     * @param l location to resolve
     * @return FileObject which corresponds to the location, or {@code null}.
     */
    public FileObject resolve(FileKey l);

    /**
     * Registration of LocationResolvers. It can create instances, which will compute the file locations
     * and inform that system configuration changed in a way, which <b>may<b/> make some other
     * locations resolvable.
     */
    public interface Factory {
        public LocationResolver create(InputGraph src);

        public void addChangeListener(ChangeListener l);

        public void removeChangeListener(ChangeListener l);
    }
}
