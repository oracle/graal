/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.interop;

final class AssertUtils {

    private AssertUtils() {
    }

    private static String formatArgs(Object[] args) {
        if (args == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("[");
        String sep = "";
        for (Object arg : args) {
            b.append(sep);
            b.append(formatValue(arg));
            sep = ", ";
        }
        b.append("]");
        return b.toString();
    }

    private static String formatValue(Object arg) {
        if (arg == null) {
            return "null";
        } else if (arg instanceof TruffleObject) {
            return arg.toString() + "(" + arg.getClass().getName() + ")";
        } else if (arg instanceof String) {
            return "\"" + arg.toString() + "\"";
        } else if (arg instanceof Character) {
            return "'" + arg.toString() + "'";
        } else if (arg instanceof Boolean) {
            return arg.toString();
        } else {
            return arg.toString() + "(" + arg.getClass().getSimpleName() + ")";
        }
    }

    private static String violationPre(Object receiver) {
        return String.format("Pre-condition contract violation for receiver %s.",
                        formatValue(receiver));
    }

    static String violationPost(Object receiver, Object returnValue) {
        return String.format("Post-condition contract violation for receiver %s and return value %s.",
                        formatValue(receiver), formatValue(returnValue));
    }

    static String violationPost(Object receiver, Throwable t) {
        return String.format("Post-condition contract violation for receiver %s. Thrown unexpected error %s.",
                        formatValue(receiver), t.getClass().getName());
    }

    static String violationInvariant(Object receiver) {
        return String.format("Invariant contract violation for receiver %s.", formatValue(receiver));
    }

    static String violationInvariant(Object receiver, Object[] args) {
        return String.format("Invariant contract violation for receiver %s and arguments %s.", formatValue(receiver), formatArgs(args));
    }

    static String violationInvariant(Object receiver, String arg) {
        return String.format("Invariant contract violation for receiver %s and identifier %s.", formatValue(receiver), arg);
    }

    static String violationInvariant(Object receiver, long arg) {
        return String.format("Invariant contract violation for receiver %s and index %s.", formatValue(receiver), arg);
    }

    static String violationInvariant(Object receiver, Object arg) {
        return String.format("Invariant contract violation for receiver %s and key %s.", formatValue(receiver), formatValue(arg));
    }

    private static String violationReturn(Object receiver, Object returnValue) {
        return String.format("Post-condition contract violation for receiver %s and return value %s.",
                        formatValue(receiver), formatValue(returnValue));
    }

    private static String violationArgument(Object receiver, Object arg) {
        return String.format("Pre-condition contract violation for receiver %s and argument %s. " +
                        "Valid arguments must be of type Boolean, Byte, Short, Integer, Long, Float, Double, Character, String or implement TruffleObject.",
                        formatValue(receiver), formatValue(arg));
    }

    static boolean validInteropReturn(Object receiver, Object arg) {
        assert InteropLibrary.isValidValue(arg) : violationReturn(receiver, arg);
        return true;
    }

    static boolean validProtocolReturn(Object receiver, Object arg) {
        assert InteropLibrary.isValidProtocolValue(arg) : violationReturn(receiver, arg);
        return true;
    }

    static boolean validInteropArgument(Object receiver, Object arg) {
        if (arg == null) {
            throw new NullPointerException(violationArgument(receiver, arg));
        }
        if (!InteropLibrary.isValidValue(arg)) {
            throw new ClassCastException(violationArgument(receiver, arg));
        }
        return true;
    }

    static boolean validProtocolArgument(Object receiver, Object arg) {
        if (arg == null) {
            throw new NullPointerException(violationArgument(receiver, arg));
        }
        if (!InteropLibrary.isValidProtocolValue(arg)) {
            throw new ClassCastException(violationArgument(receiver, arg));
        }
        return true;
    }

    static boolean assertString(Object receiver, Object string) {
        InteropLibrary uncached = InteropLibrary.getUncached(string);
        assert uncached.isString(string) : violationPost(receiver, string);
        try {
            assert uncached.asString(string) != null : violationPost(receiver, string);
        } catch (UnsupportedMessageException e) {
            assert false; // should be handled by uncached assertions
        }
        return true;
    }

    static boolean validNonInteropArgument(Object receiver, Object arg) {
        if (arg == null) {
            throw new NullPointerException(violationNonInteropArgument(receiver, arg));
        }
        return true;
    }

    private static String violationNonInteropArgument(Object receiver, Object arg) {
        return String.format("Pre-condition contract violation for receiver %s and argument %s. " +
                        "Argument must not be null.",
                        formatValue(receiver), formatValue(arg));
    }

    static boolean validArguments(Object receiver, Object[] args) {
        assert args != null : violationPre(receiver);
        for (Object arg : args) {
            assert validInteropArgument(receiver, arg);
        }
        return true;
    }

    static boolean validScope(Object o) {
        if (!(o instanceof TruffleObject)) {
            return false;
        }
        InteropLibrary uncached = InteropLibrary.getUncached(o);
        assert uncached.isScope(o) : String.format("Invariant contract violation for receiver %s: is not a scope.", formatValue(o));
        assert uncached.hasMembers(o) : String.format("Invariant contract violation for receiver %s: does not have members.", formatValue(o));
        return true;
    }

    static String violationScopeMemberLengths(Object allMembers, Object parentMembers) {
        return String.format("Scope members of %s do not contain all scope parent members of %s", allMembers, parentMembers);
    }

    static boolean validScopeMemberLengths(long allSize, long parentSize, Object allMembers, Object parentMembers) {
        assert allSize >= parentSize : String.format("Scope members of %s (count = %d) do not contain all scope parent members of %s (count = %d)", allMembers, allSize, parentMembers, parentSize);
        return allSize >= parentSize;
    }

    static boolean validScopeMemberNames(String allElementName, String parentElementName, Object allMembers, Object parentMembers, long allIndex, long parentIndex) {
        assert allElementName.equals(parentElementName) : String.format(
                        "Member %s of scope %s at [%d] does not equal to member %s of parent scope %s at [%d]. Scope must contain all members from parent scopes.",
                        allElementName, allMembers, allIndex, parentElementName, parentMembers, parentIndex);
        return allElementName.equals(parentElementName);
    }

    static boolean preCondition(Object receiver) {
        if (receiver == null) {
            throw new NullPointerException(violationPre(receiver));
        }
        return true;
    }

}
