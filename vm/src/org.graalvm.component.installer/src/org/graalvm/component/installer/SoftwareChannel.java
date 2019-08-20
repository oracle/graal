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
package org.graalvm.component.installer;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentStorage;
import org.graalvm.component.installer.remote.FileDownloader;

/**
 * An abstraction of software delivery channel. The channel provides a Registry of available
 * components. It can augment or change the download process and it can interpret the downloaded
 * files to support different file formats.
 * <p/>
 * 
 * @author sdedic
 */
public interface SoftwareChannel {
    /**
     * Loads and provides access to the component registry.
     * 
     * @return registry instance ComponentCollection getRegistry();
     */

    ComponentStorage getStorage() throws IOException;

    /**
     * Configures the downloader with specific options. The downloader may be even replaced with a
     * different instance.
     * 
     * @param dn the downloader to configure
     * @return the downloader instance.
     */
    FileDownloader configureDownloader(ComponentInfo info, FileDownloader dn);

    /*
     * Checks if the Component can be installed by native tools. In that case, the installer will
     * refuse to operate and displays an appropriate error message
     * 
     * @param info
     * 
     * @return boolean isNativeInstallable(ComponentInfo info);
     */

    interface Factory {
        /**
         * True, if the channel is willing to handle the URL. URL is passed as a String so that
         * custom protocols may be used without registering an URLStreamHandlerFactory.
         * 
         * @param source the definition of the channel including label
         * @param input input parameters
         * @param output output interface
         * @return true, if the channel is willing to work with the URL
         */
        SoftwareChannel createChannel(SoftwareChannelSource source, CommandInput input, Feedback output);

        /**
         * Adds options to the set of global options. Global options allow to accept specific
         * options from commandline, which would otherwise cause an error (unknown option).
         * 
         * @return global options to add.
         */
        default Map<String, String> globalOptions() {
            return Collections.emptyMap();
        }

        /**
         * Provides help for the injected global options.
         * 
         * @return String to append to the displayed help, or {@code null} for empty message.
         */
        default String globalOptionsHelp() {
            return null;
        }

        void init(CommandInput input, Feedback output);
    }
}
