package io.gemini.common.contants;

import io.gemini.common.util.SystemPropertyUtil;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Formatter;

public final class Constants {

    /**
     * 换行符
     */
    public static final String NEWLINE;
    /**
     * 字符编码
     */
    public static final String UTF8_CHARSET = "UTF-8";
    public static final Charset UTF8;

    static {
        String newLine;
        try {
            newLine = new Formatter().format("%n").toString();
        } catch (Exception e) {
            newLine = "\n";
        }
        NEWLINE = newLine;

        Charset charset = null;
        try {
            charset = Charset.forName(UTF8_CHARSET);
        } catch (UnsupportedCharsetException ignored) {
        }
        UTF8 = charset;
    }


    /**
     * 可配置的 available processors. 默认值是 {@link Runtime#availableProcessors()}.
     * 可以通过设置 system property "gemini.available_processors" 来覆盖默认值.
     */
    public static final int AVAILABLE_PROCESSORS =
            SystemPropertyUtil.getInt("gemini.available_processors", Runtime.getRuntime().availableProcessors());

    /** 未知应用名称 */
    public static final String UNKNOWN_APP_NAME = "UNKNOWN";
    /** 服务默认组别 */
    public static final String DEFAULT_GROUP = "Jupiter";
    /** 服务默认版本号 */
    public static final String DEFAULT_VERSION = "1.0.0";
    /** 默认的调用超时时间为3秒 **/
    public static final long DEFAULT_TIMEOUT =
            SystemPropertyUtil.getInt("gemini.rpc.invoke.timeout", 3 * 1000);
    /** Server链路read空闲检测, 默认60秒, 60秒没读到任何数据会强制关闭连接 */
    public static final int READER_IDLE_TIME_SECONDS =
            SystemPropertyUtil.getInt("gemini.io.reader.idle.time.seconds", 60);
    /** Client链路write空闲检测, 默认30秒, 30秒没有向链路中写入任何数据时Client会主动向Server发送心跳数据包 */
    public static final int WRITER_IDLE_TIME_SECONDS =
            SystemPropertyUtil.getInt("gemini.io.writer.idle.time.seconds", 30);
    /** The number of flushes after which an explicit flush will be done */
    public static final int EXPLICIT_FLUSH_AFTER_FLUSHES =
            SystemPropertyUtil.getInt("gemini.io.explicit.flush.after.flushes", 1024);
    /** Whether use low copy strategy for serialization */
    public static final boolean CODEC_LOW_COPY =
            SystemPropertyUtil.getBoolean("gemini.io.codec.low_copy", true);

    /** Load balancer 默认预热时间 **/
    public static final int DEFAULT_WARM_UP =
            SystemPropertyUtil.getInt("gemini.rpc.load-balancer.warm-up", 10 * 60 * 1000);
    /** Load balancer 默认权重 **/
    public static final int DEFAULT_WEIGHT =
            SystemPropertyUtil.getInt("gemini.rpc.load-balancer.default.weight", 50);
    /** Load balancer 最大权重 **/
    public static final int MAX_WEIGHT =
            SystemPropertyUtil.getInt("gemini.rpc.load-balancer.max.weight", 100);

    /** Suggest that the count of connections **/
    public static final int SUGGESTED_CONNECTION_COUNT =
            SystemPropertyUtil.getInt("gemini.rpc.suggest.connection.count", Math.min(AVAILABLE_PROCESSORS, 4));

    /** Metrics csv reporter */
    public static final boolean METRIC_CSV_REPORTER =
            SystemPropertyUtil.getBoolean("gemini.metric.csv.reporter", false);
    /** Metrics csv reporter directory */
    public static final String METRIC_CSV_REPORTER_DIRECTORY =
            SystemPropertyUtil.get("gemini.metric.csv.reporter.directory", SystemPropertyUtil.get("user.dir"));
    /** Metrics reporter period */
    public static final int METRIC_REPORT_PERIOD =
            SystemPropertyUtil.getInt("gemini.metric.report.period", 15);

    private Constants() {}
}
