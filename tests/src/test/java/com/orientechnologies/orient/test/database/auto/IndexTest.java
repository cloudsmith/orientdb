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
package com.orientechnologies.orient.test.database.auto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import com.orientechnologies.common.profiler.OProfiler;
import com.orientechnologies.orient.client.remote.OEngineRemote;
import com.orientechnologies.orient.client.remote.OStorageRemote;
import com.orientechnologies.orient.client.remote.OStorageRemoteThread;
import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.db.object.ODatabaseObjectTx;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.index.OIndexException;
import com.orientechnologies.orient.core.metadata.schema.OProperty.INDEX_TYPE;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.orientechnologies.orient.test.database.base.OrientTest;
import com.orientechnologies.orient.test.domain.whiz.Profile;

@Test(groups = { "index" })
public class IndexTest {
	private ODatabaseObjectTx	database;
	protected long						startRecordNumber;

	@Parameters(value = "url")
	public IndexTest(String iURL) {
		Orient.instance().registerEngine(new OEngineRemote());

		database = new ODatabaseObjectTx(iURL);
		database.getEntityManager().registerEntityClasses("com.orientechnologies.orient.test.domain");
	}

	@Test
	public void testDuplicatedIndexOnUnique() {
		database.open("admin", "admin");

		Profile jayMiner = new Profile("Jay", "Jay", "Miner", null);
		database.save(jayMiner);

		Profile jacobMiner = new Profile("Jay", "Jacob", "Miner", null);

		try {
			database.save(jacobMiner);

			// IT SHOULD GIVE ERROR ON DUPLICATED KEY
			Assert.assertTrue(false);

		} catch (OIndexException e) {
			Assert.assertTrue(true);
		}
		database.close();
	}

	@Test(dependsOnMethods = "testDuplicatedIndexOnUnique")
	public void testUseOfIndex() {
		database.open("admin", "admin");

		final List<Profile> result = database.command(new OSQLSynchQuery<Profile>("select * from Profile where nick = 'Jay'"))
				.execute();

		Assert.assertFalse(result.isEmpty());

		Profile record;
		for (int i = 0; i < result.size(); ++i) {
			record = result.get(i);

			OrientTest.printRecord(i, record);

			Assert.assertTrue(record.getName().toString().equalsIgnoreCase("Jay"));
		}

		database.close();
	}

	@Test(dependsOnMethods = "testDuplicatedIndexOnUnique")
	public void testIndexEntries() {
		database.open("admin", "admin");
		List<Profile> result = database.command(new OSQLSynchQuery<Profile>("select * from Profile where nick is not null")).execute();

		for (Profile p : result)
			database.getRecordByUserObject(p, false);

		OIndex idx = database.getMetadata().getIndexManager().getIndex("Profile.nick");

		Assert.assertEquals(idx.getSize(), result.size());

		Iterator<Entry<Object, Set<OIdentifiable>>> it = idx.iterator();
		while (it.hasNext()) {
			it.next();
		}

		database.close();
	}

	@Test(dependsOnMethods = "testDuplicatedIndexOnUnique")
	public void testIndexSize() {
		database.open("admin", "admin");

		List<Profile> result = database.command(new OSQLSynchQuery<Profile>("select * from Profile where nick is not null")).execute();

		int profileSize = result.size();

		database.getMetadata().getIndexManager().reload();
		Assert.assertEquals(database.getMetadata().getIndexManager().getIndex("Profile.nick").getSize(), profileSize);
		for (int i = 0; i < 10; i++) {
			Profile profile = new Profile("Yay-" + i, "Jay", "Miner", null);
			database.save(profile);
			profileSize++;
			Assert.assertEquals(database.getMetadata().getIndexManager().getIndex("Profile.nick").get("Yay-" + i).size(), 1);
		}
		database.close();
	}

	@Test(dependsOnMethods = "testUseOfIndex")
	public void testChangeOfIndexToNotUnique() {
		database.open("admin", "admin");
		database.getMetadata().getSchema().getClass("Profile").getProperty("nick").dropIndex();
		database.getMetadata().getSchema().getClass("Profile").getProperty("nick").createIndex(INDEX_TYPE.NOTUNIQUE);
		database.close();
	}

