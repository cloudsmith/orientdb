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
package com.orientechnologies.orient.core.storage.impl.local;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.orientechnologies.common.concur.lock.OLockManager;
import com.orientechnologies.common.concur.lock.OLockManager.LOCK;
import com.orientechnologies.common.io.OFileUtils;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.common.parser.OSystemVariableResolver;
import com.orientechnologies.common.profiler.OProfiler;
import com.orientechnologies.common.profiler.OProfiler.OProfilerHookValue;
import com.orientechnologies.common.util.OArrays;
import com.orientechnologies.orient.core.OConstants;
import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.config.OGlobalConfiguration;
import com.orientechnologies.orient.core.config.OStorageClusterConfiguration;
import com.orientechnologies.orient.core.config.OStorageConfiguration;
import com.orientechnologies.orient.core.config.OStorageDataConfiguration;
import com.orientechnologies.orient.core.config.OStorageLogicalClusterConfiguration;
import com.orientechnologies.orient.core.config.OStorageMemoryClusterConfiguration;
import com.orientechnologies.orient.core.config.OStoragePhysicalClusterConfiguration;
import com.orientechnologies.orient.core.config.OStorageSegmentConfiguration;
import com.orientechnologies.orient.core.db.record.ODatabaseRecord;
import com.orientechnologies.orient.core.exception.OConcurrentModificationException;
import com.orientechnologies.orient.core.exception.OConfigurationException;
import com.orientechnologies.orient.core.exception.OStorageException;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.storage.OCluster;
import com.orientechnologies.orient.core.storage.OPhysicalPosition;
import com.orientechnologies.orient.core.storage.ORawBuffer;
import com.orientechnologies.orient.core.storage.OStorage;
import com.orientechnologies.orient.core.storage.OStorageEmbedded;
import com.orientechnologies.orient.core.storage.fs.OMMapManager;
import com.orientechnologies.orient.core.storage.impl.memory.OClusterMemory;
import com.orientechnologies.orient.core.tx.OTransaction;
import com.orientechnologies.orient.core.tx.OTransactionRealAbstract;

public class OStorageLocal extends OStorageEmbedded {
	private final int														DELETE_MAX_RETRIES;
	private final int														DELETE_WAIT_TIME;
	public static final String[]								TYPES								= { OClusterLocal.TYPE, OClusterLogical.TYPE };

	private final OLockManager<ORID, Runnable>	lockManager;
	private final Map<String, OCluster>					clusterMap					= new LinkedHashMap<String, OCluster>();
	private OCluster[]													clusters						= new OCluster[0];
	private ODataLocal[]												dataSegments				= new ODataLocal[0];

	private final OStorageLocalTxExecuter				txManager;
	private String															storagePath;
	private final OStorageVariableParser				variableParser;
	private int																	defaultClusterId		= -1;

	private OStorageConfigurationSegment				configurationSegment;

	private static String[]											ALL_FILE_EXTENSIONS	= { "ocf", ".och", ".ocl", ".oda", ".odh", ".otx" };
	private final String												PROFILER_CREATE_RECORD;
	private final String												PROFILER_READ_RECORD;
	private final String												PROFILER_UPDATE_RECORD;
	private final String												PROFILER_DELETE_RECORD;

	public OStorageLocal(final String iName, final String iFilePath, final String iMode) throws IOException {
		super(iName, iFilePath, iMode);

		File f = new File(url);

		if (f.exists() || !exists(f.getParent())) {
			// ALREADY EXISTS OR NOT LEGACY
			storagePath = OSystemVariableResolver.resolveSystemVariables(OFileUtils.getPath(new File(url).getPath()));
		} else {
			// LEGACY DB
			storagePath = OSystemVariableResolver.resolveSystemVariables(OFileUtils.getPath(new File(url).getParent()));
		}

		variableParser = new OStorageVariableParser(storagePath);
		configuration = new OStorageConfigurationSegment(this, storagePath);
		txManager = new OStorageLocalTxExecuter(this, configuration.txSegment);
		lockManager = new OLockManager<ORID, Runnable>(OGlobalConfiguration.STORAGE_LOCK_TIMEOUT.getValueAsInteger());

		PROFILER_CREATE_RECORD = "storage." + name + ".createRecord";
		PROFILER_READ_RECORD = "storage." + name + ".readRecord";
		PROFILER_UPDATE_RECORD = "storage." + name + ".updateRecord";
		PROFILER_DELETE_RECORD = "storage." + name + ".deleteRecord";

		DELETE_MAX_RETRIES = OGlobalConfiguration.FILE_MMAP_FORCE_RETRY.getValueAsInteger();
		DELETE_WAIT_TIME = OGlobalConfiguration.FILE_MMAP_FORCE_DELAY.getValueAsInteger();

		installProfilerHooks();
	}

