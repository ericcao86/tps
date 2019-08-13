package com.iflytek.tps.foun.hibernate;

/**
 * Created by losyn on 3/3/17.
 */
public class DataSourceManager {
    private String ds = IDynamicDS.DEFAULT;

    private static ThreadLocal<DataSourceManager> holder = ThreadLocal.withInitial(() -> new DataSourceManager());

    public static DataSourceManager get(){
        return holder.get();
    }

    public String getDataSource() {
        return ds;
    }

    public void setDataSource(String ds) {
        this.ds = ds;
    }
}