	@Test(dependsOnMethods = "testChangeOfIndexToNotUnique")
	public void testDuplicatedIndexOnNotUnique() {
		database.open("admin", "admin");

		Profile nickNolte = new Profile("Jay", "Nick", "Nolte", null);
		database.save(nickNolte);

		database.close();
	}

	@Test(dependsOnMethods = "testDuplicatedIndexOnNotUnique")
	public void testQueryIndex() {
		database.open("admin", "admin");

		List<?> result = database.query(new OSQLSynchQuery<Object>("select from index:profile.nick where key = 'Jay'"));
		Assert.assertTrue(result.size() > 0);

		database.close();
	}

	@Test
	public void testIndexSQL() {
		database.open("admin", "admin");

		database.command(new OCommandSQL("create index idx unique")).execute();
		database.getMetadata().getIndexManager().reload();
		Assert.assertNotNull(database.getMetadata().getIndexManager().getIndex("idx"));

		database.command(new OCommandSQL("insert into index:IDX (key,rid) values (10,#3:0)")).execute();
		database.command(new OCommandSQL("insert into index:IDX (key,rid) values (20,#3:1)")).execute();

		List<ODocument> result = database.command(new OCommandSQL("select from index:IDX")).execute();
		Assert.assertNotNull(result);
		Assert.assertEquals(result.size(), 2);
		for (ODocument d : result) {
			Assert.assertTrue(d.containsField("key"));
			Assert.assertTrue(d.containsField("rid"));

			if (d.field("key").equals(10))
				Assert.assertEquals(d.rawField("rid"), new ORecordId("#3:0"));
			else if (d.field("key").equals(20))
				Assert.assertEquals(d.rawField("rid"), new ORecordId("#3:1"));
			else
				Assert.assertTrue(false);
		}

		result = database.command(new OCommandSQL("select key, rid from index:IDX")).execute();
		Assert.assertNotNull(result);
		Assert.assertEquals(result.size(), 2);
		for (ODocument d : result) {
			Assert.assertTrue(d.containsField("key"));
			Assert.assertTrue(d.containsField("rid"));

			if (d.field("key").equals(10))
				Assert.assertEquals(d.rawField("rid"), new ORecordId("#3:0"));
			else if (d.field("key").equals(20))
				Assert.assertEquals(d.rawField("rid"), new ORecordId("#3:1"));
			else
				Assert.assertTrue(false);
		}

		result = database.command(new OCommandSQL("select key from index:IDX")).execute();
		Assert.assertNotNull(result);
		Assert.assertEquals(result.size(), 2);
		for (ODocument d : result) {
			Assert.assertTrue(d.containsField("key"));
			Assert.assertFalse(d.containsField("rid"));
		}

		result = database.command(new OCommandSQL("select rid from index:IDX")).execute();
		Assert.assertNotNull(result);
		Assert.assertEquals(result.size(), 2);
		for (ODocument d : result) {
			Assert.assertFalse(d.containsField("key"));
			Assert.assertTrue(d.containsField("rid"));
		}

		result = database.command(new OCommandSQL("select rid from index:IDX where key = 10")).execute();
		Assert.assertNotNull(result);
		Assert.assertEquals(result.size(), 1);
		for (ODocument d : result) {
			Assert.assertFalse(d.containsField("key"));
			Assert.assertTrue(d.containsField("rid"));
		}

		database.close();
	}

	@Test(dependsOnMethods = "testQueryIndex")
	public void testChangeOfIndexToUnique() {
		database.open("admin", "admin");
		try {
			database.getMetadata().getSchema().getClass("Profile").getProperty("nick").dropIndex();
			database.getMetadata().getSchema().getClass("Profile").getProperty("nick").createIndex(INDEX_TYPE.UNIQUE);
			Assert.assertTrue(false);
		} catch (OIndexException e) {
			Assert.assertTrue(true);
		}

		database.close();
	}

