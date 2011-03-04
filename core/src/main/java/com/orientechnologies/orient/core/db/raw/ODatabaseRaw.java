/*
 * Copyright 1999-2010 Luca Garulli (l.garulli--at--orientechnologies.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.orientechnologies.orient.core.db.raw;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.cache.ODatabaseRecordCache;
import com.orientechnologies.orient.core.config.OGlobalConfiguration;
import com.orientechnologies.orient.core.db.ODatabase;
import com.orientechnologies.orient.core.db.ODatabaseLifecycleListener;
import com.orientechnologies.orient.core.db.ODatabaseListener;
import com.orientechnologies.orient.core.db.record.ODatabaseRecord;
import com.orientechnologies.orient.core.exception.ODatabaseException;
import com.orientechnologies.orient.core.exception.ORecordNotFoundException;
import com.orientechnologies.orient.core.intent.OIntent;
import com.orientechnologies.orient.core.storage.ORawBuffer;
import com.orientechnologies.orient.core.storage.OStorage;

/**
 * Lower level ODatabase implementation. It's extended or wrapped by all the others.
 * 
 * @author Luca Garulli (l.garulli--at--orientechnologies.com)
 * 
 */
@SuppressWarnings("unchecked")
public class ODatabaseRaw implements ODatabase {
	protected int										id;
	protected String								url;
	protected OStorage							storage;
	protected STATUS								status;
	protected OIntent								currentIntent;

	private ODatabaseRecord					databaseOwner;
	private Map<String, Object>			properties	= new HashMap<String, Object>();
	private List<ODatabaseListener>	listeners		= new ArrayList<ODatabaseListener>();

	private ODatabaseRecordCache		level1Cache;
	private boolean									useCache;

	public enum STATUS {
		OPEN, CLOSED
	}

	public ODatabaseRaw(final String iURL) {
		try {
			url = iURL;
			status = STATUS.CLOSED;

			// SET DEFAULT PROPERTIES
			setProperty("fetch-max", 50);

			useCache = OGlobalConfiguration.DB_USE_CACHE.getValueAsBoolean();

		} catch (Throwable t) {
			throw new ODatabaseException("Error on opening database '" + iURL + "'", t);
		}
	}

	public <DB extends ODatabase> DB open(final String iUserName, final String iUserPassword) {
		try {
			if (status == STATUS.OPEN)
				throw new IllegalStateException("Database " + getName() + " is already open");

			if (storage == null)
				storage = Orient.instance().loadStorage(url);
			storage.open(getId(), iUserName, iUserPassword, properties);

			// WAKE UP DB LIFECYCLE LISTENER
			for (ODatabaseLifecycleListener it : Orient.instance().getDbLifecycleListeners())
				it.onOpen(getDatabaseOwner());

			// WAKE UP LISTENERS
			for (ODatabaseListener listener : listeners)
				try {
					listener.onOpen(this);
				} catch (Throwable t) {
				}

			level1Cache.startup();

			status = STATUS.OPEN;
		} catch (ODatabaseException e) {
			throw e;
		} catch (Exception e) {
			throw new ODatabaseException("Can't open database", e);
		}
		return (DB) this;
	}

	public <DB extends ODatabase> DB create() {
		try {
			if (status == STATUS.OPEN)
				throw new IllegalStateException("Database " + getName() + " is already open");

			if (storage == null)
				storage = Orient.instance().loadStorage(url);
			storage.create(properties);

			// WAKE UP DB LIFECYCLE LISTENER
			for (ODatabaseLifecycleListener it : Orient.instance().getDbLifecycleListeners())
				it.onOpen(getDatabaseOwner());

			// WAKE UP LISTENERS
			for (ODatabaseListener listener : listeners)
				try {
					listener.onCreate(this);
				} catch (Throwable t) {
				}

			level1Cache.startup();

			status = STATUS.OPEN;
		} catch (Exception e) {
			throw new ODatabaseException("Can't create database", e);
		}
		return (DB) this;
	}

	public void delete() {
		close(true);

		try {
			if (storage == null)
				storage = Orient.instance().loadStorage(url);

			storage.delete();
			storage = null;

			// WAKE UP LISTENERS
			for (ODatabaseListener listener : listeners)
				try {
					listener.onDelete(this);
				} catch (Throwable t) {
				}

			status = STATUS.CLOSED;
		} catch (Exception e) {
			throw new ODatabaseException("Can't delete database", e);
		}
	}

	public boolean exists() {
		if (status == STATUS.OPEN)
			return true;

		if (storage == null)
			storage = Orient.instance().loadStorage(url);

		return storage.exists();
	}

	public long countClusterElements(final String iClusterName) {
		return storage.count(getClusterIdByName(iClusterName));
	}

	public long countClusterElements(final int iClusterId) {
		return storage.count(iClusterId);
	}

	public long countClusterElements(final int[] iClusterIds) {
		return storage.count(iClusterIds);
	}

	public ORawBuffer read(final int iClusterId, final long iPosition, final String iFetchPlan) {
		if (iClusterId < 0 || iPosition < 0)
			return null;

		try {
			return storage.readRecord(databaseOwner, id, iClusterId, iPosition, iFetchPlan);

		} catch (Throwable t) {
			throw new ODatabaseException("Error on retrieving record #" + iPosition + " in cluster '"
					+ storage.getPhysicalClusterNameById(iClusterId) + "'", t);
		}
	}

