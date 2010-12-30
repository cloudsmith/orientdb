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

import java.util.ArrayList;
import java.util.List;

import com.orientechnologies.common.parser.OStringParser;
import com.orientechnologies.orient.core.command.OCommandToParse;
import com.orientechnologies.orient.core.db.record.ODatabaseRecord;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.serialization.serializer.OStringSerializerHelper;
import com.orientechnologies.orient.core.serialization.serializer.record.string.ORecordSerializerCSVAbstract;
import com.orientechnologies.orient.core.sql.filter.OSQLFilter;
import com.orientechnologies.orient.core.sql.filter.OSQLFilterItemField;
import com.orientechnologies.orient.core.sql.filter.OSQLFilterItemParameter;
import com.orientechnologies.orient.core.sql.filter.OSQLFilterItemRecordAttrib;
import com.orientechnologies.orient.core.sql.functions.OSQLFunction;
import com.orientechnologies.orient.core.sql.functions.OSQLFunctionRuntime;
import com.orientechnologies.orient.core.sql.operator.OQueryOperator;
import com.orientechnologies.orient.core.sql.operator.OQueryOperatorAnd;
import com.orientechnologies.orient.core.sql.operator.OQueryOperatorContains;
import com.orientechnologies.orient.core.sql.operator.OQueryOperatorContainsAll;
import com.orientechnologies.orient.core.sql.operator.OQueryOperatorContainsKey;
import com.orientechnologies.orient.core.sql.operator.OQueryOperatorContainsText;
import com.orientechnologies.orient.core.sql.operator.OQueryOperatorContainsValue;
import com.orientechnologies.orient.core.sql.operator.OQueryOperatorEquals;
import com.orientechnologies.orient.core.sql.operator.OQueryOperatorIn;
import com.orientechnologies.orient.core.sql.operator.OQueryOperatorIs;
import com.orientechnologies.orient.core.sql.operator.OQueryOperatorLike;
import com.orientechnologies.orient.core.sql.operator.OQueryOperatorMajor;
import com.orientechnologies.orient.core.sql.operator.OQueryOperatorMajorEquals;
import com.orientechnologies.orient.core.sql.operator.OQueryOperatorMinor;
import com.orientechnologies.orient.core.sql.operator.OQueryOperatorMinorEquals;
import com.orientechnologies.orient.core.sql.operator.OQueryOperatorNot;
import com.orientechnologies.orient.core.sql.operator.OQueryOperatorNotEquals;
import com.orientechnologies.orient.core.sql.operator.OQueryOperatorOr;
import com.orientechnologies.orient.core.sql.operator.OQueryOperatorTraverse;

/**
 * SQL Helper class
 * 
 * @author Luca Garulli
 * 
 */
public class OSQLHelper {
	public static final String			NAME							= "sql";

	public static final String			VALUE_NOT_PARSED	= "_NOT_PARSED_";
	public static final String			NOT_NULL					= "_NOT_NULL_";

	public static OQueryOperator[]	RECORD_OPERATORS	= { new OQueryOperatorAnd(), new OQueryOperatorOr(),
			new OQueryOperatorNotEquals(), new OQueryOperatorNot(), new OQueryOperatorEquals(), new OQueryOperatorMinorEquals(),
			new OQueryOperatorMinor(), new OQueryOperatorMajorEquals(), new OQueryOperatorContainsAll(), new OQueryOperatorMajor(),
			new OQueryOperatorLike(), new OQueryOperatorIs(), new OQueryOperatorIn(), new OQueryOperatorContainsKey(),
			new OQueryOperatorContainsValue(), new OQueryOperatorContainsText(), new OQueryOperatorContains(),
			new OQueryOperatorContainsText(), new OQueryOperatorTraverse() };

	public static int nextWord(final String iText, final String iTextUpperCase, int ioCurrentPosition, final StringBuilder ioWord,
			final boolean iForceUpperCase) {
		return nextWord(iText, iTextUpperCase, ioCurrentPosition, ioWord, iForceUpperCase, " =><()");
	}

	public static int nextWord(final String iText, final String iTextUpperCase, int ioCurrentPosition, final StringBuilder ioWord,
			final boolean iForceUpperCase, final String iSeparatorChars) {
		ioWord.setLength(0);

		ioCurrentPosition = OStringParser.jumpWhiteSpaces(iText, ioCurrentPosition);
		if (ioCurrentPosition >= iText.length())
			return -1;

		final String word = OStringParser.getWordFromString(iForceUpperCase ? iTextUpperCase : iText, ioCurrentPosition,
				iSeparatorChars);

		if (word != null && word.length() > 0) {
			ioWord.append(word);
			ioCurrentPosition += word.length();
		}

		return ioCurrentPosition;
	}

	public static OQueryOperator[] getRecordOperators() {
		return RECORD_OPERATORS;
	}

	public static void registerOperator(final OQueryOperator iOperator) {
		OQueryOperator[] ops = new OQueryOperator[RECORD_OPERATORS.length + 1];
		System.arraycopy(RECORD_OPERATORS, 0, ops, 0, RECORD_OPERATORS.length);
		RECORD_OPERATORS = ops;
	}

