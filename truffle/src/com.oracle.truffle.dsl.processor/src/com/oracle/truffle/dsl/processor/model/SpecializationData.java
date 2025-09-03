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
package com.oracle.truffle.dsl.processor.model;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.expression.DSLExpression;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.AbstractDSLExpressionVisitor;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Call;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Variable;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.parser.NodeParser;
import com.oracle.truffle.dsl.processor.parser.SpecializationGroup.TypeGuard;

public final class SpecializationData extends TemplateMethod {

    public enum SpecializationKind {
        SPECIALIZED,
        FALLBACK
    }

    private final NodeData node;
    private SpecializationKind kind;
    private final List<SpecializationThrowsData> exceptions;
    private final boolean hasUnexpectedResultRewrite;
    private final List<GuardExpression> guards = new ArrayList<>();
    private List<CacheExpression> caches = Collections.emptyList();
    private List<AssumptionExpression> assumptionExpressions = Collections.emptyList();
    private Set<SpecializationData> replaces;
    private Set<String> replacesNames;
    private Set<SpecializationData> replacedBy;
    private String insertBeforeName;
    private SpecializationData insertBefore;
    private boolean replaced;
    private boolean reachable;
    private boolean reachesFallback;
    private int unroll;
    private int unrollIndex = -1;
    private int index;
    private DSLExpression limitExpression;
    private SpecializationData uncachedSpecialization;
    private final boolean reportPolymorphism;
    private final boolean reportMegamorphism;

    private boolean excludeForUncached;

    private Double localActivationProbability;

    private boolean aotReachable;

    private final List<SpecializationData> boxingOverloads = new ArrayList<>();

    public SpecializationData(NodeData node, TemplateMethod template, SpecializationKind kind, List<SpecializationThrowsData> exceptions, boolean hasUnexpectedResultRewrite,
                    boolean reportPolymorphism, boolean reportMegamorphism) {
        super(template);
        this.node = node;
        this.kind = kind;
        this.exceptions = exceptions;
        this.hasUnexpectedResultRewrite = hasUnexpectedResultRewrite;
        this.index = template.getNaturalOrder();
        this.reportPolymorphism = reportPolymorphism;
        this.reportMegamorphism = reportMegamorphism;
    }

    public SpecializationData(NodeData node, TemplateMethod template, SpecializationKind kind) {
        this(node, template, kind, new ArrayList<>(), false, true, false);
    }

    public SpecializationData copy() {
        SpecializationData copy = new SpecializationData(node, this, kind, new ArrayList<>(exceptions), hasUnexpectedResultRewrite, reportPolymorphism, reportMegamorphism);

        copy.guards.clear();
        for (GuardExpression guard : guards) {
            copy.guards.add(guard.copy(copy));
        }

        copy.caches = new ArrayList<>(caches.size());
        for (CacheExpression cache : caches) {
            copy.caches.add(cache.copy());
        }

        copy.assumptionExpressions = new ArrayList<>(assumptionExpressions);
        if (replacesNames != null) {
            copy.replacesNames = new LinkedHashSet<>();
            copy.replacesNames.addAll(replacesNames);
        }

        copy.replaced = replaced;

        if (replaces != null) {
            copy.replaces = new LinkedHashSet<>();
            copy.replaces.addAll(replaces);
        }
        if (replacedBy != null) {
            copy.replacedBy = new LinkedHashSet<>();
            copy.replacedBy.addAll(replacedBy);
        }
        copy.insertBeforeName = insertBeforeName;
        copy.reachable = reachable;
        copy.reachesFallback = reachesFallback;
        copy.index = index;
        copy.limitExpression = limitExpression;
        copy.aotReachable = aotReachable;
        copy.unroll = unroll;
        copy.uncachedSpecialization = uncachedSpecialization;
        return copy;
    }

    public void setExcludeForUncached(Boolean value) {
        this.excludeForUncached = value;
    }

    public boolean isExcludeForUncached() {
        return excludeForUncached;
    }

