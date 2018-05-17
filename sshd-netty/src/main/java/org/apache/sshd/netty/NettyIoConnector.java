/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sshd.netty;

import java.net.SocketAddress;

import org.apache.sshd.common.future.DefaultSshFuture;
import org.apache.sshd.common.io.IoConnectFuture;
import org.apache.sshd.common.io.IoConnector;
import org.apache.sshd.common.io.IoHandler;
import org.apache.sshd.common.io.IoSession;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * The Netty based IoConnector implementation.
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class NettyIoConnector extends NettyIoService implements IoConnector {

    protected final Bootstrap bootstrap = new Bootstrap();
    protected final IoHandler handler;

    public NettyIoConnector(NettyIoServiceFactory factory, IoHandler handler) {
        this.factory = factory;
        this.handler = handler;
        channelGroup = new DefaultChannelGroup("sshd-connector-channels", GlobalEventExecutor.INSTANCE);
        bootstrap.group(factory.eventLoopGroup)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.SO_BACKLOG, 100)  // TODO make this configurable
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    @SuppressWarnings("resource")
                    NettyIoSession session = new NettyIoSession(NettyIoConnector.this, handler);
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(new LoggingHandler(LogLevel.INFO));   // TODO make this configurable
                    p.addLast(session.adapter);
                }
            });
    }

    @Override
    public IoConnectFuture connect(SocketAddress address) {
        boolean debugEnabled = log.isDebugEnabled();
        if (debugEnabled) {
            log.debug("Connecting to {}", address);
        }

        IoConnectFuture future = new DefaultIoConnectFuture(address, null);
        ChannelFuture chf = bootstrap.connect(address);
        Channel channel = chf.channel();
        channel.attr(CONNECT_FUTURE_KEY).set(future);
        chf.addListener(cf -> {
            Throwable t = chf.cause();
            if (t != null) {
                future.setException(t);
            } else if (chf.isCancelled()) {
                future.cancel();
            }
        });
        return future;
    }

    public static class DefaultIoConnectFuture extends DefaultSshFuture<IoConnectFuture> implements IoConnectFuture {
        public DefaultIoConnectFuture(Object id, Object lock) {
            super(id, lock);
        }

        @Override
        public IoSession getSession() {
            Object v = getValue();
            return (v instanceof IoSession) ? (IoSession) v : null;
        }

        @Override
        public Throwable getException() {
            Object v = getValue();
            return (v instanceof Throwable) ? (Throwable) v : null;
        }

        @Override
        public boolean isConnected() {
            Object v = getValue();
            return v instanceof IoSession;
        }

        @Override
        public void setSession(IoSession session) {
            setValue(session);
        }

        @Override
        public void setException(Throwable exception) {
            setValue(exception);
        }
    }
}
