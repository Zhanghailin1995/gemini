package io.gemini.registry;

import java.util.Collection;
import java.util.Map;

/**
 * gemini
 * io.gemini.registry.RegistryService
 *
 * @author zhanghailin
 */
public interface RegistryService extends Registry {

    /**
     * Register service to registry server.
     */
    void register(RegisterMeta meta);

    /**
     * Unregister service to registry server.
     */
    void unregister(RegisterMeta meta);

    /**
     * Subscribe a service from registry server.
     */
    void subscribe(RegisterMeta.ServiceMeta serviceMeta, NotifyListener listener);

    /**
     * Find a service in the local scope.
     */
    Collection<RegisterMeta> lookup(RegisterMeta.ServiceMeta serviceMeta);

    /**
     * List all consumer's info.
     */
    Map<RegisterMeta.ServiceMeta, Integer> consumers();

    /**
     * List all provider's info.
     */
    Map<RegisterMeta, RegisterState> providers();

    /**
     * Returns {@code true} if {@link RegistryService} is shutdown.
     */
    boolean isShutdown();

    /**
     * Shutdown.
     */
    void shutdownGracefully();

    enum RegistryType {
        DEFAULT("default"),
        ZOOKEEPER("zookeeper"),
        NACOS("nacos");

        private final String value;

        RegistryType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static RegistryType parse(String name) {
            for (RegistryType s : values()) {
                if (s.name().equalsIgnoreCase(name)) {
                    return s;
                }
            }
            return null;
        }
    }

    enum RegisterState {
        PREPARE,
        DONE
    }
}
