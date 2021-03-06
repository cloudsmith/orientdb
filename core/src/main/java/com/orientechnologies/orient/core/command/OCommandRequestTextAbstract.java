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
package com.orientechnologies.orient.core.command;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.orientechnologies.orient.core.db.record.ODatabaseRecord;
import com.orientechnologies.orient.core.exception.OSerializationException;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.serialization.OMemoryInputStream;
import com.orientechnologies.orient.core.serialization.OMemoryOutputStream;
import com.orientechnologies.orient.core.serialization.OSerializableStream;
import com.orientechnologies.orient.core.serialization.serializer.record.string.ORecordSerializerStringAbstract;

/**
 * Text based Command Request abstract class.
 * 
 * @author Luca Garulli
 * 
 */
@SuppressWarnings("serial")
public abstract class OCommandRequestTextAbstract extends OCommandRequestAbstract implements OCommandRequestText {
	protected String	text;

	protected OCommandRequestTextAbstract() {
	}

	protected OCommandRequestTextAbstract(final String iText) {
		this(iText, null);
	}

	protected OCommandRequestTextAbstract(final String iText, final ODatabaseRecord iDatabase) {
		super(iDatabase);

		if (iText == null)
			throw new IllegalArgumentException("Text can't be null");

		text = iText.trim();
	}

	/**
	 * Delegates the execution to the configured command executor.
	 */
	@SuppressWarnings("unchecked")
	public <RET> RET execute(final Object... iArgs) {
		setParameters(iArgs);
		return (RET) database.getStorage().command(this);
	}

	public String getText() {
		return text;
	}

	public OCommandRequestText setText(final String iText) {
		this.text = iText;
		return this;
	}

	public OSerializableStream fromStream(byte[] iStream) throws OSerializationException {
		final OMemoryInputStream buffer = new OMemoryInputStream(iStream);
		try {
			text = buffer.getAsString();

			byte[] paramBuffer = buffer.getAsByteArray();

			if (paramBuffer.length == 0)
				parameters = null;
			else {
				final ODocument param = new ODocument(database);
				param.fromStream(paramBuffer);

				Map<String, Object> params = param.field("params");

				parameters = new HashMap<Object, Object>();
				for (Entry<String, Object> p : params.entrySet()) {
					final Object value;
					if (p.getValue() instanceof String)
						value = ORecordSerializerStringAbstract.getTypeValue((String) p.getValue());
					else
						value = p.getValue();

					if (Character.isDigit(p.getKey().charAt(0)))
						parameters.put(Integer.parseInt(p.getKey()), value);
					else
						parameters.put(p.getKey(), value);
				}
			}
		} catch (IOException e) {
			throw new OSerializationException("Error while unmarshalling OCommandRequestTextAbstract impl", e);
		}
		return this;
	}

	public byte[] toStream() throws OSerializationException {
		final OMemoryOutputStream buffer = new OMemoryOutputStream();
		try {
			buffer.add(text);

			if (parameters == null || parameters.size() == 0)
				buffer.add(new byte[0]);
			else {
				final ODocument param = new ODocument(database);
				param.field("params", parameters);
				buffer.add(param.toStream());
			}

		} catch (IOException e) {
			throw new OSerializationException("Error while marshalling OCommandRequestTextAbstract impl", e);
		}

		return buffer.toByteArray();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [text=" + text + "]";
	}
}
