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

import org.graalvm.collections.Equivalence;
import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.FieldValueTransformerRequiresUniverse;
import org.graalvm.nativeimage.impl.clinit.ClassInitializationTracking;

import com.oracle.graal.pointsto.constraints.UnsupportedPlatformException;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.heap.ImageHeapArray;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.TypedConstant;
import com.oracle.graal.pointsto.heap.value.ValueSupplier;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.BaseLayerType;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.svm.util.InterceptedField;
import com.oracle.svm.util.InterceptedFieldType;
// import com.oracle.svm.core.FutureDefaultsOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;
import com.oracle.svm.core.image.DisallowedImageHeapObjects;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.GuestTypes;
import com.oracle.svm.hosted.classinitialization.ClassInitializationOptions;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.classinitialization.InitKind;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;
import com.oracle.svm.hosted.ameta.SVMHostedValueProvider;
import com.oracle.svm.hosted.methodhandles.InjectedInvokerRenamingSubstitutionProcessor;
import com.oracle.svm.hosted.methodhandles.MethodHandleInvokerRenamingSubstitutionProcessor;
import com.oracle.svm.hosted.reflect.proxy.ProxyRenamingSubstitutionProcessor;
import com.oracle.svm.hosted.substitute.DeletedElementException;
import com.oracle.svm.shared.BuildPhaseProvider;
import com.oracle.svm.shared.collections.ConcurrentIdentityHashMap;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.HeapObjectConstantReplacer;
import com.oracle.svm.util.OriginalClassProvider;

import jdk.graal.compiler.core.common.type.CompressibleConstant;
import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.graal.compiler.util.json.JsonBuilder;
import jdk.graal.compiler.util.json.JsonPrettyWriter;
import jdk.graal.compiler.util.json.JsonWriter;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.MetaAccessProvider;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Predictable build-time initialization (pbti) feature ensures that all classes of objects
 * that are stored in or accessible from static fields of build-time initialized classes
 * are also build-time initialized, even if they are not accessed by the analysis.
 * This feature attempts to prevent these types of issues being masked by reachability.
 *
 * <p>
 * Additionally, objects are also checked by {@link DisallowedImageHeapObjects#check(Object, DisallowedImageHeapObjects.DisallowedObjectReporter)}.
 * </p>
 *
 * <p>
 * The processing lifecycle goes as follows:
 * <ul>
 *     <li>In {@link com.oracle.svm.hosted.NativeImageGenerator} after _beforeAnalysis_ feature calls are finished</li>
 *     <li>After analysis, to process roots found by analysis</li>
 *     <li>After compilation, to re-process delayed fields by {@link FieldValueTransformerWithAvailability}</li>
 *     <li>Before image write, to re-process any remaining delayed fields</li>
 * </ul>
 * </p>
 */
@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuiltinTraits.BuildtimeAccessOnly.class, layeredCallbacks = BuiltinTraits.NoLayeredCallbacks.class)
@SuppressWarnings("unused")
public class PredictableBuildTimeInitializationFeature implements InternalFeature {
    private static final int FIELD_BATCH_SIZE = 32;

    public static class Options {
        // @formatter:off
        /**
         * Full reports for failures discovered before analysis are generated and reported immediately.
         * Failures discovered after analysis are retained and included in the final report before image writing.
         */
        @Option(help = "Create a full report of predictable build-time initialization failures, including all reconstructed paths", stability = OptionStability.EXPERIMENTAL)
        public static final HostedOptionKey<Boolean> PBTIFullReport = new HostedOptionKey<>(false);

        /**
         * It is better to limit this number as the number of explored branches can be quite substantial in some cases.
         * For {@link PredictableBuildTimeInitializationFeature pbti} purposes,
         * one path per one root is enough to make decisions about class initialization configuration.
         */
        @Option(help = "Maximum number of alternate path branches explored per offending type and root when reconstructing a full PBTI report. Use -1 for no limit.", stability = OptionStability.EXPERIMENTAL)
        public static final HostedOptionKey<Integer> PBTIMaximumPathBranches = new HostedOptionKey<>(100, optionKey -> {
            int value = optionKey.getValue();
            if (value < -1) {
                throw UserError.invalidOptionValue(optionKey, value, "The value must be -1 or greater.");
            }
        });

        /**
         * This option stops class initialization being gated by reachability.
         * It can also interfere with {@link com.oracle.svm.hosted.classinitialization.ClassInitializationOptions#AssertInitializationSpecifiedForAllClasses}.
         */
        @Option(help = "Initialize all possible roots for PBTI", stability = OptionStability.EXPERIMENTAL)
        public static final HostedOptionKey<Boolean> InitializeNewRoots = new HostedOptionKey<>(false);
        // @formatter:on
    }

    protected ClassInitializationSupport classInitializationSupport;
    private AnalysisUniverse universe;
    private MetaAccessProvider origMetaAccess;
    protected SVMHostedValueProvider hostedValueProvider;
    private FieldValueInterceptionSupport fieldValueInterceptionSupport;
    private GuestTypes guestTypes;

    /**
     * Custom type layer for pbti representing types after substitution was performed,
     * and to prevent type lookups from expanding the analysis universe.
     */
    private PredictableBuildTimeInitializationLookupLayer pbtiLookupLayer;

    private CompletionExecutor executor;

    /**
     * Set of all types that were already processed.
     */
    protected final Set<InterceptedFieldType> processedTypes = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Set of processed objects read from fields to prevent cyclic dependency between objects.
     */
    protected final Set<JavaConstant> processedConstants = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Extra paths that have encountered the same constant. Used for path reconstruction.
     */
    private final Map<JavaConstant, Queue<LinkedPath>> extraPathsToConstant = new ConcurrentHashMap<>();

    /**
     * Map of run-time initialized types found at paths accessed from roots.
     */
    protected final Map<InterceptedFieldType, List<LinkedPath>> buildTimeFailures = new ConcurrentHashMap<>();

    /**
     * Disallowed objects found while traversing the fields of build-time initialized objects.
     */
    private final Map<Object, List<DisallowedObjectException>> disallowedObjectFailures = new ConcurrentIdentityHashMap<>();

    /**
     * Field reads delayed by {@link FieldValueTransformerWithAvailability#isAvailable()}.
     */
    private List<Pair<LinkedPath, ValueSupplier<JavaConstant>>> delayedFieldValues = Collections.synchronizedList(new ArrayList<>());

    /**
     * Layered field values that are not currently available.
     * These are separate from normal delayed fields because unavailable layered values can be
     * silently skipped in beforeImageWrite.
     */
    private List<Pair<LinkedPath, ValueSupplier<JavaConstant>>> delayedLayeredFieldValues = Collections.synchronizedList(new ArrayList<>());

    /**
     * Fields that require the analysis to be processed any further.
     * This is done based on {@link FieldValueTransformerRequiresUniverse},
     * if a field is read and the receiver is reachable, then it can be checked further.
     */
    private List<Pair<LinkedPath, JavaConstant>> analysisFieldValues = Collections.synchronizedList(new ArrayList<>());

    /**
     * Values whose constant replacer can only run after analysis has finished.
     */
    private List<Pair<LinkedPath, JavaConstant>> analysisReplacementValues = Collections.synchronizedList(new ArrayList<>());

    /**
     * Values of renamed types deferred so PBTI cannot consume names before analysis does.
     */
    private List<Pair<LinkedPath, JavaConstant>> analysisDeferredValues = Collections.synchronizedList(new ArrayList<>());

    /**
     * Roots discovered by type creation callbacks, which can run concurrently.
     */
    private final Queue<AnalysisType> createdTypes = new ConcurrentLinkedQueue<>();

    protected static final String[] EXCLUDED_ROOTS = {
                    /*
                     * Field nativeInstrumentedFilterState contains ByteBuffer,
                     * which pbti can find, and is disallowed by DisallowedImageHeapObjects check.
                     */
                    "com.oracle.truffle.runtime.debug.JFRListener",
    };

