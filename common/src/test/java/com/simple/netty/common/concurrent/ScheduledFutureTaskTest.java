package com.simple.netty.common.concurrent;

import org.junit.Assert;
import org.junit.Test;

/**
 * Date: 2020-01-27
 * Time: 16:18
 *
 * @author yrw
 */
public class ScheduledFutureTaskTest {

    @Test
    public void testDeadlineNanosNotOverflow() {
        Assert.assertEquals(Long.MAX_VALUE, ScheduledFutureTask.deadlineNanos(Long.MAX_VALUE));
    }
}
