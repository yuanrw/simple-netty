package com.simple.netty.buffer;

/**
 * Date: 2019-12-15
 * Time: 19:45
 *
 * @author yrw
 */
public final class Unpooled {

    private static final ByteBufAllocator ALLOC = UnpooledByteBufAllocator.DEFAULT;

    public static final ByteBuf EMPTY_BUFFER = ALLOC.buffer(0, 0);
}
