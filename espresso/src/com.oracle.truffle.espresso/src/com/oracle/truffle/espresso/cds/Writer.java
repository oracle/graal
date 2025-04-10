/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.cds;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

import org.graalvm.home.Version;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Signatures;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.ClassRegistry;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Klass.LookupMode;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ModuleTable;
import com.oracle.truffle.espresso.impl.PackageTable;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.OS;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

final class Writer implements CDSArchiveFormat {
    private final Map<Object, Integer> serializedRefs = new IdentityHashMap<>();
    private final EspressoContext espressoContext;
    private ByteBuffer byteBuffer;

    Writer(EspressoContext espressoContext) {
        this.espressoContext = espressoContext;
        this.byteBuffer = ByteBuffer.allocate(1 << 12) //
                        .order(ByteOrder.nativeOrder()) //
                        .limit(0);
    }

    /**
     * Ensures position + extraBytes <= limit, may re-allocate larger byte buffer.
     */
    private void ensureLimit(int extraBytes) {
        int requiredLimit = position() + extraBytes;
        if (requiredLimit < byteBuffer.capacity()) {
            if (requiredLimit > byteBuffer.limit()) {
                byteBuffer.limit(requiredLimit);
            }
        } else {
            int newCapacity = byteBuffer.capacity();
            while (newCapacity < requiredLimit) {
                newCapacity = Math.max(1, 2 * newCapacity);
            }
            assert newCapacity >= requiredLimit;
            int position = this.byteBuffer.position();
            ByteBuffer newByteBuffer = ByteBuffer.allocate(newCapacity) //
                            .order(this.byteBuffer.order()) //
                            .limit(requiredLimit) //
                            .put(this.byteBuffer.rewind()) //
                            .position(position);
            this.byteBuffer = newByteBuffer;
        }
        assert this.byteBuffer.position() + extraBytes <= this.byteBuffer.limit();
    }

    void write(int b) {
        ensureLimit(1);
        byteBuffer.put((byte) b);
    }

    int position() {
        return byteBuffer.position();
    }

    void position(int newPosition) {
        byteBuffer.position(newPosition);
    }

    void write(byte[] b) {
        write(b, 0, b.length);
    }

    void write(byte[] b, int off, int len) {
        ensureLimit(len);
        byteBuffer.put(b, off, len);
    }

    void writeByte(int v) {
        write(v);
    }

    void writeBoolean(boolean v) {
        writeByte(v ? 1 : 0);
    }

    void writeShort(short v) {
        ensureLimit(2);
        byteBuffer.putShort(v);
    }

    void writeChar(char v) {
        ensureLimit(2);
        byteBuffer.putChar(v);
    }

    void writeInt(int v) {
        ensureLimit(4);
        byteBuffer.putInt(v);
    }

    void writeLong(long v) {
        ensureLimit(8);
        byteBuffer.putLong(v);
    }

    void writeFloat(float v) {
        writeInt(Float.floatToRawIntBits(v));
    }

    void writeDouble(double v) {
        writeLong(Double.doubleToRawLongBits(v));
    }

    void writeHeader() {
        EspressoError.guarantee(position() == 0, "writing CDS archive header at position != 0");

        // Magic is always big endian.
        ByteOrder previousOrder = byteBuffer.order();
        try {
            byteBuffer.order(ByteOrder.BIG_ENDIAN);
            writeInt(MAGIC);
        } finally {
            byteBuffer.order(previousOrder);
        }

        // Serialization format major.minor version.
        writeInt(CDSArchiveFormat.MAJOR_VERSION);
        writeInt(CDSArchiveFormat.MINOR_VERSION);

        writeHostString(Version.getCurrent().toString());
        writeHostString("" + espressoContext.getJavaVersion().classFileVersion());
        String javaRuntimeVersion = espressoContext.readKeyFromReleaseFile(espressoContext.getVmProperties().javaHome(), "JAVA_RUNTIME_VERSION");
        if (javaRuntimeVersion == null) {
            javaRuntimeVersion = "unknown";
        }
        writeHostString(javaRuntimeVersion);
        writeHostString(OS.getCurrent().toString());
        writeHostString(System.getProperty("os.arch").toLowerCase(Locale.ROOT));
    }

