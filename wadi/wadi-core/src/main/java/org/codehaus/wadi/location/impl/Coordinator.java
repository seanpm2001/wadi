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
package org.codehaus.wadi.location.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.wadi.group.Address;
import org.codehaus.wadi.group.Cluster;
import org.codehaus.wadi.group.Dispatcher;
import org.codehaus.wadi.group.MessageExchangeException;
import org.codehaus.wadi.group.Peer;
import org.codehaus.wadi.group.Quipu;
import org.codehaus.wadi.group.impl.AbstractCluster;
import org.codehaus.wadi.location.CoordinatorConfig;
import org.codehaus.wadi.location.partition.PartitionEvacuationResponse;
import org.codehaus.wadi.location.partition.PartitionTransferCommand;
import EDU.oswego.cs.dl.util.concurrent.Slot;
import EDU.oswego.cs.dl.util.concurrent.TimeoutException;

//it's important that the Plan is constructed from snapshotted resources (i.e. the ground doesn't
//shift under its feet), and that it is made and executed as quickly as possible - as a node could
//leave the Cluster in the meantime...

/**
 * @author <a href="mailto:jules@coredevelopers.net">Jules Gosnell</a>
 * @version $Revision:1815 $
 */
public class Coordinator implements Runnable {

	protected final Log _log=LogFactory.getLog(getClass());

	protected final Slot _flag=new Slot();

	protected final CoordinatorConfig _config;
	protected final Cluster _cluster;
	protected final Dispatcher _dispatcher;
	protected final Peer _localPeer;
	protected final int _numItems;
	protected final long _inactiveTime;

	public Coordinator(CoordinatorConfig config) {
		_config=config;
		_cluster=_config.getCluster();
        _localPeer=_cluster.getLocalPeer();
		_dispatcher=_config.getDispatcher();
		_numItems=_config.getNumPartitions();
		_inactiveTime=_config.getInactiveTime();
	}

	protected Thread _thread;
	protected Peer[] _remoteNodes;


	public synchronized void start() throws Exception {
		_log.info("starting...");
		_thread=new Thread(this, "WADI Coordinator");
		_thread.start();
		_log.info("...started");
	}

	public synchronized void stop() throws Exception {
		// somehow wake up thread
		_log.info("stopping...");
		_flag.put(Boolean.FALSE);
		_thread.join();
		_thread=null;
		_log.info("...stopped");
	}

	public synchronized void queueRebalancing() {
		_log.trace("queueing rebalancing...");
		try {
			_flag.offer(Boolean.TRUE, 0);
		} catch (InterruptedException e) {
			_log.warn("unexpected interruption");
		}
		_log.trace("...rebalancing queued");
	}

	public void run() {
        AbstractCluster._cluster.set(_cluster);
		try {
			while (_flag.take()==Boolean.TRUE) {
				rebalanceClusterState();
			}
		} catch (InterruptedException e) {
			Thread.interrupted();
			_log.warn("interrupted"); // hmmm.... - TODO
		}
	}

	public void rebalanceClusterState() {
		int failures=0;
		try {

			Map nodeMap=_cluster.getRemotePeers();

			Collection stayingNodes=nodeMap.values();
			synchronized (stayingNodes) {stayingNodes=new ArrayList(stayingNodes);} // snapshot
			stayingNodes.add(_localPeer);

			Collection l=_config.getLeavers();
			synchronized (l) {l=new ArrayList(l);} // snapshot

			Collection leavingNodes=new ArrayList();
			for (Iterator i=l.iterator(); i.hasNext(); ) {
				Address d=(Address)i.next();
				Peer leaver=getPeer(d);
				if (leaver!=null) {
					leavingNodes.add(leaver);
					stayingNodes.remove(leaver);
				}
			}

			_log.trace("--------");
			_log.trace("STAYING:");

			printNodes(stayingNodes);
			_log.trace("LEAVING:");
			printNodes(leavingNodes);
			_log.trace("--------");

			Peer [] leaving=(Peer[])leavingNodes.toArray(new Peer[leavingNodes.size()]);

			if (stayingNodes.size()==0) {
				_log.warn("we are the last node - no need to rebalance cluster");
			} else {

				Peer [] living=(Peer[])stayingNodes.toArray(new Peer[stayingNodes.size()]);

				_config.regenerateMissingPartitions(living, leaving);

				RedistributionPlan plan=new RedistributionPlan(living, leaving, _numItems);

				_log.trace("--------");
				_log.trace("BEFORE:");
				printNodes(living, leaving);
				_log.trace("--------");

				Map rvMap=_config.getRendezVousMap();
				Quipu rv=new Quipu(0);
				String correlationId=_dispatcher.nextCorrelationId();
				rvMap.put(correlationId, rv);
				execute(plan, correlationId, rv); // quipu will be incremented as participants are invited

				try {
					_log.trace("WAITING ON RENDEZVOUS");
					if (rv.waitFor(_inactiveTime)) {
						_log.trace("RENDEZVOUS SUCCESSFUL");
						//Collection results=rv.getResults();
					} else {
						_log.warn("RENDEZVOUS FAILED");
						failures++;
					}
				} catch (TimeoutException e) {
					_log.warn("timed out waiting for response", e);
					failures++;
				} catch (InterruptedException e) {
					_log.warn("unexpected interruption", e);
					failures++;
				} finally {
					rvMap.remove(correlationId);
					// somehow check all returned success.. - TODO
				}

				_log.trace("--------");
				_log.trace("AFTER:");
				printNodes(living, leaving);
				_log.trace("--------");
			}

			// send EvacuationResponses to each leaving node... - hmmm....
			Collection left=_config.getLeft();
			for (int i=0; i<leaving.length; i++) {
				Peer node=leaving[i];
				if (_log.isTraceEnabled()) _log.trace("sending evacuation response to: "+_dispatcher.getPeerName(node.getAddress()));
				if (!left.contains(node.getAddress())) {
					PartitionEvacuationResponse response=new PartitionEvacuationResponse();
                    try {
                        _dispatcher.reply(_localPeer.getAddress(), 
                                        node.getAddress(), 
                                        node.getName(), 
                                        response);
                    } catch (MessageExchangeException e) {
                        if (_log.isErrorEnabled()) {
                            _log.error("problem sending EvacuationResponse to "+DIndex.getPeerName(node));
                        }
                        failures++;
                    }
					left.add(node.getAddress());
				}
			}

		} catch (Throwable t) {
			_log.warn("problem rebalancing indeces", t);
			failures++;
		}

		if (failures>0) {
			if (_log.isWarnEnabled()) _log.warn("rebalance failed - backing off for "+_inactiveTime+" millis...");
			queueRebalancing();
		}
	}

