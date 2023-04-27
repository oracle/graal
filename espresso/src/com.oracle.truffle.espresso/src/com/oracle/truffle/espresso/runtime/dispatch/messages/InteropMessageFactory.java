package com.oracle.truffle.espresso.runtime.dispatch.messages;

import java.util.Objects;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.espresso.EspressoLanguage;

public final class InteropMessageFactory {
    private static final class Key {
        private final Class<?> cls;
        private final String message;

        public Key(Class<?> cls, String message) {
            this.cls = cls;
            this.message = message;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Key) {
                Key other = (Key) obj;
                return other.cls.equals(this.cls) && other.message.equals(this.message);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(cls, message);
        }
    }

    private InteropMessageFactory() {
    }

    private static final EconomicMap<Key, Supplier<InteropMessage>> messageMap = EconomicMap.create();

    public static void register(Class<?> cls, String message, Supplier<InteropMessage> factory) {
        assert cls != null;
        assert message != null;
        assert factory != null;
        messageMap.put(new Key(cls, message), factory);
    }

    public static Supplier<CallTarget> getFactory(EspressoLanguage lang, Class<?> cls, String message) {
        return () -> {
            Supplier<InteropMessage> factory = messageMap.get(new Key(cls, message));
            if (factory == null) {
                return null;
            }
            InteropMessage interopMessage = factory.get();
            return new InteropMessageRootNode(lang, interopMessage).getCallTarget();
        };
    }
}
