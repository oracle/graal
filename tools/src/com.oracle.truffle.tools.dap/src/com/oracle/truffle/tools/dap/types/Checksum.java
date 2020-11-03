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
package com.oracle.truffle.tools.dap.types;

import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.Objects;

/**
 * The checksum of an item calculated by the specified algorithm.
 */
public class Checksum extends JSONBase {

    Checksum(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The algorithm used to calculate this checksum.
     */
    public String getAlgorithm() {
        return jsonData.getString("algorithm");
    }

    public Checksum setAlgorithm(String algorithm) {
        jsonData.put("algorithm", algorithm);
        return this;
    }

    /**
     * Value of the checksum.
     */
    public String getChecksum() {
        return jsonData.getString("checksum");
    }

    public Checksum setChecksum(String checksum) {
        jsonData.put("checksum", checksum);
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }
        Checksum other = (Checksum) obj;
        if (!Objects.equals(this.getAlgorithm(), other.getAlgorithm())) {
            return false;
        }
        if (!Objects.equals(this.getChecksum(), other.getChecksum())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 2;
        hash = 29 * hash + Objects.hashCode(this.getAlgorithm());
        hash = 29 * hash + Objects.hashCode(this.getChecksum());
        return hash;
    }

    public static Checksum create(String algorithm, String checksum) {
        final JSONObject json = new JSONObject();
        json.put("algorithm", algorithm);
        json.put("checksum", checksum);
        return new Checksum(json);
    }
}