    int getWrittenObjectCount() {
        return writtenObjectCount;
    }

    private int writtenObjectCount = 0;

    void writeReferenceOrObject(Object ref) {
        if (ref == null) {
            writeByte(TAG_NULL);
            return;
        }

        if (ref instanceof StaticObject guestRef && StaticObject.isNull(guestRef)) {
            writeByte(TAG_GUEST_NULL);
            return;
        }

        // Check if we've already serialized this object.
        if (serializedRefs.containsKey(ref)) {
            writeByte(TAG_REFERENCE);
            writeUnsignedVarInt(serializedRefs.get(ref));
            return;
        }

        writtenObjectCount++;

        int refId = position();
        serializedRefs.put(ref, refId);

        if (ref instanceof String hostString) {
            writeByte(TAG_STRING);
            writeHostString(hostString);
            return;
        }

        if (ref instanceof Symbol<?> symbol) {
            writeByte(TAG_SYMBOL);
            writeHostSymbol(symbol);
            return;
        }

        if (ref.getClass() == ArrayList.class) {
            writeByte(TAG_ARRAY_LIST);
            writeHostArrayList((ArrayList<?>) ref);
            return;
        }

        if (ref instanceof PackageTable.PackageEntry packageEntry) {
            writeByte(TAG_PACKAGE_ENTRY);
            writePackageEntry(packageEntry);
            return;
        }

        if (ref instanceof ModuleTable.ModuleEntry moduleEntry) {
            writeByte(TAG_MODULE_ENTRY);
            writeModuleEntry(moduleEntry);
            return;
        }

        if (ref instanceof ArchivedRegistryData archivedRegistryData) {
            writeByte(TAG_CLASS_REGISTRY_DATA);
            writeClassRegistryData(archivedRegistryData);
            return;
        }

        if (ref instanceof StaticObject guestRef) {
            Klass klass = guestRef.getKlass();
            if (espressoContext.getMeta().java_lang_String.equals(klass)) {
                write(TAG_GUEST_STRING);
                writeGuestString(guestRef);
            } else if (klass.isArray()) {
                write(TAG_GUEST_ARRAY);
                writeGuestArray(guestRef);
            } else if (espressoContext.getMeta().java_lang_Class.equals(klass)) {
                write(TAG_GUEST_CLASS);
                writeGuestClass(guestRef);
            } else if (espressoContext.getMeta().java_lang_ClassLoader.isAssignableFrom(klass)) {
                write(TAG_GUEST_CLASS_LOADER);
                writeGuestClassLoader(guestRef);
            } else {
                write(TAG_GUEST_OBJECT);
                writeGuestObject(guestRef);
            }
        } else {
            throw new UnsupportedOperationException("CDS cannot serialize: " + ref.getClass());
        }
    }

    void prependSize(Runnable writeAction) {
        int startPosition = position();
        writeInt(0xDEADBEEF);
        writeAction.run();
        int curPosition = position();
        try {
            position(startPosition);
            // Write the number of bytes to skip after reading the skipBytes int.
            writeInt(curPosition - startPosition - 4);
        } finally {
            position(curPosition);
        }
    }

    private void writeClassRegistryData(ArchivedRegistryData archivedRegistryData) {
        prependSize(() -> {
            ModuleTable moduleTable = archivedRegistryData.moduleTable();
            ArrayList<ModuleTable.ModuleEntry> moduleEntries = new ArrayList<>();
            moduleTable.collectValues(moduleEntries::add);
            writeReferenceOrObject(moduleEntries);

            PackageTable packageTable = archivedRegistryData.packageTable();
            ArrayList<PackageTable.PackageEntry> packageEntries = new ArrayList<>();
            packageTable.collectValues(packageEntries::add);
            writeReferenceOrObject(packageEntries);
            writeReferenceOrObject(archivedRegistryData.unnamedModule());
        });
    }

