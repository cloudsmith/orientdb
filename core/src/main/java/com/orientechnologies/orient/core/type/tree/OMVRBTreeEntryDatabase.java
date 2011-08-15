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

import java.io.IOException;

import com.orientechnologies.common.collection.OMVRBTreeEntry;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.exception.OSerializationException;
import com.orientechnologies.orient.core.id.ORID;

/**
 * Persistent TreeMap implementation that use a ODatabase instance to handle the entries. This class can be used also from the user.
 * It's transaction aware.
 * 
 * @author Luca Garulli
 * 
 * @param <K>
 *          Key type
 * @param <V>
 *          Value type
 */
@SuppressWarnings("serial")
public class OMVRBTreeEntryDatabase<K, V> extends OMVRBTreeEntryPersistent<K, V> {
	/**
	 * Called on event of splitting an entry.
	 * 
	 * @param iParent
	 *          Parent node
	 * @param iPosition
	 *          Current position
	 * @param iLeft
	 */
	public OMVRBTreeEntryDatabase(OMVRBTreeEntry<K, V> iParent, int iPosition) {
		super(iParent, iPosition);
	}

	/**
	 * Called upon unmarshalling.
	 * 
	 * @param iTree
	 *          Tree which belong
	 * @param iParent
	 *          Parent node if any
	 * @param iRecordId
	 *          Record to unmarshall
	 */
	public OMVRBTreeEntryDatabase(OMVRBTreeDatabase<K, V> iTree, OMVRBTreeEntryDatabase<K, V> iParent, ORID iRecordId)
			throws IOException {
		super(iTree, iParent, iRecordId);
		load();
	}

	public OMVRBTreeEntryDatabase(OMVRBTreeDatabase<K, V> iTree, K key, V value, OMVRBTreeEntryDatabase<K, V> iParent) {
		super(iTree, key, value, iParent);
	}

	@Override
	public OMVRBTreeEntryDatabase<K, V> load() throws IOException {
		pTree.checkForOptimization();

		try {
			record.setDatabase(ODatabaseRecordThreadLocal.INSTANCE.get());
			record.reload();
		} catch (Exception e) {
			// ERROR, MAYBE THE RECORD WASN'T CREATED
			OLogManager.instance().warn(this, "Error on loading index node record %s", e, record.getIdentity());
		}
		record.recycle(this);
		fromStream(record.toStream());

		if (OLogManager.instance().isDebugEnabled())
			OLogManager.instance().debug(this, "Loaded tree node %s (%s-%s)", record.getIdentity(), getFirstKey(), getLastKey());

		return this;
	}

	@Override
	public OMVRBTreeEntryDatabase<K, V> save() throws OSerializationException {
		if (!record.isDirty())
			return this;

		super.save();

		if (parent != null)
			if (!parent.record.getIdentity().equals(parentRid))
				OLogManager.instance().error(this,
						"[save]: Tree node %s has parentRid '%s' different by the rid of the assigned parent node: %s", record.getIdentity(),
						parentRid, parent.record.getIdentity());

		checkEntryStructure();

		if (pTree.searchNodeInCache(record.getIdentity()) != this)
			// UPDATE THE CACHE
			pTree.addNodeInCache(this);

		return this;
	}

	/**
	 * Delete all the nodes recursively. IF they are not loaded in memory, load all the tree.
	 * 
	 * @throws IOException
	 */
	@Override
	public OMVRBTreeEntryDatabase<K, V> delete() throws IOException {
		super.delete();

		// EARLY LOAD LEFT AND DELETE IT RECURSIVELY
		if (getLeft() != null)
			((OMVRBTreeEntryPersistent<K, V>) getLeft()).delete();
		leftRid = null;

		// EARLY LOAD RIGHT AND DELETE IT RECURSIVELY
		if (getRight() != null)
			((OMVRBTreeEntryPersistent<K, V>) getRight()).delete();
		rightRid = null;

		// DELETE MYSELF
		record.setDatabase(ODatabaseRecordThreadLocal.INSTANCE.get());
		record.delete();

		// FORCE REMOVING OF K/V AND SEIALIZED K/V AS WELL
		keys = null;
		values = null;
		serializedKeys = null;
		serializedValues = null;

		return this;
	}

	@Override
	protected Object keyFromStream(final int iIndex) throws IOException {
		return pTree.keySerializer.fromStream(((OMVRBTreeDatabase<K, V>) pTree).getDatabase(),
				inStream.getAsByteArray(serializedKeys[iIndex]));
	}

	@Override
	protected Object valueFromStream(final int iIndex) throws IOException {
		return pTree.valueSerializer.fromStream(((OMVRBTreeDatabase<K, V>) pTree).getDatabase(),
				inStream.getAsByteArray(serializedValues[iIndex]));
	}
}
