/*
 * Copyright (c) 2010 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.oracle.graal.runtime.logging;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Scoped logging class used to display the call hierarchy of VMEntries/VMExits calls.
 *
 * @author Lukas Stadler
 */
public class Logger {

    public static final boolean ENABLED = Boolean.valueOf(System.getProperty("c1x.debug"));
    private static final int SPACING = 4;
    private static Deque<Boolean> openStack = new LinkedList<Boolean>();
    private static boolean open = false;
    private static int level = 0;

    private static final PrintStream out;

    static {
        PrintStream ps = null;
        String filename = System.getProperty("c1x.info_file");
        if (filename != null && !"".equals(filename)) {
            try {
                ps = new PrintStream(new FileOutputStream(filename));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                ps = null;
            }
        }
        out = ps;
        if (out != null) {
            out.println("start: " + new Date());
        }
    }

    public static void info(String message) {
        if (ENABLED) {
            log(message);
        } else {
            System.out.println(message);
        }
        if (out != null) {
            out.println(message);
            out.flush();
        }
    }

    public static void log(String message) {
        if (ENABLED) {
            for (String line : message.split("\n")) {
                if (open) {
                    System.out.println("...");
                    open = false;
                }
                System.out.print(space(level));
                System.out.println(line);
            }
        }
    }

    public static void startScope(String message) {
        if (ENABLED) {
            if (open) {
                System.out.println("...");
                open = false;
            }
            System.out.print(space(level));
            System.out.print(message);
            openStack.push(open);
            open = true;
            level++;
        }
    }

    public static void endScope(String message) {
        if (ENABLED) {
            level--;
            if (open) {
                System.out.println(message);
            } else {
                System.out.println(space(level) + "..." + message);
            }
            open = openStack.pop();
        }
    }

    private static String[] spaces = new String[50];

    private static String space(int count) {
        assert count >= 0;
        String result;
        if (count >= spaces.length || spaces[count] == null) {
            StringBuilder str = new StringBuilder();
            for (int i = 0; i < count * SPACING; i++) {
                str.append(' ');
            }
            result = str.toString();
            if (count < spaces.length) {
                spaces[count] = result;
            }
        } else {
            result = spaces[count];
        }
        return result;
    }

    public static String pretty(Object value) {
        if (value == null) {
            return "null";
        }

        Class<?> klass = value.getClass();
        if (value instanceof Void) {
            return "void";
        } else if (value instanceof String) {
            return "\"" + value + "\"";
        } else if (value instanceof Method) {
            return "method \"" + ((Method) value).getName() + "\"";
        } else if (value instanceof Class<?>) {
            return "class \"" + ((Class<?>) value).getSimpleName() + "\"";
        } else if (value instanceof Integer) {
            if ((Integer) value < 10) {
                return value.toString();
            }
            return value + " (0x" + Integer.toHexString((Integer) value) + ")";
        } else if (value instanceof Long) {
            if ((Long) value < 10) {
                return value + "l";
            }
            return value + "l (0x" + Long.toHexString((Long) value) + "l)";
        } else if (klass.isArray()) {
            StringBuilder str = new StringBuilder();
            int dimensions = 0;
            while (klass.isArray()) {
                dimensions++;
                klass = klass.getComponentType();
            }
            str.append(klass.getSimpleName()).append('[').append(Array.getLength(value)).append(']');
            for (int i = 1; i < dimensions; i++) {
                str.append("[]");
            }
            return str.toString();
        }

        return value.toString();
    }
}
