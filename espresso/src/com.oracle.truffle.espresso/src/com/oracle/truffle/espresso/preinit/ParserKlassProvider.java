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

import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.classfile.ParserException;
import com.oracle.truffle.espresso.classfile.ParserKlass;
import com.oracle.truffle.espresso.classfile.ParsingContext;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.ValidationException;
import com.oracle.truffle.espresso.impl.ClassLoadingEnv;
import com.oracle.truffle.espresso.impl.ClassRegistry;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoVerifier;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public interface ParserKlassProvider {
    ParserKlass getParserKlass(ClassLoadingEnv env, StaticObject loader, Symbol<Type> typeOrNull, byte[] bytes, ClassRegistry.ClassDefinitionInfo info);

    default int getCachedParserKlassCount() {
        return 0;
    }

    /**
     * Like {@link #parseKlass} but throws host exceptions that must be caught an handled by the
     * called.
     * 
     * @throws ValidationException a descriptor validation caused a class format error.
     * @throws ParserException various exceptions, see subclasses.
     */
    static ParserKlass parseKlassWithHostErrors(ClassRegistry.ClassDefinitionInfo info, ClassLoadingEnv env, StaticObject loader, Symbol<Type> typeOrNull, byte[] bytes) throws ValidationException {
        boolean verifiable = EspressoVerifier.needsVerify(env.getLanguage(), loader);
        boolean loaderIsBootOrPlatform = env.loaderIsBootOrPlatform(loader);
        // Classes from trusted class loaders can create strongly referenced symbols directly
        // during parsing.
        boolean ensureStrongSymbols = env.loaderIsBootOrPlatform(loader) || env.loaderIsAppLoader(loader);
        ParsingContext parsingContext = ClassLoadingEnv.createParsingContext(env, ensureStrongSymbols);

        // Trusted classes do not need validation/verification.
        boolean validate = verifiable;
        ParserKlass parserKlass = ClassfileParser.parse(parsingContext, new ClassfileStream(bytes, null), verifiable, loaderIsBootOrPlatform, typeOrNull, info.isHidden,
                        info.forceAllowVMAnnotations, validate);
        if (info.isHidden) {
            Symbol<Type> requestedClassType = typeOrNull;
            assert requestedClassType != null;
            long hiddenKlassId = env.getNewKlassId();
            Symbol<Name> thisKlassName = parsingContext.getOrCreateName(TypeSymbols.hiddenClassName(requestedClassType, hiddenKlassId));
            Symbol<Type> thisKlassType = parsingContext.getOrCreateTypeFromName(thisKlassName);
            var pool = parserKlass.getConstantPool().patchForHiddenClass(parserKlass.getThisKlassIndex(), thisKlassName);
            var classFlags = parserKlass.getFlags() | Constants.ACC_IS_HIDDEN_CLASS;
            return new ParserKlass(pool, classFlags, thisKlassName, thisKlassType,
                            parserKlass.getSuperKlass(), parserKlass.getSuperInterfaces(),
                            parserKlass.getMethods(), parserKlass.getFields(),
                            parserKlass.getAttributes(),
                            parserKlass.getThisKlassIndex(),
                            parserKlass.getMajorVersion(), parserKlass.getMinorVersion(),
                            parserKlass.getHiddenKlassId());
        }

        return parserKlass;
    }

    static ParserKlass parseKlass(ClassRegistry.ClassDefinitionInfo info, ClassLoadingEnv env, StaticObject loader, Symbol<Type> typeOrNull, byte[] bytes) {
        Meta meta = env.getMeta();
        try {
            return parseKlassWithHostErrors(info, env, loader, typeOrNull, bytes);
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
            throw EspressoError.shouldNotReachHere("Not a validation or known parser exception", parserException);
        }
    }
}
