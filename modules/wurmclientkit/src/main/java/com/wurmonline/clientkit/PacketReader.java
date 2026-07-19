package com.wurmonline.clientkit;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

/**
 * Thin decode wrapper over a vanilla {@link ByteBuffer}, generalized from AuctionHouse's decode
 * helpers. The buffer is big-endian (vanilla wire format). Reads advance the underlying buffer's
 * position — construct one per received packet, positioned wherever the payload begins.
 */
public final class PacketReader {

	private final ByteBuffer bb;

	public PacketReader(ByteBuffer bb) {
		this.bb = bb;
	}

	public ByteBuffer buffer() {
		return bb;
	}

	public int remaining() {
		return bb.remaining();
	}

	public byte get() {
		return bb.get();
	}

	public short getShort() {
		return bb.getShort();
	}

	/** Read a 16-bit value as an unsigned int (0..65535). */
	public int getUnsignedShort() {
		return bb.getShort() & 0xFFFF;
	}

	public int getInt() {
		return bb.getInt();
	}

	public long getLong() {
		return bb.getLong();
	}

	public float getFloat() {
		return bb.getFloat();
	}

	/** Short-length-prefixed UTF-8 string (matches {@link PacketWriter#putString(String)}). */
	public String readString() {
		int len = bb.getShort() & 0xFFFF;
		byte[] bytes = new byte[len];
		bb.get(bytes);
		try {
			return new String(bytes, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			return "";
		}
	}
}
