/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;

import com.oracle.truffle.dsl.processor.java.ElementUtils;

public class TruffleTypes {

    private final ProcessorContext c = ProcessorContext.getInstance();

    // Checkstyle: stop

    // Testing API
    private static final String[] EXPECT_ERROR_TYPES = new String[]{TruffleTypes.EXPECT_ERROR_CLASS_NAME1, TruffleTypes.EXPECT_ERROR_CLASS_NAME2, TruffleTypes.EXPECT_WARNING_CLASS_NAME1};
    public static final String ALWAYS_SLOW_PATH_MODE_NAME = "com.oracle.truffle.api.dsl.test.AlwaysGenerateOnlySlowPath";
    public static final String DISABLE_STATE_BITWIDTH_MODIFICATION = "com.oracle.truffle.api.dsl.test.DisableStateBitWidthModfication";
    public static final String EXPECT_WARNING_CLASS_NAME1 = "com.oracle.truffle.api.dsl.test.ExpectWarning";
    public static final String EXPECT_ERROR_CLASS_NAME1 = "com.oracle.truffle.api.dsl.test.ExpectError";
    public static final String EXPECT_ERROR_CLASS_NAME2 = "com.oracle.truffle.api.test.ExpectError";
    public static final List<String> TEST_PACKAGES = List.of("com.oracle.truffle.api.test", "com.oracle.truffle.api.instrumentation.test");

    public static final String SlowPathListener_Name = "com.oracle.truffle.api.dsl.test.SlowPathListener";
    public final DeclaredType SlowPathListener = c.getDeclaredTypeOptional(SlowPathListener_Name);
    public final DeclaredType AlwaysSlowPath = c.getDeclaredTypeOptional(ALWAYS_SLOW_PATH_MODE_NAME);
    public final DeclaredType DisableStateBitWidthModification = c.getDeclaredTypeOptional(DISABLE_STATE_BITWIDTH_MODIFICATION);
    public final List<DeclaredType> ExpectErrorTypes;
    {
        List<DeclaredType> types = new ArrayList<>(EXPECT_ERROR_TYPES.length);
        for (String errorType : EXPECT_ERROR_TYPES) {
            types.add(c.getDeclaredTypeOptional(errorType));
        }
        ExpectErrorTypes = Collections.unmodifiableList(types);
    }

    // Graal SDK
    public static final String OptionCategory_Name = "org.graalvm.options.OptionCategory";
    public static final String OptionDescriptor_Name = "org.graalvm.options.OptionDescriptor";
    public static final String OptionDescriptors_Name = "org.graalvm.options.OptionDescriptors";
    public static final String OptionKey_Name = "org.graalvm.options.OptionKey";
    public static final String OptionMap_Name = "org.graalvm.options.OptionMap";
    public static final String OptionStability_Name = "org.graalvm.options.OptionStability";
    public static final String SandboxPolicy_Name = "org.graalvm.polyglot.SandboxPolicy";

    public final DeclaredType Option = c.getDeclaredType(Option_Name);
    public final DeclaredType Option_Group = c.getDeclaredType(Option_Group_Name);
    public final DeclaredType OptionCategory = c.getDeclaredType(OptionCategory_Name);
    public final DeclaredType OptionDescriptor = c.getDeclaredType(OptionDescriptor_Name);
    public final DeclaredType OptionDescriptors = c.getDeclaredType(OptionDescriptors_Name);
    public final DeclaredType OptionKey = c.getDeclaredType(OptionKey_Name);
    public final DeclaredType OptionMap = c.getDeclaredType(OptionMap_Name);
    public final DeclaredType OptionStability = c.getDeclaredType(OptionStability_Name);
    public final DeclaredType SandboxPolicy = c.getDeclaredType(SandboxPolicy_Name);

    // Truffle API
    public static final String Assumption_Name = "com.oracle.truffle.api.Assumption";
    public static final String ContextThreadLocal_Name = "com.oracle.truffle.api.ContextThreadLocal";
    public static final String CompilerAsserts_Name = "com.oracle.truffle.api.CompilerAsserts";
    public static final String CompilerDirectives_CompilationFinal_Name = "com.oracle.truffle.api.CompilerDirectives.CompilationFinal";
    public static final String CompilerDirectives_Name = "com.oracle.truffle.api.CompilerDirectives";
    public static final String CompilerDirectives_TruffleBoundary_Name = "com.oracle.truffle.api.CompilerDirectives.TruffleBoundary";
    public static final String DenyReplace_Name = "com.oracle.truffle.api.nodes.DenyReplace";
    public static final String DirectCallNode_Name = "com.oracle.truffle.api.nodes.DirectCallNode";
    public static final String EncapsulatingNodeReference_Name = "com.oracle.truffle.api.nodes.EncapsulatingNodeReference";
    public static final String ExplodeLoop_Name = "com.oracle.truffle.api.nodes.ExplodeLoop";
    public static final String Frame_Name = "com.oracle.truffle.api.frame.Frame";
    public static final String FrameDescriptor_Name = "com.oracle.truffle.api.frame.FrameDescriptor";
    public static final String FinalBitSet_Name = "com.oracle.truffle.api.utilities.FinalBitSet";
    public static final String HostCompilerDirectives_Name = "com.oracle.truffle.api.HostCompilerDirectives";

