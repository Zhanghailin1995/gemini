package io.gemini.registry;

import java.util.Collection;

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
     * Find a service in the local scope.
     */
    Collection<RegisterMeta> lookup(RegisterMeta.ServiceMeta serviceMeta);

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
