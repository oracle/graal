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
package com.oracle.truffle.dsl.processor.parser;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.collectAnnotations;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.compareByTypeHierarchy;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.createReferenceName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.findAnnotationMirror;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.firstLetterLowerCase;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.firstLetterUpperCase;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.fromTypeMirror;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getAnnotationValue;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getAnnotationValueList;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getCommonSuperType;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getQualifiedName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getQualifiedSuperTypeNames;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getReadableSignature;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getRepeatedAnnotation;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getSimpleName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getUniqueIdentifiers;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getVisibility;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isAssignable;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isSubtype;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isSubtypeBoxed;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.modifiers;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.resolveAnnotationValue;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.typeEquals;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.unboxAnnotationValue;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.uniqueSortedTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.dsl.processor.CompileErrorException;
import com.oracle.truffle.dsl.processor.Log;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.Timer;
import com.oracle.truffle.dsl.processor.TruffleProcessorOptions;
import com.oracle.truffle.dsl.processor.TruffleSuppressedWarnings;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.bytecode.parser.BytecodeDSLParser;
import com.oracle.truffle.dsl.processor.expression.DSLExpression;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Binary;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.BooleanLiteral;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Call;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Cast;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.ClassLiteral;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.DSLExpressionVisitor;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.IntLiteral;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Negate;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Variable;
import com.oracle.truffle.dsl.processor.expression.DSLExpressionResolver;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory;
import com.oracle.truffle.dsl.processor.generator.NodeCodeGenerator;
import com.oracle.truffle.dsl.processor.generator.NodeFactoryFactory;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.compiler.CompilerFactory;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedElement;
import com.oracle.truffle.dsl.processor.library.ExportsParser;
import com.oracle.truffle.dsl.processor.library.LibraryData;
import com.oracle.truffle.dsl.processor.library.LibraryParser;
import com.oracle.truffle.dsl.processor.model.AssumptionExpression;
import com.oracle.truffle.dsl.processor.model.CacheExpression;
import com.oracle.truffle.dsl.processor.model.ExecutableTypeData;
import com.oracle.truffle.dsl.processor.model.GuardExpression;
import com.oracle.truffle.dsl.processor.model.InlineFieldData;
import com.oracle.truffle.dsl.processor.model.InlinedNodeData;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.MethodSpec;
import com.oracle.truffle.dsl.processor.model.NodeChildData;
import com.oracle.truffle.dsl.processor.model.NodeChildData.Cardinality;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.NodeFieldData;
import com.oracle.truffle.dsl.processor.model.Parameter;
import com.oracle.truffle.dsl.processor.model.ParameterSpec;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.model.SpecializationData.Idempotence;
import com.oracle.truffle.dsl.processor.model.SpecializationThrowsData;
import com.oracle.truffle.dsl.processor.model.TemplateMethod;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;

public final class NodeParser extends AbstractParser<NodeData> {

    private final List<DeclaredType> annotations = Arrays.asList(types.Fallback, types.TypeSystemReference,
                    types.Specialization,
                    types.NodeChild,
                    types.Executed,
                    types.NodeChildren,
                    types.ReportPolymorphism);

    public static final String SYMBOL_NODE = "$node";
    public static final String SYMBOL_THIS = "this";
    public static final String SYMBOL_NULL = "null";

    private enum ParseMode {
        DEFAULT,
        EXPORTED_MESSAGE,
        OPERATION,
    }

    private boolean nodeOnly;
    private final ParseMode mode;
    private final TypeMirror exportLibraryType;
    private final TypeElement exportDeclarationType;
    private final TypeElement bytecodeRootNodeType;
    private final boolean substituteThisToParent;

    /*
     * Parsing parent to detect recursions.
     */
    private NodeData parsingParent;
    private final List<TypeMirror> cachedAnnotations;

    private NodeParser(ParseMode mode, TypeMirror exportLibraryType, TypeElement exportDeclarationType, TypeElement bytecodeRootNodeType, boolean substituteThisToParent) {
        this.mode = mode;
        this.exportLibraryType = exportLibraryType;
        this.exportDeclarationType = exportDeclarationType;
        this.cachedAnnotations = getCachedAnnotations();
        this.bytecodeRootNodeType = bytecodeRootNodeType;
        this.substituteThisToParent = substituteThisToParent;
    }

    public static List<TypeMirror> getCachedAnnotations() {
        TruffleTypes types = ProcessorContext.getInstance().getTypes();
        return Arrays.asList(types.Cached, types.CachedLibrary, types.Bind);
    }

    public static NodeParser createExportParser(TypeMirror exportLibraryType, TypeElement exportDeclarationType, boolean substituteThisToParent) {
        NodeParser parser = new NodeParser(ParseMode.EXPORTED_MESSAGE, exportLibraryType, exportDeclarationType, null, substituteThisToParent);
        // the ExportsParse will take care of removing the specializations if the option is set
        parser.setGenerateSlowPathOnly(false);
        return parser;
    }

    public static NodeParser createDefaultParser() {
        return new NodeParser(ParseMode.DEFAULT, null, null, null, false);
    }

    public static NodeParser createOperationParser(TypeElement bytecodeRootNodeType) {
        return new NodeParser(ParseMode.OPERATION, null, null, bytecodeRootNodeType, false);
    }

    @Override
    protected NodeData parse(Element element, List<AnnotationMirror> mirror) {
        try {
            NodeData node = parseRootType((TypeElement) element);
            if (Log.isDebug() && node != null) {
                String dump = node.dump();
                log.message(Kind.ERROR, null, null, null, dump);
            }
            return node;
        } finally {
            nodeDataCache.clear();
        }
    }

    @Override
    protected NodeData filterErrorElements(NodeData model) {
        for (Iterator<NodeData> iterator = model.getEnclosingNodes().iterator(); iterator.hasNext();) {
            NodeData node = filterErrorElements(iterator.next());
            if (node == null) {
                iterator.remove();
            }
        }
        if (model.hasErrors()) {
            return null;
        }
        return model;
    }

    @Override
    public boolean isDelegateToRootDeclaredType() {
        return true;
    }

    @Override
    public boolean isGenerateSlowPathOnly(TypeElement element) {
        if (mode == ParseMode.OPERATION) {
            return false;
        }
        return super.isGenerateSlowPathOnly(element);
    }

    @Override
    public DeclaredType getAnnotationType() {
        return null;
    }

    @Override
    public List<DeclaredType> getTypeDelegatedAnnotationTypes() {
        return annotations;
    }

    private NodeData parseRootType(TypeElement rootType) {
        List<NodeData> enclosedNodes = new ArrayList<>();
        // Only top-level nodes need to be parsed for the Bytecode DSL. If a node used as an
        // Operation has nested nodes, they will be processed during regular node generation.
        if (mode != ParseMode.OPERATION) {
            for (TypeElement enclosedType : ElementFilter.typesIn(rootType.getEnclosedElements())) {
                NodeData enclosedChild = parseRootType(enclosedType);
                if (enclosedChild != null) {
                    enclosedNodes.add(enclosedChild);
                }
            }
        }
        NodeData node;
        try {
            node = parseNode(rootType);
        } catch (CompileErrorException e) {
            throw e;
        } catch (Throwable e) {
            RuntimeException e2 = new RuntimeException(String.format("Parsing of Node %s failed.", getQualifiedName(rootType)));
            e.addSuppressed(e2);
            throw e;
        }
        if (node == null && !enclosedNodes.isEmpty()) {
            node = new NodeData(context, parsingParent, rootType);
        }
        if (node != null) {
            for (NodeData enclosedNode : enclosedNodes) {
                node.addEnclosedNode(enclosedNode);
            }
        }
        return node;
    }

    public NodeData parseNode(TypeElement originalTemplateType) {
        // reloading the type elements is needed for ecj
        TypeElement templateType;
        if (originalTemplateType instanceof CodeTypeElement) {
            templateType = originalTemplateType;
        } else {
            templateType = fromTypeMirror(context.reloadTypeElement(originalTemplateType));
        }

        if (!(originalTemplateType instanceof CodeTypeElement) && findAnnotationMirror(originalTemplateType, types.GeneratedBy) != null) {
            // generated nodes should not get called again.
            return null;
        }

        if (!isAssignable(templateType.asType(), types.Node)) {
            return null;
        }

        if (mode == ParseMode.DEFAULT && !getRepeatedAnnotation(templateType.getAnnotationMirrors(), types.ExportMessage).isEmpty()) {
            return null;
        }
        if (mode == ParseMode.DEFAULT && findAnnotationMirror(templateType.getAnnotationMirrors(), types.Operation) != null) {
            return null;
        }

        List<TypeElement> lookupTypes = collectSuperClasses(new ArrayList<>(), templateType);

        NodeData node = parseNodeData(templateType, lookupTypes);

        List<Element> declaredMembers = ElementUtils.loadFilteredMembers(templateType);
        // ensure the processed element has at least one @Specialization annotation.
        if (!containsSpecializations(declaredMembers)) {
            return null;
        }

        AnnotationMirror generateAOT = findFirstAnnotation(lookupTypes, types.GenerateAOT);
        if (generateAOT != null) {
            node.setGenerateAOT(true);
        }
        AnnotationMirror generateCached = findGenerateAnnotation(templateType.asType(), types.GenerateCached);
        if (generateCached != null) {
            node.setGenerateCached(ElementUtils.getAnnotationValue(Boolean.class, generateCached, "value"));
            node.setDefaultInlineCached(ElementUtils.getAnnotationValue(Boolean.class, generateCached, "alwaysInlineCached"));
        } else {
            node.setGenerateCached(true);
            node.setDefaultInlineCached(false);
        }
        node.setGenerateUncached(isGenerateUncached(templateType));
        node.setGenerateInline(isGenerateInline(templateType));

        if (!node.isGenerateCached() && !node.isGenerateInline() && !node.isGenerateUncached()) {
            // no generated code needed.
            return null;
        }

        if (nodeOnly) {
            return node;
        }

        if (node.hasErrors()) {
            return node;
        }

        AnnotationMirror introspectable = findFirstAnnotation(lookupTypes, types.Introspectable);
        if (introspectable != null) {
            node.setGenerateIntrospection(true);
        }
        Boolean generateProperty = TruffleProcessorOptions.generateSpecializationStatistics(ProcessorContext.getInstance().getEnvironment());
        if (generateProperty != null) {
            node.setGenerateStatistics(generateProperty);
        }
        if (findFirstAnnotation(lookupTypes, types.SpecializationStatistics_AlwaysEnabled) != null) {
            node.setGenerateStatistics(true);
        }

        if (isAssignable(templateType.asType(), types.ExecuteTracingSupport)) {
            if (mode == ParseMode.EXPORTED_MESSAGE) {
                node.addError("@%s annotated nodes do not support execute tracing. " +
                                "Remove the %s interface to resolve this.",
                                types.ExportMessage.asElement().getSimpleName().toString(),
                                types.ExecuteTracingSupport.asElement().getSimpleName().toString());
            }
            TypeMirror object = context.getType(Object.class);
            TypeMirror throwable = context.getType(Throwable.class);
            ArrayType objectArray = new ArrayCodeTypeMirror(object);
            boolean traceOnEnter = ElementUtils.isDefaultMethodOverridden(templateType, "traceOnEnter", objectArray);
            boolean traceOnReturn = ElementUtils.isDefaultMethodOverridden(templateType, "traceOnReturn", object);
            boolean traceOnException = ElementUtils.isDefaultMethodOverridden(templateType, "traceOnException", throwable);
            node.setGenerateExecuteTracing(traceOnEnter, traceOnReturn, traceOnException);
        }

        if (node.isGenerateAOT() && !node.isGenerateCached() && !node.isGenerateInline()) {
            node.addError("%@s cannot be enabled if @%s and @%s is disabled for this node.",
                            getSimpleName(types.GenerateAOT),
                            getSimpleName(types.GenerateCached),
                            getSimpleName(types.GenerateInline));
        }

        AnnotationMirror reportPolymorphism = findFirstAnnotation(lookupTypes, types.ReportPolymorphism);
        AnnotationMirror excludePolymorphism = findFirstAnnotation(lookupTypes, types.ReportPolymorphism_Exclude);
        if (reportPolymorphism != null && excludePolymorphism == null) {
            node.setReportPolymorphism(true);
        }
        node.getFields().addAll(parseFields(lookupTypes, declaredMembers));
        node.getChildren().addAll(parseChildren(node, lookupTypes, declaredMembers));
        node.getChildExecutions().addAll(parseExecutions(node, node.getFields(), node.getChildren(), declaredMembers));
        node.getExecutableTypes().addAll(parseExecutableTypeData(node, declaredMembers, node.getSignatureSize(), context.getFrameTypes(), false));

        initializeExecutableTypes(node);

        List<Element> allMembers = new ArrayList<>(declaredMembers);
        initializeStaticImports(node, lookupTypes, allMembers);
        initializeChildren(node);

        if (node.hasErrors()) {
            return node; // error sync point
        }

        node.getSpecializations().addAll(new SpecializationMethodParser(context, node, mode == ParseMode.EXPORTED_MESSAGE).parse(declaredMembers));
        node.getSpecializations().addAll(new FallbackParser(context, node).parse(declaredMembers));
        node.getCasts().addAll(new CreateCastParser(context, node).parse(declaredMembers));

        if (node.hasErrors()) {
            return node;  // error sync point
        }
        DSLExpressionResolver resolver = createBaseResolver(node, allMembers);

        initializeSpecializations(resolver, node);

        if (node.hasErrors()) {
            return node;  // error sync point
        }

        initializePolymorphicExecutable(node);
        initializeExecutableTypeHierarchy(node);
        initializeReceiverBound(node);

        if (node.hasErrors()) {
            return node;  // error sync point
        }

        initializeUncachable(node);
        initializeAOT(node);
        boolean recommendInline = initializeInlinable(resolver, node);

        initializeFastPathIdempotentGuards(node);

        if (mode == ParseMode.DEFAULT || mode == ParseMode.OPERATION) {
            boolean emitWarnings = TruffleProcessorOptions.cacheSharingWarningsEnabled(processingEnv) && //
                            !TruffleProcessorOptions.generateSlowPathOnly(processingEnv);
            node.setSharedCaches(computeSharing(node.getTemplateType(), Arrays.asList(node), emitWarnings));
        } else {
            // sharing is computed by the ExportsParser
        }

        verifySpecializationSameLength(node);
        verifyVisibilities(node);
        verifyMissingAbstractMethods(node, declaredMembers);
        verifyConstructors(node);
        verifySpecializationThrows(node);
        verifyFrame(node);
        verifyReportPolymorphism(node);

        removeSpecializations(node, node.getSharedCaches(), isGenerateSlowPathOnly(node));

        verifyRecommendationWarnings(node, recommendInline);

        return node;
    }

    private void initializeFastPathIdempotentGuards(NodeData node) {
        for (SpecializationData specialization : node.getReachableSpecializations()) {
            for (GuardExpression guard : specialization.getGuards()) {
                DSLExpression expression = guard.getExpression();

                if (guard.isWeakReferenceGuard() || FlatNodeGenFactory.guardNeedsNodeStateBit(specialization, guard)) {
                    guard.setFastPathIdempotent(false);
                    continue;
                }

                Idempotence idempotence = specialization.getIdempotence(expression);
                switch (idempotence) {
                    case IDEMPOTENT:
                        guard.setFastPathIdempotent(true);
                        break;
                    case NON_IDEMPOTENT:
                        guard.setFastPathIdempotent(false);
                        break;
                    case UNKNOWN:
                        StringBuilder message = new StringBuilder(String.format("The guard '%s' invokes methods that would benefit from the @%s or @%s annotations: %n",
                                        guard.getExpression().asString(), getSimpleName(types.Idempotent), getSimpleName(types.NonIdempotent)));
                        for (ExecutableElement method : specialization.getBoundMethods(expression)) {
                            if (ElementUtils.getIdempotent(method) == Idempotence.UNKNOWN) {
                                message.append("  - ").append(ElementUtils.getReadableReference(node.getTemplateType(), method)).append(System.lineSeparator());
                            }
                        }
                        message.append("The DSL will invoke guards always or only in the slow-path during specialization depending these annotations. ");
                        message.append("To resolve this annotate the methods, remove the guard or suppress the warning.");
                        specialization.addSuppressableWarning(TruffleSuppressedWarnings.GUARD, message.toString());
                        guard.setFastPathIdempotent(true);
                        break;
                    default:
                        throw new AssertionError();

                }
            }
        }
    }

    private DSLExpressionResolver createBaseResolver(NodeData node, List<Element> members) {
        List<VariableElement> fields = new ArrayList<>();
        for (NodeFieldData field : node.getFields()) {
            fields.add(field.getVariable());
        }

        List<Element> globalMembers = new ArrayList<>(members.size() + fields.size());
        globalMembers.addAll(fields);
        globalMembers.addAll(members);
        globalMembers.add(new CodeVariableElement(types.Node, SYMBOL_THIS));
        globalMembers.add(new CodeVariableElement(types.Node, SYMBOL_NODE));
        TypeElement accessingType = node.getTemplateType();

        if (mode == ParseMode.OPERATION) {
            /*
             * Operation nodes can bind extra variables.
             *
             * Note that Proxyable nodes cannot bind these symbols.
             */
            globalMembers.add(new CodeVariableElement(bytecodeRootNodeType.asType(), BytecodeDSLParser.SYMBOL_ROOT_NODE));
            globalMembers.add(new CodeVariableElement(types.BytecodeNode, BytecodeDSLParser.SYMBOL_BYTECODE_NODE));
            globalMembers.add(new CodeVariableElement(context.getType(int.class), BytecodeDSLParser.SYMBOL_BYTECODE_INDEX));
            // Names should be visible from the package of the generated BytecodeRootNode.
            accessingType = bytecodeRootNodeType;
        }
        return new DSLExpressionResolver(context, accessingType, globalMembers);
    }

    private static final class NodeSizeEstimate {

        final int inlinedSize;
        final int notInlinedSize;

        NodeSizeEstimate(int inlinedSize, int notInlinedSize) {
            this.inlinedSize = inlinedSize;
            this.notInlinedSize = notInlinedSize;
        }

    }

    private static int computeInstanceSize(TypeMirror mirror) {
        TypeElement type = fromTypeMirror(mirror);
        if (type != null) {
            List<Element> members = ElementUtils.loadAllMembers(type);
            int size = ElementUtils.COMPRESSED_HEADER_SIZE;
            for (VariableElement var : ElementFilter.fieldsIn(members)) {
                size += ElementUtils.getCompressedReferenceSize(var.asType());
            }
            return size;
        } else {
            return ElementUtils.getCompressedReferenceSize(mirror);
        }
    }

    private NodeSizeEstimate computeCachedInlinedSizeEstimate(ExecutableElement inlineMethod) {
        if (inlineMethod == null) {
            return new NodeSizeEstimate(0, 0);
        }
        TypeMirror type = inlineMethod.getReturnType();
        List<InlineFieldData> fields = parseInlineMethod(null, null, inlineMethod);
        NodeSizeEstimate estimate = computeInlinedSizeEstimate(fields);
        int inlineFootprint = estimate.inlinedSize;
        int notInlineFootprint;
        if (NodeCodeGenerator.isSpecializedNode(type)) {
            notInlineFootprint = ElementUtils.COMPRESSED_HEADER_SIZE + ElementUtils.COMPRESSED_POINTER_SIZE +
                            estimate.notInlinedSize;
        } else {
            notInlineFootprint = computeInstanceSize(type) + ElementUtils.COMPRESSED_POINTER_SIZE;
        }
        return new NodeSizeEstimate(inlineFootprint, notInlineFootprint);
    }

    private static NodeSizeEstimate computeInlinedSizeEstimate(List<InlineFieldData> fields) {
        int stateBits = 0;
        int referenceSizes = 0;
        for (InlineFieldData field : fields) {
            if (field.isState()) {
                stateBits += field.getBits();
            } else {
                referenceSizes += ElementUtils.getCompressedReferenceSize(field.getType());
            }
        }

        int inlinedSize = (int) Math.ceil(stateBits / 8d) + referenceSizes;
        int notInlinedSize = (int) Math.ceil(stateBits / 32d) * 4 + referenceSizes;

        // node header and parent pointer.
        notInlinedSize += ElementUtils.COMPRESSED_HEADER_SIZE + ElementUtils.COMPRESSED_POINTER_SIZE;

        return new NodeSizeEstimate(inlinedSize, notInlinedSize);
    }

