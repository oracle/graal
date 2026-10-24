/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.classinitialization.pbti;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.classinitialization.ClassInitializationOptions;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.classinitialization.pbti.PredictableBuildTimeInitializationFeature.DisallowedObjectException;
import com.oracle.svm.hosted.classinitialization.pbti.PredictableBuildTimeInitializationFeature.MaterializedPath;
import com.oracle.svm.hosted.classinitialization.pbti.PredictableBuildTimeInitializationFeature.Options;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.util.InterceptedFieldType;
import com.oracle.svm.util.OriginalClassProvider;

/**
 * Exception thrown when {@link PredictableBuildTimeInitializationFeature} finds a class instance
 * that is either initialized at run-time, or is disallowed by {@link com.oracle.svm.hosted.image.DisallowedImageHeapObjectFeature}.
 */
@SuppressWarnings("serial")
public class PredictableBuildTimeInitializationError extends UserError.UserException {
    protected final Map<InterceptedFieldType, List<MaterializedPath>> buildTimeFailures;
    protected final Map<Object, List<DisallowedObjectException>> disallowedObjectFailures;

    PredictableBuildTimeInitializationError(
                    Map<InterceptedFieldType, List<MaterializedPath>> buildTimeFailures,
                    Map<Object, List<DisallowedObjectException>> disallowedObjectExceptions) {
        this(buildTimeFailures, disallowedObjectExceptions, null);
    }

    PredictableBuildTimeInitializationError(
                    Map<InterceptedFieldType, List<MaterializedPath>> buildTimeFailures,
                    Map<Object, List<DisallowedObjectException>> disallowedObjectExceptions,
                    Path reportPath) {
        super(createMessage(buildTimeFailures, disallowedObjectExceptions, reportPath));
        this.buildTimeFailures = buildTimeFailures;
        this.disallowedObjectFailures = disallowedObjectExceptions;
    }

    public Map<InterceptedFieldType, List<MaterializedPath>> getBuildTimeFailures() {
        return buildTimeFailures;
    }

    public Map<Object, List<DisallowedObjectException>> getDisallowedObjectFailures() {
        return disallowedObjectFailures;
    }

