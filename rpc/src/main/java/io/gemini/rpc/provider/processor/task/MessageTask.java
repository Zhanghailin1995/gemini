package io.gemini.rpc.provider.processor.task;

import com.google.common.base.Throwables;
import io.gemini.common.concurrent.RejectedRunnable;
import io.gemini.common.util.Pair;
import io.gemini.common.util.Reflects;
import io.gemini.common.util.Requires;
import io.gemini.common.util.Signal;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;
import io.gemini.rpc.*;
import io.gemini.rpc.exception.GeminiBadRequestException;
import io.gemini.rpc.exception.GeminiRemoteException;
import io.gemini.rpc.exception.GeminiServerBusyException;
import io.gemini.rpc.exception.GeminiServiceNotFoundException;
import io.gemini.rpc.model.metadata.MessageWrapper;
import io.gemini.rpc.model.metadata.ResultWrapper;
import io.gemini.rpc.model.metadata.ServiceWrapper;
import io.gemini.rpc.provider.ProviderInterceptor;
import io.gemini.rpc.provider.processor.DefaultProviderProcessor;
import io.gemini.serialization.Serializer;
import io.gemini.serialization.SerializerFactory;
import io.gemini.transport.CodecConfig;
import io.gemini.transport.Status;
import io.gemini.transport.channel.Chan;
import io.gemini.transport.channel.FutureListener;
import io.gemini.transport.payload.RequestPayload;
import io.gemini.transport.payload.ResponsePayload;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * gemini
 * io.gemini.rpc.provider.processor.task.MessageTask
 *
 * @author zhanghailin
 */
public class MessageTask implements RejectedRunnable {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(MessageTask.class);

    private static final Signal INVOKE_ERROR = Signal.valueOf(MessageTask.class, "INVOKE_ERROR");

    private final DefaultProviderProcessor processor;
    private final Chan channel;
    private final Request request;

    public MessageTask(DefaultProviderProcessor processor, Chan channel, Request request) {
        this.processor = processor;
        this.channel = channel;
        this.request = request;
    }


    @Override
    public void run() {
        // stack copy
        final DefaultProviderProcessor _processor = processor;
        final Request _request = request;

        // 全局流量控制
        /*ControlResult ctrl = _processor.flowControl(_request);
        if (!ctrl.isAllowed()) {
            rejected(Status.APP_FLOW_CONTROL, new JupiterFlowControlException(String.valueOf(ctrl)));
            return;
        }*/
        MessageWrapper msg;
        try {
            RequestPayload _requestPayload = _request.payload();

            byte s_code = _requestPayload.serializerCode();
            Serializer serializer = SerializerFactory.getSerializer(s_code);

            // 在业务线程中反序列化, 减轻IO线程负担
            if (CodecConfig.isCodecLowCopy()) {
                //TODO low copy
                /*InputBuf inputBuf = _requestPayload.inputBuf();
                msg = serializer.readObject(inputBuf, MessageWrapper.class);*/
                msg = null;
            } else {
                byte[] bytes = _requestPayload.bytes();
                msg = serializer.readObject(bytes, MessageWrapper.class);
            }
            _requestPayload.clear();

            _request.message(msg);
        } catch (Throwable t) {
            rejected(Status.BAD_REQUEST, new GeminiBadRequestException("reading request failed", t));
            return;
        }

        // 查找服务
        final ServiceWrapper service = _processor.lookupService(msg.getMetadata());
        if (service == null) {
            rejected(Status.SERVICE_NOT_FOUND, new GeminiServiceNotFoundException(String.valueOf(msg)));
            return;
        }

        // provider私有流量控制
        /*FlowController<JRequest> childController = service.getFlowController();
        if (childController != null) {
            ctrl = childController.flowControl(_request);
            if (!ctrl.isAllowed()) {
                rejected(Status.PROVIDER_FLOW_CONTROL, new JupiterFlowControlException(String.valueOf(ctrl)));
                return;
            }
        }*/

        // processing
        Executor childExecutor = service.getExecutor();
        if (childExecutor == null) {
            process(service);
        } else {
            // provider私有线程池执行
            childExecutor.execute(() -> process(service));
        }

    }

    @Override
    public void rejected() {
        rejected(Status.SERVER_BUSY, new GeminiServerBusyException(String.valueOf(request)));
    }

    private void rejected(Status status, GeminiRemoteException cause) {
        /*if (METRIC_NEEDED) {
            MetricsHolder.rejectionMeter.mark();
        }*/

        // 当服务拒绝方法被调用时一般分以下几种情况:
        //  1. 非法请求, close当前连接;
        //  2. 服务端处理能力出现瓶颈, close当前连接, jupiter客户端会自动重连, 在加权负载均衡的情况下权重是一点一点升上来的.
        processor.handleRejected(channel, request, status, cause);
    }