	public synchronized void open(final String iUserName, final String iUserPassword, final Map<String, Object> iProperties) {
		final long timer = OProfiler.getInstance().startChrono();

		final boolean locked = lock.acquireExclusiveLock();

		try {
			if (open)
				// ALREADY OPENED: THIS IS THE CASE WHEN A STORAGE INSTANCE IS
				// REUSED
				return;

			addUser();

			if (!exists())
				throw new OStorageException("Can't open the storage '" + name + "' because it not exists in path: " + url);

			open = true;

			// OPEN BASIC SEGMENTS
			int pos;
			pos = registerDataSegment(new OStorageDataConfiguration(configuration, OStorage.DATA_DEFAULT_NAME));
			dataSegments[pos].open();

			pos = createClusterFromConfig(new OStoragePhysicalClusterConfiguration(configuration, OStorage.CLUSTER_INTERNAL_NAME,
					clusters.length));
			clusters[pos].open();

			configuration.load();

			pos = createClusterFromConfig(new OStoragePhysicalClusterConfiguration(configuration, OStorage.CLUSTER_INDEX_NAME,
					clusters.length));
			clusters[pos].open();

			defaultClusterId = createClusterFromConfig(new OStoragePhysicalClusterConfiguration(configuration,
					OStorage.CLUSTER_DEFAULT_NAME, clusters.length));
			clusters[defaultClusterId].open();

			// REGISTER DATA SEGMENT
			OStorageDataConfiguration dataConfig;
			for (int i = 0; i < configuration.dataSegments.size(); ++i) {
				dataConfig = configuration.dataSegments.get(i);

				pos = registerDataSegment(dataConfig);
				if (pos == -1) {
					// CLOSE AND REOPEN TO BE SURE ALL THE FILE SEGMENTS ARE
					// OPENED
					dataSegments[i].close();
					dataSegments[i] = new ODataLocal(this, dataConfig, pos);
					dataSegments[i].open();
				} else
					dataSegments[pos].open();
			}

			// REGISTER CLUSTER
			OStorageClusterConfiguration clusterConfig;
			for (int i = 0; i < configuration.clusters.size(); ++i) {
				clusterConfig = configuration.clusters.get(i);

				if (clusterConfig != null) {
					pos = createClusterFromConfig(clusterConfig);

					if (pos == -1) {
						// CLOSE AND REOPEN TO BE SURE ALL THE FILE SEGMENTS ARE
						// OPENED
						clusters[i].close();
						clusters[i] = new OClusterLocal(this, (OStoragePhysicalClusterConfiguration) clusterConfig);
						clusterMap.put(clusters[i].getName(), clusters[i]);
						clusters[i].open();
					} else {
						if (clusterConfig.getName().equals(OStorage.CLUSTER_DEFAULT_NAME))
							defaultClusterId = pos;

						clusters[pos].open();
					}
				} else {
					clusters = Arrays.copyOf(clusters, clusters.length + 1);
					clusters[i] = null;
				}
			}

			loadVersion();

			txManager.open();

		} catch (Exception e) {
			open = false;
			dataSegments = new ODataLocal[0];
			clusters = new OCluster[0];
			clusterMap.clear();
			throw new OStorageException("Can't open local storage: " + url + ", with mode=" + mode, e);
		} finally {
			lock.releaseExclusiveLock(locked);

			OProfiler.getInstance().stopChrono("storage." + name + ".open", timer);
		}
	}

	public void create(final Map<String, Object> iProperties) {
		final long timer = OProfiler.getInstance().startChrono();

		final boolean locked = lock.acquireExclusiveLock();

		try {
			if (open)
				throw new OStorageException("Can't create new storage " + name + " because it isn't closed");

			addUser();

			final File storageFolder = new File(storagePath);
			if (!storageFolder.exists())
				storageFolder.mkdir();

			if (exists())
				throw new OStorageException("Can't create new storage " + name + " because it already exists");

			open = true;

			addDataSegment(OStorage.DATA_DEFAULT_NAME);

			// ADD THE METADATA CLUSTER TO STORE INTERNAL STUFF
			addCluster(OStorage.CLUSTER_INTERNAL_NAME, OStorage.CLUSTER_TYPE.PHYSICAL);

			// ADD THE INDEX CLUSTER TO STORE, BY DEFAULT, ALL THE RECORDS OF
			// INDEXING
			addCluster(OStorage.CLUSTER_INDEX_NAME, OStorage.CLUSTER_TYPE.PHYSICAL);

			// ADD THE DEFAULT CLUSTER
			defaultClusterId = addCluster(OStorage.CLUSTER_DEFAULT_NAME, OStorage.CLUSTER_TYPE.PHYSICAL);

			configuration.create();

			txManager.create();
		} catch (OStorageException e) {
			close();
			throw e;
		} catch (IOException e) {
			close();
			throw new OStorageException("Error on creation of storage: " + name, e);

		} finally {
			lock.releaseExclusiveLock(locked);

			OProfiler.getInstance().stopChrono("storage." + name + ".create", timer);
		}
	}

