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

import java.util.Date;
import java.util.Set;

import com.orientechnologies.common.profiler.OProfiler;
import com.orientechnologies.orient.core.db.ODatabaseComplex;
import com.orientechnologies.orient.core.db.OUserObject2RecordHandler;
import com.orientechnologies.orient.core.db.record.ODatabaseRecord;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.ORecord;
import com.orientechnologies.orient.core.record.ORecordInternal;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.serialization.OBase64Utils;
import com.orientechnologies.orient.core.serialization.OBinaryProtocol;
import com.orientechnologies.orient.core.serialization.serializer.OStringSerializerHelper;
import com.orientechnologies.orient.core.serialization.serializer.record.ORecordSerializer;
import com.orientechnologies.orient.core.serialization.serializer.record.OSerializationThreadLocal;
import com.orientechnologies.orient.core.serialization.serializer.string.OStringSerializerAnyStreamable;

public abstract class ORecordSerializerStringAbstract implements ORecordSerializer {
	private static final char	DECIMAL_SEPARATOR	= '.';

	protected abstract String toString(final ORecordInternal<?> iRecord, final String iFormat,
			final OUserObject2RecordHandler iObjHandler, final Set<Integer> iMarshalledRecords);

	protected abstract ORecordInternal<?> fromString(final ODatabaseRecord iDatabase, final String iContent,
			final ORecordInternal<?> iRecord);

	public String toString(final ORecordInternal<?> iRecord, final String iFormat) {
		return toString(iRecord, iFormat, iRecord.getDatabase(), OSerializationThreadLocal.INSTANCE.get());
	}

	public ORecordInternal<?> fromString(final ODatabaseRecord iDatabase, final String iSource) {
		return fromString(iDatabase, iSource, (ORecordInternal<?>) iDatabase.newInstance());
	}

	public ORecordInternal<?> fromStream(final ODatabaseRecord iDatabase, final byte[] iSource, final ORecordInternal<?> iRecord) {
		final long timer = OProfiler.getInstance().startChrono();

		try {
			return fromString(iDatabase, OBinaryProtocol.bytes2string(iSource), iRecord);
		} finally {

			OProfiler.getInstance().stopChrono("ORecordSerializerStringAbstract.fromStream", timer);
		}
	}

	public byte[] toStream(final ODatabaseRecord iDatabase, final ORecordInternal<?> iRecord) {
		final long timer = OProfiler.getInstance().startChrono();

		try {
			return OBinaryProtocol.string2bytes(toString(iRecord, null, iDatabase, OSerializationThreadLocal.INSTANCE.get()));
		} finally {

			OProfiler.getInstance().stopChrono("ORecordSerializerStringAbstract.toStream", timer);
		}
	}

	public static Object fieldTypeFromStream(final ODocument iDocument, OType iType, final Object iValue) {
		if (iValue == null)
			return null;

		if (iType == null)
			iType = OType.EMBEDDED;

		switch (iType) {
		case STRING:
			if (iValue instanceof String) {
				final String s = (String) iValue;
				return OStringSerializerHelper.decode(s.substring(1, s.length() - 1));
			}
			return iValue.toString();

		case INTEGER:
			if (iValue instanceof Integer)
				return iValue;
			return new Integer(iValue.toString());

		case BOOLEAN:
			if (iValue instanceof Boolean)
				return iValue;
			return new Boolean(iValue.toString());

		case FLOAT:
			if (iValue instanceof Float)
				return iValue;
			return convertValue((String) iValue, iType);

		case LONG:
			if (iValue instanceof Long)
				return iValue;
			return convertValue((String) iValue, iType);

		case DOUBLE:
			if (iValue instanceof Double)
				return iValue;
			return convertValue((String) iValue, iType);

		case SHORT:
			if (iValue instanceof Short)
				return iValue;
			return convertValue((String) iValue, iType);

		case BYTE:
			if (iValue instanceof Byte)
				return iValue;
			return convertValue((String) iValue, iType);

		case BINARY:
			if (iValue instanceof byte[])
				return iValue;
			if (iValue instanceof String) {
				final String s = (String) iValue;
				if (s.length() > 2)
					return OBase64Utils.decode(s.substring(1, s.length() - 1));
				return null;
			}

		case DATE:
			if (iValue instanceof Date)
				return iValue;
			return convertValue((String) iValue, iType);

		case LINK:
			if (iValue instanceof ORID)
				return iValue.toString();
			else if (iValue instanceof String)
				return new ORecordId((String) iValue);
			else
				return ((ORecord<?>) iValue).getIdentity().toString();

		case EMBEDDED:
			// RECORD
			return OStringSerializerAnyStreamable.INSTANCE.fromStream(iDocument.getDatabase(), (String) iValue);

		case EMBEDDEDMAP:
			// RECORD
			final String value = (String) iValue;
			return ORecordSerializerSchemaAware2CSV.INSTANCE.embeddedMapFromStream(iDocument, null, value);
		}

		throw new IllegalArgumentException("Type " + iType + " not supported to convert value: " + iValue);
	}

