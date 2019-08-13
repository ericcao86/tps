package com.iflytek.tps.foun.helper;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;

/**
 * Created by losyn on 3/28/17.
 */
public final class ThreadFactoryHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ThreadFactoryHelper.class);

    private ThreadFactoryHelper() {
    }

    public static ThreadFactory threadFactoryOf(String nameFormat) {
        Thread.UncaughtExceptionHandler eh = (t, e) -> LOG.warn("Unexpected Exception at thread {}...", t.getName(), e);
        return new ThreadFactoryBuilder().setNameFormat(nameFormat).setUncaughtExceptionHandler(eh).build();
    }
}
