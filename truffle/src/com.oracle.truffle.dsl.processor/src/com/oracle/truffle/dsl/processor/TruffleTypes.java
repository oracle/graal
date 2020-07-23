/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.type.DeclaredType;

public class TruffleTypes {

    private final ProcessorContext c = ProcessorContext.getInstance();

    // Checkstyle: stop

    // Testing API
    public static final String EXPECT_ERROR_CLASS_NAME1 = "com.oracle.truffle.api.dsl.test.ExpectError";
    public static final String EXPECT_ERROR_CLASS_NAME2 = "com.oracle.truffle.api.test.ExpectError";

    // Graal SDK
    public static final String OptionCategory_Name = "org.graalvm.options.OptionCategory";
    public static final String OptionDescriptor_Name = "org.graalvm.options.OptionDescriptor";
    public static final String OptionDescriptors_Name = "org.graalvm.options.OptionDescriptors";
    public static final String OptionKey_Name = "org.graalvm.options.OptionKey";
    public static final String OptionMap_Name = "org.graalvm.options.OptionMap";
    public static final String OptionStability_Name = "org.graalvm.options.OptionStability";

    public final DeclaredType Option = c.getDeclaredType(Option_Name);
    public final DeclaredType Option_Group = c.getDeclaredType(Option_Group_Name);
    public final DeclaredType OptionCategory = c.getDeclaredType(OptionCategory_Name);
    public final DeclaredType OptionDescriptor = c.getDeclaredType(OptionDescriptor_Name);
    public final DeclaredType OptionDescriptors = c.getDeclaredType(OptionDescriptors_Name);
    public final DeclaredType OptionKey = c.getDeclaredType(OptionKey_Name);
    public final DeclaredType OptionMap = c.getDeclaredType(OptionMap_Name);
    public final DeclaredType OptionStability = c.getDeclaredType(OptionStability_Name);

    // Truffle API
    public static final String Assumption_Name = "com.oracle.truffle.api.Assumption";
    public static final String CompilerAsserts_Name = "com.oracle.truffle.api.CompilerAsserts";
    public static final String CompilerDirectives_CompilationFinal_Name = "com.oracle.truffle.api.CompilerDirectives.CompilationFinal";
    public static final String CompilerDirectives_Name = "com.oracle.truffle.api.CompilerDirectives";
    public static final String CompilerDirectives_TruffleBoundary_Name = "com.oracle.truffle.api.CompilerDirectives.TruffleBoundary";
    public static final String EncapsulatingNodeReference_Name = "com.oracle.truffle.api.nodes.EncapsulatingNodeReference";
    public static final String ExplodeLoop_Name = "com.oracle.truffle.api.nodes.ExplodeLoop";
    public static final String Frame_Name = "com.oracle.truffle.api.frame.Frame";
    public static final String FinalBitSet_Name = "com.oracle.truffle.api.utilities.FinalBitSet";
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
    public static final String SlowPathException_Name = "com.oracle.truffle.api.nodes.SlowPathException";
    public static final String SourceSection_Name = "com.oracle.truffle.api.source.SourceSection";
    public static final String TruffleLanguage_ContextReference_Name = "com.oracle.truffle.api.TruffleLanguage.ContextReference";
    public static final String TruffleLanguage_LanguageReference_Name = "com.oracle.truffle.api.TruffleLanguage.LanguageReference";
    public static final String TruffleLanguage_Name = "com.oracle.truffle.api.TruffleLanguage";
    public static final String TruffleLanguage_Provider_Name = "com.oracle.truffle.api.TruffleLanguage.Provider";
    public static final String TruffleLanguage_Registration_Name = "com.oracle.truffle.api.TruffleLanguage.Registration";
    public static final String TruffleOptions_Name = "com.oracle.truffle.api.TruffleOptions";
    public static final String UnexpectedResultException_Name = "com.oracle.truffle.api.nodes.UnexpectedResultException";
    public static final String VirtualFrame_Name = "com.oracle.truffle.api.frame.VirtualFrame";
    public static final String HostLanguage_Name = "com.oracle.truffle.polyglot.HostLanguage";

