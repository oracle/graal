/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.dsl.processor;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.object.dsl.processor.model.LayoutModel;
import com.oracle.truffle.object.dsl.processor.model.NameUtils;
import com.oracle.truffle.object.dsl.processor.model.PropertyModel;

public class LayoutGenerator {

    private final LayoutModel layout;
    private ProcessingEnvironment processingEnv;
    private final TypeMirror dispatchDefaultValue;

    public LayoutGenerator(LayoutModel layout, ProcessingEnvironment processingEnv) {
        this.layout = layout;
        this.processingEnv = processingEnv;
        this.dispatchDefaultValue = processingEnv.getElementUtils().getTypeElement("com.oracle.truffle.api.object.dsl.Layout.DispatchDefaultValue").asType();
    }

    public void generate(final PrintStream stream) {
        stream.printf("package %s;%n", layout.getPackageName());
        stream.println();

        generateImports(stream);

        stream.println();
        stream.printf("@GeneratedBy(%s.class)%n", layout.getInterfaceFullName());
        stream.printf("public class %sLayoutImpl", layout.getName());

        if (layout.getSuperLayout() != null) {
            stream.printf(" extends %sLayoutImpl", layout.getSuperLayout().getName());
        }

        stream.printf(" implements %sLayout {%n", layout.getName());

        stream.println("    ");
        stream.printf("    public static final %sLayout INSTANCE = new %sLayoutImpl();%n", layout.getName(), layout.getName());
        stream.println("    ");

        generateObjectType(stream);

        if (!layout.hasShapeProperties()) {
            stream.printf("    protected static final %sType %s_TYPE = new %sType();%n", layout.getName(),
                            NameUtils.identifierToConstant(layout.getName()), layout.getName());
            stream.println("    ");
        }

        generateAllocator(stream);
        generateProperties(stream);

        if (!layout.hasShapeProperties()) {
            stream.printf("    private static final DynamicObjectFactory %s_FACTORY = create%sShape();%n",
                            NameUtils.identifierToConstant(layout.getName()), layout.getName());
            stream.println("    ");
        }

        stream.printf("    protected %sLayoutImpl() {%n", layout.getName());
        stream.println("    }");
        stream.println("    ");

        generateShapeFactory(stream);
        generateFactory(stream);
        generateGuards(stream);
        generateAccessors(stream);

        stream.println("}");
    }

    private void generateImports(PrintStream stream) {
        boolean needsAtomicInteger = false;
        boolean needsAtomicBoolean = false;
        boolean needsAtomicReference = false;
        boolean needsIncompatibleLocationException = false;
        boolean needsFinalLocationException = false;
        boolean needsHiddenKey = false;
        boolean needsBoundary = false;

        for (PropertyModel property : layout.getProperties()) {
            if (!property.isShapeProperty() && !property.hasIdentifier()) {
                needsHiddenKey = true;
            }

            if (property.isVolatile()) {
                if (property.getType().getKind() == TypeKind.INT) {
                    needsAtomicInteger = true;
                } else if (property.getType().getKind() == TypeKind.BOOLEAN) {
                    needsAtomicBoolean = true;
                } else {
                    needsAtomicReference = true;
                }
            } else {
                if (property.hasSetter()) {
                    if (!property.isShapeProperty()) {
                        needsIncompatibleLocationException = true;
                        needsFinalLocationException = true;
                    }
                }
            }

            if (property.isShapeProperty() && (property.hasSetter() || property.hasShapeSetter())) {
                needsBoundary = true;
            }
        }

        if (layout.hasFinalInstanceProperties() || layout.hasNonNullableInstanceProperties()) {
            stream.println("import java.util.EnumSet;");
        }

        if (needsAtomicBoolean) {
            stream.println("import java.util.concurrent.atomic.AtomicBoolean;");
        }

        if (needsAtomicInteger) {
            stream.println("import java.util.concurrent.atomic.AtomicInteger;");
        }

        if (needsAtomicReference) {
            stream.println("import java.util.concurrent.atomic.AtomicReference;");
        }

        if (!layout.hasBuilder()) {
            stream.println("import com.oracle.truffle.api.CompilerAsserts;");
        }
        stream.println("import com.oracle.truffle.api.dsl.GeneratedBy;");

        if (needsBoundary) {
            stream.println("import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;");
        }

        stream.println("import com.oracle.truffle.api.object.DynamicObject;");
        stream.println("import com.oracle.truffle.api.object.DynamicObjectFactory;");

        if (needsFinalLocationException) {
            stream.println("import com.oracle.truffle.api.object.FinalLocationException;");
        }

        if (needsHiddenKey) {
            stream.println("import com.oracle.truffle.api.object.HiddenKey;");
        }

        if (needsIncompatibleLocationException) {
            stream.println("import com.oracle.truffle.api.object.IncompatibleLocationException;");
        }

        if (layout.getSuperLayout() == null) {
            stream.println("import com.oracle.truffle.api.object.Layout;");
        }

        if (layout.hasFinalInstanceProperties() || layout.hasNonNullableInstanceProperties()) {
            stream.println("import com.oracle.truffle.api.object.LocationModifier;");
        }

        stream.println("import com.oracle.truffle.api.object.ObjectType;");

        if (!layout.getInstanceProperties().isEmpty()) {
            stream.println("import com.oracle.truffle.api.object.Property;");
        }

        stream.println("import com.oracle.truffle.api.object.Shape;");
        stream.printf("import %s;%n", layout.getInterfaceFullName());

        if (layout.getSuperLayout() != null) {
            stream.printf("import %s.%sLayoutImpl;%n", layout.getSuperLayout().getPackageName(), layout.getSuperLayout().getName());
        }
    }

