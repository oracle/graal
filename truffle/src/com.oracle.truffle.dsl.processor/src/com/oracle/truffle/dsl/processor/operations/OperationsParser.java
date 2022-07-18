/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.operations;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.getAnnotationValue;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getQualifiedName;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleProcessorOptions;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.compiler.CompilerFactory;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONTokener;

public class OperationsParser extends AbstractParser<OperationsData> {

    private static final Set<TypeKind> UNBOXABLE_TYPE_KINDS = Set.of(TypeKind.BOOLEAN, TypeKind.BYTE, TypeKind.INT, TypeKind.FLOAT, TypeKind.LONG, TypeKind.DOUBLE);

    @SuppressWarnings("unchecked")
    @Override
    protected OperationsData parse(Element element, List<AnnotationMirror> mirror) {

        TypeElement typeElement = (TypeElement) element;
        AnnotationMirror generateOperationsMirror = ElementUtils.findAnnotationMirror(mirror, types.GenerateOperations);

        OperationsData data = new OperationsData(ProcessorContext.getInstance(), typeElement, generateOperationsMirror);

        // check basic declaration properties
        if (!typeElement.getModifiers().contains(Modifier.FINAL)) {
            data.addError(typeElement, "Operations class must be declared final.");
        }

        if (ElementUtils.findParentEnclosingType(typeElement).isPresent()) {
            data.addError(typeElement, "Operations class must be a top-level class.");
        }

        // find all metadata
        for (VariableElement ve : ElementFilter.fieldsIn(typeElement.getEnclosedElements())) {
            if (ElementUtils.findAnnotationMirror(ve, types.GenerateOperations_Metadata) != null) {
                OperationMetadataData metadataData = new OperationMetadataParser(data).parse(ve, false);
                if (metadataData == null) {
                    data.addError(ve, "Could not parse metadata");
                } else {
                    data.getMetadatas().add(metadataData);
                }
            }
        }

        // find @GenerateUncached
        AnnotationMirror generateUncachedMirror = ElementUtils.findAnnotationMirror(typeElement, types.GenerateUncached);
        if (generateUncachedMirror != null) {
            data.setGenerateUncached(true);
        }

        // find and bind type system
        AnnotationMirror typeSystemRefMirror = ElementUtils.findAnnotationMirror(typeElement, types.TypeSystemReference);
        if (typeSystemRefMirror != null) {
            TypeMirror typeSystemType = getAnnotationValue(TypeMirror.class, typeSystemRefMirror, "value");
            TypeSystemData typeSystem = (TypeSystemData) context.getTemplate(typeSystemType, true);
            if (typeSystem == null) {
                data.addError("The used type system '%s' is invalid. Fix errors in the type system first.", getQualifiedName(typeSystemType));
                return data;
            }

            data.setTypeSystem(typeSystem);
        } else {
            data.setTypeSystem(new TypeSystemData(context, typeElement, null, true));
        }

        // find and bind boxing elimination types
        List<AnnotationValue> boxingEliminatedTypes = (List<AnnotationValue>) ElementUtils.getAnnotationValue(generateOperationsMirror, "boxingEliminationTypes").getValue();
        for (AnnotationValue value : boxingEliminatedTypes) {
            Set<TypeKind> beTypes = data.getBoxingEliminatedTypes();
            TypeMirror mir;
            if (value.getValue() instanceof Class<?>) {
                mir = context.getType((Class<?>) value.getValue());
            } else if (value.getValue() instanceof TypeMirror) {
                mir = (TypeMirror) value.getValue();
            } else {
                throw new AssertionError();
            }

            if (UNBOXABLE_TYPE_KINDS.contains(mir.getKind())) {
                beTypes.add(mir.getKind());
            } else {
                data.addError("Cannot perform boxing elimination on %s", mir);
            }
        }

        List<TypeElement> operationTypes = new ArrayList<>(ElementFilter.typesIn(typeElement.getEnclosedElements()));
        List<AnnotationMirror> opProxies = ElementUtils.getRepeatedAnnotation(typeElement.getAnnotationMirrors(), types.OperationProxy);

        // find and bind all operation proxies
        for (AnnotationMirror mir : opProxies) {
            SingleOperationData opData = new SingleOperationParser(data, mir).parse(null, null);

            if (opData == null) {
                data.addError(mir, ElementUtils.getAnnotationValue(mir, "value"), "Could not proxy operation");
                continue;
            }

            data.addOperationData(opData);
        }

        // find and bind all inner declared operations
        for (TypeElement inner : operationTypes) {
            if (ElementUtils.findAnnotationMirror(inner, types.Operation) == null) {
                continue;
            }

            SingleOperationData opData = new SingleOperationParser(data).parse(inner, false);

            if (opData != null) {
                data.addOperationData(opData);
            } else {
                data.addError("Could not generate operation: " + inner.getSimpleName());
            }

            opData.redirectMessagesOnGeneratedElements(data);
        }

        // find and bind all sc operations
        List<AnnotationMirror> scOperations = ElementUtils.getRepeatedAnnotation(typeElement.getAnnotationMirrors(), types.ShortCircuitOperation);
        for (AnnotationMirror mir : scOperations) {
            SingleOperationData opData = new SingleOperationParser(data, mir, true).parse(null, null);
            if (opData == null) {
                data.addError(mir, ElementUtils.getAnnotationValue(mir, "name"), "Clould not proxy short circuit operation");
                continue;
            }

            data.addOperationData(opData);
            opData.setShortCircuitContinueWhen((boolean) ElementUtils.getAnnotationValue(mir, "continueWhen").getValue());
        }

        if (opProxies.isEmpty() && operationTypes.isEmpty()) {
            data.addWarning("No operations found");
        }

        if (data.hasErrors()) {
            return data;
        }

        String decisionsFilePath = getMainDecisionsFilePath(typeElement, generateOperationsMirror);
        data.setDecisionsFilePath(decisionsFilePath);

        AnnotationValue forceTracingValue = ElementUtils.getAnnotationValue(generateOperationsMirror, "forceTracing", true);

        boolean isTracing;
        if ((boolean) forceTracingValue.getValue()) {
            isTracing = true;
            data.addWarning("Tracing compilation is forced. This should only be used during development.");
            if (decisionsFilePath == null) {
                data.addError("Tracing forced, but no decisions file specified! Specify the tracing decisions file.");
            }
        } else if (decisionsFilePath == null) {
            // decisions file not specified, can't trace no matter the options
            isTracing = false;
        } else {
            isTracing = TruffleProcessorOptions.operationsEnableTracing(processingEnv);
        }

        if (!isTracing) {
            OperationDecisions decisions = parseDecisions(typeElement, generateOperationsMirror, data);
            data.setDecisions(decisions);
        }

        data.setTracing(isTracing);

        if (data.hasErrors()) {
            return data;
        }

        data.initializeContext();

        return data;
    }