    @SuppressWarnings("unchecked")
    private void process(ServiceWrapper service) {
        final Context invokeCtx = new Context(service);
        try {
            final Object invokeResult = Chains.invoke(request, invokeCtx)
                    .getResult();

            if (!(invokeResult instanceof CompletableFuture)) {
                doProcess(invokeResult);
                return;
            }

            CompletableFuture<Object> cf = (CompletableFuture<Object>) invokeResult;

            if (cf.isDone()) {
                // CompletableFuture get/join
                // CompletableFuture 提供了 join() 方法，它的功能和 get() 方法是一样的，都是阻塞获取值，它们的区别在于 join() 抛出的是 unchecked Exception。
                doProcess(cf.join());
                return;
            }

            cf.whenComplete((result, throwable) -> {
                if (throwable == null) {
                    try {
                        doProcess(result);
                    } catch (Throwable t) {
                        handleFail(invokeCtx, t);
                    }
                } else {
                    handleFail(invokeCtx, throwable);
                }
            });
        } catch (Throwable t) {
            handleFail(invokeCtx, t);
        }
    }

    private void doProcess(Object realResult) {
        ResultWrapper result = new ResultWrapper();
        result.setResult(realResult);
        byte s_code = request.serializerCode();
        Serializer serializer = SerializerFactory.getSerializer(s_code);

        ResponsePayload responsePayload = new ResponsePayload(request.invokeId());

        if (CodecConfig.isCodecLowCopy()) {
            // TODO low copy
            /*OutputBuf outputBuf =
                    serializer.writeObject(channel.allocOutputBuf(), result);
            responsePayload.outputBuf(s_code, outputBuf);*/
        } else {
            byte[] bytes = serializer.writeObject(result);
            responsePayload.bytes(s_code, bytes);
        }

        responsePayload.status(Status.OK.value());

        handleWriteResponse(responsePayload);
    }

    private void handleFail(Context invokeCtx, Throwable t) {
        if (INVOKE_ERROR == t) {
            // handle biz exception
            handleException(invokeCtx.getExpectCauseTypes(), invokeCtx.getCause());
        } else {
            processor.handleException(channel, request, Status.SERVER_ERROR, t);
        }
    }

    private void handleWriteResponse(ResponsePayload response) {
        channel.write(response, new FutureListener<Chan>() {

            @Override
            public void operationSuccess(Chan channel) throws Exception {
                // TODO METRIC
                /*if (METRIC_NEEDED) {
                    long duration = SystemClock.millisClock().now() - request.timestamp();
                    MetricsHolder.processingTimer.update(duration, TimeUnit.MILLISECONDS);
                }*/
            }

            @Override
            public void operationFailure(Chan channel, Throwable cause) throws Exception {
                long duration = System.currentTimeMillis() - request.timestamp();
                logger.error("Response sent failed, duration: {} millis, channel: {}, cause: {}.",
                        duration, channel, cause);
            }
        });
    }

    private void handleException(Class<?>[] exceptionTypes, Throwable failCause) {
        if (exceptionTypes != null && exceptionTypes.length > 0) {
            Class<?> failType = failCause.getClass();
            for (Class<?> eType : exceptionTypes) {
                // 如果抛出声明异常的子类, 客户端可能会因为不存在子类类型而无法序列化, 会在客户端抛出无法反序列化异常
                if (eType.isAssignableFrom(failType)) {
                    // 预期内的异常
                    processor.handleException(channel, request, Status.SERVICE_EXPECTED_ERROR, failCause);
                    return;
                }
            }
        }

        // 预期外的异常
        processor.handleException(channel, request, Status.SERVICE_UNEXPECTED_ERROR, failCause);
    }

    private static Object invoke(MessageWrapper msg, Context invokeCtx) throws Signal {
        ServiceWrapper service = invokeCtx.getService();
        Object provider = service.getServiceProvider();
        String methodName = msg.getMethodName();
        Object[] args = msg.getArgs();

        // TODO METRIC
        /*Timer.Context timerCtx = null;
        if (METRIC_NEEDED) {
            timerCtx = Metrics.timer(msg.getOperationName()).time();
        }*/

        Class<?>[] expectCauseTypes = null;
        try {
            List<Pair<Class<?>[], Class<?>[]>> methodExtension = service.getMethodExtension(methodName);
            if (methodExtension == null) {
                throw new NoSuchMethodException(methodName);
            }

            // 根据JLS方法调用的静态分派规则查找最匹配的方法parameterTypes
            Pair<Class<?>[], Class<?>[]> bestMatch = Reflects.findMatchingParameterTypesExt(methodExtension, args);
            Class<?>[] parameterTypes = bestMatch.getFirst();
            expectCauseTypes = bestMatch.getSecond();

            return Reflects.fastInvoke(provider, methodName, parameterTypes, args);
        } catch (Throwable t) {
            invokeCtx.setCauseAndExpectTypes(t, expectCauseTypes);
            throw INVOKE_ERROR;
        } finally {
            /*if (METRIC_NEEDED) {
                timerCtx.stop();
            }*/
        }
    }