    /**
     * Array of type names that are ignored from further processing, when an instance is found.
     * This can only be used for internal classes that might be misconfigured.
     */
    protected static final String[] IGNORED_TYPES = {
                    /*
                     * This class is not marked as build-time initialized, when Truffle's
                     * initialization configuration is missing. An instance can be found from:
                     *  - "com.oracle.svm.truffle.nfi.ErrnoMirrorGen"
                     *  - "com.oracle.svm.truffle.nfi.KeysArrayGen"
                     */
                    "com.oracle.truffle.api.library.DynamicDispatchLibraryGen",
                    /*
                     * When InitializeNewRoots is enabled, this class instance can be found from these roots
                     *  - org.graalvm.polyglot.Engine
                     *  - com.oracle.truffle.polyglot.PolyglotLanguageContext
                     *  - com.oracle.truffle.polyglot.PolyglotContextImpl
                     *  - com.oracle.truffle.api.TruffleLogger$LoggerCache
                     *  - com.oracle.truffle.sl.runtime.SLFunction
                     * And these violating objects can be found active:
                     *  - jdk.internal.jimage.ImageBufferCache$1
                     *  - started Thread instance
                     *  - ByteBuffer
                     *  - instance of Cleanable or jdk.internal.ref.Cleaner
                     */
                    "com.oracle.truffle.polyglot.PolyglotContextImpl"
    };

    private final class ProcessFieldsTask implements CompletionExecutor.DebugContextRunnable {
        private final LinkedPath path;
        private final InterceptedFieldType type;
        private final JavaConstant receiver;

        ProcessFieldsTask(LinkedPath path, InterceptedFieldType type) {
            this(path, type, null);
        }

        ProcessFieldsTask(LinkedPath path, InterceptedFieldType type, JavaConstant receiver) {
            this.path = path;
            this.type = type;
            this.receiver = receiver;
        }

        @Override
        public void run(DebugContext debug) {
            var fields = receiver == null ? type.getStaticFields() : type.getInstanceFields(true);
            for (var field : fields) {
                processField(new LinkedPath(path, new Field(type, (InterceptedField) field)), receiver);
            }
        }

        @Override
        public DebugContext.Description getDescription() {
            if (receiver == null) {
                return new DebugContext.Description(path, "PBTI static fields");
            }

            return new DebugContext.Description(path, "PBTI instance fields");
        }
    }

    private final class ProcessDelayedFieldsTask implements CompletionExecutor.DebugContextRunnable {
        private final List<Pair<LinkedPath, ValueSupplier<JavaConstant>>> taskDelayedFields;

        ProcessDelayedFieldsTask(List<Pair<LinkedPath, ValueSupplier<JavaConstant>>> taskDelayedFields) {
            this.taskDelayedFields = List.copyOf(taskDelayedFields);
        }

        @Override
        public void run(DebugContext debug) {
            for (Pair<LinkedPath, ValueSupplier<JavaConstant>> delayedField : taskDelayedFields) {
                if (!delayedField.getRight().isAvailable()) {
                    delayedFieldValues.add(delayedField);
                    continue;
                }

                processConstant(delayedField.getLeft(), getValueFromSupplier(delayedField.getLeft(), delayedField.getRight()));
            }
        }

        @Override
        public DebugContext.Description getDescription() {
            return new DebugContext.Description(taskDelayedFields, "PBTI delayed fields");
        }
    }

    private final class ProcessDelayedLayeredFieldsTask implements CompletionExecutor.DebugContextRunnable {
        private final List<Pair<LinkedPath, ValueSupplier<JavaConstant>>> taskDelayedFields;

        ProcessDelayedLayeredFieldsTask(List<Pair<LinkedPath, ValueSupplier<JavaConstant>>> taskDelayedFields) {
            this.taskDelayedFields = List.copyOf(taskDelayedFields);
        }

        @Override
        public void run(DebugContext debug) {
            for (Pair<LinkedPath, ValueSupplier<JavaConstant>> delayedField : taskDelayedFields) {
                if (delayedField.getRight().isAvailable()) {
                    processConstant(delayedField.getLeft(), getValueFromSupplier(delayedField.getLeft(), delayedField.getRight()));
                }
            }
        }

        @Override
        public DebugContext.Description getDescription() {
            return new DebugContext.Description(taskDelayedFields, "PBTI delayed layered fields");
        }
    }

    private final class ProcessAnalysisFieldsTask implements CompletionExecutor.DebugContextRunnable {
        private final List<Pair<LinkedPath, JavaConstant>> taskAnalysisFields;

        ProcessAnalysisFieldsTask(List<Pair<LinkedPath, JavaConstant>> taskAnalysisFields) {
            this.taskAnalysisFields = List.copyOf(taskAnalysisFields);
        }

        @Override
        public void run(DebugContext debug) {
            for (Pair<LinkedPath, JavaConstant> analysisField : taskAnalysisFields) {
                processField(analysisField.getLeft(), analysisField.getRight());
            }
        }

        @Override
        public DebugContext.Description getDescription() {
            return new DebugContext.Description(taskAnalysisFields, "PBTI analysis fields");
        }
    }

    private final class ProcessAnalysisConstantsTask implements CompletionExecutor.DebugContextRunnable {
        private final List<Pair<LinkedPath, JavaConstant>> taskAnalysisReplacementValues;

        ProcessAnalysisConstantsTask(List<Pair<LinkedPath, JavaConstant>> taskAnalysisReplacementValues) {
            this.taskAnalysisReplacementValues = List.copyOf(taskAnalysisReplacementValues);
        }

        @Override
        public void run(DebugContext debug) {
            for (Pair<LinkedPath, JavaConstant> analysisReplacementValue : taskAnalysisReplacementValues) {
                processConstant(analysisReplacementValue.getLeft(), analysisReplacementValue.getRight());
            }
        }

        @Override
        public DebugContext.Description getDescription() {
            return new DebugContext.Description(taskAnalysisReplacementValues, "PBTI analysis constants");
        }
    }

    /**
     * Result of a constant read from {@link #resolveObjectConstant(LinkedPath, JavaConstant)}.
     *
     * @param path Where was the constant read from
     * @param type Resolved type of the constant
     * @param constant Constant object
     */
    private record ConstantInfo(LinkedPath path, InterceptedFieldType type, JavaConstant constant) {
    }

    /** Data for one member of a path, independent of the linked path structure. */
    protected abstract static class PathMember implements Comparable<PathMember> {
        private JavaConstant constant;
        private InterceptedFieldType constantType;

        /**
         * @return Get the constant that was read at this path member.
         */
        public JavaConstant getConstant() {
            assert constant != null;
            return constant;
        }

        /**
         * @param constant Set the constant that was read from this path member, after replacement
         */
        public void setConstant(JavaConstant constant, InterceptedFieldType constantType) {
            this.constant = constant;
            this.constantType = constantType;
        }

        public InterceptedFieldType getConstantType() {
            assert constantType != null;
            return constantType;
        }

        /**
         * @return Whether there should be a dot between this and the previous member when formatting
         *         a path.
         */
        public boolean requiresTrailingDot() {
            return true;
        }

        @Override
        public abstract boolean equals(Object obj);

        @Override
        public abstract int hashCode();

        @Override
        public abstract String toString();

        public String toString(boolean qualified) {
            return toString();
        }

        @Override
        public final int compareTo(PathMember other) {
            if (this instanceof Index leftIndex && other instanceof Index rightIndex) {
                return Integer.compare(leftIndex.value, rightIndex.value);
            }

            int result = Integer.compare(sortKind(), other.sortKind());
            if (result != 0) {
                return result;
            }
            return toString().compareTo(other.toString());
        }

        private int sortKind() {
            return switch (this) {
                case Root root -> 0;
                case Field field -> 1;
                case Index index -> 2;
                default -> throw VMError.shouldNotReachHere("Unknown path member: " + this);
            };
        }
    }

    /**
     * Path that was taken from {@link Root} to access a {@link Field} to check the build-time
     * initialization status of the constant it holds.
     */
    protected static final class LinkedPath {
        public final LinkedPath prev;
        public final PathMember member;

