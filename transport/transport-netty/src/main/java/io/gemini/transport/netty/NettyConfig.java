/*
 * Copyright (c) 2015 The Jupiter Project
 *
 * Licensed under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gemini.transport.netty;

import com.google.common.collect.Lists;
import io.gemini.common.util.Requires;
import io.gemini.transport.Config;
import io.gemini.transport.ConfigGroup;
import io.gemini.transport.Option;

import java.util.Collections;
import java.util.List;

/**
 * Config for netty.
 *
 * jupiter
 * org.jupiter.transport.netty
 *
 * @author jiachun.fjc
 */
public class NettyConfig implements Config {

    private volatile int ioRatio = 100;
    private volatile boolean preferDirect = true;
    private volatile boolean usePooledAllocator = true;

    @Override
    public List<Option<?>> getOptions() {
        return getOptions(null, Option.IO_RATIO);
    }

    protected List<Option<?>> getOptions(List<Option<?>> result, Option<?>... options) {
        if (result == null) {
            result = Lists.newArrayList();
        }
        Collections.addAll(result, options);
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(Option<T> option) {
        Requires.requireNotNull(option);

        if (option == Option.IO_RATIO) {
            return (T) Integer.valueOf(getIoRatio());
        }
        return null;
    }

    @Override
    public <T> boolean setOption(Option<T> option, T value) {
        validate(option, value);

        if (option == Option.IO_RATIO) {
            setIoRatio(castToInteger(value));
        } else {
            return false;
        }
        return true;
    }

    public int getIoRatio() {
        return ioRatio;
    }

    public void setIoRatio(int ioRatio) {
        if (ioRatio < 0) {
            ioRatio = 0;
        }
        if (ioRatio > 100) {
            ioRatio = 100;
        }
        this.ioRatio = ioRatio;
    }

    public boolean isPreferDirect() {
        return preferDirect;
    }

    public void setPreferDirect(boolean preferDirect) {
        this.preferDirect = preferDirect;
    }

    public boolean isUsePooledAllocator() {
        return usePooledAllocator;
    }

    public void setUsePooledAllocator(boolean usePooledAllocator) {
        this.usePooledAllocator = usePooledAllocator;
    }

    protected <T> void validate(Option<T> option, T value) {
        Requires.requireNotNull(option, "option");
        Requires.requireNotNull(value, "value");
    }

    private static Integer castToInteger(Object value) {
        if (value instanceof Integer) {
            return (Integer) value;
        }

        if (value instanceof String) {
            return Integer.valueOf((String) value);
        }

        throw new IllegalArgumentException(value.getClass().toString());
    }

    private static Long castToLong(Object value) {
        if (value instanceof Long) {
            return (Long) value;
        }

        if (value instanceof String) {
            return Long.valueOf((String) value);
        }

        throw new IllegalArgumentException(value.getClass().toString());
    }

    private static Boolean castToBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof String) {
            return Boolean.valueOf((String) value);
        }

