/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.test;

import static com.oracle.svm.hosted.test.VerifyReflectionUsage.Justifications.CREMA_RUN_TIME_ONLY;
import static com.oracle.svm.hosted.test.VerifyReflectionUsage.Justifications.GUEST_CONTEXT_ONLY;
import static com.oracle.svm.hosted.test.VerifyReflectionUsage.Justifications.JDWP_RUN_TIME_ONLY;
import static com.oracle.svm.hosted.test.VerifyReflectionUsage.Justifications.NI_HOSTED_IMPLEMENTATION;
import static com.oracle.svm.hosted.test.VerifyReflectionUsage.Justifications.SHARED_CODE;
import static com.oracle.svm.hosted.test.VerifyReflectionUsage.Justifications.SVM_CONFIGURE_TOOL;
import static com.oracle.svm.hosted.test.VerifyReflectionUsage.Justifications.SVM_RUN_TIME_ONLY;
import static com.oracle.svm.hosted.test.VerifyReflectionUsage.Justifications.SVM_TESTING_TOOL;
import static com.oracle.svm.hosted.test.VerifyReflectionUsage.Justifications.TERMINUS_HELPER;
import static com.oracle.svm.hosted.test.VerifyReflectionUsage.Justifications.TERMINUS_OBSOLETED;

