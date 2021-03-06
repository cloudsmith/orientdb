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
package com.orientechnologies.orient.core.serialization.serializer.record.string;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.orientechnologies.common.profiler.OProfiler;
import com.orientechnologies.orient.core.annotation.OAfterSerialization;
import com.orientechnologies.orient.core.annotation.OBeforeSerialization;
import com.orientechnologies.orient.core.db.ODatabaseComplex;
import com.orientechnologies.orient.core.db.OUserObject2RecordHandler;
import com.orientechnologies.orient.core.db.object.ODatabaseObjectTx;
import com.orientechnologies.orient.core.db.object.OLazyObjectMap;
import com.orientechnologies.orient.core.db.record.ODatabaseRecord;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.db.record.ORecordElement;
import com.orientechnologies.orient.core.db.record.ORecordElement.STATUS;
import com.orientechnologies.orient.core.db.record.ORecordLazyList;
import com.orientechnologies.orient.core.db.record.ORecordLazyMap;
import com.orientechnologies.orient.core.db.record.ORecordLazySet;
import com.orientechnologies.orient.core.db.record.OTrackedList;
import com.orientechnologies.orient.core.db.record.OTrackedMap;
import com.orientechnologies.orient.core.db.record.OTrackedSet;
import com.orientechnologies.orient.core.entity.OEntityManagerInternal;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.ORecord;
import com.orientechnologies.orient.core.record.ORecordInternal;
import com.orientechnologies.orient.core.record.ORecordSchemaAware;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.serialization.serializer.OStringSerializerHelper;
import com.orientechnologies.orient.core.serialization.serializer.object.OObjectSerializerHelper;
import com.orientechnologies.orient.core.serialization.serializer.string.OStringSerializerAnyStreamable;
import com.orientechnologies.orient.core.tx.OTransactionRecordEntry;

@SuppressWarnings("unchecked")
public abstract class ORecordSerializerCSVAbstract extends ORecordSerializerStringAbstract {
	public static final char	FIELD_VALUE_SEPARATOR	= ':';

	protected abstract ORecordSchemaAware<?> newObject(ODatabaseRecord iDatabase, String iClassName);

	public Object fieldFromStream(final ORecordInternal<?> iSourceRecord, final OType iType, OClass iLinkedClass, OType iLinkedType,
			final String iName, final String iValue) {

		if (iValue == null)
			return null;

		final ODatabaseRecord database = iSourceRecord.getDatabase();

		switch (iType) {
		case EMBEDDEDLIST:
		case EMBEDDEDSET:
			return embeddedCollectionFromStream(database, (ODocument) iSourceRecord, iType, iLinkedClass, iLinkedType, iValue);

		case LINKLIST:
		case LINKSET: {
			if (iValue.length() == 0)
				return null;

			// REMOVE BEGIN & END COLLECTIONS CHARACTERS IF IT'S A COLLECTION
			final String value = iValue.startsWith("[") ? iValue.substring(1, iValue.length() - 1) : iValue;

			return iType == OType.LINKLIST ? new ORecordLazyList(iSourceRecord).setStreamedContent(new StringBuilder(value))
					: new ORecordLazySet(iSourceRecord).setStreamedContent(new StringBuilder(value));
		}

		case LINKMAP: {
			if (iValue.length() == 0)
				return null;

			// REMOVE BEGIN & END MAP CHARACTERS
			String value = iValue.substring(1, iValue.length() - 1);

			@SuppressWarnings("rawtypes")
			final Map map = new ORecordLazyMap(iSourceRecord, ODocument.RECORD_TYPE);

			if (value.length() == 0)
				return map;

			final List<String> items = OStringSerializerHelper.smartSplit(value, OStringSerializerHelper.RECORD_SEPARATOR);

			// EMBEDDED LITERALS
			for (String item : items) {
				if (item != null && item.length() > 0) {
					final List<String> entry = OStringSerializerHelper.smartSplit(item, OStringSerializerHelper.ENTRY_SEPARATOR);
					if (entry.size() > 0) {
						String mapValue = entry.get(1);
						if (mapValue != null && mapValue.length() > 0)
							mapValue = mapValue.substring(1);
						map.put(fieldTypeFromStream((ODocument) iSourceRecord, OType.STRING, entry.get(0)), new ORecordId(mapValue));
					}

				}
			}
			return map;
		}

		case EMBEDDEDMAP:
			return embeddedMapFromStream((ODocument) iSourceRecord, iLinkedType, iValue);

		case LINK:
			if (iValue.length() > 1) {
				int pos = iValue.indexOf(OStringSerializerHelper.CLASS_SEPARATOR);
				if (pos > -1)
					iLinkedClass = database.getMetadata().getSchema().getClass(iValue.substring(1, pos));
				else
					pos = 0;

				return new ORecordId(iValue.substring(pos + 1));
			} else
				return null;

		case EMBEDDED:
			if (iValue.length() > 2) {
				// REMOVE BEGIN & END EMBEDDED CHARACTERS
				final String value = iValue.substring(1, iValue.length() - 1);

				// RECORD
				final Object result = OStringSerializerAnyStreamable.INSTANCE.fromStream(iSourceRecord.getDatabase(), value);
				if (result instanceof ODocument)
					((ODocument) result).addOwner(iSourceRecord);
				return result;
			} else
				return null;

		default:
			return fieldTypeFromStream((ODocument) iSourceRecord, iType, iValue);
		}
	}

