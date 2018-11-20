package com.oracle.truffle.api.library;

import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class Libraries {

    private Libraries() {
        // no instances
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    public static <T extends Library> T getUncachedDispatch(Class<T> libraryClass) {
        return ResolvedLibrary.resolve(libraryClass).getUncachedDispatch();
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    public static <T extends Library> T createCachedDispatch(Class<T> libraryClass, int limit) {
        return ResolvedLibrary.resolve(libraryClass).createCachedDispatch(limit);
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    public static <T extends Library> T getUncached(Class<T> libraryClass, Object receiver) {
        return ResolvedLibrary.resolve(libraryClass).getUncached(receiver);
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    public static <T extends Library> T createCached(Class<T> libraryClass, Object receiver) {
        return ResolvedLibrary.resolve(libraryClass).createCached(receiver);
    }

    @TruffleBoundary
    public static List<Message> getMessages(Class<? extends Library> libraryClass) {
        return ResolvedLibrary.resolve(libraryClass).getMessages();
    }

}
