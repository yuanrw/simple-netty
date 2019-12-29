package com.simple.netty.buffer;

import static org.junit.Assert.assertEquals;

/**
 * Date: 2019-12-29
 * Time: 16:04
 *
 * @author yrw
 */
public abstract class AbstractPooledByteBufTest extends AbstractByteBufTest {

    protected abstract ByteBuf alloc(int length, int maxCapacity);

    @Override
    protected ByteBuf newBuffer(int length, int maxCapacity) {
        ByteBuf buffer = alloc(length, maxCapacity);

        // Testing if the writerIndex and readerIndex are correct when allocate and also after we reset the mark.
        assertEquals(0, buffer.writerIndex());
        assertEquals(0, buffer.readerIndex());
        buffer.resetReaderIndex();
        buffer.resetWriterIndex();
        assertEquals(0, buffer.writerIndex());
        assertEquals(0, buffer.readerIndex());
        return buffer;
    }
}
