package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.descriptors.Names;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.perf.TimerCollection;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.JavaVersion;
import com.oracle.truffle.espresso.runtime.StaticObject;
import org.graalvm.options.OptionKey;

public interface ClassLoadingEnv {

    EspressoLanguage getLanguage();
    Names getNames();
    Types getTypes();
    TimerCollection getTimers();
    TruffleLogger getLogger();
    JavaVersion getJavaVersion();
    OptionKey<EspressoOptions.SpecCompliancyMode> getSpecCompliancyMode();
    boolean needsVerify(StaticObject loader);

    abstract class CommonEnv implements ClassLoadingEnv {
        private final EspressoLanguage language;

        public CommonEnv(EspressoLanguage lang) {
            language = lang;
        }

        @Override
        public EspressoLanguage getLanguage() {
            return language;
        }

        @Override
        public Names getNames() {
            return language.getNames();
        }

        @Override
        public Types getTypes() {
            return language.getTypes();
        }

        @Override
        public OptionKey<EspressoOptions.SpecCompliancyMode> getSpecCompliancyMode() {
            return EspressoOptions.SpecCompliancy;
        }
    }

    class InContext extends CommonEnv {
        private final EspressoContext context;

        public InContext(EspressoContext ctx) {
            super(ctx.getLanguage());
            context = ctx;
        }

        @Override
        public TimerCollection getTimers() {
            return context.getTimers();
        }

        @Override
        public TruffleLogger getLogger() {
            return context.getLogger();
        }

        @Override
        public JavaVersion getJavaVersion() {
            return context.getJavaVersion();
        }

        @Override
        public boolean needsVerify(StaticObject loader) {
            return context.needsVerify(loader);
        }
    }

    class WithoutContext extends CommonEnv {
        private final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID);
        private final JavaVersion javaVersion;

        public WithoutContext(EspressoLanguage language, JavaVersion version) {
            super(language);
            javaVersion = version;
        }

        @Override
        public TimerCollection getTimers() {
            return TimerCollection.create(false);
        }

        @Override
        public TruffleLogger getLogger() {
            return logger;
        }

        @Override
        public JavaVersion getJavaVersion() {
            return javaVersion;
        }

        @Override
        public boolean needsVerify(StaticObject loader) {
            EspressoOptions.VerifyMode defaultVerifyMode = EspressoOptions.Verify.getDefaultValue();
            return defaultVerifyMode != EspressoOptions.VerifyMode.NONE;
        }
    }
}
