package com.simple.netty.buffer;

import com.simple.netty.common.internal.IllegalReferenceCountException;
import com.simple.netty.common.internal.PlatformDependent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.GatheringByteChannel;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static com.simple.netty.buffer.Unpooled.buffer;
import static com.simple.netty.buffer.Unpooled.copiedBuffer;
import static com.simple.netty.common.internal.EmptyArrays.EMPTY_BYTES;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * Date: 2019-12-29
 * Time: 15:21
 *
 * @author yrw
 */
public abstract class AbstractByteBufTest {

    private static final int CAPACITY = 4096; // Must be even
    private static final int BLOCK_SIZE = 128;
    private static final int JAVA_BYTEBUFFER_CONSISTENCY_ITERATIONS = 100;

    private long seed;
    private Random random;
    private ByteBuf buffer;

    protected final ByteBuf newBuffer(int capacity) {
        return newBuffer(capacity, Integer.MAX_VALUE);
    }

    protected abstract ByteBuf newBuffer(int capacity, int maxCapacity);

    @Before
    public void init() {
        buffer = newBuffer(CAPACITY);
        seed = System.currentTimeMillis();
        random = new Random(seed);
    }

    @After
    public void dispose() {
        if (buffer != null) {
            assertThat(buffer.release(), is(true));
            assertThat(buffer.refCnt(), is(0));

            try {
                buffer.release();
            } catch (Exception e) {
                // Ignore.
            }
            buffer = null;
        }
    }