	public Map<String, Object> embeddedMapFromStream(final ODocument iSourceDocument, final OType iLinkedType, final String iValue) {
		if (iValue.length() == 0)
			return null;

		// REMOVE BEGIN & END MAP CHARACTERS
		String value = iValue.substring(1, iValue.length() - 1);

		@SuppressWarnings("rawtypes")
		Map map;
		if (iLinkedType == OType.LINK || iLinkedType == OType.EMBEDDED)
			map = new ORecordLazyMap(iSourceDocument, ODocument.RECORD_TYPE);
		else
			map = new OTrackedMap<Object>(iSourceDocument);

		if (value.length() == 0)
			return map;

		final List<String> items = OStringSerializerHelper.smartSplit(value, OStringSerializerHelper.RECORD_SEPARATOR);

		// EMBEDDED LITERALS

		if (map instanceof ORecordElement)
			((ORecordElement) map).setInternalStatus(STATUS.UNMARSHALLING);

		for (String item : items) {
			if (item != null && item.length() > 0) {
				final List<String> entry = OStringSerializerHelper.smartSplit(item, OStringSerializerHelper.ENTRY_SEPARATOR);
				if (entry.size() > 0) {
					String mapValue = entry.get(1);

					final OType linkedType;

					if (iLinkedType == null)
						if (mapValue.length() > 0) {
							linkedType = getType(mapValue);
							if (linkedType == OType.LINK && !(map instanceof ORecordLazyMap)) {
								// CONVERT IT TO A LAZY MAP
								map = new ORecordLazyMap(iSourceDocument, ODocument.RECORD_TYPE);
								((ORecordElement) map).setInternalStatus(STATUS.UNMARSHALLING);
							}
						} else
							linkedType = OType.EMBEDDED;
					else
						linkedType = iLinkedType;

					if (linkedType == OType.EMBEDDED)
						mapValue = mapValue.substring(1, mapValue.length() - 1);

					final Object mapValueObject = fieldTypeFromStream(iSourceDocument, linkedType, mapValue);

					if (mapValueObject != null && mapValueObject instanceof ODocument)
						((ODocument) mapValueObject).addOwner(iSourceDocument);

					map.put(fieldTypeFromStream(iSourceDocument, OType.STRING, entry.get(0)), mapValueObject);
				}

			}
		}

		if (map instanceof ORecordElement)
			((ORecordElement) map).setInternalStatus(STATUS.LOADED);

		return map;
	}

