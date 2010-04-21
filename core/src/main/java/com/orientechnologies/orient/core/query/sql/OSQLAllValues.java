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
package com.orientechnologies.orient.core.query.sql;

/**
 * Represent all the values between the values contained. Used in query evaluations where all the items must satisfy the following
 * condition. Works such as the AND operator by repeating the condition against all the fields.
 * 
 * @author luca
 * 
 */
public class OSQLAllValues {
	public Object[]	values;

	public OSQLAllValues(final Object[] iValues) {
		values = iValues;
	}
}
