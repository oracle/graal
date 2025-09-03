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

    private static final String[] EXPECT_ERROR_TYPES = new String[]{
                    TruffleTypes.EXPECT_ERROR_CLASS_NAME1,
                    TruffleTypes.EXPECT_ERROR_CLASS_NAME2,
                    TruffleTypes.EXPECT_ERROR_CLASS_NAME3,
                    TruffleTypes.EXPECT_WARNING_CLASS_NAME1,
                    TruffleTypes.EXPECT_WARNING_CLASS_NAME2,
    };
    public static final String ALWAYS_SLOW_PATH_MODE_NAME = "com.oracle.truffle.api.dsl.test.AlwaysGenerateOnlySlowPath";
    public static final String DISABLE_STATE_BITWIDTH_MODIFICATION = "com.oracle.truffle.api.dsl.test.DisableStateBitWidthModfication";
    public static final String EXPECT_WARNING_CLASS_NAME1 = "com.oracle.truffle.api.dsl.test.ExpectWarning";
    public static final String EXPECT_WARNING_CLASS_NAME2 = "com.oracle.truffle.api.bytecode.test.error_tests.ExpectWarning";
    public static final String EXPECT_ERROR_CLASS_NAME1 = "com.oracle.truffle.api.dsl.test.ExpectError";
    public static final String EXPECT_ERROR_CLASS_NAME2 = "com.oracle.truffle.api.test.ExpectError";
    public static final String EXPECT_ERROR_CLASS_NAME3 = "com.oracle.truffle.api.bytecode.test.error_tests.ExpectError";
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
    public final DeclaredType BytecodeDebugListener = c.getDeclaredTypeOptional("com.oracle.truffle.api.bytecode.debug.BytecodeDebugListener");

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
    public static final String AbstractTruffleException_Name = "com.oracle.truffle.api.exception.AbstractTruffleException";
    public static final String Assumption_Name = "com.oracle.truffle.api.Assumption";
    public static final String BytecodeOSRNode_Name = "com.oracle.truffle.api.nodes.BytecodeOSRNode";
    public static final String ContextThreadLocal_Name = "com.oracle.truffle.api.ContextThreadLocal";
    public static final String ControlFlowException_Name = "com.oracle.truffle.api.nodes.ControlFlowException";
    public static final String CompilerAsserts_Name = "com.oracle.truffle.api.CompilerAsserts";
    public static final String CompilerDirectives_CompilationFinal_Name = "com.oracle.truffle.api.CompilerDirectives.CompilationFinal";
    public static final String CompilerDirectives_Name = "com.oracle.truffle.api.CompilerDirectives";
    public static final String CompilerDirectives_TruffleBoundary_Name = "com.oracle.truffle.api.CompilerDirectives.TruffleBoundary";
    public static final String DenyReplace_Name = "com.oracle.truffle.api.nodes.DenyReplace";
    public static final String DirectCallNode_Name = "com.oracle.truffle.api.nodes.DirectCallNode";
    public static final String EncapsulatingNodeReference_Name = "com.oracle.truffle.api.nodes.EncapsulatingNodeReference";
    public static final String ExplodeLoop_Name = "com.oracle.truffle.api.nodes.ExplodeLoop";
    public static final String ExplodeLoop_LoopExplosionKind_Name = "com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind";
    public static final String Frame_Name = "com.oracle.truffle.api.frame.Frame";
    public static final String FrameInstance_Name = "com.oracle.truffle.api.frame.FrameInstance";
    public static final String FrameInstance_FrameAccess_Name = "com.oracle.truffle.api.frame.FrameInstance.FrameAccess";
    public static final String FrameDescriptor_Name = "com.oracle.truffle.api.frame.FrameDescriptor";
    public static final String FrameDescriptor_Builder_Name = "com.oracle.truffle.api.frame.FrameDescriptor.Builder";
    public static final String FrameSlotKind_Name = "com.oracle.truffle.api.frame.FrameSlotKind";
    public static final String FrameSlotTypeException_Name = "com.oracle.truffle.api.frame.FrameSlotTypeException";
    public static final String FinalBitSet_Name = "com.oracle.truffle.api.utilities.FinalBitSet";
    public static final String HostCompilerDirectives_Name = "com.oracle.truffle.api.HostCompilerDirectives";
    public static final String HostCompilerDirectives_BytecodeInterpreterSwitch_Name = "com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch";
    public static final String HostCompilerDirectives_InliningCutoff_Name = "com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff";

    public static final String InternalResource_Name = "com.oracle.truffle.api.InternalResource";
    public static final String InternalResource_Id_Name = "com.oracle.truffle.api.InternalResource.Id";
    public static final String InvalidAssumptionException_Name = "com.oracle.truffle.api.nodes.InvalidAssumptionException";
    public static final String LoopNode_Name = "com.oracle.truffle.api.nodes.LoopNode";
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
    public static final String RootNode_Name = "com.oracle.truffle.api.nodes.RootNode";
    public static final String IndirectCallNode_Name = "com.oracle.truffle.api.nodes.IndirectCallNode";
    public static final String InlinedProfile_Name = "com.oracle.truffle.api.profiles.InlinedProfile";
    public static final String InternalResourceProvider_Name = "com.oracle.truffle.api.provider.InternalResourceProvider";
    public static final String SlowPathException_Name = "com.oracle.truffle.api.nodes.SlowPathException";
    public static final String Source_Name = "com.oracle.truffle.api.source.Source";
    public static final String SourceSection_Name = "com.oracle.truffle.api.source.SourceSection";
    public static final String Truffle_Name = "com.oracle.truffle.api.Truffle";
    public static final String TruffleFile_FileTypeDetector_Name = "com.oracle.truffle.api.TruffleFile.FileTypeDetector";
    public static final String TruffleLanguage_ContextReference_Name = "com.oracle.truffle.api.TruffleLanguage.ContextReference";
    public static final String TruffleLanguage_LanguageReference_Name = "com.oracle.truffle.api.TruffleLanguage.LanguageReference";
    public static final String TruffleLanguage_Name = "com.oracle.truffle.api.TruffleLanguage";
    public static final String TruffleLanguageProvider_Name = "com.oracle.truffle.api.provider.TruffleLanguageProvider";
    public static final String TruffleLanguage_Registration_Name = "com.oracle.truffle.api.TruffleLanguage.Registration";
    public static final String TruffleSafepoint_Name = "com.oracle.truffle.api.TruffleSafepoint";
    public static final String TruffleStackTraceElement_Name = "com.oracle.truffle.api.TruffleStackTraceElement";
    public static final String TruffleOptions_Name = "com.oracle.truffle.api.TruffleOptions";
    public static final String TruffleOptionDescriptors_Name = "com.oracle.truffle.api.TruffleOptionDescriptors";
    public static final String UnadoptableNode_Name = "com.oracle.truffle.api.nodes.UnadoptableNode";
    public static final String UnexpectedResultException_Name = "com.oracle.truffle.api.nodes.UnexpectedResultException";
    public static final String VirtualFrame_Name = "com.oracle.truffle.api.frame.VirtualFrame";
    public static final String HostLanguage_Name = "com.oracle.truffle.polyglot.HostLanguage";

    public final DeclaredType AbstractTruffleException = c.getDeclaredTypeOptional(AbstractTruffleException_Name);
    public final DeclaredType Assumption = c.getDeclaredType(Assumption_Name);
    public final DeclaredType BytecodeOSRNode = c.getDeclaredType(BytecodeOSRNode_Name);
    public final DeclaredType ContextThreadLocal = c.getDeclaredType(ContextThreadLocal_Name);
    public final DeclaredType ControlFlowException = c.getDeclaredType(ControlFlowException_Name);
    public final DeclaredType CompilerAsserts = c.getDeclaredType(CompilerAsserts_Name);
    public final DeclaredType CompilerDirectives = c.getDeclaredType(CompilerDirectives_Name);
    public final DeclaredType CompilerDirectives_CompilationFinal = c.getDeclaredType(CompilerDirectives_CompilationFinal_Name);
    public final DeclaredType CompilerDirectives_TruffleBoundary = c.getDeclaredType(CompilerDirectives_TruffleBoundary_Name);
    public final DeclaredType DenyReplace = c.getDeclaredType(DenyReplace_Name);
    public final DeclaredType DirectCallNode = c.getDeclaredType(DirectCallNode_Name);
    public final DeclaredType EncapsulatingNodeReference = c.getDeclaredType(EncapsulatingNodeReference_Name);
    public final DeclaredType ExplodeLoop = c.getDeclaredType(ExplodeLoop_Name);
    public final DeclaredType ExplodeLoop_LoopExplosionKind = c.getDeclaredType(ExplodeLoop_LoopExplosionKind_Name);
    public final DeclaredType Frame = c.getDeclaredType(Frame_Name);
    public final DeclaredType FrameInstance = c.getDeclaredType(FrameInstance_Name);
    public final DeclaredType FrameInstance_FrameAccess = c.getDeclaredType(FrameInstance_FrameAccess_Name);
    public final DeclaredType FrameDescriptor = c.getDeclaredType(FrameDescriptor_Name);
    public final DeclaredType FrameDescriptor_Builder = c.getDeclaredType(FrameDescriptor_Builder_Name);
    public final DeclaredType FrameSlotKind = c.getDeclaredType(FrameSlotKind_Name);
    public final DeclaredType FrameSlotTypeException = c.getDeclaredType(FrameSlotTypeException_Name);
    public final DeclaredType FinalBitSet = c.getDeclaredType(FinalBitSet_Name);
    public final DeclaredType HostCompilerDirectives = c.getDeclaredType(HostCompilerDirectives_Name);
    public final DeclaredType HostCompilerDirectives_BytecodeInterpreterSwitch = c.getDeclaredType(HostCompilerDirectives_BytecodeInterpreterSwitch_Name);
    public final DeclaredType HostCompilerDirectives_InliningCutoff = c.getDeclaredType(HostCompilerDirectives_InliningCutoff_Name);
    public final DeclaredType InternalResource = c.getDeclaredType(InternalResource_Name);
    public final DeclaredType InternalResource_Id = c.getDeclaredType(InternalResource_Id_Name);
    public final DeclaredType InvalidAssumptionException = c.getDeclaredType(InvalidAssumptionException_Name);
    public final DeclaredType LoopNode = c.getDeclaredType(LoopNode_Name);
    public final DeclaredType MaterializedFrame = c.getDeclaredType(MaterializedFrame_Name);
    public final DeclaredType Node = c.getDeclaredType(Node_Name);
    public final DeclaredType Node_Child = c.getDeclaredType(Node_Child_Name);
    public final DeclaredType Node_Children = c.getDeclaredType(Node_Children_Name);
    public final DeclaredType NodeCost = c.getDeclaredType(NodeCost_Name);
    public final DeclaredType NodeInfo = c.getDeclaredType(NodeInfo_Name);
    public final DeclaredType NodeInterface = c.getDeclaredType(NodeInterface_Name);
    public final DeclaredType NodeUtil = c.getDeclaredType(NodeUtil_Name);
    public final DeclaredType Profile = c.getDeclaredTypeOptional(Profile_Name);
    public final DeclaredType RootNode = c.getDeclaredType(RootNode_Name);
    public final DeclaredType IndirectCallNode = c.getDeclaredType(IndirectCallNode_Name);
    public final DeclaredType InlinedProfile = c.getDeclaredTypeOptional(InlinedProfile_Name);
    public final DeclaredType InternalResourceProvider = c.getDeclaredType(InternalResourceProvider_Name);
    public final DeclaredType SlowPathException = c.getDeclaredType(SlowPathException_Name);
    public final DeclaredType Source = c.getDeclaredType(Source_Name);
    public final DeclaredType SourceSection = c.getDeclaredType(SourceSection_Name);
    public final DeclaredType Truffle = c.getDeclaredType(Truffle_Name);
    public final DeclaredType TruffleLanguage = c.getDeclaredType(TruffleLanguage_Name);
    public final DeclaredType TruffleFile_FileTypeDetector = c.getDeclaredType(TruffleFile_FileTypeDetector_Name);
    public final DeclaredType TruffleLanguage_ContextReference = c.getDeclaredType(TruffleLanguage_ContextReference_Name);
    public final DeclaredType TruffleLanguage_LanguageReference = c.getDeclaredType(TruffleLanguage_LanguageReference_Name);
    public final DeclaredType TruffleLanguageProvider = c.getDeclaredType(TruffleLanguageProvider_Name);
    public final DeclaredType TruffleLanguage_Registration = c.getDeclaredType(TruffleLanguage_Registration_Name);
    public final DeclaredType TruffleSafepoint = c.getDeclaredType(TruffleSafepoint_Name);
    public final DeclaredType TruffleStackTraceElement = c.getDeclaredType(TruffleStackTraceElement_Name);
    public final DeclaredType TruffleOptions = c.getDeclaredType(TruffleOptions_Name);
    public final DeclaredType TruffleOptionDescriptors = c.getDeclaredType(TruffleOptionDescriptors_Name);
    public final DeclaredType UnadoptableNode = c.getDeclaredType(UnadoptableNode_Name);
    public final DeclaredType UnexpectedResultException = c.getDeclaredType(UnexpectedResultException_Name);
    public final DeclaredType VirtualFrame = c.getDeclaredType(VirtualFrame_Name);
    public final DeclaredType HostLanguage = c.getDeclaredTypeOptional(HostLanguage_Name);

    // DSL API
    public static final String Bind_Name = "com.oracle.truffle.api.dsl.Bind";
    public static final String Bind_DefaultExpression_Name = "com.oracle.truffle.api.dsl.Bind.DefaultExpression";
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
    public final DeclaredType Bind_DefaultExpression = c.getDeclaredType(Bind_DefaultExpression_Name);
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

    // Bytecode DSL API
    public static final String BytecodeBuilder_Name = "com.oracle.truffle.api.bytecode.BytecodeBuilder";
    public static final String BytecodeConfig_Name = "com.oracle.truffle.api.bytecode.BytecodeConfig";
    public static final String BytecodeConfig_Builder_Name = "com.oracle.truffle.api.bytecode.BytecodeConfig.Builder";
    public static final String BytecodeConfigEncoder_Name = "com.oracle.truffle.api.bytecode.BytecodeConfigEncoder";
    public static final String BytecodeEncodingException_Name = "com.oracle.truffle.api.bytecode.BytecodeEncodingException";
    public static final String BytecodeLabel_Name = "com.oracle.truffle.api.bytecode.BytecodeLabel";
    public static final String BytecodeLocal_Name = "com.oracle.truffle.api.bytecode.BytecodeLocal";
    public static final String BytecodeParser_Name = "com.oracle.truffle.api.bytecode.BytecodeParser";
    public static final String BytecodeRootNode_Name = "com.oracle.truffle.api.bytecode.BytecodeRootNode";
    public static final String BytecodeRootNodes_Name = "com.oracle.truffle.api.bytecode.BytecodeRootNodes";
    public static final String BytecodeNode_Name = "com.oracle.truffle.api.bytecode.BytecodeNode";
    public static final String BytecodeLocation_Name = "com.oracle.truffle.api.bytecode.BytecodeLocation";
    public static final String BytecodeTier_Name = "com.oracle.truffle.api.bytecode.BytecodeTier";
    public static final String BytecodeSupport_Name = "com.oracle.truffle.api.bytecode.BytecodeSupport";
    public static final String BytecodeSupport_ConstantsBuffer_Name = "com.oracle.truffle.api.bytecode.BytecodeSupport.ConstantsBuffer";
    public static final String BytecodeSupport_CloneReferenceList_Name = "com.oracle.truffle.api.bytecode.BytecodeSupport.CloneReferenceList";

    public static final String ConstantOperand_Name = "com.oracle.truffle.api.bytecode.ConstantOperand";
    public static final String ContinuationResult_Name = "com.oracle.truffle.api.bytecode.ContinuationResult";
    public static final String ContinuationRootNode_Name = "com.oracle.truffle.api.bytecode.ContinuationRootNode";
    public static final String EpilogReturn_Name = "com.oracle.truffle.api.bytecode.EpilogReturn";
    public static final String EpilogExceptional_Name = "com.oracle.truffle.api.bytecode.EpilogExceptional";
    public static final String GenerateBytecode_Name = "com.oracle.truffle.api.bytecode.GenerateBytecode";
    public static final String GenerateBytecodeTestVariants_Name = "com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants";
    public static final String GenerateBytecodeTestVariants_Variant_Name = "com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants.Variant";
    public static final String ForceQuickening_Name = "com.oracle.truffle.api.bytecode.ForceQuickening";
    public static final String LocalAccessor_Name = "com.oracle.truffle.api.bytecode.LocalAccessor";
    public static final String LocalRangeAccessor_Name = "com.oracle.truffle.api.bytecode.LocalRangeAccessor";
    public static final String MaterializedLocalAccessor_Name = "com.oracle.truffle.api.bytecode.MaterializedLocalAccessor";
    public static final String Operation_Name = "com.oracle.truffle.api.bytecode.Operation";
    public static final String OperationProxy_Name = "com.oracle.truffle.api.bytecode.OperationProxy";
    public static final String OperationProxy_Proxyable_Name = "com.oracle.truffle.api.bytecode.OperationProxy.Proxyable";
    public static final String Prolog_Name = "com.oracle.truffle.api.bytecode.Prolog";
    public static final String ShortCircuitOperation_Name = "com.oracle.truffle.api.bytecode.ShortCircuitOperation";
    public static final String Variadic_Name = "com.oracle.truffle.api.bytecode.Variadic";
    public static final String Instrumentation_Name = "com.oracle.truffle.api.bytecode.Instrumentation";

    public static final String Instruction_Argument_Kind_Name = "com.oracle.truffle.api.bytecode.Instruction.Argument.Kind";
    public static final String Instruction_Argument_Name = "com.oracle.truffle.api.bytecode.Instruction.Argument";
    public static final String Instruction_Argument_BranchProfile_Name = "com.oracle.truffle.api.bytecode.Instruction.Argument.BranchProfile";
    public static final String BytecodeIntrospection_Name = "com.oracle.truffle.api.bytecode.BytecodeIntrospection";
    public static final String Instruction_Name = "com.oracle.truffle.api.bytecode.Instruction";
    public static final String SourceInformation_Name = "com.oracle.truffle.api.bytecode.SourceInformation";
    public static final String SourceInformationTree_Name = "com.oracle.truffle.api.bytecode.SourceInformationTree";
    public static final String LocalVariable_Name = "com.oracle.truffle.api.bytecode.LocalVariable";
    public static final String ExceptionHandler_Name = "com.oracle.truffle.api.bytecode.ExceptionHandler";
    public static final String ExceptionHandler_HandlerKind_Name = "com.oracle.truffle.api.bytecode.ExceptionHandler.HandlerKind";
    public static final String TagTree_Name = "com.oracle.truffle.api.bytecode.TagTree";
    public static final String TagTreeNode_Name = "com.oracle.truffle.api.bytecode.TagTreeNode";
    public static final String TagTreeNodeExports_Name = "com.oracle.truffle.api.bytecode.TagTreeNodeExports";

    public static final String BytecodeSerializer_Name = "com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer";
    public static final String BytecodeSerializer_SerializerContext_Name = "com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer.SerializerContext";
    public static final String BytecodeDeserializer_Name = "com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer";
    public static final String BytecodeDeserializer_DeserializerContext_Name = "com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer.DeserializerContext";
    public static final String SerializationUtils_Name = "com.oracle.truffle.api.bytecode.serialization.SerializationUtils";

    public static final String ExecutionTracer_Name = "com.oracle.truffle.api.bytecode.tracing.ExecutionTracer";
    public static final String BytecodeTracingMetadata_Name = "com.oracle.truffle.api.bytecode.tracing.TracingMetadata";
    public static final String BytecodeTracingMetadata_SpecializationNames_Name = "com.oracle.truffle.api.bytecode.tracing.TracingMetadata.SpecializationNames";

    public static final String BytecodeDSLAccess_Name = "com.oracle.truffle.api.bytecode.BytecodeDSLAccess";
    public static final String ByteArraySupport_Name = "com.oracle.truffle.api.memory.ByteArraySupport";
    public static final String FrameExtensions_Name = "com.oracle.truffle.api.frame.FrameExtensions";

    public final DeclaredType BytecodeBuilder = c.getDeclaredTypeOptional(BytecodeBuilder_Name);
    public final DeclaredType BytecodeConfig = c.getDeclaredTypeOptional(BytecodeConfig_Name);
    public final DeclaredType BytecodeConfigEncoder = c.getDeclaredTypeOptional(BytecodeConfigEncoder_Name);
    public final DeclaredType BytecodeEncodingException = c.getDeclaredTypeOptional(BytecodeEncodingException_Name);
    public final DeclaredType BytecodeConfig_Builder = c.getDeclaredTypeOptional(BytecodeConfig_Builder_Name);
    public final DeclaredType BytecodeLabel = c.getDeclaredTypeOptional(BytecodeLabel_Name);
    public final DeclaredType BytecodeLocal = c.getDeclaredTypeOptional(BytecodeLocal_Name);
    public final DeclaredType BytecodeParser = c.getDeclaredTypeOptional(BytecodeParser_Name);
    public final DeclaredType BytecodeRootNode = c.getDeclaredTypeOptional(BytecodeRootNode_Name);
    public final DeclaredType BytecodeRootNodes = c.getDeclaredTypeOptional(BytecodeRootNodes_Name);
    public final DeclaredType BytecodeNode = c.getDeclaredTypeOptional(BytecodeNode_Name);
    public final DeclaredType BytecodeLocation = c.getDeclaredTypeOptional(BytecodeLocation_Name);
    public final DeclaredType BytecodeTier = c.getDeclaredTypeOptional(BytecodeTier_Name);
    public final DeclaredType BytecodeSupport = c.getDeclaredTypeOptional(BytecodeSupport_Name);
    public final DeclaredType BytecodeSupport_ConstantsBuffer = c.getDeclaredTypeOptional(BytecodeSupport_ConstantsBuffer_Name);
    public final DeclaredType BytecodeSupport_CloneReferenceList = c.getDeclaredTypeOptional(BytecodeSupport_CloneReferenceList_Name);
    public final DeclaredType ConstantOperand = c.getDeclaredTypeOptional(ConstantOperand_Name);
    public final DeclaredType ContinuationResult = c.getDeclaredTypeOptional(ContinuationResult_Name);
    public final DeclaredType ContinuationRootNode = c.getDeclaredTypeOptional(ContinuationRootNode_Name);
    public final DeclaredType EpilogReturn = c.getDeclaredTypeOptional(EpilogReturn_Name);
    public final DeclaredType EpilogExceptional = c.getDeclaredTypeOptional(EpilogExceptional_Name);
    public final DeclaredType GenerateBytecode = c.getDeclaredTypeOptional(GenerateBytecode_Name);
    public final DeclaredType GenerateBytecodeTestVariants = c.getDeclaredTypeOptional(GenerateBytecodeTestVariants_Name);
    public final DeclaredType GenerateBytecodeTestVariant_Variant = c.getDeclaredTypeOptional(GenerateBytecodeTestVariants_Variant_Name);
    public final DeclaredType ForceQuickening = c.getDeclaredTypeOptional(ForceQuickening_Name);
    public final DeclaredType LocalAccessor = c.getDeclaredTypeOptional(LocalAccessor_Name);
    public final DeclaredType LocalRangeAccessor = c.getDeclaredTypeOptional(LocalRangeAccessor_Name);
    public final DeclaredType MaterializedLocalAccessor = c.getDeclaredTypeOptional(MaterializedLocalAccessor_Name);
    public final DeclaredType Operation = c.getDeclaredTypeOptional(Operation_Name);
    public final DeclaredType OperationProxy = c.getDeclaredTypeOptional(OperationProxy_Name);
    public final DeclaredType Prolog = c.getDeclaredTypeOptional(Prolog_Name);
    public final DeclaredType OperationProxy_Proxyable = c.getDeclaredTypeOptional(OperationProxy_Proxyable_Name);
    public final DeclaredType ShortCircuitOperation = c.getDeclaredTypeOptional(ShortCircuitOperation_Name);
    public final DeclaredType Variadic = c.getDeclaredTypeOptional(Variadic_Name);
    public final DeclaredType Instrumentation = c.getDeclaredTypeOptional(Instrumentation_Name);

    public final DeclaredType Instruction_Argument = c.getDeclaredTypeOptional(Instruction_Argument_Name);
    public final DeclaredType Instruction_Argument_BranchProfile = c.getDeclaredTypeOptional(Instruction_Argument_BranchProfile_Name);
    public final DeclaredType Instruction_Argument_Kind = c.getDeclaredTypeOptional(Instruction_Argument_Kind_Name);
    public final DeclaredType BytecodeIntrospection = c.getDeclaredTypeOptional(BytecodeIntrospection_Name);
    public final DeclaredType Instruction = c.getDeclaredTypeOptional(Instruction_Name);
    public final DeclaredType SourceInformation = c.getDeclaredTypeOptional(SourceInformation_Name);
    public final DeclaredType SourceInformationTree = c.getDeclaredTypeOptional(SourceInformationTree_Name);
    public final DeclaredType LocalVariable = c.getDeclaredTypeOptional(LocalVariable_Name);
    public final DeclaredType ExceptionHandler = c.getDeclaredTypeOptional(ExceptionHandler_Name);
    public final DeclaredType ExceptionHandler_HandlerKind = c.getDeclaredTypeOptional(ExceptionHandler_HandlerKind_Name);
    public final DeclaredType TagTree = c.getDeclaredTypeOptional(TagTree_Name);
    public final DeclaredType TagTreeNode = c.getDeclaredTypeOptional(TagTreeNode_Name);
    public final DeclaredType TagTreeNodeExports = c.getDeclaredTypeOptional(TagTreeNodeExports_Name);

    public final DeclaredType BytecodeSerializer = c.getDeclaredTypeOptional(BytecodeSerializer_Name);
    public final DeclaredType BytecodeSerializer_SerializerContext = c.getDeclaredTypeOptional(BytecodeSerializer_SerializerContext_Name);
    public final DeclaredType BytecodeDeserializer = c.getDeclaredTypeOptional(BytecodeDeserializer_Name);
    public final DeclaredType BytecodeDeserializer_DeserializerContext = c.getDeclaredTypeOptional(BytecodeDeserializer_DeserializerContext_Name);
    public final DeclaredType SerializationUtils = c.getDeclaredTypeOptional(SerializationUtils_Name);

    public final DeclaredType ExecutionTracer = c.getDeclaredTypeOptional(ExecutionTracer_Name);
    public final DeclaredType BytecodeTracingMetadata = c.getDeclaredTypeOptional(BytecodeTracingMetadata_Name);
    public final DeclaredType BytecodeTracingMetadata_SpecializationNames = c.getDeclaredTypeOptional(BytecodeTracingMetadata_SpecializationNames_Name);

    public final DeclaredType BytecodeDSLAccess = c.getDeclaredTypeOptional(BytecodeDSLAccess_Name);
    public final DeclaredType ByteArraySupport = c.getDeclaredTypeOptional(ByteArraySupport_Name);
    public final DeclaredType FrameExtensions = c.getDeclaredTypeOptional(FrameExtensions_Name);

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
    public static final String Tag_Name = "com.oracle.truffle.api.instrumentation.Tag";
    public static final String TruffleInstrument_Name = "com.oracle.truffle.api.instrumentation.TruffleInstrument";
    public static final String TruffleInstrumentProvider_Name = "com.oracle.truffle.api.instrumentation.provider.TruffleInstrumentProvider";
    public static final String TruffleInstrument_Registration_Name = "com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration";

    public static final String StandardTags_RootTag_Name = "com.oracle.truffle.api.instrumentation.StandardTags.RootTag";
    public static final String StandardTags_RootBodyTag_Name = "com.oracle.truffle.api.instrumentation.StandardTags.RootBodyTag";

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
    public final DeclaredType Tag = c.getDeclaredTypeOptional(Tag_Name);
    public final DeclaredType TruffleInstrument = c.getDeclaredTypeOptional(TruffleInstrument_Name);
    public final DeclaredType TruffleInstrumentProvider = c.getDeclaredTypeOptional(TruffleInstrumentProvider_Name);
    public final DeclaredType TruffleInstrument_Registration = c.getDeclaredTypeOptional(TruffleInstrument_Registration_Name);
    public final DeclaredType StandardTags_RootTag = c.getDeclaredTypeOptional(StandardTags_RootTag_Name);
    public final DeclaredType StandardTags_RootBodyTag = c.getDeclaredTypeOptional(StandardTags_RootBodyTag_Name);

    // Interop API
    public static final String NodeLibrary_Name = "com.oracle.truffle.api.interop.NodeLibrary";
    public final DeclaredType NodeLibrary = c.getDeclaredTypeOptional(NodeLibrary_Name);

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
