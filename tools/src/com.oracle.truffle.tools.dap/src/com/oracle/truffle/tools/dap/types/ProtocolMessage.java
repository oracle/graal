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
 * Base class of requests, responses, and events.
 */
public class ProtocolMessage extends JSONBase {

    ProtocolMessage(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Sequence number (also known as message ID). For protocol messages of type 'request' this ID
     * can be used to cancel the request.
     */
    public int getSeq() {
        return jsonData.getInt("seq");
    }

    public ProtocolMessage setSeq(int seq) {
        jsonData.put("seq", seq);
        return this;
    }

    /**
     * Message type. Values: 'request', 'response', 'event', etc.
     */
    public String getType() {
        return jsonData.getString("type");
    }

    public ProtocolMessage setType(String type) {
        jsonData.put("type", type);
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
        ProtocolMessage other = (ProtocolMessage) obj;
        if (this.getSeq() != other.getSeq()) {
            return false;
        }
        if (!Objects.equals(this.getType(), other.getType())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 71 * hash + Integer.hashCode(this.getSeq());
        hash = 71 * hash + Objects.hashCode(this.getType());
        return hash;
    }

    public static ProtocolMessage create(Integer seq, String type) {
        final JSONObject json = new JSONObject();
        json.put("seq", seq);
        json.put("type", type);
        return new ProtocolMessage(json);
    }
}
