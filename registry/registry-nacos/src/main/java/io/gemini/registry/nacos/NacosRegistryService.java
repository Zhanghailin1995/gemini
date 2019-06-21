package io.gemini.registry.nacos;

import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.google.common.base.Throwables;
import io.gemini.common.util.SpiMetadata;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;
import io.gemini.registry.AbstractRegistryService;
import io.gemini.registry.RegisterMeta;

import java.util.Properties;

import static io.gemini.common.util.Requires.requireNotNull;

/**
 * gemini
 * io.gemini.registry.nacos.NacosRegistryService
 *
 * @author zhanghailin
 */
@SpiMetadata(name = "nacos")
public class NacosRegistryService extends AbstractRegistryService {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NacosRegistryService.class);

    private NamingService nacos;


    @Override
    public void destroy() {

    }

    @Override
    protected void doSubscribe(RegisterMeta.ServiceMeta serviceMeta) {

    }

    @Override
    protected void doRegister(RegisterMeta meta) {

    }

    @Override
    protected void doUnregister(RegisterMeta meta) {

    }

    @Override
    protected void doCheckRegisterNodeStatus() {

    }

    @Override
    public void connectToRegistryServer(String connectString) {

        requireNotNull(connectString, "connectString");

        try {

            Properties properties = new Properties();
            properties.setProperty("serverAddr", "21.34.53.5:8848,21.34.53.6:8848");
            properties.setProperty("namespace", "quickStart");

            nacos = NamingFactory.createNamingService(properties);
        } catch (Throwable cause) {
            logger.error("connect to nacos naming service:[{}] error,{}", connectString, Throwables.getStackTraceAsString(cause));
        }
    }
}
