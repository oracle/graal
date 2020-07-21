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
package com.oracle.truffle.dsl.processor.model;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.expression.DSLExpression;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.AbstractDSLExpressionVisitor;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Call;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

public final class SpecializationData extends TemplateMethod {

    public enum SpecializationKind {
        UNINITIALIZED,
        SPECIALIZED,
        POLYMORPHIC,
        FALLBACK
    }

    private final NodeData node;
    private SpecializationKind kind;
    private final List<SpecializationThrowsData> exceptions;
    private final boolean hasUnexpectedResultRewrite;
    private final List<GuardExpression> guards = new ArrayList<>();
    private List<CacheExpression> caches = Collections.emptyList();
    private List<AssumptionExpression> assumptionExpressions = Collections.emptyList();
    private final Set<SpecializationData> replaces = new LinkedHashSet<>();
    private final Set<String> replacesNames = new LinkedHashSet<>();
    private final Set<SpecializationData> excludedBy = new LinkedHashSet<>();
    private String insertBeforeName;
    private SpecializationData insertBefore;
    private boolean replaced;
    private boolean reachable;
    private boolean reachesFallback;
    private int index;
    private DSLExpression limitExpression;
    private SpecializationData uncachedSpecialization;
    private final boolean reportPolymorphism;

    public SpecializationData(NodeData node, TemplateMethod template, SpecializationKind kind, List<SpecializationThrowsData> exceptions, boolean hasUnexpectedResultRewrite,
                    boolean reportPolymorphism) {
        super(template);
        this.node = node;
        this.kind = kind;
        this.exceptions = exceptions;
        this.hasUnexpectedResultRewrite = hasUnexpectedResultRewrite;
        this.index = template.getNaturalOrder();
        this.reportPolymorphism = reportPolymorphism;
    }

    public SpecializationData copy() {
        SpecializationData copy = new SpecializationData(node, this, kind, new ArrayList<>(exceptions), hasUnexpectedResultRewrite, reportPolymorphism);
        copy.guards.addAll(guards);
        copy.caches = new ArrayList<>(caches);
        copy.assumptionExpressions = new ArrayList<>(assumptionExpressions);
        copy.replaced = replaced;
        copy.replaces.addAll(replaces);
        copy.replacesNames.addAll(replacesNames);
        copy.excludedBy.addAll(excludedBy);
        copy.insertBeforeName = insertBeforeName;
        copy.reachable = reachable;
        copy.reachesFallback = reachesFallback;
        copy.index = index;
        copy.limitExpression = limitExpression;
        return copy;
    }

    public void setUncachedSpecialization(SpecializationData removeCompanion) {
        this.uncachedSpecialization = removeCompanion;
    }

    public SpecializationData getUncachedSpecialization() {
        return uncachedSpecialization;
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

    public boolean isReportPolymorphism() {
        return reportPolymorphism;
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
        return getBoundCachesImpl(new HashSet<>(), guardExpression, transitiveCached);
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

    public boolean isDynamicParameterBound(DSLExpression expression, boolean transitive) {
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
                    if (isDynamicParameterBound(cache.getDefaultExpression(), true)) {
                        return true;
                    }
                }
                if ((cache.isCachedContext() || cache.isCachedLanguage()) && !cache.isReference()) {
                    return true;
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

    public SpecializationData(NodeData node, TemplateMethod template, SpecializationKind kind) {
        this(node, template, kind, new ArrayList<SpecializationThrowsData>(), false, true);
    }

    public Set<SpecializationData> getReplaces() {
        return replaces;
    }

    public Set<SpecializationData> getExcludedBy() {
        return excludedBy;
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

    public boolean isPolymorphic() {
        return kind == SpecializationKind.POLYMORPHIC;
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
        return sinks;
    }

    public boolean needsRewrite(ProcessorContext context) {
        if (!getExceptions().isEmpty()) {
            return true;
        }
        if (!getGuards().isEmpty()) {
            return true;
        }
        if (!getAssumptionExpressions().isEmpty()) {
            return true;
        }

        if (!getCaches().isEmpty()) {
            for (CacheExpression cache : getCaches()) {
                if (cache.isEagerInitialize()) {
                    continue;
                }
                if (!cache.isAlwaysInitialized() || cache.isCachedContext() || cache.isCachedLanguage()) {
                    return true;
                }
            }
        }

        int signatureIndex = 0;
        for (Parameter parameter : getSignatureParameters()) {
            for (ExecutableTypeData executableType : node.getExecutableTypes()) {
                List<TypeMirror> evaluatedParameters = executableType.getEvaluatedParameters();
                if (signatureIndex < evaluatedParameters.size()) {
                    TypeMirror evaluatedParameterType = evaluatedParameters.get(signatureIndex);
                    if (ElementUtils.needsCastTo(evaluatedParameterType, parameter.getType())) {
                        return true;
                    }
                }
            }

            NodeChildData child = parameter.getSpecification().getExecution().getChild();
            if (child != null) {

                ExecutableTypeData type = child.findExecutableType(parameter.getType());
                if (type == null) {
                    type = child.findAnyGenericExecutableType(context);
                }
                if (type.hasUnexpectedValue()) {
                    return true;
                }
                if (ElementUtils.needsCastTo(type.getReturnType(), parameter.getType())) {
                    return true;
                }
            }
            signatureIndex++;
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

    public NodeData getNode() {
        return node;
    }

    public boolean isSpecialized() {
        return kind == SpecializationKind.SPECIALIZED;
    }

    public boolean isFallback() {
        return kind == SpecializationKind.FALLBACK;
    }

    public boolean isUninitialized() {
        return kind == SpecializationKind.UNINITIALIZED;
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

    public SpecializationData findNextSpecialization() {
        List<SpecializationData> specializations = node.getSpecializations();
        for (int i = 0; i < specializations.size() - 1; i++) {
            if (specializations.get(i) == this) {
                return specializations.get(i + 1);
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("%s [id = %s, method = %s, guards = %s, signature = %s]", getClass().getSimpleName(), getId(), getMethod(), getGuards(), getDynamicTypes());
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
                if (cache.getDefaultExpression().findBoundVariableElements().contains(frame.getVariableElement())) {
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

    public boolean isGuardBindsCache() {
        if (!getCaches().isEmpty() && !getGuards().isEmpty()) {
            for (GuardExpression guard : getGuards()) {
                if (guard.hasErrors()) {
                    continue;
                }
                if (isDynamicParameterBound(guard.getExpression(), true)) {
                    if (isCacheParameterBound(guard)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isCacheParameterBound(GuardExpression guard) {
        for (CacheExpression cache : getBoundCaches(guard.getExpression(), false)) {
            if (cache.isAlwaysInitialized()) {
                continue;
            } else if (!guard.isLibraryAcceptsGuard() && cache.isCachedLibrary()) {
                continue;
            } else if (guard.isWeakReferenceGuard() && cache.isWeakReference()) {
                continue;
            }
            return true;
        }
        return false;
    }

    public boolean isConstantLimit() {
        if (isGuardBindsCache()) {
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
        if (isGuardBindsCache()) {
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

        if (prev.isGuardBindsCache()) {
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

}