    public static final String InternalResource_Name = "com.oracle.truffle.api.InternalResource";
    public static final String InternalResource_Id_Name = "com.oracle.truffle.api.InternalResource.Id";
    public static final String InvalidAssumptionException_Name = "com.oracle.truffle.api.nodes.InvalidAssumptionException";
    public static final String MaterializedFrame_Name = "com.oracle.truffle.api.frame.MaterializedFrame";
    public static final String Node_Child_Name = "com.oracle.truffle.api.nodes.Node.Child";
    public static final String Node_Children_Name = "com.oracle.truffle.api.nodes.Node.Children";
    public static final String Node_Name = "com.oracle.truffle.api.nodes.Node";
    public static final String NodeCost_Name = "com.oracle.truffle.api.nodes.NodeCost";
    public static final String NodeInfo_Name = "com.oracle.truffle.api.nodes.NodeInfo";
    public static final String NodeInterface_Name = "com.oracle.truffle.api.nodes.NodeInterface";
    public static final String NodeUtil_Name = "com.oracle.truffle.api.nodes.NodeUtil";
    public static final String Option_Group_Name = "com.oracle.truffle.api.Option.Group";
    public static final String Option_Name = "com.oracle.truffle.api.Option";
    public static final String Profile_Name = "com.oracle.truffle.api.profiles.Profile";
    public static final String IndirectCallNode_Name = "com.oracle.truffle.api.nodes.IndirectCallNode";
    public static final String InlinedProfile_Name = "com.oracle.truffle.api.profiles.InlinedProfile";
    public static final String InternalResourceProvider_Name = "com.oracle.truffle.api.provider.InternalResourceProvider";
    public static final String SlowPathException_Name = "com.oracle.truffle.api.nodes.SlowPathException";
    public static final String SourceSection_Name = "com.oracle.truffle.api.source.SourceSection";
    public static final String TruffleFile_FileTypeDetector_Name = "com.oracle.truffle.api.TruffleFile.FileTypeDetector";
    public static final String TruffleLanguage_ContextReference_Name = "com.oracle.truffle.api.TruffleLanguage.ContextReference";
    public static final String TruffleLanguage_LanguageReference_Name = "com.oracle.truffle.api.TruffleLanguage.LanguageReference";
    public static final String TruffleLanguage_Name = "com.oracle.truffle.api.TruffleLanguage";
    public static final String TruffleLanguageProvider_Name = "com.oracle.truffle.api.provider.TruffleLanguageProvider";
    public static final String TruffleLanguage_Registration_Name = "com.oracle.truffle.api.TruffleLanguage.Registration";
    public static final String TruffleOptions_Name = "com.oracle.truffle.api.TruffleOptions";
    public static final String TruffleOptionDescriptors_Name = "com.oracle.truffle.api.TruffleOptionDescriptors";
    public static final String UnexpectedResultException_Name = "com.oracle.truffle.api.nodes.UnexpectedResultException";
    public static final String VirtualFrame_Name = "com.oracle.truffle.api.frame.VirtualFrame";
    public static final String HostLanguage_Name = "com.oracle.truffle.polyglot.HostLanguage";

