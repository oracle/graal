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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Types;
import com.oracle.truffle.espresso.impl.BootClassRegistry;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ModuleTable;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class CDSSupport {

    // Root key for the boot class registry data in the archive.
    private static final String BOOT_REGISTRY_DATA_KEY = "bootClassRegistryData";

    private final Path archivePath;
    private final boolean usingArchive;
    private final boolean dumpingStaticArchive;

    private final List<Klass> toDump = new ArrayList<>();

    private volatile Map<String, Integer> archiveRoot;
    private final Reader reader;

    private static final TruffleLogger LOGGER = TruffleLogger.getLogger(EspressoLanguage.ID, "CDS");

    public static TruffleLogger getLogger() {
        return LOGGER;
    }

    private static final List<String> ARCHIVE_SUBGRAPH_ENTRY_FIELDS = List.of(
                    "Ljava/lang/Integer$IntegerCache;/archivedCache",
                    "Ljava/lang/Long$LongCache;/archivedCache",
                    "Ljava/lang/Byte$ByteCache;/archivedCache",
                    "Ljava/lang/Short$ShortCache;/archivedCache",
                    "Ljava/lang/Character$CharacterCache;/archivedCache",
                    "Ljava/util/jar/Attributes$Name;/KNOWN_NAMES",
                    "Lsun/util/locale/BaseLocale;/constantBaseLocales",
                    "Ljdk/internal/module/ArchivedModuleGraph;/archivedModuleGraph",
                    "Ljava/util/ImmutableCollections;/archivedObjects",
                    "Ljava/lang/ModuleLayer;/EMPTY_LAYER",
                    "Ljava/lang/module/Configuration;/EMPTY_CONFIGURATION",
                    "Ljdk/internal/math/FDBigInteger;/archivedCaches");

    // Full module graph roots.
    private static final List<String> ARCHIVE_FULL_MODULE_GRAPH_FIELDS = List.of(
                    "Ljdk/internal/loader/ArchivedClassLoaders;/archivedClassLoaders",
                    "Ljdk/internal/module/ArchivedBootLayer;/archivedBootLayer",
                    "Ljava/lang/Module$ArchivedData;/archivedData");

    public CDSSupport(EspressoContext context, Path archivePath, boolean usingArchive, boolean dumpingStaticArchive) throws IOException {
        EspressoError.guarantee(usingArchive ^ dumpingStaticArchive, "Cannot use and dump the static CDS archive at the same time");
        this.archivePath = archivePath;
        this.usingArchive = usingArchive;
        this.dumpingStaticArchive = dumpingStaticArchive;
        if (isUsingArchive()) {
            // May throw IncompatibleArchiveException or IOException.
            this.reader = new Reader(context, archivePath);
        } else {
            this.reader = null;
        }
    }

    public boolean isUsingArchive() {
        return this.usingArchive;
    }

    public boolean isDumpingStaticArchive() {
        return this.dumpingStaticArchive;
    }

    public ArchivedRegistryData getBootClassRegistryData() {
        Map<String, Integer> root = getArchiveRoot();
        int refId = 0;
        if (root.containsKey(BOOT_REGISTRY_DATA_KEY)) {
            refId = root.get(BOOT_REGISTRY_DATA_KEY);
        }
        return (ArchivedRegistryData) this.reader.materializeReference(refId);
    }

    private Map<String, Integer> getArchiveRoot() {
        Map<String, Integer> ref = archiveRoot;
        if (ref == null) {
            synchronized (this) {
                ref = archiveRoot;
                if (ref == null) {

                    int bootRegistryDataRefId = this.reader.readLazyReferenceOrObject();

                    int length = reader.readUnsignedVarInt();
                    Map<String, Integer> keyValues = new HashMap<>();
                    for (int i = 0; i < length; ++i) {
                        String key = (String) reader.materializeReference(reader.readLazyReferenceOrObject());
                        int valuedRefId = reader.readLazyReferenceOrObject();
                        assert !keyValues.containsKey(key);
                        keyValues.put(key, valuedRefId);
                    }

                    keyValues.put(BOOT_REGISTRY_DATA_KEY, bootRegistryDataRefId);

                    archiveRoot = ref = keyValues;
                }
            }
        }
        return ref;
    }

    public void hydrateFromCache(ModuleTable.ModuleEntry moduleEntry) {
        assert isUsingArchive();
        if (moduleEntry.module() != null) {
            assert moduleEntry.getName() == null;
            return; // already loaded
        }

        int archivedModuleRefId = moduleEntry.getArchivedModuleRefId();

        if (archivedModuleRefId == 0) {
            return;
        }

        CDSSupport.getLogger().fine(() -> "Hydrate " + moduleEntry.getNameAsString());
        StaticObject module = (StaticObject) this.reader.materializeReference(archivedModuleRefId);
        assert Types.java_lang_Module == module.getKlass().getType();
        moduleEntry.setModule(module);
    }

    public void initializeFromArchive(Klass klass) {
        getLogger().fine(() -> "initializeFromArchive: " + klass.getType());
        if (isDumpingStaticArchive()) {
            toDump.add(klass);
        }
        if (isUsingArchive()) {
            Map<String, Integer> root = getArchiveRoot();

            for (Field field : klass.getDeclaredFields()) {
                if (!field.isStatic()) {
                    continue;
                }
                Integer valueRefId = root.get(stringifyField(field));
                if (valueRefId != null) {
                    StaticObject fieldValue = (StaticObject) this.reader.materializeReference(valueRefId);
                    if (StaticObject.isNull(fieldValue)) {
                        throw EspressoError.shouldNotReachHere("Malformed CDS archive for " + field);
                    }
                    field.setObject(klass.getStatics(), fieldValue);
                }
            }
        }
    }

    private static String stringifyField(Field field) {
        return field.getDeclaringKlass().getTypeAsString() + "/" + field.getNameAsString();
    }

    private static boolean canDumpFullModuleGraph(Meta meta) {
        // Only dump full module graph (including class loaders) iff
        // ArchivedBootLayer.archivedBootLayer != null.
        ObjectKlass klass = meta.knownKlass(Types.jdk_internal_module_ArchivedBootLayer);
        Field field = klass.lookupDeclaredField(Names.archivedBootLayer, Types.jdk_internal_module_ArchivedBootLayer);
        assert field.isStatic() && TypeSymbols.isReference(field.getType());
        StaticObject fieldValue = (StaticObject) field.get(klass.getStatics());
        return !StaticObject.isNull(fieldValue);
    }

    public void dump(Meta meta) {
        getLogger().fine(() -> "Dumping static CDS archive to: " + archivePath);

        // In HotSpot, the full module graph cannot be serialized partially.
        boolean dumpFullModuleGraph = canDumpFullModuleGraph(meta);
        getLogger().fine(() -> "dumpFullModuleGraph=" + dumpFullModuleGraph);

        Map<String, Object> keyValues = new HashMap<>();
        for (Klass klass : toDump) {
            for (Field field : klass.getDeclaredFields()) {
                if (field.isStatic() && TypeSymbols.isReference(field.getType())) {
                    String fieldKey = stringifyField(field);
                    if (ARCHIVE_SUBGRAPH_ENTRY_FIELDS.contains(fieldKey) || (dumpFullModuleGraph && ARCHIVE_FULL_MODULE_GRAPH_FIELDS.contains(fieldKey))) {
                        StaticObject fieldValue = field.getObject(klass.getStatics());
                        if (StaticObject.isNull(fieldValue)) {
                            continue; // nothing to archive
                        }
                        getLogger().fine(() -> "Archiving " + fieldKey);
                        keyValues.put(fieldKey, fieldValue);
                    }
                }
            }
        }

        ArchivedRegistryData bootClassRegistryData = null;
        if (dumpFullModuleGraph) {
            BootClassRegistry bootClassRegistry = meta.getRegistries().getBootClassRegistry();
            bootClassRegistryData = new ArchivedRegistryData(bootClassRegistry.packages(), bootClassRegistry.modules(), bootClassRegistry.getUnnamedModule());
        }

        Writer writer = new Writer(meta.getContext());
        writer.writeHeader();
        writer.writeReferenceOrObject(bootClassRegistryData);
        writer.writeUnsignedVarInt(keyValues.size());
        for (Map.Entry<String, Object> entry : keyValues.entrySet()) {
            writer.writeReferenceOrObject(entry.getKey());
            writer.writeReferenceOrObject(entry.getValue());
        }

        try {
            byte[] archivedBytes = writer.toByteArray();
            Files.write(archivePath, archivedBytes,
                            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            getLogger().fine(() -> "Serialized " + writer.getWrittenObjectCount() + " objects using " + archivedBytes.length + " bytes to " + archivePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int getCDSConfigStatus(EspressoContext context) {
        Meta meta = context.getMeta();
        Klass jdkInternalMiscCDS = meta.knownKlass(Types.jdk_internal_misc_CDS);
        // Ensure static constants are initialized.
        jdkInternalMiscCDS.safeInitialize();

        int configStatus = 0; // nothing

        if (usingArchive) {
            Field isUsingArchive = jdkInternalMiscCDS.requireDeclaredField(Names.IS_USING_ARCHIVE, Types._int);
            int isUsingArchiveFlag = isUsingArchive.getInt(jdkInternalMiscCDS.getStatics());
            assert isUsingArchiveFlag != 0;
            configStatus |= isUsingArchiveFlag;
        }
        if (dumpingStaticArchive) {
            Field isDumpingStaticArchive = jdkInternalMiscCDS.requireDeclaredField(Names.IS_DUMPING_STATIC_ARCHIVE, Types._int);
            int isDumpingStaticArchiveFlag = isDumpingStaticArchive.getInt(jdkInternalMiscCDS.getStatics());
            assert isDumpingStaticArchiveFlag != 0;
            configStatus |= isDumpingStaticArchiveFlag;
        }

        return configStatus;
    }
}