	protected void execute(RedistributionPlan plan, String correlationId, Quipu quipu) {
		quipu.increment(); // add a safety margin of '1', so if we are caught up by acks, waiting thread does not finish until we have
		Iterator p=plan.getProducers().iterator();
		Iterator c=plan.getConsumers().iterator();

		PartitionOwner consumer=null;
		while (p.hasNext()) {
			PartitionOwner producer=(PartitionOwner)p.next();
			Collection transfers=new ArrayList();
			while (producer._deviation>0) {
				if (consumer==null)
					consumer=c.hasNext()?(PartitionOwner)c.next():null;
					if (null == consumer) {
						break;
					}
					if (producer._deviation>=consumer._deviation) {
						transfers.add(new PartitionTransfer(consumer._node.getAddress(), DIndex.getPeerName(consumer._node), consumer._deviation));
						producer._deviation-=consumer._deviation;
						consumer._deviation=0;
						consumer=null;
					} else {
						transfers.add(new PartitionTransfer(consumer._node.getAddress(), DIndex.getPeerName(consumer._node), producer._deviation));
						consumer._deviation-=producer._deviation;
						producer._deviation=0;
					}
			}

            if (0 < transfers.size()) {
                PartitionTransferCommand command = new PartitionTransferCommand((PartitionTransfer[])transfers.toArray(new PartitionTransfer[transfers.size()]));
                quipu.increment();
                if (_log.isTraceEnabled()) {
                    _log.trace("sending plan to: "+_dispatcher.getPeerName(producer._node.getAddress()));
                }
                try {
                    _dispatcher.send(_localPeer.getAddress(), producer._node.getAddress(), correlationId, command);
                } catch (MessageExchangeException e) {
                    _log.error("problem sending transfer command", e);
                }
            }
            
		}
		quipu.decrement(); // remove safety margin
	}

	protected int printNodes(Collection nodes) {
		int total=0;
		for (Iterator i=nodes.iterator(); i.hasNext(); )
			total+=printNode((Peer)i.next());
		return total;
	}

	protected void printNodes(Peer[] living, Peer[] leaving) {
		int total=0;
		for (int i=0; i<living.length; i++)
			total+=printNode(living[i]);
		for (int i=0; i<leaving.length; i++)
			total+=printNode(leaving[i]);
		if (_log.isTraceEnabled()) _log.trace("TOTAL: " + total);
	}

	protected int printNode(Peer peer) {
		if (peer!=_localPeer)
			peer=(Peer)_cluster.getRemotePeers().get(peer.getAddress());
		if (peer==null) {
			if (_log.isTraceEnabled()) _log.trace(DIndex.getPeerName(peer) + " : <unknown>");
			return 0;
		} else {
			PartitionKeys keys=DIndex.getPartitionKeys(peer);
			int amount=keys.size();
			if (_log.isTraceEnabled()) _log.trace(DIndex.getPeerName(peer) + " : " + amount + " - " + keys);
			return amount;
		}
	}

	protected Peer getPeer(Address address) {
		Peer localPeer=_localPeer;
		Address localAddress=localPeer.getAddress();
		if (address.equals(localAddress))
			return localPeer;
		else
			return (Peer)_cluster.getRemotePeers().get(address);
	}

}