    public final DeclaredType Assumption = c.getDeclaredType(Assumption_Name);
    public final DeclaredType ContextThreadLocal = c.getDeclaredType(ContextThreadLocal_Name);
    public final DeclaredType CompilerAsserts = c.getDeclaredType(CompilerAsserts_Name);
    public final DeclaredType CompilerDirectives = c.getDeclaredType(CompilerDirectives_Name);
    public final DeclaredType CompilerDirectives_CompilationFinal = c.getDeclaredType(CompilerDirectives_CompilationFinal_Name);
    public final DeclaredType CompilerDirectives_TruffleBoundary = c.getDeclaredType(CompilerDirectives_TruffleBoundary_Name);
    public final DeclaredType DenyReplace = c.getDeclaredType(DenyReplace_Name);
    public final DeclaredType DirectCallNode = c.getDeclaredType(DirectCallNode_Name);
    public final DeclaredType EncapsulatingNodeReference = c.getDeclaredType(EncapsulatingNodeReference_Name);
    public final DeclaredType ExplodeLoop = c.getDeclaredType(ExplodeLoop_Name);
    public final DeclaredType Frame = c.getDeclaredType(Frame_Name);
    public final DeclaredType FrameDescriptor = c.getDeclaredType(FrameDescriptor_Name);
    public final DeclaredType FinalBitSet = c.getDeclaredType(FinalBitSet_Name);
    public final DeclaredType HostCompilerDirectives = c.getDeclaredType(HostCompilerDirectives_Name);
    public final DeclaredType InternalResource = c.getDeclaredType(InternalResource_Name);
    public final DeclaredType InternalResource_Id = c.getDeclaredType(InternalResource_Id_Name);
    public final DeclaredType InvalidAssumptionException = c.getDeclaredType(InvalidAssumptionException_Name);
    public final DeclaredType MaterializedFrame = c.getDeclaredType(MaterializedFrame_Name);
    public final DeclaredType Node = c.getDeclaredType(Node_Name);
    public final DeclaredType Node_Child = c.getDeclaredType(Node_Child_Name);
    public final DeclaredType Node_Children = c.getDeclaredType(Node_Children_Name);
    public final DeclaredType NodeCost = c.getDeclaredType(NodeCost_Name);
    public final DeclaredType NodeInfo = c.getDeclaredType(NodeInfo_Name);
    public final DeclaredType NodeInterface = c.getDeclaredType(NodeInterface_Name);
    public final DeclaredType NodeUtil = c.getDeclaredType(NodeUtil_Name);
    public final DeclaredType Profile = c.getDeclaredTypeOptional(Profile_Name);
    public final DeclaredType IndirectCallNode = c.getDeclaredType(IndirectCallNode_Name);
    public final DeclaredType InlinedProfile = c.getDeclaredTypeOptional(InlinedProfile_Name);
    public final DeclaredType InternalResourceProvider = c.getDeclaredType(InternalResourceProvider_Name);
    public final DeclaredType SlowPathException = c.getDeclaredType(SlowPathException_Name);
    public final DeclaredType SourceSection = c.getDeclaredType(SourceSection_Name);
    public final DeclaredType TruffleLanguage = c.getDeclaredType(TruffleLanguage_Name);
    public final DeclaredType TruffleFile_FileTypeDetector = c.getDeclaredType(TruffleFile_FileTypeDetector_Name);
    public final DeclaredType TruffleLanguage_ContextReference = c.getDeclaredType(TruffleLanguage_ContextReference_Name);
    public final DeclaredType TruffleLanguage_LanguageReference = c.getDeclaredType(TruffleLanguage_LanguageReference_Name);
    public final DeclaredType TruffleLanguageProvider = c.getDeclaredType(TruffleLanguageProvider_Name);
    public final DeclaredType TruffleLanguage_Registration = c.getDeclaredType(TruffleLanguage_Registration_Name);
    public final DeclaredType TruffleOptions = c.getDeclaredType(TruffleOptions_Name);
    public final DeclaredType TruffleOptionDescriptors = c.getDeclaredType(TruffleOptionDescriptors_Name);
    public final DeclaredType UnexpectedResultException = c.getDeclaredType(UnexpectedResultException_Name);
    public final DeclaredType VirtualFrame = c.getDeclaredType(VirtualFrame_Name);
    public final DeclaredType HostLanguage = c.getDeclaredTypeOptional(HostLanguage_Name);

