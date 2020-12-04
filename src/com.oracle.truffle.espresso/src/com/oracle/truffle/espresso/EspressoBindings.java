package com.oracle.truffle.espresso;

import javax.lang.model.SourceVersion;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.proxy.Proxy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneRules;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@ExportLibrary(InteropLibrary.class)
public final class EspressoBindings implements TruffleObject {

    final StaticObject loader;

    public EspressoBindings(StaticObject loader) {
        this.loader = loader;
    }

    @ExportLibrary(InteropLibrary.class)
    static final class EmptyKeysArray implements TruffleObject {

        static final TruffleObject INSTANCE = new EmptyKeysArray();

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        long getArraySize() {
            return 0;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isArrayElementReadable(@SuppressWarnings("unused") long index) {
            return false;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            throw InvalidArrayIndexException.create(index);
        }
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return EmptyKeysArray.INSTANCE;
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    boolean isMemberReadable(@SuppressWarnings("unused") String member) {
        return SourceVersion.isName(member);
    }

    @ExportMessage
    Object readMember(String member, @CachedLibrary("this.loader") InteropLibrary interop, @Cached BranchProfile error) throws UnsupportedMessageException, UnknownIdentifierException {
        if (!isMemberReadable(member)) {
            throw UnknownIdentifierException.create(member);
        }
        try {
            StaticObject clazz = (StaticObject) interop.invokeMember(loader, "loadClass:(Ljava/lang/String;)Ljava/lang/Class;", member);
            return clazz.getMirrorKlass();
        } catch (ArityException | UnsupportedTypeException e) {
            throw EspressoError.shouldNotReachHere(e);
        } catch (EspressoException e) {
            error.enter();
            throw UnknownIdentifierException.create(member, e);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isScope() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "espresso-system-classloader";
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return EspressoLanguage.class;
    }
}
