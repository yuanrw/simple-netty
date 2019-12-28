package com.simple.netty.buffer;

import com.simple.netty.common.internal.PlatformDependent;

/**
 * Date: 2019-12-15
 * Time: 19:45
 *
 * @author yrw
 */
public final class Unpooled {

    private static final ByteBufAllocator ALLOC = new UnpooledByteBufAllocator(PlatformDependent.directBufferPreferred());

    public static final ByteBuf EMPTY_BUFFER = ALLOC.buffer(0, 0);
}
