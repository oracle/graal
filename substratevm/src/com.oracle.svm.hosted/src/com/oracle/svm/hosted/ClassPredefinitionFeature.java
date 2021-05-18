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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.PredefinedClassesConfigurationParser;
import com.oracle.svm.core.configure.PredefinedClassesRegistry;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.AfterRegistrationAccessImpl;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;

@AutomaticFeature
public class ClassPredefinitionFeature implements Feature {

    private final Map<String, String> nameToCanonicalHash = new HashMap<>();
    private final Map<String, Class<?>> canonicalHashToClass = new HashMap<>();

    @Override
    public void afterRegistration(AfterRegistrationAccess arg) {
        AfterRegistrationAccessImpl access = (AfterRegistrationAccessImpl) arg;
        PredefinedClassesRegistry registry = new PredefinedClassesRegistryImpl();
        ImageSingletons.add(PredefinedClassesRegistry.class, registry);
        PredefinedClassesConfigurationParser parser = new PredefinedClassesConfigurationParser(registry);
        ConfigurationParserUtils.parseAndRegisterConfigurations(parser, access.getImageClassLoader(), "class predefinition",
                        ConfigurationFiles.Options.PredefinedClassesConfigurationFiles, ConfigurationFiles.Options.PredefinedClassesConfigurationResources,
                        ConfigurationFiles.PREDEFINED_CLASSES_NAME);
    }

    private class PredefinedClassesRegistryImpl implements PredefinedClassesRegistry {
        PredefinedClassesRegistryImpl() {
        }

        @Override
        public void add(String nameInfo, String providedHash, Path basePath) {
            try {
                Path path = basePath.resolve(providedHash + ConfigurationFiles.PREDEFINED_CLASSES_AGENT_EXTRACTED_NAME_SUFFIX);
                byte[] data = Files.readAllBytes(path);

                // Compute our own hash code, the files could have been messed with.
                String hash = PredefinedClassesSupport.hash(data, 0, data.length);

                /*
                 * Compute a "canonical hash" that does not incorporate debug information such as
                 * source file names, line numbers, local variable names, etc.
                 */
                ClassReader reader = new ClassReader(data);
                ClassWriter writer = new ClassWriter(0);
                reader.accept(writer, ClassReader.SKIP_DEBUG);
                byte[] canonicalData = writer.toByteArray();
                String canonicalHash = PredefinedClassesSupport.hash(canonicalData, 0, canonicalData.length);

                Class<?> alreadyDefined = canonicalHashToClass.get(canonicalHash);
                if (alreadyDefined != null) {
                    PredefinedClassesSupport.registerClass(hash, alreadyDefined);
                    return;
                }

                String className = reader.getClassName().replace('/', '.');
                if (NativeImageSystemClassLoader.singleton().forNameOrNull(className, false) != null) {
                    System.out.println("Warning: skipping predefined class because the classpath already provides a class with name: " + className);
                    return;
                }

                String existing = nameToCanonicalHash.get(className);
                if (existing != null && !canonicalHash.equals(existing)) {
                    throw UserError.abort("More than one pre-defined class with the same name provided: " + className);
                }

                Class<?> definedClass = NativeImageSystemClassLoader.singleton().predefineClass(className, data, 0, data.length);
                canonicalHashToClass.put(canonicalHash, definedClass);
                nameToCanonicalHash.put(className, canonicalHash);

                /*
                 * Initialization of the class at image build time can have unintended side effects
                 * when the class is not even supposed to be loaded yet, so we disallow it.
                 */
                RuntimeClassInitialization.initializeAtRunTime(definedClass);

                /*
                 * We use the canonical hash to unify different representations of the same class
                 * (to some extent) instead of failing, but we don't register the class with the
                 * canonical hash because it is synthetic (or equal to the other hash anyway).
                 */
                PredefinedClassesSupport.registerClass(hash, definedClass);
            } catch (IOException t) {
                throw UserError.abort(t, "Failed to pre-define class with hash %s from %s as specified in configuration", providedHash, basePath);
            }
        }
    }
}
