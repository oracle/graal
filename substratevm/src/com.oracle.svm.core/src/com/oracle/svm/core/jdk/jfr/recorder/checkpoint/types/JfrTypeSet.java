/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr.recorder.checkpoint.types;

import java.io.IOException;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.jdk.jfr.JfrRuntimeAccess;
import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.JfrCheckpointWriter;
import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.types.traceid.JfrTraceId;
import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.types.traceid.JfrTraceIdLoadBarrier;
import com.oracle.svm.core.jdk.jfr.utilities.JfrTypes;

public class JfrTypeSet {

    private static JfrCheckpointWriter checkpointWriter;
    private static JfrCheckpointWriter leakWriter;

    // JFR.TODO
    // Will we ever need to handle class unloading?
    private static boolean classUnload;
    private static boolean flushpoint;

    private static JfrArtifactSet artifactSet;
    private static boolean clearArtifacts;

    private static long checkpointId = 1;

    /**
     * Write all "tagged" (in-use) constant artifacts and their dependencies.
     */
    public static int serialize(JfrCheckpointWriter writer, JfrCheckpointWriter leakWriter, boolean classUnload,
            boolean flushpoint) {
        setup(writer, leakWriter, classUnload, flushpoint);

        if (!writeKlasses()) {
            return 0;
        }

        writePackages();
        writeModules();
        writeClassloaders();
        // JFR.TODO
        // writeMethods();
        writeSymbols();
        return teardown();
    }

    private static int teardown() {
        int totalCount = artifactSet.getTotalCount();
        if (previousEpoch()) {
            clearArtifacts();
            clearArtifacts = true;
            ++checkpointId;
        }

        return totalCount;
    }

