/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicMapWrap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.FieldIntrospection;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.internal.misc.Unsafe;

/**
 * Support for deep copying an object across processes by {@linkplain #encode encoding} it to a
 * String in the first process and {@linkplain #decode decoding} it back into an object in the
 * second process. This copying requires that the classes of the copied objects are the same in both
 * processes with respect to fields.
 *
 * Encoded format in EBNF:
 *
 * <pre>
 *  enc = line "\n" { line "\n" }
 *  line = header | objectField
 *  header = id ":" ( builtin | object | array | fieldRef )
 *  object = "{" className ":" fieldCount "}"
 *  objectField = "  " fieldName ":" id " = " fieldValue
 *  fieldValue = ( primitive | id )
 *  id = int
 *  array = "[" className "] = " elements
 *  elements = [ fieldValue { " " fieldValue } ]
 *  fieldRef = "@" className "." fieldName
 *  builtin = "<"  className [ ":" encodingName ] "> = " builtinValue
 * </pre>
 *
 * See the {@link Builtin} subclasses for the EBNF of builtinValue.
 */
public class ObjectCopier {

    private static final Pattern BUILTIN_LINE = Pattern.compile("<(?<class>[^:}]+)(?::(?<encodingName>\\w+))?> = (?<value>.*)");
    private static final Pattern OBJECT_LINE = Pattern.compile("\\{(?<class>[\\w.$]+):(?<fieldCount>\\d+)}");
    private static final Pattern ARRAY_LINE = Pattern.compile("\\[(?<componentType>[^]]+)] = (?<elements>.*)");
    private static final Pattern FIELD_LINE = Pattern.compile("\\s*(?<desc>[^:]+):(?<typeId>[^ ]+) = (?<value>.*)");

    /**
     * A builtin is specialized support for encoded and decoding values of specific types.
     */
    public abstract static class Builtin {
        /**
         * The primary type for this builtin.
         */
        final Class<?> clazz;

        final Set<Class<?>> concreteClasses;

        protected Builtin(Class<?> clazz, Class<?>... concreteClasses) {
            this.clazz = clazz;
            if (Modifier.isAbstract(clazz.getModifiers())) {
                this.concreteClasses = Set.of(concreteClasses);
            } else {
                ArrayList<Class<?>> l = new ArrayList<>(List.of(concreteClasses));
                l.add(clazz);
                this.concreteClasses = Set.copyOf(l);
            }
        }

        /**
         * Checks that {@code obj} is of a type supported by this builtin.
         */
        final void checkObject(Object obj) {
            checkClass(obj == Class.class ? (Class<?>) obj : obj.getClass());
        }

        /**
         * Checks that {@code c} is a type supported by this builtin.
         */
        final void checkClass(Class<?> c) {
            GraalError.guarantee(c.isEnum() || concreteClasses.contains(c),
                            "Unsupported %s type: %s", this.clazz.getName(), c.getName());
        }

        /**
         * Gets the name of a non-default encoded used by this builtin for {@code obj}.
         *
         * @return null if the default encoded is used for {@code obj}
         */
        @SuppressWarnings("unused")
        String encodingName(Object obj) {
            return null;
        }

        /**
         * Ensures object ids have are created for the values referenced by {@code obj} that will be
         * handled by this builtin when {@code obj} is encoded. For example, the values in a map.
         */
        @SuppressWarnings("unused")
        void makeChildIds(Encoder encoder, Object obj, ObjectPath objectPath) {
        }

        /**
         * Encodes the value of {@code obj} to a String that does not contain {@code '\n'} or
         * {@code '\r'}.
         */
        protected abstract String encode(Encoder encoder, Object obj);

        /**
         * Decodes {@code encoded} to an object of a type handled by this builtin.
         *
         * @param encoding the non-default encoded used when encoded the object or null if the
         *            default encoded was used
         */
        protected abstract Object decode(Decoder decoder, Class<?> concreteType, String encoding, String encoded);

        @Override
        public String toString() {
            return "builtin:" + clazz.getName();
        }
    }

    /**
     * Builtin for handling {@link Class} values.
     *
     * EBNF:
     *
     * <pre>
     * builtinValue = className
     * </pre>
     *
     * The className is in {@link Class#getName()} format.
     */
    static final class ClassBuiltin extends Builtin {

        ClassBuiltin() {
            super(Class.class);
        }

        @Override
        protected String encode(Encoder encoder, Object obj) {
            return ((Class<?>) obj).getName();
        }

        @Override
        protected Object decode(Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            return switch (encoded) {
                case "boolean" -> boolean.class;
                case "byte" -> byte.class;
                case "char" -> char.class;
                case "short" -> short.class;
                case "int" -> int.class;
                case "float" -> float.class;
                case "long" -> long.class;
                case "double" -> double.class;
                case "void" -> void.class;
                default -> decoder.loadClass(encoded);
            };
        }
    }

