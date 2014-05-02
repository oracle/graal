/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.match;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import javax.tools.Diagnostic.Kind;
import javax.tools.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.nodes.*;

/**
 * Processes classes annotated with {@link MatchRule}. A {@link MatchStatementSet} service is
 * generated for each top level class containing at least one such field. These service objects can
 * be retrieved as follows:
 *
 * <pre>
 *     ServiceLoader<MatchStatementSet> sl = ServiceLoader.loadInstalled(MatchStatementSet.class);
 *     for (MatchStatementSet rules : sl) {
 *         ...
 *     }
 * </pre>
 */
@SupportedAnnotationTypes({"com.oracle.graal.compiler.match.MatchRule", "com.oracle.graal.compiler.match.MatchRules", "com.oracle.graal.compiler.match.MatchableNode"})
public class MatchProcessor extends AbstractProcessor {

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    private final Set<Element> processedMatchRule = new HashSet<>();
    private final Set<Element> processedMatchableNode = new HashSet<>();

    private static class RuleParseError extends RuntimeException {
        private static final long serialVersionUID = 6456128283609257490L;

        RuleParseError(String format, Object... args) {
            super(String.format(format, args));
        }
    }

    private class RuleParser {
        private ArrayList<TypeDescriptor> capturedTypes = new ArrayList<>();

        private ArrayList<String> capturedNames = new ArrayList<>();

        private final String[] tokens;

        private int current;

        private MatchDescriptor matchDescriptor;

        private final Set<Element> originatingElements = new HashSet<>();

        RuleParser(String rule) {
            Matcher m = tokenizer.matcher(rule);
            List<String> list = new ArrayList<>();
            int end = 0;
            while (m.lookingAt()) {
                list.add(m.group(1));
                end = m.end();
                m.region(m.end(), m.regionEnd());
            }
            if (end != m.regionEnd()) {
                throw new RuleParseError("Unnexpected tokens :" + rule.substring(m.end(), m.regionEnd()));
            }
            tokens = list.toArray(new String[0]);

            matchDescriptor = parseExpression();
            if (!done()) {
                throw new RuleParseError("didn't consume all tokens");
            }
            capturedNames.add(0, "root");
            capturedTypes.add(0, matchDescriptor.nodeType);
        }

        String next() {
            return tokens[current++];
        }

        String peek(String name) {
            if (current >= tokens.length) {
                if (name == null) {
                    throw new RuleParseError("Out of tokens");
                }
                throw new RuleParseError("Out of tokens looking for %s", name);
            }
            return tokens[current];
        }

        boolean done() {
            return current == tokens.length;
        }

        private MatchDescriptor parseExpression() {
            if (peek("(").equals("(")) {
                next();
                MatchDescriptor descriptor = parseType(true);
                for (int n = 0; n < descriptor.nodeType.inputs; n++) {
                    if (peek("(").equals("(")) {
                        descriptor.inputs[n] = parseExpression();
                    } else {
                        descriptor.inputs[n] = parseType(false);
                    }
                }
                for (int n = 0; n < descriptor.nodeType.inputs; n++) {
                    if (descriptor.inputs[n] == null) {
                        throw new RuleParseError("not enough inputs for " + descriptor.name);
                    }
                }
                if (peek(")").equals(")")) {
                    next();
                    return descriptor;
                }
            }
            throw new RuleParseError("Extra tokens following match pattern: " + peek(null));
        }

        private MatchDescriptor parseType(boolean forExpression) {
            TypeDescriptor type = null;
            String name = null;
            if (Character.isUpperCase(peek("node type or name").charAt(0))) {
                String token = next();
                type = knownTypes.get(token);
                if (type == null) {
                    throw new RuleParseError("Unknown node type: " + token);
                }
                if (peek("=").equals("=")) {
                    next();
                    name = next();
                }
                originatingElements.addAll(type.originatingElements);
            } else if (Character.isLowerCase(peek("name").charAt(0))) {
                name = next();
                type = valueType;
            } else {
                throw new RuleParseError("Unexpected token \"%s\" when looking for name or node type", peek(null));
            }
            if (name != null) {
                if (!capturedNames.contains(name)) {
                    capturedNames.add(name);
                    capturedTypes.add(type);
                } else {
                    int index = capturedNames.indexOf(name);
                    if (capturedTypes.get(index) != type) {
                        throw new RuleParseError("Captured node \"%s\" has differing types", name);
                    }
                }
            }
            return new MatchDescriptor(type, name, forExpression);
        }

