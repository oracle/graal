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

import static com.oracle.svm.core.util.EnvVariableUtils.EnvironmentVariable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.SharedConstants;
import com.oracle.svm.core.util.ArchiveSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.LogUtils;

public class LayerArchiveSupport {

    private static final int LAYER_FILE_FORMAT_VERSION_MAJOR = 0;
    private static final int LAYER_FILE_FORMAT_VERSION_MINOR = 1;

    private static final String BUILDER_ARGUMENTS_FILE_NAME = "builder-arguments.txt";
    private static final String ENV_VARIABLES_FILE_NAME = "env-variables.txt";
    private static final String SNAPSHOT_FILE_NAME = "layer-snapshot.lsb";
    private static final String SNAPSHOT_GRAPHS_FILE_NAME = "layer-snapshot-graphs.big";
    private static final String LAYER_INFO_MESSAGE_PREFIX = "Native Image Layers";
    protected static final String LAYER_TEMP_DIR_PREFIX = "layerRoot_";
    protected static final String SHARED_LIB_NAME_PREFIX = "lib";

    public static final String LAYER_FILE_EXTENSION = ".nil";

    protected final List<String> builderArguments;

    protected final LayerProperties layerProperties;
    protected final Path layerFile;
    protected final ArchiveSupport archiveSupport;

    /** The temp directory where the layer files reside in expanded form. */
    protected final Path layerDir;

    @SuppressWarnings("this-escape")
    public LayerArchiveSupport(String layerName, Path layerFile, Path layerDir, ArchiveSupport archiveSupport) {
        this.archiveSupport = archiveSupport;

        this.layerFile = layerFile;
        validateLayerFile();

        this.layerDir = layerDir;
        try {
            Files.createDirectory(layerDir);
        } catch (IOException e) {
            throw UserError.abort("Unable to create temp directory " + layerDir + " where the layer files reside in expanded form.", e);
        }

        this.layerProperties = new LayerArchiveSupport.LayerProperties(layerName);
        this.builderArguments = new ArrayList<>();
    }

    protected void validateLayerFile() {
        Path fileName = layerFile.getFileName();
        if (fileName == null || !fileName.toString().endsWith(LAYER_FILE_EXTENSION)) {
            throw UserError.abort("The given layer file " + layerFile + " must end with '" + LAYER_FILE_EXTENSION + "'.");
        }

        if (Files.isDirectory(layerFile)) {
            throw UserError.abort("The given layer file " + layerFile + " is a directory and not a file.");
        }
    }

    public Path getSnapshotPath() {
        return layerDir.resolve(SNAPSHOT_FILE_NAME);
    }

    public Path getSnapshotGraphsPath() {
        return layerDir.resolve(SNAPSHOT_GRAPHS_FILE_NAME);
    }

    public Path getSharedLibraryPath() {
        return layerDir;
    }

    public String getSharedLibraryBaseName() {
        return layerProperties.layerName().substring(SHARED_LIB_NAME_PREFIX.length());
    }

    private static final Path layerPropertiesFileName = Path.of("META-INF/nilayer.properties");

    protected Path getLayerPropertiesFile() {
        return layerDir.resolve(layerPropertiesFileName);
    }

    protected Path getBuilderArgumentsFilePath() {
        return layerDir.resolve(BUILDER_ARGUMENTS_FILE_NAME);
    }

    protected Path getEnvVariablesFilePath() {
        return layerDir.resolve(ENV_VARIABLES_FILE_NAME);
    }

    protected List<EnvironmentVariable> parseEnvVariables() {
        return System.getenv().entrySet().stream()
                        .map(EnvironmentVariable::of)
                        .filter(envVar -> !envVar.isKeyRequired())
                        .filter(envVar -> !envVar.keyEquals(SharedConstants.DRIVER_TEMP_DIR_ENV_VARIABLE))
                        .toList();
    }

    public final class LayerProperties {

        private static final String PROPERTY_KEY_LAYER_FILE_VERSION_MAJOR = "LayerFileVersionMajor";
        private static final String PROPERTY_KEY_LAYER_FILE_VERSION_MINOR = "LayerFileVersionMinor";
        private static final String PROPERTY_KEY_LAYER_FILE_CREATION_TIMESTAMP = "LayerFileCreationTimestamp";
        private static final String PROPERTY_KEY_LAYER_BUILDER_VM_PLATFORM = "BuilderVMPlatform";
        private static final String PROPERTY_KEY_IMAGE_LAYER_NAME = "LayerName";