    // DSL API
    public static final String Bind_Name = "com.oracle.truffle.api.dsl.Bind";
    public static final String Cached_Exclusive_Name = "com.oracle.truffle.api.dsl.Cached.Exclusive";
    public static final String Cached_Name = "com.oracle.truffle.api.dsl.Cached";
    public static final String Cached_Shared_Name = "com.oracle.truffle.api.dsl.Cached.Shared";
    public static final String CreateCast_Name = "com.oracle.truffle.api.dsl.CreateCast";
    public static final String DSLSupport_Name = "com.oracle.truffle.api.dsl.DSLSupport";
    public static final String DSLSupport_SpecializationDataNode_Name = "com.oracle.truffle.api.dsl.DSLSupport.SpecializationDataNode";
    public static final String Executed_Name = "com.oracle.truffle.api.dsl.Executed";
    public static final String ExecuteTracingSupport_Name = "com.oracle.truffle.api.dsl.ExecuteTracingSupport";
    public static final String Fallback_Name = "com.oracle.truffle.api.dsl.Fallback";
    public static final String GenerateAOT_Name = "com.oracle.truffle.api.dsl.GenerateAOT";
    public static final String GenerateAOT_Exclude_Name = "com.oracle.truffle.api.dsl.GenerateAOT.Exclude";
    public static final String GenerateAOT_Provider_Name = "com.oracle.truffle.api.dsl.GenerateAOT.Provider";
    public static final String GenerateCached_Name = "com.oracle.truffle.api.dsl.GenerateCached";
    public static final String GenerateInline_Name = "com.oracle.truffle.api.dsl.GenerateInline";
    public static final String GeneratedBy_Name = "com.oracle.truffle.api.dsl.GeneratedBy";
    public static final String GeneratePackagePrivate_Name = "com.oracle.truffle.api.dsl.GeneratePackagePrivate";
    public static final String GenerateNodeFactory_Name = "com.oracle.truffle.api.dsl.GenerateNodeFactory";
    public static final String GenerateUncached_Name = "com.oracle.truffle.api.dsl.GenerateUncached";
    public static final String Idempotent_Name = "com.oracle.truffle.api.dsl.Idempotent";
    public static final String ImplicitCast_Name = "com.oracle.truffle.api.dsl.ImplicitCast";
    public static final String ImportStatic_Name = "com.oracle.truffle.api.dsl.ImportStatic";
    public static final String Introspectable_Name = "com.oracle.truffle.api.dsl.Introspectable";
    public static final String Introspection_Name = "com.oracle.truffle.api.dsl.Introspection";
    public static final String Introspection_Provider_Name = "com.oracle.truffle.api.dsl.Introspection.Provider";
    public static final String InlineSupport_Name = "com.oracle.truffle.api.dsl.InlineSupport";
    public static final String InlineSupport_RequiredField_Name = "com.oracle.truffle.api.dsl.InlineSupport.RequiredField";
    public static final String InlineSupport_RequiredFields_Name = "com.oracle.truffle.api.dsl.InlineSupport.RequiredFields";
    public static final String InlineSupport_InlineTarget_Name = "com.oracle.truffle.api.dsl.InlineSupport.InlineTarget";
    public static final String InlineSupport_StateField_Name = "com.oracle.truffle.api.dsl.InlineSupport.StateField";
    public static final String InlineSupport_BooleanField_Name = "com.oracle.truffle.api.dsl.InlineSupport.BooleanField";
    public static final String InlineSupport_ByteField_Name = "com.oracle.truffle.api.dsl.InlineSupport.ByteField";
    public static final String InlineSupport_ShortField_Name = "com.oracle.truffle.api.dsl.InlineSupport.ShortField";
    public static final String InlineSupport_CharField_Name = "com.oracle.truffle.api.dsl.InlineSupport.CharField";
    public static final String InlineSupport_FloatField_Name = "com.oracle.truffle.api.dsl.InlineSupport.FloatField";
    public static final String InlineSupport_IntField_Name = "com.oracle.truffle.api.dsl.InlineSupport.IntField";
    public static final String InlineSupport_LongField_Name = "com.oracle.truffle.api.dsl.InlineSupport.LongField";
    public static final String InlineSupport_DoubleField_Name = "com.oracle.truffle.api.dsl.InlineSupport.DoubleField";
    public static final String InlineSupport_ReferenceField_Name = "com.oracle.truffle.api.dsl.InlineSupport.ReferenceField";
    public static final String InlineSupport_UnsafeAccessedField_Name = "com.oracle.truffle.api.dsl.InlineSupport.UnsafeAccessedField";
    public static final String NodeChild_Name = "com.oracle.truffle.api.dsl.NodeChild";
    public static final String NodeChildren_Name = "com.oracle.truffle.api.dsl.NodeChildren";
    public static final String NeverDefault_Name = "com.oracle.truffle.api.dsl.NeverDefault";
    public static final String NodeFactory_Name = "com.oracle.truffle.api.dsl.NodeFactory";
    public static final String NodeField_Name = "com.oracle.truffle.api.dsl.NodeField";
    public static final String NodeFields_Name = "com.oracle.truffle.api.dsl.NodeFields";
    public static final String NonIdempotent_Name = "com.oracle.truffle.api.dsl.NonIdempotent";
    public static final String ReportPolymorphism_Exclude_Name = "com.oracle.truffle.api.dsl.ReportPolymorphism.Exclude";
    public static final String ReportPolymorphism_Megamorphic_Name = "com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic";
    public static final String ReportPolymorphism_Name = "com.oracle.truffle.api.dsl.ReportPolymorphism";
    public static final String Specialization_Name = "com.oracle.truffle.api.dsl.Specialization";
    public static final String SpecializationStatistics_Name = "com.oracle.truffle.api.dsl.SpecializationStatistics";
    public static final String SpecializationStatistics_AlwaysEnabled_Name = "com.oracle.truffle.api.dsl.SpecializationStatistics.AlwaysEnabled";
    public static final String SpecializationStatistics_NodeStatistics_Name = "com.oracle.truffle.api.dsl.SpecializationStatistics.NodeStatistics";
    public static final String SuppressPackageWarnings_Name = "com.oracle.truffle.api.dsl.SuppressPackageWarnings";
    public static final String TypeCast_Name = "com.oracle.truffle.api.dsl.TypeCast";
    public static final String TypeCheck_Name = "com.oracle.truffle.api.dsl.TypeCheck";
    public static final String TypeSystem_Name = "com.oracle.truffle.api.dsl.TypeSystem";
    public static final String TypeSystemReference_Name = "com.oracle.truffle.api.dsl.TypeSystemReference";
    public static final String UnsupportedSpecializationException_Name = "com.oracle.truffle.api.dsl.UnsupportedSpecializationException";