import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import com.oracle.svm.shared.util.ClassUtil;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Verify that SVM does not use core reflection in hosted code. The purpose is to prepare the SVM
 * code base for project Terminus (GR-66203) and avoid regression along the way.
 * <p>
 * Reflection usage is problematic for code that runs hosted, because with Terminus everything that
 * impacts the state of the image must run through an Espresso Context. Core reflection bypasses
 * this context and might drag state from the builder VM ("host") into the application image.
 * Furthermore, we cannot assume to get a {@link Class} object for every {@link ResolvedJavaType}
 * because the corresponding {@link Class} might not be present in the host. (With Terminus, the
 * "host" might not be a HotSpot VM, but a native image itself that follows a closed-world
 * approach.) For code that is executed only at run time, reflection usage is ok. (Note that
 * run-time only code could, e.g., use {@link com.oracle.svm.core.hub.DynamicHub} instead of
 * {@link java.lang.Class}. Although this is not required nor recommended in general, it can help
 * understanding which code is run-time only.)
 * <p>
 * This verification can run in different {@linkplain VerifyReflectionUsageBase#MODE modes} that can
 * help with maintenance and keeping the exclude lists clean.
 * <p>
 * This implementation is still limited. It currently only supports checking for calls to methods of
 * {@link java.lang.Class}. This will likely be expanded in the future to other reflection classes
 * like {@link java.lang.reflect.Method} and {@link java.lang.reflect.Field}.
 * <p>
 * The exclude lists only support exclusion by class name. If need be, this can be expanded to only
 * allow specific methods to use reflection, or to exclude whole packages.
 * <p>
 * There are different exclude lists. See the JavaDoc of {@link #UNHANDLED_EXCLUDED_CLASSES} and
 * {@link #JUSTIFIED_EXCLUDED_CLASSES} for their purpose.
 */
public class VerifyReflectionUsage extends VerifyReflectionUsageBase {

    /**
     * Verifier-local extension point for suites that need to replace or extend this verifier.
     */
    public interface Provider {
        VerifyReflectionUsage createVerifier();
    }

    /// List of [unhandled class-level exceptions][VerifyReflectionUsageBase.UnhandledExcludeEntry].
    /// Ideally, this list becomes empty. There are several approaches get an entry removed form
    /// this list:
    /// - replace the reflection usage with JVMCI or similar alternatives
    /// - move them to the [#JUSTIFIED_EXCLUDED_CLASSES]
    /// - adjust this verifier to ignore certain patterns that are known to be acceptable
    private static final List<UnhandledExcludeEntry> UNHANDLED_EXCLUDED_CLASSES = List.of(
                    clazz("com.oracle.graal.pointsto.AbstractAnalysisEngine"),
                    clazz("com.oracle.graal.pointsto.flow.builder.TypeFlowBuilder"),
                    clazz("com.oracle.graal.pointsto.flow.builder.TypeFlowGraphBuilder"),
                    method("com.oracle.graal.pointsto.heap.AbstractImageHeapSnippetReflectionProvider", "getInjectedNodeIntrinsicParameter"),
                    clazz("com.oracle.graal.pointsto.inspect.http.InspectFeature"),
                    clazz("com.oracle.graal.pointsto.reports.ObjectTreePrinter"),
                    clazz("com.oracle.graal.pointsto.standalone.features.StandaloneAnalysisFeatureManager"),
                    clazz("com.oracle.graal.pointsto.standalone.PointsToAnalyzer"),
                    clazz("com.oracle.graal.pointsto.util.PointsToOptionParser"),
                    clazz("com.oracle.svm.core.allocationprofile.AllocationCounter"),
                    clazz("com.oracle.svm.core.c.function.GraalIsolateHeader"),
                    clazz("com.oracle.svm.core.c.libc.LibCBase"),
                    clazz("com.oracle.svm.core.c.NonmovableArrays"),
                    clazz("com.oracle.svm.core.classinitialization.ClassInitializationInfo"),
                    clazz("com.oracle.svm.core.code.FrameSourceInfo"),
                    clazz("com.oracle.svm.core.code.RuntimeMetadataDecoderImpl"),
                    clazz("com.oracle.svm.core.dcmd.DCmdArguments"),
                    clazz("com.oracle.svm.core.debug.BFDNameProvider"),
                    clazz("com.oracle.svm.core.debug.BFDNameProvider$BFDMangler"),
                    clazz("com.oracle.svm.core.foreign.RuntimeSystemLookup"),
                    clazz("com.oracle.svm.core.foreign.Util_java_lang_foreign_SymbolLookup"),
                    clazz("com.oracle.svm.core.graal.aarch64.SubstrateAArch64Backend$SubstrateAArch64ComputedIndirectCallOp"),
                    clazz("com.oracle.svm.core.graal.amd64.SubstrateAMD64Backend$SubstrateAMD64ComputedIndirectCallOp"),
                    clazz("com.oracle.svm.core.graal.jdk.SubstrateArraycopySnippets"),
                    clazz("com.oracle.svm.core.graal.llvm.NodeLLVMBuilder"),
                    clazz("com.oracle.svm.core.graal.llvm.runtime.LLVMExceptionUnwind"),
                    clazz("com.oracle.svm.core.graal.meta.DynamicHubOffsets"),
                    clazz("com.oracle.svm.core.graal.meta.KnownOffsets"),
                    clazz("com.oracle.svm.core.graal.meta.SharedConstantReflectionProvider"),
                    clazz("com.oracle.svm.core.graal.meta.SubstrateReplacements"),
                    clazz("com.oracle.svm.core.graal.meta.SubstrateReplacements$SnippetInlineInvokePlugin"),
                    clazz("com.oracle.svm.core.graal.meta.SubstrateSnippetReflectionProvider"),
                    clazz("com.oracle.svm.core.graal.snippets.NonSnippetLowerings"),
                    clazz("com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets"),
                    clazz("com.oracle.svm.core.heap.dump.HeapDumpWriter"),
                    clazz("com.oracle.svm.core.hub.PredefinedClassesSupport"),
                    clazz("com.oracle.svm.core.hub.ReferenceType"),
                    clazz("com.oracle.svm.core.hub.registry.AbstractClassRegistry"),
                    clazz("com.oracle.svm.core.hub.registry.AbstractRuntimeClassRegistry"),
                    clazz("com.oracle.svm.core.hub.registry.ClassRegistries"),
                    clazz("com.oracle.svm.core.image.DisallowedImageHeapObjects"),
                    clazz("com.oracle.svm.core.invoke.MethodHandleUtils"),
                    clazz("com.oracle.svm.core.jdk.BacktraceDecoder"),
                    clazz("com.oracle.svm.core.jdk.BacktraceVisitor"),
                    clazz("com.oracle.svm.core.jdk.BufferAddressTransformer"),
                    clazz("com.oracle.svm.core.jdk.BuildStackTraceVisitor"),
                    clazz("com.oracle.svm.core.jdk.ContainsVerifyJars"),
                    clazz("com.oracle.svm.core.jdk.GetLatestUserDefinedClassLoaderVisitor"),
                    clazz("com.oracle.svm.core.jdk.JavaIOClassCachePresent"),
                    clazz("com.oracle.svm.core.jdk.JavaLoggingModule"),
                    clazz("com.oracle.svm.core.jdk.localization.BundleContentSubstitutedLocalizationSupport"),
                    clazz("com.oracle.svm.core.jdk.localization.bundles.DelayedBundle"),
                    clazz("com.oracle.svm.core.jdk.localization.BundleSerializationUtils"),
                    clazz("com.oracle.svm.core.jdk.localization.LocalizationSupport"),
                    clazz("com.oracle.svm.core.jdk.management.ManagementSupport"),
                    clazz("com.oracle.svm.core.jdk.management.ManagementSupport$MXBeans"),
                    clazz("com.oracle.svm.core.jdk.Resources"),
                    clazz("com.oracle.svm.core.jdk.Resources$FlatModuleResourceKey"),
                    clazz("com.oracle.svm.core.jdk.Resources$LoaderModuleResourceKey"),
                    clazz("com.oracle.svm.core.jdk.Resources$ModuleInstanceResourceKey"),
                    clazz("com.oracle.svm.core.jdk.Resources$ModuleNameResourceKey"),
                    clazz("com.oracle.svm.core.jdk.resources.MissingResourceRegistrationUtils"),
                    clazz("com.oracle.svm.core.jdk.SecurityProvidersSupport"),
                    clazz("com.oracle.svm.core.jdk.StackAccessControlContextVisitor"),
                    clazz("com.oracle.svm.core.jdk.UninterruptibleUtils$AtomicBoolean"),
                    clazz("com.oracle.svm.core.jdk.UninterruptibleUtils$AtomicInteger"),
                    clazz("com.oracle.svm.core.jdk.UninterruptibleUtils$AtomicLong"),
                    clazz("com.oracle.svm.core.jdk.UninterruptibleUtils$AtomicPointer"),
                    clazz("com.oracle.svm.core.jdk.UninterruptibleUtils$AtomicReference"),
                    clazz("com.oracle.svm.core.jfr.events.ThreadParkEvent"),
                    clazz("com.oracle.svm.core.jfr.traceid.JfrTraceId"),
                    clazz("com.oracle.svm.core.jni.access.JNIAccessibleMember"),
                    clazz("com.oracle.svm.core.jni.access.JNIReflectionDictionary"),
                    clazz("com.oracle.svm.core.jni.functions.JNIFunctions"),
                    clazz("com.oracle.svm.core.jni.functions.JNIFunctions$Support"),
                    clazz("com.oracle.svm.core.jni.MissingJNIRegistrationUtils"),
                    clazz("com.oracle.svm.core.metadata.MetadataTracer"),
                    clazz("com.oracle.svm.core.methodhandles.MethodHandleIntrinsicImpl"),
                    clazz("com.oracle.svm.core.methodhandles.Util_java_lang_invoke_MethodHandle"),
                    clazz("com.oracle.svm.core.methodhandles.Util_java_lang_invoke_MethodHandleNatives"),
                    clazz("com.oracle.svm.core.MissingRegistrationSupport"),
                    clazz("com.oracle.svm.core.MissingRegistrationUtils"),
                    clazz("com.oracle.svm.core.monitor.MultiThreadedMonitorSupport"),
                    clazz("com.oracle.svm.core.option.GCOptionValue"),
                    clazz("com.oracle.svm.core.option.RuntimeOptionsSupportImpl"),
                    clazz("com.oracle.svm.core.PreMainSupport$NativeImageNoOpRuntimeInstrumentation"),
                    clazz("com.oracle.svm.core.reflect.AbstractCremaAccessor"),
                    clazz("com.oracle.svm.core.reflect.fieldaccessor.UnsafeFieldAccessorImpl"),
                    clazz("com.oracle.svm.core.reflect.MissingReflectionRegistrationUtils"),
                    clazz("com.oracle.svm.core.reflect.proxy.DynamicProxySupport"),
                    clazz("com.oracle.svm.core.reflect.ReflectionAccessorHolder"),
                    clazz("com.oracle.svm.core.reflect.RuntimeMetadataDecoder$ElementDescriptor"),
                    clazz("com.oracle.svm.core.reflect.serialize.MissingSerializationRegistrationUtils"),
                    clazz("com.oracle.svm.core.reflect.serialize.SerializationSupport"),
                    clazz("com.oracle.svm.core.reflect.SubstrateMethodAccessor"),
                    clazz("com.oracle.svm.core.reflect.target.ReflectionObjectFactory"),
                    clazz("com.oracle.svm.core.RuntimeAssertionsSupport"),
                    clazz("com.oracle.svm.core.snippets.ImplicitExceptions"),
                    clazz("com.oracle.svm.core.snippets.SnippetRuntime"),
                    clazz("com.oracle.svm.core.snippets.SnippetRuntime$SubstrateForeignCallDescriptor"),
                    clazz("com.oracle.svm.core.util.Counter"),
                    clazz("com.oracle.svm.core.util.LazyFinalReference"),
                    clazz("com.oracle.svm.driver.APIOptionHandler"),
                    clazz("com.oracle.svm.driver.BundleSupport"),
                    clazz("com.oracle.svm.driver.launcher.BundleLauncher"),
                    clazz("com.oracle.svm.driver.NativeImage"),
                    clazz("com.oracle.svm.graal.GraalCompilerSupport"),
                    clazz("com.oracle.svm.graal.hosted.FieldsOffsetsFeature"),
                    clazz("com.oracle.svm.graal.hosted.GraalCompilerFeature"),
                    clazz("com.oracle.svm.graal.hosted.runtimecompilation.GraalGraphObjectReplacer"),
                    clazz("com.oracle.svm.graal.isolated.IsolateAwareConstantReflectionProvider"),
                    clazz("com.oracle.svm.graal.isolated.IsolatedSpeculationLog"),
                    clazz("com.oracle.svm.graal.meta.SubstrateType"),
                    clazz("com.oracle.svm.graal.substitutions.Target_jdk_graal_compiler_debug_DebugContext_Immutable$ClearImmutableCache"),
                    clazz("com.oracle.svm.hosted.ameta.CustomTypeFieldHandler"),
                    clazz("com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport"),
                    clazz("com.oracle.svm.hosted.analysis.DynamicHubInitializer"),
                    clazz("com.oracle.svm.hosted.analysis.NativeImagePointsToAnalysis"),
                    clazz("com.oracle.svm.hosted.annotation.AnnotationFeature"),
                    clazz("com.oracle.svm.hosted.AutomaticallyRegisteredImageSingletonHandler"),
                    clazz("com.oracle.svm.hosted.c.CConstantValueSupportImpl"),
                    clazz("com.oracle.svm.hosted.c.CGlobalDataFeature"),
                    clazz("com.oracle.svm.hosted.c.info.InfoTreeBuilder"),
                    clazz("com.oracle.svm.hosted.c.libc.HostedLibCBase"),
                    clazz("com.oracle.svm.hosted.c.NativeLibraries"),
                    clazz("com.oracle.svm.hosted.classinitialization.ClassInitializationFeature"),
                    clazz("com.oracle.svm.hosted.classinitialization.ClassInitializationSupport"),
                    clazz("com.oracle.svm.hosted.ClassLoaderSupportImpl"),
                    clazz("com.oracle.svm.hosted.classloading.ClassRegistryFeature"),
                    clazz("com.oracle.svm.hosted.ClassNewInstanceFeature"),
                    clazz("com.oracle.svm.hosted.ClassPredefinitionFeature"),
                    clazz("com.oracle.svm.hosted.code.AnalysisToHostedGraphTransplanter"),
                    clazz("com.oracle.svm.hosted.code.CEntryPointLiteralFeature$CEntryPointLiteralObjectReplacer"),
                    clazz("com.oracle.svm.hosted.code.RestrictHeapAccessCalleesFeature"),
                    clazz("com.oracle.svm.hosted.code.RuntimeMetadataEncoderImpl"),
                    clazz("com.oracle.svm.hosted.config.JNIRegistryAdapter"),
                    clazz("com.oracle.svm.hosted.config.ReflectionRegistryAdapter"),
                    clazz("com.oracle.svm.hosted.config.RegistryAdapter"),
                    clazz("com.oracle.svm.hosted.DumpIsolateCreationOnlyOptionsFeature"),
                    clazz("com.oracle.svm.hosted.dynamicaccessinference.ConstantExpressionAnalyzer"),
                    clazz("com.oracle.svm.hosted.dynamicaccessinference.ConstantExpressionAnalyzer$CompileTimeArrayConstant"),
                    clazz("com.oracle.svm.hosted.dynamicaccessinference.ConstantExpressionRegistry"),
                    clazz("com.oracle.svm.hosted.dynamicaccessinference.StrictDynamicAccessInferenceFeature$StrictReflectionInferencePlugins$1"),
                    clazz("com.oracle.svm.hosted.dynamicaccessinference.StrictDynamicAccessInferenceFeature$StrictResourceInferencePlugins"),
                    clazz("com.oracle.svm.hosted.ExceptionSynthesizer"),
                    clazz("com.oracle.svm.hosted.FeatureHandler"),
                    clazz("com.oracle.svm.hosted.FeatureImpl$BeforeAnalysisAccessImpl"),
                    clazz("com.oracle.svm.hosted.foreign.ForeignFunctionsConfigurationParser"),
                    clazz("com.oracle.svm.hosted.foreign.ForeignFunctionsFeature"),
                    clazz("com.oracle.svm.hosted.foreign.ForeignFunctionsFeature$DirectUpcallStubFactory"),
                    clazz("com.oracle.svm.hosted.gc.shared.NativeGCAccessedFields"),
                    clazz("com.oracle.svm.hosted.heap.PodFeature"),
                    clazz("com.oracle.svm.hosted.HKDFSupportFeature"),
                    clazz("com.oracle.svm.hosted.image.DisallowedImageHeapObjectFeature"),
                    clazz("com.oracle.svm.hosted.image.NativeImage"),
                    clazz("com.oracle.svm.hosted.image.NativeImageHeap"),
                    clazz("com.oracle.svm.hosted.image.ObjectGroupHistogram"),
                    clazz("com.oracle.svm.hosted.image.PreserveOptionsSupport"),
                    clazz("com.oracle.svm.hosted.image.sources.SourceCache"),
                    clazz("com.oracle.svm.hosted.ImageClassLoader"),
                    clazz("com.oracle.svm.hosted.imagelayer.AccessImageSingletonFeature"),
                    clazz("com.oracle.svm.hosted.imagelayer.ImageSingletonSlotData"),
                    clazz("com.oracle.svm.hosted.InstrumentFeature"),
                    clazz("com.oracle.svm.hosted.InternalResourceAccess"),
                    clazz("com.oracle.svm.hosted.jdk.AtomicFieldUpdaterFeature"),
                    clazz("com.oracle.svm.hosted.jdk.HostedClassLoaderPackageManagement"),
                    clazz("com.oracle.svm.hosted.jdk.JDKRegistrations"),
                    clazz("com.oracle.svm.hosted.jdk.localization.CharsetSubstitutionsFeature"),
                    clazz("com.oracle.svm.hosted.jdk.localization.LocalizationFeature"),
                    clazz("com.oracle.svm.hosted.jdk.VarHandleFeature"),
                    clazz("com.oracle.svm.hosted.jfr.JfrEventSubstitution"),
                    clazz("com.oracle.svm.hosted.jni.JNIAccessFeature"),
                    clazz("com.oracle.svm.hosted.jni.JNIAccessFeature$JNIRuntimeAccessibilitySupportImpl"),
                    clazz("com.oracle.svm.hosted.lambda.LambdaParser"),
                    clazz("com.oracle.svm.hosted.LinkAtBuildTimeSupport"),
                    clazz("com.oracle.svm.hosted.LoggingFeature"),
                    clazz("com.oracle.svm.hosted.meta.HostedMethod$1"),
                    clazz("com.oracle.svm.hosted.methodhandles.MethodHandleFeature"),
                    clazz("com.oracle.svm.hosted.methodhandles.MethodHandleInvokerRenamingSubstitutionProcessor"),
                    clazz("com.oracle.svm.hosted.ModuleLayerFeature$ModuleLayerFeatureUtils"),
                    clazz("com.oracle.svm.hosted.ModuleLayerFeature$PackageType"),
                    clazz("com.oracle.svm.hosted.NativeImageClassLoader"),
                    clazz("com.oracle.svm.hosted.NativeImageClassLoaderSupport"),
                    clazz("com.oracle.svm.hosted.NativeImageClassLoaderSupport$LoadClassHandler"),
                    clazz("com.oracle.svm.hosted.NativeImageGenerator"),
                    clazz("com.oracle.svm.hosted.NativeImageGeneratorRunner"),
                    clazz("com.oracle.svm.hosted.NativeImageSystemClassLoader"),
                    clazz("com.oracle.svm.hosted.option.HostedOptionParser"),
                    clazz("com.oracle.svm.hosted.option.RuntimeOptionFeature"),
                    clazz("com.oracle.svm.hosted.phases.AnalysisGraphBuilderPhase$AnalysisBytecodeParser"),
                    clazz("com.oracle.svm.hosted.phases.CInterfaceEnumTool"),
                    clazz("com.oracle.svm.hosted.phases.CInterfaceInvocationPlugin"),
                    clazz("com.oracle.svm.hosted.phases.DynamicAccessDetectionPhase"),
                    clazz("com.oracle.svm.hosted.phases.HostedGraphKit"),
                    clazz("com.oracle.svm.hosted.phases.InlineBeforeAnalysisPolicyUtils$AccumulativeInlineScope"),
                    clazz("com.oracle.svm.hosted.phases.SharedGraphBuilderPhase$SharedBytecodeParser"),
                    clazz("com.oracle.svm.hosted.phases.SharedGraphBuilderPhase$SharedBytecodeParser$BootstrapMethodHandler"),
                    clazz("com.oracle.svm.hosted.reflect.proxy.ProxyRegistry"),
                    clazz("com.oracle.svm.hosted.reflect.proxy.ProxyRenamingSubstitutionProcessor"),
                    clazz("com.oracle.svm.hosted.reflect.RecordUtils"),
                    clazz("com.oracle.svm.hosted.reflect.ReflectionDataBuilder$ClassAccess"),
                    clazz("com.oracle.svm.hosted.reflect.ReflectionFeature"),
                    clazz("com.oracle.svm.hosted.reflect.serialize.SerializationBuilder"),
                    clazz("com.oracle.svm.hosted.reflect.serialize.SerializationFeature"),
                    clazz("com.oracle.svm.hosted.ResourcesFeature"),
                    clazz("com.oracle.svm.hosted.ResourcesFeature$1"),
                    clazz("com.oracle.svm.hosted.ResourcesFeature$ResourceCollectorImpl"),
                    clazz("com.oracle.svm.hosted.ResourcesFeature$ResourcesRegistryImpl"),
                    clazz("com.oracle.svm.hosted.SecurityServicesFeature"),
                    clazz("com.oracle.svm.hosted.snippets.ReflectionPlugins"),
                    clazz("com.oracle.svm.hosted.snippets.ReflectionPlugins$3"),
                    clazz("com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor"),
                    clazz("com.oracle.svm.hosted.substitute.AutomaticUnsafeTransformationSupport"),
                    clazz("com.oracle.svm.hosted.SubstitutionReportFeature"),
                    clazz("com.oracle.svm.hosted.SVMHost"),
                    clazz("com.oracle.svm.hosted.VectorAPIFeature$WarmupData"),
                    clazz("com.oracle.svm.interpreter.classfile.ClassFile"),
                    clazz("com.oracle.svm.interpreter.CremaRuntimeAccess"),
                    clazz("com.oracle.svm.interpreter.CremaSupportImpl"),
                    clazz("com.oracle.svm.interpreter.InterpreterDirectivesSupportImpl"),
                    clazz("com.oracle.svm.interpreter.InterpreterToVM"),
                    clazz("com.oracle.svm.interpreter.metadata.Bytecodes"),
                    clazz("com.oracle.svm.interpreter.metadata.CremaResolvedObjectType"),
                    clazz("com.oracle.svm.interpreter.metadata.InterpreterConstantPool$DynamicConstantError"),
                    clazz("com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType"),
                    clazz("com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType"),
                    clazz("com.oracle.svm.interpreter.metadata.serialization.ReaderImpl"),
                    clazz("com.oracle.svm.interpreter.metadata.serialization.Serializers"),
                    clazz("com.oracle.svm.interpreter.metadata.serialization.WriterImpl"),
                    clazz("com.oracle.svm.polyglot.groovy.GroovyIndyInterfaceFeature"),
                    clazz("com.oracle.svm.shared.singletons.ImageSingletonsSupportImpl$HostedManagement"),
                    clazz("com.oracle.svm.truffle.api.SubstrateTruffleRuntime"),
                    clazz("com.oracle.svm.truffle.HasStackSpaceCheck"),
                    clazz("com.oracle.svm.truffle.nfi.NativeObjectReplacer"),
                    clazz("com.oracle.svm.truffle.OptionalTrufflePolyglotGuestFeatureEnabled"),
                    clazz("com.oracle.svm.truffle.PolyglotIsolateCreateSupport"),
                    clazz("com.oracle.svm.truffle.PolyglotIsolateGuestFeature"),
                    clazz("com.oracle.svm.truffle.StaticPropertyOffsetTransformer"),
                    clazz("com.oracle.svm.truffle.Target_com_oracle_truffle_api_dsl_InlineSupport_UnsafeField$OffsetComputer"),
                    clazz("com.oracle.svm.truffle.tck.AbstractMethodListParser"),
                    clazz("com.oracle.svm.truffle.tck.AbstractMethodListParser$UnsupportedPlatformException"),
                    clazz("com.oracle.svm.truffle.tck.PermissionsFeature"),
                    clazz("com.oracle.svm.truffle.TruffleBaseFeature"),
                    clazz("com.oracle.svm.truffle.TruffleBaseFeature$IsCreateProcessDisabled"),
                    clazz("com.oracle.svm.truffle.TruffleBaseFeature$PossibleReplaceCandidatesSubtypeHandler"),
                    clazz("com.oracle.svm.truffle.TruffleBaseFeature$StaticObjectSupport$StaticObjectArrayBasedSupport"),
                    clazz("com.oracle.svm.truffle.TruffleFeature"));

    protected static final class Justifications {
        public static final String NI_HOSTED_IMPLEMENTATION = "Reflection used on hosted implementation classes";
        public static final String SHARED_CODE = "Shared code that can be used in the guest and the builder";
        public static final String TERMINUS_HELPER = "Temporary Terminus code that will be removed";
        public static final String TERMINUS_OBSOLETED = "Code that will be obsoleted by Terminus";
        public static final String JDWP_RUN_TIME_ONLY = "JDWP code called at run time only";
        public static final String CREMA_RUN_TIME_ONLY = "Crema code called at run time only";
        public static final String SVM_RUN_TIME_ONLY = "SVM code called at run time only";
        public static final String SVM_CONFIGURE_TOOL = "Tool not part of the native image builder";
        public static final String SVM_TESTING_TOOL = "Tool used for testing Native Image";
        public static final String GUEST_CONTEXT_ONLY = "Package contains only guest context code";
    }

    /**
     * {@linkplain VerifyReflectionUsageBase.JustifiedExcludeEntry Justified excludes}. Each entry
     * needs a justification why this usage is ok. Most of the entries are due to code that is known
     * to be executed at run time only.
     */
    private static final List<JustifiedExcludeEntry> JUSTIFIED_EXCLUDED_CLASSES = List.of(
                    clazz("com.oracle.graal.pointsto.standalone.MethodConfigReader", NI_HOSTED_IMPLEMENTATION),
                    clazz("com.oracle.graal.pointsto.standalone.StandaloneVMAccessSupport", NI_HOSTED_IMPLEMENTATION),
                    clazz("com.oracle.svm.configure.ConfigurationTypeDescriptor", SVM_CONFIGURE_TOOL),
                    clazz("com.oracle.svm.configure.filters.ModuleFilterTools", SVM_CONFIGURE_TOOL),
                    clazz("com.oracle.svm.configure.ReflectionConfigurationParser", SVM_CONFIGURE_TOOL),
                    clazz("com.oracle.svm.configure.UnresolvedAccessCondition", SVM_CONFIGURE_TOOL),
                    clazz("com.oracle.svm.core.BuilderUtil", NI_HOSTED_IMPLEMENTATION),
                    clazz("com.oracle.svm.core.gc.shared.NativeGCOptions", NI_HOSTED_IMPLEMENTATION),
                    clazz("com.oracle.svm.core.gc.shared.NativeGCOptions$HostedArgumentsSupplier", NI_HOSTED_IMPLEMENTATION),
                    clazz("com.oracle.svm.core.gc.shared.NativeGCOptions$RuntimeArgumentsSupplier", NI_HOSTED_IMPLEMENTATION),
                    clazz("com.oracle.svm.core.JavaMainWrapper$JavaMainSupport", NI_HOSTED_IMPLEMENTATION),
                    clazz("com.oracle.svm.core.jdk.AtomicFieldUpdaterAccessCheck", SVM_RUN_TIME_ONLY),
                    clazz("com.oracle.svm.core.jdk.LayeredModuleSingleton", TERMINUS_OBSOLETED),
                    clazz("com.oracle.svm.core.jdk.StackWalkerUtil", SVM_RUN_TIME_ONLY),
                    clazz("com.oracle.svm.core.jdk.Target_java_net_URL$DefaultFactory", SVM_RUN_TIME_ONLY),
                    clazz("com.oracle.svm.core.jfr.JfrTypeRepository", SVM_RUN_TIME_ONLY),
                    pkg("com.oracle.svm.guest", GUEST_CONTEXT_ONLY),
                    method("com.oracle.svm.hosted.imagelayer.HostedImageLayerBuildingSupport", "validateSingletonRegistration", NI_HOSTED_IMPLEMENTATION),
                    clazz("com.oracle.svm.hosted.ModuleLayerFeature", TERMINUS_OBSOLETED),
                    clazz("com.oracle.svm.interpreter.metadata.AccessChecks", CREMA_RUN_TIME_ONLY),
                    clazz("com.oracle.svm.interpreter.metadata.CremaMethodAccess", CREMA_RUN_TIME_ONLY),
                    clazz("com.oracle.svm.jdwp.bridge.JDWPJNIConfig$EnumMarshaller", JDWP_RUN_TIME_ONLY),
                    clazz("com.oracle.svm.jdwp.bridge.jniutils.JNIExceptionWrapper", JDWP_RUN_TIME_ONLY),
                    clazz("com.oracle.svm.jdwp.bridge.jniutils.JNIUtil", JDWP_RUN_TIME_ONLY),
                    clazz("com.oracle.svm.jdwp.bridge.nativebridge.JNIConfig", JDWP_RUN_TIME_ONLY),
                    clazz("com.oracle.svm.jdwp.bridge.nativebridge.JNIConfig$Builder", JDWP_RUN_TIME_ONLY),
                    clazz("com.oracle.svm.jdwp.bridge.nativebridge.TypeLiteral", JDWP_RUN_TIME_ONLY),
                    clazz("com.oracle.svm.jdwp.bridge.TagConstants", JDWP_RUN_TIME_ONLY),
                    clazz("com.oracle.svm.jdwp.resident.impl.AbstractJDWPJavaFrameInfoVisitor", JDWP_RUN_TIME_ONLY),
                    clazz("com.oracle.svm.jdwp.resident.impl.ResidentJDWP", JDWP_RUN_TIME_ONLY),
                    clazz("com.oracle.svm.jdwp.server.JDWPServerJNIEntryPointsFeature", JDWP_RUN_TIME_ONLY),
                    pkg("com.oracle.svm.junit", SVM_TESTING_TOOL),
                    clazz("com.oracle.svm.shared.option.AccumulatingLocatableMultiOptionValue", SHARED_CODE),
                    clazz("com.oracle.svm.shared.option.CommonOptionParser", SHARED_CODE),
                    clazz("com.oracle.svm.shared.option.OptionClassFilter", SHARED_CODE),
                    clazz("com.oracle.svm.shared.option.OptionUtils", SHARED_CODE),
                    clazz("com.oracle.svm.shared.option.ReplacingLocatableMultiOptionValue", SHARED_CODE),
                    clazz("com.oracle.svm.shared.option.SubstrateOptionsParser", SHARED_CODE),
                    clazz("com.oracle.svm.shared.util.ClassUtil", SHARED_CODE),
                    clazz("com.oracle.svm.shared.util.ModuleSupport", SHARED_CODE),
                    clazz("com.oracle.svm.shared.util.ModuleSupport$Access$1", SHARED_CODE),
                    clazz("com.oracle.svm.shared.util.ModuleSupport$Access$2", SHARED_CODE),
                    clazz("com.oracle.svm.shared.util.ReflectionUtil", SHARED_CODE),
                    clazz("com.oracle.svm.shared.util.SubstrateUtil", SHARED_CODE),
                    clazz("com.oracle.svm.shared.util.VMError", NI_HOSTED_IMPLEMENTATION),
                    pkg("com.oracle.svm.thirdparty.gson", GUEST_CONTEXT_ONLY),
                    clazz("com.oracle.svm.util.AnnotatedObjectAccess", TERMINUS_HELPER),
                    clazz("com.oracle.svm.util.GuestAccess", TERMINUS_HELPER),
                    clazz("com.oracle.svm.util.HostModuleUtil", TERMINUS_HELPER));

    private static List<List<? extends ExcludeEntry>> allExcludeLists(List<List<? extends ExcludeEntry>> additionalExcludeLists) {
        List<List<? extends ExcludeEntry>> allExcludeLists = new java.util.ArrayList<>(2 + additionalExcludeLists.size());
        allExcludeLists.add(UNHANDLED_EXCLUDED_CLASSES);
        allExcludeLists.add(JUSTIFIED_EXCLUDED_CLASSES);
        allExcludeLists.addAll(additionalExcludeLists);
        return allExcludeLists;
    }

    protected VerifyReflectionUsage(List<List<? extends ExcludeEntry>> additionalExcludeLists) {
        super(allExcludeLists(additionalExcludeLists));
    }

    protected VerifyReflectionUsage() {
        this(List.of());
    }

    public static VerifyReflectionUsage create() {
        VerifyReflectionUsage verifier = null;
        for (Provider provider : ServiceLoader.load(Provider.class)) {
            GraalError.guarantee(verifier == null, "Only one %s provider is supported", Provider.class.getName());
            verifier = provider.createVerifier();
        }
        return verifier == null ? new VerifyReflectionUsage() : verifier;
    }

    private record RestrictedReflectionClasses(Class<?> clazz, Set<String> allowedMethodNames) {
        RestrictedReflectionClasses(Class<?> clazz, String... allowedMethodNames) {
            this(clazz, Set.of(allowedMethodNames));
        }
    }

    private static final RestrictedReflectionClasses[] RESTRICTED_CLASSES_ARRAY = new RestrictedReflectionClasses[]{
                    new RestrictedReflectionClasses(Class.class,
                                    // injected by javac into <clinit>
                                    "desiredAssertionStatus",
                                    // primarily used for debug output
                                    "toString",
                                    // primarily used for debug output
                                    "getName",
                                    // primarily used for debug output
                                    "getSimpleName",
                                    // checks a concrete object, must be ok
                                    "isInstance",
                                    // casts a concrete object, must be ok
                                    "cast"),
                    new RestrictedReflectionClasses(Module.class,
                                    // primarily used for debug output
                                    "toString"),
                    new RestrictedReflectionClasses(Package.class,
                                    // primarily used for debug output
                                    "toString")
    };

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        MetaAccessProvider metaAccess = context.getMetaAccess();
        recordProcessedMethod(graph.method());
        if (isRuntimeOnly(graph, metaAccess)) {
            // no need to check runtime-only methods
            return;
        }
        ResolvedJavaMethod caller = graph.method();

        for (var restrictedClass : RESTRICTED_CLASSES_ARRAY) {
            ResolvedJavaType classType = metaAccess.lookupJavaType(restrictedClass.clazz);

            for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
                ResolvedJavaMethod callee = t.targetMethod();
                ResolvedJavaType calleeDeclaringClass = callee.getDeclaringClass();

                if (classType.equals(calleeDeclaringClass)) {
                    if (restrictedClass.allowedMethodNames.contains(callee.getName())) {
                        // allowed method
                        continue;
                    }
                    if (isExcluded(caller)) {
                        // the whole class is excluded, so no need to check other callsites
                        return;
                    }
                    switch (MODE) {
                        case DEFAULT, CHECK_EXCLUDE_LIST ->
                            throw new VerificationError(t.invoke(), "Call to %s is prohibited, use JVMCI reflection instead (or update exclude list in %s.java)",
                                            callee.format("%H.%n(%p)"),
                                            ClassUtil.getUnqualifiedName(getClass()));
                        case PRINT_EXCLUDE_LIST ->
                            throw new VerificationError("  clazz(\"%s\"),", caller.format("%H"));
                        default -> throw GraalError.shouldNotReachHereUnexpectedValue(MODE);
                    }
                }
            }
        }
    }
}
