/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;

import java.io.File;
import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.util.function.Function;

public interface CustomizedBytecodePattern {

    class DumpSupport {
        static final File GENERATED_CLASS_FILE_OUTPUT_DIRECTORY;

        static {
            String prop = System.getProperty("save.generated.classfile.dir");
            File file = null;
            if (prop != null) {
                file = new File(prop);
                ensureDirectoryExists(file);
                assert file.exists() : file;
            }
            GENERATED_CLASS_FILE_OUTPUT_DIRECTORY = file;
        }

        private static File ensureDirectoryExists(File file) {
            if (!file.exists()) {
                file.mkdirs();
            }
            return file;
        }

        static void dump(String className, byte[] classfileBytes) {
            if (GENERATED_CLASS_FILE_OUTPUT_DIRECTORY != null) {
                try {
                    File classfile = new File(GENERATED_CLASS_FILE_OUTPUT_DIRECTORY, className.replace('.', File.separatorChar) + ".class");
                    ensureDirectoryExists(classfile.getParentFile());
                    Files.write(classfile.toPath(), classfileBytes);
                    System.out.println("Wrote: " + classfile.getAbsolutePath());
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            }
        }
    }

    class CachedLoader extends ClassLoader {

        final String className;
        Class<?> loaded;
        final Function<String, byte[]> classfileSupplier;

        public CachedLoader(ClassLoader parent, String className, Function<String, byte[]> classfileSupplier) {
            super(parent);
            this.className = className;
            this.classfileSupplier = classfileSupplier;
        }

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(className)) {
                if (loaded == null) {
                    byte[] classfileBytes = classfileSupplier.apply(name);
                    DumpSupport.dump(name, classfileBytes);
                    loaded = defineClass(name, classfileBytes, 0, classfileBytes.length);
                }
                return loaded;
            } else {
                return super.findClass(name);
            }
        }
    }

    default Class<?> getClass(String className) throws ClassNotFoundException {
        return new CachedLoader(CustomizedBytecodePattern.class.getClassLoader(), className, this::generateClass).findClass(className);
    }

    default Class<?> lookupClass(MethodHandles.Lookup lookup, String className) throws IllegalAccessException {
        byte[] classfileBytes = generateClass(className);
        DumpSupport.dump(className, classfileBytes);
        return lookup.defineClass(classfileBytes);
    }

    int ACC_PUBLIC_STATIC = ACC_PUBLIC | ACC_STATIC;

    default ClassDesc cd(Class<?> klass) {
        return klass.describeConstable().orElseThrow();
    }

    byte[] generateClass(String className);
}
