package io.gemini.common.util;


import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A pool of {@link Constant}s.
 *
 * Forked from <a href="https://github.com/netty/netty">Netty</a>.
 */
public abstract class ConstantPool<T extends Constant<T>>{

    private final ConcurrentMap<String, T> constants = new ConcurrentHashMap<>();

    private final AtomicInteger nextId = new AtomicInteger(1);

    /**
     * Shortcut of {@link #valueOf(String) valueOf(firstNameComponent.getName() + "#" + secondNameComponent)}.
     */
    public T valueOf(Class<?> firstNameComponent, String secondNameComponent) {
        Requires.requireNotNull(firstNameComponent, "firstNameComponent");
        Requires.requireNotNull(secondNameComponent, "secondNameComponent");

        return valueOf(firstNameComponent.getName() + '#' + secondNameComponent);
    }

    /**
     * Returns the {@link Constant} which is assigned to the specified {@code name}.
     * If there's no such {@link Constant}, a new one will be created and returned.
     * Once created, the subsequent calls with the same {@code name} will always return the previously created one
     * (i.e. singleton.)
     *
     * @param name the name of the {@link Constant}
     */
    public T valueOf(String name) {
        Requires.requireTrue(StringUtils.isNotBlank(name), "empty name");
        return getOrCreate(name);
    }

    /**
     * Get existing constant by name or creates new one if not exists. Threadsafe
     *
     * @param name the name of the {@link Constant}
     */
    private T getOrCreate(String name) {
        T constant = constants.get(name);
        if (constant == null) {
            final T newConstant = newConstant(nextId.getAndIncrement(), name);
            constant = constants.putIfAbsent(name, newConstant);
            if (constant == null) {
                constant = newConstant;
            }
        }
        return constant;
    }

    /**
     * Returns {@code true} if exists for the given {@code name}.
     */
    public boolean exists(String name) {
        Requires.requireTrue(StringUtils.isNotBlank(name), "empty name");
        return constants.containsKey(name);
    }

    /**
     * Creates a new {@link Constant} for the given {@code name} or fail with an
     * {@link IllegalArgumentException} if a {@link Constant} for the given {@code name} exists.
     */
    public T newInstance(String name) {
        Requires.requireTrue(StringUtils.isNotBlank(name), "empty name");
        return createOrThrow(name);
    }

    /**
     * Creates constant by name or throws exception. Threadsafe
     *
     * @param name the name of the {@link Constant}
     */
    private T createOrThrow(String name) {
        T constant = constants.get(name);
        if (constant == null) {
            final T newConstant = newConstant(nextId.getAndIncrement(), name);
            constant = constants.putIfAbsent(name, newConstant);
            if (constant == null) {
                return newConstant;
            }
        }

        throw new IllegalArgumentException(String.format("'%s' is already in use", name));
    }

    protected abstract T newConstant(int id, String name);
}