    public final DeclaredType Bind = c.getDeclaredType(Bind_Name);
    public final DeclaredType Cached = c.getDeclaredType(Cached_Name);
    public final DeclaredType Cached_Exclusive = c.getDeclaredType(Cached_Exclusive_Name);
    public final DeclaredType Cached_Shared = c.getDeclaredType(Cached_Shared_Name);
    public final DeclaredType CreateCast = c.getDeclaredType(CreateCast_Name);
    public final DeclaredType DSLSupport = c.getDeclaredType(DSLSupport_Name);
    public final DeclaredType DSLSupport_SpecializationDataNode = c.getDeclaredType(DSLSupport_SpecializationDataNode_Name);
    public final DeclaredType Executed = c.getDeclaredType(Executed_Name);
    public final DeclaredType ExecuteTracingSupport = c.getDeclaredType(ExecuteTracingSupport_Name);
    public final DeclaredType Fallback = c.getDeclaredType(Fallback_Name);
    public final DeclaredType GenerateAOT = c.getDeclaredType(GenerateAOT_Name);
    public final DeclaredType GenerateAOT_Exclude = c.getDeclaredType(GenerateAOT_Exclude_Name);
    public final DeclaredType GenerateAOT_Provider = c.getDeclaredType(GenerateAOT_Provider_Name);
    public final DeclaredType GenerateCached = c.getDeclaredType(GenerateCached_Name);
    public final DeclaredType GenerateInline = c.getDeclaredType(GenerateInline_Name);
    public final DeclaredType GeneratedBy = c.getDeclaredType(GeneratedBy_Name);
    public final DeclaredType GeneratePackagePrivate = c.getDeclaredType(GeneratePackagePrivate_Name);
    public final DeclaredType GenerateNodeFactory = c.getDeclaredType(GenerateNodeFactory_Name);
    public final DeclaredType GenerateUncached = c.getDeclaredType(GenerateUncached_Name);
    public final DeclaredType Idempotent = c.getDeclaredType(Idempotent_Name);
    public final DeclaredType ImplicitCast = c.getDeclaredType(ImplicitCast_Name);
    public final DeclaredType ImportStatic = c.getDeclaredType(ImportStatic_Name);
    public final DeclaredType Introspectable = c.getDeclaredType(Introspectable_Name);
    public final DeclaredType Introspection = c.getDeclaredType(Introspection_Name);
    public final DeclaredType Introspection_Provider = c.getDeclaredType(Introspection_Provider_Name);
    public final DeclaredType InlineSupport = c.getDeclaredType(InlineSupport_Name);
    public final DeclaredType InlineSupport_RequiredField = c.getDeclaredType(InlineSupport_RequiredField_Name);
    public final DeclaredType InlineSupport_RequiredFields = c.getDeclaredType(InlineSupport_RequiredFields_Name);
    public final DeclaredType InlineSupport_InlineTarget = c.getDeclaredType(InlineSupport_InlineTarget_Name);
    public final DeclaredType InlineSupport_StateField = c.getDeclaredType(InlineSupport_StateField_Name);
    public final DeclaredType InlineSupport_ReferenceField = c.getDeclaredType(InlineSupport_ReferenceField_Name);
    public final DeclaredType InlineSupport_BooleanField = c.getDeclaredType(InlineSupport_BooleanField_Name);
    public final DeclaredType InlineSupport_ByteField = c.getDeclaredType(InlineSupport_ByteField_Name);
    public final DeclaredType InlineSupport_ShortField = c.getDeclaredType(InlineSupport_ShortField_Name);
    public final DeclaredType InlineSupport_CharField = c.getDeclaredType(InlineSupport_CharField_Name);
    public final DeclaredType InlineSupport_FloatField = c.getDeclaredType(InlineSupport_FloatField_Name);
    public final DeclaredType InlineSupport_IntField = c.getDeclaredType(InlineSupport_IntField_Name);
    public final DeclaredType InlineSupport_LongField = c.getDeclaredType(InlineSupport_LongField_Name);
    public final DeclaredType InlineSupport_DoubleField = c.getDeclaredType(InlineSupport_DoubleField_Name);
    public final DeclaredType InlineSupport_UnsafeAccessedField = c.getDeclaredType(InlineSupport_UnsafeAccessedField_Name);
    public final DeclaredType NodeChild = c.getDeclaredType(NodeChild_Name);
    public final DeclaredType NodeChildren = c.getDeclaredType(NodeChildren_Name);
    public final DeclaredType NeverDefault = c.getDeclaredType(NeverDefault_Name);
    public final DeclaredType NodeFactory = c.getDeclaredType(NodeFactory_Name);
    public final DeclaredType NodeField = c.getDeclaredType(NodeField_Name);
    public final DeclaredType NodeFields = c.getDeclaredType(NodeFields_Name);
    public final DeclaredType NonIdempotent = c.getDeclaredType(NonIdempotent_Name);
    public final DeclaredType ReportPolymorphism = c.getDeclaredType(ReportPolymorphism_Name);
    public final DeclaredType ReportPolymorphism_Exclude = c.getDeclaredType(ReportPolymorphism_Exclude_Name);
    public final DeclaredType ReportPolymorphism_Megamorphic = c.getDeclaredType(ReportPolymorphism_Megamorphic_Name);
    public final DeclaredType Specialization = c.getDeclaredType(Specialization_Name);
    public final DeclaredType SpecializationStatistics = c.getDeclaredType(SpecializationStatistics_Name);
    public final DeclaredType SpecializationStatistics_NodeStatistics = c.getDeclaredType(SpecializationStatistics_NodeStatistics_Name);
    public final DeclaredType SpecializationStatistics_AlwaysEnabled = c.getDeclaredType(SpecializationStatistics_AlwaysEnabled_Name);
    public final DeclaredType SuppressPackageWarnings = c.getDeclaredType(SuppressPackageWarnings_Name);
    public final DeclaredType TypeCast = c.getDeclaredType(TypeCast_Name);
    public final DeclaredType TypeCheck = c.getDeclaredType(TypeCheck_Name);
    public final DeclaredType TypeSystem = c.getDeclaredType(TypeSystem_Name);
    public final DeclaredType TypeSystemReference = c.getDeclaredType(TypeSystemReference_Name);
    public final DeclaredType UnsupportedSpecializationException = c.getDeclaredType(UnsupportedSpecializationException_Name);

