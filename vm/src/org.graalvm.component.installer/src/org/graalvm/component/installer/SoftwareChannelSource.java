/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Description of a component catalog.
 * 
 * @author sdedic
 */
public final class SoftwareChannelSource {
    /**
     * Access location, represented as String to allow custom protocols without URL protocol
     * handler.
     */
    private String locationURL;

    /**
     * Label for the catalog.
     */
    private String label;

    /**
     * Optional parametrs for the software channel.
     */
    private Map<String, String> params = new HashMap<>();

    public SoftwareChannelSource(String locationURL) throws MalformedURLException {
        this.locationURL = SystemUtils.parseURLParameters(locationURL, params);
    }

    public SoftwareChannelSource(String locationURL, String label) {
        this.locationURL = locationURL;
        this.label = label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setParameter(String param, String value) {
        params.put(param, value);
    }

    public String getLocationURL() {
        return locationURL;
    }

    public String getLabel() {
        return label;
    }

    public String getParameter(String key) {
        return params.get(key);
    }
}