	public void fieldToStream(final ODocument iRecord, final ODatabaseComplex<?> iDatabase, final StringBuilder iOutput,
			final OUserObject2RecordHandler iObjHandler, final OType iType, final OClass iLinkedClass, final OType iLinkedType,
			final String iName, final Object iValue, final Set<Integer> iMarshalledRecords, final boolean iSaveOnlyDirty) {
		if (iValue == null)
			return;

		final long timer = OProfiler.getInstance().startChrono();

		switch (iType) {

		case LINK: {
			final Object link = linkToStream(iOutput, iRecord, iValue);
			if (link != null)
				// OVERWRITE CONTENT
				iRecord.field(iName, link);
			OProfiler.getInstance().stopChrono("serializer.rec.str.link2string", timer);
			break;
		}

		case LINKLIST: {
			iOutput.append(OStringSerializerHelper.COLLECTION_BEGIN);

			if (iValue instanceof ORecordLazyList && ((ORecordLazyList) iValue).getStreamedContent() != null) {
				iOutput.append(((ORecordLazyList) iValue).getStreamedContent());
				OProfiler.getInstance().updateCounter("serializer.rec.str.linkList2string.cached", +1);
			} else {
				final ORecordLazyList coll;
				final Iterator<OIdentifiable> it;
				if (!(iValue instanceof ORecordLazyList)) {
					// FIRST TIME: CONVERT THE ENTIRE COLLECTION
					coll = new ORecordLazyList(iRecord);
					coll.addAll((Collection<? extends OIdentifiable>) iValue);
					((Collection<? extends OIdentifiable>) iValue).clear();

					iRecord.field(iName, coll);
					it = coll.rawIterator();
				} else {
					// LAZY LIST
					coll = (ORecordLazyList) iValue;
					if (coll.getStreamedContent() != null) {
						// APPEND STREAMED CONTENT
						iOutput.append(coll.getStreamedContent());
						OProfiler.getInstance().updateCounter("serializer.rec.str.linkList2string.cached", +1);
						it = coll.newItemsIterator();
					} else
						it = coll.rawIterator();
				}

				if (it != null && it.hasNext()) {
					final StringBuilder buffer = new StringBuilder();
					for (int items = 0; it.hasNext(); items++) {
						if (items > 0)
							buffer.append(OStringSerializerHelper.RECORD_SEPARATOR);

						final OIdentifiable item = it.next();

						linkToStream(buffer, iRecord, item);
					}

					coll.convertRecords2Links();

					iOutput.append(buffer);

					// UPDATE THE STREAM
					coll.setStreamedContent(buffer);
				}
			}

			iOutput.append(OStringSerializerHelper.COLLECTION_END);
			OProfiler.getInstance().stopChrono("serializer.rec.str.linkList2string", timer);
			break;
		}

		case LINKSET: {
			final ORecordLazySet coll;

			if (!(iValue instanceof ORecordLazySet)) {
				// FIRST TIME: CONVERT THE ENTIRE COLLECTION
				coll = new ORecordLazySet(iRecord);
				coll.addAll((Collection<? extends OIdentifiable>) iValue);
				((Collection<? extends OIdentifiable>) iValue).clear();

				iRecord.field(iName, coll);
			} else
				// LAZY SET
				coll = (ORecordLazySet) iValue;

			linkSetToStream(iOutput, iRecord, coll);
			OProfiler.getInstance().stopChrono("serializer.rec.str.linkSet2string", timer);
			break;
		}

		case LINKMAP: {
			iOutput.append(OStringSerializerHelper.MAP_BEGIN);

			Map<String, Object> map = (Map<String, Object>) iValue;

			// LINKED MAP
			if (map instanceof OLazyObjectMap<?>)
				((OLazyObjectMap<?>) map).setConvertToRecord(false);

			boolean invalidMap = false;
			try {
				int items = 0;
				for (Map.Entry<String, Object> entry : map.entrySet()) {
					if (items++ > 0)
						iOutput.append(OStringSerializerHelper.RECORD_SEPARATOR);

					fieldTypeToString(iOutput, iDatabase, OType.STRING, entry.getKey());
					iOutput.append(OStringSerializerHelper.ENTRY_SEPARATOR);
					final Object link = linkToStream(iOutput, iRecord, entry.getValue());

					if (link != null && !invalidMap)
						// IDENTITY IS CHANGED, RE-SET INTO THE COLLECTION TO RECOMPUTE THE HASH
						invalidMap = true;
				}
			} finally {
				if (map instanceof OLazyObjectMap<?>) {
					((OLazyObjectMap<?>) map).setConvertToRecord(true);
				}
			}

			if (invalidMap) {
				final ORecordLazyMap newMap = new ORecordLazyMap(iRecord, ODocument.RECORD_TYPE);

				// REPLACE ALL CHANGED ITEMS
				for (Map.Entry<String, Object> entry : map.entrySet()) {
					newMap.put(entry.getKey(), (OIdentifiable) entry.getValue());
				}
				map.clear();
				iRecord.field(iName, newMap);
			}

			iOutput.append(OStringSerializerHelper.MAP_END);
			OProfiler.getInstance().stopChrono("serializer.rec.str.linkMap2string", timer);
			break;
		}

		case EMBEDDED:
			if (iValue instanceof ODocument) {
				iOutput.append(OStringSerializerHelper.PARENTHESIS_BEGIN);
				toString((ODocument) iValue, iOutput, null, iObjHandler, iMarshalledRecords, false);
				iOutput.append(OStringSerializerHelper.PARENTHESIS_END);
			} else if (iValue != null)
				iOutput.append(iValue.toString());
			OProfiler.getInstance().stopChrono("serializer.rec.str.embed2string", timer);
			break;

		case EMBEDDEDLIST:
			embeddedCollectionToStream(iDatabase, iObjHandler, iOutput, iLinkedClass, iLinkedType, iValue, iMarshalledRecords,
					iSaveOnlyDirty);
			OProfiler.getInstance().stopChrono("serializer.rec.str.embedList2string", timer);
			break;

		case EMBEDDEDSET:
			embeddedCollectionToStream(iDatabase, iObjHandler, iOutput, iLinkedClass, iLinkedType, iValue, iMarshalledRecords,
					iSaveOnlyDirty);
			OProfiler.getInstance().stopChrono("serializer.rec.str.embedSet2string", timer);
			break;

		case EMBEDDEDMAP: {
			embeddedMapToStream(iDatabase, iObjHandler, iOutput, iLinkedClass, iLinkedType, iValue, iMarshalledRecords, iSaveOnlyDirty);
			OProfiler.getInstance().stopChrono("serializer.rec.str.embedMap2string", timer);
			break;
		}

		default:
			fieldTypeToString(iOutput, iDatabase, iType, iValue);
		}
	}

