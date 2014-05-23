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
package com.oracle.graal.hotspot;

import java.io.*;
import java.util.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.options.*;

/**
 * Helper class for separating loading of options from option initialization at runtime.
 */
class HotSpotOptionsLoader {
    static final SortedMap<String, OptionDescriptor> options = new TreeMap<>();

    /**
     * Initializes {@link #options} from {@link Options} services.
     */
    static {
        ServiceLoader<Options> sl = ServiceLoader.load(Options.class);
        for (Options opts : sl) {
            for (OptionDescriptor desc : opts) {
                if (isHotSpotOption(desc)) {
                    String name = desc.getName();
                    OptionDescriptor existing = options.put(name, desc);
                    assert existing == null : "Option named \"" + name + "\" has multiple definitions: " + existing.getLocation() + " and " + desc.getLocation();
                }
            }
        }
    }

    /**
     * Determines if a given option is a HotSpot command line option.
     */
    private static boolean isHotSpotOption(OptionDescriptor desc) {
        return desc.getClass().getName().startsWith("com.oracle.graal");
    }

    /**
     * Command line utility for generating the source code of GraalRuntime::set_option() which is
     * written {@link System#out}.
     */
    public static void main(String[] args) {
        PrintStream out = System.out;
        try {
            Set<Integer> lengths = new TreeSet<>();
            for (String s : options.keySet()) {
                lengths.add(s.length());
            }
            lengths.add("PrintFlags".length());

            out.println("bool GraalRuntime::set_option(KlassHandle hotSpotOptionsClass, const char* name, int name_len, Handle name_handle, const char* value, TRAPS) {");
            out.println("  if (value[0] == '+' || value[0] == '-') {");
            out.println("    // boolean options");
            genMatchers(out, lengths, true);
            out.println("  } else {");
            out.println("    // non-boolean options");
            genMatchers(out, lengths, false);
            out.println("  }");
            out.println("  return false;");
            out.println("}");
        } catch (Throwable t) {
            t.printStackTrace(out);
        }
        out.flush();
    }

    protected static void genMatchers(PrintStream out, Set<Integer> lengths, boolean isBoolean) throws Exception {
        out.println("    switch (name_len) {");
        for (int len : lengths) {
            boolean printedCase = false;

            // The use of strncmp is required (instead of strcmp) as the option name will not be
            // null terminated for <name>=<value> style options.
            if (len == "PrintFlags".length() && isBoolean) {
                printedCase = true;
                out.println("    case " + len + ":");
                out.printf("      if (strncmp(name, \"PrintFlags\", %d) == 0) {\n", len);
                out.println("        if (value[0] == '+') {");
                out.println("          VMToCompiler::setOption(hotSpotOptionsClass, name_handle, Handle(), '?', Handle(), 0L);");
                out.println("        }");
                out.println("        return true;");
                out.println("      }");
            }
            for (Map.Entry<String, OptionDescriptor> e : options.entrySet()) {
                OptionDescriptor desc = e.getValue();
                if (e.getKey().length() == len && ((desc.getType() == Boolean.class) == isBoolean)) {
                    if (!printedCase) {
                        printedCase = true;
                        out.println("    case " + len + ":");
                    }
                    out.printf("      if (strncmp(name, \"%s\", %d) == 0) {\n", e.getKey(), len);
                    Class<?> declaringClass = desc.getDeclaringClass();
                    out.printf("        Handle option = get_OptionValue(\"L%s;\", \"%s\", \"L%s;\", CHECK_(true));\n", toInternalName(declaringClass), desc.getFieldName(),
                                    toInternalName(getFieldType(desc)));
                    if (isBoolean) {
                        out.println("        VMToCompiler::setOption(hotSpotOptionsClass, name_handle, option, value[0], Handle(), 0L);");
                    } else if (desc.getType() == String.class) {
                        out.println("        Handle stringValue = java_lang_String::create_from_str(value, CHECK_(true));");
                        out.println("        VMToCompiler::setOption(hotSpotOptionsClass, name_handle, option, 's', stringValue, 0L);");
                    } else {
                        char spec = getPrimitiveSpecChar(desc);
                        out.println("        jlong primitiveValue = parse_primitive_option_value('" + spec + "', name_handle, value, CHECK_(true));");
                        out.println("        VMToCompiler::setOption(hotSpotOptionsClass, name_handle, option, '" + spec + "', Handle(), primitiveValue);");
                    }
                    out.println("        return true;");
                    out.println("      }");
                }
            }
        }
        out.println("    }");
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