/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.core;

import java.io.*;
import java.math.*;
import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.array.*;
import com.oracle.truffle.ruby.runtime.objects.*;

public class CoreLibrary {

    public static final String RUBY_VERSION = "2.1.0";

    private final RubyContext context;

    private RubyClass argumentErrorClass;
    private RubyClass arrayClass;
    private RubyClass basicObjectClass;
    private RubyClass bignumClass;
    private RubyClass bindingClass;
    private RubyClass classClass;
    private RubyClass continuationClass;
    private RubyClass dirClass;
    private RubyClass exceptionClass;
    private RubyClass falseClass;
    private RubyClass fiberClass;
    private RubyClass fileClass;
    private RubyClass fixnumClass;
    private RubyClass floatClass;
    private RubyClass hashClass;
    private RubyClass integerClass;
    private RubyClass ioClass;
    private RubyClass loadErrorClass;
    private RubyClass localJumpErrorClass;
    private RubyClass matchDataClass;
    private RubyClass moduleClass;
    private RubyClass nameErrorClass;
    private RubyClass nilClass;
    private RubyClass noMethodErrorClass;
    private RubyClass numericClass;
    private RubyClass objectClass;
    private RubyClass procClass;
    private RubyClass processClass;
    private RubyClass rangeClass;
    private RubyClass rangeErrorClass;
    private RubyClass regexpClass;
    private RubyClass rubyTruffleErrorClass;
    private RubyClass runtimeErrorClass;
    private RubyClass standardErrorClass;
    private RubyClass stringClass;
    private RubyClass structClass;
    private RubyClass symbolClass;
    private RubyClass syntaxErrorClass;
    private RubyClass systemCallErrorClass;
    private RubyClass systemExitClass;
    private RubyClass threadClass;
    private RubyClass timeClass;
    private RubyClass trueClass;
    private RubyClass typeErrorClass;
    private RubyClass zeroDivisionErrorClass;

    private RubyModule comparableModule;
    private RubyModule configModule;
    private RubyModule errnoModule;
    private RubyModule kernelModule;
    private RubyModule mathModule;
    private RubyModule objectSpaceModule;
    private RubyModule signalModule;

    private RubyModule debugModule;
    private RubyArray argv;
    private RubyBasicObject globalVariablesObject;
    private RubyBasicObject mainObject;
    private RubyFalseClass falseObject;
    private RubyNilClass nilObject;
    private RubyTrueClass trueObject;

    public CoreLibrary(RubyContext context) {
        this.context = context;
    }

