/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm;

import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;

import java.util.ArrayList;
import java.util.Base64;

final class CXXDemangler {
    private static final String NAMESPACE_PREFIX = "_ZN";
    private static final int NAMESPACE_PREFIX_LEN = NAMESPACE_PREFIX.length();
    private static final String SULONG_NAMESPACE_PREFIX = Runner.SULONG_RENAME_MARKER;
    private static final String SULONG_BASE64_NAMESPACE_SUFFIX = "base64";
    private static final String SULONG_BASE64_NAMESPACE = SULONG_NAMESPACE_PREFIX + SULONG_BASE64_NAMESPACE_SUFFIX;

    private int idx;
    private final String name;

    private CXXDemangler(String name) {
        if (!name.startsWith(NAMESPACE_PREFIX)) {
            throw new LLVMLinkerException("Not a mangled namespace: " + name);
        }
        this.idx = NAMESPACE_PREFIX_LEN;
        this.name = name;
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
     * Mangled C++ symbols. To encode the symbols, we use a special namespace (`__sulong_import` or
     * `__sulong_import_base64`) to indicate symbols the are subject to renaming. The library name
     * that includes the target function is encoded in yet another namespace. If the
     * `__sulong_import_base64` namespace is used, the library name is encoded as a base64 encoded
     * string. Example:
     *
     * <pre>
     namespace __cxxabiv1 {
     namespace __sulong_import_base64 { // special __sulong_import namespace
     namespace bGliYysrYWJpLnNv { // libname in base64 (libc++abi.so)
    
     // the function declaration that will be aliased
     static __cxa_exception* cxa_exception_from_exception_unwind_exception(_Unwind_Exception* unwind_exception);
    
     } // end libc++.so
     } // end __sulong_import
    
     ...
    
     // usage of the declared function
     __cxa_exception *ex = __sulong_import_base64::bGliYysrYWJpLnNv::cxa_exception_from_exception_unwind_exception(unwindHeader);
    
     } // end __cxxabiv1
     * </pre>
     */
    static boolean isRenamedNamespaceSymbol(String name) {
        return name.startsWith("_ZN") && name.contains(CXXDemangler.SULONG_NAMESPACE_PREFIX);
    }

    /**
     * Decodes a mangled C++ name into a list of namespaces. The last entry is the (mangled) symbol
     * name (global or function).
     *
     * @throws LLVMLinkerException if the given name cannot be decoded.
     */
    static ArrayList<String> decodeNamespace(String name) {
        return new CXXDemangler(name).decode();
    }

    /**
     * Returns the library name which is encoded as a namespace and removes it as well as the marker
     * namespace by setting it to null.
     */
    static String getAndRemoveLibraryName(ArrayList<String> namespaces) {
        // the last entry is the remaining symbol
        int numNamespaces = namespaces.size() - 1;
        for (int i = 0; i < numNamespaces; i++) {
            String namespace = namespaces.get(i);
            if (namespace.startsWith(SULONG_NAMESPACE_PREFIX)) {
                int libIdx = i + 1;
                if (libIdx >= numNamespaces) {
                    throw new LLVMLinkerException(String.format("No library name to decode: ", String.join("::", namespaces)));
                }
                String rawLibname = namespaces.get(libIdx);
                // remove marker namespaces
                namespaces.set(i, null);
                namespaces.set(libIdx, null);
                if (namespace.equals(SULONG_NAMESPACE_PREFIX)) {
                    return rawLibname;
                }
                if (namespace.equals(SULONG_BASE64_NAMESPACE)) {
                    return new String(decodeBase64(rawLibname));
                }
            }
        }
        return null;
    }

    static byte[] decodeBase64(CharSequence charSequence) {
        byte[] result = new byte[charSequence.length()];
        for (int i = 0; i < result.length; i++) {
            char ch = charSequence.charAt(i);
            assert ch >= 0 && ch <= Byte.MAX_VALUE;
            result[i] = (byte) ch;
        }
        return Base64.getDecoder().decode(result);
    }

    static String encodeNamespace(ArrayList<String> namespaces) {
        StringBuilder sb = new StringBuilder();
        sb.append(NAMESPACE_PREFIX);
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

}
