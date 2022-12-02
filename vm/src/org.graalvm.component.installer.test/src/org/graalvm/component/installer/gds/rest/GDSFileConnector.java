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

import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.remote.FileDownloader;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 *
 * @author odouda
 */
public class GDSFileConnector extends GDSRESTConnector {
    public GDSFileConnector(String baseURL, Feedback feedback, String productId, Version gvmVersion) {
        super(baseURL, feedback, productId, gvmVersion);
    }

    @Override
    protected Map<String, List<String>> getParams() {
        return Collections.emptyMap();
    }

    @Override
    protected FileDownloader obtain(String endpoint) {
        return super.obtain("");
    }

    static final String newToken = "newMockToken";
    String[] verEmInps;

    @Override
    public String sendVerificationEmail(String email, String licAddr, String oldToken) {
        verEmInps = new String[]{email, licAddr, oldToken};
        return oldToken == null ? newToken : oldToken;
    }

}