    public void initialize() {
        // Create the cyclic classes and modules

        classClass = new RubyClass.RubyClassClass(context);
        basicObjectClass = new RubyClass(context, classClass, null, null, "BasicObject");
        objectClass = new RubyClass(null, basicObjectClass, "Object");
        moduleClass = new RubyModule.RubyModuleClass(context);

        // Close the cycles

        moduleClass.unsafeSetRubyClass(classClass);
        classClass.unsafeSetSuperclass(moduleClass);
        moduleClass.unsafeSetSuperclass(objectClass);
        classClass.unsafeSetRubyClass(classClass);

        // Create all other classes and modules

        numericClass = new RubyClass(null, objectClass, "Numeric");
        integerClass = new RubyClass(null, numericClass, "Integer");

        exceptionClass = new RubyException.RubyExceptionClass(objectClass, "Exception");
        standardErrorClass = new RubyException.RubyExceptionClass(exceptionClass, "StandardError");

        ioClass = new RubyClass(null, objectClass, "IO");

        argumentErrorClass = new RubyException.RubyExceptionClass(standardErrorClass, "ArgumentError");
        arrayClass = new RubyArray.RubyArrayClass(objectClass);
        bignumClass = new RubyClass(null, integerClass, "Bignum");
        bindingClass = new RubyClass(null, objectClass, "Binding");
        continuationClass = new RubyClass(null, objectClass, "Continuation");
        comparableModule = new RubyModule(moduleClass, null, "Comparable");
        configModule = new RubyModule(moduleClass, null, "Config");
        debugModule = new RubyModule(moduleClass, null, "Debug");
        dirClass = new RubyClass(null, objectClass, "Dir");
        errnoModule = new RubyModule(moduleClass, null, "Errno");
        falseClass = new RubyClass(null, objectClass, "FalseClass");
        fiberClass = new RubyFiber.RubyFiberClass(objectClass);
        fileClass = new RubyClass(null, ioClass, "File");
        fixnumClass = new RubyClass(null, integerClass, "Fixnum");
        floatClass = new RubyClass(null, objectClass, "Float");
        hashClass = new RubyHash.RubyHashClass(objectClass);
        kernelModule = new RubyModule(moduleClass, null, "Kernel");
        loadErrorClass = new RubyException.RubyExceptionClass(standardErrorClass, "LoadError");
        localJumpErrorClass = new RubyException.RubyExceptionClass(standardErrorClass, "LocalJumpError");
        matchDataClass = new RubyClass(null, objectClass, "MatchData");
        mathModule = new RubyModule(moduleClass, null, "Math");
        nameErrorClass = new RubyClass(null, standardErrorClass, "NameError");
        nilClass = new RubyClass(null, objectClass, "NilClass");
        noMethodErrorClass = new RubyException.RubyExceptionClass(standardErrorClass, "NoMethodError");
        objectSpaceModule = new RubyModule(moduleClass, null, "ObjectSpace");
        procClass = new RubyProc.RubyProcClass(objectClass);
        processClass = new RubyClass(null, objectClass, "Process");
        rangeClass = new RubyClass(null, objectClass, "Range");
        rangeErrorClass = new RubyException.RubyExceptionClass(standardErrorClass, "RangeError");
        regexpClass = new RubyRegexp.RubyRegexpClass(objectClass);
        rubyTruffleErrorClass = new RubyException.RubyExceptionClass(standardErrorClass, "RubyTruffleError");
        runtimeErrorClass = new RubyException.RubyExceptionClass(standardErrorClass, "RuntimeError");
        stringClass = new RubyString.RubyStringClass(objectClass);
        structClass = new RubyClass(null, ioClass, "Struct");
        signalModule = new RubyModule(moduleClass, null, "Signal");
        symbolClass = new RubyClass(null, objectClass, "Symbol");
        syntaxErrorClass = new RubyException.RubyExceptionClass(standardErrorClass, "SyntaxError");
        systemCallErrorClass = new RubyException.RubyExceptionClass(standardErrorClass, "SystemCallError");
        systemExitClass = new RubyException.RubyExceptionClass(exceptionClass, "SystemExit");
        threadClass = new RubyThread.RubyThreadClass(objectClass);
        timeClass = new RubyTime.RubyTimeClass(objectClass);
        trueClass = new RubyClass(null, objectClass, "TrueClass");
        typeErrorClass = new RubyException.RubyExceptionClass(standardErrorClass, "TypeError");
        zeroDivisionErrorClass = new RubyException.RubyExceptionClass(standardErrorClass, "ZeroDivisionError");

        // Includes

        objectClass.include(kernelModule);

        // Set constants

        objectClass.setConstant("RUBY_VERSION", new RubyString(stringClass, RUBY_VERSION));
        objectClass.setConstant("RUBY_PATCHLEVEL", 0);
        objectClass.setConstant("RUBY_ENGINE", new RubyString(stringClass, "rubytruffle"));
        objectClass.setConstant("RUBY_PLATFORM", new RubyString(stringClass, "jvm"));

        argv = new RubyArray(arrayClass, new ObjectArrayStore());
        objectClass.setConstant("ARGV", argv);
        objectClass.setConstant("ENV", getEnv());

        final RubyHash configHash = new RubyHash(hashClass);
        configHash.put(new RubyString(stringClass, "ruby_install_name"), new RubyString(stringClass, "rubytruffle"));
        configHash.put(new RubyString(stringClass, "RUBY_INSTALL_NAME"), new RubyString(stringClass, "rubytruffle"));
        configHash.put(new RubyString(stringClass, "host_os"), new RubyString(stringClass, "unknown"));
        configHash.put(new RubyString(stringClass, "exeext"), new RubyString(stringClass, ""));
        configHash.put(new RubyString(stringClass, "EXEEXT"), new RubyString(stringClass, "rubytruffle"));
        configModule.setConstant("CONFIG", configHash);
        objectClass.setConstant("RbConfig", configModule);

        mathModule.setConstant("PI", Math.PI);

        fileClass.setConstant("SEPARATOR", new RubyString(stringClass, File.separator));
        fileClass.setConstant("Separator", new RubyString(stringClass, File.separator));
        fileClass.setConstant("ALT_SEPARATOR", NilPlaceholder.INSTANCE);
        fileClass.setConstant("PATH_SEPARATOR", new RubyString(stringClass, File.pathSeparator));
        fileClass.setConstant("FNM_SYSCASE", 0);

        errnoModule.setConstant("ENOENT", new RubyClass(null, systemCallErrorClass, "ENOENT"));
        errnoModule.setConstant("EPERM", new RubyClass(null, systemCallErrorClass, "EPERM"));
        errnoModule.setConstant("ENOTEMPTY", new RubyClass(null, systemCallErrorClass, "ENOTEMPTY"));
        errnoModule.setConstant("EEXIST", new RubyClass(null, systemCallErrorClass, "EEXIST"));
        errnoModule.setConstant("EXDEV", new RubyClass(null, systemCallErrorClass, "EXDEV"));
        errnoModule.setConstant("EACCES", new RubyClass(null, systemCallErrorClass, "EACCES"));

        // Add all classes and modules as constants in Object

        final RubyModule[] modules = {argumentErrorClass, //
                        arrayClass, //
                        basicObjectClass, //
                        bignumClass, //
                        bindingClass, //
                        classClass, //
                        continuationClass, //
                        comparableModule, //
                        configModule, //
                        debugModule, //
                        dirClass, //
                        errnoModule, //
                        exceptionClass, //
                        falseClass, //
                        fiberClass, //
                        fileClass, //
                        fixnumClass, //
                        floatClass, //
                        hashClass, //
                        integerClass, //
                        ioClass, //
                        kernelModule, //
                        loadErrorClass, //
                        localJumpErrorClass, //
                        matchDataClass, //
                        mathModule, //
                        moduleClass, //
                        nameErrorClass, //
                        nilClass, //
                        noMethodErrorClass, //
                        numericClass, //
                        objectClass, //
                        objectSpaceModule, //
                        procClass, //
                        processClass, //
                        rangeClass, //
                        rangeErrorClass, //
                        regexpClass, //
                        rubyTruffleErrorClass, //
                        runtimeErrorClass, //
                        signalModule, //
                        standardErrorClass, //
                        stringClass, //
                        structClass, //
                        symbolClass, //
                        syntaxErrorClass, //
                        systemCallErrorClass, //
                        systemExitClass, //
                        threadClass, //
                        timeClass, //
                        trueClass, //
                        typeErrorClass, //
                        zeroDivisionErrorClass};

        for (RubyModule module : modules) {
            objectClass.setConstant(module.getName(), module);
        }

        // Create some key objects

        mainObject = new RubyObject(objectClass);
        nilObject = new RubyNilClass(nilClass);
        trueObject = new RubyTrueClass(trueClass);
        falseObject = new RubyFalseClass(falseClass);

        // Create the globals object

        globalVariablesObject = new RubyBasicObject(objectClass);
        globalVariablesObject.switchToPrivateLayout();
        globalVariablesObject.setInstanceVariable("$:", new RubyArray(arrayClass, new ObjectArrayStore()));
    }