	@Test(dependsOnMethods = "populateIndexDocuments")
	public void testValuesMajor() {
		database.open("admin", "admin");
		database.command(new OCommandSQL("create index equalityIdx unique")).execute();

		database.getMetadata().getIndexManager().reload();
		Assert.assertNotNull(database.getMetadata().getIndexManager().getIndex("equalityIdx"));

		for (int key = 0; key <= 5; key++) {
			database.command(new OCommandSQL("insert into index:equalityIdx (key,rid) values (" + key + ",#10:" + key + ")")).execute();
		}

		final OIndex index = database.getMetadata().getIndexManager().getIndex("equalityIdx");

		final Collection<Long> valuesMajorResults = new ArrayList<Long>(Arrays.asList(4L, 5L));
		Collection<OIdentifiable> indexCollection = index.getValuesMajor(3, false);
		Assert.assertEquals(indexCollection.size(), 2);
		for (OIdentifiable identifiable : indexCollection) {
			valuesMajorResults.remove(identifiable.getIdentity().getClusterPosition());
		}
		Assert.assertEquals(valuesMajorResults.size(), 0);

		final Collection<Long> valuesMajorInclusiveResults = new ArrayList<Long>(Arrays.asList(3L, 4L, 5L));
		indexCollection = index.getValuesMajor(3, true);
		Assert.assertEquals(indexCollection.size(), 3);
		for (OIdentifiable identifiable : indexCollection) {
			valuesMajorInclusiveResults.remove(identifiable.getIdentity().getClusterPosition());
		}
		Assert.assertEquals(valuesMajorInclusiveResults.size(), 0);

		indexCollection = index.getValuesMajor(5, true);
		Assert.assertEquals(indexCollection.size(), 1);
		Assert.assertEquals(indexCollection.iterator().next().getIdentity().getClusterPosition(), 5L);

		indexCollection = index.getValuesMajor(5, false);
		Assert.assertEquals(indexCollection.size(), 0);

		database.command(new OCommandSQL("drop index equalityIdx")).execute();
		database.close();
	}

	@Test(dependsOnMethods = "populateIndexDocuments")
	public void testEntriesMajor() {
		database.open("admin", "admin");
		database.command(new OCommandSQL("create index equalityIdx unique")).execute();

		database.getMetadata().getIndexManager().reload();
		Assert.assertNotNull(database.getMetadata().getIndexManager().getIndex("equalityIdx"));

		for (int key = 0; key <= 5; key++) {
			database.command(new OCommandSQL("insert into index:equalityIdx (key,rid) values (" + key + ",#10:" + key + ")")).execute();
		}

		final OIndex index = database.getMetadata().getIndexManager().getIndex("equalityIdx");

		final Collection<Integer> valuesMajorResults = new ArrayList<Integer>(Arrays.asList(4, 5));
		Collection<ODocument> indexCollection = index.getEntriesMajor(3, false);
		Assert.assertEquals(indexCollection.size(), 2);
		for (ODocument doc : indexCollection) {
			valuesMajorResults.remove(doc.<Integer> field("key"));
			Assert.assertEquals(doc.<ORecordId> rawField("rid"), new ORecordId(10, doc.<Integer> field("key").longValue()));
		}
		Assert.assertEquals(valuesMajorResults.size(), 0);

		final Collection<Integer> valuesMajorInclusiveResults = new ArrayList<Integer>(Arrays.asList(3, 4, 5));
		indexCollection = index.getEntriesMajor(3, true);
		Assert.assertEquals(indexCollection.size(), 3);
		for (ODocument doc : indexCollection) {
			valuesMajorInclusiveResults.remove(doc.<Integer> field("key"));
			Assert.assertEquals(doc.<ORecordId> rawField("rid"), new ORecordId(10, doc.<Integer> field("key").longValue()));
		}
		Assert.assertEquals(valuesMajorInclusiveResults.size(), 0);

		indexCollection = index.getEntriesMajor(5, true);
		Assert.assertEquals(indexCollection.size(), 1);
		Assert.assertEquals(indexCollection.iterator().next().<Integer> field("key"), Integer.valueOf(5));
		Assert.assertEquals(indexCollection.iterator().next().<ORecordId> rawField("rid"), new ORecordId(10, 5));

		indexCollection = index.getEntriesMajor(5, false);
		Assert.assertEquals(indexCollection.size(), 0);

		database.command(new OCommandSQL("drop index equalityIdx")).execute();
		database.close();
	}