    private void generateObjectType(final PrintStream stream) {
        final String typeSuperclass;

        if (layout.getSuperLayout() == null) {
            typeSuperclass = layout.getObjectTypeSuperclass().toString();
        } else {
            typeSuperclass = layout.getSuperLayout().getName() + "LayoutImpl." + layout.getSuperLayout().getName() + "Type";
        }

        stream.printf("    public static class %sType extends %s {%n", layout.getName(), typeSuperclass);

        if (!processingEnv.getTypeUtils().isSameType(layout.getDispatch(), dispatchDefaultValue)) {
            stream.println("        ");
            stream.println("        @Override");
            stream.println("        public Class<?> dispatch() {");
            stream.printf("            return %s.class;%n", layout.getDispatch().toString());
            stream.println("        }");
        }

        if (layout.hasShapeProperties()) {
            stream.println("        ");

            for (PropertyModel property : layout.getShapeProperties()) {
                stream.printf("        protected final %s %s;%n", property.getType(), property.getName());
            }

            if (!layout.getShapeProperties().isEmpty()) {
                stream.println("        ");
            }

            stream.printf("        public %sType(%n", layout.getName());

            iterateProperties(layout.getAllShapeProperties(), new PropertyIteratorAction() {

                @Override
                public void run(PropertyModel property, boolean last) {
                    stream.printf("                %s %s", property.getType().toString(), property.getName());

                    if (last) {
                        stream.println(") {");
                    } else {
                        stream.println(",");
                    }
                }

            });

            if (!layout.getInheritedShapeProperties().isEmpty()) {
                stream.println("            super(");

                iterateProperties(layout.getInheritedShapeProperties(), new PropertyIteratorAction() {

                    @Override
                    public void run(PropertyModel property, boolean last) {
                        stream.printf("                %s", property.getName());

                        if (last) {
                            stream.println(");");
                        } else {
                            stream.println(",");
                        }
                    }

                });
            }

            iterateProperties(layout.getShapeProperties(), new PropertyIteratorAction() {

                @Override
                public void run(PropertyModel property, boolean last) {
                    stream.printf("            this.%s = %s;%n", property.getName(), property.getName());
                }

            });

            stream.println("        }");
            stream.println("        ");

            for (PropertyModel property : layout.getAllShapeProperties()) {
                final boolean inherited = !layout.getShapeProperties().contains(property);

                if (!inherited) {
                    stream.printf("        public %s %s() {%n", property.getType(), NameUtils.asGetter(property.getName()));
                    stream.printf("            return %s;%n", property.getName());
                    stream.println("        }");
                    stream.println("        ");
                }

                if (inherited) {
                    stream.println("        @Override");
                }

                stream.printf("        public %sType %s(%s %s) {%n", layout.getName(), NameUtils.asSetter(property.getName()), property.getType(), property.getName());
                stream.printf("            return new %sType(%n", layout.getName());

                iterateProperties(layout.getAllShapeProperties(), new PropertyIteratorAction() {

                    @Override
                    public void run(PropertyModel p, boolean last) {
                        stream.printf("                %s", p.getName());

                        if (last) {
                            stream.println(");");
                        } else {
                            stream.println(",");
                        }
                    }

                });

                stream.println("        }");
                stream.println("        ");
            }
        } else {
            stream.println("        ");
        }

        stream.println("    }");
        stream.println("    ");
    }

