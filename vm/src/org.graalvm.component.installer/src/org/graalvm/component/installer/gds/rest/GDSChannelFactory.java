/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.component.installer.gds.rest;

import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SoftwareChannel;
import org.graalvm.component.installer.SoftwareChannelSource;
import java.util.HashMap;
import java.util.Map;
import org.graalvm.component.installer.gds.GdsCommands;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Creates a Software channel for GDS REST API. The URL is:
 * <ul>
 * <li>rest://path
 * <li>rest:protocol://path
 * </ul>
 *
 * @author odouda
 */
public class GDSChannelFactory implements SoftwareChannel.Factory {
    private static final String PROTOCOL_GDS_REST_PREFIX = "rest:"; // NOI18N
    private static final String PROTOCOL_HTTTPS_PREFIX = "https:"; // NOI18N

    private static final Map<String, String> OPTIONS = new HashMap<>();

    private Feedback feedback;

    static {
        OPTIONS.put(GdsCommands.OPTION_GDS_CONFIG, "s");
        OPTIONS.put(GdsCommands.LONG_OPTION_GDS_CONFIG, GdsCommands.OPTION_GDS_CONFIG);
        OPTIONS.put(GdsCommands.OPTION_SHOW_TOKEN, "");
        OPTIONS.put(GdsCommands.LONG_OPTION_SHOW_TOKEN, GdsCommands.OPTION_SHOW_TOKEN);
    }

    @Override
    public SoftwareChannel createChannel(SoftwareChannelSource source, CommandInput input, Feedback output) {
        feedback = output;
        String urlString = source.getLocationURL();
        if (!urlString.startsWith(PROTOCOL_GDS_REST_PREFIX)) {
            return null;
        }
        String rest = urlString.substring(PROTOCOL_GDS_REST_PREFIX.length());
        URL u;
        try {
            if (rest.startsWith("http") || rest.startsWith("file:") || rest.startsWith("test:")) {
                u = new URL(rest);
            } else {
                u = new URL(PROTOCOL_HTTTPS_PREFIX + rest);
            }
        } catch (MalformedURLException ex) {
            throw output.failure("YUM_InvalidLocation", ex, urlString, ex.getLocalizedMessage());
        }
        GDSChannel ch = new GDSChannel(input, output, input.getLocalRegistry());
        ch.setIndexURL(u);
        ch.setEdition(source.getParameter("edition"));
        return ch;
    }

    @Override
    public Map<String, String> globalOptions() {
        return OPTIONS;
    }

    @Override
    public String globalOptionsHelp() {
        return feedback.l10n("GDS_REST_ExtraOptionsHelp");
    }

    @Override
    public void init(CommandInput input, Feedback output) {
        feedback = output.withBundle(this.getClass());
    }
}
