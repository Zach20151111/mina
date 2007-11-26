/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.mina.transport.vmpipe;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.mina.common.AbstractIoAcceptor;
import org.apache.mina.common.IoFuture;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.TransportMetadata;

/**
 * Binds the specified {@link IoHandler} to the specified
 * {@link VmPipeAddress}.
 *
 * @author The Apache MINA Project (dev@mina.apache.org)
 * @version $Rev$, $Date$
 */
public final class VmPipeAcceptor extends AbstractIoAcceptor {
    static final Map<VmPipeAddress, VmPipe> boundHandlers = new HashMap<VmPipeAddress, VmPipe>();

    /**
     * Creates a new instance.
     */
    public VmPipeAcceptor() {
        super(new DefaultVmPipeSessionConfig());
    }

    public TransportMetadata getTransportMetadata() {
        return VmPipeSessionImpl.METADATA;
    }

    @Override
    public VmPipeSessionConfig getSessionConfig() {
        return (VmPipeSessionConfig) super.getSessionConfig();
    }

    @Override
    public VmPipeAddress getLocalAddress() {
        return (VmPipeAddress) super.getLocalAddress();
    }

    // This method is overriden to work around a problem with
    // bean property access mechanism.

    public void setLocalAddress(VmPipeAddress localAddress) {
        super.setLocalAddress(localAddress);
    }

    @Override
    protected void dispose0() throws Exception {
        unbind();
    }

    @Override
    protected void bind0() throws IOException {
        List<SocketAddress> localAddresses = getLocalAddresses();
        List<SocketAddress> newLocalAddresses = new ArrayList<SocketAddress>();

        synchronized (boundHandlers) {
            for (SocketAddress a: localAddresses) {
                VmPipeAddress localAddress = (VmPipeAddress) a;
                if (localAddress == null || localAddress.getPort() == 0) {
                    localAddress = null;
                    for (int i = 10000; i < Integer.MAX_VALUE; i++) {
                        VmPipeAddress newLocalAddress = new VmPipeAddress(i);
                        if (!boundHandlers.containsKey(newLocalAddress) &&
                            !newLocalAddresses.contains(newLocalAddress)) {
                            localAddress = newLocalAddress;
                            break;
                        }
                    }
    
                    if (localAddress == null) {
                        throw new IOException("No port available.");
                    }
                } else if (localAddress.getPort() < 0) {
                    throw new IOException("Bind port number must be 0 or above.");
                } else if (boundHandlers.containsKey(localAddress)) {
                    throw new IOException("Address already bound: " + localAddress);
                }
                
                newLocalAddresses.add(localAddress);
            }

            for (SocketAddress a: newLocalAddresses) {
                VmPipeAddress localAddress = (VmPipeAddress) a;
                if (!boundHandlers.containsKey(localAddress)) {
                    boundHandlers.put(localAddress, new VmPipe(this, localAddress,
                            getHandler(), getListeners()));
                } else {
                    for (SocketAddress a2: newLocalAddresses) {
                        boundHandlers.remove(a2);
                    }
                    throw new IOException("Duplicate local address: " + a);
                }
            }
        }

        setLocalAddresses(newLocalAddresses);
    }

    @Override
    protected void unbind0() {
        synchronized (boundHandlers) {
            boundHandlers.remove(getLocalAddress());
        }

        getListeners().fireServiceDeactivated();
    }

    public IoSession newSession(SocketAddress remoteAddress, SocketAddress localAddress) {
        throw new UnsupportedOperationException();
    }

    void doFinishSessionInitialization(IoSession session, IoFuture future) {
        finishSessionInitialization(session, future);
    }
}