        List<String> generateVariants() {
            return matchDescriptor.generateVariants();
        }

        /**
         *
         * @return the list of node types which are captured by name
         */
        public ArrayList<TypeDescriptor> capturedTypes() {
            return capturedTypes;
        }

        public ArrayList<String> capturedNames() {
            return capturedNames;
        }
    }

    static Pattern tokenizer = Pattern.compile("\\s*([()=]|[A-Za-z][A-Za-z0-9]*)\\s*");

    static class TypeDescriptor {
        final TypeMirror mirror;

        /**
         * The name uses in match expressions to refer to this type.
         */
        final String shortName;
        /**
         * The {@link ValueNode} class represented by this type.
         */
        final String nodeClass;

        /**
         * The {@link ValueNode} class represented by this type.
         */
        final String nodePackage;

        /**
         * Expected number of matchable inputs. Should be less <= 2 at the moment.
         */
        final int inputs;

        /**
         * An adapter class to read the proper matchable inputs of the class.
         */
        final String adapter;

        /**
         * Should swapped variants of this match be generated. The user of the match is expected to
         * compensate for any ordering differences in compare which are commutative but require
         * reinterpreting the condition in that case.
         */
        final boolean commutative;

        /**
         * Can multiple users of this node subsume it. Constants can be swallowed into a match even
         * if there are multiple users.
         */
        final boolean cloneable;

        final Set<Element> originatingElements = new HashSet<>();

        TypeDescriptor(TypeMirror mirror, String shortName, String nodeClass, String nodePackage, int inputs, String adapter, boolean commutative) {
            this.mirror = mirror;
            this.shortName = shortName;
            this.nodeClass = nodeClass;
            this.nodePackage = nodePackage;
            this.inputs = inputs;
            this.adapter = adapter;
            this.commutative = commutative;
            this.cloneable = (nodePackage + "." + nodeClass).equals(ConstantNode.class.getName());
            assert !commutative || inputs == 2;
        }
    }

    /**
     * The types which are know for purpose of parsing MatchRule expressions.
     */
    Map<String, TypeDescriptor> knownTypes = new HashMap<>();

    /**
     * The set of packages which must be imported to refer to the known classes.
     */
    List<String> requiredPackages = new ArrayList<>();

    /**
     * The automatically generated wrapper class for a method based MatchRule.
     */
    private Map<ExecutableElement, MethodInvokerItem> invokers = new LinkedHashMap<>();
    private TypeDescriptor valueType;

    private void declareType(TypeMirror mirror, String shortName, String nodeClass, String nodePackage, int inputs, String adapter, boolean commutative, Element element) {
        TypeDescriptor descriptor = new TypeDescriptor(mirror, shortName, nodeClass, nodePackage, inputs, adapter, commutative);
        descriptor.originatingElements.add(element);
        knownTypes.put(shortName, descriptor);
        if (!requiredPackages.contains(descriptor.nodePackage)) {
            requiredPackages.add(descriptor.nodePackage);
        }
    }

    private static String findPackage(Element type) {
        Element enclosing = type.getEnclosingElement();
        while (enclosing != null && enclosing.getKind() != ElementKind.PACKAGE) {
            enclosing = enclosing.getEnclosingElement();
        }
        if (enclosing != null && enclosing.getKind() == ElementKind.PACKAGE) {
            return ((PackageElement) enclosing).getQualifiedName().toString();
        }
        throw new GraalInternalError("can't find package for %s", type);
    }

    class MatchDescriptor {
        TypeDescriptor nodeType;
        String name;
        MatchDescriptor[] inputs;

        MatchDescriptor(TypeDescriptor nodeType, String name, boolean forExpression) {
            this.nodeType = nodeType;
            this.name = name;
            if (forExpression) {
                this.inputs = new MatchDescriptor[nodeType.inputs];
            } else {
                this.inputs = new MatchDescriptor[0];
            }
        }