	/**
	 * Convert fields from text to real value. Supports: String, RID, Boolean, Float, Integer and NULL.
	 * 
	 * @param iDatabase
	 * @param iValue
	 *          Value to convert.
	 * @return The value converted if recognized, otherwise VALUE_NOT_PARSED
	 */
	public static Object parseValue(final ODatabaseRecord<?> iDatabase, String iValue) {
		if (iValue == null)
			return null;

		iValue = iValue.trim();

		Object fieldValue = VALUE_NOT_PARSED;

		if (iValue.startsWith("'") && iValue.endsWith("'"))
			// STRING
			fieldValue = stringContent(iValue);
		else if (iValue.charAt(0) == OStringSerializerHelper.COLLECTION_BEGIN
				&& iValue.charAt(iValue.length() - 1) == OStringSerializerHelper.COLLECTION_END) {
			// COLLECTION/ARRAY
			final List<String> items = OStringSerializerHelper.smartSplit(iValue.substring(1, iValue.length() - 1),
					OStringSerializerHelper.RECORD_SEPARATOR);

			final List<Object> coll = new ArrayList<Object>();
			for (String item : items) {
				coll.add(parseValue(iDatabase, item));
			}
			fieldValue = coll;

		} else if (iValue.indexOf(":") > 0)
			// RID
			fieldValue = new ORecordId(iValue.trim());
		else {

			final String upperCase = iValue.toUpperCase();
			if (upperCase.equals("NULL"))
				// NULL
				fieldValue = null;
			else if (upperCase.equals("NOT NULL"))
				// NULL
				fieldValue = NOT_NULL;
			else if (upperCase.equals("TRUE"))
				// BOOLEAN, TRUE
				fieldValue = Boolean.TRUE;
			else if (upperCase.equals("FALSE"))
				// BOOLEAN, FALSE
				fieldValue = Boolean.FALSE;
			else {
				OType t = ORecordSerializerCSVAbstract.getNumber(iValue);
				// NUMBER
				if (t == OType.LONG)
					fieldValue = Long.parseLong(iValue);
				else if (t == OType.DOUBLE)
					fieldValue = Double.parseDouble(iValue);
			}
		}

		return fieldValue;
	}

	public static Object parseValue(final OSQLFilter iSQLFilter, final ODatabaseRecord<?> iDatabase, final OCommandToParse iCommand,
			final String iWord) {
		if (iWord.charAt(0) == OStringSerializerHelper.PARAMETER_POSITIONAL
				|| iWord.charAt(0) == OStringSerializerHelper.PARAMETER_NAMED) {
			if (iSQLFilter != null)
				return iSQLFilter.addParameter(iWord);
			else
				return new OSQLFilterItemParameter(iWord);
		} else
			return parseValue(iDatabase, iCommand, iWord);
	}

	public static Object parseValue(final ODatabaseRecord<?> iDatabase, final OCommandToParse iCommand, final String iWord) {
		if (iWord.equals("*"))
			return "*";

		if (iWord.startsWith("@"))
			// RECORD ATTRIB
			return new OSQLFilterItemRecordAttrib(iCommand, iWord);

		// TRY TO PARSE AS RAW VALUE
		final Object v = parseValue(iDatabase, iWord);
		if (v != VALUE_NOT_PARSED)
			return v;

		// TRY TO PARSE AS FUNCTION
		final Object func = OSQLHelper.getFunction(iDatabase, iCommand, iWord);
		if (func != null)
			return func;

		// PARSE AS FIELD
		return new OSQLFilterItemField(iCommand, iWord);
	}

	public static String stringContent(final String iContent) {
		if (iContent.startsWith("'") || iContent.startsWith("\""))
			return iContent.substring(1, iContent.length() - 1);
		return iContent;
	}

	public static Object getFunction(final ODatabaseRecord<?> database, final OCommandToParse iCommand, final String iWord) {
		int sepPos = iWord.indexOf('.');
		int parPos = iWord.indexOf(OStringSerializerHelper.PARENTHESIS_BEGIN);

		if (Character.isLetter(iWord.charAt(0)) && parPos > -1 && (sepPos == -1 || sepPos > parPos)) {
			// SEARCH FOR THE FUNCTION
			final String funcName = iWord.substring(0, parPos);

			final List<String> funcParamsText = OStringSerializerHelper.getParameters(iWord);

			OSQLFunction function = OSQLEngine.getInstance().getInlineFunction(funcName);
			if (function == null)
				// AGGREGATION ?
				function = OSQLEngine.getInstance().getAggregationFunction(funcName);

			if (function == null)
				throw new OCommandSQLParsingException("Unknow function " + funcName + "()");

			if (function.getMinParams() > -1 && funcParamsText.size() < function.getMinParams() || function.getMaxParams() > -1
					&& funcParamsText.size() > function.getMaxParams())
				throw new IllegalArgumentException("Syntax error. Expected: " + function.getSyntax());

			// PARSE PARAMETERS
			final Object[] funcParams = new Object[funcParamsText.size()];
			for (int i = 0; i < funcParamsText.size(); ++i) {
				funcParams[i] = OSQLHelper.parseValue(database, iCommand, funcParamsText.get(i));
			}

			// FUNCTION: CRETAE A RUN-TIME CONTAINER FOR IT TO SAVE THE PARAMETERS
			return new OSQLFunctionRuntime(function, funcParams);
		}

		return null;
	}
}
