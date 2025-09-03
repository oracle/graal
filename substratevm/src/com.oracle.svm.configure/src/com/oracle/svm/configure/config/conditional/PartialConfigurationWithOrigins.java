/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.config.conditional;

import java.io.IOException;
import java.net.URI;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.configure.ConfigurationFile;
import com.oracle.svm.configure.ConfigurationParser;
import com.oracle.svm.configure.ConfigurationParserOption;
import com.oracle.svm.configure.config.ConfigurationSet;

import jdk.graal.compiler.util.json.JsonParserException;
import jdk.graal.compiler.util.json.JsonPrintable;
import jdk.graal.compiler.util.json.JsonWriter;

public class PartialConfigurationWithOrigins extends ConfigurationParser implements JsonPrintable {
    private static final ConfigurationSet emptyConfigurationSet = new ConfigurationSet();

    private final MethodCallNode root;
    private final MethodInfoRepository methodInfoRegistry;

    public PartialConfigurationWithOrigins(MethodCallNode root, MethodInfoRepository methodInfoRegistry) {
        super(EnumSet.of(ConfigurationParserOption.STRICT_CONFIGURATION));
        this.root = root;
        this.methodInfoRegistry = methodInfoRegistry;
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {

        writer.append("{").indent().newline()
                        .quote("configuration-with-origins").append(": ");
        printJson(root, writer);
        writer.unindent().newline();
        writer.append("}").newline();
    }

    private void printJson(MethodCallNode node, JsonWriter writer) throws IOException {
        writer.append("{").indent().newline();

        String className = node.methodInfo != null ? node.methodInfo.getJavaDeclaringClassName() : "<root>";
        String methodName = node.methodInfo != null ? node.methodInfo.getName() : "<root>";
        String signature = node.methodInfo != null ? node.methodInfo.getSignature() : "()V";
        writer.quote("class").append(": ").quote(className).append(",").newline();
        writer.quote("method").append(": ").quote(methodName).append(",").newline();
        writer.quote("signature").append(": ").quote(signature).append(",").newline();

        writer.quote("methods").append(": [");
        boolean first = true;
        for (MethodCallNode methodCallNode : node.calledMethods.values()) {
            if (first) {
                first = false;
            } else {
                writer.append(",");
            }
            writer.newline();
            printJson(methodCallNode, writer);
        }
        writer.newline().append("]");
        ConfigurationSet configSet = node.configuration == null ? emptyConfigurationSet : node.configuration;
        if (!configSet.isEmpty()) {
            writer.append(",").newline();
        }

        printConfigurationSet(writer, configSet);

        writer.unindent().newline();
        writer.append("}");

    }

    @Override
    public void parseAndRegister(Object json, URI origin) throws IOException {
        EconomicMap<String, Object> topObject = asMap(json, "Top level of document must be an object");
        Object originsObject = topObject.get("configuration-with-origins");
        if (originsObject == null) {
            throw new JsonParserException("Top level object must have a 'configuration-with-origins' property.");
        }
        EconomicMap<String, Object> rootMethod = asMap(originsObject, "'configuration-with-origins' must be an object");
        parseMethodEntry(null, rootMethod, origin);
    }

    private static String getStringProperty(EconomicMap<String, ?> json, String property) {
        Object prop = json.get(property);
        if (prop == null) {
            throw new JsonParserException("Missing property '" + property + "'");
        }
        return asString(prop);
    }

    private void parseMethodEntry(MethodCallNode parent, EconomicMap<String, ?> methodJson, URI origin) throws IOException {
        /* Check if we are parsing the root node */
        MethodCallNode target;
        if (parent == null) {
            target = root;
        } else {
            String className = getStringProperty(methodJson, "class");
            String methodName = getStringProperty(methodJson, "method");
            String signature = getStringProperty(methodJson, "signature");
            MethodInfo info = methodInfoRegistry.getOrAdd(className, methodName, signature);
            target = parent.getOrCreateChild(info);
        }

        Object config = methodJson.get("config");
        if (config != null) {
            EconomicMap<String, ?> configJson = asMap(config, "'config' must be an object");
            parseConfigurationSet(configJson, target.getConfiguration(), origin);
        }

        Object methods = methodJson.get("methods");
        List<Object> methodsList = asList(methods, "'methods' must be a list");
        for (Object methodObject : methodsList) {
            EconomicMap<String, ?> method = asMap(methodObject, "'methods' must contain objects");
            parseMethodEntry(target, method, origin);
        }
    }

    private static void printConfigurationSet(JsonWriter writer, ConfigurationSet configurationSet) throws IOException {
        if (!configurationSet.isEmpty()) {
            writer.quote("config").appendFieldSeparator().appendObjectStart();

            /* Print combined file */
            writer.quote(ConfigurationFile.REACHABILITY_METADATA.getName()).appendFieldSeparator().appendObjectStart();
            boolean first = true;
            for (ConfigurationFile file : ConfigurationFile.agentGeneratedFiles()) {
                ConfigurationBase<?, ?> config = configurationSet.getConfiguration(file);
                if (!config.isEmpty() && config.supportsCombinedFile()) {
                    if (first) {
                        first = false;
                    } else {
                        writer.appendSeparator();
                    }
                    ConfigurationSet.printConfigurationToCombinedFile(config, file, writer);
                }
            }
            writer.appendObjectEnd();

            /* Print legacy files */
            for (ConfigurationFile file : ConfigurationFile.agentGeneratedFiles()) {
                ConfigurationBase<?, ?> config = configurationSet.getConfiguration(file);
                if (!config.isEmpty() && !config.supportsCombinedFile()) {
                    writer.appendSeparator();
                    writer.quote(file.getName()).appendFieldSeparator();
                    config.printLegacyJson(writer);
                }
            }
            writer.appendObjectEnd();
        }
    }

    private static void parseConfigurationSet(EconomicMap<String, ?> configJson, ConfigurationSet configurationSet, URI origin) throws IOException {
        MapCursor<String, ?> cursor = configJson.getEntries();
        EnumSet<ConfigurationParserOption> parserOptions = EnumSet.of(ConfigurationParserOption.STRICT_CONFIGURATION, ConfigurationParserOption.TREAT_ALL_TYPE_REACHABLE_CONDITIONS_AS_TYPE_REACHED);
        EnumSet<ConfigurationParserOption> jniParserOptions = parserOptions.clone();
        jniParserOptions.add(ConfigurationParserOption.JNI_PARSER);
        while (cursor.advance()) {
            String configName = cursor.getKey();
            ConfigurationFile configType = ConfigurationFile.getByName(configName);
            if (configType == null) {
                throw new JsonParserException("Invalid configuration type: " + configName);
            }
            if (configType == ConfigurationFile.REACHABILITY_METADATA) {
                for (ConfigurationFile file : ConfigurationFile.combinedFileConfigurations()) {
                    var specificParserOptions = file == ConfigurationFile.JNI ? jniParserOptions : parserOptions;
                    configurationSet.getConfiguration(file).createParser(true, specificParserOptions).parseAndRegister(cursor.getValue(), origin);
                }
            } else {
                var specificParserOptions = configType == ConfigurationFile.JNI ? jniParserOptions : parserOptions;
                configurationSet.getConfiguration(configType).createParser(false, specificParserOptions).parseAndRegister(cursor.getValue(), origin);
            }
        }
    }
}
