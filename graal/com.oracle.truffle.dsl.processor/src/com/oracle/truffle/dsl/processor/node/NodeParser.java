/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.node;

import java.lang.annotation.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.node.NodeChildData.Cardinality;
import com.oracle.truffle.dsl.processor.node.SpecializationData.SpecializationKind;
import com.oracle.truffle.dsl.processor.template.*;
import com.oracle.truffle.dsl.processor.template.TemplateMethod.TypeSignature;
import com.oracle.truffle.dsl.processor.typesystem.*;

public class NodeParser extends AbstractParser<NodeData> {

    public static final List<Class<? extends Annotation>> ANNOTATIONS = Arrays.asList(Generic.class, TypeSystemReference.class, ShortCircuit.class, Specialization.class, NodeChild.class,
                    NodeChildren.class);

    private Map<String, NodeData> parsedNodes;

    public NodeParser(ProcessorContext c) {
        super(c);
    }

    @Override
    protected NodeData parse(Element element, AnnotationMirror mirror) {
        NodeData node = null;
        try {
            parsedNodes = new HashMap<>();
            node = resolveNode((TypeElement) element);
            if (Log.DEBUG) {
                NodeData parsed = parsedNodes.get(Utils.getQualifiedName((TypeElement) element));
                if (node != null) {
                    String dump = parsed.dump();
                    log.message(Kind.ERROR, null, null, null, dump);
                }
            }
        } finally {
            parsedNodes = null;
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
    public Class<? extends Annotation> getAnnotationType() {
        return null;
    }

    @Override
    public List<Class<? extends Annotation>> getTypeDelegatedAnnotationTypes() {
        return ANNOTATIONS;
    }

    private NodeData resolveNode(TypeElement rootType) {
        String typeName = Utils.getQualifiedName(rootType);
        if (parsedNodes.containsKey(typeName)) {
            return parsedNodes.get(typeName);
        }

        List<NodeData> enclosedNodes = new ArrayList<>();
        for (TypeElement enclosedType : ElementFilter.typesIn(rootType.getEnclosedElements())) {
            NodeData enclosedChild = resolveNode(enclosedType);
            if (enclosedChild != null) {
                enclosedNodes.add(enclosedChild);
            }
        }

        NodeData node = parseNode(rootType);
        if (node == null && !enclosedNodes.isEmpty()) {
            node = new NodeData(rootType);
        }

        if (node != null) {
            for (NodeData enclosedNode : enclosedNodes) {
                node.addEnclosedNode(enclosedNode);
            }
        }

        parsedNodes.put(typeName, node);
        return node;
    }

    private NodeData parseNode(TypeElement originalTemplateType) {
        // reloading the type elements is needed for ecj
        TypeElement templateType = Utils.fromTypeMirror(context.reloadTypeElement(originalTemplateType));

        if (Utils.findAnnotationMirror(processingEnv, originalTemplateType, GeneratedBy.class) != null) {
            // generated nodes should not get called again.
            return null;
        }

        List<TypeElement> lookupTypes = collectSuperClasses(new ArrayList<TypeElement>(), templateType);
        if (!Utils.isAssignable(context, templateType.asType(), context.getTruffleTypes().getNode())) {
            return null;
        }

        List<? extends Element> elements = context.getEnvironment().getElementUtils().getAllMembers(templateType);

        NodeData node = parseNodeData(templateType, elements, lookupTypes);
        if (node.hasErrors()) {
            return node; // error sync point
        }

        initializeChildren(node);

        node.getSpecializations().addAll(new SpecializationMethodParser(context, node).parse(elements));
        node.getSpecializations().addAll(new GenericParser(context, node).parse(elements));
        node.getCasts().addAll(new CreateCastParser(context, node).parse(elements));
        node.getShortCircuits().addAll(new ShortCircuitParser(context, node).parse(elements));

        if (node.hasErrors()) {
            return node;  // error sync point
        }

        verifySpecializationSameLength(node);
        initializeSpecializations(elements, node);
        initializeShortCircuits(node); // requires specializations and polymorphic specializations

        verifyVisibilities(node);
        verifySpecializationOrder(node);
        verifyMissingAbstractMethods(node, elements);
        verifyConstructors(node);
        verifyNamingConvention(node.getShortCircuits(), "needs");
        verifySpecializationThrows(node);
        return node;
    }

    private NodeData parseNodeData(TypeElement templateType, List<? extends Element> elements, List<TypeElement> typeHierarchy) {
        AnnotationMirror typeSystemMirror = findFirstAnnotation(typeHierarchy, TypeSystemReference.class);
        if (typeSystemMirror == null) {
            NodeData nodeData = new NodeData(templateType);
            nodeData.addError("No @%s annotation found in type hierarchy of %s.", TypeSystemReference.class.getSimpleName(), Utils.getQualifiedName(templateType));
            return nodeData;
        }

        TypeMirror typeSystemType = Utils.getAnnotationValue(TypeMirror.class, typeSystemMirror, "value");
        final TypeSystemData typeSystem = (TypeSystemData) context.getTemplate(typeSystemType, true);
        if (typeSystem == null) {
            NodeData nodeData = new NodeData(templateType);
            nodeData.addError("The used type system '%s' is invalid or not a Node.", Utils.getQualifiedName(typeSystemType));
            return nodeData;
        }

        AnnotationMirror polymorphicMirror = findFirstAnnotation(typeHierarchy, PolymorphicLimit.class);
        int polymorphicLimit = -1;
        if (polymorphicMirror != null) {
            AnnotationValue limitValue = Utils.getAnnotationValue(polymorphicMirror, "value");
            int customPolymorphicLimit = Utils.getAnnotationValue(Integer.class, polymorphicMirror, "value");
            if (customPolymorphicLimit < 1) {
                NodeData nodeData = new NodeData(templateType);
                nodeData.addError(limitValue, "Invalid polymorphic limit %s.", polymorphicLimit);
                return nodeData;
            }
            polymorphicLimit = customPolymorphicLimit;
        }

        List<String> assumptionsList = new ArrayList<>();
        for (int i = typeHierarchy.size() - 1; i >= 0; i--) {
            TypeElement type = typeHierarchy.get(i);
            AnnotationMirror assumptions = Utils.findAnnotationMirror(context.getEnvironment(), type, NodeAssumptions.class);
            if (assumptions != null) {
                List<String> assumptionStrings = Utils.getAnnotationValueList(String.class, assumptions, "value");
                for (String string : assumptionStrings) {
                    if (assumptionsList.contains(string)) {
                        assumptionsList.remove(string);
                    }
                    assumptionsList.add(string);
                }
            }
        }
        AnnotationMirror nodeInfoMirror = findFirstAnnotation(typeHierarchy, NodeInfo.class);
        String shortName = null;
        if (nodeInfoMirror != null) {
            shortName = Utils.getAnnotationValue(String.class, nodeInfoMirror, "shortName");
        }

        List<NodeFieldData> fields = parseFields(typeHierarchy, elements);
        List<NodeChildData> children = parseChildren(elements, typeHierarchy);
        List<NodeExecutionData> executions = parseExecutions(children, elements);

        NodeData nodeData = new NodeData(templateType, shortName, typeSystem, children, executions, fields, assumptionsList, polymorphicLimit);
        nodeData.setExecutableTypes(groupExecutableTypes(new ExecutableTypeMethodParser(context, nodeData).parse(elements)));

        parsedNodes.put(Utils.getQualifiedName(templateType), nodeData);

        return nodeData;
    }

    private List<NodeFieldData> parseFields(List<TypeElement> typeHierarchy, List<? extends Element> elements) {
        Set<String> names = new HashSet<>();

        List<NodeFieldData> fields = new ArrayList<>();
        for (VariableElement field : ElementFilter.fieldsIn(elements)) {
            if (field.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            if (field.getModifiers().contains(Modifier.PUBLIC) || field.getModifiers().contains(Modifier.PROTECTED)) {
                String name = field.getSimpleName().toString();
                fields.add(new NodeFieldData(field, null, field.asType(), name, false));
                names.add(name);
            }
        }

        List<TypeElement> reversedTypeHierarchy = new ArrayList<>(typeHierarchy);
        Collections.reverse(reversedTypeHierarchy);
        for (TypeElement typeElement : reversedTypeHierarchy) {
            AnnotationMirror nodeChildrenMirror = Utils.findAnnotationMirror(processingEnv, typeElement, NodeFields.class);
            List<AnnotationMirror> children = Utils.collectAnnotations(context, nodeChildrenMirror, "value", typeElement, NodeField.class);

            for (AnnotationMirror mirror : children) {
                String name = Utils.firstLetterLowerCase(Utils.getAnnotationValue(String.class, mirror, "name"));
                TypeMirror type = Utils.getAnnotationValue(TypeMirror.class, mirror, "type");

                NodeFieldData field = new NodeFieldData(typeElement, mirror, type, name, true);
                if (name.isEmpty()) {
                    field.addError(Utils.getAnnotationValue(mirror, "name"), "Field name cannot be empty.");
                } else if (names.contains(name)) {
                    field.addError(Utils.getAnnotationValue(mirror, "name"), "Duplicate field name '%s'.", name);
                }
                names.add(name);

                fields.add(field);
            }
        }

        for (NodeFieldData nodeFieldData : fields) {
            nodeFieldData.setGetter(findGetter(elements, nodeFieldData.getName(), nodeFieldData.getType()));
        }

        return fields;
    }

    private List<NodeChildData> parseChildren(List<? extends Element> elements, final List<TypeElement> typeHierarchy) {
        Set<String> shortCircuits = new HashSet<>();
        for (ExecutableElement method : ElementFilter.methodsIn(elements)) {
            AnnotationMirror mirror = Utils.findAnnotationMirror(processingEnv, method, ShortCircuit.class);
            if (mirror != null) {
                shortCircuits.add(Utils.getAnnotationValue(String.class, mirror, "value"));
            }
        }
        Map<String, TypeMirror> castNodeTypes = new HashMap<>();
        for (ExecutableElement method : ElementFilter.methodsIn(elements)) {
            AnnotationMirror mirror = Utils.findAnnotationMirror(processingEnv, method, CreateCast.class);
            if (mirror != null) {
                List<String> children = (Utils.getAnnotationValueList(String.class, mirror, "value"));
                if (children != null) {
                    for (String child : children) {
                        castNodeTypes.put(child, method.getReturnType());
                    }
                }
            }
        }

        List<NodeChildData> parsedChildren = new ArrayList<>();
        List<TypeElement> typeHierarchyReversed = new ArrayList<>(typeHierarchy);
        Collections.reverse(typeHierarchyReversed);
        for (TypeElement type : typeHierarchyReversed) {
            AnnotationMirror nodeChildrenMirror = Utils.findAnnotationMirror(processingEnv, type, NodeChildren.class);

            TypeMirror nodeClassType = type.getSuperclass();
            if (!Utils.isAssignable(context, nodeClassType, context.getTruffleTypes().getNode())) {
                nodeClassType = null;
            }

            List<AnnotationMirror> children = Utils.collectAnnotations(context, nodeChildrenMirror, "value", type, NodeChild.class);
            int index = 0;
            for (AnnotationMirror childMirror : children) {
                String name = Utils.getAnnotationValue(String.class, childMirror, "value");
                if (name.equals("")) {
                    name = "child" + index;
                }

                Cardinality cardinality = Cardinality.ONE;

                TypeMirror childType = inheritType(childMirror, "type", nodeClassType);
                if (childType.getKind() == TypeKind.ARRAY) {
                    cardinality = Cardinality.MANY;
                }

                TypeMirror originalChildType = childType;
                TypeMirror castNodeType = castNodeTypes.get(name);
                if (castNodeType != null) {
                    childType = castNodeType;
                }

                Element getter = findGetter(elements, name, childType);

                NodeChildData nodeChild = new NodeChildData(type, childMirror, name, childType, originalChildType, getter, cardinality);

                parsedChildren.add(nodeChild);

                if (nodeChild.getNodeType() == null) {
                    nodeChild.addError("No valid node type could be resoleved.");
                }
                if (nodeChild.hasErrors()) {
                    continue;
                }

                index++;
            }
        }

        List<NodeChildData> filteredChildren = new ArrayList<>();
        Set<String> encounteredNames = new HashSet<>();
        for (int i = parsedChildren.size() - 1; i >= 0; i--) {
            NodeChildData child = parsedChildren.get(i);
            if (!encounteredNames.contains(child.getName())) {
                filteredChildren.add(0, child);
                encounteredNames.add(child.getName());
            }
        }

        for (NodeChildData child : filteredChildren) {
            List<String> executeWithStrings = Utils.getAnnotationValueList(String.class, child.getMessageAnnotation(), "executeWith");
            AnnotationValue executeWithValue = Utils.getAnnotationValue(child.getMessageAnnotation(), "executeWith");
            List<NodeChildData> executeWith = new ArrayList<>();
            for (String executeWithString : executeWithStrings) {

                if (child.getName().equals(executeWithString)) {
                    child.addError(executeWithValue, "The child node '%s' cannot be executed with itself.", executeWithString);
                    continue;
                }

                NodeChildData found = null;
                boolean before = true;
                for (NodeChildData resolveChild : filteredChildren) {
                    if (resolveChild == child) {
                        before = false;
                        continue;
                    }
                    if (resolveChild.getName().equals(executeWithString)) {
                        found = resolveChild;
                        break;
                    }
                }

                if (found == null) {
                    child.addError(executeWithValue, "The child node '%s' cannot be executed with '%s'. The child node was not found.", child.getName(), executeWithString);
                    continue;
                } else if (!before) {
                    child.addError(executeWithValue, "The child node '%s' cannot be executed with '%s'. The node %s is executed after the current node.", child.getName(), executeWithString,
                                    executeWithString);
                    continue;
                }
                executeWith.add(found);
            }
            child.setExecuteWith(executeWith);
            if (child.getNodeData() == null) {
                continue;
            }
        }

        return filteredChildren;
    }

    private List<NodeExecutionData> parseExecutions(List<NodeChildData> children, List<? extends Element> elements) {
        if (children == null) {
            return null;
        }

        // pre-parse short circuits
        Set<String> shortCircuits = new HashSet<>();
        List<ExecutableElement> methods = ElementFilter.methodsIn(elements);
        for (ExecutableElement method : methods) {
            AnnotationMirror mirror = Utils.findAnnotationMirror(processingEnv, method, ShortCircuit.class);
            if (mirror != null) {
                shortCircuits.add(Utils.getAnnotationValue(String.class, mirror, "value"));
            }
        }

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

        // pre-parse specializations
        for (ExecutableElement method : methods) {
            AnnotationMirror mirror = Utils.findAnnotationMirror(processingEnv, method, Specialization.class);
            if (mirror == null) {
                continue;
            }

            int currentArgumentCount = 0;
            boolean skipShortCircuit = false;
            for (VariableElement var : method.getParameters()) {
                TypeMirror type = var.asType();
                if (currentArgumentCount == 0) {
                    // skip optionals
                    if (Utils.typeEquals(type, context.getTruffleTypes().getFrame())) {
                        continue;
                    }
                    // TODO skip optional fields?
                }
                int childIndex = currentArgumentCount < children.size() ? currentArgumentCount : children.size() - 1;
                if (childIndex == -1) {
                    continue;
                }
                if (!skipShortCircuit) {
                    NodeChildData child = children.get(childIndex);
                    if (shortCircuits.contains(NodeExecutionData.createShortCircuitId(child, currentArgumentCount - childIndex))) {
                        skipShortCircuit = true;
                        continue;
                    }
                } else {
                    skipShortCircuit = false;
                }

                currentArgumentCount++;
            }
            maxSignatureSize = Math.max(maxSignatureSize, currentArgumentCount);
        }

        List<NodeExecutionData> executions = new ArrayList<>();
        for (int i = 0; i < maxSignatureSize; i++) {
            int childIndex = i;
            boolean varArg = false;
            if (childIndex >= children.size() - 1) {
                if (hasVarArgs) {
                    childIndex = children.size() - 1;
                    varArg = hasVarArgs;
                } else if (childIndex >= children.size()) {
                    break;
                }
            }
            int varArgsIndex = varArg ? Math.abs(childIndex - i) : -1;
            NodeChildData child = children.get(childIndex);
            boolean shortCircuit = shortCircuits.contains(NodeExecutionData.createShortCircuitId(child, varArgsIndex));
            executions.add(new NodeExecutionData(child, varArgsIndex, shortCircuit));
        }
        return executions;
    }

    private static Map<Integer, List<ExecutableTypeData>> groupExecutableTypes(List<ExecutableTypeData> executableTypes) {
        Map<Integer, List<ExecutableTypeData>> groupedTypes = new TreeMap<>();
        for (ExecutableTypeData type : executableTypes) {
            int evaluatedCount = type.getEvaluatedCount();

            List<ExecutableTypeData> types = groupedTypes.get(evaluatedCount);
            if (types == null) {
                types = new ArrayList<>();
                groupedTypes.put(evaluatedCount, types);
            }
            types.add(type);
        }

        for (List<ExecutableTypeData> types : groupedTypes.values()) {
            Collections.sort(types);
        }
        return groupedTypes;
    }

    private void initializeChildren(NodeData node) {
        for (NodeChildData nodeChild : node.getChildren()) {
            NodeData fieldNodeData = resolveNode(Utils.fromTypeMirror(nodeChild.getNodeType()));
            nodeChild.setNode(fieldNodeData);
            if (fieldNodeData == null) {
                nodeChild.addError("Node type '%s' is invalid or not a valid Node.", Utils.getQualifiedName(nodeChild.getNodeType()));
            } else if (!Utils.typeEquals(fieldNodeData.getTypeSystem().getTemplateType().asType(), (node.getTypeSystem().getTemplateType().asType()))) {
                nodeChild.addError("The @%s of the node and the @%s of the @%s does not match. %s != %s. ", TypeSystem.class.getSimpleName(), TypeSystem.class.getSimpleName(),
                                NodeChild.class.getSimpleName(), Utils.getSimpleName(node.getTypeSystem().getTemplateType()), Utils.getSimpleName(fieldNodeData.getTypeSystem().getTemplateType()));
            }
            if (fieldNodeData != null) {
                List<ExecutableTypeData> types = nodeChild.findGenericExecutableTypes(context);
                if (types.isEmpty()) {
                    AnnotationValue executeWithValue = Utils.getAnnotationValue(nodeChild.getMessageAnnotation(), "executeWith");
                    nodeChild.addError(executeWithValue, "No generic execute method found with %s evaluated arguments for node type %s.", nodeChild.getExecuteWith().size(),
                                    Utils.getSimpleName(nodeChild.getNodeType()));
                }
            }
        }
    }

    private void initializeSpecializations(List<? extends Element> elements, final NodeData node) {
        if (node.getSpecializations().isEmpty()) {
            return;
        }

        for (SpecializationData specialization : node.getSpecializations()) {
            if (!specialization.isSpecialized()) {
                continue;
            }
            initializeGuards(elements, specialization);
        }

        initializeGeneric(node);
        initializeUninitialized(node);
        initializePolymorphism(node); // requires specializations
        Collections.sort(node.getSpecializations());
        initializeReachability(node);

        // reduce polymorphicness if generic is not reachable
        if (node.getGenericSpecialization() != null && !node.getGenericSpecialization().isReachable()) {
            node.setPolymorphicDepth(1);
            node.getSpecializations().remove(node.getPolymorphicSpecialization());
        }

        List<SpecializationData> needsId = new ArrayList<>();
        for (SpecializationData specialization : node.getSpecializations()) {
            if (specialization.isGeneric()) {
                specialization.setId("Generic");
            } else if (specialization.isUninitialized()) {
                specialization.setId("Uninitialized");
            } else if (specialization.isPolymorphic()) {
                specialization.setId("Polymorphic");
            } else if (specialization.isSpecialized()) {
                needsId.add(specialization);
            } else {
                throw new AssertionError();
            }
        }

        // verify specialization parameter length
        List<String> ids = initializeSpecializationIds(needsId);
        for (int i = 0; i < ids.size(); i++) {
            needsId.get(i).setId(ids.get(i));
        }

    }

    private void initializeReachability(final NodeData node) {
        SpecializationData prev = null;
        boolean reachable = true;
        for (SpecializationData specialization : node.getSpecializations()) {
            if (specialization.isUninitialized() || specialization.isPolymorphic()) {
                specialization.setReachable(true);
                continue;
            }
            if (prev != null && prev.equalsGuards(specialization) && prev.getExceptions().isEmpty()) {
                specialization.addError("%s is not reachable.", specialization.isGeneric() ? "Generic" : "Specialization");
            } else if (!reachable && specialization.getMethod() != null) {
                specialization.addError("%s is not reachable.", specialization.isGeneric() ? "Generic" : "Specialization");
            }
            specialization.setReachable(reachable);
            if (!specialization.hasRewrite(context)) {
                reachable = false;
            }
            prev = specialization;
        }
    }

    private static List<String> initializeSpecializationIds(List<SpecializationData> specializations) {
        int lastSize = -1;
        List<List<String>> signatureChunks = new ArrayList<>();
        for (SpecializationData other : specializations) {
            if (!other.isSpecialized()) {
                continue;
            }
            List<String> paramIds = new LinkedList<>();
            paramIds.add(Utils.getTypeId(other.getReturnType().getType()));
            for (ActualParameter param : other.getParameters()) {
                if (param.getSpecification().getExecution() == null) {
                    continue;
                }
                paramIds.add(Utils.getTypeId(param.getType()));
            }
            assert lastSize == -1 || lastSize == paramIds.size();
            if (lastSize != -1 && lastSize != paramIds.size()) {
                throw new AssertionError();
            }
            signatureChunks.add(paramIds);
            lastSize = paramIds.size();
        }

        // reduce id vertically
        for (int i = 0; i < lastSize; i++) {
            String prev = null;
            boolean allSame = true;
            for (List<String> signature : signatureChunks) {
                String arg = signature.get(i);
                if (prev == null) {
                    prev = arg;
                    continue;
                } else if (!prev.equals(arg)) {
                    allSame = false;
                    break;
                }
                prev = arg;
            }

            if (allSame) {
                for (List<String> signature : signatureChunks) {
                    signature.remove(i);
                }
                lastSize--;
            }
        }

        // reduce id horizontally
        for (List<String> signature : signatureChunks) {
            if (signature.isEmpty()) {
                continue;
            }
            String prev = null;
            boolean allSame = true;
            for (String arg : signature) {
                if (prev == null) {
                    prev = arg;
                    continue;
                } else if (!prev.equals(arg)) {
                    allSame = false;
                    break;
                }
                prev = arg;
            }

            if (allSame) {
                signature.clear();
                signature.add(prev);
            }
        }

        // create signatures
        List<String> signatures = new ArrayList<>();
        for (List<String> signatureChunk : signatureChunks) {
            StringBuilder b = new StringBuilder();
            if (signatureChunk.isEmpty()) {
                b.append("Default");
            } else {
                for (String s : signatureChunk) {
                    b.append(s);
                }
            }
            signatures.add(b.toString());
        }

        Map<String, Integer> counts = new HashMap<>();
        for (String s1 : signatures) {
            Integer count = counts.get(s1);
            if (count == null) {
                count = 0;
            }
            count++;
            counts.put(s1, count);
        }

        for (String s : counts.keySet()) {
            int count = counts.get(s);
            if (count > 1) {
                int number = 0;
                for (ListIterator<String> iterator = signatures.listIterator(); iterator.hasNext();) {
                    String s2 = iterator.next();
                    if (s.equals(s2)) {
                        iterator.set(s2 + number);
                        number++;
                    }
                }
            }
        }

        return signatures;
    }

    private void initializeGuards(List<? extends Element> elements, SpecializationData specialization) {
        if (specialization.getGuardDefinitions().isEmpty()) {
            specialization.setGuards(Collections.<GuardData> emptyList());
            return;
        }

        List<GuardData> foundGuards = new ArrayList<>();
        List<ExecutableElement> methods = ElementFilter.methodsIn(elements);
        for (String guardDefinition : specialization.getGuardDefinitions()) {
            GuardParser parser = new GuardParser(context, specialization, guardDefinition);
            List<GuardData> guards = parser.parse(methods);
            if (!guards.isEmpty()) {
                foundGuards.add(guards.get(0));
            } else {
                // error no guard found
                MethodSpec spec = parser.createSpecification(specialization.getMethod(), null);
                spec.applyTypeDefinitions("types");
                specialization.addError("Guard with method name '%s' not found. Expected signature: %n%s", guardDefinition, spec.toSignatureString("guard"));
            }
        }

        specialization.setGuards(foundGuards);

    }

    private void initializeGeneric(final NodeData node) {
        if (!node.needsRewrites(context)) {
            return;
        }

        List<SpecializationData> generics = new ArrayList<>();
        for (SpecializationData spec : node.getSpecializations()) {
            if (spec.isGeneric()) {
                generics.add(spec);
            }
        }

        if (generics.size() == 1 && node.getSpecializations().size() == 1) {
            // TODO this limitation should be lifted
            for (SpecializationData generic : generics) {
                generic.addError("@%s defined but no @%s.", Generic.class.getSimpleName(), Specialization.class.getSimpleName());
            }
        }

        if (generics.isEmpty()) {
            node.getSpecializations().add(createGenericSpecialization(node));
        } else {
            if (generics.size() > 1) {
                for (SpecializationData generic : generics) {
                    generic.addError("Only @%s is allowed per operation.", Generic.class.getSimpleName());
                }
            }
        }
    }

    private SpecializationData createGenericSpecialization(final NodeData node) {
        GenericParser parser = new GenericParser(context, node);
        MethodSpec specification = parser.createDefaultMethodSpec(node.getSpecializations().iterator().next().getMethod(), null, true, null);

        List<TypeMirror> parameterTypes = new ArrayList<>();
        int signatureIndex = 1;
        for (ParameterSpec spec : specification.getRequired()) {
            parameterTypes.add(createGenericType(spec, node.getSpecializations(), signatureIndex));
            if (spec.isSignature()) {
                signatureIndex++;
            }
        }

        TypeMirror returnType = createGenericType(specification.getReturnType(), node.getSpecializations(), 0);
        SpecializationData generic = parser.create("Generic", null, null, returnType, parameterTypes);
        if (generic == null) {
            throw new RuntimeException("Unable to create generic signature for node " + node.getNodeId() + " with " + parameterTypes + ". Specification " + specification + ".");
        }

        return generic;
    }

    private TypeMirror createGenericType(ParameterSpec spec, List<SpecializationData> specializations, int signatureIndex) {
        NodeExecutionData execution = spec.getExecution();
        if (execution == null) {
            if (spec.getAllowedTypes().size() == 1) {
                return spec.getAllowedTypes().get(0);
            } else {
                return Utils.getCommonSuperType(context, spec.getAllowedTypes().toArray(new TypeMirror[0]));
            }
        } else {
            Set<TypeData> types = new HashSet<>();
            for (SpecializationData specialization : specializations) {
                types.add(specialization.getTypeSignature().get(signatureIndex));
            }

            NodeChildData child = execution.getChild();
            TypeData genericType = null;
            if (types.size() == 1) {
                ExecutableTypeData executable = child.findExecutableType(context, types.iterator().next());
                if (executable != null && (signatureIndex == 0 || !executable.hasUnexpectedValue(context))) {
                    genericType = types.iterator().next();
                }
            }
            if (genericType == null) {
                genericType = child.findAnyGenericExecutableType(context).getType();
            }
            return genericType.getPrimitiveType();
        }
    }

    private static void initializeUninitialized(final NodeData node) {
        SpecializationData generic = node.getGenericSpecialization();
        if (generic == null) {
            return;
        }
        for (ActualParameter parameter : generic.getReturnTypeAndParameters()) {
            if (Utils.isObject(parameter.getType())) {
                continue;
            }
            Set<String> types = new HashSet<>();
            for (SpecializationData specialization : node.getSpecializations()) {
                ActualParameter actualParameter = specialization.findParameter(parameter.getLocalName());
                if (actualParameter != null) {
                    types.add(Utils.getQualifiedName(actualParameter.getType()));
                }
            }
            if (types.size() > 1) {
                generic.replaceParameter(parameter.getLocalName(), new ActualParameter(parameter, node.getTypeSystem().getGenericTypeData()));
            }
        }
        TemplateMethod uninializedMethod = new TemplateMethod("Uninitialized", node, generic.getSpecification(), null, null, generic.getReturnType(), generic.getParameters());
        // should not use messages from generic specialization
        uninializedMethod.getMessages().clear();
        node.getSpecializations().add(new SpecializationData(node, uninializedMethod, SpecializationKind.UNINITIALIZED));
    }

    private void initializePolymorphism(NodeData node) {
        initializePolymorphicDepth(node);

        if (!node.needsRewrites(context) || !node.isPolymorphic()) {
            return;
        }

        SpecializationData generic = node.getGenericSpecialization();

        List<TypeData> polymorphicSignature = new ArrayList<>();
        List<ActualParameter> updatePolymorphic = Arrays.asList();
        for (ActualParameter genericParameter : updatePolymorphic) {
            if (!genericParameter.getSpecification().isSignature()) {
                continue;
            }

            Set<TypeData> usedTypes = new HashSet<>();
            for (SpecializationData specialization : node.getSpecializations()) {
                if (!specialization.isSpecialized()) {
                    continue;
                }
                ActualParameter parameter = specialization.findParameter(genericParameter.getLocalName());
                if (parameter == null) {
                    throw new AssertionError("Parameter existed in generic specialization but not in specialized. param = " + genericParameter.getLocalName());
                }
                usedTypes.add(parameter.getTypeSystemType());
            }

            TypeData polymorphicType;
            if (usedTypes.size() == 1) {
                polymorphicType = usedTypes.iterator().next();
            } else {
                polymorphicType = node.getTypeSystem().getGenericTypeData();
            }
            polymorphicSignature.add(polymorphicType);
        }

        SpecializationData polymorphic = new SpecializationData(node, generic, SpecializationKind.POLYMORPHIC);
        polymorphic.updateSignature(new TypeSignature(polymorphicSignature));
        node.getSpecializations().add(polymorphic);
    }

    private static void initializePolymorphicDepth(final NodeData node) {
        int polymorphicCombinations = 0;
        for (SpecializationData specialization : node.getSpecializations()) {
            if (specialization.isGeneric()) {
                continue;
            }

            int combinations = 1;
            for (ActualParameter parameter : specialization.getSignatureParameters()) {
                combinations *= node.getTypeSystem().lookupSourceTypes(parameter.getTypeSystemType()).size();
            }
            polymorphicCombinations += combinations;
        }

        // initialize polymorphic depth
        if (node.getPolymorphicDepth() < 0) {
            node.setPolymorphicDepth(polymorphicCombinations - 1);
        }

    }

    private void initializeShortCircuits(NodeData node) {
        Map<String, List<ShortCircuitData>> groupedShortCircuits = groupShortCircuits(node.getShortCircuits());

        boolean valid = true;
        List<NodeExecutionData> shortCircuitExecutions = new ArrayList<>();
        for (NodeExecutionData execution : node.getChildExecutions()) {
            if (!execution.isShortCircuit()) {
                continue;
            }
            shortCircuitExecutions.add(execution);
            String valueName = execution.getShortCircuitId();
            List<ShortCircuitData> availableCircuits = groupedShortCircuits.get(valueName);

            if (availableCircuits == null || availableCircuits.isEmpty()) {
                node.addError("@%s method for short cut value '%s' required.", ShortCircuit.class.getSimpleName(), valueName);
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
                    circuit.addError("All short circuits for short cut value '%s' must have the same method name.", valueName);
                }
                valid = false;
                continue;
            }

            ShortCircuitData genericCircuit = null;
            for (ShortCircuitData circuit : availableCircuits) {
                if (isGenericShortCutMethod(circuit)) {
                    genericCircuit = circuit;
                    break;
                }
            }

            if (genericCircuit == null) {
                node.addError("No generic @%s method available for short cut value '%s'.", ShortCircuit.class.getSimpleName(), valueName);
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
            return;
        }

        List<SpecializationData> specializations = new ArrayList<>();
        specializations.addAll(node.getSpecializations());
        for (SpecializationData specialization : specializations) {
            List<ShortCircuitData> assignedShortCuts = new ArrayList<>(shortCircuitExecutions.size());

            for (NodeExecutionData shortCircuit : shortCircuitExecutions) {
                List<ShortCircuitData> availableShortCuts = groupedShortCircuits.get(shortCircuit.getShortCircuitId());

                ShortCircuitData genericShortCircuit = null;
                ShortCircuitData compatibleShortCircuit = null;
                for (ShortCircuitData circuit : availableShortCuts) {
                    if (circuit.isGeneric()) {
                        genericShortCircuit = circuit;
                    } else if (circuit.isCompatibleTo(specialization)) {
                        compatibleShortCircuit = circuit;
                    }
                }

                if (compatibleShortCircuit == null) {
                    compatibleShortCircuit = genericShortCircuit;
                }
                assignedShortCuts.add(compatibleShortCircuit);
            }
            specialization.setShortCircuits(assignedShortCuts);
        }
    }

    private boolean isGenericShortCutMethod(ShortCircuitData method) {
        for (ActualParameter parameter : method.getParameters()) {
            NodeExecutionData execution = parameter.getSpecification().getExecution();
            if (execution == null) {
                continue;
            }
            ExecutableTypeData found = null;
            List<ExecutableTypeData> executableElements = execution.getChild().findGenericExecutableTypes(context);
            for (ExecutableTypeData executable : executableElements) {
                if (executable.getType().equalsType(parameter.getTypeSystemType())) {
                    found = executable;
                    break;
                }
            }
            if (found == null) {
                return false;
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

    private static void verifyVisibilities(NodeData node) {
        if (node.getTemplateType().getModifiers().contains(Modifier.PRIVATE) && node.getSpecializations().size() > 0) {
            node.addError("Classes containing a @%s annotation must not be private.", Specialization.class.getSimpleName());
        }
    }

    private static void verifySpecializationOrder(NodeData node) {
        List<SpecializationData> specializations = node.getSpecializations();
        for (int i = 0; i < specializations.size(); i++) {
            SpecializationData m1 = specializations.get(i);
            for (int j = i + 1; j < specializations.size(); j++) {
                SpecializationData m2 = specializations.get(j);
                int inferredOrder = m1.compareBySignature(m2);

                if (m1.getOrder() != Specialization.DEFAULT_ORDER && m2.getOrder() != Specialization.DEFAULT_ORDER) {
                    int specOrder = m1.getOrder() - m2.getOrder();
                    if (specOrder == 0) {
                        m1.addError("Order value %d used multiple times", m1.getOrder());
                        m2.addError("Order value %d used multiple times", m1.getOrder());
                        return;
                    } else if ((specOrder < 0 && inferredOrder > 0) || (specOrder > 0 && inferredOrder < 0)) {
                        m1.addError("Explicit order values %d and %d are inconsistent with type lattice ordering.", m1.getOrder(), m2.getOrder());
                        m2.addError("Explicit order values %d and %d are inconsistent with type lattice ordering.", m1.getOrder(), m2.getOrder());
                        return;
                    }
                } else if (inferredOrder == 0) {
                    SpecializationData m = (m1.getOrder() == Specialization.DEFAULT_ORDER ? m1 : m2);
                    m.addError("Cannot calculate a consistent order for this specialization. Define the order attribute to resolve this.");
                    return;
                }
            }
        }
    }

    private static void verifyMissingAbstractMethods(NodeData nodeData, List<? extends Element> originalElements) {
        if (!nodeData.needsFactory()) {
            // missing abstract methods only needs to be implemented
            // if we need go generate factory for it.
            return;
        }

        List<Element> elements = new ArrayList<>(originalElements);
        Set<Element> unusedElements = new HashSet<>(elements);
        for (TemplateMethod method : nodeData.getAllTemplateMethods()) {
            unusedElements.remove(method.getMethod());
        }

        for (NodeFieldData field : nodeData.getFields()) {
            if (field.getGetter() != null) {
                unusedElements.remove(field.getGetter());
            }
        }

        for (NodeChildData child : nodeData.getChildren()) {
            if (child.getAccessElement() != null) {
                unusedElements.remove(child.getAccessElement());
            }
        }

        for (ExecutableElement unusedMethod : ElementFilter.methodsIn(unusedElements)) {
            if (unusedMethod.getModifiers().contains(Modifier.ABSTRACT)) {
                nodeData.addError("The type %s must implement the inherited abstract method %s.", Utils.getSimpleName(nodeData.getTemplateType()), Utils.getReadableSignature(unusedMethod));
            }
        }
    }

    private static void verifyNamingConvention(List<? extends TemplateMethod> methods, String prefix) {
        for (int i = 0; i < methods.size(); i++) {
            TemplateMethod m1 = methods.get(i);
            if (m1.getMethodName().length() < 3 || !m1.getMethodName().startsWith(prefix)) {
                m1.addError("Naming convention: method name must start with '%s'.", prefix);
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
                        if (otherThrowsData != throwsData && Utils.typeEquals(otherThrowsData.getJavaClass(), throwsData.getJavaClass())) {
                            throwsData.addError("Duplicate exception type.");
                        }
                    }
                }
            }
        }
    }

    private void verifyConstructors(NodeData nodeData) {
        if (!nodeData.needsRewrites(context)) {
            // no specialization constructor is needed if the node never rewrites.
            return;
        }

        TypeElement type = Utils.fromTypeMirror(nodeData.getNodeType());
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(type.getEnclosedElements());

        boolean parametersFound = false;
        for (ExecutableElement constructor : constructors) {
            if (!constructor.getParameters().isEmpty() && !isSourceSectionConstructor(context, constructor)) {
                parametersFound = true;
            }
        }
        if (!parametersFound) {
            return;
        }
        for (ExecutableElement e : constructors) {
            if (e.getParameters().size() == 1) {
                TypeMirror firstArg = e.getParameters().get(0).asType();
                if (Utils.typeEquals(firstArg, nodeData.getNodeType())) {
                    if (e.getModifiers().contains(Modifier.PRIVATE)) {
                        nodeData.addError("The specialization constructor must not be private.");
                    } else if (constructors.size() <= 1) {
                        nodeData.addError("The specialization constructor must not be the only constructor. The definition of an alternative constructor is required.");
                    }
                    return;
                }
            }
        }

        // not found
        nodeData.addError("Specialization constructor '%s(%s previousNode) { this(...); }' is required.", Utils.getSimpleName(type), Utils.getSimpleName(type));
    }

    static boolean isSourceSectionConstructor(ProcessorContext context, ExecutableElement constructor) {
        return constructor.getParameters().size() == 1 && Utils.typeEquals(constructor.getParameters().get(0).asType(), context.getTruffleTypes().getSourceSection());
    }

    private AnnotationMirror findFirstAnnotation(List<? extends Element> elements, Class<? extends Annotation> annotation) {
        for (Element element : elements) {
            AnnotationMirror mirror = Utils.findAnnotationMirror(processingEnv, element, annotation);
            if (mirror != null) {
                return mirror;
            }
        }
        return null;
    }

    private TypeMirror inheritType(AnnotationMirror annotation, String valueName, TypeMirror parentType) {
        TypeMirror inhertNodeType = context.getTruffleTypes().getNode();
        TypeMirror value = Utils.getAnnotationValue(TypeMirror.class, annotation, valueName);
        if (Utils.typeEquals(inhertNodeType, value)) {
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
        if (Utils.typeEquals(type, context.getType(boolean.class))) {
            methodName = "is" + Utils.firstLetterUpperCase(variableName);
        } else {
            methodName = "get" + Utils.firstLetterUpperCase(variableName);
        }

        for (ExecutableElement method : ElementFilter.methodsIn(elements)) {
            if (method.getSimpleName().toString().equals(methodName) && method.getParameters().size() == 0 && Utils.isAssignable(context, type, method.getReturnType())) {
                return method;
            }
        }
        return null;
    }

    private static List<TypeElement> collectSuperClasses(List<TypeElement> collection, TypeElement element) {
        if (element != null) {
            collection.add(element);
            if (element.getSuperclass() != null) {
                collectSuperClasses(collection, Utils.fromTypeMirror(element.getSuperclass()));
            }
        }
        return collection;
    }

}
