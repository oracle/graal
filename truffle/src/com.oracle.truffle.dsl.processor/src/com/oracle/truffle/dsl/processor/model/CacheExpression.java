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

import static com.oracle.truffle.dsl.processor.java.ElementUtils.getAnnotationValue;

import java.util.Objects;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.type.DeclaredType;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.expression.DSLExpression;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Binary;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Call;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.DSLExpressionReducer;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Negate;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Variable;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.library.LibraryData;

public final class CacheExpression extends MessageContainer {

    private final Parameter sourceParameter;
    private final AnnotationMirror sourceAnnotationMirror;
    private int dimensions = -1;
    private DSLExpression defaultExpression;
    private DSLExpression uncachedExpression;
    private boolean alwaysInitialized;
    private boolean eagerInitialize;
    private Message uncachedExpressionError;
    private boolean requiresBoundary;
    private boolean mergedLibrary;
    private boolean isWeakReferenceGet;
    private boolean isWeakReference;
    private boolean adopt = true;
    private boolean neverDefault;
    private boolean neverDefaultGuaranteed;
    private InlinedNodeData inlinedNode;

    private LibraryData cachedlibrary;
    private boolean usedInGuard;

    private AnnotationMirror sharedGroupMirror;
    private AnnotationValue sharedGroupValue;
    private String sharedGroup;

    public CacheExpression(Parameter sourceParameter, AnnotationMirror sourceAnnotationMirror) {
        this.sourceParameter = sourceParameter;
        this.sourceAnnotationMirror = sourceAnnotationMirror;
        this.sharedGroupMirror = ElementUtils.findAnnotationMirror(sourceParameter.getVariableElement(), types.Cached_Shared);
        this.sharedGroupValue = sharedGroupMirror != null ? getAnnotationValue(sharedGroupMirror, "value") : null;
        this.sharedGroup = sharedGroupMirror != null ? getAnnotationValue(String.class, sharedGroupMirror, "value", false) : null;
        if (this.sharedGroupMirror != null && sharedGroup == null) {
            this.sharedGroup = sourceParameter.getVariableElement().getSimpleName().toString();
        }
    }

    public CacheExpression copy() {
        CacheExpression copy = new CacheExpression(sourceParameter, sourceAnnotationMirror);
        copy.dimensions = this.dimensions;
        copy.defaultExpression = this.defaultExpression;
        copy.uncachedExpression = this.uncachedExpression;
        copy.alwaysInitialized = this.alwaysInitialized;
        copy.eagerInitialize = this.eagerInitialize;
        copy.uncachedExpressionError = this.uncachedExpressionError;
        copy.requiresBoundary = this.requiresBoundary;
        copy.mergedLibrary = this.mergedLibrary;
        copy.isWeakReference = this.isWeakReference;
        copy.isWeakReferenceGet = this.isWeakReferenceGet;
        copy.adopt = this.adopt;
        copy.inlinedNode = this.inlinedNode != null ? this.inlinedNode.copy() : null;
        copy.cachedlibrary = cachedlibrary;
        copy.usedInGuard = usedInGuard;
        copy.neverDefault = neverDefault;
        copy.neverDefaultGuaranteed = neverDefaultGuaranteed;
        return copy;
    }

    public void setIsUsedInGuard(boolean b) {
        this.usedInGuard = b;
    }

    public boolean isUsedInGuard() {
        return usedInGuard;
    }

    public boolean isNeverDefault() {
        return neverDefault;
    }

    public void setNeverDefault(boolean neverDefault) {
        this.neverDefault = neverDefault;
    }

    public boolean isNeverDefaultGuaranteed() {
        return neverDefaultGuaranteed;
    }

    public void setNeverDefaultGuaranteed(boolean neverDefault) {
        this.neverDefaultGuaranteed = neverDefault;
    }

    public void setInlinedNode(InlinedNodeData inlinedNode) {
        this.inlinedNode = inlinedNode;
    }

    public InlinedNodeData getInlinedNode() {
        return inlinedNode;
    }

    public boolean isEagerInitialize() {
        return eagerInitialize;
    }

    public void setEagerInitialize(boolean alreadyInitialized) {
        this.eagerInitialize = alreadyInitialized;
    }

    public void clearSharing() {
        this.sharedGroup = null;
        this.sharedGroupMirror = null;
        this.sharedGroupValue = null;
    }

    public AnnotationMirror getSharedGroupMirror() {
        return sharedGroupMirror;
    }

    public boolean isEncodedEnum() {
        if (!isCached()) {
            return false;
        }
        return ElementUtils.isAssignable(getParameter().getType(), ProcessorContext.getInstance().getType(Enum.class));
    }

    public AnnotationValue getSharedGroupValue() {
        return sharedGroupValue;
    }

    public String getSharedGroup() {
        return sharedGroup;
    }

    public void setDefaultExpression(DSLExpression expression) {
        this.defaultExpression = expression;
    }

    public void setUncachedExpressionError(Message message) {
        this.uncachedExpressionError = message;
    }

