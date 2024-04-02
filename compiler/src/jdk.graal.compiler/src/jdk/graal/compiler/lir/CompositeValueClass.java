/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.lir;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.EnumSet;

import jdk.graal.compiler.core.common.FieldIntrospection;
import jdk.graal.compiler.core.common.Fields;
import jdk.graal.compiler.core.common.FieldsScanner;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.CompositeValue.Component;

/**
 * Lazily associated metadata for every {@link CompositeValue} type. The metadata includes:
 * <ul>
 * <li>The offsets of fields annotated with {@link Component} as well as methods for iterating over
 * such fields.</li>
 * </ul>
 */
public final class CompositeValueClass<T> extends FieldIntrospection<T> {

    /**
     * The CompositeValueClass is only used for formatting for the most part so cache it as a
     * ClassValue.
     */
    private static final ClassValue<CompositeValueClass<?>> compositeClass = new ClassValue<>() {

        @Override
        protected CompositeValueClass<?> computeValue(Class<?> type) {
            CompositeValueClass<?> compositeValueClass = new CompositeValueClass<>(type);
            assert compositeValueClass.values.getDirectCount() == compositeValueClass.values.getCount() : "only direct fields are allowed in composites";
            return compositeValueClass;
        }

    };

    @SuppressWarnings("unchecked")
    public static <T> CompositeValueClass<T> get(Class<T> type) {
        return (CompositeValueClass<T>) compositeClass.get(type);
    }

    private final LIRIntrospection.Values values;

    private CompositeValueClass(Class<T> clazz) {
        super(clazz);

        CompositeValueFieldsScanner vfs = new CompositeValueFieldsScanner(new FieldsScanner.DefaultCalcOffset());
        vfs.scan(clazz, CompositeValue.class, false);

        values = LIRIntrospection.Values.create(vfs.valueAnnotations.get(Component.class));
        data = Fields.create(vfs.data);
    }

    private static class CompositeValueFieldsScanner extends LIRIntrospection.LIRFieldsScanner {

        CompositeValueFieldsScanner(FieldsScanner.CalcOffset calc) {
            super(calc);
            valueAnnotations.put(CompositeValue.Component.class, new LIRIntrospection.OperandModeAnnotation());
        }

        @Override
        protected EnumSet<LIRInstruction.OperandFlag> getFlags(Field field) {
            EnumSet<LIRInstruction.OperandFlag> result = EnumSet.noneOf(LIRInstruction.OperandFlag.class);
            if (field.isAnnotationPresent(CompositeValue.Component.class)) {
                result.addAll(Arrays.asList(field.getAnnotation(CompositeValue.Component.class).value()));
            } else {
                GraalError.shouldNotReachHereUnexpectedValue(field); // ExcludeFromJacocoGeneratedReport
            }
            return result;
        }
    }

    @Override
    public Fields[] getAllFields() {
        return new Fields[]{data, values};
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append(getClass().getSimpleName()).append(" ").append(getClazz().getSimpleName()).append(" components[");
        values.appendFields(str);
        str.append("] data[");
        data.appendFields(str);
        str.append("]");
        return str.toString();
    }

    public static String format(CompositeValue obj) {
        CompositeValueClass<?> valueClass = compositeClass.get(obj.getClass());
        StringBuilder result = new StringBuilder();

        LIRIntrospection.appendValues(result, obj, "", "", "{", "}", false, new String[]{""}, valueClass.values);

        for (int i = 0; i < valueClass.data.getCount(); i++) {
            result.append(" ").append(valueClass.data.getName(i)).append(": ").append(LIRIntrospection.getFieldString(obj, i, valueClass.data));
        }

        return result.toString();
    }
}
