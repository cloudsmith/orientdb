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
package com.orientechnologies.orient.core.sql;

import java.util.Map;

import com.orientechnologies.orient.core.command.OCommandRequestText;
import com.orientechnologies.orient.core.exception.OCommandExecutionException;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OPropertyImpl;
import com.orientechnologies.orient.core.metadata.security.ODatabaseSecurityResources;
import com.orientechnologies.orient.core.metadata.security.ORole;

/**
 * SQL REMOVE INDEX command: Remove an index
 * 
 * @author Luca Garulli (l.garulli--at--orientechnologies.com)
 * 
 */
@SuppressWarnings("unchecked")
public class OCommandExecutorSQLDropIndex extends OCommandExecutorSQLPermissionAbstract {
	public static final String	KEYWORD_DROP	= "DROP";
	public static final String	KEYWORD_INDEX	= "INDEX";

	private String							name;

	public OCommandExecutorSQLDropIndex parse(final OCommandRequestText iRequest) {
		iRequest.getDatabase().checkSecurity(ODatabaseSecurityResources.COMMAND, ORole.PERMISSION_CREATE);

		init(iRequest.getDatabase(), iRequest.getText());

		final StringBuilder word = new StringBuilder();

		int oldPos = 0;
		int pos = OSQLHelper.nextWord(text, textUpperCase, oldPos, word, true);
		if (pos == -1 || !word.toString().equals(KEYWORD_DROP))
			throw new OCommandSQLParsingException("Keyword " + KEYWORD_DROP + " not found", text, oldPos);

		oldPos = pos;
		pos = OSQLHelper.nextWord(text, textUpperCase, pos, word, true);
		if (pos == -1 || !word.toString().equals(KEYWORD_INDEX))
			throw new OCommandSQLParsingException("Keyword " + KEYWORD_INDEX + " not found", text, oldPos);

		oldPos = pos;
		pos = OSQLHelper.nextWord(text, textUpperCase, oldPos, word, false);
		if (pos == -1)
			throw new OCommandSQLParsingException("Expected index name", text, oldPos);

		name = word.toString();

		return this;
	}

	/**
	 * Execute the REMOVE INDEX.
	 */
	public Object execute(final Map<Object, Object> iArgs) {
		if (name == null)
			throw new OCommandExecutionException("Can't execute the command because it hasn't been parsed yet");

		if (name.indexOf('.') > -1) {
			// PROPERTY INDEX
			final String[] parts = name.split("\\.");
			final String className = parts[0];
			if (className == null)
				throw new OCommandExecutionException("Class " + className + " not found");
			String fieldName = parts[1];

			final OClass cls = database.getMetadata().getSchema().getClass(className);
			if (cls == null)
				throw new OCommandExecutionException("Class '" + className + "' not found");

			final OPropertyImpl prop = (OPropertyImpl) cls.getProperty(fieldName);
			if (prop == null)
				throw new IllegalArgumentException("Property '" + fieldName + "' was not found in class '" + cls + "'");

			prop.dropIndexInternal();
		} else {
			database.getMetadata().getIndexManager().dropIndex(name);
		}

		return null;
	}
}
