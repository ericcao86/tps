package com.iflytek.tps.foun.helper;

import com.google.common.collect.Maps;
import org.phprpc.util.AssocArray;
import org.phprpc.util.PHPSerializer;

import java.util.Map;

/**
 * Created by losyn on 4/12/17.
 */
public final class PHPSerializerHelper {
    private static final PHPSerializer php = new PHPSerializer();

    private PHPSerializerHelper() {
    }

    public static Map<String, byte[]> deserialize(byte[] source) {
        try {
            Object obj = php.unserialize(source);
            if(obj instanceof Map){
                return (Map<String, byte[]>)obj;
            }
            if(obj instanceof AssocArray) {
                return ((AssocArray)obj).toHashMap();
            }
        }catch (Exception e){
            throw new RuntimeException("php deserialize error.....", e);
        }
        return Maps.newHashMap();
    }
}
