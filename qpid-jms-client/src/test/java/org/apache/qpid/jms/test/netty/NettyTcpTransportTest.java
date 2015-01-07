/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.qpid.jms.test.netty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.qpid.jms.test.QpidJmsTestCase;
import org.apache.qpid.jms.test.Wait;
import org.apache.qpid.jms.transports.NettyTcpTransport;
import org.apache.qpid.jms.transports.TcpTransportOptions;
import org.apache.qpid.jms.transports.TransportListener;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.buffer.Buffer;

/**
 * Test basic functionality of the Netty based TCP transport.
 */
public class NettyTcpTransportTest extends QpidJmsTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(NettyTcpTransportTest.class);

    private boolean transportClosed;
    private final List<Throwable> exceptions = new ArrayList<Throwable>();
    private final List<Buffer> data = new ArrayList<Buffer>();

    private final TransportListener testListener = new NettyTransportListener();
    private final TcpTransportOptions testOptions = new TcpTransportOptions();

    @Test(timeout = 60 * 1000)
    public void testConnectToServer() throws Exception {
        try (NettyEchoServer server = new NettyEchoServer()) {
            server.start();

            int port = server.getServerPort();
            URI serverLocation = new URI("tcp://localhost:" + port);

            NettyTcpTransport transport = new NettyTcpTransport(testListener, serverLocation, testOptions);
            try {
                transport.connect();
                LOG.info("Connected to test server.");
            } catch (Exception e) {
                fail("Should have connected to the server");
            }

            assertTrue(transport.isConnected());

            transport.close();
        }

        assertTrue(!transportClosed);  // Normal shutdown does not trigger the event.
        assertTrue(exceptions.isEmpty());
        assertTrue(data.isEmpty());
    }

    @Test(timeout = 60 * 1000)
    public void testDetectServerClose() throws Exception {
        NettyTcpTransport transport = null;

        try (NettyEchoServer server = new NettyEchoServer()) {
            server.start();

            int port = server.getServerPort();
            URI serverLocation = new URI("tcp://localhost:" + port);

            transport = new NettyTcpTransport(testListener, serverLocation, testOptions);
            try {
                transport.connect();
                LOG.info("Connected to test server.");
            } catch (Exception e) {
                fail("Should have connected to the server");
            }

            assertTrue(transport.isConnected());

            server.close();
        }

        assertTrue(Wait.waitFor(new Wait.Condition() {

            @Override
            public boolean isSatisified() throws Exception {
                return transportClosed;
            }
        }));
        assertTrue(exceptions.isEmpty());
        assertTrue(data.isEmpty());
        assertFalse(transport.isConnected());

        try {
            transport.close();
        } catch (Exception ex) {
            fail("Close of a disconnect transport should not generate errors");
        }
    }

    @Test(timeout = 60 * 1000)
    public void testDataSentIsReceived() throws Exception {
        final int SEND_BYTE_COUNT = 1024;

        try (NettyEchoServer server = new NettyEchoServer()) {
            server.start();

            int port = server.getServerPort();
            URI serverLocation = new URI("tcp://localhost:" + port);

            NettyTcpTransport transport = new NettyTcpTransport(testListener, serverLocation, testOptions);
            try {
                transport.connect();
                LOG.info("Connected to test server.");
            } catch (Exception e) {
                fail("Should have connected to the server");
            }

            assertTrue(transport.isConnected());

            ByteBuf sendBuffer = Unpooled.buffer(SEND_BYTE_COUNT);
            for (int i = 0; i < SEND_BYTE_COUNT; ++i) {
                sendBuffer.writeByte('A');
            }

            transport.send(sendBuffer);

            assertTrue(Wait.waitFor(new Wait.Condition() {

                @Override
                public boolean isSatisified() throws Exception {
                    return !data.isEmpty();
                }
            }));

            assertEquals(SEND_BYTE_COUNT, data.get(0).length());

            transport.close();
        }

        assertTrue(!transportClosed);  // Normal shutdown does not trigger the event.
        assertTrue(exceptions.isEmpty());
    }

    private class NettyTransportListener implements TransportListener {

        @Override
        public void onData(Buffer incoming) {
            LOG.info("Client has new incoming data of size: {}", incoming.length());
            data.add(incoming);
        }

        @Override
        public void onTransportClosed() {
            LOG.info("Transport reports that it has closed.");
            transportClosed = true;
        }

        @Override
        public void onTransportError(Throwable cause) {
            LOG.info("Transport error caught: {}", cause.getMessage());
            exceptions.add(cause);
        }
    }
}