    private void generateAllocator(final PrintStream stream) {
        if (layout.getSuperLayout() == null) {
            stream.print("    protected static final Layout LAYOUT = Layout.newLayout()");

            for (VariableElement implicitCast : layout.getImplicitCasts()) {
                stream.print(".addAllowedImplicitCast(Layout.ImplicitCast.");
                stream.print(implicitCast.getSimpleName().toString());
                stream.print(")");
            }

            stream.println(".build();");

            stream.printf("    protected static final Shape.Allocator %S_ALLOCATOR = LAYOUT.createAllocator();%n", NameUtils.identifierToConstant(layout.getName()));
        } else {
            stream.printf("    protected static final Shape.Allocator %S_ALLOCATOR = LAYOUT.createAllocator();%n", NameUtils.identifierToConstant(layout.getName()));
            stream.println("    ");

            if (layout.getSuperLayout().hasInstanceProperties()) {
                stream.println("    static {");

                for (PropertyModel property : layout.getSuperLayout().getAllInstanceProperties()) {
                    final List<String> modifiers = new ArrayList<>();

                    if (!property.isNullable()) {
                        modifiers.add("LocationModifier.NonNull");
                    }

                    if (property.isFinal()) {
                        modifiers.add("LocationModifier.Final");
                    }

                    final String modifiersExpression;

                    if (modifiers.isEmpty()) {
                        modifiersExpression = "";
                    } else {
                        final StringBuilder modifiersExpressionBuilder = new StringBuilder();
                        modifiersExpressionBuilder.append(", EnumSet.of(");

                        for (String modifier : modifiers) {
                            if (!modifier.equals(modifiers.get(0))) {
                                modifiersExpressionBuilder.append(", ");
                            }

                            modifiersExpressionBuilder.append(modifier);
                        }

                        modifiersExpressionBuilder.append(")");
                        modifiersExpression = modifiersExpressionBuilder.toString();
                    }

                    final String locationType;

                    if (property.isVolatile()) {
                        if (property.getType().getKind() == TypeKind.INT) {
                            locationType = "AtomicInteger";
                        } else if (property.getType().getKind() == TypeKind.BOOLEAN) {
                            locationType = "AtomicBoolean";
                        } else {
                            locationType = "AtomicReference";
                        }
                    } else {
                        locationType = NameUtils.typeWithoutParameters(property.getType().toString());
                    }

                    stream.printf("         %s_ALLOCATOR.locationForType(%s.class%s);%n",
                                    NameUtils.identifierToConstant(layout.getName()),
                                    locationType,
                                    modifiersExpression);
                }

                stream.println("    }");
                stream.println("    ");
            }
        }
    }