	public static StringBuilder linkSetToStream(final StringBuilder iOutput, final ODocument iRecord, final ORecordLazySet iSet) {
		final Iterator<OIdentifiable> it;
		final StringBuilder buffer;
		if (iSet.getStreamedContent() != null) {
			// APPEND STREAMED CONTENT
			buffer = iSet.getStreamedContent();
			OProfiler.getInstance().updateCounter("serializer.rec.str.linkSet2string.cached", +1);
			it = iSet.newItemsIterator();
		} else {
			buffer = new StringBuilder();
			it = iSet.rawIterator();
		}

		if (it != null && it.hasNext()) {
			for (int items = 0; it.hasNext(); items++) {
				if (buffer.length() > 0)
					buffer.append(OStringSerializerHelper.RECORD_SEPARATOR);

				final OIdentifiable item = it.next();

				linkToStream(buffer, iRecord, item);
			}
		}

		iSet.convertRecords2Links();

		iOutput.append(OStringSerializerHelper.COLLECTION_BEGIN);
		iOutput.append(buffer);
		iOutput.append(OStringSerializerHelper.COLLECTION_END);

		iSet.setStreamedContent(buffer);

		return iOutput;
	}

	public void embeddedMapToStream(final ODatabaseComplex<?> iDatabase, final OUserObject2RecordHandler iObjHandler,
			final StringBuilder iOutput, final OClass iLinkedClass, OType iLinkedType, final Object iValue,
			final Set<Integer> iMarshalledRecords, final boolean iSaveOnlyDirty) {
		iOutput.append(OStringSerializerHelper.MAP_BEGIN);

		if (iValue != null) {
			int items = 0;
			// EMBEDDED OBJECTS
			for (Entry<String, Object> o : ((Map<String, Object>) iValue).entrySet()) {
				if (items > 0)
					iOutput.append(OStringSerializerHelper.RECORD_SEPARATOR);

				if (o != null) {
					fieldTypeToString(iOutput, iDatabase, OType.STRING, o.getKey());
					iOutput.append(OStringSerializerHelper.ENTRY_SEPARATOR);

					if (o.getValue() instanceof ORecord<?>) {
						final ODocument record;
						if (o.getValue() instanceof ODocument)
							record = (ODocument) o.getValue();
						else
							record = OObjectSerializerHelper.toStream(o.getValue(), new ODocument((ODatabaseRecord) iDatabase, o.getValue()
									.getClass().getSimpleName()),
									iDatabase instanceof ODatabaseObjectTx ? ((ODatabaseObjectTx) iDatabase).getEntityManager()
											: OEntityManagerInternal.INSTANCE, iLinkedClass, iObjHandler != null ? iObjHandler
											: new OUserObject2RecordHandler() {

												public Object getUserObjectByRecord(ORecordInternal<?> iRecord, final String iFetchPlan) {
													return iRecord;
												}

												public ORecordInternal<?> getRecordByUserObject(Object iPojo, boolean iCreateIfNotAvailable) {
													return new ODocument(iLinkedClass);
												}

												public boolean existsUserObjectByRID(ORID iRID) {
													return false;
												}

												public void registerUserObject(Object iObject, ORecordInternal<?> iRecord) {
												}
											}, null, iSaveOnlyDirty);

						iOutput.append(OStringSerializerHelper.PARENTHESIS_BEGIN);
						toString(record, iOutput, null, iObjHandler, iMarshalledRecords, false);
						iOutput.append(OStringSerializerHelper.PARENTHESIS_END);
					} else if (o.getValue() instanceof Set<?>) {
						// SUB SET
						fieldTypeToString(iOutput, iDatabase, OType.EMBEDDEDSET, o.getValue());
					} else if (o.getValue() instanceof Collection<?>) {
						// SUB LIST
						fieldTypeToString(iOutput, iDatabase, OType.EMBEDDEDLIST, o.getValue());
					} else if (o.getValue() instanceof Map<?, ?>) {
						// SUB MAP
						fieldTypeToString(iOutput, iDatabase, OType.EMBEDDEDMAP, o.getValue());
					} else {
						// EMBEDDED LITERALS
						if (iLinkedType == null)
							iLinkedType = OType.getTypeByClass(o.getValue().getClass());
						fieldTypeToString(iOutput, iDatabase, iLinkedType, o.getValue());
					}
				}

				items++;
			}
		}

		iOutput.append(OStringSerializerHelper.MAP_END);
	}

