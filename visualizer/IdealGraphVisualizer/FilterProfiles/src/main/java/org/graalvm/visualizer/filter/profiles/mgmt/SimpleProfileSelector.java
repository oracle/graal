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

package org.graalvm.visualizer.filter.profiles.mgmt;

import org.graalvm.visualizer.filter.profiles.impl.ProfileAccessor;
import org.openide.filesystems.FileObject;

/**
 * Describes match for activating the filter profile. Currently only regexp on graph name and parent group name
 * are supported. Other properties cannot be used.
 * <p/>
 * To obtain the selector,
 *
 * @author sdedic
 */
public final class SimpleProfileSelector {
    private final FileObject selectorFile;
    private String graphType;

    private boolean graphNameRegexp;
    private boolean ownerNameRegexp;
    private String graphName = "";
    private String ownerName = "";
    private int order = 10;

    private boolean valid;

    SimpleProfileSelector(FileObject selectorFile) {
        this.selectorFile = selectorFile;
    }

    FileObject getSelectorFile() {
        return selectorFile;
    }

    void setValid(boolean valid) {
        this.valid = valid;
    }

    public boolean isValid() {
        return valid;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean isGraphNameRegexp() {
        return graphNameRegexp;
    }

    public void setGraphNameRegexp(boolean graphNameRegexp) {
        this.graphNameRegexp = graphNameRegexp;
    }

    public boolean isOwnerNameRegexp() {
        return ownerNameRegexp;
    }

    public void setOwnerNameRegexp(boolean ownerNameRegexp) {
        this.ownerNameRegexp = ownerNameRegexp;
    }

    public String getGraphType() {
        return graphType;
    }

    public void setGraphType(String graphType) {
        this.graphType = graphType;
    }

    public String getGraphName() {
        return graphName;
    }

    public void setGraphName(String graphName) {
        this.graphName = graphName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public static void init() {
    }

    static {
        ProfileAccessor.init(new GateImpl());
    }

    private static class GateImpl extends ProfileAccessor {
        @Override
        public SimpleProfileSelector createSimpleSelector(FileObject storage) {
            return new SimpleProfileSelector(storage);
        }

        @Override
        public void setSelectorValid(SimpleProfileSelector sps, boolean valid) {
            sps.setValid(valid);
        }

        @Override
        public FileObject getSelectorFile(SimpleProfileSelector sps) {
            return sps.getSelectorFile();
        }
    }
}
