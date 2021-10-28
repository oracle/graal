/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Alibaba Group Holding Limited. All rights reserved.
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
package com.oracle.svm.core.configure;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;

import com.oracle.svm.core.util.json.JSONParser;

public class SerializationConfigurationParser extends ConfigurationParser {

    public static final String NAME_KEY = "name";
    public static final String CUSTOM_TARGET_CONSTRUCTOR_CLASS_KEY = "customTargetConstructorClass";

    private final RuntimeSerializationSupport serializationSupport;

    public SerializationConfigurationParser(RuntimeSerializationSupport serializationSupport, boolean strictConfiguration) {
        super(strictConfiguration);
        this.serializationSupport = serializationSupport;
    }

    @Override
    public void parseAndRegister(Reader reader) throws IOException {
        JSONParser parser = new JSONParser(reader);
        Object json = parser.parse();
        for (Object serializationKey : asList(json, "first level of document must be an array of serialization lists")) {
            parseSerializationDescriptorObject(asMap(serializationKey, "second level of document must be serialization descriptor objects"));
        }
    }

    private void parseSerializationDescriptorObject(Map<String, Object> data) {
        checkAttributes(data, "serialization descriptor object", Collections.singleton(NAME_KEY), Arrays.asList(CUSTOM_TARGET_CONSTRUCTOR_CLASS_KEY, CONDITIONAL_KEY));
        ConfigurationCondition unresolvedCondition = parseCondition(data);
        String targetSerializationClass = asString(data.get(NAME_KEY));
        Object optionalCustomCtorValue = data.get(CUSTOM_TARGET_CONSTRUCTOR_CLASS_KEY);
        String customTargetConstructorClass = optionalCustomCtorValue != null ? asString(optionalCustomCtorValue) : null;

        serializationSupport.registerWithTargetConstructorClass(unresolvedCondition, targetSerializationClass, customTargetConstructorClass);
    }
}
