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

public class RegistrationParams extends JSONBase {

    RegistrationParams(JSONObject jsonData) {
        super(jsonData);
    }

    public List<Registration> getRegistrations() {
        final JSONArray json = jsonData.getJSONArray("registrations");
        final List<Registration> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new Registration(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public RegistrationParams setRegistrations(List<Registration> registrations) {
        final JSONArray json = new JSONArray();
        for (Registration registration : registrations) {
            json.put(registration.jsonData);
        }
        jsonData.put("registrations", json);
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
        RegistrationParams other = (RegistrationParams) obj;
        if (!Objects.equals(this.getRegistrations(), other.getRegistrations())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.getRegistrations());
        return hash;
    }

    public static RegistrationParams create(List<Registration> registrations) {
        final JSONObject json = new JSONObject();
        JSONArray registrationsJsonArr = new JSONArray();
        for (Registration registration : registrations) {
            registrationsJsonArr.put(registration.jsonData);
        }
        json.put("registrations", registrationsJsonArr);
        return new RegistrationParams(json);
    }
}
