package com.simple.netty.buffer;

/**
 * Date: 2019-12-18
 * Time: 20:00
 *
 * @author yrw
 */
public interface PoolSubPageMetric {

    /**
     * Return the number of maximal elements that can be allocated out of the sub-page.
     */
    int maxNumElements();

    /**
     * Return the number of available elements to be allocated.
     */
    int numAvailable();

    /**
     * Return the size (in bytes) of the elements that will be allocated.
     */
    int elementSize();

    /**
     * Return the size (in bytes) of this page.
     */
    int pageSize();
}
