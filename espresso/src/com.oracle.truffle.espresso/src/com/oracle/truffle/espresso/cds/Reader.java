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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.graalvm.home.Version;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.classfile.tables.AbstractModuleTable;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ModuleTable;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.PackageTable;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.OS;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

final class Reader implements CDSArchiveFormat {

    private final Map<Integer, Object> materializedRefs = HashMap.newHashMap(1 << 14);
    private final EspressoContext espressoContext;
    private final ByteBuffer byteBuffer;

    Reader(EspressoContext espressoContext, Path archivePath) throws IOException {
        this.espressoContext = espressoContext;
        long archiveSize = Files.size(archivePath);
        try (FileChannel fileChannel = FileChannel.open(archivePath)) {
            this.byteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, archiveSize) //
                            .asReadOnlyBuffer() //
                            .order(ByteOrder.nativeOrder());
        }
        readHeader();
    }

    void readHeader() {
        EspressoError.guarantee(position() == 0, "reading CDS archive header at position != 0");

        // Magic header is always big endian.
        ByteOrder oldOrder = byteBuffer.order();
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        int magic = byteBuffer.getInt();
        byteBuffer.order(oldOrder);

        if (magic != MAGIC) {
            throw new IncompatibleCDSArchiveException("Invalid CDS archive header");
        }

        int major = byteBuffer.getInt();
        int minor = byteBuffer.getInt();
        if (major != CDSArchiveFormat.MAJOR_VERSION || (minor > CDSArchiveFormat.MINOR_VERSION)) {
            throw new IncompatibleCDSArchiveException("Incompatible major.minor version of CDS archive");
        }

        String hostVersion = readHostString(0);
        String javaVersion = readHostString(0);
        String javaRuntimeVersion = readHostString(0);
        String os = readHostString(0);
        String arch = readHostString(0);

        CDSSupport.getLogger().fine("Host Version: " + hostVersion);
        CDSSupport.getLogger().fine("Java Version: " + javaVersion);
        CDSSupport.getLogger().fine("Java Runtime Version: " + javaRuntimeVersion);
        CDSSupport.getLogger().fine("OS: " + os);
        CDSSupport.getLogger().fine("Architecture: " + arch);

        String expectedHostVersion = Version.getCurrent().toString();
        if (!Objects.equals(hostVersion, expectedHostVersion)) {
            throw new IncompatibleCDSArchiveException("Incompatible host version of CDS archive, expected " + expectedHostVersion + " but got " + hostVersion);
        }

        String expectedJavaVersion = "" + espressoContext.getJavaVersion().classFileVersion();
        if (!Objects.equals(javaVersion, expectedJavaVersion)) {
            throw new IncompatibleCDSArchiveException("Incompatible Java version of CDS archive, expected " + expectedJavaVersion + " but got " + javaVersion);
        }

        String expectedJavaRuntimeVersion = espressoContext.readKeyFromReleaseFile(espressoContext.getVmProperties().javaHome(), "JAVA_RUNTIME_VERSION");
        if (!Objects.equals(javaRuntimeVersion, expectedJavaRuntimeVersion)) {
            throw new IncompatibleCDSArchiveException("Incompatible JAVA_RUNTIME_VERSION of CDS archive, expected " + expectedJavaRuntimeVersion + " but got " + javaRuntimeVersion);
        }

        String expectedOS = OS.getCurrent().name();
        if (!Objects.equals(os, expectedOS)) {
            throw new IncompatibleCDSArchiveException("Incompatible OS of CDS archive, expected " + expectedOS + " but got " + os);
        }

        String expectedArch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if (!Objects.equals(arch, expectedArch)) {
            throw new IncompatibleCDSArchiveException("Incompatible architecture of CDS archive, expected " + expectedArch + " but got " + arch);
        }
    }

    synchronized Object materializeReference(int refId) {
        if (refId == 0) {
            return null;
        }
        if (refId == 1) {
            return StaticObject.NULL;
        }
        if (materializedRefs.containsKey(refId)) {
            return materializedRefs.get(refId);
        }
        int oldPosition = position();
        try {
            position(refId);
            Object ref = readReferenceOrObject();
            assert ref == null || (ref instanceof StaticObject guestObject && StaticObject.isNull(guestObject)) || materializedRefs.containsKey(refId);
            return ref;
        } finally {
            position(oldPosition);
        }
    }

    private Object readReferenceOrObject() {
        int refId = position();

        if (materializedRefs.containsKey(refId)) {
            readLazyReferenceOrObject(); // skip it
            return materializedRefs.get(refId);
        }

        byte tag = byteBuffer.get();
        switch (tag) {
            case TAG_NULL:
                return null;
            case TAG_REFERENCE: {
                int targetRefId = readUnsignedVarInt();
                Object targetRef = materializeReference(targetRefId);
                materializedRefs.put(refId, targetRef);
                return targetRef;
            }
            case TAG_STRING:
                return readHostString(refId);
            case TAG_SYMBOL:
                return readSymbol(refId);
            case TAG_ARRAY_LIST:
                return readHostArrayList(refId);
            case TAG_CLASS_REGISTRY_DATA:
                return readHostClassRegistryData(refId);
            case TAG_MODULE_ENTRY:
                return readHostModuleEntry(refId);
            case TAG_PACKAGE_ENTRY:
                return readHostPackageEntry(refId);

            case TAG_GUEST_NULL:
                return StaticObject.NULL;
            case TAG_GUEST_STRING:
                return readGuestString(refId);
            case TAG_GUEST_ARRAY:
                return readGuestArray(refId);
            case TAG_GUEST_CLASS:
                return readGuestClass(refId);
            case TAG_GUEST_OBJECT:
                return readGuestObject(refId);
            case TAG_GUEST_CLASS_LOADER:
                return readGuestClassLoader(refId);

            default:
                throw new UnsupportedOperationException();
        }
    }

    private void skipGuestClassLoader() {
        int skipSize = byteBuffer.getInt();
        byteBuffer.position(byteBuffer.position() + skipSize);
    }

    private @JavaType(ClassLoader.class) StaticObject readGuestClassLoader(int refId) {
        byteBuffer.getInt(); // skipSize
        StaticObject classLoader = readGuestObject(refId);
        assert refId == 0 || materializedRefs.containsKey(refId);
        ArchivedRegistryData archivedRegistryData = (ArchivedRegistryData) readReferenceOrObject();
        if (archivedRegistryData != null) {
            espressoContext.getRegistries().getOrInitializeClassRegistry(classLoader, archivedRegistryData);
        }
        return classLoader;
    }

    int readLazyReferenceOrObject() {
        int refId = position();

        byte tag = byteBuffer.get();
        switch (tag) {
            case TAG_NULL:
                return 0;
            case TAG_REFERENCE:
                return readUnsignedVarInt();
            case TAG_STRING:
                skipHostString();
                return refId;
            case TAG_SYMBOL:
                skipSymbol();
                return refId;
            case TAG_ARRAY_LIST:
                skipHostArrayList();
                return refId;
            case TAG_CLASS_REGISTRY_DATA:
                skipHostClassRegistryData();
                return refId;
            case TAG_MODULE_ENTRY:
                skipHostModuleEntry();
                return refId;
            case TAG_PACKAGE_ENTRY:
                skipHostPackageEntry();
                return refId;

            case TAG_GUEST_NULL:
                return 1;
            case TAG_GUEST_STRING:
                skipGuestString();
                return refId;
            case TAG_GUEST_ARRAY:
                skipGuestArray();
                return refId;
            case TAG_GUEST_CLASS:
                skipGuestClass();
                return refId;
            case TAG_GUEST_OBJECT:
                skipGuestObject();
                return refId;
            case TAG_GUEST_CLASS_LOADER:
                skipGuestClassLoader();
                return refId;

            default:
                throw new UnsupportedOperationException();
        }
    }

    private void skipHostArrayList() {
        int skipSize = byteBuffer.getInt();
        byteBuffer.position(byteBuffer.position() + skipSize);
    }

    private ArrayList<?> readHostArrayList(int refId) {
        byteBuffer.getInt(); // skipSize
        int length = readUnsignedVarInt();
        ArrayList<Object> arrayList = new ArrayList<>(length);
        if (refId > 0) {
            materializedRefs.put(refId, arrayList);
        }
        for (int i = 0; i < length; i++) {
            arrayList.add(readReferenceOrObject());
        }
        return arrayList;
    }

    private void skipHostString() {
        skipBytes();
    }

    private String readHostString(int refId) {
        byte[] bytes = readBytes();
        String string = new String(bytes, StandardCharsets.UTF_8);
        if (refId > 0) {
            assert !materializedRefs.containsKey(refId);
            materializedRefs.put(refId, string);
        }
        return string;
    }

    private void skipSymbol() {
        skipBytes();
    }

    private Symbol<?> readSymbol(int refId) {
        byte[] bytes = readBytes();
        Symbol<?> symbol = espressoContext.getLanguage().getUtf8Symbols().getOrCreateValidUtf8(ByteSequence.wrap(bytes));
        if (refId > 0) {
            assert !materializedRefs.containsKey(refId);
            materializedRefs.put(refId, symbol);
        }
        return symbol;
    }

    private void skipGuestArray() {
        int skipSize = byteBuffer.getInt();
        byteBuffer.position(byteBuffer.position() + skipSize);
    }

    private StaticObject readGuestArray(int refId) {
        int startPos = position();
        int skipSize = byteBuffer.getInt();
        StaticObject arrayClass = (StaticObject) readReferenceOrObject();
        InterpreterToVM interpreterToVM = espressoContext.getInterpreterToVM();
        EspressoLanguage espressoLanguage = espressoContext.getLanguage();
        Klass componentType = ((ArrayKlass) arrayClass.getMirrorKlass()).getComponentType();
        int length = readUnsignedVarInt();

        StaticObject arrayInstance = componentType.isPrimitive()
                        ? espressoContext.getAllocator().createNewPrimitiveArray(componentType, length)
                        : espressoContext.getAllocator().createNewReferenceArray(componentType, length);

        if (refId > 0) {
            materializedRefs.put(refId, arrayInstance);
        }

        for (int i = 0; i < length; ++i) {
            if (componentType.isPrimitive()) {
                JavaKind fieldKind = TypeSymbols.getJavaKind(componentType.getType());
                switch (fieldKind) {
                    case Boolean -> interpreterToVM.setArrayByte(espressoLanguage, readBoolean() ? (byte) 1 : (byte) 0, i, arrayInstance);
                    case Byte -> interpreterToVM.setArrayByte(espressoLanguage, byteBuffer.get(), i, arrayInstance);
                    case Short -> interpreterToVM.setArrayShort(espressoLanguage, byteBuffer.getShort(), i, arrayInstance);
                    case Char -> interpreterToVM.setArrayChar(espressoLanguage, byteBuffer.getChar(), i, arrayInstance);
                    case Int -> interpreterToVM.setArrayInt(espressoLanguage, byteBuffer.getInt(), i, arrayInstance);
                    case Float -> interpreterToVM.setArrayFloat(espressoLanguage, byteBuffer.getFloat(), i, arrayInstance);
                    case Long -> interpreterToVM.setArrayLong(espressoLanguage, byteBuffer.getLong(), i, arrayInstance);
                    case Double -> interpreterToVM.setArrayDouble(espressoLanguage, byteBuffer.getDouble(), i, arrayInstance);
                    default -> throw EspressoError.shouldNotReachHere();
                }
            } else {
                StaticObject value = (StaticObject) readReferenceOrObject();
                interpreterToVM.setArrayObject(espressoLanguage, value, i, arrayInstance);
            }
        }
        assert position() == startPos + 4 + skipSize;
        return arrayInstance;
    }

    private void skipGuestObject() {
        int skipSize = byteBuffer.getInt();
        byteBuffer.position(byteBuffer.position() + skipSize);
    }

    private StaticObject readGuestObject(int refId) {
        int startPos = position();
        int skipSize = byteBuffer.getInt();
        StaticObject klass = (StaticObject) readReferenceOrObject();
        int curPos = position();
        StaticObject instance = null;
        try {
            // May trigger a static initializer that reads from the archive, breaking the current
            // position.
            instance = createInstance((ObjectKlass) klass.getMirrorKlass());
        } finally {
            position(curPos);
        }

        // Store immediately to handle circular references
        if (refId > 0) {
            if (materializedRefs.containsKey(refId)) {
                // The reference was already de-serialized (triggered by the static initializer).
                // Go back and skip the read, return the cached instance.
                instance = (StaticObject) materializedRefs.get(refId);
            } else {
                materializedRefs.put(refId, instance);
            }
        }

        // Read all fields
        Klass current = klass.getMirrorKlass();
        while (current != null && !current.isJavaLangObject()) {
            for (Field field : current.getDeclaredFields()) {
                if (!field.isStatic()) {
                    Symbol<Type> fieldType = field.getType();
                    if (TypeSymbols.isPrimitive(fieldType)) {
                        JavaKind fieldKind = TypeSymbols.getJavaKind(fieldType);
                        switch (fieldKind) {
                            case Boolean -> field.setBoolean(instance, readBoolean());
                            case Byte -> field.setByte(instance, byteBuffer.get());
                            case Short -> field.setShort(instance, byteBuffer.getShort());
                            case Char -> field.setChar(instance, byteBuffer.getChar());
                            case Int -> field.setInt(instance, byteBuffer.getInt());
                            case Float -> field.setFloat(instance, byteBuffer.getFloat());
                            case Long -> field.setLong(instance, byteBuffer.getLong());
                            case Double -> field.setDouble(instance, byteBuffer.getDouble());
                            default -> throw EspressoError.shouldNotReachHere();
                        }
                    } else {
                        StaticObject fieldValue = (StaticObject) readReferenceOrObject();
                        field.set(instance, fieldValue);
                    }
                }
            }
            current = current.getSuperClass();
        }
        assert position() == startPos + 4 + skipSize;
        return instance;
    }

    private void skipGuestString() {
        skipHostString();
    }

    private @JavaType(String.class) StaticObject readGuestString(int refId) {
        String hostString = readHostString(0);
        StaticObject guestString = espressoContext.getMeta().toGuestString(hostString);
        if (refId > 0) {
            assert !materializedRefs.containsKey(refId);
            materializedRefs.put(refId, guestString);
        }
        return guestString;
    }

    private byte[] readBytes() {
        int length = readUnsignedVarInt();
        byte[] bytes = new byte[length];
        byteBuffer.get(bytes);
        return bytes;
    }

    private void skipBytes() {
        int length = readUnsignedVarInt();
        byteBuffer.position(byteBuffer.position() + length);
    }

    private void skipGuestClass() {
        skipHostString();
    }

    private @JavaType(Class.class) StaticObject readGuestClass(int refId) {
        String typeName = readHostString(0);

        Symbol<Type> typeSymbol = espressoContext.getTypes().getOrCreateValidType(typeName);

        Meta meta = espressoContext.getMeta();
        Klass klass;

        Symbol<Type> elementalTypeSymbol = espressoContext.getTypes().getElementalType(typeSymbol);
        if (TypeSymbols.isArray(typeSymbol) && TypeSymbols.isPrimitive(elementalTypeSymbol)) {
            Klass elemental = meta.resolvePrimitive(elementalTypeSymbol);
            klass = elemental.getArrayKlass(TypeSymbols.getArrayDimensions(typeSymbol));
        } else {
            klass = meta.loadKlassOrNull(typeSymbol, StaticObject.NULL, StaticObject.NULL);
        }
        StaticObject result = klass.mirror();
        if (refId > 0) {
            assert !materializedRefs.containsKey(refId);
            materializedRefs.put(refId, result);
        }
        return result;
    }

    private StaticObject createInstance(ObjectKlass clazz) {
        return espressoContext.getAllocator().createNew(clazz);
    }

    private void position(int newPosition) {
        byteBuffer.position(newPosition);
    }

    private int position() {
        return byteBuffer.position();
    }

    int readUnsignedVarInt() {
        return LEB128.readUnsignedInt(byteBuffer::get);
    }

    private void skipHostClassRegistryData() {
        int skipSize = byteBuffer.getInt();
        byteBuffer.position(byteBuffer.position() + skipSize);
    }

    @SuppressWarnings("unchecked")
    private ArchivedRegistryData readHostClassRegistryData(int refId) {
        byteBuffer.getInt(); // skipSize
        ArchivedRegistryData result = new ArchivedRegistryData(null, null, null);
        if (refId > 0) {
            materializedRefs.put(refId, result);
        }
        ReentrantReadWriteLock commonLock = new ReentrantReadWriteLock();
        ModuleTable moduleTable = new ModuleTable(commonLock);

        ArrayList<ModuleTable.ModuleEntry> moduleEntries = (ArrayList<ModuleTable.ModuleEntry>) readReferenceOrObject();
        if (moduleEntries != null) {
            moduleTable.addModuleEntriesForCDS(moduleEntries);
        }

        PackageTable packageTable = new PackageTable(commonLock);
        ArrayList<PackageTable.PackageEntry> packageEntries = (ArrayList<PackageTable.PackageEntry>) readReferenceOrObject();
        if (packageEntries != null) {
            packageTable.addPackageEntriesForCDS(packageEntries);
        }

        ModuleTable.ModuleEntry unnamedModule = (ModuleTable.ModuleEntry) readReferenceOrObject();

        result.moduleTable = moduleTable;
        result.packageTable = packageTable;
        result.unnamedModule = unnamedModule;

        return result;
    }

    private void skipHostPackageEntry() {
        int skipSize = byteBuffer.getInt();
        byteBuffer.position(byteBuffer.position() + skipSize);
    }

    @SuppressWarnings("unchecked")
    private PackageTable.PackageEntry readHostPackageEntry(int refId) {
        byteBuffer.getInt(); // skipSize
        Symbol<Name> name = (Symbol<Name>) readReferenceOrObject();
        assert name != null;

        // ModuleEntry is de-serialized with a archiveRefId, instead of a guest Module reference.
        // These are patched/inflated/de-serialized in CDS.defineArchivedModules.
        ModuleTable.ModuleEntry moduleEntry = (ModuleTable.ModuleEntry) readReferenceOrObject();
        PackageTable.PackageEntry packageEntry = new PackageTable.PackageEntry(name, moduleEntry);

        if (refId > 0) {
            materializedRefs.put(refId, packageEntry);
        }

        ArrayList<ModuleTable.ModuleEntry> exports = (ArrayList<ModuleTable.ModuleEntry>) readReferenceOrObject();
        for (ModuleTable.ModuleEntry export : exports) {
            packageEntry.addExports(export);
        }

        boolean isUnqualifiedExported = readBoolean();
        boolean isExportedAllUnnamed = readBoolean();
        String bootClasspathLocation = (String) readReferenceOrObject();
        if (isUnqualifiedExported) {
            packageEntry.setUnqualifiedExports();
        }
        if (isExportedAllUnnamed) {
            packageEntry.setExportedAllUnnamed();
        }
        packageEntry.setBootClasspathLocation(bootClasspathLocation);

        return packageEntry;
    }

    private void skipHostModuleEntry() {
        int skipSize = byteBuffer.getInt();
        byteBuffer.position(byteBuffer.position() + skipSize);
    }

    @SuppressWarnings("unchecked")
    private ModuleTable.ModuleEntry readHostModuleEntry(int refId) {
        byteBuffer.getInt(); // skipSize
        Symbol<Name> name = (Symbol<Name>) readReferenceOrObject();
        boolean isOpen = readBoolean();

        // Avoid instantiating Module instances too early, this is inflated later in
        // CDS.defineArchivedModules.
        int archivedModuleRefId = readLazyReferenceOrObject();

        String version = (String) readReferenceOrObject();
        String location = (String) readReferenceOrObject();
        boolean canReadAllUnnamed = readBoolean();

        ModuleTable.ModuleEntry moduleEntry = new ModuleTable.ModuleEntry(name, new AbstractModuleTable.ModuleData<>(version, location, null, archivedModuleRefId, isOpen));
        if (refId > 0) {
            materializedRefs.put(refId, moduleEntry);
        }

        if (canReadAllUnnamed) {
            moduleEntry.setCanReadAllUnnamed();
        }

        ArrayList<ModuleTable.ModuleEntry> reads = (ArrayList<ModuleTable.ModuleEntry>) readReferenceOrObject();
        for (ModuleTable.ModuleEntry read : reads) {
            moduleEntry.addReads(read);
        }
        boolean hasDefaultReads = readBoolean();
        if (hasDefaultReads) {
            moduleEntry.setHasDefaultReads();
        }

        return moduleEntry;
    }

    private boolean readBoolean() {
        return (byteBuffer.get() != 0);
    }
}