    public void setUncachedExpression(DSLExpression getUncachedExpression) {
        this.uncachedExpression = getUncachedExpression;
    }

    public Message getUncachedExpresionError() {
        return uncachedExpressionError;
    }

    public DSLExpression getUncachedExpression() {
        return uncachedExpression;
    }

    public void setAlwaysInitialized(boolean fastPathCache) {
        this.alwaysInitialized = fastPathCache;
    }

    public boolean isAlwaysInitialized() {
        return alwaysInitialized;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public int getDimensions() {
        return dimensions;
    }

    public Parameter getParameter() {
        return sourceParameter;
    }

    public boolean isCached() {
        return isType(types.Cached);
    }

    public boolean isBind() {
        return isType(types.Bind);
    }

    public boolean isCachedLibrary() {
        return isType(types.CachedLibrary);
    }

    public boolean isCachedLibraryManuallyDispatched() {
        return isType(types.CachedLibrary);
    }

    public String getCachedLibraryExpression() {
        if (!isCachedLibrary()) {
            return null;
        }
        return ElementUtils.getAnnotationValue(String.class, getMessageAnnotation(), "value", false);
    }

    public String getCachedLibraryLimit() {
        if (!isCachedLibrary()) {
            return null;
        }
        return ElementUtils.getAnnotationValue(String.class, getMessageAnnotation(), "limit", false);
    }

    private boolean isType(DeclaredType type) {
        return ElementUtils.typeEquals(sourceAnnotationMirror.getAnnotationType(), type);
    }

    @Override
    public Element getMessageElement() {
        return sourceParameter.getVariableElement();
    }

    @Override
    public AnnotationMirror getMessageAnnotation() {
        return sourceAnnotationMirror;
    }

    public void setRequiresBoundary(boolean requiresBoundary) {
        this.requiresBoundary = requiresBoundary;
    }

    public boolean isRequiresBoundary() {
        return requiresBoundary;
    }

    public DSLExpression getDefaultExpression() {
        return defaultExpression;
    }

    public void setMergedLibrary(boolean mergedLibrary) {
        this.mergedLibrary = mergedLibrary;
    }

    public boolean isMergedLibrary() {
        return mergedLibrary;
    }

    public boolean isThisExpression() {
        DSLExpression e = getDefaultExpression();
        if (!(e instanceof Variable)) {
            return false;
        }
        Variable v = (Variable) e;
        if (v.getResolvedVariable() instanceof CodeVariableElement) {
            if (v.getResolvedVariable().getSimpleName().toString().equals("this")) {
                return true;
            }
        }

        return false;
    }

    public String getMergedLibraryIdentifier() {
        DSLExpression identifierExpression = getDefaultExpression().reduce(new DSLExpressionReducer() {

            public DSLExpression visitVariable(Variable binary) {
                if (binary.getReceiver() == null) {
                    Variable var = new Variable(binary.getReceiver(), "receiver");
                    var.setResolvedTargetType(binary.getResolvedTargetType());
                    var.setResolvedVariable(new CodeVariableElement(binary.getResolvedType(), "receiver"));
                    return var;
                } else {
                    return binary;
                }
            }

            public DSLExpression visitNegate(Negate negate) {
                return negate;
            }

            public DSLExpression visitCall(Call binary) {
                return binary;
            }

            public DSLExpression visitBinary(Binary binary) {
                return binary;
            }
        });
        String expressionText = identifierExpression.asString();
        StringBuilder b = new StringBuilder(expressionText);
        boolean nextUpperCase = false;
        int i = 0;
        while (i < b.length()) {
            char charAt = b.charAt(i);
            if (!Character.isJavaIdentifierPart(charAt)) {
                b.deleteCharAt(i);
                nextUpperCase = true;
            } else if (nextUpperCase) {
                nextUpperCase = false;
                if (i != 0) {
                    b.setCharAt(i, Character.toUpperCase(b.charAt(i)));
                }
                i++;
            } else {
                i++;
            }
        }
        String libraryName = ElementUtils.getSimpleName(getParameter().getType());

        return b.toString() + libraryName + "_";
    }

    public void setWeakReferenceGet(boolean b) {
        this.isWeakReferenceGet = b;
    }

    public boolean isWeakReferenceGet() {
        return isWeakReferenceGet;
    }

    public void setWeakReference(boolean ignoreInUncached) {
        this.isWeakReference = ignoreInUncached;
    }

    public boolean isWeakReference() {
        return isWeakReference;
    }

    public boolean isAdopt() {
        return adopt;
    }

    public void setAdopt(boolean adopt) {
        this.adopt = adopt;
    }

    public LibraryData getCachedLibrary() {
        return cachedlibrary;
    }

    public void setCachedLibrary(LibraryData cachedlibrary) {
        this.cachedlibrary = cachedlibrary;
    }

    public boolean usesDefaultCachedInitializer() {
        return ElementUtils.getAnnotationValue(getMessageAnnotation(), "value", false) == null;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + Integer.toHexString(hashCode()) + "[" + Objects.toString(sourceParameter) + "]";
    }

}
