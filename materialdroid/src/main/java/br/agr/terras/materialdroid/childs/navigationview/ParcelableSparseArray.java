package br.agr.terras.materialdroid.childs.navigationview;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;

public class ParcelableSparseArray extends SparseArray<Parcelable> implements Parcelable {
    public static final Creator<ParcelableSparseArray> CREATOR = new ClassLoaderCreator<ParcelableSparseArray>() {
        public ParcelableSparseArray createFromParcel(Parcel source, ClassLoader loader) {
            return new ParcelableSparseArray(source, loader);
        }

        public ParcelableSparseArray createFromParcel(Parcel source) {
            return new ParcelableSparseArray(source, (ClassLoader)null);
        }

        public ParcelableSparseArray[] newArray(int size) {
            return new ParcelableSparseArray[size];
        }
    };

    public ParcelableSparseArray() {
    }

    public ParcelableSparseArray(Parcel source, ClassLoader loader) {
        int size = source.readInt();
        int[] keys = new int[size];
        source.readIntArray(keys);
        Parcelable[] values = source.readParcelableArray(loader);

        for(int i = 0; i < size; ++i) {
            this.put(keys[i], values[i]);
        }

    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel parcel, int flags) {
        int size = this.size();
        int[] keys = new int[size];
        Parcelable[] values = new Parcelable[size];

        for(int i = 0; i < size; ++i) {
            keys[i] = this.keyAt(i);
            values[i] = (Parcelable)this.valueAt(i);
        }

        parcel.writeInt(size);
        parcel.writeIntArray(keys);
        parcel.writeParcelableArray(values, flags);
    }
}
