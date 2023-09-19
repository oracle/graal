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
 * Arguments for 'terminateThreads' request.
 */
public class TerminateThreadsArguments extends JSONBase {

    TerminateThreadsArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Ids of threads to be terminated.
     */
    public List<Integer> getThreadIds() {
        final JSONArray json = jsonData.optJSONArray("threadIds");
        if (json == null) {
            return null;
        }
        final List<Integer> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(json.getInt(i));
        }
        return Collections.unmodifiableList(list);
    }

    public TerminateThreadsArguments setThreadIds(List<Integer> threadIds) {
        if (threadIds != null) {
            final JSONArray json = new JSONArray();
            for (int intValue : threadIds) {
                json.put(intValue);
            }
            jsonData.put("threadIds", json);
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
        TerminateThreadsArguments other = (TerminateThreadsArguments) obj;
        if (!Objects.equals(this.getThreadIds(), other.getThreadIds())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getThreadIds() != null) {
            hash = 79 * hash + Objects.hashCode(this.getThreadIds());
        }
        return hash;
    }

    public static TerminateThreadsArguments create() {
        final JSONObject json = new JSONObject();
        return new TerminateThreadsArguments(json);
    }
}
