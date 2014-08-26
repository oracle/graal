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
package com.oracle.graal.hotspot.sourcegen;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.*;
import java.util.zip.*;

import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.options.*;

/**
 * Command line utility for generating the source code of {@code GraalRuntime.inline.hpp}.
 */
public class GenGraalRuntimeInlineHpp {

    public static class GraalJars implements Iterable<ZipEntry> {
        private final List<ZipFile> jars = new ArrayList<>(2);

        public GraalJars() {
            String classPath = System.getProperty("java.class.path");
            for (String e : classPath.split(File.pathSeparator)) {
                if (e.endsWith(File.separatorChar + "graal.jar") || e.endsWith(File.separatorChar + "graal-truffle.jar")) {
                    try {
                        jars.add(new ZipFile(e));
                    } catch (IOException ioe) {
                        throw new InternalError(ioe);
                    }
                }
            }
            if (jars.size() != 2) {
                throw new InternalError("Could not find graal.jar or graal-truffle.jar on class path: " + classPath);
            }
        }

        public Iterator<ZipEntry> iterator() {
            Stream<ZipEntry> entries = jars.stream().flatMap(ZipFile::stream);
            return entries.iterator();
        }

        public InputStream getInputStream(String classFilePath) throws IOException {
            for (ZipFile jar : jars) {
                ZipEntry entry = jar.getEntry(classFilePath);
                if (entry != null) {
                    return jar.getInputStream(entry);
                }
            }
            return null;
        }
    }

    private static final GraalJars graalJars = new GraalJars();

    public static void main(String[] args) {
        PrintStream out = System.out;
        try {
            genGetServiceImpls(out);
            genSetOption(out);
        } catch (Throwable t) {
            t.printStackTrace(out);
        }
        out.flush();
    }

    /**
     * Generates code for {@code GraalRuntime::get_service_impls()}.
     */
    private static void genGetServiceImpls(PrintStream out) throws Exception {
        final List<Class<? extends Service>> services = new ArrayList<>();
        for (ZipEntry zipEntry : graalJars) {
            String name = zipEntry.getName();
            if (name.startsWith("META-INF/services/")) {
                String serviceName = name.substring("META-INF/services/".length());
                Class<?> c = Class.forName(serviceName);
                if (Service.class.isAssignableFrom(c)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Service> sc = (Class<? extends Service>) c;

                    services.add(sc);
                }
            }
        }

        Set<Integer> lengths = new TreeSet<>();
        for (Class<?> service : services) {
            lengths.add(toInternalName(service).length());
        }

        out.println("Handle GraalRuntime::get_service_impls(KlassHandle serviceKlass, TRAPS) {");
        out.println("  switch (serviceKlass->name()->utf8_length()) {");
        for (int len : lengths) {
            boolean printedCase = false;
            for (Class<?> service : services) {
                String serviceName = toInternalName(service);
                if (len == serviceName.length()) {
                    if (!printedCase) {
                        printedCase = true;
                        out.println("  case " + len + ":");
                    }
                    out.printf("    if (serviceKlass->name()->equals(\"%s\", %d)) {%n", serviceName, serviceName.length());
                    List<Class<?>> impls = new ArrayList<>();
                    for (Object impl : ServiceLoader.load(service)) {
                        impls.add(impl.getClass());
                    }

                    out.printf("      objArrayOop servicesOop = oopFactory::new_objArray(serviceKlass(), %d, CHECK_NH);%n", impls.size());
                    out.println("      objArrayHandle services(THREAD, servicesOop);");
                    for (int i = 0; i < impls.size(); i++) {
                        String name = toInternalName(impls.get(i));
                        out.printf("      %sservice = create_Service(\"%s\", CHECK_NH);%n", (i == 0 ? "Handle " : ""), name);
                        out.printf("      services->obj_at_put(%d, service());%n", i);
                    }
                    out.println("      return services;");
                    out.println("    }");
                }
            }

        }
        out.println("  }");
        out.println("  return Handle();");
        out.println("}");
    }

    /**
     * Generates code for {@code GraalRuntime::set_option()} and
     * {@code GraalRuntime::set_option_bool()}.
     */
    private static void genSetOption(PrintStream out) throws Exception {
        SortedMap<String, OptionDescriptor> options = getOptions();

        Set<Integer> lengths = new TreeSet<>();
        for (String s : options.keySet()) {
            lengths.add(s.length());
        }
        lengths.add("PrintFlags".length());

        out.println("bool GraalRuntime::set_option_bool(KlassHandle hotSpotOptionsClass, char* name, size_t name_len, char value, TRAPS) {");
        out.println("  bool check_only = hotSpotOptionsClass.is_null();");
        genMatchers(out, lengths, options, true);
        out.println("  return false;");
        out.println("}");
        out.println("bool GraalRuntime::set_option(KlassHandle hotSpotOptionsClass, char* name, size_t name_len, const char* value, TRAPS) {");
        out.println("  bool check_only = hotSpotOptionsClass.is_null();");
        genMatchers(out, lengths, options, false);
        out.println("  return false;");
        out.println("}");
    }