	@Test(dependsOnMethods = "populateIndexDocuments")
	public void testValuesMinor() {
		database.open("admin", "admin");
		database.command(new OCommandSQL("create index equalityIdx unique")).execute();

		database.getMetadata().getIndexManager().reload();
		Assert.assertNotNull(database.getMetadata().getIndexManager().getIndex("equalityIdx"));

		for (int key = 0; key <= 5; key++) {
			database.command(new OCommandSQL("insert into index:equalityIdx (key,rid) values (" + key + ",#10:" + key + ")")).execute();
		}

		final OIndex index = database.getMetadata().getIndexManager().getIndex("equalityIdx");

		final Collection<Long> valuesMinorResults = new ArrayList<Long>(Arrays.asList(0L, 1L, 2L));
		Collection<OIdentifiable> indexCollection = index.getValuesMinor(3, false);
		Assert.assertEquals(indexCollection.size(), 3);
		for (OIdentifiable identifiable : indexCollection) {
			valuesMinorResults.remove(identifiable.getIdentity().getClusterPosition());
		}
		Assert.assertEquals(valuesMinorResults.size(), 0);

		final Collection<Long> valuesMinorInclusiveResults = new ArrayList<Long>(Arrays.asList(0L, 1L, 2L, 3L));
		indexCollection = index.getValuesMinor(3, true);
		Assert.assertEquals(indexCollection.size(), 4);
		for (OIdentifiable identifiable : indexCollection) {
			valuesMinorInclusiveResults.remove(identifiable.getIdentity().getClusterPosition());
		}
		Assert.assertEquals(valuesMinorInclusiveResults.size(), 0);

		indexCollection = index.getValuesMinor(0, true);
		Assert.assertEquals(indexCollection.size(), 1);
		Assert.assertEquals(indexCollection.iterator().next().getIdentity().getClusterPosition(), 0L);

		indexCollection = index.getValuesMinor(0, false);
		Assert.assertEquals(indexCollection.size(), 0);

		database.command(new OCommandSQL("drop index equalityIdx")).execute();
		database.close();
	}

	@Test(dependsOnMethods = "populateIndexDocuments")
	public void testEntriesMinor() {
		database.open("admin", "admin");
		database.command(new OCommandSQL("create index equalityIdx unique")).execute();

		database.getMetadata().getIndexManager().reload();
		Assert.assertNotNull(database.getMetadata().getIndexManager().getIndex("equalityIdx"));

		for (int key = 0; key <= 5; key++) {
			database.command(new OCommandSQL("insert into index:equalityIdx (key,rid) values (" + key + ",#10:" + key + ")")).execute();
		}

		final OIndex index = database.getMetadata().getIndexManager().getIndex("equalityIdx");

		final Collection<Integer> valuesMinorResults = new ArrayList<Integer>(Arrays.asList(0, 1, 2));
		Collection<ODocument> indexCollection = index.getEntriesMinor(3, false);
		Assert.assertEquals(indexCollection.size(), 3);
		for (ODocument doc : indexCollection) {
			valuesMinorResults.remove(doc.<Integer> field("key"));
			Assert.assertEquals(doc.<ORecordId> rawField("rid"), new ORecordId(10, doc.<Integer> field("key").longValue()));
		}
		Assert.assertEquals(valuesMinorResults.size(), 0);

		final Collection<Integer> valuesMinorInclusiveResults = new ArrayList<Integer>(Arrays.asList(0, 1, 2, 3));
		indexCollection = index.getEntriesMinor(3, true);
		Assert.assertEquals(indexCollection.size(), 4);
		for (ODocument doc : indexCollection) {
			valuesMinorInclusiveResults.remove(doc.<Integer> field("key"));
			Assert.assertEquals(doc.<ORecordId> rawField("rid"), new ORecordId(10, doc.<Integer> field("key").longValue()));
		}
		Assert.assertEquals(valuesMinorInclusiveResults.size(), 0);

		indexCollection = index.getEntriesMinor(0, true);
		Assert.assertEquals(indexCollection.size(), 1);
		Assert.assertEquals(indexCollection.iterator().next().<Integer> field("key"), Integer.valueOf(0));
		Assert.assertEquals(indexCollection.iterator().next().<ORecordId> rawField("rid"), new ORecordId(10, 0));

		indexCollection = index.getEntriesMinor(0, false);
		Assert.assertEquals(indexCollection.size(), 0);

		database.command(new OCommandSQL("drop index equalityIdx")).execute();
		database.close();
	}

