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

package org.graalvm.visualizer.view.impl;

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.view.api.TimelineModel;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.Mode;
import org.openide.windows.ModeSelector;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import java.util.Objects;

/**
 * Selects the default mode for graphs. It seems appropriate to have call graph
 * as a "supplemental" window for the main graal graph.
 *
 * @author sdedic
 */
@ServiceProvider(service = ModeSelector.class, position = 1000)
public class GraphTypeModeSelector implements ModeSelector {
    @Override
    public Mode selectModeForOpen(TopComponent tc, Mode mode) {
        if (tc.getLookup().lookup(InputGraph.class) == null) {
            return null;
        }
        TimelineModel mdl = tc.getLookup().lookup(TimelineModel.class);
        if (mdl == null) {
            // maybe some graph, but we don't care, since it does not
            // contain a timeline
            return null;
        }

        String type = mdl.getPrimaryType();
        if (type != null) {
            FileObject fo = FileUtil.getConfigFile("IGV/GraphTypes/" + type); // NOI18N
            if (fo != null) {
                Object o = fo.getAttribute("defaultMode"); // NOI18N
                if (o instanceof String) {
                    Mode m = WindowManager.getDefault().findMode((String) o);
                    TopComponent[] openedComps = WindowManager.getDefault().getOpenedTopComponents(m);
                    if (openedComps.length > 0) {
                        return m;
                    }

                    for (Mode existing : WindowManager.getDefault().getModes()) {
                        openedComps = WindowManager.getDefault().getOpenedTopComponents(m);
                        for (TopComponent exC : openedComps) {
                            TimelineModel time = exC.getLookup().lookup(TimelineModel.class);
                            if (time != null &&
                                    Objects.equals(time.getPrimaryType(), mdl.getPrimaryType())) {
                                return existing;
                            }
                        }
                    }

                    return m;
                }
            }
        }
        return null;
    }
}
