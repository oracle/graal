/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.visualizer.bc.modelimpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

/**
 * This class holds informations like package, class, arguments, return values
 * and name of a method.
 *
 * @author Alexander Reder
 * @author Christian Wimmer
 */
public class MethodName {
    
    public String methodModifier;
    public Type returnValType;
    public String className;
    public String methodName;
    public List<Type> arguments;
    
    private MethodName() {
        // Prevent creation from outside.
    }
    
    /**
     * Initializes a new MethodName and parses the full name.
     *
     * @param   name    full name of the method
     */
    public static MethodName parse(String name) {
        Scanner methodScanner = new Scanner(name);
        methodScanner.useDelimiter("\\s|\\,\\s|\\(|\\)$");
        try {
            MethodName result = new MethodName();
            result.methodModifier = methodScanner.next();
            result.returnValType = getTypeOf(methodScanner.next());
            String[] classMethodName = methodScanner.next().split("\\.");
            StringBuilder pkgClass = new StringBuilder();
            pkgClass.append(classMethodName[0]);
            for(int i = 1; i < classMethodName.length - 1; i++) {
                pkgClass.append(".");
                pkgClass.append(classMethodName[i]);
            }
            result.className = pkgClass.toString();
            result.methodName = classMethodName[classMethodName.length - 1];
            result.arguments = new ArrayList<Type>();
            String argument;
            while(methodScanner.hasNext()) {
                argument = methodScanner.next();
                if(!argument.equals("")) {
                    result.arguments.add(getTypeOf(argument));
                }
            }
            return result;
        } catch (Exception ex) {
            Logger log = Logger.getLogger(BytecodesParser.class.getName());
            log.log(Level.WARNING, ex.getMessage(), ex);
            return null;
        } finally {
            methodScanner.close();
        }
    }
    
    /**
     * Checks if the specified method has the same signature as defined by this
     * parsed method name.
     */
    public boolean matches(Method m) {
        if (!methodName.equals(m.getName())) {
            return false;
        }
        if (returnValType != Type.UNKNOWN && returnValType != m.getReturnType()) {
            return false;
        }
        if (arguments.size() != m.getArgumentTypes().length) {
            return false;
        }
        for(int i = 0; i < arguments.size(); i++) {
            if (arguments.get(i) != Type.UNKNOWN && arguments.get(i) != m.getArgumentTypes()[i]) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Translates the String representation of a type into a Type.
     *
     * @param   type    String representation of the type
     * @return          Type of the Stringrepresentation
     */
    private static Type getTypeOf(String type) {
        if(type.equals("void")) {
            return Type.VOID;
        } else if(type.equals("jint")) {
            return Type.INT;
        } else if(type.equals("jshort")) {
            return Type.SHORT;
        } else if(type.equals("jlong")) {
            return Type.LONG;
        } else if(type.equals("jchar")) {
            return Type.CHAR;
        } else if(type.equals("jboolean")) {
            return Type.BOOLEAN;
        } else if(type.equals("jdouble")) {
            return Type.DOUBLE;
        } else if(type.equals("jfloat")) {
            return Type.FLOAT;
        } else {
            return Type.UNKNOWN;
        }
    }
}