	public boolean exists() {
		return exists(storagePath);
	}

	private boolean exists(String path) {
		return new File(path + "/" + OStorage.DATA_DEFAULT_NAME + ".0" + ODataLocal.DEF_EXTENSION).exists();
	}

	public void close(final boolean iForce) {
		final long timer = OProfiler.getInstance().startChrono();

		final boolean locked = lock.acquireExclusiveLock();

		try {
			if (!checkForClose(iForce))
				return;

			saveVersion();

			for (OCluster cluster : clusters)
				if (cluster != null)
					cluster.close();
			clusters = new OCluster[0];
			clusterMap.clear();

			for (ODataLocal data : dataSegments)
				data.close();
			dataSegments = new ODataLocal[0];

			txManager.close();

			configuration.close();

			level2Cache.shutdown();

			OMMapManager.flush();

			Orient.instance().unregisterStorage(this);
			open = false;
		} catch (IOException e) {
			OLogManager.instance().error(this, "Error on closing of the storage '" + name, e, OStorageException.class);

		} finally {
			lock.releaseExclusiveLock(locked);

			OProfiler.getInstance().stopChrono("storage." + name + ".close", timer);
		}
	}

	/**
	 * Deletes physically all the database files (that ends for ".och", ".ocl", ".oda", ".odh", ".otx"). Tries also to delete the
	 * container folder if the directory is empty. If files are locked, retry up to 10 times before to raise an exception.
	 */
	public void delete() {
		// CLOSE THE DATABASE BY REMOVING THE CURRENT USER
		if (open) {
			if (getUsers() > 0) {
				while (removeUser() > 0)
					;
			}
		}
		close(true);

		try {
			Orient.instance().unregisterStorage(this);
		} catch (Exception e) {
			OLogManager.instance().error(this, "Can't unregister storage", e);
		}

		final long timer = OProfiler.getInstance().startChrono();

		// GET REAL DIRECTORY
		File dbDir = new File(OSystemVariableResolver.resolveSystemVariables(url));
		if (!dbDir.exists() || !dbDir.isDirectory())
			dbDir = dbDir.getParentFile();

		final boolean locked = lock.acquireExclusiveLock();

		try {
			// RETRIES
			for (int i = 0; i < DELETE_MAX_RETRIES; ++i) {
				if (dbDir.exists() && dbDir.isDirectory()) {
					int notDeletedFiles = 0;

					// TRY TO DELETE ALL THE FILES
					for (File f : dbDir.listFiles()) {
						// DELETE ONLY THE SUPPORTED FILES
						for (String ext : ALL_FILE_EXTENSIONS)
							if (f.getPath().endsWith(ext)) {
								if (!f.delete()) {
									notDeletedFiles++;
								}
								break;
							}
					}

					if (notDeletedFiles == 0) {
						// TRY TO DELETE ALSO THE DIRECTORY IF IT'S EMPTY
						dbDir.delete();
						return;
					}
				} else
					return;

				try {
					OLogManager.instance().debug(this,
							"Can't delete database files because are locked by the OrientDB process yet: waiting a %d ms and retry %d/%d...",
							DELETE_WAIT_TIME, i, DELETE_MAX_RETRIES);

					// FORCE FINALIZATION TO COLLECT ALL THE PENDING BUFFERS
					System.gc();

					Thread.sleep(DELETE_WAIT_TIME);
				} catch (InterruptedException e) {
				}
			}

			throw new OStorageException("Can't delete database '" + name + "' located in: " + dbDir + ". Database files seems locked");

		} finally {
			lock.releaseExclusiveLock(locked);

			OProfiler.getInstance().stopChrono("storage." + name + ".delete", timer);
		}
	}

	public ODataLocal getDataSegment(final int iDataSegmentId) {
		checkOpeness();
		if (iDataSegmentId >= dataSegments.length)
			throw new IllegalArgumentException("Data segment #" + iDataSegmentId + " doesn't exist in current storage");

		final boolean locked = lock.acquireSharedLock();

		try {
			return dataSegments[iDataSegmentId];

		} finally {
			lock.releaseSharedLock(locked);
		}
	}

	/**
	 * Add a new data segment in the default segment directory and with filename equals to the cluster name.
	 */
	public int addDataSegment(final String iDataSegmentName) {
		String segmentFileName = storagePath + "/" + iDataSegmentName;
		return addDataSegment(iDataSegmentName, segmentFileName);
	}

	public int addDataSegment(String iSegmentName, final String iSegmentFileName) {
		checkOpeness();

		iSegmentName = iSegmentName.toLowerCase();

		final boolean locked = lock.acquireExclusiveLock();

		try {
			OStorageDataConfiguration conf = new OStorageDataConfiguration(configuration, iSegmentName);
			configuration.dataSegments.add(conf);

			final int pos = registerDataSegment(conf);

			if (pos == -1)
				throw new OConfigurationException("Can't add segment " + conf.name + " because it's already part of current storage");

			dataSegments[pos].create(-1);
			configuration.update();

			return pos;
		} catch (Throwable e) {
			OLogManager.instance().error(this, "Error on creation of new data segment '" + iSegmentName + "' in: " + iSegmentFileName, e,
					OStorageException.class);
			return -1;

		} finally {
			lock.releaseExclusiveLock(locked);
		}
	}

