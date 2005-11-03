/**
 * 
 */

package org.codehaus.wadi.sandbox.gridstate;

import java.util.HashMap;
import java.util.Map;

import javax.jms.Destination;

import org.codehaus.wadi.impl.Dispatcher;
import org.codehaus.wadi.sandbox.gridstate.messages.MoveBOToSO;
import org.codehaus.wadi.sandbox.gridstate.messages.MovePOToSO;
import org.codehaus.wadi.sandbox.gridstate.messages.MoveSOToBO;
import org.codehaus.wadi.sandbox.gridstate.messages.MoveSOToPO;
import org.codehaus.wadi.sandbox.gridstate.messages.ReadBOToPO;
import org.codehaus.wadi.sandbox.gridstate.messages.ReadPOToBO;
import org.codehaus.wadi.sandbox.gridstate.messages.WriteBOToPO;
import org.codehaus.wadi.sandbox.gridstate.messages.WritePOToBO;
import org.jgroups.Address;
import org.jgroups.Channel;
import org.jgroups.JChannel;
import org.jgroups.MembershipListener;
import org.jgroups.MessageListener;
import org.jgroups.SuspectedException;
import org.jgroups.TimeoutException;
import org.jgroups.View;
import org.jgroups.blocks.GroupRequest;
import org.jgroups.blocks.RpcDispatcher;

import EDU.oswego.cs.dl.util.concurrent.Sync;

public class JGroupsIndirectProtocol extends AbstractIndirectProtocol implements BucketConfig {
	
	protected final String _clusterName="ORG.CODEHAUS.WADI.TEST";
	protected final long _timeout;
	protected final Bucket[] _buckets;
	
	protected final Channel _channel;
	protected RpcDispatcher _dispatcher;
	protected Address _address;
	protected final Map _rvMap=new HashMap();
	
	protected final MembershipListener _membershipListener=new MembershipListener() {
		
		public void viewAccepted(View arg0) {
			_log.info("MembershipListener:viewAccepted: "+ arg0);
		}
		
		public void suspect(Address arg0) {
			_log.info("MembershipListener:suspect: "+ arg0);
		}
		
		public void block() {
			_log.info("MembershipListener:block");
		}
		
	};
	
	protected final MessageListener _messageListener=new MessageListener() {

		public void receive(org.jgroups.Message arg0) {
			_log.info("MessageListener:receive: "+arg0);
		}

		public byte[] getState() {
			_log.info("MessageListener:getState");
			return null;
		}

		public void setState(byte[] arg0) {
			_log.info("MessageListener:setState: "+arg0);
		}
		
	};
	
	
	public JGroupsIndirectProtocol(String nodeName, int numBuckets, BucketMapper mapper, long timeout) throws Exception {
		
		_buckets=new Bucket[numBuckets];
		
		for (int i=0; i<numBuckets; i++) {
			Bucket bucket=new Bucket(new LocalBucket());
			bucket.init(this);
			_buckets[i]=bucket;
		}
		
		_timeout=timeout;
		_channel=new JChannel();
		_dispatcher=new RpcDispatcher(_channel, _messageListener, _membershipListener, this, true, true);
	}
	
	public void init(ProtocolConfig config) {
		super.init(config);
		String channelName="WADI";
		try {
		_channel.connect(channelName);
		} catch (Exception e) {
			_log.error("ohoh!", e);
		}
		_address=_channel.getLocalAddress();
	}
	
	public BucketInterface createRemoteBucket() {
		return new JGroupsRemoteBucket(_address);
	}
	
	
	public void start() throws Exception {
		_log.debug("starting....");
		_dispatcher.start();
	}
	
	public void stop() throws Exception {
	      _dispatcher.stop();
	      _channel.disconnect();
	}
	
	
	public Bucket[] getBuckets() {
		return _buckets;
	}
	
	// BucketConfig
	
	public Destination getLocalDestination() {
		throw new UnsupportedOperationException("bah");
	}

	public Address getLocalAddress() {
		return _address;
	}
	
	public Dispatcher getDispatcher() {
		throw new UnsupportedOperationException("bah");
	}
	
