/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

/**
 * Represents public information about an instrument.
 *
 * @since 0.26
 */
public final class InstrumentInfo {

    private final Object vmObject;
    private final String id;
    private final String name;
    private final String version;

    InstrumentInfo(Object vmObject, String id, String name, String version) {
        this.vmObject = vmObject;
        this.id = id;
        this.name = name;
        this.version = version;
    }

    /**
     * Gets the id clients can use to acquire this instrument.
     *
     * @return this instrument's unique id
     * @since 0.26
     */
    public String getId() {
        return id;
    }

    /**
     * Gets a human readable name of this instrument.
     *
     * @return this instrument's user-friendly name
     * @since 0.26
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the version of this instrument.
     *
     * @return this instrument's version
     * @since 0.26
     */
    public String getVersion() {
        return version;
    }

    Object getVmObject() {
        return vmObject;
    }

    /**
     * @since 0.26
     */
    @Override
    public String toString() {
        return "InstrumentInfo [id=" + id + ", name=" + name + ", version=" + version + "]";
    }

}
