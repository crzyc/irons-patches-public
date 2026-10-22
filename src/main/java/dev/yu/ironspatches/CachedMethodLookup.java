package dev.yu.ironspatches;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-class cache for no-arg {@link Class#getMethod} lookups. A miss rethrows one shared stackless
 * NoSuchMethodException, so callers that catch ReflectiveOperationException behave exactly as before
 * without paying for a fresh exception and stack trace on every call.
 */
public final class CachedMethodLookup {
    private static final ClassValue<Map<String, Optional<Method>>> CACHE = new ClassValue<>() {
        @Override
        protected Map<String, Optional<Method>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private static final NoSuchMethodException MISSING = new NoSuchMethodException("cached miss") {
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    };

    private CachedMethodLookup() {}

    public static Method getMethod(Class<?> type, String name, Class<?>... params) throws NoSuchMethodException {
        if (params.length != 0) return type.getMethod(name, params); // only no-arg lookups are cached
        Optional<Method> m = CACHE.get(type).computeIfAbsent(name, n -> {
            try {
                return Optional.of(type.getMethod(n));
            } catch (NoSuchMethodException e) {
                return Optional.empty();
            }
        });
        if (m.isEmpty()) throw MISSING;
        return m.get();
    }
}
