package com.orientechnologies.orient.kv.index;

import com.orientechnologies.orient.core.db.record.ODatabaseBinary;
import com.orientechnologies.orient.core.db.record.ODatabaseRecord;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.serialization.serializer.stream.OStreamSerializer;
import com.orientechnologies.orient.core.type.tree.OMVRBTreeDatabase;
import com.orientechnologies.orient.kv.OSharedBinaryDatabase;

/**
 * Wrapper class for persistent tree map. It handles the asynchronous commit of changes done by the external
 * OMVRBTreePersistentAsynchThread singleton thread.
 * 
 * @author Luca Garulli
 * 
 * @param <K>
 * @param <V>
 * @see OMVRBTreePersistentAsynchThread
 */
@SuppressWarnings("serial")
public class OMVRBTreePersistentAsynch<K, V> extends OMVRBTreeDatabase<K, V> {

	public OMVRBTreePersistentAsynch(final ODatabaseRecord iDatabase, final String iClusterName,
			final OStreamSerializer iKeySerializer, final OStreamSerializer iValueSerializer) {
		super(iDatabase, iClusterName, iKeySerializer, iValueSerializer);
		OMVRBTreePersistentAsynchThread.getInstance().registerMap(this);
	}

	public OMVRBTreePersistentAsynch(final ODatabaseRecord iDatabase, final ORID iRID) {
		super(iDatabase, iRID);
		OMVRBTreePersistentAsynchThread.getInstance().registerMap(this);
	}

	/**
	 * Doesn't commit changes since they are scheduled by the external OMVRBTreePersistentAsynchThread singleton thread.
	 * 
	 * @see OMVRBTreePersistentAsynchThread#execute()
	 */
	@Override
	public void commitChanges(final ODatabaseRecord iDatabase) {
	}

	/**
	 * Commits changes for real. It's called by OMVRBTreePersistentAsynchThread singleton thread.
	 * 
	 * @see OMVRBTreePersistentAsynchThread#execute()
	 */
	public void executeCommitChanges() {
		ODatabaseBinary db = null;

		try {
			db = OSharedBinaryDatabase.acquire(database.getName());

			super.commitChanges(db);

		} catch (InterruptedException e) {
			e.printStackTrace();

		} finally {

			if (db != null)
				OSharedBinaryDatabase.release(db);
		}
	}
}
