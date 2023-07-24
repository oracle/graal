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

import org.graalvm.shadowed.org.json.JSONObject;

import java.util.Objects;

/**
 * Arguments for 'readMemory' request.
 */
public class ReadMemoryArguments extends JSONBase {

    ReadMemoryArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Memory reference to the base location from which data should be read.
     */
    public String getMemoryReference() {
        return jsonData.getString("memoryReference");
    }

    public ReadMemoryArguments setMemoryReference(String memoryReference) {
        jsonData.put("memoryReference", memoryReference);
        return this;
    }

    /**
     * Optional offset (in bytes) to be applied to the reference location before reading data. Can
     * be negative.
     */
    public Integer getOffset() {
        return jsonData.has("offset") ? jsonData.getInt("offset") : null;
    }

    public ReadMemoryArguments setOffset(Integer offset) {
        jsonData.putOpt("offset", offset);
        return this;
    }

    /**
     * Number of bytes to read at the specified location and offset.
     */
    public int getCount() {
        return jsonData.getInt("count");
    }

    public ReadMemoryArguments setCount(int count) {
        jsonData.put("count", count);
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
        ReadMemoryArguments other = (ReadMemoryArguments) obj;
        if (!Objects.equals(this.getMemoryReference(), other.getMemoryReference())) {
            return false;
        }
        if (!Objects.equals(this.getOffset(), other.getOffset())) {
            return false;
        }
        if (this.getCount() != other.getCount()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 59 * hash + Objects.hashCode(this.getMemoryReference());
        if (this.getOffset() != null) {
            hash = 59 * hash + Integer.hashCode(this.getOffset());
        }
        hash = 59 * hash + Integer.hashCode(this.getCount());
        return hash;
    }

    public static ReadMemoryArguments create(String memoryReference, Integer count) {
        final JSONObject json = new JSONObject();
        json.put("memoryReference", memoryReference);
        json.put("count", count);
        return new ReadMemoryArguments(json);
    }
}
