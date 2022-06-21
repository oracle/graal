/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.oracle.truffle.llvm.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.collections.Pair;

import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;

/**
 * This class implements parts of the Swift name mangling and demangling. Full documentation:
 * https://github.com/apple/swift/blob/main/docs/ABI/Mangling.rst [2021-11-15]
 */
public final class SwiftDemangler {
    private static final String NAMESPACE_PREFIX_DEFAULT = "$s";
    private static final Set<String> NAMESPACE_PREFIXES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("$s", "$S", "_T0")));

    public static final String FUNCTION_DESCRIPTOR_SUFFIX = "Tq";
    public static final String CLASS_SEPERATOR = "C";
    public static final String TYPE_METADATA_ACCESSOR_SUFFIX = "Ma";
    public static final String CLASS_SUFFIX = "D";

    private int idx;
    private final String name;
    private final String prefix;

    private SwiftDemangler(String name) {
        Optional<String> prefixOpt = NAMESPACE_PREFIXES.stream().filter(name::startsWith).findAny();
        if (!prefixOpt.isPresent()) {
            throw new LLVMLinkerException("Not a mangled namespace: " + name);
        }
        this.prefix = prefixOpt.get();
        this.name = name;
        this.idx = this.prefix.length();
    }

    private int parseNumber() {
        int startIdx = idx;
        int libnameLength = 0;
        while (idx < name.length()) {
            char c = name.charAt(idx);
            if (c >= '0' && c <= '9') {
                int d = c - '0';
                libnameLength = libnameLength * 10 + d;
                idx++;
            } else {
                return libnameLength;
            }
        }
        throw new LLVMLinkerException(String.format("Premature end of name string: %s (%d)", name, startIdx));
    }

    private ArrayList<String> decode() {
        ArrayList<String> namespaces = new ArrayList<>();
        while (idx < name.length()) {
            int length = parseNumber();
            if (length == 0) {
                // end of name spaces -- add remaining string to result
                namespaces.add(name.substring(idx));
                return namespaces;
            }
            int newIdx = idx + length;
            if (newIdx >= name.length()) {
                throw new LLVMLinkerException(String.format("Premature end of name string: %s (%d)", name, idx));
            }
            namespaces.add(name.substring(idx, newIdx));
            idx = newIdx;
        }
        throw new LLVMLinkerException(String.format("Unterminated name %s (%d)", name, idx));
    }

    /**
     * Decodes a mangled C++ name into a list of namespaces. The last entry is the (mangled) symbol
     * name (global or function).
     *
     * @throws LLVMLinkerException if the given name cannot be decoded.
     */
    public static ArrayList<String> decodeNamespace(String name) {
        return new SwiftDemangler(name).decode();
    }

    public class MethodDescriptor {
        ArrayList<String> className;
        String methodName;
        String returnType;
        List<Pair<String, String>> parameters = new ArrayList<>(); // List<[type, name]>

        public String getMethodName() {
            return methodName;
        }

        @Override
        public String toString() {
            String clNames = className.stream().collect(Collectors.joining("."));
            String formpars = parameters.stream().map(p -> p.getLeft() + " " + p.getRight()).collect(Collectors.joining(","));
            return String.format("%s.%s(%s) -> %s", clNames, methodName, formpars, returnType);
        }
    }

    public static MethodDescriptor decodeFunctionDescriptor(String name) {
        SwiftDemangler swiftDemangler = new SwiftDemangler(name);
        return swiftDemangler.decodeFunctionDescriptor();
    }

    private MethodDescriptor decodeFunctionDescriptor() {
        MethodDescriptor d = new MethodDescriptor();
        ArrayList<String> tmp = decode();
        String remaining = tmp.remove(tmp.size() - 1);
        d.className = tmp;

        if (name.charAt(idx) == 'C') {
            idx++;
        } else {
            throw new LLVMLinkerException(String.format("Function descriptor: after namespaces, no class follows: %s", remaining));
        }
        tmp = decode();
        d.methodName = tmp.remove(0);
        List<String> parNames = new ArrayList<>();
        while (tmp.size() > 1) {
            // parameter names
            parNames.add(tmp.remove(0));
        }
        // return type + parameter types
        Pair<List<String>, List<String>> typeList = readTypeSpec();
        List<String> returnTypes = typeList.getLeft();
        List<String> parameterTypes = typeList.getRight();
        d.returnType = returnTypes.size() < 1 ? "" : returnTypes.size() == 1 ? returnTypes.get(0) : returnTypes.stream().collect(Collectors.joining(",", "<", ">"));
        if (parNames.size() == parameterTypes.size()) {
            for (int i = 0; i < parNames.size(); i++) {
                d.parameters.add(Pair.create(parameterTypes.get(i), parNames.get(i)));
            }
        } else {
            throw new LLVMLinkerException(String.format("Number of parameter names and types differ for: %s", name));
        }
        return d;

    }

    private Pair<List<String>, List<String>> readTypeSpec() {
        List<Integer> listIndices = new ArrayList<>();
        List<String> strings = new ArrayList<>();
        if (name.charAt(idx) == 'y') {
            strings.add("<void>");
            idx++;
        }
        while (name.charAt(idx) == 'S') {
            idx++;
            Pair<String, Integer> p = readKnownTypeKind();
            while (p.getRight() > 0) {
                strings.add(p.getLeft());
                p = Pair.create(p.getLeft(), p.getRight() - 1);
            }
            if (name.charAt(idx) == '_') {
                listIndices.add(strings.size());
                idx++;
            }
        }
        // fill lists
        int i = 0;
        List<String> first = new ArrayList<>();
        if (i >= strings.size()) {
            return Pair.create(first, first);
        }
        do {
            first.add(strings.get(i++));
        } while (listIndices.contains(i) && i < strings.size());

        List<String> second = new ArrayList<>();
        if (i < strings.size()) {
            second.add(strings.get(i++));
        }
        while (listIndices.contains(i) && i < strings.size()) {
            second.add(strings.get(i++));
        }
        return Pair.create(first, second);
    }

    private Pair<String, Integer> readKnownTypeKind() {
        int repeat = parseNumber();
        if (repeat == 0) {
            repeat = 1;
        }
        String str;
        switch (name.charAt(idx++)) {
            case 'A':
                str = "Swift.AutoreleasingUnsafeMutablePointer";
                break;
            case 'a':
                str = "Swift.Array";
                break;
            case 'B':
                str = "Swift.BinaryFloatingPoint";
                break;
            case 'b':
                str = "Swift.Bool";
                break;
            case 'c':
                str = "<KNOWN-TYPE-KIND-2>";
                switch (name.charAt(idx++)) {
                    case 'A':
                        str = "Swift.Actor";
                        break;
                    case 'C':
                        str = "Swift.CheckedContinuation";
                        break;
                    case 'c':
                        str = "Swift.UnsafeContinuation";
                        break;
                    case 'E':
                        str = "Swift.CancellationError";
                        break;
                    case 'e':
                        str = "Swift.UnownedSerialExecutor";
                        break;
                    case 'F':
                        str = "Swift.Executor";
                        break;
                    case 'f':
                        str = "Swift.SerialExecutor";
                        break;
                    case 'G':
                        str = "Swift.TaskGroup";
                        break;
                    case 'g':
                        str = "Swift.ThrowingTaskGroup";
                        break;
                    case 'I':
                        str = "Swift.AsyncIteratorProtocol";
                        break;
                    case 'i':
                        str = "Swift.AsyncSequence";
                        break;
                    case 'J':
                        str = "Swift.UnownedJob";
                        break;
                    case 'M':
                        str = "Swift.MainActor";
                        break;
                    case 'P':
                        str = "Swift.TaskPriority";
                        break;
                    case 'S':
                        str = "Swift.AsyncStream";
                        break;
                    case 's':
                        str = "Swift.AsyncThrowingStream";
                        break;
                    case 'T':
                        str = "Swift.Task";
                        break;
                    case 't':
                        str = "Swift.UnsafeCurrentTask";
                        break;
                }
                break;
            case 'D':
                str = "Swift.Dictionary";
                break;
            case 'd':
                str = "Swift.Float64";
                break;
            case 'E':
                str = "Swift.Encodable";
                break;
            case 'e':
                str = "Swift.Decodable";
                break;
            case 'F':
                str = "Swift.FloatingPoint";
                break;
            case 'f':
                str = "Swift.Float32";
                break;
            case 'G':
                str = "Swift.RandomNumberGenerator";
                break;
            case 'H':
                str = "Swift.Hashable";
                break;
            case 'h':
                str = "Swift.Set";
                break;
            case 'I':
                str = "Swift.DefaultIndices";
                break;
            case 'i':
                str = "Swift.Int";
                break;
            case 'J':
                str = "Swift.Character";
                break;
            case 'j':
                str = "Swift.Numeric";
                break;
            case 'K':
                str = "Swift.BidirectionalCollection";
                break;
            case 'k':
                str = "Swift.RandomAccessCollection";
                break;
            case 'L':
                str = "Swift.Comparable";
                break;
            case 'l':
                str = "Swift.Collection";
                break;
            case 'M':
                str = "Swift.MutableCollection";
                break;
            case 'm':
                str = "Swift.RangeReplaceableCollection";
                break;
            case 'N':
                str = "Swift.ClosedRange";
                break;
            case 'n':
                str = "Swift.Range";
                break;
            case 'O':
                str = "Swift.ObjectIdentifier";
                break;
            case 'P':
                str = "Swift.UnsafePointer";
                break;
            case 'p':
                str = "Swift.UnsafeMutablePointer";
                break;
            case 'Q':
                str = "Swift.Equatable";
                break;
            case 'q':
                str = "Swift.Optional";
                break;
            case 'R':
                str = "Swift.UnsafeBufferPointer";
                break;
            case 'r':
                str = "Swift.UnsafeMutableBufferPointer";
                break;
            case 'S':
                str = "Swift.String";
                break;
            case 's':
                str = "Swift.Substring";
                break;
            case 'T':
                str = "Swift.Sequence";
                break;
            case 't':
                str = "Swift.IteratorProtocol";
                break;
            case 'U':
                str = "Swift.UnsignedInteger";
                break;
            case 'u':
                str = "Swift.UInt";
                break;
            case 'V':
                str = "Swift.UnsafeRawPointer";
                break;
            case 'v':
                str = "Swift.UnsafeMutableRawPointer";
                break;
            case 'W':
                str = "Swift.UnsafeRawBufferPointer";
                break;
            case 'w':
                str = "Swift.UnsafeMutableRawBufferPointer";
                break;
            case 'X':
                str = "Swift.RangeExpression";
                break;
            case 'x':
                str = "Swift.Strideable";
                break;
            case 'Y':
                str = "Swift.RawRepresentable";
                break;
            case 'y':
                str = "Swift.StringProtocol";
                break;
            case 'Z':
                str = "Swift.SignedInteger";
                break;
            case 'z':
                str = "Swift.BinaryInteger";
                break;
            default:
                return Pair.create("", 0);
        }
        return Pair.create(str, repeat);
    }

    public static boolean isMangledSwiftName(String name) {
        return name != null && NAMESPACE_PREFIXES.contains(name);
    }

    public static boolean isMangledSwiftFunctionName(String name) {
        return isMangledSwiftName(name) && name.endsWith(FUNCTION_DESCRIPTOR_SUFFIX);
    }

    public static String encodeNamespace(ArrayList<String> namespaces) {
        StringBuilder sb = new StringBuilder(NAMESPACE_PREFIX_DEFAULT);
        // the last entry is the remaining symbol
        int numNamespaces = namespaces.size() - 1;
        for (int i = 0; i < numNamespaces; i++) {
            String namespace = namespaces.get(i);
            if (namespace != null) {
                sb.append(namespace.length());
                sb.append(namespace);
            }
        }
        sb.append(namespaces.get(numNamespaces));
        return sb.toString();
    }

    public static String getSwiftTypeAccessorName(String[] namespaces) {
        StringBuilder sb = new StringBuilder(NAMESPACE_PREFIX_DEFAULT);
        for (String string : namespaces) {
            String namespace = string;
            if (namespace != null) {
                if (namespace.contains(".")) {
                    namespace = namespace.substring(0, namespace.indexOf("."));
                }
                sb.append(namespace.length());
                sb.append(namespace);
            }
        }
        sb.append(CLASS_SEPERATOR);
        sb.append(TYPE_METADATA_ACCESSOR_SUFFIX);
        return sb.toString();
    }
}