        /**
         * The root at which this path starts at.
         */
        private final Root root;

        LinkedPath(LinkedPath prev, PathMember member) {
            this.prev = prev;
            this.member = member;
            this.root = prev == null ? (Root) member : prev.root;
        }

        @Override
        public String toString() {
            return toString(true);
        }

        public String toString(boolean qualified) {
            return formatPath(getPathMembers(), qualified);
        }

        public List<PathMember> getPathMembers() {
            var result = new ArrayList<PathMember>();
            for (LinkedPath current = this; current != null; current = current.prev) {
                result.add(current.member);
            }
            return result;
        }

        public InterceptedFieldType getRootType() {
            return root.type;
        }

        /**
         * @return The type of the constant that holds the field (or array) that
         * the final constant, where an exception occurred, was read from.
         */
        public InterceptedFieldType getHolderType() {
            for (LinkedPath path = this; path != null; path = path.prev) {
                if (path.member instanceof Field field) {
                    return field.type;
                }
            }

            throw VMError.shouldNotReachHere("No holder type found for path: " + this);
        }

        private static String formatPath(List<PathMember> path) {
            return formatPath(path, true);
        }

        private static String formatPath(List<PathMember> path, boolean qualified) {
            var result = new StringBuilder();
            var rootIdx = path.size() - 1;
            for (int i = rootIdx; i >= 0; i--) {
                PathMember member = path.get(i);
                if (i < rootIdx && member.requiresTrailingDot()) {
                    result.append('.');
                }
                result.append(member.toString(qualified));
            }
            return result.toString();
        }
    }

    public static final class Root extends PathMember {
        public InterceptedFieldType type;

        Root(InterceptedFieldType type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return toString(true);
        }

        @Override
        public String toString(boolean qualified) {
            return type.toJavaName(qualified);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Root root && root.type.equals(type);
        }

        @Override
        public int hashCode() {
            return type.hashCode();
        }
    }

    public static final class Field extends PathMember {
        public InterceptedFieldType type;
        public InterceptedField field;

        Field(InterceptedFieldType type, InterceptedField field) {
            this.type = type;
            this.field = field;
        }

        @Override
        public String toString() {
            return field.getName();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Field f && f.field.equals(field) && f.type.equals(type);
        }

        @Override
        public int hashCode() {
            return field.hashCode();
        }
    }

    public static final class Index extends PathMember {
        public int value;

        Index(int value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "[" + value + "]";
        }

        @Override
        public boolean requiresTrailingDot() {
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Index idx && idx.value == value;
        }

        @Override
        public int hashCode() {
            return value;
        }
    }

    @SuppressWarnings("serial")
    public final class DisallowedObjectException extends RuntimeException {
        private final Object object;
        private final InterceptedFieldType type;
        private final String originalMessage;
        private final String initializerAction;
        private final MaterializedPath path;

        private DisallowedObjectException(String message, Object object, String initializerAction, InterceptedFieldType type, MaterializedPath path) {
            super(createMessage(message, object, initializerAction, type, path));
            this.object = object;
            this.initializerAction = initializerAction;
            this.originalMessage = message;
            this.type = type;
            this.path = path;
        }

        public String getOriginalMessage() {
            return originalMessage;
        }

        public Object getObject() {
            return object;
        }

        public InterceptedFieldType getType() {
            return type;
        }

        public String getPathString() {
            return path.getPathString();
        }

        public MaterializedPath getPath() {
            return path;
        }

        public String getInitializerAction() {
            return initializerAction;
        }

        private static String createMessage(String message, Object object, String initializerAction, InterceptedFieldType type, MaterializedPath path) {
            return type.toJavaName() + " reachable from " + path.getRootType().toJavaName() + " via " + path.getUnqualifiedPathString() + ": " + message + " " + initializerAction;
        }
    }

    /**
     * Exception thrown in an object replacer or a field value transformer.
     * The exception trace is shown along with the traversal path and build-time initialized root.
     * When the cause is a UserError instance, then a possible resolution is shown by initializing
     * the root at run-time.
     */
    @SuppressWarnings("serial")
    public static final class TraversalException extends UnsupportedFeatureException {
        private final MaterializedPath path;

        /**
         * Different from {@code cause} to shorten the error message,
         * because it is already shown there.
         */
        private final Throwable source;

        private TraversalException(MaterializedPath path, Throwable cause) {
            super(createMessage(path, cause));
            this.path = path;
            this.source = cause;
        }

        public String getPathString() {
            return path.getPathString();
        }

        public Throwable getSourceException() {
            return source;
        }

        private static String createMessage(MaterializedPath path, Throwable cause) {
            StringBuilder message = new StringBuilder(
                            "An exception occurred while traversing an object accessible from a static field of a build-time initialized class.")
                            .append(System.lineSeparator()).append(System.lineSeparator())
                            .append("Found from the build-time initialized class '")
                            .append(path.getRootType().toJavaName())
                            .append("' through the path:")
                            .append(System.lineSeparator()).append(System.lineSeparator())
                            .append("    '").append(path.getUnqualifiedPathString()).append('\'')
                            .append(System.lineSeparator()).append(System.lineSeparator());

            if (cause instanceof UserError.UserException) {
                message
                                .append("Possible resolution:")
                                .append(System.lineSeparator())
                                .append("  The object was found through the build-time initialized class '")
                                .append(path.getRootType().toJavaName())
                                .append("'.")
                                .append(System.lineSeparator())
                                .append("  If this class does not need to be initialized at build time, the safer approach is to initialize it at run time with:")
                                .append(System.lineSeparator()).append(System.lineSeparator()).append("  ")
                                .append(SubstrateOptionsParser.commandArgument(ClassInitializationOptions.ClassInitialization,
                                                path.getRootType().toJavaName(), "initialize-at-run-time", true, false))
                                .append(System.lineSeparator()).append(System.lineSeparator())
                                .append("  This prevents the root's static state from being persisted in the image heap.")
                                .append(System.lineSeparator()).append(System.lineSeparator());
            }

            return message.append("The traversal failed with:")
                            .append(System.lineSeparator())
                            .append(cause)
                            .toString();
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        // return FutureDefaultsOptions.predictableBuildTimeInitialization();
        return true;
    }

    @Override
    public void onRegistration(OnRegistrationAccess access) {
        ImageSingletons.add(PredictableBuildTimeInitializationFeature.class, this);
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        var access = (FeatureImpl.DuringSetupAccessImpl) a;

        classInitializationSupport = ClassInitializationSupport.singleton();
        universe = access.getUniverse();
        hostedValueProvider = (SVMHostedValueProvider) universe.getHostedValuesProvider();
        fieldValueInterceptionSupport = FieldValueInterceptionSupport.singleton();
        origMetaAccess = universe.getOriginalMetaAccess();
        guestTypes = access.getImageClassLoader().guestTypes;

        pbtiLookupLayer = new PredictableBuildTimeInitializationLookupLayer(
                        access.getHostVM(), origMetaAccess, universe.getSubstitutions(), universe.getWordKind());

        executor = new CompletionExecutor(access.getDebugContext(), access.getBigBang());

        if (Options.InitializeNewRoots.getValue()) {
            access.registerOnTypeCreatedCallback((type) -> {
                if (type.getWrapped() instanceof BaseLayerType) {
                    /*
                     * It is assumed that the type from previous layer
                     * was checked by the feature the layer was built.
                     * There is no extra re-processing for these types.
                     */
                    return;
                }

                /* Run the potential initialization later */
                createdTypes.add(type);
            });
        }
    }