    private static void writeSymbols() {
        assert (checkpointWriter != null);

        JfrTypeWriter symbolWriter = new JfrTypeWriter(JfrTypes.JfrTypeId.TYPE_SYMBOL.id, checkpointWriter);

        if (leakWriter == null) {
            try {
                symbolWriter.begin();
                BiConsumer<String, Long> sc = (s, l) -> symbolWriter.incrementCount(writeSymbol(checkpointWriter, s, l));
                artifactSet.getSymbolMap().forEach(sc);

                symbolWriter.end();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            throw new RuntimeException("Leak Profiler not yet implemented");
        }
        artifactSet.tally(symbolWriter.count());
    }

    private static int writeSymbol(JfrCheckpointWriter writer, String s, Long id) {
        try {
            writer.encoded().writeLong(getSymbolId(id));
            byte UTF8 = 3;
            writer.encoded().writeByte(UTF8);
            writer.encoded().writeInt(s.length());
            for (byte b : s.getBytes()) {
                writer.encoded().writeByte(b);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 1;
    }

    private static void writeMethods() {
        // JFR.TODO
        // Write out methods
    }

    private static void writeClassloaders() {
        assert (checkpointWriter != null);

        JfrTypeWriter classloaderWriter = new JfrTypeWriter(JfrTypes.JfrTypeId.TYPE_CLASSLOADER.id, checkpointWriter);
        try {
            classloaderWriter.begin();
            Consumer<Class<?>> km = c -> {
                ClassLoader cld = c.getClassLoader();
                if (cld != null && !JfrTraceId.isSerialized(cld)) {
                    classloaderWriter.incrementCount(writeClassloader(checkpointWriter, cld));
                }
            };
            if (currentEpoch()) {
                artifactSet.iterateKlasses(km);
                for (ClassLoader cl : getReachableClassloaders()) {
                    if (!JfrTraceId.isSerialized(cl)) {
                        classloaderWriter.incrementCount(writeClassloader(checkpointWriter, cl));
                    }
                }
                artifactSet.tally(classloaderWriter.count());
                classloaderWriter.end();
                return;
            }
            if (leakWriter == null) {
                // JFR.TODO
                artifactSet.iterateKlasses(km);
                for (ClassLoader cl : getReachableClassloaders()) {
                    if (!JfrTraceId.isSerialized(cl)) {
                        classloaderWriter.incrementCount(writeClassloader(checkpointWriter, cl));
                    }
                }
                // This iterates the CLDG and writes then clears the classloaders
            } else {
                throw new RuntimeException("Leak Profiler not yet implemented");
            }

            artifactSet.tally(classloaderWriter.count());
            classloaderWriter.end();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static int writeClassloader(JfrCheckpointWriter writer, ClassLoader cld) {
        assert (cld != null);
        JfrTraceId.setSerialized(cld);
        return writeClassloader(writer, cld, false);
    }

    private static int writeClassloader(JfrCheckpointWriter writer, ClassLoader cld, boolean leak) {
        assert (cld != null);
        assert (artifactSet != null);
        try {
            writer.encoded().writeLong(cldId(cld, leak));
            long cid = cld.getClass() != null ? JfrTraceId.getTraceId(cld.getClass()) : 0;
            writer.encoded().writeLong(cid);
            String name = cld.getName() == null ? "bootstrap" : cld.getName();
            writer.encoded().writeLong(markSymbol(name, leak));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 1;
    }

    private static void writeModules() {
        assert (checkpointWriter != null);

        JfrTypeWriter moduleWriter = new JfrTypeWriter(JfrTypes.JfrTypeId.TYPE_MODULE.id, checkpointWriter);
        try {
            moduleWriter.begin();
            Consumer<Class<?>> km = c -> {
                Module m = c.getModule();
                if (m != null && !JfrTraceId.isSerialized(m)) {
                    moduleWriter.incrementCount(writeModule(checkpointWriter, m));
                }
            };
            if (currentEpoch()) {
                artifactSet.iterateKlasses(km);
                artifactSet.tally(moduleWriter.count());
                moduleWriter.end();
                return;
            }
            if (leakWriter == null) {
                artifactSet.iterateKlasses(km);
                // JFR.TODO
                // This iterates the CLDG and writes then clears their modules
            } else {
                throw new RuntimeException("Leak profiler paths are not yet implemented");
            }

            artifactSet.tally(moduleWriter.count());
            moduleWriter.end();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int writeModule(JfrCheckpointWriter writer, Module m) {
        assert (m != null);
        JfrTraceId.setSerialized(m);
        return writeModule(writer, m, false);
    }

    private static int writeModule(JfrCheckpointWriter writer, Module m, boolean leak) {
        assert (m != null);
        assert (artifactSet != null);
        try {
            writer.encoded().writeLong(moduleId(m, leak));
            writer.encoded().writeLong(markSymbol(m.getName(), leak));
            String version;
            if (m.getDescriptor() == null) {
                version = "";
            } else {
                version = m.getDescriptor().version().isPresent() ? m.getDescriptor().version().get().toString() : "";
            }
            if (version.equals("")) {
                writer.encoded().writeLong(0);
            } else {
                writer.encoded().writeLong(markSymbol(version, leak));
            }
            // writer->write(mark_symbol(mod->location(), leakp)
            writer.encoded().writeLong(0);
            writer.encoded().writeLong(m.getClassLoader() != null ? cldId(m.getClassLoader(), leak) : 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 1;
    }

    private static long cldId(ClassLoader classLoader, boolean leak) {
        assert (classLoader != null);
        if (leak) {
            // JFR.TODO.LEAK
        } else {
            JfrTraceId.setTransient(classLoader);
        }
        return JfrTraceId.getTraceId(classLoader);
    }

    private static long moduleId(Module m, boolean leak) {
        assert (m != null);
        if (leak) {
            // JFR.TODO.LEAK
        } else {
            JfrTraceId.setTransient(m);
        }
        return JfrTraceId.getTraceId(m);
    }

    private static void writePackages() {
        assert (checkpointWriter != null);

        JfrTypeWriter packageWriter = new JfrTypeWriter(JfrTypes.JfrTypeId.TYPE_PACKAGE.id, checkpointWriter);
        try {
            packageWriter.begin();

            Consumer<Class<?>> kp = c -> {
                Package p = c.getPackage();
                if (p != null && p.hashCode() != 0 && !JfrTraceId.isSerialized(p)) {
                    packageWriter.incrementCount(writePackage(checkpointWriter, c));
                }
            };

            if (currentEpoch()) {
                artifactSet.iterateKlasses(kp);
                artifactSet.tally(packageWriter.count());
                packageWriter.end();
                return;
            }

            if (leakWriter == null) {
                artifactSet.iterateKlasses(kp);
                // JFR.TODO
                // This iterates the CLDG and writes then clears their packages

            } else {
                throw new RuntimeException("Leak profiler paths are not yet implemented");
            }

            artifactSet.tally(packageWriter.count());

            packageWriter.end();
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert (previousEpoch());
    }

    private static int writePackage(JfrCheckpointWriter writer, Class<?> c) {
        JfrTraceId.setSerialized(c.getPackage());
        return writePackage(writer, c, false);
    }

    private static int writePackage(JfrCheckpointWriter writer, Class<?> c, boolean leak) {
        assert (writer != null);
        assert (artifactSet != null);

        Package p = c.getPackage();

        assert (p != null);

        try {
            long traceid = JfrTraceId.getTraceId(p);
            assert (traceid != -1);
            writer.encoded().writeLong(JfrTraceId.getTraceId(p));
            writer.encoded().writeLong(markSymbol(p.getName(), leak));
            writer.encoded().writeLong(moduleId(c, leak));
            // JFR.TODO
            // writer->write((bool)pkg->is_exported());
            writer.encoded().writeInt(0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return 1;
    }

    private static long moduleId(Class<?> c, boolean leak) {
        assert (c != null);
        Module m = c.getModule();
        if (!m.isNamed()) {
            return 0;
        }

        if (leak) {
            // JFR.TODO.LEAK
        } else {
            JfrTraceId.setTransient(m);
        }

        return JfrTraceId.getTraceId(m);
    }

    private static long packageId(Class<?> c) {
        Package p = c.getPackage();
        if (p.hashCode() == 0) {
            return 0;
        }
        if (JfrTraceId.getTraceId(p) != -1) {
            return JfrTraceId.getTraceId(p);
        }

        return JfrTraceId.assign(p);
    }

    private static boolean writeKlasses() {
        assert (!artifactSet.hasKlassEntries());
        assert (checkpointWriter != null);

        JfrTypeWriter klassWriter = new JfrTypeWriter(JfrTypes.JfrTypeId.TYPE_CLASS.id, checkpointWriter);

        if (leakWriter == null && !classUnload) {
            try {
                klassWriter.begin();
                Consumer<Class<?>> kc = c -> {
                    if (!JfrTraceId.isSerialized(c)) {
                        klassWriter.incrementCount(writeKlass(checkpointWriter, c));
                    }
                    artifactSet.registerKlass(c);
                };

                JfrTraceIdLoadBarrier.doKlasses(kc, previousEpoch());
                doClassloaders(klassWriter);
                doObject();

                klassWriter.end();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            throw new RuntimeException("Leak profiler not yet implemented");
        }
        if (isComplete()) {
            return false;
        }

        artifactSet.tally(klassWriter.count());
        return true;
    }

    private static boolean isComplete() {
        return !artifactSet.hasKlassEntries() && currentEpoch();
    }

    private static int writeKlass(JfrCheckpointWriter writer, Class<?> c) {
        JfrTraceId.setSerialized(c);
        return writeKlass(writer, c, false);
    }

    private static int writeKlass(JfrCheckpointWriter writer, Class<?> c, boolean leak) {
        assert (writer != null);
        assert (artifactSet != null);
        assert (c != null);
        try {
            long cid = JfrTraceId.getTraceId(c);
            writer.encoded().writeLong(cid);
            ClassLoader cld = c.getClassLoader();
            long cldid = cld != null ? cldId(cld, leak) : 0;
            writer.encoded().writeLong(cldid);
            long symid = markSymbol(c, leak);
            writer.encoded().writeLong(symid);
            long pid = c.getPackage() != null ? packageId(c) : 0;
            writer.encoded().writeLong(pid);
            // JFR.TODO
            // writer->write(get_flags(klass));
            writer.encoded().writeInt(0);
            // JFR.TODO: This is added to metadata for JDK 15+
            // writer->write<bool>(klass->is_hidden());
            // writer.encoded().writeInt(0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return 1;
    }

    private static long markSymbol(Class<?> c, boolean leak) {
        return c != null ? getSymbolId(artifactSet.mark(c, leak)) : 0;
    }

    private static long markSymbol(String name, boolean leak) {
        return name != null ? getSymbolId(artifactSet.mark(name, leak)) : 0;
    }

    private static long getSymbolId(long artifactId) {
        return artifactId != 0 ? checkpointId << 24 | artifactId : 0;
    }

    private static void doObject() {
        // JFR.TODO
        // Write java.lang.Object
    }

    private static void doClassloaders(JfrTypeWriter klassWriter) {
        assert (checkpointWriter != null);
        for (ClassLoader cl : getReachableClassloaders()) {
            Class<?> c = cl.getClass();
            if (!JfrTraceId.isSerialized(c)) {
                klassWriter.incrementCount(writeKlass(checkpointWriter, c));
            }
            artifactSet.registerKlass(c);
        }
    }

    private static boolean currentEpoch() {
        return classUnload || flushpoint;
    }

    private static boolean previousEpoch() {
        return !currentEpoch();
    }

    private static void setup(JfrCheckpointWriter writer, JfrCheckpointWriter leakWriter, boolean classUnload,
            boolean flushpoint) {
        checkpointWriter = writer;
        JfrTypeSet.leakWriter = leakWriter;
        JfrTypeSet.classUnload = classUnload;
        JfrTypeSet.flushpoint = flushpoint;

        if (artifactSet == null) {
            artifactSet = new JfrArtifactSet(JfrTypeSet.classUnload);
        } else {
            artifactSet.initialize(JfrTypeSet.classUnload, clearArtifacts);
        }

        // if (!_class_unload) {
        // JfrKlassUnloading::sort(previous_epoch());
        // }
        clearArtifacts = false;
        assert (artifactSet != null);
        assert (!artifactSet.hasKlassEntries());

    }

    private static Set<ClassLoader> getReachableClassloaders() {
        return ImageSingletons.lookup(JfrRuntimeAccess.class).getReachableClassloaders();
    }

    public static void clear() {
        clearArtifacts();
        clearArtifacts = true;
        setup(null, null, false, false);
    }

    private static void clearArtifacts() {
        if (artifactSet == null) {
            return;
        }
        clearPackages();
        clearModules();
        clearClassloaders();
        clearKlassesAndMethods();
    }

    private static void clearPackages() {
        Consumer<Package> consumer = JfrTraceId::clearSerialized;
        artifactSet.iteratePackages(consumer);
    }

    private static void clearModules() {
        Consumer<Module> consumer = JfrTraceId::clearSerialized;
        artifactSet.iterateModules(consumer);
    }

    private static void clearClassloaders() {
        Consumer<ClassLoader> consumer = JfrTraceId::clearSerialized;
        artifactSet.iterateClassLoaders(consumer);
        for (ClassLoader cl : getReachableClassloaders()) {
            JfrTraceId.clearSerialized(cl);
        }
    }

    private static void clearKlassesAndMethods() {
        Consumer<Class<?>> consumer = JfrTraceId::clearSerialized;
        artifactSet.iterateKlasses(consumer);

    }
}