	@Test(dependsOnMethods = "populateIndexDocuments")
	public void testIndexInMajorSelect() {
		database.open("admin", "admin");
		if (database.getStorage() instanceof OStorageRemote || database.getStorage() instanceof OStorageRemoteThread) {
			database.close();
			return;
		}

		final boolean oldRecording = OProfiler.getInstance().isRecording();

		if (!oldRecording) {
			OProfiler.getInstance().startRecording();
		}

		long indexQueries = OProfiler.getInstance().getCounter("Query.indexUsage");
		if (indexQueries < 0) {
			indexQueries = 0;
		}

		final List<Profile> result = database.command(
				new OSQLSynchQuery<Profile>("select * from Profile where nick > 'ZZZJayLongNickIndex3'")).execute();
		final List<String> expectedNicks = new ArrayList<String>(Arrays.asList("ZZZJayLongNickIndex4", "ZZZJayLongNickIndex5"));

		if (!oldRecording) {
			OProfiler.getInstance().stopRecording();
		}

		Assert.assertEquals(result.size(), 2);
		for (Profile profile : result) {
			expectedNicks.remove(profile.getNick());
		}

		Assert.assertEquals(expectedNicks.size(), 0);
		long newIndexQueries = OProfiler.getInstance().getCounter("Query.indexUsage");
		Assert.assertEquals(newIndexQueries, indexQueries + 1);

		database.close();
	}

	@Test(dependsOnMethods = "populateIndexDocuments")
	public void testIndexInMajorEqualsSelect() {
		database.open("admin", "admin");
		if (database.getStorage() instanceof OStorageRemote || database.getStorage() instanceof OStorageRemoteThread) {
			database.close();
			return;
		}

		final boolean oldRecording = OProfiler.getInstance().isRecording();

		if (!oldRecording) {
			OProfiler.getInstance().startRecording();
		}

		long indexQueries = OProfiler.getInstance().getCounter("Query.indexUsage");
		if (indexQueries < 0) {
			indexQueries = 0;
		}

		final List<Profile> result = database.command(
				new OSQLSynchQuery<Profile>("select * from Profile where nick >= 'ZZZJayLongNickIndex3'")).execute();
		final List<String> expectedNicks = new ArrayList<String>(Arrays.asList("ZZZJayLongNickIndex3", "ZZZJayLongNickIndex4",
				"ZZZJayLongNickIndex5"));

		if (!oldRecording) {
			OProfiler.getInstance().stopRecording();
		}

		Assert.assertEquals(result.size(), 3);
		for (Profile profile : result) {
			expectedNicks.remove(profile.getNick());
		}

		Assert.assertEquals(expectedNicks.size(), 0);
		long newIndexQueries = OProfiler.getInstance().getCounter("Query.indexUsage");
		Assert.assertEquals(newIndexQueries, indexQueries + 1);

		database.close();
	}

