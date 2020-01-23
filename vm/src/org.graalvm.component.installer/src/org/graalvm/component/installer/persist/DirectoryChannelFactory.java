/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.Commands;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SoftwareChannel;
import org.graalvm.component.installer.SoftwareChannelSource;

/**
 * Creates {@link DirectoryCatalogProvider}s over a directory.
 * 
 * @author sdedic
 */
public class DirectoryChannelFactory implements SoftwareChannel.Factory {
    @Override
    public SoftwareChannel createChannel(SoftwareChannelSource source, CommandInput input, Feedback output) {
        String u = source.getLocationURL();
        if (!u.startsWith("file:")) { // NOI18N
            return null;
        }
        Feedback out2 = output.withBundle(DirectoryChannelFactory.class);
        try {
            Path p = new File(new URI(u)).toPath();
            if (!Files.isDirectory(p)) {
                return null;
            }
            DirectoryCatalogProvider dp = new DirectoryCatalogProvider(p, out2);
            dp.setVerifyJars(input.optValue(Commands.OPTION_NO_VERIFY_JARS) == null);
            // do not report errors for directory sources implied by commandline arguments.
            if (Boolean.FALSE.toString().equals(source.getParameter("reportErrors"))) { // NOI18N
                dp.setReportErrors(false);
            }
            return dp;
        } catch (URISyntaxException ex) {
            out2.error("ERR_DirectoryURLInvalid", ex, u, ex.getMessage());
            return null;
        }
    }

    @Override
    public void init(CommandInput input, Feedback output) {
    }
}
