/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.codegen.processor.operation;

import java.lang.annotation.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.ast.*;
import com.oracle.truffle.codegen.processor.template.*;
import com.oracle.truffle.codegen.processor.template.ParameterSpec.Kind;
import com.oracle.truffle.codegen.processor.typesystem.*;

public class OperationParser extends TemplateParser<OperationData> {

    public OperationParser(ProcessorContext c) {
        super(c);
    }

    @Override
    public Class< ? extends Annotation> getAnnotationType() {
        return com.oracle.truffle.api.codegen.Operation.class;
    }

    @Override
    protected OperationData parse(Element element, AnnotationMirror templateTypeAnnotation) {
        TypeElement templateType = (TypeElement) element;

        if (!verifyTemplateType(templateType, templateTypeAnnotation)) {
            return null;
        }

        TypeMirror typeSystemMirror = Utils.getAnnotationValueType(templateTypeAnnotation, "typeSystem");
        final TypeSystemData typeSystem = (TypeSystemData) context.getTemplate(typeSystemMirror, true);
        if (typeSystem == null) {
            log.error(templateType, templateTypeAnnotation, "Type system '%s' is invalid.", Utils.getQualifiedName(typeSystemMirror));
            return null;
        }

        TypeMirror nodeType = Utils.getAnnotationValueType(templateTypeAnnotation, "baseClass");
        if (Utils.typeEquals(nodeType, context.getTruffleTypes().getNode())) {
            nodeType = typeSystem.getNodeType();
        }

        if (!Utils.isAssignable(nodeType, typeSystem.getNodeType())) {
            log.error(templateType, templateTypeAnnotation,
                            Utils.getAnnotationValue(templateTypeAnnotation, "baseClass"),
                            "The baseClass does not extend the base class of the type system '%s'.", Utils.getQualifiedName(typeSystem.getNodeType()));
            return null;
        }

        for (VariableElement field : ElementFilter.fieldsIn(templateType.getEnclosedElements())) {
            if (!field.getModifiers().contains(Modifier.STATIC)
                            && !field.getModifiers().contains(Modifier.FINAL)) {
                log.error(field, "Field must be final.");
                return null;
            }
        }


        List<String> valueNames = Utils.getAnnotationValueList(templateTypeAnnotation, "values");
        List<String> shortCircuitNames = Utils.getAnnotationValueList(templateTypeAnnotation, "shortCircuitValues");

        List<String> names = new ArrayList<>();
        names.addAll(valueNames);
        names.addAll(shortCircuitNames);

        List<AnnotationMirror> fieldAnnotations = Collections.emptyList(); // call collectionAnnotations instead if you want OperationField support enabled.
        List<OperationFieldData> fields = new ArrayList<>();
        for (AnnotationMirror fieldMirror : fieldAnnotations) {
            String name = Utils.getAnnotationValueString(fieldMirror, "name");
            TypeMirror javaClass = Utils.getAnnotationValueType(fieldMirror, "javaClass");
            fields.add(new OperationFieldData(name, javaClass));
            names.add(name);
        }

        List<OperationFieldData> constructorFields = parseConstructorFields(templateType, true);
        if (constructorFields == null) {
            return null;
        }

        List<OperationFieldData> superConstructorFields = parseConstructorFields(Utils.fromTypeMirror(nodeType), false);
        if (superConstructorFields == null) {
            return null;
        }

        List<OperationFieldData> protectedSuperFields = parseProtectedFields(Utils.fromTypeMirror(nodeType));
        if (protectedSuperFields == null) {
            return null;
        }

        List<OperationFieldData> matchedSuperFields = matchFields(superConstructorFields, protectedSuperFields);
        if (matchedSuperFields == null) {
            log.error(templateType, templateTypeAnnotation, Utils.getAnnotationValue(templateTypeAnnotation, "baseClass"),
                            "The signature of the protected fields (%s) and the first constructor(%s) in %s does not match.",
                            protectedSuperFields.toString(),
                            superConstructorFields.toString(),
                            Utils.getQualifiedName(nodeType));
            return null;
        }

        for (OperationFieldData field : constructorFields) {
            names.add(field.getName());
        }

        for (OperationFieldData field : matchedSuperFields) {
            names.add(field.getName());
        }

        if (!verifyNames(templateType, templateTypeAnnotation, names)) {
            return null;
        }

        OperationData operationData = new OperationData(templateType, templateTypeAnnotation, typeSystem, nodeType,
                        valueNames.toArray(new String[valueNames.size()]),
                        shortCircuitNames.toArray(new String[shortCircuitNames.size()]),
                        fields.toArray(new OperationFieldData[fields.size()]),
                        constructorFields.toArray(new OperationFieldData[constructorFields.size()]),
                        matchedSuperFields.toArray(new OperationFieldData[matchedSuperFields.size()]));

        if (!verifyExclusiveMethodAnnotation(templateType,
                        Specialization.class, Generic.class, SpecializationListener.class, ShortCircuit.class, GuardCheck.class)) {
            return noFactory(operationData);
        }

        operationData.setExtensionElements(getExtensionParser().parseAll(templateType));

        List<SpecializationData> genericSpecializations = parseMethods(operationData, new GenericParser(context, operationData));
        List<GuardData> guards = parseMethods(operationData, new GuardParser(context, operationData.getTypeSystem(), operationData));
        operationData.setGuards(guards.toArray(new GuardData[guards.size()]));

        SpecializationParser specializationParser = new SpecializationParser(context, operationData);
        operationData.setSpecification(specializationParser.getSpecification());
        List<SpecializationData> specializations = parseMethods(operationData, specializationParser);
        List<ShortCircuitData> shortCircuits = parseMethods(operationData, new ShortCircuitParser(context, operationData));
        List<TemplateMethod> listeners = parseMethods(operationData, new SpecializationListenerParser(context, operationData));

        if (specializations == null || genericSpecializations == null || shortCircuits == null  || listeners == null || guards == null) {
            return noFactory(operationData);
        }


        SpecializationData genericSpecialization = null;
        if (genericSpecializations.size() > 1) {
            for (SpecializationData generic : genericSpecializations) {
                log.error(generic.getMethod(), "Only one method with @%s is allowed per operation.", Generic.class.getSimpleName());
            }
            return noFactory(operationData);
        } else if (genericSpecializations.size() == 1) {
            genericSpecialization = genericSpecializations.get(0);
        }

        if (specializations.size() > 1 && genericSpecialization == null) {
            log.error(templateType, "Need a @%s method.", Generic.class.getSimpleName());
            return noFactory(operationData);
        }

        Collections.sort(specializations, new Comparator<SpecializationData>() {
            @Override
            public int compare(SpecializationData o1, SpecializationData o2) {
                return compareSpecialization(typeSystem, o1, o2);
            }
        });

        List<SpecializationData> allSpecializations = new ArrayList<>(specializations);
        if (genericSpecialization != null) {
            allSpecializations.add(genericSpecialization);
            TemplateMethod uninializedMethod = new TemplateMethod(genericSpecialization.getSpecification(), new CodeExecutableElement(context.getType(void.class), "doUninialized"),
                            genericSpecialization.getMarkerAnnotation(), genericSpecialization.getReturnType(), genericSpecialization.getParameters());
            allSpecializations.add(0, new SpecializationData(uninializedMethod, false, true));
        }

        // verify order is not ambiguous
        verifySpecializationOrder(typeSystem, specializations);

        operationData.setGenericSpecialization(genericSpecialization);
        operationData.setSpecializations(allSpecializations.toArray(new SpecializationData[allSpecializations.size()]));
        operationData.setSpecializationListeners(listeners.toArray(new TemplateMethod[listeners.size()]));

        if (!assignShortCircuitsToSpecializations(operationData, allSpecializations, shortCircuits)) {
            return null;
        }

        if (!verifyNamingConvention(specializations, "do")) {
            return noFactory(operationData);
        }

        if (!verifyNamesUnique(specializations)) {
            return noFactory(operationData);
        }

        if (!verifyNamingConvention(shortCircuits, "needs")) {
            return noFactory(operationData);
        }

        if (!verifySpecializationThrows(typeSystem, specializations)) {
            return noFactory(operationData);
        }

        return operationData;
    }

