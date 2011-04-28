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
package com.orientechnologies.orient.client.remote;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.orientechnologies.common.concur.resource.OSharedResourceAdaptive;
import com.orientechnologies.orient.core.cache.OStorageRecordCache;
import com.orientechnologies.orient.core.command.OCommandRequestText;
import com.orientechnologies.orient.core.config.OStorageConfiguration;
import com.orientechnologies.orient.core.db.record.ODatabaseRecord;
import com.orientechnologies.orient.core.dictionary.ODictionary;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.record.ORecordInternal;
import com.orientechnologies.orient.core.storage.OCluster;
import com.orientechnologies.orient.core.storage.OPhysicalPosition;
import com.orientechnologies.orient.core.storage.ORawBuffer;
import com.orientechnologies.orient.core.storage.OStorage;
import com.orientechnologies.orient.core.tx.OTransaction;

/**
 * Wrapper of OStorageRemote that maintains the sessionId. It's bound to the ODatabase and allow to use the shared OStorageRemote.
 */
@SuppressWarnings("unchecked")
public class OStorageRemoteThread implements OStorage {
	private static AtomicInteger	sessionSerialId	= new AtomicInteger(-1);

	private OStorageRemote				delegate;
	private int										sessionId;

	public OStorageRemoteThread(final OStorageRemote iSharedStorage) {
		delegate = iSharedStorage;
		sessionId = sessionSerialId.decrementAndGet();
	}

	public void open(final String iUserName, final String iUserPassword, final Map<String, Object> iOptions) {
		delegate.setSessionId(sessionId);
		delegate.open(iUserName, iUserPassword, iOptions);
		sessionId = delegate.getSessionId();
	}

	public void create(final Map<String, Object> iOptions) {
		delegate.setSessionId(sessionId);
		delegate.create(iOptions);
		sessionId = delegate.getSessionId();
	}

	public void close(boolean iForce) {
		delegate.setSessionId(sessionId);
		delegate.setSessionId(sessionId);
		delegate.close(iForce);
	}

	public boolean removeCluster(final String iClusterName) {
		delegate.setSessionId(sessionId);
		return delegate.removeCluster(iClusterName);
	}

	public OStorageRecordCache getCache() {
		delegate.setSessionId(sessionId);
		return delegate.getCache();
	}

	public int getUsers() {
		delegate.setSessionId(sessionId);
		return delegate.getUsers();
	}

	public int addUser() {
		delegate.setSessionId(sessionId);
		return delegate.addUser();
	}

	public OSharedResourceAdaptive getLock() {
		delegate.setSessionId(sessionId);
		return delegate.getLock();
	}

	public void setSessionId(final int iSessionId) {
		delegate.setSessionId(sessionId);
		delegate.setSessionId(iSessionId);
	}

	public boolean exists() {
		delegate.setSessionId(sessionId);
		return delegate.exists();
	}

	public int removeUser() {
		delegate.setSessionId(sessionId);
		return delegate.removeUser();
	}

	public void close() {
		delegate.setSessionId(sessionId);
		delegate.close();
	}

	public void delete() {
		delegate.setSessionId(sessionId);
		delegate.delete();
	}

	public Set<String> getClusterNames() {
		delegate.setSessionId(sessionId);
		return delegate.getClusterNames();
	}

	public long createRecord(final ORecordId iRid, final byte[] iContent, final byte iRecordType) {
		delegate.setSessionId(sessionId);
		return delegate.createRecord(iRid, iContent, iRecordType);
	}

	public ORawBuffer readRecord(final ODatabaseRecord iDatabase, final ORecordId iRid, final String iFetchPlan) {
		delegate.setSessionId(sessionId);
		return delegate.readRecord(iDatabase, iRid, iFetchPlan);
	}

	public int updateRecord(final ORecordId iRid, final byte[] iContent, final int iVersion, final byte iRecordType) {
		delegate.setSessionId(sessionId);
		return delegate.updateRecord(iRid, iContent, iVersion, iRecordType);
	}

	public String toString() {
		delegate.setSessionId(sessionId);
		return delegate.toString();
	}

	public boolean deleteRecord(final ORecordId iRid, final int iVersion) {
		delegate.setSessionId(sessionId);
		return delegate.deleteRecord(iRid, iVersion);
	}

	public long count(final int iClusterId) {
		delegate.setSessionId(sessionId);
		return delegate.count(iClusterId);
	}

	public long[] getClusterDataRange(final int iClusterId) {
		delegate.setSessionId(sessionId);
		return delegate.getClusterDataRange(iClusterId);
	}

	public long getSize() {
		delegate.setSessionId(sessionId);
		return delegate.getSize();
	}

	public long countRecords() {
		delegate.setSessionId(sessionId);
		return delegate.countRecords();
	}