    private void writePackageEntry(PackageTable.PackageEntry packageEntry) {
        prependSize(() -> {
            writeReferenceOrObject(packageEntry.getName());
            writeReferenceOrObject(packageEntry.module());
            ArrayList<ModuleTable.ModuleEntry> exports = new ArrayList<>(packageEntry.getExportsForCDS());
            writeReferenceOrObject(exports);
            writeBoolean(packageEntry.isUnqualifiedExported());
            writeBoolean(packageEntry.isExportedAllUnnamed());
            writeReferenceOrObject(packageEntry.getBootClasspathLocation());
        });
    }

    private void writeModuleEntry(ModuleTable.ModuleEntry moduleEntry) {
        prependSize(() -> {
            writeReferenceOrObject(moduleEntry.getName());
            writeBoolean(moduleEntry.isOpen());
            writeReferenceOrObject(moduleEntry.module());
            writeReferenceOrObject(moduleEntry.version());
            writeReferenceOrObject(moduleEntry.location());
            writeBoolean(moduleEntry.canReadAllUnnamed());
            ArrayList<ModuleTable.ModuleEntry> reads = new ArrayList<>(moduleEntry.getReadsForCDS());
            writeReferenceOrObject(reads);
            writeBoolean(moduleEntry.hasDefaultReads());
        });
    }

    private void writeGuestClassLoader(@JavaType(ClassLoader.class) StaticObject guestClassLoader) {
        prependSize(() -> {
            writeGuestObject(guestClassLoader);
            // Do not eagerly initialize and register the registry, just read the hidden field.
            ClassRegistry classRegistry = (ClassRegistry) espressoContext.getMeta().HIDDEN_CLASS_LOADER_REGISTRY.getHiddenObject(guestClassLoader, true);
            ArchivedRegistryData data = null;
            if (classRegistry != null) {
                data = new ArchivedRegistryData(classRegistry.packages(), classRegistry.modules(), classRegistry.getUnnamedModule());
            }
            // can be null
            writeReferenceOrObject(data);
        });
    }

    private void writeGuestArray(StaticObject guestArray) {
        assert !StaticObject.isNull(guestArray);
        assert guestArray.getKlass().isArray();

        prependSize(() -> {

            ArrayKlass arrayClass = (ArrayKlass) guestArray.getKlass();
            writeReferenceOrObject(arrayClass.mirror());

            EspressoLanguage espressoLanguage = espressoContext.getLanguage();
            InterpreterToVM interpreterToVM = espressoContext.getInterpreterToVM();

            int length = guestArray.length(espressoLanguage);
            writeUnsignedVarInt(length);

            Klass componentType = arrayClass.getComponentType();
            for (int i = 0; i < length; ++i) {
                if (componentType.isPrimitive()) {
                    JavaKind fieldKind = TypeSymbols.getJavaKind(componentType.getType());
                    switch (fieldKind) {
                        case Boolean -> writeBoolean(interpreterToVM.getArrayByte(espressoLanguage, i, guestArray) != 0);
                        case Byte -> writeByte(interpreterToVM.getArrayByte(espressoLanguage, i, guestArray));
                        case Short -> writeShort(interpreterToVM.getArrayShort(espressoLanguage, i, guestArray));
                        case Char -> writeChar(interpreterToVM.getArrayChar(espressoLanguage, i, guestArray));
                        case Int -> writeInt(interpreterToVM.getArrayInt(espressoLanguage, i, guestArray));
                        case Float -> writeFloat(interpreterToVM.getArrayFloat(espressoLanguage, i, guestArray));
                        case Long -> writeLong(interpreterToVM.getArrayLong(espressoLanguage, i, guestArray));
                        case Double -> writeDouble(interpreterToVM.getArrayDouble(espressoLanguage, i, guestArray));
                        default -> throw EspressoError.shouldNotReachHere();
                    }
                } else {
                    writeReferenceOrObject(interpreterToVM.getArrayObject(espressoLanguage, i, guestArray));
                }
            }

        });
    }