    public final DeclaredType Assumption = c.getDeclaredType(Assumption_Name);
    public final DeclaredType CompilerAsserts = c.getDeclaredType(CompilerAsserts_Name);
    public final DeclaredType CompilerDirectives = c.getDeclaredType(CompilerDirectives_Name);
    public final DeclaredType CompilerDirectives_CompilationFinal = c.getDeclaredType(CompilerDirectives_CompilationFinal_Name);
    public final DeclaredType CompilerDirectives_TruffleBoundary = c.getDeclaredType(CompilerDirectives_TruffleBoundary_Name);
    public final DeclaredType EncapsulatingNodeReference = c.getDeclaredType(EncapsulatingNodeReference_Name);
    public final DeclaredType ExplodeLoop = c.getDeclaredType(ExplodeLoop_Name);
    public final DeclaredType Frame = c.getDeclaredType(Frame_Name);
    public final DeclaredType FinalBitSet = c.getDeclaredType(FinalBitSet_Name);
    public final DeclaredType InvalidAssumptionException = c.getDeclaredType(InvalidAssumptionException_Name);
    public final DeclaredType MaterializedFrame = c.getDeclaredType(MaterializedFrame_Name);
    public final DeclaredType Node = c.getDeclaredType(Node_Name);
    public final DeclaredType Node_Child = c.getDeclaredType(Node_Child_Name);
    public final DeclaredType Node_Children = c.getDeclaredType(Node_Children_Name);
    public final DeclaredType NodeCost = c.getDeclaredType(NodeCost_Name);
    public final DeclaredType NodeInfo = c.getDeclaredType(NodeInfo_Name);
    public final DeclaredType NodeInterface = c.getDeclaredType(NodeInterface_Name);
    public final DeclaredType NodeUtil = c.getDeclaredType(NodeUtil_Name);
    public final DeclaredType SlowPathException = c.getDeclaredType(SlowPathException_Name);
    public final DeclaredType SourceSection = c.getDeclaredType(SourceSection_Name);
    public final DeclaredType TruffleLanguage = c.getDeclaredType(TruffleLanguage_Name);
    public final DeclaredType TruffleLanguage_ContextReference = c.getDeclaredType(TruffleLanguage_ContextReference_Name);
    public final DeclaredType TruffleLanguage_LanguageReference = c.getDeclaredType(TruffleLanguage_LanguageReference_Name);
    public final DeclaredType TruffleLanguage_Provider = c.getDeclaredType(TruffleLanguage_Provider_Name);
    public final DeclaredType TruffleLanguage_Registration = c.getDeclaredType(TruffleLanguage_Registration_Name);
    public final DeclaredType TruffleOptions = c.getDeclaredType(TruffleOptions_Name);
    public final DeclaredType UnexpectedResultException = c.getDeclaredType(UnexpectedResultException_Name);
    public final DeclaredType VirtualFrame = c.getDeclaredType(VirtualFrame_Name);
    public final DeclaredType HostLanguage = c.getDeclaredTypeOptional(HostLanguage_Name);

    // DSL API
    public static final String Bind_Name = "com.oracle.truffle.api.dsl.Bind";
    public static final String Cached_Exclusive_Name = "com.oracle.truffle.api.dsl.Cached.Exclusive";
    public static final String Cached_Name = "com.oracle.truffle.api.dsl.Cached";
    public static final String Cached_Shared_Name = "com.oracle.truffle.api.dsl.Cached.Shared";
    public static final String CachedContext_Name = "com.oracle.truffle.api.dsl.CachedContext";
    public static final String CachedLanguage_Name = "com.oracle.truffle.api.dsl.CachedLanguage";
    public static final String CreateCast_Name = "com.oracle.truffle.api.dsl.CreateCast";
    public static final String Executed_Name = "com.oracle.truffle.api.dsl.Executed";
    public static final String Fallback_Name = "com.oracle.truffle.api.dsl.Fallback";
    public static final String GeneratedBy_Name = "com.oracle.truffle.api.dsl.GeneratedBy";
    public static final String GenerateNodeFactory_Name = "com.oracle.truffle.api.dsl.GenerateNodeFactory";
    public static final String GenerateUncached_Name = "com.oracle.truffle.api.dsl.GenerateUncached";
    public static final String ImplicitCast_Name = "com.oracle.truffle.api.dsl.ImplicitCast";
    public static final String ImportStatic_Name = "com.oracle.truffle.api.dsl.ImportStatic";
    public static final String Introspectable_Name = "com.oracle.truffle.api.dsl.Introspectable";
    public static final String Introspection_Name = "com.oracle.truffle.api.dsl.Introspection";
    public static final String Introspection_Provider_Name = "com.oracle.truffle.api.dsl.Introspection.Provider";
    public static final String NodeChild_Name = "com.oracle.truffle.api.dsl.NodeChild";
    public static final String NodeChildren_Name = "com.oracle.truffle.api.dsl.NodeChildren";
    public static final String NodeFactory_Name = "com.oracle.truffle.api.dsl.NodeFactory";
    public static final String NodeField_Name = "com.oracle.truffle.api.dsl.NodeField";
    public static final String NodeFields_Name = "com.oracle.truffle.api.dsl.NodeFields";
    public static final String ReportPolymorphism_Exclude_Name = "com.oracle.truffle.api.dsl.ReportPolymorphism.Exclude";
    public static final String ReportPolymorphism_Name = "com.oracle.truffle.api.dsl.ReportPolymorphism";
    public static final String Specialization_Name = "com.oracle.truffle.api.dsl.Specialization";
    public static final String TypeCast_Name = "com.oracle.truffle.api.dsl.TypeCast";
    public static final String TypeCheck_Name = "com.oracle.truffle.api.dsl.TypeCheck";
    public static final String TypeSystem_Name = "com.oracle.truffle.api.dsl.TypeSystem";
    public static final String TypeSystemReference_Name = "com.oracle.truffle.api.dsl.TypeSystemReference";
    public static final String UnsupportedSpecializationException_Name = "com.oracle.truffle.api.dsl.UnsupportedSpecializationException";