	@Test(dependsOnMethods = "populateIndexDocuments")
	public void testIndexInMinorSelect() {
		database.open("admin", "admin");
		if (database.getStorage() instanceof OStorageRemote || database.getStorage() instanceof OStorageRemoteThread) {
			database.close();
			return;
		}

		final boolean oldRecording = OProfiler.getInstance().isRecording();

		if (!oldRecording) {
			OProfiler.getInstance().startRecording();
		}

		long indexQueries = OProfiler.getInstance().getCounter("Query.indexUsage");
		if (indexQueries < 0) {
			indexQueries = 0;
		}

		final List<Profile> result = database.command(new OSQLSynchQuery<Profile>("select * from Profile where nick < '002'"))
				.execute();
		final List<String> expectedNicks = new ArrayList<String>(Arrays.asList("000", "001"));

		if (!oldRecording) {
			OProfiler.getInstance().stopRecording();
		}

		Assert.assertEquals(result.size(), 2);
		for (Profile profile : result) {
			expectedNicks.remove(profile.getNick());
		}

		Assert.assertEquals(expectedNicks.size(), 0);
		long newIndexQueries = OProfiler.getInstance().getCounter("Query.indexUsage");
		Assert.assertEquals(newIndexQueries, indexQueries + 1);

		database.close();
	}

	@Test(dependsOnMethods = "populateIndexDocuments")
	public void testIndexInMinorEqualsSelect() {
		database.open("admin", "admin");
		if (database.getStorage() instanceof OStorageRemote || database.getStorage() instanceof OStorageRemoteThread) {
			database.close();
			return;
		}

		final boolean oldRecording = OProfiler.getInstance().isRecording();

		if (!oldRecording) {
			OProfiler.getInstance().startRecording();
		}

		long indexQueries = OProfiler.getInstance().getCounter("Query.indexUsage");
		if (indexQueries < 0) {
			indexQueries = 0;
		}

		final List<Profile> result = database.command(new OSQLSynchQuery<Profile>("select * from Profile where nick <= '002'"))
				.execute();
		final List<String> expectedNicks = new ArrayList<String>(Arrays.asList("000", "001", "002"));

		if (!oldRecording) {
			OProfiler.getInstance().stopRecording();
		}

		Assert.assertEquals(result.size(), 3);
		for (Profile profile : result) {
			expectedNicks.remove(profile.getNick());
		}

		Assert.assertEquals(expectedNicks.size(), 0);
		long newIndexQueries = OProfiler.getInstance().getCounter("Query.indexUsage");
		Assert.assertEquals(newIndexQueries, indexQueries + 1);

		database.close();
	}

	@Test(dependsOnMethods = "populateIndexDocuments")
	public void testIndexBetweenSelect() {
		database.open("admin", "admin");
		if (database.getStorage() instanceof OStorageRemote || database.getStorage() instanceof OStorageRemoteThread) {
			database.close();
			return;
		}

		final boolean oldRecording = OProfiler.getInstance().isRecording();

		if (!oldRecording) {
			OProfiler.getInstance().startRecording();
		}

		long indexQueries = OProfiler.getInstance().getCounter("Query.indexUsage");
		if (indexQueries < 0) {
			indexQueries = 0;
		}

		final List<Profile> result = database.command(
				new OSQLSynchQuery<Profile>("select * from Profile where nick between '001' and '004'")).execute();
		final List<String> expectedNicks = new ArrayList<String>(Arrays.asList("001", "002", "003", "004"));

		if (!oldRecording) {
			OProfiler.getInstance().stopRecording();
		}

		Assert.assertEquals(result.size(), 4);
		for (Profile profile : result) {
			expectedNicks.remove(profile.getNick());
		}

		Assert.assertEquals(expectedNicks.size(), 0);
		long newIndexQueries = OProfiler.getInstance().getCounter("Query.indexUsage");
		Assert.assertEquals(newIndexQueries, indexQueries + 1);

		database.close();
	}

	public void populateIndexDocuments() {
		database.open("admin", "admin");

		for (int i = 0; i <= 5; i++) {
			final Profile profile = new Profile("ZZZJayLongNickIndex" + i, "NickIndex" + i, "NolteIndex" + i, null);
			database.save(profile);
		}

		for (int i = 0; i <= 5; i++) {
			final Profile profile = new Profile("00" + i, "NickIndex" + i, "NolteIndex" + i, null);
			database.save(profile);
		}

		database.close();
	}

}