    // Library API
    public static final String CachedLibrary_Name = "com.oracle.truffle.api.library.CachedLibrary";
    public static final String DefaultExportProvider_Name = "com.oracle.truffle.api.library.provider.DefaultExportProvider";
    public static final String DynamicDispatchLibrary_Name = "com.oracle.truffle.api.library.DynamicDispatchLibrary";
    public static final String EagerExportProvider_Name = "com.oracle.truffle.api.library.provider.EagerExportProvider";
    public static final String ExportLibrary_Name = "com.oracle.truffle.api.library.ExportLibrary";
    public static final String ExportLibrary_Repeat_Name = "com.oracle.truffle.api.library.ExportLibrary.Repeat";
    public static final String ExportMessage_Ignore_Name = "com.oracle.truffle.api.library.ExportMessage.Ignore";
    public static final String ExportMessage_Name = "com.oracle.truffle.api.library.ExportMessage";
    public static final String ExportMessage_Repeat_Name = "com.oracle.truffle.api.library.ExportMessage.Repeat";
    public static final String GenerateLibrary_Abstract_Name = "com.oracle.truffle.api.library.GenerateLibrary.Abstract";
    public static final String GenerateLibrary_DefaultExport_Name = "com.oracle.truffle.api.library.GenerateLibrary.DefaultExport";
    public static final String GenerateLibrary_Name = "com.oracle.truffle.api.library.GenerateLibrary";
    public static final String Library_Name = "com.oracle.truffle.api.library.Library";
    public static final String LibraryExport_Name = "com.oracle.truffle.api.library.LibraryExport";
    public static final String LibraryExport_DelegateExport_Name = "com.oracle.truffle.api.library.LibraryExport.DelegateExport";
    public static final String LibraryFactory_Name = "com.oracle.truffle.api.library.LibraryFactory";
    public static final String Message_Name = "com.oracle.truffle.api.library.Message";
    public static final String ReflectionLibrary_Name = "com.oracle.truffle.api.library.ReflectionLibrary";

