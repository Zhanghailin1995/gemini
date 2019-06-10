package io.gemini.registry;

/**
 * gemini
 * io.gemini.registry.RegisterMeta
 *
 * @author zhanghailin
 */
public class RegisterMeta {

    // 地址
    private Address address = new Address();

    // 权重 hashCode() 与 equals() 不把weight计算在内
    private volatile int weight;

    // 建议连接数, jupiter客户端会根据connCount的值去建立对应数量的连接, hashCode() 与 equals() 不把connCount计算在内
    private volatile int connCount;

    public String getHost() {
        return address.getHost();
    }

    public void setHost(String host) {
        address.setHost(host);
    }

    public int getPort() {
        return address.getPort();
    }

    public void setPort(int port) {
        address.setPort(port);
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getConnCount() {
        return connCount;
    }

    public void setConnCount(int connCount) {
        this.connCount = connCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RegisterMeta that = (RegisterMeta) o;

        // 地址相同就代表元数据相同
        return !address.equals(that.address);
    }

    @Override
    public int hashCode() {
        return address != null ? address.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "RegisterMeta{" +
                "address=" + address +
                ", weight=" + weight +
                ", connCount=" + connCount +
                '}';
    }


    /**
     * 不要轻易修改成员变量, 否则将影响hashCode和equals, Address需要经常放入List, Map等容器中.
     */
    public static class Address {
        // 地址
        private String host;
        // 端口
        private int port;

        public Address() {}

        public Address(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Address address = (Address) o;

            return port == address.port && !(host != null ? !host.equals(address.host) : address.host != null);
        }

        @Override
        public int hashCode() {
            int result = host != null ? host.hashCode() : 0;
            result = 31 * result + port;
            return result;
        }

        @Override
        public String toString() {
            return "Address{" +
                    "host='" + host + '\'' +
                    ", port=" + port +
                    '}';
        }
    }
}
