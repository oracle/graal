/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.codegen;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.webimage.type.TypeControl;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.Signature;

/**
 * Replaces intrinsification patterns in handwritten JS code and jsbody code with type, method, and
 * field identifiers that appear in the image.
 *
 * Intrinsification Rules for handwritten JS:
 *
 * <ul>
 * <li>$t["qualified name"] denotes the specified java type and produces a
 * {@link TypeIntrinsification}</li>
 * <li>$f["field name"] is a field name access and produces a {@link FieldIntrinsification}</li>
 * <li>$m["method name"] is a method name access and produces a {@link MethodIntrinsification}</li>
 * </ul>
 *
 * A type intrinsification can stand on its own and can for example be used for a JavaScript
 * 'instanceof' check.
 *
 * Both field and method intrinsification need to be directly preceded by a type intrinsification.
 * Together, they reference a field or method on a given type.
 *
 * For static fields and methods, this would look like this:
 *
 * <pre>
 * $t["com.oracle.svm.webimage.longemulation.Long64"].$f["LongMinValue"]
 * $t["com.oracle.svm.webimage.longemulation.Long64"].$m["fromInt"]
 * </pre>
 *
 * This is replaced with the JS identifier for the type followed by a dot and the identifier for the
 * field or method.
 *
 * The syntax for member fields and methods is similar, but the whole thing must be preceded by the
 * target object and a period:
 *
 * <pre>
 * javaStr.$t["java.lang.String"].$f["value"]
 * javaStr.$t["java.lang.String"].$m["length"]
 * </pre>
 *
 * Here the type intrinsification will not appear in the output, it is just used to get the correct
 * identifier for the field or method.
 *
 * Intrinsification Rules for jsbody code:
 *
 * The syntax for jsbody is more restricted. Only method calls are allowed. Such a method call
 * begins with an '@' followed by the qualified name of the class, '::', the name of the method, the
 * method signature in parenthesis, and the arguments in parenthesis.
 *
 * <pre>
 * r.@java.lang.Runnable::run()()
 * var ten = @java.lang.Integer::parseInt(Ljava/lang/String;)("10")
 * </pre>
 */
public class JSIntrinsifyFile {

    @FunctionalInterface
    public interface MethodIntrinsificationFI {

        void intrinsifyMethod(WebImageJSProviders providers, StringBuilder sb, MethodIntrinsification methodIntrinsification, HostedMethod method, String ident);
    }

    private static final class NoIntrinsificationFI implements MethodIntrinsificationFI {

        @Override
        public void intrinsifyMethod(WebImageJSProviders providers, StringBuilder sb, MethodIntrinsification methodIntrinsification, HostedMethod method, String ident) {
            sb.append(ident);
        }
    }

    private static final MethodIntrinsificationFI NoIntrinsification = new NoIntrinsificationFI();

    /**
     * Reads a file from supplied stream.
     *
     * Makes sure that the file ends in a newline in case there is a '//' comment in the last line
     * which would also comment out anything that is appended.
     */
    public static String readFile(Supplier<InputStream> file) {
        StringBuilder sb = new StringBuilder();
        try (Scanner s = new Scanner(file.get())) {
            while (s.hasNextLine()) {
                String line = s.nextLine();
                sb.append(line);
                sb.append(System.lineSeparator());
            }
        }
        return sb.toString();
    }

    public static void process(FileData f, WebImageJSProviders providers) {
        process(f, providers, NoIntrinsification);
    }

