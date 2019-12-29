package com.simple.netty.buffer;

import com.simple.netty.common.internal.ObjectUtil;
import com.simple.netty.common.internal.PlatformDependent;

import java.nio.CharBuffer;
import java.nio.charset.Charset;

/**
 * Date: 2019-12-15
 * Time: 19:45
 *
 * @author yrw
 */
public final class Unpooled {

    private static final ByteBufAllocator ALLOC = new UnpooledByteBufAllocator(PlatformDependent.directBufferPreferred());

    public static final ByteBuf EMPTY_BUFFER = ALLOC.buffer(0, 0);

    static {
        assert EMPTY_BUFFER instanceof EmptyByteBuf : "EMPTY_BUFFER must be an EmptyByteBuf.";
    }

    /**
     * Creates a new big-endian Java heap buffer with reasonably small initial capacity, which
     * expands its capacity boundlessly on demand.
     */
    public static ByteBuf buffer() {
        return ALLOC.heapBuffer();
    }

    /**
     * Creates a new big-endian direct buffer with reasonably small initial capacity, which
     * expands its capacity boundlessly on demand.
     */
    public static ByteBuf directBuffer() {
        return ALLOC.directBuffer();
    }

    /**
     * Creates a new big-endian Java heap buffer with the specified {@code capacity}, which
     * expands its capacity boundlessly on demand.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0}.
     */
    public static ByteBuf buffer(int initialCapacity) {
        return ALLOC.heapBuffer(initialCapacity);
    }

    /**
     * Creates a new big-endian direct buffer with the specified {@code capacity}, which
     * expands its capacity boundlessly on demand.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0}.
     */
    public static ByteBuf directBuffer(int initialCapacity) {
        return ALLOC.directBuffer(initialCapacity);
    }

    /**
     * Creates a new big-endian Java heap buffer with the specified
     * {@code initialCapacity}, that may grow up to {@code maxCapacity}
     * The new buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0}.
     */
    public static ByteBuf buffer(int initialCapacity, int maxCapacity) {
        return ALLOC.heapBuffer(initialCapacity, maxCapacity);
    }