    /**
     * Builtin for handling {@link String} values.
     *
     * EBNF:
     *
     * <pre>
     * builtinValue = string
     * </pre>
     *
     * The string has no embedded \r or \n characters.
     */
    static final class StringBuiltin extends Builtin {

        StringBuiltin() {
            super(String.class, char[].class);
        }

        @Override
        String encodingName(Object obj) {
            String s = obj instanceof String ? (String) obj : new String((char[]) obj);
            if (s.indexOf('\n') != -1 || s.indexOf('\r') != -1) {
                return "escaped";
            }
            return super.encodingName(obj);
        }

        @Override
        protected String encode(Encoder encoder, Object obj) {
            String s = obj instanceof String ? (String) obj : new String((char[]) obj);
            if ("escaped".equals(encodingName(s))) {
                return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
            }
            return s;
        }

        @Override
        protected Object decode(Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            String s = encoded;
            if (encoding != null) {
                GraalError.guarantee(encoding.equals("escaped"), "Unknown encoded: %s", encoding);
                s = encoded.replace("\\r", "\r").replace("\\n", "\n").replace("\\\\", "\\");
            }
            if (concreteType == char[].class) {
                return s.toCharArray();
            }
            return s;
        }
    }

    /**
     * Builtin for handling {@link Enum} values.
     *
     * EBNF:
     *
     * <pre>
     * builtinValue = enumName
     * </pre>
     *
     * The enumName is given by {@link Enum#name()}.
     */
    static final class EnumBuiltin extends Builtin {

        EnumBuiltin() {
            super(Enum.class);
        }

        @Override
        protected String encode(Encoder encoder, Object obj) {
            return ((Enum<?>) obj).name();
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        protected Object decode(Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            return Enum.valueOf((Class) concreteType, encoded);
        }
    }

    /**
     * Builtin for handling {@link HashMap} or {@link IdentityHashMap} values.
     *
     * EBNF:
     *
     * <pre>
     *  builtinValue = [ key ":" value { " " } key ":" value ]
     *  key = fieldValue
     *  value = fieldValue
     * </pre>
     */
    static final class HashMapBuiltin extends Builtin {

        final Map<Class<?>, Supplier<?>> factories;

        HashMapBuiltin() {
            super(HashMap.class, IdentityHashMap.class, LinkedHashMap.class, SnippetTemplate.LRUCache.class);
            int size = SnippetTemplate.Options.MaxTemplatesPerSnippet.getDefaultValue();
            factories = Map.of(
                            HashMap.class, HashMap::new,
                            IdentityHashMap.class, IdentityHashMap::new,
                            LinkedHashMap.class, LinkedHashMap::new,
                            SnippetTemplate.LRUCache.class, () -> new SnippetTemplate.LRUCache<>(size, size));
        }

        @Override
        void makeChildIds(Encoder encoder, Object obj, ObjectPath objectPath) {
            Map<?, ?> map = (Map<?, ?>) obj;
            encoder.makeMapChildIds(new EconomicMapWrap<>(map), objectPath);
        }

        @Override
        protected String encode(Encoder encoder, Object obj) {
            Map<?, ?> map = (Map<?, ?>) obj;
            return encoder.encodeMap(new EconomicMapWrap<>(map));
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Object decode(Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            Map<Object, Object> map = (Map<Object, Object>) factories.get(concreteType).get();
            decoder.decodeMap(encoded, map::put);
            return map;
        }
    }

    /**
     * Builtin for handling {@link EconomicMap} values.
     *
     * EBNF:
     *
     * <pre>
     *  builtinValue = [ key ":" value { " " } key ":" value ]
     *  key = fieldValue
     *  value = fieldValue
     * </pre>
     */
    static final class EconomicMapBuiltin extends Builtin {
        EconomicMapBuiltin() {
            super(EconomicMap.class, EconomicMap.create().getClass());
        }

        @Override
        void makeChildIds(Encoder encoder, Object obj, ObjectPath objectPath) {
            EconomicMap<?, ?> map = (EconomicMap<?, ?>) obj;
            GraalError.guarantee(map.getEquivalenceStrategy() == Equivalence.DEFAULT,
                            "Only DEFAULT strategy supported: %s", map.getEquivalenceStrategy());
            encoder.makeMapChildIds(map, objectPath);
        }

        @Override
        protected String encode(Encoder encoder, Object obj) {
            return encoder.encodeMap((UnmodifiableEconomicMap<?, ?>) obj);
        }

