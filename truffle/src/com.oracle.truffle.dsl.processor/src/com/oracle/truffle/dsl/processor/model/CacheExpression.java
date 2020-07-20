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

import static com.oracle.truffle.dsl.processor.java.ElementUtils.getAnnotationValue;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.expression.DSLExpression;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Binary;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Call;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.DSLExpressionReducer;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Negate;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Variable;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

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
    private String sharedGroup;
    private boolean mergedLibrary;
    private boolean guardForNull;
    private boolean isWeakReference;
    private boolean adopt = true;

    private TypeMirror languageType;
    private TypeMirror referenceType;

    public CacheExpression(Parameter sourceParameter, AnnotationMirror sourceAnnotationMirror) {
        this.sourceParameter = sourceParameter;
        this.sourceAnnotationMirror = sourceAnnotationMirror;
    }

    public CacheExpression copy() {
        CacheExpression copy = new CacheExpression(sourceParameter, sourceAnnotationMirror);
        copy.dimensions = this.dimensions;
        copy.defaultExpression = this.defaultExpression;
        copy.uncachedExpression = this.uncachedExpression;
        copy.alwaysInitialized = this.alwaysInitialized;
        copy.sharedGroup = this.sharedGroup;
        return copy;
    }

    public void setLanguageType(TypeMirror languageType) {
        this.languageType = languageType;
    }

    public boolean isReference() {
        if (isCachedLanguage()) {
            return !ElementUtils.typeEquals(getLanguageType(), getParameter().getType());
        } else {
            return ElementUtils.typeEquals(getReferenceType(), getParameter().getType());
        }
    }

    public boolean isEagerInitialize() {
        return eagerInitialize;
    }

    public void setEagerInitialize(boolean alreadyInitialized) {
        this.eagerInitialize = alreadyInitialized;
    }

    public TypeMirror getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(TypeMirror supplierType) {
        this.referenceType = supplierType;
    }

    public TypeMirror getLanguageType() {
        return languageType;
    }

    public void setSharedGroup(String sharedGroup) {
        this.sharedGroup = sharedGroup;
    }

    public AnnotationMirror getSharedGroupMirror() {
        return ElementUtils.findAnnotationMirror(sourceParameter.getVariableElement(), types.Cached_Shared);
    }

    public AnnotationValue getSharedGroupValue() {
        AnnotationMirror sharedAnnotation = getSharedGroupMirror();
        if (sharedAnnotation != null) {
            return getAnnotationValue(sharedAnnotation, "value");
        }
        return null;
    }

    public String getSharedGroup() {
        AnnotationMirror sharedAnnotation = getSharedGroupMirror();
        if (sharedAnnotation != null) {
            return getAnnotationValue(String.class, sharedAnnotation, "value");
        }
        return null;
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

    public boolean isCachedContext() {
        return isType(types.CachedContext);
    }

    public boolean isCachedLanguage() {
        return isType(types.CachedLanguage);
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

    public void setGuardForNull(boolean b) {
        this.guardForNull = b;
    }

    public boolean isGuardForNull() {
        return guardForNull;
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

}
