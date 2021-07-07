/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.gds;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SoftwareChannel;
import org.graalvm.component.installer.SoftwareChannelSource;

/**
 * Creates a Software channel from GDS url. The format is:
 * <ul>
 * <li>gds://host/path/metadata.json
 * <li>gds:protocol://host/path/...
 * </ul>
 * If no nested protocol is given, HTTPS is used by default.
 * 
 * @author sdedic
 */
public class GraalChannelFactory implements SoftwareChannel.Factory {
    private static final String PROTOCOL_OLDS_PREFIX = "gds:"; // NOI18N
    private static final String PROTOCOL_HTTTPS_PREFIX = "https:"; // NOI18N

    private static final Map<String, String> OPTIONS = new HashMap<>();

    private MailStorage mailStorage;
    private Feedback feedback;

    static {
        OPTIONS.put(GdsCommands.OPTION_EMAIL_ADDRESS, "s");
        OPTIONS.put(GdsCommands.LONG_OPTION_EMAIL_ADDRESS, GdsCommands.OPTION_EMAIL_ADDRESS);
    }

    private MailStorage initMailStorage(CommandInput input, Feedback output) {
        if (mailStorage == null) {
            MailStorage ms = new MailStorage(input.getLocalRegistry(), output);
            ms.setStorage(input.getGraalHomePath());
            mailStorage = ms;
        }
        return mailStorage;
    }

    @Override
    public SoftwareChannel createChannel(SoftwareChannelSource source, CommandInput input, Feedback output) {
        feedback = output;
        String urlString = source.getLocationURL();
        if (!urlString.startsWith(PROTOCOL_OLDS_PREFIX)) {
            return null;
        }
        String rest = urlString.substring(PROTOCOL_OLDS_PREFIX.length());
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
        GraalChannel ch = new GraalChannel(input, output, input.getLocalRegistry());
        ch.setEdition(source.getParameter("edition"));
        ch.setReleasesIndexURL(u);
        ch.setMailStorage(initMailStorage(input, output));
        return ch;
    }

    @Override
    public Map<String, String> globalOptions() {
        return OPTIONS;
    }

    @Override
    public String globalOptionsHelp() {
        return feedback.l10n("GDS_ExtraOptionsHelp");
    }

    @Override
    public void init(CommandInput input, Feedback output) {
        feedback = output;
    }
}
