/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Alibaba Group Holding Limited. All rights reserved.
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
package com.oracle.svm.hosted;

// Checkstyle: allow reflection

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.PredefinedClassesConfigurationParser;
import com.oracle.svm.core.configure.PredefinedClassesConfigurationParser.PredefinedClassesConfigurationParserDelegate;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.AfterRegistrationAccessImpl;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;

@AutomaticFeature
public class ClassPredefinitionFeature implements Feature {

    private final Map<String, String> hashToCanonicalHash = new HashMap<>();
    private final Map<String, String> nameToCanonicalHash = new HashMap<>();

    @Override
    public void afterRegistration(AfterRegistrationAccess arg) {
        AfterRegistrationAccessImpl access = (AfterRegistrationAccessImpl) arg;
        ImageClassLoader imageClassLoader = access.getImageClassLoader();
        PredefinedClassesConfigurationParserDelegate delegate = (nameInfo, hash, basePath) -> {
            try {
                String canonicalHash = hashToCanonicalHash.get(hash);
                if (canonicalHash != null) {
                    return; // already registered
                }

                /*
                 * Compute a "canonical hash" that does not incorporate debug information such as
                 * source file names, line numbers, local variable names, etc.
                 */
                Path path = basePath.resolve(hash + ConfigurationFiles.PREDEFINED_CLASSES_AGENT_EXTRACTED_NAME_SUFFIX);
                byte[] data = Files.readAllBytes(path);
                ClassReader reader = new ClassReader(data);
                ClassWriter writer = new ClassWriter(0);
                reader.accept(writer, ClassReader.SKIP_DEBUG);
                byte[] canonicalData = writer.toByteArray();
                canonicalHash = PredefinedClassesSupport.hash(canonicalData, 0, canonicalData.length);
                hashToCanonicalHash.put(hash, canonicalHash);

                String className = reader.getClassName().replace('/', '.');
                String existing = nameToCanonicalHash.putIfAbsent(className, canonicalHash);
                if (existing != null) {
                    if (canonicalHash.equals(existing)) {
                        return; // already registered
                    }
                    throw UserError.abort("More than one pre-defined class with the same name provided: " + className);
                }

                /*
                 * Note that we don't register the class with the canonical hash because it is
                 * synthetic (or equal to the file's hash), but we use it to unify different
                 * representations of the same class.
                 */
                Class<?> definedClass = imageClassLoader.predefineClass(className, data, 0, data.length);
                RuntimeClassInitialization.initializeAtRunTime(definedClass);
                PredefinedClassesSupport.registerClass(hash, definedClass);
            } catch (IOException t) {
                throw UserError.abort(t, "Failed to pre-define class with hash %s from %s as specified in configuration", hash, basePath);
            }
        };

        PredefinedClassesConfigurationParser parser = new PredefinedClassesConfigurationParser(delegate);
        ConfigurationParserUtils.parseAndRegisterConfigurations(parser, access.getImageClassLoader(), "class predefinition",
                        ConfigurationFiles.Options.PredefinedClassesConfigurationFiles, ConfigurationFiles.Options.PredefinedClassesConfigurationResources,
                        ConfigurationFiles.PREDEFINED_CLASSES_NAME);
    }
}
