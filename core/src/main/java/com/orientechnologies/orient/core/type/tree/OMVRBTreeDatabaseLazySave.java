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
package com.orientechnologies.orient.core.type.tree;

import com.orientechnologies.orient.core.config.OGlobalConfiguration;
import com.orientechnologies.orient.core.db.record.ODatabaseRecord;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.serialization.serializer.stream.OStreamSerializer;
import com.orientechnologies.orient.core.tx.OTransactionOptimistic;

/**
 * Collects changes all together and save them following the selected strategy. By default the map is saved automatically every
 * "maxUpdatesBeforeSave" updates (=500). "maxUpdatesBeforeSave" is configurable: 0 means no automatic save, 1 means non-lazy map
 * (save each operation) and > 1 is lazy.
 * 
 * @author Luca Garulli
 */
@SuppressWarnings("serial")
public class OMVRBTreeDatabaseLazySave<K, V> extends OMVRBTreeDatabase<K, V> {
	protected int	maxUpdatesBeforeSave;
	protected int	updates	= 0;

	public OMVRBTreeDatabaseLazySave(ODatabaseRecord iDatabase, ORID iRID) {
		super(iDatabase, iRID);
	}

	public OMVRBTreeDatabaseLazySave(ODatabaseRecord iDatabase, String iClusterName, OStreamSerializer iKeySerializer,
			OStreamSerializer iValueSerializer) {
		super(iDatabase, iClusterName, iKeySerializer, iValueSerializer);
	}

	/**
	 * Do nothing since all the changes will be committed expressly at lazySave() time or on closing.
	 */
	@Override
	public synchronized void commitChanges(final ODatabaseRecord iDatabase) {
		if (database.getTransaction() instanceof OTransactionOptimistic
				|| (maxUpdatesBeforeSave > 0 && ++updates >= maxUpdatesBeforeSave)) {
			lazySave();
			updates = 0;
		}
	}

	@Override
	public void clear() {
		super.clear();
		lazySave();
	}

	public void lazySave() {
		super.commitChanges(database);
	}

	@Override
	public void optimize() {
		lazySave();
		super.optimize();
	}

	/**
	 * Returns the maximum updates to save the map persistently.
	 * 
	 * @return 0 means no automatic save, 1 means non-lazy map (save each operation) and > 1 is lazy.
	 */
	public int getMaxUpdatesBeforeSave() {
		return maxUpdatesBeforeSave;
	}

	/**
	 * Sets the maximum updates to save the map persistently.
	 * 
	 * @param iValue
	 *          0 means no automatic save, 1 means non-lazy map (save each operation) and > 1 is lazy.
	 */
	public void setMaxUpdatesBeforeSave(final int iValue) {
		this.maxUpdatesBeforeSave = iValue;
	}

	@Override
	protected void config() {
		super.config();
		maxUpdatesBeforeSave = OGlobalConfiguration.MVRBTREE_LAZY_UPDATES.getValueAsInteger();
//
//		if (size() > 10000)
//			// AUTO ADJUST BASED ON TREE SIZE
//			// TODO: CONSIDER MEMORY
//			maxUpdatesBeforeSave = size() / 20;
	}
}