    @Test
    public void initialState() {
        assertEquals(CAPACITY, buffer.capacity());
        assertEquals(0, buffer.readerIndex());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void readerIndexBoundaryCheck1() {
        try {
            buffer.writerIndex(0);
        } catch (IndexOutOfBoundsException e) {
            fail();
        }
        buffer.readerIndex(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void readerIndexBoundaryCheck2() {
        try {
            buffer.writerIndex(buffer.capacity());
        } catch (IndexOutOfBoundsException e) {
            fail();
        }
        buffer.readerIndex(buffer.capacity() + 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void readerIndexBoundaryCheck3() {
        try {
            buffer.writerIndex(CAPACITY / 2);
        } catch (IndexOutOfBoundsException e) {
            fail();
        }
        buffer.readerIndex(CAPACITY * 3 / 2);
    }

    @Test
    public void readerIndexBoundaryCheck4() {
        buffer.writerIndex(0);
        buffer.readerIndex(0);
        buffer.writerIndex(buffer.capacity());
        buffer.readerIndex(buffer.capacity());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void writerIndexBoundaryCheck1() {
        buffer.writerIndex(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void writerIndexBoundaryCheck2() {
        try {
            buffer.writerIndex(CAPACITY);
            buffer.readerIndex(CAPACITY);
        } catch (IndexOutOfBoundsException e) {
            fail();
        }
        buffer.writerIndex(buffer.capacity() + 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void writerIndexBoundaryCheck3() {
        try {
            buffer.writerIndex(CAPACITY);
            buffer.readerIndex(CAPACITY / 2);
        } catch (IndexOutOfBoundsException e) {
            fail();
        }
        buffer.writerIndex(CAPACITY / 4);
    }

    @Test
    public void writerIndexBoundaryCheck4() {
        buffer.writerIndex(0);
        buffer.readerIndex(0);
        buffer.writerIndex(CAPACITY);

        buffer.writeBytes(ByteBuffer.wrap(EMPTY_BYTES));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getBooleanBoundaryCheck1() {
        buffer.getBoolean(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getBooleanBoundaryCheck2() {
        buffer.getBoolean(buffer.capacity());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getByteBoundaryCheck1() {
        buffer.getByte(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getByteBoundaryCheck2() {
        buffer.getByte(buffer.capacity());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getShortBoundaryCheck1() {
        buffer.getShort(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getShortBoundaryCheck2() {
        buffer.getShort(buffer.capacity() - 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getIntBoundaryCheck1() {
        buffer.getInt(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getIntBoundaryCheck2() {
        buffer.getInt(buffer.capacity() - 3);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getLongBoundaryCheck1() {
        buffer.getLong(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getLongBoundaryCheck2() {
        buffer.getLong(buffer.capacity() - 7);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getByteArrayBoundaryCheck1() {
        buffer.getBytes(-1, EMPTY_BYTES);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getByteArrayBoundaryCheck2() {
        buffer.getBytes(-1, EMPTY_BYTES, 0, 0);
    }

    @Test
    public void getByteArrayBoundaryCheck3() {
        byte[] dst = new byte[4];
        buffer.setInt(0, 0x01020304);
        try {
            buffer.getBytes(0, dst, -1, 4);
            fail();
        } catch (IndexOutOfBoundsException e) {
            // Success
        }

        // No partial copy is expected.
        assertEquals(0, dst[0]);
        assertEquals(0, dst[1]);
        assertEquals(0, dst[2]);
        assertEquals(0, dst[3]);
    }

    @Test
    public void getByteArrayBoundaryCheck4() {
        byte[] dst = new byte[4];
        buffer.setInt(0, 0x01020304);
        try {
            buffer.getBytes(0, dst, 1, 4);
            fail();
        } catch (IndexOutOfBoundsException e) {
            // Success
        }

        // No partial copy is expected.
        assertEquals(0, dst[0]);
        assertEquals(0, dst[1]);
        assertEquals(0, dst[2]);
        assertEquals(0, dst[3]);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getByteBufferBoundaryCheck() {
        buffer.getBytes(-1, ByteBuffer.allocate(0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void setIndexBoundaryCheck1() {
        buffer.setIndex(-1, CAPACITY);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void setIndexBoundaryCheck2() {
        buffer.setIndex(CAPACITY / 2, CAPACITY / 4);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void setIndexBoundaryCheck3() {
        buffer.setIndex(0, CAPACITY + 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getDirectByteBufferBoundaryCheck() {
        buffer.getBytes(-1, ByteBuffer.allocateDirect(0));
    }

    @Test
    public void testRandomShortAccess() {
        for (int i = 0; i < buffer.capacity() - 1; i += 2) {
            short value = (short) random.nextInt();
            buffer.setShort(i, value);
        }

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity() - 1; i += 2) {
            short value = (short) random.nextInt();
            assertEquals(value, buffer.getShort(i));
        }
    }

    @Test
    public void testShortConsistentWithByteBuffer() {
        testShortConsistentWithByteBuffer(true);
        testShortConsistentWithByteBuffer(false);
    }

    private void testShortConsistentWithByteBuffer(boolean direct) {
        for (int i = 0; i < JAVA_BYTEBUFFER_CONSISTENCY_ITERATIONS; ++i) {
            ByteBuffer javaBuffer = direct ? ByteBuffer.allocateDirect(buffer.capacity())
                : ByteBuffer.allocate(buffer.capacity());

            short expected = (short) (random.nextInt() & 0xFFFF);
            javaBuffer.putShort(expected);

            final int bufferIndex = buffer.capacity() - 2;

            buffer.setShort(bufferIndex, expected);
            javaBuffer.flip();

            short javaActual = javaBuffer.getShort();
            assertEquals(expected, javaActual);
            assertEquals(javaActual, buffer.getShort(bufferIndex));
        }
    }

    @Test
    public void testRandomIntAccess() {
        for (int i = 0; i < buffer.capacity() - 3; i += 4) {
            int value = random.nextInt();
            buffer.setInt(i, value);
        }

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity() - 3; i += 4) {
            int value = random.nextInt();
            assertEquals(value, buffer.getInt(i));
        }
    }

    @Test
    public void testIntConsistentWithByteBuffer() {
        testIntConsistentWithByteBuffer(true);
        testIntConsistentWithByteBuffer(false);
    }

    private void testIntConsistentWithByteBuffer(boolean direct) {
        for (int i = 0; i < JAVA_BYTEBUFFER_CONSISTENCY_ITERATIONS; ++i) {
            ByteBuffer javaBuffer = direct ? ByteBuffer.allocateDirect(buffer.capacity())
                : ByteBuffer.allocate(buffer.capacity());

            int expected = random.nextInt();
            javaBuffer.putInt(expected);

            final int bufferIndex = buffer.capacity() - 4;

            buffer.setInt(bufferIndex, expected);
            javaBuffer.flip();

            int javaActual = javaBuffer.getInt();
            assertEquals(expected, javaActual);
            assertEquals(javaActual, buffer.getInt(bufferIndex));
        }
    }

    @Test
    public void testRandomLongAccess() {
        for (int i = 0; i < buffer.capacity() - 7; i += 8) {
            long value = random.nextLong();
            buffer.setLong(i, value);
        }

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity() - 7; i += 8) {
            long value = random.nextLong();
            assertEquals(value, buffer.getLong(i));
        }
    }

    @Test
    public void testLongConsistentWithByteBuffer() {
        testLongConsistentWithByteBuffer(true);
        testLongConsistentWithByteBuffer(false);
    }

    private void testLongConsistentWithByteBuffer(boolean direct) {
        for (int i = 0; i < JAVA_BYTEBUFFER_CONSISTENCY_ITERATIONS; ++i) {
            ByteBuffer javaBuffer = direct ? ByteBuffer.allocateDirect(buffer.capacity())
                : ByteBuffer.allocate(buffer.capacity());

            long expected = random.nextLong();
            javaBuffer.putLong(expected);

            final int bufferIndex = buffer.capacity() - 8;

            buffer.setLong(bufferIndex, expected);
            javaBuffer.flip();

            long javaActual = javaBuffer.getLong();
            assertEquals(expected, javaActual);
            assertEquals(javaActual, buffer.getLong(bufferIndex));
        }
    }

    @Test
    public void testRandomDoubleAccess() {
        for (int i = 0; i < buffer.capacity() - 7; i += 8) {
            double value = random.nextDouble();
            buffer.setDouble(i, value);
        }

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity() - 7; i += 8) {
            double expected = random.nextDouble();
            double actual = buffer.getDouble(i);
            assertEquals(expected, actual, 0.01);
        }
    }

    @Test
    public void testSequentialByteAccess() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity(); i++) {
            byte value = (byte) random.nextInt();
            assertEquals(i, buffer.writerIndex());
            assertTrue(buffer.isWritable());
            buffer.writeByte(value);
        }

        assertEquals(0, buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isWritable());

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity(); i++) {
            byte value = (byte) random.nextInt();
            assertEquals(i, buffer.readerIndex());
            assertTrue(buffer.isReadable());
            assertEquals(value, buffer.readByte());
        }

        assertEquals(buffer.capacity(), buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isReadable());
        assertFalse(buffer.isWritable());
    }

    @Test
    public void testSequentialShortAccess() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity(); i += 2) {
            short value = (short) random.nextInt();
            assertEquals(i, buffer.writerIndex());
            assertTrue(buffer.isWritable());

            buffer.writeShort(value);
        }

        assertEquals(0, buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isWritable());

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity(); i += 2) {
            short value = (short) random.nextInt();
            assertEquals(i, buffer.readerIndex());
            assertTrue(buffer.isReadable());
            assertEquals(value, buffer.readShort());
        }

        assertEquals(buffer.capacity(), buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isReadable());
        assertFalse(buffer.isWritable());
    }

    @Test
    public void testSequentialIntAccess() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity(); i += 4) {
            int value = random.nextInt();
            assertEquals(i, buffer.writerIndex());
            assertTrue(buffer.isWritable());
            buffer.writeInt(value);
        }

        assertEquals(0, buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isWritable());

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity(); i += 4) {
            int value = random.nextInt();
            assertEquals(i, buffer.readerIndex());
            assertTrue(buffer.isReadable());
            assertEquals(value, buffer.readInt());
        }

        assertEquals(buffer.capacity(), buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isReadable());
        assertFalse(buffer.isWritable());
    }

    @Test
    public void testByteArrayTransfer() {
        byte[] value = new byte[BLOCK_SIZE * 2];
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value);
            buffer.setBytes(i, value, random.nextInt(BLOCK_SIZE), BLOCK_SIZE);
        }

        random.setSeed(seed);
        byte[] expectedValue = new byte[BLOCK_SIZE * 2];
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            buffer.getBytes(i, value, valueOffset, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j++) {
                assertEquals(expectedValue[j], value[j]);
            }
        }
    }

    @Test
    public void testRandomByteBufferTransfer() {
        ByteBuffer value = ByteBuffer.allocate(BLOCK_SIZE * 2);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value.array());
            value.clear().position(random.nextInt(BLOCK_SIZE));
            value.limit(value.position() + BLOCK_SIZE);
            buffer.setBytes(i, value);
        }

        random.setSeed(seed);
        ByteBuffer expectedValue = ByteBuffer.allocate(BLOCK_SIZE * 2);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue.array());
            int valueOffset = random.nextInt(BLOCK_SIZE);
            value.clear().position(valueOffset).limit(valueOffset + BLOCK_SIZE);
            buffer.getBytes(i, value);
            assertEquals(valueOffset + BLOCK_SIZE, value.position());
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j++) {
                assertEquals(expectedValue.get(j), value.get(j));
            }
        }
    }

    @Test
    public void testSequentialByteArrayTransfer1() {
        byte[] value = new byte[BLOCK_SIZE];
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value);
            assertEquals(0, buffer.readerIndex());
            assertEquals(i, buffer.writerIndex());
            buffer.writeBytes(value);
        }

        random.setSeed(seed);
        byte[] expectedValue = new byte[BLOCK_SIZE];
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue);
            assertEquals(i, buffer.readerIndex());
            assertEquals(CAPACITY, buffer.writerIndex());
            buffer.readBytes(value);
            for (int j = 0; j < BLOCK_SIZE; j++) {
                assertEquals(expectedValue[j], value[j]);
            }
        }
    }

    @Test
    public void testSequentialByteArrayTransfer2() {
        byte[] value = new byte[BLOCK_SIZE * 2];
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value);
            assertEquals(0, buffer.readerIndex());
            assertEquals(i, buffer.writerIndex());
            int readerIndex = random.nextInt(BLOCK_SIZE);
            buffer.writeBytes(value, readerIndex, BLOCK_SIZE);
        }

        random.setSeed(seed);
        byte[] expectedValue = new byte[BLOCK_SIZE * 2];
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            assertEquals(i, buffer.readerIndex());
            assertEquals(CAPACITY, buffer.writerIndex());
            buffer.readBytes(value, valueOffset, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j++) {
                assertEquals(expectedValue[j], value[j]);
            }
        }
    }

    @Test
    public void testSequentialByteBufferTransfer() {
        buffer.writerIndex(0);
        ByteBuffer value = ByteBuffer.allocate(BLOCK_SIZE * 2);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value.array());
            value.clear().position(random.nextInt(BLOCK_SIZE));
            value.limit(value.position() + BLOCK_SIZE);
            buffer.writeBytes(value);
        }

        random.setSeed(seed);
        ByteBuffer expectedValue = ByteBuffer.allocate(BLOCK_SIZE * 2);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue.array());
            int valueOffset = random.nextInt(BLOCK_SIZE);
            value.clear().position(valueOffset).limit(valueOffset + BLOCK_SIZE);
            buffer.readBytes(value);
            assertEquals(valueOffset + BLOCK_SIZE, value.position());
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j++) {
                assertEquals(expectedValue.get(j), value.get(j));
            }
        }
    }

    @Test
    public void testInternalNioBuffer() {
        testInternalNioBuffer(128);
        testInternalNioBuffer(1024);
        testInternalNioBuffer(4 * 1024);
        testInternalNioBuffer(64 * 1024);
        testInternalNioBuffer(32 * 1024 * 1024);
        testInternalNioBuffer(64 * 1024 * 1024);
    }

    private void testInternalNioBuffer(int a) {
        ByteBuf buffer = newBuffer(2);
        ByteBuffer buf = buffer.internalNioBuffer(buffer.readerIndex(), 1);
        assertEquals(1, buf.remaining());

        byte[] data = new byte[a];
        PlatformDependent.threadLocalRandom().nextBytes(data);
        buffer.writeBytes(data);

        buf = buffer.internalNioBuffer(buffer.readerIndex(), a);
        assertEquals(a, buf.remaining());

        for (int i = 0; i < a; i++) {
            assertEquals(data[i], buf.get());
        }
        assertFalse(buf.hasRemaining());
        buffer.release();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void readByteThrowsIndexOutOfBoundsException() {
        final ByteBuf buffer = newBuffer(8);
        try {
            buffer.writeByte(0);
            assertEquals((byte) 0, buffer.readByte());
            buffer.readByte();
        } finally {
            buffer.release();
        }
    }

    private ByteBuf releasedBuffer() {
        ByteBuf buffer = newBuffer(8);
        // Clear the buffer so we are sure the reader and writer indices are 0.
        // This is important as we may return a slice from newBuffer(...).
        buffer.clear();
        assertTrue(buffer.release());
        return buffer;
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetBooleanAfterRelease() {
        releasedBuffer().getBoolean(0);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetByteAfterRelease() {
        releasedBuffer().getByte(0);
    }


    @Test(expected = IllegalReferenceCountException.class)
    public void testGetShortAfterRelease() {
        releasedBuffer().getShort(0);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetIntAfterRelease() {
        releasedBuffer().getInt(0);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetLongAfterRelease() {
        releasedBuffer().getLong(0);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetCharAfterRelease() {
        releasedBuffer().getChar(0);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetDoubleAfterRelease() {
        releasedBuffer().getDouble(0);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetBytesAfterRelease() {
        ByteBuf buffer = buffer(8);
        try {
            releasedBuffer().getBytes(0, buffer);
        } finally {
            buffer.release();
        }
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetBytesAfterRelease2() {
        ByteBuf buffer = buffer();
        try {
            releasedBuffer().getBytes(0, buffer, 1);
        } finally {
            buffer.release();
        }
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetBytesAfterRelease3() {
        ByteBuf buffer = buffer();
        try {
            releasedBuffer().getBytes(0, buffer, 0, 1);
        } finally {
            buffer.release();
        }
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetBytesAfterRelease4() {
        releasedBuffer().getBytes(0, new byte[8]);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetBytesAfterRelease5() {
        releasedBuffer().getBytes(0, new byte[8], 0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetBytesAfterRelease6() {
        releasedBuffer().getBytes(0, ByteBuffer.allocate(8));
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetBytesAfterRelease8() throws IOException {
        releasedBuffer().getBytes(0, new DevNullGatheringByteChannel(), 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetShortAfterRelease() {
        releasedBuffer().setShort(0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetIntAfterRelease() {
        releasedBuffer().setInt(0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetLongAfterRelease() {
        releasedBuffer().setLong(0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetCharAfterRelease() {
        releasedBuffer().setChar(0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetDoubleAfterRelease() {
        releasedBuffer().setDouble(0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetBytesAfterRelease3() {
        ByteBuf buffer = buffer();
        try {
            releasedBuffer().setBytes(0, buffer, 0, 1);
        } finally {
            buffer.release();
        }
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetBytesAfterRelease5() {
        releasedBuffer().setBytes(0, new byte[8], 0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetBytesAfterRelease6() {
        releasedBuffer().setBytes(0, ByteBuffer.allocate(8));
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBooleanAfterRelease() {
        releasedBuffer().readBoolean();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadByteAfterRelease() {
        releasedBuffer().readByte();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadShortAfterRelease() {
        releasedBuffer().readShort();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadIntAfterRelease() {
        releasedBuffer().readInt();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadLongAfterRelease() {
        releasedBuffer().readLong();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadCharAfterRelease() {
        releasedBuffer().readChar();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadDoubleAfterRelease() {
        releasedBuffer().readDouble();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBytesAfterRelease() {
        releasedBuffer().readBytes(1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBytesAfterRelease2() {
        ByteBuf buffer = buffer(8);
        try {
            releasedBuffer().readBytes(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBytesAfterRelease3() {
        ByteBuf buffer = buffer(8);
        try {
            releasedBuffer().readBytes(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBytesAfterRelease4() {
        ByteBuf buffer = buffer(8);
        try {
            releasedBuffer().readBytes(buffer, 0, 1);
        } finally {
            buffer.release();
        }
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBytesAfterRelease5() {
        releasedBuffer().readBytes(new byte[8]);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBytesAfterRelease6() {
        releasedBuffer().readBytes(new byte[8], 0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBytesAfterRelease7() {
        releasedBuffer().readBytes(ByteBuffer.allocate(8));
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBytesAfterRelease10() throws IOException {
        releasedBuffer().readBytes(new DevNullGatheringByteChannel(), 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteBooleanAfterRelease() {
        releasedBuffer().writeBoolean(true);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteByteAfterRelease() {
        releasedBuffer().writeByte(1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteShortAfterRelease() {
        releasedBuffer().writeShort(1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteIntAfterRelease() {
        releasedBuffer().writeInt(1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteCharAfterRelease() {
        releasedBuffer().writeChar(1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteBytesAfterRelease() {
        ByteBuf buffer = buffer(8);
        try {
            releasedBuffer().writeBytes(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteBytesAfterRelease2() {
        ByteBuf buffer = copiedBuffer(new byte[8]);
        try {
            releasedBuffer().writeBytes(buffer, 1);
        } finally {
            buffer.release();
        }
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteBytesAfterRelease3() {
        ByteBuf buffer = buffer(8);
        try {
            releasedBuffer().writeBytes(buffer, 0, 1);
        } finally {
            buffer.release();
        }
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteBytesAfterRelease4() {
        releasedBuffer().writeBytes(new byte[8]);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteBytesAfterRelease5() {
        releasedBuffer().writeBytes(new byte[8], 0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteBytesAfterRelease6() {
        releasedBuffer().writeBytes(ByteBuffer.allocate(8));
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testInternalNioBufferAfterRelease() {
        ByteBuf releasedBuffer = releasedBuffer();
        releasedBuffer.internalNioBuffer(releasedBuffer.readerIndex(), 1);
    }

    @Test
    public void testArrayAfterRelease() {
        ByteBuf buf = releasedBuffer();
        if (buf.hasArray()) {
            try {
                buf.array();
                fail();
            } catch (IllegalReferenceCountException e) {
                // expected
            }
        }
    }

    // Test-case trying to reproduce:
    // https://github.com/netty/netty/issues/2843
    @Test
    public void testRefCnt() throws Exception {
        testRefCnt0(false);
    }

    // Test-case trying to reproduce:
    // https://github.com/netty/netty/issues/2843
    @Test
    public void testRefCnt2() throws Exception {
        testRefCnt0(true);
    }

    @Test
    public void testGetReadOnlyDirectDst() {
        testGetReadOnlyDst(true);
    }

    @Test
    public void testGetReadOnlyHeapDst() {
        testGetReadOnlyDst(false);
    }

    private void testGetReadOnlyDst(boolean direct) {
        byte[] bytes = {'a', 'b', 'c', 'd'};

        ByteBuf buffer = newBuffer(bytes.length);
        buffer.writeBytes(bytes);

        ByteBuffer dst = direct ? ByteBuffer.allocateDirect(bytes.length) : ByteBuffer.allocate(bytes.length);
        ByteBuffer readOnlyDst = dst.asReadOnlyBuffer();
        try {
            buffer.getBytes(0, readOnlyDst);
            fail();
        } catch (ReadOnlyBufferException e) {
            // expected
        }
        assertEquals(0, readOnlyDst.position());
        buffer.release();
    }

    @Test
    public void testReadBytes() {
        ByteBuf buffer = newBuffer(8);
        byte[] bytes = new byte[8];
        buffer.writeBytes(bytes);

        ByteBuf buffer2 = buffer.readBytes(4);
        assertSame(buffer.alloc(), buffer2.alloc());
        assertEquals(4, buffer.readerIndex());
        assertTrue(buffer.release());
        assertEquals(0, buffer.refCnt());
        assertTrue(buffer2.release());
        assertEquals(0, buffer2.refCnt());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetBytesByteBuffer() {
        byte[] bytes = {'a', 'b', 'c', 'd', 'e', 'f', 'g'};
        // Ensure destination buffer is bigger then what is in the ByteBuf.
        ByteBuffer nioBuffer = ByteBuffer.allocate(bytes.length + 1);
        ByteBuf buffer = newBuffer(bytes.length);
        try {
            buffer.writeBytes(bytes);
            buffer.getBytes(buffer.readerIndex(), nioBuffer);
        } finally {
            buffer.release();
        }
    }

    private void testRefCnt0(final boolean parameter) throws Exception {
        for (int i = 0; i < 10; i++) {
            final CountDownLatch latch = new CountDownLatch(1);
            final CountDownLatch innerLatch = new CountDownLatch(1);

            final ByteBuf buffer = newBuffer(4);
            assertEquals(1, buffer.refCnt());
            final AtomicInteger cnt = new AtomicInteger(Integer.MAX_VALUE);
            Thread t1 = new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean released;
                    if (parameter) {
                        released = buffer.release(buffer.refCnt());
                    } else {
                        released = buffer.release();
                    }
                    assertTrue(released);
                    Thread t2 = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            cnt.set(buffer.refCnt());
                            latch.countDown();
                        }
                    });
                    t2.start();
                    try {
                        // Keep Thread alive a bit so the ThreadLocal caches are not freed
                        innerLatch.await();
                    } catch (InterruptedException ignore) {
                        // ignore
                    }
                }
            });
            t1.start();

            latch.await();
            assertEquals(0, cnt.get());
            innerLatch.countDown();
        }
    }

    private static final class DevNullGatheringByteChannel implements GatheringByteChannel {
        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long write(ByteBuffer[] srcs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int write(ByteBuffer src) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public void close() {
            throw new UnsupportedOperationException();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCapacityEnforceMaxCapacity() {
        ByteBuf buffer = newBuffer(3, 13);
        assertEquals(13, buffer.maxCapacity());
        assertEquals(3, buffer.capacity());
        try {
            buffer.capacity(14);
        } finally {
            buffer.release();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCapacityNegative() {
        ByteBuf buffer = newBuffer(3, 13);
        assertEquals(13, buffer.maxCapacity());
        assertEquals(3, buffer.capacity());
        try {
            buffer.capacity(-1);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testCapacityDecrease() {
        ByteBuf buffer = newBuffer(3, 13);
        assertEquals(13, buffer.maxCapacity());
        assertEquals(3, buffer.capacity());
        try {
            buffer.capacity(2);
            assertEquals(2, buffer.capacity());
            assertEquals(13, buffer.maxCapacity());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testCapacityIncrease() {
        ByteBuf buffer = newBuffer(3, 13);
        assertEquals(13, buffer.maxCapacity());
        assertEquals(3, buffer.capacity());
        try {
            buffer.capacity(4);
            assertEquals(4, buffer.capacity());
            assertEquals(13, buffer.maxCapacity());
        } finally {
            buffer.release();
        }
    }
}