	/**
	 * Add a new cluster into the storage. Type can be: "physical" or "logical".
	 */
	public int addCluster(String iClusterName, final OStorage.CLUSTER_TYPE iClusterType, final Object... iParameters) {
		checkOpeness();

		final boolean locked = lock.acquireExclusiveLock();

		try {
			iClusterName = iClusterName.toLowerCase();

			switch (iClusterType) {
			case PHYSICAL: {
				// GET PARAMETERS
				final String clusterFileName = (String) (iParameters.length < 1 ? storagePath + "/" + iClusterName : iParameters[0]);
				final int startSize = (iParameters.length < 2 ? -1 : (Integer) iParameters[1]);

				return addPhysicalCluster(iClusterName, clusterFileName, startSize);
			}
			case LOGICAL: {
				// GET PARAMETERS
				final int physicalClusterId = (iParameters.length < 1 ? getClusterIdByName(OStorage.CLUSTER_INTERNAL_NAME)
						: (Integer) iParameters[0]);

				return addLogicalCluster(iClusterName, physicalClusterId);
			}

			case MEMORY:
				return addMemoryCluster(iClusterName);

			default:
				OLogManager.instance().exception(
						"Cluster type '" + iClusterType + "' is not supported. Supported types are: " + Arrays.toString(TYPES), null,
						OStorageException.class);
			}

		} catch (Exception e) {
			OLogManager.instance().exception("Error in creation of new cluster '" + iClusterName + "' of type: " + iClusterType, e,
					OStorageException.class);

		} finally {

			lock.releaseExclusiveLock(locked);
		}
		return -1;
	}

	public ODataLocal[] getDataSegments() {
		return dataSegments;
	}

	public OStorageLocalTxExecuter getTxManager() {
		return txManager;
	}

	public boolean dropCluster(final int iClusterId) {
		final boolean locked = lock.acquireExclusiveLock();

		try {
			if (iClusterId < 0 || iClusterId >= clusters.length)
				throw new IllegalArgumentException("Cluster id '" + iClusterId + "' is out of range of configured clusters (0-"
						+ (clusters.length - 1) + ")");

			final OCluster cluster = clusters[iClusterId];
			if (cluster == null)
				return false;

			getLevel2Cache().freeCluster(iClusterId);

			cluster.delete();

			clusterMap.remove(cluster.getName());
			clusters[iClusterId] = null;

			// UPDATE CONFIGURATION
			configuration.clusters.set(iClusterId, null);
			configuration.update();

			return true;
		} catch (Exception e) {
			OLogManager.instance().exception("Error while removing cluster '" + iClusterId + "'", e, OStorageException.class);

		} finally {
			lock.releaseExclusiveLock(locked);
		}

		return false;
	}

	public long count(final int[] iClusterIds) {
		checkOpeness();

		final boolean locked = lock.acquireSharedLock();

		try {
			long tot = 0;

			OCluster c;
			for (int i = 0; i < iClusterIds.length; ++i) {
				if (iClusterIds[i] >= clusters.length)
					throw new OConfigurationException("Cluster id " + iClusterIds[i] + "was not found");

				c = clusters[iClusterIds[i]];
				if (c != null)
					tot += c.getEntries();
			}

			return tot;

		} finally {
			lock.releaseSharedLock(locked);
		}
	}

	public long[] getClusterDataRange(final int iClusterId) {
		if (iClusterId == -1)
			throw new OStorageException("Cluster Id is invalid: " + iClusterId);

		checkOpeness();

		final boolean locked = lock.acquireSharedLock();

		try {
			return clusters[iClusterId] != null ? new long[] { clusters[iClusterId].getFirstEntryPosition(),
					clusters[iClusterId].getLastEntryPosition() } : new long[0];

		} catch (IOException e) {

			OLogManager.instance().error(this, "Error on getting last entry position", e);
			return null;

		} finally {
			lock.releaseSharedLock(locked);
		}
	}

	public long count(final int iClusterId) {
		if (iClusterId == -1)
			throw new OStorageException("Cluster Id is invalid: " + iClusterId);

		// COUNT PHYSICAL CLUSTER IF ANY
		checkOpeness();

		final boolean locked = lock.acquireSharedLock();

		try {
			return clusters[iClusterId] != null ? clusters[iClusterId].getEntries() : 0l;

		} finally {
			lock.releaseSharedLock(locked);
		}
	}

