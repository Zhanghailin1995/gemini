package io.gemini.rpc.model.metadata;

import io.gemini.common.util.MapUtils;
import io.gemini.common.util.SystemPropertyUtil;
import io.gemini.serialization.ArrayElement;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

/**
 * gemini
 * io.gemini.rpc.model.metadata.MessageWrapper
 *
 * @author zhanghailin
 */
public class MessageWrapper implements Serializable {

    private static final long serialVersionUID = 1009813828866652852L;

    public static final boolean ALLOW_NULL_ARRAY_ARG =
            SystemPropertyUtil.getBoolean("gemini.message.args.allow_null_array_arg", false);

    private String appName;                 // 应用名称
    private final ServiceMetadata metadata; // 目标服务元数据
    private String methodName;              // 目标方法名称
    private Object[] args;                  // 目标方法参数
    private Map<String, String> attachments;

    public MessageWrapper(ServiceMetadata metadata) {
        this.metadata = metadata;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public ServiceMetadata getMetadata() {
        return metadata;
    }

    public String getGroup() {
        return metadata.getGroup();
    }

    public String getServiceProviderName() {
        return metadata.getServiceProviderName();
    }

    public String getVersion() {
        return metadata.getVersion();
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Object[] getArgs() {
        if (ALLOW_NULL_ARRAY_ARG) {
            if (args != null) {
                for (int i = 0; i < args.length - 1; i++) {
                    if (args[i] == ArrayElement.NULL) {
                        args[i] = null;
                    }
                }
            }
        }
        return args;
    }

    public void setArgs(Object[] args) {
        if (ALLOW_NULL_ARRAY_ARG) {
            if (args != null) {
                for (int i = 0; i < args.length - 1; i++) {
                    if (args[i] == null) {
                        args[i] = ArrayElement.NULL;
                    }
                }
            }
        }
        this.args = args;
    }

    public Map<String, String> getAttachments() {
        return attachments;
    }

    public void putAttachment(String key, String value) {
        if (attachments == null) {
            attachments = MapUtils.newHashMap();
        }
        attachments.put(key, value);
    }

    public String getOperationName() {
        return metadata.directoryString() + "." + methodName;
    }

    @Override
    public String toString() {
        return "MessageWrapper{" +
                "appName='" + appName + '\'' +
                ", metadata=" + metadata +
                ", methodName='" + methodName + '\'' +
                ", args=" + Arrays.toString(args) +
                ", attachments=" + attachments +
                '}';
    }
}
