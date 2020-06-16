/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.server.types;

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ShowMessageRequestParams extends JSONBase {

    ShowMessageRequestParams(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The message type. See {@link MessageType}
     */
    public MessageType getType() {
        return MessageType.get(jsonData.getInt("type"));
    }

    public ShowMessageRequestParams setType(MessageType type) {
        jsonData.put("type", type.getIntValue());
        return this;
    }

    /**
     * The actual message.
     */
    public String getMessage() {
        return jsonData.getString("message");
    }

    public ShowMessageRequestParams setMessage(String message) {
        jsonData.put("message", message);
        return this;
    }

    /**
     * The message action items to present.
     */
    public List<MessageActionItem> getActions() {
        final JSONArray json = jsonData.optJSONArray("actions");
        if (json == null) {
            return null;
        }
        final List<MessageActionItem> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new MessageActionItem(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public ShowMessageRequestParams setActions(List<MessageActionItem> actions) {
        if (actions != null) {
            final JSONArray json = new JSONArray();
            for (MessageActionItem messageActionItem : actions) {
                json.put(messageActionItem.jsonData);
            }
            jsonData.put("actions", json);
        }
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
        ShowMessageRequestParams other = (ShowMessageRequestParams) obj;
        if (this.getType() != other.getType()) {
            return false;
        }
        if (!Objects.equals(this.getMessage(), other.getMessage())) {
            return false;
        }
        if (!Objects.equals(this.getActions(), other.getActions())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 89 * hash + Objects.hashCode(this.getType());
        hash = 89 * hash + Objects.hashCode(this.getMessage());
        if (this.getActions() != null) {
            hash = 89 * hash + Objects.hashCode(this.getActions());
        }
        return hash;
    }

    public static ShowMessageRequestParams create(MessageType type, String message) {
        final JSONObject json = new JSONObject();
        json.put("type", type.getIntValue());
        json.put("message", message);
        return new ShowMessageRequestParams(json);
    }
}