    private void generateProperties(final PrintStream stream) {
        for (PropertyModel property : layout.getInstanceProperties()) {
            if (!property.hasIdentifier()) {
                stream.printf("    protected static final HiddenKey %s_IDENTIFIER = new HiddenKey(\"%s\");%n", NameUtils.identifierToConstant(property.getName()), property.getName());
            }

            final List<String> modifiers = new ArrayList<>();

            if (!property.isNullable()) {
                modifiers.add("LocationModifier.NonNull");
            }

            if (property.isFinal()) {
                modifiers.add("LocationModifier.Final");
            }

            final String modifiersExpression;

            if (modifiers.isEmpty()) {
                modifiersExpression = "";
            } else {
                final StringBuilder modifiersExpressionBuilder = new StringBuilder();
                modifiersExpressionBuilder.append(", EnumSet.of(");

                for (String modifier : modifiers) {
                    if (!modifier.equals(modifiers.get(0))) {
                        modifiersExpressionBuilder.append(", ");
                    }

                    modifiersExpressionBuilder.append(modifier);
                }

                modifiersExpressionBuilder.append(")");
                modifiersExpression = modifiersExpressionBuilder.toString();
            }

            final String locationType;

            if (property.isVolatile()) {
                if (property.getType().getKind() == TypeKind.INT) {
                    locationType = "AtomicInteger";
                } else if (property.getType().getKind() == TypeKind.BOOLEAN) {
                    locationType = "AtomicBoolean";
                } else {
                    locationType = "AtomicReference";
                }
            } else {
                locationType = NameUtils.typeWithoutParameters(property.getType().toString());
            }

            stream.printf("    protected static final Property %S_PROPERTY = Property.create(%s_IDENTIFIER, %S_ALLOCATOR.locationForType(%s.class%s), 0);%n",
                            NameUtils.identifierToConstant(property.getName()),
                            NameUtils.identifierToConstant(property.getName()),
                            NameUtils.identifierToConstant(layout.getName()),
                            locationType,
                            modifiersExpression);

            stream.println("    ");
        }
    }

    private void generateShapeFactory(final PrintStream stream) {
        if (layout.hasShapeProperties()) {
            stream.println("    @Override");
            stream.print("    public");
        } else {
            stream.print("    private static");
        }

        stream.printf(" DynamicObjectFactory create%sShape(", layout.getName());

        if (layout.hasShapeProperties()) {
            stream.println();

            for (PropertyModel property : layout.getAllShapeProperties()) {
                stream.printf("            %s %s", property.getType().toString(), property.getName());

                if (property == layout.getAllShapeProperties().get(layout.getAllShapeProperties().size() - 1)) {
                    stream.println(") {");
                } else {
                    stream.println(",");
                }
            }
        } else {
            stream.println(") {");
        }

        stream.printf("        return LAYOUT.createShape(new %sType(", layout.getName());

        if (layout.hasShapeProperties()) {
            stream.println();

            iterateProperties(layout.getAllShapeProperties(), new PropertyIteratorAction() {

                @Override
                public void run(PropertyModel property, boolean last) {
                    stream.printf("                %s", property.getName());

                    if (last) {
                        stream.println("))");
                    } else {
                        stream.println(",");
                    }
                }

            });
        } else {
            stream.println("))");
        }

        iterateProperties(layout.getAllInstanceProperties(), new PropertyIteratorAction() {

            @Override
            public void run(PropertyModel property, boolean last) {
                stream.printf("            .addProperty(%s_PROPERTY)%n", NameUtils.identifierToConstant(property.getName()));
            }

        });

        stream.println("            .createFactory();");

        stream.println("    }");
        stream.println("    ");
    }