	public long createRecord(final ORecordId iRid, final byte[] iContent, final byte iRecordType) {
		checkOpeness();

		iRid.clusterPosition = createRecord(getClusterById(iRid.clusterId), iContent, iRecordType);
		return iRid.clusterPosition;
	}

	public ORawBuffer readRecord(final ODatabaseRecord iDatabase, final ORecordId iRid, final String iFetchPlan) {
		checkOpeness();
		return readRecord(getClusterById(iRid.clusterId), iRid, true);
	}

	public int updateRecord(final ORecordId iRid, final byte[] iContent, final int iVersion, final byte iRecordType) {
		checkOpeness();
		return updateRecord(getClusterById(iRid.clusterId), iRid, iContent, iVersion, iRecordType);
	}

	public boolean deleteRecord(final ORecordId iRid, final int iVersion) {
		checkOpeness();
		return deleteRecord(getClusterById(iRid.clusterId), iRid, iVersion);
	}

	public Set<String> getClusterNames() {
		checkOpeness();

		final boolean locked = lock.acquireSharedLock();

		try {

			return clusterMap.keySet();

		} finally {
			lock.releaseSharedLock(locked);
		}
	}

	public int getClusterIdByName(final String iClusterName) {
		checkOpeness();

		if (iClusterName == null)
			throw new IllegalArgumentException("Cluster name is null");

		if (iClusterName.length() == 0)
			throw new IllegalArgumentException("Cluster name is empty");

		if (Character.isDigit(iClusterName.charAt(0)))
			return Integer.parseInt(iClusterName);

		// SEARCH IT BETWEEN PHYSICAL CLUSTERS
		OCluster segment;

		final boolean locked = lock.acquireSharedLock();

		try {
			segment = clusterMap.get(iClusterName.toLowerCase());

		} finally {
			lock.releaseSharedLock(locked);
		}

		if (segment != null)
			return segment.getId();

		return -1;
	}

	public String getClusterTypeByName(final String iClusterName) {
		checkOpeness();

		if (iClusterName == null)
			throw new IllegalArgumentException("Cluster name is null");

		// SEARCH IT BETWEEN PHYSICAL CLUSTERS
		OCluster segment;

		final boolean locked = lock.acquireSharedLock();

		try {
			segment = clusterMap.get(iClusterName.toLowerCase());

		} finally {
			lock.releaseSharedLock(locked);
		}

		if (segment != null)
			return segment.getType();

		return null;
	}

	public void commit(final OTransaction iTx) {
		final boolean locked = lock.acquireSharedLock();

		try {
			txManager.commitAllPendingRecords((OTransactionRealAbstract) iTx);

			incrementVersion();
			if (OGlobalConfiguration.TX_COMMIT_SYNCH.getValueAsBoolean())
				synch();

		} catch (IOException e) {
			rollback(iTx);

		} finally {
			lock.releaseSharedLock(locked);
		}
	}

	public void rollback(final OTransaction iTx) {
	}

	public void synch() {
		checkOpeness();

		final long timer = OProfiler.getInstance().startChrono();

		final boolean locked = lock.acquireSharedLock();

		try {
			saveVersion();

			for (OCluster cluster : clusters)
				if (cluster != null)
					cluster.synch();

			for (ODataLocal data : dataSegments)
				data.synch();

		} catch (IOException e) {
			throw new OStorageException("Error on synch", e);
		} finally {
			lock.releaseSharedLock(locked);

			OProfiler.getInstance().stopChrono("storage." + name + ".synch", timer);
		}
	}

	/**
	 * Returns the list of holes as pair of position & ODataHoleInfo
	 * 
	 * @throws IOException
	 */
	public List<ODataHoleInfo> getHolesList() {
		final boolean locked = lock.acquireSharedLock();
		try {
			final List<ODataHoleInfo> holes = new ArrayList<ODataHoleInfo>();
			for (ODataLocal d : dataSegments) {
				holes.addAll(d.getHolesList());
			}
			return holes;

		} finally {
			lock.releaseSharedLock(locked);
		}
	}

	/**
	 * Returns the total number of holes.
	 * 
	 * @throws IOException
	 */
	public long getHoles() {
		final boolean locked = lock.acquireSharedLock();
		try {
			long holes = 0;
			for (ODataLocal d : dataSegments)
				holes += d.getHoles();
			return holes;

		} finally {
			lock.releaseSharedLock(locked);
		}
	}

	/**
	 * Returns the total size used by holes
	 * 
	 * @throws IOException
	 */
	public long getHoleSize() {
		final boolean locked = lock.acquireSharedLock();
		try {
			final List<ODataHoleInfo> holes = getHolesList();
			long size = 0;
			for (ODataHoleInfo h : holes) {
				if (h.dataOffset > -1 && h.size > 0)
					size += h.size;
			}
			return size;

		} finally {
			lock.releaseSharedLock(locked);
		}
	}