    private static String createMessage(Map<InterceptedFieldType, List<MaterializedPath>> buildTimeFailures,
                    Map<Object, List<DisallowedObjectException>> disallowedObjectFailures, Path reportPath) {
        StringBuilder message = new StringBuilder(
                        "Objects were found accessible from static fields of build-time initialized classes, " +
                                        "but cannot be persisted safely in the image heap.")
                        .append(System.lineSeparator())
                        .append("This is not allowed for correctness reasons: all objects accessible from static " +
                                        "fields of build-time initialized classes must be safe to persist in the image heap.")
                        .append(System.lineSeparator()).append(System.lineSeparator());

        int violationNumber = 1;

        for (var entry : buildTimeFailures.entrySet()) {
            InterceptedFieldType type = entry.getKey();
            List<MaterializedPath> paths = entry.getValue();

            String typeName = type.toJavaName();
            List<String> rootTypeNames = paths.stream().map(path -> path.getRootType().toJavaName()).distinct().toList();
            boolean typeInstantiationTraced = isTypeInitializationTraced(type);

            message
                            .append('[').append(violationNumber++).append("] An object of type '").append(typeName)
                            .append("' was found accessible from static fields of build-time initialized classes.")
                            .append(System.lineSeparator())
                            .append("    This type, however, is marked for initialization at image run time for the following reason: ")
                            .append(ClassInitializationSupport.singleton().reasonForClass(OriginalClassProvider.getJavaClass(type)))
                            .append('.').append(System.lineSeparator());

            message.append(System.lineSeparator());
            if (paths.size() == 1) {
                MaterializedPath path = paths.getFirst();
                appendPathDetails(message, path, 4, typeInstantiationTraced);
            } else {
                for (int i = 0; i < paths.size(); i++) {
                    MaterializedPath path = paths.get(i);
                    message.append("    ").append(i + 1).append(". Path:").append(System.lineSeparator());
                    appendPathDetails(message, path, 6, typeInstantiationTraced);
                    if (i + 1 < paths.size()) {
                        message.append(System.lineSeparator());
                    }
                }
            }

            message.append(System.lineSeparator())
                            .append("    Possible resolutions:")
                            .append(System.lineSeparator()).append("      1) If it is intended that objects of type '")
                            .append(typeName)
                            .append("' are persisted in the image heap, add the following to the native-image arguments:")
                            .append(System.lineSeparator()).append(System.lineSeparator()).append("         ")
                            .append(SubstrateOptionsParser.commandArgument(ClassInitializationOptions.ClassInitialization, typeName,
                                            "initialize-at-build-time", true, false))
                            .append(System.lineSeparator()).append(System.lineSeparator())
                            .append("         Note that initializing new types can store additional objects in the heap.")
                            .append(System.lineSeparator())
                            .append("         It is advised to check the static fields of '")
                            .append(typeName)
                            .append("' to see if they are safe for build-time initialization, and that they do not contain any " +
                                            "sensitive data that should not become part of the image.")
                            .append(System.lineSeparator()).append(System.lineSeparator())
                            .append("      2) The object was found through the build-time initialized ")
                            .append(rootTypeNames.size() == 1 ? "class '" : "classes '")
                            .append(String.join("', '", rootTypeNames)).append("'.")
                            .append(System.lineSeparator()).append("         If ")
                            .append(rootTypeNames.size() == 1 ? "this class does" : "these classes do")
                            .append(" not need to be initialized at build time, the safer approach is to initialize ")
                            .append(rootTypeNames.size() == 1 ? "it" : "them").append(
                                            " at run time with:")
                            .append(System.lineSeparator()).append(System.lineSeparator()).append("         ")
                            .append(SubstrateOptionsParser.commandArgument(ClassInitializationOptions.ClassInitialization,
                                            String.join(",", rootTypeNames),
                                            "initialize-at-run-time", true, false))
                            .append(System.lineSeparator()).append(System.lineSeparator()).append("         This prevents the ")
                            .append(rootTypeNames.size() == 1 ? "root's" : "roots'")
                            .append(" static state from being persisted in the image heap.");

            if (!typeInstantiationTraced) {
                appendTraceInstantiationSuggestion(message, 6, typeName);
            } else {
                message.append(System.lineSeparator()).append(System.lineSeparator());
            }
        }

        for (List<DisallowedObjectException> objectFailures : disallowedObjectFailures.values()) {
            DisallowedObjectException failure = objectFailures.getFirst();
            List<String> rootTypeNames = objectFailures.stream().map(objectFailure -> objectFailure.getPath().getRootType().toJavaName()).distinct().toList();
            boolean typeInstantiationTraced = isTypeInitializationTraced(failure.getType());

            message
                            .append('[').append(violationNumber++).append("] ")
                            .append(failure.getOriginalMessage())
                            .append(System.lineSeparator())
                            .append(System.lineSeparator());

            if (objectFailures.size() == 1) {
                appendPathDetails(message, failure.getPath(), 4, typeInstantiationTraced);
            } else {
                for (int i = 0; i < objectFailures.size(); i++) {
                    message.append("    ").append(i + 1).append(". Path:").append(System.lineSeparator());
                    appendPathDetails(message, objectFailures.get(i).getPath(), 6, typeInstantiationTraced);
                    if (i + 1 < objectFailures.size()) {
                        message.append(System.lineSeparator());
                    }
                }
            }

            message
                            .append(System.lineSeparator())
                            .append("    Possible resolution:")
                            .append(System.lineSeparator())
                            .append("      The object was found through the build-time initialized ")
                            .append(rootTypeNames.size() == 1 ? "class '" : "classes '")
                            .append(String.join("', '", rootTypeNames)).append("'.")
                            .append(System.lineSeparator()).append("      If ")
                            .append(rootTypeNames.size() == 1 ? "this class does" : "these classes do")
                            .append(" not need to be initialized at build time, the safer approach is to initialize ")
                            .append(rootTypeNames.size() == 1 ? "it" : "them").append(" at run time with:")
                            .append(System.lineSeparator()).append(System.lineSeparator()).append("      ")
                            .append(SubstrateOptionsParser.commandArgument(ClassInitializationOptions.ClassInitialization,
                                            String.join(",", rootTypeNames), "initialize-at-run-time",
                                            true, false))
                            .append(System.lineSeparator()).append(System.lineSeparator())
                            .append("      This prevents the ")
                            .append(rootTypeNames.size() == 1 ? "root's" : "roots'")
                            .append(" static state from being persisted in the image heap.")
                            .append(System.lineSeparator()).append(System.lineSeparator())
                            .append("    Recommended action:")
                            .append(System.lineSeparator()).append("      ").append(failure.getInitializerAction());

            if (!typeInstantiationTraced) {
                appendTraceInstantiationSuggestion(message, 4, failure.getType().toJavaName());
            } else {
                message.append(System.lineSeparator()).append(System.lineSeparator());
            }
        }

        if (reportPath == null) {
            appendFullReportOptionMessage(message);
        } else {
            message
                            .append("JSON file report can be found at: ")
                            .append(reportPath.toAbsolutePath().normalize());
        }

        return message.toString();
    }

