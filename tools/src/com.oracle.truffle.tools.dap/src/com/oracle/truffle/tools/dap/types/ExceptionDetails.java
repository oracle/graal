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
 * Detailed information about an exception that has occurred.
 */
public class ExceptionDetails extends JSONBase {

    ExceptionDetails(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Message contained in the exception.
     */
    public String getMessage() {
        return jsonData.optString("message", null);
    }

    public ExceptionDetails setMessage(String message) {
        jsonData.putOpt("message", message);
        return this;
    }

    /**
     * Short type name of the exception object.
     */
    public String getTypeName() {
        return jsonData.optString("typeName", null);
    }

    public ExceptionDetails setTypeName(String typeName) {
        jsonData.putOpt("typeName", typeName);
        return this;
    }

    /**
     * Fully-qualified type name of the exception object.
     */
    public String getFullTypeName() {
        return jsonData.optString("fullTypeName", null);
    }

    public ExceptionDetails setFullTypeName(String fullTypeName) {
        jsonData.putOpt("fullTypeName", fullTypeName);
        return this;
    }

    /**
     * Optional expression that can be evaluated in the current scope to obtain the exception
     * object.
     */
    public String getEvaluateName() {
        return jsonData.optString("evaluateName", null);
    }

    public ExceptionDetails setEvaluateName(String evaluateName) {
        jsonData.putOpt("evaluateName", evaluateName);
        return this;
    }

    /**
     * Stack trace at the time the exception was thrown.
     */
    public String getStackTrace() {
        return jsonData.optString("stackTrace", null);
    }

    public ExceptionDetails setStackTrace(String stackTrace) {
        jsonData.putOpt("stackTrace", stackTrace);
        return this;
    }

    /**
     * Details of the exception contained by this exception, if any.
     */
    public List<ExceptionDetails> getInnerException() {
        final JSONArray json = jsonData.optJSONArray("innerException");
        if (json == null) {
            return null;
        }
        final List<ExceptionDetails> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new ExceptionDetails(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public ExceptionDetails setInnerException(List<ExceptionDetails> innerException) {
        if (innerException != null) {
            final JSONArray json = new JSONArray();
            for (ExceptionDetails exceptionDetails : innerException) {
                json.put(exceptionDetails.jsonData);
            }
            jsonData.put("innerException", json);
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
        ExceptionDetails other = (ExceptionDetails) obj;
        if (!Objects.equals(this.getMessage(), other.getMessage())) {
            return false;
        }
        if (!Objects.equals(this.getTypeName(), other.getTypeName())) {
            return false;
        }
        if (!Objects.equals(this.getFullTypeName(), other.getFullTypeName())) {
            return false;
        }
        if (!Objects.equals(this.getEvaluateName(), other.getEvaluateName())) {
            return false;
        }
        if (!Objects.equals(this.getStackTrace(), other.getStackTrace())) {
            return false;
        }
        if (!Objects.equals(this.getInnerException(), other.getInnerException())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getMessage() != null) {
            hash = 53 * hash + Objects.hashCode(this.getMessage());
        }
        if (this.getTypeName() != null) {
            hash = 53 * hash + Objects.hashCode(this.getTypeName());
        }
        if (this.getFullTypeName() != null) {
            hash = 53 * hash + Objects.hashCode(this.getFullTypeName());
        }
        if (this.getEvaluateName() != null) {
            hash = 53 * hash + Objects.hashCode(this.getEvaluateName());
        }
        if (this.getStackTrace() != null) {
            hash = 53 * hash + Objects.hashCode(this.getStackTrace());
        }
        if (this.getInnerException() != null) {
            hash = 53 * hash + Objects.hashCode(this.getInnerException());
        }
        return hash;
    }

    public static ExceptionDetails create() {
        final JSONObject json = new JSONObject();
        return new ExceptionDetails(json);
    }
}
