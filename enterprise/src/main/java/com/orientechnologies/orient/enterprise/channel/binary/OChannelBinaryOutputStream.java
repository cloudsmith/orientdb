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
package com.orientechnologies.orient.enterprise.channel.binary;

import java.io.IOException;
import java.io.OutputStream;

/**
 * OutputStream class bound to a binary channel. Reuses the shared buffer of the channel.
 * 
 * @author Luca Garulli (l.garulli--at--orientechnologies.com)
 * 
 */
public class OChannelBinaryOutputStream extends OutputStream {
	private OChannelBinary	channel;
	private final byte[]		buffer;
	private int							pos	= 0;

	public OChannelBinaryOutputStream(final OChannelBinary channel) {
		this.channel = channel;
		buffer = channel.getBuffer();
	}

	@Override
	public void write(final int iByte) throws IOException {
		if (pos < buffer.length) {
			buffer[pos++] = (byte) iByte;
			return;
		}

		flush();
		channel.out.writeByte(1);
	}

	@Override
	public void close() throws IOException {
		flush();
		channel.out.writeByte(0);
	}

	@Override
	public void flush() throws IOException {
		channel.out.writeInt(buffer.length);
		channel.out.write(buffer, 0, pos);
	}
}
