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
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.graalvm.component.installer.Archive;
import org.graalvm.component.installer.InstallerStopException;
import org.graalvm.component.installer.SuppressFBWarnings;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.remote.FileDownloader;

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

    default FileDownloader configureRelatedDownloader(FileDownloader dn) {
        return dn;
    }

    @SuppressWarnings("unused")
    default Date isLicenseAccepted(ComponentInfo info, String licenseID) {
        return null;
    }

    /**
     * A provider-dependent way of accepting a license. If the Loader returns {@code true}, it has
     * to record the accepted license on its own. Returning {@code false} will suppress the default
     * recording. {@code null} means the default recording should be used.
     * <p/>
     * The default implementation returns {@code null}.
     * 
     * @param info Component for which the license is being accepted.
     * @param licenseID ID of the license.
     * @param licenseText The text of the license.
     * @param d date accepted
     * @return recording decision.
     * @throws IOException
     */
    @SuppressFBWarnings(value = "NP_BOOLEAN_RETURN_NULL", justification = "The return value is a tri-state, indicates a success, denial, or default.")
    default Boolean recordLicenseAccepted(ComponentInfo info, String licenseID, String licenseText, Date d) throws IOException {
        return null;
    }
}