    protected static void genMatchers(PrintStream out, Set<Integer> lengths, SortedMap<String, OptionDescriptor> options, boolean isBoolean) throws Exception {
        out.println("  switch (name_len) {");
        for (int len : lengths) {
            boolean printedCase = false;

            // The use of strncmp is required (instead of strcmp) as the option name will not be
            // null terminated for <name>=<value> style options.
            if (len == "PrintFlags".length() && isBoolean) {
                printedCase = true;
                out.println("  case " + len + ":");
                out.printf("    if (strncmp(name, \"PrintFlags\", %d) == 0) {%n", len);
                out.println("      if (value == '+') {");
                out.println("        if (check_only) {");
                out.println("          TempNewSymbol name = SymbolTable::new_symbol(\"Lcom/oracle/graal/hotspot/HotSpotOptions;\", CHECK_(true));");
                out.println("          hotSpotOptionsClass = SystemDictionary::resolve_or_fail(name, true, CHECK_(true));");
                out.println("        }");
                out.println("        set_option_helper(hotSpotOptionsClass, name, name_len, Handle(), '?', Handle(), 0L);");
                out.println("      }");
                out.println("      return true;");
                out.println("    }");
            }
            for (Map.Entry<String, OptionDescriptor> e : options.entrySet()) {
                OptionDescriptor desc = e.getValue();
                if (e.getKey().length() == len && ((desc.getType() == Boolean.class) == isBoolean)) {
                    if (!printedCase) {
                        printedCase = true;
                        out.println("  case " + len + ":");
                    }
                    out.printf("    if (strncmp(name, \"%s\", %d) == 0) {%n", e.getKey(), len);
                    Class<?> declaringClass = desc.getDeclaringClass();
                    if (isBoolean) {
                        out.printf("      Handle option = get_OptionValue(\"L%s;\", \"%s\", \"L%s;\", CHECK_(true));%n", toInternalName(declaringClass), desc.getFieldName(),
                                        toInternalName(getFieldType(desc)));
                        out.println("      if (!check_only) {");
                        out.println("        set_option_helper(hotSpotOptionsClass, name, name_len, option, value, Handle(), 0L);");
                        out.println("      }");
                    } else if (desc.getType() == String.class) {
                        out.println("      check_required_value(name, name_len, value, CHECK_(true));");
                        out.printf("      Handle option = get_OptionValue(\"L%s;\", \"%s\", \"L%s;\", CHECK_(true));%n", toInternalName(declaringClass), desc.getFieldName(),
                                        toInternalName(getFieldType(desc)));
                        out.println("      if (!check_only) {");
                        out.println("        Handle stringValue = java_lang_String::create_from_str(value, CHECK_(true));");
                        out.println("        set_option_helper(hotSpotOptionsClass, name, name_len, option, 's', stringValue, 0L);");
                        out.println("      }");
                    } else {
                        char spec = getPrimitiveSpecChar(desc);
                        out.println("      jlong primitiveValue = parse_primitive_option_value('" + spec + "', name, name_len, value, CHECK_(true));");
                        out.println("      if (!check_only) {");
                        out.printf("        Handle option = get_OptionValue(\"L%s;\", \"%s\", \"L%s;\", CHECK_(true));%n", toInternalName(declaringClass), desc.getFieldName(),
                                        toInternalName(getFieldType(desc)));
                        out.println("        set_option_helper(hotSpotOptionsClass, name, name_len, option, '" + spec + "', Handle(), primitiveValue);");
                        out.println("      }");
                    }
                    out.println("      return true;");
                    out.println("    }");
                }
            }
        }
        out.println("  }");
    }

    @SuppressWarnings("unchecked")
    static SortedMap<String, OptionDescriptor> getOptions() throws Exception {
        Field field = Class.forName("com.oracle.graal.hotspot.HotSpotOptionsLoader").getDeclaredField("options");
        field.setAccessible(true);
        SortedMap<String, OptionDescriptor> options = (SortedMap<String, OptionDescriptor>) field.get(null);

        Set<Class<?>> checked = new HashSet<>();
        for (final OptionDescriptor option : options.values()) {
            Class<?> cls = option.getDeclaringClass();
            OptionsVerifier.checkClass(cls, option, checked, graalJars);
        }
        return options;
    }

    private static Class<?> getFieldType(OptionDescriptor desc) throws Exception {
        return desc.getDeclaringClass().getDeclaredField(desc.getFieldName()).getType();
    }

    private static String toInternalName(Class<?> c) {
        return c.getName().replace('.', '/');
    }

    /**
     * @see HotSpotOptions#setOption(String, OptionValue, char, String, long)
     */
    @SuppressWarnings("javadoc")
    private static char getPrimitiveSpecChar(OptionDescriptor desc) {
        if (desc.getType() == Integer.class) {
            return 'i';
        }
        if (desc.getType() == Float.class) {
            return 'f';
        }
        if (desc.getType() == Double.class) {
            return 'd';
        }
        throw GraalInternalError.shouldNotReachHere("Unexpected primitive option type: " + desc.getType().getName());
    }
}
