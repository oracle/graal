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

import org.graalvm.shadowed.org.json.JSONArray;
import org.graalvm.shadowed.org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Response to 'gotoTargets' request.
 */
public class GotoTargetsResponse extends Response {

    GotoTargetsResponse(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public ResponseBody getBody() {
        return new ResponseBody(jsonData.getJSONObject("body"));
    }

    public GotoTargetsResponse setBody(ResponseBody body) {
        jsonData.put("body", body.jsonData);
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
        GotoTargetsResponse other = (GotoTargetsResponse) obj;
        if (!Objects.equals(this.getBody(), other.getBody())) {
            return false;
        }
        if (!Objects.equals(this.getType(), other.getType())) {
            return false;
        }
        if (this.getRequestSeq() != other.getRequestSeq()) {
            return false;
        }
        if (this.isSuccess() != other.isSuccess()) {
            return false;
        }
        if (!Objects.equals(this.getCommand(), other.getCommand())) {
            return false;
        }
        if (!Objects.equals(this.getMessage(), other.getMessage())) {
            return false;
        }
        if (this.getSeq() != other.getSeq()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 2;
        hash = 53 * hash + Objects.hashCode(this.getBody());
        hash = 53 * hash + Objects.hashCode(this.getType());
        hash = 53 * hash + Integer.hashCode(this.getRequestSeq());
        hash = 53 * hash + Boolean.hashCode(this.isSuccess());
        hash = 53 * hash + Objects.hashCode(this.getCommand());
        if (this.getMessage() != null) {
            hash = 53 * hash + Objects.hashCode(this.getMessage());
        }
        hash = 53 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static GotoTargetsResponse create(ResponseBody body, Integer requestSeq, Boolean success, String command, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("body", body.jsonData);
        json.put("type", "response");
        json.put("request_seq", requestSeq);
        json.put("success", success);
        json.put("command", command);
        json.put("seq", seq);
        return new GotoTargetsResponse(json);
    }

    public static class ResponseBody extends JSONBase {

        ResponseBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * The possible goto targets of the specified location.
         */
        public List<GotoTarget> getTargets() {
            final JSONArray json = jsonData.getJSONArray("targets");
            final List<GotoTarget> list = new ArrayList<>(json.length());
            for (int i = 0; i < json.length(); i++) {
                list.add(new GotoTarget(json.getJSONObject(i)));
            }
            return Collections.unmodifiableList(list);
        }

        public ResponseBody setTargets(List<GotoTarget> targets) {
            final JSONArray json = new JSONArray();
            for (GotoTarget gotoTarget : targets) {
                json.put(gotoTarget.jsonData);
            }
            jsonData.put("targets", json);
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
            ResponseBody other = (ResponseBody) obj;
            if (!Objects.equals(this.getTargets(), other.getTargets())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 2;
            hash = 67 * hash + Objects.hashCode(this.getTargets());
            return hash;
        }

        public static ResponseBody create(List<GotoTarget> targets) {
            final JSONObject json = new JSONObject();
            JSONArray targetsJsonArr = new JSONArray();
            for (GotoTarget gotoTarget : targets) {
                targetsJsonArr.put(gotoTarget.jsonData);
            }
            json.put("targets", targetsJsonArr);
            return new ResponseBody(json);
        }
    }
}
