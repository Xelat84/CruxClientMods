package com.wurmonline.clientkit;

import java.io.UnsupportedEncodingException;

/**
 * Growable big-endian byte builder, generalized from AuctionHouse's {@code Encoder}. Optionally
 * seed the buffer with leading bytes (e.g. {@code new PacketWriter(cmd, sub)}). All puts are
 * big-endian to match the vanilla ByteBuffer wire format. Fluent — every put returns {@code this}.
 */
public final class PacketWriter {

	private byte[] buf = new byte[32];
	private int len;

	public PacketWriter() {
	}

	/** Seed the buffer with leading bytes, e.g. the command byte, or command + sub-command. */
	public PacketWriter(byte... leading) {
		for (byte b : leading) {
			put(b);
		}
	}

	private void ensure(int extra) {
		if (len + extra > buf.length) {
			int n = buf.length * 2;
			while (n < len + extra) {
				n *= 2;
			}
			byte[] nb = new byte[n];
			System.arraycopy(buf, 0, nb, 0, len);
			buf = nb;
		}
	}

	public PacketWriter put(byte b) {
		ensure(1);
		buf[len++] = b;
		return this;
	}

	public PacketWriter putShort(int v) {
		ensure(2);
		buf[len++] = (byte) (v >> 8);
		buf[len++] = (byte) v;
		return this;
	}

	public PacketWriter putInt(int v) {
		ensure(4);
		buf[len++] = (byte) (v >> 24);
		buf[len++] = (byte) (v >> 16);
		buf[len++] = (byte) (v >> 8);
		buf[len++] = (byte) v;
		return this;
	}

	public PacketWriter putLong(long v) {
		ensure(8);
		for (int s = 56; s >= 0; s -= 8) {
			buf[len++] = (byte) (v >> s);
		}
		return this;
	}

	public PacketWriter putFloat(float v) {
		return putInt(Float.floatToIntBits(v));
	}

	/** Short-length-prefixed UTF-8 string (matches {@link PacketReader#readString()}). */
	public PacketWriter putString(String s) {
		if (s == null) {
			s = "";
		}
		byte[] bytes;
		try {
			bytes = s.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			bytes = new byte[0];
		}
		putShort(bytes.length);
		ensure(bytes.length);
		System.arraycopy(bytes, 0, buf, len, bytes.length);
		len += bytes.length;
		return this;
	}

	public byte[] bytes() {
		byte[] out = new byte[len];
		System.arraycopy(buf, 0, out, 0, len);
		return out;
	}
}