	public long count(final int[] iClusterIds) {
		delegate.setSessionId(sessionId);
		return delegate.count(iClusterIds);
	}

	public long count(final String iClassName) {
		delegate.setSessionId(sessionId);
		return delegate.count(iClassName);
	}

	public Object command(final OCommandRequestText iCommand) {
		delegate.setSessionId(sessionId);
		return delegate.command(iCommand);
	}

	public void commit(final OTransaction iTx) {
		delegate.setSessionId(sessionId);
		delegate.commit(iTx);
	}

	public int getClusterIdByName(final String iClusterName) {
		delegate.setSessionId(sessionId);
		return delegate.getClusterIdByName(iClusterName);
	}

	public String getClusterTypeByName(final String iClusterName) {
		delegate.setSessionId(sessionId);
		return delegate.getClusterTypeByName(iClusterName);
	}

	public int getDefaultClusterId() {
		delegate.setSessionId(sessionId);
		return delegate.getDefaultClusterId();
	}

	public int addCluster(final String iClusterName, final CLUSTER_TYPE iClusterType, final Object... iArguments) {
		delegate.setSessionId(sessionId);
		return delegate.addCluster(iClusterName, iClusterType, iArguments);
	}

	public boolean removeCluster(final int iClusterId) {
		delegate.setSessionId(sessionId);
		return delegate.removeCluster(iClusterId);
	}

	public int addDataSegment(final String iDataSegmentName) {
		delegate.setSessionId(sessionId);
		return delegate.addDataSegment(iDataSegmentName);
	}

	public int addDataSegment(final String iSegmentName, final String iSegmentFileName) {
		delegate.setSessionId(sessionId);
		return delegate.addDataSegment(iSegmentName, iSegmentFileName);
	}

	public <REC extends ORecordInternal<?>> REC dictionaryPut(final ODatabaseRecord iDatabase, final String iKey,
			final ORecordInternal<?> iRecord) {
		delegate.setSessionId(sessionId);
		return (REC) delegate.dictionaryPut(iDatabase, iKey, iRecord);
	}

	public <REC extends ORecordInternal<?>> REC dictionaryLookup(final ODatabaseRecord iDatabase, final String iKey) {
		delegate.setSessionId(sessionId);
		return (REC) delegate.dictionaryLookup(iDatabase, iKey);
	}

	public <REC extends ORecordInternal<?>> REC dictionaryRemove(final ODatabaseRecord iDatabase, final Object iKey) {
		delegate.setSessionId(sessionId);
		return (REC) delegate.dictionaryRemove(iDatabase, iKey);
	}

	public int dictionarySize(final ODatabaseRecord iDatabase) {
		delegate.setSessionId(sessionId);
		return delegate.dictionarySize(iDatabase);
	}

	public ODictionary<?> createDictionary(final ODatabaseRecord iDatabase) throws Exception {
		delegate.setSessionId(sessionId);
		return delegate.createDictionary(iDatabase);
	}

	public Set<String> dictionaryKeys(final ODatabaseRecord iDatabase) {
		delegate.setSessionId(sessionId);
		return delegate.dictionaryKeys(iDatabase);
	}

	public void synch() {
		delegate.setSessionId(sessionId);
		delegate.synch();
	}

	public String getPhysicalClusterNameById(final int iClusterId) {
		delegate.setSessionId(sessionId);
		return delegate.getPhysicalClusterNameById(iClusterId);
	}

	public Collection<OCluster> getClusters() {
		delegate.setSessionId(sessionId);
		return delegate.getClusters();
	}

	public OCluster getClusterById(final int iId) {
		delegate.setSessionId(sessionId);
		return delegate.getClusterById(iId);
	}

	public long getVersion() {
		delegate.setSessionId(sessionId);
		return delegate.getVersion();
	}

	public boolean isPermanentRequester() {
		delegate.setSessionId(sessionId);
		return delegate.isPermanentRequester();
	}

	public void updateClusterConfiguration(final byte[] iContent) {
		delegate.setSessionId(sessionId);
		delegate.updateClusterConfiguration(iContent);
	}

	public OStorageConfiguration getConfiguration() {
		delegate.setSessionId(sessionId);
		return delegate.getConfiguration();
	}

	public boolean isClosed() {
		delegate.setSessionId(sessionId);
		return delegate.isClosed();
	}

	public boolean checkForRecordValidity(final OPhysicalPosition ppos) {
		delegate.setSessionId(sessionId);
		return delegate.checkForRecordValidity(ppos);
	}

	public String getName() {
		delegate.setSessionId(sessionId);
		return delegate.getName();
	}

	public String getURL() {
		delegate.setSessionId(sessionId);
		return delegate.getURL();
	}
}