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
package org.codehaus.wadi.sandbox.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.wadi.sandbox.Contextualiser;
import org.codehaus.wadi.sandbox.ContextualiserConfig;
import org.codehaus.wadi.sandbox.Emoter;
import org.codehaus.wadi.sandbox.Evicter;
import org.codehaus.wadi.sandbox.EvicterConfig;
import org.codehaus.wadi.sandbox.Immoter;
import org.codehaus.wadi.sandbox.Locker;
import org.codehaus.wadi.sandbox.Motable;

import EDU.oswego.cs.dl.util.concurrent.Sync;


/**
 * Basic implementation for Contextualisers which maintain a local Map of references
 * to Motables.
 *
 * @author <a href="mailto:jules@coredevelopers.net">Jules Gosnell</a>
 * @version $Revision$
 */
public abstract class AbstractExclusiveContextualiser extends AbstractMotingContextualiser implements EvicterConfig {
	protected final Map _map;
    protected final Evicter _evicter;

	public AbstractExclusiveContextualiser(Contextualiser next, Locker locker, Evicter evicter, Map map) {
		super(next, locker);
		_map=map;
        _evicter=evicter;
	}

	protected String _stringPrefix="<"+getClass().getName()+":";
	protected String _stringSuffix=">";
	public String toString() {
		return new StringBuffer(_stringPrefix).append(_map.size()).append(_stringSuffix).toString();
	}

	public Motable get(String id) {return (Motable)_map.get(id);}

	// TODO - sometime figure out how to make this a wrapper around AbstractChainedContextualiser.handle() instead of a replacement...
	public boolean handle(HttpServletRequest hreq, HttpServletResponse hres, FilterChain chain, String id, Immoter immoter, Sync motionLock) throws IOException, ServletException {
	    Motable emotable=get(id);
	    if (emotable==null)
	        return false; // we cannot proceed without the session...

	    if (immoter!=null) {
	        return promote(hreq, hres, chain, id, immoter, motionLock, emotable); // motionLock will be released here...
	    } else {
	        return false;
	    }
	}

	public Emoter getEvictionEmoter(){return getEmoter();}

    protected void unload() {
        // get an immoter from the first shared Contextualiser
        Immoter immoter=_next.getSharedDemoter();
        Emoter emoter=getEmoter();

        // emote all our Motables using it
        RankedRWLock.setPriority(RankedRWLock.EVICTION_PRIORITY);
        Collection copy=null;
        synchronized (_map) {copy=new ArrayList(_map.entrySet());}
        int s=copy.size();

        for (Iterator i=copy.iterator(); i.hasNext(); ) {
            Map.Entry e=(Map.Entry)i.next();
            String id=(String)e.getKey();
            Motable emotable=(Motable)e.getValue();
            Utils.mote(emoter, immoter, emotable, id);
        }
        RankedRWLock.setPriority(RankedRWLock.NO_PRIORITY);
        if (_log.isInfoEnabled()) _log.info("distributed sessions: "+s);
    }

    public void init(ContextualiserConfig config) {
        super.init(config);
        _evicter.init(this);
    }

    public void start() throws Exception {
        super.start();
        _evicter.start();
    }

    public void stop() throws Exception {
        unload();
        _evicter.stop();
        super.stop();
    }

    public void destroy() {
        _evicter.destroy();
        super.destroy();
    }

    public int loadMotables(Emoter emoter, Immoter immoter){return 0;} // MappedContextualisers are all Exclusive

    public Evicter getEvicter(){return _evicter;}

    public Immoter getDemoter(String id, Motable motable) {
        long time=System.currentTimeMillis();
        if (getEvicter().test(motable, motable.getTimeToLive(time), time))
            return _next.getDemoter(id, motable);
        else
            return getImmoter();
    }

    // EvicterConfig

    // BestEffortEvicters

    public Map getMap(){return _map;}

    // EvicterConfig

    public Timer getTimer() {return _config.getTimer();}

    // BestEffortEvicters

    public Sync getEvictionLock(String id, Motable motable) {
        return _locker.getLock(id, motable);
    }

    public void demote(Motable emotable) {
        String id=emotable.getId();
        Immoter immoter=_next.getDemoter(id, emotable);
        Emoter emoter=getEvictionEmoter();
        Utils.mote(emoter, immoter, emotable, id);
    }

    // StrictEvicters
    public int getMaxInactiveInterval() {return _config.getMaxInactiveInterval();}


}