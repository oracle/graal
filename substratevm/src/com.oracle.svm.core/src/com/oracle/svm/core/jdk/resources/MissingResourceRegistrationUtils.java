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
package com.oracle.svm.core.jdk.resources;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;

import com.oracle.svm.configure.ConditionalElement;
import com.oracle.svm.configure.UnresolvedConfigurationCondition;
import com.oracle.svm.configure.config.ResourceConfiguration;
import com.oracle.svm.core.MissingRegistrationUtils;
import com.oracle.svm.core.util.VMError;

import jdk.internal.loader.BuiltinClassLoader;
import jdk.internal.loader.Loader;

public final class MissingResourceRegistrationUtils extends MissingRegistrationUtils {

    public static void reportResourceAccess(Module module, String resourcePath) {
        String moduleMessage = module == null ? "" : " from module " + quote(module.getName());
        String moduleOrNull = module == null ? null : module.getName();
        ConditionalElement<ResourceConfiguration.ResourceEntry> entry = new ConditionalElement<>(
                        UnresolvedConfigurationCondition.alwaysTrue(),
                        new ResourceConfiguration.ResourceEntry(resourcePath, moduleOrNull));
        StringWriter json = new StringWriter();
        try {
            ResourceConfiguration.conditionalGlobElementJson(entry, getJSONWriter(json), true);
        } catch (IOException e) {
            throw VMError.shouldNotReachHere("In memory JSON printing should not fail");
        }
        MissingResourceRegistrationError exception = new MissingResourceRegistrationError(
                        resourceError("resource" + moduleMessage + " at path " + quote(resourcePath), json.toString(), "resources"),
                        resourcePath);
        report(exception);
    }

    public static void reportResourceBundleAccess(Module module, String baseName) {
        Objects.requireNonNull(module);
        var bundleConfig = new ResourceConfiguration.BundleConfiguration(UnresolvedConfigurationCondition.alwaysTrue(), module.getName(), baseName);
        StringWriter json = new StringWriter();
        try {
            ResourceConfiguration.printResourceBundle(bundleConfig, getJSONWriter(json), true);
        } catch (IOException e) {
            throw VMError.shouldNotReachHere("In memory JSON printing should not fail");
        }
        String moduleMessage = module.isNamed() ? " from module " + quote(module.getName()) : "";
        MissingResourceRegistrationError exception = new MissingResourceRegistrationError(
                        resourceError("resource bundle" + moduleMessage + " with name " + quote(baseName), json.toString(), "resource-bundles"),
                        baseName);
        report(exception);
    }

    private static String resourceError(String resourceDescriptor, String resourceJSON, String anchor) {
        return registrationMessage("access", resourceDescriptor, resourceJSON, "", "resources", anchor);
    }

    private static void report(MissingResourceRegistrationError exception) {
        StackTraceElement responsibleClass = getResponsibleClass(exception);
        MissingRegistrationUtils.report(exception, responsibleClass);
    }

    /*
     * This is a list of all public JDK methods that end up potentially throwing missing
     * registration errors. This should be implemented using wrapping substitutions once they are
     * available.
     */
    private static final Map<String, Set<String>> resourceEntryPoints = Map.of(
                    ClassLoader.class.getTypeName(), Set.of(
                                    "getResource",
                                    "getResources",
                                    "getSystemResource",
                                    "getSystemResources"),
                    BuiltinClassLoader.class.getTypeName(), Set.of(
                                    "findResource",
                                    "findResourceAsStream"),
                    Loader.class.getTypeName(), Set.of("findResource"),
                    ResourceBundle.class.getTypeName(), Set.of("getBundleImpl"),
                    Module.class.getTypeName(), Set.of("getResourceAsStream"),
                    Class.class.getTypeName(), Set.of(
                                    "getResource",
                                    "getResourceAsStream"),
                    // Those methods can only throw missing registration errors when using a
                    // NativeImageResourceFileSystem and a NativeImageResourcePath.
                    Files.class.getTypeName(), Set.of(
                                    "walk",
                                    "getFileStore",
                                    "readAttributes",
                                    "setAttribute",
                                    "newByteChannel",
                                    "newOutputStream",
                                    "newInputStream",
                                    "createDirectory",
                                    "move",
                                    "copy",
                                    "newDirectoryStream",
                                    "delete"),
                    FileSystemProvider.class.getTypeName(), Set.of("newFileChannel"));

    private static StackTraceElement getResponsibleClass(Throwable t) {
        StackTraceElement[] stackTrace = t.getStackTrace();
        for (StackTraceElement stackTraceElement : stackTrace) {
            if (resourceEntryPoints.getOrDefault(stackTraceElement.getClassName(), Set.of()).contains(stackTraceElement.getMethodName())) {
                return stackTraceElement;
            }
        }
        return null;
    }
}