	public Object
	syncRpc(Object address, String methodName, Object message) throws Exception
	{
		_log.info("sync rpc-ing from:"+_address+" to:"+address);
		return _dispatcher.callRemoteMethod((Address)address, methodName, new Object[]{message}, new Class[]{message.getClass()}, GroupRequest.GET_ALL, _timeout);
	}
	
	protected void
	asyncRpc(Object address, String methodName, Class[] argClasses, Object[] argInstances) throws TimeoutException, SuspectedException
	{
		_log.info("async rpc-ing from:"+_address+" to:"+address);
		_dispatcher.callRemoteMethod((Address)address, methodName, argInstances, argClasses, GroupRequest.GET_NONE, _timeout);
	}
	
	//--------------------------------------------------------------------------------
	// Get
	//--------------------------------------------------------------------------------
	
	// called on PO...
	/* (non-Javadoc)
	 * @see org.codehaus.wadi.sandbox.gridstate.Protocol#get(java.io.Object)
	 */
	public Object get(Object key) {
		Sync sync=null;
		try {
			_log.info("get - [PO] trying for lock("+key+")...");
			sync=_config.getSOSyncs().acquire(key);
			_log.info("get - [PO] ...lock("+key+") acquired - "+sync);
			Object value=null;
			Map map=_config.getMap();
			synchronized (map) {
				value=map.get(key);
			}
			if (value!=null)
				return value;
			else {
				// exchangeSendLoop GetPOToBO to BO
				Object response=null;
				try {
					Address po=_address;
					Address bo=_buckets[_config.getBucketMapper().map(key)].getAddress();
					response=syncRpc(bo, "onReadPOToBO", new ReadPOToBO(key, po));
 				} catch(Exception e) {
					_log.error("problem publishing change in state over JavaGroups", e);
				}
				
				if (response instanceof ReadBOToPO) {
					// association not present
					value=null;
				} else if (response instanceof Boolean) {
					_log.info("get "+(((Boolean)response).booleanValue()?"succeeded":"failed"));
					synchronized (_rvMap) {
						value=_rvMap.remove(key);
						_log.info("getting: "+key+"="+value+ " - "+this);
						synchronized (map) {
							map.put(key, value);
						}
					}
					return value;
				}
				
				return value;
			}
		} finally {
			_log.info("get - [PO] releasing lock("+key+") - "+sync);
			sync.release();
		}
	}
	
	public Object onMoveSOToPO(MoveSOToPO move) {
		_log.info("[PO] - onMoveSOToPO@"+_address);
		// association exists
		// associate returned value with key
		//_log.info("received "+key+"="+value+" <- SO");
		//Map map=_config.getMap();
		Object key=move.getKey();
		Object value=move.getValue();
		_log.info("putting: "+key+"="+value+ " - "+this+" localAddress:"+_address);
		if (value!=null) {
			synchronized (_rvMap) {
				_rvMap.put(key, value);
			}
		}
		return new MovePOToSO(true);
	}
	