    public void initializeAfterMethodsAdded() {
        bignumClass.getSingletonClass().undefMethod("new");
        falseClass.getSingletonClass().undefMethod("new");
        fixnumClass.getSingletonClass().undefMethod("new");
        floatClass.getSingletonClass().undefMethod("new");
        integerClass.getSingletonClass().undefMethod("new");
        nilClass.getSingletonClass().undefMethod("new");
        numericClass.getSingletonClass().undefMethod("new");
        trueClass.getSingletonClass().undefMethod("new");
    }

    public RubyBasicObject box(Object object) {
        assert RubyContext.shouldObjectBeVisible(object);

        // TODO(cs): pool common object instances like small Fixnums?

        if (object instanceof RubyBasicObject) {
            return (RubyBasicObject) object;
        }

        if (object instanceof Boolean) {
            if ((boolean) object) {
                return trueObject;
            } else {
                return falseObject;
            }
        }

        if (object instanceof Integer) {
            return new RubyFixnum(fixnumClass, (int) object);
        }

        if (object instanceof BigInteger) {
            return new RubyBignum(bignumClass, (BigInteger) object);
        }

        if (object instanceof Double) {
            return new RubyFloat(floatClass, (double) object);
        }

        if (object instanceof NilPlaceholder) {
            return nilObject;
        }

        CompilerDirectives.transferToInterpreter();

        throw new UnsupportedOperationException("Don't know how to box " + object.getClass().getName());
    }