    public List<TypeGuard> getImplicitTypeGuards() {
        TypeSystemData typeSystem = getNode().getTypeSystem();
        if (typeSystem.getImplicitCasts().isEmpty()) {
            return List.of();
        }
        int signatureIndex = 0;
        List<TypeGuard> implicitTypeChecks = new ArrayList<>();
        for (Parameter p : getDynamicParameters()) {
            if (typeSystem.hasImplicitSourceTypes(p.getType())) {
                implicitTypeChecks.add(new TypeGuard(typeSystem, p.getType(), signatureIndex));
            }
            signatureIndex++;
        }
        return implicitTypeChecks;
    }

    public boolean isNodeReceiverVariable(VariableElement var) {
        if (getNode().isGenerateInline()) {
            Parameter p = findByVariable(var);
            if (p != null && p.getSpecification().isSignature()) {
                NodeExecutionData execution = p.getSpecification().getExecution();
                if (execution.getIndex() == FlatNodeGenFactory.INLINED_NODE_INDEX) {
                    return true;
                }
            }
        }

        String simpleString = var.getSimpleName().toString();
        return (simpleString.equals(NodeParser.SYMBOL_THIS) || simpleString.equals(NodeParser.SYMBOL_NODE)) && ElementUtils.typeEquals(var.asType(), types.Node);
    }

    public boolean isNodeReceiverBoundInAnyExpression() {
        for (GuardExpression guard : getGuards()) {
            if (isNodeReceiverBound(guard.getExpression())) {
                return true;
            }
        }
        for (CacheExpression cache : getCaches()) {
            if (isNodeReceiverBound(cache.getDefaultExpression())) {
                return true;
            }
        }
        for (AssumptionExpression assumption : getAssumptionExpressions()) {
            if (isNodeReceiverBound(assumption.getExpression())) {
                return true;
            }
        }
        if (isNodeReceiverBound(getLimitExpression())) {
            return true;
        }
        return false;
    }

    public boolean isNodeReceiverBound(DSLExpression expression) {
        for (Variable variable : expression.findBoundVariables()) {
            if (isNodeReceiverVariable(variable.getResolvedVariable())) {
                return true;
            }
        }
        return false;
    }

