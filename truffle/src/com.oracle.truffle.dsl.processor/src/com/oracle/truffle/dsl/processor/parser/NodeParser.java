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
import com.oracle.truffle.dsl.processor.TruffleTypes;
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
import com.oracle.truffle.dsl.processor.expression.InvalidExpressionException;
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
import com.oracle.truffle.dsl.processor.model.MethodSpec;
import com.oracle.truffle.dsl.processor.model.NodeChildData;
import com.oracle.truffle.dsl.processor.model.NodeChildData.Cardinality;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.NodeFieldData;
import com.oracle.truffle.dsl.processor.model.Parameter;
import com.oracle.truffle.dsl.processor.model.ParameterSpec;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.model.SpecializationData.SpecializationKind;
import com.oracle.truffle.dsl.processor.model.SpecializationThrowsData;
import com.oracle.truffle.dsl.processor.model.TemplateMethod;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;

public final class NodeParser extends AbstractParser<NodeData> {

    public final List<DeclaredType> annotations = Arrays.asList(types.Fallback, types.TypeSystemReference,
                    types.Specialization,
                    types.NodeChild,
                    types.Executed,
                    types.NodeChildren,
                    types.ReportPolymorphism);

    public enum ParseMode {
        DEFAULT,
        EXPORTED_MESSAGE
    }

    private boolean nodeOnly;
    private final ParseMode mode;
    private final TypeMirror exportLibraryType;
    private final TypeElement exportDeclarationType;
    private final boolean substituteThisToParent;

    private final List<TypeMirror> cachedAnnotations;

    private NodeParser(ParseMode mode, TypeMirror exportLibraryType, TypeElement exportDeclarationType, boolean substituteThisToParent) {
        this.mode = mode;
        this.exportLibraryType = exportLibraryType;
        this.exportDeclarationType = exportDeclarationType;
        this.cachedAnnotations = getCachedAnnotations();
        this.substituteThisToParent = substituteThisToParent;
    }

    public static List<TypeMirror> getCachedAnnotations() {
        TruffleTypes types = ProcessorContext.getInstance().getTypes();
        return Arrays.asList(types.Cached, types.CachedLibrary, types.CachedContext, types.CachedLanguage, types.Bind);
    }

    public static NodeParser createExportParser(TypeMirror exportLibraryType, TypeElement exportDeclarationType, boolean substituteThisToParent) {
        return new NodeParser(ParseMode.EXPORTED_MESSAGE, exportLibraryType, exportDeclarationType, substituteThisToParent);
    }

    public static NodeParser createDefaultParser() {
        return new NodeParser(ParseMode.DEFAULT, null, null, false);
    }