    public final DeclaredType CachedLibrary = c.getDeclaredType(CachedLibrary_Name);
    public final DeclaredType DefaultExportProvider = c.getDeclaredType(DefaultExportProvider_Name);
    public final DeclaredType DynamicDispatchLibrary = c.getDeclaredType(DynamicDispatchLibrary_Name);
    public final DeclaredType EagerExportProvider = c.getDeclaredType(EagerExportProvider_Name);
    public final DeclaredType ExportLibrary = c.getDeclaredType(ExportLibrary_Name);
    public final DeclaredType ExportLibrary_Repeat = c.getDeclaredType(ExportLibrary_Repeat_Name);
    public final DeclaredType ExportMessage = c.getDeclaredType(ExportMessage_Name);
    public final DeclaredType ExportMessage_Ignore = c.getDeclaredType(ExportMessage_Ignore_Name);
    public final DeclaredType ExportMessage_Repeat = c.getDeclaredType(ExportMessage_Repeat_Name);
    public final DeclaredType GenerateLibrary = c.getDeclaredType(GenerateLibrary_Name);
    public final DeclaredType GenerateLibrary_Abstract = c.getDeclaredType(GenerateLibrary_Abstract_Name);
    public final DeclaredType GenerateLibrary_DefaultExport = c.getDeclaredType(GenerateLibrary_DefaultExport_Name);
    public final DeclaredType Library = c.getDeclaredType(Library_Name);
    public final DeclaredType LibraryExport = c.getDeclaredType(LibraryExport_Name);
    public final DeclaredType LibraryExport_DelegateExport = c.getDeclaredType(LibraryExport_DelegateExport_Name);
    public final DeclaredType LibraryFactory = c.getDeclaredType(LibraryFactory_Name);
    public final DeclaredType Message = c.getDeclaredType(Message_Name);
    public final DeclaredType ReflectionLibrary = c.getDeclaredType(ReflectionLibrary_Name);

    // Instrumentation API
    public static final String GenerateWrapper_Ignore_Name = "com.oracle.truffle.api.instrumentation.GenerateWrapper.Ignore";
    public static final String GenerateWrapper_IncomingConverter_Name = "com.oracle.truffle.api.instrumentation.GenerateWrapper.IncomingConverter";
    public static final String GenerateWrapper_Name = "com.oracle.truffle.api.instrumentation.GenerateWrapper";
    public static final String GenerateWrapper_OutgoingConverter_Name = "com.oracle.truffle.api.instrumentation.GenerateWrapper.OutgoingConverter";
    public static final String GenerateWrapper_YieldException_Name = "com.oracle.truffle.api.instrumentation.GenerateWrapper.YieldException";
    public static final String InstrumentableNode_Name = "com.oracle.truffle.api.instrumentation.InstrumentableNode";
    public static final String InstrumentableNode_WrapperNode_Name = "com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode";
    public static final String ProbeNode_Name = "com.oracle.truffle.api.instrumentation.ProbeNode";
    public static final String ProvidedTags_Name = "com.oracle.truffle.api.instrumentation.ProvidedTags";
    public static final String TruffleInstrument_Name = "com.oracle.truffle.api.instrumentation.TruffleInstrument";
    public static final String TruffleInstrumentProvider_Name = "com.oracle.truffle.api.instrumentation.provider.TruffleInstrumentProvider";
    public static final String TruffleInstrument_Registration_Name = "com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration";

    /*
     * Instrumentation types may not be available when compiling instrumentation itself.
     */
    public final DeclaredType GenerateWrapper = c.getDeclaredTypeOptional(GenerateWrapper_Name);
    public final DeclaredType GenerateWrapper_Ignore = c.getDeclaredTypeOptional(GenerateWrapper_Ignore_Name);
    public final DeclaredType GenerateWrapper_IncomingConverter = c.getDeclaredTypeOptional(GenerateWrapper_IncomingConverter_Name);
    public final DeclaredType GenerateWrapper_OutgoingConverter = c.getDeclaredTypeOptional(GenerateWrapper_OutgoingConverter_Name);
    public final DeclaredType GenerateWrapper_YieldException = c.getDeclaredTypeOptional(GenerateWrapper_YieldException_Name);
    public final DeclaredType InstrumentableNode = c.getDeclaredTypeOptional(InstrumentableNode_Name);
    public final DeclaredType InstrumentableNode_WrapperNode = c.getDeclaredTypeOptional(InstrumentableNode_WrapperNode_Name);
    public final DeclaredType ProbeNode = c.getDeclaredTypeOptional(ProbeNode_Name);
    public final DeclaredType ProvidedTags = c.getDeclaredTypeOptional(ProvidedTags_Name);
    public final DeclaredType TruffleInstrument = c.getDeclaredTypeOptional(TruffleInstrument_Name);
    public final DeclaredType TruffleInstrumentProvider = c.getDeclaredTypeOptional(TruffleInstrumentProvider_Name);
    public final DeclaredType TruffleInstrument_Registration = c.getDeclaredTypeOptional(TruffleInstrument_Registration_Name);

