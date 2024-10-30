/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.reports.causality.facts;

import java.net.URI;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.reports.causality.ReachabilityExport;

public final class ConfigurationFile extends Fact {
    public final URI uri;

    ConfigurationFile(URI uri) {
        this.uri = uri;
    }

    @Override
    public String toString() {
        String path;

        if (uri.getPath() != null) {
            path = uri.getPath();
        } else {
            path = uri.toString();
            if (path.startsWith("jar:file:")) {
                path = path.substring(9);
            }
        }

        return path + typeDescriptor().suffix;
    }

    @Override
    public boolean root() {
        return true;
    }

    @Override
    public ReachabilityExport.HierarchyNode getParent(ReachabilityExport export, AnalysisMetaAccess metaAccess) {
        return export.computeIfAbsent(uri);
    }

    @Override
    public FactKinds typeDescriptor() {
        return FactKinds.ConfigurationFile;
    }
}