        throw new IllegalArgumentException(value.getClass().toString());
    }

    /**
     * TCP netty option
     */
    public static class NettyTcpConfigGroup implements ConfigGroup {

        private ParentConfig parent = new ParentConfig();
        private ChildConfig child = new ChildConfig();

        @Override
        public ParentConfig parent() {
            return parent;
        }

        @Override
        public ChildConfig child() {
            return child;
        }

        /**
         * TCP netty parent option
         */
        public static class ParentConfig extends NettyConfig {

            private volatile int backlog = 1024;
            private volatile int rcvBuf = -1;
            private volatile boolean reuseAddress = true;

            // netty native epoll options
            private volatile int pendingFastOpenRequestsThreshold = -1;
            private volatile int tcpDeferAccept = -1;
            private volatile boolean edgeTriggered = true;
            private volatile boolean reusePort = false;
            private volatile boolean ipFreeBind = false;
            private volatile boolean ipTransparent = false;

            @Override
            public List<Option<?>> getOptions() {
                return getOptions(super.getOptions(),
                        Option.SO_BACKLOG,
                        Option.SO_RCVBUF,
                        Option.SO_REUSEADDR,
                        Option.TCP_FASTOPEN,
                        Option.TCP_DEFER_ACCEPT,
                        Option.EDGE_TRIGGERED,
                        Option.SO_REUSEPORT,
                        Option.IP_FREEBIND,
                        Option.IP_TRANSPARENT);
            }

            protected List<Option<?>> getOptions(List<Option<?>> result, Option<?>... options) {
                if (result == null) {
                    result = Lists.newArrayList();
                }
                Collections.addAll(result, options);
                return result;
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T> T getOption(Option<T> option) {
                Requires.requireNotNull(option);

                if (option == Option.SO_BACKLOG) {
                    return (T) Integer.valueOf(getBacklog());
                }
                if (option == Option.SO_RCVBUF) {
                    return (T) Integer.valueOf(getRcvBuf());
                }
                if (option == Option.SO_REUSEADDR) {
                    return (T) Boolean.valueOf(isReuseAddress());
                }
                if (option == Option.TCP_FASTOPEN) {
                    return (T) Integer.valueOf(getPendingFastOpenRequestsThreshold());
                }
                if (option == Option.TCP_DEFER_ACCEPT) {
                    return (T) Integer.valueOf(getTcpDeferAccept());
                }
                if (option == Option.EDGE_TRIGGERED) {
                    return (T) Boolean.valueOf(isEdgeTriggered());
                }
                if (option == Option.SO_REUSEPORT) {
                    return (T) Boolean.valueOf(isReusePort());
                }
                if (option == Option.IP_FREEBIND) {
                    return (T) Boolean.valueOf(isIpFreeBind());
                }
                if (option == Option.IP_TRANSPARENT) {
                    return (T) Boolean.valueOf(isIpTransparent());
                }

                return super.getOption(option);
            }

            @Override
            public <T> boolean setOption(Option<T> option, T value) {
                validate(option, value);

                if (option == Option.SO_BACKLOG) {
                    setBacklog(castToInteger(value));
                } else if (option == Option.SO_RCVBUF) {
                    setRcvBuf(castToInteger(value));
                } else if (option == Option.SO_REUSEADDR) {
                    setReuseAddress(castToBoolean(value));
                } else if (option == Option.TCP_FASTOPEN) {
                    setPendingFastOpenRequestsThreshold(castToInteger(value));
                } else if (option == Option.TCP_DEFER_ACCEPT) {
                    setTcpDeferAccept(castToInteger(value));
                } else if (option == Option.EDGE_TRIGGERED) {
                    setEdgeTriggered(castToBoolean(value));
                } else if (option == Option.SO_REUSEPORT) {
                    setReusePort(castToBoolean(value));
                } else if (option == Option.IP_FREEBIND) {
                    setIpFreeBind(castToBoolean(value));
                } else if (option == Option.IP_TRANSPARENT) {
                    setIpTransparent(castToBoolean(value));
                } else {
                    return super.setOption(option, value);
                }

                return true;
            }

            public int getBacklog() {
                return backlog;
            }

            public void setBacklog(int backlog) {
                this.backlog = backlog;
            }

            public int getRcvBuf() {
                return rcvBuf;
            }

            public void setRcvBuf(int rcvBuf) {
                this.rcvBuf = rcvBuf;
            }

            public boolean isReuseAddress() {
                return reuseAddress;
            }

            public void setReuseAddress(boolean reuseAddress) {
                this.reuseAddress = reuseAddress;
            }

            public int getPendingFastOpenRequestsThreshold() {
                return pendingFastOpenRequestsThreshold;
            }

            public void setPendingFastOpenRequestsThreshold(int pendingFastOpenRequestsThreshold) {
                this.pendingFastOpenRequestsThreshold = pendingFastOpenRequestsThreshold;
            }

            public int getTcpDeferAccept() {
                return tcpDeferAccept;
            }

            public void setTcpDeferAccept(int tcpDeferAccept) {
                this.tcpDeferAccept = tcpDeferAccept;
            }

            public boolean isEdgeTriggered() {
                return edgeTriggered;
            }

            public void setEdgeTriggered(boolean edgeTriggered) {
                this.edgeTriggered = edgeTriggered;
            }

            public boolean isReusePort() {
                return reusePort;
            }

            public void setReusePort(boolean reusePort) {
                this.reusePort = reusePort;
            }

            public boolean isIpFreeBind() {
                return ipFreeBind;
            }

            public void setIpFreeBind(boolean ipFreeBind) {
                this.ipFreeBind = ipFreeBind;
            }

            public boolean isIpTransparent() {
                return ipTransparent;
            }

            public void setIpTransparent(boolean ipTransparent) {
                this.ipTransparent = ipTransparent;
            }
        }

        /**
         * TCP netty child option
         */
        public static class ChildConfig extends NettyConfig {

            private volatile int rcvBuf = -1;
            private volatile int sndBuf = -1;
            private volatile int linger = -1;
            private volatile int ipTos = -1;
            private volatile int connectTimeoutMillis = -1;
            private volatile int writeBufferHighWaterMark = -1;
            private volatile int writeBufferLowWaterMark = -1;
            private volatile boolean reuseAddress = true;
            private volatile boolean keepAlive = true;
            private volatile boolean tcpNoDelay = true;
            private volatile boolean allowHalfClosure = false;

            // netty native epoll options
            private volatile long tcpNotSentLowAt = -1;
            private volatile int tcpKeepCnt = -1;
            private volatile int tcpUserTimeout = -1;
            private volatile int tcpKeepIdle = -1;
            private volatile int tcpKeepInterval = -1;
            private volatile boolean edgeTriggered = true;
            private volatile boolean tcpCork = false;
            private volatile boolean tcpQuickAck = true;
            private volatile boolean ipTransparent = false;
            private volatile boolean tcpFastOpenConnect = false;

            @Override
            public List<Option<?>> getOptions() {
                return getOptions(super.getOptions(),
                        Option.SO_RCVBUF,
                        Option.SO_SNDBUF,
                        Option.SO_LINGER,
                        Option.SO_REUSEADDR,
                        Option.CONNECT_TIMEOUT_MILLIS,
                        Option.WRITE_BUFFER_HIGH_WATER_MARK,
                        Option.WRITE_BUFFER_LOW_WATER_MARK,
                        Option.KEEP_ALIVE,
                        Option.TCP_NODELAY,
                        Option.IP_TOS,
                        Option.ALLOW_HALF_CLOSURE,
                        Option.TCP_NOTSENT_LOWAT,
                        Option.TCP_KEEPCNT,
                        Option.TCP_USER_TIMEOUT,
                        Option.TCP_KEEPIDLE,
                        Option.TCP_KEEPINTVL,
                        Option.EDGE_TRIGGERED,
                        Option.TCP_CORK,
                        Option.TCP_QUICKACK,
                        Option.IP_TRANSPARENT,
                        Option.TCP_FASTOPEN_CONNECT);
            }

            protected List<Option<?>> getOptions(List<Option<?>> result, Option<?>... options) {
                if (result == null) {
                    result = Lists.newArrayList();
                }
                Collections.addAll(result, options);
                return result;
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T> T getOption(Option<T> option) {
                Requires.requireNotNull(option);

                if (option == Option.SO_RCVBUF) {
                    return (T) Integer.valueOf(getRcvBuf());
                }
                if (option == Option.SO_SNDBUF) {
                    return (T) Integer.valueOf(getSndBuf());
                }
                if (option == Option.SO_LINGER) {
                    return (T) Integer.valueOf(getLinger());
                }
                if (option == Option.IP_TOS) {
                    return (T) Integer.valueOf(getIpTos());
                }
                if (option == Option.CONNECT_TIMEOUT_MILLIS) {
                    return (T) Integer.valueOf(getConnectTimeoutMillis());
                }
                if (option == Option.WRITE_BUFFER_HIGH_WATER_MARK) {
                    return (T) Integer.valueOf(getWriteBufferHighWaterMark());
                }
                if (option == Option.WRITE_BUFFER_LOW_WATER_MARK) {
                    return (T) Integer.valueOf(getWriteBufferLowWaterMark());
                }
                if (option == Option.SO_REUSEADDR) {
                    return (T) Boolean.valueOf(isReuseAddress());
                }
                if (option == Option.KEEP_ALIVE) {
                    return (T) Boolean.valueOf(isKeepAlive());
                }
                if (option == Option.TCP_NODELAY) {
                    return (T) Boolean.valueOf(isTcpNoDelay());
                }
                if (option == Option.ALLOW_HALF_CLOSURE) {
                    return (T) Boolean.valueOf(isAllowHalfClosure());
                }
                if (option == Option.TCP_NOTSENT_LOWAT) {
                    return (T) Long.valueOf(getTcpNotSentLowAt());
                }
                if (option == Option.TCP_KEEPIDLE) {
                    return (T) Integer.valueOf(getTcpKeepIdle());
                }
                if (option == Option.TCP_KEEPINTVL) {
                    return (T) Integer.valueOf(getTcpKeepInterval());
                }
                if (option == Option.TCP_KEEPCNT) {
                    return (T) Integer.valueOf(getTcpKeepCnt());
                }
                if (option == Option.TCP_USER_TIMEOUT) {
                    return (T) Integer.valueOf(getTcpUserTimeout());
                }
                if (option == Option.EDGE_TRIGGERED) {
                    return (T) Boolean.valueOf(isEdgeTriggered());
                }
                if (option == Option.TCP_CORK) {
                    return (T) Boolean.valueOf(isTcpCork());
                }
                if (option == Option.TCP_QUICKACK) {
                    return (T) Boolean.valueOf(isTcpQuickAck());
                }
                if (option == Option.IP_TRANSPARENT) {
                    return (T) Boolean.valueOf(isIpTransparent());
                }
                if (option == Option.TCP_FASTOPEN_CONNECT) {
                    return (T) Boolean.valueOf(isTcpFastOpenConnect());
                }

                return super.getOption(option);
            }

            @Override
            public <T> boolean setOption(Option<T> option, T value) {
                validate(option, value);

                if (option == Option.SO_RCVBUF) {
                    setRcvBuf(castToInteger(value));
                } else if (option == Option.SO_SNDBUF) {
                    setSndBuf(castToInteger(value));
                } else if (option == Option.SO_LINGER) {
                    setLinger(castToInteger(value));
                } else if (option == Option.IP_TOS) {
                    setIpTos(castToInteger(value));
                } else if (option == Option.CONNECT_TIMEOUT_MILLIS) {
                    setConnectTimeoutMillis(castToInteger(value));
                } else if (option == Option.WRITE_BUFFER_HIGH_WATER_MARK) {
                    setWriteBufferHighWaterMark(castToInteger(value));
                } else if (option == Option.WRITE_BUFFER_LOW_WATER_MARK) {
                    setWriteBufferLowWaterMark(castToInteger(value));
                } else if (option == Option.SO_REUSEADDR) {
                    setReuseAddress(castToBoolean(value));
                } else if (option == Option.KEEP_ALIVE) {
                    setKeepAlive(castToBoolean(value));
                } else if (option == Option.TCP_NODELAY) {
                    setTcpNoDelay(castToBoolean(value));
                } else if (option == Option.ALLOW_HALF_CLOSURE) {
                    setAllowHalfClosure(castToBoolean(value));
                } else if (option == Option.TCP_NOTSENT_LOWAT) {
                    setTcpNotSentLowAt(castToLong(value));
                } else if (option == Option.TCP_KEEPIDLE) {
                    setTcpKeepIdle(castToInteger(value));
                } else if (option == Option.TCP_KEEPCNT) {
                    setTcpKeepCnt(castToInteger(value));
                } else if (option == Option.TCP_KEEPINTVL) {
                    setTcpKeepInterval(castToInteger(value));
                } else if (option == Option.TCP_USER_TIMEOUT) {
                    setTcpUserTimeout(castToInteger(value));
                } else if (option == Option.IP_TRANSPARENT) {
                    setIpTransparent(castToBoolean(value));
                } else if (option == Option.EDGE_TRIGGERED) {
                    setEdgeTriggered(castToBoolean(value));
                } else if (option == Option.TCP_CORK) {
                    setTcpCork(castToBoolean(value));
                } else if (option == Option.TCP_QUICKACK) {
                    setTcpQuickAck(castToBoolean(value));
                } else if (option == Option.TCP_FASTOPEN_CONNECT) {
                    setTcpFastOpenConnect(castToBoolean(value));
                } else {
                    return super.setOption(option, value);
                }

                return true;
            }

            public int getRcvBuf() {
                return rcvBuf;
            }

            public void setRcvBuf(int rcvBuf) {
                this.rcvBuf = rcvBuf;
            }

            public int getSndBuf() {
                return sndBuf;
            }

            public void setSndBuf(int sndBuf) {
                this.sndBuf = sndBuf;
            }

            public int getLinger() {
                return linger;
            }

            public void setLinger(int linger) {
                this.linger = linger;
            }

            public int getIpTos() {
                return ipTos;
            }

            public void setIpTos(int ipTos) {
                this.ipTos = ipTos;
            }

            public int getConnectTimeoutMillis() {
                return connectTimeoutMillis;
            }

            public void setConnectTimeoutMillis(int connectTimeoutMillis) {
                this.connectTimeoutMillis = connectTimeoutMillis;
            }

            public int getWriteBufferHighWaterMark() {
                return writeBufferHighWaterMark;
            }

            public void setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
                this.writeBufferHighWaterMark = writeBufferHighWaterMark;
            }

            public int getWriteBufferLowWaterMark() {
                return writeBufferLowWaterMark;
            }

            public void setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
                this.writeBufferLowWaterMark = writeBufferLowWaterMark;
            }

            public boolean isReuseAddress() {
                return reuseAddress;
            }

            public void setReuseAddress(boolean reuseAddress) {
                this.reuseAddress = reuseAddress;
            }

            public boolean isKeepAlive() {
                return keepAlive;
            }

            public void setKeepAlive(boolean keepAlive) {
                this.keepAlive = keepAlive;
            }

            public boolean isTcpNoDelay() {
                return tcpNoDelay;
            }

            public void setTcpNoDelay(boolean tcpNoDelay) {
                this.tcpNoDelay = tcpNoDelay;
            }

            public boolean isAllowHalfClosure() {
                return allowHalfClosure;
            }

            public void setAllowHalfClosure(boolean allowHalfClosure) {
                this.allowHalfClosure = allowHalfClosure;
            }

            public long getTcpNotSentLowAt() {
                return tcpNotSentLowAt;
            }

            public void setTcpNotSentLowAt(long tcpNotSentLowAt) {
                this.tcpNotSentLowAt = tcpNotSentLowAt;
            }

            public int getTcpKeepCnt() {
                return tcpKeepCnt;
            }

            public void setTcpKeepCnt(int tcpKeepCnt) {
                this.tcpKeepCnt = tcpKeepCnt;
            }

            public int getTcpUserTimeout() {
                return tcpUserTimeout;
            }

            public void setTcpUserTimeout(int tcpUserTimeout) {
                this.tcpUserTimeout = tcpUserTimeout;
            }

            public int getTcpKeepIdle() {
                return tcpKeepIdle;
            }

            public void setTcpKeepIdle(int tcpKeepIdle) {
                this.tcpKeepIdle = tcpKeepIdle;
            }

            public int getTcpKeepInterval() {
                return tcpKeepInterval;
            }

            public void setTcpKeepInterval(int tcpKeepInterval) {
                this.tcpKeepInterval = tcpKeepInterval;
            }

            public boolean isEdgeTriggered() {
                return edgeTriggered;
            }

            public void setEdgeTriggered(boolean edgeTriggered) {
                this.edgeTriggered = edgeTriggered;
            }

            public boolean isTcpCork() {
                return tcpCork;
            }

            public void setTcpCork(boolean tcpCork) {
                this.tcpCork = tcpCork;
            }

            public boolean isTcpQuickAck() {
                return tcpQuickAck;
            }

            public void setTcpQuickAck(boolean tcpQuickAck) {
                this.tcpQuickAck = tcpQuickAck;
            }

            public boolean isIpTransparent() {
                return ipTransparent;
            }

            public void setIpTransparent(boolean ipTransparent) {
                this.ipTransparent = ipTransparent;
            }

            public boolean isTcpFastOpenConnect() {
                return tcpFastOpenConnect;
            }

            public void setTcpFastOpenConnect(boolean tcpFastOpenConnect) {
                this.tcpFastOpenConnect = tcpFastOpenConnect;
            }
        }
    }

    /**
     * Unix domain socket option
     */
    public static class NettyDomainConfigGroup implements ConfigGroup {

        private ParentConfig parent = new ParentConfig();
        private ChildConfig child = new ChildConfig();

        @Override
        public ParentConfig parent() {
            return parent;
        }

        @Override
        public ChildConfig child() {
            return child;
        }

        /**
         * Unix domain socket parent option
         */
        public static class ParentConfig extends NettyConfig {}

        /**
         * Unix domain socket child option
         */
        public static class ChildConfig extends NettyConfig {

            private volatile int connectTimeoutMillis = -1;
            private volatile int writeBufferHighWaterMark = -1;
            private volatile int writeBufferLowWaterMark = -1;

            @Override
            public List<Option<?>> getOptions() {
                return getOptions(super.getOptions(),
                        Option.CONNECT_TIMEOUT_MILLIS,
                        Option.WRITE_BUFFER_HIGH_WATER_MARK,
                        Option.WRITE_BUFFER_LOW_WATER_MARK);
            }

            protected List<Option<?>> getOptions(List<Option<?>> result, Option<?>... options) {
                if (result == null) {
                    result = Lists.newArrayList();
                }
                Collections.addAll(result, options);
                return result;
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T> T getOption(Option<T> option) {
                Requires.requireNotNull(option);

                if (option == Option.CONNECT_TIMEOUT_MILLIS) {
                    return (T) Integer.valueOf(getConnectTimeoutMillis());
                }
                if (option == Option.WRITE_BUFFER_HIGH_WATER_MARK) {
                    return (T) Integer.valueOf(getWriteBufferHighWaterMark());
                }
                if (option == Option.WRITE_BUFFER_LOW_WATER_MARK) {
                    return (T) Integer.valueOf(getWriteBufferLowWaterMark());
                }

                return super.getOption(option);
            }

            @Override
            public <T> boolean setOption(Option<T> option, T value) {
                validate(option, value);

                if (option == Option.CONNECT_TIMEOUT_MILLIS) {
                    setConnectTimeoutMillis(castToInteger(value));
                } else if (option == Option.WRITE_BUFFER_HIGH_WATER_MARK) {
                    setWriteBufferHighWaterMark(castToInteger(value));
                } else if (option == Option.WRITE_BUFFER_LOW_WATER_MARK) {
                    setWriteBufferLowWaterMark(castToInteger(value));
                } else {
                    return super.setOption(option, value);
                }

                return true;
            }

            public int getConnectTimeoutMillis() {
                return connectTimeoutMillis;
            }

            public void setConnectTimeoutMillis(int connectTimeoutMillis) {
                this.connectTimeoutMillis = connectTimeoutMillis;
            }

            public int getWriteBufferHighWaterMark() {
                return writeBufferHighWaterMark;
            }

            public void setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
                this.writeBufferHighWaterMark = writeBufferHighWaterMark;
            }

            public int getWriteBufferLowWaterMark() {
                return writeBufferLowWaterMark;
            }

            public void setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
                this.writeBufferLowWaterMark = writeBufferLowWaterMark;
            }
        }
    }
}