    @SuppressWarnings("all")
    private static void handleBeforeInvoke(ProviderInterceptor[] interceptors,
                                           Object provider,
                                           String methodName,
                                           Object[] args) {

        for (int i = 0; i < interceptors.length; i++) {
            try {
                interceptors[i].beforeInvoke(provider, methodName, args);
            } catch (Throwable t) {
                logger.error("Interceptor[{}#beforeInvoke]: {}.", Reflects.simpleClassName(interceptors[i]),
                        Throwables.getStackTraceAsString(t));
            }
        }
    }

    private static void handleAfterInvoke(ProviderInterceptor[] interceptors,
                                          Object provider,
                                          String methodName,
                                          Object[] args,
                                          Object invokeResult,
                                          Throwable failCause) {

        for (int i = interceptors.length - 1; i >= 0; i--) {
            try {
                interceptors[i].afterInvoke(provider, methodName, args, invokeResult, failCause);
            } catch (Throwable t) {
                logger.error("Interceptor[{}#afterInvoke]: {}.", Reflects.simpleClassName(interceptors[i]),
                        Throwables.getStackTraceAsString(t));
            }
        }
    }

    public static class Context implements FilterContext {

        private final ServiceWrapper service;

        private Object result;                  // 服务调用结果
        private Throwable cause;                // 业务异常
        private Class<?>[] expectCauseTypes;    // 预期内的异常类型

        public Context(ServiceWrapper service) {
            this.service = Requires.requireNotNull(service, "service");
        }

        public ServiceWrapper getService() {
            return service;
        }

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
        }

        public Throwable getCause() {
            return cause;
        }

        public Class<?>[] getExpectCauseTypes() {
            return expectCauseTypes;
        }

        public void setCauseAndExpectTypes(Throwable cause, Class<?>[] expectCauseTypes) {
            this.cause = cause;
            this.expectCauseTypes = expectCauseTypes;
        }

        @Override
        public Filter.Type getType() {
            return Filter.Type.PROVIDER;
        }
    }

    static class InterceptorsFilter implements Filter {

        @Override
        public Type getType() {
            return Type.PROVIDER;
        }

        @Override
        public <T extends FilterContext> void doFilter(Request request, T filterCtx, FilterChain next) throws Throwable {
            Context invokeCtx = (Context) filterCtx;
            ServiceWrapper service = invokeCtx.getService();
            // 拦截器
            ProviderInterceptor[] interceptors = service.getInterceptors();

            if (interceptors == null || interceptors.length == 0) {
                next.doFilter(request, filterCtx);
            } else {
                Object provider = service.getServiceProvider();

                MessageWrapper msg = request.message();
                String methodName = msg.getMethodName();
                Object[] args = msg.getArgs();

                handleBeforeInvoke(interceptors, provider, methodName, args);
                try {
                    next.doFilter(request, filterCtx);
                } finally {
                    handleAfterInvoke(
                            interceptors, provider, methodName, args, invokeCtx.getResult(), invokeCtx.getCause());
                }
            }
        }
    }

    static class InvokeFilter implements Filter {

        @Override
        public Type getType() {
            return Type.PROVIDER;
        }

        @Override
        public <T extends FilterContext> void doFilter(Request request, T filterCtx, FilterChain next) throws Throwable {
            MessageWrapper msg = request.message();
            Context invokeCtx = (Context) filterCtx;

            Object invokeResult = MessageTask.invoke(msg, invokeCtx);

            invokeCtx.setResult(invokeResult);
        }
    }

    static class Chains {

        private static final FilterChain headChain;

        static {
            FilterChain invokeChain = new DefaultFilterChain(new InvokeFilter(), null);
            FilterChain interceptChain = new DefaultFilterChain(new InterceptorsFilter(), invokeChain);
            headChain = FilterLoader.loadExtFilters(interceptChain, Filter.Type.PROVIDER);
        }

        static <T extends FilterContext> T invoke(Request request, T invokeCtx) throws Throwable {
            headChain.doFilter(request, invokeCtx);
            return invokeCtx;
        }
    }
}