    public final DeclaredType Bind = c.getDeclaredType(Bind_Name);
    public final DeclaredType Cached = c.getDeclaredType(Cached_Name);
    public final DeclaredType Cached_Exclusive = c.getDeclaredType(Cached_Exclusive_Name);
    public final DeclaredType Cached_Shared = c.getDeclaredType(Cached_Shared_Name);
    public final DeclaredType CachedContext = c.getDeclaredType(CachedContext_Name);
    public final DeclaredType CachedLanguage = c.getDeclaredType(CachedLanguage_Name);
    public final DeclaredType CreateCast = c.getDeclaredType(CreateCast_Name);
    public final DeclaredType Executed = c.getDeclaredType(Executed_Name);
    public final DeclaredType Fallback = c.getDeclaredType(Fallback_Name);
    public final DeclaredType GeneratedBy = c.getDeclaredType(GeneratedBy_Name);
    public final DeclaredType GenerateNodeFactory = c.getDeclaredType(GenerateNodeFactory_Name);
    public final DeclaredType GenerateUncached = c.getDeclaredType(GenerateUncached_Name);
    public final DeclaredType ImplicitCast = c.getDeclaredType(ImplicitCast_Name);
    public final DeclaredType ImportStatic = c.getDeclaredType(ImportStatic_Name);
    public final DeclaredType Introspectable = c.getDeclaredType(Introspectable_Name);
    public final DeclaredType Introspection = c.getDeclaredType(Introspection_Name);
    public final DeclaredType Introspection_Provider = c.getDeclaredType(Introspection_Provider_Name);
    public final DeclaredType NodeChild = c.getDeclaredType(NodeChild_Name);
    public final DeclaredType NodeChildren = c.getDeclaredType(NodeChildren_Name);
    public final DeclaredType NodeFactory = c.getDeclaredType(NodeFactory_Name);
    public final DeclaredType NodeField = c.getDeclaredType(NodeField_Name);
    public final DeclaredType NodeFields = c.getDeclaredType(NodeFields_Name);
    public final DeclaredType ReportPolymorphism = c.getDeclaredType(ReportPolymorphism_Name);
    public final DeclaredType ReportPolymorphism_Exclude = c.getDeclaredType(ReportPolymorphism_Exclude_Name);
    public final DeclaredType Specialization = c.getDeclaredType(Specialization_Name);
    public final DeclaredType TypeCast = c.getDeclaredType(TypeCast_Name);
    public final DeclaredType TypeCheck = c.getDeclaredType(TypeCheck_Name);
    public final DeclaredType TypeSystem = c.getDeclaredType(TypeSystem_Name);
    public final DeclaredType TypeSystemReference = c.getDeclaredType(TypeSystemReference_Name);
    public final DeclaredType UnsupportedSpecializationException = c.getDeclaredType(UnsupportedSpecializationException_Name);

