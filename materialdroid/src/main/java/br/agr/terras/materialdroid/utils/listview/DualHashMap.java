package br.agr.terras.materialdroid.utils.listview;

import java.util.HashMap;

/**
 * Created by leo on 15/06/16.
 */
public class DualHashMap<TKey, TValue> {
    HashMap<TKey, TValue> mKeyToValue = new HashMap<TKey, TValue>();
    HashMap<TValue, TKey> mValueToKey = new HashMap<TValue, TKey>();

    public void put(TKey t1, TValue t2){
        remove(t1);
        removeByValue(t2);
        mKeyToValue.put(t1, t2);
        mValueToKey.put(t2, t1);
    }

    public TKey getKey(TValue value){
        return mValueToKey.get(value);
    }
    public TValue get(TKey key){
        return mKeyToValue.get(key);
    }

    public void remove(TKey key){
        if(get(key)!=null){
            mValueToKey.remove(get(key));
        }
        mKeyToValue.remove(key);
    }
    public void removeByValue(TValue value){
        if(getKey(value)!=null){
            mKeyToValue.remove(getKey(value));
        }
        mValueToKey.remove(value);
    }
}