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
package org.graalvm.component.installer.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.graalvm.component.installer.SoftwareChannel;
import org.graalvm.component.installer.SoftwareChannelSource;

/**
 * Represents GraalVM edition. Each installation has an edition.
 * 
 * @author sdedic
 */
public class GraalEdition {
    private final String id;
    private final String displayName;
    private List<SoftwareChannelSource> softwareSources = new ArrayList<>();

    /**
     * Components from that specific edition.
     */
    private SoftwareChannel catalogProvider;

    public GraalEdition(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public SoftwareChannel getCatalogProvider() {
        return catalogProvider;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setSoftwareSources(List<SoftwareChannelSource> sources) {
        this.softwareSources = sources;
    }

    public void setCatalogProvider(SoftwareChannel catalogProvider) {
        this.catalogProvider = catalogProvider;
    }

    public List<SoftwareChannelSource> getSoftwareSources() {
        return softwareSources;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + Objects.hashCode(this.id);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final GraalEdition other = (GraalEdition) obj;
        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        if (id.equals(displayName)) {
            return "Graal " + id;
        } else {
            return "Graal " + id + "(" + displayName + ")";
        }
    }
}
