/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver.launcher.configuration;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.util.List;
import java.util.Map;

import com.oracle.svm.driver.launcher.json.BundleJSONParser;
import com.oracle.svm.driver.launcher.json.BundleJSONParserException;

public abstract class BundleConfigurationParser {

    public void parseAndRegister(Reader reader) throws IOException {
        parseAndRegister(new BundleJSONParser(reader).parse(), null);
    }

    public abstract void parseAndRegister(Object json, URI origin) throws IOException;

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object data, String errorMessage) {
        if (data instanceof List) {
            return (List<Object>) data;
        }
        throw new BundleJSONParserException(errorMessage);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object data, String errorMessage) {
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        throw new BundleJSONParserException(errorMessage);
    }
}