	public String getPhysicalClusterNameById(final int iClusterId) {
		checkOpeness();

		final boolean locked = lock.acquireSharedLock();
		try {
			if (iClusterId >= clusters.length)
				return null;

			return clusters[iClusterId] != null ? clusters[iClusterId].getName() : null;

		} finally {
			lock.releaseSharedLock(locked);
		}
	}

	@Override
	public OStorageConfiguration getConfiguration() {
		return configuration;
	}

	public int getDefaultClusterId() {
		return defaultClusterId;
	}

	public OCluster getClusterById(int iClusterId) {
		if (iClusterId == ORID.CLUSTER_ID_INVALID)
			// GET THE DEFAULT CLUSTER
			iClusterId = defaultClusterId;

		checkClusterSegmentIndexRange(iClusterId);

		return clusters[iClusterId];
	}

	@Override
	public OCluster getClusterByName(final String iClusterName) {
		final boolean locked = lock.acquireSharedLock();

		try {
			final OCluster cluster = clusterMap.get(iClusterName.toLowerCase());

			if (cluster == null)
				throw new IllegalArgumentException("Cluster " + iClusterName + " not exists");
			return cluster;

		} finally {
			lock.releaseSharedLock(locked);
		}
	}

	public String getStoragePath() {
		return storagePath;
	}

	public String getMode() {
		return mode;
	}

	public OStorageVariableParser getVariableParser() {
		return variableParser;
	}

	public Set<OCluster> getClusters() {
		final Set<OCluster> result = new HashSet<OCluster>();

		final boolean locked = lock.acquireSharedLock();
		try {

			// ADD ALL THE CLUSTERS
			for (OCluster c : clusters)
				result.add(c);

		} finally {
			lock.releaseSharedLock(locked);
		}

		return result;
	}

	protected int registerDataSegment(final OStorageDataConfiguration iConfig) throws IOException {
		checkOpeness();

		int pos = 0;

		// CHECK FOR DUPLICATION OF NAMES
		for (ODataLocal data : dataSegments)
			if (data.getName().equals(iConfig.name)) {
				// OVERWRITE CONFIG
				data.config = iConfig;
				return -1;
			}
		pos = dataSegments.length;

		// CREATE AND ADD THE NEW REF SEGMENT
		ODataLocal segment = new ODataLocal(this, iConfig, pos);

		dataSegments = OArrays.copyOf(dataSegments, dataSegments.length + 1);
		dataSegments[pos] = segment;

		return pos;
	}

	/**
	 * Create the cluster by reading the configuration received as argument and register it assigning it the higher serial id.
	 * 
	 * @param iConfig
	 *          A OStorageClusterConfiguration implementation, namely physical or logical
	 * @return The id (physical position into the array) of the new cluster just created. First is 0.
	 * @throws IOException
	 * @throws IOException
	 */
	private int createClusterFromConfig(final OStorageClusterConfiguration iConfig) throws IOException {
		OCluster cluster = clusterMap.get(iConfig.getName());

		if (cluster != null) {
			if (cluster instanceof OClusterLocal)
				// ALREADY CONFIGURED, JUST OVERWRITE CONFIG
				((OClusterLocal) cluster).config = (OStorageSegmentConfiguration) iConfig;
			return -1;
		}

		if (iConfig instanceof OStoragePhysicalClusterConfiguration)
			cluster = new OClusterLocal(this, (OStoragePhysicalClusterConfiguration) iConfig);
		else
			cluster = new OClusterLogical(this, (OStorageLogicalClusterConfiguration) iConfig);

		return registerCluster(cluster);
	}

	/**
	 * Register the cluster internally.
	 * 
	 * @param iCluster
	 *          OCluster implementation
	 * @return The id (physical position into the array) of the new cluster just created. First is 0.
	 * @throws IOException
	 */
	private int registerCluster(final OCluster iCluster) throws IOException {
		// CHECK FOR DUPLICATION OF NAMES
		if (clusterMap.containsKey(iCluster.getName()))
			throw new OConfigurationException("Can't add segment '" + iCluster.getName() + "' because it was already registered");

		// CREATE AND ADD THE NEW REF SEGMENT
		clusterMap.put(iCluster.getName(), iCluster);

		final int id = iCluster.getId();

		clusters = OArrays.copyOf(clusters, id + 1);
		clusters[id] = iCluster;

		return id;
	}

	private void checkClusterSegmentIndexRange(final int iClusterId) {
		if (iClusterId > clusters.length - 1)
			throw new IllegalArgumentException("Cluster segment #" + iClusterId + " not exists");
	}

	protected int getDataSegmentForRecord(final OCluster iCluster, final byte[] iContent) {
		// TODO: CREATE POLICY & STRATEGY TO ASSIGN THE BEST-MULTIPLE DATA
		// SEGMENT
		return 0;
	}

