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

        RuleParseError(String message) {
            super(message);
        }
    }

    private class RuleParser {
        final String[] tokens;
        int current;

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
                throw new RuleParseError("unexpected tokens :" + rule.substring(m.end(), m.regionEnd()));
            }
            tokens = list.toArray(new String[0]);
        }

        String next() {
            return tokens[current++];
        }

        String peek() {
            return tokens[current];
        }

        boolean done() {
            return current == tokens.length;
        }

        private MatchDescriptor parseSexp() {
            if (peek().equals("(")) {
                next();
                MatchDescriptor descriptor = parseType(true);
                for (int n = 0; n < descriptor.nodeType.inputs; n++) {
                    if (peek().equals("(")) {
                        descriptor.inputs[n] = parseSexp();
                    } else {
                        descriptor.inputs[n] = parseType(false);
                    }
                }
                for (int n = 0; n < descriptor.nodeType.inputs; n++) {
                    if (descriptor.inputs[n] == null) {
                        throw new RuleParseError("not enough inputs for " + descriptor.name);
                    }
                }
                if (peek().equals(")")) {
                    next();
                    return descriptor;
                }
            }
            throw new RuleParseError("didn't swallow sexp at: " + peek());
        }

        private MatchDescriptor parseType(boolean sexp) {
            TypeDescriptor type = null;
            String name = null;
            if (Character.isUpperCase(peek().charAt(0))) {
                String token = next();
                type = types.get(token);
                if (type == null) {
                    throw new RuleParseError("unknown node type: " + token);
                }
                if (peek().equals("=")) {
                    next();
                    name = next();
                }
            } else {
                name = next();
                type = null;
            }
            return new MatchDescriptor(type, name, sexp);
        }

        ArrayList<String> generateVariants() {
            MatchDescriptor descriptor = parseSexp();
            if (!done()) {
                throw new RuleParseError("didn't consume all tokens");
            }
            return descriptor.generateVariants();
        }
    }

    static Pattern tokenizer = Pattern.compile("\\s*([()=]|[A-Za-z][A-Za-z0-9]*)\\s*");

    static class TypeDescriptor {
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

        TypeDescriptor(String shortName, String nodeClass, String nodePackage, int inputs, String adapter, boolean commutative) {
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

    HashMap<String, TypeDescriptor> types = new HashMap<>();
    ArrayList<String> packages = new ArrayList<>();

    private void declareType(String shortName, String nodeClass, String nodePackage, int inputs, String adapter, boolean commutative) {
        TypeDescriptor descriptor = new TypeDescriptor(shortName, nodeClass, nodePackage, inputs, adapter, commutative);
        types.put(shortName, descriptor);
        if (!packages.contains(descriptor.nodePackage)) {
            packages.add(descriptor.nodePackage);
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

    static class MatchDescriptor {
        TypeDescriptor nodeType;
        String name;
        MatchDescriptor[] inputs;

        MatchDescriptor(TypeDescriptor nodeType, String name, boolean sexp) {
            this.nodeType = nodeType;
            this.name = name;
            if (sexp) {
                this.inputs = new MatchDescriptor[nodeType.inputs];
            } else {
                this.inputs = new MatchDescriptor[0];
            }
        }

        ArrayList<String> generateVariants() {
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
            if (nodeType == null) {
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

    private void createFiles(MatchRuleDescriptor info) {
        String pkg = ((PackageElement) info.topDeclaringType.getEnclosingElement()).getQualifiedName().toString();
        Name topDeclaringClass = info.topDeclaringType.getSimpleName();

        String optionsClassName = topDeclaringClass + "_" + MatchStatementSet.class.getSimpleName();
        Element[] originatingElements = info.originatingElements.toArray(new Element[info.originatingElements.size()]);

        Filer filer = processingEnv.getFiler();
        try (PrintWriter out = createSourceFile(pkg, optionsClassName, filer, originatingElements)) {

            out.println("// CheckStyle: stop header check");
            out.println("// GENERATED CONTENT - DO NOT EDIT");
            out.println("// Source: " + topDeclaringClass + ".java");
            out.println("package " + pkg + ";");
            out.println("");
            out.println("import java.util.*;");
            out.println("import " + MatchStatementSet.class.getPackage().getName() + ".*;");
            out.println("import " + NodeLIRBuilder.class.getName() + ";");
            for (String p : packages) {
                out.println("import " + p + ".*;");
            }
            out.println("");
            out.println("public class " + optionsClassName + " implements " + MatchStatementSet.class.getSimpleName() + " {");
            String desc = MatchStatement.class.getSimpleName();
            out.println("    // CheckStyle: stop line length check");
            out.println("    private static final List<" + desc + "> options = Collections.unmodifiableList(Arrays.asList(");

            int i = 0;
            for (MatchRuleItem option : info.options) {
                String optionValue;
                if (option.field.getModifiers().contains(Modifier.PRIVATE)) {
                    optionValue = "field(" + option.declaringClass + ".class, \"" + option.field.getSimpleName() + "\")";
                } else {
                    optionValue = option.declaringClass + "." + option.field.getSimpleName();
                }
                String name = option.name;
                Name fieldName = option.field.getSimpleName();
                String comma = i == info.options.size() - 1 ? "" : ",";
                out.printf("        new MatchStatement(\"%s\", %s, %s.class)%s\n", fieldName, name, optionValue, comma);
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
            out.println("        return options;");
            out.println("    }");
            out.println("}");
        }

        try {
            createProviderFile(pkg, optionsClassName, originatingElements);
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

    static class MatchRuleItem implements Comparable<MatchRuleItem> {

        final String name;
        final String declaringClass;
        final TypeElement field;

        public MatchRuleItem(String name, String declaringClass, TypeElement field) {
            this.name = name;
            this.declaringClass = declaringClass;
            this.field = field;
        }

        @Override
        public int compareTo(MatchRuleItem other) {
            return name.compareTo(other.name);
        }

        @Override
        public String toString() {
            return declaringClass + "." + field;
        }
    }

    static class MatchRuleDescriptor {

        final TypeElement topDeclaringType;
        final List<MatchRuleItem> options = new ArrayList<>();
        final Set<Element> originatingElements = new HashSet<>();

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
            processingEnv.getMessager().printMessage(Kind.ERROR, "Exception throw during processing: " + t);
        }

        return true;
    }

    /**
     * Build up the type table to be used during parsing of the MatchRule.
     */
    private void processMatchableNode(Element element) {
        if (!processedMatchableNode.contains(element)) {
            processedMatchableNode.add(element);
            TypeElement topDeclaringType = topDeclaringType(element);
            MatchableNode[] matchables = element.getAnnotationsByType(MatchableNode.class);
            for (MatchableNode matchable : matchables) {
                String nodeClass;
                String nodePackage;
                String shortName = matchable.shortName();
                TypeMirror nodeClassMirror = null;
                try {
                    matchable.value();
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

                declareType(shortName, nodeClass, nodePackage, matchable.inputs(), nodeAdapter, matchable.commutative());
            }
        }
    }

    private void processMatchRule(Map<TypeElement, MatchRuleDescriptor> map, Element element) {
        if (!processedMatchRule.contains(element)) {
            processedMatchRule.add(element);
            TypeElement topDeclaringType = topDeclaringType(element);
            MatchRuleDescriptor options = map.get(topDeclaringType);
            if (options == null) {
                options = new MatchRuleDescriptor(topDeclaringType);
                map.put(topDeclaringType, options);
            }
            MatchRule[] matchRules = element.getAnnotationsByType(MatchRule.class);
            for (MatchRule matchRule : matchRules) {
                processMatchRule(element, options, matchRule);
            }
        }
    }

    private void processMatchRule(Element element, MatchRuleDescriptor info, MatchRule matchRule) {
        assert element instanceof TypeElement;
        assert element.getKind() == ElementKind.CLASS;
        TypeElement field = (TypeElement) element;

        TypeMirror fieldType = field.asType();
        if (fieldType.getKind() != TypeKind.DECLARED) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Option field must be of type " + MatchRule.class.getName(), element);
            return;
        }

        Element enclosing = element.getEnclosingElement();
        String declaringClass = "";
        String separator = "";
        Set<Element> originatingElementsList = info.originatingElements;
        originatingElementsList.add(field);
        while (enclosing != null) {
            if (enclosing.getKind() == ElementKind.CLASS || enclosing.getKind() == ElementKind.INTERFACE) {
                if (enclosing.getModifiers().contains(Modifier.PRIVATE)) {
                    String msg = String.format("Option field cannot be declared in a private %s %s", enclosing.getKind().name().toLowerCase(), enclosing);
                    processingEnv.getMessager().printMessage(Kind.ERROR, msg, element);
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

        String rule = matchRule.value();
        try {
            ArrayList<String> matches = new RuleParser(rule).generateVariants();
            for (String match : matches) {
                info.options.add(new MatchRuleItem(match, declaringClass, field));
            }
        } catch (RuleParseError e) {
            processingEnv.getMessager().printMessage(Kind.ERROR, e.getMessage(), element);
        }
    }

}