    private void verifyRecommendationWarnings(NodeData node, boolean recommendInline) {
        if (node.hasErrors()) {
            // no recommendations if there are errors.
            return;
        }

        if (recommendInline && !node.isGenerateInline() && mode == ParseMode.DEFAULT && node.isGenerateCached()) {

            AnnotationMirror annotation = getGenerateInlineAnnotation(node.getTemplateType().asType());
            if (annotation == null) {

                NodeSizeEstimate estimate = computeInlinedSizeEstimate(FlatNodeGenFactory.createInlinedFields(node));
                if (estimate.inlinedSize <= estimate.notInlinedSize) {
                    node.addSuppressableWarning(TruffleSuppressedWarnings.INLINING_RECOMMENDATION, "This node is a candidate for node object inlining. " + //
                                    "The memory footprint is estimated to be reduced from %s to %s byte(s). " +
                                    "Add @%s(true) to enable object inlining for this node or @%s(false) to disable this warning. " + //
                                    "Also consider disabling cached node generation with @%s(false) if all usages will be inlined.",
                                    estimate.notInlinedSize,
                                    estimate.inlinedSize,
                                    getSimpleName(types.GenerateInline),
                                    getSimpleName(types.GenerateInline),
                                    getSimpleName(types.GenerateCached),
                                    getSimpleName(types.GenerateInline),
                                    getSimpleName(types.GenerateInline));
                }
            }
        }

        for (SpecializationData specialization : node.getSpecializations()) {
            ExecutableElement element = (ExecutableElement) specialization.getMessageElement();
            if (element == null) {
                continue;
            }

            if (mode == ParseMode.DEFAULT && element.getSimpleName().toString().startsWith("execute")) {
                specialization.addWarning("It is discouraged that @%s annotated methods start with the prefix 'execute'. " + //
                                "This prefix is reserved for execute methods, which identifies methods to execute nodes and should alwas be separate from execute methods. " + //
                                "Rename this method to resolve this. Examples for specialization names are 'doInt', 'doInBounds' or 'doCached'.",
                                getSimpleName(types.Specialization));
            }

            boolean usesInlinedNodes = false;
            boolean usesSpecializationClass = FlatNodeGenFactory.useSpecializationClass(specialization);
            boolean usesSharedInlineNodes = false;
            boolean usesExclusiveInlineNodes = false;
            ArrayList<String> sharedInlinedCachesNames = new ArrayList<>(specialization.getCaches().size());
            for (CacheExpression cache : specialization.getCaches()) {
                if (cache.getInlinedNode() != null) {
                    usesInlinedNodes = true;
                    if (cache.getSharedGroup() != null) {
                        sharedInlinedCachesNames.add(cache.getParameter().getLocalName());
                        usesSharedInlineNodes = true;
                    } else {
                        usesExclusiveInlineNodes = true;
                    }
                }
            }

            if (usesInlinedNodes) {
                if (usesSpecializationClass && usesSharedInlineNodes && usesExclusiveInlineNodes) {
                    specialization.addSuppressableWarning(TruffleSuppressedWarnings.INTERPRETED_PERFORMANCE,
                                    String.format("It is discouraged that specializations with specialization data class combine " + //
                                                    "shared and exclusive @Cached inline nodes or profiles arguments. Truffle inlining support code then must " + //
                                                    "traverse the parent pointer in order to resolve the inline data of the shared nodes or profiles, " + //
                                                    "which incurs performance hit in the interpreter. To resolve this: make @Exclusive all the currently @Shared inline " + //
                                                    "arguments (%s), or merge specializations to avoid @Shared arguments, or if the footprint benefit " + //
                                                    "outweighs the performance degradation, then suppress the warning.", String.join(", ", sharedInlinedCachesNames)));
                }

                boolean isStatic = element.getModifiers().contains(Modifier.STATIC);
                if (node.isGenerateInline()) {
                    /*
                     * For inlined nodes we need pass down the inlineTarget Node even for shared
                     * nodes using the first specialization parameter.
                     */
                    boolean firstParameterNode = false;
                    for (Parameter p : specialization.getSignatureParameters()) {
                        firstParameterNode = p.isDeclared();
                        break;
                    }
                    if (!firstParameterNode) {
                        specialization.addError("For @%s annotated nodes all specialization methods with inlined cached values must declare 'Node node' dynamic parameter. " +
                                        "This parameter must be passed along to inlined cached values. " +
                                        "To resolve this add the 'Node node' parameter as first parameter to the specialization method and pass the value along to inlined cached values.",
                                        getSimpleName(types.GenerateInline));
                    } else if (!isStatic) {
                        specialization.addError("For @%s annotated nodes all specialization methods with inlined cached values must be static. " +
                                        "The method must be static to avoid accidently passing the wrong node parameter to inlined cached nodes. " +
                                        "To resolve this add the static keyword to the specialization method. ",
                                        getSimpleName(types.GenerateInline));
                    }
                } else if (mode == ParseMode.EXPORTED_MESSAGE || FlatNodeGenFactory.substituteNodeWithSpecializationClass(specialization)) {
                    /*
                     * For exported message we need to use @Bind("$node") always even for any
                     * inlined cache as the "this" receiver refers to the library receiver.
                     *
                     * For regular cached nodes @Bind("this") must be used if a specialization data
                     * class is in use. If all inlined caches are shared we can use this and avoid
                     * the warning.
                     */
                    boolean hasNodeParameter = false;
                    for (CacheExpression cache : specialization.getCaches()) {
                        if (cache.isBind() && specialization.isNodeReceiverBound(cache.getDefaultExpression())) {
                            hasNodeParameter = true;
                            break;
                        }
                    }

                    if (!hasNodeParameter) {
                        String nodeParameter = String.format("@%s Node node", getSimpleName(types.Bind));
                        String message = String.format(
                                        "For this specialization with inlined cache parameters a '%s' parameter must be declared. " + //
                                                        "This parameter must be passed along to inlined cached values. " +
                                                        "To resolve this add a '%s' parameter to the specialization method and pass the value along to inlined cached values.",
                                        nodeParameter, nodeParameter);

                        specialization.addSuppressableWarning(TruffleSuppressedWarnings.INLINING_RECOMMENDATION, message);

                    } else if (!isStatic && mode != ParseMode.EXPORTED_MESSAGE) {
                        // The static keyword does not make sense for exported messages, where the
                        // receiver would be incompatible anyway.

                        String message = String.format(
                                        "For this specialization with inlined cache parameters it is recommended to use the static modifier. " + //
                                                        "The method should be static to avoid accidently passing the wrong node parameter to inlined cached nodes. " +
                                                        "To resolve this add the static keyword to the specialization method. ");

                        specialization.addSuppressableWarning(TruffleSuppressedWarnings.STATIC_METHOD, message);
                    }

                }
            }

            if (specialization.hasMultipleInstances() && ElementUtils.getAnnotationValue(specialization.getMessageAnnotation(), "limit", false) == null) {
                specialization.addSuppressableWarning(TruffleSuppressedWarnings.LIMIT,
                                "For this specialization no limit attribute was specified, but multiple instances of this specialization are possible. " + //
                                                "To resolve this add the limit attribute to the @%s annotation.",
                                getSimpleName(types.Specialization),
                                getSimpleName(types.Specialization));
            }

            boolean isLayoutBenefittingFromNeverDefault = FlatNodeGenFactory.isLayoutBenefittingFromNeverDefault(specialization);
            for (CacheExpression cache : specialization.getCaches()) {
                if (cache.isAlwaysInitialized()) {
                    // no space needed
                    continue;
                }
                if (cache.getInlinedNode() != null) {
                    // no space needed for inlined
                    continue;
                }
                if (!cache.isCached()) {
                    continue;
                }

                Boolean neverDefault = getAnnotationValue(Boolean.class, cache.getMessageAnnotation(), "neverDefault", false);
                if (neverDefault != null) {
                    if (!isLayoutBenefittingFromNeverDefault && cache.getSharedGroup() == null) {
                        cache.addSuppressableWarning(TruffleSuppressedWarnings.UNUSED,
                                        ElementUtils.getAnnotationValue(cache.getMessageAnnotation(), "neverDefault"),
                                        "The @%s(neverDefault=true|false) property is not needed to be set. Remove the property to resolve this warning.",
                                        getSimpleName(types.Cached));

                    } else if (isNeverDefaultGuaranteed(specialization, cache) || isNeverDefaultImpliedByAnnotation(cache) || (neverDefault && isNeverDefaultImplied(cache))) {
                        cache.addSuppressableWarning(TruffleSuppressedWarnings.UNUSED,
                                        ElementUtils.getAnnotationValue(cache.getMessageAnnotation(), "neverDefault"),
                                        "The @%s(neverDefault=true|false) property is guaranteed or implied by the initializer expression. Remove the property to resolve this warning.",
                                        getSimpleName(types.Cached),
                                        getSimpleName(types.NeverDefault));
                    }
                } else {
                    if (!cache.isEagerInitialize() && (cache.getSharedGroup() != null || isLayoutBenefittingFromNeverDefault) && !isNeverDefaultImplied(cache) &&
                                    !isNeverDefaultEasilySupported(cache)) {
                        /*
                         * The exports parser adds a suppress warning when calling into the node
                         * parser for accepts messages. Normally suppression is calculated later,
                         * but here we do it eagerly because of that.
                         */
                        if (!TruffleSuppressedWarnings.isSuppressed(specialization.getMessageElement(), TruffleSuppressedWarnings.NEVERDEFAULT)) {
                            cache.addSuppressableWarning(
                                            TruffleSuppressedWarnings.NEVERDEFAULT,
                                            "It is recommended to set the @%s(neverDefault=true|false) property for this cache expression to allow the DSL to further optimize the generated layout of this node. " +
                                                            "Please set the neverDefault property to true if the value may never return the default value, else false. " +
                                                            "You may also use the @%s annotation on any method or field bound in the initializer expression to indicate never default. " +
                                                            "To allow optimizations in the generated code layout, it is recommended to guarantee non-default values for initializer expressions whenever possible.",
                                            getSimpleName(types.Cached),
                                            getSimpleName(types.NeverDefault));
                        }
                    }
                }
            }

        }
    }

    private static TypeMirror findRedirectedInliningType(SpecializationData specialization, CacheExpression cache) {
        if (specialization.hasMultipleInstances()) {
            return null;
        }
        TypeElement typeElement = ElementUtils.castTypeElement(cache.getParameter().getType());
        if (typeElement == null) {
            return null;
        }

        ExecutableElement inlineMethod = ElementUtils.findStaticMethod(typeElement, "inline");
        if (inlineMethod != null && !typeEquals(inlineMethod.getReturnType(), cache.getParameter().getType())) {
            return inlineMethod.getReturnType();
        }
        return null;
    }

    private static boolean isInliningSupported(CacheExpression cache) {
        TypeMirror type = cache.getParameter().getType();
        if (ElementUtils.isPrimitive(type) && !ElementUtils.isVoid(type)) {
            return true;
        }
        TypeElement typeElement = ElementUtils.castTypeElement(type);
        ExecutableElement inlineMethod = typeElement != null ? ElementUtils.findStaticMethod(typeElement, "inline") : null;
        if (inlineMethod == null) {
            if (ElementUtils.isAssignable(cache.getParameter().getType(), ProcessorContext.types().Node)) {
                if (typeElement != null && isGenerateInline(typeElement) && NodeCodeGenerator.isSpecializedNode(type)) {
                    return true;
                }
                return false;
            } else {
                // references can be inlined
                return true;
            }
        } else {
            // inline method available -> inlinable
            return true;
        }

    }

    private boolean initializeInlinable(DSLExpressionResolver resolver, NodeData node) {
        for (SpecializationData specialization : node.getSpecializations()) {
            for (CacheExpression cache : specialization.getCaches()) {
                if (!cache.isCached()) {
                    continue;
                }
                Boolean inline = getAnnotationValue(Boolean.class, cache.getMessageAnnotation(), "inline", false);
                if (inline != null && inline && !isInliningSupported(cache)) {
                    AnnotationValue inlineValue = getAnnotationValue(cache.getMessageAnnotation(), "inline", false);
                    cache.addError(inlineValue, "Cached type '%s' does not support inlining. " + //
                                    "Only inlinable types are supported for nodes annotated with @%s. " + //
                                    "Inlinable types declare a static inline method or use the @%s annotation. " + //
                                    "Non node references and primtives types are also considered inlinable.",
                                    getSimpleName(cache.getParameter().getType()),
                                    getSimpleName(types.GenerateInline),
                                    getSimpleName(types.GenerateInline));
                }
            }
        }

        if (!node.shouldInlineByDefault()) {
            for (SpecializationData specialization : node.getSpecializations()) {
                for (CacheExpression cache : specialization.getCaches()) {
                    if (!cache.isCached()) {
                        continue;
                    }
                    if (cache.hasErrors()) {
                        continue;
                    }
                    if (hasInlineMethod(cache)) {
                        Boolean inline = getAnnotationValue(Boolean.class, cache.getMessageAnnotation(), "inline", false);
                        if (inline == null && !forceInlineByDefault(cache)) {
                            TypeMirror type = findRedirectedInliningType(specialization, cache);
                            String solutionHint = String.format("Set @%s(..., inline=true|false) to determine whether object-inlining should be performed. ",
                                            getSimpleName(types.Cached));
                            if (type != null) {
                                solutionHint = String.format("To use object-inlining use the cached type '%s' instead or set @%s(..., inline=true|false). ",
                                                getSimpleName(type),
                                                getSimpleName(types.Cached));
                            }

                            TypeMirror cacheType = type == null ? cache.getParameter().getType() : type;
                            ExecutableElement inlineMethod = lookupInlineMethod(resolver, node, cache, cacheType, null);
                            NodeSizeEstimate estimate = computeCachedInlinedSizeEstimate(inlineMethod);

                            AnnotationValue inlineValue = getAnnotationValue(cache.getMessageAnnotation(), "inline", false);
                            cache.addSuppressableWarning(TruffleSuppressedWarnings.INLINING_RECOMMENDATION,
                                            inlineValue, "The cached type '%s' supports object-inlining. The footprint is estimated to be reduced from %s to %s byte(s). %s" + //
                                                            "Alternatively @%s(alwaysInlineCached=true) can be used to enable inlining for an entire class or in combination with the inherit option for a hierarchy of node classes.",
                                            getSimpleName(cache.getParameter().getType()),
                                            estimate.notInlinedSize, estimate.inlinedSize,
                                            solutionHint,
                                            getSimpleName(types.GenerateCached));
                        }
                    }
                }
            }
        }

        for (SpecializationData specialization : node.getSpecializations()) {
            for (CacheExpression cache : specialization.getCaches()) {
                if (!cache.isCached()) {
                    continue;
                }
                if (cache.hasErrors()) {
                    continue;
                }
                Boolean inline = getAnnotationValue(Boolean.class, cache.getMessageAnnotation(), "inline", false);

                if (node.isGenerateInline() && inline != null && inline && cache.getInlinedNode() != null) {
                    // failing later see code below
                    continue;
                }

                if (inline != null) {
                    boolean defaultInline = node.shouldInlineByDefault() || forceInlineByDefault(cache);
                    if (inline && defaultInline || //
                                    (!inline && !defaultInline && !isInliningSupported(cache))) {
                        AnnotationValue inlineValue = getAnnotationValue(cache.getMessageAnnotation(), "inline", false);
                        cache.addSuppressableWarning(TruffleSuppressedWarnings.UNUSED, inlineValue, "Redundant specification of @Cached(... inline=%s). The attribute has no effect. " + //
                                        "Inlining is forced to '%s' for this cached parameter. " + //
                                        "To resolve this remove the redundant inlining attribute.",
                                        inline, defaultInline,
                                        getSimpleName(context.getType(SuppressWarnings.class)));
                    }
                }
            }
        }

        boolean emitErrors = node.isGenerateInline();
        boolean recommendInline = true;

        if (!node.getChildren().isEmpty()) {
            if (emitErrors) {
                node.addError("Error generating code for @%s: Inlinable nodes cannot use @%s. " + //
                                "Disable inlining generation or remove @%s to resolve this.", getSimpleName(types.GenerateInline),
                                getSimpleName(types.NodeChild), getSimpleName(types.NodeChild));
            }
            recommendInline = false;
        }

        if (!node.getFields().isEmpty()) {
            if (emitErrors) {
                node.addError("Error generating code for @%s: Inlinable nodes cannot use @%s. " + //
                                "Disable inlining generation or remove @%s to resolve this.", getSimpleName(types.GenerateInline),
                                getSimpleName(types.NodeField), getSimpleName(types.NodeField));
            }
            recommendInline = false;
        }

        VariableElement instanceField = getNodeFirstInstanceField(node.getTemplateType());
        if (instanceField != null) {
            if (emitErrors) {
                node.addError(getGenerateInlineAnnotation(node.getTemplateType().asType()), null, "Failed to generate code for @%s: The node must not declare any instance variables. " +
                                "Found instance variable %s.%s. Remove instance variable to resolve this.",
                                getSimpleName(types.GenerateInline),
                                getSimpleName(instanceField.getEnclosingElement().asType()), instanceField.getSimpleName().toString());
            }
            recommendInline = false;
        }

        for (SpecializationData specialization : node.getSpecializations()) {
            for (CacheExpression cache : specialization.getCaches()) {
                if (!cache.isCached()) {
                    continue;
                }
                AnnotationValue inlineValue = getAnnotationValue(cache.getMessageAnnotation(), "inline", false);
                Boolean inline = getAnnotationValue(Boolean.class, cache.getMessageAnnotation(), "inline", false);
                if (cache.getInlinedNode() != null && inline != null && inline) {
                    if (emitErrors) {
                        cache.addSuppressableWarning(TruffleSuppressedWarnings.UNUSED, inlineValue, "Redundant specification of @%s(... inline=true). " + //
                                        "Cached values of nodes with @%s are implicitely inlined.",
                                        getSimpleName(types.GenerateInline),
                                        getSimpleName(types.Cached),
                                        getSimpleName(types.GenerateInline));
                    }
                }
            }
        }

        ExecutableElement method = ElementUtils.findStaticMethod(node.getTemplateType(), "inline");
        if (method != null) {

            List<InlineFieldData> declaredFields = parseInlineMethod(node, method, method);
            if (node.hasErrors()) {
                return false;
            }
            List<InlineFieldData> requiredFields = FlatNodeGenFactory.createInlinedFields(node);

            boolean valid = declaredFields.size() == requiredFields.size();
            if (valid) {
                for (int i = 0; i < declaredFields.size(); i++) {
                    InlineFieldData declared = declaredFields.get(i);
                    InlineFieldData required = requiredFields.get(i);

                    if (!required.isCompatibleWith(declared)) {
                        valid = false;
                        break;
                    }
                }
            }

            if (!valid) {
                CodeExecutableElement expectedInline = NodeFactoryFactory.createInlineMethod(node, null);
                node.addError(method, "The custom inline method does not specify enough bit space or too few or too many fields for this node. The expected inline method for this node is:%n%s",
                                expectedInline.toString());
            }
        }

        return recommendInline;

    }

    public static boolean isGenerateUncached(TypeElement templateType) {
        AnnotationMirror annotation = findGenerateAnnotation(templateType.asType(), ProcessorContext.getInstance().getTypes().GenerateUncached);
        Boolean value = Boolean.FALSE;
        if (annotation != null) {
            value = ElementUtils.getAnnotationValue(Boolean.class, annotation, "value");
        }
        return value;
    }

    static boolean isGenerateInline(TypeElement templateType) {
        AnnotationMirror annotation = getGenerateInlineAnnotation(templateType.asType());
        Boolean value = Boolean.FALSE;
        if (annotation != null) {
            value = ElementUtils.getAnnotationValue(Boolean.class, annotation, "value");
        }
        return value;
    }

    static AnnotationMirror getGenerateInlineAnnotation(TypeMirror type) {
        return findGenerateAnnotation(type, ProcessorContext.getInstance().getTypes().GenerateInline);
    }

    public static AnnotationMirror findGenerateAnnotation(TypeMirror nodeType, DeclaredType annotationType) {
        TypeElement originalType = ElementUtils.castTypeElement(nodeType);
        TypeElement currentType = originalType;
        while (currentType != null) {
            AnnotationMirror annotation = ElementUtils.findAnnotationMirror(currentType, annotationType);
            if (annotation != null) {
                Boolean inherit = ElementUtils.getAnnotationValue(Boolean.class, annotation, "inherit");
                if (inherit == null) {
                    inherit = Boolean.TRUE;
                }

                if (currentType != originalType && !inherit) {
                    // not inherited from to the sub type
                    currentType = ElementUtils.castTypeElement(currentType.getSuperclass());
                    continue;
                }

                return annotation;
            }
            currentType = ElementUtils.castTypeElement(currentType.getSuperclass());
        }
        return null;
    }

    private void initializeAOT(NodeData node) {
        if (!node.isGenerateAOT()) {
            return;
        }

        // apply exclude rules
        for (SpecializationData specialization : node.getSpecializations()) {
            if (!specialization.isReachable() || specialization.getMethod() == null) {
                continue;
            }
            AnnotationMirror mirror = ElementUtils.findAnnotationMirror(specialization.getMethod(), types.GenerateAOT_Exclude);
            if (mirror != null) {
                // explicitly excluded
                continue;
            }

            specialization.setPrepareForAOT(true);
        }

        // exclude uncached specializations
        for (SpecializationData specialization : node.getSpecializations()) {
            if (specialization.getUncachedSpecialization() != null) {
                specialization.getUncachedSpecialization().setPrepareForAOT(false);
            }
        }

        // second pass to remove replaced specializations
        outer: for (SpecializationData specialization : node.getSpecializations()) {
            if (!specialization.isPrepareForAOT()) {
                continue;
            }

            // not reachable during AOT
            for (SpecializationData otherSpecialization : node.getSpecializations()) {
                if (otherSpecialization.getReplaces().contains(specialization) && otherSpecialization.isPrepareForAOT()) {
                    specialization.setPrepareForAOT(false);
                    continue outer;
                }
            }
        }

        // third pass validate included specializations
        outer: for (SpecializationData specialization : node.getSpecializations()) {
            if (!specialization.isPrepareForAOT()) {
                continue;
            }

            for (CacheExpression cache : specialization.getCaches()) {

                if (ElementUtils.typeEquals(cache.getParameter().getType(), node.getTemplateType().asType())) {
                    if (specialization.getGuards().isEmpty()) {
                        if (cache.usesDefaultCachedInitializer()) {
                            // guaranteed recursion
                            cache.addError("Failed to generate code for @%s: " + //
                                            "Recursive AOT preparation detected. Recursive AOT preparation is not supported as this would lead to infinite compiled code." + //
                                            "Resolve this problem by either: %n" + //
                                            " - Exclude this specialization from AOT with @%s.%s if it is acceptable to deoptimize for this specialization in AOT compiled code. %n" + //
                                            " - Configure the specialization to be replaced with a more generic specialization. %n" + //
                                            " - Add a specialization guard that guarantees that the recursion is finite. %n" + //
                                            " - Remove the cached parameter value. %n", //
                                            getSimpleName(types.GenerateAOT),
                                            getSimpleName(types.GenerateAOT), getSimpleName(types.GenerateAOT_Exclude));
                            continue;
                        }
                    }
                }

                if (cache.isMergedLibrary()) {
                    cache.addError("Merged libraries are not supported in combination with AOT preparation. " + //
                                    "Resolve this problem by either: %n" + //
                                    " - Setting @%s(..., useForAOT=false) to disable AOT preparation for this export. %n" + //
                                    " - Using a dispatched library without receiver expression. %n" + //
                                    " - Adding the @%s.%s annotation to the specialization or exported method.",
                                    getSimpleName(types.ExportLibrary),
                                    getSimpleName(types.GenerateAOT),
                                    getSimpleName(types.GenerateAOT_Exclude));
                    continue;
                }

                TypeMirror type = cache.getParameter().getType();
                if (NodeCodeGenerator.isSpecializedNode(type)) {
                    List<TypeElement> lookupTypes = collectSuperClasses(new ArrayList<>(), ElementUtils.castTypeElement(type));
                    AnnotationMirror generateAOT = findFirstAnnotation(lookupTypes, types.GenerateAOT);
                    if (generateAOT == null) {
                        cache.addError("Failed to generate code for @%s: " + //
                                        "Referenced node type cannot be initialized for AOT." + //
                                        "Resolve this problem by either: %n" + //
                                        " - Exclude this specialization from AOT with @%s.%s if it is acceptable to deoptimize for this specialization in AOT compiled code. %n" + //
                                        " - Configure the specialization to be replaced with a more generic specialization. %n" + //
                                        " - Remove the cached parameter value. %n" + //
                                        " - Add the @%s annotation to node type '%s' or one of its super types.",
                                        getSimpleName(types.GenerateAOT),
                                        getSimpleName(types.GenerateAOT), getSimpleName(types.GenerateAOT_Exclude),
                                        getSimpleName(types.GenerateAOT),
                                        getSimpleName(type));
                        continue;
                    }
                }

                boolean cachedLibraryAOT = false;
                if (cache.isCachedLibrary()) {
                    cachedLibraryAOT = ElementUtils.findAnnotationMirror(ElementUtils.fromTypeMirror(cache.getParameter().getType()), types.GenerateAOT) != null;
                }

                if (cache.isCachedLibrary() && cache.getCachedLibraryLimit() != null && !cache.getCachedLibraryLimit().equals("")) {
                    if (!cachedLibraryAOT) {
                        cache.addError("Failed to generate code for @%s: " + //
                                        "@%s with automatic dispatch cannot be prepared for AOT." + //
                                        "Resolve this problem by either: %n" + //
                                        " - Exclude this specialization from AOT with @%s.%s if it is acceptable to deoptimize for this specialization in AOT compiled code. %n" + //
                                        " - Configure the specialization to be replaced with a more generic specialization. %n" + //
                                        " - Remove the cached parameter value. %n" + //
                                        " - Define a cached library initializer expression for manual dispatch. %n" + //
                                        " - Add the @%s annotation to the %s library class to enable AOT for the library.",
                                        getSimpleName(types.GenerateAOT),
                                        getSimpleName(types.CachedLibrary),
                                        getSimpleName(types.GenerateAOT), getSimpleName(types.GenerateAOT_Exclude),
                                        getSimpleName(types.GenerateAOT), getSimpleName(type));
                        continue;
                    }
                }

                if (specialization.isDynamicParameterBound(cache.getDefaultExpression(), true)) {

                    if (!cachedLibraryAOT) {
                        cache.addError("Failed to generate code for @%s: " + //
                                        "Cached values in specializations included for AOT must not bind dynamic values. " + //
                                        "Such caches are only allowed to bind static values, values read from the node or values from the current language instance using a language reference. " + //
                                        "Resolve this problem by either: %n" + //
                                        " - Exclude this specialization from AOT with @%s.%s if it is acceptable to deoptimize for this specialization in AOT compiled code. %n" + //
                                        " - Configure the specialization to be replaced with a more generic specialization. %n" + //
                                        " - Remove the cached parameter value. %n" + //
                                        " - Avoid binding dynamic parameters in the cache initializer expression. %n" + //
                                        " - If a cached library is used add the @%s annotation to the library class to enable AOT for the library.",
                                        getSimpleName(types.GenerateAOT),
                                        getSimpleName(types.GenerateAOT), getSimpleName(types.GenerateAOT_Exclude),
                                        getSimpleName(types.GenerateAOT));
                        continue outer;
                    }
                }
            }
        }

    }

