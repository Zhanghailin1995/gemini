package io.gemini.transport;

/**
 * gemini
 * io.gemini.transport.Connection
 *
 * @author zhanghailin
 */
public abstract class Connection {

    private final UnresolvedAddress address;

    public Connection(UnresolvedAddress address) {
        this.address = address;
    }

    public UnresolvedAddress getAddress() {
        return address;
    }

    public void operationComplete(@SuppressWarnings("unused") OperationListener operationListener) {
        // the default implementation does nothing
    }

    public abstract void setReconnect(boolean reconnect);

    public interface OperationListener {

        void complete(boolean isSuccess);
    }
}