	// called on PO...
	/* (non-Javadoc)
	 * @see org.codehaus.wadi.sandbox.gridstate.Protocol#put(java.io.Object, java.io.Object, boolean, boolean)
	 */
	public Object put(Object key, Object newValue, boolean overwrite, boolean returnOldValue) {
		_log.info("[PO] - put@"+_address);
		boolean removal=(newValue==null);
		Map map=_config.getMap();
		Sync sync=null;
		try {
			_log.info("put- [PO] trying for lock("+key+")...");
			sync=_config.getSOSyncs().acquire(key);
			_log.info("put- [PO] ...lock("+key+") acquired - "+sync);
			
			if (!removal) { // removals must do the round trip to BO
				boolean local;
				synchronized (map) {
					local=map.containsKey(key);
				}
				
				if (local) {
					// local
					if (overwrite) {
						synchronized (map) {
							Object oldValue=map.put(key, newValue);
							return returnOldValue?oldValue:null;
						}
					} else {
						return Boolean.FALSE;
					}
				}
			}
			
			try {
				// absent or remote
				// exchangeSendLoop PutPOToBO to BO
				Address po=_address;
				Address bo=_buckets[_config.getBucketMapper().map(key)].getAddress();
				Object response=syncRpc(bo, "onWritePOToBO", new WritePOToBO(key, newValue==null, overwrite, returnOldValue, po));
				
				// 2 possibilities - 
				// PutBO2PO - Absent
				if (response instanceof WriteBOToPO) {
					if (overwrite) {
						synchronized (map) {
							Object oldValue=(removal?map.remove(key):map.put(key, newValue));
							return returnOldValue?oldValue:null;
						}
					} else {
						if (((WriteBOToPO)response).getSuccess()) {
							synchronized (map) {
								map.put(key, newValue);
							}
							return Boolean.TRUE;
						} else {
							return Boolean.FALSE;
						}
					}
				} else if (response instanceof Boolean) {
					boolean success=((Boolean)response).booleanValue();
					if (returnOldValue && success) {
						Object oldValue=null;
						synchronized (_rvMap) {
							oldValue=_rvMap.remove(key);
							_log.info("getting: "+key+"="+oldValue+ " - "+this+" localAddress:"+_address);
							if (!removal && oldValue!=null) {
								synchronized (map) {
									map.put(key, newValue);
								}
							}
						}
						return oldValue;
					} else
						return null;
				} else {
					_log.error("unexpected response: "+response.getClass().getName());
					return null;
				}
			} catch (Exception e) {
				_log.error("something went wrong :-(", e);
				return null;
			}
		} finally {
			_log.info("put- [PO] releasing lock("+key+") - "+sync);
			sync.release();
		}
	}
	
	// called on BO...
	public Object onWritePOToBO(WritePOToBO write) throws Exception {
		Object key=write.getKey();
		boolean valueIsNull=write.getValueIsNull();
		boolean overwrite=write.getOverwrite();
		//boolean returnOldValue=write.getReturnOldValue();
		Address po=(Address)write.getPO();
		_log.info("[BO] - onWritePOToBO@"+_address);
		// what if we are NOT the BO anymore ?
		Bucket bucket=_buckets[_config.getBucketMapper().map(key)];
		Map bucketMap=bucket.getMap();
		Sync sync=null;
		try {
			_log.info("onWritePOToBO- [BO] trying for lock("+key+")...");
			sync=_config.getBOSyncs().acquire(key);
			_log.info("onWritePOToBO- [BO] ...lock("+key+") acquired - "+sync);
			Location location=valueIsNull?null:new JGroupsLocation(po);
			// remove or update location, remembering old value
			JGroupsLocation oldLocation=(JGroupsLocation)(location==null?bucketMap.remove(key):bucketMap.put(key, location));
			// if we are not allowed to overwrite, and we have...
			if (!overwrite && oldLocation!=null) {
				//  undo our change
				bucketMap.put(key, oldLocation);
				// send BOToPO - failure
				return new WriteBOToPO(false);
			} else if (oldLocation==null || (po.equals(oldLocation.getValue()))) {
				// if there was previously no SO, or there was, but it was PO ...
				// then there is no need to go and remove the old value from the old SO
				// send BOToPO - success
				return new WriteBOToPO(true);
			} else {
				// previous value needs removing and possibly returning...
				// send BOToSO...
				Address bo=_address;
				Object so=oldLocation.getValue();
				_log.info(""+po+"=="+so+" ? "+(po.equals(so)));
				MoveSOToBO response=(MoveSOToBO)syncRpc(so, "onMoveBOToSO", new MoveBOToSO(key, po, bo, null));
				return response.getSuccess()?Boolean.TRUE:Boolean.FALSE;
			}
		} finally {
			_log.info("onWritePOToBO- [BO] releasing lock("+key+") - "+sync);
			sync.release();
		}
	}
	
	//--------------------------------------------------------------------------------
	// Remove
	//--------------------------------------------------------------------------------
	
	// called on PO...
	public Object remove(Object key, boolean returnOldValue) {
		return put(key, null, true, returnOldValue); // a remove is a put(key, null)...
	}
	
	public Object getLocalLocation() {
		return _address;
	}

}
