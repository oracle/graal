/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.preinit;

import com.oracle.truffle.espresso.impl.ClassLoadingEnv;
import com.oracle.truffle.espresso.impl.ClassRegistry;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.shared.classfile.ClassfileParser;
import com.oracle.truffle.espresso.shared.classfile.ClassfileStream;
import com.oracle.truffle.espresso.shared.classfile.ParserException;
import com.oracle.truffle.espresso.shared.classfile.ParserKlass;
import com.oracle.truffle.espresso.shared.classfile.ParsingContext;
import com.oracle.truffle.espresso.shared.descriptors.Symbol;
import com.oracle.truffle.espresso.shared.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.shared.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.shared.descriptors.ValidationException;
import com.oracle.truffle.espresso.verifier.MethodVerifier;

public interface ParserKlassProvider {
    ParserKlass getParserKlass(ClassLoadingEnv env, StaticObject loader, Symbol<Type> typeOrNull, byte[] bytes, ClassRegistry.ClassDefinitionInfo info);

    default int getCachedParserKlassCount() {
        return 0;
    }

    static ParserKlass parseKlass(ClassRegistry.ClassDefinitionInfo info, ClassLoadingEnv env, StaticObject loader, Symbol<Type> typeOrNull, byte[] bytes) {
        boolean verifiable = MethodVerifier.needsVerify(env.getLanguage(), loader);
        boolean loaderIsBootOrPlatform = env.loaderIsBootOrPlatform(loader);
        Meta meta = env.getMeta();
        try {
            return ClassfileParser.parse(env.getParsingContext(), new ClassfileStream(bytes, null), verifiable, loaderIsBootOrPlatform, typeOrNull, info.isHidden, info.forceAllowVMAnnotations);
        } catch (ValidationException | ParserException.ClassFormatError validationOrBadFormat) {
            throw meta.throwExceptionWithMessage(meta.java_lang_ClassFormatError, validationOrBadFormat.getMessage());
        } catch (ParserException.UnsupportedClassVersionError unsupportedClassVersionError) {
            throw meta.throwExceptionWithMessage(meta.java_lang_UnsupportedClassVersionError, unsupportedClassVersionError.getMessage());
        } catch (ParserException.NoClassDefFoundError noClassDefFoundError) {
            throw meta.throwExceptionWithMessage(meta.java_lang_NoClassDefFoundError, noClassDefFoundError.getMessage());
        } catch (ParserException parserException) {
            throw EspressoError.shouldNotReachHere("Not a validation nor parser exception", parserException);
        }
    }

    static Symbol<Name> getClassName(Meta meta, ParsingContext parsingContext, byte[] bytes) {
        try {
            return ClassfileParser.getClassName(parsingContext, bytes);
        } catch (ValidationException | ParserException.ClassFormatError validationOrBadFormat) {
            throw meta.throwExceptionWithMessage(meta.java_lang_ClassFormatError, validationOrBadFormat.getMessage());
        } catch (ParserException.UnsupportedClassVersionError unsupportedClassVersionError) {
            throw meta.throwExceptionWithMessage(meta.java_lang_UnsupportedClassVersionError, unsupportedClassVersionError.getMessage());
        } catch (ParserException.NoClassDefFoundError noClassDefFoundError) {
            throw meta.throwExceptionWithMessage(meta.java_lang_NoClassDefFoundError, noClassDefFoundError.getMessage());
        } catch (ParserException parserException) {
            throw EspressoError.shouldNotReachHere("Not a validation nor parser exception", parserException);
        }
    }
}
