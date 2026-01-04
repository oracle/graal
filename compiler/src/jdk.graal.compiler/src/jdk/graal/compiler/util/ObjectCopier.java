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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicMapWrap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.FieldIntrospection;
import jdk.graal.compiler.core.common.util.FrequencyEncoder;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.JavaKind;

/**
 * Support for deep copying an object across processes by {@linkplain #encode encoding} it to bytes
 * in the first process and {@linkplain #decode decoding} it back into an object in the second
 * process. This copying requires that the classes of the copied objects are the same in both
 * processes with respect to fields.
 * <p>
 * See the {@link Builtin} subclasses for encoding of specific types.
 */
public class ObjectCopier {

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
                this.concreteClasses = CollectionsUtil.setOf(concreteClasses);
            } else {
                Set<Class<?>> l = new EconomicHashSet<>(Arrays.asList(concreteClasses));
                l.add(clazz);
                this.concreteClasses = Collections.unmodifiableSet(l);
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
         * Ensures object ids are created for the values referenced by {@code obj} that will be
         * handled by this builtin when {@code obj} is encoded. For example, the values in a map.
         */
        @SuppressWarnings("unused")
        protected void makeChildIds(Encoder encoder, Object obj, ObjectPath objectPath) {
        }

        /**
         * Encodes the value of {@code obj} to a String that does not contain {@code '\n'} or
         * {@code '\r'}.
         */
        protected abstract void encode(Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException;

        /**
         * Decodes {@code encoded} to an object of a type handled by this builtin.
         */
        protected abstract Object decode(Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException;

        @Override
        public String toString() {
            return "builtin:" + clazz.getName();
        }
    }

    /**
     * Builtin for handling {@link Class} values. The className is in {@link Class#getName()}
     * format.
     */
    static final class ClassBuiltin extends Builtin {

        ClassBuiltin() {
            super(Class.class);
        }

        private static String getName(Object obj) {
            return ((Class<?>) obj).getName();
        }

        @Override
        protected void makeChildIds(Encoder encoder, Object obj, ObjectPath objectPath) {
            encoder.makeStringId(getName(obj), objectPath);
        }

        @Override
        protected void encode(Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            String name = getName(obj);
            encoder.writeString(stream, name);
        }

        @Override
        protected Object decode(Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            String encoded = decoder.readString(stream);
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
     * Builtin for handling boxed primitives.
     */
    static final class BoxBuiltin extends Builtin {

        private static final Map<Class<?>, JavaKind> CONCRETE_CLASSES = //
                        Stream.of(JavaKind.values())//
                                        .filter(k -> k.isPrimitive() && k != JavaKind.Void)//
                                        .collect(Collectors.toMap(JavaKind::toBoxedJavaClass, Function.identity()));

        BoxBuiltin() {
            super(Number.class, CONCRETE_CLASSES.keySet().toArray(Class<?>[]::new));
        }

        @Override
        protected void encode(Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            stream.writeUntypedValue(obj);
        }

        @Override
        protected Object decode(Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            char typeCh = CONCRETE_CLASSES.get(concreteType).getTypeChar();
            return stream.readUntypedValue(typeCh);
        }
    }

    /**
     * Builtin for handling {@link String} values.
     */
    static final class StringBuiltin extends Builtin {

        StringBuiltin() {
            super(String.class, char[].class);
        }

        private static String asString(Object obj) {
            return obj instanceof String ? (String) obj : new String((char[]) obj);
        }

        @Override
        protected void makeChildIds(Encoder encoder, Object obj, ObjectPath objectPath) {
            encoder.makeStringId(asString(obj), objectPath);
        }

        @Override
        protected void encode(Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            encoder.writeString(stream, asString(obj));
        }

        @Override
        protected Object decode(Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            String s = decoder.readString(stream);
            if (concreteType == char[].class) {
                return s.toCharArray();
            }
            return s;
        }
    }

    /**
     * Builtin for handling {@link Enum} values. The value is described by its
     * {@link Enum#ordinal()}.
     */
    static final class EnumBuiltin extends Builtin {

        EnumBuiltin() {
            super(Enum.class);
        }

        @Override
        protected void encode(Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            Enum<?> con = (Enum<?>) obj;
            stream.writePackedUnsignedInt(con.ordinal());
            stream.writeShort(fingerprint(con));
        }

        private static short fingerprint(Enum<?> con) {
            int h = con.name().hashCode();
            return hashIntToShort(h);
        }

        @Override
        protected Object decode(Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            int ord = stream.readPackedUnsignedInt();
            int fingerprint = stream.readShort();
            Enum<?> con = (Enum<?>) concreteType.getEnumConstants()[ord];
            GraalError.guarantee(fingerprint(con) == fingerprint, "Enum constant type mismatch: %s ordinal %d not expected to be %s", concreteType.getName(), ord, con);
            return con;
        }
    }

    static final class HashMapBuiltin extends Builtin {

        final Map<Class<?>, Supplier<?>> factories;

        HashMapBuiltin() {
            super(HashMap.class, IdentityHashMap.class, LinkedHashMap.class, SnippetTemplate.LRUCache.class);
            factories = new EconomicHashMap<>();
            factories.put(HashMap.class, HashMap::new);
            factories.put(IdentityHashMap.class, IdentityHashMap::new);
            factories.put(LinkedHashMap.class, LinkedHashMap::new);
            factories.put(SnippetTemplate.LRUCache.class, SnippetTemplate.LRUCache::new);
        }

        @Override
        protected void makeChildIds(Encoder encoder, Object obj, ObjectPath objectPath) {
            Map<?, ?> map = (Map<?, ?>) obj;
            encoder.makeMapChildIds(new EconomicMapWrap<>(map), objectPath);
        }

        @Override
        protected void encode(Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            Map<?, ?> map = (Map<?, ?>) obj;
            encoder.encodeMap(stream, new EconomicMapWrap<>(map));
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Object decode(Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            Map<Object, Object> map = (Map<Object, Object>) factories.get(concreteType).get();
            decoder.decodeMap(stream, map::put);
            return map;
        }
    }

    static final class EconomicMapBuiltin extends Builtin {
        EconomicMapBuiltin() {
            super(EconomicMap.class, EconomicMap.create().getClass());
        }

        @Override
        protected void makeChildIds(Encoder encoder, Object obj, ObjectPath objectPath) {
            EconomicMap<?, ?> map = (EconomicMap<?, ?>) obj;
            GraalError.guarantee(map.getEquivalenceStrategy() == Equivalence.DEFAULT,
                            "Only DEFAULT strategy supported: %s", map.getEquivalenceStrategy());
            encoder.makeMapChildIds(map, objectPath);
        }

        @Override
        protected void encode(Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            encoder.encodeMap(stream, (UnmodifiableEconomicMap<?, ?>) obj);
        }

        @Override
        protected Object decode(Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            if (EconomicMap.class.isAssignableFrom(concreteType)) {
                EconomicMap<Object, Object> map = EconomicMap.create();
                decoder.decodeMap(stream, map::put);
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
    public record ClassInfo(Class<?> clazz, SortedMap<String, Field> fields) implements Comparable<ClassInfo> {
        public static ClassInfo of(Class<?> declaringClass) {
            SortedMap<String, Field> fields = new TreeMap<>();
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

        @Override
        public int compareTo(ClassInfo o) {
            return clazz.getName().compareTo(o.clazz.getName());
        }

        ClassInfo makeChildIds(Encoder encoder, ObjectPath objectPath) {
            encoder.makeStringId(clazz.getName(), objectPath);
            for (Field field : fields.values()) {
                encoder.makeStringId(field.getDeclaringClass().getName(), objectPath);
                encoder.makeStringId(field.getName(), objectPath);
                encoder.makeStringId(field.getType().getName(), objectPath);
            }
            return this;
        }

        void encode(Encoder encoder, ObjectCopierOutputStream out) throws IOException {
            encoder.writeString(out, clazz.getName());
            out.writePackedUnsignedInt(fields.size());
            for (Field field : fields.values()) {
                encoder.writeString(out, field.getDeclaringClass().getName());
                encoder.writeString(out, field.getName());
                encoder.writeString(out, field.getType().getName());
            }
        }

        public static ClassInfo decode(Decoder decoder, ObjectCopierInputStream in) throws IOException {
            String className = decoder.readString(in);
            Class<?> clazz = decoder.loadClass(className);
            ClassInfo classInfo = of(clazz);
            int count = in.readPackedUnsignedInt();
            if (count != classInfo.fields.size()) {
                throw new GraalError("field count mismatch (%d != %d) for %s", count, classInfo.fields.size(), classInfo);
            }
            for (Field field : classInfo.fields.values()) {
                classInfo.checkField("declaring class", field.getDeclaringClass().getName(), decoder.readString(in));
                classInfo.checkField("name", field.getName(), decoder.readString(in));
                classInfo.checkField("type", field.getType().getName(), decoder.readString(in));
            }
            return classInfo;
        }

        private void checkField(String attr, String expect, String actual) {
            if (!expect.equals(actual)) {
                throw new GraalError("field %s mismatch (%s != %s) for %s", attr, expect, actual, this);
            }
        }

        private String fieldString(Field field) {
            String res = field.getName() + ":" + field.getType().getName();
            if (field.getDeclaringClass() != clazz) {
                res = field.getDeclaringClass().getName() + "." + res;
            }
            return res;
        }

        @Override
        public String toString() {
            String allFields = fields.values().stream().map(this::fieldString).collect(Collectors.joining(","));
            return clazz.getName() + "{" + allFields + "}";
        }
    }

    private static short hashIntToShort(int h) {
        return (short) (h ^ (h >>> 16));
    }

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static final Comparator<Class<?>> CLASS_COMPARATOR = Comparator.comparing(Class::getName);
    final Map<Class<?>, ClassInfo> classInfos = new TreeMap<>(CLASS_COMPARATOR);
    final Map<Class<?>, Builtin> builtinClasses = new LinkedHashMap<>();
    final Set<Class<?>> notBuiltins = new EconomicHashSet<>();

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
        addBuiltin(new BoxBuiltin());
        addBuiltin(new EconomicMapBuiltin());
        addBuiltin(new EnumBuiltin());

        HashMapBuiltin hashMapBuiltin = new HashMapBuiltin();
        addBuiltin(hashMapBuiltin);
        addBuiltin(hashMapBuiltin, IdentityHashMap.class);

        StringBuiltin stringBuiltin = new StringBuiltin();
        addBuiltin(stringBuiltin);
        addBuiltin(stringBuiltin, char[].class);
    }

    /**
     * Encodes {@code root} to a String using {@code encoder}.
     */
    public static byte[] encode(Encoder encoder, Object root) {
        encoder.makeId(root, ObjectPath.of("[root:" + root.getClass().getName() + "]"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectCopierOutputStream cos = new ObjectCopierOutputStream(baos, encoder.debugOutput)) {
            encoder.encode(cos, root);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    public static Object decode(byte[] encoded, ClassLoader loader) {
        Decoder decoder = new Decoder(loader);
        return decode(decoder, encoded);
    }

    public static Object decode(Decoder decoder, byte[] encoded) {
        return decoder.decode(encoded);
    }

    public static class Decoder extends ObjectCopier {

        private final Map<Integer, Object> idToObject = new EconomicHashMap<>();
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

        void decodeMap(ObjectCopierInputStream stream, BiConsumer<Object, Object> putMethod) throws IOException {
            int size = stream.readPackedUnsignedInt();
            for (int i = 0; i < size; i++) {
                int keyId = readId(stream);
                int valueId = readId(stream);
                resolveId(keyId, k -> resolveId(valueId, v -> putMethod.accept(k, v)));
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
        record Deferred(Runnable runnable, int recordNum, int fieldNum) {
        }

        List<Deferred> deferred;
        int recordNum = -1;
        int fieldNum = -1;
        String[] strings;

        private Object decode(byte[] encoded) {
            int rootId;
            deferred = new ArrayList<>();
            recordNum = 0;

            try (ObjectCopierInputStream stream = new ObjectCopierInputStream(new ByteArrayInputStream(encoded))) {
                int nstrings = stream.readPackedUnsignedInt();
                strings = new String[nstrings];
                for (int i = 0; i < nstrings; i++) {
                    strings[i] = stream.readStringValue();
                }
                int nClassInfos = stream.readPackedUnsignedInt();
                for (int i = 0; i < nClassInfos; i++) {
                    ClassInfo classInfo = ClassInfo.decode(this, stream);
                    ClassInfo conflict = classInfos.put(classInfo.clazz, classInfo);
                    GraalError.guarantee(conflict == null, "Duplicate class infos found for %s", classInfo.clazz);
                }

                rootId = readId(stream);
                for (int id = 1;; id++) {
                    recordNum = id;
                    fieldNum = -1;
                    int c = stream.read();
                    if (c == -1) {
                        break;
                    }
                    switch (c) {
                        case '<': {
                            String className = readString(stream);
                            Class<?> clazz = loadClass(className);
                            Builtin builtin = getBuiltin(clazz);
                            GraalError.guarantee(builtin != null, "No builtin for %s in record %d", className, recordNum);
                            builtin.checkClass(clazz);
                            addDecodedObject(id, builtin.decode(this, clazz, stream));
                            break;
                        }
                        case '[': { // primitive array
                            Object arr = stream.readTypedPrimitiveArray();
                            addDecodedObject(id, arr);
                            break;
                        }
                        case ']': { // object array
                            String componentTypeName = readString(stream);
                            Class<?> componentType = loadClass(componentTypeName);
                            int length = stream.readPackedUnsignedInt();
                            int[] elements = new int[length];
                            for (int i = 0; i < length; i++) {
                                elements[i] = readId(stream);
                            }
                            Object[] arr = (Object[]) Array.newInstance(componentType, elements.length);
                            addDecodedObject(id, arr);
                            for (int i = 0; i < elements.length; i++) {
                                int index = i;
                                resolveId(elements[i], o -> arr[index] = o);
                            }
                            break;
                        }
                        case '@': {
                            String className = readString(stream);
                            String fieldName = readString(stream);
                            Class<?> declaringClass = loadClass(className);
                            Field field = getField(declaringClass, fieldName);
                            addDecodedObject(id, readField(field, null));
                            break;
                        }
                        case '{': {
                            String className = readString(stream);
                            Class<?> clazz = loadClass(className);
                            Object obj = allocateInstance(clazz);
                            addDecodedObject(id, obj);
                            ClassInfo classInfo = classInfos.get(clazz);
                            GraalError.guarantee(classInfo != null, "No class info for %s", clazz);
                            fieldNum = 0;
                            for (Field field : classInfo.fields.values()) {
                                Class<?> type = field.getType();
                                if (type.isPrimitive()) {
                                    char typeCh = type.descriptorString().charAt(0);
                                    Object value = stream.readUntypedValue(typeCh);
                                    writeField(field, obj, value);
                                } else {
                                    int value = readId(stream);
                                    resolveId(value, o -> writeField(field, obj, o));
                                }
                                fieldNum++;
                            }
                            break;
                        }
                        default: {
                            throw new GraalError("Invalid char '%c' for kind in record %d", c, recordNum);
                        }
                    }
                }
                for (Deferred d : deferred) {
                    recordNum = d.recordNum();
                    fieldNum = d.fieldNum;
                    d.runnable().run();
                }
            } catch (Throwable e) {
                throw new GraalError(e, "Error in record %d (field %d)", recordNum, fieldNum);
            } finally {
                deferred = null;
                recordNum = -1;
                fieldNum = -1;
            }
            return getObject(rootId, true);
        }

        private static int readId(ObjectCopierInputStream stream) throws IOException {
            return stream.readPackedUnsignedInt();
        }

        void resolveId(int id, Consumer<Object> c) {
            if (id != 0) {
                Object objValue = getObject(id, false);
                if (objValue != null) {
                    c.accept(objValue);
                } else {
                    deferred.add(new Deferred(() -> c.accept(getObject(id, true)), recordNum, fieldNum));
                }
            } else {
                c.accept(null);
            }
        }

        public String readString(ObjectCopierInputStream stream) throws IOException {
            int id = stream.readPackedUnsignedInt();
            return strings[id];
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

        final FrequencyEncoder<Object> objects = FrequencyEncoder.createIdentityEncoder();

        /**
         * We use a separate string table to deduplicate strings and to make sure they are available
         * upfront during decoding so that deferred processing with transitive dependencies is not
         * needed.
         */
        final FrequencyEncoder<String> strings = FrequencyEncoder.createEqualityEncoder();

        /**
         * Map from values to static final fields. In a serialized object graph, references to such
         * values are encoded using the static final field they come from. This field is then looked
         * up via reflection when the value needs to be decoded.
         */
        final Map<Object, Field> externalValues;

        private final PrintStream debugOutput;

        public Encoder(List<Field> externalValueFields) {
            this(externalValueFields, null);
        }

        public Encoder(List<Field> externalValueFields, PrintStream debugOutput) {
            this(gatherExternalValues(externalValueFields), debugOutput);
        }

        /**
         * Use precomputed {@code externalValues} to avoid recomputing them.
         */
        public Encoder(Map<Object, Field> externalValues) {
            this(externalValues, null);
        }

        public Encoder(Map<Object, Field> externalValues, PrintStream debugOutput) {
            objects.addObject(null);
            this.externalValues = externalValues;
            this.debugOutput = debugOutput;
        }

        public static Map<Object, Field> gatherExternalValues(List<Field> externalValueFields) {
            Map<Object, Field> result = EconomicHashMap.newIdentityMap();
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

        private void encodeMap(ObjectCopierOutputStream stream, UnmodifiableEconomicMap<?, ?> map) throws IOException {
            stream.internalWritePackedUnsignedInt(map.size());

            UnmodifiableMapCursor<?, ?> cursor = map.getEntries();
            while (cursor.advance()) {
                debugf("%n ");
                writeId(stream, getId(cursor.getKey()));
                debugf(" :");
                writeId(stream, getId(cursor.getValue()));
            }
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

        public void writeString(ObjectCopierOutputStream stream, String s) throws IOException {
            int id = strings.getIndex(s);
            stream.internalWritePackedUnsignedInt(id);
            if (debugOutput != null) {
                debugf(" %s", escapeDebugStringValue(s));
            }
        }

        public void makeStringId(String s, ObjectPath objectPath) {
            GraalError.guarantee(s != null, "Illegal null string: Path %s", objectPath);
            strings.addObject(s);
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

        void makeId(Object obj, ObjectPath objectPath) {
            Field field = externalValues.get(obj);
            if (field != null) {
                if (objects.addObject(field)) {
                    makeStringId(field.getDeclaringClass().getName(), objectPath);
                    makeStringId(field.getName(), objectPath);
                }
                return;
            }

            if (!objects.addObject(obj)) {
                return; // already known
            }

            Class<?> clazz = obj.getClass();
            Builtin builtin = getBuiltin(clazz);
            if (builtin != null) {
                builtin.checkObject(obj);
                makeStringId(clazz.getName(), objectPath);
                builtin.makeChildIds(this, obj, objectPath);
                return;
            }

            checkIllegalValue(Field.class, obj, objectPath, "Field type is used in object copying implementation");
            checkIllegalValue(FieldIntrospection.class, obj, objectPath, "Graal metadata type cannot be copied");

            if (clazz.isArray()) {
                Class<?> componentType = clazz.getComponentType();
                if (!componentType.isPrimitive()) {
                    strings.addObject(componentType.getName());
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

                prepareObject(obj);
                makeStringId(clazz.getName(), objectPath);
                ClassInfo classInfo = makeClassInfo(clazz, this, objectPath);
                classInfo.fields().forEach((fieldDesc, f) -> {
                    String fieldName = f.getDeclaringClass().getSimpleName() + "#" + f.getName();
                    if (!f.getType().isPrimitive()) {
                        Object fieldValue = readField(f, obj);
                        makeId(fieldValue, objectPath.add(fieldName));
                    }
                });
            }
        }

        @SuppressWarnings("unused")
        protected void prepareObject(Object obj) {
            /* Hook to prepare special objects */
        }

        private ClassInfo makeClassInfo(Class<?> clazz, Encoder encoder, ObjectPath objectPath) {
            try {
                ClassInfo classInfo = classInfos.get(clazz);
                if (classInfo == null) {
                    classInfo = makeClassInfo(clazz).makeChildIds(encoder, objectPath);
                    classInfos.put(clazz, classInfo);
                }
                return classInfo;
            } catch (Throwable e) {
                throw new GraalError(e, "Error creating ClassInfo%n  Path: %s", objectPath);
            }
        }

        private void encode(ObjectCopierOutputStream out, Object root) throws IOException {
            String[] encodedStrings = strings.encodeAll(new String[strings.getLength()]);
            out.internalWritePackedUnsignedInt(encodedStrings.length);
            for (String s : encodedStrings) {
                out.writeStringValue(s);
            }
            out.internalWritePackedUnsignedInt(classInfos.size());
            for (ClassInfo ci : classInfos.values()) {
                ci.encode(this, out);
            }
            Object[] encodedObjects = objects.encodeAll(new Object[objects.getLength()]);
            debugf("root:");
            writeId(out, getId(root));
            debugf("%n");
            for (int id = 1; id < encodedObjects.length; id++) {
                Object obj = encodedObjects[id];
                Class<?> clazz = obj.getClass();
                Builtin builtin = getBuiltin(clazz);
                if (builtin != null) {
                    out.internalWriteByte('<');
                    debugf("%d:<", id);
                    writeString(out, clazz.getName());
                    debugf(" > =");
                    try {
                        builtin.encode(this, out, obj);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else if (clazz.isArray()) {
                    Class<?> componentType = clazz.getComponentType();
                    if (!componentType.isPrimitive()) {
                        out.internalWriteByte(']');
                        debugf("%d:[", id);
                        writeString(out, componentType.getName());
                        debugf(" ] =");
                        Object[] objs = (Object[]) obj;
                        out.internalWritePackedUnsignedInt(objs.length);
                        for (Object o : objs) {
                            writeId(out, getId(o));
                        }
                    } else {
                        out.internalWriteByte('[');
                        debugf("%d:[ %s ] =", id, componentType.getName());
                        out.writeTypedPrimitiveArray(obj);
                    }
                } else if (clazz == Field.class) {
                    Field field = (Field) obj;
                    out.internalWriteByte('@');
                    debugf("%d:@", id);
                    writeString(out, field.getDeclaringClass().getName());
                    writeString(out, field.getName());
                } else {
                    ClassInfo classInfo = classInfos.get(clazz);
                    out.internalWriteByte('{');
                    debugf("%d:{", id);
                    writeString(out, clazz.getName());
                    debugf(" }");
                    for (var e : classInfo.fields().entrySet()) {
                        Field f = e.getValue();
                        debugf("%n ");
                        Class<?> fieldType = f.getType();
                        Object fValue = readField(f, obj);
                        if (fieldType.isPrimitive()) {
                            out.writeUntypedValue(fValue);
                        } else {
                            writeId(out, getId(fValue));
                        }
                        debugf("\t # %s", f.getName());
                        if (!fieldType.isPrimitive()) {
                            debugf(" (object)");
                        }
                    }
                }
                debugf("%n");
            }
        }

        private static void writeId(ObjectCopierOutputStream out, int id) throws IOException {
            out.writePackedUnsignedInt(id);
        }

        private int getId(Object o) {
            Field field = externalValues.get(o);
            if (field != null) {
                return objects.getIndex(field);
            }
            return objects.getIndex(o);
        }

        private void debugf(String format, Object... args) {
            if (debugOutput != null) {
                debugOutput.printf(format, args);
            }
        }

        static String escapeDebugStringValue(String s) {
            return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
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

    /**
     * Denotes a field that should not be treated as an external value.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface NotExternalValue {
        /**
         * Documents the reason why the annotated field is not an external value.
         */
        String reason();
    }

    /**
     * Gets the set of static, final fields whose values are not serialized.
     *
     * @see NotExternalValue
     */
    public static List<Field> getExternalValueFields() throws IOException {
        List<Field> externalValues = new ArrayList<>();
        addImmutableCollectionsFields(externalValues);
        externalValues.addAll(getStaticFinalObjectFields(LocationIdentity.class));

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
                                externalValues.addAll(getStaticFinalObjectFields(graalClass));
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
    public static List<Field> getStaticFinalObjectFields(Class<?> declaringClass) {
        if (Enum.class.isAssignableFrom(declaringClass)) {
            return List.of();
        }
        List<Field> fields = new ArrayList<>();
        for (Field field : declaringClass.getDeclaredFields()) {
            int fieldModifiers = field.getModifiers();
            int fieldMask = Modifier.STATIC | Modifier.FINAL;
            boolean isStaticAndFinal = (fieldModifiers & fieldMask) == fieldMask;
            if (!isStaticAndFinal) {
                continue;
            }
            if (field.getType().isPrimitive()) {
                continue;
            }
            if (field.getAnnotation(NotExternalValue.class) != null) {
                continue;
            }
            field.setAccessible(true);
            fields.add(field);
        }
        return fields;
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
    public record ObjectPath(ObjectPath prefix, Object name) {
        /**
         * Creates an object path for a root object.
         *
         * @param rootName names a reference to the root object (e.g. "[root]" or the qualified name
         *            of a static field)
         */
        static ObjectPath of(String rootName) {
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
}