    // Library API
    public static final String CachedLibrary_Name = "com.oracle.truffle.api.library.CachedLibrary";
    public static final String DefaultExportProvider_Name = "com.oracle.truffle.api.library.DefaultExportProvider";
    public static final String DynamicDispatchLibrary_Name = "com.oracle.truffle.api.library.DynamicDispatchLibrary";
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
    public static final String GenerateWrapper_IncomingConverter_Name = "com.oracle.truffle.api.instrumentation.GenerateWrapper.IncomingConverter";
    public static final String GenerateWrapper_Name = "com.oracle.truffle.api.instrumentation.GenerateWrapper";
    public static final String GenerateWrapper_OutgoingConverter_Name = "com.oracle.truffle.api.instrumentation.GenerateWrapper.OutgoingConverter";
    public static final String InstrumentableNode_Name = "com.oracle.truffle.api.instrumentation.InstrumentableNode";
    public static final String InstrumentableNode_WrapperNode_Name = "com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode";
    public static final String ProbeNode_Name = "com.oracle.truffle.api.instrumentation.ProbeNode";
    public static final String ProvidedTags_Name = "com.oracle.truffle.api.instrumentation.ProvidedTags";
    public static final String TruffleInstrument_Name = "com.oracle.truffle.api.instrumentation.TruffleInstrument";
    public static final String TruffleInstrument_Provider_Name = "com.oracle.truffle.api.instrumentation.TruffleInstrument.Provider";
    public static final String TruffleInstrument_Registration_Name = "com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration";

    /*
     * Instrumentation types may not be available when compiling instrumentation itself.
     */
    public final DeclaredType GenerateWrapper = c.getDeclaredTypeOptional(GenerateWrapper_Name);
    public final DeclaredType GenerateWrapper_IncomingConverter = c.getDeclaredTypeOptional(GenerateWrapper_IncomingConverter_Name);
    public final DeclaredType GenerateWrapper_OutgoingConverter = c.getDeclaredTypeOptional(GenerateWrapper_OutgoingConverter_Name);
    public final DeclaredType InstrumentableNode = c.getDeclaredTypeOptional(InstrumentableNode_Name);
    public final DeclaredType InstrumentableNode_WrapperNode = c.getDeclaredTypeOptional(InstrumentableNode_WrapperNode_Name);
    public final DeclaredType ProbeNode = c.getDeclaredTypeOptional(ProbeNode_Name);
    public final DeclaredType ProvidedTags = c.getDeclaredTypeOptional(ProvidedTags_Name);
    public final DeclaredType TruffleInstrument = c.getDeclaredTypeOptional(TruffleInstrument_Name);
    public final DeclaredType TruffleInstrument_Provider = c.getDeclaredTypeOptional(TruffleInstrument_Provider_Name);
    public final DeclaredType TruffleInstrument_Registration = c.getDeclaredTypeOptional(TruffleInstrument_Registration_Name);

    // OM API
    public static final String Layout_Name = "com.oracle.truffle.api.object.dsl.Layout";
    public static final String Nullable_Name = "com.oracle.truffle.api.object.dsl.Nullable";
    public static final String Volatile_Name = "com.oracle.truffle.api.object.dsl.Volatile";
    public static final String Layout_ImplicitCast_Name = "com.oracle.truffle.api.object.Layout.ImplicitCast";
    public static final String DynamicObjectFactory_Name = "com.oracle.truffle.api.object.DynamicObjectFactory";
    public static final String DynamicObject_Name = "com.oracle.truffle.api.object.DynamicObject";
    public static final String ObjectType_Name = "com.oracle.truffle.api.object.ObjectType";

    public final DeclaredType Layout = c.getDeclaredTypeOptional(Layout_Name);
    public final DeclaredType Nullable = c.getDeclaredTypeOptional(Nullable_Name);
    public final DeclaredType Volatile = c.getDeclaredTypeOptional(Volatile_Name);
    public final DeclaredType DynamicObjectFactory = c.getDeclaredTypeOptional(DynamicObjectFactory_Name);
    public final DeclaredType DynamicObject = c.getDeclaredTypeOptional(DynamicObject_Name);
    public final DeclaredType ObjectType = c.getDeclaredTypeOptional(ObjectType_Name);
    public final DeclaredType Layout_ImplicitCast = c.getDeclaredTypeOptional(Layout_ImplicitCast_Name);

    // Utilities API
    public static final String TruffleWeakReference_Name = "com.oracle.truffle.api.utilities.TruffleWeakReference";

    public final DeclaredType TruffleWeakReference = c.getDeclaredTypeOptional(TruffleWeakReference_Name);

    // Checkstyle: resume
}