    private static List<OperationFieldData> matchFields(List<OperationFieldData> params,
                    List<OperationFieldData> fields) {

        if (params.size() != fields.size()) {
            return null;
        }

        List<OperationFieldData> matchedFields = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            OperationFieldData param = params.get(i);
            OperationFieldData field = fields.get(i);
            if (!Utils.typeEquals(param.getJavaClass(), field.getJavaClass())) {
                return null;
            }
            matchedFields.add(new OperationFieldData(field.getName(), param.getJavaClass()));
        }

        return matchedFields;
    }

    private static List<OperationFieldData> parseProtectedFields(Element element) {
        List<OperationFieldData> opFields = new ArrayList<>();
        List<VariableElement> fields = ElementFilter.fieldsIn(element.getEnclosedElements());
        for (VariableElement var : fields) {
            if (var.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }

            if (var.getModifiers().contains(Modifier.PROTECTED)) {
                opFields.add(new OperationFieldData(var.getSimpleName().toString(), var.asType()));
            }
        }
        return opFields;

    }

    private List<OperationFieldData> parseConstructorFields(Element element, boolean failOnMultipleConstructors) {
        if (element == null) {
            return Collections.emptyList();
        }

        List<ExecutableElement> constructors = ElementFilter.constructorsIn(element.getEnclosedElements());
        ExecutableElement constructor = null;
        if (constructors.size() > 1) {
            if (failOnMultipleConstructors) {
                for (ExecutableElement c : constructors) {
                    log.error(c, "The Operation annotated class must not define multiple constructors.");
                }
                return null;
            } else {
                // take first constructor
                constructor = constructors.get(0);
            }
        } else if (constructors.size() == 1) {
            constructor = constructors.get(0);
        }

        List<OperationFieldData> constructorFields = new ArrayList<>();
        if (constructor != null) {
            for (VariableElement var : constructor.getParameters()) {
                constructorFields.add(new OperationFieldData(var.getSimpleName().toString(), var.asType()));
            }
        }
        return constructorFields;
    }

    private static OperationData noFactory(OperationData data) {
        data.generateFactory = false;
        return data;
    }

    private boolean verifySpecializationThrows(TypeSystemData typeSystem, List<SpecializationData> specializations) {
        Map<String, SpecializationData> specializationMap = new HashMap<>();
        for (SpecializationData spec : specializations) {
            specializationMap.put(spec.getMethodName(), spec);
        }
        boolean valid = true;
        for (SpecializationData sourceSpecialization : specializations) {
            if (sourceSpecialization.getExceptions() != null) {
                for (SpecializationThrowsData throwsData : sourceSpecialization.getExceptions()) {
                    SpecializationData targetSpecialization = specializationMap.get(throwsData.getTransitionToName());
                    AnnotationValue value = Utils.getAnnotationValue(throwsData.getAnnotationMirror(), "transitionTo");
                    if (targetSpecialization == null) {
                        log.error(throwsData.getSpecialization().getMethod(), throwsData.getAnnotationMirror(), value,
                                        "Specialization with name '%s' not found.", throwsData.getTransitionToName());
                        valid = false;
                    } else if (compareSpecialization(typeSystem, sourceSpecialization, targetSpecialization) >= 0) {
                        log.error(throwsData.getSpecialization().getMethod(), throwsData.getAnnotationMirror(), value,
                                        "The order of the target specializalization must be higher than the source specialization.", throwsData.getTransitionToName());
                        valid = false;
                    }

                    for (SpecializationThrowsData otherThrowsData : sourceSpecialization.getExceptions()) {
                        if (otherThrowsData != throwsData
                                        && Utils.typeEquals(otherThrowsData.getJavaClass(), throwsData.getJavaClass())) {
                            AnnotationValue javaClassValue = Utils.getAnnotationValue(throwsData.getAnnotationMirror(), "javaClass");
                            log.error(throwsData.getSpecialization().getMethod(), throwsData.getAnnotationMirror(), javaClassValue,
                                            "Duplicate exception type.", throwsData.getTransitionToName());
                            valid = false;
                        }
                    }
                }
            }
        }
        return valid;
    }


    private boolean assignShortCircuitsToSpecializations(OperationData operation,
                    List<SpecializationData> specializations,
                    List<ShortCircuitData> shortCircuits) {

        Map<String, List<ShortCircuitData>> groupedShortCircuits = groupShortCircuits(shortCircuits);

        boolean valid = true;

        for (String valueName : operation.getShortCircuitValues()) {
            List<ShortCircuitData> availableCircuits = groupedShortCircuits.get(valueName);

            if (availableCircuits == null || availableCircuits.isEmpty()) {
                log.error(operation.getTemplateType(), operation.getTemplateTypeAnnotation(),
                                "@%s method for short cut value '%s' required.",
                                ShortCircuit.class.getSimpleName(), valueName);
                valid = false;
                continue;
            }


            boolean sameMethodName = true;
            String methodName = availableCircuits.get(0).getMethodName();
            for (ShortCircuitData circuit : availableCircuits) {
                if (!circuit.getMethodName().equals(methodName)) {
                    sameMethodName = false;
                }
            }

            if (!sameMethodName) {
                for (ShortCircuitData circuit : availableCircuits) {
                    log.error(circuit.getMethod(), circuit.getMarkerAnnotation(), "All short circuits for short cut value '%s' must have the same method name.", valueName);
                }
                valid = false;
                continue;
            }

            ShortCircuitData genericCircuit = null;
            for (ShortCircuitData circuit : availableCircuits) {
                if (isGenericShortCutMethod(circuit, operation.getTypeSystem().getGenericType())) {
                    genericCircuit = circuit;
                    break;
                }
            }

            if (genericCircuit == null) {
                log.error(operation.getTemplateType(), operation.getTemplateTypeAnnotation(),
                                "No generic @%s method available for short cut value '%s'.", ShortCircuit.class.getSimpleName(), valueName);
                valid = false;
                continue;
            }

            for (ShortCircuitData circuit : availableCircuits) {
                if (circuit != genericCircuit) {
                    circuit.setGenericShortCircuitMethod(genericCircuit);
                }
            }
        }

        if (!valid) {
            return valid;
        }

        for (SpecializationData specialization : specializations) {
            ShortCircuitData[] assignedShortCuts = new ShortCircuitData[operation.getShortCircuitValues().length];

            for (int i = 0; i < operation.getShortCircuitValues().length; i++) {
                List<ShortCircuitData> availableShortCuts = groupedShortCircuits.get(operation.getShortCircuitValues()[i]);

                ShortCircuitData genericShortCircuit = null;
                for (ShortCircuitData circuit : availableShortCuts) {
                    if (circuit.isGeneric()) {
                        genericShortCircuit = circuit;
                    } else if (circuit.isCompatibleTo(specialization)) {
                        assignedShortCuts[i] = circuit;
                    }
                }

                if (assignedShortCuts[i] == null) {
                    assignedShortCuts[i] = genericShortCircuit;
                }
            }
            specialization.setShortCircuits(assignedShortCuts);
        }
        return true;
    }

    private static boolean isGenericShortCutMethod(TemplateMethod method, TypeMirror genericType) {
        for (ActualParameter parameter : method.getParameters()) {
            if (parameter.getSpecification().getKind() == Kind.EXECUTE) {
                if (!Utils.typeEquals(genericType, parameter.getActualType())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Map<String, List<ShortCircuitData>> groupShortCircuits(List<ShortCircuitData> shortCircuits) {
        Map<String, List<ShortCircuitData>> group = new HashMap<>();
        for (ShortCircuitData shortCircuit : shortCircuits) {
            List<ShortCircuitData> circuits = group.get(shortCircuit.getValueName());
            if (circuits == null) {
                circuits = new ArrayList<>();
                group.put(shortCircuit.getValueName(), circuits);
            }
            circuits.add(shortCircuit);
        }
        return group;
    }

    private boolean verifyNamingConvention(List<? extends TemplateMethod> methods, String prefix) {
        boolean valid = true;
        for (int i = 0; i < methods.size(); i++) {
            TemplateMethod m1 = methods.get(i);
            if (m1.getMethodName().length() < 3 || !m1.getMethodName().startsWith(prefix)) {
                log.error(m1.getMethod(), m1.getMarkerAnnotation(), "Naming convention: method name must start with '%s'.", prefix);
                valid = false;
            }
        }
        return valid;
    }

    private boolean verifyNamesUnique(List<? extends TemplateMethod> methods) {
        boolean valid = true;
        for (int i = 0; i < methods.size(); i++) {
            TemplateMethod m1 = methods.get(i);
            for (int j = i + 1; j < methods.size(); j++) {
                TemplateMethod m2 = methods.get(j);

                if (m1.getMethodName().equalsIgnoreCase(m2.getMethodName())) {
                    log.error(m1.getMethod(), m1.getMarkerAnnotation(), "Method name '%s' used multiple times", m1.getMethodName());
                    log.error(m2.getMethod(), m2.getMarkerAnnotation(), "Method name '%s' used multiple times", m1.getMethodName());
                    return false;
                }
            }
        }
        return valid;
    }

    private boolean verifySpecializationOrder(TypeSystemData typeSystem, List<SpecializationData> specializations) {
        for (int i = 0; i < specializations.size(); i++) {
            SpecializationData m1 = specializations.get(i);
            for (int j = i + 1; j < specializations.size(); j++) {
                SpecializationData m2 = specializations.get(j);
                int inferredOrder = compareSpecializationWithoutOrder(typeSystem, m1, m2);

                if (m1.getOrder() != Specialization.DEFAULT_ORDER && m2.getOrder() != Specialization.DEFAULT_ORDER) {
                    int specOrder = m1.getOrder() - m2.getOrder();
                    if (specOrder == 0) {
                        log.error(m1.getMethod(), m1.getMarkerAnnotation(), "Order value %d used multiple times", m1.getOrder());
                        log.error(m2.getMethod(), m2.getMarkerAnnotation(), "Order value %d used multiple times", m1.getOrder());
                        return false;
                    } else if ((specOrder < 0 && inferredOrder > 0) || (specOrder > 0 && inferredOrder < 0)) {
                        log.error(m1.getMethod(), m1.getMarkerAnnotation(), "Explicit order values %d and %d are inconsistent with type lattice ordering.", m1.getOrder(), m2.getOrder());
                        log.error(m2.getMethod(), m2.getMarkerAnnotation(), "Explicit order values %d and %d are inconsistent with type lattice ordering.", m1.getOrder(), m2.getOrder());
                        return false;
                    }
                } else if (inferredOrder == 0) {
                    SpecializationData m = (m1.getOrder() == Specialization.DEFAULT_ORDER ? m1 : m2);
                    log.error(m.getMethod(), m.getMarkerAnnotation(), "Cannot calculate a consistent order for this specialization. Define the order attribute to resolve this.");
                    return false;
                }
            }
        }
        return true;
    }

    private static int compareSpecialization(TypeSystemData typeSystem, SpecializationData m1, SpecializationData m2) {
        int result = compareSpecializationWithoutOrder(typeSystem, m1, m2);
        if (result == 0) {
            if (m1.getOrder() != Specialization.DEFAULT_ORDER && m2.getOrder() != Specialization.DEFAULT_ORDER) {
                return m1.getOrder() - m2.getOrder();
            }
        }
        return result;
    }

    private static int compareSpecializationWithoutOrder(TypeSystemData typeSystem, SpecializationData m1, SpecializationData m2) {
        if (m1.getSpecification() != m2.getSpecification()) {
            throw new UnsupportedOperationException("Cannot compare two specializations with different specifications.");
        }

        int result = compareActualParameter(typeSystem, m1.getReturnType(), m2.getReturnType());

        for (ParameterSpec spec : m1.getSpecification().getParameters()) {
            ActualParameter p1 = m1.findParameter(spec);
            ActualParameter p2 = m2.findParameter(spec);

            if (p1 != null && p2 != null && !Utils.typeEquals(p1.getActualType(), p2.getActualType())) {
                int typeResult = compareActualParameter(typeSystem, p1, p2);
                if (result == 0) {
                    result = typeResult;
                } else if (Math.signum(result) != Math.signum(typeResult)) {
                    // We cannot define an order.
                    return 0;
                }
            }
        }
        return result;
    }

    private static int compareActualParameter(TypeSystemData typeSystem, ActualParameter p1, ActualParameter p2) {
        int index1 = typeSystem.findType(p1.getActualType());
        int index2 = typeSystem.findType(p2.getActualType());

        assert index1 != index2;
        assert !(index1 == -1 ^ index2 == -1);

        return index1 - index2;
    }

    private boolean verifyNames(TypeElement element, AnnotationMirror mirror, List<String> names) {
        boolean valid = true;

        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (!JavaName.isValid(name)) {
                log.error(element, mirror, "Name '%s' is not a valid java identifier.", name);
                valid = false;
            } else if (JavaName.isReserved(name)) {
                log.error(element, mirror, "Name '%s' is a reserved java identifier.", name);
                valid = false;
            }
            for (int j = i + 1; j < names.size(); j++) {
                String otherName = names.get(j);
                if (name.equalsIgnoreCase(otherName)) {
                    log.error(element, mirror, "Name '%s' is not unique.", name);
                    valid = false;
                }
            }
        }
        return valid;
    }
}
