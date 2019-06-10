package io.gemini.core.autoconfigure.property;

import io.gemini.common.contants.Constants;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * gemini
 * io.gemini.core.autoconfigure.property.BizThreadPool
 *
 * @author zhanghailin
 */
@Data
@NoArgsConstructor
@Deprecated
public class BizThreadPool {

    private int corePoolSize;

    private int maximumPoolSize;

    private int keepAliveTime;

    private String rejectPolicy;

    public BizThreadPool(int corePoolSize, int maximumPoolSize, int keepAliveTime, String rejectPolicy) {
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.rejectPolicy = rejectPolicy;
    }

    public static BizThreadPool DEFAULT = new BizThreadPool(Constants.AVAILABLE_PROCESSORS << 1,
            Constants.AVAILABLE_PROCESSORS << 2,
            60,
            "callerRunsPolicy");
}
