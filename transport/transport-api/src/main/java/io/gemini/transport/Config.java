package io.gemini.transport;

import java.util.List;

/**
 * Jupiter transport config.
 *
 * 传输层配置选项, 通常多用于配置网络层参数.
 *
 * jupiter
 * org.jupiter.transport
 *
 * @author jiachun.fjc
 */
public interface Config {

    /**
     * Return all set {@link Option}'s.
     */
    List<Option<?>> getOptions();

    /**
     * Return the value of the given {@link Option}.
     */
    <T> T getOption(Option<T> option);

    /**
     * Sets a configuration property with the specified name and value.
     *
     * @return {@code true} if and only if the property has been set
     */
    <T> boolean setOption(Option<T> option, T value);
}