    public boolean isUncachedSpecialization() {
        // do not initialize unroll for uncached specializations
        if (getReplaces() != null) {
            for (SpecializationData replace : getReplaces()) {
                if (replace.getUncachedSpecialization() == this) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setUnrollIndex(int unrollIndex) {
        this.unrollIndex = unrollIndex;
    }

    public int getUnrollIndex() {
        return unrollIndex;
    }

    public void setUnroll(int unroll) {
        this.unroll = unroll;
    }

    public boolean hasUnroll() {
        return unroll > 0;
    }

    public boolean isUnrolled() {
        return hasUnroll() && unrollIndex != -1;
    }

    public int getUnroll() {
        return unroll;
    }

    /**
     * Returns true if an expression can always be folded to a constant during PE. Method calls are
     * natural boundaries as we cannot look into them at annotation processing time. The passed
     * expression must be resolved.
     */
    public boolean isCompilationFinalExpression(DSLExpression expression) {
        if (!(expression instanceof Variable)) {
            // fast-path check
            return false;
        }

        if (expression.resolveConstant() != null) {
            return true;
        }

        /*
         * Check whether the expression is of form a.b.c with final or compilation final values.
         */
        DSLExpression current = expression;
        while (current != null) {
            if (!(current instanceof Variable)) {
                return false;
            }

            Variable variable = (Variable) current;
            // if not a final field
            if (!variable.isCompilationFinalField()) {
                Parameter boundParameter = findByVariable(variable.getResolvedVariable());
                // and not a bound parameter
                if (boundParameter == null) {
                    // it cannot be compilation final
                    return false;
                }
            }

            current = variable.getReceiver();
        }

        // do a more thorough analysis of whether a dynamic parameter is bound, possibly indirectly
        // e.g. through @Bind.
        if (isDynamicParameterBound(expression, true)) {
            // dynamic parameters cannot be known to be PE final (they might be though).
            return false;
        }

        return true;
    }

    public boolean isPrepareForAOT() {
        return aotReachable;
    }

    public void setPrepareForAOT(boolean prepareForAOT) {
        this.aotReachable = prepareForAOT;
    }

    public void setUncachedSpecialization(SpecializationData removeCompanion) {
        this.uncachedSpecialization = removeCompanion;
    }

    public SpecializationData getUncachedSpecialization() {
        return uncachedSpecialization;
    }

    public boolean hasFrameParameter() {
        for (Parameter p : getSignatureParameters()) {
            if (ElementUtils.typeEquals(p.getType(), types.VirtualFrame) || ElementUtils.typeEquals(p.getType(), types.Frame)) {
                return true;
            }
        }
        return false;
    }

    public boolean needsVirtualFrame() {
        if (getFrame() != null && ElementUtils.typeEquals(getFrame().getType(), types.VirtualFrame)) {
            // not supported for frames
            return true;
        }
        return false;
    }

    public boolean needsTruffleBoundary() {
        for (CacheExpression cache : caches) {
            if (cache.isAlwaysInitialized() && cache.isRequiresBoundary()) {
                return true;
            }
        }
        return false;
    }

    public boolean needsPushEncapsulatingNode() {
        for (CacheExpression cache : caches) {
            if (cache.isAlwaysInitialized() && cache.isRequiresBoundary() && cache.isCachedLibrary()) {
                if (cache.getCachedLibrary().isPushEncapsulatingNode()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isAnyLibraryBoundInGuard() {
        for (CacheExpression cache : getCaches()) {
            if (!cache.isCachedLibrary()) {
                continue;
            }
            if (isLibraryBoundInGuard(cache)) {
                return true;
            }
        }
        return false;
    }

    public boolean isLibraryBoundInGuard(CacheExpression cachedLibrary) {
        if (!cachedLibrary.isCachedLibrary()) {
            return false;
        }
        for (GuardExpression guard : getGuards()) {
            if (guard.isLibraryAcceptsGuard()) {
                continue;
            }
            for (CacheExpression cacheExpression : getBoundCaches(guard.getExpression(), true)) {
                if (cacheExpression.getParameter().equals(cachedLibrary.getParameter())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isTrivialExpression(DSLExpression expression) {
        Set<ExecutableElement> boundMethod = expression.findBoundExecutableElements();
        ProcessorContext context = ProcessorContext.getInstance();
        for (ExecutableElement method : boundMethod) {
            String name = method.getSimpleName().toString();
            if (name.equals("getClass") && ElementUtils.typeEquals(method.getEnclosingElement().asType(), context.getType(Object.class))) {
                continue;
            }
            return false;
        }
        for (VariableElement variable : expression.findBoundVariableElements()) {
            if (variable.getSimpleName().toString().equals("null")) {
                // null is allowed.
                continue;
            }
            Parameter parameter = findByVariable(variable);
            if (parameter == null) {
                return false;
            }
            if (parameter.getSpecification().isCached() || parameter.getSpecification().isSignature()) {
                continue;
            }
            return false;
        }
        return true;
    }

    public void setReachesFallback(boolean reachesFallback) {
        this.reachesFallback = reachesFallback;
    }

    /** == !@ReportPolymorphism.Exclude. */
    public boolean isReportPolymorphism() {
        return reportPolymorphism;
    }

    public boolean isReportMegamorphism() {
        return reportMegamorphism;
    }

    public boolean isReachesFallback() {
        return reachesFallback;
    }

    public boolean isGuardBoundWithCache(GuardExpression guardExpression) {
        for (CacheExpression cache : getBoundCaches(guardExpression.getExpression(), false)) {
            if (cache.isAlwaysInitialized()) {
                continue;
            }
            // bound caches for caches are returned before
            return true;
        }
        return false;
    }

    public Set<CacheExpression> getBoundCaches(DSLExpression guardExpression, boolean transitiveCached) {
        return getBoundCachesImpl(new LinkedHashSet<>(), guardExpression, transitiveCached);
    }

    private Set<CacheExpression> getBoundCachesImpl(Set<DSLExpression> visitedExpressions, DSLExpression guardExpression, boolean transitiveCached) {
        List<CacheExpression> resolvedCaches = getCaches();
        if (resolvedCaches.isEmpty()) {
            return Collections.emptySet();
        }
        visitedExpressions.add(guardExpression);
        Set<VariableElement> boundVars = guardExpression.findBoundVariableElements();
        Set<CacheExpression> foundCaches = new LinkedHashSet<>();
        for (CacheExpression cache : resolvedCaches) {
            VariableElement cacheVar = cache.getParameter().getVariableElement();
            if (boundVars.contains(cacheVar)) {
                if (cache.getDefaultExpression() != null && !visitedExpressions.contains(cache.getDefaultExpression())) {
                    if (transitiveCached || cache.isAlwaysInitialized()) {
                        foundCaches.addAll(getBoundCachesImpl(visitedExpressions, cache.getDefaultExpression(), transitiveCached));
                    }
                }
                foundCaches.add(cache);
            }
        }
        return foundCaches;
    }

    public void setKind(SpecializationKind kind) {
        this.kind = kind;
    }

    static final class FindDynamicBindingVisitor extends AbstractDSLExpressionVisitor {

        boolean found;

        final String[] resultValues = new String[]{
                        "get", ((TypeElement) ProcessorContext.getInstance().getTypes().TruffleLanguage_ContextReference.asElement()).getQualifiedName().toString(),
                        "get", ((TypeElement) ProcessorContext.getInstance().getTypes().TruffleLanguage_LanguageReference.asElement()).getQualifiedName().toString(),
                        "get", ProcessorContext.getInstance().getTypeElement(Reference.class).getQualifiedName().toString(),
        };

        @Override
        public void visitCall(Call binary) {
            ExecutableElement method = binary.getResolvedMethod();
            String methodName = method.getSimpleName().toString();
            Element enclosingElement = method.getEnclosingElement();
            if (enclosingElement == null || !enclosingElement.getKind().isClass()) {
                return;
            }
            String className = ((TypeElement) enclosingElement).getQualifiedName().toString();
            for (int i = 0; i < resultValues.length; i = i + 2) {
                String searchMethod = resultValues[i];
                String searchClass = resultValues[i + 1];
                if (searchMethod.equals(methodName) && className.equals(searchClass)) {
                    found = true;
                    break;
                }
            }
        }
    }

    public enum Idempotence {

        IDEMPOTENT,

        NON_IDEMPOTENT,

        UNKNOWN

    }

    public Idempotence getIdempotence(DSLExpression expression) {
        if (isDynamicParameterBound(expression, true)) {
            return Idempotence.NON_IDEMPOTENT;
        }

        IdempotentenceVisitor visitor = new IdempotentenceVisitor();
        expression.accept(visitor);
        return visitor.current;
    }

    public Set<ExecutableElement> getBoundMethods(DSLExpression expression) {
        var foundMethods = new LinkedHashSet<ExecutableElement>();
        expression.accept(new AbstractDSLExpressionVisitor() {
            @Override
            public void visitCall(Call n) {
                foundMethods.add(n.getResolvedMethod());
            }

            @Override
            public void visitVariable(Variable n) {
                VariableElement var = n.getResolvedVariable();
                if (n.getReceiver() == null) {
                    Parameter p = findByVariable(var);
                    if (p != null) {
                        CacheExpression cache = findCache(p);
                        if (cache != null && cache.isAlwaysInitialized()) {
                            foundMethods.addAll(getBoundMethods(cache.getDefaultExpression()));
                        }
                    }
                }
            }
        });
        return foundMethods;
    }

    public boolean isDynamicParameterBound(DSLExpression expression, boolean transitive) {
        if (expression == null) {
            return false;
        }
        Set<VariableElement> boundVariables = expression.findBoundVariableElements();
        for (Parameter parameter : getDynamicParameters()) {
            if (boundVariables.contains(parameter.getVariableElement())) {
                return true;
            }
        }
        FindDynamicBindingVisitor visitor = new FindDynamicBindingVisitor();
        expression.accept(visitor);
        if (visitor.found) {
            return true;
        }

        if (transitive) {
            for (CacheExpression cache : getBoundCaches(expression, false)) {
                if (cache.isAlwaysInitialized()) {
                    if (cache.isWeakReferenceGet()) {
                        // only cached values come from weak reference gets although
                        // they are initialized every time
                        continue;
                    }
                    if (isDynamicParameterBound(cache.getDefaultExpression(), true)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public Parameter findByVariable(VariableElement variable) {
        for (Parameter parameter : getParameters()) {
            if (ElementUtils.variableEquals(parameter.getVariableElement(), variable)) {
                return parameter;
            }
        }
        return null;
    }

    public DSLExpression getLimitExpression() {
        return limitExpression;
    }

    public void setLimitExpression(DSLExpression limitExpression) {
        this.limitExpression = limitExpression;
    }

    public void setInsertBefore(SpecializationData insertBefore) {
        this.insertBefore = insertBefore;
    }

    public void setInsertBeforeName(String insertBeforeName) {
        this.insertBeforeName = insertBeforeName;
    }

    public SpecializationData getInsertBefore() {
        return insertBefore;
    }

    public String getInsertBeforeName() {
        return insertBeforeName;
    }

    public Set<String> getReplacesNames() {
        return replacesNames;
    }

    public void setReplacesNames(Set<String> replacesNames) {
        this.replacesNames = replacesNames;
    }

    public Set<SpecializationData> getReplaces() {
        return replaces;
    }

    public void setReplaces(Set<SpecializationData> replaces) {
        this.replaces = replaces;
    }

    public Set<SpecializationData> getReplacedBy() {
        return replacedBy;
    }

    public void setReplacedBy(Set<SpecializationData> replacedBy) {
        this.replacedBy = replacedBy;
    }

    public void setReachable(boolean reachable) {
        this.reachable = reachable;
    }

    public void setReplaced(boolean replaced) {
        this.replaced = replaced;
    }

    public boolean isReachable() {
        return reachable;
    }

    public boolean isReplaced() {
        return replaced;
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        List<MessageContainer> sinks = new ArrayList<>();
        if (exceptions != null) {
            sinks.addAll(exceptions);
        }
        if (guards != null) {
            sinks.addAll(guards);
        }
        if (caches != null) {
            sinks.addAll(caches);
        }
        if (assumptionExpressions != null) {
            sinks.addAll(assumptionExpressions);
        }
        sinks.addAll(getBoxingOverloads());
        return sinks;
    }

    /**
     * Returns <code>true</code> if this specialization needs lazy initialization of cached fields.
     */
    boolean needsSpecialize() {
        if (!getAssumptionExpressions().isEmpty()) {
            return true;
        }

        if (!getCaches().isEmpty()) {
            for (CacheExpression cache : getCaches()) {
                if (cache.isAlwaysInitialized()) {
                    continue; // @Bind
                }
                if (cache.getInlinedNode() != null) {
                    continue; // @Cached but inlined so no init state needed
                }
                return true;
            }
        }

        if (hasMultipleInstances()) {
            // guard needs initialization
            return true;
        }

        List<TypeGuard> implicitTypeGuards = getImplicitTypeGuards();
        if (!implicitTypeGuards.isEmpty()) {
            for (TypeGuard guard : implicitTypeGuards) {
                if (isImplicitTypeGuardUsed(guard)) {
                    return true;
                }
            }
        }

        return FlatNodeGenFactory.useSpecializationClass(this);
    }

    boolean needsState() {
        if (!getAssumptionExpressions().isEmpty()) {
            return true;
        }

        if (!getCaches().isEmpty()) {
            for (CacheExpression cache : getCaches()) {
                if (cache.isAlwaysInitialized()) {
                    continue; // @Bind
                }
                return true;
            }
        }

        if (hasMultipleInstances()) {
            // guard needs initialization
            return true;
        }

        List<TypeGuard> implicitTypeGuards = getImplicitTypeGuards();
        if (!implicitTypeGuards.isEmpty()) {
            for (TypeGuard guard : implicitTypeGuards) {
                if (isImplicitTypeGuardUsed(guard)) {
                    return true;
                }
            }
        }

        return FlatNodeGenFactory.useSpecializationClass(this);
    }

    private boolean isImplicitTypeGuardUsed(TypeGuard guard) {
        int signatureIndex = guard.getSignatureIndex();
        for (ExecutableTypeData executable : node.getExecutableTypes()) {
            List<TypeMirror> parameters = executable.getSignatureParameters();
            if (signatureIndex >= parameters.size()) {
                // dynamically executed can be any type.
                return true;
            }
            TypeMirror polymorphicParameter = parameters.get(signatureIndex);
            TypeMirror specializationType = this.getSignatureParameters().get(signatureIndex).getType();
            if (ElementUtils.needsCastTo(polymorphicParameter, specializationType)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int compareTo(TemplateMethod other) {
        if (this == other) {
            return 0;
        } else if (!(other instanceof SpecializationData)) {
            return super.compareTo(other);
        }
        SpecializationData m2 = (SpecializationData) other;
        int kindOrder = kind.compareTo(m2.kind);
        if (kindOrder != 0) {
            return kindOrder;
        }

        int compare = 0;
        int order1 = index;
        int order2 = m2.index;
        if (order1 != NO_NATURAL_ORDER && order2 != NO_NATURAL_ORDER) {
            compare = Integer.compare(order1, order2);
            if (compare != 0) {
                return compare;
            }
        }

        return super.compareTo(other);
    }

    public void setIndex(int order) {
        this.index = order;
    }

    public int getIndex() {
        return index;
    }

    public int getIntrospectionIndex() {
        if (getMethod() == null) {
            return -1;
        }
        return index;
    }

    public NodeData getNode() {
        return node;
    }

    public boolean isSpecialized() {
        return kind == SpecializationKind.SPECIALIZED;
    }

    public boolean isFallback() {
        return kind == SpecializationKind.FALLBACK;
    }

    public List<SpecializationThrowsData> getExceptions() {
        return exceptions;
    }

    public boolean hasUnexpectedResultRewrite() {
        return hasUnexpectedResultRewrite;
    }

    public List<GuardExpression> getGuards() {
        return guards;
    }

    public void setLocalActivationProbability(double activationProbability) {
        this.localActivationProbability = activationProbability;
    }

    public double getLocalActivationProbability() {
        return localActivationProbability;
    }

    public double getActivationProbability() {
        return getNode().getActivationProbability() * localActivationProbability;
    }

    @Override
    public String toString() {
        return String.format("%s [nodeId =%s, id = %s, method = %s, guards = %s, signature = %s]", getClass().getSimpleName(), getNode().getNodeId(), getId(), getMethod(), getGuards(),
                        getDynamicTypes());
    }

    public boolean isFrameUsedByGuard() {
        Parameter frame = getFrame();
        if (frame != null) {
            for (GuardExpression guard : getGuards()) {
                if (guard.getExpression().findBoundVariableElements().contains(frame.getVariableElement())) {
                    return true;
                }
            }
            for (CacheExpression cache : getCaches()) {
                if (cache.getDefaultExpression() != null && cache.getDefaultExpression().findBoundVariableElements().contains(frame.getVariableElement())) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<CacheExpression> getCaches() {
        return caches;
    }

    public void setCaches(List<CacheExpression> caches) {
        this.caches = caches;
    }

    public void setAssumptionExpressions(List<AssumptionExpression> assumptionExpressions) {
        this.assumptionExpressions = assumptionExpressions;
    }

    public List<AssumptionExpression> getAssumptionExpressions() {
        return assumptionExpressions;
    }

    public boolean hasMultipleInstances() {
        return getMaximumNumberOfInstances() > 1;
    }

    public boolean isGuardBindsExclusiveCache() {
        if (!getCaches().isEmpty() && !getGuards().isEmpty()) {
            for (GuardExpression guard : getGuards()) {
                if (guard.hasErrors()) {
                    continue;
                }
                if (isDynamicParameterBound(guard.getExpression(), true)) {
                    if (isExclusiveCacheParameterBound(guard)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isExclusiveCacheParameterBound(GuardExpression guard) {
        for (CacheExpression cache : getBoundCaches(guard.getExpression(), false)) {
            if (cache.isAlwaysInitialized()) {
                continue;
            } else if (!guard.isLibraryAcceptsGuard() && cache.isCachedLibrary()) {
                continue;
            } else if (guard.isWeakReferenceGuard() && cache.isWeakReference()) {
                continue;
            } else if (cache.getSharedGroup() != null) {
                continue;
            }
            return true;
        }
        return false;
    }

    public boolean isConstantLimit() {
        if (isGuardBindsExclusiveCache()) {
            DSLExpression expression = getLimitExpression();
            if (expression == null) {
                return true;
            } else {
                Object constant = expression.resolveConstant();
                if (constant != null && constant instanceof Integer) {
                    return true;
                }
            }
            return false;
        } else {
            return true;
        }
    }

    public int getMaximumNumberOfInstances() {
        if (isGuardBindsExclusiveCache()) {
            DSLExpression expression = getLimitExpression();
            if (expression == null) {
                return 3; // default limit
            } else {
                Object constant = expression.resolveConstant();
                if (constant != null && constant instanceof Integer) {
                    return (int) constant;
                } else {
                    return Integer.MAX_VALUE;
                }
            }
        } else {
            return 1;
        }
    }

    public boolean isReachableAfter(SpecializationData prev) {
        if (!prev.isSpecialized()) {
            return true;
        }

        if (!prev.getExceptions().isEmpty()) {
            // may get excluded by exception
            return true;
        }

        if (prev.isGuardBindsExclusiveCache()) {
            // may fallthrough due to limit
            return true;
        }

        if (node.isGenerateUncached() && !isReplaced() && prev.isReplaced()) {
            // becomes reachable in the uncached node
            return true;
        }

        /*
         * Cached libraries have an implicit accepts guard and generate an uncached specialization
         * which avoids any fallthrough except if there is a specialization that replaces it.
         */
        for (CacheExpression cache : prev.getCaches()) {
            if (cache.isCachedLibrary()) {
                if (getReplaces().contains(prev)) {
                    return true;
                }
            }
        }

        Iterator<Parameter> currentSignature = getSignatureParameters().iterator();
        Iterator<Parameter> prevSignature = prev.getSignatureParameters().iterator();

        TypeSystemData typeSystem = prev.getNode().getTypeSystem();
        while (currentSignature.hasNext() && prevSignature.hasNext()) {
            TypeMirror currentType = currentSignature.next().getType();
            TypeMirror prevType = prevSignature.next().getType();

            if (!typeSystem.isImplicitSubtypeOf(currentType, prevType)) {
                return true;
            }
        }

        if (!prev.getAssumptionExpressions().isEmpty()) {
            // TODO: chumer: we could at least check reachability after trivial assumptions
            // not sure if this is worth it.
            return true;
        }

        Iterator<GuardExpression> prevGuards = prev.getGuards().iterator();
        Iterator<GuardExpression> currentGuards = getGuards().iterator();
        while (prevGuards.hasNext()) {
            GuardExpression prevGuard = prevGuards.next();
            if (prev.isGuardBoundWithCache(prevGuard)) {
                // if a guard with cache is bound the next specialization is always reachable
                return true;
            }

            GuardExpression currentGuard = currentGuards.hasNext() ? currentGuards.next() : null;
            if (currentGuard == null || !currentGuard.implies(prevGuard)) {
                return true;
            }
        }

        return false;
    }

    public CacheExpression findCache(Parameter resolvedParameter) {
        for (CacheExpression cache : getCaches()) {
            if (cache.getParameter().equals(resolvedParameter)) {
                return cache;
            }
        }
        return null;
    }

    private final class IdempotentenceVisitor extends AbstractDSLExpressionVisitor {

        Idempotence current = Idempotence.IDEMPOTENT;

        @Override
        public void visitCall(Call n) {
            if (current == Idempotence.NON_IDEMPOTENT) {
                // if one method is known to be non-idempotent all of them are
                return;
            }

            Idempotence idempotent = ElementUtils.getIdempotent(n.getResolvedMethod());
            if (idempotent == Idempotence.UNKNOWN || idempotent == Idempotence.NON_IDEMPOTENT) {
                current = idempotent;
            }
        }

        @Override
        public void visitVariable(Variable n) {
            if (current == Idempotence.NON_IDEMPOTENT) {
                // if one method is known to be non-idempotent all of them are
                return;
            }

            VariableElement var = n.getResolvedVariable();
            if (n.getReceiver() == null) {
                /*
                 * Directly bound variable that is not dynamic. The DSL ensures such reads are not
                 * side-effecting in the fast-path. They can be treated as effectively final field
                 * reads.
                 */
                Parameter p = findByVariable(var);
                if (p != null) {
                    CacheExpression cache = findCache(p);
                    if (cache != null && cache.isAlwaysInitialized()) {
                        /*
                         * Bind variables may cause side-effects themselves.
                         */
                        Idempotence cacheIdempotent = getIdempotence(cache.getDefaultExpression());
                        switch (cacheIdempotent) {
                            case IDEMPOTENT:
                                break;
                            case NON_IDEMPOTENT:
                            case UNKNOWN:
                                current = cacheIdempotent;
                                break;
                            default:
                                throw new AssertionError();
                        }
                    }
                }
            } else {
                if (!var.getModifiers().contains(Modifier.FINAL)) {
                    /*
                     * If we see a non-final read an expression is sensitive to side-effects.
                     */
                    current = Idempotence.NON_IDEMPOTENT;
                }
            }
        }
    }

    public boolean isBoxingOverloadable(SpecializationData other) {
        if (!ElementUtils.isPrimitive(other.getReturnType().getType()) && !ElementUtils.isVoid(other.getReturnType().getType())) {
            return false;
        }

        List<Parameter> signature = getSignatureParameters();
        List<Parameter> otherSignature = other.getSignatureParameters();
        if (signature.size() != otherSignature.size()) {
            return false;
        }

        for (int i = 0; i < signature.size(); i++) {
            Parameter parameter = signature.get(i);
            Parameter otherParameter = otherSignature.get(i);
            if (!ElementUtils.typeEquals(parameter.getType(), otherParameter.getType())) {
                return false;
            }
        }
        if (!Objects.equals(getLimitExpression(), other.getLimitExpression())) {
            return false;
        }
        if (!hasSameGuards(other)) {
            return false;
        }
        if (!hasSameCaches(other)) {
            return false;
        }
        if (!hasSameAssumptions(other)) {
            return false;
        }
        return true;
    }

    public boolean hasSameGuards(SpecializationData other) {
        if (this.guards.size() != other.guards.size()) {
            return false;
        }

        for (int i = 0; i < guards.size(); i++) {
            GuardExpression guard = guards.get(i);
            GuardExpression otherGuard = other.guards.get(i);
            if (!guard.getExpression().equals(otherGuard.getExpression())) {
                return false;
            }
        }

        return true;
    }

    public boolean hasSameCaches(SpecializationData other) {
        if (this.caches.size() != other.caches.size()) {
            return false;
        }

        for (int i = 0; i < caches.size(); i++) {
            CacheExpression cache = caches.get(i);
            CacheExpression otherCache = other.caches.get(i);
            if (!cache.isSameCache(otherCache)) {
                return false;
            }
        }

        return true;
    }

    public boolean hasSameAssumptions(SpecializationData other) {
        if (this.assumptionExpressions.size() != other.assumptionExpressions.size()) {
            return false;
        }

        for (int i = 0; i < assumptionExpressions.size(); i++) {
            AssumptionExpression assumption = assumptionExpressions.get(i);
            AssumptionExpression otherAssumptions = other.assumptionExpressions.get(i);
            if (!assumption.getExpression().equals(otherAssumptions.getExpression())) {
                return false;
            }
        }

        return true;
    }

    public List<SpecializationData> getBoxingOverloads() {
        return boxingOverloads;
    }

    public SpecializationData lookupBoxingOverload(ExecutableTypeData type) {
        if (!type.hasUnexpectedValue()) {
            return null;
        }
        for (SpecializationData specialization : getBoxingOverloads()) {
            if (ElementUtils.typeEquals(specialization.getReturnType().getType(), type.getReturnType())) {
                return specialization;
            }
        }
        return null;
    }

    public TypeMirror lookupBoxingOverloadReturnType(ExecutableTypeData type) {
        SpecializationData specializationData = lookupBoxingOverload(type);
        if (specializationData == null) {
            return getReturnType().getType();
        } else {
            return specializationData.getReturnType().getType();
        }

    }

}
