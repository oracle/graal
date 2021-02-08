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

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A Source is a descriptor for source code. It is returned from the debug adapter as part of a
 * StackFrame and it is used by clients when specifying breakpoints.
 */
public class Source extends JSONBase {

    Source(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The short name of the source. Every source returned from the debug adapter has a name. When
     * sending a source to the debug adapter this name is optional.
     */
    public String getName() {
        return jsonData.optString("name", null);
    }

    public Source setName(String name) {
        jsonData.putOpt("name", name);
        return this;
    }

    /**
     * The path of the source to be shown in the UI. It is only used to locate and load the content
     * of the source if no sourceReference is specified (or its value is 0).
     */
    public String getPath() {
        return jsonData.optString("path", null);
    }

    public Source setPath(String path) {
        jsonData.putOpt("path", path);
        return this;
    }

    /**
     * If sourceReference &gt 0 the contents of the source must be retrieved through the
     * SourceRequest (even if a path is specified). A sourceReference is only valid for a session,
     * so it must not be used to persist a source. The value should be less than or equal to
     * 2147483647 (2^31 - 1).
     */
    public Integer getSourceReference() {
        return jsonData.has("sourceReference") ? jsonData.getInt("sourceReference") : null;
    }

    public Source setSourceReference(Integer sourceReference) {
        jsonData.putOpt("sourceReference", sourceReference);
        return this;
    }

    /**
     * An optional hint for how to present the source in the UI. A value of 'deemphasize' can be
     * used to indicate that the source is not available or that it is skipped on stepping.
     */
    public String getPresentationHint() {
        return jsonData.optString("presentationHint", null);
    }

    public Source setPresentationHint(String presentationHint) {
        jsonData.putOpt("presentationHint", presentationHint);
        return this;
    }

    /**
     * The (optional) origin of this source: possible values 'internal module', 'inlined content
     * from source map', etc.
     */
    public String getOrigin() {
        return jsonData.optString("origin", null);
    }

    public Source setOrigin(String origin) {
        jsonData.putOpt("origin", origin);
        return this;
    }

    /**
     * An optional list of sources that are related to this source. These may be the source that
     * generated this source.
     */
    public List<Source> getSources() {
        final JSONArray json = jsonData.optJSONArray("sources");
        if (json == null) {
            return null;
        }
        final List<Source> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new Source(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public Source setSources(List<Source> sources) {
        if (sources != null) {
            final JSONArray json = new JSONArray();
            for (Source source : sources) {
                json.put(source.jsonData);
            }
            jsonData.put("sources", json);
        }
        return this;
    }

    /**
     * Optional data that a debug adapter might want to loop through the client. The client should
     * leave the data intact and persist it across sessions. The client should not interpret the
     * data.
     */
    public Object getAdapterData() {
        return jsonData.opt("adapterData");
    }

    public Source setAdapterData(Object adapterData) {
        jsonData.putOpt("adapterData", adapterData);
        return this;
    }

    /**
     * The checksums associated with this file.
     */
    public List<Checksum> getChecksums() {
        final JSONArray json = jsonData.optJSONArray("checksums");
        if (json == null) {
            return null;
        }
        final List<Checksum> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new Checksum(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public Source setChecksums(List<Checksum> checksums) {
        if (checksums != null) {
            final JSONArray json = new JSONArray();
            for (Checksum checksum : checksums) {
                json.put(checksum.jsonData);
            }
            jsonData.put("checksums", json);
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
        Source other = (Source) obj;
        if (!Objects.equals(this.getName(), other.getName())) {
            return false;
        }
        if (!Objects.equals(this.getPath(), other.getPath())) {
            return false;
        }
        if (!Objects.equals(this.getSourceReference(), other.getSourceReference())) {
            return false;
        }
        if (!Objects.equals(this.getPresentationHint(), other.getPresentationHint())) {
            return false;
        }
        if (!Objects.equals(this.getOrigin(), other.getOrigin())) {
            return false;
        }
        if (!Objects.equals(this.getSources(), other.getSources())) {
            return false;
        }
        if (!Objects.equals(this.getAdapterData(), other.getAdapterData())) {
            return false;
        }
        if (!Objects.equals(this.getChecksums(), other.getChecksums())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        if (this.getName() != null) {
            hash = 37 * hash + Objects.hashCode(this.getName());
        }
        if (this.getPath() != null) {
            hash = 37 * hash + Objects.hashCode(this.getPath());
        }
        if (this.getSourceReference() != null) {
            hash = 37 * hash + Integer.hashCode(this.getSourceReference());
        }
        if (this.getPresentationHint() != null) {
            hash = 37 * hash + Objects.hashCode(this.getPresentationHint());
        }
        if (this.getOrigin() != null) {
            hash = 37 * hash + Objects.hashCode(this.getOrigin());
        }
        if (this.getSources() != null) {
            hash = 37 * hash + Objects.hashCode(this.getSources());
        }
        if (this.getAdapterData() != null) {
            hash = 37 * hash + Objects.hashCode(this.getAdapterData());
        }
        if (this.getChecksums() != null) {
            hash = 37 * hash + Objects.hashCode(this.getChecksums());
        }
        return hash;
    }

    public static Source create() {
        final JSONObject json = new JSONObject();
        return new Source(json);
    }
}