	public Object embeddedCollectionFromStream(final ODatabaseRecord iDatabase, final ODocument iDocument, final OType iType,
			OClass iLinkedClass, OType iLinkedType, final String iValue) {
		if (iValue.length() == 0)
			return null;

		// REMOVE BEGIN & END COLLECTIONS CHARACTERS IF IT'S A COLLECTION
		final String value = iValue.charAt(0) == '[' ? iValue.substring(1, iValue.length() - 1) : iValue;

		final Collection<?> coll;
		if (iLinkedType == OType.LINK) {
			if (iDocument != null)
				coll = iType == OType.EMBEDDEDLIST ? new ORecordLazyList(iDocument).setStreamedContent(new StringBuilder(value))
						: new ORecordLazySet(iDocument).setStreamedContent(new StringBuilder(value));
			else
				coll = iType == OType.EMBEDDEDLIST ? new ORecordLazyList(iDatabase).setStreamedContent(new StringBuilder(value))
						: new ORecordLazySet(iDatabase).setStreamedContent(new StringBuilder(value));

			// LAZY LOADED: RETURN
			return coll;
		}

		coll = iType == OType.EMBEDDEDLIST ? new OTrackedList<Object>(iDocument) : new OTrackedSet<Object>(iDocument);

		if (value.length() == 0)
			return coll;

		if (iLinkedType == null) {
			final char begin = value.charAt(0);

			// AUTO-DETERMINE LINKED TYPE
			if (begin == OStringSerializerHelper.LINK)
				iLinkedType = OType.LINK;
		}

		if (coll instanceof ORecordElement)
			((ORecordElement) coll).setInternalStatus(STATUS.UNMARSHALLING);

		final List<String> items = OStringSerializerHelper.smartSplit(value, OStringSerializerHelper.RECORD_SEPARATOR);
		for (String item : items) {
			Object objectToAdd = null;

			if (item.length() > 2 && item.charAt(0) == OStringSerializerHelper.PARENTHESIS_BEGIN) {
				// REMOVE EMBEDDED BEGIN/END CHARS
				item = item.substring(1, item.length() - 1);

				if (item.length() > 0) {
					// EMBEDDED RECORD, EXTRACT THE CLASS NAME IF DIFFERENT BY THE PASSED (SUB-CLASS OR IT WAS PASSED NULL)
					iLinkedClass = OStringSerializerHelper.getRecordClassName(iDatabase, item, iLinkedClass);

					if (iLinkedClass != null) {
						objectToAdd = fromString(iDocument.getDatabase(), item, new ODocument(iDatabase, iLinkedClass.getName()));
					} else
						// EMBEDDED OBJECT
						objectToAdd = fieldTypeFromStream(iDocument, iLinkedType, item);
				}
			} else {
				// EMBEDDED LITERAL
				if (iLinkedType == null)
					throw new IllegalArgumentException(
							"Linked type can't be null. Probably the serialized type has not stored the type along with data");
				objectToAdd = fieldTypeFromStream(iDocument, iLinkedType, item);
			}

			if (objectToAdd != null) {
				if (objectToAdd instanceof ODocument && coll instanceof ORecordElement)
					((ODocument) objectToAdd).addOwner((ORecordElement) coll);
				((Collection<Object>) coll).add(objectToAdd);
			}
		}

		if (coll instanceof ORecordElement)
			((ORecordElement) coll).setInternalStatus(STATUS.LOADED);

		return coll;
	}