    public static Map<CacheExpression, String> computeSharing(Element templateType, Collection<NodeData> nodes, boolean emitSharingWarnings) {
        TruffleTypes types = ProcessorContext.getInstance().getTypes();
        Map<SharableCache, Collection<CacheExpression>> groups = computeSharableCaches(nodes);
        // compute unnecessary sharing.

        Map<String, List<SharableCache>> declaredGroups = new HashMap<>();
        for (NodeData node : nodes) {
            for (SpecializationData specialization : node.getSpecializations()) {
                for (CacheExpression cache : specialization.getCaches()) {
                    if (cache.isAlwaysInitialized()) {
                        continue;
                    }
                    String group = cache.getSharedGroup();
                    if (group != null) {
                        declaredGroups.computeIfAbsent(group, (v) -> new ArrayList<>()).add(new SharableCache(specialization, cache));
                    }
                }
            }
        }

        Map<CacheExpression, String> sharedExpressions = new LinkedHashMap<>();
        for (NodeData node : nodes) {
            for (SpecializationData specialization : node.getSpecializations()) {
                for (CacheExpression cache : specialization.getCaches()) {
                    if (cache.isAlwaysInitialized()) {
                        continue;
                    }
                    Element declaringElement;
                    if (node.getTemplateType() instanceof GeneratedElement) {
                        // generated node
                        declaringElement = node.getTemplateType().getEnclosingElement();
                        if (!declaringElement.getKind().isClass() &&
                                        !declaringElement.getKind().isInterface()) {
                            // throw new AssertionError("Unexpected declared element for generated
                            // element: " + declaringElement.toString());

                            declaringElement = node.getTemplateType();
                        }
                    } else {
                        declaringElement = node.getTemplateType();
                    }

                    String group = cache.getSharedGroup();
                    SharableCache sharable = new SharableCache(specialization, cache);
                    Collection<CacheExpression> expressions = groups.get(sharable);
                    List<SharableCache> declaredSharing = declaredGroups.get(group);
                    if (group != null) {
                        if (declaredSharing.size() <= 1) {
                            if (!ElementUtils.elementEquals(templateType, declaringElement)) {
                                // ignore errors for single declared sharing as its not in the same
                                // class but only happens between class and superclass. These
                                // errors might not be resolvable if the base class is not
                                // modifiable.
                                continue;
                            }
                        }

                        if (declaredSharing.size() <= 1 && (expressions == null || expressions.size() <= 1)) {
                            if (declaredSharing.size() == 1 && expressions != null && expressions.size() == 1 && declaredSharing.get(0).specialization.hasMultipleInstances()) {
                                // allow single shared in multiple instance specialization
                                sharedExpressions.put(sharable.expression, group);
                            } else {
                                cache.addError(cache.getSharedGroupMirror(), cache.getSharedGroupValue(),
                                                "Could not find any other cached parameter that this parameter could be shared. " +
                                                                "Cached parameters are only sharable if they declare the same type and initializer expressions and if the specialization only has a single instance. " +
                                                                "Remove the @%s annotation or make the parameter sharable to resolve this.",
                                                types.Cached_Shared.asElement().getSimpleName().toString());
                            }
                        } else {
                            if (declaredSharing.size() <= 1) {

                                String error = String.format("No other cached parameters are specified as shared with the group '%s'.", group);
                                Set<String> similarGroups = new LinkedHashSet<>(declaredGroups.keySet());
                                similarGroups.remove(group);
                                List<String> fuzzyMatches = ExportsParser.fuzzyMatch(similarGroups, group, 0.7f);
                                if (!fuzzyMatches.isEmpty()) {
                                    StringBuilder appendix = new StringBuilder(" Did you mean ");
                                    String sep = "";
                                    for (String string : fuzzyMatches) {
                                        appendix.append(sep);
                                        appendix.append('\'').append(string).append('\'');
                                        sep = ", ";
                                    }
                                    error += appendix.toString() + "?";
                                }
                                cache.addError(cache.getSharedGroupMirror(), cache.getSharedGroupValue(), error);
                            } else {
                                StringBuilder b = new StringBuilder();
                                for (SharableCache otherCache : declaredSharing) {
                                    if (cache == otherCache.expression) {
                                        continue;
                                    }
                                    String reason = sharable.equalsWithReason(otherCache);
                                    if (reason == null) {
                                        continue;
                                    }
                                    String signature = formatCacheExpression(otherCache.expression);
                                    b.append(String.format("  - %s : %s%n", signature, reason));
                                }
                                if (b.length() != 0) {
                                    cache.addError(cache.getSharedGroupMirror(), cache.getSharedGroupValue(),
                                                    "Could not share some of the cached parameters in group '%s': %n%sRemove the @%s annotation or resolve the described issues to allow sharing.",
                                                    group,
                                                    b.toString(),
                                                    types.Cached_Shared.asElement().getSimpleName().toString());
                                } else {
                                    sharedExpressions.put(sharable.expression, group);
                                }
                            }
                        }
                    } else if (expressions != null && expressions.size() > 1) {
                        if (emitSharingWarnings) {
                            List<CacheExpression> filteredExpressions = new ArrayList<>();
                            for (CacheExpression expression : expressions) {
                                /*
                                 * We only emit sharing warnings for the same declaring type,
                                 * because otherwise sharing warnings might not be resolvable if the
                                 * base type is not modifiable.
                                 */
                                if (ElementUtils.isDeclaredIn(expression.getParameter().getVariableElement(), declaringElement)) {
                                    filteredExpressions.add(expression);
                                }
                            }
                            if (filteredExpressions.size() > 1 && findAnnotationMirror(cache.getParameter().getVariableElement(), types.Cached_Exclusive) == null) {
                                StringBuilder sharedCaches = new StringBuilder();
                                Set<String> recommendedGroups = new LinkedHashSet<>();
                                for (CacheExpression cacheExpression : filteredExpressions) {
                                    if (cacheExpression != cache) {
                                        String signature = formatCacheExpression(cacheExpression);
                                        sharedCaches.append(String.format("  - %s%n", signature));
                                        String otherGroup = cacheExpression.getSharedGroup();
                                        if (otherGroup != null) {
                                            recommendedGroups.add(otherGroup);
                                        }
                                    }
                                }

                                String recommendedGroup = recommendedGroups.size() == 1 ? recommendedGroups.iterator().next() : "group";
                                cache.addSuppressableWarning(TruffleSuppressedWarnings.SHARING_RECOMMENDATION,
                                                "The cached parameter may be shared with: %n%s Annotate the parameter with @%s(\"%s\") or @%s to allow or deny sharing of the parameter.",
                                                sharedCaches, types.Cached_Shared.asElement().getSimpleName().toString(),
                                                recommendedGroup,
                                                types.Cached_Exclusive.asElement().getSimpleName().toString());
                            }
                        }
                    }
                }
            }
        }
        return sharedExpressions;
    }

    private static String formatCacheExpression(CacheExpression cacheExpression) {
        VariableElement cacheParameter = cacheExpression.getParameter().getVariableElement();
        ExecutableElement method = (ExecutableElement) cacheParameter.getEnclosingElement();
        StringBuilder builder = new StringBuilder();
        builder.append(method.getSimpleName().toString());
        builder.append("(");
        int index = method.getParameters().indexOf(cacheParameter);
        if (index != 0) {
            builder.append("..., ");
        }
        String annotationName = cacheExpression.getMessageAnnotation().getAnnotationType().asElement().getSimpleName().toString();
        builder.append(String.format("@%s(...) ", annotationName));
        builder.append(getSimpleName(cacheParameter.asType()));
        builder.append(" ");
        builder.append(cacheParameter.getSimpleName().toString());
        if (index != method.getParameters().size() - 1) {
            builder.append(",...");
        }
        builder.append(")");
        return builder.toString();
    }

    private void initializeReceiverBound(NodeData node) {
        boolean requireNodeUnbound = mode == ParseMode.EXPORTED_MESSAGE;
        boolean nodeBound = false;

        for (SpecializationData specialization : node.getSpecializations()) {
            if (!specialization.isReachable() || specialization.getMethod() == null) {
                continue;
            }
            ExecutableElement specializationMethod = specialization.getMethod();
            if (!specializationMethod.getModifiers().contains(Modifier.STATIC)) {
                nodeBound = true;
                if (requireNodeUnbound) {
                    specialization.addError("@%s annotated nodes must declare static @%s methods. " +
                                    "Add a static modifier to the method to resolve this.",
                                    types.ExportMessage.asElement().getSimpleName().toString(),
                                    types.Specialization.asElement().getSimpleName().toString());
                }
                break;
            }
            for (GuardExpression guard : specialization.getGuards()) {
                DSLExpression guardExpression = guard.getExpression();
                if (guardExpression.isNodeReceiverBound()) {
                    nodeBound = true;
                    if (requireNodeUnbound && guardExpression.isNodeReceiverImplicitlyBound()) {
                        guard.addError("@%s annotated nodes must only refer to static guard methods or fields. " +
                                        "Add a static modifier to the bound guard method or field to resolve this.",
                                        types.ExportMessage.asElement().getSimpleName().toString());
                    }
                    break;
                }
            }
            for (CacheExpression cache : specialization.getCaches()) {
                DSLExpression cachedInitializer = cache.getDefaultExpression();
                if (cachedInitializer != null && !cache.isMergedLibrary() && cachedInitializer.isNodeReceiverBound()) {
                    nodeBound = true;
                    if (requireNodeUnbound && cachedInitializer.isNodeReceiverImplicitlyBound()) {
                        cache.addError("@%s annotated nodes must only refer to static cache initializer methods or fields. " +
                                        "Add a static modifier to the bound cache initializer method or field or " +
                                        "use the keyword 'this' to refer to the receiver type explicitly.",
                                        types.ExportMessage.asElement().getSimpleName().toString());
                    }
                    break;
                }
            }
            DSLExpression limit = specialization.getLimitExpression();
            if (limit != null && limit.isNodeReceiverBound()) {
                nodeBound = true;
                if (requireNodeUnbound && limit.isNodeReceiverImplicitlyBound()) {
                    specialization.addError("@%s annotated nodes must only refer to static limit initializer methods or fields. " +
                                    "Add a static modifier to the bound cache initializer method or field or " +
                                    "use the keyword 'this' to refer to the receiver type explicitly.",
                                    types.ExportMessage.asElement().getSimpleName().toString());
                }
                break;
            }
        }

        node.setNodeBound(nodeBound);
    }

    private VariableElement getNodeFirstInstanceField(TypeElement nodeType) {
        TypeElement currentType = nodeType;
        while (currentType != null) {
            if (ElementUtils.typeEquals(currentType.asType(), types.Node)) {
                // don't care about fields in node.
                break;
            }
            for (VariableElement field : ElementFilter.fieldsIn(currentType.getEnclosedElements())) {
                if (field.getModifiers().contains(Modifier.STATIC)) {
                    continue;
                }
                return field;
            }
            currentType = ElementUtils.getSuperType(currentType);
        }
        return null;
    }

    private void initializeUncachable(NodeData node) {
        AnnotationMirror generateUncached = findAnnotationMirror(node.getTemplateType().getAnnotationMirrors(), types.GenerateUncached);

        boolean requireUncachable = node.isGenerateUncached();
        boolean uncachable = true;

        VariableElement instanceField = getNodeFirstInstanceField(node.getTemplateType());
        if (instanceField != null) {
            uncachable = false;

            if (requireUncachable) {
                node.addError(generateUncached, null, "Failed to generate code for @%s: The node must not declare any instance variables. " +
                                "Found instance variable %s.%s. Remove instance variable to resolve this.",
                                types.GenerateUncached.asElement().getSimpleName().toString(),
                                getSimpleName(instanceField.getEnclosingElement().asType()), instanceField.getSimpleName().toString());
            }
        }

        for (SpecializationData specialization : node.computeUncachedSpecializations(node.getSpecializations())) {
            if (!specialization.isReachable()) {
                continue;
            }
            for (CacheExpression cache : specialization.getCaches()) {
                if (cache.getUncachedExpression() == null) {

                    if (specialization.isReplaced()) {
                        // for compatibility reasons
                        specialization.setExcludeForUncached(true);
                    } else {
                        uncachable = false;
                    }
                    if (requireUncachable) {
                        String message = String.format("Failed to generate code for @%s: The specialization uses @%s without valid uncached expression. %s " +
                                        "To resolve this specify the uncached or allowUncached attribute in @%s or exclude the specialization from @%s using @%s(excludeForUncached=true).",
                                        getSimpleName(types.GenerateUncached),
                                        getSimpleName(types.Cached),
                                        cache.getUncachedExpressionError() != null ? cache.getUncachedExpressionError().getText() : "",
                                        getSimpleName(types.Cached),
                                        getSimpleName(types.GenerateUncached),
                                        getSimpleName(types.Specialization));

                        if (uncachable) {
                            // for compatibility reasons with previous DSL specifications we
                            // need to emit this error as a warning.
                            cache.addWarning(message + " This error is a warning for compatibility reasons. This specialization is ignored for @%s until the warning is fixed.",
                                            getSimpleName(types.GenerateUncached));
                        } else {
                            cache.addError(message);
                        }
                    }
                    if (!uncachable) {
                        break;
                    }
                }
            }
        }

        int effectiveExecutionCount = 0;
        for (NodeExecutionData childExecution : node.getChildExecutions()) {
            if (childExecution.getChild() == null || !childExecution.getChild().isAllowUncached()) {
                effectiveExecutionCount++;
            }
        }

        List<ExecutableTypeData> validExecutableType = new ArrayList<>();
        for (ExecutableTypeData executableType : node.getExecutableTypes()) {
            if (executableType.getMethod() != null && executableType.getEvaluatedCount() >= effectiveExecutionCount) {
                validExecutableType.add(executableType);
                break;
            }
        }
        if (validExecutableType.isEmpty()) {
            uncachable = false;
            if (requireUncachable) {
                node.addError(generateUncached, null, "Failed to generate code for @%s: " +
                                "The node does not declare any execute method with %s evaluated argument(s). " +
                                "The generated uncached node does not declare an execute method that can be generated by the DSL. " +
                                "Declare a non-final method that starts with 'execute' and takes %s argument(s) or variable arguments to resolve this.",
                                types.GenerateUncached.asElement().getSimpleName().toString(),
                                effectiveExecutionCount,
                                effectiveExecutionCount);
            }
        }

        node.setUncachable(uncachable);
    }

    private static void initializeFallbackReachability(NodeData node) {
        TruffleTypes types = ProcessorContext.getInstance().getTypes();
        List<SpecializationData> specializations = node.getSpecializations();
        SpecializationData fallback = null;
        for (int i = specializations.size() - 1; i >= 0; i--) {
            SpecializationData specialization = specializations.get(i);
            if (specialization.isFallback() && specialization.getMethod() != null) {
                fallback = specialization;
                break;
            }
        }

        if (fallback == null) {
            // no need to compute reachability
            return;
        }

        for (int index = 0; index < specializations.size(); index++) {
            SpecializationData specialization = specializations.get(index);
            SpecializationData lastReachable = specialization;
            for (int searchIndex = index + 1; searchIndex < specializations.size(); searchIndex++) {
                SpecializationData search = specializations.get(searchIndex);
                if (search == fallback) {
                    // reached the end of the specialization
                    break;
                }
                assert lastReachable != search;
                if (!lastReachable.isReachableAfter(search)) {
                    lastReachable = search;
                } else if (search.getReplaces().contains(specialization)) {
                    lastReachable = search;
                }
            }

            specialization.setReachesFallback(lastReachable == specialization);

            List<SpecializationData> failedSpecializations = null;
            if (specialization.isReachesFallback() && !specialization.getCaches().isEmpty() && !specialization.getGuards().isEmpty()) {
                boolean failed = false;
                if (specialization.getMaximumNumberOfInstances() > 1) {
                    for (GuardExpression guard : specialization.getGuards()) {
                        if (specialization.isGuardBoundWithCache(guard)) {
                            failed = true;
                            break;
                        }
                    }
                }

                for (CacheExpression cache : specialization.getCaches()) {
                    if (cache.isWeakReferenceGet()) {
                        failed = true;
                        break;
                    }
                }

                if (failed) {
                    if (failedSpecializations == null) {
                        failedSpecializations = new ArrayList<>();
                    }
                    failedSpecializations.add(specialization);
                }
            }

            if (failedSpecializations != null) {
                List<String> specializationIds = failedSpecializations.stream().map((e) -> e.getId()).collect(Collectors.toList());
                fallback.addError(
                                "Some guards for the following specializations could not be negated for the @%s specialization: %s. " +
                                                "Guards cannot be negated for the @%s when they bind @%s parameters and the specialization may consist of multiple instances or if any of the @%s parameters is configured as weak. " +
                                                "To fix this limit the number of instances to '1' or " +
                                                "introduce a more generic specialization declared between this specialization and the fallback. " +
                                                "Alternatively the use of @%s can be avoided by declaring a @%s with manually specified negated guards.",
                                ElementUtils.getSimpleName(types.Fallback), specializationIds,
                                ElementUtils.getSimpleName(types.Fallback), ElementUtils.getSimpleName(types.Cached), ElementUtils.getSimpleName(types.Cached),
                                ElementUtils.getSimpleName(types.Fallback), ElementUtils.getSimpleName(types.Specialization));
            }

        }

        for (SpecializationData specialization : specializations) {
            if (!specialization.getAssumptionExpressions().isEmpty() && specialization.isReachesFallback()) {
                specialization.addSuppressableWarning(TruffleSuppressedWarnings.ASSUMPTION,
                                """
                                                It is discouraged to use assumptions with a specialization that reaches a @%s specialization.\s\
                                                Specialization instances get removed if assumptions are no longer valid, which may lead to unexpected @%s invocations.\s\
                                                This may be fixed by translating the assumption usage to a regular method guard instead.\s\
                                                Instead of assumptions="a" you may use guards="a.isValid()".\s\
                                                This problem may also be fixed by adding a new more generic specialization that replaces this specialization.\s\
                                                """,
                                getSimpleName(types.Fallback),
                                getSimpleName(types.Fallback));
            }
        }

    }

    private static void initializeExecutableTypeHierarchy(NodeData node) {
        List<ExecutableTypeData> rootTypes = buildExecutableHierarchy(node);
        List<ExecutableTypeData> additionalAbstractRootTypes = new ArrayList<>();
        for (int i = 1; i < rootTypes.size(); i++) {
            ExecutableTypeData rootType = rootTypes.get(i);
            if (rootType.isAbstract()) {
                // cannot implemement root
                additionalAbstractRootTypes.add(rootType);
            } else {
                node.getExecutableTypes().remove(rootType);
            }
        }
        if (!additionalAbstractRootTypes.isEmpty()) {
            node.addError("Incompatible abstract execute methods found %s.", additionalAbstractRootTypes);
        }

        namesUnique(node.getExecutableTypes());

    }

    private void initializePolymorphicExecutable(NodeData node) throws AssertionError {
        SpecializationData generic = node.getFallbackSpecialization();

        Collection<TypeMirror> frameTypes = new HashSet<>();
        for (SpecializationData specialization : node.getSpecializations()) {
            if (specialization.getFrame() != null) {
                frameTypes.add(specialization.getFrame().getType());
            }
        }
        if (node.supportsFrame()) {
            frameTypes.add(node.getFrameType());
        }

        TypeMirror frameType = null;
        if (!frameTypes.isEmpty()) {
            frameTypes = uniqueSortedTypes(frameTypes, false);
            if (frameTypes.size() == 1) {
                frameType = frameTypes.iterator().next();
            } else {
                frameType = types.Frame;
            }
        }

        List<TypeMirror> parameterTypes = new ArrayList<>();
        TypeMirror returnType = null;
        for (Parameter genericParameter : generic.getReturnTypeAndParameters()) {
            TypeMirror polymorphicType;
            if (genericParameter.getLocalName().equals(TemplateMethod.FRAME_NAME)) {
                continue;
            }
            boolean isReturnParameter = genericParameter == generic.getReturnType();
            if (!genericParameter.getSpecification().isSignature()) {
                polymorphicType = genericParameter.getType();
            } else {
                NodeExecutionData execution = genericParameter.getSpecification().getExecution();
                Collection<TypeMirror> usedTypes = new HashSet<>();
                for (SpecializationData specialization : node.getSpecializations()) {
                    Parameter parameter = specialization.findParameter(genericParameter.getLocalName());
                    if (parameter == specialization.getReturnType() && specialization.isFallback() && specialization.getMethod() == null) {
                        continue;
                    }
                    if (parameter == null) {
                        throw new AssertionError("Parameter existed in generic specialization but not in specialized. param = " + genericParameter.getLocalName());
                    }
                    if (isReturnParameter && specialization.hasUnexpectedResultRewrite()) {
                        if (!isSubtypeBoxed(context, context.getType(Object.class), node.getGenericType(execution))) {
                            specialization.addError("Implicit 'Object' return type from UnexpectedResultException not compatible with generic type '%s'.", node.getGenericType(execution));
                        } else {
                            // if any specialization throws UnexpectedResultException, Object could
                            // be returned
                            usedTypes.add(context.getType(Object.class));
                        }
                    }
                    usedTypes.add(parameter.getType());
                }
                usedTypes = uniqueSortedTypes(usedTypes, false);

                if (usedTypes.size() == 1) {
                    polymorphicType = usedTypes.iterator().next();
                } else {
                    polymorphicType = getCommonSuperType(context, usedTypes);
                }

                if (execution != null && !isSubtypeBoxed(context, polymorphicType, node.getGenericType(execution))) {
                    throw new AssertionError(String.format("Polymorphic types %s not compatible to generic type %s.", polymorphicType, node.getGenericType(execution)));
                }

            }
            if (isReturnParameter) {
                returnType = polymorphicType;
            } else {
                parameterTypes.add(polymorphicType);
            }
        }

        boolean polymorphicSignatureFound = false;
        List<TypeMirror> dynamicTypes = parameterTypes;

        ExecutableTypeData polymorphicType = new ExecutableTypeData(node, returnType, "execute", frameType, dynamicTypes);
        String genericName = ExecutableTypeData.createName(polymorphicType) + "_";
        polymorphicType.setUniqueName(genericName);

        for (ExecutableTypeData type : node.getExecutableTypes()) {
            if (polymorphicType.sameSignature(type)) {
                polymorphicSignatureFound = true;
                break;
            }
        }

        if (!polymorphicSignatureFound) {
            node.getExecutableTypes().add(polymorphicType);
        }
        node.setPolymorphicExecutable(polymorphicType);
    }

    private static List<ExecutableTypeData> buildExecutableHierarchy(NodeData node) {
        List<ExecutableTypeData> executes = node.getExecutableTypes();
        if (executes.isEmpty()) {
            return Collections.emptyList();
        }
        List<ExecutableTypeData> hierarchyExecutes = new ArrayList<>(executes);
        Collections.sort(hierarchyExecutes);
        ExecutableTypeData parent = hierarchyExecutes.get(0);
        ListIterator<ExecutableTypeData> executesIterator = hierarchyExecutes.listIterator(1);
        buildExecutableHierarchy(node, parent, executesIterator);
        return hierarchyExecutes;
    }

    private static void buildExecutableHierarchy(NodeData node, ExecutableTypeData parent, ListIterator<ExecutableTypeData> executesIterator) {
        while (executesIterator.hasNext()) {
            ExecutableTypeData other = executesIterator.next();
            if (other.canDelegateTo(parent)) {
                parent.addDelegatedFrom(other);
                executesIterator.remove();
            }
        }
        for (int i = 1; i < parent.getDelegatedFrom().size(); i++) {
            buildExecutableHierarchy(node, parent.getDelegatedFrom().get(i - 1), parent.getDelegatedFrom().listIterator(i));
        }
    }

    private boolean containsSpecializations(List<Element> elements) {
        boolean foundSpecialization = false;
        for (ExecutableElement method : ElementFilter.methodsIn(elements)) {
            if (findAnnotationMirror(method, types.Specialization) != null) {
                foundSpecialization = true;
                break;
            }
        }
        return foundSpecialization;
    }

    private Element getVisibiltySource(NodeData nodeData) {
        if (mode == ParseMode.DEFAULT) {
            return nodeData.getTemplateType();
        } else {
            return exportDeclarationType;
        }
    }

    private void initializeStaticImports(NodeData node, List<TypeElement> lookupTypes, List<Element> elements) {
        for (TypeElement lookupType : lookupTypes) {
            initializeStaticImport(node, lookupType, elements);
        }

        if (mode == ParseMode.OPERATION) {
            // Operations can inherit static imports from the root node. Add root node imports after
            // operation imports so that operation imports take precedence.
            initializeStaticImport(node, bytecodeRootNodeType, elements);
        }
    }

    private void initializeStaticImport(NodeData node, TypeElement lookupType, List<Element> elements) {
        AnnotationMirror importAnnotation = findAnnotationMirror(lookupType, types.ImportStatic);
        if (importAnnotation == null) {
            return;
        }
        AnnotationValue importClassesValue = getAnnotationValue(importAnnotation, "value");
        List<TypeMirror> importClasses = getAnnotationValueList(TypeMirror.class, importAnnotation, "value");
        if (importClasses.isEmpty()) {
            node.addError(importAnnotation, importClassesValue, "At least one import class must be specified.");
            return;
        }
        for (TypeMirror importClass : importClasses) {
            if (importClass.getKind() != TypeKind.DECLARED) {
                node.addError(importAnnotation, importClassesValue, "The specified static import class '%s' is not a declared type.", getQualifiedName(importClass));
                continue;
            }

            TypeElement importClassElement = fromTypeMirror(context.reloadType(importClass));
            if (!ElementUtils.isVisible(getVisibiltySource(node), importClassElement)) {
                node.addError(importAnnotation, importClassesValue, "The specified static import class '%s' is not visible.",
                                getQualifiedName(importClass));
            }
            elements.addAll(importVisibleStaticMembersImpl(node.getTemplateType(), importClassElement, false));
        }
    }

    private record ImportsKey(TypeElement relativeTo, TypeElement importGuardsClass, boolean includeConstructors) {
    }

    private final Map<ImportsKey, List<Element>> importCache = ProcessorContext.getInstance().getCacheMap(ImportsKey.class);

    @SuppressWarnings("unchecked")
    private List<Element> importVisibleStaticMembersImpl(TypeElement relativeTo, TypeElement importType, boolean includeConstructors) {
        ImportsKey key = new ImportsKey(relativeTo, importType, includeConstructors);
        List<Element> elements = importCache.get(key);
        if (elements != null) {
            return elements;
        }

        List<Element> members = importVisibleStaticMembers(relativeTo, importType, includeConstructors);
        importCache.put(key, members);
        return members;
    }

