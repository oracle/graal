/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.library;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.Template;

public final class LibraryData extends Template {

    private final List<LibraryMessage> methods = new ArrayList<>();
    private final List<LibraryData> superTypes = new ArrayList<>();
    private List<TypeMirror> cachedSignature;
    private List<String> cachedSignatureNames;

    private final List<LibraryDefaultExportData> defaultExports = new ArrayList<>();
    private TypeMirror signatureReceiverType;
    private TypeMirror exportsReceiverType;
    private TypeMirror assertions;

    private ExportsLibrary objectExports;
    private boolean defaultExportLookupEnabled;
    private boolean dynamicDispatchEnabled = true;

    public LibraryData(TypeElement type, AnnotationMirror annotationMirror) {
        super(ProcessorContext.getInstance(), type, annotationMirror);
    }

    public void setDynamicDispatchEnabled(boolean dynamicDispatchEnabled) {
        this.dynamicDispatchEnabled = dynamicDispatchEnabled;
    }

    public boolean isDynamicDispatchEnabled() {
        return dynamicDispatchEnabled;
    }

    public void setDefaultExportLookupEnabled(boolean defaultExportLookupEnabled) {
        this.defaultExportLookupEnabled = defaultExportLookupEnabled;
    }

    public boolean isDefaultExportLookupEnabled() {
        return defaultExportLookupEnabled;
    }

    public void setExportsReceiverType(TypeMirror receiverType) {
        this.exportsReceiverType = receiverType;
    }

    public void setSignatureReceiverType(TypeMirror signatureReceiverType) {
        this.signatureReceiverType = signatureReceiverType;
    }

    public TypeMirror getSignatureReceiverType() {
        return signatureReceiverType;
    }

    public TypeMirror getExportsReceiverType() {
        return exportsReceiverType;
    }

    public List<LibraryData> getSuperTypes() {
        return superTypes;
    }

    public List<LibraryMessage> getMethods() {
        return methods;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected List<MessageContainer> findChildContainers() {
        return (List<MessageContainer>) (List<?>) methods;
    }

    void setCachedSignatureNames(List<String> cachedSignatureNames) {
        this.cachedSignatureNames = cachedSignatureNames;
    }

    public List<String> getCachedSignatureNames() {
        return cachedSignatureNames;
    }

    void setCachedSignature(List<TypeMirror> cachedSignature) {
        this.cachedSignature = cachedSignature;
    }

    public List<TypeMirror> getCachedSignature() {
        return cachedSignature;
    }

    public List<LibraryDefaultExportData> getDefaultExports() {
        return defaultExports;
    }

    public void setAssertions(TypeMirror assertions) {
        this.assertions = assertions;
    }

    public TypeMirror getAssertions() {
        return assertions;
    }

    public boolean isDynamicDispatch() {
        return getTemplateType().getSimpleName().toString().equals(types.DynamicDispatchLibrary.asElement().getSimpleName().toString());
    }

    void setObjectExports(ExportsLibrary objectExports) {
        this.objectExports = objectExports;
    }

    public ExportsLibrary getObjectExports() {
        return objectExports;
    }

    public LibraryDefaultExportData getBuiltinDefaultExport(TypeMirror receiverType) {
        for (LibraryDefaultExportData export : defaultExports) {
            if (export.isDefaultObjectExport()) {
                continue;
            }
            if (ElementUtils.isAssignable(export.getReceiverType(), receiverType)) {
                return export;
            }
            if (ElementUtils.isAssignable(ProcessorContext.getInstance().getType(Object.class), export.getReceiverType())) {
                return export;
            }
        }
        return null;
    }

}