    private void writeGuestObject(@JavaType(Object.class) StaticObject guestObject) {
        assert !StaticObject.isNull(guestObject);
        assert espressoContext.getMeta().java_lang_Class != guestObject.getKlass();
        assert espressoContext.getMeta().java_lang_String != guestObject.getKlass();
        assert !guestObject.getKlass().isArray();

        prependSize(() -> {

            Klass klass = guestObject.getKlass();
            EspressoError.guarantee(!klass.isHidden(), "Cannot serialize hidden classes in the CDS archive");
            EspressoError.guarantee(StaticObject.isNull(klass.getDefiningClassLoader()), "Class not defined by the boot class loader: " + klass.getTypeAsString());

            writeReferenceOrObject(klass.mirror());

            resetArchivedStates(guestObject);

            // Serialize all fields (including superclass fields)
            Klass current = klass;
            while (current != null && !current.isJavaLangObject()) {
                for (Field field : current.getDeclaredFields()) {
                    if (!field.isStatic()) {
                        Symbol<Type> fieldType = field.getType();
                        if (TypeSymbols.isPrimitive(fieldType)) {
                            JavaKind fieldKind = TypeSymbols.getJavaKind(fieldType);
                            switch (fieldKind) {
                                case Boolean -> writeBoolean(field.getBoolean(guestObject));
                                case Byte -> writeByte(field.getByte(guestObject));
                                case Short -> writeShort(field.getShort(guestObject));
                                case Char -> writeChar(field.getChar(guestObject));
                                case Int -> writeInt(field.getInt(guestObject));
                                case Float -> writeFloat(field.getFloat(guestObject));
                                case Long -> writeLong(field.getLong(guestObject));
                                case Double -> writeDouble(field.getDouble(guestObject));
                                default -> throw EspressoError.shouldNotReachHere();
                            }
                        } else {
                            StaticObject fieldValue = (StaticObject) field.get(guestObject);
                            writeReferenceOrObject(fieldValue);
                        }
                    }
                }
                current = current.getSuperClass();
            }
        });
    }

    private static void resetArchivedStates(StaticObject guestObject) {
        Klass current = guestObject.getKlass();
        while (current != null && !current.isJavaLangObject()) {
            Method resetArchivedStates = current.lookupDeclaredMethod(Names.resetArchivedStates, Signatures._void, LookupMode.INSTANCE_ONLY);
            if (resetArchivedStates != null) {
                // Always clear.
                resetArchivedStates.invokeDirectSpecial(guestObject);
            }
            current = current.getSuperClass();
        }
    }

    private void writeGuestClass(@JavaType(Class.class) StaticObject guestClass) {
        assert !StaticObject.isNull(guestClass);
        assert espressoContext.getMeta().java_lang_Class == guestClass.getKlass();
        Klass klass = guestClass.getMirrorKlass();
        EspressoError.guarantee(StaticObject.isNull(klass.getDefiningClassLoader()), "Class not defined by the boot class loader: " + klass.getTypeAsString());
        String typeName = klass.getTypeAsString();
        writeHostString(typeName);
    }

    private void writeGuestString(@JavaType(String.class) StaticObject guestString) {
        assert !StaticObject.isNull(guestString);
        assert espressoContext.getMeta().java_lang_String == guestString.getKlass();
        String hostString = Meta.toHostStringStatic(guestString);
        writeHostString(hostString);
    }

    private void writeHostSymbol(Symbol<?> symbol) {
        byte[] bytes = new byte[symbol.length()];
        symbol.writeTo(ByteBuffer.wrap(bytes));
        writeBytes(bytes);
    }

    private void writeBytes(byte[] bytes) {
        writeUnsignedVarInt(bytes.length);
        write(bytes);
    }

    private void writeHostString(String hostString) {
        byte[] bytes = hostString.getBytes(StandardCharsets.UTF_8);
        writeBytes(bytes);
    }

    private void writeHostArrayList(ArrayList<?> hostArrayList) {
        assert hostArrayList.getClass() == ArrayList.class;
        prependSize(() -> {
            writeUnsignedVarInt(hostArrayList.size());
            for (Object elem : hostArrayList) {
                writeReferenceOrObject(elem);
            }
        });
    }

    byte[] toByteArray() {
        int oldPosition = byteBuffer.position();
        try {
            byteBuffer.position(0);
            byte[] bytes = new byte[byteBuffer.limit()];
            byteBuffer.get(bytes);
            return bytes;
        } finally {
            byteBuffer.position(oldPosition);
        }
    }

    void writeUnsignedVarInt(int value) {
        LEB128.writeUnsignedInt(this::writeByte, value);
    }
}