        /**
         * Recursively generate all the variants of this rule pattern. Currently that just means to
         * swap the inputs for commutative rules, producing all possible permutations.
         *
         * @return a list of Strings which will construct pattern matchers for this rule.
         */
        List<String> generateVariants() {
            String prefix = formatPrefix();
            String suffix = formatSuffix();
            ArrayList<String> variants = new ArrayList<>();
            if (inputs.length == 2) {
                // Generate this version and a swapped version
                for (String first : inputs[0].generateVariants()) {
                    for (String second : inputs[1].generateVariants()) {
                        variants.add(prefix + ", " + first + ", " + second + suffix);
                        if (nodeType.commutative) {
                            variants.add(prefix + ", " + second + ", " + first + suffix);
                        }
                    }
                }
            } else if (inputs.length == 1) {
                for (String first : inputs[0].generateVariants()) {
                    variants.add(prefix + ", " + first + suffix);
                }
            } else {
                variants.add(prefix + suffix);
            }
            return variants;
        }

        private String formatPrefix() {
            if (nodeType == valueType) {
                return String.format("new MatchPattern(%s, false", name != null ? ("\"" + name + "\"") : "null");
            } else {
                return String.format("new MatchPattern(%s.class, %s", nodeType.nodeClass, name != null ? ("\"" + name + "\"") : "null");
            }
        }

        private String formatSuffix() {
            if (nodeType != null) {
                if (inputs.length != nodeType.inputs) {
                    return ", true)";
                } else {
                    if (nodeType.adapter != null) {
                        return ", " + nodeType.adapter + "," + !nodeType.cloneable + ")";
                    }
                    if (nodeType.cloneable) {
                        return ", false)";
                    }
                }
            }
            return ")";
        }

    }

    /**
     * Strip the package off a class name leaving the full class name including any outer classes.
     */
    static String fullClassName(Element element) {
        assert element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.INTERFACE : element;
        String pkg = findPackage(element);
        return ((TypeElement) element).getQualifiedName().toString().substring(pkg.length() + 1);
    }