    private String getMainDecisionsFilePath(TypeElement element, AnnotationMirror generateOperationsMirror) {
        String file = (String) ElementUtils.getAnnotationValue(generateOperationsMirror, "decisionsFile").getValue();
        if (file == null || file.isEmpty()) {
            return null;
        }

        return getDecisionsFile(element, file).getAbsolutePath();
    }

    private File getDecisionsFile(TypeElement element, String path) {
        File file = CompilerFactory.getCompiler(element).getEnclosingSourceFile(processingEnv, element);
        String parent = file.getParent();
        File target = new File(parent, path);

        return target;
    }

    @SuppressWarnings("unchecked")
    private OperationDecisions parseDecisions(TypeElement element, AnnotationMirror generateOperationsMirror, OperationsData data) {
        String file = (String) ElementUtils.getAnnotationValue(generateOperationsMirror, "decisionsFile").getValue();
        OperationDecisions mainDecisions;

        if (file == null || file.isEmpty()) {
            mainDecisions = new OperationDecisions();
        } else {
            mainDecisions = parseDecisions(element, file, data, true);
        }

        if (mainDecisions == null) {
            return null;
        }

        List<AnnotationValue> overrideFiles = (List<AnnotationValue>) ElementUtils.getAnnotationValue(generateOperationsMirror, "decisionOverrideFiles").getValue();

        for (AnnotationValue overrideFile : overrideFiles) {
            OperationDecisions overrideDecision = parseDecisions(element, (String) overrideFile.getValue(), data, false);
            if (overrideDecision != null) {
                mainDecisions.merge(overrideDecision, data);
            }
        }

        return mainDecisions;
    }

    private OperationDecisions parseDecisions(TypeElement element, String path, OperationsData data, boolean isMain) {
        File target = getDecisionsFile(element, path);
        try {
            FileInputStream fi = new FileInputStream(target);
            JSONArray o = new JSONArray(new JSONTokener(fi));
            return OperationDecisions.deserialize(o, data);
        } catch (FileNotFoundException ex) {
            if (isMain) {
                data.addError("Decisions file '%s' not found. Build & run with tracing to generate it.", path);
            } else {
                data.addError("Decisions file '%s' not found.", path);
            }
        }
        return null;
    }

    @Override
    public DeclaredType getAnnotationType() {
        return types.GenerateOperations;
    }

}