    /**
     * Creates a new big-endian direct buffer with the specified
     * {@code initialCapacity}, that may grow up to {@code maxCapacity}.
     * The new buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0}.
     */
    public static ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        return ALLOC.directBuffer(initialCapacity, maxCapacity);
    }

    /**
     * Creates a new big-endian buffer which wraps the specified {@code array}.
     * A modification on the specified array's content will be visible to the
     * returned buffer.
     */
    public static ByteBuf wrappedBuffer(byte[] array) {
        if (array.length == 0) {
            return EMPTY_BUFFER;
        }
        return new UnpooledHeapByteBuf(ALLOC, array, array.length);
    }

    /**
     * Creates a new big-endian buffer whose content is a copy of the
     * specified {@code array}.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0} and {@code array.length} respectively.
     */
    public static ByteBuf copiedBuffer(byte[] array) {
        if (array.length == 0) {
            return EMPTY_BUFFER;
        }
        return wrappedBuffer(array.clone());
    }

    /**
     * Creates a new big-endian buffer whose content is a copy of the
     * specified {@code array}'s sub-region.  The new buffer's
     * {@code readerIndex} and {@code writerIndex} are {@code 0} and
     * the specified {@code length} respectively.
     */
    public static ByteBuf copiedBuffer(byte[] array, int offset, int length) {
        if (length == 0) {
            return EMPTY_BUFFER;
        }
        byte[] copy = PlatformDependent.allocateUninitializedArray(length);
        System.arraycopy(array, offset, copy, 0, length);
        return wrappedBuffer(copy);
    }

    /**
     * Creates a new buffer whose content is a copy of the specified
     * {@code buffer}'s readable bytes.  The new buffer's {@code readerIndex}
     * and {@code writerIndex} are {@code 0} and {@code buffer.readableBytes}
     * respectively.
     */
    public static ByteBuf copiedBuffer(ByteBuf buffer) {
        int readable = buffer.readableBytes();
        if (readable > 0) {
            ByteBuf copy = buffer(readable);
            copy.writeBytes(buffer, buffer.readerIndex(), readable);
            return copy;
        } else {
            return EMPTY_BUFFER;
        }
    }

    /**
     * Creates a new big-endian buffer whose content is a merged copy of
     * the specified {@code arrays}.  The new buffer's {@code readerIndex}
     * and {@code writerIndex} are {@code 0} and the sum of all arrays'
     * {@code length} respectively.
     */
    public static ByteBuf copiedBuffer(byte[]... arrays) {
        switch (arrays.length) {
            case 0:
                return EMPTY_BUFFER;
            case 1:
                if (arrays[0].length == 0) {
                    return EMPTY_BUFFER;
                } else {
                    return copiedBuffer(arrays[0]);
                }
        }

        // Merge the specified arrays into one array.
        int length = 0;
        for (byte[] a : arrays) {
            if (Integer.MAX_VALUE - length < a.length) {
                throw new IllegalArgumentException(
                    "The total length of the specified arrays is too big.");
            }
            length += a.length;
        }

        if (length == 0) {
            return EMPTY_BUFFER;
        }

        byte[] mergedArray = PlatformDependent.allocateUninitializedArray(length);
        for (int i = 0, j = 0; i < arrays.length; i++) {
            byte[] a = arrays[i];
            System.arraycopy(a, 0, mergedArray, j, a.length);
            j += a.length;
        }

        return wrappedBuffer(mergedArray);
    }

    /**
     * Creates a new big-endian buffer whose content is the specified
     * {@code string} encoded in the specified {@code charset}.
     * The new buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0} and the length of the encoded string respectively.
     */
    public static ByteBuf copiedBuffer(CharSequence string, Charset charset) {
        ObjectUtil.checkNotNull(string, "string");

        if (string instanceof CharBuffer) {
            return copiedBuffer((CharBuffer) string, charset);
        }

        return copiedBuffer(CharBuffer.wrap(string), charset);
    }

    /**
     * Creates a new big-endian buffer whose content is a subregion of
     * the specified {@code string} encoded in the specified {@code charset}.
     * The new buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0} and the length of the encoded string respectively.
     */
    public static ByteBuf copiedBuffer(
        CharSequence string, int offset, int length, Charset charset) {
        ObjectUtil.checkNotNull(string, "string");
        if (length == 0) {
            return EMPTY_BUFFER;
        }

        if (string instanceof CharBuffer) {
            CharBuffer buf = (CharBuffer) string;
            if (buf.hasArray()) {
                return copiedBuffer(
                    buf.array(),
                    buf.arrayOffset() + buf.position() + offset,
                    length, charset);
            }

            buf = buf.slice();
            buf.limit(length);
            buf.position(offset);
            return copiedBuffer(buf, charset);
        }

        return copiedBuffer(CharBuffer.wrap(string, offset, offset + length), charset);
    }

    /**
     * Creates a new big-endian buffer whose content is the specified
     * {@code array} encoded in the specified {@code charset}.
     * The new buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0} and the length of the encoded string respectively.
     */
    public static ByteBuf copiedBuffer(char[] array, Charset charset) {
        ObjectUtil.checkNotNull(array, "array");
        return copiedBuffer(array, 0, array.length, charset);
    }

    /**
     * Creates a new big-endian buffer whose content is a subregion of
     * the specified {@code array} encoded in the specified {@code charset}.
     * The new buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0} and the length of the encoded string respectively.
     */
    public static ByteBuf copiedBuffer(char[] array, int offset, int length, Charset charset) {
        ObjectUtil.checkNotNull(array, "array");
        if (length == 0) {
            return EMPTY_BUFFER;
        }
        return copiedBuffer(CharBuffer.wrap(array, offset, length), charset);
    }

    /**
     * Creates a new 4-byte big-endian buffer that holds the specified 32-bit integer.
     */
    public static ByteBuf copyInt(int value) {
        ByteBuf buf = buffer(4);
        buf.writeInt(value);
        return buf;
    }

    /**
     * Create a big-endian buffer that holds a sequence of the specified 32-bit integers.
     */
    public static ByteBuf copyInt(int... values) {
        if (values == null || values.length == 0) {
            return EMPTY_BUFFER;
        }
        ByteBuf buffer = buffer(values.length * 4);
        for (int v : values) {
            buffer.writeInt(v);
        }
        return buffer;
    }

    /**
     * Creates a new 2-byte big-endian buffer that holds the specified 16-bit integer.
     */
    public static ByteBuf copyShort(int value) {
        ByteBuf buf = buffer(2);
        buf.writeShort(value);
        return buf;
    }

    /**
     * Create a new big-endian buffer that holds a sequence of the specified 16-bit integers.
     */
    public static ByteBuf copyShort(short... values) {
        if (values == null || values.length == 0) {
            return EMPTY_BUFFER;
        }
        ByteBuf buffer = buffer(values.length * 2);
        for (int v : values) {
            buffer.writeShort(v);
        }
        return buffer;
    }

    /**
     * Create a new big-endian buffer that holds a sequence of the specified 16-bit integers.
     */
    public static ByteBuf copyShort(int... values) {
        if (values == null || values.length == 0) {
            return EMPTY_BUFFER;
        }
        ByteBuf buffer = buffer(values.length * 2);
        for (int v : values) {
            buffer.writeShort(v);
        }
        return buffer;
    }

    /**
     * Creates a new single-byte big-endian buffer that holds the specified boolean value.
     */
    public static ByteBuf copyBoolean(boolean value) {
        ByteBuf buf = buffer(1);
        buf.writeBoolean(value);
        return buf;
    }

    /**
     * Create a new big-endian buffer that holds a sequence of the specified boolean values.
     */
    public static ByteBuf copyBoolean(boolean... values) {
        if (values == null || values.length == 0) {
            return EMPTY_BUFFER;
        }
        ByteBuf buffer = buffer(values.length);
        for (boolean v : values) {
            buffer.writeBoolean(v);
        }
        return buffer;
    }

    private Unpooled() {
        // Unused
    }
}
