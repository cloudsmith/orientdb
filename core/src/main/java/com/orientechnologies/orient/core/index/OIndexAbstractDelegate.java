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
package com.orientechnologies.orient.core.index;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import com.orientechnologies.common.listener.OProgressListener;
import com.orientechnologies.orient.core.db.record.ODatabaseRecord;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;

/**
 * Generic abstract wrapper for indexes. It delegates all the operations to the wrapped OIndex instance.
 * 
 * @author Luca Garulli
 * 
 */
public class OIndexAbstractDelegate implements OIndex {
	protected OIndex	delegate;

	public OIndexAbstractDelegate(final OIndex iDelegate) {
		this.delegate = iDelegate;
	}

	public OIndex create(final String iName, final ODatabaseRecord iDatabase, final String iClusterIndexName,
			final int[] iClusterIdsToIndex, final OProgressListener iProgressListener, final boolean iAutomatic) {
		return delegate.create(iName, iDatabase, iClusterIndexName, iClusterIdsToIndex, iProgressListener, iAutomatic);
	}

	public Iterator<Entry<Object, Set<OIdentifiable>>> iterator() {
		return delegate.iterator();
	}

	public Collection<OIdentifiable> get(final Object iKey) {
		return delegate.get(iKey);
	}

	public boolean contains(final Object iKey) {
		return delegate.contains(iKey);
	}

	public OIndex put(final Object iKey, final OIdentifiable iValue) {
		return delegate.put(iKey, iValue);
	}

	public boolean remove(final Object iKey) {
		return delegate.remove(iKey);
	}

	public boolean remove(final Object iKey, final OIdentifiable iRID) {
		return delegate.remove(iKey, iRID);
	}

	public int remove(final OIdentifiable iRID) {
		return delegate.remove(iRID);
	}

	public OIndex clear() {
		return delegate.clear();
	}

	public Iterable<Object> keys() {
		return delegate.keys();
	}

	public Collection<OIdentifiable> getBetween(final Object iRangeFrom, final Object iRangeTo) {
		return delegate.getBetween(iRangeFrom, iRangeTo);
	}

	public long getSize() {
		return delegate.getSize();
	}

	public OIndex lazySave() {
		return delegate.lazySave();
	}

	public OIndex delete() {
		return delegate.delete();
	}

	public String getName() {
		return delegate.getName();
	}

	public String getType() {
		return delegate.getType();
	}

	public boolean isAutomatic() {
		return delegate.isAutomatic();
	}

	public void setCallback(final OIndexCallback iCallback) {
		delegate.setCallback(iCallback);
	}

	public ODocument getConfiguration() {
		return delegate.getConfiguration();
	}

	public ORID getIdentity() {
		return delegate.getIdentity();
	}

	public void commit(final List<ODocument> iEntries) {
		delegate.commit(iEntries);
	}

	public void unload() {
		delegate.unload();
	}
}