        private final Map<String, String> properties;

        LayerProperties(String layerName) {
            properties = new HashMap<>();
            VMError.guarantee(layerName != null && !layerName.isEmpty(), "LayerProperties entry " + PROPERTY_KEY_IMAGE_LAYER_NAME + " requires non-empty layer-name");
            properties.put(PROPERTY_KEY_IMAGE_LAYER_NAME, layerName);
        }

        private record BuilderVMIdentifier(String vendor, String version) {

            private static final String PROPERTY_KEY_VM_VENDOR = "BuilderVMVendor";
            private static final String PROPERTY_KEY_VM_VERSION = "BuilderVMVersion";

            BuilderVMIdentifier {
                Objects.requireNonNull(vendor);
                Objects.requireNonNull(version);
            }

            static BuilderVMIdentifier system() {
                return new BuilderVMIdentifier(System.getProperty("java.vm.vendor"), System.getProperty("java.vm.version"));
            }

            static BuilderVMIdentifier load(Map<String, String> properties) {
                String vmVendor = properties.get(PROPERTY_KEY_VM_VENDOR);
                String vmVersion = properties.get(PROPERTY_KEY_VM_VERSION);
                return new BuilderVMIdentifier(vmVendor, vmVersion);
            }

            public void store(Map<String, String> properties) {
                properties.put(PROPERTY_KEY_VM_VENDOR, vendor);
                properties.put(PROPERTY_KEY_VM_VERSION, version);
            }

            @Override
            public String toString() {
                return '\'' + vendor + ' ' + version + '\'';
            }
        }

        void loadAndVerify(Platform current) {
            Path layerFileName = layerFile.getFileName();
            Path layerPropertiesFile = getLayerPropertiesFile();

            if (!Files.isReadable(layerPropertiesFile)) {
                throw UserError.abort("The given layer file " + layerFileName + " does not contain a layer properties file");
            }

            properties.putAll(ArchiveSupport.loadProperties(layerPropertiesFile));
            verifyLayerFileVersion(layerFileName);
            info("Loaded layer %s from %s", layerName(), layerFileName);

            BuilderVMIdentifier layerBuilderVMIdentifier = BuilderVMIdentifier.load(properties);
            if (!layerBuilderVMIdentifier.equals(BuilderVMIdentifier.system())) {
                String message = String.format("The given layer file '%s' was created with an image builder running on %s. This image builder is using %s." +
                                " The given layer file can only be used with an image builder running the exact same version.",
                                layerFileName, layerBuilderVMIdentifier, BuilderVMIdentifier.system());
                throw UserError.abort(message);
            }

            String archivePlatform = properties.getOrDefault(PROPERTY_KEY_LAYER_BUILDER_VM_PLATFORM, "unknown");
            String currentPlatform = asString(current);
            if (!archivePlatform.equals(currentPlatform)) {
                String message = String.format("The given layer file '%s' was created on platform '%s'. The current platform is '%s'." +
                                " The given layer file can only be used with an image builder running on that same platform.",
                                layerFileName, archivePlatform, currentPlatform);
                throw UserError.abort(message);
            }

            String layerCreationTimestamp = properties.getOrDefault(PROPERTY_KEY_LAYER_FILE_CREATION_TIMESTAMP, "");
            info("Layer created at '%s'", ArchiveSupport.parseTimestamp(layerCreationTimestamp));
            info("Using version: %s on platform: '%s'", layerBuilderVMIdentifier, archivePlatform);
        }

        private void verifyLayerFileVersion(Path layerFileName) {
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

        void write(Platform current) {
            properties.put(PROPERTY_KEY_LAYER_FILE_CREATION_TIMESTAMP, ArchiveSupport.currentTime());
            properties.put(PROPERTY_KEY_LAYER_BUILDER_VM_PLATFORM, asString(current));
            BuilderVMIdentifier.system().store(properties);
            Path layerPropertiesFile = getLayerPropertiesFile();
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

        public String layerName() {
            return properties.get(PROPERTY_KEY_IMAGE_LAYER_NAME);
        }
    }

    private static String asString(Platform val) {
        return (val.getOS() + "-" + val.getArchitecture()).toLowerCase(Locale.ROOT);
    }

    protected static void info(String format, Object... args) {
        LogUtils.prefixInfo(LAYER_INFO_MESSAGE_PREFIX, format, args);
    }
}
