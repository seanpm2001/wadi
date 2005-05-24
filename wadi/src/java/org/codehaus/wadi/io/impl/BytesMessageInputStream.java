/**
 *
 * Copyright 2003-2005 Core Developers Network Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.codehaus.wadi.io.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import javax.jms.BytesMessage;
import javax.jms.JMSException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import EDU.oswego.cs.dl.util.concurrent.Channel;
import EDU.oswego.cs.dl.util.concurrent.Puttable;
import EDU.oswego.cs.dl.util.concurrent.TimeoutException;

public class BytesMessageInputStream extends InputStream implements Puttable {

    protected static final Log _log=LogFactory.getLog(BytesMessageInputStream.class);
    protected static final Object _endOfQueue=new Object();

    protected final Channel _inputQueue;
    protected final long _timeout;
    
    public BytesMessageInputStream(Channel inputQueue, long timeout) {
        _inputQueue=inputQueue;
        _timeout=timeout;
    }

    protected BytesMessage _buffer;
    protected long _remaining;
    
    // impl
    
    protected boolean ensureBuffer() throws JMSException, TimeoutException {
        if (_buffer!=null)
            return true; // we still have input...
        
        Object tmp=null;
        do {
            try {
                tmp=_inputQueue.poll(_timeout); // we need a fresh buffer...
            } catch (InterruptedException e) {
                // ignore
            }
        } while (Thread.interrupted());

        if (tmp==_endOfQueue) // no more input - our producer has committed his end of the queue...
            return false; 
        
        if (tmp==null)
            throw new TimeoutException(_timeout, "timed out waiting for input");
        
        _buffer=(BytesMessage)tmp;
        _remaining=_buffer.getBodyLength();
        return true; // there is further input
    }

    // InputStream
    
    public int read() throws IOException {
        try {
            if (!ensureBuffer())
                return -1;
            
            int b=_buffer.readUnsignedByte();
            _remaining--;
            
            if (_remaining==0)
                _buffer=null;
            
            //_log.info("reading: "+(char)b);
            
            return b;
        } catch (Exception e) {
            _log.warn("unexpected problem", e);
            throw new IOException();
        }
    }
    
    public int read(byte b[], int off, int len) throws IOException {
        try {
            if (!ensureBuffer())
                return -1;
            
            int toCopy=Math.min(len, (int)_remaining);
            if (off==0)
                _buffer.readBytes(b, toCopy);
            else {
                // inefficient - but we are not helped by JMS API...
                for (int i=0; i<toCopy; i++)
                    b[off++]=_buffer.readByte();
            }
            _remaining-=toCopy;
            
            if (_remaining==0)
                _buffer=null;
            
            //_log.info(" returning bytes: "+toCopy);
            
            return toCopy;
        } catch (Exception e) {
            _log.error("unexpected problem", e);
            throw new IOException();
        }
    }
    
    // Puttable
    
    public synchronized void put(Object item) throws InterruptedException {
        //_log.info("receiving: "+item);
        _inputQueue.put(item);
    }

    public synchronized boolean offer(Object item, long msecs) throws InterruptedException {
        return _inputQueue.offer(item, msecs);
    }

    // BytesMessageInputStream
    
    public void commit() {
        Utils.safePut(_endOfQueue, _inputQueue);
    }
    
    public void read(ByteBuffer buffer, int from, int to) {
        throw new UnsupportedOperationException();  // cannot be done properly over ActiveMQ
    }

}