    /**
     * This method has to run after every beforeAnalysis feature method has run.
     * Called in {@link com.oracle.svm.hosted.NativeImageGenerator}, after class
     * initialization registration is sealed.
     *
     * <p>
     * The current processing goes as following:
     * <ul>
     *     <li>Initialize new roots from {@link GuestTypes} in series, if -H:+InitializeNewRoots</li>
     *     <li>Process fields in parallel before analysis</li>
     *     <li>Process newly found roots by analysis after it completes</li>
     *     <li>
     *         Process delayed fields via {@link FieldValueTransformerWithAvailability}
     *         after compilation, before the post-compilation image heap verification.
     *     </li>
     *     <li>Process delayed fields before heap layout, before the final image heap verification.</li>
     *     <li>Process any remaining delayed fields before image write, where they must be available.</li>
     * </ul>
     * </p>
     */
    public void afterBeforeAnalysis() {
        executor.init();

        assert createdTypes.isEmpty();
        processBuildTimeRoots();

        if (Options.InitializeNewRoots.getValue()) {
            /*
             * Concurrent initialization can deadlock when class initializers contain cyclic
             * dependencies, which happens for Truffle's Assumption classes.
             */
            for (ResolvedJavaType type : guestTypes.getApplicationTypes()) {
                processUninitializedRoot(OriginalClassProvider.getJavaClass(type));
            }
        }

        runExecutor();
        throwIfFailure();
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        executor.init();

        processAnalysisFieldValues();
        processAnalysisReplacementValues();
        processDeferredValues();

        processDelayedFieldValues();
        processBuildTimeRoots();

        if (Options.InitializeNewRoots.getValue()) {
            AnalysisType type;
            while ((type = createdTypes.poll()) != null) {
                processUninitializedRoot(OriginalClassProvider.getJavaClass(type));
            }
        }

        runExecutor();
        if (!Options.PBTIFullReport.getValue()) {
            throwIfFailure();
        }
    }

    /**
     * Retry values that become available once compilation is finished. This must run before the
     * post-compilation image heap verification so PBTI failures retain their rooted paths and are
     * reported before the general image heap check.
     */
    public void afterCompilationFinishes() {
        executor.init();

        processDelayedFieldValues();
        processDelayedLayeredFieldValues();
        runExecutor();

        throwIfFailure();
    }

    /**
     * Run before the final heap verification is done and the heap is sealed.
     */
    @Override
    public void beforeHeapLayout(BeforeHeapLayoutAccess access) {
        executor.init();

        processDelayedFieldValues();
        processDelayedLayeredFieldValues();
        runExecutor();

        throwIfFailure();
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        /*
         * Values that can appear in the image heap must already be available because the image is about to be written.
         * Process delayed fields in beforeImageWrite so that failures are reported before the executable is written out.
         */
        executor.init();

        assert createdTypes.isEmpty();
        processDelayedFieldValues();
        processDelayedLayeredFieldValues();
        runExecutor();

        /* All fields must be processed by now */
        VMError.guarantee(delayedFieldValues.isEmpty(),
                        "Field values are still unavailable before image writing: %n%s",
                        String.join("\n", delayedFieldValues.stream().map(Pair::getLeft).map(p -> " - " + p.toString()).toList()));

        throwIfFailure();
    }

    /**
     * Process the current set of build-time initialized classes.
     */
    protected void processBuildTimeRoots() {
        for (Class<?> clazz : classInitializationSupport.classesWithKind(InitKind.BUILD_TIME)) {
            InterceptedFieldType type = lookupType(clazz);
            if (type == null) {
                continue;
            }

            if (classInitializationSupport.computedInitKindFor(OriginalClassProvider.getJavaClass(type)) != InitKind.BUILD_TIME) {
                /* The Target_ class is initialized, but the underlying class might not be. */
                if (Options.InitializeNewRoots.getValue()) {
                    processUninitializedRoot(type);
                }
                continue;
            }

            processInitializedRoot(type);
        }
    }

    /**
     * Process a root synchronously and add its static fields to the executor.
     */
    protected final void processUninitializedRoot(Class<?> clazz) {
        InterceptedFieldType type = lookupType(clazz);
        if (type == null) {
            return;
        }

        processUninitializedRoot(type);
    }

    /**
     * Add root's static fields to the executor, ready for processing.
     * A root is a type that is initialized at build time. If an attempt
     * to initialize the type fails, then it is ignored.
     */
    private void processUninitializedRoot(InterceptedFieldType type) {
        if (isExcludedRoot(type)) {
            return;
        }

        if (isFromSharedLayer(type)) {
            /*
             * Shared layer types were initialized and verified while building the previous layer.
             * Do not initialize or inspect their current hosted versions again. Generated types,
             * such as Weld proxies, can be recreated under the same name with different metadata,
             * which can make the build fail when compared with the persisted shared layer type.
             */
            return;
        }

        if (processedTypes.contains(type)) {
            return;
        }

        try {
            var clazz = OriginalClassProvider.getJavaClass(type);
            if (!classInitializationSupport.maybeInitializeAtBuildTime(clazz)) {
                return;
            }
        } catch (UserError.UserException e) {
            if (!e.toString().contains("Class initialization of ")) {
                /* Re-throw other exceptions that are unrelated to class initialization */
                throw e;
            }

            return;
        }

        processInitializedRoot(type);
    }

    private void processInitializedRoot(InterceptedFieldType type) {
        if (isExcludedRoot(type)) {
            return;
        }

        if (isFromSharedLayer(type)) {
            return;
        }

        if (!processedTypes.add(type)) {
            /* Using the substituted type for deduplication of processing. */
            return;
        }

        var clazz = OriginalClassProvider.getJavaClass(type);
        assert classInitializationSupport.computedInitKindFor(clazz) == InitKind.BUILD_TIME;

        var root = new LinkedPath(null, new Root(type));
        if (type.getStaticFields().length != 0) {
            executor.execute(new ProcessFieldsTask(root, type));
        }
    }

