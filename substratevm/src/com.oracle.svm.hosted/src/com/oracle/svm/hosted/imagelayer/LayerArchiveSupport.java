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
package com.oracle.svm.hosted.imagelayer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.util.ArchiveSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.NativeImageGenerator;
import com.oracle.svm.util.LogUtils;

public class LayerArchiveSupport {

    protected static final String MODULE_OPTION = "module";
    public static final String PACKAGE_OPTION = "package";
    protected static final String PATH_OPTION = "path";

    private static final int LAYER_FILE_FORMAT_VERSION_MAJOR = 0;
    private static final int LAYER_FILE_FORMAT_VERSION_MINOR = 1;

    protected static final String LAYER_INFO_MESSAGE_PREFIX = "Native Image Layers";
    protected static final String LAYER_TEMP_DIR_PREFIX = "layerRoot-";

    protected static final String LAYER_FILE_EXTENSION = ".nil";

    protected final LayerProperties layerProperties;
    protected final ArchiveSupport archiveSupport;

    public LayerArchiveSupport(ArchiveSupport archiveSupport) {
        this.archiveSupport = archiveSupport;
        this.layerProperties = new LayerArchiveSupport.LayerProperties();
    }

    protected static final Path layerPropertiesFileName = Path.of("META-INF/nilayer.properties");

    public final class LayerProperties {

        private static final String PROPERTY_KEY_LAYER_FILE_VERSION_MAJOR = "LayerFileVersionMajor";
        private static final String PROPERTY_KEY_LAYER_FILE_VERSION_MINOR = "LayerFileVersionMinor";
        private static final String PROPERTY_KEY_LAYER_FILE_CREATION_TIMESTAMP = "LayerFileCreationTimestamp";
        private static final String PROPERTY_KEY_NATIVE_IMAGE_PLATFORM = "NativeImagePlatform";
        private static final String PROPERTY_KEY_NATIVE_IMAGE_VENDOR = "NativeImageVendor";
        private static final String PROPERTY_KEY_NATIVE_IMAGE_VERSION = "NativeImageVersion";
        private static final String PROPERTY_KEY_IMAGE_LAYER_NAME = "LayerName";

        private final Map<String, String> properties;

        LayerProperties() {
            this.properties = new HashMap<>();
        }

        void loadAndVerify(Path inputLayerLocation, Path expandedInputLayerDir) {
            Path layerFileName = inputLayerLocation.getFileName();
            Path layerPropertiesFile = expandedInputLayerDir.resolve(layerPropertiesFileName);

            if (!Files.isReadable(layerPropertiesFile)) {
                throw UserError.abort("The given layer file " + layerFileName + " does not contain a layer properties file");
            }

            properties.putAll(ArchiveSupport.loadProperties(layerPropertiesFile));
            verifyVersion(layerFileName);

            String niVendor = properties.getOrDefault(PROPERTY_KEY_NATIVE_IMAGE_VENDOR, "unknown");
            String javaVmVendor = System.getProperty("java.vm.vendor");
            String currentVendor = niVendor.equals(javaVmVendor) ? "" : " != '" + javaVmVendor + "'";
            String niVersion = properties.getOrDefault(PROPERTY_KEY_NATIVE_IMAGE_VERSION, "unknown");
            String javaVmVersion = System.getProperty("java.vm.version");
            String currentVersion = niVersion.equals(javaVmVersion) ? "" : " != '" + javaVmVersion + "'";
            String niPlatform = properties.getOrDefault(PROPERTY_KEY_NATIVE_IMAGE_PLATFORM, "unknown");
            // GR-55581 will enforce platform compatibility
            String currentPlatform = niPlatform.equals(platform()) ? "" : " != '" + platform() + "'";
            String layerCreationTimestamp = properties.getOrDefault(PROPERTY_KEY_LAYER_FILE_CREATION_TIMESTAMP, "");
            info("Loaded layer from %s", layerFileName);
            info("Layer created at '%s'", ArchiveSupport.parseTimestamp(layerCreationTimestamp));
            info("Using version: '%s'%s (vendor '%s'%s) on platform: '%s'%s", niVersion, currentVersion, niVendor, currentVendor, niPlatform, currentPlatform);
        }

        private void verifyVersion(Path layerFileName) {
            String fileVersionKey = PROPERTY_KEY_LAYER_FILE_VERSION_MAJOR;
            try {
                int major = Integer.parseInt(properties.getOrDefault(fileVersionKey, "-1"));
                fileVersionKey = PROPERTY_KEY_LAYER_FILE_VERSION_MINOR;
                int minor = Integer.parseInt(properties.getOrDefault(fileVersionKey, "-1"));
                String message = String.format("The given layer file %s was created with a newer layer-file-format version %d.%d" +
                                " (current %d.%d). Update to the latest version of native-image.", layerFileName, major, minor, LAYER_FILE_FORMAT_VERSION_MAJOR, LAYER_FILE_FORMAT_VERSION_MINOR);
                if (major > LAYER_FILE_FORMAT_VERSION_MAJOR) {
                    throw UserError.abort(message);
                } else if (major == LAYER_FILE_FORMAT_VERSION_MAJOR) {
                    if (minor > LAYER_FILE_FORMAT_VERSION_MINOR) {
                        LogUtils.warning(message);
                    }
                }
            } catch (NumberFormatException e) {
                throw VMError.shouldNotReachHere(fileVersionKey + " in " + layerPropertiesFileName + " is missing or ill-defined", e);
            }
        }

        void write() {
            properties.put(PROPERTY_KEY_LAYER_FILE_CREATION_TIMESTAMP, ArchiveSupport.currentTime());
            properties.put(PROPERTY_KEY_NATIVE_IMAGE_PLATFORM, platform());
            properties.put(PROPERTY_KEY_NATIVE_IMAGE_VENDOR, System.getProperty("java.vm.vendor"));
            properties.put(PROPERTY_KEY_NATIVE_IMAGE_VERSION, System.getProperty("java.vm.version"));
            Path layerPropertiesFile = NativeImageGenerator.getOutputDirectory().resolve(layerPropertiesFileName);
            Path parent = layerPropertiesFile.getParent();
            if (parent == null) {
                throw VMError.shouldNotReachHere("The layer properties file " + layerPropertiesFile + " doesn't have a parent directory.");
            }
            archiveSupport.ensureDirectoryExists(parent);
            try (OutputStream outputStream = Files.newOutputStream(layerPropertiesFile)) {
                Properties p = new Properties();
                p.putAll(properties);
                p.store(outputStream, "Native Image Layer file properties");
            } catch (IOException e) {
                throw VMError.shouldNotReachHere("Creating layer properties file " + layerPropertiesFile + " failed", e);
            }
        }

        public void writeLayerName(String layerName) {
            properties.put(PROPERTY_KEY_IMAGE_LAYER_NAME, layerName);
        }

        public String layerName() {
            VMError.guarantee(!properties.isEmpty(), "Property file is no loaded.");
            String name = properties.get(PROPERTY_KEY_IMAGE_LAYER_NAME);
            VMError.guarantee(name != null, "Property " + PROPERTY_KEY_IMAGE_LAYER_NAME + " must be set.");
            return name;
        }
    }

    private static String platform() {
        return (OS.getCurrent().className + "-" + SubstrateUtil.getArchitectureName()).toLowerCase(Locale.ROOT);
    }

    protected static void info(String format, Object... args) {
        LogUtils.prefixInfo(LAYER_INFO_MESSAGE_PREFIX, format, args);
    }
}
