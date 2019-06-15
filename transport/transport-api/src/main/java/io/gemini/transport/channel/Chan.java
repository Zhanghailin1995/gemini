package io.gemini.transport.channel;

import io.gemini.serialization.io.OutputBuf;

import java.net.SocketAddress;

/**
 *
 * gemini
 * io.gemini.transport.channel.Chan
 *
 * @author zhanghailin
 */
public interface Chan {


    /**
     * A {@link FutureListener} that closes the {@link Chan}.
     */
    FutureListener<Chan> CLOSE = new FutureListener<Chan>() {

        @Override
        public void operationSuccess(Chan channel) throws Exception {
            channel.close();
        }

        @Override
        public void operationFailure(Chan channel, Throwable cause) throws Exception {
            channel.close();
        }
    };

    /**
     * Returns the identifier of this {@link Chan}.
     */
    String id();

    /**
     * Return {@code true} if the {@link Chan} is active and so connected.
     */
    boolean isActive();

    /**
     * Return {@code true} if the current {@link Thread} is executed in the
     * IO thread, {@code false} otherwise.
     */
    boolean inIoThread();

    /**
     * Returns the local address where this channel is bound to.
     */
    SocketAddress localAddress();

    /**
     * Returns the remote address where this channel is connected to.
     */
    SocketAddress remoteAddress();

    /**
     * Returns {@code true} if and only if the I/O thread will perform the
     * requested write operation immediately.
     * Any write requests made when this method returns {@code false} are
     * queued until the I/O thread is ready to process the queued write requests.
     */
    boolean isWritable();

    /**
     * Is set up automatic reconnection.
     */
    boolean isMarkedReconnect();

    /**
     * Returns {@code true} if and only if read(socket) will be invoked
     * automatically so that a user application doesn't need to call it
     * at all. The default value is {@code true}.
     */
    boolean isAutoRead();

    /**
     * Sets if read(socket) will be invoked automatically so that a user
     * application doesn't need to call it at all. The default value is
     * {@code true}.
     */
    void setAutoRead(boolean autoRead);

    /**
     * Requests to close this {@link Chan}.
     */
    Chan close();

    /**
     * Requests to close this {@link Chan}.
     */
    Chan close(FutureListener<Chan> listener);


    /**
     * Requests to write a message on the channel.
     */
    Chan write(Object msg);

    /**
     * Requests to write a message on the channel.
     */
    Chan write(Object msg, FutureListener<Chan> listener);

    /**
     * Add a task will execute in the io thread later.
     */
    void addTask(Runnable task);

    /**
     * Allocate a {@link OutputBuf}.
     */
    OutputBuf allocOutputBuf();
}