	public long save(final int iClusterId, long iPosition, final byte[] iContent, final int iVersion, final byte iRecordType) {
		try {
			if (iPosition < 0) {
				// CREATE
				return storage.createRecord(iClusterId, iContent, iRecordType);

			} else {
				// UPDATE
				return storage.updateRecord(id, iClusterId, iPosition, iContent, iVersion, iRecordType);
			}
		} catch (Throwable t) {
			throw new ODatabaseException("Error on saving record in cluster id: " + iClusterId + ", position: " + iPosition, t);
		}
	}

	public void delete(final String iClusterName, final long iPosition, final int iVersion) {
		delete(getClusterIdByName(iClusterName), iPosition, iVersion);
	}

	public void delete(final int iClusterId, final long iPosition, final int iVersion) {
		try {
			if (!storage.deleteRecord(id, iClusterId, iPosition, iVersion))
				throw new ORecordNotFoundException("The record with id '" + iClusterId + ":" + iPosition + "' was not found");

		} catch (Exception e) {
			OLogManager.instance().exception("Error on deleting record #%d in cluster '%s'", e, ODatabaseException.class, iPosition,
					storage.getPhysicalClusterNameById(iClusterId));
		}
	}

	public OStorage getStorage() {
		return storage;
	}

	public boolean isUseCache() {
		return useCache;
	}

	public void setUseCache(final boolean useCache) {
		this.useCache = useCache;
	}

	public boolean isClosed() {
		return status == STATUS.CLOSED;
	}

	public String getName() {
		return storage != null ? storage.getName() : "<no-name>";
	}

	public String getURL() {
		return storage != null ? storage.getURL() : "<no-url>";
	}

	@Override
	public void finalize() {
		close();
	}

	public void close() {
		close(true);
	}

	public int getId() {
		return id;
	}

	public String getClusterType(final String iClusterName) {
		return storage.getClusterTypeByName(iClusterName);
	}

	public int getClusterIdByName(final String iClusterName) {
		return storage.getClusterIdByName(iClusterName);
	}

	public String getClusterNameById(final int iClusterId) {
		if (iClusterId == -1)
			return null;

		// PHIYSICAL CLUSTER
		return storage.getPhysicalClusterNameById(iClusterId);
	}

	public int addLogicalCluster(final String iClusterName, final int iPhyClusterContainerId) {
		return storage.addCluster(iClusterName, OStorage.CLUSTER_TYPE.LOGICAL, iPhyClusterContainerId);
	}

	public int addPhysicalCluster(final String iClusterName, final String iClusterFileName, final int iStartSize) {
		return storage.addCluster(iClusterName, OStorage.CLUSTER_TYPE.PHYSICAL, iClusterFileName, iStartSize);
	}

	public int addDataSegment(final String iSegmentName, final String iSegmentFileName) {
		return storage.addDataSegment(iSegmentName, iSegmentFileName);
	}

	public Collection<String> getClusterNames() {
		return storage.getClusterNames();
	}

	public ODatabaseRecordCache getCache() {
		return level1Cache;
	}

	public int getDefaultClusterId() {
		return storage.getDefaultClusterId();
	}

	public void declareIntent(final OIntent iIntent) {
		if (currentIntent != null)
			// END CURRENT INTENT
			currentIntent.end(this);

		currentIntent = iIntent;

		if (iIntent != null)
			iIntent.begin(this);
	}

	public ODatabaseRecord getDatabaseOwner() {
		return databaseOwner;
	}

	public ODatabaseRaw setOwner(final ODatabaseRecord iOwner) {
		databaseOwner = iOwner;
		level1Cache = new ODatabaseRecordCache(databaseOwner);
		return this;
	}

	public Object setProperty(final String iName, final Object iValue) {
		if (iValue == null)
			return properties.remove(iName);
		else
			return properties.put(iName, iValue);
	}

	public Object getProperty(final String iName) {
		return properties.get(iName);
	}

	public Iterator<Entry<String, Object>> getProperties() {
		return properties.entrySet().iterator();
	}

	public void registerListener(final ODatabaseListener iListener) {
		listeners.add(iListener);
	}

	public void unregisterListener(final ODatabaseListener iListener) {
		for (int i = 0; i < listeners.size(); ++i)
			if (listeners.get(i) == iListener) {
				listeners.remove(i);
				break;
			}
	}

	public List<ODatabaseListener> getListeners() {
		return listeners;
	}

	protected void close(boolean iCloseStorageToo) {
		if (status != STATUS.OPEN)
			return;

		// WAKE UP DB LIFECYCLE LISTENER
		for (ODatabaseLifecycleListener it : Orient.instance().getDbLifecycleListeners())
			it.onClose(getDatabaseOwner());

		// WAKE UP LISTENERS
		for (ODatabaseListener listener : listeners)
			try {
				listener.onClose(this);
			} catch (Throwable t) {
				t.printStackTrace();
			}
		listeners.clear();

		if (storage != null && iCloseStorageToo)
			storage.removeUser();
		
		storage = null;
		status = STATUS.CLOSED;

		level1Cache.shutdown();
	}

	@Override
	public String toString() {
		return "OrientDB[" + getStorage().getURL() + "]";
	}
}