    public RubyException runtimeError(String message) {
        return new RubyException(runtimeErrorClass, message);
    }

    public RubyException frozenError(String className) {
        return runtimeError(String.format("can't modify frozen %s", className));
    }

    public RubyException argumentError(String message) {
        return new RubyException(argumentErrorClass, message);
    }

    public RubyException argumentError(int passed, int required) {
        return argumentError(String.format("wrong number of arguments (%d for %d)", passed, required));
    }

    public RubyException argumentErrorUncaughtThrow(Object tag) {
        return argumentError(String.format("uncaught throw `%s'", tag));
    }

    public RubyException localJumpError(String message) {
        return new RubyException(localJumpErrorClass, message);
    }

    public RubyException unexpectedReturn() {
        return localJumpError("unexpected return");
    }

    public RubyException typeError(String message) {
        return new RubyException(typeErrorClass, message);
    }

    public RubyException typeError(String from, String to) {
        return typeError(String.format("can't convert %s to %s", from, to));
    }

    public RubyException typeErrorIsNotA(String value, String expectedType) {
        return typeError(String.format("%s is not a %s", value, expectedType));
    }

    public RubyException typeErrorNeedsToBe(String name, String expectedType) {
        return typeError(String.format("%s needs to be %s", name, expectedType));
    }

    public RubyException rangeError(String message) {
        return new RubyException(rangeErrorClass, message);
    }

    public RubyException nameError(String message) {
        return new RubyException(nameErrorClass, message);
    }

    public RubyException nameErrorUninitializedConstant(String name) {
        return nameError(String.format("uninitialized constant %s", name));
    }

    public RubyException nameErrorNoMethod(String name, String object) {
        return nameError(String.format("undefined local variable or method `%s' for %s", name, object));
    }

    public RubyException nameErrorInstanceNameNotAllowable(String name) {
        return nameError(String.format("`%s' is not allowable as an instance variable name", name));
    }

    public RubyException nameErrorUncaughtThrow(Object tag) {
        return nameError(String.format("uncaught throw `%s'", tag));
    }

    public RubyException noMethodError(String message) {
        return new RubyException(context.getCoreLibrary().getNoMethodErrorClass(), message);
    }

    public RubyException noMethodError(String name, String object) {
        return noMethodError(String.format("undefined method `%s' for %s", name, object));
    }

    public RubyException loadError(String message) {
        return new RubyException(context.getCoreLibrary().getLoadErrorClass(), message);
    }

    public RubyException loadErrorCannotLoad(String name) {
        return loadError(String.format("cannot load such file -- %s", name));
    }

    public RubyException zeroDivisionError() {
        return new RubyException(context.getCoreLibrary().getZeroDivisionErrorClass(), "divided by 0");
    }

    public RubyContext getContext() {
        return context;
    }

    public RubyClass getArgumentErrorClass() {
        return argumentErrorClass;
    }

    public RubyClass getArrayClass() {
        return arrayClass;
    }

    public RubyClass getBasicObjectClass() {
        return basicObjectClass;
    }

    public RubyClass getBignumClass() {
        return bignumClass;
    }

    public RubyClass getBindingClass() {
        return bindingClass;
    }

    public RubyClass getClassClass() {
        return classClass;
    }