	public StringBuilder embeddedCollectionToStream(final ODatabaseComplex<?> iDatabase, final OUserObject2RecordHandler iObjHandler,
			final StringBuilder iOutput, final OClass iLinkedClass, OType iLinkedType, final Object iValue,
			final Set<Integer> iMarshalledRecords, final boolean iSaveOnlyDirty) {
		iOutput.append(OStringSerializerHelper.COLLECTION_BEGIN);

		final Iterator<Object> iterator = iValue instanceof Collection<?> ? ((Collection<Object>) iValue).iterator() : null;
		final int size = iValue instanceof Collection<?> ? ((Collection<Object>) iValue).size() : Array.getLength(iValue);

		for (int i = 0; i < size; ++i) {
			final Object o;
			if (iValue instanceof Collection<?>)
				o = iterator.next();
			else
				o = Array.get(iValue, i);

			if (i > 0)
				iOutput.append(OStringSerializerHelper.RECORD_SEPARATOR);

			if (o == null)
				continue;

			OIdentifiable id = null;
			ODocument doc = null;

			final OClass linkedClass;
			if (!(o instanceof OIdentifiable)) {
				final String fieldBound = OObjectSerializerHelper.getDocumentBoundField(o.getClass());
				if (fieldBound != null) {
					OObjectSerializerHelper.invokeCallback(o, null, OBeforeSerialization.class);
					doc = (ODocument) OObjectSerializerHelper.getFieldValue(o, fieldBound);
					OObjectSerializerHelper.invokeCallback(o, doc, OAfterSerialization.class);
					id = doc;
				}
				linkedClass = iLinkedClass;
			} else {
				id = (OIdentifiable) o;

				if (iLinkedType == null)
					// AUTO-DETERMINE LINKED TYPE
					if (id.getIdentity().isValid())
						iLinkedType = OType.LINK;
					else
						iLinkedType = OType.EMBEDDED;

				if (id instanceof ODocument) {
					doc = (ODocument) id;

					if (id.getIdentity().isTemporary())
						doc.save();

					linkedClass = doc.getSchemaClass();
				} else
					linkedClass = null;
			}

			if (id != null && iLinkedType != OType.LINK)
				iOutput.append(OStringSerializerHelper.PARENTHESIS_BEGIN);

			if (iLinkedType != OType.LINK && (linkedClass != null || doc != null)) {
				if (id == null)
					// EMBEDDED OBJECTS
					id = OObjectSerializerHelper.toStream(o, new ODocument((ODatabaseRecord) iDatabase, o.getClass().getSimpleName()),
							iDatabase instanceof ODatabaseObjectTx ? ((ODatabaseObjectTx) iDatabase).getEntityManager()
									: OEntityManagerInternal.INSTANCE, iLinkedClass, iObjHandler != null ? iObjHandler
									: new OUserObject2RecordHandler() {
										public Object getUserObjectByRecord(ORecordInternal<?> iRecord, final String iFetchPlan) {
											return iRecord;
										}

										public ORecordInternal<?> getRecordByUserObject(Object iPojo, boolean iCreateIfNotAvailable) {
											return new ODocument(linkedClass);
										}

										public boolean existsUserObjectByRID(ORID iRID) {
											return false;
										}

										public void registerUserObject(Object iObject, ORecordInternal<?> iRecord) {
										}
									}, null, iSaveOnlyDirty);

				toString(doc, iOutput, null, iObjHandler, iMarshalledRecords, false);
			} else {
				// EMBEDDED LITERALS
				fieldTypeToString(iOutput, iDatabase, iLinkedType, o);
			}

			if (id != null && iLinkedType != OType.LINK)
				iOutput.append(OStringSerializerHelper.PARENTHESIS_END);
		}

		iOutput.append(OStringSerializerHelper.COLLECTION_END);
		return iOutput;
	}