    public static List<Element> importVisibleStaticMembers(TypeElement relativeTo, TypeElement importType, boolean includeConstructors) {
        ProcessorContext context = ProcessorContext.getInstance();
        // hack to reload type is necessary for incremental compiling in eclipse.
        // otherwise methods inside of import guard types are just not found.
        TypeElement importElement = fromTypeMirror(context.reloadType(importType.asType()));

        List<Element> members = new ArrayList<>();
        List<? extends Element> importMembers = CompilerFactory.getCompiler(importType).getAllMembersInDeclarationOrder(context.getEnvironment(), importType);
        // add default constructor
        if (includeConstructors && ElementUtils.isVisible(relativeTo, importElement) && ElementFilter.constructorsIn(importMembers).isEmpty()) {
            CodeExecutableElement executable = new CodeExecutableElement(modifiers(Modifier.PUBLIC), importElement.asType(), null);
            executable.setEnclosingElement(importType);
            members.add(executable);
        }

        for (Element importMember : importMembers) {
            if (importMember.getModifiers().contains(Modifier.PRIVATE)) {
                continue;
            }
            if (includeConstructors && importMember.getKind() == ElementKind.CONSTRUCTOR) {
                members.add(importMember);
                continue;
            }
            if (!importMember.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }

            ElementKind kind = importMember.getKind();
            if (kind.isField() || kind == ElementKind.METHOD) {
                members.add(importMember);
            }
        }

        /*
         * Sort elements by enclosing type to ensure that duplicate static methods are used from the
         * most concrete subtype.
         */
        Collections.sort(members, new Comparator<Element>() {
            Map<TypeMirror, Set<String>> cachedQualifiedNames = new HashMap<>();

            public int compare(Element o1, Element o2) {
                TypeMirror e1 = ElementUtils.findNearestEnclosingType(o1).orElseThrow(AssertionError::new).asType();
                TypeMirror e2 = ElementUtils.findNearestEnclosingType(o2).orElseThrow(AssertionError::new).asType();

                Set<String> e1SuperTypes = getCachedSuperTypes(e1);
                Set<String> e2SuperTypes = getCachedSuperTypes(e2);
                return compareByTypeHierarchy(e1, e1SuperTypes, e2, e2SuperTypes);
            }

            private Set<String> getCachedSuperTypes(TypeMirror e) {
                if (e == null) {
                    return Collections.emptySet();
                }
                Set<String> superTypes = cachedQualifiedNames.get(e);
                if (superTypes == null) {
                    superTypes = new HashSet<>(getQualifiedSuperTypeNames(fromTypeMirror(e)));
                    cachedQualifiedNames.put(e, superTypes);
                }
                return superTypes;
            }
        });
        return members;
    }

    private NodeData parseNodeData(TypeElement templateType, List<TypeElement> typeHierarchy) {
        AnnotationMirror typeSystemMirror = findFirstAnnotation(typeHierarchy, types.TypeSystemReference);
        TypeSystemData typeSystem = null;
        if (typeSystemMirror != null) {
            TypeMirror typeSystemType = getAnnotationValue(TypeMirror.class, typeSystemMirror, "value");
            TypeElement type = ElementUtils.castTypeElement(typeSystemType);
            typeSystem = context.parseIfAbsent(type, TypeSystemParser.class, (e) -> {
                TypeSystemParser parser = new TypeSystemParser();
                return parser.parse(e, false);
            });
            if (typeSystem == null) {
                NodeData nodeData = new NodeData(context, parsingParent, templateType);
                nodeData.addError("The used type system '%s' is invalid. Fix errors in the type system first.", getQualifiedName(typeSystemType));
                return nodeData;
            }
        } else {
            // default dummy type system
            typeSystem = new TypeSystemData(context, templateType, null, true);
        }
        boolean useNodeFactory = findFirstAnnotation(typeHierarchy, types.GenerateNodeFactory) != null;
        AnnotationMirror generateUncachedMirror = null;
        boolean needsInherit = false;
        for (Element element : typeHierarchy) {
            AnnotationMirror mirror = findAnnotationMirror(element, types.GenerateUncached);
            if (mirror != null) {
                generateUncachedMirror = mirror;
                break;
            }
            needsInherit = true;
        }
        boolean generateUncached;
        if (generateUncachedMirror != null) {
            if (needsInherit) {
                generateUncached = ElementUtils.getAnnotationValue(Boolean.class, generateUncachedMirror, "inherit");
            } else {
                generateUncached = true;
            }
        } else {
            generateUncached = false;
        }
        boolean generatePackagePrivate = findFirstAnnotation(typeHierarchy, types.GeneratePackagePrivate) != null;
        return new NodeData(context, parsingParent, templateType, typeSystem, useNodeFactory, generateUncached, generatePackagePrivate);

    }

    private List<NodeFieldData> parseFields(List<TypeElement> typeHierarchy, List<? extends Element> elements) {
        Set<String> names = new HashSet<>();

        List<NodeFieldData> fields = new ArrayList<>();
        for (VariableElement field : ElementFilter.fieldsIn(elements)) {
            if (field.getModifiers().contains(Modifier.STATIC)) {
                continue;
            } else if (ElementUtils.findAnnotationMirror(field, types.Executed) != null) {
                continue;
            }
            if (field.getModifiers().contains(Modifier.PUBLIC) || field.getModifiers().contains(Modifier.PROTECTED)) {
                String name = field.getSimpleName().toString();
                fields.add(new NodeFieldData(field, null, field, false));
                names.add(name);
            }
        }

        List<TypeElement> reversedTypeHierarchy = new ArrayList<>(typeHierarchy);
        Collections.reverse(reversedTypeHierarchy);
        for (TypeElement typeElement : reversedTypeHierarchy) {
            AnnotationMirror nodeChildrenMirror = findAnnotationMirror(typeElement, types.NodeFields);
            List<AnnotationMirror> children = collectAnnotations(nodeChildrenMirror, "value", typeElement, types.NodeField);

            for (AnnotationMirror mirror : children) {
                String name = firstLetterLowerCase(getAnnotationValue(String.class, mirror, "name"));
                TypeMirror type = getAnnotationValue(TypeMirror.class, mirror, "type");

                if (type != null) {
                    NodeFieldData field = new NodeFieldData(typeElement, mirror, new CodeVariableElement(type, name), true);
                    if (name.isEmpty()) {
                        field.addError(getAnnotationValue(mirror, "name"), "Field name cannot be empty.");
                    } else if (names.contains(name)) {
                        field.addError(getAnnotationValue(mirror, "name"), "Duplicate field name '%s'.", name);
                    }

                    names.add(name);

                    fields.add(field);
                } else {
                    // Type is null here. This indicates that the type could not be resolved.
                    // The Java compiler will subsequently raise the appropriate error.
                }
            }
        }

        for (NodeFieldData nodeFieldData : fields) {
            nodeFieldData.setGetter(findGetter(elements, nodeFieldData.getName(), nodeFieldData.getType()));
            nodeFieldData.setSetter(findSetter(elements, nodeFieldData.getName(), nodeFieldData.getType()));
        }

        return fields;
    }

    private List<NodeChildData> parseChildren(NodeData node, final List<TypeElement> typeHierarchy, List<? extends Element> elements) {
        Map<String, TypeMirror> castNodeTypes = new HashMap<>();
        for (ExecutableElement method : ElementFilter.methodsIn(elements)) {
            AnnotationMirror mirror = findAnnotationMirror(method, types.CreateCast);
            if (mirror != null) {
                List<String> children = (getAnnotationValueList(String.class, mirror, "value"));
                if (children != null) {
                    for (String child : children) {
                        castNodeTypes.put(child, method.getReturnType());
                    }
                }
            }
        }

        List<NodeChildData> executedFieldChildren = new ArrayList<>();
        for (VariableElement field : ElementFilter.fieldsIn(elements)) {
            if (field.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            AnnotationMirror executed = findAnnotationMirror(field.getAnnotationMirrors(), types.Executed);
            if (executed != null) {
                TypeMirror type = field.asType();
                String name = field.getSimpleName().toString();
                Cardinality cardinality = Cardinality.ONE;
                if (type.getKind() == TypeKind.ARRAY) {
                    cardinality = Cardinality.MANY;
                }
                AnnotationValue executedWith = getAnnotationValue(executed, "with");
                NodeChildData child = new NodeChildData(field, executed, name, type, type, field, cardinality, executedWith, null, null);
                executedFieldChildren.add(child);

                if (field.getModifiers().contains(Modifier.PRIVATE)) {
                    child.addError("Field annotated with @%s must be visible for the generated subclass to execute.", types.Executed.asElement().getSimpleName().toString());
                }

                if (cardinality == Cardinality.ONE) {
                    if (ElementUtils.findAnnotationMirror(field, types.Node_Child) == null) {
                        child.addError("Field annotated with @%s must also be annotated with @%s.", types.Executed.asElement().getSimpleName().toString(),
                                        types.Node_Child.asElement().getSimpleName().toString());
                    }
                } else {
                    assert cardinality == Cardinality.MANY;
                    if (ElementUtils.findAnnotationMirror(field, types.Node_Children) == null) {
                        child.addError("Field annotated with @%s must also be annotated with @%s.", types.Executed.asElement().getSimpleName().toString(),
                                        types.Node_Children.asElement().getSimpleName().toString());
                    }
                }
            }
        }

        NodeChildData many = null;
        Set<String> names = new HashSet<>();
        for (NodeChildData child : executedFieldChildren) {
            if (child.needsGeneratedField()) {
                throw new AssertionError("Should not need generated field.");
            }
            if (names.contains(child.getName())) {
                child.addError("Field annotated with @%s has duplicate name '%s'. " +
                                "Executed children must have unique names.", types.Executed.asElement().getSimpleName().toString(), child.getName());
            } else if (many != null) {
                child.addError("Field annotated with @%s is hidden by executed field '%s'. " +
                                "Executed child fields with multiple children hide all following executed child declarations. " +
                                "Reorder or remove this executed child declaration.", types.Executed.asElement().getSimpleName().toString(), many.getName());
            } else if (child.getCardinality().isMany()) {
                many = child;
            }
            names.add(child.getName());
        }

        List<NodeChildData> nodeChildren = new ArrayList<>();
        List<TypeElement> typeHierarchyReversed = new ArrayList<>(typeHierarchy);
        Collections.reverse(typeHierarchyReversed);
        for (TypeElement type : typeHierarchyReversed) {
            AnnotationMirror nodeChildrenMirror = findAnnotationMirror(type, types.NodeChildren);

            TypeMirror nodeClassType = type.getSuperclass();
            if (nodeClassType.getKind() == TypeKind.NONE || !isAssignable(nodeClassType, types.Node)) {
                nodeClassType = null;
            }

            List<AnnotationMirror> children = collectAnnotations(nodeChildrenMirror, "value", type, types.NodeChild);
            int index = 0;
            for (AnnotationMirror childMirror : children) {
                String name = getAnnotationValue(String.class, childMirror, "value");
                if (name.equals("")) {
                    name = "child" + index;
                }

                Cardinality cardinality = Cardinality.ONE;

                TypeMirror childNodeType = inheritType(childMirror, "type", nodeClassType);
                if (childNodeType.getKind() == TypeKind.ARRAY) {
                    cardinality = Cardinality.MANY;
                }

                TypeMirror originalChildType = childNodeType;
                TypeMirror castNodeType = castNodeTypes.get(name);
                if (castNodeType != null) {
                    childNodeType = castNodeType;
                }

                Element getter = findGetter(elements, name, childNodeType);
                AnnotationValue executeWith = getAnnotationValue(childMirror, "executeWith");

                String create = null;
                boolean implicit = (boolean) unboxAnnotationValue(getAnnotationValue(childMirror, "implicit"));
                boolean implicitCreateSpecified = getAnnotationValue(childMirror, "implicitCreate", false) != null;
                if (implicit || implicitCreateSpecified) {
                    create = (String) unboxAnnotationValue(getAnnotationValue(childMirror, "implicitCreate"));
                }

                String uncached = null;
                boolean allowUncached = (boolean) unboxAnnotationValue(getAnnotationValue(childMirror, "allowUncached"));
                boolean uncachedSpecified = getAnnotationValue(childMirror, "uncached", false) != null;
                if (allowUncached || uncachedSpecified) {
                    uncached = (String) unboxAnnotationValue(getAnnotationValue(childMirror, "uncached"));
                }
                NodeChildData nodeChild = new NodeChildData(type, childMirror, name, childNodeType, originalChildType, getter, cardinality, executeWith, create, uncached);

                if (implicitCreateSpecified && implicit) {
                    nodeChild.addError("The attributes 'implicit' and 'implicitCreate' are mutually exclusive. Remove one of the attributes to resolve this.");
                }
                if (uncachedSpecified && allowUncached) {
                    nodeChild.addError("The attributes 'allowUncached' and 'uncached' are mutually exclusive. Remove one of the attributes to resolve this.");
                }

                nodeChildren.add(nodeChild);

                if (nodeChild.getNodeType() == null) {
                    nodeChild.addError("No valid node type could be resolved.");
                }
                if (nodeChild.hasErrors()) {
                    continue;
                }

                index++;
            }
        }

        if (!nodeChildren.isEmpty() && !executedFieldChildren.isEmpty()) {
            node.addError("The use of @%s and @%s at the same time is not supported.", types.NodeChild.asElement().getSimpleName().toString(), types.Executed.asElement().getSimpleName().toString());
            return executedFieldChildren;
        } else if (!executedFieldChildren.isEmpty()) {
            return executedFieldChildren;
        } else {
            List<NodeChildData> filteredChildren = new ArrayList<>();
            Set<String> encounteredNames = new HashSet<>();
            for (int i = nodeChildren.size() - 1; i >= 0; i--) {
                NodeChildData child = nodeChildren.get(i);
                if (!encounteredNames.contains(child.getName())) {
                    filteredChildren.add(0, child);
                    encounteredNames.add(child.getName());
                }
            }

            return filteredChildren;
        }

    }

    private List<NodeExecutionData> parseExecutions(@SuppressWarnings("unused") NodeData node, List<NodeFieldData> fields, List<NodeChildData> children, List<? extends Element> elements) {
        List<ExecutableElement> methods = ElementFilter.methodsIn(elements);
        boolean hasVarArgs = false;
        int maxSignatureSize = 0;
        if (!children.isEmpty()) {
            int lastIndex = children.size() - 1;
            hasVarArgs = children.get(lastIndex).getCardinality() == Cardinality.MANY;
            if (hasVarArgs) {
                maxSignatureSize = lastIndex;
            } else {
                maxSignatureSize = children.size();
            }
        }

        List<NodeFieldData> nonGetterFields = new ArrayList<>();
        for (NodeFieldData field : fields) {
            if (field.getGetter() == null && field.isGenerated()) {
                nonGetterFields.add(field);
            }
        }

        List<TypeMirror> frameTypes = context.getFrameTypes();
        // pre-parse specializations to find signature size
        boolean seenNodeParameter = false;
        for (ExecutableElement method : methods) {
            AnnotationMirror mirror = findAnnotationMirror(method, types.Specialization);
            if (mirror == null) {
                continue;
            }
            int currentArgumentIndex = 0;
            parameter: for (VariableElement var : method.getParameters()) {
                TypeMirror type = var.asType();
                if (currentArgumentIndex == 0) {
                    if (node.isGenerateInline() && typeEquals(type, types.Node)) {
                        seenNodeParameter = true;
                    }

                    // skip optionals
                    for (TypeMirror frameType : frameTypes) {
                        if (typeEquals(type, frameType)) {
                            continue parameter;
                        }
                    }
                }

                if (currentArgumentIndex < nonGetterFields.size()) {
                    for (NodeFieldData field : nonGetterFields) {
                        if (typeEquals(var.asType(), field.getType())) {
                            continue parameter;
                        }
                    }
                }

                for (TypeMirror cachedAnnotation : cachedAnnotations) {
                    if (findAnnotationMirror(var.getAnnotationMirrors(), cachedAnnotation) != null) {
                        continue parameter;
                    }
                }

                currentArgumentIndex++;
            }
            maxSignatureSize = Math.max(maxSignatureSize, currentArgumentIndex);
        }

        /*
         * No specialization uses the Node parameter so we need to artificially increment the max
         * signature count.
         */
        if (node.isGenerateInline() && !seenNodeParameter) {
            maxSignatureSize++;
        }

        List<NodeExecutionData> executions = new ArrayList<>();
        for (int i = 0; i < maxSignatureSize; i++) {
            boolean varArgParameter = false;
            int childIndex = i;
            if (i >= children.size() - 1) {
                if (hasVarArgs) {
                    varArgParameter = hasVarArgs;
                    childIndex = Math.min(i, children.size() - 1);
                } else if (i >= children.size()) {
                    childIndex = -1;
                }
            }
            int varArgsIndex = -1;
            NodeChildData child = null;
            if (childIndex != -1) {
                varArgsIndex = varArgParameter ? Math.abs(childIndex - i) : -1;
                child = children.get(childIndex);
            }
            executions.add(new NodeExecutionData(child, i, varArgsIndex));
        }
        return executions;
    }

    private List<ExecutableTypeData> parseExecutableTypeData(NodeData node, List<? extends Element> elements, int signatureSize, List<TypeMirror> frameTypes, boolean includeFinals) {
        List<ExecutableTypeData> typeData = new ArrayList<>();
        for (ExecutableElement method : ElementFilter.methodsIn(elements)) {
            Set<Modifier> modifiers = method.getModifiers();
            if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.STATIC)) {
                continue;
            }
            if (!includeFinals && modifiers.contains(Modifier.FINAL)) {
                continue;
            }

            if (!hasValidExecutableName(method)) {
                continue;
            }
            if (findAnnotationMirror(method, types.Specialization) != null) {
                continue;
            }
            boolean ignoreUnexpected = mode == ParseMode.EXPORTED_MESSAGE;
            boolean reachableForRuntimeCompilation = !(mode == ParseMode.OPERATION && node.isGenerateUncached());
            ExecutableTypeData executableType = new ExecutableTypeData(node, method, signatureSize, context.getFrameTypes(), ignoreUnexpected, reachableForRuntimeCompilation);

            if (executableType.getFrameParameter() != null) {
                boolean supportedType = false;
                for (TypeMirror type : frameTypes) {
                    if (isAssignable(type, executableType.getFrameParameter())) {
                        supportedType = true;
                        break;
                    }
                }
                if (!supportedType) {
                    continue;
                }
            }

            typeData.add(executableType);
        }

        namesUnique(typeData);