    @Override
    protected NodeData parse(Element element, List<AnnotationMirror> mirror) {
        NodeData node = parseRootType((TypeElement) element);
        if (Log.isDebug() && node != null) {
            String dump = node.dump();
            log.message(Kind.ERROR, null, null, null, dump);
        }
        return node;
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
    public DeclaredType getAnnotationType() {
        return null;
    }

    @Override
    public List<DeclaredType> getTypeDelegatedAnnotationTypes() {
        return annotations;
    }

    private NodeData parseRootType(TypeElement rootType) {
        List<NodeData> enclosedNodes = new ArrayList<>();
        for (TypeElement enclosedType : ElementFilter.typesIn(rootType.getEnclosedElements())) {
            NodeData enclosedChild = parseRootType(enclosedType);
            if (enclosedChild != null) {
                enclosedNodes.add(enclosedChild);
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
            node = new NodeData(context, rootType);
        }

        if (node != null) {
            for (NodeData enclosedNode : enclosedNodes) {
                node.addEnclosedNode(enclosedNode);
            }
        }
        return node;
    }

    private NodeData parseNode(TypeElement originalTemplateType) {
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

        List<TypeElement> lookupTypes = collectSuperClasses(new ArrayList<TypeElement>(), templateType);

        NodeData node = parseNodeData(templateType, lookupTypes);

        List<Element> members = loadMembers(templateType);
        // ensure the processed element has at least one @Specialization annotation.
        if (!containsSpecializations(members)) {
            return null;
        }

        if (nodeOnly) {
            return node;
        }

        if (node.hasErrors()) {
            return node;
        }

        AnnotationMirror reflectable = findFirstAnnotation(lookupTypes, types.Introspectable);
        if (reflectable != null) {
            node.setReflectable(true);
        }

        AnnotationMirror reportPolymorphism = findFirstAnnotation(lookupTypes, types.ReportPolymorphism);
        AnnotationMirror excludePolymorphism = findFirstAnnotation(lookupTypes, types.ReportPolymorphism_Exclude);
        if (reportPolymorphism != null && excludePolymorphism == null) {
            node.setReportPolymorphism(true);
        }
        node.getFields().addAll(parseFields(lookupTypes, members));
        node.getChildren().addAll(parseChildren(node, lookupTypes, members));
        node.getChildExecutions().addAll(parseExecutions(node.getFields(), node.getChildren(), members));
        node.getExecutableTypes().addAll(parseExecutableTypeData(node, members, node.getSignatureSize(), context.getFrameTypes(), false));

        initializeExecutableTypes(node);
        initializeImportGuards(node, lookupTypes, members);
        initializeChildren(node);

        if (node.hasErrors()) {
            return node; // error sync point
        }

        node.getSpecializations().addAll(new SpecializationMethodParser(context, node, mode == ParseMode.EXPORTED_MESSAGE).parse(members));
        node.getSpecializations().addAll(new FallbackParser(context, node).parse(members));
        node.getCasts().addAll(new CreateCastParser(context, node).parse(members));

        if (node.hasErrors()) {
            return node;  // error sync point
        }
        initializeSpecializations(members, node);
        initializeExecutableTypeHierarchy(node);
        initializeReceiverBound(node);
        if (node.hasErrors()) {
            return node;  // error sync point
        }
        initializeUncachable(node);

        if (mode == ParseMode.DEFAULT) {
            boolean emitWarnings = Boolean.parseBoolean(System.getProperty("truffle.dsl.cacheSharingWarningsEnabled", "false"));
            node.setSharedCaches(computeSharing(node.getTemplateType(), Arrays.asList(node), emitWarnings));
        } else {
            // sharing is computed by the ExportsParser
        }

        verifySpecializationSameLength(node);
        verifyVisibilities(node);
        verifyMissingAbstractMethods(node, members);
        verifyConstructors(node);
        verifySpecializationThrows(node);
        verifyFrame(node);
        return node;
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
                            throw new AssertionError("Unexpected declared element for generated element: " + declaringElement.toString());
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
                            cache.addError(cache.getSharedGroupMirror(), cache.getSharedGroupValue(),
                                            "Could not find any other cached parameter that this parameter could be shared. " +
                                                            "Cached parameters are only sharable if they declare the same type and initializer expressions and if the specialization only has a single instance. " +
                                                            "Remove the @%s annotation or make the parameter sharable to resolve this.",
                                            types.Cached_Shared.asElement().getSimpleName().toString());
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
                            /*
                             * We only emit sharing warnings for the same declaring type, because
                             * otherwise sharing warnings might not be resolvable if the base type
                             * is not modifiable.
                             */
                            List<CacheExpression> declaredInExpression = new ArrayList<>();
                            for (CacheExpression expression : expressions) {
                                if (ElementUtils.isDeclaredIn(expression.getParameter().getVariableElement(), declaringElement)) {
                                    declaredInExpression.add(expression);
                                }
                            }
                            if (declaredInExpression.size() > 1 && findAnnotationMirror(cache.getParameter().getVariableElement(), types.Cached_Exclusive) == null) {
                                StringBuilder sharedCaches = new StringBuilder();
                                Set<String> recommendedGroups = new LinkedHashSet<>();
                                for (CacheExpression cacheExpression : declaredInExpression) {
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
                                cache.addWarning("The cached parameter may be shared with: %n%s Annotate the parameter with @%s(\"%s\") or @%s to allow or deny sharing of the parameter.",
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
                if (guard.getExpression().isNodeReceiverBound()) {
                    nodeBound = true;
                    if (requireNodeUnbound) {
                        guard.addError("@%s annotated nodes must only refer to static guard methods or fields. " +
                                        "Add a static modifier to the bound guard method or field to resolve this.",
                                        types.ExportMessage.asElement().getSimpleName().toString());
                    }
                    break;
                }
            }
            for (CacheExpression cache : specialization.getCaches()) {
                if (cache.getDefaultExpression() != null && !cache.isMergedLibrary() && cache.getDefaultExpression().isNodeReceiverBound()) {
                    nodeBound = true;
                    if (requireNodeUnbound) {
                        cache.addError("@%s annotated nodes must only refer to static cache initializer methods or fields. " +
                                        "Add a static modifier to the bound cache initializer method or field or " +
                                        "use the keyword 'this' to refer to the receiver type explicitely.",
                                        types.ExportMessage.asElement().getSimpleName().toString());
                    }
                    break;
                }
            }
            if (specialization.getLimitExpression() != null && specialization.getLimitExpression().isNodeReceiverBound()) {
                nodeBound = true;
                if (requireNodeUnbound) {
                    specialization.addError("@%s annotated nodes must only refer to static limit initializer methods or fields. " +
                                    "Add a static modifier to the bound cache initializer method or field or " +
                                    "use the keyword 'this' to refer to the receiver type explicitely.",
                                    types.ExportMessage.asElement().getSimpleName().toString());
                }
                break;
            }
        }
        node.setNodeBound(nodeBound);
    }

    private void initializeUncachable(NodeData node) {
        AnnotationMirror generateUncached = findAnnotationMirror(node.getTemplateType().getAnnotationMirrors(), types.GenerateUncached);

        boolean requireUncachable = node.isGenerateUncached();
        boolean uncachable = true;
        TypeElement type = node.getTemplateType();
        while (type != null) {
            if (ElementUtils.typeEquals(type.asType(), types.Node)) {
                // don't care about fields in node.
                break;
            }
            for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
                if (field.getModifiers().contains(Modifier.STATIC)) {
                    continue;
                } else if (typeEquals(field.getEnclosingElement().asType(), types.Node)) {
                    // ignore fields in Node. They are safe.
                    continue;
                }
                // uncachable because local non-static fields are not allowed
                uncachable = false;

                if (requireUncachable) {
                    node.addError(generateUncached, null, "Failed to generate code for @%s: The node must not declare any instance variables. " +
                                    "Found instance variable %s.%s. Remove instance variable to resolve this.",
                                    types.GenerateUncached.asElement().getSimpleName().toString(),
                                    getSimpleName(field.getEnclosingElement().asType()), field.getSimpleName().toString());
                }
                break;
            }
            type = ElementUtils.getSuperType(type);
        }

        for (SpecializationData specialization : node.computeUncachedSpecializations(node.getSpecializations())) {
            if (!specialization.isReachable()) {
                continue;
            }
            for (CacheExpression cache : specialization.getCaches()) {
                if (cache.getUncachedExpression() == null) {
                    uncachable = false;
                    if (requireUncachable) {
                        cache.addError("Failed to generate code for @%s: The specialization uses @%s without valid uncached expression. %s. " +
                                        "To resolve this specify the uncached or allowUncached attribute in @%s.",
                                        types.GenerateUncached.asElement().getSimpleName().toString(),
                                        types.Cached.asElement().getSimpleName().toString(),
                                        cache.getUncachedExpresionError() != null ? cache.getUncachedExpresionError().getText() : "",
                                        types.Cached.asElement().getSimpleName().toString());
                    }
                    break;
                }
            }

            for (GuardExpression guard : specialization.getGuards()) {
                if (guard.getExpression().isNodeReceiverBound()) {
                    uncachable = false;
                    if (requireUncachable) {
                        guard.addError("Failed to generate code for @%s: One of the guards bind non-static methods or fields . " +
                                        "Add a static modifier to the bound guard method or field to resolve this.", types.GenerateUncached.asElement().getSimpleName().toString());
                    }
                    break;
                }
            }
            if (!specialization.getExceptions().isEmpty()) {
                uncachable = false;
                if (requireUncachable) {
                    specialization.addError(getAnnotationValue(specialization.getMarkerAnnotation(), "rewriteOn"),
                                    "Failed to generate code for @%s: The specialization rewrites on exceptions and there is no specialization that replaces it. " +
                                                    "Add a replaces=\"%s\" class to specialization below to resolve this problem.",
                                    types.GenerateUncached.asElement().getSimpleName().toString(), specialization.getMethodName());
                }
            }

        }
        List<ExecutableTypeData> validExecutableType = new ArrayList<>();
        for (ExecutableTypeData executableType : node.getExecutableTypes()) {
            if (executableType.getMethod() != null && executableType.getEvaluatedCount() >= node.getExecutionCount()) {
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
                                node.getExecutionCount(),
                                node.getExecutionCount());
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
                    if (cache.isGuardForNull()) {
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
    }

    private static void initializeExecutableTypeHierarchy(NodeData node) {
        SpecializationData polymorphic = node.getPolymorphicSpecialization();
        if (polymorphic != null) {
            boolean polymorphicSignatureFound = false;
            List<TypeMirror> dynamicTypes = polymorphic.getDynamicTypes();
            TypeMirror frame = null;
            if (polymorphic.getFrame() != null) {
                frame = dynamicTypes.remove(0);
            }

            ExecutableTypeData polymorphicType = new ExecutableTypeData(node, polymorphic.getReturnType().getType(), "execute", frame, dynamicTypes);
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
        }

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

    private List<Element> loadMembers(TypeElement templateType) {
        List<Element> elements = newElementList(CompilerFactory.getCompiler(templateType).getAllMembersInDeclarationOrder(context.getEnvironment(), templateType));
        Iterator<Element> elementIterator = elements.iterator();
        while (elementIterator.hasNext()) {
            Element element = elementIterator.next();
            // not interested in methods of Node
            if (typeEquals(element.getEnclosingElement().asType(), types.Node)) {
                elementIterator.remove();
            }
            // not interested in methods of Object
            if (typeEquals(element.getEnclosingElement().asType(), context.getType(Object.class))) {
                elementIterator.remove();
            }
        }
        return elements;
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

    private void initializeImportGuards(NodeData node, List<TypeElement> lookupTypes, List<Element> elements) {
        for (TypeElement lookupType : lookupTypes) {
            AnnotationMirror importAnnotation = findAnnotationMirror(lookupType, types.ImportStatic);
            if (importAnnotation == null) {
                continue;
            }
            AnnotationValue importClassesValue = getAnnotationValue(importAnnotation, "value");
            List<TypeMirror> importClasses = getAnnotationValueList(TypeMirror.class, importAnnotation, "value");
            if (importClasses.isEmpty()) {
                node.addError(importAnnotation, importClassesValue, "At least one import class must be specified.");
                continue;
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
    }

    private static class ImportsKey {

        private final TypeElement relativeTo;
        private final TypeElement importGuardsClass;
        private final boolean includeConstructors;

        ImportsKey(TypeElement relativeTo, TypeElement importGuardsClass, boolean includeConstructors) {
            this.relativeTo = relativeTo;
            this.importGuardsClass = importGuardsClass;
            this.includeConstructors = includeConstructors;
        }

        @Override
        public int hashCode() {
            return Objects.hash(relativeTo, importGuardsClass, includeConstructors);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ImportsKey) {
                ImportsKey other = (ImportsKey) obj;
                return Objects.equals(relativeTo, other.relativeTo) && Objects.equals(importGuardsClass, other.importGuardsClass) && Objects.equals(includeConstructors, other.includeConstructors);
            }
            return false;
        }

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
        List<? extends Element> importMembers = context.getEnvironment().getElementUtils().getAllMembers(importType);
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
            typeSystem = (TypeSystemData) context.getTemplate(typeSystemType, true);
            if (typeSystem == null) {
                NodeData nodeData = new NodeData(context, templateType);
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
        return new NodeData(context, templateType, typeSystem, useNodeFactory, generateUncached);

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
                NodeChildData child = new NodeChildData(field, executed, name, type, type, field, cardinality, executedWith);
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
                NodeChildData nodeChild = new NodeChildData(type, childMirror, name, childNodeType, originalChildType, getter, cardinality, executeWith);

                nodeChildren.add(nodeChild);

                if (nodeChild.getNodeType() == null) {
                    nodeChild.addError("No valid node type could be resoleved.");
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

    private List<NodeExecutionData> parseExecutions(List<NodeFieldData> fields, List<NodeChildData> children, List<? extends Element> elements) {
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
        for (ExecutableElement method : methods) {
            AnnotationMirror mirror = findAnnotationMirror(method, types.Specialization);
            if (mirror == null) {
                continue;
            }
            int currentArgumentIndex = 0;
            parameter: for (VariableElement var : method.getParameters()) {
                TypeMirror type = var.asType();
                if (currentArgumentIndex == 0) {
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

            if (!method.getSimpleName().toString().startsWith("execute")) {
                continue;
            }
            if (findAnnotationMirror(method, types.Specialization) != null) {
                continue;
            }
            boolean ignoreUnexpected = mode == ParseMode.EXPORTED_MESSAGE;
            ExecutableTypeData executableType = new ExecutableTypeData(node, method, signatureSize, context.getFrameTypes(), ignoreUnexpected);

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

        if (!requireNodeChildDeclarations.isEmpty()) {
            node.addError("Not enough child node declarations found. Please annotate the node class with addtional @NodeChild annotations or remove all execute methods that do not provide all evaluated values. " +
                            "The following execute methods do not provide all evaluated values for the expected signature size %s: %s.", executions.size(), requireNodeChildDeclarations);
        }

        if (nodeChildDeclarations > 0 && executions.size() == node.getMinimalEvaluatedParameters()) {
            for (NodeChildData child : node.getChildren()) {
                child.addError("Unnecessary @NodeChild declaration. All evaluated child values are provided as parameters in execute methods.");
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

        List<TypeElement> lookupTypes = collectSuperClasses(new ArrayList<TypeElement>(), templateType);

        // Declaration order is not required for child nodes.
        List<? extends Element> members = processingEnv.getElementUtils().getAllMembers(templateType);
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

    private void initializeSpecializations(List<? extends Element> elements, final NodeData node) {
        if (node.getSpecializations().isEmpty()) {
            return;
        }

        initializeReplaces(node);
        resolveReplaces(node);
        initializeExpressions(elements, node);

        if (node.hasErrors()) {
            return;
        }

        initializeGeneric(node);
        initializeUninitialized(node);
        initializeOrder(node);
        initializePolymorphism(node); // requires specializations
        initializeReachability(node);
        initializeFallbackReachability(node);
        initializeCheckedExceptions(node);

        List<SpecializationData> specializations = node.getSpecializations();
        for (SpecializationData cur : specializations) {
            for (SpecializationData contained : cur.getReplaces()) {
                if (contained != cur) {
                    contained.getExcludedBy().add(cur);
                }
            }
        }

        initializeSpecializationIdsWithMethodNames(node.getSpecializations());
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

    private static void initializeReplaces(NodeData node) {
        for (SpecializationData specialization : node.getSpecializations()) {
            Set<SpecializationData> resolvedSpecializations = specialization.getReplaces();
            Set<String> includeNames = specialization.getReplacesNames();
            for (String includeName : includeNames) {
                // TODO reduce complexity of this lookup.
                List<SpecializationData> foundSpecializations = lookupSpecialization(node, includeName);

                AnnotationValue value = getAnnotationValue(specialization.getMarkerAnnotation(), "replaces");
                if (foundSpecializations.isEmpty()) {
                    specialization.addError(value, "The referenced specialization '%s' could not be found.", includeName);
                } else {
                    resolvedSpecializations.addAll(foundSpecializations);
                    for (SpecializationData foundSpecialization : foundSpecializations) {
                        if (foundSpecialization.compareTo(specialization) > 0) {
                            specialization.addError(value, "The replaced specialization '%s' must be declared before the replacing specialization.", includeName);
                        }
                    }
                }
            }
        }
    }

    private void resolveReplaces(NodeData node) {
        // flatten transitive includes
        for (SpecializationData specialization : node.getSpecializations()) {
            if (specialization.getReplaces().isEmpty()) {
                continue;
            }

            for (SpecializationData replaced : specialization.getReplaces()) {
                replaced.setReplaced(true);
            }

            Set<SpecializationData> foundSpecializations = new HashSet<>();
            collectIncludes(specialization, foundSpecializations, new HashSet<SpecializationData>());
            specialization.getReplaces().addAll(foundSpecializations);
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

    private void collectIncludes(SpecializationData specialization, Set<SpecializationData> found, Set<SpecializationData> visited) {
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
            if (current.isPolymorphic()) {
                current.setReachable(true);
                continue;
            }

            List<SpecializationData> shadowedBy = null;
            for (int j = i - 1; j >= 0; j--) {
                SpecializationData prev = specializations.get(j);
                if (prev.isPolymorphic()) {
                    continue;
                }
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
    }

    private static void initializeSpecializationIdsWithMethodNames(List<SpecializationData> specializations) {
        List<String> signatures = new ArrayList<>();
        for (SpecializationData specialization : specializations) {
            if (specialization.isFallback()) {
                signatures.add("Fallback");
            } else if (specialization.isUninitialized()) {
                signatures.add("Uninitialized");
            } else if (specialization.isPolymorphic()) {
                signatures.add("Polymorphic");
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

    private void initializeExpressions(List<? extends Element> elements, NodeData node) {
        List<? extends Element> members = elements;

        List<VariableElement> fields = new ArrayList<>();
        for (NodeFieldData field : node.getFields()) {
            fields.add(field.getVariable());
        }

        List<Element> globalMembers = new ArrayList<>(members.size() + fields.size());
        globalMembers.addAll(fields);
        globalMembers.addAll(members);
        DSLExpressionResolver originalResolver = new DSLExpressionResolver(context, node.getTemplateType(), globalMembers);

        // the number of specializations might grow while expressions are initialized.
        List<SpecializationData> specializations = node.getSpecializations();
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

    }

    private void initializeAssumptions(SpecializationData specialization, DSLExpressionResolver resolver) {
        final DeclaredType assumptionType = types.Assumption;
        final TypeMirror assumptionArrayType = new ArrayCodeTypeMirror(assumptionType);
        final List<String> assumptionDefinitions = getAnnotationValueList(String.class, specialization.getMarkerAnnotation(), "assumptions");
        List<AssumptionExpression> assumptionExpressions = new ArrayList<>();
        int assumptionId = 0;
        for (String assumption : assumptionDefinitions) {
            AssumptionExpression assumptionExpression;
            DSLExpression expression = null;
            try {
                expression = DSLExpression.parse(assumption);
                expression.accept(resolver);
                assumptionExpression = new AssumptionExpression(specialization, expression, "assumption" + assumptionId);
                if (!isAssignable(expression.getResolvedType(), assumptionType) && !isAssignable(expression.getResolvedType(), assumptionArrayType)) {
                    assumptionExpression.addError("Incompatible return type %s. Assumptions must be assignable to %s or %s.", getSimpleName(expression.getResolvedType()),
                                    getSimpleName(assumptionType), getSimpleName(assumptionArrayType));
                }
                if (specialization.isDynamicParameterBound(expression, true)) {
                    specialization.addError("Assumption expressions must not bind dynamic parameter values.");
                }
            } catch (InvalidExpressionException e) {
                assumptionExpression = new AssumptionExpression(specialization, null, "assumption" + assumptionId);
                assumptionExpression.addError("Error parsing expression '%s': %s", assumption, e.getMessage());
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
                specialization.addWarning(annotationValue, "The limit expression has no effect. Multiple specialization instantiations are impossible for this specialization.");
            }
            return;
        }

        TypeMirror expectedType = context.getType(int.class);
        try {
            DSLExpression expression = DSLExpression.parse(limitValue);
            expression.accept(resolver);
            if (!typeEquals(expression.getResolvedType(), expectedType)) {
                specialization.addError(annotationValue, "Incompatible return type %s. Limit expressions must return %s.", getSimpleName(expression.getResolvedType()),
                                getSimpleName(expectedType));
            }
            if (specialization.isDynamicParameterBound(expression, true)) {
                specialization.addError(annotationValue, "Limit expressions must not bind dynamic parameter values.");
            }

            specialization.setLimitExpression(expression);
        } catch (InvalidExpressionException e) {
            specialization.addError(annotationValue, "Error parsing expression '%s': %s", limitValue, e.getMessage());
        }
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
                    resolveCachedExpression(resolver, cache, weakType, newWeakReference, null);

                    CacheExpression weakCache = new CacheExpression(weakParameter, foundCached);
                    weakCache.setDefaultExpression(newWeakReference);
                    weakCache.setUncachedExpression(newWeakReference);
                    weakCache.setWeakReference(true);

                    caches.add(0, weakCache);

                    DSLExpressionResolver weakResolver = resolver.copy(Arrays.asList());
                    weakResolver.addVariable(weakName, weakVariable);
                    specialization.addParameter(specialization.getParameters().size(), weakParameter);

                    DSLExpression parsedDefaultExpression = parseCachedExpression(weakResolver, cache, parameter.getType(), weakName + ".get()");
                    cache.setDefaultExpression(parsedDefaultExpression);
                    cache.setUncachedExpression(sourceExpression);
                    cache.setAlwaysInitialized(true);
                    cache.setGuardForNull(true);
                } else {
                    parseCached(cache, specialization, resolver, parameter);
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
                                        types.CachedLibrary.asElement().getSimpleName().toString(),
                                        types.CachedLibrary.asElement().getSimpleName().toString(),
                                        types.CachedLibrary.asElement().getSimpleName().toString(),
                                        types.CachedLibrary.asElement().getSimpleName().toString());
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
                    DSLExpression defaultExpression = new DSLExpression.Call(resolveCall, "createDispatched",
                                    Arrays.asList(limitExpression));
                    DSLExpression uncachedExpression = new DSLExpression.Call(resolveCall, "getUncached",
                                    Arrays.asList());

                    cache.setDefaultExpression(resolveCachedExpression(cachedResolver, cache, libraryType, defaultExpression, null));
                    cache.setUncachedExpression(resolveCachedExpression(cachedResolver, cache, libraryType, uncachedExpression, null));
                } else {
                    if (limit != null) {
                        cache.addError("The limit and specialized value expression cannot be specified at the same time. They are mutually exclusive.");
                        continue parameters;
                    }
                    cachedLibraries.add(cache);
                }
            } else if (cache.isCachedLanguage()) {
                TypeMirror languageType = cache.getParameter().getType();

                boolean isLanguage = ElementUtils.isAssignable(languageType, types.TruffleLanguage);
                boolean isLanguageReference = ElementUtils.isAssignable(languageType, types.TruffleLanguage_LanguageReference);

                if (!isLanguage && !isLanguageReference) {
                    cache.addError("Invalid @%s specification. The parameter type must be a subtype of %s or of type LanguageReference<%s>.",
                                    types.CachedLanguage.asElement().getSimpleName().toString(),
                                    types.TruffleLanguage.asElement().getSimpleName().toString(),
                                    types.TruffleLanguage.asElement().getSimpleName().toString());
                    continue parameters;
                }

                TypeMirror supplierType;
                if (isLanguageReference) {
                    TypeMirror typeArgument = getFirstTypeArgument(languageType);
                    if (typeArgument == null || !ElementUtils.isAssignable(typeArgument, types.TruffleLanguage)) {
                        cache.addError("Invalid @%s specification. The first type argument of the LanguageReference must be a subtype of '%s'.",
                                        types.CachedLanguage.asElement().getSimpleName().toString(),
                                        types.TruffleLanguage.asElement().getSimpleName().toString());
                    } else {
                        verifyLanguageType(types.CachedLanguage, cache, typeArgument);
                    }
                    supplierType = languageType;
                    languageType = typeArgument;
                } else {
                    verifyLanguageType(types.CachedLanguage, cache, languageType);
                    supplierType = new CodeTypeMirror.DeclaredCodeTypeMirror(context.getTypeElement(types.TruffleLanguage_LanguageReference), Arrays.asList(languageType));
                }
                if (cache.hasErrors()) {
                    continue parameters;
                }
                String fieldName = ElementUtils.firstLetterLowerCase(ElementUtils.getSimpleName(languageType)) + "Reference_";
                CodeVariableElement variableElement = new CodeVariableElement(supplierType, fieldName);
                List<? extends Element> elements = Arrays.asList(variableElement);
                DSLExpressionResolver localResolver = resolver.copy(elements);
                DSLExpression accessReference = new DSLExpression.Variable(null, "null");
                cache.setReferenceType(supplierType);
                cache.setLanguageType(languageType);
                cache.setDefaultExpression(resolveCachedExpression(localResolver, cache, null, accessReference, null));
                cache.setUncachedExpression(resolveCachedExpression(localResolver, cache, null, accessReference, null));
                cache.setAlwaysInitialized(true);
            } else if (cache.isCachedContext()) {
                AnnotationMirror cachedContext = cache.getMessageAnnotation();
                TypeMirror languageType = ElementUtils.getAnnotationValue(TypeMirror.class, cachedContext, "value");
                if (!ElementUtils.isAssignable(languageType, languageType)) {
                    cache.addError("Invalid @%s specification. The value type must be a subtype of %s.",
                                    types.CachedContext.asElement().getSimpleName().toString(),
                                    types.TruffleLanguage.asElement().getSimpleName().toString());
                    continue parameters;
                }
                verifyLanguageType(types.CachedContext, cache, languageType);
                if (cache.hasErrors()) {
                    continue parameters;
                }
                TypeMirror contextType = null;
                TypeElement languageTypeElement = ElementUtils.fromTypeMirror(languageType);
                TypeMirror superType = languageTypeElement.getSuperclass();
                while (languageTypeElement != null) {
                    superType = languageTypeElement.getSuperclass();
                    languageTypeElement = ElementUtils.fromTypeMirror(superType);
                    if (ElementUtils.elementEquals(context.getTypeElement(types.TruffleLanguage), languageTypeElement)) {
                        contextType = getFirstTypeArgument(superType);
                        break;
                    }
                }
                if (contextType == null || contextType.getKind() != TypeKind.DECLARED) {
                    cache.addError("Invalid @%s specification. The context type could not be inferred from super type '%s' in language '%s'.",
                                    types.CachedContext.asElement().getSimpleName().toString(),
                                    ElementUtils.getSimpleName(superType),
                                    ElementUtils.getSimpleName(languageType));
                    continue parameters;
                }

                TypeMirror declaredContextType = parameter.getType();
                if (ElementUtils.typeEquals(ElementUtils.eraseGenericTypes(parameter.getType()), ElementUtils.eraseGenericTypes(types.TruffleLanguage_ContextReference))) {
                    declaredContextType = getFirstTypeArgument(parameter.getType());
                }

                if (!ElementUtils.typeEquals(contextType, declaredContextType)) {
                    cache.addError("Invalid @%s specification. The parameter type must match the context type '%s' or 'ContextReference<%s>'.",
                                    types.CachedContext.asElement().getSimpleName().toString(),
                                    ElementUtils.getSimpleName(contextType),
                                    ElementUtils.getSimpleName(contextType));
                    continue parameters;
                }
                TypeMirror referenceType = new CodeTypeMirror.DeclaredCodeTypeMirror(context.getTypeElement(types.TruffleLanguage_ContextReference), Arrays.asList(contextType));

                DSLExpression accessReference = new DSLExpression.Variable(null, "null");
                cache.setReferenceType(referenceType);
                cache.setLanguageType(languageType);
                cache.setDefaultExpression(resolveCachedExpression(resolver, cache, null, accessReference, null));
                cache.setUncachedExpression(resolveCachedExpression(resolver, cache, null, accessReference, null));
                cache.setAlwaysInitialized(true);
            } else if (cache.isBind()) {
                AnnotationMirror dynamic = cache.getMessageAnnotation();
                String expression = ElementUtils.getAnnotationValue(String.class, dynamic, "value", false);

                DSLExpression parsedExpression = parseCachedExpression(resolver, cache, parameter.getType(), expression);
                cache.setDefaultExpression(parsedExpression);
                cache.setUncachedExpression(parsedExpression);
                cache.setAlwaysInitialized(true);
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

    private static TypeMirror getFirstTypeArgument(TypeMirror languageType) {
        for (TypeMirror currentTypeArgument : ((DeclaredType) languageType).getTypeArguments()) {
            return currentTypeArgument;
        }
        return null;
    }

    private void verifyLanguageType(DeclaredType annotationType, CacheExpression cache, TypeMirror languageType) {
        if (ElementUtils.typeEquals(types.HostLanguage, languageType)) {
            // allowed without Registration annotation
            return;
        }

        AnnotationMirror registration = ElementUtils.findAnnotationMirror(ElementUtils.fromTypeMirror(languageType), types.TruffleLanguage_Registration);
        if (registration == null) {
            cache.addError("Invalid @%s specification. The type '%s' is not a valid language type. Valid language types must be annotated with @%s.",
                            annotationType.asElement().getSimpleName().toString(),
                            ElementUtils.getSimpleName(ElementUtils.eraseGenericTypes(languageType)),
                            types.TruffleLanguage_Registration.asElement().getSimpleName().toString());
        }
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
            uncachedSpecialization.getReplaces().add(specialization);

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

            LibraryParser parser = new LibraryParser();
            LibraryData parsedLibrary = parser.parse(type);
            if (parsedLibrary == null || parsedLibrary.hasErrors()) {
                cachedLibrary.addError("Library '%s' has errors. Please resolve them first.", getSimpleName(parameterType));
                continue;
            }
            String expression = cachedLibrary.getCachedLibraryExpression();
            DSLExpression receiverExpression = parseCachedExpression(resolver, cachedLibrary, parsedLibrary.getSignatureReceiverType(), expression);
            if (receiverExpression == null) {
                continue;
            }
            DSLExpression substituteCachedExpression = null;
            DSLExpression substituteUncachedExpression = null;

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
                continue;
            } else {
                seenDynamicParameterBound |= specialization.isDynamicParameterBound(receiverExpression, false);
                cachedLibrary.setDefaultExpression(receiverExpression);

                String receiverName = cachedLibrary.getParameter().getVariableElement().getSimpleName().toString();
                DSLExpression acceptGuard = new DSLExpression.Call(new DSLExpression.Variable(null, receiverName), "accepts",
                                Arrays.asList(receiverExpression));
                acceptGuard = resolveCachedExpression(resolver, cachedLibrary, context.getType(boolean.class), acceptGuard, expression);
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
                defaultExpression = resolveCachedExpression(cachedResolver, cachedLibrary, libraryType, defaultExpression, expression);
                cachedLibrary.setDefaultExpression(defaultExpression);

                DSLExpression uncachedExpression = new DSLExpression.Call(resolveCall, "getUncached",
                                Arrays.asList(receiverExpression));
                cachedLibrary.setUncachedExpression(uncachedExpression);

                uncachedExpression = resolveCachedExpression(cachedResolver, cachedLibrary, libraryType, uncachedExpression, expression);

                if (uncachedLibrary != null) {
                    uncachedLibrary.setDefaultExpression(uncachedExpression);
                    uncachedLibrary.setUncachedExpression(uncachedExpression);

                    uncachedLibrary.setAlwaysInitialized(true);
                    uncachedLibrary.setRequiresBoundary(true);
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
        AnnotationValue adopt = null;
        if (!cache.hasErrors()) {
            adopt = getAnnotationValue(cachedAnnotation, "adopt", false);
            AnnotationMirror cached = findAnnotationMirror(cache.getParameter().getVariableElement(), types.Cached);
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

        List<String> expressionParameters = getAnnotationValueList(String.class, cachedAnnotation, "parameters");

        String initializer = getAnnotationValue(String.class, cachedAnnotation, "value");
        String uncached = getAnnotationValue(String.class, cachedAnnotation, "uncached");

        String parameters = "";
        if (!expressionParameters.isEmpty()) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < expressionParameters.size(); i++) {
                String param = expressionParameters.get(i);
                b.append(param);
                if (i != 0) {
                    b.append(", ");
                }
            }
            parameters = b.toString();
        }

        initializer = initializer.replace("$parameters", parameters);
        uncached = uncached.replace("$parameters", parameters);

        if (ElementUtils.isAssignable(parameter.getType(), types.Library) && !ElementUtils.typeEquals(parameter.getType(), types.Library)) {
            cache.addError("The use of @%s is not supported for libraries. Use @%s instead.",
                            types.Cached.asElement().getSimpleName().toString(), types.CachedLibrary.asElement().getSimpleName().toString());
        } else if (NodeCodeGenerator.isSpecializedNode(parameter.getType())) {
            // if it is a node try to parse with the node parser to find out whether we
            // should may use the generated create and getUncached methods.
            NodeParser parser = NodeParser.createDefaultParser();
            parser.nodeOnly = true; // make sure we cannot have cycles
            TypeElement element = ElementUtils.castTypeElement(parameter.getType());
            if (!nodeOnly) {
                NodeData parsedNode = parser.parse(element);
                if (parsedNode != null) {
                    List<CodeExecutableElement> executables = NodeFactoryFactory.createFactoryMethods(parsedNode, ElementFilter.constructorsIn(element.getEnclosedElements()));
                    TypeElement type = ElementUtils.castTypeElement(NodeCodeGenerator.factoryOrNodeType(parsedNode));
                    for (CodeExecutableElement executableElement : executables) {
                        executableElement.setEnclosingElement(type);
                    }
                    resolver = resolver.copy(executables);
                }
            }
        }

        if (!cache.hasErrors()) {
            cache.setDefaultExpression(parseCachedExpression(resolver, cache, parameter.getType(), initializer));
        }
        boolean requireUncached = specialization.getNode().isGenerateUncached() || mode == ParseMode.EXPORTED_MESSAGE;
        if (cache.hasErrors()) {
            return; // error sync point
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
        }

        if (requireUncached && cache.getUncachedExpression() == null && cache.getDefaultExpression() != null) {
            if (specialization.isTrivialExpression(cache.getDefaultExpression())) {
                cache.setUncachedExpression(cache.getDefaultExpression());
            }
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
    }

    private DSLExpression resolveCachedExpression(DSLExpressionResolver resolver, CacheExpression cache, TypeMirror targetType, DSLExpression expression, String originalString) {
        DSLExpressionResolver localResolver = targetType == null ? resolver : importStatics(resolver, targetType);
        try {
            expression.accept(localResolver);
        } catch (InvalidExpressionException e) {
            cache.addError("Error parsing expression '%s': %s", originalString, e.getMessage());
            return null;
        }

        if (targetType == null || isAssignable(expression.getResolvedType(), targetType)) {
            return expression;
        } else {
            cache.addError("Incompatible return type %s. The expression type must be equal to the parameter type %s.", getSimpleName(expression.getResolvedType()),
                            getSimpleName(targetType));
            return null;
        }
    }

    private DSLExpressionResolver importStatics(DSLExpressionResolver resolver, TypeMirror targetType) {
        DSLExpressionResolver localResolver = resolver;
        if (targetType.getKind() == TypeKind.DECLARED) {
            List<Element> prefixedImports = importVisibleStaticMembersImpl(resolver.getAccessType(), fromTypeMirror(targetType), true);
            localResolver = localResolver.copy(prefixedImports);
        }
        return localResolver;
    }

    private DSLExpression parseCachedExpression(DSLExpressionResolver resolver, CacheExpression cache, TypeMirror targetType, String string) {
        try {
            return resolveCachedExpression(resolver, cache, targetType, DSLExpression.parse(string), string);
        } catch (InvalidExpressionException e) {
            cache.addError("Error parsing expression '%s': %s", string, e.getMessage());
            return null;
        }
    }

    private void initializeGuards(SpecializationData specialization, DSLExpressionResolver resolver) {
        List<String> guardDefinitions = getAnnotationValueList(String.class, specialization.getMarkerAnnotation(), "guards");

        Set<CacheExpression> handledCaches = new HashSet<>();
        List<GuardExpression> guards = new ArrayList<>();
        for (String guardExpression : guardDefinitions) {
            GuardExpression guard = parseGuard(resolver, specialization, guardExpression);

            if (guard.getExpression() != null) {
                Set<CacheExpression> caches = specialization.getBoundCaches(guard.getExpression(), false);
                for (CacheExpression cache : caches) {
                    if (handledCaches.contains(cache)) {
                        continue;
                    }
                    if (cache.isGuardForNull()) {
                        guards.add(createWeakReferenceGuard(resolver, specialization, cache));
                    }
                }
                handledCaches.addAll(caches);
            }

            guards.add(guard);
        }
        for (CacheExpression cache : specialization.getCaches()) {
            if (cache.isGuardForNull()) {
                if (handledCaches.contains(cache)) {
                    continue;
                }
                guards.add(createWeakReferenceGuard(resolver, specialization, cache));
            }
        }

        specialization.getGuards().addAll(guards);
    }

    private GuardExpression createWeakReferenceGuard(DSLExpressionResolver resolver, SpecializationData specialization, CacheExpression cache) {
        GuardExpression guard = parseGuard(resolver, specialization, cache.getParameter().getLocalName() + " != null");
        guard.setWeakReferenceGuard(true);
        return guard;
    }

    private GuardExpression parseGuard(DSLExpressionResolver resolver, SpecializationData specialization, String guard) {
        final TypeMirror booleanType = context.getType(boolean.class);
        GuardExpression guardExpression;
        DSLExpression expression;
        try {
            expression = DSLExpression.parse(guard);
            expression.accept(resolver);
            guardExpression = new GuardExpression(specialization, expression);
            if (!typeEquals(expression.getResolvedType(), booleanType)) {
                guardExpression.addError("Incompatible return type %s. Guards must return %s.", getSimpleName(expression.getResolvedType()), getSimpleName(booleanType));
            }
        } catch (InvalidExpressionException e) {
            guardExpression = new GuardExpression(specialization, null);
            guardExpression.addError("Error parsing expression '%s': %s", guard, e.getMessage());
        }
        return guardExpression;
    }

    private void initializeGeneric(final NodeData node) {
        List<SpecializationData> generics = new ArrayList<>();
        for (SpecializationData spec : node.getSpecializations()) {
            if (spec.isFallback()) {
                generics.add(spec);
            }
        }

        if (generics.size() == 1 && node.getSpecializations().size() == 1) {
            // TODO this limitation should be lifted
            for (SpecializationData generic : generics) {
                generic.addError("@%s defined but no @%s.", types.Fallback.asElement().getSimpleName().toString(), types.Specialization.asElement().getSimpleName().toString());
            }
        }

        if (generics.isEmpty()) {
            node.getSpecializations().add(createGenericSpecialization(node));
        } else {
            if (generics.size() > 1) {
                for (SpecializationData generic : generics) {
                    generic.addError("Only one @%s is allowed per operation.", types.Fallback.asElement().getSimpleName().toString());
                }
            }
        }
    }

    private SpecializationData createGenericSpecialization(final NodeData node) {
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

    private static void initializeUninitialized(final NodeData node) {
        SpecializationData generic = node.getGenericSpecialization();
        if (generic == null) {
            return;
        }
        TemplateMethod uninializedMethod = new TemplateMethod("Uninitialized", -1, node, generic.getSpecification(), null, null, generic.getReturnType(), generic.getParameters());
        // should not use messages from generic specialization
        uninializedMethod.getMessages().clear();
        node.getSpecializations().add(new SpecializationData(node, uninializedMethod, SpecializationKind.UNINITIALIZED));
    }

    private void initializePolymorphism(NodeData node) {

        SpecializationData generic = node.getGenericSpecialization();
        List<VariableElement> foundTypes = new ArrayList<>();

        Collection<TypeMirror> frameTypes = new HashSet<>();
        for (SpecializationData specialization : node.getSpecializations()) {
            if (specialization.getFrame() != null) {
                frameTypes.add(specialization.getFrame().getType());
            }
        }
        if (node.supportsFrame()) {
            frameTypes.add(node.getFrameType());
        }

        if (!frameTypes.isEmpty()) {
            frameTypes = uniqueSortedTypes(frameTypes, false);
            TypeMirror frameType;
            if (frameTypes.size() == 1) {
                frameType = frameTypes.iterator().next();
            } else {
                frameType = types.Frame;
            }
            foundTypes.add(new CodeVariableElement(frameType, TemplateMethod.FRAME_NAME));
        }

        TypeMirror returnType = null;
        int index = 0;
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
                    if (specialization.isUninitialized()) {
                        continue;
                    }
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
                foundTypes.add(new CodeVariableElement(polymorphicType, "param" + index));
            }
            index++;
        }

        SpecializationMethodParser parser = new SpecializationMethodParser(context, node, mode == ParseMode.EXPORTED_MESSAGE);
        SpecializationData polymorphic = parser.create("Polymorphic", TemplateMethod.NO_NATURAL_ORDER, null, null, returnType, foundTypes);
        if (polymorphic == null) {
            throw new AssertionError("Failed to parse polymorphic signature. " + parser.createDefaultMethodSpec(null, null, false, null) + " Types: " + returnType + " - " + foundTypes);
        }

        polymorphic.setKind(SpecializationKind.POLYMORPHIC);
        node.getSpecializations().add(polymorphic);
    }

    private static boolean verifySpecializationSameLength(NodeData nodeData) {
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

    /**
     * @see "https://bugs.openjdk.java.net/browse/JDK-8039214"
     */
    @SuppressWarnings("unused")
    private static List<Element> newElementList(List<? extends Element> src) {
        List<Element> workaround = new ArrayList<Element>(src);
        return workaround;
    }

    private static void verifyMissingAbstractMethods(NodeData nodeData, List<? extends Element> originalElements) {
        if (!nodeData.needsFactory()) {
            // missing abstract methods only needs to be implemented
            // if we need go generate factory for it.
            return;
        }

        List<Element> elements = newElementList(originalElements);
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
                for (CacheExpression cache : specialization.getCaches()) {
                    if (cache.isAlwaysInitialized() || cache.isCachedLibrary()) {
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
                            !specialization.hasMultipleInstances() && !other.specialization.hasMultipleInstances() &&
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
                return String.format("The cache initializer does not match.");
            }
            if (!equalsExpression(expression.getUncachedExpression(), other.specialization, other.expression.getUncachedExpression())) {
                if (!generateMessage) {
                    return "";
                }
                return String.format("The uncached initializer does not match.");
            }
            if (specialization.hasMultipleInstances()) {
                if (!generateMessage) {
                    return "";
                }
                return String.format("The specialization '%s' has multiple instances.", ElementUtils.getReadableSignature(specialization.getMethod()));
            }
            if (other.specialization.hasMultipleInstances()) {
                if (!generateMessage) {
                    return "";
                }
                return String.format("The specialization '%s' has multiple instances.", ElementUtils.getReadableSignature(other.specialization.getMethod()));
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

        private static class DSLExpressionHash implements DSLExpressionVisitor {
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
