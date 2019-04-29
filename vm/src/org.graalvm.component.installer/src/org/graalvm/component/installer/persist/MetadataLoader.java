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
package org.graalvm.component.installer.persist;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.graalvm.component.installer.Archive;
import org.graalvm.component.installer.InstallerStopException;
import org.graalvm.component.installer.model.ComponentInfo;

/**
 * Abstraction that loads metadata for a given component.
 * 
 * @author sdedic
 */
public interface MetadataLoader extends Closeable {

    ComponentInfo getComponentInfo();

    List<InstallerStopException> getErrors();

    Archive getArchive() throws IOException;

    /**
     * License name/type. Not the actual content, but general name, like "Oracle OTN license",
     * "GPLv2" or so.
     * 
     * @return license type or {@code null}
     */
    String getLicenseType();

    /**
     * License ID. Usually a digest of the license file contents.
     * 
     * @return license ID
     */
    String getLicenseID();

    /**
     * Path to the license file. Should be to iterate through {@link #getArchive} to obtain the
     * license contents.
     * 
     * @return path to the license or {@code null}
     */
    String getLicensePath();

    MetadataLoader infoOnly(boolean only);

    boolean isNoVerifySymlinks();

    void loadPaths() throws IOException;

    Map<String, String> loadPermissions() throws IOException;

    Map<String, String> loadSymlinks() throws IOException;

    void setNoVerifySymlinks(boolean noVerifySymlinks);

    /**
     * Completes the metadata. The entire file may be loaded in order to load all the metadata.
     * 
     * @throws IOException if the metadata load fails.
     * @return ComponentInfo with completed metadata
     */
    ComponentInfo completeMetadata() throws IOException;
}