    private void generateFactory(final PrintStream stream) {
        // The shortcut factory when there are no shape properties
        if (!layout.hasShapeProperties()) {
            stream.println("    @Override");
            stream.printf("    public DynamicObject create%s(", layout.getName());

            if (layout.getAllProperties().isEmpty()) {
                stream.println(") {");
            } else {
                stream.println();

                for (PropertyModel property : layout.getAllProperties()) {
                    stream.printf("            %s %s", property.getType().toString(), property.getName());

                    if (property == layout.getAllProperties().get(layout.getAllProperties().size() - 1)) {
                        stream.println(") {");
                    } else {
                        stream.println(",");
                    }
                }
            }

            stream.printf("        return create%s(%s_FACTORY", layout.getName(), NameUtils.identifierToConstant(layout.getName()));

            if (layout.getAllProperties().isEmpty()) {
                stream.println(");");
            } else {
                stream.println(",");

                for (PropertyModel property : layout.getAllProperties()) {
                    stream.printf("            %s", property.getName());

                    if (property == layout.getAllProperties().get(layout.getAllProperties().size() - 1)) {
                        stream.println(");");
                    } else {
                        stream.println(",");
                    }
                }
            }

            stream.println("    }");
            stream.println("    ");
        }

        // The full factory
        final boolean builder = layout.hasBuilder();
        final String methodName = builder ? "build" : "create" + layout.getName();
        final String returnType = builder ? "Object[]" : "DynamicObject";

        if (layout.hasShapeProperties()) {
            stream.println("    @Override");
            stream.print("    public");
        } else {
            stream.print("    private");

            if (!layout.hasObjectTypeGuard()) {
                stream.printf(" static");
            }
        }

        if (layout.hasInstanceProperties()) {
            stream.printf(" %s %s(%n", returnType, methodName);
            if (!builder) {
                stream.println("            DynamicObjectFactory factory,");
            }

            for (PropertyModel property : layout.getAllInstanceProperties()) {
                stream.printf("            %s %s", property.getType().toString(), property.getName());

                if (property == layout.getAllProperties().get(layout.getAllProperties().size() - 1)) {
                    stream.println(") {");
                } else {
                    stream.println(",");
                }
            }
        } else {
            String factoryArg = builder ? "" : "DynamicObjectFactory factory";
            stream.printf(" %s %s(%s) {%n", returnType, methodName, factoryArg);
        }

        if (!builder) {
            stream.println("        assert factory != null;");
            stream.println("        CompilerAsserts.partialEvaluationConstant(factory);");
            stream.printf("        assert creates%s(factory);%n", layout.getName());

            for (PropertyModel property : layout.getAllInstanceProperties()) {
                stream.printf("        assert factory.getShape().hasProperty(%s_IDENTIFIER);%n", NameUtils.identifierToConstant(property.getName()));
            }
        }

        for (PropertyModel property : layout.getAllInstanceProperties()) {
            if (!property.getType().getKind().isPrimitive() && !property.isNullable()) {
                stream.printf("        assert %s != null;%n", property.getName());
            }
        }

        if (layout.hasInstanceProperties()) {
            if (builder) {
                stream.println("        return new Object[] { ");
            } else {
                stream.println("        return factory.newInstance(");
            }

            for (PropertyModel property : layout.getAllInstanceProperties()) {
                if (property.isVolatile()) {
                    if (property.getType().getKind() == TypeKind.INT) {
                        stream.printf("            new AtomicInteger(%s)", property.getName());
                    } else if (property.getType().getKind() == TypeKind.BOOLEAN) {
                        stream.printf("            new AtomicBoolean(%s)", property.getName());
                    } else {
                        stream.printf("            new AtomicReference<>(%s)", property.getName());
                    }
                } else {
                    stream.printf("            %s", property.getName());
                }

                if (property == layout.getAllProperties().get(layout.getAllProperties().size() - 1)) {
                    stream.println(builder ? " };" : ");");
                } else {
                    stream.println(",");
                }
            }
        } else {
            if (builder) {
                stream.println("        return new Object[0];");
            } else {
                stream.println("        return factory.newInstance();");
            }
        }

        stream.println("    }");
        stream.println("    ");
    }

    private void generateGuards(final PrintStream stream) {
        if (layout.hasObjectGuard()) {
            stream.println("    @Override");
            stream.printf("    public boolean is%s(Object object) {%n", layout.getName());
            stream.printf("        return (object instanceof DynamicObject) && is%s((DynamicObject) object);%n", layout.getName());
            stream.println("    }");
            stream.println("    ");
        }

        if (layout.hasDynamicObjectGuard() || layout.hasGettersOrSetters()) {
            if (layout.hasDynamicObjectGuard()) {
                stream.println("    @Override");
                stream.print("    public");
            } else {
                stream.print("    private");
            }

            if (!layout.hasDynamicObjectGuard() && !layout.hasObjectTypeGuard()) {
                stream.printf(" static");
            }

            stream.printf(" boolean is%s(DynamicObject object) {%n", layout.getName());
            stream.printf("        return is%s(object.getShape().getObjectType());%n", layout.getName());
            stream.println("    }");
            stream.println("    ");
        }

        if (layout.hasObjectTypeGuard()) {
            stream.println("    @Override");
            stream.print("    public");
        } else {
            stream.print("    private static");
        }

        stream.printf(" boolean is%s(ObjectType objectType) {%n", layout.getName());
        stream.printf("        return objectType instanceof %sType;%n", layout.getName());
        stream.println("    }");

        if (!layout.hasBuilder()) {
            stream.println("    ");
            stream.printf("    private");

            if (!layout.hasObjectTypeGuard()) {
                stream.printf(" static");
            }

            stream.printf(" boolean creates%s(DynamicObjectFactory factory) {%n", layout.getName());
            stream.printf("        return is%s(factory.getShape().getObjectType());%n", layout.getName());
            stream.println("    }");
        }
        stream.println("    ");
    }