    private void createFiles(MatchRuleDescriptor info) {
        String pkg = ((PackageElement) info.topDeclaringType.getEnclosingElement()).getQualifiedName().toString();
        Name topDeclaringClass = info.topDeclaringType.getSimpleName();

        String matchStatementClassName = topDeclaringClass + "_" + MatchStatementSet.class.getSimpleName();
        Element[] originatingElements = info.originatingElements.toArray(new Element[info.originatingElements.size()]);

        Types typeUtils = processingEnv.getTypeUtils();
        Filer filer = processingEnv.getFiler();
        try (PrintWriter out = createSourceFile(pkg, matchStatementClassName, filer, originatingElements)) {

            out.println("// CheckStyle: stop header check");
            out.println("// GENERATED CONTENT - DO NOT EDIT");
            out.println("// Source: " + topDeclaringClass + ".java");
            out.println("package " + pkg + ";");
            out.println("");
            out.println("import java.util.*;");
            out.println("import java.lang.reflect.*;");
            out.println("import " + MatchStatementSet.class.getPackage().getName() + ".*;");
            out.println("import " + GraalInternalError.class.getName() + ";");
            out.println("import " + NodeLIRBuilder.class.getName() + ";");
            for (String p : requiredPackages) {
                out.println("import " + p + ".*;");
            }
            out.println("");
            out.println("public class " + matchStatementClassName + " implements " + MatchStatementSet.class.getSimpleName() + " {");

            // Generate declarations for the reflective invocation of the code generation methods.
            for (MethodInvokerItem invoker : invokers.values()) {
                StringBuilder args = new StringBuilder();
                StringBuilder types = new StringBuilder();
                int count = invoker.fields.size();
                for (VariableElement arg : invoker.fields) {
                    args.append('"');
                    args.append(arg.getSimpleName());
                    args.append('"');
                    types.append(fullClassName(typeUtils.asElement(arg.asType())));
                    types.append(".class");
                    if (count-- > 1) {
                        args.append(", ");
                        types.append(", ");
                    }
                }
                out.printf("        private static final String[] %s = new String[] {%s};\n", invoker.argumentsListName(), args);
                out.printf("        private static final Method %s;\n", invoker.reflectiveMethodName());
                out.printf("        static {\n");
                out.printf("            Method result = null;\n");
                out.printf("            try {\n");
                out.printf("                result = %s.class.getDeclaredMethod(\"%s\", %s);\n", invoker.nodeLIRBuilderClass, invoker.methodName, types);
                out.printf("             } catch (Exception e) {\n");
                out.printf("                 throw new GraalInternalError(e);\n");
                out.printf("             }\n");
                out.printf("             %s = result;\n", invoker.reflectiveMethodName());
                out.printf("        }\n");

                out.println();

            }

            String desc = MatchStatement.class.getSimpleName();
            out.println("    // CheckStyle: stop line length check");
            out.println("    private static final List<" + desc + "> statements = Collections.unmodifiableList(Arrays.asList(");

            int i = 0;
            for (MatchRuleItem matchRule : info.matchRules) {
                String comma = i == info.matchRules.size() - 1 ? "" : ",";
                out.printf("        %s%s\n", matchRule.ruleBuilder(), comma);
                i++;
            }
            out.println("    ));");
            out.println("    // CheckStyle: resume line length check");
            out.println();

            out.println("    public Class<? extends NodeLIRBuilder> forClass() {");
            out.println("        return " + topDeclaringClass + ".class;");
            out.println("    }");
            out.println();
            out.println("    @Override");
            out.println("    public List<" + desc + "> statements() {");
            out.println("        return statements;");
            out.println("    }");
            out.println("}");
        }

        try {
            createProviderFile(pkg, matchStatementClassName, originatingElements);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Kind.ERROR, e.getMessage(), info.topDeclaringType);
        }
    }

    private void createProviderFile(String pkg, String providerClassName, Element... originatingElements) throws IOException {
        String filename = "META-INF/providers/" + pkg + "." + providerClassName;
        FileObject file = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", filename, originatingElements);
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(file.openOutputStream(), "UTF-8"));
        writer.println(MatchStatementSet.class.getName());
        writer.close();
    }

    protected PrintWriter createSourceFile(String pkg, String relativeName, Filer filer, Element... originatingElements) {
        try {
            // Ensure Unix line endings to comply with Graal code style guide checked by Checkstyle
            JavaFileObject sourceFile = filer.createSourceFile(pkg + "." + relativeName, originatingElements);
            return new PrintWriter(sourceFile.openWriter()) {

                @Override
                public void println() {
                    print("\n");
                }
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Used to generate the MatchStatement constructor invocation.
     */
    static class MatchRuleItem {
        private final String matchPattern;
        private final MethodInvokerItem invoker;

        public MatchRuleItem(String matchPattern, MethodInvokerItem invoker) {
            this.matchPattern = matchPattern;
            this.invoker = invoker;
        }

        /**
         * @return a string which will construct the MatchStatement instance to match this pattern.
         */
        public String ruleBuilder() {
            return String.format("new MatchStatement(\"%s\", %s, %s, %s)", invoker.name, matchPattern, invoker.reflectiveMethodName(), invoker.argumentsListName());
        }
    }

    /**
     * Used to generate the declarations needed for reflective invocation of the code generation
     * method.
     */
    static class MethodInvokerItem {
        final String name;
        final String nodeLIRBuilderClass;
        final String methodName;
        final List<? extends VariableElement> fields;

        MethodInvokerItem(String name, String nodeLIRBuilderClass, String methodName, List<? extends VariableElement> fields) {
            this.name = name;
            this.nodeLIRBuilderClass = nodeLIRBuilderClass;
            this.methodName = methodName;
            this.fields = fields;
        }

        String reflectiveMethodName() {
            return methodName + "_invoke";
        }

        String argumentsListName() {
            return methodName + "_arguments";
        }
    }

    static class MatchRuleDescriptor {

        final TypeElement topDeclaringType;
        final List<MatchRuleItem> matchRules = new ArrayList<>();
        private final Set<Element> originatingElements = new HashSet<>();

        public MatchRuleDescriptor(TypeElement topDeclaringType) {
            this.topDeclaringType = topDeclaringType;
        }
    }

    private static TypeElement topDeclaringType(Element element) {
        Element enclosing = element.getEnclosingElement();
        if (enclosing == null || enclosing.getKind() == ElementKind.PACKAGE) {
            assert element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.INTERFACE;
            return (TypeElement) element;
        }
        return topDeclaringType(enclosing);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }

        try {
            // Define a TypeDescriptor the generic node but don't enter it into the nodeTypes table
            // since it shouldn't mentioned in match rules.
            TypeMirror mirror = processingEnv.getElementUtils().getTypeElement(ValueNode.class.getName()).asType();
            valueType = new TypeDescriptor(mirror, "Value", ValueNode.class.getSimpleName(), ValueNode.class.getPackage().getName(), 0, null, false);

            // Import default definitions
            processMatchableNode(processingEnv.getElementUtils().getTypeElement(GraalMatchableNodes.class.getName()));
            for (Element element : roundEnv.getElementsAnnotatedWith(MatchableNodeImport.class)) {
                // Import any other definitions required by this element
                String[] imports = element.getAnnotation(MatchableNodeImport.class).value();
                for (String m : imports) {
                    TypeElement el = processingEnv.getElementUtils().getTypeElement(m);
                    processMatchableNode(el);
                }
            }

            // Process any local MatchableNode declarations
            for (Element element : roundEnv.getElementsAnnotatedWith(MatchableNode.class)) {
                processMatchableNode(element);
            }

            Map<TypeElement, MatchRuleDescriptor> map = new HashMap<>();
            for (Element element : roundEnv.getElementsAnnotatedWith(MatchRule.class)) {
                processMatchRule(map, element);
            }
            for (Element element : roundEnv.getElementsAnnotatedWith(MatchRules.class)) {
                processMatchRule(map, element);
            }

            for (MatchRuleDescriptor info : map.values()) {
                createFiles(info);
            }

        } catch (Throwable t) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Exception throw during processing: " + t.toString() + " " + Arrays.toString(Arrays.copyOf(t.getStackTrace(), 2)));
        }

        return true;
    }

    /**
     * Build up the type table to be used during parsing of the MatchRule.
     */
    private void processMatchableNode(Element element) {
        if (!processedMatchableNode.contains(element)) {
            try {
                processedMatchableNode.add(element);
                TypeElement topDeclaringType = topDeclaringType(element);
                MatchableNode[] matchables = element.getAnnotationsByType(MatchableNode.class);
                for (MatchableNode matchable : matchables) {
                    String nodeClass;
                    String nodePackage;
                    TypeMirror nodeClassMirror = null;
                    try {
                        matchable.nodeClass();
                    } catch (MirroredTypeException e) {
                        nodeClassMirror = e.getTypeMirror();
                    }
                    if (nodeClassMirror == null) {
                        throw new GraalInternalError("Can't get mirror for node class %s", element);
                    }
                    if (nodeClassMirror.toString().equals(MatchableNode.class.getName())) {
                        nodeClass = topDeclaringType.getQualifiedName().toString();
                    } else {
                        nodeClass = nodeClassMirror.toString();
                    }
                    nodePackage = findPackage(processingEnv.getElementUtils().getTypeElement(nodeClass));
                    assert nodeClass.startsWith(nodePackage);
                    nodeClass = nodeClass.substring(nodePackage.length() + 1);
                    assert nodeClass.endsWith("Node");
                    String shortName = nodeClass.substring(0, nodeClass.length() - 4);

                    TypeMirror nodeAdapterMirror = null;
                    try {
                        matchable.adapter();
                    } catch (MirroredTypeException e) {
                        nodeAdapterMirror = e.getTypeMirror();
                    }
                    if (nodeAdapterMirror == null) {
                        throw new GraalInternalError("Can't get mirror for adapter %s", element);
                    }
                    String nodeAdapter = null;
                    if (!nodeAdapterMirror.toString().equals(MatchableNode.class.getName())) {
                        nodeAdapter = String.format("new %s()", nodeAdapterMirror.toString());
                    }

                    declareType(nodeClassMirror, shortName, nodeClass, nodePackage, matchable.inputs(), nodeAdapter, matchable.commutative(), element);
                }
            } catch (Throwable t) {
                reportExceptionThrow(element, t);
            }
        }
    }

    private void reportExceptionThrow(Element element, Throwable t) {
        processingEnv.getMessager().printMessage(Kind.ERROR, "Exception throw during processing: " + t.toString() + " " + Arrays.toString(Arrays.copyOf(t.getStackTrace(), 2)), element);
    }

    private void processMatchRule(Map<TypeElement, MatchRuleDescriptor> map, Element element) {
        if (!processedMatchRule.contains(element)) {
            try {
                processedMatchRule.add(element);

                // The annotation element type should ensure this is true.
                assert element instanceof ExecutableElement;

                TypeElement topDeclaringType = topDeclaringType(element);
                MatchRuleDescriptor info = map.get(topDeclaringType);
                if (info == null) {
                    info = new MatchRuleDescriptor(topDeclaringType);
                    map.put(topDeclaringType, info);
                }
                for (MatchRule matchRule : element.getAnnotationsByType(MatchRule.class)) {
                    // System.err.println(matchRule);
                    processMethodMatchRule((ExecutableElement) element, info, matchRule);
                }
            } catch (Throwable t) {
                reportExceptionThrow(element, t);
            }
        }
    }

    private void processMethodMatchRule(ExecutableElement method, MatchRuleDescriptor info, MatchRule matchRule) {
        Types typeUtils = processingEnv.getTypeUtils();

        if (!method.getModifiers().contains(Modifier.PUBLIC)) {
            String msg = String.format("MatchRule method %s must be public", method.getSimpleName());
            processingEnv.getMessager().printMessage(Kind.ERROR, msg, method);
            return;
        }
        if (method.getModifiers().contains(Modifier.STATIC)) {
            String msg = String.format("MatchRule method %s must be non-static", method.getSimpleName());
            processingEnv.getMessager().printMessage(Kind.ERROR, msg, method);
            return;
        }

        try {
            TypeMirror returnType = method.getReturnType();
            if (!typeUtils.isSameType(returnType, processingEnv.getElementUtils().getTypeElement(ComplexMatchResult.class.getName()).asType())) {
                String msg = String.format("MatchRule method return type must be %s", ComplexMatchResult.class.getName());
                processingEnv.getMessager().printMessage(Kind.ERROR, msg, method);
                return;
            }

            String rule = matchRule.value();
            RuleParser parser = new RuleParser(rule);
            ArrayList<TypeDescriptor> expectedTypes = parser.capturedTypes();
            ArrayList<String> expectedNames = parser.capturedNames();
            List<? extends VariableElement> actualParameters = method.getParameters();
            if (expectedTypes.size() + 1 < actualParameters.size()) {
                String msg = String.format("Too many arguments for match method %s %s", expectedTypes.size() + 1, actualParameters.size());
                processingEnv.getMessager().printMessage(Kind.ERROR, msg, method);
                return;
            }

            // Walk through the parameters to the method and see if they exist in the match rule.
            // The order doesn't matter but only names mentioned in the rule can be used and they
            // must be assignment compatible.
            for (VariableElement parameter : actualParameters) {
                String name = parameter.getSimpleName().toString();
                int nameIndex = expectedNames.indexOf(name);
                if (nameIndex == -1) {
                    String msg = String.format("Argument \"%s\" isn't captured in the match rule", name);
                    processingEnv.getMessager().printMessage(Kind.ERROR, msg, method);
                    return;
                }
                TypeMirror type = parameter.asType();
                if (!typeUtils.isAssignable(expectedTypes.get(nameIndex).mirror, type)) {
                    String msg = String.format("Captured value \"%s\" of type %s is not assignable to argument of type %s", name, expectedTypes.get(nameIndex).mirror, type);
                    processingEnv.getMessager().printMessage(Kind.ERROR, msg, method);
                    return;
                }
            }

            MethodInvokerItem invoker = invokers.get(method);
            if (invoker == null) {
                invoker = new MethodInvokerItem(method.getSimpleName().toString(), topDeclaringType(method).getSimpleName().toString(), method.getSimpleName().toString(), actualParameters);
                invokers.put(method, invoker);
            }

            Element enclosing = method.getEnclosingElement();
            String declaringClass = "";
            String separator = "";
            Set<Element> originatingElementsList = info.originatingElements;
            originatingElementsList.add(method);
            while (enclosing != null) {
                if (enclosing.getKind() == ElementKind.CLASS || enclosing.getKind() == ElementKind.INTERFACE) {
                    if (enclosing.getModifiers().contains(Modifier.PRIVATE)) {
                        String msg = String.format("MatchRule cannot be declared in a private %s %s", enclosing.getKind().name().toLowerCase(), enclosing);
                        processingEnv.getMessager().printMessage(Kind.ERROR, msg, method);
                        return;
                    }
                    originatingElementsList.add(enclosing);
                    declaringClass = enclosing.getSimpleName() + separator + declaringClass;
                    separator = ".";
                } else {
                    assert enclosing.getKind() == ElementKind.PACKAGE;
                }
                enclosing = enclosing.getEnclosingElement();
            }

            originatingElementsList.addAll(parser.originatingElements);

            List<String> matches = parser.generateVariants();
            for (String match : matches) {
                info.matchRules.add(new MatchRuleItem(match, invoker));
            }
        } catch (RuleParseError e) {
            processingEnv.getMessager().printMessage(Kind.ERROR, e.getMessage(), method);
        }
    }
}
