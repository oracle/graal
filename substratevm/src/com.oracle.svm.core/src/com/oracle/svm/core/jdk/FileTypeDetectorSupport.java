/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk;

/* Checkstyle: allow reflection */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileTypeDetector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * This class allows to customize the {@link FileTypeDetector}s used by
 * {@link Files#probeContentType(Path)}.
 */
public final class FileTypeDetectorSupport {

    public static class Options {
        @Option(help = "Make all supported FileTypeDetector available at run time.")//
        public static final HostedOptionKey<Boolean> AddAllFileTypeDetectors = new HostedOptionKey<>(true);
    }

    /**
     * Prepends the provided {@link FileTypeDetector} at the beginning of the list of detectors.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void addFirst(FileTypeDetector detector) {
        ImageSingletons.lookup(FileTypeDetectorFeature.class).installedDetectors.add(0, Objects.requireNonNull(detector));
    }

    /**
     * Appends the provided {@link FileTypeDetector} at the end of the list of detectors.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void addLast(FileTypeDetector detector) {
        ImageSingletons.lookup(FileTypeDetectorFeature.class).installedDetectors.add(Objects.requireNonNull(detector));
    }

    /**
     * Overwrites the default {@link FileTypeDetector}, i.e., the detector that is used when no
     * detector of the list matches.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setDefault(FileTypeDetector detector) {
        ImageSingletons.lookup(FileTypeDetectorFeature.class).defaultFileTypeDetector = Objects.requireNonNull(detector);
    }

    public static class AlwaysNullFileTypeDetector extends FileTypeDetector {
        @Override
        public String probeContentType(Path path) throws IOException {
            return null;
        }
    }
}

@AutomaticFeature
final class FileTypeDetectorFeature implements Feature {

    /*
     * These fields are only used during image generation and can therefore be in the hosted-only
     * feature class.
     */
    List<FileTypeDetector> installedDetectors;
    FileTypeDetector defaultFileTypeDetector;

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        installedDetectors = new ArrayList<>();
        if (FileTypeDetectorSupport.Options.AddAllFileTypeDetectors.getValue()) {
            Class<?> jdkClass = access.findClassByName("java.nio.file.Files$FileTypeDetectors");
            /* Note the typo in the JDK field name on JDK 8. */
            String installedDetectorsFieldName = JavaVersionUtil.JAVA_SPEC <= 8 ? "installeDetectors" : "installedDetectors";
            installedDetectors.addAll(ReflectionUtil.readStaticField(jdkClass, installedDetectorsFieldName));
            defaultFileTypeDetector = ReflectionUtil.readStaticField(jdkClass, "defaultFileTypeDetector");
        }
        if (defaultFileTypeDetector == null) {
            /*
             * The JDK does not allow a null value for the defaultFileTypeDetector, so we need an
             * implementation class that always returns null.
             */
            defaultFileTypeDetector = new FileTypeDetectorSupport.AlwaysNullFileTypeDetector();
        }
    }

}

final class InstalledDetectorsComputer implements RecomputeFieldValue.CustomFieldValueComputer {
    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        return ImageSingletons.lookup(FileTypeDetectorFeature.class).installedDetectors;
    }
}

final class DefaultDetectorComputer implements RecomputeFieldValue.CustomFieldValueComputer {
    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        return ImageSingletons.lookup(FileTypeDetectorFeature.class).defaultFileTypeDetector;
    }
}

@TargetClass(value = java.nio.file.Files.class, innerClass = "FileTypeDetectors")
final class Target_java_nio_file_Files_FileTypeDetectors {

    @Alias @TargetElement(onlyWith = JDK8OrEarlier.class) //
    @RecomputeFieldValue(kind = Kind.Custom, declClass = InstalledDetectorsComputer.class) //
    static List<FileTypeDetector> installeDetectors;
    @Alias @TargetElement(onlyWith = JDK11OrLater.class) //
    @RecomputeFieldValue(kind = Kind.Custom, declClass = InstalledDetectorsComputer.class) //
    static List<FileTypeDetector> installedDetectors;

    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = DefaultDetectorComputer.class) //
    static FileTypeDetector defaultFileTypeDetector;
}