    private void generateAccessors(final PrintStream stream) {
        for (PropertyModel property : layout.getProperties()) {
            if (property.hasObjectTypeGetter()) {
                stream.println("    @Override");
                stream.printf("    public %s %s(ObjectType objectType) {%n", property.getType(), NameUtils.asGetter(property.getName()));
                stream.printf("        assert is%s(objectType);%n", layout.getName());
                stream.printf("        return ((%sType) objectType).%s();%n", layout.getName(), NameUtils.asGetter(property.getName()));
                stream.println("    }");
                stream.println("    ");
            }

            if (property.hasShapeGetter()) {
                stream.println("    @Override");
                stream.printf("    public %s %s(DynamicObjectFactory factory) {%n", property.getType(), NameUtils.asGetter(property.getName()));
                stream.printf("        assert creates%s(factory);%n", layout.getName());
                stream.printf("        return ((%sType) factory.getShape().getObjectType()).%s();%n", layout.getName(), NameUtils.asGetter(property.getName()));
                stream.println("    }");
                stream.println("    ");
            }

            if (property.hasGetter()) {
                addSuppressWarningsUncheckedCast(stream, property);
                stream.println("    @Override");
                stream.printf("    public %s %s(DynamicObject object) {%n", property.getType(), NameUtils.asGetter(property.getName()));
                stream.printf("        assert is%s(object);%n", layout.getName());

                if (property.isShapeProperty()) {
                    stream.printf("        return getObjectType(object).%s();%n", NameUtils.asGetter(property.getName()));
                } else {
                    stream.printf("        assert object.getShape().hasProperty(%s_IDENTIFIER);%n", NameUtils.identifierToConstant(property.getName()));
                    stream.println("        ");

                    if (property.isVolatile()) {
                        if (property.getType().getKind() == TypeKind.INT) {
                            stream.printf("        return ((AtomicInteger) %s_PROPERTY.get(object, is%s(object))).get();%n", NameUtils.identifierToConstant(property.getName()), layout.getName());
                        } else if (property.getType().getKind() == TypeKind.BOOLEAN) {
                            stream.printf("        return ((AtomicBoolean) %s_PROPERTY.get(object, is%s(object))).get();%n", NameUtils.identifierToConstant(property.getName()), layout.getName());
                        } else {
                            stream.printf("        return ((AtomicReference<%s>) %s_PROPERTY.get(object, is%s(object))).get();%n", property.getType(),
                                            NameUtils.identifierToConstant(property.getName()), layout.getName());
                        }
                    } else {
                        stream.printf("        return %s%s_PROPERTY.get(object, is%s(object));%n", cast(property.getType()), NameUtils.identifierToConstant(property.getName()), layout.getName());
                    }
                }

                stream.println("    }");
                stream.println("    ");
            }

            if (property.hasShapeSetter()) {
                addSuppressWarnings(stream, Collections.singleton("deprecation"));
                stream.println("    @TruffleBoundary");
                stream.println("    @Override");
                stream.printf("    public DynamicObjectFactory %s(DynamicObjectFactory factory, %s value) {%n", NameUtils.asSetter(property.getName()), property.getType());
                stream.printf("        assert creates%s(factory);%n", layout.getName());
                stream.println("        final Shape shape = factory.getShape();");
                stream.printf("        return shape.changeType(((%sType) shape.getObjectType()).%s(value)).createFactory();%n", layout.getName(), NameUtils.asSetter(property.getName()));
                stream.println("    }");
                stream.println("    ");
            }

            if (property.hasSetter() || property.hasUnsafeSetter()) {
                addSuppressWarningsForSetter(stream, property);
                if (property.isShapeProperty()) {
                    stream.println("    @TruffleBoundary");
                }

                stream.println("    @Override");

                final String methodNameSuffix;

                if (property.hasUnsafeSetter()) {
                    methodNameSuffix = "Unsafe";
                } else {
                    methodNameSuffix = "";
                }

                stream.printf("    public void %s%s(DynamicObject object, %s value) {%n", NameUtils.asSetter(property.getName()), methodNameSuffix, property.getType());
                stream.printf("        assert is%s(object);%n", layout.getName());

                if (property.isShapeProperty()) {
                    stream.println("        final Shape shape = object.getShape();");
                    stream.printf("        object.setShapeAndGrow(shape, shape.changeType(getObjectType(object).%s(value)));%n", NameUtils.asSetter(property.getName()));
                } else {
                    stream.printf("        assert object.getShape().hasProperty(%s_IDENTIFIER);%n", NameUtils.identifierToConstant(property.getName()));

                    if (!property.getType().getKind().isPrimitive() && !property.isNullable()) {
                        stream.println("        assert value != null;");
                    }

                    stream.println("        ");

                    if (property.hasUnsafeSetter()) {
                        stream.printf("        %s_PROPERTY.setInternal(object, value);%n", NameUtils.identifierToConstant(property.getName()));
                    } else if (property.isVolatile()) {
                        if (property.getType().getKind() == TypeKind.INT) {
                            stream.printf("        ((AtomicInteger) %s_PROPERTY.get(object, is%s(object))).set(value);%n", NameUtils.identifierToConstant(property.getName()), layout.getName());
                        } else if (property.getType().getKind() == TypeKind.BOOLEAN) {
                            stream.printf("        ((AtomicBoolean) %s_PROPERTY.get(object, is%s(object))).set(value);%n", NameUtils.identifierToConstant(property.getName()), layout.getName());
                        } else {
                            stream.printf("        ((AtomicReference<%s>) %s_PROPERTY.get(object, is%s(object))).set(value);%n", property.getType(),
                                            NameUtils.identifierToConstant(property.getName()),
                                            layout.getName());
                        }
                    } else {
                        stream.printf("        try {%n");
                        stream.printf("            %s_PROPERTY.set(object, value, object.getShape());%n", NameUtils.identifierToConstant(property.getName()));
                        stream.printf("        } catch (IncompatibleLocationException | FinalLocationException e) {%n");
                        stream.printf("            throw new UnsupportedOperationException(e);%n");
                        stream.printf("        }%n");
                    }
                }

                stream.println("    }");
                stream.println("    ");
            }

            if (property.hasCompareAndSet()) {
                addSuppressWarningsUncheckedCast(stream, property);

                stream.println("    @Override");
                stream.printf("    public boolean %s(DynamicObject object, %s expected_value, %s value) {%n",
                                NameUtils.asCompareAndSet(property.getName()),
                                property.getType(),
                                property.getType());

                stream.printf("        assert is%s(object);%n", layout.getName());
                stream.printf("        assert object.getShape().hasProperty(%s_IDENTIFIER);%n",
                                NameUtils.identifierToConstant(property.getName()));
                if (!property.getType().getKind().isPrimitive() && !property.isNullable()) {
                    stream.println("        assert value != null;");
                }

                if (property.getType().getKind() == TypeKind.INT) {
                    stream.printf(
                                    "        return ((AtomicInteger) %s_PROPERTY.get(object, is%s(object))).compareAndSet(expected_value, value);%n",
                                    NameUtils.identifierToConstant(property.getName()), layout.getName());
                } else if (property.getType().getKind() == TypeKind.BOOLEAN) {
                    stream.printf(
                                    "        return ((AtomicBoolean) %s_PROPERTY.get(object, is%s(object))).compareAndSet(expected_value, value);%n",
                                    NameUtils.identifierToConstant(property.getName()), layout.getName());
                } else {
                    stream.printf(
                                    "        return ((AtomicReference<%s>) %s_PROPERTY.get(object, is%s(object))).compareAndSet(expected_value, value);%n",
                                    property.getType(),
                                    NameUtils.identifierToConstant(property.getName()), layout.getName());
                }

                stream.println("    }");
                stream.println("    ");
            }

            if (property.hasGetAndSet()) {
                addSuppressWarningsUncheckedCast(stream, property);

                stream.println("    @Override");
                stream.printf("    public %s %s(DynamicObject object, %s value) {%n",
                                property.getType(),
                                NameUtils.asGetAndSet(property.getName()),
                                property.getType());

                stream.printf("        assert is%s(object);%n", layout.getName());
                stream.printf("        assert object.getShape().hasProperty(%s_IDENTIFIER);%n",
                                NameUtils.identifierToConstant(property.getName()));
                if (!property.getType().getKind().isPrimitive() && !property.isNullable()) {
                    stream.println("        assert value != null;");
                }

                if (property.getType().getKind() == TypeKind.INT) {
                    stream.printf(
                                    "        return ((AtomicInteger) %s_PROPERTY.get(object, is%s(object))).getAndSet(value);%n",
                                    NameUtils.identifierToConstant(property.getName()), layout.getName());
                } else if (property.getType().getKind() == TypeKind.BOOLEAN) {
                    stream.printf(
                                    "        return ((AtomicBoolean) %s_PROPERTY.get(object, is%s(object))).getAndSet(value);%n",
                                    NameUtils.identifierToConstant(property.getName()), layout.getName());
                } else {
                    stream.printf(
                                    "        return ((AtomicReference<%s>) %s_PROPERTY.get(object, is%s(object))).getAndSet(value);%n",
                                    property.getType(),
                                    NameUtils.identifierToConstant(property.getName()), layout.getName());
                }

                stream.println("    }");
                stream.println("    ");
            }
        }

        if (!layout.getShapeProperties().isEmpty()) {
            stream.print("    private");

            if (!layout.hasObjectTypeGuard()) {
                stream.print(" static");
            }

            stream.printf(" %sType getObjectType(DynamicObject object) {%n", layout.getName());
            stream.printf("        assert is%s(object);%n", layout.getName());
            stream.printf("        return (%sType) object.getShape().getObjectType();%n", layout.getName());
            stream.println("    }");
            stream.println("    ");
        }
    }