    public static void process(FileData f, WebImageJSProviders providers, MethodIntrinsificationFI methodIntrinsifier) {
        for (JSIntrinsification js : f.intrinsics) {
            if (js instanceof TypeIntrinsification tjs) {
                /*
                 * By default, a type intrinsification is emitted. Method or field intrinsification
                 * may disable it for their preceding type.
                 */
                tjs.emit = true;
                Class<?> c = ReflectionUtil.lookupClass(false, tjs.name, providers.getClassLoader());
                tjs.resolvedType = (HostedType) providers.getMetaAccess().lookupJavaType(c);
            } else if (js instanceof FieldIntrinsification fjs) {
                HostedType type = fjs.precedingType.resolvedType;

                List<HostedField> fields = new ArrayList<>();
                Collections.addAll(fields, type.getInstanceFields(true));
                Collections.addAll(fields, (HostedField[]) type.getStaticFields());

                fjs.emit = true;
                for (HostedField field : fields) {
                    if (field.getName().equals(fjs.name)) {
                        fjs.resolvedField = field;
                        fjs.precedingType.emit = field.isStatic();
                        break;
                    }
                }
            } else if (js instanceof MethodIntrinsification mjs) {
                mjs.emit = true;
                for (HostedMethod method : mjs.precedingType.resolvedType.getAllDeclaredMethods()) {
                    if (method.getName().equals(mjs.name) && checkSignature(mjs.sig, method.getSignature())) {
                        mjs.resolvedMethod = method;
                        mjs.precedingType.emit = method.isStatic();
                        break;
                    }
                }
            }
        }

        validate(f);

        // all types,methods and fields are found, so we perform the replacements
        StringBuilder sb = new StringBuilder();
        // The end of the previous intrinsification
        int previousEnd = 0;
        for (JSIntrinsification js : f.intrinsics) {
            // Add anything between the previous intrinsification and the current one
            sb.append(f.content, previousEnd, js.startIndex);
            previousEnd = js.endIndex;

            // Will the next intrinsic be replaced?
            if (js.emit) {
                TypeControl typeControl = providers.typeControl();
                if (js instanceof TypeIntrinsification typeIntrinsification) {
                    HostedType type = typeIntrinsification.resolvedType;
                    sb.append(providers.typeControl().requestTypeName(type));
                } else if (js instanceof FieldIntrinsification fieldIntrinsification) {
                    HostedField field = fieldIntrinsification.resolvedField;

                    if (field.isStatic()) {
                        sb.append('.');
                    }

                    sb.append(typeControl.requestFieldName(field));
                } else if (js instanceof MethodIntrinsification methodIntrinsification) {
                    HostedMethod method = methodIntrinsification.resolvedMethod;
                    String ident = typeControl.requestMethodName(method);

                    if (method.isStatic()) {
                        sb.append('.');
                    }
                    methodIntrinsifier.intrinsifyMethod(providers, sb, methodIntrinsification, method, ident);
                }
            }
        }

        // Add everything after the final intrinsification
        sb.append(f.content.substring(previousEnd));

        f.setProcessed(sb.toString());
    }

    /**
     * Returns true if the parameters of the signature match the specified string.
     * <p>
     * Examples:
     *
     * <ul>
     *
     * <li>empty string for parameterless method</li>
     * <li>"ILjava/lang/String;ILjava/lang/String;" for method taking (int, String, int String)</li>
     * <li>Null string matches any signature.</li>
     * </ul>
     * Signature must not be null.
     */
    private static boolean checkSignature(String str, Signature sig) {
        if (sig == null) {
            throw new NullPointerException();
        }
        if (str == null) {
            return true;
        }
        int strPtr = 0;
        int arity = sig.getParameterCount(false);
        for (int i = 0; i < arity; i++) {
            String arg = sig.getParameterType(i, null).getName();
            int argLen = arg.length();
            if (!str.regionMatches(strPtr, arg, 0, argLen)) {
                return false;
            }
            strPtr += argLen;
        }
        return strPtr == str.length();
    }

    private static void validate(FileData f) {
        JSIntrinsification previous = null;

        for (JSIntrinsification js : f.intrinsics) {
            if (previous != null) {
                GraalError.guarantee(previous.endIndex <= js.startIndex, "Intrinsifications must appear in order.");
            }

            previous = js;

            switch (js) {
                case TypeIntrinsification tjs ->
                    GraalError.guarantee(tjs.resolvedType != null, "%s: Type must evaluate to a image type: '%s'", f.name, js.name);
                case FieldIntrinsification fjs -> {
                    GraalError.guarantee(fjs.resolvedField != null, "%s: Field must evaluate to an image type field '%s' type '%s'", f.name, js.name, fjs.precedingType.name);
                    GraalError.guarantee(fjs.startIndex == fjs.precedingType.endIndex, "%s: Field intrinsification must directly follow a type intrinsification", f.name);
                }
                case MethodIntrinsification mjs -> {
                    GraalError.guarantee(mjs.resolvedMethod != null, "%s: Method must evaluate to an image type method '%s' type '%s'", f.name, js.name, mjs.precedingType.name);
                    GraalError.guarantee(mjs.startIndex == mjs.precedingType.endIndex, "%s: Method intrinsification must directly follow a type intrinsification", f.name);
                }
                default -> GraalError.shouldNotReachHere(js.toString());
            }
        }
    }