    public RubyModule getComparableClass() {
        return comparableModule;
    }

    public RubyClass getContinuationClass() {
        return continuationClass;
    }

    public RubyClass getDirClass() {
        return dirClass;
    }

    public RubyClass getExceptionClass() {
        return exceptionClass;
    }

    public RubyClass getFalseClass() {
        return falseClass;
    }

    public RubyClass getFiberClass() {
        return fiberClass;
    }

    public RubyClass getFileClass() {
        return fileClass;
    }

    public RubyClass getFixnumClass() {
        return fixnumClass;
    }

    public RubyClass getFloatClass() {
        return floatClass;
    }

    public RubyClass getHashClass() {
        return hashClass;
    }

    public RubyClass getIntegerClass() {
        return integerClass;
    }

    public RubyClass getIoClass() {
        return ioClass;
    }

    public RubyClass getLoadErrorClass() {
        return loadErrorClass;
    }

    public RubyClass getLocalJumpErrorClass() {
        return localJumpErrorClass;
    }

    public RubyClass getMatchDataClass() {
        return matchDataClass;
    }

    public RubyClass getModuleClass() {
        return moduleClass;
    }

    public RubyClass getNameErrorClass() {
        return nameErrorClass;
    }

    public RubyClass getNilClass() {
        return nilClass;
    }

    public RubyClass getNoMethodErrorClass() {
        return noMethodErrorClass;
    }

    public RubyClass getNumericClass() {
        return numericClass;
    }

    public RubyClass getObjectClass() {
        return objectClass;
    }

    public RubyClass getProcClass() {
        return procClass;
    }

    public RubyClass getProcessClass() {
        return processClass;
    }

    public RubyClass getRangeClass() {
        return rangeClass;
    }

    public RubyClass getRangeErrorClass() {
        return rangeErrorClass;
    }

    public RubyClass getRegexpClass() {
        return regexpClass;
    }

    public RubyClass getRubyTruffleErrorClass() {
        return rubyTruffleErrorClass;
    }

    public RubyClass getRuntimeErrorClass() {
        return runtimeErrorClass;
    }

    public RubyModule getSignalModule() {
        return signalModule;
    }

    public RubyClass getStandardErrorClass() {
        return standardErrorClass;
    }

    public RubyClass getStringClass() {
        return stringClass;
    }

    public RubyClass getStructClass() {
        return structClass;
    }

    public RubyClass getSymbolClass() {
        return symbolClass;
    }

    public RubyClass getSyntaxErrorClass() {
        return syntaxErrorClass;
    }

    public RubyClass getSystemCallErrorClass() {
        return systemCallErrorClass;
    }

    public RubyClass getThreadClass() {
        return threadClass;
    }

    public RubyClass getTimeClass() {
        return timeClass;
    }

    public RubyClass getTrueClass() {
        return trueClass;
    }

    public RubyClass getTypeErrorClass() {
        return typeErrorClass;
    }

    public RubyClass getZeroDivisionErrorClass() {
        return zeroDivisionErrorClass;
    }

    public RubyModule getKernelModule() {
        return kernelModule;
    }

    public RubyModule getMathModule() {
        return mathModule;
    }

    public RubyModule getObjectSpaceModule() {
        return objectSpaceModule;
    }

    public RubyModule getDebugModule() {
        return debugModule;
    }

    public RubyArray getArgv() {
        return argv;
    }

    public RubyBasicObject getGlobalVariablesObject() {
        return globalVariablesObject;
    }

    public RubyBasicObject getMainObject() {
        return mainObject;
    }

    public RubyFalseClass getFalseObject() {
        return falseObject;
    }

    public RubyNilClass getNilObject() {
        return nilObject;
    }

    public RubyTrueClass getTrueObject() {
        return trueObject;
    }

    public RubyHash getEnv() {
        final RubyHash hash = new RubyHash(context.getCoreLibrary().getHashClass());

        for (Map.Entry<String, String> variable : System.getenv().entrySet()) {
            hash.put(context.makeString(variable.getKey()), context.makeString(variable.getValue()));
        }

        return hash;
    }

}