        @Override
        protected Object decode(Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            if (EconomicMap.class.isAssignableFrom(concreteType)) {
                EconomicMap<Object, Object> map = EconomicMap.create();
                decoder.decodeMap(encoded, map::put);
                return map;
            } else {
                throw new GraalError("Unexpected concrete Map type: ", concreteType);
            }
        }
    }

    /**
     * Caches metadata needs for encoded and decoding the non-static fields of a class.
     *
     * @param fields a map from a descriptor to a field for each non-static field in {@code clazz}'s
     *            super type hierarchy. A descriptor is the simple name of a field unless 2 fields
     *            have the same name in which case the descriptor includes the qualified name of the
     *            class declaring the field as a prefix.
     */
    public record ClassInfo(Class<?> clazz, Map<String, Field> fields) {
        public static ClassInfo of(Class<?> declaringClass) {
            Map<String, Field> fields = new HashMap<>();
            for (Class<?> c = declaringClass; !c.equals(Object.class); c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        String fieldDesc = f.getName();
                        if (fields.containsKey(fieldDesc)) {
                            fieldDesc = c.getName() + "." + fieldDesc;
                        }
                        Field conflict = fields.put(fieldDesc, f);
                        GraalError.guarantee(conflict == null, "Cannot support 2 fields with same name and declaring class: %s and %s", conflict, f);
                    }
                }
            }
            return new ClassInfo(declaringClass, fields);
        }
    }

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    final Map<Class<?>, ClassInfo> classInfos = new HashMap<>();
    final Map<Class<?>, Builtin> builtinClasses = new HashMap<>();
    final Set<Class<?>> notBuiltins = new HashSet<>();

    protected final void addBuiltin(Builtin builtin) {
        addBuiltin(builtin, builtin.clazz);
    }

    /**
     * Registers {@code builtin} and ensures the type {@code clazz} it handles is not handled by an
     * existing registered builtin.
     */
    final void addBuiltin(Builtin builtin, Class<?> clazz) {
        Builtin conflict = getBuiltin(clazz, true);
        GraalError.guarantee(conflict == null, "Conflicting builtins: %s and %s", conflict, builtin);
        builtinClasses.put(clazz, builtin);
    }

    public ObjectCopier() {
        addBuiltin(new ClassBuiltin());
        addBuiltin(new EconomicMapBuiltin());
        addBuiltin(new EnumBuiltin());

        HashMapBuiltin hashMapBuiltin = new HashMapBuiltin();
        addBuiltin(hashMapBuiltin);
        addBuiltin(hashMapBuiltin, IdentityHashMap.class);

        StringBuiltin stringBuiltin = new StringBuiltin();
        addBuiltin(stringBuiltin);
        addBuiltin(stringBuiltin, char[].class);
    }

    static String[] splitSpaceSeparatedElements(String elements) {
        if (elements.isEmpty()) {
            return new String[0];
        }
        return elements.split(" ");
    }

