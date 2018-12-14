/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.expression.DSLExpression;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

public final class CacheExpression extends MessageContainer {

    private final Parameter sourceParameter;
    private final AnnotationMirror sourceAnnotationMirror;
    private int dimensions = -1;
    private DSLExpression defaultExpression;
    private DSLExpression uncachedExpression;
    private boolean initializedInFastPath = false;
    private Message uncachedExpressionError;
    private boolean requiresBoundary;
    private Cached.Scope scope;
    private String sharedGroup;

    public CacheExpression(Parameter sourceParameter, AnnotationMirror sourceAnnotationMirror) {
        this.sourceParameter = sourceParameter;
        this.sourceAnnotationMirror = sourceAnnotationMirror;
    }

    public CacheExpression copy() {
        CacheExpression copy = new CacheExpression(sourceParameter, sourceAnnotationMirror);
        copy.dimensions = this.dimensions;
        copy.defaultExpression = this.defaultExpression;
        copy.uncachedExpression = this.uncachedExpression;
        copy.initializedInFastPath = this.initializedInFastPath;
        copy.sharedGroup = this.sharedGroup;
        return copy;
    }

    public void setSharedGroup(String sharedGroup) {
        this.sharedGroup = sharedGroup;
    }

    public AnnotationMirror getSharedGroupMirror() {
        return ElementUtils.findAnnotationMirror(sourceParameter.getVariableElement(), Shared.class);
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

    public Cached.Scope getScope() {
        return scope;
    }

    void setScope(Cached.Scope scope) {
        this.scope = scope;
    }

    public void setInitializedInFastPath(boolean fastPathCache) {
        this.initializedInFastPath = fastPathCache;
    }

    public boolean isInitializedInFastPath() {
        return initializedInFastPath;
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
        return isType(Cached.class);
    }

    public boolean isCachedLibrary() {
        return isType(CachedLibrary.class);
    }

    private boolean isType(Class<?> type) {
        return ElementUtils.typeEquals(sourceAnnotationMirror.getAnnotationType(), ProcessorContext.getInstance().getType(type));
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

}
