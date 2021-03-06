package com.lcf.pipeline;


import com.lcf.channel.Channel;
import com.lcf.common.RpcContext;
import com.lcf.common.RpcRequest;
import com.lcf.common.RpcResponse;
import com.lcf.context.AbstractContext;
import com.lcf.context.Context;
import org.springframework.cglib.reflect.FastClass;
import org.springframework.cglib.reflect.FastMethod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 默认实现,仅供参考
 * @author k.arthur
 * @date 2020.7.12
 */
public class DefaultPaladinPipeline extends AbstractPipeline {


    private final FastClass serviceFastClass;

    public DefaultPaladinPipeline(Channel channel, Class<?> clazz, Object invoker) {
        super(channel, clazz, invoker);
        serviceFastClass = FastClass.create(clazz);

    }


    @Override
    protected Context newHead() {
        return new PaladinHeadContext(this);
    }

    @Override
    protected Context newTail() {
        return new PaladinTailContext(this);

    }

    private class PaladinHeadContext extends AbstractContext {


        protected PaladinHeadContext(Pipeline pipeline) {
            super("paladinHeadContext", pipeline);
        }

        @Override
        protected void handle(Object obj) {

            if (obj instanceof RpcRequest) {
                RpcRequest rpcRequest = (RpcRequest) obj;
                RpcContext rpcContext = RpcContext.getContext();
                rpcContext.setRpcRequest(rpcRequest);

            }
        }

        @Override
        protected void response(Object obj) {
            try {
                RpcContext rpcContext = RpcContext.getContext();
                RpcRequest rpcRequest = rpcContext.getRpcRequest();
                io.netty.channel.Channel channel=rpcContext.getChannel();
                RpcResponse response = buildResponse(rpcRequest.getRequestId()
                        , true
                        , obj
                        , null);
                getChannel().response(response,channel);
                logger.info("headContext has responsed");
            } catch (Exception e) {
                logger.error("rpcContext has no request!");
                throw new RuntimeException("rpcContext has error");
            } finally {
                RpcContext.remove();
            }
        }

        @Override
        protected void caughtException(Object obj) {
            if (obj instanceof Exception) {
                try {
                    RpcContext rpcContext = RpcContext.getContext();
                    RpcRequest rpcRequest = rpcContext.getRpcRequest();
                    io.netty.channel.Channel channel=rpcContext.getChannel();
                    RpcResponse response = buildResponse(rpcRequest.getRequestId()
                            , false
                            , null
                            , obj);
                    getChannel().exception(response,channel);
                    logger.error("headContext has caughtException, e: {}",response);
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.error(e.getMessage());
                } finally {
                    RpcContext.remove();
                }
            }
        }

        private RpcResponse buildResponse(String id, boolean success, Object result, Object exception) {
            RpcResponse response = new RpcResponse();
            response.setRequestId(id);
            response.setException(exception);
            response.setResult(result);
            response.setSuccess(success);
            return response;
        }
    }


    private class PaladinTailContext extends AbstractContext {

        protected PaladinTailContext(Pipeline pipeline) {
            super("paladinTailContext", pipeline);
        }

        @Override
        protected void handle(Object obj) {
            if (obj instanceof RpcRequest) {
                RpcRequest rpcRequest = (RpcRequest) obj;
                String methodName = rpcRequest.getMethodName();
                Class<?>[] parameterTypes = rpcRequest.getParameterTypes();
                Object[] arguments = rpcRequest.getArguments();
                int methodIndex = serviceFastClass.getIndex(methodName, parameterTypes);
                try {
                    Object result = serviceFastClass.invoke(methodIndex, getInvoker(), arguments);
                    logger.info("invoke result is : {}",result.toString());
                    getPipeline().response(result);
                } catch (Exception e) {
                    getPipeline().exception(e);
                }
            }
        }

        @Override
        protected void response(Object obj) {
            return;
        }

        @Override
        protected void caughtException(Object obj) {
            return;
        }


    }


}