	protected long createRecord(final OCluster iClusterSegment, final byte[] iContent, final byte iRecordType) {
		checkOpeness();

		if (iContent == null)
			throw new IllegalArgumentException("Record is null");

		final long timer = OProfiler.getInstance().startChrono();

		final boolean locked = lock.acquireSharedLock();

		try {
			final int dataSegment = getDataSegmentForRecord(iClusterSegment, iContent);
			final ODataLocal data = getDataSegment(dataSegment);

			final ORecordId rid = new ORecordId(iClusterSegment.getId());
			rid.clusterPosition = iClusterSegment.addPhysicalPosition(-1, -1, iRecordType);

			final long dataOffset = data.addRecord(rid, iContent);

			// UPDATE THE POSITION IN CLUSTER WITH THE POSITION OF RECORD IN
			// DATA
			iClusterSegment.setPhysicalPosition(rid.clusterPosition, dataSegment, dataOffset, iRecordType);

			incrementVersion();

			return rid.clusterPosition;

		} catch (IOException e) {

			OLogManager.instance().error(this, "Error on creating record in cluster: " + iClusterSegment, e);
			return -1;
		} finally {
			lock.releaseSharedLock(locked);

			OProfiler.getInstance().stopChrono(PROFILER_CREATE_RECORD, timer);
		}
	}

	@Override
	protected ORawBuffer readRecord(final OCluster iClusterSegment, final ORecordId iRid, boolean iAtomicLock) {
		if (iRid.clusterPosition < 0)
			throw new IllegalArgumentException("Can't read the record " + iRid + " since the position is invalid");

		// NOT FOUND: SEARCH IT IN THE STORAGE
		final long timer = OProfiler.getInstance().startChrono();

		// GET LOCK ONLY IF IT'S IN ATOMIC-MODE (SEE THE PARAMETER iAtomicLock)
		// USUALLY BROWSING OPERATIONS (QUERY) AVOID ATOMIC LOCKING
		// TO IMPROVE PERFORMANCES BY LOCKING THE ENTIRE CLUSTER FROM THE
		// OUTSIDE.
		final boolean locked = iAtomicLock ? lock.acquireSharedLock() : false;

		try {
			lockManager.acquireLock(Thread.currentThread(), iRid, LOCK.SHARED);
			try {
				final OPhysicalPosition ppos = iClusterSegment.getPhysicalPosition(iRid.clusterPosition, new OPhysicalPosition());
				if (ppos == null || !checkForRecordValidity(ppos))
					// DELETED
					return null;

				final ODataLocal data = getDataSegment(ppos.dataSegment);
				return new ORawBuffer(data.getRecord(ppos.dataPosition), ppos.version, ppos.type);

			} finally {
				lockManager.releaseLock(Thread.currentThread(), iRid, LOCK.SHARED);
			}
		} catch (IOException e) {

			OLogManager.instance().error(this, "Error on reading record " + iRid + " (cluster: " + iClusterSegment + ")", e);
			return null;

		} finally {
			lock.releaseSharedLock(locked);

			OProfiler.getInstance().stopChrono(PROFILER_READ_RECORD, timer);
		}
	}

	protected int updateRecord(final OCluster iClusterSegment, final ORecordId iRid, final byte[] iContent, final int iVersion,
			final byte iRecordType) {
		final long timer = OProfiler.getInstance().startChrono();

		final boolean locked = lock.acquireSharedLock();

		try {
			lockManager.acquireLock(Thread.currentThread(), iRid, LOCK.EXCLUSIVE);
			try {
				final OPhysicalPosition ppos = iClusterSegment.getPhysicalPosition(iRid.clusterPosition, new OPhysicalPosition());
				if (!checkForRecordValidity(ppos))
					// DELETED
					return -1;

				// MVCC TRANSACTION: CHECK IF VERSION IS THE SAME
				if (iVersion > -1 && iVersion != ppos.version)
					throw new OConcurrentModificationException(
							"Can't update record "
									+ iRid
									+ " because it has been modified by another user (v"
									+ ppos.version
									+ " != v"
									+ iVersion
									+ ") in the meanwhile of current transaction. Use pessimistic locking instead of optimistic or simply re-execute the transaction");

				if (ppos.type != iRecordType)
					iClusterSegment.updateRecordType(iRid.clusterPosition, iRecordType);

				iClusterSegment.updateVersion(iRid.clusterPosition, ++ppos.version);

				final long newDataSegmentOffset;
				if (ppos.dataPosition == -1)
					// WAS EMPTY FIRST TIME, CREATE IT NOW
					newDataSegmentOffset = getDataSegment(ppos.dataSegment).addRecord(iRid, iContent);
				else
					// UPDATE IT
					newDataSegmentOffset = getDataSegment(ppos.dataSegment).setRecord(ppos.dataPosition, iRid, iContent);

				if (newDataSegmentOffset != ppos.dataPosition)
					// UPDATE DATA SEGMENT OFFSET WITH THE NEW PHYSICAL POSITION
					iClusterSegment.setPhysicalPosition(iRid.clusterPosition, ppos.dataSegment, newDataSegmentOffset, iRecordType);

				incrementVersion();

				return ppos.version;

			} finally {
				lockManager.releaseLock(Thread.currentThread(), iRid, LOCK.EXCLUSIVE);
			}
		} catch (IOException e) {

			OLogManager.instance().error(this, "Error on updating record " + iRid + " (cluster: " + iClusterSegment + ")", e);

		} finally {
			lock.releaseSharedLock(locked);

			OProfiler.getInstance().stopChrono(PROFILER_UPDATE_RECORD, timer);
		}

		return -1;
	}

