/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure;

import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;

import com.oracle.svm.configure.config.conditional.ConfigurationConditionResolver;
import com.oracle.svm.util.LogUtils;

final class SerializationMetadataParser<C> extends SerializationConfigurationParser<C> {

    SerializationMetadataParser(ConfigurationConditionResolver<C> conditionResolver, RuntimeSerializationSupport<C> serializationSupport, EnumSet<ConfigurationParserOption> parserOptions) {
        super(conditionResolver, serializationSupport, parserOptions);
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        Object serializationJson = getFromGlobalFile(json, SERIALIZATION_KEY);
        if (serializationJson != null) {
            parseSerializationTypes(asList(serializationJson, "The serialization property must be an array of serialization descriptor object"), false);
        }
    }

    private boolean customConstructorWarningTriggered = false;

    @Override
    protected void parseSerializationDescriptorObject(EconomicMap<String, Object> data, boolean lambdaCapturingType) {
        checkAttributes(data, "serialization descriptor object", List.of(TYPE_KEY), List.of(CONDITIONAL_KEY, CUSTOM_TARGET_CONSTRUCTOR_CLASS_KEY));

        Optional<ConfigurationTypeDescriptor> targetSerializationClass = parseTypeContents(data.get(TYPE_KEY));
        if (targetSerializationClass.isEmpty()) {
            return;
        }

        UnresolvedConfigurationCondition unresolvedCondition = parseCondition(data, true);
        var condition = conditionResolver.resolveCondition(unresolvedCondition);
        if (!condition.isPresent()) {
            return;
        }

        if (!customConstructorWarningTriggered && data.containsKey(CUSTOM_TARGET_CONSTRUCTOR_CLASS_KEY)) {
            customConstructorWarningTriggered = true;
            LogUtils.warning("\"" + CUSTOM_TARGET_CONSTRUCTOR_CLASS_KEY +
                            "\" is deprecated in reachability-metadata.json. All serializable classes can be instantiated through any superclass constructor without the use of the flag.");
        }
        registerType(targetSerializationClass.get(), condition.get());
    }
}