    private static boolean needsUncheckedCast(PropertyModel property) {
        return property.getType().toString().indexOf('<') != -1 || (property.isVolatile() && !property.getType().getKind().isPrimitive());
    }

    private static void addSuppressWarningsUncheckedCast(final PrintStream stream, PropertyModel property) {
        if (needsUncheckedCast(property)) {
            addSuppressWarnings(stream, Collections.singleton("unchecked"));
        }
    }

    private static void addSuppressWarningsForSetter(final PrintStream stream, PropertyModel property) {
        Collection<String> warnings = new ArrayList<>(2);
        if (needsUncheckedCast(property)) {
            warnings.add("unchecked");
        }
        if (property.hasUnsafeSetter() || property.isShapeProperty()) {
            warnings.add("deprecation");
        }
        addSuppressWarnings(stream, warnings);
    }

    private static void addSuppressWarnings(final PrintStream stream, Collection<String> warnings) {
        if (!warnings.isEmpty()) {
            stream.printf("    @SuppressWarnings({%s})\n", warnings.stream().map(s -> '\"' + s + '\"').collect(Collectors.joining(", ")));
        }
    }

    private static void iterateProperties(List<PropertyModel> properties, PropertyIteratorAction action) {
        for (int n = 0; n < properties.size(); n++) {
            action.run(properties.get(n), n == properties.size() - 1);
        }
    }

    private static String cast(TypeMirror type) {
        if (type.toString().equals(Object.class.getName())) {
            return "";
        } else {
            return String.format("(%s) ", type.toString());
        }
    }

    private interface PropertyIteratorAction {

        void run(PropertyModel property, boolean last);

    }

    public String getGeneratedClassName() {
        return layout.getPackageName() + "." + layout.getName() + "LayoutImpl";
    }

}
