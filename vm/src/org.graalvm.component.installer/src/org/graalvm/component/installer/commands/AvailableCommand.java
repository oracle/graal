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
package org.graalvm.component.installer.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.Commands;
import org.graalvm.component.installer.ComponentCollection;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.ComponentInfo;

/**
 *
 * @author sdedic
 */
public class AvailableCommand extends ListInstalledCommand {

    private Version.Match vmatch;

    @Override
    public Map<String, String> supportedOptions() {
        Map<String, String> opts = new HashMap<>(super.supportedOptions());
        opts.put(Commands.OPTION_ALL, "");
        opts.put(Commands.LONG_OPTION_ALL, Commands.OPTION_ALL);
        opts.put(Commands.OPTION_VERSION, "s");
        opts.put(Commands.LONG_OPTION_VERSION, Commands.OPTION_VERSION);

        opts.put(Commands.OPTION_CATALOG, "X");
        opts.put(Commands.OPTION_FOREIGN_CATALOG, "s");
        opts.put(Commands.LONG_OPTION_FOREIGN_CATALOG, Commands.OPTION_FOREIGN_CATALOG);
        return opts;
    }

    @Override
    protected ComponentCollection initRegistry() {
        super.initRegistry();
        return input.getRegistry();
    }

    @Override
    public void init(CommandInput commandInput, Feedback feedBack) {
        super.init(commandInput, feedBack);
        String v = commandInput.optValue(Commands.OPTION_VERSION);
        if (v != null) {
            vmatch = Version.versionFilter(v);
        }
    }

    @Override
    public int execute() throws IOException {
        if (input.optValue(Commands.OPTION_HELP) != null) {
            feedback.output("AVAILABLE_Help");
            return 0;
        }
        return super.execute();
    }

    @Override
    protected List<ComponentInfo> filterDisplayedVersions(String id, Collection<ComponentInfo> infos) {
        if (input.optValue(Commands.OPTION_ALL) != null) {
            return super.filterDisplayedVersions(id, infos);
        }
        Set<Version> seen = new HashSet<>();
        Collection<ComponentInfo> filtered = new ArrayList<>();
        for (ComponentInfo ci : infos) {
            if (seen.add(ci.getVersion().installVersion())) {
                filtered.add(ci);
            }
        }
        return super.filterDisplayedVersions(id, filtered);
    }

    @Override
    protected String acceptExpression(String expr) {
        if (vmatch != null) {
            return super.acceptExpression(expr);
        }
        Version.Match vm = Version.versionFilter(expr);
        if (vm == null) {
            vmatch = getRegistry().getGraalVersion().match(Version.Match.Type.INSTALLABLE);
            return expr;
        } else {
            vmatch = vm;
            // consume
            return null;
        }
    }

    @Override
    protected Version.Match getVersionFilter() {
        return vmatch == null ? super.getVersionFilter() : vmatch;
    }

}