    private void runExecutor() {
        executor.start();
        try {
            executor.complete();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        executor.shutdown();
    }

    private void processField(LinkedPath path, JavaConstant receiver) {
        Field fieldMember = (Field) path.member;
        InterceptedField field = fieldMember.field;
        if (!field.getStorageKind().isObject()) {
            return;
        }

        if (fieldValueInterceptionSupport.requiresAnalysis(field)) {
            /*
             * Cannot read this field without accessing the analysis.
             * The field has to be read and the receiver must be reachable
             * in order to proceed further.
             *
             * It is assumed that the SVMHostedValueProvider
             * uses FieldValueInterceptionSupport to read field values.
             */
            if (!BuildPhaseProvider.isAnalysisFinished()) {
                analysisFieldValues.add(Pair.create(path, receiver));
                return;
            }

            if (!canProcessAnalysisField(field, receiver)) {
                return;
            }

            field = getAnalysisField(field);
            /*
             * At this point, the field is known to be read and its receiver is reachable,
             * therefore the value can be read using its field value transformer
             * without expanding the image heap or the analysis/hosted universe through internal operations.
             */
        }

        /* This must use the analysis field when a layered transformer needs to be installed. */
        if (fieldValueInterceptionSupport.isValueNeverAvailable(field)) {
            /* Explicitly ignore fields, that can never be read */
            return;
        }

        /*
         * Field value transformers are expected to be registered either
         * in beforeAnalysis or in a beforeFieldValueAccess callback.
         * Otherwise, no further registration is expected.
         */
        JavaConstant receiverToRead = receiver;
        if (receiver instanceof ImageHeapConstant imageHeapConstant) {
            assert imageHeapConstant.isBackedByHostedObject();
            /* Cannot read the value from the VM if using heap constant */
            receiverToRead = imageHeapConstant.getHostedObject();
        }

        ValueSupplier<JavaConstant> valueSupplier = hostedValueProvider.readFieldValue(field, receiverToRead);
        if (!valueSupplier.isAvailable()) {
            if (fieldValueInterceptionSupport.isLayeredFieldValue(field)) {
                /* Retried before image write */
                delayedLayeredFieldValues.add(Pair.create(path, valueSupplier));
            } else {
                delayedFieldValues.add(Pair.create(path, valueSupplier));
            }
            return;
        }

        JavaConstant constant = getValueFromSupplier(path, valueSupplier);
        processConstant(path, constant);
    }

    private JavaConstant getValueFromSupplier(LinkedPath path, ValueSupplier<JavaConstant> valueSupplier) {
        try {
            return valueSupplier.get();
        } catch (Throwable cause) {
            throw new TraversalException(new MaterializedPath(path), cause);
        }
    }

    /**
     * All fields that were not available at the time of the read are re-processed. Fields that
     * are still unavailable, as well as fields delayed while processing the newly available
     * values, are retained for the next attempt.
     */
    private void processDelayedFieldValues() {
        var previouslyDelayedFieldValues = delayedFieldValues;
        delayedFieldValues = Collections.synchronizedList(new ArrayList<>());

        processCollectionBatched(previouslyDelayedFieldValues, ProcessDelayedFieldsTask::new);
    }

    /**
     * Retry layered field values once, ignoring values for which this layer has no update. Install
     * a new list first so layered fields discovered transitively during processing can be skipped
     * safely as well.
     */
    private void processDelayedLayeredFieldValues() {
        var previouslyDelayedLayeredFieldValues = delayedLayeredFieldValues;
        delayedLayeredFieldValues = Collections.synchronizedList(new ArrayList<>());

        processCollectionBatched(previouslyDelayedLayeredFieldValues, ProcessDelayedLayeredFieldsTask::new);
    }

    /**
     * Process fields whose transformers need analysis elements, after analysis has finished.
     */
    private void processAnalysisFieldValues() {
        processCollectionBatched(analysisFieldValues, ProcessAnalysisFieldsTask::new);
        analysisFieldValues = null; /* No longer needed */
    }

    /**
     * Retry values whose constant replacement requires analysis reachability.
     */
    private void processAnalysisReplacementValues() {
        processCollectionBatched(analysisReplacementValues, ProcessAnalysisConstantsTask::new);
        analysisReplacementValues = null; /* No longer needed */
    }

    /**
     * Retry values of renamed types, after analysis has finished.
     */
    private void processDeferredValues() {
        processCollectionBatched(analysisDeferredValues, ProcessAnalysisConstantsTask::new);
        analysisDeferredValues = null; /* No longer needed */
    }

    private <T> void processCollectionBatched(List<T> collection, Function<List<T>, CompletionExecutor.DebugContextRunnable> taskCreator) {
        for (int start = 0; start < collection.size(); start += FIELD_BATCH_SIZE) {
            int end = Math.min(start + FIELD_BATCH_SIZE, collection.size());
            executor.execute(taskCreator.apply(collection.subList(start, end)));
        }
    }

    /**
     * Process a constant by either turning it into a constant
     * and calling {@link #processObjectConstant(LinkedPath, JavaConstant, InterceptedFieldType)}, or if
     * said constant represents an array, then iterate until all
     * objects are read from the array and processed.
     *
     * @param rootPath Path where the constant was read
     * @param root Constant to be processed
     */
    private void processConstant(LinkedPath rootPath, JavaConstant root) {
        /* Process the constant first, before allocating a deque */
        ConstantInfo rootInfo = resolveObjectConstant(rootPath, root);
        if (rootInfo == null) {
            return;
        }

        if (!rootInfo.type().isArray()) {
            processObjectConstant(rootInfo.path(), rootInfo.constant(), rootInfo.type());
            return;
        }

        if (rootInfo.type().getComponentType().isPrimitive()) {
            return;
        }

        var constWorklist = new ArrayDeque<Pair<LinkedPath, JavaConstant>>();
        addArrayElementsToWorklist(constWorklist, rootInfo.path(), rootInfo.constant());

        while (!constWorklist.isEmpty()) {
            var cp = constWorklist.poll();
            ConstantInfo info = resolveObjectConstant(cp.getLeft(), cp.getRight());
            if (info == null) {
                continue;
            }

            if (!info.type().isArray()) {
                processObjectConstant(info.path(), info.constant(), info.type());
                continue;
            }

            if (info.type().getComponentType().isPrimitive()) {
                continue;
            }

            addArrayElementsToWorklist(constWorklist, info.path(), info.constant());
        }
    }

    private ConstantInfo resolveObjectConstant(LinkedPath path, JavaConstant constant) {
        if (constant.isNull() || !constant.getJavaKind().isObject()) {
            return null;
        }

        JavaConstant replacement;
        if (constant instanceof ImageHeapConstant imageHeapConstant) {
            replacement = imageHeapConstant;
        } else {
            replacement = replaceConstantNoHeap(path, constant);
            if (HeapObjectConstantReplacer.CANNOT_REPLACE_MARKER.equals(replacement)) {
                if (!BuildPhaseProvider.isAnalysisFinished()) {
                    analysisReplacementValues.add(Pair.create(path, constant));
                    return null;
                }

                if (!canProcessAnalysisReplacement(path)) {
                    return null;
                }

                /*
                 * The constant cannot be replaced without internal operations that expand the analysis/hosted universe or image heap,
                 * so it is checked whether the nearest field that the constant was read from is read and its receiver is reachable.
                 * This increases the coverage of pbti without triggering those operations for unreachable objects.
                 */
                replacement = replaceConstantWithAllReplacers(path, constant);
            }
        }

        if (replacement.isNull()) {
            return null;
        }

        if (replacement instanceof ImageHeapConstant imageHeapConstant && !imageHeapConstant.isBackedByHostedObject()) {
            /* It is assumed that un-backed constants were already analyzed in the prior layer. */
            return null;
        }

        if (!BuildPhaseProvider.isAnalysisFinished() && !(constant instanceof TypedConstant) && isDeferredType(origMetaAccess.lookupJavaType(constant))) {
            /* Defer before substitution lookup and constant deduplication so the value is retried. */
            analysisDeferredValues.add(Pair.create(path, replacement));
            return null;
        }

        var type = lookupType(replacement);
        if (type == null || !type.getStorageKind().isObject()) {
            /*
             * Platform unsupported and deleted types are ignored, these
             * exceptions are thrown when their instances are visible to analysis.
             */
            return null;
        }

        /* For path reconstruction, the constant needs to be noted. */
        path.member.setConstant(replacement, type);

        JavaConstant constantIdentity = getConstantIdentity(replacement);
        if (!processedConstants.add(constantIdentity)) {
            if (Options.PBTIFullReport.getValue()) {
                /* Skip extra processing, only take note of the path, if full report is done. */
                extraPathsToConstant.computeIfAbsent(constantIdentity, _ -> new ConcurrentLinkedQueue<>()).add(path);
            }
            return null;
        }

        return new ConstantInfo(path, type, replacement);
    }

    private JavaConstant replaceConstantNoHeap(LinkedPath path, JavaConstant constant) {
        try {
            return universe.replaceConstantNoHeap(constant);
        } catch (Throwable cause) {
            throw new TraversalException(new MaterializedPath(path), cause);
        }
    }

    private JavaConstant replaceConstantWithAllReplacers(LinkedPath path, JavaConstant constant) {
        try {
            return universe.replaceConstantWithAllReplacers(constant);
        } catch (Throwable cause) {
            throw new TraversalException(new MaterializedPath(path), cause);
        }
    }

    private void addArrayElementsToWorklist(ArrayDeque<Pair<LinkedPath, JavaConstant>> constWorklist, LinkedPath path, JavaConstant inputConstant) {
        JavaConstant array = inputConstant;
        if (array instanceof ImageHeapConstant imageHeapConstant) {
            assert imageHeapConstant.isBackedByHostedObject();

            array = imageHeapConstant.getHostedObject();
        }

        int length = hostedValueProvider.readArrayLength(array);
        for (int i = 0; i < length; i++) {
            JavaConstant element = hostedValueProvider.readArrayElement(array, i);
            constWorklist.add(Pair.create(new LinkedPath(path, new Index(i)), element));
        }
    }

    private int readArrayLength(JavaConstant inputConstant) {
        JavaConstant array = inputConstant;
        if (array instanceof ImageHeapConstant imageHeapConstant) {
            if (imageHeapConstant instanceof ImageHeapArray imageHeapArray) {
                return imageHeapArray.getLength();
            }

            array = imageHeapConstant.getHostedObject();
        }
        return hostedValueProvider.readArrayLength(array);
    }

    /**
     * Check if constant's class is build-time initialized.
     * If not, report a failure. If it is, read all its
     * fields and schedule them for execution.
     *
     * @param path Where the constant was read from
     * @param constant Constant to be processed
     * @param type of the constant
     */
    private void processObjectConstant(LinkedPath path, JavaConstant constant, InterceptedFieldType type) {
        assert !constant.isNull();

        if (isIgnoredType(type)) {
            return;
        }

        if (!maybeInitializeAtBuildTime(type)) {
            buildTimeFailures
                            .computeIfAbsent(type, _ -> Collections.synchronizedList(new ArrayList<>()))
                            .add(path);
            return;
        }

        var backingObject = getBackingObject(constant);
        if (backingObject != null) {
            DisallowedImageHeapObjects.DisallowedObjectReporter reporter = (msg, disallowedObject, initializerAction) -> {
                return new DisallowedObjectException(msg, disallowedObject, initializerAction, type, new MaterializedPath(path));
            };

            try {
                DisallowedImageHeapObjects.check(backingObject, reporter);
            } catch (DisallowedObjectException exception) {
                disallowedObjectFailures
                                .computeIfAbsent(exception.object, _ -> Collections.synchronizedList(new ArrayList<>()))
                                .add(exception);
                return;
            }
        }

        if (type.getInstanceFields(true).length != 0) {
            executor.execute(new ProcessFieldsTask(path, type, constant));
        }
    }

    private Object getBackingObject(JavaConstant constant) {
        if (constant instanceof ImageHeapConstant imageHeapConstant) {
            assert imageHeapConstant.isBackedByHostedObject();

            return hostedValueProvider.asObject(Object.class, imageHeapConstant.getHostedObject());
        }

        return hostedValueProvider.asObject(Object.class, constant);
    }

    protected static JavaConstant getConstantIdentity(JavaConstant constant) {
        JavaConstant uncompressed = CompressibleConstant.uncompress(constant);
        if (uncompressed instanceof ImageHeapConstant imageHeapConstant) {
            /* Assumption that un-backed hosted object has been skipped/processed in previous steps. */
            assert imageHeapConstant.isBackedByHostedObject();

            return imageHeapConstant.getHostedObject();
        }
        return uncompressed;
    }

    /**
     * @return Whether this field that requires the universe can be further processed without
     *         internal operations expanding the analysis/hosted universe or image heap - the field is read
     *         and its receiver is reachable.
     */
    private boolean canProcessAnalysisField(InterceptedField field, JavaConstant receiver) {
        var analysisField = getAnalysisField(field);
        if (analysisField == null) {
            return false;
        }

        if (!analysisField.isRead()) {
            return false;
        }

        if (receiver == null) {
            return true;
        }

        if (receiver instanceof ImageHeapConstant imageHeapConstant) {
            return imageHeapConstant.isReachable();
        }

        Object receiverObject = hostedValueProvider.asObject(Object.class, receiver);
        return universe.getHeapScanner().isObjectReachable(receiverObject);
    }

    private AnalysisField getAnalysisField(InterceptedField field) {
        var pbtiField = (PredictableBuildTimeInitializationField) field;
        return universe.getField(pbtiField.getWrapped());
    }

    /**
     * @return Whether the nearest field is read and its receiver is reachable.
     */
    private boolean canProcessAnalysisReplacement(LinkedPath path) {
        for (LinkedPath current = path; current != null; current = current.prev) {
            /* Find the last field and check if it is read and its receiver is reachable. */
            if (current.member instanceof Field fieldMember) {
                JavaConstant receiver = null;
                if (!fieldMember.field.isStatic()) {
                    assert current.prev != null : current;
                    receiver = current.prev.member.getConstant();
                }
                return canProcessAnalysisField(fieldMember.field, receiver);
            }
        }
        throw VMError.shouldNotReachHere("No field found for path: " + path);
    }

    private static final int JSON_OUTPUT_VERSION = 1;

    /**
     * Write a versioned, machine-readable report. Run-time initialization failures are grouped by
     * type and contain all paths to that type. Disallowed object failures have one entry per object.
     *
     * <pre>{@code
     * {
     *   "version": 1,
     *   "failures": [
     *     {
     *       "kind": "run-time-initialized-type",
     *       "type": "example.RunTimeType",
     *       "runTimeInitializationReason": "classes are initialized at run time by default",
     *       "paths": [
     *         {
     *           "buildTimeRootType": "example.BuildTimeRoot",
     *           "objectHolderType": "example.Holder",
     *           "path": "example.BuildTimeRoot.value"
     *         }
     *       ],
     *       "recommendedActions": [
     *         {
     *           "action": "initialize-type-at-build-time",
     *           "command": "--initialize-at-build-time=example.RunTimeType"
     *         },
     *         {
     *           "action": "initialize-roots-at-run-time",
     *           "command": "--initialize-at-run-time=example.BuildTimeRoot"
     *         }
     *       ]
     *     },
     *     {
     *       "kind": "disallowed-image-heap-object",
     *       "type": "java.util.Random",
     *       "message": "Reason the object is disallowed.",
     *       "initializerAction": "Suggested action.",
     *       "paths": [
     *         {
     *           "buildTimeRootType": "example.BuildTimeRoot",
     *           "objectHolderType": "example.BuildTimeRoot",
     *           "path": "example.BuildTimeRoot.random"
     *         }
     *       ],
     *       "recommendedActions": [
     *         {
     *           "action": "initialize-roots-at-run-time",
     *           "command": "--initialize-at-run-time=example.BuildTimeRoot"
     *         }
     *       ]
     *     }
     *   ]
     * }
     * }</pre>
     */
    private static Path printStructuredFailures(
                    Map<InterceptedFieldType, List<MaterializedPath>> buildTimeFailures,
                    Map<Object, List<DisallowedObjectException>> disallowedObjectFailures) {
        Consumer<PrintWriter> consumer = writer -> {
            try (JsonWriter out = new JsonPrettyWriter(writer); JsonBuilder.ObjectBuilder report = out.objectBuilder()) {
                report.append("version", JSON_OUTPUT_VERSION);
                try (JsonBuilder.ArrayBuilder failures = report.append("failures").array()) {
                    for (var entry : buildTimeFailures.entrySet()) {
                        List<MaterializedPath> paths = entry.getValue();
                        List<String> rootTypeNames = paths.stream().map(path -> path.getRootType().toJavaName()).distinct().toList();
                        try (JsonBuilder.ObjectBuilder failure = failures.nextEntry().object()) {
                            var initializationReason = ClassInitializationSupport.singleton().reasonForClass(OriginalClassProvider.getJavaClass(entry.getKey()));

                            failure.append("kind", "run-time-initialized-type");
                            failure.append("type", entry.getKey().toJavaName());
                            failure.append("runTimeInitializationReason", initializationReason);
                            try (JsonBuilder.ArrayBuilder jsonPaths = failure.append("paths").array()) {
                                for (MaterializedPath path : paths) {
                                    try (JsonBuilder.ObjectBuilder jsonPath = jsonPaths.nextEntry().object()) {
                                        jsonPath.append("buildTimeRootType", path.getRootType().toJavaName());
                                        jsonPath.append("objectHolderType", path.getHolderType().toJavaName());
                                        jsonPath.append("path", path.getPathString());
                                    }
                                }
                            }

                            try (JsonBuilder.ArrayBuilder recommendedActions = failure.append("recommendedActions").array()) {
                                try (JsonBuilder.ObjectBuilder action = recommendedActions.nextEntry().object()) {
                                    action.append("action", "initialize-type-at-build-time");
                                    action.append("command", SubstrateOptionsParser.commandArgument(ClassInitializationOptions.ClassInitialization,
                                                    entry.getKey().toJavaName(), "initialize-at-build-time"));
                                }
                                try (JsonBuilder.ObjectBuilder action = recommendedActions.nextEntry().object()) {
                                    action.append("action", "initialize-roots-at-run-time");
                                    action.append("command", SubstrateOptionsParser.commandArgument(ClassInitializationOptions.ClassInitialization,
                                                    String.join(",", rootTypeNames), "initialize-at-run-time"));
                                }
                            }
                        }
                    }
                    for (List<DisallowedObjectException> objectFailures : disallowedObjectFailures.values()) {
                        DisallowedObjectException disallowed = objectFailures.getFirst();
                        List<String> rootTypeNames = objectFailures.stream().map(objectFailure -> objectFailure.path.getRootType().toJavaName()).distinct().toList();
                        try (JsonBuilder.ObjectBuilder failure = failures.nextEntry().object()) {
                            failure.append("kind", "disallowed-image-heap-object");
                            failure.append("type", disallowed.type.toJavaName());
                            failure.append("message", disallowed.getOriginalMessage());
                            failure.append("initializerAction", disallowed.getInitializerAction());
                            try (JsonBuilder.ArrayBuilder jsonPaths = failure.append("paths").array()) {
                                for (DisallowedObjectException objectFailure : objectFailures) {
                                    try (JsonBuilder.ObjectBuilder jsonPath = jsonPaths.nextEntry().object()) {
                                        jsonPath.append("buildTimeRootType", objectFailure.path.getRootType().toJavaName());
                                        jsonPath.append("objectHolderType", objectFailure.path.getHolderType().toJavaName());
                                        jsonPath.append("path", objectFailure.getPathString());
                                    }
                                }
                            }
                            try (JsonBuilder.ArrayBuilder recommendedActions = failure.append("recommendedActions").array();
                                            JsonBuilder.ObjectBuilder action = recommendedActions.nextEntry().object()) {
                                action.append("action", "initialize-roots-at-run-time");
                                action.append("command", SubstrateOptionsParser.commandArgument(ClassInitializationOptions.ClassInitialization,
                                                String.join(",", rootTypeNames), "initialize-at-run-time"));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        return ReportUtils.report("predictable build-time initialization failures", SubstrateOptions.reportsPath(),
                        "predictable_build_time_initialization_failures", "json", consumer, false);
    }

    private void throwIfFailure() {
        if (!hasFailures()) {
            return;
        }

        if (!Options.PBTIFullReport.getValue()) {
            throw new PredictableBuildTimeInitializationError(
                            sortFailureMap(materializeFailureMap(this.buildTimeFailures)),
                            sortDisallowedObjectFailureMap(disallowedObjectFailures));
        }

        var sortedBuildTimeFailures = reconstructAllFailurePaths();
        var sortedDisallowedObjectFailures = sortDisallowedObjectFailureMap(this.disallowedObjectFailures);
        Path reportPath = printStructuredFailures(sortedBuildTimeFailures, sortedDisallowedObjectFailures);

        throw new PredictableBuildTimeInitializationError(sortedBuildTimeFailures, sortedDisallowedObjectFailures, reportPath);
    }

    private boolean hasFailures() {
        return !buildTimeFailures.isEmpty() || !disallowedObjectFailures.isEmpty();
    }

    private boolean maybeInitializeAtBuildTime(InterceptedFieldType type) {
        var clazz = OriginalClassProvider.getJavaClass(type);
        var result = classInitializationSupport.maybeInitializeAtBuildTime(clazz);
        if (result) {
            /* Newly initialized roots should also be processed */
            processInitializedRoot(type);
        }
        return result;
    }

    /**
     * Lookup a {@link InterceptedFieldType} without expanding the analysis/hosted universe,
     * skipping deleted types and types unsupported on this platform.
     *
     * @param clazz Class to lookup type for
     * @return Resolved platform supported type or null.
     */
    protected final InterceptedFieldType lookupType(Class<?> clazz) {
        try {
            return pbtiLookupLayer.lookup(clazz);
        } catch (DeletedElementException | UnsupportedPlatformException e) {
            /* Deleted types and types unsupported on the current platform are skipped. */
            return null;
        }
    }

    private InterceptedFieldType lookupType(JavaConstant constant) {
        try {
            return pbtiLookupLayer.lookup(constant);
        } catch (DeletedElementException | UnsupportedPlatformException e) {
            return null;
        }
    }

    private static boolean isDeferredType(ResolvedJavaType type) {
        ResolvedJavaType originalType = OriginalClassProvider.getOriginalType(type);
        return LambdaUtils.isLambdaType(originalType) ||
                        InjectedInvokerRenamingSubstitutionProcessor.isInjectedInvokerType(originalType) ||
                        MethodHandleInvokerRenamingSubstitutionProcessor.isMethodHandleType(originalType) ||
                        ProxyRenamingSubstitutionProcessor.isProxyType(originalType);
    }

    protected String[] getExcludedRoots() {
        return EXCLUDED_ROOTS;
    }

    private boolean isExcludedRoot(InterceptedFieldType type) {
        return Arrays.stream(getExcludedRoots()).anyMatch(root -> type.toJavaName().startsWith(root));
    }

    protected String[] getIgnoredTypes() {
        return IGNORED_TYPES;
    }

    private boolean isIgnoredType(InterceptedFieldType type) {
        return Arrays.stream(getIgnoredTypes()).anyMatch(ignoredType -> type.toJavaName().startsWith(ignoredType));
    }

    private boolean isFromSharedLayer(InterceptedFieldType type) {
        var analysisType = universe.optionalLookup(OriginalClassProvider.getOriginalType(type));
        return analysisType != null && analysisType.isInSharedLayer();
    }

    /**
     * Materialized path from the original {@link LinkedPath linked structure path}.
     * Used for sorting and printing.
     */
    public class MaterializedPath implements Comparable<MaterializedPath> {
        /**
         * The type holding the last field before the constant was read.
         *
         * <p>
         * For example, in Root.field1.field2[0][1], the holder type
         * is the type of constant that "field1" contains.
         * </p>
         */
        private final InterceptedFieldType holderType;

        /**
         * List of path members in a list. The order is reversed (root is the last element).
         */
        private final List<PathMember> members;

        private final String pathString;

        MaterializedPath(LinkedPath path) {
            this(path.getHolderType(), path.getPathMembers());
        }

        MaterializedPath(InterceptedFieldType holderType, List<PathMember> members) {
            this.holderType = holderType;
            this.members = members;
            this.pathString = LinkedPath.formatPath(members);
        }

        @Override
        public String toString() {
            return pathString;
        }

        public String getPathString() {
            return pathString;
        }

        public String getUnqualifiedPathString() {
            return LinkedPath.formatPath(members, false);
        }

        public InterceptedFieldType getRootType() {
            var root = (Root) this.members.getLast();
            return root.type;
        }

        public InterceptedFieldType getHolderType() {
            return holderType;
        }

        public boolean isRootDifferentFromHolder() {
            return !holderType.equals(getRootType());
        }

        protected Object getOffendingObject() {
            return getBackingObject(members.getFirst().getConstant());
        }

        boolean hasInstantiationTrace() {
            Object object = getOffendingObject();
            return ClassInitializationTracking.instantiatedObjects.get(object) != null;
        }

        String getInstantiationTrace() {
            Object object = getOffendingObject();
            if (object == null) {
                return null;
            }

            StackTraceElement[] trace = ClassInitializationTracking.instantiatedObjects.get(object);
            if (trace == null) {
                return null;
            }

            StringBuilder result = new StringBuilder();
            for (StackTraceElement element : trace) {
                result.append("\tat ").append(element).append(System.lineSeparator());
            }
            return result.toString();
        }

        /**
         * Show the path in more detail, with types and array sizes.
         *
         * <p>
         * The path {@code Root.field[1]} is shown as follows:
         * <pre>{@code
         * Root
         *   - field: Object[2]
         *     - [1]: RuntimeInitialized
         * }</pre>
         *
         * Where, {@code Root} is the build-time initialized root, {@code Object[2]} is the type of the array that
         * was read from {@code field} field of {@code Root}.
         * </p>
         *
         * @param indentation Prefix for each line of the formatted path
         * @return Formatted path
         */
        String getDetailedObjectPathString(String indentation) {
            StringBuilder message = new StringBuilder();
            for (int i = members.size() - 1; i >= 0; i--) {
                PathMember member = members.get(i);
                message.append(indentation).repeat("  ", members.size() - (i + 1));

                switch (member) {
                    case Root root -> message.append(root.type.toJavaName());
                    case Field field -> message.append("- ").append(field.field.getName()).append(": ").append(getConstantTypeName(field));
                    case Index index -> message.append("- [").append(index.value).append("]: ").append(getConstantTypeName(index));
                    default -> {
                    }
                }

                message.append(System.lineSeparator());
            }

            return message.toString();
        }

        private String getConstantTypeName(PathMember member) {
            InterceptedFieldType constantType = member.getConstantType();
            String typeName = constantType.toJavaName();
            if (!constantType.isArray()) {
                return typeName;
            }

            int firstArrayDimension = typeName.indexOf("[]");
            assert firstArrayDimension >= 0 : typeName;
            return typeName.substring(0, firstArrayDimension) + '[' + readArrayLength(member.getConstant()) + ']' + typeName.substring(firstArrayDimension + 2);
        }

        @Override
        public int compareTo(MaterializedPath other) {
            int result = Integer.compare(members.size(), other.members.size());
            if (result != 0) {
                return result;
            }

            /* Members are in the reverse order */
            for (int i = members.size() - 1; i >= 0; i--) {
                result = members.get(i).compareTo(other.members.get(i));
                if (result != 0) {
                    return result;
                }
            }
            return 0;
        }
    }

    private Map<InterceptedFieldType, List<MaterializedPath>> materializeFailureMap(Map<InterceptedFieldType, List<LinkedPath>> failures) {
        var result = new EconomicHashMap<InterceptedFieldType, List<MaterializedPath>>();
        failures.forEach((type, paths) -> result.put(type, paths.stream().map(MaterializedPath::new).toList()));
        return result;
    }

    /**
     * Sort failure map by offending type to make the errors (somewhat) stable across different builds.
     */
    private static Map<InterceptedFieldType, List<MaterializedPath>> sortFailureMap(Map<InterceptedFieldType, List<MaterializedPath>> failures) {
        var result = new LinkedHashMap<InterceptedFieldType, List<MaterializedPath>>(failures.size());
        failures.entrySet().stream()
                        .sorted(Comparator.comparing(entry -> entry.getKey().toJavaName()))
                        .forEachOrdered(entry -> result.put(entry.getKey(), sortedPaths(entry.getValue())));
        return Collections.unmodifiableMap(result);
    }

    /**
     * Sort paths by their number of members and then by each member from root to leaf.
     * To have access to the shortest path where a violation for certain type has occurred.
     */
    private static List<MaterializedPath> sortedPaths(List<MaterializedPath> paths) {
        return paths.stream().sorted().toList();
    }

    private static Map<Object, List<DisallowedObjectException>> sortDisallowedObjectFailureMap(Map<Object, List<DisallowedObjectException>> failures) {
        var result = new EconomicHashMap<Object, List<DisallowedObjectException>>(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE, failures.size());

        // Compare by type name and sort individual paths
        var comparator = Comparator.comparing((Pair<Object, List<DisallowedObjectException>> entry) -> entry.getRight().getFirst().type.toJavaName());
        failures.entrySet().stream()
                        .map(entry -> Pair.create(entry.getKey(), sortedDisallowedObjectFailures(entry.getValue())))
                        .sorted(comparator)
                        .forEachOrdered(entry -> result.put(entry.getLeft(), entry.getRight()));

        return Collections.unmodifiableMap(result);
    }

    private static List<DisallowedObjectException> sortedDisallowedObjectFailures(List<DisallowedObjectException> failures) {
        var comparator = Comparator.comparing((DisallowedObjectException failure) -> failure.type.toJavaName())
                        .thenComparing(failure -> failure.path);

        return failures.stream().sorted(comparator).toList();
    }

    private record BranchCounterKey(InterceptedFieldType offendingType, InterceptedFieldType rootType) {
    }

    /** Entry for path reconstruction from {@link #extraPathsToConstant}. */
    private class PathWorklistEntry {
        private InterceptedFieldType holderType;
        private final List<PathMember> members;
        private final Set<JavaConstant> visitedConstants;
        private final InterceptedFieldType offendingType;
        private LinkedPath path;

        PathWorklistEntry(LinkedPath path, InterceptedFieldType offendingType) {
            this.members = new ArrayList<>();
            this.visitedConstants = new EconomicHashSet<>();
            this.path = path;
            this.offendingType = offendingType;
        }

        PathWorklistEntry(PathWorklistEntry previous, LinkedPath path) {
            this.holderType = previous.holderType;
            this.members = new ArrayList<>(previous.members);
            this.visitedConstants = new EconomicHashSet<>(previous.visitedConstants);
            this.path = path;
            this.offendingType = previous.offendingType;
        }

        private InterceptedFieldType getRoot() {
            return path.getRootType();
        }

        private boolean hasNext() {
            return path.prev != null;
        }

        private boolean next() {
            if (!visitedConstants.add(getConstantIdentity(path.member.getConstant()))) {
                return false;
            }

            if (holderType == null && path.member instanceof Field field) {
                holderType = field.type;
            }

            members.add(path.member);
            path = path.prev;
            return true;
        }

        private MaterializedPath toResult() {
            assert holderType != null;

            members.add(path.member);
            path = null;
            return new MaterializedPath(holderType, members);
        }
    }

    /**
     * Reconstruct paths from the {@link LinkedPath} that have shared the same constant, via
     * {@link #extraPathsToConstant}. Retain the shortest path from each root to an offending type,
     * and sort the resulting paths so the shortest path is first.
     */
    private Map<InterceptedFieldType, List<MaterializedPath>> reconstructAllFailurePaths() {
        Map<InterceptedFieldType, Map<InterceptedFieldType, MaterializedPath>> pathsByRoot = new EconomicHashMap<>();
        Map<BranchCounterKey, Integer> branchCounters = new EconomicHashMap<>();
        Queue<PathWorklistEntry> worklist = new ArrayDeque<>();
        for (var failure : buildTimeFailures.entrySet()) {
            for (var path : failure.getValue()) {
                worklist.add(new PathWorklistEntry(path, failure.getKey()));
            }
        }

        while (!worklist.isEmpty()) {
            var entry = worklist.poll();

            if (!entry.hasNext()) {
                InterceptedFieldType root = entry.getRoot();
                MaterializedPath reconstructedPath = entry.toResult();
                pathsByRoot.computeIfAbsent(entry.offendingType, _ -> new EconomicHashMap<>())
                                .merge(root, reconstructedPath, (current, candidate) -> candidate.compareTo(current) < 0 ? candidate : current);
                continue;
            }

            var extraPaths = extraPathsToConstant.get(getConstantIdentity(entry.path.member.getConstant()));
            if (extraPaths != null) {
                for (var branch : extraPaths) {
                    BranchCounterKey counterKey = new BranchCounterKey(entry.offendingType, branch.getRootType());
                    if (!canExerciseBranch(branchCounters, counterKey)) {
                        continue;
                    }
                    var branchEntry = new PathWorklistEntry(entry, branch);
                    if (branchEntry.next()) {
                        worklist.add(branchEntry);
                    }
                }
            }

            if (entry.next()) {
                worklist.add(entry);
            }
        }

        Map<InterceptedFieldType, List<MaterializedPath>> result = new EconomicHashMap<>();
        pathsByRoot.forEach((type, rootPaths) -> result.put(type, new ArrayList<>(rootPaths.values())));
        return sortFailureMap(result);
    }

    private static boolean canExerciseBranch(Map<BranchCounterKey, Integer> branchCounters, BranchCounterKey counterKey) {
        if (Options.PBTIMaximumPathBranches.getValue() < 0) {
            return true;
        }

        int branchCount = branchCounters.getOrDefault(counterKey, 0);
        if (branchCount >= Options.PBTIMaximumPathBranches.getValue()) {
            return false;
        }

        branchCounters.put(counterKey, branchCount + 1);
        return true;
    }
}
