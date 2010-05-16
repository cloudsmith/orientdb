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

import com.orientechnologies.orient.core.db.ODatabaseComplex;
import com.orientechnologies.orient.core.iterator.ORecordIteratorCluster;
import com.orientechnologies.orient.core.metadata.security.ORole.CRUD_OPERATIONS;
import com.orientechnologies.orient.core.record.ORecordInternal;

/**
 * Generic interface for record based Database implementations.
 * 
 * @author Luca Garulli
 */
public interface ODatabaseRecord<REC extends ORecordInternal<?>> extends ODatabaseComplex<REC> {

	/**
	 * Browse all the records of the specified cluster.
	 * 
	 * @param iClusterName
	 *          Cluster name to iterate
	 * @return Iterator of ODocument instances
	 */
	public ORecordIteratorCluster<REC> browseCluster(String iClusterName);

	/**
	 * Return the record type class.
	 */
	public Class<? extends REC> getRecordType();

	/**
	 * Checks if the operation on a resource is allowed fir the current user.
	 * 
	 * @param iResource
	 *          Resource where to execute the operation
	 * @param iOperation
	 *          Operation to execute against the resource
	 * @return The Database instance itself giving a "fluent interface". Useful to call multiple methods in chain.
	 */
	public <DB extends ODatabaseRecord<?>> DB checkSecurity(String iResource, CRUD_OPERATIONS iOperation);
}