    /**
     * Regular expression that matches all supported intrinsification patterns.
     * <ul>
     * <li>Type: {@code $t["<type name>"]}</li>
     * <li>Field: {@code .$f["<field name>"]}</li>
     * <li>Method: {@code .$m["<method name>"]}</li>
     * </ul>
     *
     * Also matches arbitrary whitespace, as allowed by the JavaScript syntax (around the dot,
     * around the brackets and around the string literal).
     * <p>
     * For field and method patterns, the single character ('f' or 'm') is matched in the
     * {@code indexedType} group, while for the type pattern, the 't' character is matched in the
     * {@code type} group. The name inside the quotes is always matched in the {@code name} group.
     */
    public static final Pattern LOOKUP_PATTERN = Pattern.compile("((\\s*\\.\\s*\\$(?<indexedType>[fm]))|(\\$(?<type>t)))\\s*\\[\\s*\"(?<name>[\\w|.]+)\"\\s*]");

    /**
     * Parse the contents of the given file data and extract intrinsifications using our
     * intrinsification syntax.
     */
    public static void collectIntrinsifications(FileData f) {
        Matcher m = LOOKUP_PATTERN.matcher(f.content);

        while (m.find()) {
            String match = m.group();
            String indexedType = m.group("indexedType");
            String type = m.group("type");
            String name = m.group("name");

            int startIndex = m.start();
            int endIndex = m.end();

            if (name == null) {
                throw new IntrinsificationException(f.name, startIndex, "Could not match name", match);
            }

            JSIntrinsification js;

            if (type != null) {
                if (!"t".equals(type)) {
                    throw new IntrinsificationException(f.name, startIndex, "Illegal intrinsification type", match);
                }
                js = new TypeIntrinsification();
            } else {
                if (indexedType == null) {
                    throw new IntrinsificationException(f.name, startIndex, "No type was matched", match);
                }

                if (name.contains(".")) {
                    throw new IntrinsificationException(f.name, startIndex, "Name in method or field pattern must not contain periods", match);
                }

                TypeIntrinsification precedingType = (TypeIntrinsification) f.intrinsics.getLast();

                if ("f".equals(indexedType)) {
                    FieldIntrinsification field = new FieldIntrinsification();
                    field.precedingType = precedingType;
                    js = field;
                } else if ("m".equals(indexedType)) {
                    MethodIntrinsification method = new MethodIntrinsification();
                    method.precedingType = precedingType;
                    js = method;
                } else {
                    throw new IntrinsificationException(f.name, startIndex, "Illegal intrinsification type " + indexedType, match);
                }
            }

            js.name = name;
            js.startIndex = startIndex;
            js.endIndex = endIndex;
            f.addIntrinsic(js);
        }
    }

    public static class FileData {
        public List<JSIntrinsification> intrinsics = new ArrayList<>();

        /**
         * A name for this "file" for debugging purposes.
         */
        public final String name;

        /**
         * The raw contents of the parsed data.
         */
        public final String content;

        /**
         * The contents of the parsed data after any patterns have been replaced.
         */
        private String processed;

        public FileData(String name, String content) {
            this.name = name;
            this.content = content;
        }

        public void addIntrinsic(JSIntrinsification js) {
            intrinsics.add(js);
        }

        public boolean isProcessed() {
            return processed != null;
        }

        public void setProcessed(String s) {
            assert s != null;
            processed = s;
        }

        public String getProcessed() {
            assert isProcessed() : "File " + name + " was never processed";
            return processed;
        }
    }

    public abstract static sealed class JSIntrinsification {
        public String name;
        /**
         * The intrinsification starts at this character in the string.
         */
        public int startIndex;
        /**
         * The index of the first character after the intrinsification.
         */
        public int endIndex;
        /**
         * Whether this intrinsification should be emitted in the image.
         *
         * If it is not emitted, the intrinsification pattern is removed without replacement.
         */
        public boolean emit;
    }

    public static final class TypeIntrinsification extends JSIntrinsification {
        public HostedType resolvedType;
    }

    public static final class FieldIntrinsification extends JSIntrinsification {
        public TypeIntrinsification precedingType;
        public HostedField resolvedField;
    }

    public static final class MethodIntrinsification extends JSIntrinsification {
        public TypeIntrinsification precedingType;
        public HostedMethod resolvedMethod;
        public String sig;
    }

    @SuppressWarnings("serial")
    public static class IntrinsificationException extends RuntimeException {
        public IntrinsificationException(String filename, int startIndex, String message, String fullMatch) {
            super(filename + " at index " + startIndex + ": " + message + ". Full match: " + fullMatch);
        }
    }
}