        return typeData;
    }

    private static boolean hasValidExecutableName(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        return methodName.startsWith("execute") && !methodName.equals("executeOSR");
    }

    private static void namesUnique(List<ExecutableTypeData> typeData) {
        List<String> names = new ArrayList<>();
        for (ExecutableTypeData type : typeData) {
            names.add(type.getUniqueName());
        }
        while (renameDuplicateIds(names)) {
            // fix point
        }

        for (int i = 0; i < typeData.size(); i++) {
            typeData.get(i).setUniqueName(names.get(i));
        }
    }

    private void initializeExecutableTypes(NodeData node) {
        List<ExecutableTypeData> allExecutes = node.getExecutableTypes();

        Set<String> inconsistentFrameTypes = new HashSet<>();
        TypeMirror frameType = null;
        for (ExecutableTypeData execute : allExecutes) {

            TypeMirror frame = execute.getFrameParameter();
            TypeMirror resolvedFrameType;
            if (frame != null) {
                resolvedFrameType = frame;
                if (frameType == null) {
                    frameType = resolvedFrameType;
                } else if (!typeEquals(frameType, resolvedFrameType)) {
                    // found inconsistent frame types
                    inconsistentFrameTypes.add(getSimpleName(frameType));
                    inconsistentFrameTypes.add(getSimpleName(resolvedFrameType));
                }
            }
        }
        if (!inconsistentFrameTypes.isEmpty()) {
            // ensure they are sorted somehow
            List<String> inconsistentFrameTypesList = new ArrayList<>(inconsistentFrameTypes);
            Collections.sort(inconsistentFrameTypesList);
            node.addError("Invalid inconsistent frame types %s found for the declared execute methods. The frame type must be identical for all execute methods.", inconsistentFrameTypesList);
        }
        if (frameType == null) {
            frameType = context.getType(void.class);
        }

        node.setFrameType(frameType);

        boolean genericFound = false;
        for (ExecutableTypeData type : node.getExecutableTypes()) {
            if (mode == ParseMode.EXPORTED_MESSAGE || !type.hasUnexpectedValue()) {
                genericFound = true;
                break;
            }
        }

        // no generic executes
        if (!genericFound) {
            node.addError("No accessible and overridable generic execute method found. Generic execute methods usually have the " +
                            "signature 'public abstract {Type} execute(VirtualFrame)'.");
        }

        int nodeChildDeclarations = 0;
        int nodeChildDeclarationsRequired = 0;
        List<NodeExecutionData> executions = node.getChildExecutions();
        for (NodeExecutionData execution : executions) {
            if (execution.getChild() == null) {
                nodeChildDeclarationsRequired = execution.getIndex() + 1;
            } else {
                nodeChildDeclarations++;
            }
        }

        List<String> requireNodeChildDeclarations = new ArrayList<>();
        for (ExecutableTypeData type : allExecutes) {
            if (type.getEvaluatedCount() < nodeChildDeclarationsRequired) {
                requireNodeChildDeclarations.add(createReferenceName(type.getMethod()));
            }
        }

        if (node.isGenerateInline()) {
            if (nodeChildDeclarations <= 0) {
                for (ExecutableTypeData type : allExecutes) {
                    int index;
                    if (type.getFrameParameter() != null) {
                        index = 1;
                    } else {
                        index = 0;
                    }
                    TypeMirror firstParameter;
                    if (index < type.getMethod().getParameters().size()) {
                        firstParameter = type.getMethod().getParameters().get(index).asType();
                    } else {
                        firstParameter = null;
                    }

                    if (firstParameter == null || !typeEquals(types.Node, firstParameter)) {
                        node.addError("Error generating code for @%s: Found non-final execute method without a node parameter %s. " +
                                        "Inlinable nodes must use the %s type as the first parameter after the optional frame for all non-final execute methods. " +
                                        "A valid signature for an inlinable node is execute([VirtualFrame frame, ] Node node, ...).",
                                        getSimpleName(types.GenerateInline),
                                        ElementUtils.getReadableSignature(type.getMethod()),
                                        getSimpleName(types.Node));
                        break;
                    }
                }
            }
        } else {
            if (!requireNodeChildDeclarations.isEmpty()) {
                node.addError("Not enough child node declarations found. Please annotate the node class with additional @NodeChild annotations or remove all execute methods that do not provide all evaluated values. " +
                                "The following execute methods do not provide all evaluated values for the expected signature size %s: %s.", executions.size(), requireNodeChildDeclarations);
            }

            if (nodeChildDeclarations > 0 && executions.size() == node.getMinimalEvaluatedParameters()) {
                for (NodeChildData child : node.getChildren()) {
                    child.addError("Unnecessary @NodeChild declaration. All evaluated child values are provided as parameters in execute methods.");
                }
            }
        }

        TypeMirror runtimeException = context.getType(RuntimeException.class);
        Set<String> allowedCheckedExceptions = null;
        for (ExecutableTypeData type : allExecutes) {
            List<? extends TypeMirror> thrownTypes = type.getMethod().getThrownTypes();
            List<String> checkedTypes = null;
            for (TypeMirror thrownType : thrownTypes) {
                if (type.hasUnexpectedValue()) {
                    continue;
                } else if (isAssignable(thrownType, runtimeException)) {
                    // runtime exceptions don't need to be declared.
                    continue;
                }
                if (checkedTypes == null) {
                    checkedTypes = new ArrayList<>();
                }
                checkedTypes.add(ElementUtils.getQualifiedName(thrownType));
            }
            if (allowedCheckedExceptions == null) {
                if (checkedTypes != null) {
                    allowedCheckedExceptions = new LinkedHashSet<>(checkedTypes);
                }
            } else {
                if (checkedTypes != null) {
                    allowedCheckedExceptions.retainAll(checkedTypes);
                }
            }
            // no further types will be allowed.
            if (allowedCheckedExceptions != null && allowedCheckedExceptions.isEmpty()) {
                break;
            }
        }
        node.setAllowedCheckedExceptions(allowedCheckedExceptions == null ? Collections.emptySet() : allowedCheckedExceptions);
    }

    @SuppressWarnings("unchecked")
    private void initializeChildren(NodeData node) {
        for (NodeChildData child : node.getChildren()) {
            AnnotationValue executeWithValue1 = child.getExecuteWithValue();

            List<AnnotationValue> executeWithValues = resolveAnnotationValue(List.class, executeWithValue1);
            List<NodeExecutionData> executeWith = new ArrayList<>();
            for (AnnotationValue executeWithValue : executeWithValues) {
                String executeWithString = resolveAnnotationValue(String.class, executeWithValue);

                if (child.getName().equals(executeWithString)) {
                    child.addError(executeWithValue1, "The child node '%s' cannot be executed with itself.", executeWithString);
                    continue;
                }
                NodeExecutionData found = null;
                boolean before = true;
                for (NodeExecutionData resolveChild : node.getChildExecutions()) {
                    if (resolveChild.getChild() == child) {
                        before = false;
                        continue;
                    }
                    if (resolveChild.getIndexedName().equals(executeWithString)) {
                        found = resolveChild;
                        break;
                    }
                }

                if (found == null) {
                    child.addError(executeWithValue1, "The child node '%s' cannot be executed with '%s'. The child node was not found.", child.getName(), executeWithString);
                    continue;
                } else if (!before) {
                    child.addError(executeWithValue1, "The child node '%s' cannot be executed with '%s'. The node %s is executed after the current node.", child.getName(), executeWithString,
                                    executeWithString);
                    continue;
                }
                executeWith.add(found);
            }

            child.setExecuteWith(executeWith);
        }

        for (NodeChildData child : node.getChildren()) {
            TypeMirror nodeType = child.getNodeType();
            NodeData fieldNodeData = parseChildNodeData(node, child, fromTypeMirror(nodeType));

            child.setNode(fieldNodeData);
            if (fieldNodeData == null || fieldNodeData.hasErrors()) {
                child.addError("Node type '%s' is invalid or not a subclass of Node.", getQualifiedName(nodeType));
            } else {
                if (child.isImplicit() || child.isAllowUncached()) {
                    DSLExpressionResolver resolver = new DSLExpressionResolver(context, node.getTemplateType(), Collections.emptyList());
                    resolver = importStatics(resolver, node.getNodeType());
                    if (NodeCodeGenerator.isSpecializedNode(nodeType)) {
                        List<CodeExecutableElement> executables = parseNodeFactoryMethods(nodeType);
                        if (executables != null) {
                            resolver = resolver.copy(executables);
                        }
                    }

                    if (child.isImplicit()) {
                        DSLExpression expr = parseCachedExpression(resolver, child, nodeType, child.getImplicitCreate());
                        child.setImplicitCreateExpression(expr);
                    }
                    if (child.isAllowUncached()) {
                        DSLExpression expr = parseCachedExpression(resolver, child, nodeType, child.getUncached());
                        child.setUncachedExpression(expr);
                    }
                }

                List<ExecutableTypeData> foundTypes = child.findGenericExecutableTypes();
                if (foundTypes.isEmpty()) {
                    AnnotationValue executeWithValue = child.getExecuteWithValue();
                    child.addError(executeWithValue, "No generic execute method found with %s evaluated arguments for node type %s and frame types %s.", child.getExecuteWith().size(),
                                    getSimpleName(nodeType), getUniqueIdentifiers(createAllowedChildFrameTypes(node)));
                }
            }
        }
    }

    private NodeData parseChildNodeData(NodeData parentNode, NodeChildData child, TypeElement originalTemplateType) {
        TypeElement templateType = fromTypeMirror(context.reloadTypeElement(originalTemplateType));

        if (findAnnotationMirror(originalTemplateType, types.GeneratedBy) != null) {
            // generated nodes should not get called again.
            return null;
        }

        if (!isAssignable(templateType.asType(), types.Node)) {
            return null;
        }

        List<TypeElement> lookupTypes = collectSuperClasses(new ArrayList<>(), templateType);

        List<? extends Element> members = CompilerFactory.getCompiler(templateType).getAllMembersInDeclarationOrder(processingEnv, templateType);
        NodeData node = parseNodeData(templateType, lookupTypes);
        if (node.hasErrors()) {
            return node;
        }
        List<TypeMirror> frameTypes = Collections.emptyList();
        if (parentNode.getFrameType() != null) {
            frameTypes = Arrays.asList(parentNode.getFrameType());
        }
        node.getExecutableTypes().addAll(parseExecutableTypeData(node, members, child.getExecuteWith().size(), frameTypes, true));
        node.setFrameType(parentNode.getFrameType());
        return node;
    }

    private List<TypeMirror> createAllowedChildFrameTypes(NodeData parentNode) {
        List<TypeMirror> allowedFrameTypes = new ArrayList<>();
        for (TypeMirror frameType : context.getFrameTypes()) {
            if (isAssignable(parentNode.getFrameType(), frameType)) {
                allowedFrameTypes.add(frameType);
            }
        }
        return allowedFrameTypes;
    }

    private void initializeSpecializations(DSLExpressionResolver resolver, final NodeData node) {
        if (node.getSpecializations().isEmpty()) {
            return;
        }

        initializeFallback(node);

        resolveReplaces(node, true);

        initializeExpressions(resolver, node);

        if (node.hasErrors()) {
            return;
        }

        initializeUnroll(node);
        initializeOrder(node);
        initializeReachability(node);
        initializeBoxingOverloads(node);
        initializeProbability(node);
        initializeFallbackReachability(node);
        initializeExcludeForUncached(node);

        initializeCheckedExceptions(node);
        initializeSpecializationIdsWithMethodNames(node.getSpecializations());
    }

    private void initializeExcludeForUncached(NodeData node) {
        boolean allExcluded = true;
        for (SpecializationData s : node.getReachableSpecializations()) {
            if (s.getMethod() == null) {
                continue;
            }
            boolean exclude;
            Boolean annotationValue = ElementUtils.getAnnotationValue(Boolean.class, s.getMessageAnnotation(), "excludeForUncached", false);
            if (annotationValue == null) {
                if (s.isGuardBindsExclusiveCache() && s.isReplaced()) {
                    exclude = true;
                } else {
                    exclude = false;
                }
            } else {
                AnnotationValue v = ElementUtils.getAnnotationValue(s.getMessageAnnotation(), "excludeForUncached");
                if (!node.isGenerateUncached() && mode == ParseMode.DEFAULT) {
                    s.addSuppressableWarning(TruffleSuppressedWarnings.UNUSED, s.getMessageAnnotation(), v,
                                    "The attribute excludeForUncached has no effect as the node is not configured for uncached generation.");
                }
                exclude = annotationValue;
            }
            s.setExcludeForUncached(exclude);
            if (!exclude) {
                allExcluded = false;
            }
        }

        if (allExcluded && node.isGenerateUncached()) {
            node.addError("All specializations were excluded for uncached. At least one specialization must remain included. Set the excludeForUncached attribute to false for at least one specialization to resolve this problem.");
        }

    }

    private void initializeBoxingOverloads(NodeData node) {
        for (SpecializationData specialization : node.getSpecializations()) {
            if (specialization.hasUnexpectedResultRewrite() && (ElementUtils.isPrimitive(specialization.getReturnType().getType()) || ElementUtils.isVoid(specialization.getReturnType().getType()))) {
                if (specialization.isReplaced()) {
                    for (SpecializationData replacingSpecialization : specialization.getReplacedBy()) {
                        if (replacingSpecialization.isBoxingOverloadable(specialization)) {
                            if (!ElementUtils.isObject(replacingSpecialization.getReturnType().getType())) {
                                continue;
                            }

                            replacingSpecialization.getBoxingOverloads().add(specialization);
                        } else {
                            if (specialization.hasMultipleInstances() != replacingSpecialization.hasMultipleInstances()) {
                                /*
                                 * Avoid warnings for cases where the replaced specialization has an
                                 * inline cache and the generic one does not. This case is clearly
                                 * not suitable for boxing overloads.
                                 */
                                continue;
                            }
                            replacingSpecialization.addSuppressableWarning(TruffleSuppressedWarnings.UNEXPECTED_RESULT_REWRITE,
                                            "The specialization '%s' throws an %s and is replaced by this specialization but their signature, guards or cached state are not compatible with each other so it cannot be used for boxing elimination. " +
                                                            "It is recommended to align the specializations to resolve this.",
                                            specialization.createReferenceName(),
                                            getSimpleName(types.UnexpectedResultException));
                            specialization.addSuppressableWarning(TruffleSuppressedWarnings.UNEXPECTED_RESULT_REWRITE,
                                            "This specialization throws an %s and is replaced by the '%s' specialization but their signature, guards or cached state are not compatible with each other so it cannot be used for boxing elimination. " +
                                                            "It is recommended to align the specializations to resolve this.",
                                            getSimpleName(types.UnexpectedResultException),
                                            replacingSpecialization.createReferenceName());
                        }
                    }
                } else {
                    for (SpecializationData replaceSpecialization : node.getSpecializations()) {
                        if (replaceSpecialization.hasUnexpectedResultRewrite()) {
                            continue;
                        } else if (!replaceSpecialization.isReachableAfter(specialization)) {
                            continue;
                        }
                        if (replaceSpecialization.isBoxingOverloadable(specialization)) {
                            replaceSpecialization.addSuppressableWarning(TruffleSuppressedWarnings.UNEXPECTED_RESULT_REWRITE,
                                            "The specialization '%s' throws an %s and is compatible for boxing elimination but the specialization does not replace it. " +
                                                            "It is recommmended to specify a @%s(..., replaces=\"%s\") attribute to resolve this.",
                                            specialization.createReferenceName(),
                                            getSimpleName(types.UnexpectedResultException),
                                            getSimpleName(types.Specialization),
                                            specialization.getMethodName());
                            specialization.addSuppressableWarning(TruffleSuppressedWarnings.UNEXPECTED_RESULT_REWRITE,
                                            "This specialization throws an %s and is replaced by the '%s' specialization but their signature, guards or cached state are not compatible with each other so it cannot be used for boxing elimination. " +
                                                            "It is recommended to align the specializations to resolve this.",
                                            getSimpleName(types.UnexpectedResultException),
                                            replaceSpecialization.createReferenceName());

                        }
                    }
                }
            }
        }
        for (SpecializationData specialization : node.getSpecializations()) {
            Map<TypeMirror, SpecializationData> existingOverloads = new HashMap<>();
            for (SpecializationData boxingOverload : specialization.getBoxingOverloads()) {
                SpecializationData other = existingOverloads.putIfAbsent(boxingOverload.getReturnType().getType(), boxingOverload);
                if (other != null) {
                    boxingOverload.addSuppressableWarning(
                                    TruffleSuppressedWarnings.UNEXPECTED_RESULT_REWRITE,
                                    "The given boxing overload specialization shadowed by '%s' and is never used. Remove this specialization to resolve this.",
                                    ElementUtils.getReadableReference(node.getTemplateType(), other.getMethod()));
                }

            }

        }

    }

    private static void initializeProbability(NodeData node) {
        /*
         * TODO GR-42193 probabilities should be provided by the user.
         */
        List<SpecializationData> specializations = node.getReachableSpecializations();
        int count = specializations.size();
        double sum = 0;
        for (int i = 0; i < count; i++) {
            SpecializationData s = specializations.get(i);

            // bonus factor degrades linearly with each specialization
            // sum of all bonus factors is 1.0d
            double bonus = (count - i) / ((count / 2d) * (1d + count));

            // we do not know much about specialization activation without profiling
            // this is a rough heuristic to favor specializations that are declared first.
            // every specialization minimally gets a 10% share in the total probability
            // The rest of the 90% is distributed with the bonus factor that already accounts for
            // count.
            double probability = (0.10d / count) + (bonus * 0.90d);
            s.setLocalActivationProbability(probability);
            sum += probability;
        }

        if (Math.abs(1.0 - sum) > 0.000001d) { // sum != 1.0
            throw new AssertionError("Activation probability must sum up to 1.0 but was " + sum);
        }
    }

    private static void initializeUnroll(NodeData node) {
        List<SpecializationData> newSpecializations = null;
        List<SpecializationData> specializations = node.getSpecializations();
        for (int index = 0; index < specializations.size(); index++) {
            SpecializationData specialization = specializations.get(index);
            if (specialization.getMethod() == null) {
                if (newSpecializations != null) {
                    newSpecializations.add(specialization);
                }
                continue;
            }

            Integer unrollCount = ElementUtils.getAnnotationValue(Integer.class, specialization.getMarkerAnnotation(), "unroll");
            if (unrollCount == null) {
                unrollCount = 0;
            }

            if (specialization.isUncachedSpecialization()) {
                unrollCount = 0;
            }

            if (unrollCount > 0) {
                specialization.setUnroll(unrollCount);
            }

            // to future me or maintainer: let us be reasonable and not increase this limit.
            AnnotationValue unrollAnnotationValue = ElementUtils.getAnnotationValue(specialization.getMarkerAnnotation(), "unroll");
            if (unrollCount > 8 || unrollCount < 0) {
                node.addError(unrollAnnotationValue,
                                "The maximum specialization instance unroll count is 8. The number is limited to avoid code explosion. Reduce the unroll limit to resolve this.");
            }

            if (unrollCount <= 0) {
                if (newSpecializations != null) {
                    newSpecializations.add(specialization);
                }
            } else {
                int maxInstances = specialization.getMaximumNumberOfInstances();
                if (maxInstances <= 1) {
                    specialization.addError(unrollAnnotationValue, "A specialization that cannot have multiple instances cannot be unrolled. Remove the unroll specification to resolve this.");
                }

                if (maxInstances < unrollCount) {
                    specialization.addError(unrollAnnotationValue,
                                    "The maximum number of instances for this specialization is %s. But there were %s instances unrolled. Set the unrolled index to %s to resolve this.",
                                    maxInstances, unrollCount, maxInstances);
                }

                if (newSpecializations == null) {
                    newSpecializations = new ArrayList<>();
                    newSpecializations.addAll(specializations.subList(0, index));
                }

                for (int unrollIndex = 0; unrollIndex < unrollCount; unrollIndex++) {
                    SpecializationData unrolled = specialization.copy();
                    unrolled.setUnrollIndex(unrollIndex);
                    if (maxInstances > unrollCount) {
                        DSLExpression guard = new Binary("<", new IntLiteral(String.valueOf(unrollIndex)), unrolled.getLimitExpression());
                        unrolled.getGuards().add(new GuardExpression(specialization, guard));
                    }
                    unrolled.setLimitExpression(new IntLiteral("1"));

                    newSpecializations.add(unrolled);
                }

                if (maxInstances > unrollCount) {
                    // we need to keep the generic specialization
                    newSpecializations.add(specialization);
                }
            }
        }
        if (newSpecializations != null) {
            node.getSpecializations().clear();
            node.getSpecializations().addAll(newSpecializations);
            resolveReplaces(node, false);
        }
    }

    private void initializeCheckedExceptions(NodeData node) {

        for (SpecializationData specialization : node.getSpecializations()) {
            TypeMirror runtimeException = context.getType(RuntimeException.class);
            Set<String> exceptionTypeNames = getExceptionTypes(specialization.getMethod(), runtimeException);

            for (GuardExpression guard : specialization.getGuards()) {
                for (ExecutableElement executableElement : guard.getExpression().findBoundExecutableElements()) {
                    exceptionTypeNames.addAll(getExceptionTypes(executableElement, runtimeException));
                }
            }

            for (CacheExpression cache : specialization.getCaches()) {
                if (cache.getDefaultExpression() != null) {
                    for (ExecutableElement executableElement : cache.getDefaultExpression().findBoundExecutableElements()) {
                        exceptionTypeNames.addAll(getExceptionTypes(executableElement, runtimeException));
                    }
                }
                if (cache.getUncachedExpression() != null) {
                    for (ExecutableElement executableElement : cache.getUncachedExpression().findBoundExecutableElements()) {
                        exceptionTypeNames.addAll(getExceptionTypes(executableElement, runtimeException));
                    }
                }
            }

            Set<String> allowedCheckedExceptions = new LinkedHashSet<>(node.getAllowedCheckedExceptions());
            for (SpecializationThrowsData t : specialization.getExceptions()) {
                allowedCheckedExceptions.add(getQualifiedName(t.getJavaClass()));
            }

            exceptionTypeNames.removeAll(allowedCheckedExceptions);
            if (!exceptionTypeNames.isEmpty()) {
                specialization.addError(
                                "Specialization guard method or cache initializer declares an undeclared checked exception %s. " +
                                                "Only checked exceptions are allowed that were declared in the execute signature. Allowed exceptions are: %s.",
                                exceptionTypeNames, allowedCheckedExceptions);
            }
        }
    }

    private static Set<String> getExceptionTypes(ExecutableElement method, TypeMirror runtimeException) {
        if (method == null) {
            return Collections.emptySet();
        }
        return method.getThrownTypes().stream().filter((t) -> !isAssignable(t, runtimeException)).map(ElementUtils::getQualifiedName).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static void initializeOrder(NodeData node) {
        List<SpecializationData> specializations = node.getSpecializations();
        Collections.sort(specializations);

        for (SpecializationData specialization : specializations) {
            String searchName = specialization.getInsertBeforeName();
            if (searchName == null || specialization.getMethod() == null) {
                continue;
            }
            List<SpecializationData> found = lookupSpecialization(node, searchName);
            if (found.isEmpty() || found.get(0).getMethod() == null) {
                AnnotationValue value = getAnnotationValue(specialization.getMarkerAnnotation(), "insertBefore");
                specialization.addError(value, "The referenced specialization '%s' could not be found.", searchName);
                continue;
            }
            SpecializationData first = found.iterator().next();

            ExecutableElement currentMethod = specialization.getMethod();
            ExecutableElement insertBeforeMethod = first.getMethod();

            TypeMirror currentEnclosedType = currentMethod.getEnclosingElement().asType();
            TypeMirror insertBeforeEnclosedType = insertBeforeMethod.getEnclosingElement().asType();

            if (typeEquals(currentEnclosedType, insertBeforeEnclosedType) || !isSubtype(currentEnclosedType, insertBeforeEnclosedType)) {
                AnnotationValue value = getAnnotationValue(specialization.getMarkerAnnotation(), "insertBefore");
                specialization.addError(value, "Specializations can only be inserted before specializations in superclasses.", searchName);
                continue;
            }

            specialization.setInsertBefore(first);
        }

        for (int i = 0; i < specializations.size(); i++) {
            SpecializationData specialization = specializations.get(i);
            SpecializationData insertBefore = specialization.getInsertBefore();
            if (insertBefore != null) {
                int insertIndex = specializations.indexOf(insertBefore);
                if (insertIndex < i) {
                    specializations.remove(i);
                    specializations.add(insertIndex, specialization);
                }
            }
        }

        for (int i = 0; i < specializations.size(); i++) {
            specializations.get(i).setIndex(i);
        }
    }

    private static void resolveReplaces(NodeData node, boolean emitMessages) {
        for (SpecializationData specialization : node.getSpecializations()) {
            specialization.setReplaces(new LinkedHashSet<>());
        }

        for (SpecializationData specialization : node.getSpecializations()) {
            Set<SpecializationData> resolvedReplaces = specialization.getReplaces();
            Set<String> includeNames = specialization.getReplacesNames();
            if (includeNames != null) {
                for (String includeName : includeNames) {
                    // TODO GR-38632 reduce complexity of this lookup.
                    List<SpecializationData> foundSpecializations = lookupSpecialization(node, includeName);

                    AnnotationValue value = getAnnotationValue(specialization.getMarkerAnnotation(), "replaces");
                    if (foundSpecializations.isEmpty()) {
                        if (emitMessages) {
                            specialization.addError(value, "The referenced specialization '%s' could not be found.", includeName);
                        }
                    } else {
                        resolvedReplaces.addAll(foundSpecializations);
                        for (SpecializationData foundSpecialization : foundSpecializations) {
                            if (foundSpecialization.compareTo(specialization) > 0) {
                                if (emitMessages) {
                                    specialization.addError(value, "The replaced specialization '%s' must be declared before the replacing specialization.", includeName);
                                }
                            }
                        }
                    }
                }
            }
            if (specialization.getUncachedSpecialization() != null) {
                specialization.getUncachedSpecialization().getReplaces().add(specialization);
            }
        }

        for (SpecializationData specialization : node.getSpecializations()) {
            if (specialization.getReplaces().isEmpty()) {
                continue;
            }

            for (SpecializationData replaced : specialization.getReplaces()) {
                replaced.setReplaced(true);
            }

            // transitively resolve includes
            Set<SpecializationData> foundSpecializations = new LinkedHashSet<>();
            collectIncludes(specialization, foundSpecializations, new HashSet<>());
            specialization.getReplaces().addAll(foundSpecializations);
        }

        // compute replaced by
        List<SpecializationData> specializations = node.getSpecializations();
        for (SpecializationData cur : specializations) {
            cur.setReplacedBy(new LinkedHashSet<>());
        }

        for (SpecializationData cur : specializations) {
            for (SpecializationData contained : cur.getReplaces()) {
                if (contained != cur) {
                    contained.getReplacedBy().add(cur);
                }
            }
        }
    }

    private static List<SpecializationData> lookupSpecialization(NodeData node, String includeName) {
        List<SpecializationData> specializations = new ArrayList<>();
        for (SpecializationData searchSpecialization : node.getSpecializations()) {
            if (searchSpecialization.getMethodName().equals(includeName)) {
                specializations.add(searchSpecialization);
            }
        }
        return specializations;
    }

    private static void collectIncludes(SpecializationData specialization, Set<SpecializationData> found, Set<SpecializationData> visited) {
        if (visited.contains(specialization)) {
            // circle found
            specialization.addError("Circular replaced specialization '%s' found.", specialization.createReferenceName());
            return;
        }
        visited.add(specialization);

        for (SpecializationData included : specialization.getReplaces()) {
            collectIncludes(included, found, new HashSet<>(visited));
            found.add(included);
        }
    }

    private static void initializeReachability(final NodeData node) {
        List<SpecializationData> specializations = node.getSpecializations();
        for (int i = specializations.size() - 1; i >= 0; i--) {
            SpecializationData current = specializations.get(i);
            List<SpecializationData> shadowedBy = null;
            for (int j = i - 1; j >= 0; j--) {
                SpecializationData prev = specializations.get(j);
                if (!current.isReachableAfter(prev)) {
                    if (shadowedBy == null) {
                        shadowedBy = new ArrayList<>();
                    }
                    shadowedBy.add(prev);
                }
            }

            if (shadowedBy != null) {
                StringBuilder name = new StringBuilder();
                String sep = "";
                for (SpecializationData shadowSpecialization : shadowedBy) {
                    name.append(sep);
                    name.append(shadowSpecialization.createReferenceName());
                    sep = ", ";
                }
                current.addError("%s is not reachable. It is shadowed by %s.", current.isFallback() ? "Generic" : "Specialization", name);
            }
            current.setReachable(shadowedBy == null);
        }

        // reachability is no longer changing.
        node.setReachableSpecializations(node.getReachableSpecializations());
    }

    public static void removeSpecializations(NodeData node, Map<CacheExpression, String> sharing, boolean generateSlowPathOnly) {
        Set<SpecializationData> toRemove = new LinkedHashSet<>();
        List<SpecializationData> specializations = node.getSpecializations();
        for (SpecializationData cur : specializations) {
            if (cur.getReplaces() != null) {
                if (generateSlowPathOnly) {
                    for (SpecializationData contained : cur.getReplaces()) {
                        if (contained != cur && contained.getUncachedSpecialization() != cur) {
                            toRemove.add(contained);
                        }
                    }
                }

                for (SpecializationData overload : cur.getBoxingOverloads()) {
                    boolean allReplaced = true;
                    for (SpecializationData replacedBy : overload.getReplacedBy()) {
                        if (!replacedBy.getBoxingOverloads().contains(overload)) {
                            // not a boxing overload

                            if (replacedBy.getReplaces().containsAll(cur.getBoxingOverloads()) && replacedBy.getReplaces().contains(cur)) {
                                /*
                                 * It is fine to use as boxing overload as the replacer replaces the
                                 * specialization and all boxing overloads.
                                 */
                                continue;
                            }

                            allReplaced = false;
                        }
                    }
                    if (allReplaced) {
                        toRemove.add(overload);
                    }
                }

            }
        }

        if (toRemove.isEmpty()) {
            return;
        }

        for (SpecializationData remove : toRemove) {
            for (CacheExpression cache : remove.getCaches()) {
                sharing.remove(cache);
            }
        }

        // group sharing by key
        Map<String, List<CacheExpression>> newCaches = new HashMap<>();
        for (Entry<CacheExpression, String> entry : sharing.entrySet()) {
            newCaches.computeIfAbsent(entry.getValue(), (s) -> new ArrayList<>()).add(entry.getKey());
        }

        // remove sharing with a single shared cache
        for (Entry<String, List<CacheExpression>> entry : newCaches.entrySet()) {
            if (entry.getValue().size() <= 1) {
                for (CacheExpression cache : entry.getValue()) {
                    sharing.remove(cache);
                }
            }
        }

        // clear sharing info in cache to be consistent in node generation
        for (SpecializationData specialization : specializations) {
            for (CacheExpression cache : specialization.getCaches()) {
                if (cache.getSharedGroup() != null && !sharing.containsKey(cache)) {
                    cache.clearSharing();
                }
            }
        }

        specializations.removeAll(toRemove);
        node.getReachableSpecializations().removeAll(toRemove);
        resolveReplaces(node, false);

        // reinitialize propabilities
        initializeProbability(node);

    }

    private static void initializeSpecializationIdsWithMethodNames(List<SpecializationData> specializations) {
        List<String> signatures = new ArrayList<>();
        for (SpecializationData specialization : specializations) {
            if (specialization.isFallback()) {
                signatures.add("Fallback");
            } else {
                String name = specialization.getMethodName();

                // hack for name clashes with BaseNode.
                if (name.equalsIgnoreCase("base")) {
                    name = name + "0";
                } else if (name.startsWith("do")) {
                    String filteredDo = name.substring(2, name.length());
                    if (!filteredDo.isEmpty() && Character.isJavaIdentifierStart(filteredDo.charAt(0))) {
                        name = filteredDo;
                    }
                }
                signatures.add(firstLetterUpperCase(name));
            }
        }

        while (renameDuplicateIds(signatures)) {
            // fix point
        }

        for (int i = 0; i < specializations.size(); i++) {
            specializations.get(i).setId(signatures.get(i));
        }
    }

    private static boolean renameDuplicateIds(List<String> signatures) {
        boolean changed = false;
        Map<String, Integer> counts = new HashMap<>();
        for (String s1 : signatures) {
            Integer count = counts.get(s1.toLowerCase());
            if (count == null) {
                count = 0;
            }
            count++;
            counts.put(s1.toLowerCase(), count);
        }

        for (String s : counts.keySet()) {
            int count = counts.get(s);
            if (count > 1) {
                changed = true;
                int number = 0;
                for (ListIterator<String> iterator = signatures.listIterator(); iterator.hasNext();) {
                    String s2 = iterator.next();
                    if (s.equalsIgnoreCase(s2)) {
                        iterator.set(s2 + number);
                        number++;
                    }
                }
            }
        }
        return changed;
    }

    private void initializeExpressions(DSLExpressionResolver originalResolver, NodeData node) {
        // the number of specializations might grow while expressions are initialized.
        List<SpecializationData> specializations = node.getSpecializations();
        int originalSize = specializations.size();
        int i = 0;
        while (i < specializations.size()) {
            SpecializationData specialization = specializations.get(i);
            if (specialization.getMethod() == null || specialization.hasErrors()) {
                i++;
                continue;
            }

            List<Element> specializationMembers = new ArrayList<>();
            for (Parameter p : specialization.getParameters()) {
                specializationMembers.add(p.getVariableElement());
            }
            DSLExpressionResolver resolver = originalResolver.copy(specializationMembers);
            SpecializationData uncached = initializeCaches(specialization, resolver);
            initializeGuards(specialization, resolver);
            initializeLimit(specialization, resolver, false);
            initializeAssumptions(specialization, resolver);

            if (uncached != null) {
                specializations.add(++i, uncached);
                initializeGuards(uncached, resolver);
                initializeLimit(uncached, resolver, true);
                initializeAssumptions(uncached, resolver);
            }
            i++;
        }

        if (originalSize != specializations.size()) {
            resolveReplaces(node, false);
        }

    }

    private void initializeAssumptions(SpecializationData specialization, DSLExpressionResolver resolver) {
        final DeclaredType assumptionType = types.Assumption;
        final TypeMirror assumptionArrayType = new ArrayCodeTypeMirror(assumptionType);
        final List<String> assumptionDefinitions = getAnnotationValueList(String.class, specialization.getMarkerAnnotation(), "assumptions");
        List<AssumptionExpression> assumptionExpressions = new ArrayList<>();
        int assumptionId = 0;
        for (String assumption : assumptionDefinitions) {
            DSLExpression expression = DSLExpression.parseAndResolve(resolver, specialization, "assumptions", assumption);
            if (expression == null) {
                // failed to parse
                continue;
            }
            AssumptionExpression assumptionExpression = new AssumptionExpression(specialization, expression, "assumption" + assumptionId);
            if (!isAssignable(expression.getResolvedType(), assumptionType) && !isAssignable(expression.getResolvedType(), assumptionArrayType)) {
                assumptionExpression.addError("Incompatible return type %s. Assumptions must be assignable to %s or %s.", getSimpleName(expression.getResolvedType()),
                                getSimpleName(assumptionType), getSimpleName(assumptionArrayType));
            }
            if (specialization.isDynamicParameterBound(expression, true)) {
                specialization.addError("Assumption expressions must not bind dynamic parameter values.");
            }
            assumptionExpressions.add(assumptionExpression);
            assumptionId++;
        }
        specialization.setAssumptionExpressions(assumptionExpressions);
    }

    private void initializeLimit(SpecializationData specialization, DSLExpressionResolver resolver, boolean uncached) {
        AnnotationValue annotationValue = getAnnotationValue(specialization.getMessageAnnotation(), "limit", false);

        String limitValue;
        if (annotationValue == null) {
            limitValue = "3";
        } else {
            limitValue = (String) annotationValue.getValue();
        }

        if (!uncached && annotationValue != null && !specialization.hasMultipleInstances()) {
            if (!specialization.hasErrors()) {
                specialization.addSuppressableWarning(TruffleSuppressedWarnings.UNUSED, annotationValue,
                                "The limit expression has no effect. Multiple specialization instantiations are impossible for this specialization.");
            }
            return;
        }

        TypeMirror expectedType = context.getType(int.class);
        DSLExpression expression = DSLExpression.parseAndResolve(resolver, specialization, "limit", limitValue);
        if (expression != null) {
            if (!typeEquals(expression.getResolvedType(), expectedType)) {
                specialization.addError(annotationValue, "Incompatible return type %s. Limit expressions must return %s.", getSimpleName(expression.getResolvedType()),
                                getSimpleName(expectedType));
            }
            if (specialization.isDynamicParameterBound(expression, true)) {
                specialization.addError(annotationValue, "Limit expressions must not bind dynamic parameter values.");
            }
        }
        specialization.setLimitExpression(expression);
    }

    private SpecializationData initializeCaches(SpecializationData specialization, DSLExpressionResolver resolver) {
        List<CacheExpression> caches = new ArrayList<>();
        List<CacheExpression> cachedLibraries = new ArrayList<>();

        Parameter[] parameters = specialization.getParameters().toArray(new Parameter[0]);
        parameters: for (Parameter parameter : parameters) {
            if (!parameter.getSpecification().isCached()) {
                continue;
            }
            AnnotationMirror foundCached = null;
            for (TypeMirror cachedAnnotation : cachedAnnotations) {
                AnnotationMirror found = ElementUtils.findAnnotationMirror(parameter.getVariableElement().getAnnotationMirrors(), cachedAnnotation);
                if (found == null) {
                    continue;
                }
                if (foundCached == null) {
                    foundCached = found;
                } else {
                    StringBuilder b = new StringBuilder();
                    String sep = "";
                    for (TypeMirror stringCachedAnnotation : cachedAnnotations) {
                        b.append(sep);
                        b.append("@");
                        b.append(ElementUtils.getSimpleName(stringCachedAnnotation));
                        sep = ", ";
                    }
                    specialization.addError(parameter.getVariableElement(), "The following annotations are mutually exclusive for a parameter: %s.", b.toString());
                    continue parameters;
                }
            }
            if (foundCached == null) {
                continue;
            }

            CacheExpression cache = new CacheExpression(parameter, foundCached);
            caches.add(cache);

            if (cache.isCached()) {
                boolean weakReference = getAnnotationValue(Boolean.class, foundCached, "weak");
                if (weakReference) {
                    if (ElementUtils.isPrimitive(cache.getParameter().getType())) {
                        cache.addError("Cached parameters with primitive types cannot be weak. Set weak to false to resolve this.");
                    }

                    parseCached(cache, specialization, resolver, parameter);
                    if (cache.hasErrors()) {
                        continue;
                    }

                    DSLExpression sourceExpression = cache.getDefaultExpression();

                    String weakName = "weak" + ElementUtils.firstLetterUpperCase(parameter.getLocalName()) + "Gen_";
                    TypeMirror weakType = new CodeTypeMirror.DeclaredCodeTypeMirror(context.getTypeElement(types.TruffleWeakReference), Arrays.asList(cache.getParameter().getType()));
                    CodeVariableElement weakVariable = new CodeVariableElement(weakType, weakName);
                    weakVariable.setEnclosingElement(specialization.getMethod());
                    Parameter weakParameter = new Parameter(parameter, weakVariable);

                    DSLExpression newWeakReference = new DSLExpression.Call(null, "new", Arrays.asList(sourceExpression));
                    newWeakReference.setResolvedTargetType(weakType);
                    resolveCachedExpression(resolver, cache, weakType, cache.getParameter().getType(), newWeakReference, null);

                    CacheExpression weakCache = new CacheExpression(weakParameter, foundCached);
                    weakCache.setDefaultExpression(newWeakReference);
                    weakCache.setUncachedExpression(newWeakReference);
                    weakCache.setWeakReference(true);
                    weakCache.setNeverDefault(true);

                    caches.add(0, weakCache);

                    DSLExpressionResolver weakResolver = resolver.copy(Arrays.asList());
                    weakResolver.addVariable(weakName, weakVariable);
                    specialization.addParameter(weakParameter);

                    DSLExpression parsedDefaultExpression = parseCachedExpression(weakResolver, cache, parameter.getType(), weakName + ".get()");
                    cache.setDefaultExpression(parsedDefaultExpression);
                    cache.setUncachedExpression(sourceExpression);
                    cache.setAlwaysInitialized(true);
                    cache.setWeakReferenceGet(true);

                } else {
                    parseCached(cache, specialization, resolver, parameter);
                }

                if (this.mode == ParseMode.DEFAULT && cache.isThisExpression()) {
                    cache.addError("Cannot use 'this' with @%s use @%s instead.",
                                    getSimpleName(types.Cached),
                                    getSimpleName(types.Bind));
                }

            } else if (cache.isCachedLibrary()) {
                String expression = cache.getCachedLibraryExpression();
                String limit = cache.getCachedLibraryLimit();
                if (expression == null) {
                    // its cached dispatch version treat it as normal cached
                    if (limit == null) {
                        cache.addError("A specialized value expression or limit must be specified for @%s. " +
                                        "Use @%s(\"value\") for a specialized or " +
                                        "@%s(limit=\"\") for a dispatched library. " +
                                        "See the javadoc of @%s for further details.",
                                        getSimpleName(types.CachedLibrary),
                                        getSimpleName(types.CachedLibrary),
                                        getSimpleName(types.CachedLibrary),
                                        getSimpleName(types.CachedLibrary));
                        continue;
                    }
                    DSLExpression limitExpression = parseCachedExpression(resolver, cache, context.getType(int.class), limit);
                    if (limitExpression == null) {
                        continue;
                    }
                    TypeMirror libraryType = types.Library;
                    DSLExpressionResolver cachedResolver = importStatics(resolver, types.LibraryFactory);
                    TypeMirror usedLibraryType = parameter.getType();

                    DSLExpression resolveCall = new DSLExpression.Call(null, "resolve", Arrays.asList(new DSLExpression.ClassLiteral(usedLibraryType)));
                    DSLExpression defaultExpression = new DSLExpression.Call(resolveCall, "createDispatched", Arrays.asList(limitExpression));
                    DSLExpression uncachedExpression = new DSLExpression.Call(resolveCall, "getUncached", Arrays.asList());

                    cache.setDefaultExpression(resolveCachedExpression(cachedResolver, cache, libraryType, null, defaultExpression, null));
                    cache.setUncachedExpression(resolveCachedExpression(cachedResolver, cache, libraryType, null, uncachedExpression, null));
                } else {
                    if (limit != null) {
                        cache.addError("The limit and specialized value expression cannot be specified at the same time. They are mutually exclusive.");
                        continue parameters;
                    }
                    cachedLibraries.add(cache);
                }
                cache.setNeverDefault(true);
            } else if (cache.isBind()) {
                AnnotationMirror dynamic = cache.getMessageAnnotation();
                String expression = ElementUtils.getAnnotationValue(String.class, dynamic, "value", false);
                TypeMirror type = cache.getParameter().getType();
                String defaultExpression = resolveDefaultSymbol(type);

                if (defaultExpression == null && expression == null) {
                    cache.addError("No expression specified for @%s annotation and no @%s could be resolved from the parameter type. Specify a bind expression or change the type to resolve this.",
                                    getSimpleName(types.Bind),
                                    getSimpleName(types.Bind_DefaultExpression));
                } else if (defaultExpression != null && expression != null) {
                    if (defaultExpression.equals(expression)) {
                        cache.addSuppressableWarning(TruffleSuppressedWarnings.UNUSED,
                                        "Bind expression '%s' is redundant and can be automatically be resolved from the parameter type. Remove the expression to resolve this warning.", expression);
                    }
                    // use expression
                } else if (defaultExpression != null && expression == null) {
                    // inherit expression from default expression
                    expression = defaultExpression;
                } else if (defaultExpression == null && expression != null) {
                    // use expression
                } else {
                    throw new AssertionError("Unexpected case.");
                }
                if (cache.hasErrors()) {
                    continue;
                }

                if (mode == ParseMode.EXPORTED_MESSAGE && expression.trim().equals(NodeParser.SYMBOL_THIS) && typeEquals(type, types.Node)) {
                    Iterator<Parameter> firstParameter = specialization.getSignatureParameters().iterator();
                    if (firstParameter.hasNext() && firstParameter.next().getVariableElement().getSimpleName().toString().equals(NodeParser.SYMBOL_THIS)) {
                        cache.addError("Variable 'this' is reserved for library receiver values in methods annotated with @%s. " +
                                        "If the intention was to access the encapsulting Node for inlined nodes or profiles, you may use '%s' as expression instead.",
                                        getSimpleName(types.ExportMessage),
                                        NodeParser.SYMBOL_NODE);
                    }
                }

                if (cache.hasErrors()) {
                    continue;
                }
                DSLExpression parsedExpression = parseCachedExpression(resolver, cache, parameter.getType(), expression);
                cache.setDefaultExpression(parsedExpression);
                cache.setUncachedExpression(parsedExpression);
                cache.setAlwaysInitialized(true);
            }
            if (!cache.hasErrors() && !warnForThisVariable(cache, cache.getDefaultExpression())) {
                warnForThisVariable(cache, cache.getUncachedExpression());
            }
        }
        specialization.setCaches(caches);

        SpecializationData uncachedSpecialization = null;
        if (!cachedLibraries.isEmpty()) {
            uncachedSpecialization = parseCachedLibraries(specialization, resolver, cachedLibraries);
        }

        if (specialization.hasErrors()) {
            return null;
        }

        // verify that cache expressions are bound in the correct order.
        for (int i = 0; i < caches.size(); i++) {
            CacheExpression currentExpression = caches.get(i);
            Set<VariableElement> boundVariables = new HashSet<>();
            if (currentExpression.getDefaultExpression() != null) {
                boundVariables.addAll(currentExpression.getDefaultExpression().findBoundVariableElements());
            }
            if (currentExpression.getUncachedExpression() != null) {
                boundVariables.addAll(currentExpression.getUncachedExpression().findBoundVariableElements());
            }
            for (int j = i + 1; j < caches.size(); j++) {
                CacheExpression boundExpression = caches.get(j);
                if (boundVariables.contains(boundExpression.getParameter().getVariableElement())) {
                    currentExpression.addError("The initializer expression of parameter '%s' binds uninitialized parameter '%s. Reorder the parameters to resolve the problem.",
                                    currentExpression.getParameter().getLocalName(), boundExpression.getParameter().getLocalName());
                    break;
                }
            }
        }
        return uncachedSpecialization;
    }

    private boolean warnForThisVariable(CacheExpression cache, DSLExpression expression) {
        if (expression != null && expression.isSymbolBoundBound(types.Node, NodeParser.SYMBOL_THIS)) {
            cache.addSuppressableWarning(TruffleSuppressedWarnings.TRUFFLE,
                            "This expression binds variable '%s' which should no longer be used. Use the '%s' variable instead to resolve this warning.",
                            NodeParser.SYMBOL_THIS, NodeParser.SYMBOL_NODE);
            return true;
        }
        return false;
    }

    private String resolveDefaultSymbol(TypeMirror type) {
        TypeElement typeElement = ElementUtils.castTypeElement(type);
        if (typeElement != null) {
            AnnotationMirror defaultSymbol = ElementUtils.findAnnotationMirror(context.getEnvironment().getElementUtils().getAllAnnotationMirrors(typeElement), types.Bind_DefaultExpression);
            if (defaultSymbol != null) {
                return ElementUtils.getAnnotationValue(String.class, defaultSymbol, "value");
            } else if (mode == ParseMode.OPERATION && ElementUtils.isAssignable(type, types.RootNode)) {
                return BytecodeDSLParser.SYMBOL_ROOT_NODE;
            } else if (ElementUtils.isAssignable(type, types.Node)) {
                return NodeParser.SYMBOL_NODE;
            }
        }
        return null;
    }

    public static TypeMirror findContextTypeFromLanguage(TypeMirror languageType) {
        TypeElement languageTypeElement = ElementUtils.fromTypeMirror(languageType);
        TypeMirror superType = languageTypeElement.getSuperclass();
        ProcessorContext context = ProcessorContext.getInstance();
        while (languageTypeElement != null) {
            superType = languageTypeElement.getSuperclass();
            languageTypeElement = ElementUtils.fromTypeMirror(superType);
            if (ElementUtils.elementEquals(context.getTypeElement(context.getTypes().TruffleLanguage), languageTypeElement)) {
                return getFirstTypeArgument(superType);
            }
        }
        return null;
    }

    private static TypeMirror getFirstTypeArgument(TypeMirror languageType) {
        for (TypeMirror currentTypeArgument : ((DeclaredType) languageType).getTypeArguments()) {
            return currentTypeArgument;
        }
        return null;
    }

    private SpecializationData parseCachedLibraries(SpecializationData specialization, DSLExpressionResolver resolver, List<CacheExpression> libraries) {
        SpecializationData uncachedSpecialization = null;
        List<CacheExpression> uncachedLibraries = null;
        /*
         * No uncached specialization needed if there is a specialization that is strictly more
         * generic than this one.
         */
        if (!specialization.isReplaced()) {
            uncachedSpecialization = specialization.copy();
            uncachedLibraries = new ArrayList<>();

            List<CacheExpression> caches = uncachedSpecialization.getCaches();
            for (int i = 0; i < caches.size(); i++) {
                CacheExpression expression = caches.get(i);
                if (expression.getCachedLibraryExpression() != null) {
                    expression = expression.copy();
                    caches.set(i, expression);
                    uncachedLibraries.add(expression);
                }
            }
            specialization.setUncachedSpecialization(uncachedSpecialization);
        }

        if (uncachedLibraries != null && uncachedLibraries.size() != libraries.size()) {
            throw new AssertionError("Unexpected number of uncached libraries.");
        }

        boolean seenDynamicParameterBound = false;

        for (int i = 0; i < libraries.size(); i++) {
            CacheExpression cachedLibrary = libraries.get(i);
            CacheExpression uncachedLibrary = uncachedLibraries != null ? uncachedLibraries.get(i) : null;

            TypeMirror parameterType = cachedLibrary.getParameter().getType();
            TypeElement type = ElementUtils.fromTypeMirror(parameterType);
            if (type == null) {
                cachedLibrary.addError("Invalid library type %s. Must be a declared type.", getSimpleName(parameterType));
                continue;
            }
            if (!ElementUtils.isAssignable(parameterType, types.Library)) {
                cachedLibrary.addError("Invalid library type %s. Library is not a subclass of %s.", getSimpleName(parameterType), types.Library.asElement().getSimpleName().toString());
                continue;
            }

            if (ElementUtils.findAnnotationMirror(cachedLibrary.getParameter().getVariableElement(), types.Cached_Shared) != null) {
                cachedLibrary.addError("Specialized cached libraries cannot be shared yet.");
                continue;
            }

            LibraryData parsedLibrary = context.parseIfAbsent(type, LibraryParser.class, (t) -> new LibraryParser().parse(t));
            if (parsedLibrary == null || parsedLibrary.hasErrors()) {
                cachedLibrary.addError("Library '%s' has errors. Please resolve them first.", getSimpleName(parameterType));
                continue;
            }

            cachedLibrary.setCachedLibrary(parsedLibrary);
            if (uncachedLibrary != null) {
                uncachedLibrary.setCachedLibrary(parsedLibrary);
            }

            String expression = cachedLibrary.getCachedLibraryExpression();
            DSLExpression receiverExpression = parseCachedExpression(resolver, cachedLibrary, parsedLibrary.getSignatureReceiverType(), expression);
            if (receiverExpression == null) {
                continue;
            }
            DSLExpression substituteCachedExpression = null;
            DSLExpression substituteUncachedExpression = null;
            boolean supportsMerge = false;
            // try substitutions
            if (mode == ParseMode.EXPORTED_MESSAGE) {
                Parameter receiverParameter = specialization.findParameterOrDie(specialization.getNode().getChildExecutions().get(0));
                if (receiverExpression instanceof DSLExpression.Variable) {
                    DSLExpression.Variable variable = (DSLExpression.Variable) receiverExpression;
                    if (variable.getReceiver() == null) {
                        VariableElement resolvedVariable = variable.getResolvedVariable();
                        if (ElementUtils.variableEquals(resolvedVariable, receiverParameter.getVariableElement())) {
                            // found a cached library that refers to a library with the same
                            // receiver
                            if (typeEquals(type.asType(), exportLibraryType)) {
                                DSLExpression.Variable nodeReceiver = new DSLExpression.Variable(null, "this");
                                nodeReceiver.setResolvedTargetType(exportLibraryType);
                                nodeReceiver.setResolvedVariable(new CodeVariableElement(exportLibraryType, "this"));
                                if (substituteThisToParent) {
                                    DSLExpression.Call call = new DSLExpression.Call(nodeReceiver, "getParent", Collections.emptyList());
                                    call.setResolvedMethod(ElementUtils.findMethod(types.Node, "getParent"));
                                    call.setResolvedTargetType(context.getType(Object.class));
                                    substituteCachedExpression = new DSLExpression.Cast(call, exportLibraryType);
                                } else {
                                    substituteCachedExpression = nodeReceiver;
                                }
                            }
                        }
                    }
                }
                if (substituteCachedExpression == null && supportsLibraryMerge(receiverExpression, receiverParameter.getVariableElement())) {
                    substituteCachedExpression = receiverExpression;
                    supportsMerge = true;
                    cachedLibrary.setMergedLibrary(true);
                }
            }

            if (substituteCachedExpression != null) {
                if (substituteUncachedExpression == null) {
                    substituteUncachedExpression = substituteCachedExpression;
                }
                cachedLibrary.setDefaultExpression(substituteCachedExpression);
                cachedLibrary.setUncachedExpression(substituteUncachedExpression);
                cachedLibrary.setAlwaysInitialized(true);

                if (uncachedLibrary != null) {
                    uncachedLibrary.setDefaultExpression(substituteUncachedExpression);
                    uncachedLibrary.setUncachedExpression(substituteUncachedExpression);
                    uncachedLibrary.setMergedLibrary(supportsMerge);
                    uncachedLibrary.setAlwaysInitialized(true);
                }

            } else {
                seenDynamicParameterBound |= specialization.isDynamicParameterBound(receiverExpression, true);
                cachedLibrary.setDefaultExpression(receiverExpression);

                String receiverName = cachedLibrary.getParameter().getVariableElement().getSimpleName().toString();
                DSLExpression acceptGuard = new DSLExpression.Call(new DSLExpression.Variable(null, receiverName), "accepts",
                                Arrays.asList(receiverExpression));
                acceptGuard = resolveCachedExpression(resolver, cachedLibrary, context.getType(boolean.class), null, acceptGuard, expression);
                if (acceptGuard != null) {
                    GuardExpression guard = new GuardExpression(specialization, acceptGuard);
                    guard.setLibraryAcceptsGuard(true);
                    specialization.getGuards().add(guard);
                }
                TypeMirror libraryType = types.Library;

                TypeMirror usedLibraryType = parameterType;
                DSLExpression resolveCall = new DSLExpression.Call(null, "resolve", Arrays.asList(new DSLExpression.ClassLiteral(usedLibraryType)));
                DSLExpressionResolver cachedResolver = importStatics(resolver, types.LibraryFactory);

                DSLExpression defaultExpression = new DSLExpression.Call(resolveCall, "create",
                                Arrays.asList(receiverExpression));
                defaultExpression = resolveCachedExpression(cachedResolver, cachedLibrary, libraryType, null, defaultExpression, expression);
                cachedLibrary.setDefaultExpression(defaultExpression);

                DSLExpression uncachedExpression = new DSLExpression.Call(resolveCall, "getUncached",
                                Arrays.asList(receiverExpression));
                cachedLibrary.setUncachedExpression(uncachedExpression);

                uncachedExpression = resolveCachedExpression(cachedResolver, cachedLibrary, libraryType, null, uncachedExpression, expression);

                if (uncachedLibrary != null) {
                    uncachedLibrary.setDefaultExpression(uncachedExpression);
                    uncachedLibrary.setUncachedExpression(uncachedExpression);

                    uncachedLibrary.setAlwaysInitialized(true);
                    uncachedLibrary.setRequiresBoundary(true);
                }
            }

        }
        if (specialization.isFallback()) {
            for (int i = 0; i < libraries.size(); i++) {
                CacheExpression cachedLibrary = libraries.get(i);
                if (cachedLibrary.getCachedLibraryExpression() != null && specialization.hasMultipleInstances()) {
                    cachedLibrary.addError("@%s annotations with specialized receivers are not supported in combination with @%s annotations. " +
                                    "Specify the @%s(limit=\"...\") attribute and remove the receiver expression to use an dispatched library instead.",
                                    getSimpleName(types.CachedLibrary), getSimpleName(types.Fallback), getSimpleName(types.CachedLibrary));
                }
            }
        }

        if (!libraries.isEmpty() && !specialization.hasErrors() && ElementUtils.getAnnotationValue(specialization.getMarkerAnnotation(), "limit", false) == null &&
                        specialization.hasMultipleInstances()) {
            specialization.addError("The limit attribute must be specified if @%s is used with a dynamic parameter. E.g. add limit=\"3\" to resolve this.",
                            types.CachedLibrary.asElement().getSimpleName().toString());
        }

        if (!seenDynamicParameterBound) {
            // no uncached version needed if multiple instances of caches are impossible as they are
            // bound by a cache only.
            return null;
        }

        return uncachedSpecialization;
    }

    private static boolean supportsLibraryMerge(DSLExpression receiverExpression, VariableElement receiverParameter) {
        Set<VariableElement> vars = receiverExpression.findBoundVariableElements();
        // receiver is the only bound parameter
        if (vars.size() == 1 && vars.contains(receiverParameter)) {
            AtomicBoolean supportsMerge = new AtomicBoolean(true);
            receiverExpression.accept(new DSLExpression.AbstractDSLExpressionVisitor() {
                @Override
                public void visitCall(Call binary) {
                    // no longer all
                    supportsMerge.set(false);
                }

                @Override
                public void visitVariable(Variable binary) {
                    if (binary.getReceiver() != null && binary.getResolvedVariable().getKind() == ElementKind.FIELD && !binary.getResolvedVariable().getModifiers().contains(Modifier.FINAL)) {
                        supportsMerge.set(false);
                    }
                }
            });

            return supportsMerge.get();
        }
        return false;
    }

    private void parseCached(CacheExpression cache, SpecializationData specialization, DSLExpressionResolver originalResolver, Parameter parameter) {
        DSLExpressionResolver resolver = originalResolver;
        AnnotationMirror cachedAnnotation = cache.getMessageAnnotation();
        final NodeData node = specialization.getNode();
        Boolean inline = getAnnotationValue(Boolean.class, cachedAnnotation, "inline", false);
        boolean declaresInline = inline != null;
        boolean hasInlineMethod = hasInlineMethod(cache);
        boolean requireCached = node.isGenerateCached() || !hasInlineMethod || (inline != null && !inline);

        if (inline == null) {
            if (forceInlineByDefault(cache)) {
                inline = true;
            } else {
                inline = node.shouldInlineByDefault();
            }
        }

        if (inline && !hasInlineMethod && ElementUtils.isAssignable(cache.getParameter().getType(), types.Node)) {
            if (declaresInline) {
                cache.addError(cachedAnnotation, getAnnotationValue(cachedAnnotation, "inline"),
                                "The cached node type does not support object inlining." + //
                                                " Add @%s or @%s(false) on the node type or disable inlining using @%s(inline=false) to resolve this.",
                                getSimpleName(types.GenerateInline),
                                getSimpleName(types.GenerateInline),
                                getSimpleName(types.Cached));
            } else if (node.isGenerateInline() && NodeCodeGenerator.isSpecializedNode(cache.getParameter().getType()) && !isGenerateInlineFalse(cache)) {
                cache.addSuppressableWarning(TruffleSuppressedWarnings.INLINING_RECOMMENDATION,
                                "The cached node type does not support object inlining." + //
                                                " Add @%s or @%s(false) on the node type or disable inlining using @%s(inline=false) to resolve this.",
                                getSimpleName(types.GenerateInline),
                                getSimpleName(types.GenerateInline),
                                getSimpleName(types.Cached));
                inline = false;
            } else {
                inline = false;
            }
        }

        if (cache.hasErrors()) {
            return;
        }

        boolean requireUncached = node.isGenerateUncached() || mode == ParseMode.EXPORTED_MESSAGE;

        List<String> expressionParameters = getAnnotationValueList(String.class, cachedAnnotation, "parameters");

        String initializer = getAnnotationValue(String.class, cachedAnnotation, "value");
        String uncached = getAnnotationValue(String.class, cachedAnnotation, "uncached");

        String parameters = String.join(", ", expressionParameters);
        initializer = initializer.replace("$parameters", parameters);
        uncached = uncached.replace("$parameters", parameters);

        if (ElementUtils.isAssignable(parameter.getType(), types.Library) && !ElementUtils.typeEquals(parameter.getType(), types.Library)) {
            cache.addError("The use of @%s is not supported for libraries. Use @%s instead.",
                            types.Cached.asElement().getSimpleName().toString(), types.CachedLibrary.asElement().getSimpleName().toString());
        } else if (NodeCodeGenerator.isSpecializedNode(parameter.getType())) {
            // if it is a node try to parse with the node parser to find out whether we
            // should may use the generated create and getUncached methods.
            List<CodeExecutableElement> executables = parseNodeFactoryMethods(parameter.getType());
            if (executables != null) {
                resolver = resolver.copy(executables);
            }
        }

        if (cache.hasErrors()) {
            return;
        }

        AnnotationValue adopt = getAnnotationValue(cachedAnnotation, "adopt", false);
        if (inline && hasInlineMethod) {
            ExecutableElement inlineMethod = lookupInlineMethod(originalResolver, node, cache, cache.getParameter().getType(), cache);
            List<InlineFieldData> fields;
            if (inlineMethod != null) {
                fields = parseInlineMethod(cache, null, inlineMethod);
                if (!cache.hasErrors() && !typeEquals(inlineMethod.getReturnType(), cache.getParameter().getType())) {
                    cache.addError("Invalid return type %s found but expected %s. This is a common error if a different type is required for inlining.",
                                    getQualifiedName(inlineMethod.getReturnType()),
                                    getQualifiedName(cache.getParameter().getType()));
                }
            } else {
                /*
                 * May happen for recursive inlines.
                 */
                fields = Collections.emptyList();
            }

            cache.setInlinedNode(new InlinedNodeData(inlineMethod, fields));
        } else if (requireCached) {
            AnnotationMirror cached = findAnnotationMirror(cache.getParameter().getVariableElement(), types.Cached);
            cache.setDefaultExpression(parseCachedExpression(resolver, cache, parameter.getType(), initializer));

            cache.setDimensions(getAnnotationValue(Integer.class, cached, "dimensions"));
            boolean disabledAdopt = adopt != null && Boolean.FALSE.equals(adopt.getValue());
            if (parameter.getType().getKind() == TypeKind.ARRAY &&
                            (disabledAdopt || !isSubtype(((ArrayType) parameter.getType()).getComponentType(), types.NodeInterface))) {
                if (cache.getDimensions() == -1) {
                    cache.addWarning("The cached dimensions attribute must be specified for array types.");
                }
            } else {
                if (!disabledAdopt && cache.getDimensions() != -1) {
                    cache.addError("The dimensions attribute has no affect for the type %s.", getSimpleName(parameter.getType()));
                }
            }
        }

        if (cache.hasErrors()) {
            return;
        }

        boolean uncachedSpecified = getAnnotationValue(cachedAnnotation, "uncached", false) != null;
        if (requireUncached) {
            boolean allowUncached = getAnnotationValue(Boolean.class, cachedAnnotation, "allowUncached");
            if (uncachedSpecified && allowUncached) {
                cache.addError("The attributes 'allowUncached' and 'uncached' are mutually exclusive. Remove one of the attributes to resolve this.");
            } else if (allowUncached) {
                cache.setUncachedExpression(cache.getDefaultExpression());
            } else {
                if (!uncachedSpecified && cache.getDefaultExpression() != null && !cache.getDefaultExpression().mayAllocate()) {
                    cache.setUncachedExpression(cache.getDefaultExpression());
                } else {
                    cache.setUncachedExpression(parseCachedExpression(resolver, cache, parameter.getType(), uncached));

                    if (!uncachedSpecified && cache.hasErrors()) {
                        cache.setUncachedExpressionError(cache.getMessages().iterator().next());
                        cache.getMessages().clear();
                    }
                }
            }
            if (cache.getUncachedExpression() == null && cache.getDefaultExpression() != null) {
                if (specialization.isTrivialExpression(cache.getDefaultExpression())) {
                    cache.setUncachedExpression(cache.getDefaultExpression());
                }
            }
        }
        if (cache.hasErrors()) {
            return;
        }

        if (adopt != null) {
            TypeMirror type = parameter.getType();
            if (type == null || !ElementUtils.isAssignable(type, types.NodeInterface) &&
                            !(type.getKind() == TypeKind.ARRAY && isAssignable(((ArrayType) type).getComponentType(), types.NodeInterface))) {
                cache.addError("Type '%s' is neither a NodeInterface type, nor an array of NodeInterface types and therefore it can not be adopted. Remove the adopt attribute to resolve this.",
                                Objects.toString(type));
            }
        }
        cache.setAdopt(getAnnotationValue(Boolean.class, cachedAnnotation, "adopt", true));

        Boolean neverDefault = getAnnotationValue(Boolean.class, cachedAnnotation, "neverDefault", false);
        cache.setNeverDefaultGuaranteed(isNeverDefaultGuaranteed(specialization, cache));
        if (neverDefault != null) {
            cache.setNeverDefault(neverDefault);
        } else {
            cache.setNeverDefault(isNeverDefaultImplied(cache));
        }

    }

    private static boolean isNeverDefaultEasilySupported(CacheExpression cache) {
        if (cache.isEncodedEnum()) {
            /*
             * Encoded enums can just encode the never default state in a special bit so does not
             * need to cause a warning for never default.
             */
            return true;
        }
        return false;
    }

    private boolean isNeverDefaultImplied(CacheExpression cache) {
        /*
         * Never default is implied if there is no custom initializer expression set. Default
         * initializer create expressions very rarely may return null, therefore it is enough to
         * fail at runtime for these cases.
         */
        if (cache.isWeakReference()) {
            return true;
        }

        if (cache.isCachedLibrary()) {
            return true;
        }

        if (cache.getInlinedNode() != null) {
            return true;
        }

        if (cache.isNeverDefaultGuaranteed()) {
            return true;
        }

        if (isNeverDefaultImpliedByAnnotation(cache)) {
            return true;
        }

        return false;
    }

    private boolean isNeverDefaultImpliedByAnnotation(CacheExpression cache) {
        if (cache.getDefaultExpression() != null) {
            ExecutableElement executable = cache.getDefaultExpression().resolveExecutable();
            if (executable != null) {
                if (ElementUtils.findAnnotationMirror(executable, types.NeverDefault) != null) {
                    return true;
                }
            }
            VariableElement var = cache.getDefaultExpression().resolveVariable();
            if (var != null) {
                if (ElementUtils.findAnnotationMirror(var, types.NeverDefault) != null) {
                    return true;
                }
            }

        }
        return false;
    }

    private static boolean isNeverDefaultGuaranteed(SpecializationData specialization, CacheExpression cache) {
        DSLExpression expression = cache.getDefaultExpression();
        if (expression == null) {
            return false;
        }

        TypeMirror type = expression.getResolvedType();
        if (type != null && ElementUtils.isPrimitive(type) && !ElementUtils.isPrimitive(cache.getParameter().getType())) {
            // An assignment of a primitive to its boxed type is never null.
            return true;
        }

        Object constant = expression.resolveConstant();
        if (constant != null) {
            if (constant instanceof Number) {
                long value = ((Number) constant).longValue();
                if (value != 0) {
                    return true;
                }
            } else if (constant instanceof Boolean) {
                if ((boolean) constant != false) {
                    return true;
                }
            } else if (constant instanceof String) {
                return true;
            } else {
                throw new AssertionError("Unhandled constant type.");
            }
        }

        ExecutableElement method = expression.resolveExecutable();
        if (method != null && method.getKind() == ElementKind.CONSTRUCTOR) {
            // Constructors never return null.
            return true;
        }

        /*
         * Object.getClass() and Object.toString() always returns a non-null value.
         */
        ProcessorContext context = ProcessorContext.getInstance();
        TruffleTypes types = context.getTypes();
        if (method != null && types.isBuiltinNeverDefault(method)) {
            return true;
        }

        VariableElement var = expression.resolveVariable();
        if (var != null) {
            if (var.getSimpleName().toString().equals("this")) {
                // this pointer for libraries never null
                return true;
            }

            if (types.isBuiltinNeverDefault(var)) {
                return true;
            }
        }

        /*
         * If cached value binds a type guard checked reference type we know the value is never
         * null.
         */
        if (var != null && !var.asType().getKind().isPrimitive()) {
            int index = 0;
            for (Parameter p : specialization.getSignatureParameters()) {
                if (Objects.equals(var, p.getVariableElement())) {
                    SpecializationData generic = specialization.getNode().getFallbackSpecialization();
                    NodeExecutionData execution = specialization.getNode().getChildExecutions().get(index);
                    TypeMirror genericType = generic.findParameter(execution).getType();
                    if (ElementUtils.needsCastTo(genericType, p.getType())) {
                        return true;
                    }
                }
                index++;
            }
        }

        return false;
    }

    private List<InlineFieldData> parseInlineMethod(MessageContainer errorContainer, Element errorElement, ExecutableElement inlineMethod) {
        List<InlineFieldData> fields = new ArrayList<>();
        if (inlineMethod.getParameters().size() != 1 || !typeEquals(types.InlineSupport_InlineTarget, inlineMethod.getParameters().get(0).asType())) {
            if (errorContainer != null) {
                errorContainer.addError(errorElement, "Inline method %s is invalid. The method must have exactly one parameter of type '%s'.",
                                ElementUtils.getReadableSignature(inlineMethod),
                                getSimpleName(types.InlineSupport_InlineTarget));
            }
            return fields;
        }

        VariableElement var = inlineMethod.getParameters().get(0);
        List<AnnotationMirror> requiredFields = ElementUtils.getRepeatedAnnotation(var.getAnnotationMirrors(), types.InlineSupport_RequiredField);

        int fieldIndex = 0;
        for (AnnotationMirror requiredField : requiredFields) {
            TypeMirror value = ElementUtils.getAnnotationValue(TypeMirror.class, requiredField, "value");
            Integer bits = ElementUtils.getAnnotationValue(Integer.class, requiredField, "bits", false);
            TypeMirror type = ElementUtils.getAnnotationValue(TypeMirror.class, requiredField, "type", false);
            int dimensions = ElementUtils.getAnnotationValue(Integer.class, requiredField, "dimensions");

            if (value == null) {
                // compile error;
                continue;
            }
            if (bits != null) {
                if (bits < 0 || bits > 32) {
                    if (errorContainer != null) {
                        errorContainer.addError(errorElement, "Inline method %s is invalid. Bits specification is out of range (must be >= 0 && <= 32).",
                                        ElementUtils.getReadableSignature(inlineMethod));
                    }
                    continue;
                }
            }
            if (type != null) {
                if (ElementUtils.isPrimitive(type)) {
                    if (errorContainer != null) {
                        errorContainer.addError(errorElement, "Inline method %s is invalid. Field type must not be a primitive type. Use primitive field classes instead.",
                                        ElementUtils.getReadableSignature(inlineMethod));
                    }
                    continue;
                }
            }

            InlineFieldData fieldData = new InlineFieldData(null, "field" + fieldIndex, value, bits, type, dimensions);
            if (fieldData.isState()) {
                if (bits == null) {
                    if (errorContainer != null) {
                        errorContainer.addError(errorElement, "Inline method %s is invalid. State fields must specify a bits attribute",
                                        ElementUtils.getReadableSignature(inlineMethod));
                    }
                    continue;
                }
                if (type != null) {
                    if (errorContainer != null) {
                        errorContainer.addError(errorElement, "Inline method %s is invalid. State fields must not specify a type. ",
                                        ElementUtils.getReadableSignature(inlineMethod));
                    }
                    continue;
                }
                type = context.getType(int.class);
            } else if (fieldData.isPrimitive()) {
                if (type != null) {
                    if (errorContainer != null) {
                        errorContainer.addError(errorElement, "Inline method %s is invalid. Primitive fields must not specify a type. ",
                                        ElementUtils.getReadableSignature(inlineMethod));
                    }

                    continue;
                }
                if (bits != null) {
                    if (errorContainer != null) {
                        errorContainer.addError(errorElement, "Inline method %s is invalid. Primitive fields must not specify bits. ",
                                        ElementUtils.getReadableSignature(inlineMethod));
                    }
                    continue;
                }

            } else if (fieldData.isReference()) {
                if (type == null) {
                    if (errorContainer != null) {
                        errorContainer.addError(errorElement, "Inline method %s is invalid. Reference fields must specify a type. ",
                                        ElementUtils.getReadableSignature(inlineMethod));
                    }
                    continue;
                }
            } else {
                if (errorContainer != null) {
                    errorContainer.addError(errorElement, "Inline method %s is invalid. Invalid field type %s.",
                                    ElementUtils.getReadableSignature(inlineMethod),
                                    ElementUtils.getSimpleName(type));
                }
                continue;
            }

            fields.add(fieldData);
            fieldIndex++;
        }
        return fields;
    }

    /*
     * Is force inline by default enabled. Special case when cached is not set explicitly and the
     * target type only has an inline method and no create method, then we turn on inline by default
     * on. This enables that @Cached InlinedBranchProfile inlines by default even if a cached
     * version is generated and no warning is printed.
     */
    private static boolean forceInlineByDefault(CacheExpression cache) {
        AnnotationMirror cacheAnnotation = cache.getMessageAnnotation();
        TypeElement parameterType = ElementUtils.castTypeElement(cache.getParameter().getType());
        if (parameterType == null) {
            return false;
        }
        boolean defaultCached = getAnnotationValue(cacheAnnotation, "value", false) == null;
        if (defaultCached && !hasDefaultCreateCacheMethod(parameterType.asType())) {
            return hasInlineMethod(cache);
        }
        if (NodeCodeGenerator.isSpecializedNode(parameterType.asType())) {
            AnnotationMirror inlineAnnotation = getGenerateInlineAnnotation(parameterType.asType());
            if (inlineAnnotation != null) {
                return getAnnotationValue(Boolean.class, inlineAnnotation, "value") &&
                                getAnnotationValue(Boolean.class, inlineAnnotation, "inlineByDefault");
            }
        }
        return false;
    }

    private static boolean isGenerateInlineFalse(CacheExpression cache) {
        TypeMirror type = cache.getParameter().getType();
        if (!NodeCodeGenerator.isSpecializedNode(type)) {
            return false;
        }
        AnnotationMirror inlineAnnotation = getGenerateInlineAnnotation(type);
        if (inlineAnnotation != null && getAnnotationValue(Boolean.class, inlineAnnotation, "value") == false) {
            return true;
        }
        return false;
    }

    private static boolean hasInlineMethod(CacheExpression cache) {
        TypeMirror type = cache.getParameter().getType();
        TypeElement parameterType = ElementUtils.castTypeElement(type);
        if (parameterType == null) {
            return false;
        }

        ExecutableElement inlineMethod = ElementUtils.findStaticMethod(parameterType, "inline");
        if (inlineMethod != null) {
            return true;
        }
        if (isGenerateInline(parameterType) && NodeCodeGenerator.isSpecializedNode(parameterType.asType())) {
            return true;
        }

        String inlineMethodName = ElementUtils.getAnnotationValue(String.class, cache.getMessageAnnotation(), "inlineMethod", false);
        if (inlineMethodName != null) {
            return true;
        }

        return false;
    }

    private ExecutableElement lookupInlineMethod(DSLExpressionResolver resolver, NodeData node, CacheExpression cache, TypeMirror type, MessageContainer errorTarget) {
        String inlineMethodName = ElementUtils.getAnnotationValue(String.class, cache.getMessageAnnotation(), "inlineMethod", false);
        ExecutableElement inlineMethod = null;
        if (inlineMethodName != null) {
            inlineMethod = resolver.lookupMethod(inlineMethodName, Arrays.asList(types.InlineSupport_InlineTarget));

            String errorMessage = null;
            if (inlineMethod == null) {
                errorMessage = String.format("Static inline method with name '%s' and parameter type '%s' could not be resolved. ",
                                inlineMethodName,
                                ElementUtils.getSimpleName(types.InlineSupport_InlineTarget));
            } else if (!ElementUtils.isVisible(node.getTemplateType(), inlineMethod)) {
                errorMessage = String.format("The method %s is not visible. ", ElementUtils.getReadableSignature(inlineMethod));
            }

            if (errorMessage != null && errorTarget != null) {
                errorTarget.addError(
                                "%sExpected method with signature 'static %s inline(%s target)' in an enclosing class like '%s'. ",
                                errorMessage,
                                cache.getParameter().getType(),
                                getSimpleName(types.InlineSupport_InlineTarget),
                                getSimpleName(node.getTemplateType()));
            }
        }

        if (inlineMethod == null) {
            if (!hasInlineMethod(cache)) {
                return null;
            }
            TypeElement parameterType = ElementUtils.castTypeElement(type);
            inlineMethod = ElementUtils.findStaticMethod(parameterType, "inline");

            if (node.isGenerateInline() && isRecursiveType(node, parameterType)) {
                if (errorTarget != null) {
                    errorTarget.addError("Detected recursive inlined cache with type '%s'. Recursive inlining cannot be supported. " + //
                                    "Remove the recursive declaration or disable inlining with @%s(..., inline=false) to resolve this.",
                                    getSimpleName(parameterType),
                                    getSimpleName(types.Cached));
                }
                return null;
            }

            Map<String, ExecutableElement> inlineSignatureCache = context.getInlineSignatureCache();
            String id = ElementUtils.getUniqueIdentifier(parameterType.asType());
            ExecutableElement cachedInline;
            if (inlineSignatureCache.containsKey(id)) {
                cachedInline = inlineSignatureCache.get(id);
            } else {
                NodeData inlinedNode = lookupNodeData(node, type, errorTarget);
                if (inlinedNode != null && inlinedNode.isGenerateInline()) {
                    CodeExecutableElement method = NodeFactoryFactory.createInlineMethod(inlinedNode, null);
                    method.setEnclosingElement(NodeCodeGenerator.nodeElement(inlinedNode));
                    cachedInline = method;
                } else {
                    cachedInline = null;
                }
                inlineSignatureCache.put(id, cachedInline);
            }
            if (cachedInline != null) {
                inlineMethod = cachedInline;
            }
        }
        return inlineMethod;
    }

    private static boolean hasDefaultCreateCacheMethod(TypeMirror type) {
        TypeElement parameterType = ElementUtils.castTypeElement(type);
        if (parameterType == null) {
            return false;
        }
        ExecutableElement createMethod = ElementUtils.findStaticMethod(parameterType, "create");
        if (createMethod != null) {
            return true;
        }
        AnnotationMirror annotation = findGenerateAnnotation(parameterType.asType(), ProcessorContext.getInstance().getTypes().GenerateCached);
        Boolean value = Boolean.TRUE;
        if (annotation != null) {
            value = ElementUtils.getAnnotationValue(Boolean.class, annotation, "value");
        }
        return value && NodeCodeGenerator.isSpecializedNode(type);
    }

    @SuppressWarnings({"unchecked", "try"})
    private NodeData lookupNodeData(NodeData node, TypeMirror type, MessageContainer errorTarget) {
        TypeElement parameterType = ElementUtils.castTypeElement(type);
        String typeId = ElementUtils.getQualifiedName(type);
        NodeData nodeData = nodeDataCache.get(typeId);
        if (nodeDataCache.containsKey(typeId)) {
            return nodeData;
        }
        if (NodeCodeGenerator.isSpecializedNode(type) && !isRecursiveType(node, parameterType)) {
            try (Timer timer = Timer.create(getClass().getSimpleName(), parameterType)) {
                /*
                 * The annotation processor cannot see the code that it is producing, so we actually
                 * parse the entire specialized node to fully be able to compute the state space and
                 * therefore get a valid inline state space parameters.
                 */
                NodeParser parser = NodeParser.createDefaultParser();
                // assign parent inlining parser for recursion detection
                parser.parsingParent = node;

                nodeData = parser.parseNode(parameterType);

                if (nodeData == null || nodeData.hasErrors()) {
                    if (errorTarget != null && nodeData != null && nodeData.hasErrors()) {
                        nodeData.redirectMessages(errorTarget);
                    }
                    // do not cache if there are errors
                    return nodeData;
                }
            }
        }
        nodeDataCache.put(typeId, nodeData);
        return nodeData;
    }

    private static boolean isRecursiveType(NodeData node, TypeElement parameterType) {
        NodeData parent = node;
        while (parent != null) {
            if (ElementUtils.elementEquals(parent.getTemplateType(), parameterType)) {
                return true;
            }
            parent = parent.getParsingParent();
        }
        return false;
    }

    private final Map<String, NodeData> nodeDataCache = new HashMap<>();

    private static final class FactoryMethodCacheKey {
    }

    private List<CodeExecutableElement> parseNodeFactoryMethods(TypeMirror nodeType) {
        if (nodeOnly) {
            return null;
        }
        Map<TypeMirror, List<CodeExecutableElement>> cache = ProcessorContext.getInstance().getCacheMap(FactoryMethodCacheKey.class);
        if (cache.containsKey(nodeType)) {
            return cache.get(nodeType);
        }

        NodeParser parser = NodeParser.createDefaultParser();
        parser.nodeOnly = true; // make sure we cannot have cycles
        TypeElement element = ElementUtils.castTypeElement(nodeType);
        NodeData parsedNode = parser.parse(element);
        List<CodeExecutableElement> executables = null;
        if (parsedNode != null) {
            executables = NodeFactoryFactory.createFactoryMethods(parsedNode, ElementFilter.constructorsIn(element.getEnclosedElements()));
            TypeElement type = ElementUtils.castTypeElement(NodeCodeGenerator.factoryOrNodeType(parsedNode));
            for (CodeExecutableElement executableElement : executables) {
                executableElement.setEnclosingElement(type);
            }
        }
        cache.put(nodeType, executables);
        return executables;
    }

    private DSLExpression resolveCachedExpression(DSLExpressionResolver resolver, MessageContainer msg,
                    TypeMirror targetType, TypeMirror importType, DSLExpression expression, String originalString) {
        DSLExpressionResolver localResolver = importStatics(resolver, importType);
        localResolver = importStatics(localResolver, targetType);

        DSLExpression resolvedExpression = DSLExpression.resolve(localResolver, msg, null, expression, originalString);
        if (resolvedExpression != null) {
            if (targetType == null || isAssignable(resolvedExpression.getResolvedType(), targetType)) {
                return resolvedExpression;
            } else {
                msg.addError("Incompatible return type %s. The expression type must be equal to the parameter type %s.", getSimpleName(resolvedExpression.getResolvedType()),
                                getSimpleName(targetType));
                return null;
            }
        }
        return resolvedExpression;
    }

    private DSLExpressionResolver importStatics(DSLExpressionResolver resolver, TypeMirror targetType) {
        if (targetType == null) {
            return resolver;
        }
        DSLExpressionResolver localResolver = resolver;
        if (targetType.getKind() == TypeKind.DECLARED) {
            List<Element> prefixedImports = importVisibleStaticMembersImpl(resolver.getAccessType(), fromTypeMirror(targetType), true);
            localResolver = localResolver.copy(prefixedImports);
        }
        return localResolver;
    }

    private DSLExpression parseCachedExpression(DSLExpressionResolver resolver, MessageContainer msg, TypeMirror targetType, String string) {
        DSLExpression expression = DSLExpression.parse(msg, null, string);
        if (expression == null) {
            return null;
        }
        return resolveCachedExpression(resolver, msg, targetType, null, expression, string);
    }

    private void initializeGuards(SpecializationData specialization, DSLExpressionResolver resolver) {
        List<String> guardDefinitions = getAnnotationValueList(String.class, specialization.getMarkerAnnotation(), "guards");

        Set<CacheExpression> handledCaches = new HashSet<>();

        List<GuardExpression> existingGuards = new ArrayList<>(specialization.getGuards());
        for (String guardExpression : guardDefinitions) {
            existingGuards.add(parseGuard(resolver, specialization, guardExpression));
        }

        List<GuardExpression> newGuards = new ArrayList<>();
        for (GuardExpression guard : existingGuards) {
            if (guard.getExpression() != null) {
                Set<CacheExpression> caches = specialization.getBoundCaches(guard.getExpression(), false);
                for (CacheExpression cache : caches) {
                    if (handledCaches.contains(cache)) {
                        continue;
                    }
                    cache.setIsUsedInGuard(true);

                    handledCaches.add(cache);
                    if (cache.isWeakReferenceGet()) {
                        newGuards.add(createWeakReferenceGuard(resolver, specialization, cache));
                    }
                }
            }

            newGuards.add(guard);
        }
        for (CacheExpression cache : specialization.getCaches()) {
            if (cache.isWeakReferenceGet()) {
                if (handledCaches.contains(cache)) {
                    continue;
                }
                newGuards.add(createWeakReferenceGuard(resolver, specialization, cache));
            }
        }
        specialization.getGuards().clear();
        specialization.getGuards().addAll(newGuards);
    }

    private GuardExpression createWeakReferenceGuard(DSLExpressionResolver resolver, SpecializationData specialization, CacheExpression cache) {
        GuardExpression guard = parseGuard(resolver, specialization, cache.getParameter().getLocalName() + " != null");
        guard.setWeakReferenceGuard(true);
        return guard;
    }

    private GuardExpression parseGuard(DSLExpressionResolver resolver, SpecializationData specialization, String guard) {
        final TypeMirror booleanType = context.getType(boolean.class);
        DSLExpression expression = DSLExpression.parseAndResolve(resolver, specialization, "guards", guard);
        GuardExpression guardExpression = new GuardExpression(specialization, expression);
        if (expression == null) {
            return guardExpression;
        }
        if (!typeEquals(expression.getResolvedType(), booleanType)) {
            guardExpression.addError("Incompatible return type %s. Guards must return %s.", getSimpleName(expression.getResolvedType()), getSimpleName(booleanType));
        }
        return guardExpression;
    }

    private void initializeFallback(final NodeData node) {
        List<SpecializationData> fallbackSpecializations = new ArrayList<>();
        for (SpecializationData spec : node.getSpecializations()) {
            if (spec.isFallback()) {
                fallbackSpecializations.add(spec);
            }
        }

        if (fallbackSpecializations.size() == 1 && node.getSpecializations().size() == 1) {
            // TODO GR-38632 this limitation should be lifted
            for (SpecializationData fallback : fallbackSpecializations) {
                fallback.addError("@%s defined but no @%s.", getSimpleName(types.Fallback), getSimpleName(types.Specialization));
            }
        }

        if (fallbackSpecializations.isEmpty()) {
            node.getSpecializations().add(createFallbackSpecialization(node));
        } else {
            if (fallbackSpecializations.size() > 1) {
                for (SpecializationData generic : fallbackSpecializations) {
                    generic.addError("Only one @%s is allowed per operation.", getSimpleName(types.Fallback));
                }
            }
        }
    }

    private SpecializationData createFallbackSpecialization(final NodeData node) {
        FallbackParser parser = new FallbackParser(context, node);
        MethodSpec specification = parser.createDefaultMethodSpec(node.getSpecializations().iterator().next().getMethod(), null, true, null);

        List<VariableElement> parameterTypes = new ArrayList<>();
        int signatureIndex = 1;
        for (ParameterSpec spec : specification.getRequired()) {
            parameterTypes.add(new CodeVariableElement(createGenericType(node, spec), "arg" + signatureIndex));
            if (spec.isSignature()) {
                signatureIndex++;
            }
        }

        TypeMirror returnType = createGenericType(node, specification.getReturnType());
        SpecializationData generic = parser.create("Generic", TemplateMethod.NO_NATURAL_ORDER, null, null, returnType, parameterTypes);

        if (generic == null) {
            throw new RuntimeException("Unable to create generic signature for node " + node.getNodeId() + " with " + parameterTypes + ". Specification " + specification + ".");
        }

        generic.setReplaced(false);
        generic.setReplaces(new LinkedHashSet<>());
        generic.setReplacedBy(new LinkedHashSet<>());

        return generic;
    }

    private TypeMirror createGenericType(NodeData node, ParameterSpec spec) {
        NodeExecutionData execution = spec.getExecution();
        Collection<TypeMirror> allowedTypes;
        if (execution == null) {
            allowedTypes = spec.getAllowedTypes();
        } else {
            allowedTypes = Arrays.asList(node.getGenericType(execution));
        }
        if (allowedTypes.size() == 1) {
            return allowedTypes.iterator().next();
        } else {
            return getCommonSuperType(context, allowedTypes);
        }
    }

    private static boolean verifySpecializationSameLength(NodeData nodeData) {
        if (nodeData.isGenerateInline()) {
            // no children allowed. the execute method determines the number of children
            // necessary because the node signature parameter is optional.
            return true;
        }
        int lastArgs = -1;
        for (SpecializationData specializationData : nodeData.getSpecializations()) {
            int signatureArgs = specializationData.getSignatureSize();
            if (lastArgs == signatureArgs) {
                continue;
            }
            if (lastArgs != -1) {
                for (SpecializationData specialization : nodeData.getSpecializations()) {
                    specialization.addError("All specializations must have the same number of arguments.");
                }
                return false;
            } else {
                lastArgs = signatureArgs;
            }
        }
        return true;
    }

    private void verifyVisibilities(NodeData node) {
        if (node.getTemplateType().getModifiers().contains(Modifier.PRIVATE) && node.getSpecializations().size() > 0) {
            node.addError("Classes containing a @%s annotation must not be private.", types.Specialization.asElement().getSimpleName().toString());
        }
    }

    private static void verifyMissingAbstractMethods(NodeData nodeData, List<? extends Element> originalElements) {
        if (!nodeData.needsFactory()) {
            // missing abstract methods only needs to be implemented
            // if we need go generate factory for it.
            return;
        }

        List<Element> elements = ElementUtils.newElementList(originalElements);
        Set<Element> unusedElements = new HashSet<>(elements);
        for (ExecutableElement method : nodeData.getAllTemplateMethods()) {
            unusedElements.remove(method);
        }

        for (NodeFieldData field : nodeData.getFields()) {
            if (field.getGetter() != null) {
                unusedElements.remove(field.getGetter());
            }
            if (field.getSetter() != null) {
                unusedElements.remove(field.getSetter());
            }
        }

        for (NodeChildData child : nodeData.getChildren()) {
            if (child.getAccessElement() != null) {
                unusedElements.remove(child.getAccessElement());
            }
        }

        Map<String, List<ExecutableElement>> methodsByName = null;

        outer: for (ExecutableElement unusedMethod : ElementFilter.methodsIn(unusedElements)) {
            if (unusedMethod.getModifiers().contains(Modifier.ABSTRACT)) {

                // group by method name to avoid N^2 worst case complexity.
                if (methodsByName == null) {
                    methodsByName = new HashMap<>();
                    for (ExecutableElement m : ElementFilter.methodsIn(unusedElements)) {
                        String name = m.getSimpleName().toString();
                        List<ExecutableElement> groupedElements = methodsByName.get(name);
                        if (groupedElements == null) {
                            groupedElements = new ArrayList<>();
                            methodsByName.put(name, groupedElements);
                        }
                        groupedElements.add(m);
                    }
                }

                for (ExecutableElement otherMethod : methodsByName.get(unusedMethod.getSimpleName().toString())) {
                    if (unusedMethod == otherMethod) {
                        continue;
                    }
                    if (ProcessorContext.getInstance().getEnvironment().getElementUtils().overrides(otherMethod, unusedMethod, nodeData.getTemplateType())) {
                        // the abstract method overridden by another method in the template type.
                        // -> the method does not need an implementation.
                        continue outer;
                    }
                }

                nodeData.addError("The type %s must implement the inherited abstract method %s.", getSimpleName(nodeData.getTemplateType()),
                                getReadableSignature(unusedMethod));
            }
        }
    }

    private static void verifySpecializationThrows(NodeData node) {
        Map<String, SpecializationData> specializationMap = new HashMap<>();
        for (SpecializationData spec : node.getSpecializations()) {
            specializationMap.put(spec.getMethodName(), spec);
        }
        for (SpecializationData sourceSpecialization : node.getSpecializations()) {
            if (sourceSpecialization.getExceptions() != null) {
                for (SpecializationThrowsData throwsData : sourceSpecialization.getExceptions()) {
                    for (SpecializationThrowsData otherThrowsData : sourceSpecialization.getExceptions()) {
                        if (otherThrowsData != throwsData && typeEquals(otherThrowsData.getJavaClass(), throwsData.getJavaClass())) {
                            throwsData.addError("Duplicate exception type.");
                        }
                    }
                }
            }
        }
    }

    private static void verifyFrame(NodeData node) {
        final List<NodeExecutionData> childExecutions = node.getChildExecutions();
        final ExecutableTypeData[] requiresFrameParameter = new ExecutableTypeData[childExecutions.size()];
        boolean needsCheck = false;
        for (int i = 0; i < childExecutions.size(); i++) {
            final NodeChildData childExecution = childExecutions.get(i).getChild();
            if (childExecution != null) {
                for (ExecutableTypeData executable : childExecution.getNodeData().getExecutableTypes()) {
                    if (executable.getFrameParameter() != null) {
                        requiresFrameParameter[i] = executable;
                        needsCheck = true;
                        break;
                    }
                }
            }
        }
        if (needsCheck) {
            for (ExecutableTypeData executable : node.getExecutableTypes()) {
                if (executable.getFrameParameter() == null) {
                    for (int executionIndex = executable.getEvaluatedCount(); executionIndex < node.getExecutionCount(); executionIndex++) {
                        if (requiresFrameParameter[executionIndex] != null) {
                            node.addError(String.format(
                                            "Child execution method: %s called from method: %s requires a frame parameter.",
                                            createMethodSignature(requiresFrameParameter[executionIndex].getMethod()),
                                            createMethodSignature(executable.getMethod())));
                        }
                    }
                }
            }
        }
    }

    private static void verifyReportPolymorphism(NodeData node) {
        if (node.isReportPolymorphism()) {
            List<SpecializationData> reachableSpecializations = node.getReachableSpecializations();
            if (reachableSpecializations.size() == 1 && reachableSpecializations.get(0).getMaximumNumberOfInstances() == 1) {
                node.addSuppressableWarning(TruffleSuppressedWarnings.SPLITTING,
                                "This node uses @ReportPolymorphism but has a single specialization instance, so the annotation has no effect. Remove the annotation or move it to another node to resolve this.");
            }

            if (reachableSpecializations.stream().noneMatch(SpecializationData::isReportPolymorphism)) {
                node.addSuppressableWarning(TruffleSuppressedWarnings.SPLITTING,
                                "This node uses @ReportPolymorphism but all specializations use @ReportPolymorphism.Exclude. Remove some excludes or do not use ReportPolymorphism at all for this node to resolve this.");
            }

            if (reachableSpecializations.stream().anyMatch(SpecializationData::isReportMegamorphism)) {
                node.addSuppressableWarning(TruffleSuppressedWarnings.SPLITTING,
                                "This node uses @ReportPolymorphism on the class and @ReportPolymorphism.Megamorphic on some specializations, the latter annotation has no effect. Remove one of the annotations to resolve this.");
            }
        }
    }

    private static String createMethodSignature(final ExecutableElement method) {
        final StringBuilder result = new StringBuilder();
        result.append(getSimpleName(method.getReturnType())).append(' ').append(getSimpleName((TypeElement) method.getEnclosingElement())).append("::").append(
                        method.getSimpleName()).append('(');
        boolean first = true;
        for (VariableElement parameter : method.getParameters()) {
            if (first) {
                first = false;
            } else {
                result.append(", ");
            }
            result.append(getSimpleName(parameter.asType()));
        }
        result.append(')');
        return result.toString();
    }

    private static void verifyConstructors(NodeData nodeData) {
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(nodeData.getTemplateType().getEnclosedElements());
        if (constructors.isEmpty()) {
            return;
        }

        boolean oneNonPrivate = false;
        for (ExecutableElement constructor : constructors) {
            if (getVisibility(constructor.getModifiers()) != Modifier.PRIVATE) {
                oneNonPrivate = true;
                break;
            }
        }
        if (!oneNonPrivate && !nodeData.getTemplateType().getModifiers().contains(Modifier.PRIVATE)) {
            nodeData.addError("At least one constructor must be non-private.");
        }
    }

    private static AnnotationMirror findFirstAnnotation(List<? extends Element> elements, DeclaredType annotation) {
        for (Element element : elements) {
            AnnotationMirror mirror = findAnnotationMirror(element, annotation);
            if (mirror != null) {
                return mirror;
            }
        }
        return null;
    }

    private TypeMirror inheritType(AnnotationMirror annotation, String valueName, TypeMirror parentType) {
        TypeMirror inhertNodeType = types.Node;
        TypeMirror value = getAnnotationValue(TypeMirror.class, annotation, valueName);
        if (typeEquals(inhertNodeType, value)) {
            return parentType;
        } else {
            return value;
        }
    }

    private ExecutableElement findGetter(List<? extends Element> elements, String variableName, TypeMirror type) {
        if (type == null) {
            return null;
        }
        String methodName;
        if (typeEquals(type, context.getType(boolean.class))) {
            methodName = "is" + firstLetterUpperCase(variableName);
        } else {
            methodName = "get" + firstLetterUpperCase(variableName);
        }

        for (ExecutableElement method : ElementFilter.methodsIn(elements)) {
            if (method.getSimpleName().toString().equals(methodName) && method.getParameters().size() == 0 && isAssignable(type, method.getReturnType())) {
                return method;
            }
        }
        return null;
    }

    private static ExecutableElement findSetter(List<? extends Element> elements, String variableName, TypeMirror type) {
        if (type == null) {
            return null;
        }
        String methodName = "set" + firstLetterUpperCase(variableName);
        for (ExecutableElement method : ElementFilter.methodsIn(elements)) {
            if (method.getSimpleName().toString().equals(methodName) && method.getParameters().size() == 1 && typeEquals(type, method.getParameters().get(0).asType())) {
                return method;
            }
        }
        return null;
    }

    private static List<TypeElement> collectSuperClasses(List<TypeElement> collection, TypeElement element) {
        if (element != null) {
            collection.add(element);
            if (element.getSuperclass() != null) {
                collectSuperClasses(collection, fromTypeMirror(element.getSuperclass()));
            }
        }
        return collection;
    }

    private static Map<SharableCache, Collection<CacheExpression>> computeSharableCaches(Collection<NodeData> nodes) {
        Map<SharableCache, Collection<CacheExpression>> sharableCaches = new LinkedHashMap<>();
        for (NodeData node : nodes) {
            for (SpecializationData specialization : node.getSpecializations()) {
                if (specialization == null) {
                    continue;
                }
                if (specialization.isUncachedSpecialization()) {
                    continue;
                }
                if (specialization.isUnrolled()) {
                    continue;
                }

                for (CacheExpression cache : specialization.getCaches()) {
                    if (cache.isAlwaysInitialized()) {
                        continue;
                    }
                    if (cache.isCachedLibrary() && cache.getCachedLibraryLimit() == null) {
                        // can never be shared without a limit
                        continue;
                    }
                    if (specialization.isDynamicParameterBound(cache.getDefaultExpression(), true)) {
                        continue;
                    }

                    SharableCache sharable = new SharableCache(specialization, cache);
                    sharableCaches.computeIfAbsent(sharable, (c) -> new ArrayList<>()).add(cache);
                }
            }
        }

        return sharableCaches;
    }

    private static final class SharableCache {

        private final SpecializationData specialization;
        private final CacheExpression expression;
        private final int hashCode;

        SharableCache(SpecializationData specialization, CacheExpression expression) {
            this.specialization = specialization;
            this.expression = expression;
            this.hashCode = Objects.hash(expression.getParameter().getType(),
                            DSLExpressionHash.compute(expression.getDefaultExpression()),
                            DSLExpressionHash.compute(expression.getUncachedExpression()));
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SharableCache)) {
                return false;
            }
            SharableCache other = (SharableCache) obj;
            if (this == obj) {
                return true;
            } else if (ElementUtils.executableEquals(specialization.getMethod(), other.specialization.getMethod()) &&
                            ElementUtils.variableEquals(expression.getParameter().getVariableElement(), other.expression.getParameter().getVariableElement())) {
                return true;
            }
            String reason = equalsWithReasonImpl(other, false);
            if (reason == null) {
                if (hashCode != other.hashCode) {
                    throw new AssertionError();
                }
                return true;
            }
            return false;
        }

        private String equalsWithReasonImpl(SharableCache other, boolean generateMessage) {
            TypeMirror thisParametertype = expression.getParameter().getType();
            TypeMirror otherParametertype = other.expression.getParameter().getType();
            if (specialization == other.specialization) {
                if (!generateMessage) {
                    return "";
                }
                return "Cannot share caches within the same specialization.";
            }

            if (!ElementUtils.typeEquals(thisParametertype, otherParametertype)) {
                if (!generateMessage) {
                    return "";
                }
                return String.format("The cache parameter type does not match. Expected '%s' but was '%s'.",
                                ElementUtils.getSimpleName(thisParametertype),
                                ElementUtils.getSimpleName(otherParametertype));
            }
            if (!equalsExpression(expression.getDefaultExpression(), other.specialization, other.expression.getDefaultExpression())) {
                if (!generateMessage) {
                    return "";
                }
                return "The cache initializer does not match.";
            }
            if (!equalsExpression(expression.getUncachedExpression(), other.specialization, other.expression.getUncachedExpression())) {
                if (!generateMessage) {
                    return "";
                }
                return "The uncached initializer does not match.";
            }

            if (this.expression.isNeverDefault() != other.expression.isNeverDefault()) {
                if (!generateMessage) {
                    return "";
                }
                return String.format("The value for @%s(neverDefault=...) must be equal for all shared caches.",
                                getSimpleName(ProcessorContext.types().Cached));

            }

            boolean isInlined = this.expression.getInlinedNode() != null;
            boolean otherIsInlined = other.expression.getInlinedNode() != null;
            if (isInlined != otherIsInlined) {
                if (!generateMessage) {
                    return "";
                }
                return String.format("The value for @%s(inline=...) must be equal for all shared caches.",
                                getSimpleName(ProcessorContext.types().Cached));

            }

            return null;
        }

        String equalsWithReason(SharableCache other) {
            return equalsWithReasonImpl(other, true);
        }

        private boolean equalsExpression(DSLExpression thisExpression, SpecializationData otherSpecialization, DSLExpression otherExpression) {
            if (thisExpression == null && otherExpression == null) {
                return true;
            } else if (thisExpression == null || otherExpression == null) {
                return false;
            }

            List<DSLExpression> otherExpressions = thisExpression.flatten();
            List<DSLExpression> expressions = otherExpression.flatten();
            if (otherExpressions.size() != expressions.size()) {
                return false;
            }
            Iterator<DSLExpression> otherExpressionIterator = expressions.iterator();
            Iterator<DSLExpression> thisExpressionIterator = otherExpressions.iterator();
            while (otherExpressionIterator.hasNext()) {
                DSLExpression e1 = thisExpressionIterator.next();
                DSLExpression e2 = otherExpressionIterator.next();
                if (e1.getClass() != e2.getClass()) {
                    return false;
                } else if (e1 instanceof Variable) {
                    VariableElement var1 = ((Variable) e1).getResolvedVariable();
                    VariableElement var2 = ((Variable) e2).getResolvedVariable();

                    if (var1.getKind() == ElementKind.PARAMETER && var2.getKind() == ElementKind.PARAMETER) {
                        Parameter p1 = specialization.findByVariable(var1);
                        Parameter p2 = otherSpecialization.findByVariable(var2);
                        if (p1 != null && p2 != null) {
                            NodeExecutionData execution1 = p1.getSpecification().getExecution();
                            NodeExecutionData execution2 = p2.getSpecification().getExecution();
                            if (execution1 != null && execution2 != null && execution1.getIndex() == execution2.getIndex()) {
                                continue;
                            }
                        }
                    }
                    if (!ElementUtils.variableEquals(var1, var2)) {
                        return false;
                    }
                } else if (e1 instanceof Call) {
                    ExecutableElement var1 = ((Call) e1).getResolvedMethod();
                    ExecutableElement var2 = ((Call) e2).getResolvedMethod();
                    if (!ElementUtils.executableEquals(var1, var2)) {
                        return false;
                    }
                } else if (e1 instanceof Binary) {
                    String var1 = ((Binary) e1).getOperator();
                    String var2 = ((Binary) e2).getOperator();
                    if (!Objects.equals(var1, var2)) {
                        return false;
                    }
                } else if (e1 instanceof Negate) {
                    assert e2 instanceof Negate;
                    // nothing to do
                } else if (!e1.equals(e2)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        private static final class DSLExpressionHash implements DSLExpressionVisitor {
            private int hash = 1;

            public void visitCast(Cast binary) {
                hash *= binary.getCastType().hashCode();
            }

            public void visitVariable(Variable binary) {
                hash *= 31;
            }

            public void visitNegate(Negate negate) {
                hash *= 31;
            }

            public void visitIntLiteral(IntLiteral binary) {
                hash *= 31 + binary.getResolvedValueInt();
            }

            public void visitClassLiteral(ClassLiteral classLiteral) {
                hash *= 31 + Objects.hash(classLiteral.getResolvedType());
            }

            public void visitCall(Call binary) {
                hash *= 31 + Objects.hash(binary.getName());
            }

            public void visitBooleanLiteral(BooleanLiteral binary) {
                hash *= 31 + Objects.hash(binary.getLiteral());
            }

            public void visitBinary(Binary binary) {
                hash *= 31 + Objects.hash(binary.getOperator());
            }

            static int compute(DSLExpression e) {
                if (e == null) {
                    return 1;
                }
                DSLExpressionHash hash = new DSLExpressionHash();
                e.accept(hash);
                return hash.hash;
            }
        }

    }
}