	protected boolean deleteRecord(final OCluster iClusterSegment, final ORecordId iRid, final int iVersion) {
		final long timer = OProfiler.getInstance().startChrono();

		final boolean locked = lock.acquireSharedLock();

		try {
			lockManager.acquireLock(Thread.currentThread(), iRid, LOCK.EXCLUSIVE);
			try {
				final OPhysicalPosition ppos = iClusterSegment.getPhysicalPosition(iRid.clusterPosition, new OPhysicalPosition());

				if (!checkForRecordValidity(ppos))
					// ALREADY DELETED
					return false;

				// MVCC TRANSACTION: CHECK IF VERSION IS THE SAME
				if (iVersion > -1 && ppos.version != iVersion)
					throw new OConcurrentModificationException(
							"Can't delete the record "
									+ iRid
									+ " because it was modified by another user in the meanwhile of current transaction. Use pessimistic locking instead of optimistic or simply re-execute the transaction");

				iClusterSegment.removePhysicalPosition(iRid.clusterPosition, ppos);

				if (ppos.dataPosition > -1)
					getDataSegment(ppos.dataSegment).deleteRecord(ppos.dataPosition);

				incrementVersion();

				return true;

			} finally {
				lockManager.releaseLock(Thread.currentThread(), iRid, LOCK.EXCLUSIVE);
			}
		} catch (IOException e) {

			OLogManager.instance().error(this, "Error on deleting record " + iRid + "( cluster: " + iClusterSegment + ")", e);

		} finally {
			lock.releaseSharedLock(locked);

			OProfiler.getInstance().stopChrono(PROFILER_DELETE_RECORD, timer);
		}

		return false;
	}

	/***
	 * Save the version number to disk
	 * 
	 * @throws IOException
	 */
	private void saveVersion() throws IOException {
		dataSegments[0].files[0].writeHeaderLong(OConstants.SIZE_LONG, version);
	}

	/**
	 * Read the storage version from disk;
	 * 
	 * @return Long as serial version number
	 * @throws IOException
	 */
	private long loadVersion() throws IOException {
		return version = dataSegments[0].files[0].readHeaderLong(OConstants.SIZE_LONG);
	}

	/**
	 * Add a new physical cluster into the storage.
	 * 
	 * @throws IOException
	 */
	private int addPhysicalCluster(final String iClusterName, String iClusterFileName, final int iStartSize) throws IOException {
		final OStoragePhysicalClusterConfiguration config = new OStoragePhysicalClusterConfiguration(configuration, iClusterName,
				clusters.length);
		configuration.clusters.add(config);

		final OClusterLocal cluster = new OClusterLocal(this, config);
		final int id = registerCluster(cluster);

		clusters[id].create(iStartSize);
		configuration.update();
		return id;
	}

	private int addLogicalCluster(final String iClusterName, final int iPhysicalCluster) throws IOException {
		final OStorageLogicalClusterConfiguration config = new OStorageLogicalClusterConfiguration(iClusterName, clusters.length,
				iPhysicalCluster, null);

		configuration.clusters.add(config);

		final OClusterLogical cluster = new OClusterLogical(this, clusters.length, iClusterName, iPhysicalCluster);
		config.map = cluster.getRID();
		final int id = registerCluster(cluster);

		configuration.update();
		return id;
	}

	private int addMemoryCluster(final String iClusterName) throws IOException {
		final OStorageMemoryClusterConfiguration config = new OStorageMemoryClusterConfiguration(iClusterName, clusters.length);

		configuration.clusters.add(config);

		final OClusterMemory cluster = new OClusterMemory(clusters.length, iClusterName);
		final int id = registerCluster(cluster);
		configuration.update();

		return id;
	}

	public long getSize() {
		long size = 0;

		for (ODataLocal d : dataSegments)
			size += d.getFilledUpTo();

		for (OCluster c : clusters)
			if (c != null)
				size += c.getSize();

		return size;
	}

	public OStorageConfigurationSegment getConfigurationSegment() {
		return configurationSegment;
	}

	private void installProfilerHooks() {
		OProfiler.getInstance().registerHookValue("storage." + name + ".data.holes", new OProfilerHookValue() {
			public Object getValue() {
				return getHoles();
			}
		});
		OProfiler.getInstance().registerHookValue("storage." + name + ".data.holeSize", new OProfilerHookValue() {
			public Object getValue() {
				return getHoleSize();
			}
		});
	}
}