    // OM API
    public static final String DynamicObjectFactory_Name = "com.oracle.truffle.api.object.DynamicObjectFactory";
    public static final String DynamicObject_Name = "com.oracle.truffle.api.object.DynamicObject";
    public static final String ObjectType_Name = "com.oracle.truffle.api.object.ObjectType";

    public final DeclaredType DynamicObjectFactory = c.getDeclaredTypeOptional(DynamicObjectFactory_Name);
    public final DeclaredType DynamicObject = c.getDeclaredTypeOptional(DynamicObject_Name);
    public final DeclaredType ObjectType = c.getDeclaredTypeOptional(ObjectType_Name);

    // Utilities API
    public static final String TruffleWeakReference_Name = "com.oracle.truffle.api.utilities.TruffleWeakReference";

    public final DeclaredType TruffleWeakReference = c.getDeclaredTypeOptional(TruffleWeakReference_Name);

    public final Map<String, List<Element>> idempotentMethods = new HashMap<>();
    public final Map<String, List<Element>> nonIdempotentMethods = new HashMap<>();
    public final Map<String, List<Element>> neverDefaultElements = new HashMap<>();

    {
        // idempotent
        addMethod(idempotentMethods, TruffleLanguage_LanguageReference, "get");
        addMethod(idempotentMethods, DirectCallNode, "getCallTarget");
        addMethod(idempotentMethods, c.getDeclaredType(Object.class), "equals");
        addMethod(idempotentMethods, c.getDeclaredType(String.class), "equals");

        // non-idempotent
        addMethod(nonIdempotentMethods, TruffleLanguage_ContextReference, "get");
        addMethod(nonIdempotentMethods, ContextThreadLocal, "get");
        addMethod(nonIdempotentMethods, Assumption, "isValid");
        addMethod(nonIdempotentMethods, Assumption, "isValidAssumption");
        addMethod(nonIdempotentMethods, Library, "accepts");

        // never default elements
        addMethod(neverDefaultElements, c.getDeclaredType(Object.class), "getClass");
        addMethod(neverDefaultElements, c.getDeclaredType(Object.class), "toString");

        addMethod(neverDefaultElements, Frame, "getFrameDescriptor");
        addMethod(neverDefaultElements, Frame, "getArguments");
        addMethod(neverDefaultElements, Frame, "materialize");

        addMethod(neverDefaultElements, FrameDescriptor, "getSlotKind");
        addMethod(neverDefaultElements, FrameDescriptor, "getAuxiliarySlots");

        addMethod(neverDefaultElements, DirectCallNode, "create");
        addMethod(neverDefaultElements, IndirectCallNode, "create");

        addField(neverDefaultElements, Assumption, "ALWAYS_VALID");
        addField(neverDefaultElements, Assumption, "NEVER_VALID");
    }

    public boolean isBuiltinNeverDefault(Element e) {
        return isElementInMap(neverDefaultElements, e);
    }

    public boolean isBuiltinIdempotent(ExecutableElement e) {
        return isElementInMap(idempotentMethods, e);
    }

    public boolean isBuiltinNonIdempotent(ExecutableElement e) {
        return isElementInMap(nonIdempotentMethods, e);
    }

    private static boolean isElementInMap(Map<String, List<Element>> map, Element e) {
        List<Element> elements = map.get(e.getSimpleName().toString());
        if (elements == null) {
            return false;
        }
        for (Element m : elements) {
            if (ElementUtils.elementEquals(m, e)) {
                return true;
            }
        }
        return false;
    }

    private static void addMethod(Map<String, List<Element>> map, DeclaredType type, String methodName) {
        List<ExecutableElement> m = ElementUtils.findAllPublicMethods(type, methodName);
        if (m.isEmpty()) {
            throw new AssertionError(String.format("Method %s.%s not found.", ElementUtils.getSimpleName(type), methodName));
        }
        map.computeIfAbsent(methodName, (e) -> new ArrayList<>()).addAll(m);
    }

    private static void addField(Map<String, List<Element>> map, DeclaredType type, String fieldName) {
        VariableElement v = ElementUtils.findVariableElement(type, fieldName);
        if (v == null) {
            throw new AssertionError(String.format("Field %s.%s not found.", ElementUtils.getSimpleName(type), fieldName));
        }
        map.computeIfAbsent(fieldName, (e) -> new ArrayList<>()).add(v);
    }

    // Checkstyle: resume
}