	/**
	 * Serialize the link.
	 * 
	 * @param buffer
	 * @param iParentRecord
	 * @param iFieldName
	 *          TODO
	 * @param iLinked
	 *          Can be an instance of ORID or a Record<?>
	 * @return
	 */
	private static OIdentifiable linkToStream(final StringBuilder buffer, final ORecordSchemaAware<?> iParentRecord, Object iLinked) {
		if (iLinked == null)
			// NULL REFERENCE
			return null;

		OIdentifiable resultRid = null;
		ORID rid;

		if (iLinked instanceof ORID) {
			// JUST THE REFERENCE
			rid = (ORID) iLinked;

			if (rid.isNew()) {
				// SAVE AT THE FLY AND STORE THE NEW RID
				final ORecord<?> record = rid.getRecord();

				if (record.getDatabase().getTransaction().isActive()) {
					final OTransactionRecordEntry recordEntry = record.getDatabase().getTransaction().getRecordEntry(rid);
					if (recordEntry != null)
						// GET THE CLUSTER SPECIFIED
						record.getDatabase().save((ORecordInternal<?>) record, recordEntry.clusterName);
					else
						// USE THE DEFAULT CLUSTER
						record.getDatabase().save((ORecordInternal<?>) record);

				} else
					record.getDatabase().save((ORecordInternal<?>) record);

				rid = record.getIdentity();
			}
		} else {
			if (!(iLinked instanceof ORecordInternal<?>)) {
				// NOT RECORD: TRY TO EXTRACT THE DOCUMENT IF ANY
				final String boundDocumentField = OObjectSerializerHelper.getDocumentBoundField(iLinked.getClass());
				if (boundDocumentField != null)
					iLinked = OObjectSerializerHelper.getFieldValue(iLinked, boundDocumentField);
			}

			if (!(iLinked instanceof ORecordInternal<?>))
				throw new IllegalArgumentException("Invalid object received. Expected a record but received type="
						+ iLinked.getClass().getName() + " and value=" + iLinked);

			// RECORD
			ORecordInternal<?> iLinkedRecord = (ORecordInternal<?>) iLinked;
			rid = iLinkedRecord.getIdentity();

			if (rid.isNew() || iLinkedRecord.isDirty()) {
				if (iLinkedRecord.getDatabase() == null && iParentRecord != null)
					// OVERWRITE THE DATABASE TO THE SAME OF THE PARENT ONE
					iLinkedRecord.setDatabase(iParentRecord.getDatabase());

				if (iLinkedRecord instanceof ODocument) {
					final OClass schemaClass = ((ODocument) iLinkedRecord).getSchemaClass();
					iLinkedRecord.getDatabase().save(iLinkedRecord,
							schemaClass != null ? iLinkedRecord.getDatabase().getClusterNameById(schemaClass.getDefaultClusterId()) : null);
				} else
					// STORE THE TRAVERSED OBJECT TO KNOW THE RECORD ID. CALL THIS VERSION TO AVOID CLEAR OF STACK IN THREAD-LOCAL
					iLinkedRecord.getDatabase().save(iLinkedRecord);

				iLinkedRecord.getDatabase().registerUserObject(iLinkedRecord.getDatabase().getUserObjectByRecord(iLinkedRecord, null),
						iLinkedRecord);

				resultRid = iLinkedRecord;
			}

			if (iParentRecord != null && iParentRecord.getDatabase() instanceof ODatabaseRecord) {
				final ODatabaseRecord db = iParentRecord.getDatabase();
				if (!db.isRetainRecords())
					// REPLACE CURRENT RECORD WITH ITS ID: THIS SAVES A LOT OF MEMORY
					resultRid = iLinkedRecord.getIdentity();
			}
		}

		if (rid.isValid())
			rid.toString(buffer);

		return resultRid;
	}
}
