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

/**
 * Thrown on a failed requirement / dependency of a Component.
 */
public class DependencyException extends InstallerStopException {
    private static final long serialVersionUID = 1L;
    private final String component;
    private final String version;
    private final String installedVersion;

    /**
     * Constructs a dependency exception.
     * 
     * @param component component / feature which failed the requirement
     * @param version version of the offending component/feature. {@code null} for conflicting
     *            component
     * @param installedVersion the actually installed (offending) version. {@code null}, if the
     *            component is not installed and should be
     * @param message display message
     */
    public DependencyException(String component, String version, String installedVersion, String message) {
        super(message);
        this.component = component;
        this.version = version;
        this.installedVersion = installedVersion;
    }

    public DependencyException(String component, String version, String message) {
        super(message);
        this.component = component;
        this.version = version;
        this.installedVersion = null;
    }

    public String getComponent() {
        return component;
    }

    public String getVersion() {
        return version;
    }

    public String getInstalledVersion() {
        return installedVersion;
    }

    /**
     * Represents a conflict between components. The same component is installed.
     */
    public static class Conflict extends DependencyException {
        private static final long serialVersionUID = 1L;

        public Conflict(String component, String version, String installedVersion, String message) {
            super(component, version, installedVersion, message);
        }
    }

    /**
     * Represents requirements mismatch. One component requires something, which is not satisfied by
     * the installation (not present or wrong version)
     */
    public static class Mismatch extends DependencyException {
        private static final long serialVersionUID = 1L;
        private final String capability;

        public Mismatch(String component, String capability, String version, String installedVersion, String message) {
            super(component, version, installedVersion, message);
            this.capability = capability;
        }

        public String getCapability() {
            return capability;
        }
    }
}
