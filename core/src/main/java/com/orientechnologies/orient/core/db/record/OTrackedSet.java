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
package com.orientechnologies.orient.core.db.record;

import java.util.HashSet;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.ORecord;

/**
 * Implementation of Set bound to a source ORecord object to keep track of changes. This avoid to call the makeDirty() by hand when
 * the set is changed.
 * 
 * @author Luca Garulli (l.garulli--at--orientechnologies.com)
 * 
 */
@SuppressWarnings("serial")
public class OTrackedSet<T> extends HashSet<T> implements ORecordElement {
	protected final ORecord<?>		sourceRecord;
	protected final static Object	ENTRY_REMOVAL	= new Object();

	public OTrackedSet(final ORecord<?> iSourceRecord) {
		this.sourceRecord = iSourceRecord;
	}

	public boolean add(final T e) {
		if (super.add(e)) {
			setDirty();
			return true;
		}
		return false;
	}

	@Override
	public boolean remove(final Object o) {
		if (super.remove(o)) {
			setDirty();
			return true;
		}
		return false;
	}

	@Override
	public void clear() {
		setDirty();
		super.clear();
	}

	public OTrackedSet<T> setDirty() {
		if (sourceRecord != null && !sourceRecord.isDirty())
			sourceRecord.setDirty();
		return this;
	}

	public void onBeforeIdentityChanged(ORID iRID) {
	}

	public void onAfterIdentityChanged(ORecord<?> iRecord) {
	}

	public boolean setDatabase(final ODatabaseRecord iDatabase) {
		if (sourceRecord != null)
			return sourceRecord.setDatabase(iDatabase);
		return false;
	}
}