    /**
     * Encodes {@code root} to a String using {@code encoder}.
     */
    public static String encode(Encoder encoder, Object root) {
        int rootId = encoder.makeId(root, ObjectPath.of("[root:" + root.getClass().getName() + "]")).id();
        GraalError.guarantee(rootId == 1, "The root object should have id of 1, not %d", rootId);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos)) {
            encoder.encode(ps);
        }
        return baos.toString();
    }

    public static Object decode(String encoded, ClassLoader loader) {
        Decoder decoder = new Decoder(loader);
        return decode(decoder, encoded);
    }

    public static Object decode(Decoder decoder, String encoded) {
        return decoder.decode(encoded);
    }

    public static class Decoder extends ObjectCopier {

        private final Map<Integer, Object> idToObject = new HashMap<>();
        private final ClassLoader loader;

        public Decoder(ClassLoader loader) {
            this.loader = loader;
        }

        public Class<?> loadClass(String className) {
            try {
                return Class.forName(className, false, loader);
            } catch (ClassNotFoundException e) {
                throw new GraalError(e);
            }
        }

        Object getObject(int id, boolean requireNonNull) {
            Object obj = idToObject.get(id);
            GraalError.guarantee(obj != null || id == 0 || !requireNonNull, "Could not resolve object id: %d", id);
            return obj;
        }

        void decodeMap(String encoded, BiConsumer<Object, Object> putMethod) {
            for (String e : splitSpaceSeparatedElements(encoded)) {
                String[] keyValue = e.split(":");
                GraalError.guarantee(keyValue.length == 2, "Invalid encoded key:value: %s", e);
                resolveId(keyValue[0], k -> resolveId(keyValue[1], v -> putMethod.accept(k, v)));
            }
        }

        private void addDecodedObject(int id, Object obj) {
            Object conflict = idToObject.put(id, obj);
            GraalError.guarantee(conflict == null, "Objects both have id %d: %s and %s", id, obj, conflict);
        }

        private static void writeField(Field field, Object receiver, Object value) {
            try {
                field.set(receiver, value);
            } catch (IllegalAccessException e) {
                throw new GraalError(e);
            }
        }

        /**
         * Action deferred due to unresolved object id.
         */
        record Deferred(Runnable runnable, int lineNum) {
        }

        List<Deferred> deferred;
        int lineNum = -1;

        private Object decode(String encoded) {
            deferred = new ArrayList<>();
            lineNum = 0;

            Iterator<String> iter = encoded.lines().iterator();
            try {
                while (iter.hasNext()) {
                    String line = iter.next();
                    lineNum++;
                    int colon = line.indexOf(':');
                    GraalError.guarantee(colon != -1, "Missing ':' in line: %s", line);
                    int id = Integer.parseInt(line.substring(0, colon));
                    switch (line.charAt(colon + 1)) {
                        case '<': {
                            Matcher matcher = BUILTIN_LINE.matcher(line.substring(colon + 1));
                            GraalError.guarantee(matcher.matches(), "Invalid builtin line: %s", line);
                            String className = matcher.group("class");
                            String encodingName = matcher.group("encodingName");
                            String value = matcher.group("value");
                            Class<?> clazz = loadClass(className);
                            Builtin builtin = getBuiltin(clazz);
                            GraalError.guarantee(builtin != null, "No builtin for %s: %s", className, line);
                            builtin.checkClass(clazz);
                            addDecodedObject(id, builtin.decode(this, clazz, encodingName, value));
                            break;
                        }
                        case '[': {
                            Matcher matcher = ARRAY_LINE.matcher(line.substring(colon + 1));
                            GraalError.guarantee(matcher.matches(), "Invalid array line: %s", line);
                            String componentTypeName = matcher.group("componentType");
                            String[] elements = splitSpaceSeparatedElements(matcher.group("elements"));
                            switch (componentTypeName) {
                                case "boolean": {
                                    boolean[] arr = new boolean[elements.length];
                                    for (int i = 0; i < elements.length; i++) {
                                        arr[i] = Boolean.parseBoolean(elements[i]);
                                    }
                                    addDecodedObject(id, arr);
                                    break;
                                }
                                case "byte": {
                                    byte[] arr = new byte[elements.length];
                                    for (int i = 0; i < elements.length; i++) {
                                        arr[i] = Byte.parseByte(elements[i]);
                                    }
                                    addDecodedObject(id, arr);
                                    break;
                                }
                                case "char": {
                                    throw GraalError.shouldNotReachHere("char[] should be handled by " + StringBuiltin.class);
                                }
                                case "short": {
                                    short[] arr = new short[elements.length];
                                    for (int i = 0; i < elements.length; i++) {
                                        arr[i] = Short.parseShort(elements[i]);
                                    }
                                    addDecodedObject(id, arr);
                                    break;
                                }
                                case "int": {
                                    int[] arr = new int[elements.length];
                                    for (int i = 0; i < elements.length; i++) {
                                        arr[i] = Integer.parseInt(elements[i]);
                                    }
                                    addDecodedObject(id, arr);
                                    break;
                                }
                                case "float": {
                                    float[] arr = new float[elements.length];
                                    for (int i = 0; i < elements.length; i++) {
                                        arr[i] = Float.parseFloat(elements[i]);
                                    }
                                    addDecodedObject(id, arr);
                                    break;
                                }
                                case "long": {
                                    long[] arr = new long[elements.length];
                                    for (int i = 0; i < elements.length; i++) {
                                        arr[i] = Long.parseLong(elements[i]);
                                    }
                                    addDecodedObject(id, arr);
                                    break;
                                }
                                case "double": {
                                    double[] arr = new double[elements.length];
                                    for (int i = 0; i < elements.length; i++) {
                                        arr[i] = Double.parseDouble(elements[i]);
                                    }
                                    addDecodedObject(id, arr);
                                    break;
                                }
                                default: {
                                    Class<?> componentType = loadClass(componentTypeName);
                                    Object[] arr = (Object[]) Array.newInstance(componentType, elements.length);
                                    addDecodedObject(id, arr);
                                    for (int i = 0; i < elements.length; i++) {
                                        int elementId = Integer.parseInt(elements[i]);
                                        int index = i;
                                        resolveId(elementId, o -> arr[index] = o);
                                    }
                                    break;
                                }
                            }
                            break;
                        }
                        case '@': {
                            String fieldDesc = line.substring(colon + 2);
                            int lastDot = fieldDesc.lastIndexOf('.');
                            GraalError.guarantee(lastDot != -1, "Invalid field name: %s", fieldDesc);
                            String className = fieldDesc.substring(0, lastDot);
                            String fieldName = fieldDesc.substring(lastDot + 1);
                            Class<?> declaringClass = loadClass(className);
                            Field field = getField(declaringClass, fieldName);
                            addDecodedObject(id, readField(field, null));
                            break;
                        }
                        case '{': {
                            Matcher matcher = OBJECT_LINE.matcher(line.substring(colon + 1));
                            GraalError.guarantee(matcher.matches(), "Invalid object line: %s", line);
                            String className = matcher.group("class");
                            int fieldCount = Integer.parseInt(matcher.group("fieldCount"));
                            Class<?> clazz = loadClass(className);
                            Object obj = allocateInstance(clazz);
                            addDecodedObject(id, obj);
                            ClassInfo classInfo = classInfos.computeIfAbsent(clazz, ClassInfo::of);
                            for (int i = 0; i < fieldCount; i++) {
                                GraalError.guarantee(iter.hasNext(), "Truncated input");
                                String fieldLine = iter.next();
                                lineNum++;
                                Matcher fieldMatcher = FIELD_LINE.matcher(fieldLine);
                                GraalError.guarantee(fieldMatcher.matches(), "Invalid field line: %s", fieldLine);
                                String fieldDesc = fieldMatcher.group("desc");
                                String value = fieldMatcher.group("value");
                                Field field = classInfo.fields().get(fieldDesc);
                                GraalError.guarantee(field != null, "Unknown field: %s", fieldDesc);
                                Class<?> type = field.getType();

                                int expectTypeId = Integer.parseInt(fieldMatcher.group("typeId"));
                                resolveId(expectTypeId, o -> checkFieldType(expectTypeId, field));

                                if (type.isPrimitive()) {
                                    switch (type.getName()) {
                                        case "boolean": {
                                            writeField(field, obj, Boolean.parseBoolean(value));
                                            break;
                                        }
                                        case "byte": {
                                            writeField(field, obj, Byte.parseByte(value));
                                            break;
                                        }
                                        case "char": {
                                            writeField(field, obj, (char) Integer.parseInt(value));
                                            break;
                                        }
                                        case "short": {
                                            writeField(field, obj, Short.parseShort(value));
                                            break;
                                        }
                                        case "int": {
                                            writeField(field, obj, Integer.parseInt(value));
                                            break;
                                        }
                                        case "float": {
                                            writeField(field, obj, Float.parseFloat(value));
                                            break;
                                        }
                                        case "long": {
                                            writeField(field, obj, Long.parseLong(value));
                                            break;
                                        }
                                        case "double": {
                                            writeField(field, obj, Double.parseDouble(value));
                                            break;
                                        }
                                        default: {
                                            throw new GraalError("Unexpected primitive type: %s", type.getName());
                                        }
                                    }
                                } else {
                                    resolveId(value, o -> writeField(field, obj, o));
                                }
                            }
                            break;
                        }
                        default: {
                            throw new GraalError("Invalid char after ':' in line: %s", line);
                        }
                    }
                }
                for (Deferred d : deferred) {
                    lineNum = d.lineNum();
                    d.runnable().run();
                }
            } catch (Throwable e) {
                String line = encoded.lines().skip(lineNum - 1).findFirst().get();
                throw new GraalError(e, "Error on line %d: %s", lineNum, line);
            } finally {
                deferred = null;
                lineNum = -1;
            }
            return getObject(1, true);
        }

        void resolveId(String id, Consumer<Object> c) {
            try {
                resolveId(Integer.parseInt(id), c);
            } catch (NumberFormatException e) {
                throw new GraalError(e, "Invalid object id: %s", id);
            }
        }

        void resolveId(int id, Consumer<Object> c) {
            if (id != 0) {
                Object objValue = getObject(id, false);
                if (objValue != null) {
                    c.accept(objValue);
                } else {
                    deferred.add(new Deferred(() -> c.accept(getObject(id, true)), lineNum));
                }
            } else {
                c.accept(null);
            }
        }

        private void checkFieldType(int expectTypeId, Field field) {
            Class<?> actualType = field.getType();
            Class<?> expectType = (Class<?>) idToObject.get(expectTypeId);
            GraalError.guarantee(actualType.equals(expectType), "Type of %s has changed: %s != %s", field, expectType, actualType);
        }

        private static Object allocateInstance(Class<?> clazz) {
            try {
                return UNSAFE.allocateInstance(clazz);
            } catch (InstantiationException e) {
                throw new GraalError(e);
            }
        }
    }

    final Builtin getBuiltin(Class<?> clazz) {
        return getBuiltin(clazz, false);
    }

    final Builtin getBuiltin(Class<?> clazz, boolean onlyCheck) {
        if (notBuiltins.contains(clazz)) {
            return null;
        }
        Builtin b = builtinClasses.get(clazz);
        if (b == null) {
            for (var e : builtinClasses.entrySet()) {
                if (e.getKey().isAssignableFrom(clazz)) {
                    b = e.getValue();
                    break;
                }
            }
            if (!onlyCheck) {
                if (b == null) {
                    notBuiltins.add(clazz);
                } else {
                    builtinClasses.put(clazz, b);
                }
            }
        }
        return b;
    }

    public static class Encoder extends ObjectCopier {

        final Map<Object, ObjectID> objectToId = new IdentityHashMap<>();
        final List<Object> objects = new ArrayList<>();

        /**
         * Map from values to static final fields. In a serialized object graph, references to such
         * values are encoded using the static final field they come from. This field is then looked
         * up via reflection when the value needs to be decoded.
         */
        final Map<Object, Field> externalValues;

        public Encoder(List<Field> externalValueFields) {
            this(gatherExternalValues(externalValueFields));
        }

        /**
         * Use precomputed {@code externalValues} to avoid recomputing them.
         */
        public Encoder(Map<Object, Field> externalValues) {
            objects.add(null);
            objectToId.put(null, new ObjectID(0, null));
            this.externalValues = externalValues;
        }

        public static Map<Object, Field> gatherExternalValues(List<Field> externalValueFields) {
            Map<Object, Field> result = new IdentityHashMap<>();
            for (Field f : externalValueFields) {
                addExternalValue(result, f);
            }
            return result;
        }

        /**
         * Gets a {@link ClassInfo} for encoding the fields of {@code declaringClass}.
         * <p>
         * A subclass can override this to enforce encoding invariants on classes or fields.
         *
         * @throws GraalError if an invariant is violated
         */
        protected ClassInfo makeClassInfo(Class<?> declaringClass) {
            return ClassInfo.of(declaringClass);
        }

        private static void addExternalValue(Map<Object, Field> externalValues, Field field) {
            GraalError.guarantee(Modifier.isStatic(field.getModifiers()), "Field '%s' is not static. Only a static field can be used as known location for an instance.", field);
            Object value = readField(field, null);
            if (value == null) {
                /* There is only one null, no need to know where it came from */
                return;
            }
            Field oldField = externalValues.put(value, field);
            if (oldField != null) {
                Object oldValue = readField(oldField, null);
                GraalError.guarantee(oldValue == value,
                                "%s and %s have different values: %s != %s", field, oldField, value, oldValue);
            }

        }

        public Map<Object, Field> getExternalValues() {
            return Collections.unmodifiableMap(externalValues);
        }

        private String encodeMap(UnmodifiableEconomicMap<?, ?> map) {
            UnmodifiableMapCursor<?, ?> cursor = map.getEntries();
            StringBuilder value = new StringBuilder();
            while (cursor.advance()) {
                if (!value.isEmpty()) {
                    value.append(" ");
                }
                value.append(makeId(cursor.getKey(), null).id()).append(":").append(makeId(cursor.getValue(), null).id());
            }
            return value.toString();
        }

        void makeMapChildIds(EconomicMap<?, ?> map, ObjectPath objectPath) {
            UnmodifiableMapCursor<?, ?> cursor = map.getEntries();
            while (cursor.advance()) {
                Object key = cursor.getKey();
                String keyString = key instanceof String ? "\"" + key + '"' : String.valueOf(key);
                makeId(key, objectPath.add("{key:" + keyString + "}"));
                makeId(cursor.getValue(), objectPath.add("{" + keyString + "}"));
            }
        }

        /**
         * Checks that {@code value} is not an instance of {@code type}.
         *
         * @param reason reason to use in the error message if the check fails
         */
        static void checkIllegalValue(Class<?> type, Object value, ObjectPath objectPath, String reason) {
            if (type.isInstance(value)) {
                throw new GraalError("Illegal instance of %s: %s%n  Type: %s%n  Value: %s%n  Path: %s",
                                type.getName(), reason, value.getClass().getName(), value, objectPath);
            }
        }

        ObjectID makeId(Object obj, ObjectPath objectPath) {
            if (!objectToId.containsKey(obj)) {
                ObjectID id = new ObjectID(objects.size(), objectPath);
                Field field = externalValues.get(obj);
                if (field != null) {
                    objects.add(field);
                    objectToId.put(obj, id);
                    objectToId.put(field, id);
                    return id;
                }

                objects.add(obj);
                objectToId.put(obj, id);

                Class<?> clazz = obj.getClass();
                Builtin builtin = getBuiltin(clazz);
                if (builtin != null) {
                    builtin.checkObject(obj);
                    builtin.makeChildIds(this, obj, objectPath);
                    return id;
                }

                checkIllegalValue(Field.class, obj, objectPath, "Field type is used in object copying implementation");
                checkIllegalValue(FieldIntrospection.class, obj, objectPath, "Graal metadata type cannot be copied");

                if (clazz.isArray()) {
                    Class<?> componentType = clazz.getComponentType();
                    if (!componentType.isPrimitive()) {
                        Object[] objArray = (Object[]) obj;
                        int index = 0;
                        for (Object element : objArray) {
                            makeId(element, objectPath.add(index));
                            index++;
                        }
                    }
                } else {
                    checkIllegalValue(LocationIdentity.class, obj, objectPath, "must come from a static field");
                    checkIllegalValue(HashSet.class, obj, objectPath, "hashes are typically not stable across VM executions");

                    ClassInfo classInfo = makeClassInfo(clazz, objectPath);
                    for (Field f : classInfo.fields().values()) {
                        String fieldName = f.getDeclaringClass().getSimpleName() + "#" + f.getName();
                        makeId(f.getType(), objectPath.add(fieldName + ":type"));
                        if (!f.getType().isPrimitive()) {
                            Object fieldValue = readField(f, obj);
                            makeId(fieldValue, objectPath.add(fieldName));
                        }
                    }
                }

            }
            return objectToId.get(obj);
        }

        private ClassInfo makeClassInfo(Class<?> clazz, ObjectPath objectPath) {
            try {
                return classInfos.computeIfAbsent(clazz, this::makeClassInfo);
            } catch (Throwable e) {
                throw new GraalError(e, "Error creating ClassInfo%n  Path: %s", objectPath);
            }
        }

        private void encode(PrintStream out) {
            for (int id = 1; id < objects.size(); id++) {
                Object obj = objects.get(id);
                Class<?> clazz = obj.getClass();
                Builtin builtin = getBuiltin(clazz);
                if (builtin != null) {
                    String encodingName = builtin.encodingName(obj);
                    String encoding = encodingName == null ? "" : ":" + encodingName;
                    out.printf("%d:<%s%s> = %s%n", id, clazz.getName(), encoding, builtin.encode(this, obj));
                } else if (clazz.isArray()) {
                    Class<?> componentType = clazz.getComponentType();
                    if (!componentType.isPrimitive()) {
                        String elements = Stream.of((Object[]) obj).map(this::getIdString).collect(Collectors.joining(" "));
                        out.printf("%d:[%s] = %s%n", id, componentType.getName(), elements);
                    } else {
                        int length = Array.getLength(obj);
                        StringBuilder elements = new StringBuilder(length * 5);
                        for (int i = 0; i < length; i++) {
                            elements.append(' ').append(Array.get(obj, i));
                        }
                        out.printf("%d:[%s] =%s%n", id, componentType.getName(), elements);
                    }
                } else {
                    if (clazz == Field.class) {
                        Field field = (Field) obj;
                        out.printf("%d:@%s.%s%n", id, field.getDeclaringClass().getName(), field.getName());
                    } else {
                        ClassInfo classInfo = classInfos.get(clazz);
                        out.printf("%d:{%s:%d}%n", id, clazz.getName(), classInfo.fields().size());
                        for (var e : classInfo.fields().entrySet()) {
                            Field f = e.getValue();
                            Object fValue = readField(f, obj);
                            Class<?> fieldType = f.getType();
                            int fieldTypeId = makeId(fieldType, null).id();
                            if (!fieldType.isPrimitive()) {
                                fValue = getIdString(fValue);
                            } else if (fieldType == char.class) {
                                fValue = (int) (Character) fValue;
                            }
                            out.printf("  %s:%d = %s%n", e.getKey(), fieldTypeId, fValue);
                        }
                    }
                }
            }
        }

        private String getIdString(Object o) {
            GraalError.guarantee(objectToId.containsKey(o), "Unknown object: %s", o);
            return String.valueOf(objectToId.get(o).id());
        }
    }

    public static Object readField(Field field, Object receiver) {
        try {
            return field.get(receiver);
        } catch (IllegalAccessException | Error e) {
            throw new GraalError(e, "Error reading %s", field);
        }
    }

    /**
     * Gets the declared field {@code fieldName} from {@code declaredClass}. The field is made
     * accessible before returning.
     *
     * @throws GraalError if the field does not exist
     */
    public static Field getField(Class<?> declaredClass, String fieldName) {
        try {
            Field f = declaredClass.getDeclaredField(fieldName);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    public static List<Field> getExternalValueFields() throws IOException {
        List<Field> externalValues = new ArrayList<>();
        addImmutableCollectionsFields(externalValues);
        addStaticFinalObjectFields(LocationIdentity.class, externalValues);

        try (FileSystem fs = FileSystems.newFileSystem(URI.create("jrt:/"), Collections.emptyMap())) {
            for (String module : List.of("jdk.internal.vm.ci", "jdk.graal.compiler", "com.oracle.graal.graal_enterprise")) {
                Path top = fs.getPath("/modules/" + module);
                try (Stream<Path> files = Files.find(top, Integer.MAX_VALUE, (path, attrs) -> attrs.isRegularFile())) {
                    files.forEach(p -> {
                        String fileName = p.getFileName().toString();
                        if (fileName.endsWith(".class") && !fileName.equals("module-info.class")) {
                            // Strip module prefix and convert to dotted form
                            int nameCount = p.getNameCount();
                            String className = p.subpath(2, nameCount).toString().replace('/', '.');
                            // Strip ".class" suffix
                            className = className.replace('/', '.').substring(0, className.length() - ".class".length());
                            try {
                                Class<?> graalClass = Class.forName(className);
                                addStaticFinalObjectFields(graalClass, externalValues);
                            } catch (ClassNotFoundException e) {
                                throw new GraalError(e);
                            }
                        }
                    });
                }
            }
        }
        return externalValues;
    }

    /**
     * Adds the static, final, non-primitive fields of non-enum {@code declaringClass} to
     * {@code fields}. In the process, the fields are made {@linkplain Field#setAccessible
     * accessible}.
     */
    public static void addStaticFinalObjectFields(Class<?> declaringClass, List<Field> fields) {
        if (Enum.class.isAssignableFrom(declaringClass)) {
            return;
        }
        for (Field field : declaringClass.getDeclaredFields()) {
            int fieldModifiers = field.getModifiers();
            int fieldMask = Modifier.STATIC | Modifier.FINAL;
            if ((fieldModifiers & fieldMask) != fieldMask) {
                continue;
            }
            if (field.getType().isPrimitive()) {
                continue;
            }
            field.setAccessible(true);
            fields.add(field);
        }
    }

    /**
     * Adds the EMPTY* fields from {@code java.util.ImmutableCollections} to {@code fields}, making
     * them {@linkplain Field#setAccessible accessible} in the process.
     */
    private static void addImmutableCollectionsFields(List<Field> fields) {
        Class<?> c = List.of().getClass().getDeclaringClass();
        GraalError.guarantee(c.getName().equals("java.util.ImmutableCollections"), "Incompatible ImmutableCollections class");
        for (Field f : c.getDeclaredFields()) {
            if (f.getName().startsWith("EMPTY")) {
                int modifiers = f.getModifiers();
                GraalError.guarantee(Modifier.isStatic(modifiers), "Expect %s to be static", f);
                GraalError.guarantee(Modifier.isFinal(modifiers), "Expect %s to be final", f);
                GraalError.guarantee(!f.getType().isPrimitive(), "Expect %s to be non-primitive", f);
                f.setAccessible(true);
                fields.add(f);
            }
        }
    }

    /**
     * Describes the path from a root object to a target object. That is, the sequence of field and
     * array reads performed on the root object to access the target object.
     *
     * @param prefix the prefix path
     * @param name the last field or array index read in the path
     */
    record ObjectPath(ObjectPath prefix, Object name) {
        /**
         * Creates an object path for a root object.
         *
         * @param rootName names a reference to the root object (e.g. "[root]" or the qualified name
         *            of a static field)
         */
        public static ObjectPath of(String rootName) {
            return new ObjectPath(null, rootName);
        }

        /**
         * Extends this path with a field read.
         *
         * @param fieldName name of a field (or pseudo field)
         * @return extended path
         */
        ObjectPath add(String fieldName) {
            return new ObjectPath(this, fieldName);
        }

        /**
         * Extends this path with an array index.
         *
         * @param index an array index
         * @return extended path
         */
        ObjectPath add(int index) {
            return new ObjectPath(this, index);
        }

        /**
         * Gets the path as a string, starting at the root of the path. Examples:
         *
         * <pre>
         * [root].{"encodedSnippets"}.snippetObjects.[16]
         * StampFactory.stampCache.[1]
         * </pre>
         */
        @Override
        public String toString() {
            List<String> components = new ArrayList<>();
            for (ObjectPath p = this; p != null; p = p.prefix) {
                if (p.name instanceof String s) {
                    components.add(s);
                } else {
                    components.add("[" + p.name + "]");
                }
            }
            return String.join(".", components.reversed());
        }
    }

    /**
     * A unique int id for an object as well as the path by which it was (first) reached.
     */
    record ObjectID(int id, ObjectPath path) {
    }
}