	public static Object convertValue(final String iValue, final OType iExpectedType) {
		final Object v = getSimpleValue((String) iValue);
		return OType.convert(v, iExpectedType.getDefaultJavaType());
	}

	public static String fieldTypeToString(final ODatabaseComplex<?> iDatabase, OType iType, final Object iValue) {
		if (iValue == null)
			return null;

		if (iType == null)
			iType = OType.EMBEDDED;

		switch (iType) {
		case STRING:
			return "\"" + OStringSerializerHelper.encode(iValue.toString()) + "\"";

		case BOOLEAN:
		case INTEGER:
			return String.valueOf(iValue);

		case FLOAT:
			return String.valueOf(iValue) + 'f';
		case LONG:
			return String.valueOf(iValue) + 'l';
		case DOUBLE:
			return String.valueOf(iValue) + 'd';
		case SHORT:
			return String.valueOf(iValue) + 's';

		case BYTE:
			if (iValue instanceof Character)
				return String.valueOf((int) ((Character) iValue).charValue()) + 'b';
			else if (iValue instanceof String)
				return String.valueOf((int) ((String) iValue).charAt(0)) + 'b';
			else
				return String.valueOf(iValue) + 'b';

		case BINARY:
			final String str;
			if (iValue instanceof Byte)
				str = new String(new byte[] { ((Byte) iValue).byteValue() });
			else
				str = OBase64Utils.encodeBytes((byte[]) iValue);
			return "\"" + str + "\"";

		case DATE:
			if (iValue instanceof Date)
				return String.valueOf(((Date) iValue).getTime()) + 't';
			else
				return String.valueOf(iValue) + 't';

		case LINK:
			if (iValue instanceof ORID)
				return iValue.toString();
			else
				return ((ORecord<?>) iValue).getIdentity().toString();

		case EMBEDDEDMAP:
			return ORecordSerializerSchemaAware2CSV.INSTANCE.embeddedMapToStream(iDatabase, null, null, null, iValue, null, true);

		case EMBEDDED:
			// RECORD
			return OStringSerializerAnyStreamable.INSTANCE.toStream(iDatabase, iValue);
		}

		throw new IllegalArgumentException("Type " + iType + " not supported to convert value: " + iValue);
	}

	/**
	 * Parse a string returning the closer type. Numbers by default are INTEGER if haven't decimal separator, otherwise FLOAT. To
	 * treat all the number types numbers are postponed with a character that tells the type: b=byte, s=short, l=long, f=float,
	 * d=double, t=date.
	 * 
	 * @param iUnusualSymbols
	 *          Localized decimal number separators
	 * @param iValue
	 *          Value to parse
	 * @return The closest type recognized
	 */
	public static OType getType(final String iValue) {
		boolean integer = true;
		char c;

		for (int index = 0; index < iValue.length(); ++index) {
			c = iValue.charAt(index);
			if (c < '0' || c > '9')
				if ((index == 0 && (c == '+' || c == '-')))
					continue;
				else if (c == DECIMAL_SEPARATOR)
					integer = false;
				else {
					if (index > 0)
						if (c == 'f')
							return OType.FLOAT;
						else if (c == 'l')
							return OType.LONG;
						else if (c == 'd')
							return OType.DOUBLE;
						else if (c == 'b')
							return OType.BYTE;
						else if (c == 't')
							return OType.DATE;
						else if (c == 's')
							return OType.SHORT;

					return OType.STRING;
				}
		}

		return integer ? OType.INTEGER : OType.FLOAT;
	}

	/**
	 * Parse a string returning the value with the closer type. Numbers by default are INTEGER if haven't decimal separator, otherwise
	 * FLOAT. To treat all the number types numbers are postponed with a character that tells the type: b=byte, s=short, l=long,
	 * f=float, d=double, t=date. Most of the code is equals to getType() but has been copied to speed-up it.
	 * 
	 * @param iUnusualSymbols
	 *          Localized decimal number separators
	 * @param iValue
	 *          Value to parse
	 * @return The closest type recognized
	 */
	public static Object getSimpleValue(final String iValue) {
		boolean integer = true;
		char c;

		for (int index = 0; index < iValue.length(); ++index) {
			c = iValue.charAt(index);
			if (c < '0' || c > '9')
				if ((index == 0 && (c == '+' || c == '-')))
					continue;
				else if (c == DECIMAL_SEPARATOR)
					integer = false;
				else {
					if (index > 0)
						if (c == 'f')
							return new Float(iValue.substring(0, index));
						else if (c == 'l')
							return new Long(iValue.substring(0, index));
						else if (c == 'd')
							return new Double(iValue.substring(0, index));
						else if (c == 'b')
							return new Byte(iValue.substring(0, index));
						else if (c == 't')
							return new Date(Long.parseLong(iValue.substring(0, index)));
						else if (c == 's')
							return new Short(iValue.substring(0, index));

					return OType.STRING;
				}
		}

		return integer ? new Integer(iValue) : new Float(iValue);
	}
}