    private static void appendPathDetails(StringBuilder message, MaterializedPath path, int offset, boolean typeInstantiationTraced) {
        String indentation = " ".repeat(offset);
        if (Options.PBTIFullReport.getValue()) {
            appendDetailedObjectPath(message, path, indentation);
        } else {
            message
                            .append(indentation)
                            .append("Found from the build-time initialized class '")
                            .append(path.getRootType().toJavaName())
                            .append("' through the path:")
                            .append(System.lineSeparator()).append(System.lineSeparator())
                            .append(indentation)
                            .append('\'').append(path.getUnqualifiedPathString()).append('\'')
                            .append(System.lineSeparator());

            if (path.isRootDifferentFromHolder()) {
                message
                                .append(System.lineSeparator())
                                .append(indentation).append("Held by class '")
                                .append(path.getHolderType().toJavaName()).append("'.")
                                .append(System.lineSeparator());
            }
        }

        if (Options.PBTIFullReport.getValue()) {
            InterceptedFieldType rootType = path.getRootType();
            message
                            .append(System.lineSeparator())
                            .append(indentation).append("The class '")
                            .append(rootType.toJavaName())
                            .append("' is marked for initialization at image build time for the following reason: ")
                            .append(ClassInitializationSupport.singleton().reasonForClass(OriginalClassProvider.getJavaClass(rootType)))
                            .append('.').append(System.lineSeparator());
        }

        if (typeInstantiationTraced && path.hasInstantiationTrace()) {
            message.append(System.lineSeparator());
            appendObjectInstantiationTrace(message, indentation, path.getInstantiationTrace());
        }
    }

    private static void appendDetailedObjectPath(StringBuilder message, MaterializedPath path, String indentation) {
        message
                        .append(indentation)
                        .append("The violating object was found from a build-time initialized class with the following path:")
                        .append(System.lineSeparator());

        message.append(path.getDetailedObjectPathString(indentation + "  "));
    }

    private static void appendTraceInstantiationSuggestion(StringBuilder message, int offset, String type) {
        String indentation = " ".repeat(offset);
        message
                        .append(System.lineSeparator()).append(System.lineSeparator())
                        .append(indentation).append("To trace where the offending objects are instantiated, re-run with:")
                        .append(System.lineSeparator()).append(System.lineSeparator()).append(indentation)
                        .append(SubstrateOptionsParser.commandArgument(SubstrateOptions.TraceObjectInstantiation, type, true, false))
                        .append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void appendObjectInstantiationTrace(StringBuilder message, String linePrefix, String trace) {
        message
                        .append(linePrefix)
                        .append("Tracing showed that the offending object was instantiated with the following trace:")
                        .append(System.lineSeparator()).append(trace);
    }

    private static void appendFullReportOptionMessage(StringBuilder message) {
        message
                        .append("To show violations in more detail, re-run with:")
                        .append(System.lineSeparator()).append(System.lineSeparator())
                        .append(SubstrateOptionsParser.commandArgument(Options.PBTIFullReport, "+", true, false))
                        .append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static boolean isTypeInitializationTraced(InterceptedFieldType type) {
        return SubstrateOptions.TraceObjectInstantiation.hasBeenSet() &&
                        SubstrateOptions.TraceObjectInstantiation.getValue().values().contains(type.toJavaName());
    }
}
